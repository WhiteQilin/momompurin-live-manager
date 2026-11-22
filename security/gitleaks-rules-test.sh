#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Momompurin Live Manager — gitleaks rule regression test
#
# The android-keystore-password rule is broad because RE2 has no lookahead, so
# the allowlist carries the discrimination between "a password" and "a
# reference to one". That is exactly the kind of pair that drifts: a future
# edit can widen the allowlist until the rule catches nothing, and nobody
# notices because a green build looks like a passing gate.
#
# This asserts BOTH directions against synthetic fixtures:
#   must-detect  — a real credential pasted into a Gradle/properties file
#   must-ignore  — this project's legitimate references to local.properties
#
# Requires gitleaks on PATH (or set GITLEAKS). Usage:
#     security/gitleaks-rules-test.sh
# ---------------------------------------------------------------------------
set -uo pipefail

cd "$(dirname "$0")/.." || exit 1

GITLEAKS=${GITLEAKS:-gitleaks}
if ! command -v "$GITLEAKS" >/dev/null 2>&1 && [ ! -x "$GITLEAKS" ]; then
  echo "gitleaks not found. Install it, or set GITLEAKS=/path/to/gitleaks" >&2
  exit 2
fi

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
fail=0

run_case() {
  local name=$1 expected=$2 content=$3
  local dir="$work/$name"
  mkdir -p "$dir"
  printf '%s\n' "$content" > "$dir/build.gradle.kts"

  local out
  out=$("$GITLEAKS" detect --source "$dir" --config security/gitleaks.toml \
        --no-git --redact --exit-code 0 2>&1)
  local found=0
  echo "$out" | grep -qi "leaks found: [1-9]" && found=1

  if [ "$expected" = "detect" ] && [ "$found" -eq 1 ]; then
    echo "  ok    [$name] credential detected"
  elif [ "$expected" = "ignore" ] && [ "$found" -eq 0 ]; then
    echo "  ok    [$name] reference ignored"
  else
    echo "  FAIL  [$name] expected=$expected detected=$found" >&2
    echo "$out" | sed 's/^/          /' >&2
    fail=1
  fi
}

echo "must-detect (a real secret pasted in):"
run_case quoted-literal   detect 'storePassword = "hunter2hunter2"'
run_case single-quoted    detect "storePassword='hunter2hunter2'"
run_case bare-literal     detect 'storePassword = hunter2hunter2'
run_case key-quoted       detect 'keyPassword = "s3cretpass"'
run_case props-literal    detect 'RELEASE_STORE_PASSWORD=hunter2hunter2'

echo "must-ignore (this project's references to local.properties):"
run_case read-var         ignore 'storePassword = releaseStorePassword'
run_case read-key-var     ignore 'keyPassword = releaseKeyPassword'
run_case type-annotation  ignore 'val releaseStorePassword: String? = localProps.getProperty("RELEASE_STORE_PASSWORD")'

echo ""
if [ "$fail" -ne 0 ]; then
  echo "gitleaks rule test FAILED — the allowlist has drifted." >&2
  exit 1
fi
echo "gitleaks rule test passed (5 must-detect, 3 must-ignore)"
