package io.github.unityinflow.budget

/** Events emitted by the budget system via SharedFlow. */
sealed class BudgetEvent {
    abstract val agentId: String
    abstract val tokensUsed: Long

    data class SoftLimitReached(
        override val agentId: String,
        override val tokensUsed: Long,
        val budgetTokens: Long,
        val percentUsed: Double,
    ) : BudgetEvent()

    data class HardLimitExceeded(
        override val agentId: String,
        override val tokensUsed: Long,
        val budgetTokens: Long,
        val estimatedCostUsd: Double,
    ) : BudgetEvent()

    data class CallTracked(
        override val agentId: String,
        override val tokensUsed: Long,
        val promptTokens: Long,
        val completionTokens: Long,
    ) : BudgetEvent()
}
