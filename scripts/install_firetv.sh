#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# Safe installer: application ID is verified inside Docker before adb install.
if [[ $# -gt 0 ]]; then
  exec "$ROOT/dev.sh" install "$1"
else
  exec "$ROOT/dev.sh" install
fi
