plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation("com.gradleup.nmcp:nmcp:1.4.4")

    // Dokka Gradle Plugin v2 — plugin-classpath only so the publishing convention can apply
    // `org.jetbrains.dokka-javadoc` and render KDoc into the javadoc jar (D-01, STARTER-05).
    // Declared `implementation` (NOT `api`): it never enters any consumer POM (STARTER-04 leak rule).
    implementation("org.jetbrains.dokka:dokka-gradle-plugin:2.2.0")
}
