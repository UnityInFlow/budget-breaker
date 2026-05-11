package io.github.unityinflow.budget

import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class TokenTrackerTest {

    private val budget = AgentBudget(
        model = "claude-sonnet-4-6",
        hardLimitTokens = 1000,
        softLimitTokens = 800,
    )

    @Test
    fun `tracks prompt and completion tokens separately`() {
        val tracker = TokenTracker("test-agent", budget)
        tracker.add(promptTokens = 100, completionTokens = 50)

        tracker.promptTokens shouldBe 100
        tracker.completionTokens shouldBe 50
        tracker.totalTokens shouldBe 150
    }

    @Test
    fun `accumulates tokens across multiple calls`() {
        val tracker = TokenTracker("test-agent", budget)
        tracker.add(promptTokens = 100, completionTokens = 50)
        tracker.add(promptTokens = 200, completionTokens = 100)

        tracker.totalTokens shouldBe 450
    }

    @Test
    fun `detects soft limit breach`() {
        val tracker = TokenTracker("test-agent", budget)
        tracker.add(promptTokens = 500, completionTokens = 350)

        tracker.isAboveSoftLimit() shouldBe true
        tracker.isAboveHardLimit() shouldBe false
    }

    @Test
    fun `detects hard limit breach`() {
        val tracker = TokenTracker("test-agent", budget)
        tracker.add(promptTokens = 600, completionTokens = 500)

        tracker.isAboveHardLimit() shouldBe true
    }

    @Test
    fun `reports percent used`() {
        val tracker = TokenTracker("test-agent", budget)
        tracker.add(promptTokens = 250, completionTokens = 250)

        tracker.percentUsed() shouldBe 50.0
    }

    @Test
    fun `is thread-safe under concurrent access`() = runTest {
        val tracker = TokenTracker("test-agent", AgentBudget(hardLimitTokens = 1_000_000, softLimitTokens = 800_000))

        val jobs = (1..100).map {
            launch {
                repeat(100) {
                    tracker.add(promptTokens = 1, completionTokens = 1)
                }
            }
        }
        jobs.forEach { it.join() }

        tracker.totalTokens shouldBe 20_000
    }

    @Test
    fun `starts at zero`() {
        val tracker = TokenTracker("test-agent", budget)

        tracker.promptTokens shouldBe 0
        tracker.completionTokens shouldBe 0
        tracker.totalTokens shouldBe 0
        tracker.isAboveSoftLimit() shouldBe false
        tracker.isAboveHardLimit() shouldBe false
    }
}
