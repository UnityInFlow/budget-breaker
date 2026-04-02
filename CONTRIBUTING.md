# Contributing to budget-breaker

Thanks for considering a contribution! This guide covers everything you need to get started.

## Prerequisites

- JDK 21+ (we recommend [Eclipse Temurin](https://adoptium.net/))
- Kotlin 2.0+ (managed by Gradle -- no separate install needed)

## Dev Setup

```bash
git clone git@github.com:UnityInFlow/budget-breaker.git
cd budget-breaker

# Build everything
./gradlew build

# Run tests
./gradlew test

# Format code (must pass before committing)
./gradlew ktlintFormat
```

## Project Structure

```
budget-breaker/                          # Core library (pure Kotlin + coroutines)
  src/main/kotlin/dev/unityinflow/budget/
    AgentBudget.kt                       # Budget configuration data class
    TokenTracker.kt                      # Thread-safe AtomicLong token counter
    BudgetCircuitBreaker.kt              # Coroutine supervisor with withBudget() API
    BudgetScope.kt                       # DSL scope for tracking calls
    BudgetException.kt                   # Sealed exception hierarchy
    BudgetEvent.kt                       # SharedFlow event types
    ModelPricing.kt                      # LLM cost estimation
    BudgetReport.kt                      # Post-run summary

budget-breaker-spring-boot-starter/      # Spring Boot auto-config (Phase 2)
```

## How to Add Model Pricing

Default pricing lives in `ModelPricing.kt` in the companion object's `DEFAULTS` map.

To add a new model:

1. Add an entry to the `DEFAULTS` map in `ModelPricing.kt`:
   ```kotlin
   "model-name" to PriceConfig(inputPerMillion = 2.0, outputPerMillion = 8.0),
   ```
2. Add a test in `ModelPricingTest.kt` verifying the cost estimate.
3. Update the pricing table in `README.md`.

Users can also provide custom pricing at runtime without modifying the library:

```kotlin
val pricing = ModelPricing(
    overrides = mapOf(
        "my-custom-model" to ModelPricing.PriceConfig(1.0, 5.0)
    )
)
val breaker = BudgetCircuitBreaker(pricing = pricing)
```

## How to Extend

### Adding a New BudgetEvent Type

1. Add a new subclass to the `BudgetEvent` sealed class in `BudgetEvent.kt`.
2. Emit it from `BudgetScope` at the appropriate point.
3. Add tests in `BudgetCircuitBreakerTest.kt` verifying the event is emitted.

### Adding a New Exception Type

1. Add a new subclass to the `BudgetException` sealed class in `BudgetException.kt`.
2. Handle it in `BudgetScope.trackCall()` or `BudgetCircuitBreaker.withBudget()`.
3. Add tests covering the throw + catch path.

## Commit Conventions

We follow [Conventional Commits](https://www.conventionalcommits.org/):

```
feat: add Mistral model pricing defaults
fix: TokenTracker overflow on Long.MAX_VALUE
test: add edge cases for zero-token budgets
docs: update README with new model pricing
chore: bump coroutines to 1.10.2
refactor: extract limit checking into separate function
```

## Branch Naming

```
feat/add-mistral-pricing
fix/tracker-overflow
docs/contributing-guide
```

## Code Style

- Kotlin idioms first (`let`, `also`, `apply`, `run`)
- `val` only -- no `var`
- No `!!` without a comment explaining safety
- Coroutines for all async work -- never `Thread.sleep()`
- KDoc on all public APIs
- `ktlint` must pass before every commit

## Testing

- JUnit 5 + Kotest matchers
- Every public API: at least 3 passing and 3 failing cases
- Edge cases: zero tokens, Long.MAX_VALUE, concurrent access
- Run with `./gradlew test`

## Pull Requests

1. Fork and create a feature branch from `main`.
2. Write tests first (TDD preferred).
3. Ensure `./gradlew build` and `./gradlew test` pass.
4. Open a PR against `main` with a clear description.
5. One approval required to merge.

## License

By contributing, you agree that your contributions will be licensed under the [MIT License](LICENSE).
