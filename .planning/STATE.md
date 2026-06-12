---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
status: unknown
last_updated: "2026-06-12T11:02:17.087Z"
progress:
  total_phases: 3
  completed_phases: 0
  total_plans: 0
  completed_plans: 0
  percent: 0
---

# State: budget-breaker

## Project Reference

See: .planning/PROJECT.md (updated 2026-04-02)
**Core value:** Stop agent cost overruns with reactive budget enforcement
**Current focus:** v0.0.1 live on Maven Central (2026-05-11). Phase 2 remaining scope: Spring Boot starter + integrations.

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

## Session Notes

- 2026-05-11: Sonatype/Maven Central publishing infra added; v0.0.1 published as `io.github.unityinflow:budget-breaker:0.0.1` (issue #7 closed).
- 2026-04-02: Harness engineering setup complete. Ready for GSD discuss-phase 1.
- 2026-04-01: Phase 1 complete. All core classes implemented, tests passing, v0.0.1 tagged and released.

---
*Last updated: 2026-06-12*
