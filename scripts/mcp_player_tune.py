#!/usr/bin/env python3
"""Read/set Dev-only runtime player tuning through the real MCP server."""
from __future__ import annotations

import argparse
import json
import sys
from typing import Any

from mcp_media3_matrix import MCPProcess, call_dict, initialize


def _bool(value: str) -> bool:
    v = str(value).strip().lower()
    if v in ("1", "true", "yes", "on", "enabled"): return True
    if v in ("0", "false", "no", "off", "disabled"): return False
    raise argparse.ArgumentTypeError("use on/off, true/false, yes/no, or 1/0")


def build_tuning(args: argparse.Namespace) -> dict[str, Any]:
    backend = args.backend
    out: dict[str, Any] = {}
    def put(suffix: str, value: Any) -> None:
        if value is None: return
        if backend in ("media3", "both"):
            out[f"media3_{suffix}"] = value
        if backend in ("exo2", "both"):
            out[f"exo2_{suffix}"] = value
    put("ts_search_multiplier", args.ts_search_multiplier)
    put("pull_read_kb", args.pull_read_kb)
    put("min_buffer_ms", args.min_buffer_ms)
    put("max_buffer_ms", args.max_buffer_ms)
    put("playback_buffer_ms", args.playback_buffer_ms)
    put("rebuffer_ms", args.rebuffer_ms)
    put("seek_recovery_enabled", args.seek_recovery)
    put("seek_recovery_delay_ms", args.seek_recovery_ms)
    put("seek_policy", args.seek_policy)
    put("codec_mode", args.codec_mode)
    if args.directional_sync_min_delta_ms is not None:
        out["directional_sync_min_delta_ms"] = args.directional_sync_min_delta_ms
    return out


def main() -> int:
    p = argparse.ArgumentParser(description="Set/show Dev runtime player tuning without rebuilding the APK")
    p.add_argument("--backend", choices=("media3", "exo2", "both"), default="media3")
    p.add_argument("--show", action="store_true")
    p.add_argument("--reset", action="store_true")
    p.add_argument("--ts-search-multiplier", type=int)
    p.add_argument("--seek-policy", choices=("closest", "next", "previous", "directional"))
    p.add_argument("--pull-read-kb", type=int)
    p.add_argument("--min-buffer-ms", type=int)
    p.add_argument("--max-buffer-ms", type=int)
    p.add_argument("--playback-buffer-ms", type=int)
    p.add_argument("--rebuffer-ms", type=int)
    p.add_argument("--directional-sync-min-delta-ms", type=int)
    p.add_argument("--seek-recovery", type=_bool)
    p.add_argument("--seek-recovery-ms", type=int)
    p.add_argument("--codec-mode", choices=("auto", "async", "sync"))
    args = p.parse_args()

    client = MCPProcess()
    try:
        initialize(client)
        tuning = build_tuning(args)
        if args.reset or tuning:
            result = call_dict(client, "dev_set_player_tuning", {"reset": args.reset, **tuning})
        else:
            result = call_dict(client, "dev_player_tuning")
        print(json.dumps(result, indent=2, sort_keys=True))
        state = call_dict(client, "dev_player_state")
        version = int(state.get("debugStatusVersion", 0) or 0)
        if version < 13:
            print(f"ERROR: installed debug APK has debugStatusVersion={version}; v0.5.70 requires >=13", file=sys.stderr)
            return 2
        if tuning:
            print("Applied runtime tuning. Player/datasource creation after this call uses the new values.")
        return 0
    finally:
        client.close()


if __name__ == "__main__":
    raise SystemExit(main())
