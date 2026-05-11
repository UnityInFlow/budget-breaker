plugins {
    kotlin("jvm") version "2.1.0" apply false
    id("com.gradleup.nmcp.aggregation")
}

repositories {
    mavenCentral()
}

allprojects {
    group = "io.github.unityinflow"
    version = "0.0.1"

    repositories {
        mavenCentral()
    }
}

// Sonatype Central Portal aggregation. Only the budget-breaker module is
// published — budget-breaker-spring-boot-starter is a Phase 2 stub with
// no source files yet and should NOT appear on Maven Central.

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
}
