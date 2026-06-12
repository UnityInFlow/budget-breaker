# Phase 2: Spring Boot Starter + Release — Pattern Map

**Mapped:** 2026-06-12
**Files analyzed:** 12 new/modified files
**Analogs found:** 10 / 12

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `budget-breaker-spring-boot-starter/build.gradle.kts` | config | — | `budget-breaker/build.gradle.kts` | exact |
| `budget-breaker-spring-boot-starter/src/main/kotlin/.../BudgetBreakerAutoConfiguration.kt` | config | request-response | `buildSrc/src/main/kotlin/budget-breaker.publishing.gradle.kts` (structure only) | partial |
| `budget-breaker-spring-boot-starter/src/main/kotlin/.../BudgetBreakerProperties.kt` | config | — | `budget-breaker/src/main/kotlin/.../AgentBudget.kt` | role-match |
| `budget-breaker-spring-boot-starter/src/main/kotlin/.../BudgetEndpoint.kt` | provider | request-response | `budget-breaker/src/main/kotlin/.../BudgetCircuitBreaker.kt` (data source) | partial |
| `budget-breaker-spring-boot-starter/src/main/kotlin/.../BudgetBreakerHealthIndicator.kt` | provider | request-response | no analog in codebase | none |
| `budget-breaker-spring-boot-starter/src/main/kotlin/.../MetricsEventCollector.kt` | service | event-driven | `06-token-dashboard/src/main/kotlin/.../OtlpMetricMapper.kt` (metric naming) | partial |
| `budget-breaker-spring-boot-starter/src/main/kotlin/.../SLF4JEventLogger.kt` | service | event-driven | `budget-breaker/src/test/kotlin/.../BudgetCircuitBreakerTest.kt` (Flow.collect pattern) | partial |
| `budget-breaker-spring-boot-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` | config | — | no analog | none |
| `budget-breaker-spring-boot-starter/src/test/kotlin/.../BudgetBreakerAutoConfigurationTest.kt` | test | — | `budget-breaker/src/test/kotlin/.../BudgetCircuitBreakerTest.kt` | role-match |
| `budget-breaker-spring-boot-starter/src/test/kotlin/.../BudgetStarterIntegrationTest.kt` | test | — | `budget-breaker/src/test/kotlin/.../BudgetCircuitBreakerTest.kt` | role-match |
| `budget-breaker/src/main/kotlin/.../BudgetCircuitBreaker.kt` (modify) | service | event-driven | itself — additive D-08 enhancement | exact |
| `build.gradle.kts` (modify) | config | — | itself — nmcp aggregation + version bump | exact |

---

## Pattern Assignments

### `budget-breaker-spring-boot-starter/build.gradle.kts` (config)

**Analog:** `budget-breaker/build.gradle.kts`

**Core build structure pattern** (lines 1–35):
```kotlin
plugins {
    kotlin("jvm")
    id("budget-breaker.publishing")
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

publishing {
    publications {
        named<MavenPublication>("maven") {
            pom {
                name.set("budget-breaker")
                description.set("...")
            }
        }
    }
}
```

**Adaptation for starter:** Replace `implementation(coroutines)` with `api(project(":budget-breaker"))` + `compileOnly(spring-boot-autoconfigure:3.5.3)` + `compileOnly(spring-boot-actuator-autoconfigure:3.5.3)` + `compileOnly(micrometer-core:1.15.0)`. Add `testImplementation` copies of the compileOnly deps (Pitfall 1 from RESEARCH.md). Update `name.set` and `description.set` to the starter artifact identity.

---

### `BudgetBreakerAutoConfiguration.kt` (config, request-response)

**No direct codebase analog** — this is the first Spring auto-config class in the project. Use RESEARCH.md Pattern 1 as the template.

**Package and import pattern** (mirror `BudgetCircuitBreaker.kt` lines 1–7 for package convention):
```kotlin
package io.github.unityinflow.budget.spring

import io.github.unityinflow.budget.AgentBudget
import io.github.unityinflow.budget.BudgetCircuitBreaker
import io.github.unityinflow.budget.ModelPricing
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
```

**Core factory pattern** — copy `BudgetCircuitBreaker` constructor call from `BudgetCircuitBreaker.kt` lines 15–19:
```kotlin
class BudgetCircuitBreaker(
    private val defaultBudget: AgentBudget = AgentBudget(),
    private val pricing: ModelPricing = ModelPricing(),
    private val onSoftLimit: ((BudgetReport) -> Unit)? = null,
)
```
The auto-config `@Bean` method maps `BudgetBreakerProperties` fields to these constructor parameters directly.

**Structural rule:** Every `@Bean` in this file corresponds to one of: `BudgetCircuitBreaker` (with `@ConditionalOnMissingBean`), `SLF4JEventLogger`, `MetricsEventCollector`, `BudgetEndpoint`, `BudgetBreakerHealthIndicator`. No `@Component` scanning. No `@ComponentScan`.

---

### `BudgetBreakerProperties.kt` (config)

**Analog:** `budget-breaker/src/main/kotlin/io/github/unityinflow/budget/AgentBudget.kt`

**Validation pattern in `init` block** (lines 12–19 of AgentBudget.kt):
```kotlin
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

**Adaptation:** Copy the `init` block validation verbatim but use property names with hyphens in message strings to match YAML path (`budget-breaker.soft-limit-tokens must be <= hard-limit-tokens`). Add `@ConfigurationProperties("budget-breaker")` annotation. Add a nested `data class ModelPriceProperties(val inputPerMillion: Double = 0.0, val outputPerMillion: Double = 0.0)` as an inner data class. Defaults mirror `AgentBudget` defaults (`"claude-sonnet-4-6"`, `100_000`, `80_000`).

---

### `BudgetEndpoint.kt` (provider, request-response)

**Analog (data source):** `budget-breaker/src/main/kotlin/io/github/unityinflow/budget/BudgetCircuitBreaker.kt`

**Data access pattern** (lines 54–55 of BudgetCircuitBreaker.kt):
```kotlin
/** Get the budget report for a completed agent run. */
fun getReport(agentId: String): BudgetReport? = reports[agentId]
```

The endpoint wraps the D-08 `getAllReports()` and `getReport(agentId)` methods. Return types are plain data from `ConcurrentHashMap` snapshots — never suspend. No blocking inside `@ReadOperation`.

**KDoc pattern** (copy from BudgetCircuitBreaker.kt lines 9–14):
```kotlin
/**
 * Actuator endpoint exposing budget reports for all tracked agents.
 *
 * Enable via: management.endpoints.web.exposure.include=budget
 * GET /actuator/budget            — all agents summary map
 * GET /actuator/budget/{agentId}  — full BudgetReport for one agent
 */
```

---

### `BudgetBreakerHealthIndicator.kt` (provider, request-response)

**No codebase analog.** Use RESEARCH.md Pattern 4 directly. Key constraint from D-07: always return `Health.up()` — never `Health.down()` regardless of breach state. Detail keys must match exactly: `agentsTracked`, `softBreaches`, `hardBreaches` (from CONTEXT.md specifics section).

---

### `MetricsEventCollector.kt` (service, event-driven)

**Analog (metric naming):** `06-token-dashboard/src/main/kotlin/io/github/unityinflow/tokendashboard/otlp/OtlpMetricMapper.kt`

**Metric name verification** (lines 96–98 of OtlpMetricMapper.kt — the consumer side):
```kotlin
when (metric.name) {
    "llm.token.input", "gen_ai.client.token.usage.input", "llm.input_tokens" ->
        group.inputTokens += value
    "llm.token.output", "gen_ai.client.token.usage.output", "llm.output_tokens" ->
        group.outputTokens += value
```
The token-dashboard recognizes `gen_ai.client.token.usage.input` and `gen_ai.client.token.usage.output` — use these exact names (dots, not underscores). This confirms RESEARCH.md open question #2: use dots throughout.

**Analog (Flow.collect pattern):** `budget-breaker/src/test/kotlin/io/github/unityinflow/budget/BudgetCircuitBreakerTest.kt`

**Flow subscription pattern** (lines 62–81 of BudgetCircuitBreakerTest.kt):
```kotlin
val collected = mutableListOf<BudgetEvent>()
val eventJob = launch {
    breaker.events.collect { collected.add(it) }
}
// Yield to let the collector subscribe before emitting
kotlinx.coroutines.yield()
```
In production `MetricsEventCollector`, replace `launch` with `scope.launch` where `scope` is `CoroutineScope(SupervisorJob() + Dispatchers.Default)` managed by `SmartLifecycle`.

**Analog (AtomicLong mutable state pattern):** `budget-breaker/src/main/kotlin/io/github/unityinflow/budget/TokenTracker.kt`

**Thread-safe mutable state without `var`** (lines 22–27 of TokenTracker.kt):
```kotlin
private val _promptTokens = AtomicLong(0)
private val _completionTokens = AtomicLong(0)

val promptTokens: Long get() = _promptTokens.get()
val completionTokens: Long get() = _completionTokens.get()
val totalTokens: Long get() = _promptTokens.get() + _completionTokens.get()
```
Copy this `AtomicLong`/`AtomicDouble` pattern for the gauge's mutable ratio value. Never use `var` — wrap mutation in `AtomicDouble` (for gauge) or let Micrometer `Counter.increment()` manage its own state.

**Model tag resolution:** `TokenTracker` exposes `val model: String get() = budget.model` (line 14). After D-08 enhancement adds `activeTrackers: ConcurrentHashMap<String, TokenTracker>` to `BudgetCircuitBreaker`, look up model via `activeTrackers[event.agentId]?.model ?: "unknown"` at event collection time. This is option B from RESEARCH.md; option A (add `model` to `CallTracked`) is also valid — planner decides.

---

### `SLF4JEventLogger.kt` (service, event-driven)

**Analog (Flow.collect + coroutine scope):** same as `MetricsEventCollector` above.

**Sealed class match pattern** (copy exhaustive `when` from BudgetCircuitBreakerTest.kt style):
```kotlin
breaker.events.collect { event ->
    when (event) {
        is BudgetEvent.SoftLimitReached -> log.warn(...)
        is BudgetEvent.HardLimitExceeded -> { /* no-op per D-03 */ }
        is BudgetEvent.CallTracked -> { /* no-op */ }
    }
}
```
Exhaustive `when` is required — the CLAUDE.md constraint is "pattern match exhaustively — no catch-all `_`." The sealed class has three variants (`SoftLimitReached`, `HardLimitExceeded`, `CallTracked`) as of Phase 1.

---

### `org.springframework.boot.autoconfigure.AutoConfiguration.imports` (config)

**No codebase analog.** Single-line file containing the fully-qualified class name:
```
io.github.unityinflow.budget.spring.BudgetBreakerAutoConfiguration
```
Location: `budget-breaker-spring-boot-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

---

### `BudgetBreakerAutoConfigurationTest.kt` (test)

**Analog:** `budget-breaker/src/test/kotlin/io/github/unityinflow/budget/BudgetCircuitBreakerTest.kt`

**Test class structure pattern** (lines 1–18 of BudgetCircuitBreakerTest.kt):
```kotlin
package io.github.unityinflow.budget

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
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
```

**Adaptation for `ApplicationContextRunner` tests:** Replace `runTest {}` with synchronous lambda in `contextRunner.run { ctx -> ... }`. Use `assertThat(ctx).hasSingleBean(...)` from `spring-boot-test-autoconfigure`. Kotest matchers can still be used inside the lambda alongside AssertJ (`assertThat(ctx).hasFailed()`).

**Test naming convention** — copy backtick-style from existing tests:
```kotlin
@Test
fun `registers BudgetCircuitBreaker with defaults`() { ... }

@Test
fun `honors user-defined BudgetCircuitBreaker bean`() { ... }

@Test
fun `validates invalid property combination at startup`() { ... }
```

---

### `BudgetStarterIntegrationTest.kt` (test)

**Analog:** `budget-breaker/src/test/kotlin/io/github/unityinflow/budget/BudgetCircuitBreakerTest.kt`

**Concurrent agent test pattern** (lines 128–147 of BudgetCircuitBreakerTest.kt) shows how to assert on `BudgetCircuitBreaker` results after a `withBudget` call. Copy the pattern for the `@SpringBootTest` smoke test:
1. `@Autowired lateinit var breaker: BudgetCircuitBreaker`
2. Call `breaker.withBudget("test-agent") { trackCall(...) }` in a `runTest` or `runBlocking` block
3. Assert on `SimpleMeterRegistry` that `gen_ai.client.token.usage.input` counter has been incremented
4. Assert on `mockMvc.get("/actuator/budget")` that response contains `"test-agent"`

---

### `BudgetCircuitBreaker.kt` (modify — D-08 core enhancement)

**Analog:** itself.

**Existing `withBudget` try/finally pattern** (lines 32–51):
```kotlin
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
```

**Enhancement insertion points:**
- Add `private val activeTrackers = ConcurrentHashMap<String, TokenTracker>()` immediately after `private val reports` (line 20)
- Add `activeTrackers[agentId] = tracker` on the line after `val scope = BudgetScope(...)` (after line 38)
- Add `activeTrackers.remove(agentId)` as the first line of the `finally` block (before line 48)
- Add `getAllReports(): Map<String, BudgetSnapshot>` and supporting `BudgetSnapshot` data class as new public API

**Existing data exposure pattern** (line 54):
```kotlin
fun getReport(agentId: String): BudgetReport? = reports[agentId]
```
`getAllReports()` follows the same read-only snapshot pattern — return an immutable copy (`toMap()`) not the live map.

---

### `build.gradle.kts` (modify — root, nmcp aggregation + version bump)

**Analog:** itself.

**Current nmcp dependencies block** (lines 23–34):
```kotlin
nmcpAggregation {
    centralPortal {
        username = providers.environmentVariable("SONATYPE_USERNAME")
        password = providers.environmentVariable("SONATYPE_PASSWORD")
        publishingType = "USER_MANAGED"
        publicationName = "budget-breaker-${project.version}"
    }
}

dependencies {
    nmcpAggregation(project(":budget-breaker"))
}
```

**Two edits only:**
1. Line 12: change `version = "0.0.1"` → `version = "0.1.0"`
2. After line 33: add `nmcpAggregation(project(":budget-breaker-spring-boot-starter"))`

---

## Shared Patterns

### Coroutine async — no raw threads
**Source:** `budget-breaker/src/main/kotlin/.../BudgetCircuitBreaker.kt` and `TokenTracker.kt`
**Apply to:** `MetricsEventCollector.kt`, `SLF4JEventLogger.kt`

All async work uses `CoroutineScope` + `scope.launch { ... }`. `TokenTracker` demonstrates `AtomicLong` for thread-safe counters without coroutine overhead. `MetricsEventCollector` and `SLF4JEventLogger` use `CoroutineScope(SupervisorJob() + Dispatchers.Default)` managed via `SmartLifecycle`.

### Sealed class exhaustive matching
**Source:** `budget-breaker/src/main/kotlin/.../BudgetEvent.kt` (definition) + `BudgetCircuitBreakerTest.kt` (usage pattern)
**Apply to:** `MetricsEventCollector.kt`, `SLF4JEventLogger.kt`

`BudgetEvent` has three variants: `SoftLimitReached`, `HardLimitExceeded`, `CallTracked`. All `when` expressions over `BudgetEvent` must be exhaustive — no `else` branch. If a new variant is added in future, the compiler catches unhandled cases.

### `val`-only data classes with `init` validation
**Source:** `budget-breaker/src/main/kotlin/.../AgentBudget.kt` (lines 10–20)
**Apply to:** `BudgetBreakerProperties.kt`

All properties are `val`. Validation in `init { require(...) }` with descriptive messages including the YAML property path. No `@field:Valid` or Spring Validator — `require()` throws `IllegalArgumentException` which Spring Boot wraps as startup failure.

### KDoc on all public APIs
**Source:** `budget-breaker/src/main/kotlin/.../BudgetCircuitBreaker.kt` (lines 9–14, 27–31, 53)
**Apply to:** All new public classes and methods in the starter module

Pattern: class-level KDoc explains purpose + usage contract; method-level KDoc covers `@throws` and non-obvious semantics. Internal implementation details (`private`, `internal`) do not need KDoc.

### Maven publishing pom override
**Source:** `budget-breaker/build.gradle.kts` (lines 22–35) + `buildSrc/src/main/kotlin/budget-breaker.publishing.gradle.kts` (lines 46–81)
**Apply to:** `budget-breaker-spring-boot-starter/build.gradle.kts`

Convention plugin creates the `"maven"` publication. Each module only needs to `named<MavenPublication>("maven") { pom { name.set(...); description.set(...) } }`. Signing, scm, developer, license metadata are all inherited from the convention plugin — do not repeat them in the module build file.

### Test style
**Source:** `budget-breaker/src/test/kotlin/.../BudgetCircuitBreakerTest.kt`
**Apply to:** Both test files in the starter

- JUnit 5 `@Test` + Kotest `shouldBe` / `shouldNotBe` matchers
- Backtick function names: `fun \`registers BudgetCircuitBreaker with defaults\`()`
- `runTest {}` for coroutine tests (from `kotlinx-coroutines-test`)
- Private test-local `@Configuration` classes for `ApplicationContextRunner` user-config scenarios

---

## No Analog Found

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| `BudgetBreakerHealthIndicator.kt` | provider | request-response | No Spring `HealthIndicator` implementations exist in this codebase. Use RESEARCH.md Pattern 4. Key constraint: always `Health.up()`, detail keys = `agentsTracked`, `softBreaches`, `hardBreaches`. |
| `org.springframework.boot.autoconfigure.AutoConfiguration.imports` | config | — | No Spring auto-config registration files exist. Single-line file, canonical location: `src/main/resources/META-INF/spring/`. |

---

## Metadata

**Analog search scope:**
- `05-budget-breaker/budget-breaker/src/` — core library (8 source files, 4 test files)
- `05-budget-breaker/buildSrc/src/` — convention plugins
- `05-budget-breaker/build.gradle.kts` — root build
- `06-token-dashboard/src/main/kotlin/.../otlp/` — metric naming contract

**Files scanned:** 16 source files across 2 projects
**Pattern extraction date:** 2026-06-12
