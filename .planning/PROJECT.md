# budget-breaker

## What This Is
A Kotlin coroutine-aware circuit breaker for AI agent token budgets. Enforces soft/hard token limits with clean coroutine cancellation, cost estimation across Claude/GPT/Gemini models, and Micrometer metrics. First JVM library in the UnityInFlow ecosystem. Tool #05.

## Core Value
Stop agent cost overruns before they happen — with reactive budget enforcement that works with Kotlin coroutines, not against them.

## Requirements

### Active
- [ ] **CORE-01**: AgentBudget data class with soft/hard limit configuration
- [ ] **CORE-02**: TokenTracker — thread-safe counter with limit checks
- [ ] **CORE-03**: BudgetCircuitBreaker — coroutine supervisor with cancel-on-breach
- [ ] **CORE-04**: BudgetException sealed hierarchy (SoftLimit, HardLimit)
- [ ] **CORE-05**: ModelPricing with Claude, GPT-4o, Gemini pricing
- [ ] **CORE-06**: BudgetReport data class and report generation
- [ ] **SPRING-01**: Auto-configuration with @ConfigurationProperties
- [ ] **SPRING-02**: Actuator endpoint /actuator/budget
- [ ] **SPRING-03**: Micrometer metrics integration
- [ ] **REL-01**: Published to Maven Central as dev.unityinflow:budget-breaker

### Out of Scope
- Kafka event streaming — v0.1.0, Kotlin Flows by default
- Web UI — token-dashboard (Tool #06) handles this
- LLM API integration — library wraps calls, doesn't make them

## Context
- First Kotlin/JVM tool — sets patterns for all future Kotlin tools (#06, #08, #09, #11, #13, #17, #19, #20)
- Gradle multi-module: `budget-breaker` (core) + `budget-breaker-spring-boot-starter` (optional)
- Feeds into kore-runtime (#08) which uses this for per-agent budget enforcement
- Token-dashboard (#06) consumes Micrometer metrics emitted by this library

## Constraints
- Kotlin 2.0+, JVM 21, Gradle Kotlin DSL
- Coroutines for all async — no Thread.sleep, no raw threads
- Immutable data classes, sealed classes, no var
- ktlint, JUnit 5 + Kotest matchers
- Group: dev.unityinflow
- Self-hosted CI runners

## Key Decisions
| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Kotlin Flows over Kafka | Zero-config local experience, Kafka opt-in later | — Pending |
| Coroutine supervisor pattern | Clean cancellation on budget breach | — Pending |
| Multi-module Gradle | Core library usable without Spring | — Pending |
| AtomicLong for token counter | Thread-safe without coroutine mutex overhead | — Pending |

---
*Last updated: 2026-04-02*
