#!/bin/sh
# verify-published.sh — post-publish verification harness (D-04, STARTER-01/06).
#
# Proves "published" in the strict sense: a clean consumer can resolve the coordinate from
# Maven Central (NOT mavenLocal, NOT a sibling project) and boot the auto-configuration.
#
# What it does:
#   1. Bounded-polls repo1.maven.org for the POMs of BOTH published coordinates — the starter
#      AND the core library — because success criterion 1 requires both to be resolvable
#      (RESEARCH Open Question 2 / Pitfall 6). The poll absorbs the ~30-minute Central → repo1
#      propagation delay AFTER the human presses Publish (RESEARCH Pitfall 4): verifying too
#      early is the failure mode this guards against.
#   2. Once both POMs are present, runs the standalone clean-consumer test, resolving the
#      starter from Central via -PstarterVersion.
#
# NOTE on `sleep`: the shell `sleep` below is a propagation poll/retry — it is the idiomatic
# choice for waiting on an external CDN. It is DISTINCT from (and not subject to) the CLAUDE.md
# ban on `Thread.sleep()` / raw threads in Kotlin async code.
#
# Usage: sh scripts/verify-published.sh [version]   (version defaults to 0.1.0)
set -eu

VER="${1:-0.1.0}"
BASE="https://repo1.maven.org/maven2"
GROUP_PATH="io/github/unityinflow"

# Bounded poll: 30 attempts x 60s = up to 30 minutes per coordinate (NOT an infinite loop).
MAX_ATTEMPTS=30
SLEEP_SECONDS=60

# Poll a single canonical repo1 POM URL until it resolves, or fail after MAX_ATTEMPTS.
# $1 = human label, $2 = full POM URL
poll_pom() {
    label="$1"
    url="$2"
    attempt=0
    while [ "$attempt" -lt "$MAX_ATTEMPTS" ]; do
        attempt=$((attempt + 1))
        if curl -fsI "$url" >/dev/null 2>&1; then
            echo "[$label] resolvable on repo1 (attempt $attempt/$MAX_ATTEMPTS): $url"
            return 0
        fi
        echo "[$label] not yet on repo1 (attempt $attempt/$MAX_ATTEMPTS) — sleeping ${SLEEP_SECONDS}s"
        sleep "$SLEEP_SECONDS"
    done
    echo "[$label] POM never appeared on repo1 after $MAX_ATTEMPTS attempts: $url" >&2
    return 1
}

# Coordinate 1: the Spring Boot starter (the artifact the consumer depends on directly).
STARTER_URL="${BASE}/${GROUP_PATH}/budget-breaker-spring-boot-starter/${VER}/budget-breaker-spring-boot-starter-${VER}.pom"
# Coordinate 2: the core library (pulled in transitively by the starter — both must be resolvable).
CORE_URL="${BASE}/${GROUP_PATH}/budget-breaker/${VER}/budget-breaker-${VER}.pom"

echo "Verifying budget-breaker $VER is resolvable from Maven Central (repo1)..."
poll_pom "starter" "$STARTER_URL"
poll_pom "core" "$CORE_URL"
echo "Both coordinates resolvable on repo1 — running clean-consumer boot test."

# Resolve + boot from Central. --refresh-dependencies so a prior 404 isn't served from cache;
# --no-configuration-cache per CLAUDE.md (signing/kapt config-cache friction).
./gradlew -p verification/clean-consumer --refresh-dependencies \
    -PstarterVersion="$VER" test --no-configuration-cache

echo "VERIFIED: budget-breaker $VER is clean-consumer-resolvable from Maven Central."
