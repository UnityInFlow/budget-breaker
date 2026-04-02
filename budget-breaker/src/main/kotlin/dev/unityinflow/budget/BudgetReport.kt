package dev.unityinflow.budget

/**
 * Summary of an agent's budget usage after a run completes.
 */
data class BudgetReport(
    val agentId: String,
    val model: String,
    val promptTokens: Long,
    val completionTokens: Long,
    val totalTokens: Long,
    val estimatedCostUsd: Double,
    val softLimitBreachCount: Int,
    val hardLimitBreached: Boolean,
    val durationMs: Long,
    val percentUsed: Double,
)
