# PR Artifacts Skill — Kotlin/Spring Boot

Produce ALL of the following before opening a PR for review.

## 1. GitHub Issue
- Verify the PR is linked to a GH issue
- Set the issue reference in the PR body: "Closes #N"

## 2. ADR (Architecture Decision Record)
Required when the PR introduces or changes:
- A new library dependency (coroutines, micrometer, Spring Boot)
- Coroutine scope/supervisor design
- Budget enforcement strategy (cancel vs pause)
- Maven Central publishing configuration
- Spring Boot auto-configuration patterns

Not required for: pricing updates, test additions, KDoc improvements.

File as `docs/adr/NNNN-short-title.md`.

## 3. Documentation Update
- `README.md` — if new API, configuration, or usage patterns
- KDoc on all public classes, functions, and properties
- Spring Boot configuration properties documented

## 4. Tests
Verify before shipping:
- [ ] `./gradlew test` passes
- [ ] `./gradlew ktlintCheck` passes
- [ ] Coroutine cancellation tested (budget exceeded → scope cancelled)
- [ ] Soft/hard limit behavior tested
- [ ] Thread safety tested (concurrent token tracking)

## 5. Verification Report
- [ ] No `var` — all `val`
- [ ] No `!!` without justifying comment
- [ ] No `Thread.sleep()` or raw threads — coroutines only
- [ ] Sealed classes for error hierarchy
- [ ] Immutable data classes
- [ ] ADR written (or: not required because ...)
- [ ] Issue: Closes #N
