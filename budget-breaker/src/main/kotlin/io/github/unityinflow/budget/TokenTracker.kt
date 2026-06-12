package io.github.unityinflow.budget

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Thread-safe token counter with soft/hard limit checks.
 * Uses AtomicLong for lock-free concurrent access.
 */
class TokenTracker(
    val agentId: String,
    private val budget: AgentBudget,
) {
    /** The model identifier from the budget configuration. */
    val model: String get() = budget.model

    /** The configured hard limit in tokens. */
    val hardLimitTokens: Long get() = budget.hardLimitTokens

    /** The configured soft limit in tokens. */
    val softLimitTokens: Long get() = budget.softLimitTokens

    private val _promptTokens = AtomicLong(0)
    private val _completionTokens = AtomicLong(0)
    private val _softLimitBreachCount = AtomicInteger(0)

    val promptTokens: Long get() = _promptTokens.get()
    val completionTokens: Long get() = _completionTokens.get()
    val totalTokens: Long get() = _promptTokens.get() + _completionTokens.get()

    /**
     * Number of soft limit breaches recorded for this run.
     *
     * Lives on the tracker (not [BudgetScope]) so live snapshots of in-flight agents
     * can report the true count while the run is still executing.
     */
    val softLimitBreachCount: Int get() = _softLimitBreachCount.get()

    /** Record token usage from an LLM call. */
    fun add(promptTokens: Long, completionTokens: Long) {
        _promptTokens.addAndGet(promptTokens)
        _completionTokens.addAndGet(completionTokens)
    }

    /** Record a soft limit breach. Called by [BudgetScope] when the soft limit first trips. */
    internal fun recordSoftLimitBreach() {
        _softLimitBreachCount.incrementAndGet()
    }

    /** Check if total tokens exceed the soft limit. */
    fun isAboveSoftLimit(): Boolean = totalTokens >= budget.softLimitTokens

    /** Check if total tokens exceed the hard limit. */
    fun isAboveHardLimit(): Boolean = totalTokens >= budget.hardLimitTokens

    /** Current usage as a percentage of the hard limit. */
    fun percentUsed(): Double = (totalTokens.toDouble() / budget.hardLimitTokens) * 100.0
}
