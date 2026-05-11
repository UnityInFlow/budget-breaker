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
 */
class BudgetCircuitBreaker(
    private val defaultBudget: AgentBudget = AgentBudget(),
    private val pricing: ModelPricing = ModelPricing(),
    private val onSoftLimit: ((BudgetReport) -> Unit)? = null,
) {
    private val reports = ConcurrentHashMap<String, BudgetReport>()
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

        return try {
            coroutineScope {
                scope.block()
            }
        } catch (e: BudgetHardLimitException) {
            throw e
        } finally {
            val durationMs = System.currentTimeMillis() - startTime
            reports[agentId] = scope.buildReport(durationMs)
        }
    }

    /** Get the budget report for a completed agent run. */
    fun getReport(agentId: String): BudgetReport? = reports[agentId]
}
