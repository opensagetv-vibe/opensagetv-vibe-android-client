#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

run_step() {
    local name="$1"
    shift

    echo
    echo "============================================================"
    echo "STEP: $name"
    echo "============================================================"

    "$@"

    echo
    echo "PASS: $name"
}

run_step "TEST"     ./dev.sh test
run_step "VALIDATE" ./dev.sh validate
run_step "BUILD"    ./dev.sh build
run_step "INSTALL"  ./dev.sh install
run_step "LAUNCH"   ./dev.sh launch

echo
echo "============================================================"
echo "IMPORTANT: FIRST-TIME SETUP BEFORE AUTOMATED TESTING"
echo "============================================================"
echo "If this install cleared app data, complete the MiniClient first-time"
echo "setup and reach the normal SageTV UI before running ANY MCP/player test."
echo "Do not use a test matrix/session/search test as the setup procedure."
echo "Automated test default client ID after setup: 44:45:56:30:30:31 (DEV001)"
echo "Override per test with: --client-id <ID>"

echo
echo "============================================================"
echo "ALL STEPS PASSED"
echo "============================================================"
