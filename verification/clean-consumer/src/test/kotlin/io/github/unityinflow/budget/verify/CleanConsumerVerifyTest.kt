package io.github.unityinflow.budget.verify

import io.github.unityinflow.budget.BudgetCircuitBreaker
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Clean-consumer verification test (D-04, STARTER-06).
 *
 * This test is the automated proof that "published" means *clean-consumer-resolvable from Maven
 * Central*. Unlike the in-repo [BudgetStarterIntegrationTest], this build:
 *
 * - resolves `io.github.unityinflow:budget-breaker-spring-boot-starter` from Maven Central
 *   ONLY (no local-repo lookup, no sibling-module dependency), and
 * - does NOT import the auto-config class explicitly. Instead it uses [EnableAutoConfiguration],
 *   which forces Spring to discover auto-configurations from the classpath via each jar's
 *   `META-INF/spring/...AutoConfiguration.imports`. That exercises the published jar's imports
 *   file itself (STARTER-02), not merely the auto-config class.
 *
 * It then asserts the [BudgetCircuitBreaker] bean auto-registers — confirming the published
 * starter boots a Spring context end-to-end straight off Maven Central.
 *
 * NOTE: this build will NOT compile/resolve until the coordinate is actually on Central
 * (Plan 03, after the human Publish). That failure-until-published behaviour IS the verification
 * mechanism — it is run via `scripts/verify-published.sh` once the artifact has propagated.
 */
@SpringBootTest(classes = [CleanConsumerVerifyTest.TestConfig::class])
class CleanConsumerVerifyTest {

    @Autowired
    lateinit var breaker: BudgetCircuitBreaker

    @Test
    fun `BudgetCircuitBreaker auto-registers from the published starter jar`() {
        // If this is non-null, the published jar's AutoConfiguration.imports was discovered on the
        // classpath and BudgetBreakerAutoConfiguration ran — the published starter is consumable.
        assertThat(breaker).isNotNull
    }

    /**
     * Minimal consumer configuration.
     *
     * [EnableAutoConfiguration] (NOT an explicit per-class auto-config import) makes Spring scan
     * the classpath's `AutoConfiguration.imports` files — proving the published jar's imports entry.
     * A [SimpleMeterRegistry] bean is provided because the starter declares `micrometer-core`
     * `compileOnly`, so a real consumer supplies its own [MeterRegistry] (matches production).
     */
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    class TestConfig {
        @Bean
        fun meterRegistry(): MeterRegistry = SimpleMeterRegistry()
    }
}
