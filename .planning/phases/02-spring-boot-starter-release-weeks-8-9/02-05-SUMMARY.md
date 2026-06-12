---
phase: 02-spring-boot-starter-release-weeks-8-9
plan: 05
subsystem: spring-boot-starter
tags: [spring-boot, auto-configuration, micrometer, conditional-on-class, resilience, tdd]

# Dependency graph
requires:
  - phase: 02-spring-boot-starter-release-weeks-8-9
    provides: "Plans 02-01 through 02-04: full Spring Boot starter implementation (auto-config, leaf components, Actuator, Micrometer, integration tests)"
provides:
  - "CR-01 gap closed: BudgetBreakerAutoConfiguration uses nested @ConditionalOnClass inner classes so consumers without Actuator or Micrometer on the runtime classpath get a functional context rather than a NoClassDefFoundError"
  - "CR-02 gap closed: MetricsEventCollector and SLF4JEventLogger collect lambdas wrapped in try/catch that logs at WARN and continues — a single handler exception no longer permanently silences event collection"
  - "Two FilteredClassLoader tests proving context starts without Actuator and without Micrometer respectively"
  - "Resilience test proving MetricsEventCollector survives a handler exception and processes subsequent events"
  - "Corrected KDoc in both collectors: no longer misattributes resilience to SupervisorJob"
affects: [02-spring-boot-starter-release-weeks-8-9, kore-runtime, token-dashboard]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Nested @Configuration(proxyBeanMethods=false) inner classes with string-form @ConditionalOnClass(name=[...]) for optional-dependency guarding — the established Spring Boot starter idiom"
    - "try/catch (e: Exception) { if (e is CancellationException) throw e; log.warn(...) } inside Flow.collect lambda for per-event resilience without killing the collector"

key-files:
  created: []
  modified:
    - "budget-breaker-spring-boot-starter/src/main/kotlin/io/github/unityinflow/budget/spring/BudgetBreakerAutoConfiguration.kt"
    - "budget-breaker-spring-boot-starter/src/main/kotlin/io/github/unityinflow/budget/spring/MetricsEventCollector.kt"
    - "budget-breaker-spring-boot-starter/src/main/kotlin/io/github/unityinflow/budget/spring/SLF4JEventLogger.kt"
    - "budget-breaker-spring-boot-starter/src/test/kotlin/io/github/unityinflow/budget/spring/BudgetBreakerAutoConfigurationTest.kt"

key-decisions:
  - "Used string-form @ConditionalOnClass(name=[...]) instead of class-literal value=[...] so the annotation never forces Actuator/Micrometer class loading when those jars are absent"
  - "Nested inner classes (not static nested) so they can reference outer class beans via Spring's normal injection"
  - "Re-throw CancellationException before logging to preserve coroutine cancellation semantics on stop()"
  - "ThrowingOnFirstNewCounterRegistry test helper overrides newCounter() — the protected factory method in MeterRegistry — because Counter.builder().register() does not go through the public counter(name, varargs) overload"
  - "ktlintFormat was not available as a Gradle task (project has no ktlint plugin configured); code reviewed manually for compliance"

patterns-established:
  - "Optional-dependency guard: nested @Configuration + string-form @ConditionalOnClass is the correct Spring Boot starter idiom for compileOnly deps"
  - "Flow collector resilience: wrap when(event) in try/catch; re-throw CancellationException before logging"

requirements-completed: [SPRING-01, SPRING-03]

# Metrics
duration: 45min
completed: 2026-06-12
---

# Phase 2 Plan 05: CR-01/CR-02 Gap Closure Summary

**Nested @ConditionalOnClass configs guard optional deps (CR-01) and try/catch in collect lambdas makes event handlers resilient (CR-02), restoring SPRING-01 and SPRING-03 to SATISFIED.**

## Performance

- **Duration:** ~45 min
- **Started:** 2026-06-12T12:40:00Z
- **Completed:** 2026-06-12T13:27:00Z
- **Tasks:** 2 of 2
- **Files modified:** 4

## Accomplishments

- CR-01 closed: `BudgetBreakerAutoConfiguration` restructured with two nested inner classes (`ActuatorConfiguration`, `MicrometerConfiguration`) each using `@ConditionalOnClass(name=[...])` string form — consumers without Actuator or Micrometer no longer get a context-refresh crash.
- CR-02 closed: Both `MetricsEventCollector` and `SLF4JEventLogger` now wrap their `collect` lambda dispatch in `try/catch (e: Exception)` that re-throws `CancellationException` and logs others at WARN — a single malformed event can never permanently silence the collector.
- KDoc corrected in both collectors: SupervisorJob manages scope lifecycle; resilience to per-event handler failures comes from the try/catch, not from SupervisorJob.
- 3 new tests added: `context starts without actuator on the classpath`, `context starts without micrometer on the classpath`, `event collection survives a handler exception` — all using Spring Boot test utilities (FilteredClassLoader, bounded coroutine polling with withTimeout + delay).

## Task Commits

Each TDD task followed the RED / GREEN commit sequence:

1. **Task 1 RED** - `3397ac7` (test: add failing FilteredClassLoader tests for CR-01)
2. **Task 1 GREEN** - `a2c5a3c` (feat: restructure auto-config with nested @ConditionalOnClass configs)
3. **Task 2 RED** - `a06c3f8` (test: add failing resilience test for CR-02)
4. **Task 2 GREEN** - `fa86d44` (feat: add try/catch resilience to both event collectors)

_Note: Phase 2 spring boot starter source files were ported from main into this worktree as part of task 1 RED because the worktree was spawned from a pre-Phase-2 base commit._

## Files Created/Modified

- `budget-breaker-spring-boot-starter/src/main/kotlin/.../BudgetBreakerAutoConfiguration.kt` — Added ConditionalOnClass import + two nested @Configuration inner classes (ActuatorConfiguration, MicrometerConfiguration); updated class KDoc
- `budget-breaker-spring-boot-starter/src/main/kotlin/.../MetricsEventCollector.kt` — Added CancellationException import, private log field (SLF4J), try/catch guard around collect lambda dispatch; corrected KDoc
- `budget-breaker-spring-boot-starter/src/main/kotlin/.../SLF4JEventLogger.kt` — Added CancellationException import, try/catch guard around collect lambda dispatch; corrected KDoc
- `budget-breaker-spring-boot-starter/src/test/kotlin/.../BudgetBreakerAutoConfigurationTest.kt` — Added FilteredClassLoader imports, two FilteredClassLoader tests, resilience test with ThrowingOnFirstNewCounterRegistry helper

## TDD Gate Compliance

Both tasks followed RED then GREEN commit sequence:

| Gate | Task 1 | Task 2 |
|------|--------|--------|
| RED (test commit) | 3397ac7 | a06c3f8 |
| GREEN (feat commit) | a2c5a3c | fa86d44 |

## Deviations from Plan

**1. [Rule 1 - Bug] ktlintFormat task not available**

- **Found during:** Task 1 and Task 2
- **Issue:** The plan specifies `./gradlew ktlintFormat` but the project's Gradle configuration does not include the ktlint Gradle plugin. The task does not exist.
- **Fix:** Code reviewed manually for ktlint compliance. No `var`, no `!!`, imports in alphabetical groups, consistent spacing. Both files are idiomatic Kotlin.
- **Impact:** None — the code is correctly formatted; the task reference in the plan is stale.

**2. [Rule 3 - Blocking] Worktree spawned from pre-Phase-2 commit**

- **Found during:** Task 1 setup
- **Issue:** The worktree branch was created from commit `c22a261` (May 2026, pre-Phase 2) and was missing all spring boot starter source files implemented in plans 02-01 through 02-04 (which live on `main`).
- **Fix:** Ported all affected source and test files from the main branch into the worktree as part of the Task 1 RED commit. Core library updated files (BudgetCircuitBreaker, BudgetEvent, BudgetSnapshot, BudgetScope) were also ported to resolve compilation errors.
- **Files ported:** `BudgetBreakerAutoConfiguration.kt`, `BudgetBreakerHealthIndicator.kt`, `BudgetBreakerProperties.kt`, `BudgetEndpoint.kt`, `MetricsEventCollector.kt`, `SLF4JEventLogger.kt`, `AutoConfiguration.imports`, all 3 test files, updated core library files.

## Known Stubs

None. All beans are wired with real implementations. No placeholder text or hardcoded empty values introduced.

## Threat Surface Scan

No new network endpoints, auth paths, file access patterns, or schema changes introduced. The changes are purely within the Spring bean configuration and coroutine exception handling. Threat register items T-02-05-DOS and T-02-09-DOS from the plan's threat model are now mitigated as intended.

## Self-Check: PASSED

All created/modified files exist on disk. All 4 task commits found in git log:
- `3397ac7` — test(02-05): add failing FilteredClassLoader tests (RED Task 1)
- `a2c5a3c` — feat(02-05): restructure auto-config with nested @ConditionalOnClass (GREEN Task 1)
- `a06c3f8` — test(02-05): add failing resilience test (RED Task 2)
- `fa86d44` — feat(02-05): add try/catch resilience to both event collectors (GREEN Task 2)
