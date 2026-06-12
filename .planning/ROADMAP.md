# Roadmap: budget-breaker

## Phase 1: Core Library (Week 7)

**Goal:** Pure Kotlin library with budget enforcement, no Spring dependency.
**Scope:** CORE-01 through CORE-06
**Deliverables:** AgentBudget, TokenTracker, BudgetCircuitBreaker, BudgetException, ModelPricing, BudgetReport + tests

## Phase 2: Spring Boot Starter + Release (Weeks 8-9)

**Goal:** Spring Boot integration (auto-config, Actuator, Micrometer) plus core+starter v0.1.0 release to Maven Central.
**Scope:** SPRING-01 through SPRING-03, REL-01
**Plans:** 5/5 plans complete
Plans:
**Wave 1**

- [x] 02-01-PLAN.md — Core D-08 live-snapshot + aggregate-breach enhancement, CallTracked.model, starter build setup (Wave 1) ✅ 2026-06-12

**Wave 2** *(blocked on Wave 1 completion)*

- [x] 02-02-PLAN.md — Leaf Spring components: BudgetBreakerProperties, BudgetEndpoint, HealthIndicator, MetricsEventCollector (Wave 2) ✅ 2026-06-12

**Wave 3** *(blocked on Wave 2 completion)*

- [x] 02-03-PLAN.md — Auto-config wiring, SLF4JEventLogger, imports file, ApplicationContextRunner unit tests (Wave 3) ✅ 2026-06-12

**Wave 4** *(blocked on Wave 3 completion)*

- [x] 02-04-PLAN.md — @SpringBootTest smoke test, nmcp aggregation + v0.1.0 bump, README Spring section (Wave 4) ✅ 2026-06-12

**Gap Closure** *(closes CR-01, CR-02 from 02-VERIFICATION.md)*

- [x] 02-05-PLAN.md — @ConditionalOnClass guards for Actuator/Micrometer beans + try/catch resilience in both event collectors (gap closure)

---
*Last updated: 2026-06-12 (Phase 02 gap closure planned — 02-05 closes 2 BLOCKER gaps)*
