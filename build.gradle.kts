plugins {
    kotlin("jvm") version "2.1.21" apply false
    id("com.gradleup.nmcp.aggregation")
}

repositories {
    mavenCentral()
}

allprojects {
    group = "io.github.unityinflow"
    version = "0.1.0"

    repositories {
        mavenCentral()
    }
}

// Sonatype Central Portal aggregation. Both modules are published as of v0.1.0:
// - budget-breaker (core library)
// - budget-breaker-spring-boot-starter (Spring Boot auto-configuration)

nmcpAggregation {
    centralPortal {
        username = providers.environmentVariable("SONATYPE_USERNAME")
        password = providers.environmentVariable("SONATYPE_PASSWORD")
        publishingType = "USER_MANAGED"
        publicationName = "budget-breaker-${project.version}"
    }
}

dependencies {
    nmcpAggregation(project(":budget-breaker"))
    nmcpAggregation(project(":budget-breaker-spring-boot-starter"))
}
