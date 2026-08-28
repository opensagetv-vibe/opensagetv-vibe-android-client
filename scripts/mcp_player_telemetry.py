#!/usr/bin/env python3
"""Read live SageTV Dev player telemetry through the actual MCP stdio server."""
from __future__ import annotations

import json
import sys

from mcp_smoke_test import MCPProcess, compact_tool_result, initialize, tool_call


def main() -> int:
    max_events = 80
    if len(sys.argv) > 1:
        max_events = max(1, min(int(sys.argv[1]), 500))

    client = MCPProcess()
    try:
        negotiated, _ = initialize(client)
        print(f"PASS: MCP initialize handshake ({negotiated})")
        tool_call(client, "adb_connect", timeout=30.0)
        print("PASS: adb_connect")

        result = tool_call(client, "get_player_telemetry", {"max_events": max_events}, timeout=30.0)
        structured = result.get("structuredContent")
        if not isinstance(structured, dict):
            print(compact_tool_result(result))
            return 0

        print("Telemetry transport:", structured.get("transport"))
        print("Telemetry file:", structured.get("file"))
        count = int(structured.get("event_count", 0))
        if count <= 0:
            print("NO PLAYER TELEMETRY EVENTS FOUND")
            print("Build/install the telemetry-enabled Dev APK, start video playback, then run this command again.")
            return 2

        print(f"PASS: player telemetry ({count} recent events)")
        print("Active player:", structured.get("active_player"))
        print("Active session:", structured.get("active_session"))
        print("Current state:")
        print(json.dumps(structured.get("state", {}), indent=2))
        print("Recent events:")
        for event in structured.get("events", [])[-10:]:
            event = dict(event)
            event.pop("raw", None)
            print(json.dumps(event, sort_keys=True))
        print("MCP PLAYER TELEMETRY TEST: PASS")
        return 0
    except Exception as exc:
        print(f"MCP PLAYER TELEMETRY TEST: FAIL\n{exc}", file=sys.stderr)
        return 1
    finally:
        client.close()


if __name__ == "__main__":
    raise SystemExit(main())
