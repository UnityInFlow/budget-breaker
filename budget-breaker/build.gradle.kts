plugins {
    kotlin("jvm")
    id("budget-breaker.publishing")
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")

    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
    testImplementation("io.kotest:kotest-assertions-core:5.9.1")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}

publishing {
    publications {
        named<MavenPublication>("maven") {
            pom {
                name.set("budget-breaker")
                description.set(
                    "Reactive, coroutine-aware circuit breaker for AI agent token budgets. " +
                        "Enforces soft and hard token limits with clean coroutine cancellation " +
                        "and cost estimation across Claude/GPT/Gemini models.",
                )
            }
        }
    }
}
