package dev.unityinflow.budget

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
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
}
