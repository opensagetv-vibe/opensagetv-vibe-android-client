#!/usr/bin/env python3
"""Verify MiniPlayer command 28 against a real hardware-decoded recording."""
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


def wait_for_step(client: MCPProcess, before_ms: int, before_rendered: int,
                  amount: int, timeout_s: float = 8.0) -> dict:
    deadline = time.monotonic() + timeout_s
    last: dict = {}
    while time.monotonic() < deadline:
        last = call_dict(client, "dev_player_state", timeout=30.0)
        after_ms = int(last.get("health_playerPositionMs", -1))
        after_rendered = int(last.get("health_videoRendered", -1))
        moved = after_ms > before_ms if amount > 0 else after_ms < before_ms
        rendered = before_rendered < 0 or after_rendered > before_rendered
        if moved and rendered:
            return last
        time.sleep(0.2)
    raise RuntimeError(f"frame did not advance: beforeMs={before_ms} beforeRendered={before_rendered} last={last}")


def main() -> int:
    parser = argparse.ArgumentParser(description="Run the Android command-28 physical gate")
    parser.add_argument("--server-address", default="192.168.10.232")
    parser.add_argument("--server-port", type=int, default=31099)
    parser.add_argument("--server-path", required=True)
    parser.add_argument("--player", choices=("exoplayer", "media3", "ijkplayer"), default="media3")
    parser.add_argument("--streaming", choices=("pull", "push", "smb_direct", "smb_auto"), default="pull")
    parser.add_argument("--amount", type=int, default=1)
    parser.add_argument("--playback-timeout-s", type=float, default=60.0)
    args = parser.parse_args()
    if args.amount == 0:
        parser.error("--amount must be a non-zero signed frame count")

    supported = args.streaming in {"pull", "smb_direct", "smb_auto"}
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
            "decoding": "hardware",
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
            "verify_ms": 1500,
        }, timeout=args.playback_timeout_s + 35.0)
        require(bool(started.get("passed")), f"Initial playback failed: {started}")

        call_dict(client, "dev_player_control", {"action": "pause"}, timeout=30.0)
        before = wait_state(client, PAUSE_STATE)
        require(
            str(before.get("health_videoDecoderKind", "")).lower() == "hardware",
            f"frame-step gate requires hardware video decode: {before.get('health_videoDecoderKind')}",
        )
        call_dict(client, "dev_clear_player_events", timeout=30.0)
        before_ms = int(before.get("health_playerPositionMs", -1))
        before_rendered = int(before.get("health_videoRendered", -1))
        result = call_dict(client, "dev_frame_step", {"amount": args.amount}, timeout=30.0)
        require(bool(result.get("accepted")) == supported, f"unexpected frame-step acceptance: {result}")

        if supported:
            after = wait_for_step(client, before_ms, before_rendered, args.amount)
            require(int(after.get("state", -1)) == PAUSE_STATE, f"frame-step resumed playback: {after}")
            require(not bool(after.get("health_isPlaying")), f"frame-step left player running: {after}")
            events = call_dict(client, "dev_player_events", timeout=30.0)
            recent = str(events.get("trapRecent", ""))
            for event_name in ("frame_step_invoke", "frame_step_return", "server_frame_step_command"):
                require(event_name in recent, f"missing {event_name} event: {events}")
            delta_ms = int(after.get("health_playerPositionMs", -1)) - before_ms
            print(
                "PASS: paused command 28 advanced one hardware-decoded frame "
                f"player={args.player} source={args.streaming} deltaMs={delta_ms}"
            )

            call_dict(client, "dev_player_control", {"action": "play"}, timeout=30.0)
            wait_state(client, PLAY_STATE)
            call_dict(client, "dev_clear_player_events", timeout=30.0)
            playing_result = call_dict(client, "dev_frame_step", {"amount": args.amount}, timeout=30.0)
            require(not bool(playing_result.get("accepted")), f"playing frame-step must be rejected: {playing_result}")
            playing_events = call_dict(client, "dev_player_events", timeout=30.0)
            require(
                "server_frame_step_unsupported" in str(playing_events.get("trapRecent", "")),
                f"playing rejection was not trapped: {playing_events}",
            )
            print("PASS: command 28 is rejected safely while playing")
        else:
            events = call_dict(client, "dev_player_events", timeout=30.0)
            require(
                "server_frame_step_unsupported" in str(events.get("trapRecent", "")),
                f"unsupported source rejection was not trapped: {events}",
            )
            print(f"PASS: command 28 is rejected safely for unsupported source {args.streaming}")

        crash = call_dict(client, "dev_crash_probe", timeout=30.0)
        require(not bool(crash.get("signatureDetected")), f"Crash signature detected: {crash}")
        print("ANDROID FRAME STEP: PASS")
        return 0
    except Exception as exc:
        print(f"ANDROID FRAME STEP: FAIL\n{exc}", file=sys.stderr)
        try:
            diagnostics = call_dict(client, "collect_playback_diagnostics", {
                "label": "frame-step-failure",
            }, timeout=90.0)
            print(json.dumps(diagnostics, indent=2, sort_keys=True), file=sys.stderr)
        except Exception as diagnostic_exc:
            print(f"WARN: frame-step diagnostics failed: {diagnostic_exc}", file=sys.stderr)
        return 1
    finally:
        try:
            call_dict(client, "dev_exit_session", {"stop_app": True}, timeout=30.0)
        except Exception:
            pass
        client.close()


if __name__ == "__main__":
    raise SystemExit(main())
