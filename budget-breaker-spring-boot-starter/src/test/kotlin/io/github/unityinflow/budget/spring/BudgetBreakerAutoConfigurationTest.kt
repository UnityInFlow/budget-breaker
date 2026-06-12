package io.github.unityinflow.budget.spring

import io.github.unityinflow.budget.BudgetCircuitBreaker
import io.github.unityinflow.budget.ModelPricing
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.FilteredClassLoader
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

class BudgetBreakerAutoConfigurationTest {

    private val contextRunner = ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(BudgetBreakerAutoConfiguration::class.java))

    @Test
    fun `registers BudgetCircuitBreaker with defaults`() {
        contextRunner.run { ctx ->
            assertThat(ctx).hasSingleBean(BudgetCircuitBreaker::class.java)
        }
    }

    @Test
    fun `honors user-defined BudgetCircuitBreaker bean`() {
        contextRunner
            .withUserConfiguration(CustomBreakerConfig::class.java)
            .run { ctx ->
                assertThat(ctx).hasSingleBean(BudgetCircuitBreaker::class.java)
                assertThat(ctx.getBean(BudgetCircuitBreaker::class.java))
                    .isSameAs(ctx.getBean("customBreaker"))
            }
    }

    @Test
    fun `honors user-defined ModelPricing bean`() {
        contextRunner
            .withUserConfiguration(CustomPricingConfig::class.java)
            .run { ctx ->
                assertThat(ctx).hasNotFailed()
                assertThat(ctx).hasSingleBean(ModelPricing::class.java)
                assertThat(ctx.getBean(ModelPricing::class.java))
                    .isSameAs(ctx.getBean("customPricing"))
            }
    }

    @Test
    fun `validates invalid property combination at startup`() {
        contextRunner
            .withPropertyValues(
                "budget-breaker.soft-limit-tokens=100000",
                "budget-breaker.hard-limit-tokens=50000",
            )
            .run { ctx ->
                assertThat(ctx).hasFailed()
            }
    }

    @Test
    fun `registers observability beans`() {
        contextRunner.run { ctx ->
            assertThat(ctx).hasSingleBean(BudgetEndpoint::class.java)
            assertThat(ctx).hasSingleBean(BudgetBreakerHealthIndicator::class.java)
            assertThat(ctx).hasSingleBean(MetricsEventCollector::class.java)
            assertThat(ctx).hasSingleBean(SLF4JEventLogger::class.java)
        }
    }

    @Test
    fun `context starts without actuator on the classpath`() {
        contextRunner
            .withClassLoader(FilteredClassLoader(HealthIndicator::class.java))
            .run { ctx ->
                assertThat(ctx).hasNotFailed()
                assertThat(ctx).hasSingleBean(BudgetCircuitBreaker::class.java)
                assertThat(ctx).doesNotHaveBean(BudgetEndpoint::class.java)
                assertThat(ctx).doesNotHaveBean(BudgetBreakerHealthIndicator::class.java)
            }
    }

    @Test
    fun `context starts without micrometer on the classpath`() {
        contextRunner
            .withClassLoader(FilteredClassLoader(MeterRegistry::class.java))
            .run { ctx ->
                assertThat(ctx).hasNotFailed()
                assertThat(ctx).hasSingleBean(BudgetCircuitBreaker::class.java)
                assertThat(ctx).doesNotHaveBean(MetricsEventCollector::class.java)
            }
    }

    /**
     * Tests that MetricsEventCollector continues processing events after a handler exception.
     *
     * Strategy: use a [ThrowingOnFirstNewCounterRegistry] that throws [RuntimeException] when
     * Micrometer first calls [newCounter] (the protected factory method in MeterRegistry that
     * creates new counters). This simulates a corrupt/broken registry state on the first event.
     *
     * Without try/catch in the collect lambda, the first thrown exception terminates the Flow
     * collection permanently — the second event is never processed. With try/catch the
     * collection loop continues and the second event's counter must be registered and
     * incremented.
     *
     * Uses bounded polling with [withTimeout] + [delay] — no [Thread.sleep].
     */
    @Test
    fun `event collection survives a handler exception`() = runBlocking<Unit> {
        val breaker = BudgetCircuitBreaker()
        val throwingRegistry = ThrowingOnFirstNewCounterRegistry()
        val pricing = ModelPricing()
        val collector = MetricsEventCollector(breaker, pricing)

        collector.bindTo(throwingRegistry)
        collector.start()

        // First call: registry throws on newCounter — without try/catch this kills the collect loop.
        breaker.withBudget("agent-error") {
            trackCall(promptTokens = 100, completionTokens = 50)
        }

        // Allow the collector coroutine to process the first (failing) event.
        delay(100)

        // Second call: must still be processed if try/catch is in place.
        breaker.withBudget("agent-normal") {
            trackCall(promptTokens = 200, completionTokens = 100)
        }

        // Bounded poll: wait up to 5 seconds for the second event's meter to appear.
        withTimeout(5_000) {
            while (
                throwingRegistry.getMeters()
                    .none { m ->
                        m.id.name == "gen_ai.client.token.usage.input" &&
                            m.id.getTag("agent") == "agent-normal"
                    }
            ) {
                delay(10)
            }
        }

        val inputCount = throwingRegistry
            .get("gen_ai.client.token.usage.input")
            .tag("agent", "agent-normal")
            .counter()
            .count()
        assertThat(inputCount).isEqualTo(200.0)

        collector.stop()
    }

    @Configuration(proxyBeanMethods = false)
    internal class CustomBreakerConfig {
        @Bean("customBreaker")
        fun customBreaker(): BudgetCircuitBreaker = BudgetCircuitBreaker()
    }

    @Configuration(proxyBeanMethods = false)
    internal class CustomPricingConfig {
        @Bean("customPricing")
        fun customPricing(): ModelPricing = ModelPricing()
    }

    /**
     * A [SimpleMeterRegistry] subclass that throws [RuntimeException] the first time
     * Micrometer calls its [newCounter] factory method, simulating a broken registry state.
     *
     * [newCounter] is the protected factory invoked when a [Counter.builder] is registered for
     * the first time — it is the correct interception point for `Counter.builder().register()`.
     *
     * After the first throw, all subsequent [newCounter] calls behave normally.
     */
    private inner class ThrowingOnFirstNewCounterRegistry : SimpleMeterRegistry() {
        private var newCounterCallCount = 0

        override fun newCounter(id: io.micrometer.core.instrument.Meter.Id): Counter {
            newCounterCallCount++
            if (newCounterCallCount == 1) {
                throw RuntimeException("simulated newCounter failure on first call (event $id)")
            }
            return super.newCounter(id)
        }
    }
}
