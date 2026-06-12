# Project Retrospective

*A living document updated after each milestone. Lessons feed forward into future planning.*

## Milestone: v1.0 — Core + Spring Boot Starter

**Shipped:** 2026-06-12
**Phases:** 2 | **Plans:** 5 (Phase 2; Phase 1 pre-dates plan tracking) | **Tasks:** 14

### What Was Built
- Core Kotlin library: AgentBudget, TokenTracker, BudgetCircuitBreaker (coroutine supervisor with cancel-on-breach), sealed exception/event hierarchies, ModelPricing, BudgetReport with live in-flight snapshots
- Spring Boot starter: auto-configuration with @ConditionalOnClass guards, read-only /actuator/budget endpoint, always-UP health indicator, Micrometer collector emitting OTel semconv gen_ai.* meters, SLF4J soft-limit logging
- Release machinery: GPG-signed dual-module publish via nmcp aggregation, tag-vs-version guard in release.yml, version single-sourced in gradle.properties

### What Worked
- Wave-based plan decomposition (core → leaf components → wiring → integration test/release) kept every executor scoped and reviewable
- The gap-closure loop did its job: verification caught two BLOCKERs (classpath crash, silenced collectors) that unit-green plans missed, and plan 02-05 closed both with FilteredClassLoader and resilience tests
- Plan-time threat modelling made the security audit a pure verification pass — 15/15 threats closed with file/line evidence, no retroactive STRIDE needed
- Code review --fix cleared all 7 warnings in one pass, each as an atomic verified commit

### What Was Inefficient
- An executor worktree forked from a stale base (pre-Phase-2 commit) and self-recovered by porting files instead of halting; the orchestrator had to recover via file-level apply instead of a branch merge
- Phase 1 was built before GSD plan tracking, leaving directory-naming debt (phase-1/ vs 01-slug/) that blocked milestone.complete until renamed
- Phase 2 work accumulated locally across multiple sessions without pushing — the v0.1.0 push delivered ~50 commits at once, and CI runner outages went unnoticed meanwhile

### Patterns Established
- Sonatype+GPG publishing convention plugin reusable for the next 9 JVM tools
- Starter pattern: core module dependency-free (JUL logging), optional deps behind nested @ConditionalOnClass configs, ApplicationContextRunner + FilteredClassLoader test style
- Event resilience contract: per-event try/catch, CancellationException always re-thrown, fresh Job per SmartLifecycle start()

### Key Lessons
1. Optional-dependency starters must be tested with FilteredClassLoader from the first plan — classpath safety is not discoverable from unit tests that have everything on the classpath.
2. Push early and often; local-only milestones hide CI outages until release day.
3. When a worktree base mismatch is detected, halt and let the orchestrator recover — self-recovery by porting files produces unmergeable branches.

### Cost Observations
- Model mix: orchestrator on default model, executors/verifier/auditor/fixer on sonnet
- Phase 2 close-out session: 1 executor, 1 reviewer, 1 verifier, 1 security auditor, 1 fixer subagent
- Notable: fixer's isolated-worktree + fast-forward pattern delivered 7 verified fixes with zero main-tree risk

---

## Cross-Milestone Trends

### Process Evolution
- v1.0: established the full GSD loop (plan → execute → verify → gap-close → secure → review-fix) for JVM tools; next milestone should start with plan tracking from day one.
