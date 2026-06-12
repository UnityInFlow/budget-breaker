package io.github.unityinflow.budget

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Coroutine-aware circuit breaker for AI agent token budgets.
 *
 * Wraps agent code in a budget-tracked scope. Soft limit triggers callback + Flow event.
 * Hard limit cancels the coroutine scope.
 *
 * Use [withBudget] to run agent code within a budget. Track token usage via
 * [BudgetScope.trackCall] inside the block.
 *
 * Use [getAllReports] to query a live snapshot of all tracked agents, including
 * in-flight ones. Use [getActiveTrackerCount], [getTotalSoftBreaches],
 * [getTotalHardBreaches], and [modelOf] for aggregate observability.
 */
class BudgetCircuitBreaker(
    private val defaultBudget: AgentBudget = AgentBudget(),
    private val pricing: ModelPricing = ModelPricing(),
    private val onSoftLimit: ((BudgetReport) -> Unit)? = null,
) {
    private val reports = ConcurrentHashMap<String, BudgetReport>()
    private val activeTrackers = ConcurrentHashMap<String, TokenTracker>()
    private val _events = MutableSharedFlow<BudgetEvent>(extraBufferCapacity = 64)

    /** SharedFlow of budget events for reactive consumers. */
    val events: SharedFlow<BudgetEvent> = _events.asSharedFlow()

    /**
     * Execute [block] within a budget-tracked scope.
     * Use [BudgetScope.trackCall] inside the block to record token usage.
     *
     * @throws BudgetHardLimitException if hard limit is exceeded
     */
    suspend fun <T> withBudget(
        agentId: String,
        budget: AgentBudget = defaultBudget,
        block: suspend BudgetScope.() -> T,
    ): T {
        val tracker = TokenTracker(agentId, budget)
        val scope = BudgetScope(tracker, pricing, onSoftLimit, _events)
        val startTime = System.currentTimeMillis()

        // Register tracker as in-flight before the block executes
        activeTrackers[agentId] = tracker

        return try {
            coroutineScope {
                scope.block()
            }
        } catch (e: BudgetHardLimitException) {
            throw e
        } finally {
            // Remove from active trackers first so the agent never appears both running and completed
            activeTrackers.remove(agentId)
            val durationMs = System.currentTimeMillis() - startTime
            reports[agentId] = scope.buildReport(durationMs)
        }
    }

    /** Get the budget report for a completed agent run. */
    fun getReport(agentId: String): BudgetReport? = reports[agentId]

    /**
     * Returns a snapshot of all tracked agents — both completed and currently in-flight.
     *
     * Completed agents appear with [BudgetSnapshot.running] == `false`.
     * In-flight agents appear with [BudgetSnapshot.running] == `true`, with a
     * point-in-time [BudgetReport] built from their current token counters.
     *
     * The returned map is an immutable copy; mutations to internal state after this
     * call returns are not reflected in the snapshot.
     */
    fun getAllReports(): Map<String, BudgetSnapshot> {
        val result = mutableMapOf<String, BudgetSnapshot>()

        // Add all completed reports as not-running
        reports.forEach { (agentId, report) ->
            result[agentId] = BudgetSnapshot(report = report, running = false)
        }

        // Overlay in-flight trackers as running (live snapshot of current counters)
        activeTrackers.forEach { (agentId, tracker) ->
            val liveReport = BudgetReport(
                agentId = agentId,
                model = tracker.model,
                promptTokens = tracker.promptTokens,
                completionTokens = tracker.completionTokens,
                totalTokens = tracker.totalTokens,
                estimatedCostUsd = pricing.estimateCost(
                    tracker.model,
                    tracker.promptTokens,
                    tracker.completionTokens,
                ),
                softLimitBreachCount = 0,
                hardLimitBreached = tracker.isAboveHardLimit(),
                durationMs = 0,
                percentUsed = tracker.percentUsed(),
            )
            result[agentId] = BudgetSnapshot(report = liveReport, running = true)
        }

        return result.toMap()
    }

    /**
     * Returns the number of agents currently executing inside a [withBudget] block.
     */
    fun getActiveTrackerCount(): Int = activeTrackers.size

    /**
     * Returns the total number of soft limit breaches across all completed agent runs.
     */
    fun getTotalSoftBreaches(): Int = reports.values.sumOf { it.softLimitBreachCount }

    /**
     * Returns the number of completed agent runs where the hard limit was breached.
     */
    fun getTotalHardBreaches(): Int = reports.values.count { it.hardLimitBreached }

    /**
     * Returns the model identifier for the given agent, or `null` if the agent is unknown.
     *
     * Checks active in-flight trackers first, then completed reports.
     *
     * @param agentId the agent identifier
     */
    fun modelOf(agentId: String): String? =
        activeTrackers[agentId]?.model ?: reports[agentId]?.model
}
