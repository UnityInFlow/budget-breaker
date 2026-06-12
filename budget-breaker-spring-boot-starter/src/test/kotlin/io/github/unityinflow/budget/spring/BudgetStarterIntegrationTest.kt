package io.github.unityinflow.budget.spring

import io.github.unityinflow.budget.BudgetCircuitBreaker
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.test.context.ContextConfiguration
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
 * No [Thread.sleep] — yields via [kotlinx.coroutines.yield] and [delay] (coroutines only).
 */
@SpringBootTest(classes = [BudgetStarterIntegrationTest.TestConfig::class])
@ContextConfiguration(classes = [BudgetStarterIntegrationTest.TestConfig::class])
@TestPropertySource(
    properties = [
        "budget-breaker.default-model=claude-sonnet-4-6",
        "budget-breaker.hard-limit-tokens=100000",
        "budget-breaker.soft-limit-tokens=80000",
    ],
)
class BudgetStarterIntegrationTest {

    @Autowired
    private lateinit var breaker: BudgetCircuitBreaker

    @Autowired
    private lateinit var registry: MeterRegistry

    @Autowired
    private lateinit var endpoint: BudgetEndpoint

    @Autowired
    private lateinit var collector: MetricsEventCollector

    @Test
    fun `gen_ai counters appear in MeterRegistry after a tracked call`() = runBlocking {
        // Ensure the collector has subscribed to the events Flow.
        // SmartLifecycle.start() is called by Spring during context refresh;
        // yield here lets the coroutine dispatcher process the subscription before
        // we emit the first event (mirrors BudgetCircuitBreakerTest yield pattern).
        yield()

        breaker.withBudget("test-agent") {
            trackCall(promptTokens = 1000, completionTokens = 500)
        }

        // Yield to let MetricsEventCollector's coroutine process the buffered CallTracked event.
        yield()
        // Small additional delay so Dispatchers.Default coroutine has time to run.
        delay(50)

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
    fun `budget endpoint reports the tracked agent after a call`() = runBlocking {
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
    @org.springframework.boot.autoconfigure.ImportAutoConfiguration(
        BudgetBreakerAutoConfiguration::class,
    )
    class TestConfig {
        @Bean
        fun meterRegistry(): MeterRegistry = SimpleMeterRegistry()
    }
}
