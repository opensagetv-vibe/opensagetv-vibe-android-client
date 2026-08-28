#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
echo "Local host builds are disabled for this project. Using the Docker development environment instead." >&2
exec "$ROOT/dev.sh" build
