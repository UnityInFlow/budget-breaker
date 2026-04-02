# Tool 05: `budget-breaker`
## Kotlin Coroutine Budget Circuit Breaker — Deep Dive

> **Phase:** 2 · **Effort:** 3/10 · **Impact:** 8/10 · **Stack:** Kotlin  
> **Repo name:** `budget-breaker` · **Maven:** `com.your-org:budget-breaker`  
> **Build in:** Weeks 7–9

---

## 1. Problem Statement

Token cost overruns in autonomous agent runs are a real, growing problem. A single overnight Claude Code session can cost $50–$200. Multi-agent pipelines running in CI can consume millions of tokens with no warning. No library provides a reactive, coroutine-aware circuit breaker for agent token budgets.

This is the first Kotlin library in the ecosystem — the proof point that the JVM story is real.

---

## 2. Feature Specification

### Core API

```kotlin
// Simple usage
val budget = AgentBudget(
    model = ClaudeModel.SONNET,
    hardLimitTokens = 100_000,
    softLimitTokens = 80_000   // warn at 80%
)

val breaker = BudgetCircuitBreaker(budget)

// Wrap your agent loop
breaker.withBudget("my-agent") {
    // any LLM calls here are tracked
    // soft limit: logs warning + emits event
    // hard limit: throws BudgetHardLimitException and cancels scope
    myAgent.run(task)
}
```

### Spring Boot Integration

```yaml
# application.yml
kore:
  budget:
    default-model: claude-sonnet-4-6
    hard-limit-tokens: 100000
    soft-limit-tokens: 80000
    on-soft-limit: WARN         # WARN | PAUSE | EMIT_EVENT
    on-hard-limit: CANCEL       # CANCEL | PAUSE
    alert-webhook: https://hooks.slack.com/...
```

```kotlin
// Auto-wired by Spring Boot starter
@Service
class MyAgentService(
    private val budgetCircuitBreaker: BudgetCircuitBreaker
) {
    suspend fun runAgent(task: String) =
        budgetCircuitBreaker.withBudget("my-agent") {
            // your agent logic
        }
}
```

### Exception Hierarchy

```kotlin
sealed class BudgetException(message: String) : Exception(message)

class BudgetSoftLimitException(
    val agentId: String,
    val tokensUsed: Long,
    val budgetTokens: Long,
    val percentUsed: Double
) : BudgetException("Agent '$agentId' reached soft limit: ${percentUsed.roundToInt()}% used")

class BudgetHardLimitException(
    val agentId: String,
    val tokensUsed: Long,
    val budgetTokens: Long,
    val estimatedCostUsd: Double
) : BudgetException("Agent '$agentId' exceeded hard limit. Estimated cost: \$$estimatedCostUsd")
```

### Built-in Model Pricing

```kotlin
// Pricing as of April 2026 — updatable via config
enum class ClaudeModel(
    val inputCostPerMillionTokens: Double,
    val outputCostPerMillionTokens: Double
) {
    OPUS(15.0, 75.0),
    SONNET(3.0, 15.0),
    HAIKU(0.25, 1.25)
}

object ModelPricing {
    // Also supports OpenAI, Gemini, Ollama (free)
    fun estimateCostUsd(model: LLMModel, tokensUsed: Long): Double
}
```

### Budget Report

```kotlin
data class BudgetReport(
    val agentId: String,
    val model: String,
    val promptTokens: Long,
    val completionTokens: Long,
    val totalTokens: Long,
    val estimatedCostUsd: Double,
    val softLimitBreachCount: Int,
    val hardLimitBreached: Boolean,
    val durationMs: Long
)

// Access after run completes
val report = breaker.getReport("my-agent")
println("Cost: \$${report.estimatedCostUsd.format(4)}")
```

---

## 3. Technical Architecture

### Coroutine Supervisor Pattern

```kotlin
class BudgetCircuitBreaker(private val defaultBudget: AgentBudget) {
    
    suspend fun <T> withBudget(
        agentId: String,
        budget: AgentBudget = defaultBudget,
        block: suspend BudgetScope.() -> T
    ): T = coroutineScope {
        val tracker = TokenTracker(agentId, budget)
        val scope = BudgetScope(this, tracker)
        
        try {
            scope.block()
        } catch (e: BudgetHardLimitException) {
            // Clean coroutine cancellation
            coroutineContext.cancel(CancellationException("Budget exceeded", e))
            throw e
        }
    }
}

// BudgetScope wraps LLM calls to intercept token counts
class BudgetScope(
    private val coroutineScope: CoroutineScope,
    private val tracker: TokenTracker
) {
    // Intercepts token usage from LLM response headers/metadata
    suspend fun trackCall(tokensUsed: Long) {
        tracker.add(tokensUsed)
        when {
            tracker.isAboveHardLimit() -> throw BudgetHardLimitException(...)
            tracker.isAboveSoftLimit() -> tracker.emitSoftLimitEvent()
        }
    }
}
```

### Micrometer Integration

```kotlin
// Emits metrics that token-dashboard (Tool 06) can consume
class BudgetMetrics(private val meterRegistry: MeterRegistry) {
    fun recordTokenUsage(agentId: String, model: String, tokens: Long) {
        meterRegistry.counter("agent.tokens.used",
            "agent", agentId, "model", model
        ).increment(tokens.toDouble())
    }
    
    fun recordBudgetBreach(agentId: String, type: String) {
        meterRegistry.counter("agent.budget.breach",
            "agent", agentId, "type", type
        ).increment()
    }
}
```

---

## 4. Implementation Todos

### Week 7: Core Library

- [ ] Gradle multi-project setup: `budget-breaker/`, `budget-breaker-spring-boot-starter/`
- [ ] Apply `your-org.kotlin-library` convention plugin
- [ ] `AgentBudget` data class with all configuration fields
- [ ] `TokenTracker`: thread-safe token counter with soft/hard limit checks
- [ ] `BudgetCircuitBreaker`: coroutine supervisor with cancel-on-breach
- [ ] `BudgetException` sealed class hierarchy
- [ ] `ModelPricing` object with Claude, GPT-4o, Gemini pricing
- [ ] `BudgetReport` data class and report generation
- [ ] Unit tests: cancellation, soft limit, report accuracy

### Week 8: Spring Boot + Maven Central

- [ ] `BudgetBreakerAutoConfiguration` class
- [ ] `BudgetBreakerProperties` with `@ConfigurationProperties`
- [ ] Spring Boot Actuator endpoint: `/actuator/budget`
- [ ] Health indicator: `BudgetBreakerHealthIndicator`
- [ ] Micrometer metrics integration
- [ ] Configure Maven Central publishing via Sonatype
- [ ] GPG signing configured in GitHub Actions

### Week 9: Polish + Release

- [ ] Integration tests with Spring Boot
- [ ] Performance test: zero overhead on happy path (<1ms)
- [ ] KDoc documentation on all public APIs
- [ ] README: problem statement, installation, 3 usage examples
- [ ] Publish `com.your-org:budget-breaker:0.0.1` to Maven Central
- [ ] Blog post: "Kotlin Coroutines and AI Agent Cost Control"
- [ ] Post to r/kotlin: "I built a budget circuit breaker for AI agents"

---

## 5. Success Metrics

| Metric | Week 9 Target | Month 4 Target |
|---|---|---|
| Maven Central downloads | 20 | 500 |
| GitHub stars | 50 | 300 |
| Spring Boot starter downloads | 10 | 200 |
| r/kotlin upvotes | 30 | — |

---

*Part of the AI Agent Tooling Ecosystem · See 00-MASTER-ANALYSIS.md for full context*
