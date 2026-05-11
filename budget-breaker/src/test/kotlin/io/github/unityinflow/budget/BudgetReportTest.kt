package io.github.unityinflow.budget

import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class BudgetReportTest {

    @Test
    fun `creates report with all fields`() {
        val report = BudgetReport(
            agentId = "test-agent",
            model = "claude-sonnet-4-6",
            promptTokens = 500,
            completionTokens = 200,
            totalTokens = 700,
            estimatedCostUsd = 0.0045,
            softLimitBreachCount = 0,
            hardLimitBreached = false,
            durationMs = 1500,
            percentUsed = 70.0,
        )

        report.agentId shouldBe "test-agent"
        report.totalTokens shouldBe 700
        report.estimatedCostUsd shouldBeGreaterThan 0.0
        report.hardLimitBreached shouldBe false
    }

    @Test
    fun `totalTokens equals prompt + completion`() {
        val report = BudgetReport(
            agentId = "test",
            model = "claude-sonnet-4-6",
            promptTokens = 300,
            completionTokens = 200,
            totalTokens = 500,
            estimatedCostUsd = 0.0,
            softLimitBreachCount = 0,
            hardLimitBreached = false,
            durationMs = 0,
            percentUsed = 50.0,
        )

        report.totalTokens shouldBe (report.promptTokens + report.completionTokens)
    }
}
