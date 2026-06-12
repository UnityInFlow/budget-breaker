package io.github.unityinflow.budget.spring

import io.github.unityinflow.budget.BudgetCircuitBreaker
import io.github.unityinflow.budget.BudgetEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.context.SmartLifecycle
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Subscribes to the [BudgetCircuitBreaker] events [kotlinx.coroutines.flow.SharedFlow] and
 * logs soft-limit breaches at WARN via SLF4J.
 *
 * ## Lifecycle
 *
 * Implements [SmartLifecycle]:
 * - [start] sets running = true and launches a fresh Flow collector [Job]
 * - [stop] sets running = false and cancels only the collection [Job] — the long-lived
 *   parent [CoroutineScope] is never cancelled, so a subsequent [start] (permitted by the
 *   [SmartLifecycle] contract, e.g. a JMX-triggered restart) launches a working collector
 * - [isRunning] reflects the current lifecycle state
 *
 * The [CoroutineScope] uses [SupervisorJob] + [Dispatchers.Default] to manage scope
 * lifecycle. Resilience to per-event handler failures is provided by the try/catch inside
 * the collect lambda, which logs at WARN and continues to the next event. [CancellationException]
 * is always re-thrown inside the catch to preserve clean shutdown when [stop] cancels the
 * collection job.
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
    private val collectJob = AtomicReference<Job?>(null)

    /**
     * Starts the coroutine that collects [BudgetEvent]s and logs soft-limit breaches.
     *
     * Spring calls this near the end of context refresh, after all beans are ready.
     * A fresh collection [Job] is launched per call, so a stop/start cycle (permitted by
     * the [SmartLifecycle] contract) resumes event collection instead of launching a dead
     * coroutine on a cancelled scope.
     */
    override fun start() {
        running.set(true)
        val job = scope.launch {
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
        collectJob.getAndSet(job)?.cancel()
    }

    /**
     * Cancels the collection job, stopping all event collection.
     *
     * Called by Spring during context shutdown — ensures no background coroutines outlive
     * the Spring context. Only the collection [Job] is cancelled — the parent
     * [SupervisorJob] stays alive so a subsequent [start] can resume collection.
     */
    override fun stop() {
        running.set(false)
        collectJob.getAndSet(null)?.cancel()
    }

    override fun isRunning(): Boolean = running.get()
}
