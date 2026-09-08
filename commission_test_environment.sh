#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONFIG="$ROOT/config/firetv.toml"
EXAMPLE="$ROOT/config/firetv.example.toml"
REGENERATE=0
SKIP_FIXTURES=0
SKIP_BUILD=0
INSTALL=0

while (($#)); do
  case "$1" in
    --regenerate) REGENERATE=1 ;;
    --skip-fixtures) SKIP_FIXTURES=1 ;;
    --skip-build) SKIP_BUILD=1 ;;
    --install) INSTALL=1 ;;
    *) echo "Unknown option: $1" >&2; exit 2 ;;
  esac
  shift
done

if [[ ! -f "$CONFIG" ]]; then
  cp "$EXAMPLE" "$CONFIG"
  echo "Created ignored local configuration: $CONFIG"
  echo "Edit its documentation addresses, aliases, paths, and blank credentials, then run this command again."
  exit 2
fi

cd "$ROOT"
./dev.sh config-check
./dev.sh preflight

if (( ! SKIP_FIXTURES )); then
  seek=artifacts/test-media/VibeSeekTest-1080i-MPEG2-AC3-CC.ts
  codec=artifacts/test-media/kodi-codec/fixture-manifest.json
  dvd=artifacts/test-media/VIBE_AUTHORED_DVD/VIBE_DVD_TEST_MANIFEST.json
  if (( REGENERATE )) || [[ ! -f "$seek" ]]; then
    ./dev.sh seek-fixture "$seek" 900
  else
    echo "REUSE: $seek"
  fi
  if (( REGENERATE )) || [[ ! -f "$codec" ]]; then
    ./dev.sh codec-fixtures --duration 6 --output-dir artifacts/test-media/kodi-codec
  else
    echo "REUSE: $codec"
  fi
  if (( REGENERATE )) || [[ ! -f "$dvd" ]]; then
    dvd_args=(dvd-fixture)
    (( REGENERATE )) && dvd_args+=(--overwrite)
    ./dev.sh "${dvd_args[@]}"
  else
    echo "REUSE: $dvd"
  fi
fi

if (( ! SKIP_BUILD )); then
  ./dev.sh test
  ./dev.sh validate
  ./dev.sh build
fi
if (( INSTALL )); then
  ./dev.sh install
  ./dev.sh launch
fi

echo "PASS: OpenSageTV Vibe Android test environment commissioned."
(( INSTALL )) || echo "APK installation was not requested. Re-run with --install after first-time device setup is understood."
