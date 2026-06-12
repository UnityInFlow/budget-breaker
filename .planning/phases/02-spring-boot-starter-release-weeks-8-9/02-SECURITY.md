---
phase: 02
slug: spring-boot-starter-release-weeks-8-9
status: verified
threats_open: 0
asvs_level: 1
created: 2026-06-12
audited: 2026-06-12
auditor: gsd-security-auditor (claude-sonnet-4-6)
---

# Phase 02 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| Spring context / caller | HTTP request to `/actuator/budget` (opt-in only) | Agent IDs, token counts (no secrets, no PII) |
| Build pipeline / Sonatype | Release workflow publishing signed artifacts | GPG key material via env vars; never committed |
| Library jar / host JVM | Auto-config loading at context refresh | Property values (limits, model names, pricing) |

---

## Threat Register

| Threat ID | Category | Component | Disposition | Mitigation | Status |
|-----------|----------|-----------|-------------|------------|--------|
| T-02-01 | Tampering | Spring/Micrometer dependency coordinates | mitigate | Exact version pins: Spring Boot 3.5.3, Micrometer 1.15.0, coroutines 1.10.1 — no ranges | closed |
| T-02-02 | DoS | activeTrackers map in-flight leak | mitigate | `finally { activeTrackers.remove(agentId) }` on every exit path incl. cancellation | closed |
| T-02-03 | Info Disclosure | GET /actuator/budget exposing agent names + token counts | mitigate | Not auto-exposed; KDoc + README document opt-in via `management.endpoints.web.exposure.include`; endpoint is read-only (`@ReadOperation` only) | closed |
| T-02-04 | Tampering | Budget limit bypass via property injection | mitigate | `BudgetBreakerProperties.init { require(...) }` fails startup on soft > hard or non-positive values; proven by 5 unit tests in BudgetBreakerPropertiesTest + `hasFailed()` test in BudgetBreakerAutoConfigurationTest | closed |
| T-02-05 | DoS | Coroutine scope not cancelled on context shutdown | mitigate | `SmartLifecycle.stop()` calls `scope.cancel()` in both `MetricsEventCollector` and `SLF4JEventLogger`; `SupervisorJob` + `Dispatchers.Default` used; `GlobalScope` absent | closed |
| T-02-06 | Info Disclosure | HealthIndicator leaking breach state | accept | D-07 by design: `Health.up()` always returned; breach counts appear as detail keys only, not as health status | closed |
| T-02-07 | Tampering | Auto-config silently overriding user-defined breaker | mitigate | `@ConditionalOnMissingBean` on `budgetCircuitBreaker` bean method; `ApplicationContextRunner` override test (`honors user-defined BudgetCircuitBreaker bean`) proves user bean wins | closed |
| T-02-08 | EoP | Component-scanning in starter jar | mitigate | No `@Component` or `@ComponentScan` annotations in any starter source file; all beans registered via explicit `@Bean` in `@AutoConfiguration` class | closed |
| T-02-09 | Info Disclosure | Secrets leaking into build files / release.yml | mitigate | `providers.environmentVariable(...)` for Sonatype credentials; `useInMemoryPgpKeys` reads `SIGNING_KEY`/`SIGNING_PASSWORD` env vars; CI uses `${{ secrets.* }}` notation; no literal values in any build file | closed |
| T-02-10 | Tampering | Unsigned or wrong-version artifact published | mitigate | GPG signing via `useInMemoryPgpKeys(signingKey, signingPassword)` in `budget-breaker.publishing.gradle.kts`; uniform `version = "0.1.0"` set in root `build.gradle.kts`; both modules included in `nmcpAggregation` | closed |
| T-02-11 | Info Disclosure | README implying /actuator/budget auto-exposed | mitigate | README line 282: `include: health,budget  # opt-in — budget endpoint is NOT auto-exposed` | closed |
| T-02-05-DOS | DoS | Auto-config loading on minimal classpath (no Actuator/Micrometer) | mitigate | Nested `@Configuration(proxyBeanMethods=false)` inner classes `ActuatorConfiguration` and `MicrometerConfiguration` each guarded by string-form `@ConditionalOnClass(name=[...])` preventing class load; proven by two `FilteredClassLoader` tests | closed |
| T-02-09-DOS | DoS | Collector loop permanently silenced by handler exception | mitigate | `try/catch (e: Exception) { if (e is CancellationException) throw e; log.warn(...) }` wraps `when(event)` dispatch in both `MetricsEventCollector.start()` and `SLF4JEventLogger.start()`; resilience test `event collection survives a handler exception` confirms second event processed after first throws | closed |
| T-02-10-INFO | Info Disclosure | log.warn on handler failure includes event toString() | accept | `BudgetEvent` fields: agentId, token counts, model name, percentUsed, estimatedCostUsd — no secrets or PII flow through the sealed class hierarchy; WARN level is appropriate | closed |
| T-02-SC | Tampering | npm/pip/cargo installs | accept | No package-manager installs in any build file or workflow; all dependencies are Maven coordinates fetched from Maven Central | closed |

*Status: open · closed*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-02-01 | T-02-06 | Health indicator always returns UP regardless of budget breach state. Design decision D-07: a token budget breach means an agent spent too many tokens, not that the application is unhealthy. Flipping to DOWN would trigger Kubernetes liveness/readiness probe failures and kill healthy pods. Breach counts are surfaced as health detail keys for operator observability without disrupting deployment health gates. | Jiří Hermann (phase author) | 2026-06-12 |
| AR-02-02 | T-02-10-INFO | `log.warn` on handler failure includes `event.toString()`. `BudgetEvent` is a sealed class containing only: agentId (string), token counts (long), model name (string), percentUsed (double), estimatedCostUsd (double). No secrets, credentials, prompt text, or PII are present in any event variant. WARN level is appropriate for an unexpected handler failure. | Jiří Hermann (phase author) | 2026-06-12 |
| AR-02-03 | T-02-SC | Project uses only Maven Central coordinates for all dependencies (Gradle build, no npm/pip/cargo). Supply-chain risk is accepted at the Maven Central trust boundary level, consistent with all other JVM tooling in the UnityInFlow ecosystem. | Jiří Hermann (phase author) | 2026-06-12 |

*Accepted risks do not resurface in future audit runs.*

---

## Unregistered Threat Flags

No threat flags from SUMMARY.md lacked a threat register mapping. All flags (T-02-03, T-02-04, T-02-05, T-02-06, T-02-07, T-02-08, T-02-09, T-02-10, T-02-11) map directly to registered threats.

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-06-12 | 15 | 15 | 0 | gsd-security-auditor (claude-sonnet-4-6) |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-06-12
