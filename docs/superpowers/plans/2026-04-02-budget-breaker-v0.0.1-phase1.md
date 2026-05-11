# budget-breaker v0.0.1 Phase 1: Core Library Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the core `budget-breaker` Kotlin library — a coroutine-aware circuit breaker for AI agent token budgets with soft/hard limits, cost estimation, and budget reports. No Spring dependency.

**Architecture:** A `BudgetCircuitBreaker` wraps user code in a coroutine scope. A `TokenTracker` uses two AtomicLongs (prompt + completion) for lock-free token counting. When soft limit is reached, a callback fires + a SharedFlow emits. When hard limit is reached, the coroutine scope is cancelled via `BudgetHardLimitException`. `ModelPricing` provides cost estimation with hardcoded defaults + override support.

**Tech Stack:** Kotlin 2.0+, JVM 21, Gradle (Kotlin DSL), kotlinx-coroutines, JUnit 5, Kotest matchers

**Spec:** `05-budget-breaker.md`
**Context:** `.planning/phases/phase-1/01-CONTEXT.md`

**Important:** No `Co-Authored-By` in any commits. No `var`. No `!!`. No `Thread.sleep`.

---

## File Structure (Phase 1 — core module only)

```
budget-breaker/
├── .github/
│   ├── workflows/ci.yml
│   └── PULL_REQUEST_TEMPLATE.md
├── docs/
│   └── adr/
│       └── ADR-001-tech-stack.md
├── budget-breaker/                    ← core library module
│   ├── build.gradle.kts
│   └── src/
│       ├── main/kotlin/dev/unityinflow/budget/
│       │   ├── AgentBudget.kt         ← budget configuration data class
│       │   ├── TokenTracker.kt        ← AtomicLong pair, limit checks
│       │   ├── BudgetCircuitBreaker.kt ← coroutine supervisor, withBudget()
│       │   ├── BudgetScope.kt         ← trackCall(), budget scope DSL
│       │   ├── BudgetException.kt     ← sealed class hierarchy
│       │   ├── BudgetReport.kt        ← report data class
│       │   ├── BudgetEvent.kt         ← SharedFlow events
│       │   └── ModelPricing.kt        ← cost estimation with defaults + override
│       └── test/kotlin/dev/unityinflow/budget/
│           ├── TokenTrackerTest.kt
│           ├── BudgetCircuitBreakerTest.kt
│           ├── ModelPricingTest.kt
│           └── BudgetReportTest.kt
├── budget-breaker-spring-boot-starter/ ← Phase 2 (empty placeholder)
│   └── build.gradle.kts
├── build.gradle.kts                   ← root build file
├── settings.gradle.kts                ← includes both modules
├── gradle.properties
├── README.md
└── LICENSE
```

---

## Task 1: Create GitHub repo and Gradle multi-module scaffold

> **PR 1: Foundation**

- [ ] **Step 1: Create repo**

```bash
gh repo create UnityInFlow/budget-breaker --public --description "Kotlin coroutine budget circuit breaker for AI agents — soft/hard token limits with cost estimation"
```

- [ ] **Step 2: Initialize Gradle project**

Work from: `/Users/jirihermann/Documents/workspace-1-ideas/unity-in-flow-ai/05-budget-breaker/`

Create `settings.gradle.kts`:
```kotlin
rootProject.name = "budget-breaker-root"

include("budget-breaker")
include("budget-breaker-spring-boot-starter")
```

Create `gradle.properties`:
```properties
kotlin.code.style=official
org.gradle.jvmargs=-Xmx1g
group=io.github.unityinflow
version=0.0.1-SNAPSHOT
```

Create root `build.gradle.kts`:
```kotlin
plugins {
    kotlin("jvm") version "2.1.0" apply false
}

allprojects {
    group = "io.github.unityinflow"
    version = "0.0.1-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}
```

Create `budget-breaker/build.gradle.kts`:
```kotlin
plugins {
    kotlin("jvm")
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")

    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
    testImplementation("io.kotest:kotest-assertions-core:5.9.1")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}
```

Create empty `budget-breaker-spring-boot-starter/build.gradle.kts` (Phase 2 placeholder):
```kotlin
plugins {
    kotlin("jvm")
}

// Phase 2: Spring Boot starter implementation

kotlin {
    jvmToolchain(21)
}
```

- [ ] **Step 3: Create package directories**

```bash
mkdir -p budget-breaker/src/main/kotlin/dev/unityinflow/budget
mkdir -p budget-breaker/src/test/kotlin/dev/unityinflow/budget
mkdir -p budget-breaker-spring-boot-starter/src/main/kotlin/dev/unityinflow/budget/spring
```

- [ ] **Step 4: Install Gradle wrapper**

```bash
gradle wrapper --gradle-version 8.12
```

- [ ] **Step 5: Create .gitignore**

```
.gradle/
build/
*.class
.idea/
*.iml
.DS_Store
```

- [ ] **Step 6: Create CI workflow**

```yaml
name: CI

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

jobs:
  build-and-test:
    runs-on: [arc-runner-unityinflow]
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '21'
      - uses: gradle/actions/setup-gradle@v4
      - run: ./gradlew build
      - run: ./gradlew test
```

- [ ] **Step 7: Create PR template, README skeleton, LICENSE, ADR-001**

ADR-001 documents: Kotlin + coroutines, Gradle multi-module, AtomicLong for counters, caller-driven tracking, no Spring in core.

- [ ] **Step 8: Verify build**

```bash
./gradlew build
```

- [ ] **Step 9: Commit and push**

```bash
git init
git add .
git commit -m "feat: scaffold Gradle multi-module project with Kotlin, coroutines"
git remote add origin git@github.com:UnityInFlow/budget-breaker.git
git push -u origin main
gh api repos/UnityInFlow/budget-breaker/milestones -f title="v0.0.1"
```

---

## Task 2: Implement AgentBudget + BudgetException

> **PR 1 (continued)**

- [ ] **Step 1: Create AgentBudget**

`budget-breaker/src/main/kotlin/dev/unityinflow/budget/AgentBudget.kt`:
```kotlin
package io.github.unityinflow.budget

/**
 * Configuration for an agent's token budget.
 *
 * @param model The LLM model identifier (used for cost estimation)
 * @param hardLimitTokens Hard limit — exceeding this cancels the coroutine scope
 * @param softLimitTokens Soft limit — triggers callback and Flow event when reached
 */
data class AgentBudget(
    val model: String = "claude-sonnet-4-6",
    val hardLimitTokens: Long = 100_000,
    val softLimitTokens: Long = 80_000,
) {
    init {
        require(hardLimitTokens > 0) { "hardLimitTokens must be positive" }
        require(softLimitTokens > 0) { "softLimitTokens must be positive" }
        require(softLimitTokens <= hardLimitTokens) { "softLimitTokens must be <= hardLimitTokens" }
    }
}
```

- [ ] **Step 2: Create BudgetException**

`budget-breaker/src/main/kotlin/dev/unityinflow/budget/BudgetException.kt`:
```kotlin
package io.github.unityinflow.budget

import kotlin.math.roundToInt

/** Sealed hierarchy for budget-related exceptions. */
sealed class BudgetException(message: String) : Exception(message)

/** Thrown when an agent's soft token limit is reached. Non-fatal — execution continues. */
class BudgetSoftLimitException(
    val agentId: String,
    val tokensUsed: Long,
    val budgetTokens: Long,
    val percentUsed: Double,
) : BudgetException(
    "Agent '$agentId' reached soft limit: ${percentUsed.roundToInt()}% used ($tokensUsed/$budgetTokens tokens)"
)

/** Thrown when an agent's hard token limit is exceeded. Fatal — coroutine scope is cancelled. */
class BudgetHardLimitException(
    val agentId: String,
    val tokensUsed: Long,
    val budgetTokens: Long,
    val estimatedCostUsd: Double,
) : BudgetException(
    "Agent '$agentId' exceeded hard limit ($tokensUsed/$budgetTokens tokens). Estimated cost: \$${String.format("%.4f", estimatedCostUsd)}"
)
```

- [ ] **Step 3: Create BudgetEvent**

`budget-breaker/src/main/kotlin/dev/unityinflow/budget/BudgetEvent.kt`:
```kotlin
package io.github.unityinflow.budget

/** Events emitted by the budget system via SharedFlow. */
sealed class BudgetEvent {
    abstract val agentId: String
    abstract val tokensUsed: Long

    data class SoftLimitReached(
        override val agentId: String,
        override val tokensUsed: Long,
        val budgetTokens: Long,
        val percentUsed: Double,
    ) : BudgetEvent()

    data class HardLimitExceeded(
        override val agentId: String,
        override val tokensUsed: Long,
        val budgetTokens: Long,
        val estimatedCostUsd: Double,
    ) : BudgetEvent()

    data class CallTracked(
        override val agentId: String,
        override val tokensUsed: Long,
        val promptTokens: Long,
        val completionTokens: Long,
    ) : BudgetEvent()
}
```

- [ ] **Step 4: Commit**

```bash
git add budget-breaker/src/main/kotlin/dev/unityinflow/budget/AgentBudget.kt
git add budget-breaker/src/main/kotlin/dev/unityinflow/budget/BudgetException.kt
git add budget-breaker/src/main/kotlin/dev/unityinflow/budget/BudgetEvent.kt
git commit -m "feat: add AgentBudget, BudgetException sealed hierarchy, BudgetEvent"
```

---

## Task 3: Implement TokenTracker

> **PR 2: Core Engine**

- [ ] **Step 1: Create feature branch**

```bash
git checkout -b feat/core-engine
```

- [ ] **Step 2: Write failing tests**

`budget-breaker/src/test/kotlin/dev/unityinflow/budget/TokenTrackerTest.kt`:
```kotlin
package io.github.unityinflow.budget

import io.kotest.matchers.shouldBe
import io.kotest.matchers.longs.shouldBeGreaterThan
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.launch
import org.junit.jupiter.api.Test

class TokenTrackerTest {

    private val budget = AgentBudget(
        model = "claude-sonnet-4-6",
        hardLimitTokens = 1000,
        softLimitTokens = 800,
    )

    @Test
    fun `tracks prompt and completion tokens separately`() {
        val tracker = TokenTracker("test-agent", budget)
        tracker.add(promptTokens = 100, completionTokens = 50)

        tracker.promptTokens shouldBe 100
        tracker.completionTokens shouldBe 50
        tracker.totalTokens shouldBe 150
    }

    @Test
    fun `accumulates tokens across multiple calls`() {
        val tracker = TokenTracker("test-agent", budget)
        tracker.add(promptTokens = 100, completionTokens = 50)
        tracker.add(promptTokens = 200, completionTokens = 100)

        tracker.totalTokens shouldBe 450
    }

    @Test
    fun `detects soft limit breach`() {
        val tracker = TokenTracker("test-agent", budget)
        tracker.add(promptTokens = 500, completionTokens = 350)

        tracker.isAboveSoftLimit() shouldBe true
        tracker.isAboveHardLimit() shouldBe false
    }

    @Test
    fun `detects hard limit breach`() {
        val tracker = TokenTracker("test-agent", budget)
        tracker.add(promptTokens = 600, completionTokens = 500)

        tracker.isAboveHardLimit() shouldBe true
    }

    @Test
    fun `reports percent used`() {
        val tracker = TokenTracker("test-agent", budget)
        tracker.add(promptTokens = 250, completionTokens = 250)

        tracker.percentUsed() shouldBe 50.0
    }

    @Test
    fun `is thread-safe under concurrent access`() = runTest {
        val tracker = TokenTracker("test-agent", AgentBudget(hardLimitTokens = 1_000_000, softLimitTokens = 800_000))

        val jobs = (1..100).map {
            launch {
                repeat(100) {
                    tracker.add(promptTokens = 1, completionTokens = 1)
                }
            }
        }
        jobs.forEach { it.join() }

        tracker.totalTokens shouldBe 20_000
    }

    @Test
    fun `starts at zero`() {
        val tracker = TokenTracker("test-agent", budget)

        tracker.promptTokens shouldBe 0
        tracker.completionTokens shouldBe 0
        tracker.totalTokens shouldBe 0
        tracker.isAboveSoftLimit() shouldBe false
        tracker.isAboveHardLimit() shouldBe false
    }
}
```

- [ ] **Step 3: Implement TokenTracker**

`budget-breaker/src/main/kotlin/dev/unityinflow/budget/TokenTracker.kt`:
```kotlin
package io.github.unityinflow.budget

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

    val promptTokens: Long get() = _promptTokens.get()
    val completionTokens: Long get() = _completionTokens.get()
    val totalTokens: Long get() = _promptTokens.get() + _completionTokens.get()

    /** Record token usage from an LLM call. */
    fun add(promptTokens: Long, completionTokens: Long) {
        _promptTokens.addAndGet(promptTokens)
        _completionTokens.addAndGet(completionTokens)
    }

    /** Check if total tokens exceed the soft limit. */
    fun isAboveSoftLimit(): Boolean = totalTokens >= budget.softLimitTokens

    /** Check if total tokens exceed the hard limit. */
    fun isAboveHardLimit(): Boolean = totalTokens >= budget.hardLimitTokens

    /** Current usage as a percentage of the hard limit. */
    fun percentUsed(): Double = (totalTokens.toDouble() / budget.hardLimitTokens) * 100.0
}
```

- [ ] **Step 4: Run tests, commit**

```bash
./gradlew :budget-breaker:test
git add budget-breaker/src/
git commit -m "feat: add TokenTracker with AtomicLong pair for thread-safe tracking"
```

---

## Task 4: Implement ModelPricing

> **PR 2 (continued)**

- [ ] **Step 1: Write tests**

`budget-breaker/src/test/kotlin/dev/unityinflow/budget/ModelPricingTest.kt`:
```kotlin
package io.github.unityinflow.budget

import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ModelPricingTest {

    @Test
    fun `estimates cost for Claude Sonnet`() {
        val cost = ModelPricing.estimateCost("claude-sonnet-4-6", promptTokens = 1_000_000, completionTokens = 0)
        cost shouldBe 3.0 // $3 per million input tokens
    }

    @Test
    fun `estimates cost for Claude Opus output`() {
        val cost = ModelPricing.estimateCost("claude-opus-4-6", promptTokens = 0, completionTokens = 1_000_000)
        cost shouldBe 75.0 // $75 per million output tokens
    }

    @Test
    fun `estimates combined input + output cost`() {
        val cost = ModelPricing.estimateCost("claude-sonnet-4-6", promptTokens = 500_000, completionTokens = 100_000)
        // 0.5M * $3/M + 0.1M * $15/M = $1.5 + $1.5 = $3.0
        cost shouldBe 3.0
    }

    @Test
    fun `returns zero for unknown model`() {
        val cost = ModelPricing.estimateCost("unknown-model", promptTokens = 1000, completionTokens = 1000)
        cost shouldBe 0.0
    }

    @Test
    fun `supports custom price overrides`() {
        val pricing = ModelPricing(
            overrides = mapOf(
                "my-model" to ModelPricing.PriceConfig(inputPerMillion = 10.0, outputPerMillion = 50.0)
            )
        )
        val cost = pricing.estimateCost("my-model", promptTokens = 1_000_000, completionTokens = 0)
        cost shouldBe 10.0
    }

    @Test
    fun `override takes precedence over default`() {
        val pricing = ModelPricing(
            overrides = mapOf(
                "claude-sonnet-4-6" to ModelPricing.PriceConfig(inputPerMillion = 99.0, outputPerMillion = 99.0)
            )
        )
        val cost = pricing.estimateCost("claude-sonnet-4-6", promptTokens = 1_000_000, completionTokens = 0)
        cost shouldBe 99.0
    }
}
```

- [ ] **Step 2: Implement ModelPricing**

`budget-breaker/src/main/kotlin/dev/unityinflow/budget/ModelPricing.kt`:
```kotlin
package io.github.unityinflow.budget

/**
 * Estimates LLM API costs based on token usage.
 * Has hardcoded defaults for popular models, supports custom overrides.
 */
class ModelPricing(
    private val overrides: Map<String, PriceConfig> = emptyMap(),
) {
    data class PriceConfig(
        val inputPerMillion: Double,
        val outputPerMillion: Double,
    )

    /** Estimate cost in USD for the given token usage. */
    fun estimateCost(model: String, promptTokens: Long, completionTokens: Long): Double {
        val config = overrides[model] ?: DEFAULTS[model] ?: return 0.0
        val inputCost = (promptTokens.toDouble() / 1_000_000) * config.inputPerMillion
        val outputCost = (completionTokens.toDouble() / 1_000_000) * config.outputPerMillion
        return inputCost + outputCost
    }

    companion object {
        private val DEFAULTS = mapOf(
            // Claude (April 2026)
            "claude-opus-4-6" to PriceConfig(15.0, 75.0),
            "claude-sonnet-4-6" to PriceConfig(3.0, 15.0),
            "claude-haiku-4-5" to PriceConfig(0.80, 4.0),
            // OpenAI
            "gpt-4o" to PriceConfig(2.50, 10.0),
            "gpt-4o-mini" to PriceConfig(0.15, 0.60),
            // Google
            "gemini-2.5-pro" to PriceConfig(1.25, 10.0),
            "gemini-2.5-flash" to PriceConfig(0.15, 0.60),
            // Local (free)
            "ollama" to PriceConfig(0.0, 0.0),
        )

        /** Convenience: estimate cost using default pricing (no overrides). */
        fun estimateCost(model: String, promptTokens: Long, completionTokens: Long): Double =
            ModelPricing().estimateCost(model, promptTokens, completionTokens)
    }
}
```

- [ ] **Step 3: Run tests, commit**

```bash
./gradlew :budget-breaker:test
git add budget-breaker/src/main/kotlin/dev/unityinflow/budget/ModelPricing.kt
git add budget-breaker/src/test/kotlin/dev/unityinflow/budget/ModelPricingTest.kt
git commit -m "feat: add ModelPricing with hardcoded defaults and custom overrides"
```

---

## Task 5: Implement BudgetReport

> **PR 2 (continued)**

- [ ] **Step 1: Implement BudgetReport**

`budget-breaker/src/main/kotlin/dev/unityinflow/budget/BudgetReport.kt`:
```kotlin
package io.github.unityinflow.budget

/**
 * Summary of an agent's budget usage after a run completes.
 */
data class BudgetReport(
    val agentId: String,
    val model: String,
    val promptTokens: Long,
    val completionTokens: Long,
    val totalTokens: Long,
    val estimatedCostUsd: Double,
    val softLimitBreachCount: Int,
    val hardLimitBreached: Boolean,
    val durationMs: Long,
    val percentUsed: Double,
)
```

- [ ] **Step 2: Write test, commit**

```kotlin
package io.github.unityinflow.budget

import io.kotest.matchers.shouldBe
import io.kotest.matchers.doubles.shouldBeGreaterThan
import org.junit.jupiter.api.Test

class BudgetReportTest {

    @Test
    fun `creates report with all fields`() {
        val report = BudgetReport(
            agentId = "test-agent",
            model = "claude-sonnet-4-6",
            promptTokens = 500,
            completionTokens = 200,
            totalTokens = 700,
            estimatedCostUsd = 0.0045,
            softLimitBreachCount = 0,
            hardLimitBreached = false,
            durationMs = 1500,
            percentUsed = 70.0,
        )

        report.agentId shouldBe "test-agent"
        report.totalTokens shouldBe 700
        report.estimatedCostUsd shouldBeGreaterThan 0.0
        report.hardLimitBreached shouldBe false
    }

    @Test
    fun `totalTokens equals prompt + completion`() {
        val report = BudgetReport(
            agentId = "test",
            model = "claude-sonnet-4-6",
            promptTokens = 300,
            completionTokens = 200,
            totalTokens = 500,
            estimatedCostUsd = 0.0,
            softLimitBreachCount = 0,
            hardLimitBreached = false,
            durationMs = 0,
            percentUsed = 50.0,
        )

        report.totalTokens shouldBe (report.promptTokens + report.completionTokens)
    }
}
```

```bash
git add budget-breaker/src/
git commit -m "feat: add BudgetReport data class"
```

---

## Task 6: Implement BudgetScope + BudgetCircuitBreaker

> **PR 2 (continued)**

- [ ] **Step 1: Implement BudgetScope**

`budget-breaker/src/main/kotlin/dev/unityinflow/budget/BudgetScope.kt`:
```kotlin
package io.github.unityinflow.budget

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

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
```

- [ ] **Step 2: Implement BudgetCircuitBreaker**

`budget-breaker/src/main/kotlin/dev/unityinflow/budget/BudgetCircuitBreaker.kt`:
```kotlin
package io.github.unityinflow.budget

import kotlinx.coroutines.CancellationException
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
```

- [ ] **Step 3: Write circuit breaker tests**

`budget-breaker/src/test/kotlin/dev/unityinflow/budget/BudgetCircuitBreakerTest.kt`:
```kotlin
package io.github.unityinflow.budget

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.doubles.shouldBeGreaterThan
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

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
        var callbackFired = false
        val breaker = BudgetCircuitBreaker(
            budget,
            onSoftLimit = { callbackFired = true },
        )

        breaker.withBudget("agent-1") {
            trackCall(promptTokens = 500, completionTokens = 350)
        }

        callbackFired shouldBe true
    }

    @Test
    fun `emits event on soft limit`() = runTest {
        val breaker = BudgetCircuitBreaker(budget)

        val eventJob = launch {
            val event = breaker.events.first { it is BudgetEvent.SoftLimitReached }
            event.agentId shouldBe "agent-1"
        }

        breaker.withBudget("agent-1") {
            trackCall(promptTokens = 500, completionTokens = 350)
        }

        eventJob.join()
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
        report?.estimatedCostUsd shouldBeGreaterThan 0.0
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
```

- [ ] **Step 4: Run all tests, commit, push PR**

```bash
./gradlew :budget-breaker:test
git add budget-breaker/src/
git commit -m "feat: add BudgetScope and BudgetCircuitBreaker with coroutine supervisor"
git push -u origin feat/core-engine
gh pr create --title "feat: core engine — TokenTracker, ModelPricing, BudgetCircuitBreaker" --body "PR 2 of budget-breaker v0.0.1 Phase 1.

## What
- TokenTracker: AtomicLong pair (prompt + completion), thread-safe
- ModelPricing: hardcoded defaults + custom overrides, Claude/GPT/Gemini
- BudgetReport: post-run summary data class
- BudgetScope: trackCall() DSL with soft/hard limit checks
- BudgetCircuitBreaker: coroutine supervisor, withBudget() API
- Callback + SharedFlow for soft limit events
- 20+ tests covering cancellation, limits, concurrency, reports"
```

---

## Task 7: Release prep

> **PR 3: Release**

- [ ] **Step 1: Merge to main, create release branch**

```bash
git checkout main && git pull
git merge feat/core-engine --no-edit
git checkout -b feat/release-prep
```

- [ ] **Step 2: Write full README**

Problem statement, installation (Gradle + Maven), usage examples (basic, with callback, with Flow), API reference, model pricing table.

- [ ] **Step 3: Create CONTRIBUTING.md**

- [ ] **Step 4: Full verification**

```bash
./gradlew build
./gradlew test
```

- [ ] **Step 5: Commit, push, create PR, merge**

- [ ] **Step 6: Tag and release**

```bash
git tag v0.0.1
git push origin v0.0.1
gh release create v0.0.1 --title "v0.0.1" --notes "..."
```

- [ ] **Step 7: Create v0.1.0 issues + update STATE.md**

Issues:
- SDK interceptors (Anthropic, OpenAI, LangChain4j)
- Spring Boot starter (Phase 2)
- Kotlin Flow event streaming to token-dashboard
- Kafka opt-in event bus
- Maven Central publishing
