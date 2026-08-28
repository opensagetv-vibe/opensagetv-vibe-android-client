#!/usr/bin/env python3
"""Execute one explicit multiline Android/SageTV input sequence through one MCP tool call."""
from __future__ import annotations

import argparse
from pathlib import Path
import re
import sys

from mcp_smoke_test import MCPProcess, compact_tool_result, initialize, tool_call


def _read_sequence(args: argparse.Namespace) -> str:
    if args.sequence is not None:
        return args.sequence
    if args.file is not None:
        return Path(args.file).read_text(encoding="utf-8")
    data = sys.stdin.read()
    if not data.strip():
        raise SystemExit("No sequence supplied. Pipe a multiline sequence, use --file, or use --sequence.")
    return data


def _timeout_for(sequence: str) -> float:
    total_delay_ms = 0
    for line in sequence.splitlines():
        match = re.match(r"^\s*delay\s+(\d+)\s*$", line, re.IGNORECASE)
        if match:
            total_delay_ms += int(match.group(1))
    return max(30.0, min(180.0, 30.0 + total_delay_ms / 1000.0))


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Send one explicit multiline sequence through a single dev_send_sequence MCP call"
    )
    src = parser.add_mutually_exclusive_group()
    src.add_argument("--sequence", help="Inline multiline sequence")
    src.add_argument("--file", help="Read sequence from a text file")
    args = parser.parse_args()
    sequence = _read_sequence(args)

    client = MCPProcess()
    try:
        negotiated, _ = initialize(client)
        print(f"PASS: MCP initialize handshake ({negotiated})")
        connected = tool_call(client, "adb_connect", timeout=30.0)
        print("PASS: adb_connect")
        print(compact_tool_result(connected))
        result = tool_call(
            client,
            "dev_send_sequence",
            {"sequence": sequence},
            timeout=_timeout_for(sequence),
        )
        print("PASS: dev_send_sequence")
        print(compact_tool_result(result))
        return 0
    finally:
        client.close()


if __name__ == "__main__":
    raise SystemExit(main())
