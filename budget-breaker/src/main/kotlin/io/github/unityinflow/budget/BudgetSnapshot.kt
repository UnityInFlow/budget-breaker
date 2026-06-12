package io.github.unityinflow.budget

/**
 * A point-in-time snapshot of an agent's budget usage.
 *
 * Combines the agent's [BudgetReport] with a flag indicating whether the
 * agent is currently executing inside a [BudgetCircuitBreaker.withBudget] block.
 *
 * Returned by [BudgetCircuitBreaker.getAllReports] so callers can distinguish
 * live in-flight agents ([running] == `true`) from completed runs
 * ([running] == `false`).
 */
data class BudgetSnapshot(
    /** The budget usage report for this agent. */
    val report: BudgetReport,
    /** `true` if the agent is currently in-flight inside a withBudget block; `false` if completed. */
    val running: Boolean,
)
