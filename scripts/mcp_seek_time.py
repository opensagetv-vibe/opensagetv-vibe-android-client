#!/usr/bin/env python3
"""Seek the active MiniClient debug player to an explicit absolute media time through MCP."""
from __future__ import annotations

import argparse
import json
import sys

from mcp_seek_suite import MCPProcess, call_dict, initialize


def main() -> int:
    parser = argparse.ArgumentParser(description="Seek active MiniClient playback to a caller-supplied absolute time")
    parser.add_argument("--target-ms", type=int, required=True, help="Required absolute media time in milliseconds; 0 = beginning")
    parser.add_argument("--tolerance-ms", type=int, default=2000)
    parser.add_argument("--timeout-s", type=float, default=15.0)
    parser.add_argument("--stable-ms", type=int, default=1200, help="Require a stable ready-state landing before PASS")
    args = parser.parse_args()
    if args.target_ms < 0:
        parser.error("--target-ms must be >= 0")

    client = MCPProcess()
    try:
        negotiated, _ = initialize(client)
        print(f"PASS: MCP initialize handshake ({negotiated})")
        call_dict(client, "adb_connect", timeout=30.0)
        print("PASS: adb_connect")
        result = call_dict(
            client,
            "dev_seek_time",
            {
                "target_ms": args.target_ms,
                "tolerance_ms": args.tolerance_ms,
                "timeout_s": args.timeout_s,
                "stable_ms": args.stable_ms,
            },
            timeout=max(30.0, args.timeout_s + 15.0),
        )
        print(json.dumps(result, indent=2, sort_keys=True))
        if result.get("passed"):
            print(f"MCP SEEK TIME: PASS target={args.target_ms} ms landed={result.get('landed_ms')} ms current={result.get('reached_ms')} ms stable={result.get('stable_observed_ms')} ms")
            return 0
        print(f"MCP SEEK TIME: FAIL target={args.target_ms} ms reached={result.get('reached_ms')} ms", file=sys.stderr)
        return 1
    finally:
        client.close()


if __name__ == "__main__":
    raise SystemExit(main())
