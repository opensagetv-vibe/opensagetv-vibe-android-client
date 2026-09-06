#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
python3 -m py_compile scripts/*.py mcp/src/sagetv_dev_mcp/*.py tests/*.py mcp/tests/*.py
python3 scripts/project_manifest.py --check
python3 -m unittest discover -s tests -p 'test_*.py'
PYTHONPATH=mcp/src python3 -m unittest discover -s mcp/tests -p 'test_*.py'
bash -n dev.sh update.sh scripts/*.sh docker/entrypoint.sh
(
  cd "$ROOT/source/dev"
  ./gradlew --no-daemon :core:test
)
echo "PASS: scaffold unit/static tests"
