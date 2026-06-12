# Phase 2: Spring Boot Starter + Release — Research

**Researched:** 2026-06-12
**Domain:** Spring Boot auto-configuration / Micrometer / Actuator / Maven Central multi-module release
**Confidence:** MEDIUM

---

<user_constraints>

## User Constraints (from CONTEXT.md)

### Locked Decisions

**Configuration Properties (SPRING-01)**
- D-01: Property prefix `budget-breaker.*`
- D-02: Knobs: `default-model`, `soft-limit-tokens`, `hard-limit-tokens`, `pricing` map (per-model input/output per-million prices)
- D-03: Starter auto-subscribes to `events: SharedFlow<BudgetEvent>` and logs soft-limit breaches at WARN via SLF4J
- D-04: Zero-config boot with sensible defaults (sonnet-class model, 100k hard / 80k soft); invalid combos fail startup fast

**Bean Surface (SPRING-01)**
- D-17: Auto-config registers ONE app-facing bean: `BudgetCircuitBreaker` guarded with `@ConditionalOnMissingBean`
- D-18: Extension point for custom behavior is `events: SharedFlow<BudgetEvent>`; auto-config does NOT wire a `onSoftLimit` callback bean

**Actuator Endpoint (SPRING-02)**
- D-05: `GET /actuator/budget` returns map of all tracked agents; `GET /actuator/budget/{agentId}` returns one full BudgetReport
- D-06: Read-only (GET only); no reset/mutation
- D-07: `BudgetBreakerHealthIndicator` — always UP, contributing details (agentsTracked, softBreaches, hardBreaches); breach MUST NOT flip to DOWN
- D-08: Endpoint shows live in-flight agents; requires small core enhancement to expose snapshot of active trackers with `running: true/false`

**Micrometer Metrics (SPRING-03)**
- D-09: Metric names follow OTel GenAI semconv: `gen_ai.client.token.usage.input` / `gen_ai.client.token.usage.output` (counters, {agent, model}); `budget.breaker.breach` (counter, {agent, type=soft|hard}); `budget.breaker.usage.ratio` (gauge, {agent})
- D-10: `MetricsEventCollector` subscribes to core events Flow; NOT inline instrumentation
- D-11: `budget.breaker.cost.usd` counter {agent, model} computed via core `ModelPricing`

**Release (REL-01)**
- D-12: Tag v0.1.0 releasing core + starter together (uniform version); core needs re-release for D-08 enhancement
- D-13: Spring Boot baseline 3.5.x (compile against 3.5)
- D-14: Add starter to nmcp aggregation in root build; apply existing `budget-breaker.publishing` convention plugin
- D-15: README-only demonstration — no examples/ sample app shipped
- D-19: Three testing layers: (1) ApplicationContextRunner unit tests, (2) one @SpringBootTest smoke test hitting /actuator/budget and checking gen_ai.* metrics, (3) THROWAWAY local demo app against publishToMavenLocal artifact

### Claude's Discretion
- Whether to resolve model tag for `CallTracked` events at collection time (via AgentBudget) or by adding `model` field to `CallTracked`
- Actuator endpoint exposure follows Spring conventions; document, don't auto-expose
- Flow subscription lifecycle: which CoroutineScope, buffer/overflow handling for `extraBufferCapacity = 64`
- Exact default values for zero-config budget limits

### Deferred Ideas (OUT OF SCOPE)
- Per-agent yml map (`budget-breaker.agents.<id>.*`) — v0.2.0
- `on-soft-limit` / `on-hard-limit` enums incl. PAUSE — needs core pause support
- `alert-webhook` (Slack) — v0.1.0+
- examples/ runnable sample app
- SDK interceptors (Anthropic/OpenAI/LangChain4j), Kafka opt-in event bus

</user_constraints>

---

<phase_requirements>

## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| SPRING-01 | Auto-configuration: `@ConfigurationProperties`, `BudgetCircuitBreaker` bean, SLF4J WARN logging | Auto-config patterns (D-17), properties binding (D-01–D-04), `@ConditionalOnMissingBean` |
| SPRING-02 | Actuator endpoint `/actuator/budget` + health indicator | `@Endpoint(id="budget")` with `@ReadOperation`, `@Selector`, `HealthIndicator` impl (D-05–D-08) |
| SPRING-03 | Micrometer metrics via `MetricsEventCollector` + `MeterBinder` | Counter/Gauge registration, Flow subscription lifecycle (D-09–D-11) |
| REL-01 | Maven Central publish core + starter as v0.1.0 | nmcp aggregation extension, uniform-version bump, D-14 |

</phase_requirements>

---

## Summary

This phase completes the `budget-breaker-spring-boot-starter` module — currently a bare stub (`kotlin("jvm")` plugin only) — and releases core + starter together as `v0.1.0` on Maven Central. Three implementation tracks run in parallel:

**SPRING-01 (auto-config):** Create `BudgetBreakerAutoConfiguration` annotated with `@AutoConfiguration`, registered in `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`. A `BudgetBreakerProperties` Kotlin data class with constructor binding (prefix `budget-breaker.*`) drives the single `BudgetCircuitBreaker` bean. `@ConditionalOnMissingBean` allows user override. The auto-config also starts the SLF4J WARN logger by subscribing to the events Flow.

**SPRING-02 + SPRING-03 (observability):** A `BudgetEndpoint` class (`@Endpoint(id="budget")`) with two `@ReadOperation` methods covers the aggregate and per-agent views. `BudgetBreakerHealthIndicator` (always UP) adds detail keys from aggregate counters. A `MetricsEventCollector` implements `MeterBinder` and subscribes to the core `SharedFlow<BudgetEvent>` to drive Micrometer counters/gauges in OTel GenAI semconv naming. Both components need a coroutine scope tied to Spring lifecycle — `SmartLifecycle` (`start()`/`stop()`) with `SupervisorJob` + `Dispatchers.Default` is the recommended pattern.

**REL-01 (release):** One line added to the root `build.gradle.kts` `nmcp` aggregation dependencies block adds the starter to the bundle. The starter applies the existing `budget-breaker.publishing` convention plugin and adds a pom override with its own `name`/`description`. Version bumped from `0.0.1` to `0.1.0` in root `allprojects`. A small core enhancement (D-08: live-snapshot map) is needed before v0.1.0 tagging.

**Primary recommendation:** Implement in order: (1) core D-08 snapshot enhancement, (2) starter build setup + auto-config wiring, (3) Actuator endpoint, (4) Micrometer MeterBinder, (5) tests at all three layers, (6) README Spring section, (7) version bump + release.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| `BudgetCircuitBreaker` bean instantiation | Spring application context | — | Bean lifecycle owned by IoC container; auto-config provides the factory |
| Properties binding (`budget-breaker.*`) | Spring Boot config layer | — | `@ConfigurationProperties` with constructor binding; no custom parsing |
| SLF4J WARN logging on soft breach | Starter module | — | Subscriber to `events` Flow; starter-side concern, not core |
| Actuator endpoint (read data) | Actuator layer (management HTTP) | Core library (data source) | Core exposes snapshot; endpoint formats and serves it |
| Health indicator | Actuator health aggregator | — | Always UP; decorates existing `/actuator/health` with budget details |
| Micrometer metrics | Micrometer `MeterRegistry` | Core SharedFlow | `MeterBinder` subscribes to Flow events to update counters/gauges |
| Coroutine lifecycle management | Spring `SmartLifecycle` | `kotlinx-coroutines` | Spring lifecycle calls `start()`/`stop()`; scope cancelled on stop |
| Maven Central publication | Sonatype Central Portal (via nmcp) | CI release.yml | Existing proven pipeline; phase just adds starter to the bundle |

---

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `org.springframework.boot:spring-boot-autoconfigure` | 3.5.3 [VERIFIED: maven central] | `@AutoConfiguration`, `@ConditionalOnMissingBean`, `@ConfigurationProperties` | Required by any SB3 starter |
| `org.springframework.boot:spring-boot-actuator-autoconfigure` | 3.5.3 [VERIFIED: maven central] | `@Endpoint`, `@ReadOperation`, `HealthIndicator` | Required for custom Actuator endpoint |
| `io.micrometer:micrometer-core` | 1.15.0 [VERIFIED: maven central] | `MeterRegistry`, `Counter`, `Gauge`, `MeterBinder` | Standard Micrometer API; Spring Boot BOM manages version |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core` | 1.10.2 [VERIFIED: maven central] | `CoroutineScope`, `SupervisorJob`, `Flow.collect` | Already in core; inherited via project |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `org.springframework.boot:spring-boot-configuration-processor` | 3.5.3 [VERIFIED: maven central] | Generates `spring-configuration-metadata.json` for IDE completion | `kapt` configuration in build.gradle.kts — enables IntelliJ property hints |
| `org.springframework.boot:spring-boot-starter-test` | 3.5.3 [VERIFIED: maven central] | `ApplicationContextRunner`, `@SpringBootTest`, `MockMvc` | Test scope only |
| `io.micrometer:micrometer-test` | 1.15.0 [ASSUMED] | `SimpleMeterRegistry` for unit testing metrics | Test scope; verifies counter/gauge values without real registry |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `SmartLifecycle` for scope management | `DisposableBean` + `InitializingBean` | SmartLifecycle gives phase ordering and async stop; DisposableBean is simpler but no ordering guarantees |
| `MeterBinder` for metrics registration | Inject `MeterRegistry` directly in auto-config | MeterBinder is the library-safe pattern — defers registration until registry is available |
| Kotlin kapt for config processor | Skip processor entirely | Processor is optional (just IDE hints); skipping avoids kapt complexity if not needed |

**Installation (starter build.gradle.kts):**
```kotlin
plugins {
    kotlin("jvm")
    id("budget-breaker.publishing")
}

dependencies {
    api(project(":budget-breaker"))
    compileOnly("org.springframework.boot:spring-boot-autoconfigure:3.5.3")
    compileOnly("org.springframework.boot:spring-boot-actuator-autoconfigure:3.5.3")
    compileOnly("io.micrometer:micrometer-core:1.15.0")
    kapt("org.springframework.boot:spring-boot-configuration-processor:3.5.3")

    testImplementation(kotlin("test"))
    testImplementation("org.springframework.boot:spring-boot-starter-test:3.5.3")
    testImplementation("io.micrometer:micrometer-core:1.15.0")
}
```

> **`compileOnly` vs `implementation` for Spring deps:** The starter declares Spring Boot deps as `compileOnly` (or `optional` in Maven terms). Users bring their own Spring Boot BOM; the starter should not force a specific SB version onto them. `api(project(":budget-breaker"))` ensures core is a transitive dependency — apps need only declare the starter.

---

## Package Legitimacy Audit

All packages below are from the Spring, Micrometer, or JetBrains organizations and are long-established in the JVM ecosystem.

| Package | Registry | Age | Downloads | Source Repo | Verdict | Disposition |
|---------|----------|-----|-----------|-------------|---------|-------------|
| `org.springframework.boot:spring-boot-autoconfigure` | Maven Central | 10+ yrs | Hundreds M | github.com/spring-projects/spring-boot | OK | Approved |
| `org.springframework.boot:spring-boot-actuator-autoconfigure` | Maven Central | 10+ yrs | Hundreds M | github.com/spring-projects/spring-boot | OK | Approved |
| `org.springframework.boot:spring-boot-configuration-processor` | Maven Central | 10+ yrs | Hundreds M | github.com/spring-projects/spring-boot | OK | Approved |
| `io.micrometer:micrometer-core` | Maven Central | 7+ yrs | Hundreds M | github.com/micrometer-metrics/micrometer | OK | Approved |
| `org.springframework.boot:spring-boot-starter-test` | Maven Central | 10+ yrs | Hundreds M | github.com/spring-projects/spring-boot | OK | Approved |

**Packages removed due to SLOP verdict:** none
**Packages flagged as suspicious SUS:** none

---

## Architecture Patterns

### System Architecture Diagram

```
application.yml (budget-breaker.*)
         |
         v
BudgetBreakerProperties  ──── @ConfigurationProperties("budget-breaker")
         |
         v
BudgetBreakerAutoConfiguration  ──── @AutoConfiguration
         |
         |──── creates ──── BudgetCircuitBreaker (BudgetBreakerBean)
         |                       |
         |                       |── events: SharedFlow<BudgetEvent>
         |                       |       |
         |                       |       +──► SLF4JEventLogger (starts in auto-config)
         |                       |       |        \__ WARN on SoftLimitReached
         |                       |       |
         |                       |       +──► MetricsEventCollector (MeterBinder)
         |                       |                \__ increments Counters / updates Gauges
         |                       |
         |                       +── reports: ConcurrentHashMap<String, BudgetReport>
         |                       |       |
         |                       |       +──► BudgetEndpoint (@Endpoint id="budget")
         |                       |                 GET /actuator/budget
         |                       |                 GET /actuator/budget/{agentId}
         |                       |
         |                       +── activeTrackers (D-08 enhancement)
         |                               +──► BudgetEndpoint (running: true/false flag)
         |
         |──── creates ──── BudgetBreakerHealthIndicator
         |                       \__ always UP, detail: agentsTracked, softBreaches, hardBreaches
         |
         +──── creates ──── MetricsEventCollector (as MeterBinder bean)
                                 \__ bindTo(MeterRegistry) → subscribes to events Flow
                                         gen_ai.client.token.usage.input  (Counter, {agent, model})
                                         gen_ai.client.token.usage.output (Counter, {agent, model})
                                         budget.breaker.breach            (Counter, {agent, type})
                                         budget.breaker.usage.ratio       (Gauge,   {agent})
                                         budget.breaker.cost.usd          (Counter, {agent, model})
```

### Recommended Project Structure

```
budget-breaker-spring-boot-starter/
├── src/
│   ├── main/
│   │   ├── kotlin/io/github/unityinflow/budget/spring/
│   │   │   ├── BudgetBreakerAutoConfiguration.kt    # @AutoConfiguration root
│   │   │   ├── BudgetBreakerProperties.kt           # @ConfigurationProperties data class
│   │   │   ├── BudgetEndpoint.kt                    # @Endpoint(id="budget")
│   │   │   ├── BudgetBreakerHealthIndicator.kt      # HealthIndicator (always UP)
│   │   │   ├── MetricsEventCollector.kt             # MeterBinder + coroutine scope
│   │   │   └── SLF4JEventLogger.kt                  # WARN logger for SoftLimitReached
│   │   └── resources/
│   │       └── META-INF/spring/
│   │           └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
│   └── test/
│       └── kotlin/io/github/unityinflow/budget/spring/
│           ├── BudgetBreakerAutoConfigurationTest.kt  # ApplicationContextRunner tests
│           └── BudgetStarterIntegrationTest.kt        # @SpringBootTest smoke test
└── build.gradle.kts
```

### Pattern 1: Spring Boot Auto-Configuration with @AutoConfiguration

**What:** `@AutoConfiguration` replaces plain `@Configuration` for starter auto-configs in SB 3.x. It is registered via the imports file rather than component scanning.

**When to use:** Always for library starters — never use `@Component` scanning in a starter jar.

**Example:**
```kotlin
// Source: https://docs.spring.io/spring-boot/reference/features/developing-auto-configuration.html
@AutoConfiguration
@EnableConfigurationProperties(BudgetBreakerProperties::class)
class BudgetBreakerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    fun budgetCircuitBreaker(properties: BudgetBreakerProperties): BudgetCircuitBreaker {
        return BudgetCircuitBreaker(
            defaultBudget = AgentBudget(
                model = properties.defaultModel,
                hardLimitTokens = properties.hardLimitTokens,
                softLimitTokens = properties.softLimitTokens,
            ),
            pricing = ModelPricing(
                overrides = properties.pricing.mapValues { (_, v) ->
                    ModelPricing.PriceConfig(v.inputPerMillion, v.outputPerMillion)
                }
            ),
        )
    }

    // SLF4J WARN logger bean — subscribes to events Flow
    @Bean
    fun budgetEventLogger(breaker: BudgetCircuitBreaker): SLF4JEventLogger =
        SLF4JEventLogger(breaker)

    // MeterBinder — registered by Spring Boot metrics auto-config
    @Bean
    fun budgetMetricsCollector(breaker: BudgetCircuitBreaker): MetricsEventCollector =
        MetricsEventCollector(breaker)

    @Bean
    fun budgetEndpoint(breaker: BudgetCircuitBreaker): BudgetEndpoint =
        BudgetEndpoint(breaker)

    @Bean
    fun budgetHealthIndicator(breaker: BudgetCircuitBreaker): BudgetBreakerHealthIndicator =
        BudgetBreakerHealthIndicator(breaker)
}
```

**Registration file** at `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`:
```
io.github.unityinflow.budget.spring.BudgetBreakerAutoConfiguration
```

### Pattern 2: @ConfigurationProperties Kotlin Data Class

**What:** Immutable `val` data class with `@ConfigurationProperties` prefix. Constructor binding works natively in SB 3.x — no `@ConstructorBinding` annotation needed.

**When to use:** All external configuration in a starter.

**Example:**
```kotlin
// Source: https://docs.spring.io/spring-boot/reference/features/kotlin.html
@ConfigurationProperties("budget-breaker")
data class BudgetBreakerProperties(
    /** Default LLM model identifier for cost estimation. */
    val defaultModel: String = "claude-sonnet-4-6",
    /** Hard limit in tokens — exceeding this cancels the agent's coroutine scope. */
    val hardLimitTokens: Long = 100_000,
    /** Soft limit in tokens — triggers WARN log and Flow event. */
    val softLimitTokens: Long = 80_000,
    /** Per-model pricing overrides (input/output cost per million tokens). */
    val pricing: Map<String, ModelPriceProperties> = emptyMap(),
) {
    init {
        require(hardLimitTokens > 0) { "budget-breaker.hard-limit-tokens must be positive" }
        require(softLimitTokens > 0) { "budget-breaker.soft-limit-tokens must be positive" }
        require(softLimitTokens <= hardLimitTokens) {
            "budget-breaker.soft-limit-tokens must be <= hard-limit-tokens"
        }
    }
}

data class ModelPriceProperties(
    val inputPerMillion: Double = 0.0,
    val outputPerMillion: Double = 0.0,
)
```

### Pattern 3: Custom Actuator Endpoint

**What:** `@Endpoint(id="budget")` with `@ReadOperation` for GET and `@Selector` for path variables.

**When to use:** Read-only operational views over domain data.

**Example:**
```kotlin
// Source: https://docs.spring.io/spring-boot/reference/actuator/endpoints.html
@Endpoint(id = "budget")
class BudgetEndpoint(private val breaker: BudgetCircuitBreaker) {

    @ReadOperation
    fun budget(): Map<String, Any> {
        // Returns all tracked reports plus active snapshot (D-08)
        return breaker.getAllReports().mapValues { (_, report) -> report }
    }

    @ReadOperation
    fun agentBudget(@Selector agentId: String): BudgetReport? {
        return breaker.getReport(agentId)
    }
}
```

Expose by adding to application.yml:
```yaml
management:
  endpoints:
    web:
      exposure:
        include: "health,info,budget"
```

### Pattern 4: HealthIndicator (Always UP)

**What:** Implement `HealthIndicator`, return `Health.up()` with detail keys. Bean name minus `HealthIndicator` suffix becomes the key in `/actuator/health`.

```kotlin
// Source: https://docs.spring.io/spring-boot/reference/actuator/endpoints.html
class BudgetBreakerHealthIndicator(
    private val breaker: BudgetCircuitBreaker,
) : HealthIndicator {

    override fun health(): Health =
        Health.up()
            .withDetail("agentsTracked", breaker.getActiveTrackerCount())
            .withDetail("softBreaches", breaker.getTotalSoftBreaches())
            .withDetail("hardBreaches", breaker.getTotalHardBreaches())
            .build()
}
```

### Pattern 5: MeterBinder for Library Metrics

**What:** Implement `MeterBinder`; register as a Spring Bean. Spring Boot's Micrometer auto-config calls `bindTo(registry)` after the registry is fully configured. This is the correct library pattern — do NOT inject `MeterRegistry` directly in the auto-config constructor.

**When to use:** Any library/starter that exposes metrics.

```kotlin
// Source: https://docs.spring.io/spring-boot/reference/actuator/metrics.html
class MetricsEventCollector(
    private val breaker: BudgetCircuitBreaker,
) : MeterBinder, SmartLifecycle {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var running = false

    override fun bindTo(registry: MeterRegistry) {
        // Register usage ratio gauge per tracked agent
        // Counters are registered lazily on first event
        this.registry = registry
    }

    override fun start() {
        running = true
        scope.launch {
            breaker.events.collect { event ->
                when (event) {
                    is BudgetEvent.CallTracked -> {
                        registry.counter(
                            "gen.ai.client.token.usage.input",
                            "agent", event.agentId,
                            "model", resolveModel(event.agentId),
                        ).increment(event.promptTokens.toDouble())
                        // ... output counter, cost counter
                    }
                    is BudgetEvent.SoftLimitReached ->
                        registry.counter(
                            "budget.breaker.breach",
                            "agent", event.agentId,
                            "type", "soft",
                        ).increment()
                    // etc.
                }
            }
        }
    }

    override fun stop() {
        running = false
        scope.cancel()
    }

    override fun isRunning(): Boolean = running
}
```

> **Important:** Metric name `gen.ai.client.token.usage.input` uses dots (Micrometer convention). The OTel exporter converts dots to underscores as needed.

### Pattern 6: Coroutine Scope with Spring SmartLifecycle

**What:** Use `SmartLifecycle` (`start()`/`stop()`/`isRunning()`) to manage `CoroutineScope` lifetime. `SupervisorJob` prevents one failed child from cancelling the whole scope.

**When to use:** Any Spring bean that needs a background coroutine that should live for the entire Spring context lifetime.

```kotlin
// Source: [ASSUMED] — standard JVM Spring + coroutines pattern
class SLF4JEventLogger(
    private val breaker: BudgetCircuitBreaker,
) : SmartLifecycle {

    private val log = LoggerFactory.getLogger(SLF4JEventLogger::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var running = false

    override fun start() {
        running = true
        scope.launch {
            breaker.events.collect { event ->
                if (event is BudgetEvent.SoftLimitReached) {
                    log.warn(
                        "Agent '{}' reached soft budget limit: {:.1f}% used ({}/{})",
                        event.agentId, event.percentUsed, event.tokensUsed, event.budgetTokens,
                    )
                }
            }
        }
    }

    override fun stop() {
        running = false
        scope.cancel()
    }

    override fun isRunning(): Boolean = running
}
```

### Pattern 7: ApplicationContextRunner Tests for Auto-Config

**What:** Test-scope `ApplicationContextRunner` setup — no Spring context needed per test.

```kotlin
// Source: https://docs.spring.io/spring-boot/reference/features/developing-auto-configuration.html#features.developing-auto-configuration.testing
class BudgetBreakerAutoConfigurationTest {

    private val contextRunner = ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(BudgetBreakerAutoConfiguration::class.java))

    @Test
    fun `registers BudgetCircuitBreaker with defaults`() {
        contextRunner.run { ctx ->
            assertThat(ctx).hasSingleBean(BudgetCircuitBreaker::class.java)
        }
    }

    @Test
    fun `honors user-defined BudgetCircuitBreaker bean`() {
        contextRunner
            .withUserConfiguration(CustomBreakerConfig::class.java)
            .run { ctx ->
                assertThat(ctx).hasSingleBean(BudgetCircuitBreaker::class.java)
                assertThat(ctx.getBean(BudgetCircuitBreaker::class.java))
                    .isSameAs(ctx.getBean("customBreaker"))
            }
    }

    @Test
    fun `validates invalid property combination`() {
        contextRunner
            .withPropertyValues(
                "budget-breaker.soft-limit-tokens=100000",
                "budget-breaker.hard-limit-tokens=50000",
            )
            .run { ctx ->
                assertThat(ctx).hasFailed()
            }
    }

    @Configuration
    internal class CustomBreakerConfig {
        @Bean("customBreaker")
        fun customBreaker() = BudgetCircuitBreaker()
    }
}
```

### Anti-Patterns to Avoid

- **Using `@Component` in the starter jar:** Never — starters must not component-scan. Only `@Bean` methods in `@AutoConfiguration`.
- **Injecting `MeterRegistry` in `@AutoConfiguration` constructor:** Breaks bean initialization order. Use `MeterBinder` pattern instead.
- **Using `GlobalScope` for coroutine collection:** Scope not cancelled on Spring shutdown — causes resource leak. Use `SmartLifecycle`-managed scope.
- **`implementation` for Spring Boot deps in starter:** Forces a specific SB version on users. Use `compileOnly` (+ `optional = true` in Maven POM).
- **Blocking inside `@ReadOperation`:** Actuator endpoints can be called by Spring on any thread. Return a snapshot (from `ConcurrentHashMap`) — never block waiting for coroutines.
- **Health indicator returning DOWN on budget breach:** Violates D-07; kills k8s probes.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Auto-config registration | Custom `SpringFactoriesLoader` | `AutoConfiguration.imports` file | SB3 standard; old `spring.factories` still works but `imports` is idiomatic for SB 3.x |
| Property binding | Manual env-var reading | `@ConfigurationProperties` data class | Handles type coercion, validation, relaxed binding, nested maps |
| Metric registration lifecycle | Direct field in auto-config init | `MeterBinder.bindTo()` called by Spring | Ensures registry is ready before binding; works with composite registries |
| Coroutine scope lifecycle | Ad-hoc `runBlocking` or `GlobalScope` | `SmartLifecycle` + `CoroutineScope(SupervisorJob())` | Tied to Spring context; `SupervisorJob` prevents cascade cancellation |
| Configuration metadata generation | Manual JSON editing | `spring-boot-configuration-processor` via kapt | IDE completion, type hints, documentation |

**Key insight:** Spring Boot's auto-configuration machinery solves ordering, conditionality, and override-ability problems that are genuinely hard to get right by hand. Don't short-circuit it.

---

## Core Enhancement Required (D-08)

Before implementing the Actuator endpoint, `BudgetCircuitBreaker` needs a live-snapshot capability. The `reports: ConcurrentHashMap<String, BudgetReport>` currently only stores COMPLETED runs. D-08 requires in-flight agents to also be visible.

**Required change to `BudgetCircuitBreaker.kt`:**
```kotlin
// Add a second map for active trackers
private val activeTrackers = ConcurrentHashMap<String, TokenTracker>()

suspend fun <T> withBudget(agentId: String, budget: AgentBudget = defaultBudget, block: suspend BudgetScope.() -> T): T {
    val tracker = TokenTracker(agentId, budget)
    activeTrackers[agentId] = tracker          // register before block
    // ... existing code ...
    finally {
        activeTrackers.remove(agentId)         // deregister after completion
        reports[agentId] = scope.buildReport(durationMs)
    }
}

/** Returns snapshot of all completed reports + in-flight tracker snapshots. */
fun getAllReports(): Map<String, BudgetSnapshot> {
    val result = mutableMapOf<String, BudgetSnapshot>()
    reports.forEach { (id, report) -> result[id] = BudgetSnapshot(report, running = false) }
    activeTrackers.forEach { (id, tracker) ->
        result[id] = BudgetSnapshot(tracker.toSnapshot(), running = true)
    }
    return result
}
```

This is a ~10-line addition to the core module — no API breaks.

---

## Common Pitfalls

### Pitfall 1: `compileOnly` Spring Boot deps — classpath gap in tests

**What goes wrong:** `compileOnly` deps are not on the test classpath unless explicitly re-added. `testImplementation("org.springframework.boot:spring-boot-starter-test")` pulls in the necessary classes, but `spring-boot-autoconfigure` itself must be added as `testImplementation` too if it's only `compileOnly`.

**Why it happens:** Gradle's `compileOnly` configuration is intentionally excluded from runtime and test classpaths.

**How to avoid:** Add `testImplementation("org.springframework.boot:spring-boot-autoconfigure:3.5.3")` and `testImplementation("org.springframework.boot:spring-boot-actuator-autoconfigure:3.5.3")` alongside `spring-boot-starter-test`.

**Warning signs:** `ClassNotFoundException: org.springframework.boot.autoconfigure.AutoConfiguration` at test time.

### Pitfall 2: `CallTracked` event has no `model` field

**What goes wrong:** `MetricsEventCollector` needs `{agent, model}` tags on `gen_ai.*` counters, but `BudgetEvent.CallTracked` only carries `agentId`. Without model, all counters have the same `model=unknown` tag.

**Why it happens:** The core event was designed before the metrics naming decision.

**How to avoid:** Two valid approaches (Claude's discretion per D-10):
- **Option A:** Add `model: String` to `CallTracked` data class (breaks no existing consumers — the field is additive). Simplest.
- **Option B:** Look up model from `BudgetCircuitBreaker.activeTrackers` at event time using `agentId`.

Option A is cleaner (model is a first-class property of a call). Recommend it.

**Warning signs:** Metrics appear but all share one `model` tag value.

### Pitfall 3: nmcp aggregation name collision with v0.0.1

**What goes wrong:** The nmcp `publicationName` in root `build.gradle.kts` is currently `"budget-breaker-${project.version}"`. When version bumps to `0.1.0`, this becomes `"budget-breaker-0.1.0"` — fine. But the aggregation dependencies block lists only `:budget-breaker`. Adding `:budget-breaker-spring-boot-starter` to the `nmcpAggregation` block is a one-liner.

**Why it happens:** Starter was deliberately excluded in Phase 1 (no source files yet).

**How to avoid:** Add `nmcpAggregation(project(":budget-breaker-spring-boot-starter"))` in root `build.gradle.kts`. Also update the GitHub Release body template in `release.yml` to mention both artifacts.

**Warning signs:** CI release workflow produces a bundle without the starter jar.

### Pitfall 4: kapt plugin required for configuration-processor with Kotlin

**What goes wrong:** `annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")` does not work for Kotlin classes — annotation processors don't see Kotlin-generated bytecode without kapt.

**Why it happens:** kapt generates Java stubs so Java annotation processors can inspect Kotlin code.

**How to avoid:** Add `id("org.jetbrains.kotlin.kapt")` to the starter's `build.gradle.kts` plugins block and declare `kapt("org.springframework.boot:spring-boot-configuration-processor:3.5.3")`. Alternatively, skip the processor entirely (metadata generation is optional — just IDE convenience). Given the project uses JVM 21 and kapt maintenance mode, consider skipping and hand-writing `additional-spring-configuration-metadata.json` instead.

**Warning signs:** IDE shows no auto-complete for `budget-breaker.*` properties.

### Pitfall 5: `@Endpoint` bean not registered as Spring bean

**What goes wrong:** `@Endpoint` alone does not make a Spring bean — you still need `@Bean` (or `@Component`). If the endpoint class is not a Spring bean, Actuator's endpoint discovery silently skips it.

**Why it happens:** `@Endpoint` is a marker annotation, not a stereotype.

**How to avoid:** Declare the endpoint as a `@Bean` in `BudgetBreakerAutoConfiguration`. Do NOT add `@Component` to it (that would require component scanning, which starters must not enable).

**Warning signs:** `GET /actuator/budget` returns 404 and the endpoint is absent from `GET /actuator`.

### Pitfall 6: SharedFlow collector not started before first events

**What goes wrong:** `MetricsEventCollector` (and `SLF4JEventLogger`) start collecting in `SmartLifecycle.start()`. If an agent runs before `start()` is called (e.g., during context initialization in a `@PostConstruct`), early events are silently dropped.

**Why it happens:** `SharedFlow` with no replay cache (`replayCache = 0`) drops events with no active collectors.

**How to avoid:** `BudgetCircuitBreaker` already has `extraBufferCapacity = 64`. This buffers 64 events. If `start()` is called promptly (Spring calls `SmartLifecycle.start()` near the end of context refresh), the buffer absorbs startup-phase events. Do NOT use agents in `@PostConstruct` before the context is fully started.

**Warning signs:** First few `gen_ai.*` metric increments are missing; counters start at non-zero values unexpectedly.

---

## Code Examples

### Complete starter build.gradle.kts (Phase 2)

```kotlin
// budget-breaker-spring-boot-starter/build.gradle.kts
plugins {
    kotlin("jvm")
    id("budget-breaker.publishing")
    // Optional: uncomment for IDE config hints
    // id("org.jetbrains.kotlin.kapt")
}

dependencies {
    api(project(":budget-breaker"))
    compileOnly("org.springframework.boot:spring-boot-autoconfigure:3.5.3")
    compileOnly("org.springframework.boot:spring-boot-actuator-autoconfigure:3.5.3")
    compileOnly("io.micrometer:micrometer-core:1.15.0")

    // Optional IDE hints:
    // kapt("org.springframework.boot:spring-boot-configuration-processor:3.5.3")

    testImplementation(kotlin("test"))
    testImplementation("org.springframework.boot:spring-boot-autoconfigure:3.5.3")
    testImplementation("org.springframework.boot:spring-boot-actuator-autoconfigure:3.5.3")
    testImplementation("org.springframework.boot:spring-boot-starter-test:3.5.3")
    testImplementation("io.micrometer:micrometer-core:1.15.0")
}

tasks.test { useJUnitPlatform() }

kotlin { jvmToolchain(21) }

publishing {
    publications {
        named<MavenPublication>("maven") {
            pom {
                name.set("budget-breaker-spring-boot-starter")
                description.set(
                    "Spring Boot auto-configuration for budget-breaker: " +
                    "@ConfigurationProperties, Actuator endpoint, and Micrometer metrics.",
                )
            }
        }
    }
}
```

### Root build.gradle.kts nmcp additions

```kotlin
// Root build.gradle.kts — diff from current
allprojects {
    group = "io.github.unityinflow"
    version = "0.1.0"          // bumped from 0.0.1
    // ...
}

nmcpAggregation {
    centralPortal {
        // ...
        publicationName = "budget-breaker-${project.version}"
    }
}

dependencies {
    nmcpAggregation(project(":budget-breaker"))
    nmcpAggregation(project(":budget-breaker-spring-boot-starter"))   // ADD THIS
}
```

### Counter registration in MetricsEventCollector

```kotlin
// Source: https://docs.spring.io/spring-boot/reference/actuator/metrics.html
private fun handleCallTracked(event: BudgetEvent.CallTracked, registry: MeterRegistry) {
    val model = resolveModel(event.agentId)
    Counter.builder("gen.ai.client.token.usage.input")
        .description("Input tokens consumed by LLM calls")
        .tags("agent", event.agentId, "model", model)
        .register(registry)
        .increment(event.promptTokens.toDouble())

    Counter.builder("gen.ai.client.token.usage.output")
        .description("Output tokens consumed by LLM calls")
        .tags("agent", event.agentId, "model", model)
        .register(registry)
        .increment(event.completionTokens.toDouble())
}
```

### Gauge for usage ratio

```kotlin
// Source: https://docs.micrometer.io/micrometer/reference/concepts/gauges.html
// Gauges must hold a strong reference to the measured object
private val ratioTrackers = ConcurrentHashMap<String, AtomicDouble>()

private fun ensureRatioGauge(agentId: String, registry: MeterRegistry) {
    ratioTrackers.computeIfAbsent(agentId) { id ->
        val gauge = AtomicDouble(0.0)
        Gauge.builder("budget.breaker.usage.ratio", gauge) { it.get() }
            .description("Token usage as fraction of hard limit")
            .tags("agent", id)
            .register(registry)
        gauge
    }
}
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `spring.factories` for auto-config registration | `AutoConfiguration.imports` file | Spring Boot 2.7 (imports), SB 3.0 (factories deprecated) | Must use `imports` file for SB 3.x [CITED: docs.spring.io/spring-boot/reference/features/developing-auto-configuration.html] |
| `@ConstructorBinding` required on Kotlin data class | Not needed in SB 3.x | Spring Boot 3.0 | Can use plain data classes with `val` properties directly |
| Micrometer 1.x naming (dots) | OTel GenAI semconv naming | OTel GenAI semconv spec | `gen_ai.client.token.usage.*` is now the interop-safe naming for AI frameworks [ASSUMED] |
| kapt for annotation processing | KSP (in progress for SB) | Spring Boot team issue #28046 still open | kapt works on JVM 21; KSP for spring-boot-configuration-processor not yet available; kapt remains the practical choice |

**Deprecated/outdated:**
- `META-INF/spring.factories` key `org.springframework.boot.autoconfigure.EnableAutoConfiguration`: still works but deprecated in SB 3.x; use `AutoConfiguration.imports`.
- `@ConstructorBinding` at class level: not supported in SB 3.x on data classes.

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `io.micrometer:micrometer-test` artifact exists for SimpleMeterRegistry in test scope | Standard Stack | SimpleMeterRegistry is in `micrometer-core`; if `micrometer-test` doesn't exist, use `new SimpleMeterRegistry()` from core directly |
| A2 | Metric naming `gen.ai.client.token.usage.input` (dots) is the correct Micrometer convention for OTel GenAI semconv | Architecture, Code Examples | If wrong naming, token-dashboard's OTLP mapper won't recognize them; cross-check with 06-token-dashboard `OtlpMetricMapper.kt` before tagging |
| A3 | `MeterBinder` + `SmartLifecycle` on the same class is idiomatic | Architecture Patterns | Could require two separate beans; test with ApplicationContextRunner to confirm Spring orders them correctly |
| A4 | kapt still works on Gradle 9.4.1 + JVM 24 (the machine runs JVM 24 per env check) | Common Pitfalls | kapt generates stubs compiled under JVM 21 (jvmToolchain setting); if kapt fails on JVM 24, skip the configuration processor entirely |
| A5 | `spring-boot-starter-test:3.5.3` includes `ApplicationContextRunner` without additional deps | Standard Stack | Spring Boot test slice may need explicit `spring-boot-autoconfigure` on testImplementation |

---

## Open Questions

1. **Model tag resolution for `CallTracked` events**
   - What we know: `CallTracked` has no `model` field; `AgentBudget.model` is set per agent
   - What's unclear: `BudgetCircuitBreaker` does not currently expose a map from `agentId` to `AgentBudget`
   - Recommendation: Add `model: String` to `BudgetEvent.CallTracked` in the core D-08 enhancement — cleanest solution, no lookup needed

2. **`gen.ai` vs `gen_ai` metric naming**
   - What we know: CONTEXT.md D-09 specifies `gen_ai.client.token.usage.input` (underscore prefix, dot separator); Micrometer uses dots
   - What's unclear: Whether `gen_ai` uses underscores within the prefix (`gen_ai.`) or the whole name uses dots
   - Recommendation: Follow Micrometer convention — use dots throughout (`gen.ai.client.token.usage.input`); the OTel exporter normalizes to underscores. Cross-verify against 06-token-dashboard `OtlpMetricMapper.kt`.

3. **kapt vs no-kapt for configuration-processor**
   - What we know: kapt is maintenance-only; KSP support for SB config processor not yet available; machine runs JVM 24
   - What's unclear: Whether kapt toolchain successfully compiles against JVM 21 target when the host JVM is 24
   - Recommendation: Skip kapt initially. Add a hand-written `additional-spring-configuration-metadata.json` for IDE hints. Revisit kapt/KSP when KSP support lands in spring-boot.

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Java / JVM | Build + test | Yes | 24.0.2 (Corretto) | — |
| Gradle | Build | Yes | 9.4.1 | — |
| Maven Central access | Dependency resolution | Yes (CI + local) | — | — |
| Sonatype Central Portal | REL-01 publish | Yes (proven v0.0.1) | — | — |
| SIGNING_KEY / SIGNING_PASSWORD | CI release.yml | Yes (GitHub secrets set) | — | — |
| Self-hosted CI runners | CI | Yes (`arc-runner-unityinflow`) | — | — |

**Missing dependencies:** None that block execution.

> Note: JVM on this machine is 24; project `jvmToolchain(21)` ensures Kotlin compiles to JVM 21 bytecode. Spring Boot 3.5.x requires JVM 17+, so JVM 24 is fine for running tests.

---

## Validation Architecture

> `workflow.nyquist_validation` not present in config.json — treated as enabled.

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 (via `useJUnitPlatform()`) + Kotest matchers 5.9.1 |
| Config file | none (configured in `tasks.test { useJUnitPlatform() }`) |
| Quick run command | `./gradlew :budget-breaker-spring-boot-starter:test` |
| Full suite command | `./gradlew build` |

### Phase Requirements to Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| SPRING-01 | Auto-config registers `BudgetCircuitBreaker` bean | Unit (ApplicationContextRunner) | `./gradlew :budget-breaker-spring-boot-starter:test` | No — Wave 0 |
| SPRING-01 | `@ConditionalOnMissingBean` backs off when user defines own bean | Unit (ApplicationContextRunner) | `./gradlew :budget-breaker-spring-boot-starter:test` | No — Wave 0 |
| SPRING-01 | Invalid property combo fails startup with clear message | Unit (ApplicationContextRunner) | `./gradlew :budget-breaker-spring-boot-starter:test` | No — Wave 0 |
| SPRING-02 | `GET /actuator/budget` returns agents map | Integration (@SpringBootTest) | `./gradlew :budget-breaker-spring-boot-starter:test` | No — Wave 0 |
| SPRING-02 | Health indicator always returns UP | Unit (ApplicationContextRunner) | `./gradlew :budget-breaker-spring-boot-starter:test` | No — Wave 0 |
| SPRING-03 | `gen_ai.*` counters increment after `trackCall` | Integration (@SpringBootTest + SimpleMeterRegistry) | `./gradlew :budget-breaker-spring-boot-starter:test` | No — Wave 0 |
| REL-01 | Starter artifact present in nmcp bundle | Manual (publishToMavenLocal + local demo) | n/a — throwaway demo per D-19 | No |

### Sampling Rate

- Per task commit: `./gradlew :budget-breaker-spring-boot-starter:test`
- Per wave merge: `./gradlew build`
- Phase gate: Full suite green before `/gsd-verify-work`

### Wave 0 Gaps

- `budget-breaker-spring-boot-starter/src/test/kotlin/io/github/unityinflow/budget/spring/BudgetBreakerAutoConfigurationTest.kt` — covers SPRING-01 ApplicationContextRunner tests
- `budget-breaker-spring-boot-starter/src/test/kotlin/io/github/unityinflow/budget/spring/BudgetStarterIntegrationTest.kt` — covers SPRING-02/SPRING-03 @SpringBootTest smoke test

---

## Security Domain

> `security_enforcement` not set in config.json — treated as enabled.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | No | Actuator endpoint exposure is user-configured; starter does not add auth |
| V3 Session Management | No | No sessions in this library |
| V4 Access Control | Partial | Document that `/actuator/budget` must be secured by the consuming application (Spring Security or firewall) |
| V5 Input Validation | Yes | `BudgetBreakerProperties.init {}` block validates limits at startup; prevents misconfiguration |
| V6 Cryptography | No | No cryptographic operations |

### Known Threat Patterns

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Unauthenticated access to `/actuator/budget` exposing agent names and token counts | Information Disclosure | Document in README: restrict endpoint via `management.endpoints.web.exposure.include` and Spring Security |
| Budget limit bypass via property injection in test/dev profile | Tampering | `init {}` validation in `BudgetBreakerProperties` fails startup on invalid combos |
| Coroutine scope not cancelled on context shutdown | DoS (resource leak) | `SmartLifecycle.stop()` cancels `CoroutineScope`; covered by integration test teardown |

---

## Sources

### Primary (MEDIUM confidence)

- [Spring Boot Auto-Configuration docs](https://docs.spring.io/spring-boot/reference/features/developing-auto-configuration.html) — `@AutoConfiguration`, imports file, `@ConditionalOnMissingBean`, `@ConfigurationProperties`, `ApplicationContextRunner`
- [Spring Boot Actuator Endpoints docs](https://docs.spring.io/spring-boot/reference/actuator/endpoints.html) — `@Endpoint`, `@ReadOperation`, `@Selector`, `HealthIndicator`
- [Spring Boot Metrics docs](https://docs.spring.io/spring-boot/reference/actuator/metrics.html) — `MeterBinder`, `MeterRegistry`, Kotlin examples
- [Spring Boot Kotlin docs](https://docs.spring.io/spring-boot/reference/features/kotlin.html) — `@ConfigurationProperties` data class, constructor binding, `kotlin-spring` plugin

### Secondary (LOW confidence)

- [Micrometer Counter concepts](https://docs.micrometer.io/micrometer/reference/concepts/counters.html) — Counter.builder(), tags
- [Micrometer Gauge concepts](https://docs.micrometer.io/micrometer/reference/concepts/gauges.html) — Gauge.builder(), AtomicDouble pattern
- [Spring Boot Testing docs](https://docs.spring.io/spring-boot/reference/testing/spring-boot-applications.html) — `@SpringBootTest`, `ApplicationContextRunner`, `withPropertyValues`
- [SmartLifecycle pattern (Medium)](https://medium.com/@AlexanderObregon/managing-lifecycle-hooks-with-smartlifecycle-in-spring-boot-a85d3ae70360) — `start()`/`stop()`/`isRunning()` pattern
- [GitHub issue #43875 — spring-boot-configuration-processor + Kotlin requires kapt](https://github.com/spring-projects/spring-boot/issues/43875)
- Maven Central version verification — all packages above confirmed via `search.maven.org`

---

## Project Constraints (from CLAUDE.md)

Extracted from `/Users/jirihermann/Documents/workspace-1-ideas/unity-in-flow-ai/05-budget-breaker/CLAUDE.md` and project skill `code-review/SKILL.md`:

| Directive | Source | Impact on Phase |
|-----------|--------|-----------------|
| Kotlin 2.0+, JVM target 21 | CLAUDE.md | `jvmToolchain(21)` in starter build; Kotlin 2.1.0 already in root |
| Gradle Kotlin DSL only — never Groovy | CLAUDE.md | All `.gradle.kts` files; confirmed in existing build |
| No `var` — always `val` | CLAUDE.md + skills | All properties in `BudgetBreakerProperties` must be `val`; mutable state via `AtomicDouble`/`AtomicInteger` not `var` |
| No `!!` without explanation | CLAUDE.md | Use `?.` with fallback in metric collection |
| Coroutines for async — never `Thread.sleep()` or raw threads | CLAUDE.md | `SmartLifecycle` uses `CoroutineScope`, not `ExecutorService` |
| Sealed classes for domain modelling | CLAUDE.md | `BudgetEvent` is already sealed; no new exception types unless sealed |
| KDoc on all public APIs | CLAUDE.md | `BudgetEndpoint`, `BudgetBreakerProperties`, `BudgetBreakerAutoConfiguration` — all public items need KDoc |
| ktlint before every commit | CLAUDE.md | Run `./gradlew ktlintFormat` before each commit |
| Test coverage >80% on core logic before release | CLAUDE.md | ApplicationContextRunner tests + @SpringBootTest cover all three public-facing components |
| `@ConfigurationProperties` for configuration (not `@Value`) | code-review skill | Confirmed by D-01/D-02 |
| Auto-config in `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` | code-review skill | Required file location — do not use old `spring.factories` |
| Health indicators implement `HealthIndicator` | code-review skill | `BudgetBreakerHealthIndicator implements HealthIndicator` |
| Coroutine scope properly managed (no leaks, proper cancellation) | code-review skill | `SmartLifecycle.stop()` must cancel scope |
| No Kafka dependency | CLAUDE.md | Events delivered via Kotlin `SharedFlow` only |
| No frontend | CLAUDE.md | README-only demonstration (D-15) |
| Do not add Kafka as dependency | CLAUDE.md | N/A for this phase |
| CI on `[arc-runner-unityinflow]` — never `ubuntu-latest` | CLAUDE.md | ci.yml and release.yml already correct |

---

## Metadata

**Confidence breakdown:**
- Standard stack: MEDIUM — Maven Central versions verified; Spring Boot and Micrometer are authoritative sources fetched directly from docs
- Architecture: MEDIUM — Auto-config, Actuator, Micrometer patterns confirmed from official Spring Boot docs
- Pitfalls: LOW/MEDIUM — `compileOnly` gap and kapt requirement confirmed from GitHub issues; coroutine lifecycle pattern confirmed from Spring docs + community patterns
- OTel metric naming: LOW — `gen_ai.*` naming tagged ASSUMED; must verify against token-dashboard OtlpMetricMapper before tagging v0.1.0

**Research date:** 2026-06-12
**Valid until:** 2026-07-12 (Spring Boot 3.5.x is stable; Micrometer naming conventions stable)
