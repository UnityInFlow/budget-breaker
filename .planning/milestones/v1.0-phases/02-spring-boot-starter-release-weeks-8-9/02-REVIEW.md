---
phase: 02-spring-boot-starter-release-weeks-8-9
reviewed: 2026-06-12T13:37:12Z
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
  critical: 0
  warning: 7
  info: 6
  total: 13
status: issues_found
---

# Phase 02: Code Review Report (re-review after gap-closure plan 02-05)

**Reviewed:** 2026-06-12T13:37:12Z
**Depth:** standard
**Files Reviewed:** 17
**Status:** issues_found

## Summary

This re-review verifies the two prior BLOCKER findings and performs a fresh standard-depth pass over all 17 files. Cross-referenced core classes the starter calls into (`TokenTracker`, `ModelPricing`, `BudgetReport`) and executed `./gradlew :budget-breaker-spring-boot-starter:test` to verify the fixes at runtime (exit 0, all tests pass).

**Prior blocker verification:**

- **CR-01 (auto-config crash without Actuator/Micrometer) — FIXED.** `BudgetBreakerAutoConfiguration.kt:134-181` now isolates Actuator beans (`BudgetEndpoint`, `BudgetBreakerHealthIndicator`) and the Micrometer bean (`MetricsEventCollector`) in nested configurations guarded by string-form `@ConditionalOnClass(name = [...])`, which never forces class loading. `FilteredClassLoader` tests (`BudgetBreakerAutoConfigurationTest.kt:66-86`) verify the context starts cleanly without `HealthIndicator` and without `MeterRegistry`, and that the conditional beans are absent. Confirmed passing.
- **CR-02 (handler exception permanently silences collectors) — FIXED.** Both `MetricsEventCollector.kt:102-111` and `SLF4JEventLogger.kt:64-79` wrap per-event dispatch in try/catch, log at WARN, and correctly re-throw `CancellationException` to preserve clean shutdown. A regression test (`event collection survives a handler exception`, `BudgetBreakerAutoConfigurationTest.kt:103-146`) proves the collect loop survives a first-event failure. Confirmed passing.

No new critical issues found. Seven warnings remain: a broken `SmartLifecycle` restart contract in both event collectors, a bean-conflict gap on `budgetPricing`, agent-ID collision corruption in the core breaker, inaccurate in-flight breach reporting at the actuator endpoint, an unguarded user callback in the core soft-limit path (same failure class as CR-02, unfixed on the synchronous path), a subscription race that makes two event-driven tests flaky on loaded CI runners, and a missing tag-vs-Gradle-version guard in the release workflow.

## Narrative Findings (AI reviewer)

## Warnings

### WR-01: SmartLifecycle restart silently does nothing — `isRunning()` lies after stop/start cycle

**File:** `budget-breaker-spring-boot-starter/src/main/kotlin/io/github/unityinflow/budget/spring/MetricsEventCollector.kt:70,97-125` and `budget-breaker-spring-boot-starter/src/main/kotlin/io/github/unityinflow/budget/spring/SLF4JEventLogger.kt:52,60-93`
**Issue:** Both classes create their `CoroutineScope` once at construction (`SupervisorJob() + Dispatchers.Default`). `stop()` calls `scope.cancel()`, which cancels the parent `SupervisorJob` permanently. A subsequent `start()` — which the `SmartLifecycle` contract explicitly permits (JMX-triggered restart, `LifecycleProcessor` stop/start, context restart in tests) — sets `running = true` and calls `scope.launch {}` on a cancelled scope: the launched coroutine is dead on arrival and never subscribes to the events Flow. The component then reports `isRunning() == true` while collecting nothing — metrics and WARN logs silently stop forever with no error.
**Fix:** Create the job per start instead of per instance:
```kotlin
private val supervisor = SupervisorJob()
private var job: Job? = null

override fun start() {
    running.set(true)
    job = CoroutineScope(supervisor + Dispatchers.Default).launch { ... }
}

override fun stop() {
    running.set(false)
    job?.cancel()
    job = null
}
```
(cancel only the collection job in `stop()`, never the long-lived parent). Apply to both `MetricsEventCollector` and `SLF4JEventLogger`.

### WR-02: `budgetPricing` bean lacks `@ConditionalOnMissingBean` — user-defined `ModelPricing` bean crashes the context

**File:** `budget-breaker-spring-boot-starter/src/main/kotlin/io/github/unityinflow/budget/spring/BudgetBreakerAutoConfiguration.kt:78-84`
**Issue:** `budgetCircuitBreaker` is guarded with `@ConditionalOnMissingBean` (D-17), but `budgetPricing` is not. If a consuming application defines its own `ModelPricing` bean, the context contains two `ModelPricing` candidates; the by-type injection points (`budgetCircuitBreaker(properties, pricing)` at lines 99-102 and `budgetMetricsCollector(breaker, pricing)` at lines 177-180) fail with `NoUniqueBeanDefinitionException` — startup crash. Neither bean name matches the parameter name `pricing`, so by-name disambiguation does not rescue it. Secondary effect: when a user overrides `BudgetCircuitBreaker` (the documented D-17 extension path), `MetricsEventCollector` still receives the auto-config's pricing, which may diverge from the pricing inside the user's breaker — violating the D-11 invariant ("cost counter uses the same overrides as the breaker") that the shared bean exists to guarantee.
**Fix:**
```kotlin
@Bean
@ConditionalOnMissingBean
fun budgetPricing(properties: BudgetBreakerProperties): ModelPricing = ...
```
and document that users overriding `BudgetCircuitBreaker` should also provide a matching `ModelPricing` bean to keep metrics cost in sync.

### WR-03: Concurrent `withBudget` runs with the same agentId corrupt tracking state

**File:** `budget-breaker/src/main/kotlin/io/github/unityinflow/budget/BudgetCircuitBreaker.kt:50,60-62`
**Issue:** `activeTrackers[agentId] = tracker` unconditionally overwrites. If two coroutines run `withBudget("agent-1")` concurrently (easy in a Spring app serving parallel requests), the second registration replaces the first tracker. When the first run finishes, its `finally` block executes `activeTrackers.remove(agentId)` — removing the *second* run's tracker. Consequences: the still-running agent vanishes from `getAllReports()` and `getActiveTrackerCount()` (health indicator and `/actuator/budget` under-report), and `reports[agentId]` is overwritten by whichever run completes last, mixing results from interleaved runs. There is no guard, no error, and no KDoc warning that agentId must be unique per concurrent run.
**Fix:** Either fail fast on collision:
```kotlin
val previous = activeTrackers.putIfAbsent(agentId, tracker)
require(previous == null) { "Agent '$agentId' is already running inside withBudget" }
```
with the two-arg `activeTrackers.remove(agentId, tracker)` in `finally`, or key internal state by a unique run ID — and document the chosen contract.

### WR-04: In-flight snapshot hardcodes `softLimitBreachCount = 0` — actuator endpoint reports false data during a breach

**File:** `budget-breaker/src/main/kotlin/io/github/unityinflow/budget/BudgetCircuitBreaker.kt:100`
**Issue:** `getAllReports()` builds live reports for in-flight agents with `softLimitBreachCount = 0` regardless of whether the agent has already breached its soft limit. An operator inspecting `GET /actuator/budget` during an active soft breach — exactly the moment the endpoint matters most — sees `softLimitBreachCount: 0` for the running agent even while `percentUsed` shows the breach. The real counter lives in `BudgetScope` (`BudgetScope.kt:15`), which the breaker cannot reach because `activeTrackers` only retains the `TokenTracker`.
**Fix:** Move the breach counter into `TokenTracker` (or retain the `BudgetScope` alongside the tracker) so the live snapshot can report the true count:
```kotlin
softLimitBreachCount = tracker.softLimitBreachCount,
```

### WR-05: Unguarded `onSoftLimit` user callback aborts the agent run and suppresses the `SoftLimitReached` event

**File:** `budget-breaker/src/main/kotlin/io/github/unityinflow/budget/BudgetScope.kt:56-68`
**Issue:** `onSoftLimit?.invoke(report)` at line 59 runs user code synchronously inside `trackCall` with no try/catch. If the callback throws: (a) the exception propagates out of `trackCall` and aborts the agent's run — a logging/alerting hook kills the agent; and (b) `eventFlow.emit(SoftLimitReached(...))` at lines 60-67 is never reached, so the SLF4J logger and the Micrometer breach counter never observe the breach. Because `softLimitFired.getAndSet(true)` already flipped at line 56, the event can never fire on subsequent calls either — the breach is permanently invisible. This is the same failure class as the fixed CR-02 (a handler failure must not break the event pipeline), unaddressed on the synchronous callback path.
**Fix:** Emit the event before invoking the callback, and isolate callback failures:
```kotlin
if (tracker.isAboveSoftLimit() && !softLimitFired.getAndSet(true)) {
    softLimitBreachCount.incrementAndGet()
    eventFlow.emit(BudgetEvent.SoftLimitReached(...))
    try {
        onSoftLimit?.invoke(buildReport(0))
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        // log and continue — callback failure must not abort the agent run
    }
}
```
(or explicitly document that a throwing callback aborts the run — but the event must still be emitted first).

### WR-06: Event-driven tests race on Flow subscription — flaky on loaded CI runners

**File:** `budget-breaker-spring-boot-starter/src/test/kotlin/io/github/unityinflow/budget/spring/BudgetStarterIntegrationTest.kt:69-98` and `budget-breaker-spring-boot-starter/src/test/kotlin/io/github/unityinflow/budget/spring/BudgetBreakerAutoConfigurationTest.kt:103-146`
**Issue:** Both collectors subscribe via `scope.launch` on `Dispatchers.Default`, and `breaker.events` is a `SharedFlow` with `replay = 0` — events emitted before subscription is established are silently dropped.
- `BudgetStarterIntegrationTest:74`: the `yield()` runs on the `runBlocking` event loop and does nothing to advance the collector coroutine on `Dispatchers.Default`; the comment's claim that it "lets the coroutine dispatcher process the subscription" is incorrect for a cross-dispatcher coroutine. If the collector launched during context refresh has not reached `collect` when `trackCall` emits, the event is dropped and `registry.get(...)` throws `MeterNotFoundException`. The fixed `delay(50)` at line 83 is also a hard race bound on shared self-hosted runners.
- `BudgetBreakerAutoConfigurationTest:110-121`: `collector.start()` returns before the launched coroutine subscribes; the `delay(100)` is a heuristic, not a guarantee. If the first (`agent-error`) emission is dropped, the simulated first-`newCounter` failure lands on the `agent-normal` event instead, its input counter is never registered, and the 5-second bounded poll times out.
**Fix:** Await subscription before emitting — e.g., expose `val subscriptions: StateFlow<Int> = _events.subscriptionCount` on `BudgetCircuitBreaker` and poll `subscriptions.value > 0` inside `withTimeout`; replace the fixed `delay(50)` in the integration test with the bounded-poll pattern already used at `BudgetBreakerAutoConfigurationTest:126-136`.

### WR-07: Release workflow never verifies the git tag matches the Gradle project version

**File:** `.github/workflows/release.yml:37-41` (with the version pinned in both `gradle.properties` and root `build.gradle.kts:12`)
**Issue:** The published Maven coordinates come from the Gradle `version` property (currently hardcoded in two places: `gradle.properties` `version=0.1.0` and root `build.gradle.kts:12` `version = "0.1.0"`), while the GitHub release notes derive the version from the tag (`${GITHUB_REF_NAME#v}`, lines 39-41, 58, 69). Nothing checks they agree. Tagging `v0.2.0` while Gradle still says `0.1.0` produces a release page instructing users to depend on `0.2.0` while the Sonatype bundle contains `0.1.0` (or a duplicate version the portal rejects only after the build and publish steps already ran). Duplicating the version across two files makes this drift likely.
**Fix:** Add a guard step before publishing:
```yaml
- name: Verify tag matches Gradle version
  run: |
    GRADLE_VERSION=$(./gradlew properties -q --no-configuration-cache | awk '/^version:/ {print $2}')
    TAG_VERSION="${GITHUB_REF_NAME#v}"
    if [ "$GRADLE_VERSION" != "$TAG_VERSION" ]; then
      echo "Tag $TAG_VERSION does not match Gradle version $GRADLE_VERSION" >&2
      exit 1
    fi
```
and define the version once (in `gradle.properties`), removing the duplicate in `build.gradle.kts`.

## Info

### IN-01: Dead catch-and-rethrow block in `withBudget`

**File:** `budget-breaker/src/main/kotlin/io/github/unityinflow/budget/BudgetCircuitBreaker.kt:56-57`
**Issue:** `catch (e: BudgetHardLimitException) { throw e }` is a no-op — the exception propagates identically without it, and `finally` runs either way.
**Fix:** Delete the catch clause (keep `try`/`finally`).

### IN-02: Release notes hardcode "new in v0.1.0"

**File:** `.github/workflows/release.yml:62`
**Issue:** The body template says "Spring Boot starter (new in v0.1.0)" for every future tag; the v0.2.0 release will still claim the starter is new in v0.1.0.
**Fix:** Drop the "(new in v0.1.0)" qualifier from the reusable template.

### IN-03: Per-agent actuator endpoint returns 404 for in-flight agents the aggregate endpoint shows

**File:** `budget-breaker-spring-boot-starter/src/main/kotlin/io/github/unityinflow/budget/spring/BudgetEndpoint.kt:62`
**Issue:** `agentBudget` delegates to `breaker.getReport(agentId)`, which only consults completed reports. `GET /actuator/budget` lists a running agent while `GET /actuator/budget/{id}` for the same agent returns 404. The KDoc documents this, but it is surprising API behavior with a one-line fix available.
**Fix:** `fun agentBudget(@Selector agentId: String): BudgetReport? = breaker.getAllReports()[agentId]?.report`

### IN-04: `ModelPriceProperties` defaults to 0.0 with no validation — partial pricing config silently yields $0 cost metrics

**File:** `budget-breaker-spring-boot-starter/src/main/kotlin/io/github/unityinflow/budget/spring/BudgetBreakerProperties.kt:61-64`
**Issue:** Omitting `output-per-million` (or fat-fingering a key so binding skips it) silently prices that dimension at $0.0, undercounting `budget.breaker.cost.usd` with no warning. `ModelPricing.estimateCost` (`ModelPricing.kt:17`) likewise returns 0.0 for unknown models. Validation exists for token limits but not for pricing.
**Fix:** Add `init { require(inputPerMillion >= 0.0 && outputPerMillion >= 0.0) }` and warn (or fail) at startup when a pricing override entry leaves both values at the 0.0 default.

### IN-05: Nested auto-config classes use Kotlin `inner` instead of static nesting

**File:** `budget-breaker-spring-boot-starter/src/main/kotlin/io/github/unityinflow/budget/spring/BudgetBreakerAutoConfiguration.kt:136,168`
**Issue:** `ActuatorConfiguration` and `MicrometerConfiguration` are declared `inner class`, compiling to non-static nested classes whose constructors require the enclosing instance. This works (Spring resolves the enclosing config bean — verified by the passing test suite), but Spring's documented convention is static nested `@Configuration` classes, and neither class touches outer state. The `inner` modifier adds reliance on under-specified Spring instantiation behavior for zero benefit.
**Fix:** Remove the `inner` modifier (a plain Kotlin nested class compiles to a static nested class).

### IN-06: Events arriving before `bindTo` are silently dropped by the metrics collector

**File:** `budget-breaker-spring-boot-starter/src/main/kotlin/io/github/unityinflow/budget/spring/MetricsEventCollector.kt:101`
**Issue:** `val registry = registryRef.get() ?: return@collect` silently discards events when the collector is started but not yet bound (a misconfigured manual setup, as the integration test's explicit `bindTo` call illustrates). Silent data loss is hard to diagnose.
**Fix:** Log once at WARN (e.g., guarded by an `AtomicBoolean`) when events are dropped because no registry is bound.

---

_Reviewed: 2026-06-12T13:37:12Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
