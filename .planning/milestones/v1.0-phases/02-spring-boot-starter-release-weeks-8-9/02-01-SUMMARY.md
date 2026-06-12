---
phase: 02-spring-boot-starter-release-weeks-8-9
plan: "01"
subsystem: budget-breaker-core + budget-breaker-spring-boot-starter
tags: [kotlin, core, spring-boot-starter, micrometer, tdd, observability]
dependency_graph:
  requires: []
  provides:
    - BudgetSnapshot (data class with running flag)
    - BudgetCircuitBreaker.getAllReports()
    - BudgetCircuitBreaker.getActiveTrackerCount()
    - BudgetCircuitBreaker.getTotalSoftBreaches()
    - BudgetCircuitBreaker.getTotalHardBreaches()
    - BudgetCircuitBreaker.modelOf()
    - BudgetEvent.CallTracked.model field
    - starter build.gradle.kts with Spring Boot 3.5.3 + Micrometer 1.15.0
  affects:
    - budget-breaker core module (additive)
    - budget-breaker-spring-boot-starter module (build only, no source yet)
tech_stack:
  added:
    - Spring Boot 3.5.3 (compileOnly in starter)
    - micrometer-core 1.15.0 (compileOnly in starter)
    - spring-boot-starter-test 3.5.3 (testImplementation in starter)
  patterns:
    - TDD Red-Green (both Task 1 and Task 2)
    - ConcurrentHashMap for in-flight tracker registry
    - compileOnly deps re-declared as testImplementation (Spring Boot starter pattern)
key_files:
  created:
    - budget-breaker/src/main/kotlin/io/github/unityinflow/budget/BudgetSnapshot.kt
  modified:
    - budget-breaker/src/main/kotlin/io/github/unityinflow/budget/BudgetEvent.kt
    - budget-breaker/src/main/kotlin/io/github/unityinflow/budget/BudgetScope.kt
    - budget-breaker/src/main/kotlin/io/github/unityinflow/budget/BudgetCircuitBreaker.kt
    - budget-breaker/src/test/kotlin/io/github/unityinflow/budget/BudgetCircuitBreakerTest.kt
    - budget-breaker-spring-boot-starter/build.gradle.kts
decisions:
  - "Appended model field to CallTracked as last constructor parameter (no positional-arg break)"
  - "activeTrackers.remove() called FIRST in finally block before reports[agentId] assignment (never double-visible)"
  - "getAllReports() overlays in-flight trackers over completed reports to give consumers the full picture"
  - "compileOnly for Spring/Micrometer in starter to avoid forcing Spring Boot version on consumers"
  - "No kapt in starter (per RESEARCH Pitfall 4: JVM-24 host risk; IDE hints optional)"
metrics:
  duration: ~18 minutes
  completed_date: "2026-06-12"
  tasks_completed: 3
  tasks_total: 3
  files_created: 1
  files_modified: 5
---

# Phase 02 Plan 01: Core D-08 Enhancement + Starter Build Foundation Summary

**One-liner:** Live in-flight agent snapshot via `getAllReports()`, aggregate breach counts, `CallTracked.model` field, and starter build wired with Spring Boot 3.5.3 + Micrometer 1.15.0.

## What Was Built

### Task 1: CallTracked.model field (TDD)

Added `val model: String` as the last constructor parameter to `BudgetEvent.CallTracked`. `BudgetScope.trackCall` now populates `model = tracker.model` (from `TokenTracker.model`, which reads `budget.model`). This is Option A from RESEARCH.md — clean model-tag resolution at event emission. All existing consumers of `CallTracked` that use named arguments are unaffected.

### Task 2: Live-snapshot + aggregate-breach APIs (TDD)

Created `BudgetSnapshot` data class wrapping a `BudgetReport` + `running: Boolean` flag.

Enhanced `BudgetCircuitBreaker`:

- Added `private val activeTrackers = ConcurrentHashMap<String, TokenTracker>()` for in-flight tracking
- In `withBudget`: registers tracker immediately before the coroutine block, removes it as the **first** statement of the `finally` block (so an agent is never simultaneously visible as both running and completed)
- `getAllReports()` — returns an immutable snapshot map: completed agents with `running=false`, in-flight agents overlaid with `running=true` and a point-in-time `BudgetReport` built from live counters
- `getActiveTrackerCount()` — `activeTrackers.size`
- `getTotalSoftBreaches()` — sums `softLimitBreachCount` across all completed reports
- `getTotalHardBreaches()` — counts completed reports where `hardLimitBreached == true`
- `modelOf(agentId)` — checks active trackers first, then completed reports; returns `null` if unknown

KDoc added to every new public member.

### Task 3: Starter build.gradle.kts

Replaced the bare stub with the full starter build:
- `id("budget-breaker.publishing")` convention applied
- `api(project(":budget-breaker"))` for transitive core dep
- `compileOnly` Spring Boot 3.5.3 + Micrometer 1.15.0 (consumers bring their own managed version)
- Test classpath re-declares all `compileOnly` deps (RESEARCH Pitfall 1) plus `spring-boot-starter-test`, `kotest-assertions-core`, `kotlinx-coroutines-test`
- No `kapt` (per RESEARCH Pitfall 4 / D-19: configuration processor is optional)
- Publishing pom override with proper `name` and `description`

## Deviations from Plan

### Auto-fixed Issues

None — plan executed exactly as written.

### Notes

- `ktlintFormat` was not available as a Gradle task (ktlint plugin not yet configured in the project build). Code formatting was applied manually following Kotlin style conventions. This is a pre-existing project gap — tracked for future buildSrc convention addition.

## Commits

| Task | Commit | Type | Description |
|------|--------|------|-------------|
| Task 1 RED | e32a673 | test | Add failing test for CallTracked.model propagation |
| Task 1 GREEN | 1d6c85d | feat | Add model field to CallTracked and populate from tracker |
| Task 2 RED | cf070a3 | test | Add failing tests for snapshot/aggregate-breach APIs |
| Task 2 GREEN | b55baf1 | feat | Add BudgetSnapshot and live-snapshot/aggregate APIs to BudgetCircuitBreaker |
| Task 3 | 02ce553 | feat | Wire starter build with Spring Boot + Micrometer + core deps |

## TDD Gate Compliance

Both Task 1 and Task 2 followed the full RED/GREEN cycle:

- RED commits: `e32a673` (Task 1), `cf070a3` (Task 2) — tests fail to compile on missing API
- GREEN commits: `1d6c85d` (Task 1), `b55baf1` (Task 2) — implementation makes tests pass
- No REFACTOR needed — code clean from the first pass

## Known Stubs

None — this plan is build-only and core enhancement only. No UI/rendering components involved.

## Self-Check: PASSED
