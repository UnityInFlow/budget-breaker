---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
status: Awaiting next milestone
last_updated: "2026-06-12T15:40:13.869Z"
last_activity: 2026-06-12 — Milestone v1.0 completed and archived
progress:
  total_phases: 2
  completed_phases: 2
  total_plans: 5
  completed_plans: 5
  percent: 100
---

# State: budget-breaker

## Project Reference

See: .planning/PROJECT.md (updated 2026-06-12 after v1.0 milestone)
**Core value:** Stop agent cost overruns with reactive budget enforcement
**Current focus:** Planning next milestone (/gsd-new-milestone) — kore-runtime integration is the ecosystem driver

## Phase 1 — Core Library (COMPLETE)

### Deliverables

- [x] Gradle multi-module setup (budget-breaker + budget-breaker-spring-boot-starter stub)
- [x] AgentBudget data class with soft/hard limit config + validation
- [x] TokenTracker: thread-safe AtomicLong pair (prompt + completion)
- [x] BudgetCircuitBreaker: coroutine supervisor with withBudget() API
- [x] BudgetScope: DSL scope with trackCall() and limit checking
- [x] BudgetException sealed hierarchy (SoftLimit, HardLimit)
- [x] BudgetEvent sealed class (CallTracked, SoftLimitReached, HardLimitExceeded)
- [x] ModelPricing: Claude/GPT/Gemini defaults + custom overrides
- [x] BudgetReport: post-run summary with cost estimation
- [x] Callback + SharedFlow for soft limit events
- [x] Full test suite (BudgetCircuitBreakerTest, TokenTrackerTest, ModelPricingTest, BudgetReportTest)
- [x] CONTRIBUTING.md
- [x] Full README.md with problem statement, 3 usage examples, API reference, model pricing table
- [x] GitHub Release v0.0.1
- [x] v0.1.0 issues created (#3-#8)

### Release

- **Version:** v0.0.1 — published to Maven Central as `io.github.unityinflow:budget-breaker:0.0.1` (2026-05-11)
- **Tag:** https://github.com/UnityInFlow/budget-breaker/releases/tag/v0.0.1
- **PR #1:** Scaffold + types
- **PR #2:** Core engine (TokenTracker, BudgetCircuitBreaker, ModelPricing, BudgetReport, tests)
- **PR #3 (internal numbering):** Release prep (CONTRIBUTING.md, README.md, version bump)

## Phase 2 — Spring Boot Starter + Integrations (In Progress)

### v0.1.0 Issues

- #3 Spring Boot starter (auto-config, actuator, Micrometer)
- #4 SDK interceptors (Anthropic, OpenAI, LangChain4j)
- #5 Kotlin Flow event streaming to token-dashboard
- #6 Kafka opt-in event bus
- ~~#7 Maven Central publishing via Sonatype~~ ✅ Done — publishing infra added, v0.0.1 published 2026-05-11
- #8 Performance benchmark (<1ms overhead)

Remaining Phase 2 scope: Spring Boot starter + integrations (#3–#6, #8).

## Decisions

- `CallTracked.model` appended as last constructor parameter (additive, no break)
- `activeTrackers.remove()` called first in `finally` block (never double-visible as running + completed)
- `getAllReports()` overlays in-flight trackers over completed reports for full-picture snapshot
- `compileOnly` for Spring/Micrometer in starter (consumers bring own managed version)
- No `kapt` in starter (optional; JVM-24 risk per RESEARCH Pitfall 4)
- `AtomicBoolean` for running flag, `AtomicReference<MeterRegistry?>` for registry in MetricsEventCollector (no var)
- HealthIndicator always UP per D-07 (budget breach must never flip k8s probes to DOWN)
- Metric names pinned to `gen_ai.client.token.usage.input/.output` (OtlpMetricMapper.kt contract)
- Gauge backed by `AtomicReference<Double>` in ConcurrentHashMap for Micrometer strong-reference requirement
- `kotlinx-coroutines-core` added as `implementation` to starter (core uses implementation not api)
- `budgetPricing` exposed as separate `@Bean` so breaker and `MetricsEventCollector` share exact same `ModelPricing` instance (D-11 cost consistency)
- No `onSoftLimit` wiring in auto-config — extension point is `BudgetCircuitBreaker.events` Flow (D-18)
- `slf4j-api:2.0.16` added as `compileOnly` to starter (not transitively available from compileOnly Spring Boot deps)
- `@Configuration(proxyBeanMethods=false)` required for Kotlin final inner class in `ApplicationContextRunner` tests
- `@BeforeEach bindTo(registry)` in `@SpringBootTest` test — minimal context doesn't include Spring Boot Micrometer auto-config, bindTo() must be called explicitly
- `runBlocking<Unit>` explicit type annotation required for JUnit 5 test discovery with Kotlin coroutines in @SpringBootTest tests
- KGP 2.1.0 → 2.1.21 upgrade — getDependencyProject() removed in Gradle 9.0; KGP 2.1.21 fixes the DeprecatedPomDependenciesRewriter incompatibility

## Session Notes

- 2026-06-12: Phase 02, Plan 04 complete. @SpringBootTest smoke test (D-19 layer 2), v0.1.0 version bump, starter in nmcp aggregation, README Spring Boot section. Rule 1 fixes: runBlocking<Unit> for JUnit test discovery, @BeforeEach bindTo() for MeterBinder wiring, KGP 2.1.21 for Gradle 9 compat.
- 2026-06-12: Phase 02, Plan 03 complete. SLF4JEventLogger (D-03 WARN logging), BudgetBreakerAutoConfiguration (@AutoConfiguration wiring 5 beans), AutoConfiguration.imports. 4 ApplicationContextRunner tests green. Rule 3 fixes: slf4j-api compileOnly, @Configuration(proxyBeanMethods=false) for Kotlin final class.
- 2026-06-12: Phase 02, Plan 02 complete. Four Spring leaf components: BudgetBreakerProperties, BudgetEndpoint, BudgetBreakerHealthIndicator, MetricsEventCollector. TDD RED/GREEN on properties. Rule 3 fix: coroutines dep added to starter.
- 2026-06-12: Phase 02, Plan 01 complete. Core D-08 enhancement (BudgetSnapshot, live-snapshot APIs), CallTracked.model field, starter build.gradle.kts wired.
- 2026-05-11: Sonatype/Maven Central publishing infra added; v0.0.1 published as `io.github.unityinflow:budget-breaker:0.0.1` (issue #7 closed).
- 2026-04-02: Harness engineering setup complete. Ready for GSD discuss-phase 1.
- 2026-04-01: Phase 1 complete. All core classes implemented, tests passing, v0.0.1 tagged and released.

---
*Last updated: 2026-06-12 (Plan 04 complete — Phase 02 COMPLETE)*

## Current Position

Phase: Milestone v1.0 complete
Plan: —
Status: Awaiting next milestone
Last activity: 2026-06-12 — Milestone v1.0 completed and archived

## Operator Next Steps

- Start the next milestone with /gsd-new-milestone
