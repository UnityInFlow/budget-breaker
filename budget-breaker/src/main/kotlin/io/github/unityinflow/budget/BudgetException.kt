package io.github.unityinflow.budget

import kotlin.math.roundToInt

/** Sealed hierarchy for budget-related exceptions. */
sealed class BudgetException(message: String) : Exception(message)

/** Thrown when an agent's soft token limit is reached. Non-fatal — execution continues. */
class BudgetSoftLimitException(
    val agentId: String,
    val tokensUsed: Long,
    val budgetTokens: Long,
    val percentUsed: Double,
) : BudgetException(
    "Agent '$agentId' reached soft limit: ${percentUsed.roundToInt()}% used ($tokensUsed/$budgetTokens tokens)"
)

/** Thrown when an agent's hard token limit is exceeded. Fatal — coroutine scope is cancelled. */
class BudgetHardLimitException(
    val agentId: String,
    val tokensUsed: Long,
    val budgetTokens: Long,
    val estimatedCostUsd: Double,
) : BudgetException(
    "Agent '$agentId' exceeded hard limit ($tokensUsed/$budgetTokens tokens). Estimated cost: \$${String.format("%.4f", estimatedCostUsd)}"
)
