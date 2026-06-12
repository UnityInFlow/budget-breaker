package io.github.unityinflow.budget.spring

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration properties for the budget-breaker starter.
 *
 * Bind via `budget-breaker.*` in your application.yml / application.properties.
 *
 * Example:
 * ```yaml
 * budget-breaker:
 *   default-model: claude-sonnet-4-6
 *   hard-limit-tokens: 100000
 *   soft-limit-tokens: 80000
 *   pricing:
 *     gpt-4o:
 *       input-per-million: 2.50
 *       output-per-million: 10.0
 * ```
 *
 * Invalid combinations (soft >= hard, non-positive values) cause a startup failure
 * with a clear, YAML-path-named message so the configuration error is easy to diagnose.
 *
 * @param defaultModel The default LLM model identifier used for cost estimation when no
 *   per-call model is specified. Defaults to `claude-sonnet-4-6`.
 * @param hardLimitTokens Hard token limit per agent run — exceeding this cancels the agent's
 *   coroutine scope. Must be positive. Defaults to `100_000`.
 * @param softLimitTokens Soft token limit per agent run — reaching this triggers a WARN log
 *   and a [io.github.unityinflow.budget.BudgetEvent.SoftLimitReached] event. Must be positive
 *   and <= [hardLimitTokens]. Defaults to `80_000`.
 * @param pricing Per-model pricing overrides (input and output cost per million tokens).
 *   When present, these override the hardcoded defaults in
 *   [io.github.unityinflow.budget.ModelPricing]. Keyed by model identifier string.
 */
@ConfigurationProperties("budget-breaker")
data class BudgetBreakerProperties(
    val defaultModel: String = "claude-sonnet-4-6",
    val hardLimitTokens: Long = 100_000,
    val softLimitTokens: Long = 80_000,
    val pricing: Map<String, ModelPriceProperties> = emptyMap(),
) {
    init {
        require(hardLimitTokens > 0) { "budget-breaker.hard-limit-tokens must be positive" }
        require(softLimitTokens > 0) { "budget-breaker.soft-limit-tokens must be positive" }
        require(softLimitTokens <= hardLimitTokens) {
            "budget-breaker.soft-limit-tokens must be <= hard-limit-tokens"
        }
    }
}

/**
 * Per-model pricing override for cost estimation.
 *
 * Used as values in [BudgetBreakerProperties.pricing] to override the default
 * hardcoded prices in [io.github.unityinflow.budget.ModelPricing].
 *
 * @param inputPerMillion Cost in USD per million input (prompt) tokens.
 * @param outputPerMillion Cost in USD per million output (completion) tokens.
 */
data class ModelPriceProperties(
    val inputPerMillion: Double = 0.0,
    val outputPerMillion: Double = 0.0,
)
