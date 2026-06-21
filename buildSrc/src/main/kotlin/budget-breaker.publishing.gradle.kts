/*
 * Publishing convention plugin for budget-breaker modules.
 *
 * Produces signed artifacts suitable for Sonatype Central Portal:
 *   - main jar
 *   - sources jar  (required by Sonatype)
 *   - javadoc jar  (Dokka-rendered KDoc — real API docs, not an empty placeholder)
 *   - pom with full metadata (name, description, url, licenses,
 *     developers, scm, issueManagement)
 *   - .asc signature for every artifact above
 *
 * Each module that applies this plugin must override `name` and
 * `description` via:
 *
 * publishing {
 *     publications {
 *         named<MavenPublication>("maven") {
 *             pom {
 *                 name.set("budget-breaker — Module Name")
 *                 description.set("Module description.")
 *             }
 *         }
 *     }
 * }
 *
 * Signing reads SIGNING_KEY / SIGNING_PASSWORD env vars at execution time.
 * Local builds without these env vars produce unsigned artifacts —
 * useful for publishToMavenLocal. CI release workflow sets both.
 *
 * NOTE: Gradle configuration cache may serialize stale env var values.
 * Release workflow passes --no-configuration-cache to force re-read.
 */

plugins {
    `java-library`
    `maven-publish`
    signing
    id("com.gradleup.nmcp")
    // Dokka v2 Javadoc-format engine — renders KDoc into the javadoc jar (D-01, STARTER-05).
    id("org.jetbrains.dokka-javadoc")
}

java {
    // No Java-plugin javadoc-jar helper is used here: the Dokka-rendered `dokkaJavadocJar`
    // below supplies the `javadoc`-classifier artifact. Registering both would publish a
    // duplicate `javadoc` classifier and fail Central bundle validation (RESEARCH Pitfall 1).
    withSourcesJar()
}

// Dokka v2, applied via a buildSrc precompiled script plugin, cannot always auto-detect the
// module's Kotlin source sets through the KotlinBasePlugin (classloader isolation between the
// buildSrc plugin classpath and the modules' `kotlin("jvm")` — Gradle #25616 / #35117), which
// silently produces an EMPTY javadoc jar. Register the main source directory explicitly so the
// KDoc is always rendered regardless of auto-detection.
dokka {
    dokkaSourceSets.register("main") {
        sourceRoots.from(project.file("src/main/kotlin"))
    }
}

// Real Dokka-rendered javadoc jar, attached to the signed "maven" publication (D-01).
val dokkaJavadocJar by tasks.registering(Jar::class) {
    dependsOn(tasks.named("dokkaGeneratePublicationJavadoc"))
    from(tasks.named("dokkaGeneratePublicationJavadoc"))
    archiveClassifier.set("javadoc")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            // Attach the Dokka javadoc jar. It becomes part of the "maven" publication, so the
            // existing signing block signs it automatically (.asc produced for the javadoc jar).
            artifact(dokkaJavadocJar)
            pom {
                name.set(provider { project.name })
                description.set(provider { "budget-breaker module: ${project.name}" })
                url.set("https://github.com/UnityInFlow/budget-breaker")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                        distribution.set("repo")
                    }
                }
                developers {
                    developer {
                        id.set("jhermann")
                        name.set("Jiří Hermann")
                        email.set("jiri@unityinflow.com")
                        organization.set("UnityInFlow")
                        organizationUrl.set("https://github.com/UnityInFlow")
                    }
                }
                scm {
                    connection.set("scm:git:https://github.com/UnityInFlow/budget-breaker.git")
                    developerConnection.set("scm:git:ssh://git@github.com/UnityInFlow/budget-breaker.git")
                    url.set("https://github.com/UnityInFlow/budget-breaker")
                }
                issueManagement {
                    system.set("GitHub")
                    url.set("https://github.com/UnityInFlow/budget-breaker/issues")
                }
            }
        }
    }
}

signing {
    val signingKey: String? = providers.environmentVariable("SIGNING_KEY").orNull
    val signingPassword: String? = providers.environmentVariable("SIGNING_PASSWORD").orNull
    if (signingKey != null && signingPassword != null) {
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications["maven"])
    } else {
        logger.lifecycle(
            "[budget-breaker.publishing] Signing skipped for ${project.name} — " +
                "SIGNING_KEY / SIGNING_PASSWORD env vars not set.",
        )
    }
}
