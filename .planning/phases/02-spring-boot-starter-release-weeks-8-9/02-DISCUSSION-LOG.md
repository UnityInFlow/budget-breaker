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
