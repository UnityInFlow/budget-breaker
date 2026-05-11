# budget-breaker — Kotlin Coroutine Budget Circuit Breaker

## Project Overview

**Tool 05** in the [UnityInFlow](https://github.com/UnityInFlow) ecosystem.

Reactive, coroutine-aware circuit breaker for AI agent token budgets. Enforces soft and hard token limits with clean coroutine cancellation, cost estimation across Claude/GPT/Gemini models, and Micrometer metrics integration. The first Kotlin library in the ecosystem.

**Phase:** 2 | **Stack:** Kotlin | **Maven:** `io.github.unityinflow:budget-breaker`

## Status

Ready to build — Phase 1 complete, all 4 tools shipped. First Kotlin/JVM tool in the ecosystem.

## Reference Documents

- `05-budget-breaker.md` — Feature spec, core API, Spring Boot integration, exception hierarchy, Micrometer metrics, implementation todos (Weeks 7-9)
- `claude-code-harness-engineering-guide-v2.md` — Harness engineering patterns and best practices

Read these before making architectural or scope decisions.

## Tooling

| Tool | Status | Usage |
|---|---|---|
| **GSD** | Installed (global) | `/gsd:new-project` to scaffold when ready. `/gsd:plan-phase` and `/gsd:execute-phase` for structured development. |
| **RTK** | Active (v0.34.2) | Automatic via hooks. Compresses gradle, git output. ~80% token savings. |
| **Superpowers** | Active (v5.0.5) | Auto-triggers brainstorming, TDD, planning, code review, debugging skills. |

## Constraints

### Kotlin (inherited from ecosystem CLAUDE.md)
- Kotlin 2.0+, JVM target 21
- Gradle (Kotlin DSL only — never Groovy)
- Test with JUnit 5 + Kotest matchers
- Coroutines for all async work — never `Thread.sleep()` or raw threads
- Immutable data classes preferred over mutable state
- Sealed classes for domain modelling (results, errors, states)
- `Result<T>` or sealed classes instead of exceptions for expected failure cases
- No `var` — always `val`, refactor if mutation seems needed
- No `!!` without a comment explaining why it's safe
- `ktlint` before every commit
- Group: `io.github.unityinflow`
- Maven Central publishing via Sonatype

### General
- Test coverage >80% on core logic before release
- No secrets committed — all credentials via environment variables
- KDoc documentation on all public APIs

## Acceptance Criteria — v0.0.1

- [ ] `AgentBudget` data class with soft/hard limit configuration
- [ ] `TokenTracker`: thread-safe token counter with limit checks
- [ ] `BudgetCircuitBreaker`: coroutine supervisor with cancel-on-breach
- [ ] `BudgetException` sealed class hierarchy (SoftLimit, HardLimit)
- [ ] `ModelPricing` object with Claude, GPT-4o, Gemini pricing
- [ ] `BudgetReport` data class and report generation
- [ ] Spring Boot auto-configuration with `@ConfigurationProperties`
- [ ] Spring Boot Actuator endpoint: `/actuator/budget`
- [ ] Micrometer metrics integration
- [ ] Unit tests: cancellation, soft limit, hard limit, report accuracy
- [ ] Zero overhead on happy path (<1ms)
- [ ] Published to Maven Central as `io.github.unityinflow:budget-breaker`
- [ ] README with problem statement, installation, 3 usage examples

## Development Workflow

When ready to build:

1. `/gsd:new-project` — describe budget-breaker, feed existing spec
2. `/gsd:discuss-phase 1` — lock in decisions for Week 7 (core library: AgentBudget, TokenTracker, BudgetCircuitBreaker, BudgetException)
3. `/gsd:plan-phase 1` — atomic task plans with file paths
4. `/gsd:execute-phase 1` — parallel execution with fresh context windows
5. `/gsd:discuss-phase 2` — lock in decisions for Weeks 8-9 (Spring Boot starter, Micrometer, Maven Central publishing)
6. `/gsd:plan-phase 2` — atomic task plans
7. `/gsd:execute-phase 2` — build and ship

Superpowers skills (TDD, code review, debugging) activate automatically during execution.

## Key Dependencies (for reference, not installed yet)

- `kotlinx-coroutines-core` — coroutine support
- `micrometer-core` — metrics
- `spring-boot-starter` — auto-configuration (optional module)
- `spring-boot-actuator` — health/budget endpoints (optional module)

---

## CI / Self-Hosted Runners

Use UnityInFlow org-level self-hosted runners. Never use `ubuntu-latest`.

```yaml
runs-on: [arc-runner-unityinflow]
```

Available runners: `hetzner-runner-1/2/3` (X64), `orangepi-runner` (ARM64).

---

## Do Not

- Do not add Kafka as a dependency — Kotlin Flows by default, Kafka is opt-in
- Do not use `var` in Kotlin — always `val`
- Do not use `!!` without a comment explaining why it's safe
- Do not use `Thread.sleep()` or raw threads — use coroutines
- Do not commit secrets or API keys
- Do not skip writing tests
- Do not inline the reference docs into this file — read them by path
