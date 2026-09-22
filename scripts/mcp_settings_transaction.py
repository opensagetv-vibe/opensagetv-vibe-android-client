#!/usr/bin/env python3
"""Checkpoint or restore device settings through the real MCP debug surface."""
from __future__ import annotations

import argparse
import json

from mcp_seek_suite import MCPProcess, call_dict, initialize


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("action", choices=("checkpoint", "restore"))
    args = parser.parse_args()

    client = MCPProcess()
    try:
        initialize(client)
        call_dict(client, "adb_connect", timeout=30.0)
        tool = "dev_checkpoint_settings" if args.action == "checkpoint" else "dev_restore_settings"
        result = call_dict(client, tool, timeout=30.0)
        print(json.dumps(result, sort_keys=True))
        if args.action == "checkpoint" and not bool(result.get("captured")):
            raise RuntimeError(f"settings checkpoint was not captured: {result}")
        return 0
    finally:
        client.close()


if __name__ == "__main__":
    raise SystemExit(main())
