# Phase 1 Context: Core Library

**Phase:** 1
**Date:** 2026-04-02
**Status:** Ready for planning

## Decisions

### CORE-02: Token counter — AtomicLong pair (prompt + completion)
Two AtomicLong counters: `promptTokens` and `completionTokens`. Lock-free, zero overhead on happy path. Total tokens = sum. Required for accurate cost estimation since input and output pricing differs (e.g. Sonnet: $3/M input vs $15/M output).

### CORE-03: Budget enforcement — caller-driven core
User calls `budgetScope.trackCall(promptTokens, completionTokens)` after each LLM call. Library does not know about LLM SDKs. Works with any provider. SDK-specific interceptors (Anthropic, OpenAI, LangChain4j) deferred to v0.1.0 as optional extension modules.

### CORE-04: Soft limit behavior — callback + Flow
Immediate callback lambda `onSoftLimit: (BudgetReport) -> Unit` for simple use cases (logging, Slack alerts). Plus a `SharedFlow<BudgetEvent>` for reactive consumers that want to collect events. Both optional — if neither is provided, soft limit is silently tracked in the report.

### CORE-05: Model pricing — hardcoded defaults + config override
Built-in enum with current prices (Claude Opus/Sonnet/Haiku, GPT-4o, Gemini). Works with zero config. Users can override via constructor parameter (core library) or `application.yml` (Spring Boot starter). Prices change frequently — override avoids waiting for library releases.

### Multi-module: Two modules
- `budget-breaker` — core library, zero Spring deps, just kotlinx-coroutines
- `budget-breaker-spring-boot-starter` — auto-config, actuator, Micrometer, properties

Micrometer lives in the Spring starter module (not separate). Three modules is overkill for v0.0.1.

## Deferred Ideas

- SDK-specific interceptors (Anthropic, OpenAI, LangChain4j) — v0.1.0
- Kotlin Flow event streaming to token-dashboard — v0.1.0
- Kafka event bus — v0.1.0 opt-in
- Web UI — token-dashboard (#06) handles this

## Downstream Notes

- **Planner:** Core module has ZERO Spring/framework dependencies
- **Planner:** Two AtomicLong counters, not one — prompt + completion
- **Planner:** trackCall() is the only way to record usage in v0.0.1 (caller-driven)
- **Planner:** Soft limit fires both callback AND Flow event
- **Planner:** ModelPricing has hardcoded defaults but accepts overrides via Map<String, PriceConfig>
- **Planner:** Phase 1 is core library only — Spring starter is Phase 2

---
*Created: 2026-04-02 after discuss-phase 1*
