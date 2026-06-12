---
phase: 02-spring-boot-starter-release-weeks-8-9
fixed_at: 2026-06-12T14:32:00Z
review_path: .planning/phases/02-spring-boot-starter-release-weeks-8-9/02-REVIEW.md
iteration: 1
findings_in_scope: 7
fixed: 7
skipped: 0
status: all_fixed
---

# Phase 02: Code Review Fix Report

**Fixed at:** 2026-06-12T14:32:00Z
**Source review:** .planning/phases/02-spring-boot-starter-release-weeks-8-9/02-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 7 (fix_scope: critical_warning — 0 Critical, 7 Warning; 6 Info findings out of scope)
- Fixed: 7
- Skipped: 0

Every fix was verified with `./gradlew :budget-breaker:test :budget-breaker-spring-boot-starter:test` before committing; a final `./gradlew clean build` passed after the last fix. The race-sensitive starter tests were additionally re-run with `--rerun-tasks` to confirm stability.

## Fixed Issues

### WR-01: SmartLifecycle restart silently does nothing — `isRunning()` lies after stop/start cycle

**Files modified:** `budget-breaker-spring-boot-starter/src/main/kotlin/io/github/unityinflow/budget/spring/MetricsEventCollector.kt`, `budget-breaker-spring-boot-starter/src/main/kotlin/io/github/unityinflow/budget/spring/SLF4JEventLogger.kt`
**Commit:** 0857cff
**Applied fix:** Both collectors now launch a fresh collection `Job` per `start()` and `stop()` cancels only that job (held in an `AtomicReference<Job?>` per the project's no-`var` convention) — the long-lived parent `SupervisorJob`/scope is never cancelled, so a SmartLifecycle stop/start cycle resumes collection. KDoc updated in both files. A regression test (`collector resumes event collection after a stop-start cycle`) was added in the WR-06 commit, once the subscription-sync primitive it needs existed.

### WR-02: `budgetPricing` bean lacks `@ConditionalOnMissingBean` — user-defined `ModelPricing` bean crashes the context

**Files modified:** `budget-breaker-spring-boot-starter/src/main/kotlin/io/github/unityinflow/budget/spring/BudgetBreakerAutoConfiguration.kt`, `budget-breaker-spring-boot-starter/src/test/kotlin/io/github/unityinflow/budget/spring/BudgetBreakerAutoConfigurationTest.kt`
**Commit:** f569b6c
**Applied fix:** Added `@ConditionalOnMissingBean` to `budgetPricing` and KDoc documenting that applications overriding `BudgetCircuitBreaker` (D-17) should also provide a matching `ModelPricing` bean to preserve the D-11 invariant. Added regression test `honors user-defined ModelPricing bean`.

### WR-03: Concurrent `withBudget` runs with the same agentId corrupt tracking state

**Files modified:** `budget-breaker/src/main/kotlin/io/github/unityinflow/budget/BudgetCircuitBreaker.kt`, `budget-breaker/src/test/kotlin/io/github/unityinflow/budget/BudgetCircuitBreakerTest.kt`
**Commit:** e3ed687
**Applied fix:** Registration now uses `activeTrackers.putIfAbsent` + `require(previous == null)` (fail-fast `IllegalArgumentException` on collision, as suggested by the review) and the `finally` block uses the two-arg `activeTrackers.remove(agentId, tracker)` so a run can only deregister its own tracker. The concurrency contract is documented in KDoc (`@throws IllegalArgumentException`). Added regression test `rejects a concurrent withBudget run with a duplicate agentId`.

### WR-04: In-flight snapshot hardcodes `softLimitBreachCount = 0` — actuator endpoint reports false data during a breach

**Files modified:** `budget-breaker/src/main/kotlin/io/github/unityinflow/budget/TokenTracker.kt`, `budget-breaker/src/main/kotlin/io/github/unityinflow/budget/BudgetScope.kt`, `budget-breaker/src/main/kotlin/io/github/unityinflow/budget/BudgetCircuitBreaker.kt`, `budget-breaker/src/test/kotlin/io/github/unityinflow/budget/BudgetCircuitBreakerTest.kt`
**Commit:** c1fe256
**Applied fix:** Moved the soft-limit breach counter from `BudgetScope` into `TokenTracker` (AtomicInteger + `softLimitBreachCount` getter + internal `recordSoftLimitBreach()`), so `getAllReports()` now reports `softLimitBreachCount = tracker.softLimitBreachCount` for in-flight agents. `BudgetScope.buildReport` reads the same tracker counter — completed-report behavior is unchanged. Added regression test `getAllReports reports live soft breach count for an in-flight agent`.

### WR-05: Unguarded `onSoftLimit` user callback aborts the agent run and suppresses the `SoftLimitReached` event

**Files modified:** `budget-breaker/src/main/kotlin/io/github/unityinflow/budget/BudgetScope.kt`, `budget-breaker/src/test/kotlin/io/github/unityinflow/budget/BudgetCircuitBreakerTest.kt`
**Commit:** dafdb73
**Applied fix:** `trackCall` now emits `BudgetEvent.SoftLimitReached` **before** invoking the user callback, and the callback is wrapped in try/catch: `CancellationException` is re-thrown, all other exceptions are logged at WARNING and swallowed so a failing logging/alerting hook never aborts the agent run. Deviation from the review snippet: the core module is dependency-free (no SLF4J), so logging uses `java.util.logging` with a comment explaining the choice (Spring Boot apps bridge JUL to SLF4J/Logback by default). KDoc on `trackCall` documents the new contract. Added regression test `throwing soft limit callback does not abort the run and the event is still emitted`.

### WR-06: Event-driven tests race on Flow subscription — flaky on loaded CI runners

**Files modified:** `budget-breaker/src/main/kotlin/io/github/unityinflow/budget/BudgetCircuitBreaker.kt`, `budget-breaker-spring-boot-starter/src/test/kotlin/io/github/unityinflow/budget/spring/BudgetStarterIntegrationTest.kt`, `budget-breaker-spring-boot-starter/src/test/kotlin/io/github/unityinflow/budget/spring/BudgetBreakerAutoConfigurationTest.kt`
**Commit:** 1b4ca44
**Applied fix:** Exposed `val subscriptions: StateFlow<Int> = _events.subscriptionCount` on `BudgetCircuitBreaker` (with KDoc). The integration test now bounded-polls `breaker.subscriptions.value >= 2` (both SLF4JEventLogger and MetricsEventCollector subscribe in that context) before emitting, and replaced the cross-dispatcher `yield()` + fixed `delay(50)` with a bounded poll on the actual counter values. The handler-exception test polls `subscriptions.value > 0` after `collector.start()`; the `delay(100)` heuristic was removed because event ordering through the single collector coroutine is deterministic once subscription is established. Also added the WR-01 restart regression test here (`collector resumes event collection after a stop-start cycle`), which depends on the new `subscriptions` API. Starter tests re-run with `--rerun-tasks` to confirm stability.

### WR-07: Release workflow never verifies the git tag matches the Gradle project version

**Files modified:** `.github/workflows/release.yml`, `build.gradle.kts`
**Commit:** 143494d
**Applied fix:** Added a "Verify tag matches Gradle version" guard step (placed right after Gradle setup so it fails fast before the expensive build/publish steps) comparing `${GITHUB_REF_NAME#v}` against `./gradlew properties` output. Removed the duplicated `version = "0.1.0"` (and the equally duplicated `group`) from the root `build.gradle.kts` `allprojects` block — both are single-sourced in `gradle.properties`, with a comment warning against re-duplication. Verified `group`/`version` still resolve to `io.github.unityinflow`/`0.1.0` for the root project and both subprojects, the awk extraction works against actual `./gradlew properties -q` output, and the workflow YAML parses.

---

_Fixed: 2026-06-12T14:32:00Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
