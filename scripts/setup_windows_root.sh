#!/usr/bin/env bash
set -euo pipefail
ROOT="${1:-/mnt/c/source/opensagetv-vibe-android-client}"
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
printf 'Windows-backed OpenSageTV Vibe Android workspace ready: %s\n' "$ROOT"
printf 'Bundled source should already exist under: %s/source\n' "$ROOT"
printf 'Use %s/incoming only for a future replacement source ZIP.\n' "$ROOT"
printf 'No .env is required when running from this repository; OPENSAGETV_VIBE_ANDROID_ROOT is an optional override.\n'
