#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
command -v docker >/dev/null || { echo "docker not found on PATH" >&2; exit 3; }
command -v codex >/dev/null || { echo "codex CLI not found on PATH" >&2; exit 4; }

if codex mcp get sagetv-dev-firetv >/dev/null 2>&1; then
  echo "MCP entry 'sagetv-dev-firetv' already exists. Remove it first with: codex mcp remove sagetv-dev-firetv" >&2
  exit 5
fi

# Codex launches this stdio server on demand. dev.sh then launches the MCP process
# inside the same Docker image that contains ADB, aapt, Android SDK and Python.
codex mcp add sagetv-dev-firetv -- "$ROOT/dev.sh" mcp

echo "Registered SageTV Dev Dockerized MCP. Verify with: codex mcp get sagetv-dev-firetv"
