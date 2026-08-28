#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [[ $# -gt 0 ]]; then
  echo "Fire TV target now comes from config/firetv.toml; ignoring positional target." >&2
fi
exec "$ROOT/dev.sh" connect
