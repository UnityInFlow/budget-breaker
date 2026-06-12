package io.github.unityinflow.budget

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean

class BudgetCircuitBreakerTest {

    private val budget = AgentBudget(
        model = "claude-sonnet-4-6",
        hardLimitTokens = 1000,
        softLimitTokens = 800,
    )

    @Test
    fun `completes normally within budget`() = runTest {
        val breaker = BudgetCircuitBreaker(budget)

        val result = breaker.withBudget("agent-1") {
            trackCall(promptTokens = 100, completionTokens = 50)
            "done"
        }

        result shouldBe "done"
    }

    @Test
    fun `throws on hard limit breach`() = runTest {
        val breaker = BudgetCircuitBreaker(budget)

        shouldThrow<BudgetHardLimitException> {
            breaker.withBudget("agent-1") {
                trackCall(promptTokens = 600, completionTokens = 500)
            }
        }
    }

    @Test
    fun `fires callback on soft limit`() = runTest {
        val callbackFired = AtomicBoolean(false)
        val breaker = BudgetCircuitBreaker(
            budget,
            onSoftLimit = { callbackFired.set(true) },
        )

        breaker.withBudget("agent-1") {
            trackCall(promptTokens = 500, completionTokens = 350)
        }

        callbackFired.get() shouldBe true
    }

    @Test
    fun `emits event on soft limit`() = runTest {
        val breaker = BudgetCircuitBreaker(budget)
        val collected = mutableListOf<BudgetEvent>()

        val eventJob = launch {
            breaker.events.collect { collected.add(it) }
        }

        // Yield to let the collector subscribe before emitting
        kotlinx.coroutines.yield()

        breaker.withBudget("agent-1") {
            trackCall(promptTokens = 500, completionTokens = 350)
        }

        // Yield to let collector process buffered events
        kotlinx.coroutines.yield()
        eventJob.cancel()

        val softEvent = collected.filterIsInstance<BudgetEvent.SoftLimitReached>()
        softEvent.size shouldBe 1
        softEvent.first().agentId shouldBe "agent-1"
    }

    @Test
    fun `generates report after completion`() = runTest {
        val breaker = BudgetCircuitBreaker(budget)

        breaker.withBudget("agent-1") {
            trackCall(promptTokens = 200, completionTokens = 100)
        }

        val report = breaker.getReport("agent-1")
        report shouldNotBe null
        report?.totalTokens shouldBe 300
        report?.hardLimitBreached shouldBe false
    }

    @Test
    fun `generates report even after hard limit exception`() = runTest {
        val breaker = BudgetCircuitBreaker(budget)

        try {
            breaker.withBudget("agent-1") {
                trackCall(promptTokens = 600, completionTokens = 500)
            }
        } catch (_: BudgetHardLimitException) {
            // expected
        }

        val report = breaker.getReport("agent-1")
        report shouldNotBe null
        report?.hardLimitBreached shouldBe true
    }

    @Test
    fun `report includes cost estimation`() = runTest {
        val breaker = BudgetCircuitBreaker(budget)

        breaker.withBudget("agent-1") {
            trackCall(promptTokens = 200, completionTokens = 100)
        }

        val report = breaker.getReport("agent-1")
        report shouldNotBe null
        report!!.estimatedCostUsd shouldBeGreaterThan 0.0 // safe: asserted non-null above
    }

    @Test
    fun `supports multiple agents concurrently`() = runTest {
        val breaker = BudgetCircuitBreaker(budget)

        val job1 = launch {
            breaker.withBudget("agent-1") {
                trackCall(promptTokens = 100, completionTokens = 50)
            }
        }
        val job2 = launch {
            breaker.withBudget("agent-2") {
                trackCall(promptTokens = 200, completionTokens = 100)
            }
        }

        job1.join()
        job2.join()

        breaker.getReport("agent-1")?.totalTokens shouldBe 150
        breaker.getReport("agent-2")?.totalTokens shouldBe 300
    }

    @Test
    fun `rejects a concurrent withBudget run with a duplicate agentId`() = runTest {
        val breaker = BudgetCircuitBreaker(budget)
        val gate = CompletableDeferred<Unit>()

        val firstRun = launch {
            breaker.withBudget("dup-agent") {
                trackCall(promptTokens = 10, completionTokens = 5)
                gate.await()
            }
        }

        // Let the first run register its in-flight tracker
        while (breaker.getActiveTrackerCount() == 0) yield()

        shouldThrow<IllegalArgumentException> {
            breaker.withBudget("dup-agent") { }
        }

        gate.complete(Unit)
        firstRun.join()

        // The first run's tracking state must be intact — not corrupted by the rejected run
        breaker.getReport("dup-agent")?.totalTokens shouldBe 15
        breaker.getActiveTrackerCount() shouldBe 0
    }

    // ---- Task 2: snapshot and aggregate-breach API tests ----

    @Test
    fun `getAllReports returns completed agent with running false`() = runTest {
        val breaker = BudgetCircuitBreaker(budget)

        breaker.withBudget("snap-agent") {
            trackCall(promptTokens = 100, completionTokens = 50)
        }

        val snapshots = breaker.getAllReports()
        snapshots["snap-agent"] shouldNotBe null
        snapshots["snap-agent"]!!.running shouldBe false
        snapshots["snap-agent"]!!.report.agentId shouldBe "snap-agent"
    }

    @Test
    fun `getActiveTrackerCount returns zero when no agents in flight`() = runTest {
        val breaker = BudgetCircuitBreaker(budget)

        breaker.withBudget("agent-x") {
            trackCall(promptTokens = 50, completionTokens = 25)
        }

        breaker.getActiveTrackerCount() shouldBe 0
    }

    @Test
    fun `getTotalSoftBreaches sums soft breach counts across completed reports`() = runTest {
        val softBudget = AgentBudget(
            model = "claude-sonnet-4-6",
            hardLimitTokens = 1000,
            softLimitTokens = 100,
        )
        val breaker = BudgetCircuitBreaker(softBudget)

        breaker.withBudget("breach-agent-1") {
            trackCall(promptTokens = 200, completionTokens = 50)
        }
        breaker.withBudget("breach-agent-2") {
            trackCall(promptTokens = 200, completionTokens = 50)
        }

        // Each agent triggered a soft breach
        breaker.getTotalSoftBreaches() shouldBe 2
    }

    @Test
    fun `getTotalHardBreaches counts agents where hard limit was breached`() = runTest {
        val breaker = BudgetCircuitBreaker(budget)

        try {
            breaker.withBudget("hard-agent") {
                trackCall(promptTokens = 600, completionTokens = 500)
            }
        } catch (_: BudgetHardLimitException) { /* expected */ }

        breaker.getTotalHardBreaches() shouldBe 1
    }

    @Test
    fun `modelOf returns model from completed report`() = runTest {
        val breaker = BudgetCircuitBreaker(budget)

        breaker.withBudget("model-agent") {
            trackCall(promptTokens = 50, completionTokens = 25)
        }

        breaker.modelOf("model-agent") shouldBe "claude-sonnet-4-6"
        breaker.modelOf("non-existent") shouldBe null
    }

    @Test
    fun `getAllReports returns immutable snapshot copy`() = runTest {
        val breaker = BudgetCircuitBreaker(budget)

        breaker.withBudget("immutable-agent") {
            trackCall(promptTokens = 50, completionTokens = 25)
        }

        val snap1 = breaker.getAllReports()
        breaker.withBudget("immutable-agent-2") {
            trackCall(promptTokens = 100, completionTokens = 50)
        }
        val snap2 = breaker.getAllReports()

        // snap1 was captured before agent-2 completed — it should not reflect agent-2
        snap1.containsKey("immutable-agent-2") shouldBe false
        snap2.containsKey("immutable-agent-2") shouldBe true
    }

    @Test
    fun `CallTracked event carries model from budget`() = runTest {
        val breaker = BudgetCircuitBreaker(budget)
        val collected = mutableListOf<BudgetEvent>()

        val eventJob = launch {
            breaker.events.collect { collected.add(it) }
        }

        // Yield to let the collector subscribe before emitting
        kotlinx.coroutines.yield()

        breaker.withBudget("agent-model-test") {
            trackCall(promptTokens = 100, completionTokens = 50)
        }

        // Yield to let collector process buffered events
        kotlinx.coroutines.yield()
        eventJob.cancel()

        val callTrackedEvents = collected.filterIsInstance<BudgetEvent.CallTracked>()
        callTrackedEvents.size shouldBe 1
        callTrackedEvents.first().model shouldBe "claude-sonnet-4-6"
        callTrackedEvents.first().agentId shouldBe "agent-model-test"
    }
}
