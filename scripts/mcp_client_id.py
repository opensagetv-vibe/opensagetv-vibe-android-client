#!/usr/bin/env python3
"""Show or change the Dev MiniClient's persisted SageTV client ID through MCP/ADB."""
from __future__ import annotations

import argparse
import json
import re
import sys
import time
from typing import Any

from mcp_seek_suite import MCPProcess, call_dict, initialize
from mcp_smoke_test import tool_call

EXAMPLE_TEXT_ID = "DEV001"
EXAMPLE_CLIENT_ID = "44:45:56:30:30:31"


def normalize_requested_id(value: str) -> str:
    value = str(value or "").strip()
    if re.fullmatch(r"(?i)[0-9a-f]{2}(?::[0-9a-f]{2}){5}", value):
        return value.lower()
    if not value or len(value) > 6:
        raise ValueError("text client ID must be 1 to 6 characters or six colon-separated hex bytes")
    chars = [ord(ch) for ch in value]
    if any(ch > 0xFF for ch in chars):
        raise ValueError("text client ID must use single-byte characters")
    chars += [0] * (6 - len(chars))
    return ":".join(f"{ch:02x}" for ch in chars)


def _tool(client: MCPProcess, name: str, arguments: dict[str, Any] | None = None, timeout: float = 30.0) -> dict[str, Any]:
    result = tool_call(client, name, arguments or {}, timeout=timeout)
    if bool(result.get("isError")):
        raise RuntimeError(f"tool {name} failed: {json.dumps(result, sort_keys=True)}")
    return result


def _ensure_app_running(client: MCPProcess) -> None:
    status = call_dict(client, "dev_app_status")
    if status.get("running"):
        return
    _tool(client, "launch_dev_app")
    time.sleep(1.5)


def _restart_app(client: MCPProcess) -> None:
    _tool(client, "stop_dev_app")
    time.sleep(0.5)
    _tool(client, "launch_dev_app")
    time.sleep(1.5)


def main() -> int:
    p = argparse.ArgumentParser(
        description="Show/set the persisted SageTV MiniClient client ID. Normal app behavior generates and persists an ID on first setup."
    )
    group = p.add_mutually_exclusive_group()
    group.add_argument("--set", dest="set_value", metavar="ID", help=f"set text (for example {EXAMPLE_TEXT_ID}) or six-byte ID")
    group.add_argument("--ensure", dest="ensure_value", metavar="ID", help="set/restart only when the requested ID is not already configured/active")
    group.add_argument("--generate", action="store_true", help="generate and persist a fresh original-style random client ID")
    group.add_argument("--show", action="store_true", help="show configured and active client IDs (default)")
    p.add_argument("--quiet", action="store_true", help="suppress normal output; intended for automated test wrappers")
    args = p.parse_args()

    client = MCPProcess()
    try:
        initialize(client)
        call_dict(client, "adb_connect", timeout=30.0)
        _ensure_app_running(client)

        state = call_dict(client, "dev_player_state")
        version = int(state.get("debugStatusVersion", 0) or 0)
        if version < 14:
            print(f"ERROR: installed debug APK has debugStatusVersion={version}; client-id CLI requires >=14 / v0.5.75", file=sys.stderr)
            return 2

        current = call_dict(client, "dev_client_id")
        restarted = False
        changed = False

        if args.ensure_value is not None:
            desired = normalize_requested_id(args.ensure_value)
            configured = str(current.get("configuredClientId", "") or "").lower()
            active = str(current.get("activeClientId", "") or "").lower()
            changed = configured != desired
            restart_needed = bool(active) and active != desired
            if changed:
                result = call_dict(client, "dev_set_client_id", {"client_id": args.ensure_value, "generate": False})
                if not args.quiet:
                    print(json.dumps(result, indent=2, sort_keys=True))
                time.sleep(0.75)
                restart_needed = True
            if restart_needed:
                _restart_app(client)
                restarted = True
            current = call_dict(client, "dev_client_id")
        elif args.set_value is not None or args.generate:
            requested = {"client_id": args.set_value or "", "generate": bool(args.generate)}
            result = call_dict(client, "dev_set_client_id", requested)
            if not args.quiet:
                print(json.dumps(result, indent=2, sort_keys=True))
            # SharedPreferences apply() is asynchronous; allow it to flush before force-stop.
            time.sleep(0.75)
            _restart_app(client)
            restarted = True
            changed = True
            current = call_dict(client, "dev_client_id")

        if not args.quiet:
            print(json.dumps(current, indent=2, sort_keys=True))
            print(f"Example test ID: {EXAMPLE_TEXT_ID} -> {EXAMPLE_CLIENT_ID}")
            if restarted:
                print("The Dev app was restarted so the configured client ID is active on the next SageTV connection.")
            elif args.ensure_value is not None:
                print("Requested client ID is already configured/active; no app restart was needed.")
        return 0
    finally:
        client.close()


if __name__ == "__main__":
    raise SystemExit(main())
