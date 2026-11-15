#!/usr/bin/env bash
# GBL-013 US-5 (wave 2) — CI contract for api-key-pool-gradle.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT/api-key-pool-plugin"

job="${1:-}"
# GBL-014 — publication channel (jitpack default until the Sonatype reset).
channel="${CHANNEL:-jitpack}"
case "$job" in
  fast|test)
    ./gradlew ktlintCheck verifyChannel -Pchannel="$channel" --no-daemon --console=plain
    ;;
  *)
    printf 'unsupported job for api-key-pool-gradle: %s\n' "${job:-<none>}" >&2
    exit 2
    ;;
esac
