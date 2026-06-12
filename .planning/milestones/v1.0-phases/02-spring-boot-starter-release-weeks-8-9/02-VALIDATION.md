---
phase: 2
slug: spring-boot-starter-release-weeks-8-9
status: approved
nyquist_compliant: true
wave_0_complete: true
created: 2026-06-12
---

# Phase 2 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 (`useJUnitPlatform()`) + Kotest matchers 5.9.1 + kotlinx-coroutines-test 1.10.1; Spring `ApplicationContextRunner` / `@SpringBootTest` for the starter |
| **Config file** | none — configured per module in `tasks.test { useJUnitPlatform() }`; test deps declared in `budget-breaker-spring-boot-starter/build.gradle.kts` (02-01 Task 3) |
| **Quick run command** | `./gradlew :budget-breaker-spring-boot-starter:test --no-configuration-cache` (or `:budget-breaker:test` for core-only tasks) |
| **Full suite command** | `./gradlew build --no-configuration-cache` |
| **Estimated runtime** | ~30–90 seconds (Spring context boot dominates the @SpringBootTest) |

---

## Sampling Rate

- **After every task commit:** Run the task's `<automated>` command (module-scoped `:test` or `:compileKotlin`)
- **After every plan wave:** Run `./gradlew build --no-configuration-cache`
- **Before `/gsd-verify-work`:** Full suite (`./gradlew build`) must be green
- **Max feedback latency:** 90 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 02-01-01 | 01 | 1 | SPRING-03 | T-02-02 / — | CallTracked carries model → metrics can tag {agent,model} (no untagged leakage) | unit | `./gradlew :budget-breaker:test --tests "io.github.unityinflow.budget.BudgetCircuitBreakerTest"` | ❌ W0 (test extended) | ⬜ pending |
| 02-01-02 | 01 | 1 | SPRING-02 | T-02-02 | activeTrackers removed in finally on every exit path (no in-flight leak / DoS) | unit | `./gradlew :budget-breaker:test --tests "io.github.unityinflow.budget.BudgetCircuitBreakerTest"` | ❌ W0 (test extended) | ⬜ pending |
| 02-01-03 | 01 | 1 | SPRING-01, REL-01 | T-02-01 | Spring/Micrometer pinned versions, compileOnly (no forced SB version on consumers) | build (dependency resolution) | `./gradlew :budget-breaker-spring-boot-starter:dependencies --configuration testCompileClasspath --no-configuration-cache` | ✅ (build file) | ⬜ pending |
| 02-02-01 | 02 | 2 | SPRING-01 | T-02-04 | init{} require() fails fast on soft>=hard / non-positive (no limit bypass) | unit | `./gradlew :budget-breaker-spring-boot-starter:test --tests "io.github.unityinflow.budget.spring.BudgetBreakerPropertiesTest"` | ❌ W0 | ⬜ pending |
| 02-02-02 | 02 | 2 | SPRING-02 | T-02-03 | Read-only @Endpoint (no @Write/@Delete); Health always UP (no probe flap) | compile (behavior proven in 02-03-03 / 02-04-01) | `./gradlew :budget-breaker-spring-boot-starter:compileKotlin --no-configuration-cache` | ✅ (source) | ⬜ pending |
| 02-02-03 | 02 | 2 | SPRING-03 | T-02-05 | SmartLifecycle scope (no GlobalScope); AtomicBoolean/AtomicReference state (no var/!!); exact OTel meter names | compile (behavior proven in 02-04-01) | `./gradlew :budget-breaker-spring-boot-starter:compileKotlin --no-configuration-cache` | ✅ (source) | ⬜ pending |
| 02-03-01 | 03 | 3 | SPRING-01 | T-02-05 | Logs only soft breaches at WARN; scope cancelled on stop(); AtomicBoolean running flag | compile (behavior proven in 02-03-03) | `./gradlew :budget-breaker-spring-boot-starter:compileKotlin --no-configuration-cache` | ✅ (source) | ⬜ pending |
| 02-03-02 | 03 | 3 | SPRING-01 | T-02-08 | No @Component/@ComponentScan (starter must not scan); imports-file discovery | compile (behavior proven in 02-03-03) | `./gradlew :budget-breaker-spring-boot-starter:compileKotlin --no-configuration-cache` | ✅ (source + imports) | ⬜ pending |
| 02-03-03 | 03 | 3 | SPRING-01 | T-02-07 / T-02-04 | @ConditionalOnMissingBean back-off (user bean wins); fail-fast on invalid props (hasFailed) | unit (ApplicationContextRunner) | `./gradlew :budget-breaker-spring-boot-starter:test --tests "io.github.unityinflow.budget.spring.BudgetBreakerAutoConfigurationTest"` | ❌ W0 | ⬜ pending |
| 02-04-01 | 04 | 4 | SPRING-02, SPRING-03 | T-02-03 | Real Spring context: gen_ai.* meters after tracked call; agent appears in endpoint | integration (@SpringBootTest + SimpleMeterRegistry) | `./gradlew :budget-breaker-spring-boot-starter:test --tests "io.github.unityinflow.budget.spring.BudgetStarterIntegrationTest"` | ❌ W0 | ⬜ pending |
| 02-04-02 | 04 | 4 | REL-01 | T-02-09 / T-02-10 | No literal secrets in build/CI; signed artifact set at uniform 0.1.0 | build (publish + artifact listing) | `./gradlew :budget-breaker-spring-boot-starter:publishToMavenLocal --no-configuration-cache` | ✅ (build/CI files) | ⬜ pending |
| 02-04-03 | 04 | 4 | SPRING-01, SPRING-02, SPRING-03 | T-02-11 | README documents opt-in actuator exposure; no deferred features claimed as shipped | grep gate | `grep -c "budget-breaker-spring-boot-starter" README.md` | ✅ (README) | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

**Sampling continuity:** No run of 3 consecutive tasks lacks an `<automated>` verify — every task above carries a runnable command. Compile-only gates (02-02-02/03, 02-03-01/02) are each backed by a downstream behavioral test in the same or next wave (02-03-03 ApplicationContextRunner, 02-04-01 @SpringBootTest), so no behavior ships without an automated behavioral check.

---

## Wave 0 Requirements

Test files created/extended before or as the first action of the owning task (per RESEARCH Wave 0 Gaps + the per-task map above):

- [x] `budget-breaker/src/test/kotlin/io/github/unityinflow/budget/BudgetCircuitBreakerTest.kt` — EXTENDED for CallTracked.model propagation (02-01-01) and snapshot/aggregate APIs (02-01-02). Pre-existing file; new tests added in-task.
- [x] `budget-breaker-spring-boot-starter/src/test/kotlin/io/github/unityinflow/budget/spring/BudgetBreakerPropertiesTest.kt` — NEW, plain JUnit5 unit test for `init{}` validation (02-02-01).
- [x] `budget-breaker-spring-boot-starter/src/test/kotlin/io/github/unityinflow/budget/spring/BudgetBreakerAutoConfigurationTest.kt` — NEW, ApplicationContextRunner tests for SPRING-01 conditionals (02-03-03).
- [x] `budget-breaker-spring-boot-starter/src/test/kotlin/io/github/unityinflow/budget/spring/BudgetStarterIntegrationTest.kt` — NEW, @SpringBootTest smoke test for SPRING-02/SPRING-03 (02-04-01).
- [x] Test framework: existing Gradle + JUnit 5 + Kotest in core; starter test classpath declared in `budget-breaker-spring-boot-starter/build.gradle.kts` (02-01-03) — no separate framework install needed.

No standalone Wave 0 plan is required: each MISSING test file is created as the RED step of the task that owns it (TDD tasks 02-01-01, 02-02-01, 02-03-03, 02-04-01) and the starter test classpath that those tests compile against is delivered in 02-01-03 (Wave 1, before any starter test runs in Wave 2+).

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Starter artifact present in the nmcp/Sonatype bundle and consumable from Maven Central | REL-01 | Maven Central propagation + Sonatype Central Portal release is an external, network/credential-gated step (USER_MANAGED publishingType); cannot run in unit/integration tests | After `publishToMavenLocal` is green and v0.1.0 is tagged, trigger `.github/workflows/release.yml`; then in a throwaway project add `implementation("io.github.unityinflow:budget-breaker-spring-boot-starter:0.1.0")` and confirm it resolves. |

The local half of REL-01 (artifact set produced at 0.1.0 with sources/javadoc/signature) IS automated via `publishToMavenLocal` in 02-04-02; only the remote Central propagation is manual.

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify (every task has a runnable command; compile-only gates are backed by downstream behavioral tests)
- [x] Wave 0 covers all MISSING references (4 test files, each created as the RED step of its owning task; starter test classpath delivered in 02-01-03)
- [x] No watch-mode flags (all commands are one-shot `:test` / `:compileKotlin` / `:dependencies` / `publishToMavenLocal`; `--no-configuration-cache` only)
- [x] Feedback latency < 90s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** approved
</content>
