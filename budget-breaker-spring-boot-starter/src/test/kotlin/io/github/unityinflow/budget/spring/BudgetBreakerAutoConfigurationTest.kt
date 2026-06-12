package io.github.unityinflow.budget.spring

import io.github.unityinflow.budget.BudgetCircuitBreaker
import io.kotest.matchers.shouldBeSameInstanceAs
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
            org.assertj.core.api.Assertions.assertThat(ctx).hasSingleBean(BudgetCircuitBreaker::class.java)
        }
    }

    @Test
    fun `honors user-defined BudgetCircuitBreaker bean`() {
        contextRunner
            .withUserConfiguration(CustomBreakerConfig::class.java)
            .run { ctx ->
                org.assertj.core.api.Assertions.assertThat(ctx).hasSingleBean(BudgetCircuitBreaker::class.java)
                val autoBean = ctx.getBean(BudgetCircuitBreaker::class.java)
                val customBean = ctx.getBean("customBreaker") as BudgetCircuitBreaker
                autoBean shouldBeSameInstanceAs customBean
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
                org.assertj.core.api.Assertions.assertThat(ctx).hasFailed()
            }
    }

    @Test
    fun `registers observability beans`() {
        contextRunner.run { ctx ->
            org.assertj.core.api.Assertions.assertThat(ctx).hasSingleBean(BudgetEndpoint::class.java)
            org.assertj.core.api.Assertions.assertThat(ctx).hasSingleBean(BudgetBreakerHealthIndicator::class.java)
            org.assertj.core.api.Assertions.assertThat(ctx).hasSingleBean(MetricsEventCollector::class.java)
            org.assertj.core.api.Assertions.assertThat(ctx).hasSingleBean(SLF4JEventLogger::class.java)
        }
    }

    @Configuration
    internal class CustomBreakerConfig {
        @Bean("customBreaker")
        fun customBreaker(): BudgetCircuitBreaker = BudgetCircuitBreaker()
    }
}
