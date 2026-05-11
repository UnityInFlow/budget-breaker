package io.github.unityinflow.budget

/**
 * Configuration for an agent's token budget.
 *
 * @param model The LLM model identifier (used for cost estimation)
 * @param hardLimitTokens Hard limit — exceeding this cancels the coroutine scope
 * @param softLimitTokens Soft limit — triggers callback and Flow event when reached
 */
data class AgentBudget(
    val model: String = "claude-sonnet-4-6",
    val hardLimitTokens: Long = 100_000,
    val softLimitTokens: Long = 80_000,
) {
    init {
        require(hardLimitTokens > 0) { "hardLimitTokens must be positive" }
        require(softLimitTokens > 0) { "softLimitTokens must be positive" }
        require(softLimitTokens <= hardLimitTokens) { "softLimitTokens must be <= hardLimitTokens" }
    }
}
