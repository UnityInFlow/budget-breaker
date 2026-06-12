package io.github.unityinflow.budget.spring

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test

class BudgetBreakerPropertiesTest {

    @Test
    fun `default construction yields documented defaults`() {
        val props = BudgetBreakerProperties()

        props.defaultModel shouldBe "claude-sonnet-4-6"
        props.hardLimitTokens shouldBe 100_000L
        props.softLimitTokens shouldBe 80_000L
        props.pricing shouldBe emptyMap()
    }

    @Test
    fun `throws when softLimitTokens exceeds hardLimitTokens`() {
        val ex = shouldThrow<IllegalArgumentException> {
            BudgetBreakerProperties(softLimitTokens = 100_000, hardLimitTokens = 50_000)
        }

        ex.message shouldContain "soft-limit-tokens"
        ex.message shouldContain "hard-limit-tokens"
    }

    @Test
    fun `throws when hardLimitTokens is negative`() {
        shouldThrow<IllegalArgumentException> {
            BudgetBreakerProperties(hardLimitTokens = -1)
        }
    }

    @Test
    fun `throws when softLimitTokens is zero`() {
        shouldThrow<IllegalArgumentException> {
            BudgetBreakerProperties(softLimitTokens = 0)
        }
    }

    @Test
    fun `throws when hardLimitTokens is zero`() {
        shouldThrow<IllegalArgumentException> {
            BudgetBreakerProperties(hardLimitTokens = 0, softLimitTokens = 0)
        }
    }

    @Test
    fun `allows equal soft and hard limits`() {
        val props = BudgetBreakerProperties(softLimitTokens = 50_000, hardLimitTokens = 50_000)
        props.softLimitTokens shouldBe 50_000L
        props.hardLimitTokens shouldBe 50_000L
    }

    @Test
    fun `stores pricing map entries`() {
        val pricing = mapOf(
            "gpt-4o" to ModelPriceProperties(inputPerMillion = 2.50, outputPerMillion = 10.0),
        )
        val props = BudgetBreakerProperties(pricing = pricing)
        props.pricing["gpt-4o"]?.inputPerMillion shouldBe 2.50
        props.pricing["gpt-4o"]?.outputPerMillion shouldBe 10.0
    }
}
