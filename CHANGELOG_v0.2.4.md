## v0.2.4 - MCP test runner fix

- Fixed `scripts/run_unit_tests.sh` MCP test invocation.
- Replaced path-style `python3 -m unittest mcp/tests/test_adb.py` with unittest discovery under `mcp/tests`.
- Prevents `ModuleNotFoundError: No module named 'mcp.tests'` inside the Docker development environment.
- No Docker image rebuild is required.
