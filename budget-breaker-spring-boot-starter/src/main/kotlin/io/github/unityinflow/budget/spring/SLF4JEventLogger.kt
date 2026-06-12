package io.github.unityinflow.budget.spring

import io.github.unityinflow.budget.BudgetCircuitBreaker
import io.github.unityinflow.budget.BudgetEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.context.SmartLifecycle
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Subscribes to the [BudgetCircuitBreaker] events [kotlinx.coroutines.flow.SharedFlow] and
 * logs soft-limit breaches at WARN via SLF4J.
 *
 * ## Lifecycle
 *
 * Implements [SmartLifecycle]:
 * - [start] sets running = true and launches the Flow collector coroutine
 * - [stop] sets running = false and cancels the [CoroutineScope]
 * - [isRunning] reflects the current lifecycle state
 *
 * The [CoroutineScope] uses [SupervisorJob] + [Dispatchers.Default] to manage scope
 * lifecycle. Resilience to per-event handler failures is provided by the try/catch inside
 * the collect lambda, which logs at WARN and continues to the next event. [CancellationException]
 * is always re-thrown inside the catch to preserve clean shutdown when [stop] cancels the scope.
 *
 * **Scope management:** scope is fully managed by [SmartLifecycle] — never uses the
 * global coroutine scope — so there is no resource leak on Spring context shutdown.
 *
 * ## Logging
 *
 * Only [BudgetEvent.SoftLimitReached] events are logged — at WARN level — to alert
 * operators that an agent is approaching its token budget.
 * [BudgetEvent.HardLimitExceeded] and [BudgetEvent.CallTracked] are intentional
 * no-ops here (D-03); the metrics collector handles them separately.
 *
 * This class has no @Component annotation — it is registered as a @Bean in the
 * auto-configuration class. Do not add @Component here.
 *
 * @param breaker The [BudgetCircuitBreaker] whose [BudgetCircuitBreaker.events] Flow
 *   this logger subscribes to.
 */
class SLF4JEventLogger(
    private val breaker: BudgetCircuitBreaker,
) : SmartLifecycle {

    private val log = LoggerFactory.getLogger(SLF4JEventLogger::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val running = AtomicBoolean(false)

    /**
     * Starts the coroutine that collects [BudgetEvent]s and logs soft-limit breaches.
     *
     * Spring calls this near the end of context refresh, after all beans are ready.
     */
    override fun start() {
        running.set(true)
        scope.launch {
            breaker.events.collect { event ->
                try {
                    when (event) {
                        is BudgetEvent.SoftLimitReached -> log.warn(
                            "Agent '{}' reached soft budget limit: {}/{} tokens ({}%)",
                            event.agentId,
                            event.tokensUsed,
                            event.budgetTokens,
                            event.percentUsed,
                        )
                        is BudgetEvent.HardLimitExceeded -> {}
                        is BudgetEvent.CallTracked -> {}
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    log.warn("budget-breaker event logger handler failed for event {}", event, e)
                }
            }
        }
    }

    /**
     * Cancels the coroutine scope, stopping all event collection.
     *
     * Called by Spring during context shutdown — ensures no background coroutines outlive
     * the Spring context.
     */
    override fun stop() {
        running.set(false)
        scope.cancel()
    }

    override fun isRunning(): Boolean = running.get()
}
