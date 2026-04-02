package dev.unityinflow.budget

import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Scope DSL for tracking LLM calls within a budget.
 * Call [trackCall] after each LLM invocation to record token usage.
 */
class BudgetScope internal constructor(
    private val tracker: TokenTracker,
    private val pricing: ModelPricing,
    private val onSoftLimit: ((BudgetReport) -> Unit)?,
    private val eventFlow: MutableSharedFlow<BudgetEvent>,
) {
    private val softLimitBreachCount = java.util.concurrent.atomic.AtomicInteger(0)
    private val softLimitFired = java.util.concurrent.atomic.AtomicBoolean(false)

    /**
     * Record token usage from an LLM call.
     * Checks soft/hard limits after recording.
     *
     * @throws BudgetHardLimitException if hard limit is exceeded
     */
    suspend fun trackCall(promptTokens: Long, completionTokens: Long) {
        tracker.add(promptTokens, completionTokens)

        eventFlow.emit(
            BudgetEvent.CallTracked(
                agentId = tracker.agentId,
                tokensUsed = tracker.totalTokens,
                promptTokens = promptTokens,
                completionTokens = completionTokens,
            )
        )

        if (tracker.isAboveHardLimit()) {
            val cost = pricing.estimateCost(
                tracker.model, tracker.promptTokens, tracker.completionTokens
            )
            val event = BudgetEvent.HardLimitExceeded(
                agentId = tracker.agentId,
                tokensUsed = tracker.totalTokens,
                budgetTokens = tracker.hardLimitTokens,
                estimatedCostUsd = cost,
            )
            eventFlow.emit(event)
            throw BudgetHardLimitException(
                agentId = tracker.agentId,
                tokensUsed = tracker.totalTokens,
                budgetTokens = tracker.hardLimitTokens,
                estimatedCostUsd = cost,
            )
        }

        if (tracker.isAboveSoftLimit() && !softLimitFired.getAndSet(true)) {
            softLimitBreachCount.incrementAndGet()
            val report = buildReport(0)
            onSoftLimit?.invoke(report)
            eventFlow.emit(
                BudgetEvent.SoftLimitReached(
                    agentId = tracker.agentId,
                    tokensUsed = tracker.totalTokens,
                    budgetTokens = tracker.softLimitTokens,
                    percentUsed = tracker.percentUsed(),
                )
            )
        }
    }

    internal fun buildReport(durationMs: Long): BudgetReport {
        val cost = pricing.estimateCost(
            tracker.model, tracker.promptTokens, tracker.completionTokens
        )
        return BudgetReport(
            agentId = tracker.agentId,
            model = tracker.model,
            promptTokens = tracker.promptTokens,
            completionTokens = tracker.completionTokens,
            totalTokens = tracker.totalTokens,
            estimatedCostUsd = cost,
            softLimitBreachCount = softLimitBreachCount.get(),
            hardLimitBreached = tracker.isAboveHardLimit(),
            durationMs = durationMs,
            percentUsed = tracker.percentUsed(),
        )
    }
}
