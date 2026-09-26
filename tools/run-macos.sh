#!/usr/bin/env bash
# Runs Opencraft4 on Apple Silicon using the x64 JDK (Ultralight natives are x86_64-only).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JDK_DIR="$ROOT/.jdk/temurin-21-x64"

if [[ ! -x "$JDK_DIR/bin/java" ]]; then
  echo "Missing x64 JDK at $JDK_DIR" >&2
  echo "Download Temurin 21 (macOS x64) into that folder, or re-run the setup that created .jdk/." >&2
  exit 1
fi

if [[ ! -d "$ROOT/natives/ultralight-legacy/bin" ]]; then
  echo "Ultralight SDK missing. Run: tools/fetch-ultralight-sdk.sh" >&2
  exit 1
fi

export JAVA_HOME="$JDK_DIR"
export PATH="$JAVA_HOME/bin:/opt/homebrew/bin:/usr/local/bin:$PATH"
export DYLD_LIBRARY_PATH="$ROOT/run/natives:$ROOT/natives/ultralight-legacy/bin${DYLD_LIBRARY_PATH:+:$DYLD_LIBRARY_PATH}"

cd "$ROOT"
exec arch -x86_64 env JAVA_HOME="$JAVA_HOME" PATH="$PATH" DYLD_LIBRARY_PATH="$DYLD_LIBRARY_PATH" mvn -q compile exec:exec "$@"
