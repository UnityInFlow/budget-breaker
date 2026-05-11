# budget-breaker

Kotlin coroutine budget circuit breaker for AI agents -- soft/hard token limits with cost estimation.

**Tool 05** in the [UnityInFlow](https://github.com/UnityInFlow) ecosystem.

## Problem

Overnight agent runs routinely cost $50--$200 when a loop goes unchecked. Existing solutions are either polling-based (too slow), framework-coupled (Spring/Ktor only), or lack coroutine awareness (can't cancel a structured scope cleanly).

No reactive budget enforcement exists for Kotlin coroutines. `budget-breaker` fixes this: it wraps your agent code in a budget-tracked scope, fires a callback at your soft limit, and cancels the coroutine at the hard limit. Zero Spring dependency in the core module.

## Installation

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("io.github.unityinflow:budget-breaker:0.0.1")
}
```

### Gradle (Groovy)

```groovy
dependencies {
    implementation 'io.github.unityinflow:budget-breaker:0.0.1'
}
```

### Maven

```xml
<dependency>
    <groupId>io.github.unityinflow</groupId>
    <artifactId>budget-breaker</artifactId>
    <version>0.0.1</version>
</dependency>
```

## Usage

### Basic `withBudget`

```kotlin
import io.github.unityinflow.budget.*

val budget = AgentBudget(
    model = "claude-sonnet-4-6",
    hardLimitTokens = 100_000,
    softLimitTokens = 80_000,
)

val breaker = BudgetCircuitBreaker()

suspend fun main() {
    try {
        breaker.withBudget(agentId = "research-agent", budget = budget) {
            // Simulate LLM calls inside the budget scope
            trackCall(promptTokens = 500, completionTokens = 200)
            trackCall(promptTokens = 1_000, completionTokens = 400)
            // ...your agent logic here
        }
    } catch (e: BudgetHardLimitException) {
        println("Budget exceeded: ${e.message}")
    }

    // Get the post-run report
    val report = breaker.getReport("research-agent")
    println("Total tokens: ${report?.totalTokens}")
    println("Estimated cost: \$${report?.estimatedCostUsd}")
}
```

### With Soft Limit Callback

When the soft limit is reached, your callback fires but execution continues. Use this for alerts, logging, or graceful wind-down.

```kotlin
val breaker = BudgetCircuitBreaker(
    onSoftLimit = { report ->
        println("WARNING: Agent '${report.agentId}' hit soft limit")
        println("  Tokens used: ${report.totalTokens}")
        println("  Estimated cost: \$${report.estimatedCostUsd}")
        // Send a Slack alert, write to a log, etc.
    }
)

breaker.withBudget(agentId = "summarizer", budget = budget) {
    repeat(100) {
        trackCall(promptTokens = 1_000, completionTokens = 500)
        // Callback fires once when soft limit (80k) is crossed
        // Execution continues until hard limit (100k)
    }
}
```

### With SharedFlow Events

For reactive consumers, `BudgetCircuitBreaker` exposes a `SharedFlow<BudgetEvent>` that emits every call tracked, soft limit reached, and hard limit exceeded.

```kotlin
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect

val breaker = BudgetCircuitBreaker()

fun main() = runBlocking {
    // Collect events in a background coroutine
    val collector = launch {
        breaker.events.collect { event ->
            when (event) {
                is BudgetEvent.CallTracked -> println("Call: +${event.promptTokens}p/${event.completionTokens}c")
                is BudgetEvent.SoftLimitReached -> println("SOFT LIMIT: ${event.percentUsed}% used")
                is BudgetEvent.HardLimitExceeded -> println("HARD LIMIT: \$${event.estimatedCostUsd}")
            }
        }
    }

    try {
        breaker.withBudget(agentId = "streaming-agent", budget = budget) {
            repeat(200) {
                trackCall(promptTokens = 500, completionTokens = 200)
            }
        }
    } catch (_: BudgetHardLimitException) {
        // Expected
    }

    collector.cancel()
}
```

## Model Pricing

Built-in pricing defaults (per million tokens, USD):

| Model | Input | Output |
|---|---:|---:|
| `claude-opus-4-6` | $15.00 | $75.00 |
| `claude-sonnet-4-6` | $3.00 | $15.00 |
| `claude-haiku-4-5` | $0.80 | $4.00 |
| `gpt-4o` | $2.50 | $10.00 |
| `gpt-4o-mini` | $0.15 | $0.60 |
| `gemini-2.5-pro` | $1.25 | $10.00 |
| `gemini-2.5-flash` | $0.15 | $0.60 |
| `ollama` | $0.00 | $0.00 |

### Custom Pricing

Override or add models at construction time:

```kotlin
val pricing = ModelPricing(
    overrides = mapOf(
        "mistral-large" to ModelPricing.PriceConfig(
            inputPerMillion = 2.0,
            outputPerMillion = 6.0,
        )
    )
)

val breaker = BudgetCircuitBreaker(pricing = pricing)
```

## API Reference

### `AgentBudget`

Configuration data class for an agent's token budget.

| Property | Type | Default | Description |
|---|---|---|---|
| `model` | `String` | `"claude-sonnet-4-6"` | LLM model identifier (for cost estimation) |
| `hardLimitTokens` | `Long` | `100_000` | Exceeding this cancels the coroutine scope |
| `softLimitTokens` | `Long` | `80_000` | Triggers callback and Flow event when reached |

### `BudgetCircuitBreaker`

Main entry point. Wraps agent code in a budget-tracked scope.

| Method | Description |
|---|---|
| `withBudget(agentId, budget, block)` | Execute `block` within a budget-tracked `BudgetScope` |
| `getReport(agentId)` | Get the `BudgetReport` for a completed agent run |
| `events` | `SharedFlow<BudgetEvent>` for reactive consumers |

### `BudgetScope`

DSL scope available inside `withBudget`. Call `trackCall()` after each LLM invocation.

| Method | Description |
|---|---|
| `trackCall(promptTokens, completionTokens)` | Record token usage; checks limits after recording |

### `TokenTracker`

Thread-safe token counter using `AtomicLong`.

| Property / Method | Description |
|---|---|
| `promptTokens` | Total prompt tokens recorded |
| `completionTokens` | Total completion tokens recorded |
| `totalTokens` | Sum of prompt + completion |
| `percentUsed()` | Usage as percentage of hard limit |

### `BudgetException` (sealed)

| Subclass | When | Fatal? |
|---|---|---|
| `BudgetSoftLimitException` | Soft limit reached | No |
| `BudgetHardLimitException` | Hard limit exceeded | Yes -- cancels scope |

### `BudgetEvent` (sealed)

| Subclass | When |
|---|---|
| `CallTracked` | Every `trackCall()` invocation |
| `SoftLimitReached` | First time soft limit is crossed |
| `HardLimitExceeded` | Hard limit exceeded (before exception) |

### `BudgetReport`

Post-run summary returned by `getReport()`.

| Field | Type | Description |
|---|---|---|
| `agentId` | `String` | Agent identifier |
| `model` | `String` | LLM model used |
| `promptTokens` | `Long` | Total prompt tokens |
| `completionTokens` | `Long` | Total completion tokens |
| `totalTokens` | `Long` | Sum of prompt + completion |
| `estimatedCostUsd` | `Double` | Estimated cost in USD |
| `softLimitBreachCount` | `Int` | Number of soft limit breaches |
| `hardLimitBreached` | `Boolean` | Whether hard limit was hit |
| `durationMs` | `Long` | Execution duration in milliseconds |
| `percentUsed` | `Double` | Usage as percentage of hard limit |

### `ModelPricing`

Cost estimation with hardcoded defaults and runtime overrides.

| Method | Description |
|---|---|
| `estimateCost(model, promptTokens, completionTokens)` | Estimate cost in USD |
| `ModelPricing.estimateCost(...)` (companion) | Static convenience using default pricing |

## Spring Boot Integration

Coming in v0.1.0:

- `@ConfigurationProperties`-based auto-configuration
- `/actuator/budget` endpoint
- Micrometer metrics integration (counters, gauges, timers)

The core library has zero Spring dependency -- it works in any Kotlin project.

## Requirements

- JDK 21+
- Kotlin 2.0+
- `kotlinx-coroutines-core` 1.10+

## License

[MIT](LICENSE)
