#!/bin/bash
cd "$CLAUDE_PROJECT_DIR" || exit 0

# Kotlin: compile + ktlint — silent on success
OUTPUT=$(./gradlew compileKotlin ktlintCheck -q 2>&1)
if [ $? -ne 0 ]; then
  echo "Kotlin compile/lint errors:" >&2
  echo "$OUTPUT" >&2
  exit 2
fi

# SUCCESS: completely silent
