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
        # Literal ``\n`` separators survive cmd.exe/PowerShell -> WSL -> Docker
        # argument forwarding, where embedded newline arguments can be split.
        return args.sequence.replace("\\n", "\n")
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
    parser.add_argument(
        "--screenshot",
        metavar="LABEL",
        help="Capture a labeled Fire TV screenshot after the sequence completes",
    )
    parser.add_argument(
        "--state",
        action="store_true",
        help="Print compact player/debug state after the sequence completes",
    )
    parser.add_argument(
        "--server-address",
        help="Launch/connect the Vibe client to this SageTV server before sending the sequence",
    )
    parser.add_argument("--server-port", type=int, default=31099)
    args = parser.parse_args()
    sequence = _read_sequence(args)

    client = MCPProcess()
    try:
        negotiated, _ = initialize(client)
        print(f"PASS: MCP initialize handshake ({negotiated})")
        connected = tool_call(client, "adb_connect", timeout=30.0)
        print("PASS: adb_connect")
        print(compact_tool_result(connected))
        if args.server_address:
            server = tool_call(
                client,
                "dev_connect_server",
                {
                    "address": args.server_address,
                    "port": max(1, min(args.server_port, 65535)),
                    "save": False,
                },
                timeout=30.0,
            )
            print("PASS: dev_connect_server")
            print(compact_tool_result(server))
        result = tool_call(
            client,
            "dev_send_sequence",
            {"sequence": sequence},
            timeout=_timeout_for(sequence),
        )
        print("PASS: dev_send_sequence")
        print(compact_tool_result(result))
        if args.screenshot:
            screenshot = tool_call(
                client,
                "take_screenshot",
                {"label": args.screenshot},
                timeout=30.0,
            )
            print("PASS: take_screenshot")
            print(compact_tool_result(screenshot))
        if args.state:
            state = tool_call(client, "dev_player_state", timeout=30.0)
            print("PASS: dev_player_state")
            print(compact_tool_result(state))
        return 0
    finally:
        client.close()


if __name__ == "__main__":
    raise SystemExit(main())
