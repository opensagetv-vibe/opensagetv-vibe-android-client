#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
# Numerical analysis tests are host tooling, not APK/MCP runtime dependencies.
# Use a disposable environment when the SDK Python lacks the pinned test-only
# packages; preserve the reusable SDK's locked runtime and authorization state.
unit_test_environment=""
cleanup_unit_test_environment() {
  if [[ -n "$unit_test_environment" ]]; then
    local resolved_environment
    resolved_environment="$(realpath "$unit_test_environment")"
    [[ "$resolved_environment" == /tmp/vibe-unit-tests.* && ! -L "$unit_test_environment" ]] || return 1
    rm -rf -- "$resolved_environment"
  fi
}
trap cleanup_unit_test_environment EXIT
if ! python3 -c 'import numpy, scipy; assert numpy.__version__ == "2.5.1" and scipy.__version__ == "1.18.0"' >/dev/null 2>&1; then
  unit_test_environment="$(mktemp -d /tmp/vibe-unit-tests.XXXXXX)"
  python3 -m venv --system-site-packages "$unit_test_environment"
  "$unit_test_environment/bin/python3" -m pip install -r tests/requirements.txt
  export PATH="$unit_test_environment/bin:$PATH"
fi
python3 -m py_compile scripts/*.py mcp/src/sagetv_dev_mcp/*.py tests/*.py mcp/tests/*.py
python3 scripts/project_manifest.py --check
python3 -m unittest discover -s tests -p 'test_*.py'
PYTHONPATH=mcp/src python3 -m unittest discover -s mcp/tests -p 'test_*.py'
bash -n dev.sh update.sh commission_test_environment.sh scripts/*.sh docker/entrypoint.sh
(
  cd "$ROOT/source/dev"
  ./gradlew --no-daemon :core:test
)
echo "PASS: scaffold unit/static tests"
