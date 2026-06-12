plugins {
    kotlin("jvm")
    id("budget-breaker.publishing")
}

dependencies {
    api(project(":budget-breaker"))

    // kotlinx-coroutines is needed directly: the core module uses `implementation` (not `api`),
    // so coroutines are not exposed transitively to the starter compile classpath.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")

    // SLF4J API is compileOnly — every Spring Boot app brings SLF4J + a backend (Logback/Log4j2).
    // The starter must not force a specific SLF4J version on consumers.
    compileOnly("org.slf4j:slf4j-api:2.0.16")

    // Spring Boot and Micrometer are compileOnly to avoid forcing a Spring Boot version on consumers.
    // Each consuming Spring Boot application brings its own managed version.
    compileOnly("org.springframework.boot:spring-boot-autoconfigure:3.5.3")
    compileOnly("org.springframework.boot:spring-boot-actuator-autoconfigure:3.5.3")
    compileOnly("io.micrometer:micrometer-core:1.15.0")

    // Re-declare compileOnly deps for test classpath (Gradle does not include compileOnly on testCompileClasspath)
    testImplementation(kotlin("test"))
    testImplementation("org.slf4j:slf4j-api:2.0.16")
    testImplementation("org.springframework.boot:spring-boot-autoconfigure:3.5.3")
    testImplementation("org.springframework.boot:spring-boot-actuator-autoconfigure:3.5.3")
    testImplementation("org.springframework.boot:spring-boot-starter-test:3.5.3")
    testImplementation("io.micrometer:micrometer-core:1.15.0")
    testImplementation("io.kotest:kotest-assertions-core:5.9.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
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
                name.set("budget-breaker-spring-boot-starter")
                description.set(
                    "Spring Boot auto-configuration for budget-breaker: " +
                        "@ConfigurationProperties, Actuator endpoint, and Micrometer metrics.",
                )
            }
        }
    }
}
