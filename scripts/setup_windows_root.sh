#!/usr/bin/env bash
set -euo pipefail
ROOT="${1:-/mnt/c/SageTV-MiniClient-Dev}"
mkdir -p \
  "$ROOT/incoming" \
  "$ROOT/source/dev" \
  "$ROOT/source/existing" \
  "$ROOT/artifacts/firetv" \
  "$ROOT/artifacts/existing" \
  "$ROOT/adb" \
  "$ROOT/logs" \
  "$ROOT/screenshots" \
  "$ROOT/recordings" \
  "$ROOT/config"
printf 'Windows-backed SageTV workspace ready: %s\n' "$ROOT"
printf 'Bundled source should already exist under: %s/source\n' "$ROOT"
printf 'Use %s/incoming only for a future replacement source ZIP.\n' "$ROOT"
printf 'No .env is required when running from this project directory; SAGETV_WINDOWS_ROOT is an optional override.\n'
