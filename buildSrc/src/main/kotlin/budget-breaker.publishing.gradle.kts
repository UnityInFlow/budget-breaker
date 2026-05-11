/*
 * Publishing convention plugin for budget-breaker modules.
 *
 * Produces signed artifacts suitable for Sonatype Central Portal:
 *   - main jar
 *   - sources jar  (required by Sonatype)
 *   - javadoc jar  (required by Sonatype — empty is acceptable)
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
}

java {
    withSourcesJar()
    withJavadocJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
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
