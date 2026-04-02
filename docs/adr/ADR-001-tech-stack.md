# ADR-001: Technology Stack

## Status
Accepted

## Context
budget-breaker is a token budget circuit breaker for AI agents. It needs to track token usage
across concurrent coroutines, enforce soft/hard limits, and integrate with the JVM ecosystem.

## Decision

### Language: Kotlin 2.0+ on JVM 21
- Coroutine-first design with structured concurrency
- Sealed classes for domain modelling (exceptions, events)
- Data classes for immutable value objects
- Kotlin DSL for builder patterns

### Build: Gradle multi-module (Kotlin DSL)
- `budget-breaker` — core library, no Spring dependency
- `budget-breaker-spring-boot-starter` — optional auto-configuration

### Concurrency: AtomicLong for token counters
- Lock-free, zero overhead on happy path
- Thread-safe for concurrent coroutine access
- No mutex or synchronized blocks needed for simple counters

### Tracking: Caller-driven (not interceptor-based)
- Users call `trackCall(promptTokens, completionTokens)` explicitly
- No magic proxies, AOP, or bytecode manipulation
- Clear, predictable, testable

### No Spring in core module
- Core library depends only on kotlinx-coroutines
- Spring Boot starter is a separate module
- Users not on Spring can still use the library

## Consequences
- Users must manually report token usage (no automatic interception)
- Two modules to maintain instead of one
- Spring Boot users get auto-configuration for free via starter
