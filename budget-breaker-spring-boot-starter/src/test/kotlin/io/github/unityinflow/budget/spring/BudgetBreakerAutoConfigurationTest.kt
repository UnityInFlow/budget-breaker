package io.github.unityinflow.budget.spring

import io.github.unityinflow.budget.BudgetCircuitBreaker
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
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

    @Configuration(proxyBeanMethods = false)
    internal class CustomBreakerConfig {
        @Bean("customBreaker")
        fun customBreaker(): BudgetCircuitBreaker = BudgetCircuitBreaker()
    }
}
