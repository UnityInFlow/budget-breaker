package io.github.unityinflow.budget

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import java.util.logging.Level
import java.util.logging.Logger

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
    private val softLimitFired = java.util.concurrent.atomic.AtomicBoolean(false)

    /**
     * Record token usage from an LLM call.
     * Checks soft/hard limits after recording.
     *
     * On the first soft limit breach, the [BudgetEvent.SoftLimitReached] event is emitted
     * **before** the `onSoftLimit` callback runs, and a throwing callback is logged at
     * WARNING and swallowed — a failing logging/alerting hook never aborts the agent run
     * and never suppresses the breach event ([CancellationException] is always re-thrown).
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
                model = tracker.model,
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
            tracker.recordSoftLimitBreach()
            // Emit the event BEFORE invoking the user callback so a throwing callback can
            // never suppress the breach from event consumers (metrics, WARN logging).
            eventFlow.emit(
                BudgetEvent.SoftLimitReached(
                    agentId = tracker.agentId,
                    tokensUsed = tracker.totalTokens,
                    budgetTokens = tracker.softLimitTokens,
                    percentUsed = tracker.percentUsed(),
                )
            )
            try {
                onSoftLimit?.invoke(buildReport(0))
            } catch (e: CancellationException) {
                throw e // never swallow coroutine cancellation
            } catch (e: Exception) {
                // A failing user callback (logging/alerting hook) must not abort the agent
                // run. The core module stays dependency-free, so log via JUL — Spring Boot
                // applications bridge JUL to SLF4J/Logback by default.
                log.log(
                    Level.WARNING,
                    "budget-breaker onSoftLimit callback failed for agent '${tracker.agentId}'",
                    e,
                )
            }
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
            softLimitBreachCount = tracker.softLimitBreachCount,
            hardLimitBreached = tracker.isAboveHardLimit(),
            durationMs = durationMs,
            percentUsed = tracker.percentUsed(),
        )
    }

    private companion object {
        val log: Logger = Logger.getLogger(BudgetScope::class.java.name)
    }
}
