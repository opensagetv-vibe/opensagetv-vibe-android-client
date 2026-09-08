#!/usr/bin/env python3
"""Verify retained Media3 completed-file replacement on a physical Android device."""
from __future__ import annotations

from sagetv_dev_mcp.config import default_server_address, default_smb_mappings, default_smb_value, default_server_value

import argparse
import json
import sys
import time

from mcp_lifecycle_test import MCPProcess, call_dict, initialize, require, wait_automation_ready


def wait_for_success(client: MCPProcess, before_attempts: int, timeout_s: float) -> dict:
    deadline = time.monotonic() + timeout_s
    last: dict = {}
    while time.monotonic() < deadline:
        last = call_dict(client, "dev_player_state", timeout=30.0)
        attempts = int(last.get("fastSwitchAttemptCount", 0) or 0)
        successes = int(last.get("fastSwitchSuccessCount", 0) or 0)
        if (attempts > before_attempts and successes > 0
                and bool(last.get("health_isPlaying"))):
            return last
        time.sleep(0.25)
    raise RuntimeError(f"fast switch did not render and resume: {last}")


def main() -> int:
    parser = argparse.ArgumentParser(description="Run the Media3 retained-source physical gate")
    parser.add_argument("--server-address", default=default_server_address())
    parser.add_argument("--server-port", type=int, default=int(default_server_value("miniclient_port", 31099)))
    parser.add_argument("--initial-path", required=True)
    parser.add_argument("--switch-path", required=True)
    parser.add_argument("--streaming", choices=("pull", "smb_direct"), default="pull")
    parser.add_argument("--smb-mappings", default=default_smb_mappings())
    parser.add_argument("--smb-username", default=default_smb_value("username"))
    parser.add_argument("--smb-password", default=default_smb_value("password"))
    parser.add_argument("--playback-timeout-s", type=float, default=60.0)
    parser.add_argument("--switch-timeout-s", type=float, default=30.0)
    args = parser.parse_args()

    client = MCPProcess()
    try:
        negotiated, _ = initialize(client)
        print(f"PASS: MCP initialize handshake ({negotiated})")
        call_dict(client, "adb_connect", timeout=30.0)
        clean = call_dict(client, "dev_prepare_clean_start", {
            "wake": True,
            "graceful_timeout_s": 2.0,
        }, timeout=30.0)
        require(bool(clean.get("readyToLaunch")), f"clean start failed: {clean}")

        config = {
            "player": "media3",
            "streaming": args.streaming,
            "decoding": "hardware",
        }
        if args.streaming == "smb_direct":
            config.update({
                "smb_mappings": args.smb_mappings,
                "smb_username": args.smb_username,
                "smb_password": args.smb_password,
            })
        call_dict(client, "dev_set_player_config", config, timeout=30.0)
        call_dict(client, "dev_connect_server", {
            "address": args.server_address,
            "port": args.server_port,
            "save": False,
        }, timeout=30.0)
        wait_automation_ready(client)
        started = call_dict(client, "dev_play_server_path", {
            "server_path": args.initial_path,
            "timeout_s": args.playback_timeout_s,
            "verify_ms": 2500,
        }, timeout=args.playback_timeout_s + 35.0)
        require(bool(started.get("passed")), f"initial playback failed: {started}")

        before = call_dict(client, "dev_player_state", timeout=30.0)
        require(before.get("health_videoDecoderKind") == "hardware",
                f"hardware decoder required: {before.get('health_videoDecoderKind')}")
        before_attempts = int(before.get("fastSwitchAttemptCount", 0) or 0)
        call_dict(client, "dev_media3_fast_switch_file", {
            "server_path": args.switch_path,
        }, timeout=30.0)
        after = wait_for_success(client, before_attempts, args.switch_timeout_s)

        require(int(after.get("fastSwitchSuccessCount", 0) or 0) > 0,
                f"success counter missing: {after}")
        require(int(after.get("fastSwitchFallbackCount", 0) or 0) == 0,
                f"unexpected fallback: {after}")
        require(after.get("fastSwitchLastReason") == "first_frame_rendered",
                f"unexpected outcome: {after.get('fastSwitchLastReason')}")
        require(str(after.get("fastSwitchTargetUrl", "")).endswith(args.switch_path),
                f"target path mismatch: {after.get('fastSwitchTargetUrl')}")
        require(after.get("health_videoDecoderKind") == "hardware",
                f"replacement lost hardware decoder: {after.get('health_videoDecoderKind')}")
        if args.streaming == "smb_direct":
            require(after.get("playbackSource") == "SMB_DIRECT",
                    f"SMB bytes were not selected: {after.get('playbackSource')}")
            require(int(after.get("smbBytesRead", 0) or 0) > 0,
                    f"no SMB bytes read after replacement: {after}")

        crash = call_dict(client, "dev_crash_probe", timeout=30.0)
        require(not bool(crash.get("signatureDetected")), f"crash signature detected: {crash}")
        print("ANDROID MEDIA3 FAST SWITCH: PASS")
        print(json.dumps({
            "streaming": args.streaming,
            "target": after.get("fastSwitchTargetUrl"),
            "attempts": after.get("fastSwitchAttemptCount"),
            "successes": after.get("fastSwitchSuccessCount"),
            "fallbacks": after.get("fastSwitchFallbackCount"),
            "decoder": after.get("health_videoDecoder"),
            "playbackSource": after.get("playbackSource"),
        }, indent=2, sort_keys=True))
        return 0
    except Exception as exc:
        print(f"ANDROID MEDIA3 FAST SWITCH: FAIL\n{exc}", file=sys.stderr)
        try:
            diagnostics = call_dict(client, "collect_playback_diagnostics", {
                "label": "media3-fast-switch-failure",
            }, timeout=90.0)
            print(json.dumps(diagnostics, indent=2, sort_keys=True), file=sys.stderr)
        except Exception as diagnostic_exc:
            print(f"WARN: diagnostics failed: {diagnostic_exc}", file=sys.stderr)
        return 1
    finally:
        try:
            call_dict(client, "dev_exit_session", {"stop_app": True}, timeout=30.0)
        except Exception:
            pass
        client.close()


if __name__ == "__main__":
    raise SystemExit(main())
