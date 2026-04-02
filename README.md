# budget-breaker

Kotlin coroutine budget circuit breaker for AI agents — soft/hard token limits with cost estimation.

**Tool 05** in the [UnityInFlow](https://github.com/UnityInFlow) ecosystem.

## Problem

AI agents calling LLMs can silently burn through token budgets. Without a circuit breaker,
a runaway agent loop can consume thousands of dollars in minutes. `budget-breaker` provides
coroutine-aware soft and hard token limits with clean cancellation semantics.

## Features

- **Soft limits** — callback + SharedFlow event when threshold is reached; execution continues
- **Hard limits** — coroutine scope cancelled via `BudgetHardLimitException`
- **Thread-safe** — AtomicLong counters for lock-free concurrent tracking
- **Cost estimation** — built-in pricing for Claude, GPT-4o, Gemini models
- **Zero Spring dependency** — core library is pure Kotlin + coroutines
- **Spring Boot starter** — optional auto-configuration (Phase 2)

## Installation

```kotlin
// build.gradle.kts
dependencies {
    implementation("dev.unityinflow:budget-breaker:0.0.1-SNAPSHOT")
}
```

## Quick Start

```kotlin
val budget = AgentBudget(
    model = "claude-sonnet-4-6",
    hardLimitTokens = 100_000,
    softLimitTokens = 80_000,
)

val breaker = BudgetCircuitBreaker(agentId = "my-agent", budget = budget)

breaker.withBudget {
    // Your agent code here
    trackCall(promptTokens = 500, completionTokens = 200)
}
```

## Status

v0.0.1-SNAPSHOT — Phase 1 (core library) in progress.

## License

MIT
