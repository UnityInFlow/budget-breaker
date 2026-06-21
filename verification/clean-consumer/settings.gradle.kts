// Standalone verification build (D-04). It has its OWN root and is deliberately NOT added to the
// publishable root settings.gradle.kts `include(...)` aggregation — keeping it out of the
// publishable build guarantees it can never resolve sibling project(":...") modules and must
// resolve the starter from Maven Central instead (RESEARCH anti-pattern: never include the
// consumer in the publishable build).
rootProject.name = "budget-breaker-clean-consumer"
