#!/usr/bin/env python3
"""One-shot native SageTV Search using debug-direct MiniClient text/command controls."""
from __future__ import annotations

import argparse

from mcp_smoke_test import MCPProcess, compact_tool_result, initialize, tool_call


def main() -> int:
    parser = argparse.ArgumentParser(description="Open SageTV Search, inject text directly through MiniClient, hide IME directly, then send SageTV commands")
    parser.add_argument("--text", required=True, help="Required SageTV Search text to enter")
    parser.add_argument("--next", action="store_true", help="Also press keyboard Next/Enter after typing (not used by default)")
    parser.add_argument(
        "--commands",
        default="down,right,right,right,play_pause,play_pause",
        help="Comma-separated direct SageTV commands sent after IME hide",
    )
    parser.add_argument("--no-commands", action="store_true", help="Do not send post-search SageTV commands")
    parser.add_argument("--keys", default="", help="Legacy comma-separated Android keys for diagnostics only")
    parser.add_argument("--post-submit-settle-ms", type=int, default=1000, help="Delay after typing before remote keys")
    parser.add_argument("--key-delay-ms", type=int, default=350, help="Delay between remote keys")
    args = parser.parse_args()

    client = MCPProcess()
    try:
        negotiated, _ = initialize(client)
        print(f"PASS: MCP initialize handshake ({negotiated})")
        connected = tool_call(client, "adb_connect", timeout=30.0)
        print("PASS: adb_connect")
        print(compact_tool_result(connected))
        result = tool_call(
            client,
            "dev_search_text",
            {
                "text": args.text,
                "submit": args.next,
                "post_commands": "" if args.no_commands else args.commands,
                "post_keys": args.keys,
                "post_submit_settle_ms": args.post_submit_settle_ms,
                "key_delay_ms": args.key_delay_ms,
            },
            timeout=30.0,
        )
        print(f"PASS: SageTV Search typed {args.text!r}")
        if args.next:
            print("PASS: direct SageTV Select sent")
        else:
            print("PASS: Android keyboard dismissed directly (no BACK key)")
        if not args.no_commands and args.commands.strip():
            print(f"PASS: post-search SageTV commands sent directly: {args.commands}")
        if args.keys.strip():
            print(f"PASS: legacy diagnostic Android keys sent: {args.keys}")
        print(compact_tool_result(result))
        return 0
    finally:
        client.close()


if __name__ == "__main__":
    raise SystemExit(main())
