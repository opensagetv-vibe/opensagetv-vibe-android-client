#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
python3 -m py_compile scripts/*.py mcp/src/sagetv_dev_mcp/*.py tests/*.py mcp/tests/*.py
python3 -m unittest discover -s tests -p 'test_*.py'
PYTHONPATH=mcp/src python3 -m unittest discover -s mcp/tests -p 'test_*.py'
bash -n dev.sh build_existing_app.sh compile_existing_app.sh scripts/*.sh docker/entrypoint.sh
echo "PASS: scaffold unit/static tests"
