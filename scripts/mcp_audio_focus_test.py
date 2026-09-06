#!/usr/bin/env python3
"""Exercise lifecycle-owned audio focus against real playback on the Dev APK."""
from __future__ import annotations

import argparse
import json
import sys
import time

from mcp_lifecycle_test import MCPProcess, call_dict, initialize, require, wait_automation_ready


PLAY_STATE = 2
PAUSE_STATE = 3


def wait_state(client: MCPProcess, expected: int, timeout_s: float = 8.0) -> dict:
    deadline = time.monotonic() + timeout_s
    last: dict = {}
    while time.monotonic() < deadline:
        last = call_dict(client, "dev_player_state", timeout=30.0)
        if int(last.get("state", -1)) == expected:
            return last
        time.sleep(0.2)
    raise RuntimeError(f"player did not reach state {expected}: {last}")


def require_advancing_av(client: MCPProcess, timeout_s: float, verify_ms: int) -> dict:
    result = call_dict(client, "dev_wait_for_playback_started", {
        "timeout_s": timeout_s,
        "verify_ms": verify_ms,
        "expect_video": True,
        "expect_audio": True,
    }, timeout=timeout_s + 20.0)
    require(bool(result.get("passed")), f"playback did not resume advancing A/V: {result}")
    return result


def request_and_restore(client: MCPProcess, mode: str, timeout_s: float, verify_ms: int) -> None:
    requested = call_dict(client, "dev_request_competing_audio_focus", {"mode": mode}, timeout=30.0)
    require(bool(requested.get("granted")), f"competing {mode} focus was not granted: {requested}")
    wait_state(client, PAUSE_STATE)
    released = call_dict(client, "dev_abandon_competing_audio_focus", timeout=30.0)
    require(bool(released.get("abandoned")), f"competing {mode} focus was not abandoned: {released}")
    require_advancing_av(client, timeout_s, verify_ms)
    print(f"PASS: {mode} focus loss paused playback and gain resumed advancing A/V")


def main() -> int:
    parser = argparse.ArgumentParser(description="Run the Android audio-focus physical gate")
    parser.add_argument("--server-address", default="192.168.10.232")
    parser.add_argument("--server-port", type=int, default=31099)
    parser.add_argument("--server-path", required=True)
    parser.add_argument("--player", choices=("exoplayer", "media3"), default="media3")
    parser.add_argument("--streaming", choices=("dynamic", "pull", "fixed"), default="pull")
    parser.add_argument("--decoding", choices=("hardware", "software", "hardware_preferred"), default="hardware")
    parser.add_argument("--playback-timeout-s", type=float, default=60.0)
    parser.add_argument("--verify-ms", type=int, default=3000)
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
        require(bool(clean.get("readyToLaunch")), f"Dev app clean-start preparation failed: {clean}")
        call_dict(client, "dev_set_player_config", {
            "player": args.player,
            "streaming": args.streaming,
            "decoding": args.decoding,
        })
        call_dict(client, "dev_connect_server", {
            "address": args.server_address,
            "port": args.server_port,
            "save": False,
        })
        wait_automation_ready(client)
        started = call_dict(client, "dev_play_server_path", {
            "server_path": args.server_path,
            "timeout_s": args.playback_timeout_s,
            "verify_ms": args.verify_ms,
        }, timeout=args.playback_timeout_s + 35.0)
        require(bool(started.get("passed")), f"Initial playback failed: {started}")
        wait_state(client, PLAY_STATE)

        request_and_restore(client, "transient", args.playback_timeout_s, args.verify_ms)
        request_and_restore(client, "duck", args.playback_timeout_s, args.verify_ms)

        call_dict(client, "dev_player_control", {"action": "pause"}, timeout=30.0)
        wait_state(client, PAUSE_STATE)
        requested = call_dict(client, "dev_request_competing_audio_focus", {"mode": "transient"}, timeout=30.0)
        require(bool(requested.get("granted")), f"user-pause focus request failed: {requested}")
        call_dict(client, "dev_abandon_competing_audio_focus", timeout=30.0)
        time.sleep(2.0)
        wait_state(client, PAUSE_STATE, timeout_s=1.0)
        print("PASS: focus gain did not override an existing user pause")

        call_dict(client, "dev_player_control", {"action": "play"}, timeout=30.0)
        require_advancing_av(client, args.playback_timeout_s, args.verify_ms)
        requested = call_dict(client, "dev_request_competing_audio_focus", {"mode": "permanent"}, timeout=30.0)
        require(bool(requested.get("granted")), f"permanent focus request failed: {requested}")
        wait_state(client, PAUSE_STATE)
        call_dict(client, "dev_abandon_competing_audio_focus", timeout=30.0)
        time.sleep(2.0)
        wait_state(client, PAUSE_STATE, timeout_s=1.0)
        print("PASS: permanent focus loss paused without an automatic resume")

        crash = call_dict(client, "dev_crash_probe", timeout=30.0)
        require(not bool(crash.get("signatureDetected")), f"Crash signature detected: {crash}")
        print("ANDROID AUDIO FOCUS: PASS")
        return 0
    except Exception as exc:
        print(f"ANDROID AUDIO FOCUS: FAIL\n{exc}", file=sys.stderr)
        try:
            diagnostics = call_dict(client, "collect_playback_diagnostics", {
                "label": "audio-focus-failure",
            }, timeout=90.0)
            print(json.dumps(diagnostics, indent=2, sort_keys=True), file=sys.stderr)
        except Exception as diagnostic_exc:
            print(f"WARN: audio-focus diagnostics failed: {diagnostic_exc}", file=sys.stderr)
        return 1
    finally:
        try:
            call_dict(client, "dev_abandon_competing_audio_focus", timeout=30.0)
        except Exception:
            pass
        try:
            call_dict(client, "dev_exit_session", {"stop_app": True}, timeout=30.0)
        except Exception:
            pass
        client.close()


if __name__ == "__main__":
    raise SystemExit(main())
