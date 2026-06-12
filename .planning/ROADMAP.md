# Roadmap: budget-breaker

## Phase 1: Core Library (Week 7)
**Goal:** Pure Kotlin library with budget enforcement, no Spring dependency.
**Scope:** CORE-01 through CORE-06
**Deliverables:** AgentBudget, TokenTracker, BudgetCircuitBreaker, BudgetException, ModelPricing, BudgetReport + tests

## Phase 2: Spring Boot Starter + Release (Weeks 8-9)
**Goal:** Spring Boot integration (auto-config, Actuator, Micrometer) plus core+starter v0.1.0 release to Maven Central.
**Scope:** SPRING-01 through SPRING-03, REL-01
**Plans:** 4 plans

Plans:
- [ ] 02-01-PLAN.md — Core D-08 live-snapshot + aggregate-breach enhancement, CallTracked.model, starter build setup (Wave 1)
- [ ] 02-02-PLAN.md — Leaf Spring components: BudgetBreakerProperties, BudgetEndpoint, HealthIndicator, MetricsEventCollector (Wave 2)
- [ ] 02-03-PLAN.md — Auto-config wiring, SLF4JEventLogger, imports file, ApplicationContextRunner unit tests (Wave 3)
- [ ] 02-04-PLAN.md — @SpringBootTest smoke test, nmcp aggregation + v0.1.0 bump, README Spring section (Wave 4)

---
*Last updated: 2026-06-12*
