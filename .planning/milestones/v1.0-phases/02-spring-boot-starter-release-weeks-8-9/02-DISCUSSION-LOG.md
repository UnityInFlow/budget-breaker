# Phase 2: Spring Boot Starter + Release - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-06-12
**Phase:** 2-Spring Boot Starter + Release (Weeks 8-9)
**Areas discussed:** Config properties design, Actuator endpoint shape, Micrometer metric names, Release versioning

---

## Config properties design

| Option | Description | Selected |
|--------|-------------|----------|
| budget-breaker.* | Matches artifact name — standalone identity; kore maps its own prefix later | ✓ |
| kore.budget.* | As sketched in the deep-dive spec; couples standalone library to future kore tool | |
| unityinflow.budget.* | Org-level namespace; longer, less discoverable | |

**User's choice:** budget-breaker.*

| Option | Description | Selected |
|--------|-------------|----------|
| Core mirror only | default-model, limits, pricing override map — maps 1:1 to existing core | ✓ |
| + behavior enums | Adds on-soft-limit WARN\|EMIT_EVENT, on-hard-limit CANCEL | |
| Full spec incl. webhook | Everything incl. alert-webhook (HTTP client + retry semantics) | |

**User's choice:** Core mirror only — behaviors/webhook deferred

| Option | Description | Selected |
|--------|-------------|----------|
| Log WARN by default | Starter subscribes to events Flow and logs soft-limit via SLF4J | ✓ |
| Silent unless user subscribes | User wires own collector; breaches can pass unnoticed | |

**User's choice:** Log WARN by default

| Option | Description | Selected |
|--------|-------------|----------|
| Defaults + fail-fast invalid | Boots with sensible defaults; invalid combos fail startup | ✓ |
| Require explicit limits | No defaults; breaks zero-config starter convention | |
| Defaults + lenient | Warnings only on invalid config — dangerous for cost control | |

**User's choice:** Defaults + fail-fast invalid

---

## Actuator endpoint shape

| Option | Description | Selected |
|--------|-------------|----------|
| All agents + per-agent drill-down | /actuator/budget map + /actuator/budget/{agentId}; mirrors /actuator/metrics | ✓ |
| Flat list only | Single endpoint, no sub-path | |
| Summary + totals | Aggregate spend view; loses raw reports | |

**User's choice:** All agents + per-agent drill-down

| Option | Description | Selected |
|--------|-------------|----------|
| Read-only | GET only; mutations stay in code | ✓ |
| Read + reset operation | DELETE per agent; mutation surface on ops port | |

**User's choice:** Read-only

| Option | Description | Selected |
|--------|-------------|----------|
| UP with details | Always UP, contributes breach stats — won't flip k8s probes | ✓ |
| DOWN on hard breach | Spec-faithful but couples cost to app health | |
| Skip health indicator | Less to build, loses visibility | |

**User's choice:** UP with details

| Option | Description | Selected |
|--------|-------------|----------|
| Live + completed | Small core enhancement: snapshot of active trackers, running flag | ✓ |
| Completed runs only | Exactly what getReport() gives today; blind to in-flight agents | |

**User's choice:** Live + completed

---

## Micrometer metric names

| Option | Description | Selected |
|--------|-------------|----------|
| gen_ai semconv names | gen_ai.client.token.usage.input/.output + budget.breaker.* — OTel-aligned, recognized by token-dashboard | ✓ |
| llm.token.* names | token-dashboard's first-choice mapping; house convention | |
| Spec names (agent.tokens.used) | Needs token-dashboard mapper update before integration works | |

**User's choice:** gen_ai semconv names
**Notes:** Verified against token-dashboard's OtlpMetricMapper.kt — `agent.tokens.used` from the spec is NOT recognized there.

| Option | Description | Selected |
|--------|-------------|----------|
| Subscribe to events Flow | MetricsEventCollector on core SharedFlow; CallTracked already exists | ✓ |
| Explicit BudgetMetrics calls | Inline instrumentation per spec sketch; needs decorator seam | |

**User's choice:** Subscribe to events Flow

| Option | Description | Selected |
|--------|-------------|----------|
| Emit cost counter too | budget.breaker.cost.usd via core ModelPricing, respects yml overrides | ✓ |
| Tokens only | Consumers derive cost themselves | |

**User's choice:** Emit cost counter too

---

## Release versioning

| Option | Description | Selected |
|--------|-------------|----------|
| v0.1.0, both modules | Core + starter together; "0.1.0 adds Spring Boot support" | ✓ |
| v0.0.2, both modules | Conservative bump; undersells a new module | |
| Starter-only 0.0.1 | Avoids core re-release, but core needs snapshot enhancement anyway | |

**User's choice:** v0.1.0, both modules

| Option | Description | Selected |
|--------|-------------|----------|
| Spring Boot 3.5.x | Current GA baseline; matches start.spring.io defaults | ✓ |
| Spring Boot 3.4.x | One line back for wider compat | |
| You decide | Researcher picks based on OpsWave/QAWave + support windows | |

**User's choice:** Spring Boot 3.5.x

| Option | Description | Selected |
|--------|-------------|----------|
| README section only | Dependency snippet, yml example, usage, actuator output | ✓ |
| README + examples/ sample app | Runnable demo app; another module to maintain | |

**User's choice:** README section only

---

## Claude's Discretion

- Model tag resolution for metrics (CallTracked lacks model name — resolve from AgentBudget or extend the event)
- Actuator exposure documentation (follow Spring opt-in convention)
- Flow collector lifecycle/scope and buffer handling
- Exact zero-config default limit values

## Deferred Ideas

- Behavior enums (WARN/EMIT_EVENT/PAUSE) — needs core pause support
- alert-webhook Slack notifications
- examples/ sample app (for the launch post)
- SDK interceptors, Kafka opt-in — existing backlog issues #3–#6, #8

---

# Session 2 — 2026-06-12 (context update)

**Areas discussed:** Per-agent budget config, Injected bean surface, Release quality bar, Decision review (D-01–D-15)

---

## Per-agent budget config

| Option | Description | Selected |
|--------|-------------|----------|
| Global only | Keep D-02 as-is; per-agent budgets stay code-side via withBudget(budget = ...); agents map can come in v0.2.0 without breaking | ✓ |
| Add agents map | budget-breaker.agents.<id>.{soft,hard,model} resolved by auto-config; needs resolver hook into core call path | |
| You decide | Planner's judgment based on implementation cost | |

**User's choice:** Global only (→ D-16)

---

## Injected bean surface

| Option | Description | Selected |
|--------|-------------|----------|
| Single breaker bean | One @Bean BudgetCircuitBreaker from properties, @ConditionalOnMissingBean; properties internal | ✓ |
| Breaker + properties bean | Also document injecting BudgetBreakerProperties | |
| Breaker + DSL helper | Additional Kotlin convenience wrapper | |

**User's choice:** Single breaker bean (→ D-17)

| Option | Description | Selected |
|--------|-------------|----------|
| Events Flow only | Apps collect breaker.events for custom reactions; callback users replace the bean | ✓ |
| Optional callback bean | Auto-config picks up user (BudgetReport) -> Unit bean as onSoftLimit | |
| You decide | Whichever keeps auto-config simplest | |

**User's choice:** Events Flow only (→ D-18)

---

## Release quality bar

| Option | Description | Selected |
|--------|-------------|----------|
| Boot smoke test | ApplicationContextRunner units + one @SpringBootTest hitting /actuator/budget + MeterRegistry assertion | |
| Unit-level only | ApplicationContextRunner + plain unit tests; README manual verification | |
| Full sample-app verification | Additionally verify a demo app against a published artifact before tagging | ✓ |

**User's choice:** Full sample-app verification

| Option | Description | Selected |
|--------|-------------|----------|
| Throwaway, D-15 stands | Demo built outside repo (or gitignored) against mavenLocal artifact, verified, discarded | ✓ |
| Keep it — revise D-15 | Commit as examples/demo-app excluded from publishing | |
| Keep it outside the repo | Separate UnityInFlow/budget-breaker-demo repo | |

**User's choice:** Throwaway, D-15 stands (→ D-19)

---

## Decision review (D-01–D-15)

| Group | Decision |
|-------|----------|
| Config properties (D-01–D-04) | Keep as-is |
| Actuator (D-05–D-08) | Keep as-is |
| Metrics (D-09–D-11) | Keep as-is |
| Release (D-12–D-15) | Keep as-is |

## Deferred Ideas (session 2)

- Per-agent budget overrides in application.yml (budget-breaker.agents.<id>.*) — v0.2.0
