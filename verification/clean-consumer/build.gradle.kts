// Clean-consumer verification build (D-04, STARTER-06).
//
// "Published" means: a consumer that knows NOTHING about this repo can resolve the starter
// coordinate from Maven Central and boot its auto-configuration. This build proves exactly that:
//   - Maven Central is the ONLY repository — there is deliberately NO local-repo lookup and NO
//     sibling-module dependency, so a local install or sibling module can never falsely pass
//     the check (T-1-falsepass).
//   - The starter version is supplied via `-PstarterVersion` (never hardcoded) so Phase 2
//     (kore v0.1.0, KORE-06) can reuse this harness verbatim by swapping the coordinate/version.
plugins {
    // This is a STANDALONE build (its own settings root), so it cannot inherit the Kotlin plugin
    // version from the publishable root build's plugins block — the version is pinned here to match
    // the rest of the repo (Kotlin 2.1.21). Resolved from gradlePluginPortal (see settings).
    kotlin("jvm") version "2.1.21"
}

repositories {
    // The ONLY repository — the entire point of D-04. Do NOT add the local Maven repo: a locally
    // installed jar would let the test pass without the artifact ever reaching Central.
    mavenCentral()
}

// Supplied on the command line via `-PstarterVersion=<x>` (default in gradle.properties so the
// build is configurable without `-P` locally; the script/CI always passes the real version).
val starterVersion: String by project

dependencies {
    // The published coordinate ONLY — no sibling-module dependency. This is what resolves from
    // Central and brings BudgetBreakerAutoConfiguration + its AutoConfiguration.imports on the
    // classpath, transitively dragging in the core budget-breaker artifact.
    testImplementation("io.github.unityinflow:budget-breaker-spring-boot-starter:$starterVersion")
    testImplementation("org.springframework.boot:spring-boot-starter-test:3.5.3")
    // SimpleMeterRegistry lives in micrometer-core (the starter declares it compileOnly, so the
    // consumer must bring its own MeterRegistry implementation, as a real Spring Boot app would).
    testImplementation("io.micrometer:micrometer-core:1.15.0")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}
