# Phase 2: Spring Boot Starter + Release - Context

**Gathered:** 2026-06-12
**Status:** Ready for planning

<domain>
## Phase Boundary

Deliver the `budget-breaker-spring-boot-starter` module (currently an empty stub): Spring Boot auto-configuration with `@ConfigurationProperties`, Actuator endpoint `/actuator/budget` + health indicator, and Micrometer metrics — then release core + starter together as v0.1.0 to Maven Central.

Scope = SPRING-01, SPRING-02, SPRING-03, REL-01 (starter portion). Note: core `io.github.unityinflow:budget-breaker:0.0.1` is ALREADY on Maven Central (2026-05-11) — publishing infra (buildSrc convention + nmcp + release.yml) exists and works. This phase adds the starter to that machinery, it does not build publishing from scratch.

</domain>

<decisions>
## Implementation Decisions

### Configuration Properties (SPRING-01)
- **D-01:** Property prefix is `budget-breaker.*` (matches artifact name; NOT `kore.budget.*` from the deep-dive spec — kore (#08) can map its own prefix onto this later).
- **D-02:** Knobs in this release mirror the core API only: `default-model`, `soft-limit-tokens`, `hard-limit-tokens`, and a `pricing` override map (per-model input/output per-million prices). Behavior enums (`on-soft-limit`, `on-hard-limit`, `PAUSE`) and `alert-webhook` are deferred (see Deferred Ideas).
- **D-03:** Out of the box, the starter subscribes to the core `events: SharedFlow<BudgetEvent>` and logs soft-limit breaches at WARN via SLF4J — zero-effort visibility for typical Spring users.
- **D-04:** Zero-config boot works with sensible defaults (e.g. sonnet-class model, 100k hard / 80k soft). Invalid combos (soft >= hard, negative values) fail startup fast with a clear validation message.

### Actuator Endpoint (SPRING-02)
- **D-05:** `GET /actuator/budget` returns a map of all tracked agents' reports; `GET /actuator/budget/{agentId}` returns one full BudgetReport. Mirrors the `/actuator/metrics` interaction pattern.
- **D-06:** Endpoint is read-only (GET only). No reset/mutation operations.
- **D-07:** Include `BudgetBreakerHealthIndicator` — always reports UP, contributing details (agentsTracked, softBreaches, hardBreaches). A budget breach must NOT flip health to DOWN (k8s probes would kill healthy pods).
- **D-08:** Endpoint shows live in-flight agents, not just completed runs. Requires a small core enhancement: expose a snapshot of active trackers; entries flagged `running: true/false`.

### Micrometer Metrics (SPRING-03)
- **D-09:** Metric names follow OTel GenAI semconv and are already recognized by token-dashboard's OTLP mapper: `gen_ai.client.token.usage.input` / `gen_ai.client.token.usage.output` counters tagged {agent, model}; `budget.breaker.breach` counter tagged {agent, type=soft|hard}; `budget.breaker.usage.ratio` gauge tagged {agent} (used/hard).
- **D-10:** Wiring: a `MetricsEventCollector` in the starter subscribes to the core events Flow (`CallTracked`, `SoftLimitReached`, `HardLimitExceeded` already exist — no new core event types needed). NOT inline instrumentation in the call path.
- **D-11:** Also emit `budget.breaker.cost.usd` counter tagged {agent, model}, computed via core ModelPricing so application.yml price overrides are respected.

### Release (REL-01)
- **D-12:** Tag v0.1.0 releasing core + starter together at the same version (uniform-version build). Story: "0.1.0 adds Spring Boot support". Core needs the re-release anyway for the live-snapshot enhancement (D-08).
- **D-13:** Spring Boot baseline 3.5.x (compile against 3.5; expected to work on 3.4+).
- **D-14:** Starter must be added to the nmcp publish aggregation in the root build (it is currently deliberately excluded) and apply the existing `budget-breaker.publishing` convention plugin.
- **D-15:** Demonstration via README only: add a Spring Boot section (dependency snippet, application.yml example, @Autowired usage, actuator/metrics output). No examples/ sample app this release.

### Claude's Discretion
- `CallTracked` events don't carry the model name; the metrics collector needs {agent, model} tags. Either resolve model from the agent's AgentBudget at collection time or add `model` to `CallTracked` — planner/executor's call.
- Actuator endpoint exposure follows Spring conventions (user opts in via `management.endpoints.web.exposure.include`); document it, don't auto-expose.
- Flow subscription lifecycle (which CoroutineScope the collector uses, buffer/overflow handling for `extraBufferCapacity = 64`) — standard Spring lifecycle management, Claude decides.
- Exact default values for zero-config budget limits.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Feature spec
- `05-budget-breaker.md` — Deep-dive spec: Spring Boot integration sketch (§2), Micrometer sketch (§3), Week 8–9 implementation todos (§4). NOTE: decisions above OVERRIDE the spec where they differ (prefix is `budget-breaker.*` not `kore.budget.*`; metric names are gen_ai semconv not `agent.tokens.used`; no webhook/PAUSE this release).

### Prior phase decisions
- `.planning/phases/phase-1/01-CONTEXT.md` — Phase 1 locked decisions that still bind: caller-driven trackCall, Micrometer lives in the starter module (no third module), pricing override map shape, soft limit fires callback AND Flow.

### Existing code to integrate with
- `budget-breaker/src/main/kotlin/io/github/unityinflow/budget/BudgetCircuitBreaker.kt` — `withBudget()`, `events: SharedFlow<BudgetEvent>`, `getReport()`; D-08 live-snapshot enhancement lands here.
- `budget-breaker/src/main/kotlin/io/github/unityinflow/budget/BudgetEvent.kt` — sealed events incl. `CallTracked(promptTokens, completionTokens)`.
- `buildSrc/src/main/kotlin/budget-breaker.publishing.gradle.kts` — publishing convention the starter must apply.
- Root `build.gradle.kts` — nmcp aggregation list (starter currently excluded; D-14 adds it).

### Cross-tool integration target
- `../06-token-dashboard/src/main/kotlin/io/github/unityinflow/tokendashboard/otlp/OtlpMetricMapper.kt` — the consumer that recognizes `gen_ai.client.token.usage.*` names (basis for D-09).

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- Core library complete and shipped (8 source files, 23 tests green): AgentBudget, TokenTracker, BudgetCircuitBreaker, BudgetScope, BudgetException, BudgetEvent, ModelPricing, BudgetReport.
- `BudgetEvent.CallTracked` already emits per-call usage — the metrics collector needs no new core event types.
- Publishing pipeline proven: buildSrc convention plugin + nmcp Central Portal aggregation + tag-triggered release.yml (used for core v0.0.1 on 2026-05-11).

### Established Patterns
- Group `io.github.unityinflow`, uniform version via root `allprojects` (currently 0.0.1 → bump to 0.1.0 at release).
- Kotlin 2.x, JVM 21, JUnit 5 + Kotest, ktlint, no `var`, sealed classes, coroutines-only async.
- CI on self-hosted runners `[arc-runner-unityinflow]` (never ubuntu-latest).

### Integration Points
- `budget-breaker-spring-boot-starter/build.gradle.kts` is a bare stub (kotlin jvm plugin only) — everything in this phase lands there plus the small core snapshot enhancement.
- token-dashboard (#06) consumes the emitted metrics via OTLP — naming contract is D-09.
- kore-runtime (#08) will consume this starter for per-agent budgets in its v0.1.0 milestone.

</code_context>

<specifics>
## Specific Ideas

- Actuator response shape agreed during discussion: top-level `agents` map keyed by agentId with summary fields; full BudgetReport at the per-agent path.
- Health detail keys: `agentsTracked`, `softBreaches`, `hardBreaches`.

</specifics>

<deferred>
## Deferred Ideas

- `on-soft-limit` / `on-hard-limit` behavior enums incl. PAUSE — needs core pause support; revisit v0.2.0.
- `alert-webhook` (Slack notification on breach) — adds HTTP client + retry semantics; revisit after starter ships.
- examples/ runnable sample app — useful for the r/kotlin launch post, not release-blocking.
- SDK interceptors (Anthropic/OpenAI/LangChain4j), Kafka opt-in event bus — already tracked as v0.1.0+ issues #3–#6, #8 (note: issue numbering predates the v0.1.0-for-starter decision; reconcile milestones when planning).

</deferred>

---

*Phase: 2-Spring Boot Starter + Release*
*Context gathered: 2026-06-12*
