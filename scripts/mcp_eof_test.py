#!/usr/bin/env python3
"""Verify that an exact completed-file EOF is handled without a client crash."""
from __future__ import annotations

from sagetv_dev_mcp.config import default_server_address, default_server_value

import argparse
import json
import sys
import time

from mcp_lifecycle_test import MCPProcess, call_dict, initialize, require, wait_automation_ready


def main() -> int:
    parser = argparse.ArgumentParser(description="Run the Android exact-EOF physical gate")
    parser.add_argument("--server-address", default=default_server_address())
    parser.add_argument("--server-port", type=int, default=int(default_server_value("miniclient_port", 31099)))
    parser.add_argument("--server-path", required=True)
    parser.add_argument("--player", choices=("exoplayer", "media3"), default="media3")
    parser.add_argument("--decoding", choices=("hardware", "software", "hardware_preferred"), default="hardware")
    parser.add_argument("--duration-ms", type=int, default=0, help="Override only when the player cannot report duration")
    parser.add_argument("--timeout-s", type=float, default=30.0)
    parser.add_argument(
        "--seek-policy",
        choices=("closest", "next", "previous", "directional"),
        default="",
        help="Optional Dev-only backend seek-policy override for diagnosis",
    )
    parser.add_argument("--pull-read-kb", type=int, default=0, help="Optional Dev-only Pull read size")
    args = parser.parse_args()

    client = MCPProcess()
    try:
        negotiated, _ = initialize(client)
        print(f"PASS: MCP initialize handshake ({negotiated})")
        call_dict(client, "adb_connect", timeout=30.0)
        clean = call_dict(client, "dev_prepare_clean_start", {"wake": True, "graceful_timeout_s": 2.0}, timeout=30.0)
        require(bool(clean.get("readyToLaunch")), f"Dev app clean-start preparation failed: {clean}")
        call_dict(client, "dev_set_player_config", {
            "player": args.player,
            "streaming": "pull",
            "decoding": args.decoding,
        })
        tuning = {}
        if args.seek_policy:
            tuning["media3_seek_policy" if args.player == "media3" else "exo2_seek_policy"] = args.seek_policy
        if args.pull_read_kb:
            tuning["media3_pull_read_kb" if args.player == "media3" else "exo2_pull_read_kb"] = args.pull_read_kb
        if tuning:
            call_dict(client, "dev_set_player_tuning", {"reset": True, **tuning})
        call_dict(client, "dev_connect_server", {
            "address": args.server_address,
            "port": args.server_port,
            "save": False,
        })
        wait_automation_ready(client)
        started = call_dict(client, "dev_play_server_path", {
            "server_path": args.server_path,
            "timeout_s": 45.0,
            "verify_ms": 1500,
        }, timeout=80.0)
        require(bool(started.get("passed")), f"Completed-file playback failed: {started}")

        state = call_dict(client, "dev_player_state", timeout=30.0)
        duration_ms = int(args.duration_ms or state.get("health_durationMs", 0))
        require(duration_ms > 0, f"Player did not report a usable completed-file duration: {state}")
        baseline_crash = call_dict(client, "dev_crash_probe", timeout=30.0)
        seek = call_dict(client, "dev_local_seek_absolute", {"target_ms": duration_ms}, timeout=30.0)
        require(bool(seek.get("ok", True)), f"Exact-EOF seek was rejected: {seek}")

        deadline = time.monotonic() + max(5.0, min(args.timeout_s, 90.0))
        last = state
        ended_state_seen = False
        eof_ui_seen = False
        while time.monotonic() < deadline:
            last = call_dict(client, "dev_player_state", timeout=30.0)
            playback_state = int(last.get("health_playbackState", last.get("state", -1)))
            mini_state = int(last.get("state", -1))
            menu_name = str(last.get("menuName", ""))
            ended_state_seen = ended_state_seen or playback_state == 4 or mini_state == 5
            eof_ui_seen = eof_ui_seen or "AskToDeleteRecording" in menu_name
            if ended_state_seen or eof_ui_seen:
                break
            time.sleep(0.25)

        status = call_dict(client, "dev_app_status", timeout=30.0)
        final_crash = call_dict(client, "dev_crash_probe", timeout=30.0)
        require(bool(status.get("running")), f"Dev process died at EOF: {status}")
        require(not bool(final_crash.get("signatureDetected")), f"Crash signature detected at EOF: {final_crash}")
        require(
            baseline_crash.get("signatureFingerprint", "") == final_crash.get("signatureFingerprint", ""),
            f"Crash signature changed at EOF: {final_crash}",
        )
        require(ended_state_seen or eof_ui_seen, f"No clean EOF state was observed: {last}")
        print(
            "PASS: exact EOF handled without process death "
            f"durationMs={duration_ms} endedState={ended_state_seen} eofUi={eof_ui_seen}"
        )
        print("ANDROID EXACT EOF: PASS")
        return 0
    except Exception as exc:
        print(f"ANDROID EXACT EOF: FAIL\n{exc}", file=sys.stderr)
        try:
            diagnostics = call_dict(client, "collect_playback_diagnostics", {"label": "exact-eof-failure"}, timeout=90.0)
            print(json.dumps(diagnostics, indent=2, sort_keys=True), file=sys.stderr)
        except Exception as diagnostic_exc:
            print(f"WARN: EOF diagnostics failed: {diagnostic_exc}", file=sys.stderr)
        return 1
    finally:
        try:
            call_dict(client, "dev_exit_session", {"stop_app": True}, timeout=30.0)
        except Exception:
            pass
        client.close()


if __name__ == "__main__":
    raise SystemExit(main())
