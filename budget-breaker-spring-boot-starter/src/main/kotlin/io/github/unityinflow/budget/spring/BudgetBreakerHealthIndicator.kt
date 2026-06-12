package io.github.unityinflow.budget.spring

import io.github.unityinflow.budget.BudgetCircuitBreaker
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator

/**
 * Spring Boot Actuator health indicator for the budget-breaker.
 *
 * **Always reports UP** regardless of breach state. This is by design (D-07):
 * a budget breach means an agent spent too many tokens, not that the application
 * is unhealthy. Flipping to DOWN would cause Kubernetes liveness/readiness probes
 * to kill healthy pods, which is the opposite of the intended behaviour.
 *
 * The three detail keys provide operational observability without disrupting
 * deployment health gates:
 * - `agentsTracked` — agents currently in-flight inside a `withBudget` block
 * - `softBreaches` — total soft limit breaches across all completed agent runs
 * - `hardBreaches` — number of completed agent runs where the hard limit was breached
 *
 * Appears in `/actuator/health` under the key `budgetBreaker` (class name minus
 * the `HealthIndicator` suffix, camelCase).
 *
 * This class has no @Component annotation — it is registered as a @Bean in the
 * auto-configuration class (plan 03). Do not add @Component here.
 *
 * @param breaker The [BudgetCircuitBreaker] instance to read aggregate counters from.
 */
class BudgetBreakerHealthIndicator(private val breaker: BudgetCircuitBreaker) : HealthIndicator {

    /**
     * Returns [Health.up] with three budget detail keys.
     *
     * Never returns [Health.down] — see class-level KDoc for rationale.
     */
    override fun health(): Health =
        Health.up()
            .withDetail("agentsTracked", breaker.getActiveTrackerCount())
            .withDetail("softBreaches", breaker.getTotalSoftBreaches())
            .withDetail("hardBreaches", breaker.getTotalHardBreaches())
            .build()
}
