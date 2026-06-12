package io.github.unityinflow.budget.spring

import io.github.unityinflow.budget.BudgetCircuitBreaker
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.test.context.TestPropertySource

/**
 * @SpringBootTest smoke test for the budget-breaker-spring-boot-starter (D-19 layer 2).
 *
 * Boots a minimal Spring context with [BudgetBreakerAutoConfiguration] and a
 * [SimpleMeterRegistry]. Verifies:
 * 1. `gen_ai.client.token.usage.input` and `.output` counters appear in the
 *    [MeterRegistry] with the correct agent tag and values after a tracked call.
 * 2. The [BudgetEndpoint] reports the agent via [BudgetCircuitBreaker.getAllReports].
 *
 * No blocking sleeps — yields via [kotlinx.coroutines.yield] and [delay] (coroutines only).
 *
 * Uses [@SpringBootTest] to boot a real Spring application context (D-19 layer 2).
 *
 * **[MetricsEventCollector] binding:** The collector implements [io.micrometer.core.instrument.binder.MeterBinder].
 * Spring Boot's full Micrometer auto-config would call [MetricsEventCollector.bindTo] automatically,
 * but since this test uses a minimal context without that auto-config, [bindTo] is called
 * explicitly in [@BeforeEach] before emitting events. This matches the production lifecycle where
 * Spring Boot calls [bindTo] before [SmartLifecycle.start].
 */
@SpringBootTest(classes = [BudgetStarterIntegrationTest.TestConfig::class])
@TestPropertySource(
    properties = [
        "budget-breaker.default-model=claude-sonnet-4-6",
        "budget-breaker.hard-limit-tokens=100000",
        "budget-breaker.soft-limit-tokens=80000",
    ],
)
class BudgetStarterIntegrationTest {

    @Autowired
    lateinit var breaker: BudgetCircuitBreaker

    @Autowired
    lateinit var registry: MeterRegistry

    @Autowired
    lateinit var endpoint: BudgetEndpoint

    @Autowired
    lateinit var collector: MetricsEventCollector

    @BeforeEach
    fun bindCollectorToRegistry() {
        // Spring Boot's MeterRegistryAutoConfiguration normally calls bindTo() automatically.
        // In this minimal test context, we bind explicitly so counters are registered.
        // bindTo() is idempotent — safe to call on every test method.
        collector.bindTo(registry)
    }

    @Test
    fun `gen_ai counters appear in MeterRegistry after a tracked call`() = runBlocking<Unit> {
        // Await both event collectors (SLF4JEventLogger + MetricsEventCollector) on the
        // events Flow: it has no replay buffer, so emitting before their subscriptions are
        // established on Dispatchers.Default would silently drop the event. A yield() on
        // the runBlocking event loop cannot advance cross-dispatcher coroutines — bounded
        // polling on the subscription count is deterministic on loaded CI runners.
        withTimeout(5_000) {
            while (breaker.subscriptions.value < 2) delay(10)
        }

        breaker.withBudget("test-agent") {
            trackCall(promptTokens = 1000, completionTokens = 500)
        }

        // Bounded poll: wait for the collector coroutine on Dispatchers.Default to process
        // the buffered CallTracked event and increment both counters (no fixed sleeps).
        withTimeout(5_000) {
            while (
                (
                    registry.find("gen_ai.client.token.usage.input")
                        .tag("agent", "test-agent").counter()?.count() ?: 0.0
                ) < 1000.0 ||
                (
                    registry.find("gen_ai.client.token.usage.output")
                        .tag("agent", "test-agent").counter()?.count() ?: 0.0
                ) < 500.0
            ) {
                delay(10)
            }
        }

        val inputCount = registry
            .get("gen_ai.client.token.usage.input")
            .tag("agent", "test-agent")
            .counter()
            .count()
        assertThat(inputCount).isEqualTo(1000.0)

        val outputCount = registry
            .get("gen_ai.client.token.usage.output")
            .tag("agent", "test-agent")
            .counter()
            .count()
        assertThat(outputCount).isEqualTo(500.0)
    }

    @Test
    fun `budget endpoint reports the tracked agent after a call`() = runBlocking<Unit> {
        yield()

        breaker.withBudget("test-agent") {
            trackCall(promptTokens = 1000, completionTokens = 500)
        }

        val allReports = endpoint.budget()
        assertThat(allReports).containsKey("test-agent")
        assertThat(allReports["test-agent"]!!.report.agentId).isEqualTo("test-agent")
    }

    /**
     * Minimal Spring Boot test configuration.
     *
     * Imports [BudgetBreakerAutoConfiguration] explicitly and provides a
     * [SimpleMeterRegistry] so Micrometer meters can be registered and queried
     * in assertions. [SimpleMeterRegistry] lives in `micrometer-core` (RESEARCH A1).
     */
    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration(BudgetBreakerAutoConfiguration::class)
    class TestConfig {
        @Bean
        fun meterRegistry(): MeterRegistry = SimpleMeterRegistry()
    }
}
