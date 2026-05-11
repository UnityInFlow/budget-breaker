package io.github.unityinflow.budget

/**
 * Estimates LLM API costs based on token usage.
 * Has hardcoded defaults for popular models, supports custom overrides.
 */
class ModelPricing(
    private val overrides: Map<String, PriceConfig> = emptyMap(),
) {
    data class PriceConfig(
        val inputPerMillion: Double,
        val outputPerMillion: Double,
    )

    /** Estimate cost in USD for the given token usage. */
    fun estimateCost(model: String, promptTokens: Long, completionTokens: Long): Double {
        val config = overrides[model] ?: DEFAULTS[model] ?: return 0.0
        val inputCost = (promptTokens.toDouble() / 1_000_000) * config.inputPerMillion
        val outputCost = (completionTokens.toDouble() / 1_000_000) * config.outputPerMillion
        return inputCost + outputCost
    }

    companion object {
        private val DEFAULTS = mapOf(
            // Claude (April 2026)
            "claude-opus-4-6" to PriceConfig(15.0, 75.0),
            "claude-sonnet-4-6" to PriceConfig(3.0, 15.0),
            "claude-haiku-4-5" to PriceConfig(0.80, 4.0),
            // OpenAI
            "gpt-4o" to PriceConfig(2.50, 10.0),
            "gpt-4o-mini" to PriceConfig(0.15, 0.60),
            // Google
            "gemini-2.5-pro" to PriceConfig(1.25, 10.0),
            "gemini-2.5-flash" to PriceConfig(0.15, 0.60),
            // Local (free)
            "ollama" to PriceConfig(0.0, 0.0),
        )

        /** Convenience: estimate cost using default pricing (no overrides). */
        fun estimateCost(model: String, promptTokens: Long, completionTokens: Long): Double =
            ModelPricing().estimateCost(model, promptTokens, completionTokens)
    }
}
