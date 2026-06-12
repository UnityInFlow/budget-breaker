---
phase: 02-spring-boot-starter-release-weeks-8-9
reviewed: 2026-06-12T00:00:00Z
depth: standard
files_reviewed: 17
files_reviewed_list:
  - .github/workflows/release.yml
  - budget-breaker-spring-boot-starter/build.gradle.kts
  - budget-breaker-spring-boot-starter/src/main/kotlin/io/github/unityinflow/budget/spring/BudgetBreakerAutoConfiguration.kt
  - budget-breaker-spring-boot-starter/src/main/kotlin/io/github/unityinflow/budget/spring/BudgetBreakerHealthIndicator.kt
  - budget-breaker-spring-boot-starter/src/main/kotlin/io/github/unityinflow/budget/spring/BudgetBreakerProperties.kt
  - budget-breaker-spring-boot-starter/src/main/kotlin/io/github/unityinflow/budget/spring/BudgetEndpoint.kt
  - budget-breaker-spring-boot-starter/src/main/kotlin/io/github/unityinflow/budget/spring/MetricsEventCollector.kt
  - budget-breaker-spring-boot-starter/src/main/kotlin/io/github/unityinflow/budget/spring/SLF4JEventLogger.kt
  - budget-breaker-spring-boot-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
  - budget-breaker-spring-boot-starter/src/test/kotlin/io/github/unityinflow/budget/spring/BudgetBreakerAutoConfigurationTest.kt
  - budget-breaker-spring-boot-starter/src/test/kotlin/io/github/unityinflow/budget/spring/BudgetBreakerPropertiesTest.kt
  - budget-breaker-spring-boot-starter/src/test/kotlin/io/github/unityinflow/budget/spring/BudgetStarterIntegrationTest.kt
  - budget-breaker/src/main/kotlin/io/github/unityinflow/budget/BudgetCircuitBreaker.kt
  - budget-breaker/src/main/kotlin/io/github/unityinflow/budget/BudgetEvent.kt
  - budget-breaker/src/main/kotlin/io/github/unityinflow/budget/BudgetScope.kt
  - budget-breaker/src/main/kotlin/io/github/unityinflow/budget/BudgetSnapshot.kt
  - budget-breaker/src/test/kotlin/io/github/unityinflow/budget/BudgetCircuitBreakerTest.kt
findings:
  critical: 2
  warning: 8
  info: 8
  total: 18
status: issues_found
---

# Phase 2: Code Review Report

**Reviewed:** 2026-06-12
**Depth:** standard
**Files Reviewed:** 17
**Status:** issues_found

## Summary

Reviewed the Spring Boot starter module, the new core observability APIs (`getAllReports`, `BudgetSnapshot`, aggregate breach counters, `CallTracked.model`), and the release workflow. Supporting core files (`TokenTracker`, `ModelPricing`, `AgentBudget`, `BudgetReport`, `BudgetException`, root/buildSrc Gradle files) were read for cross-reference.

The code is well-documented and the bean-override and property-validation paths are tested. However, two Critical defects exist: (1) the auto-configuration will crash any consuming Spring Boot application that does not have Actuator and Micrometer on its runtime classpath — the starter declares them `compileOnly` but provides no `@ConditionalOnClass` guards; (2) both event collectors die permanently and silently on the first exception thrown inside their `collect` block, while the KDoc incorrectly claims `SupervisorJob` protects against this. Several lifecycle, concurrency, and test-reliability warnings follow.

## Critical Issues

### CR-01: Starter crashes consumer apps without Actuator/Micrometer on the runtime classpath

**File:** `budget-breaker-spring-boot-starter/src/main/kotlin/io/github/unityinflow/budget/spring/BudgetBreakerAutoConfiguration.kt:51-131` (and `budget-breaker-spring-boot-starter/build.gradle.kts:19-21`)

**Issue:** `spring-boot-actuator-autoconfigure` and `micrometer-core` are `compileOnly` (build.gradle.kts:19-21), meaning consumers do NOT get them transitively. Yet `BudgetBreakerAutoConfiguration` has bean methods whose signatures reference Actuator/Micrometer types directly: `budgetEndpoint()` returns `BudgetEndpoint` (annotated `@Endpoint`), `budgetHealthIndicator()` returns `BudgetBreakerHealthIndicator` (implements `HealthIndicator`), and `budgetMetricsCollector()` returns `MetricsEventCollector` (implements `MeterBinder`). There are no `@ConditionalOnClass` guards anywhere. A plain Spring Boot app (web app without actuator — extremely common) that adds this starter will fail at context refresh with `NoClassDefFoundError`/`ClassNotFoundException` when Spring resolves the configuration class's bean methods. The starter only works for the subset of apps that already depend on both actuator AND micrometer. The auto-configuration test suite never exercises a classpath without these jars, so this is invisible in CI.

**Fix:** Split the actuator/micrometer beans into nested configuration classes guarded by `@ConditionalOnClass`, so the outer class never forces those types to load:

```kotlin
@AutoConfiguration
@EnableConfigurationProperties(BudgetBreakerProperties::class)
class BudgetBreakerAutoConfiguration {
    @Bean fun budgetPricing(...) = ...
    @Bean @ConditionalOnMissingBean fun budgetCircuitBreaker(...) = ...
    @Bean fun budgetEventLogger(...) = ...

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = ["org.springframework.boot.actuate.health.HealthIndicator"])
    class ActuatorConfiguration {
        @Bean fun budgetEndpoint(breaker: BudgetCircuitBreaker) = BudgetEndpoint(breaker)
        @Bean fun budgetHealthIndicator(breaker: BudgetCircuitBreaker) = BudgetBreakerHealthIndicator(breaker)
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = ["io.micrometer.core.instrument.MeterRegistry"])
    class MicrometerConfiguration {
        @Bean fun budgetMetricsCollector(breaker: BudgetCircuitBreaker, pricing: ModelPricing) =
            MetricsEventCollector(breaker, pricing)
    }
}
```

Add an `ApplicationContextRunner` test using `FilteredClassLoader(HealthIndicator::class.java, MeterRegistry::class.java)` to prove the context still starts without actuator/micrometer. (Alternative: promote actuator/micrometer to `api`/`implementation` deps — but conditional config is the established starter idiom and keeps the dependency footprint optional as the build comments intend.)

### CR-02: Event collector coroutines die permanently and silently on the first handler exception

**File:** `budget-breaker-spring-boot-starter/src/main/kotlin/io/github/unityinflow/budget/spring/MetricsEventCollector.kt:92-104` (same pattern in `SLF4JEventLogger.kt:57-74`)

**Issue:** `start()` launches a single coroutine that runs `breaker.events.collect { ... }`. Any exception thrown inside the `collect` lambda terminates the `collect` call and the entire coroutine — all subsequent events are silently lost for the lifetime of the application, while `isRunning()` continues to report `true`. The KDoc (lines 45-46) claims "The CoroutineScope uses SupervisorJob ... so that a single event-handling failure does not cancel the entire collection loop" — this is false: `SupervisorJob` only isolates failures *between sibling children*, and there is exactly one child here. The failure path is realistic: Micrometer meter registration throws `IllegalArgumentException` in several registries when a meter with the same name is registered with a different tag set (e.g., another library or a future version emits `gen_ai.client.token.usage.input` with different tags into a Prometheus registry), and `pricing.estimateCost` operates on unvalidated user-supplied price config. Result: all token/cost/breach metrics stop flowing with no error surfaced anywhere — for a budget-governance tool this is silent loss of the primary safety signal.

**Fix:** Catch and log per-event failures inside the loop so collection survives:

```kotlin
scope.launch {
    breaker.events.collect { event ->
        val registry = registryRef.get() ?: return@collect
        try {
            when (event) {
                is BudgetEvent.CallTracked -> handleCallTracked(event, registry)
                is BudgetEvent.SoftLimitReached -> handleSoftLimitReached(event, registry)
                is BudgetEvent.HardLimitExceeded -> handleHardLimitExceeded(event, registry)
            }
        } catch (e: Exception) {
            log.warn("budget-breaker metrics handler failed for event {}", event, e)
        }
    }
}
```

Apply the same guard in `SLF4JEventLogger`, and correct the misleading `SupervisorJob` KDoc in both classes.

## Warnings

### WR-01: `budgetPricing` bean lacks `@ConditionalOnMissingBean` — user override of `ModelPricing` breaks startup; user breaker override breaks D-11

**File:** `budget-breaker-spring-boot-starter/src/main/kotlin/io/github/unityinflow/budget/spring/BudgetBreakerAutoConfiguration.kt:62-68`

**Issue:** Only `budgetCircuitBreaker` is guarded with `@ConditionalOnMissingBean`. If a consumer defines their own `ModelPricing` bean, the context gets two `ModelPricing` beans and `budgetCircuitBreaker(properties, pricing)` / `budgetMetricsCollector(...)` fail with `NoUniqueBeanDefinitionException` at startup. Separately, when a user provides their own `BudgetCircuitBreaker` (the documented D-17 path), that breaker carries its own internal pricing, but `MetricsEventCollector` is still wired with the auto-config `budgetPricing` built from `budget-breaker.pricing.*` properties — so `budget.breaker.cost.usd` can diverge from the breaker's own cost reports, violating the D-11 invariant the KDoc promises.

**Fix:** Add `@ConditionalOnMissingBean` to `budgetPricing`, and document that users who override `BudgetCircuitBreaker` should also override `ModelPricing` to keep metrics costs consistent (or expose pricing from the breaker so the collector can reuse it).

### WR-02: Concurrent `withBudget` runs with the same agentId corrupt tracker and report state

**File:** `budget-breaker/src/main/kotlin/io/github/unityinflow/budget/BudgetCircuitBreaker.kt:50, 60-62`

**Issue:** `activeTrackers[agentId] = tracker` unconditionally overwrites. If two runs share an agentId concurrently: run B's tracker replaces run A's; when run A finishes, `activeTrackers.remove(agentId)` removes *run B's* tracker, so a live agent disappears from `getActiveTrackerCount()`, `getAllReports()` (running view), the health indicator, and the usage-ratio gauge, while `reports[agentId]` flip-flops between the two runs. Budget enforcement per run still works (each scope holds its own tracker), but every observability surface built in this phase reports wrong data under this collision, with no error or warning.

**Fix:** Either reject the collision (`require(activeTrackers.putIfAbsent(agentId, tracker) == null) { "agent '$agentId' is already running" }`) with a guarded remove (`activeTrackers.remove(agentId, tracker)`), or document single-flight-per-agentId as a hard contract and use `remove(agentId, tracker)` so a newer run is never evicted by an older one.

### WR-03: `SmartLifecycle` restart is broken — `start()` after `stop()` silently does nothing while `isRunning()` reports true

**File:** `budget-breaker-spring-boot-starter/src/main/kotlin/io/github/unityinflow/budget/spring/MetricsEventCollector.kt:65, 92-115`; `SLF4JEventLogger.kt:49, 57-87`

**Issue:** `scope` is created once at construction. `stop()` calls `scope.cancel()`, which cancels the scope's `Job` permanently. A subsequent `start()` (Spring lifecycle restart, e.g., via `LifecycleProcessor` or actuator `restart`) sets `running = true` and calls `scope.launch { ... }` — but launching on a cancelled scope creates an already-cancelled coroutine that never runs. The component then reports `isRunning() == true` while collecting nothing.

**Fix:** Create the `Job`/scope inside `start()` and cancel it in `stop()`:

```kotlin
private var job: kotlinx.coroutines.Job? = null  // guarded by lifecycle calls
override fun start() {
    running.set(true)
    job = CoroutineScope(SupervisorJob() + Dispatchers.Default).launch { ... }
}
override fun stop() {
    running.set(false)
    job?.cancel()
}
```

### WR-04: Subscription race — events emitted shortly after `start()` returns are silently dropped

**File:** `budget-breaker-spring-boot-starter/src/main/kotlin/io/github/unityinflow/budget/spring/MetricsEventCollector.kt:92-104`; `SLF4JEventLogger.kt:57-74`; root cause interacts with `budget-breaker/src/main/kotlin/io/github/unityinflow/budget/BudgetCircuitBreaker.kt:29`

**Issue:** `start()` returns as soon as `scope.launch` schedules the collector on `Dispatchers.Default`; actual subscription to the `SharedFlow` happens later on another thread. `MutableSharedFlow(extraBufferCapacity = 64)` has `replay = 0`, so any `BudgetEvent` emitted between context refresh completing and the collector subscribing is dropped without trace — e.g., an agent run triggered by an `ApplicationReadyEvent` listener can lose its first `CallTracked`/breach events from metrics and WARN logging. The integration test acknowledges this exact race by inserting `yield()` + `delay(50)` workarounds.

**Fix:** Block `start()` until subscription is active using `SharedFlow.subscriptionCount`:

```kotlin
override fun start() {
    running.set(true)
    scope.launch { breaker.events.collect { ... } }
    runBlocking { breaker.events.subscriptionCount.first { it > 0 } } // start() is a lifecycle thread, bounded wait
}
```

or give the events flow `replay` capacity in the core so late subscribers see recent events.

### WR-05: Slow event subscriber can suspend `trackCall` and stall agent execution (backpressure via SUSPEND overflow)

**File:** `budget-breaker/src/main/kotlin/io/github/unityinflow/budget/BudgetCircuitBreaker.kt:29`; emission sites `BudgetScope.kt:27, 47, 60`

**Issue:** `MutableSharedFlow(extraBufferCapacity = 64)` uses the default `BufferOverflow.SUSPEND`. Once any subscriber exists (the starter always wires two), a subscriber that falls 64 events behind causes `eventFlow.emit(...)` inside `trackCall` to suspend — meaning an observability consumer can block the agent's hot path indefinitely (e.g., if the metrics handler hangs or the dispatcher is starved). This contradicts the "zero overhead on happy path" acceptance criterion and couples agent liveness to observer health. Combined with CR-02 (a dead collector no longer drains but also unsubscribes only if the coroutine fully completes — a hung handler keeps the subscription alive), this is a plausible production stall.

**Fix:** Use `onBufferOverflow = BufferOverflow.DROP_OLDEST` (observability events are lossy-tolerable), or `tryEmit` with a dropped-event counter, and document the delivery guarantee.

### WR-06: `/actuator/budget/{agentId}` returns 404 for in-flight agents that the aggregate endpoint reports

**File:** `budget-breaker-spring-boot-starter/src/main/kotlin/io/github/unityinflow/budget/spring/BudgetEndpoint.kt:62`

**Issue:** `agentBudget` delegates to `breaker.getReport(agentId)`, which reads only the completed-reports map. A user who sees `agent-x` listed as `running: true` in `GET /actuator/budget` then gets a 404 from `GET /actuator/budget/agent-x`. The KDoc documents this, but the core already exposes the data needed to answer correctly — the inconsistency is gratuitous and will read as a bug to operators.

**Fix:** `fun agentBudget(@Selector agentId: String): BudgetReport? = breaker.getAllReports()[agentId]?.report` (or return the `BudgetSnapshot` so the `running` flag is included).

### WR-07: `ModelPriceProperties` accepts negative prices and silently defaults missing sides to 0.0

**File:** `budget-breaker-spring-boot-starter/src/main/kotlin/io/github/unityinflow/budget/spring/BudgetBreakerProperties.kt:61-64`

**Issue:** `BudgetBreakerProperties` validates the token limits but pricing entries are unvalidated. `inputPerMillion = -5.0` is accepted and produces negative cost in `budget.breaker.cost.usd` (a Micrometer `Counter.increment(negative)` — counters reject or corrupt on negative increments depending on registry, feeding CR-02). A user who sets only `input-per-million` silently gets `outputPerMillion = 0.0`, under-reporting cost with no warning — for a budget-control tool, silently wrong cost data defeats the product's purpose.

**Fix:**

```kotlin
data class ModelPriceProperties(
    val inputPerMillion: Double = 0.0,
    val outputPerMillion: Double = 0.0,
) {
    init {
        require(inputPerMillion >= 0) { "budget-breaker.pricing.*.input-per-million must be >= 0" }
        require(outputPerMillion >= 0) { "budget-breaker.pricing.*.output-per-million must be >= 0" }
    }
}
```

Consider making both fields required (no defaults) so partial configuration fails fast at binding time.

### WR-08: Integration test is order-dependent and timing-dependent (shared context, shared agentId, bare `delay(50)`)

**File:** `budget-breaker-spring-boot-starter/src/test/kotlin/io/github/unityinflow/budget/spring/BudgetStarterIntegrationTest.kt:69-111`

**Issue:** Both test methods share one cached Spring context (same breaker, same `SimpleMeterRegistry`, same started collector) and both run `withBudget("test-agent") { trackCall(1000, 500) }`. The first test asserts `count() == 1000.0` exactly. JUnit 5's default method order is deterministic but unspecified — if `budget endpoint reports the tracked agent after a call` executes first, the collector increments the counter to 1000 before the metrics test runs, and the metrics test then observes 2000.0 and fails. Additionally, `yield()` on `runBlocking`'s dispatcher does nothing for the collector running on `Dispatchers.Default`; correctness rests entirely on the bare `delay(50)`, which is a flake under loaded CI runners (the self-hosted Hetzner boxes run parallel jobs). This affects test reliability, not just style.

**Fix:** Use a unique agent ID per test (e.g., method-name based), and replace `yield()/delay(50)` with bounded polling:

```kotlin
withTimeout(5_000) {
    while (registry.find("gen_ai.client.token.usage.input").tag("agent", agentId).counter() == null) {
        delay(10)
    }
}
```

## Info

### IN-01: Dead catch-and-rethrow block

**File:** `budget-breaker/src/main/kotlin/io/github/unityinflow/budget/BudgetCircuitBreaker.kt:56-57`
**Issue:** `catch (e: BudgetHardLimitException) { throw e }` is a no-op — the exception would propagate identically without it.
**Fix:** Delete the catch block; the `finally` is sufficient.

### IN-02: Wall-clock duration measurement

**File:** `budget-breaker/src/main/kotlin/io/github/unityinflow/budget/BudgetCircuitBreaker.kt:47, 61`
**Issue:** `System.currentTimeMillis()` is not monotonic — NTP adjustments can produce negative or skewed `durationMs` in reports.
**Fix:** Use `kotlin.time.TimeSource.Monotonic.markNow()` (or `System.nanoTime()`).

### IN-03: `!!` without the project-mandated safety comment

**File:** `budget-breaker-spring-boot-starter/src/test/kotlin/io/github/unityinflow/budget/spring/BudgetStarterIntegrationTest.kt:110`; `budget-breaker/src/test/kotlin/io/github/unityinflow/budget/BudgetCircuitBreakerTest.kt:161-162`
**Issue:** Project rule (CLAUDE.md "Do Not") requires every `!!` to carry a comment explaining why it is safe; `BudgetCircuitBreakerTest.kt:124` follows the rule, these three usages do not.
**Fix:** Add `// safe: asserted non-null above` comments, or use `shouldNotBe null` + `?.` chains.

### IN-04: Release workflow: stale/misleading release notes and no tag-version consistency check

**File:** `.github/workflows/release.yml:51-77` (and root `build.gradle.kts:12`)
**Issue:** (a) The body hardcodes "Spring Boot starter (new in v0.1.0)" — every future tag (v0.2.0, …) will repeat this text. (b) It states the release "is now available on Maven Central" while the NOTE in the same body admits `USER_MANAGED` staging requires a manual portal action — the GitHub Release is created before the artifacts are actually public. (c) Nothing verifies the pushed tag matches the hardcoded `version = "0.1.0"` in the root build — tagging `v0.2.0` would publish `0.1.0` artifacts under a `v0.2.0` release. (d) `softprops/action-gh-release@v2` is pinned by mutable tag rather than commit SHA.
**Fix:** Derive the Gradle version from the tag (or assert equality and fail), soften the availability wording ("staged for Maven Central, pending portal publish"), drop the "new in v0.1.0" literal, and pin the third-party action by SHA.

### IN-05: `softLimitBreachCount` can only ever be 0 or 1

**File:** `budget-breaker/src/main/kotlin/io/github/unityinflow/budget/BudgetScope.kt:15-16, 56-58`
**Issue:** `softLimitFired` (AtomicBoolean) gates the increment, so the `AtomicInteger` never exceeds 1; `BudgetReport.softLimitBreachCount` and `getTotalSoftBreaches()` ("total number of soft limit breaches") therefore actually count *agents that breached*, not breaches. Misleading naming plus redundant state.
**Fix:** Either replace the counter with a Boolean (`softLimitBreached`) and rename the aggregate, or count every breach and emit/fire only on the first.

### IN-06: Hard-limit boundary semantics contradict the documentation

**File:** `budget-breaker/src/main/kotlin/io/github/unityinflow/budget/TokenTracker.kt:39` (docs in `AgentBudget.kt:7`, `BudgetBreakerProperties.kt:27-28`)
**Issue:** `isAboveHardLimit()` uses `totalTokens >= hardLimitTokens`, so a run is cancelled when it *reaches* the limit, while all KDoc ("exceeding this cancels") implies strictly greater-than. An agent budgeted at exactly N tokens that uses exactly N is killed.
**Fix:** Use `>` for the hard limit, or align all documentation to "reaching".

### IN-07: `BudgetSoftLimitException` is dead code

**File:** `budget-breaker/src/main/kotlin/io/github/unityinflow/budget/BudgetException.kt:9-16` (cross-reference)
**Issue:** Grep confirms `BudgetSoftLimitException` is defined but never thrown or referenced anywhere in the codebase — a public API that can never occur.
**Fix:** Remove it, or wire it into the soft-limit path if a throwing mode is planned (it is part of the v0.0.1 acceptance criteria's "sealed hierarchy", so document intent if kept).

### IN-08: Tracked-agent state is never evicted — `/actuator/budget` and meter tags grow without bound and report stale agents forever

**File:** `budget-breaker/src/main/kotlin/io/github/unityinflow/budget/BudgetCircuitBreaker.kt:27`; `budget-breaker-spring-boot-starter/src/main/kotlin/io/github/unityinflow/budget/spring/MetricsEventCollector.kt:75, 169-176`
**Issue:** `reports` and `ratioHolders` only ever grow; with per-run agent IDs (UUIDs are a natural choice) every run adds a permanent map entry, a permanent gauge, and permanent counter tag combinations. Beyond the (out-of-scope) memory aspect, this is a behavioral defect: the actuator endpoint payload and health computations include every agent since process start, and stale usage-ratio gauges keep reporting their last value indefinitely.
**Fix:** Add an eviction policy (max entries / TTL) or a `clearReport(agentId)`/`reset()` API, and remove the gauge holder (`registry.remove`) when an agent's report is evicted. Document retention behavior in the endpoint KDoc.

---

_Reviewed: 2026-06-12_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
