package io.github.unityinflow.budget.spring

import io.github.unityinflow.budget.AgentBudget
import io.github.unityinflow.budget.BudgetCircuitBreaker
import io.github.unityinflow.budget.ModelPricing
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean

/**
 * Spring Boot auto-configuration for the budget-breaker starter.
 *
 * Registers the following beans into the application context:
 *
 * - [BudgetCircuitBreaker] — the single app-facing bean; guarded with
 *   [@ConditionalOnMissingBean][ConditionalOnMissingBean] so user-defined beans win (D-17).
 *   Configured from [BudgetBreakerProperties] (prefix `budget-breaker.*`).
 * - [SLF4JEventLogger] — subscribes to the events Flow and logs soft-limit breaches at WARN (D-03).
 * - [MetricsEventCollector] — [io.micrometer.core.instrument.binder.MeterBinder] that drives
 *   Micrometer counters/gauges from budget events (SPRING-03).
 * - [BudgetEndpoint] — Actuator endpoint at `/actuator/budget` (SPRING-02).
 * - [BudgetBreakerHealthIndicator] — always-UP health indicator (D-07).
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
     * Exposed as a separate bean so both [budgetCircuitBreaker] and [budgetMetricsCollector]
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
     * Subscribes to [BudgetCircuitBreaker.events] via [SmartLifecycle][org.springframework.context.SmartLifecycle].
     */
    @Bean
    fun budgetEventLogger(breaker: BudgetCircuitBreaker): SLF4JEventLogger =
        SLF4JEventLogger(breaker)

    /**
     * Micrometer [io.micrometer.core.instrument.binder.MeterBinder] for budget events (SPRING-03).
     *
     * Uses the shared [pricing] bean so `budget.breaker.cost.usd` cost calculations
     * honour the same per-model overrides as the breaker (D-11).
     */
    @Bean
    fun budgetMetricsCollector(
        breaker: BudgetCircuitBreaker,
        pricing: ModelPricing,
    ): MetricsEventCollector = MetricsEventCollector(breaker, pricing)

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
