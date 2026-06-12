# Milestones

## v1.0 Core + Spring Boot Starter (Shipped: 2026-06-12)

**Phases completed:** 2 phases, 5 plans, 14 tasks

**Key accomplishments:**

- Live in-flight agent snapshot via `getAllReports()`, aggregate breach counts, `CallTracked.model` field, and starter build wired with Spring Boot 3.5.3 + Micrometer 1.15.0.
- Four plain Spring leaf classes (no auto-wiring yet): `@ConfigurationProperties` data class with fail-fast init validation, read-only `@Endpoint(id=budget)` exposing live snapshots, always-UP `HealthIndicator`, and `MeterBinder+SmartLifecycle` metrics collector with OTel semconv meter names.
- `BudgetBreakerAutoConfiguration` wires all five beans from `BudgetBreakerProperties`, `SLF4JEventLogger` logs soft-limit breaches at WARN via SLF4J, both discoverable via the Spring Boot 3.x `AutoConfiguration.imports` file, and proven by four green `ApplicationContextRunner` tests.
- `BudgetStarterIntegrationTest` boots a real Spring context, verifies `gen_ai.client.token.usage.input/.output` counters appear in `SimpleMeterRegistry` after a tracked call and the budget endpoint reports the agent; root version bumped to 0.1.0 with both modules in the nmcp aggregation, and README documents the full Spring Boot integration.
- Nested @ConditionalOnClass configs guard optional deps (CR-01) and try/catch in collect lambdas makes event handlers resilient (CR-02), restoring SPRING-01 and SPRING-03 to SATISFIED.

---
