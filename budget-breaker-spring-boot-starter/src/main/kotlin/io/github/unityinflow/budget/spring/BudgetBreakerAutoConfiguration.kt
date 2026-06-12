package io.github.unityinflow.budget.spring

import io.github.unityinflow.budget.AgentBudget
import io.github.unityinflow.budget.BudgetCircuitBreaker
import io.github.unityinflow.budget.ModelPricing
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Spring Boot auto-configuration for the budget-breaker starter.
 *
 * Registers the following beans into the application context:
 *
 * - [BudgetCircuitBreaker] — the single app-facing bean; guarded with
 *   [@ConditionalOnMissingBean][ConditionalOnMissingBean] so user-defined beans win (D-17).
 *   Configured from [BudgetBreakerProperties] (prefix `budget-breaker.*`).
 * - [SLF4JEventLogger] — subscribes to the events Flow and logs soft-limit breaches at WARN (D-03).
 *   SLF4J is always present on the Spring Boot classpath — no conditional needed.
 * - [MetricsEventCollector] — [io.micrometer.core.instrument.binder.MeterBinder] that drives
 *   Micrometer counters/gauges from budget events (SPRING-03). Registered only when
 *   `io.micrometer.core.instrument.MeterRegistry` is on the runtime classpath
 *   (see [MicrometerConfiguration]).
 * - [BudgetEndpoint] — Actuator endpoint at `/actuator/budget` (SPRING-02). Registered only when
 *   `org.springframework.boot.actuate.health.HealthIndicator` is on the runtime classpath
 *   (see [ActuatorConfiguration]).
 * - [BudgetBreakerHealthIndicator] — always-UP health indicator (D-07). Registered only when
 *   Actuator is on the runtime classpath (see [ActuatorConfiguration]).
 *
 * ## Zero-Config Boot
 *
 * No properties required. Sensible defaults are provided by [BudgetBreakerProperties]:
 * - Model: `claude-sonnet-4-6`
 * - Hard limit: 100 000 tokens
 * - Soft limit: 80 000 tokens
 *
 * ## User Override
 *
 * Define your own [BudgetCircuitBreaker] bean to opt out of auto-configuration for that
 * bean. `@ConditionalOnMissingBean` ensures the user's bean always wins.
 *
 * ## Extension Point
 *
 * Custom soft-limit behaviour is achieved by subscribing directly to
 * [BudgetCircuitBreaker.events] — the auto-config does NOT wire a callback bean (D-18).
 * To use a callback, provide your own `BudgetCircuitBreaker` bean.
 *
 * ## Optional Dependency Design
 *
 * `spring-boot-actuator-autoconfigure` and `micrometer-core` are declared `compileOnly` in the
 * starter. Consumers without those jars get a functional [BudgetCircuitBreaker] with SLF4J event
 * logging; Actuator and Micrometer observability beans are omitted gracefully via nested
 * [ActuatorConfiguration] and [MicrometerConfiguration] inner classes guarded by
 * string-form `@ConditionalOnClass` — so the annotation itself never forces class loading.
 *
 * ## Discovery
 *
 * Registered via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
 * (Spring Boot 3.x idiom — no `spring.factories`).
 *
 * This class uses no component scanning — starters must never component-scan
 * the user's classpath (T-02-08 mitigation).
 */
@AutoConfiguration
@EnableConfigurationProperties(BudgetBreakerProperties::class)
class BudgetBreakerAutoConfiguration {

    /**
     * Builds a [ModelPricing] instance from the per-model overrides in [properties].
     *
     * Exposed as a separate bean so both [budgetCircuitBreaker] and any metrics collector
     * share the exact same pricing configuration (D-11: cost counter uses the same overrides
     * as the breaker).
     */
    @Bean
    fun budgetPricing(properties: BudgetBreakerProperties): ModelPricing =
        ModelPricing(
            overrides = properties.pricing.mapValues { (_, v) ->
                ModelPricing.PriceConfig(v.inputPerMillion, v.outputPerMillion)
            },
        )

    /**
     * The primary app-facing bean.
     *
     * Configured with [AgentBudget] defaults from [properties] and the pricing map
     * built by [budgetPricing]. No soft-limit callback is wired — apps that need
     * callback behaviour should subscribe to [BudgetCircuitBreaker.events] or provide
     * their own bean (D-18).
     *
     * Guarded with [@ConditionalOnMissingBean][ConditionalOnMissingBean]: if the consuming
     * application declares its own [BudgetCircuitBreaker] bean, this method is skipped (D-17).
     */
    @Bean
    @ConditionalOnMissingBean
    fun budgetCircuitBreaker(
        properties: BudgetBreakerProperties,
        pricing: ModelPricing,
    ): BudgetCircuitBreaker =
        BudgetCircuitBreaker(
            defaultBudget = AgentBudget(
                model = properties.defaultModel,
                hardLimitTokens = properties.hardLimitTokens,
                softLimitTokens = properties.softLimitTokens,
            ),
            pricing = pricing,
        )

    /**
     * SLF4J WARN logger for soft-limit breach events (D-03).
     *
     * SLF4J is always present in any Spring Boot application, so no `@ConditionalOnClass`
     * is needed here. Subscribes to [BudgetCircuitBreaker.events] via
     * [SmartLifecycle][org.springframework.context.SmartLifecycle].
     */
    @Bean
    fun budgetEventLogger(breaker: BudgetCircuitBreaker): SLF4JEventLogger =
        SLF4JEventLogger(breaker)

    /**
     * Nested configuration registering Actuator beans only when
     * `org.springframework.boot.actuate.health.HealthIndicator` is present on the runtime
     * classpath (CR-01 fix).
     *
     * Using the string-form `@ConditionalOnClass(name = [...])` ensures the annotation
     * itself never forces Actuator classes to load when they are absent.
     *
     * Spring Boot discovers nested `@Configuration` classes of an imported auto-config
     * automatically — no explicit import needed.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = ["org.springframework.boot.actuate.health.HealthIndicator"])
    inner class ActuatorConfiguration {

        /**
         * Actuator endpoint at `/actuator/budget` exposing live agent snapshots (SPRING-02).
         *
         * Expose via `management.endpoints.web.exposure.include=budget` in application.yml.
         */
        @Bean
        fun budgetEndpoint(breaker: BudgetCircuitBreaker): BudgetEndpoint =
            BudgetEndpoint(breaker)

        /**
         * Always-UP health indicator contributing budget detail keys to `/actuator/health` (D-07).
         */
        @Bean
        fun budgetHealthIndicator(breaker: BudgetCircuitBreaker): BudgetBreakerHealthIndicator =
            BudgetBreakerHealthIndicator(breaker)
    }

    /**
     * Nested configuration registering Micrometer beans only when
     * `io.micrometer.core.instrument.MeterRegistry` is present on the runtime
     * classpath (CR-01 fix).
     *
     * Using the string-form `@ConditionalOnClass(name = [...])` ensures the annotation
     * itself never forces Micrometer classes to load when they are absent.
     *
     * Spring Boot discovers nested `@Configuration` classes of an imported auto-config
     * automatically — no explicit import needed.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = ["io.micrometer.core.instrument.MeterRegistry"])
    inner class MicrometerConfiguration {

        /**
         * Micrometer [io.micrometer.core.instrument.binder.MeterBinder] for budget events (SPRING-03).
         *
         * Uses the shared [budgetPricing] bean so `budget.breaker.cost.usd` cost calculations
         * honour the same per-model overrides as the breaker (D-11).
         */
        @Bean
        fun budgetMetricsCollector(
            breaker: BudgetCircuitBreaker,
            pricing: ModelPricing,
        ): MetricsEventCollector = MetricsEventCollector(breaker, pricing)
    }
}
