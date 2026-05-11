package io.github.unityinflow.budget

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ModelPricingTest {

    @Test
    fun `estimates cost for Claude Sonnet`() {
        val cost = ModelPricing.estimateCost("claude-sonnet-4-6", promptTokens = 1_000_000, completionTokens = 0)
        cost shouldBe 3.0 // $3 per million input tokens
    }

    @Test
    fun `estimates cost for Claude Opus output`() {
        val cost = ModelPricing.estimateCost("claude-opus-4-6", promptTokens = 0, completionTokens = 1_000_000)
        cost shouldBe 75.0 // $75 per million output tokens
    }

    @Test
    fun `estimates combined input + output cost`() {
        val cost = ModelPricing.estimateCost("claude-sonnet-4-6", promptTokens = 500_000, completionTokens = 100_000)
        // 0.5M * $3/M + 0.1M * $15/M = $1.5 + $1.5 = $3.0
        cost shouldBe 3.0
    }

    @Test
    fun `returns zero for unknown model`() {
        val cost = ModelPricing.estimateCost("unknown-model", promptTokens = 1000, completionTokens = 1000)
        cost shouldBe 0.0
    }

    @Test
    fun `supports custom price overrides`() {
        val pricing = ModelPricing(
            overrides = mapOf(
                "my-model" to ModelPricing.PriceConfig(inputPerMillion = 10.0, outputPerMillion = 50.0)
            )
        )
        val cost = pricing.estimateCost("my-model", promptTokens = 1_000_000, completionTokens = 0)
        cost shouldBe 10.0
    }

    @Test
    fun `override takes precedence over default`() {
        val pricing = ModelPricing(
            overrides = mapOf(
                "claude-sonnet-4-6" to ModelPricing.PriceConfig(inputPerMillion = 99.0, outputPerMillion = 99.0)
            )
        )
        val cost = pricing.estimateCost("claude-sonnet-4-6", promptTokens = 1_000_000, completionTokens = 0)
        cost shouldBe 99.0
    }
}
