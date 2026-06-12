---
phase: 02-spring-boot-starter-release-weeks-8-9
plan: "02"
subsystem: budget-breaker-spring-boot-starter
tags: [kotlin, spring-boot-starter, configurationproperties, actuator, micrometer, tdd, observability]
dependency_graph:
  requires:
    - BudgetCircuitBreaker.getAllReports()
    - BudgetCircuitBreaker.getReport()
    - BudgetCircuitBreaker.getActiveTrackerCount()
    - BudgetCircuitBreaker.getTotalSoftBreaches()
    - BudgetCircuitBreaker.getTotalHardBreaches()
    - BudgetEvent.CallTracked.model
    - BudgetSnapshot
    - ModelPricing.estimateCost()
  provides:
    - BudgetBreakerProperties (@ConfigurationProperties data class)
    - ModelPriceProperties (nested pricing override data class)
    - BudgetEndpoint (@Endpoint id=budget with two @ReadOperation methods)
    - BudgetBreakerHealthIndicator (always-UP HealthIndicator)
    - MetricsEventCollector (MeterBinder + SmartLifecycle)
  affects:
    - budget-breaker-spring-boot-starter module (four new source files)
    - budget-breaker-spring-boot-starter/build.gradle.kts (coroutines dep added)
tech_stack:
  added:
    - kotlinx-coroutines-core:1.10.1 (implementation in starter — needed directly since core uses `implementation` not `api`)
  patterns:
    - TDD Red-Green (Task 1: BudgetBreakerPropertiesTest RED commit then GREEN impl)
    - @ConfigurationProperties constructor binding (SB3 native, no @ConstructorBinding)
    - @Endpoint/@ReadOperation/@Selector (actuator custom endpoint)
    - HealthIndicator always-UP with detail keys
    - MeterBinder + SmartLifecycle (coroutine scope tied to Spring context lifetime)
    - AtomicBoolean for running flag, AtomicReference<MeterRegistry?> for registry reference
    - ConcurrentHashMap<String, AtomicReference<Double>> for Micrometer gauge strong-reference
key_files:
  created:
    - budget-breaker-spring-boot-starter/src/main/kotlin/io/github/unityinflow/budget/spring/BudgetBreakerProperties.kt
    - budget-breaker-spring-boot-starter/src/main/kotlin/io/github/unityinflow/budget/spring/BudgetEndpoint.kt
    - budget-breaker-spring-boot-starter/src/main/kotlin/io/github/unityinflow/budget/spring/BudgetBreakerHealthIndicator.kt
    - budget-breaker-spring-boot-starter/src/main/kotlin/io/github/unityinflow/budget/spring/MetricsEventCollector.kt
    - budget-breaker-spring-boot-starter/src/test/kotlin/io/github/unityinflow/budget/spring/BudgetBreakerPropertiesTest.kt
  modified:
    - budget-breaker-spring-boot-starter/build.gradle.kts
decisions:
  - "AtomicBoolean for running flag instead of var (CLAUDE.md: no var)"
  - "AtomicReference<MeterRegistry?> with ?: return@collect guard instead of !! (CLAUDE.md: no !!)"
  - "kotlinx-coroutines-core added as implementation to starter (core uses implementation not api)"
  - "HealthIndicator always UP — D-07: budget breach must never flip k8s probes to DOWN"
  - "Gauge backed by AtomicReference<Double> in ConcurrentHashMap for Micrometer strong-reference requirement"
  - "Metric names pinned to gen_ai.client.token.usage.input/.output (underscore in prefix, dots elsewhere — OtlpMetricMapper.kt contract)"
metrics:
  duration: ~4 minutes
  completed_date: "2026-06-12"
  tasks_completed: 3
  tasks_total: 3
  files_created: 5
  files_modified: 1
---

# Phase 02 Plan 02: Spring Leaf Components (Properties, Endpoint, Health, Metrics) Summary

**One-liner:** Four plain Spring leaf classes (no auto-wiring yet): `@ConfigurationProperties` data class with fail-fast init validation, read-only `@Endpoint(id=budget)` exposing live snapshots, always-UP `HealthIndicator`, and `MeterBinder+SmartLifecycle` metrics collector with OTel semconv meter names.

## What Was Built

### Task 1: BudgetBreakerProperties + BudgetBreakerPropertiesTest (TDD)

Created `BudgetBreakerProperties.kt` — a `val`-only `@ConfigurationProperties("budget-breaker")` data class:
- `defaultModel: String = "claude-sonnet-4-6"`
- `hardLimitTokens: Long = 100_000`
- `softLimitTokens: Long = 80_000`
- `pricing: Map<String, ModelPriceProperties> = emptyMap()`

`init {}` block with three `require(...)` checks using YAML-path-named messages:
- "budget-breaker.hard-limit-tokens must be positive"
- "budget-breaker.soft-limit-tokens must be positive"
- "budget-breaker.soft-limit-tokens must be <= hard-limit-tokens"

`ModelPriceProperties` sibling data class with `inputPerMillion`/`outputPerMillion` Double fields.

`BudgetBreakerPropertiesTest.kt` — plain JUnit5 unit tests (no Spring context):
- Default construction assertions via Kotest `shouldBe`
- `shouldThrow<IllegalArgumentException>` for soft > hard, hardLimitTokens = -1, softLimitTokens = 0
- Equal limits are valid (boundary condition)
- Pricing map entries stored correctly

### Task 2: BudgetEndpoint + BudgetBreakerHealthIndicator (SPRING-02)

`BudgetEndpoint.kt`:
- `@Endpoint(id = "budget")` with constructor param `BudgetCircuitBreaker`
- `@ReadOperation fun budget(): Map<String, BudgetSnapshot>` — returns `breaker.getAllReports()`
- `@ReadOperation fun agentBudget(@Selector agentId: String): BudgetReport?` — returns `breaker.getReport(agentId)`
- No `@WriteOperation` / `@DeleteOperation` (D-06 read-only)
- No `@Component` (will be a `@Bean` in plan 03 auto-config)
- KDoc documents the `management.endpoints.web.exposure.include` requirement

`BudgetBreakerHealthIndicator.kt`:
- Implements `org.springframework.boot.actuate.health.HealthIndicator`
- `override fun health()` returns `Health.up().withDetail(...)` always — never `Health.down()`
- Detail keys: `agentsTracked`, `softBreaches`, `hardBreaches` (D-07)
- No `@Component`

### Task 3: MetricsEventCollector (MeterBinder + SmartLifecycle, SPRING-03)

`MetricsEventCollector.kt` implementing both `MeterBinder` and `SmartLifecycle`:

Constructor params: `breaker: BudgetCircuitBreaker`, `pricing: ModelPricing`

State (no `var`, no `!!`):
- `private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)`
- `private val running = AtomicBoolean(false)`
- `private val registryRef = AtomicReference<MeterRegistry?>(null)`
- `private val ratioHolders = ConcurrentHashMap<String, AtomicReference<Double>>()`

Lifecycle:
- `bindTo(registry)` stores the registry in `registryRef`; does NOT start collecting
- `start()` sets `running = true` and launches `scope.launch { breaker.events.collect { ... } }`
- `stop()` sets `running = false` and calls `scope.cancel()`
- `isRunning()` returns `running.get()`

Exhaustive `when` over all three `BudgetEvent` variants (no `else`):
- `CallTracked` → increments `gen_ai.client.token.usage.input`, `gen_ai.client.token.usage.output`, `budget.breaker.cost.usd`, updates `budget.breaker.usage.ratio` gauge
- `SoftLimitReached` → increments `budget.breaker.breach{type=soft}`
- `HardLimitExceeded` → increments `budget.breaker.breach{type=hard}`

Gauge pattern: `AtomicReference<Double>` in `ratioHolders` map provides strong reference for Micrometer gauge GC safety.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Added kotlinx-coroutines-core dependency to starter build.gradle.kts**
- **Found during:** Task 3 compile
- **Issue:** `MetricsEventCollector` uses `CoroutineScope`, `SupervisorJob`, `Dispatchers`, `launch`, and `cancel`. The core module declares `kotlinx-coroutines-core` as `implementation` (not `api`), so these symbols are not transitively visible on the starter's compile classpath.
- **Fix:** Added `implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")` to the starter's `dependencies {}` block with an explanatory comment.
- **Files modified:** `budget-breaker-spring-boot-starter/build.gradle.kts`
- **Commit:** c0c2f2e (included in Task 3 commit)

### Notes

- `ktlintFormat` task is not yet available (ktlint plugin not configured in buildSrc). Code was written following Kotlin style conventions manually. Pre-existing gap — tracked in previous plan.
- The `grep -c "@Component"` acceptance check finds matches only in KDoc comments ("Do not add @Component here"), not executable code. All `@Component` references are documentation warnings.
- `grep -c "Health.down"` in BudgetBreakerHealthIndicator returns 1 for the KDoc comment "Never returns [Health.down]" — no callable `Health.down()` exists in the implementation.

## TDD Gate Compliance

Task 1 followed the full RED/GREEN cycle:
- RED commit: `7b95206` — `BudgetBreakerPropertiesTest.kt` fails to compile (unresolved `BudgetBreakerProperties`)
- GREEN commit: `723ebe4` — implementation makes all 7 test cases pass

Task 3 has `tdd="true"` but no test file is included in `<files>` — the behavior verification is the compile-only check. Integration tests (ApplicationContextRunner) are delivered in plan 03.

## Known Stubs

None — all four classes are complete production implementations with no placeholder code. No UI or rendering components.

## Threat Flags

| Flag | File | Description |
|------|------|-------------|
| T-02-03 documented | BudgetEndpoint.kt | KDoc documents that endpoint must be opted-in via `management.endpoints.web.exposure.include`; not auto-exposed |
| T-02-04 mitigated | BudgetBreakerProperties.kt | `init {}` validates hard/soft limits at startup |
| T-02-05 mitigated | MetricsEventCollector.kt | `stop()` cancels coroutine scope |
| T-02-06 accepted | BudgetBreakerHealthIndicator.kt | Always UP per D-07 design decision |

## Self-Check: PASSED
