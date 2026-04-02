# Code Review Skill — Kotlin/Spring Boot

## Review Checklist
- [ ] No `var` — always `val`. Refactor if mutation seems needed
- [ ] No `!!` without a comment explaining why it's safe
- [ ] No `Thread.sleep()` or raw threads — use `delay()` and coroutines
- [ ] Sealed classes for domain modelling (errors, states, results)
- [ ] Immutable data classes for value objects
- [ ] `Result<T>` or sealed classes for expected failures (not exceptions)
- [ ] Coroutine scope properly managed (no leaks, proper cancellation)
- [ ] `suspend` functions only called from coroutine context
- [ ] KDoc on all public items (classes, functions, properties)
- [ ] Extension functions for domain enrichment where it improves clarity
- [ ] Kotlin idioms: `let`, `also`, `apply`, `run`, `with` where they help
- [ ] Companion objects for factory methods
- [ ] No wildcard imports
- [ ] Test names: `should <behavior> when <condition>`
- [ ] Kotest matchers used (not plain JUnit assertions)

## Spring Boot Specific
- [ ] `@ConfigurationProperties` for configuration (not `@Value`)
- [ ] Auto-configuration in `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- [ ] Health indicators implement `HealthIndicator`
- [ ] Actuator endpoints properly secured

## Output Format
Return: summary, blocking issues (must fix), suggestions (nice to have).
Cite each issue as `filepath:line — description`.
