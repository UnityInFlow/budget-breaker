#!/bin/bash
cd "$CLAUDE_PROJECT_DIR" || exit 0

# 1. Run tests silently
TEST_OUTPUT=$(./gradlew test -q 2>&1)
if [ $? -ne 0 ]; then
  echo "Tests failed:" >&2
  echo "$TEST_OUTPUT" >&2
  exit 2
fi

# 2. ktlint
LINT_OUTPUT=$(./gradlew ktlintCheck -q 2>&1)
if [ $? -ne 0 ]; then
  echo "ktlint errors:" >&2
  echo "$LINT_OUTPUT" >&2
  exit 2
fi

# 3. PR artifact check
BRANCH=$(git rev-parse --abbrev-ref HEAD 2>/dev/null)
if [[ "$BRANCH" == feat/* ]] || [[ "$BRANCH" == fix/* ]]; then
  ISSUE_LINKED=$(git log origin/main..HEAD --format="%s %b" 2>/dev/null | grep -cE "#[0-9]+")
  if [ "$ISSUE_LINKED" -eq 0 ]; then
    echo "No GitHub issue linked in commit history. Add before finishing." >&2
    exit 2
  fi
fi

# SUCCESS: completely silent
