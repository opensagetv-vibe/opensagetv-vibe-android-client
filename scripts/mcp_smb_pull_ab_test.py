#!/usr/bin/env python3
"""Compare SageTV Pull and SMB Direct seek recovery on one generated fixture."""
from __future__ import annotations

from sagetv_dev_mcp.config import (
    default_fixture,
    default_server_address,
    default_smb_mappings,
    default_smb_value,
    default_server_value,
)

import argparse
import json
import sys

from mcp_seek_suite import MCPProcess, call_dict, initialize


TIMING_FIELDS = (
    "seekRequestMonotonicMs",
    "firstSourceReadAfterRequestMonotonicMs",
    "firstDecoderInputAfterRequestMonotonicMs",
    "firstRenderedFrameAfterRequestMonotonicMs",
    "sourceReadAfterRequestMs",
    "decoderInputAfterRequestMs",
    "renderedFrameAfterRequestMs",
    "recoveryMs",
)


def run_mode(client: MCPProcess, args: argparse.Namespace, mode: str) -> dict:
    clean = call_dict(client, "dev_prepare_clean_start", {"wake": True}, timeout=30.0)
    if not clean.get("readyToLaunch"):
        raise RuntimeError(f"{mode}: Dev app did not reach a clean stopped state")
    call_dict(
        client,
        "dev_set_player_config",
        {
            "player": args.player,
            "streaming": mode,
            "decoding": args.decoding,
            "smb_mappings": args.smb_mappings,
            "smb_username": args.smb_username,
            "smb_password": args.smb_password,
        },
    )
    call_dict(
        client,
        "dev_connect_server",
        {"address": args.server_address, "port": args.server_port, "save": True},
    )
    ready = call_dict(
        client,
        "dev_wait_for_ui",
        {"connected": True, "automation_ready": True, "stable_ms": 1000, "timeout_s": 30.0},
        timeout=40.0,
    )
    if not ready.get("passed"):
        raise RuntimeError(f"{mode}: SageTV UI did not become automation-ready")
    started = call_dict(
        client,
        "dev_play_server_path",
        {"server_path": args.server_path, "timeout_s": 45.0, "verify_ms": 1500},
        timeout=80.0,
    )
    if not started.get("passed"):
        raise RuntimeError(f"{mode}: generated fixture did not start")
    normalized = call_dict(
        client,
        "dev_seek_time",
        {"target_ms": args.start_ms, "tolerance_ms": 5000, "timeout_s": 45.0, "stable_ms": 1200},
        timeout=65.0,
    )
    if not normalized.get("passed"):
        raise RuntimeError(f"{mode}: failed to normalize to {args.start_ms} ms")
    call_dict(client, "dev_clear_player_events")
    measured = call_dict(
        client,
        "dev_run_seek_check",
        {
            "commands": [args.command],
            "expected_net_ms": 0,
            "recovery_timeout_ms": 45000,
            "verify_playback_ms": 1500,
            "health_poll_ms": 100,
        },
        timeout=80.0,
    )
    if not measured.get("passed"):
        raise RuntimeError(f"{mode}: seek recovery failed: {measured.get('healthFailureReason', '')}")
    missing = [field for field in TIMING_FIELDS if int(measured.get(field, -1)) < 0]
    if missing:
        raise RuntimeError(f"{mode}: timing evidence missing: {', '.join(missing)}")

    final_source = str(measured.get("final_playbackSource", ""))
    network_bytes = int(measured.get("final_dataSourceNetworkReadBytes", -1))
    if mode == "pull":
        if final_source != "SAGETV_PULL" or network_bytes <= 0:
            raise RuntimeError(f"pull: expected SageTV MediaServer bytes, got {final_source}/{network_bytes}")
    else:
        if final_source != "SMB_DIRECT" or network_bytes != 0:
            raise RuntimeError(f"smb_direct: expected SMB-only bytes, got {final_source}/{network_bytes}")
        if int(measured.get("final_smbBytesRead", 0)) <= 0:
            raise RuntimeError("smb_direct: no SMB media bytes were recorded")
        if int(measured.get("final_shadowReadBytes", 1024)) >= 1024:
            raise RuntimeError("smb_direct: shadow connection transferred unexpected media volume")

    return {
        "mode": mode,
        "player": args.player,
        "fixture": args.server_path,
        "playbackSource": final_source,
        "mediaServerReadBytes": network_bytes,
        "smbBytesRead": int(measured.get("final_smbBytesRead", -1)),
        "shadowReadBytes": int(measured.get("final_shadowReadBytes", -1)),
        **{field: int(measured.get(field, -1)) for field in TIMING_FIELDS},
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="Physical Pull versus SMB Direct seek A/B")
    parser.add_argument("--server-address", default=default_server_address())
    parser.add_argument("--server-port", type=int, default=int(default_server_value("miniclient_port", 31099)))
    parser.add_argument(
        "--server-path", default=default_fixture("seek_server_path", "/var/media/videos/VibeSeekTest-1080i-MPEG2-AC3-CC.ts")
    )
    parser.add_argument(
        "--smb-mappings", default=default_smb_mappings()
    )
    parser.add_argument("--smb-username", default=default_smb_value("username"))
    parser.add_argument("--smb-password", default=default_smb_value("password"))
    parser.add_argument("--player", choices=("exoplayer", "media3"), default="exoplayer")
    parser.add_argument("--decoding", choices=("hardware", "software", "fallback"), default="hardware")
    parser.add_argument("--start-ms", type=int, default=120000)
    parser.add_argument(
        "--command", choices=("ff", "ff_2"), default="ff_2",
        help="Use the large SageTV jump by default so both modes must perform a physical source read",
    )
    args = parser.parse_args()

    client = MCPProcess()
    try:
        negotiated, _ = initialize(client)
        print(f"PASS: MCP initialize handshake ({negotiated})")
        call_dict(client, "adb_connect", timeout=30.0)
        rows = []
        for mode in ("pull", "smb_direct"):
            row = run_mode(client, args, mode)
            rows.append(row)
            print("PASS: " + json.dumps(row, sort_keys=True))
            call_dict(client, "dev_exit_session", {"stop_app": True}, timeout=30.0)
        report = {"passed": True, "fixture": args.server_path, "rows": rows}
        print("SMB PULL A/B TEST PASSED")
        print(json.dumps(report, indent=2, sort_keys=True))
        return 0
    except Exception as exc:
        print(f"SMB PULL A/B TEST FAILED: {exc}", file=sys.stderr)
        return 1
    finally:
        try:
            call_dict(client, "dev_exit_session", {"stop_app": True}, timeout=30.0)
        except Exception:
            pass
        client.close()


if __name__ == "__main__":
    raise SystemExit(main())
