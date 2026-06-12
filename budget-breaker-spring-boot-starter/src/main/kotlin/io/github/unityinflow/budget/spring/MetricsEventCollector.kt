package io.github.unityinflow.budget.spring

import io.github.unityinflow.budget.BudgetCircuitBreaker
import io.github.unityinflow.budget.BudgetEvent
import io.github.unityinflow.budget.ModelPricing
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.MeterBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.springframework.context.SmartLifecycle
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Subscribes to the [BudgetCircuitBreaker] events [kotlinx.coroutines.flow.SharedFlow] and
 * registers Micrometer meters with OTel GenAI semconv naming recognized by token-dashboard.
 *
 * ## Meter families registered
 *
 * | Meter | Type | Tags | OTel semconv |
 * |---|---|---|---|
 * | `gen_ai.client.token.usage.input`  | Counter | agent, model | D-09 |
 * | `gen_ai.client.token.usage.output` | Counter | agent, model | D-09 |
 * | `budget.breaker.cost.usd`          | Counter | agent, model | D-11 |
 * | `budget.breaker.breach`            | Counter | agent, type=soft&#124;hard | D-09 |
 * | `budget.breaker.usage.ratio`       | Gauge   | agent | D-09 |
 *
 * **Naming contract:** `gen_ai.client.token.usage.input` and `.output` use an underscore
 * inside the `gen_ai` prefix and dots elsewhere. These are the exact strings that
 * `06-token-dashboard/OtlpMetricMapper.kt` matches on — do not rename.
 *
 * ## Lifecycle
 *
 * Implements [MeterBinder] + [SmartLifecycle]:
 * - [bindTo] stores the registry reference (no collection started here)
 * - [start] sets running = true and launches the Flow collector coroutine
 * - [stop] sets running = false and cancels the [CoroutineScope]
 *
 * The [CoroutineScope] uses [SupervisorJob] + [Dispatchers.Default] so that a single
 * event-handling failure does not cancel the entire collection loop.
 *
 * **Scope management:** scope is fully managed by [SmartLifecycle] — never uses the
 * global coroutine scope — so there is no resource leak on Spring context shutdown
 * (T-02-05 mitigation).
 *
 * This class has no @Component annotation — it is registered as a @Bean in the
 * auto-configuration class (plan 03). Do not add @Component here.
 *
 * @param breaker The [BudgetCircuitBreaker] whose [BudgetCircuitBreaker.events] Flow
 *   this collector subscribes to.
 * @param pricing The [ModelPricing] instance shared with the breaker, so application.yml
 *   price overrides are honoured when computing `budget.breaker.cost.usd` (D-11).
 */
class MetricsEventCollector(
    private val breaker: BudgetCircuitBreaker,
    private val pricing: ModelPricing,
) : MeterBinder, SmartLifecycle {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val running = AtomicBoolean(false)
    private val registryRef = AtomicReference<MeterRegistry?>(null)

    /**
     * Per-agent AtomicReference<Double> holders for the usage ratio gauge.
     *
     * Gauges must hold a strong reference to their measured object; the ConcurrentHashMap
     * acts as that strong reference holder so the gauge is never garbage-collected.
     */
    private val ratioHolders = ConcurrentHashMap<String, AtomicReference<Double>>()

    /**
     * Stores the [MeterRegistry] for use by the Flow collector.
     *
     * Called by Spring Boot's Micrometer auto-config once the registry is fully configured.
     * Does NOT start collecting — that happens in [start].
     */
    override fun bindTo(registry: MeterRegistry) {
        registryRef.set(registry)
    }

    /**
     * Starts the coroutine that collects [BudgetEvent]s and updates meters.
     *
     * Spring calls this near the end of context refresh, after all beans are ready.
     */
    override fun start() {
        running.set(true)
        scope.launch {
            breaker.events.collect { event ->
                val registry = registryRef.get() ?: return@collect
                when (event) {
                    is BudgetEvent.CallTracked -> handleCallTracked(event, registry)
                    is BudgetEvent.SoftLimitReached -> handleSoftLimitReached(event, registry)
                    is BudgetEvent.HardLimitExceeded -> handleHardLimitExceeded(event, registry)
                }
            }
        }
    }

    /**
     * Cancels the coroutine scope, stopping all event collection.
     *
     * Called by Spring during context shutdown — ensures no background coroutines outlive
     * the Spring context (T-02-05 mitigation).
     */
    override fun stop() {
        running.set(false)
        scope.cancel()
    }

    override fun isRunning(): Boolean = running.get()

    // ---- event handlers ----

    private fun handleCallTracked(event: BudgetEvent.CallTracked, registry: MeterRegistry) {
        Counter.builder("gen_ai.client.token.usage.input")
            .description("Input tokens consumed by LLM calls")
            .tags("agent", event.agentId, "model", event.model)
            .register(registry)
            .increment(event.promptTokens.toDouble())

        Counter.builder("gen_ai.client.token.usage.output")
            .description("Output tokens consumed by LLM calls")
            .tags("agent", event.agentId, "model", event.model)
            .register(registry)
            .increment(event.completionTokens.toDouble())

        Counter.builder("budget.breaker.cost.usd")
            .description("Estimated cost in USD for LLM calls")
            .tags("agent", event.agentId, "model", event.model)
            .register(registry)
            .increment(pricing.estimateCost(event.model, event.promptTokens, event.completionTokens))

        updateUsageRatioGauge(event.agentId, registry)
    }

    private fun handleSoftLimitReached(event: BudgetEvent.SoftLimitReached, registry: MeterRegistry) {
        Counter.builder("budget.breaker.breach")
            .description("Number of budget limit breaches")
            .tags("agent", event.agentId, "type", "soft")
            .register(registry)
            .increment()
    }

    private fun handleHardLimitExceeded(event: BudgetEvent.HardLimitExceeded, registry: MeterRegistry) {
        Counter.builder("budget.breaker.breach")
            .description("Number of budget limit breaches")
            .tags("agent", event.agentId, "type", "hard")
            .register(registry)
            .increment()
    }

    /**
     * Ensures a [Gauge] for `budget.breaker.usage.ratio` exists for the given agent and updates it.
     *
     * The gauge is backed by an [AtomicReference<Double>] stored in [ratioHolders] to prevent
     * garbage collection of the measured object (Micrometer gauge strong-reference requirement).
     *
     * The ratio value is read from the live snapshot in [BudgetCircuitBreaker.getAllReports]
     * so it reflects the agent's current usage relative to its hard limit.
     */
    private fun updateUsageRatioGauge(agentId: String, registry: MeterRegistry) {
        val holder = ratioHolders.computeIfAbsent(agentId) { id ->
            val ref = AtomicReference(0.0)
            Gauge.builder("budget.breaker.usage.ratio", ref) { it.get() }
                .description("Token usage as fraction of hard limit (0.0–1.0+)")
                .tags("agent", id)
                .register(registry)
            ref
        }

        // Update ratio from the live snapshot; default to 0.0 if agent not found
        val snapshot = breaker.getAllReports()[agentId]
        holder.set(snapshot?.report?.percentUsed?.div(100.0) ?: 0.0)
    }
}
