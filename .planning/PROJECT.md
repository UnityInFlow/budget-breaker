# budget-breaker

## What This Is
A Kotlin coroutine-aware circuit breaker for AI agent token budgets. Enforces soft/hard token limits with clean coroutine cancellation, cost estimation across Claude/GPT/Gemini models, and a Spring Boot starter (auto-config, Actuator endpoint, Micrometer metrics). First JVM library in the UnityInFlow ecosystem. Tool #05.

## Core Value
Stop agent cost overruns before they happen — with reactive budget enforcement that works with Kotlin coroutines, not against them.

## Current State
v1.0 milestone shipped 2026-06-12: core library (v0.0.1 on Maven Central since 2026-05-11) + Spring Boot starter. ~2,100 LOC Kotlin across 2 Gradle modules, 61 commits, Apr 2 → Jun 12. v0.1.0 tag pushed — Maven Central publish of core+starter queued on CI (Hetzner ARC runners offline at close). Phase 2 closed with verification 13/13, threats_open: 0, and all 7 code-review warnings fixed.

## Requirements

### Validated
- ✓ CORE-01: AgentBudget data class — v1.0
- ✓ CORE-02: TokenTracker (thread-safe, AtomicLong) — v1.0
- ✓ CORE-03: BudgetCircuitBreaker (coroutine supervisor, cancel-on-breach) — v1.0
- ✓ CORE-04: BudgetException sealed hierarchy — v1.0
- ✓ CORE-05: ModelPricing (Claude, GPT-4o, Gemini) — v1.0
- ✓ CORE-06: BudgetReport + live in-flight snapshots — v1.0
- ✓ SPRING-01: Auto-configuration with @ConfigurationProperties + @ConditionalOnClass guards — v1.0
- ✓ SPRING-02: Actuator endpoint /actuator/budget (read-only, opt-in exposure) — v1.0
- ✓ SPRING-03: Micrometer metrics (OTel semconv gen_ai.* meters) — v1.0
- ✓ REL-01: Maven Central publish as io.github.unityinflow:budget-breaker — v1.0

### Active
(None — define with `/gsd-new-milestone`. Candidates: kore-runtime integration feedback, Kafka opt-in event sink, per-model budget policies.)

### Out of Scope
- Kafka event streaming — Kotlin Flows by default, Kafka opt-in later (held through v1.0)
- Web UI — token-dashboard (Tool #06) handles this
- LLM API integration — library wraps calls, doesn't make them

## Context
- First Kotlin/JVM tool — set the patterns now reused by #06, #08 and the next 8 JVM tools (Sonatype+GPG publishing convention, multi-module layout, ApplicationContextRunner test style)
- Gradle multi-module: `budget-breaker` (core, dependency-free) + `budget-breaker-spring-boot-starter` (optional)
- Feeds into kore-runtime (#08) which uses this for per-agent budget enforcement — kore v0.1.0 milestone is next
- Token-dashboard (#06) consumes the Micrometer metrics emitted by the starter

## Constraints
- Kotlin 2.0+, JVM 21, Gradle Kotlin DSL
- Coroutines for all async — no Thread.sleep, no raw threads
- Immutable data classes, sealed classes, no var
- ktlint, JUnit 5 + Kotest matchers
- Group: io.github.unityinflow (renamed from dev.unityinflow at first release)
- Core module stays dependency-free (logging via java.util.logging, bridged to SLF4J in Spring apps)
- Self-hosted CI runners (arc-runner-unityinflow)

## Key Decisions
| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Kotlin Flows over Kafka | Zero-config local experience, Kafka opt-in later | ✓ Good — SharedFlow events power metrics + logging with no broker |
| Coroutine supervisor pattern | Clean cancellation on budget breach | ✓ Good — cancel-on-breach proven in tests |
| Multi-module Gradle | Core library usable without Spring | ✓ Good — starter is a true optional add-on |
| AtomicLong for token counter | Thread-safe without coroutine mutex overhead | ✓ Good |
| Nested @ConditionalOnClass configs | Starter must not crash consumers lacking Actuator/Micrometer | ✓ Good — CR-01 gap closure, FilteredClassLoader-tested |
| try/catch per event in collectors | One bad handler event must not silence safety metrics | ✓ Good — CR-02 gap closure |
| java.util.logging in core | Keep core dependency-free; JUL bridges to SLF4J in Spring apps | ✓ Good (WR-05 fix) |
| Version single-sourced in gradle.properties + tag guard in release.yml | Prevent tag/version drift publishing mismatched artifacts | ✓ Good (WR-07 fix) |

---
*Last updated: 2026-06-12 after v1.0 milestone*
