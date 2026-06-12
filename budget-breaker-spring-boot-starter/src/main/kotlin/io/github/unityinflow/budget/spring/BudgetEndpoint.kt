package io.github.unityinflow.budget.spring

import io.github.unityinflow.budget.BudgetCircuitBreaker
import io.github.unityinflow.budget.BudgetReport
import io.github.unityinflow.budget.BudgetSnapshot
import org.springframework.boot.actuate.endpoint.annotation.Endpoint
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation
import org.springframework.boot.actuate.endpoint.annotation.Selector

/**
 * Actuator endpoint exposing live budget usage for all tracked agents.
 *
 * Provides two read-only views over [BudgetCircuitBreaker]:
 * - Aggregate: all tracked agents (in-flight and completed) via `GET /actuator/budget`
 * - Per-agent: single agent's full [BudgetReport] via `GET /actuator/budget/{agentId}`
 *
 * **Exposure:** This endpoint is NOT auto-exposed. Opt in via application.yml:
 * ```yaml
 * management:
 *   endpoints:
 *     web:
 *       exposure:
 *         include: "health,info,budget"
 * ```
 *
 * **Security:** Restrict the management port or apply Spring Security to avoid
 * leaking agent names and token counts to unauthorised callers.
 *
 * This class has no @Component annotation — it is registered as a @Bean
 * in the auto-configuration class (plan 03). Do not add @Component here.
 *
 * @param breaker The [BudgetCircuitBreaker] instance to read reports from.
 */
@Endpoint(id = "budget")
class BudgetEndpoint(private val breaker: BudgetCircuitBreaker) {

    /**
     * Returns a snapshot of all tracked agents, keyed by agent ID.
     *
     * Each entry is a [BudgetSnapshot] containing a [BudgetReport] and a [BudgetSnapshot.running]
     * flag indicating whether the agent is currently executing inside a `withBudget` block.
     *
     * This method reads directly from [BudgetCircuitBreaker.getAllReports], which returns an
     * immutable copy of the internal `ConcurrentHashMap` snapshot — no blocking, no suspend.
     *
     * Exposed at: `GET /actuator/budget`
     */
    @ReadOperation
    fun budget(): Map<String, BudgetSnapshot> = breaker.getAllReports()

    /**
     * Returns the full [BudgetReport] for a single agent by ID.
     *
     * Returns `null` if the agent is not found in completed reports.
     * Note: in-flight agents appear only in the aggregate [budget] endpoint.
     *
     * Exposed at: `GET /actuator/budget/{agentId}`
     *
     * @param agentId The agent identifier to query.
     */
    @ReadOperation
    fun agentBudget(@Selector agentId: String): BudgetReport? = breaker.getReport(agentId)
}
