#!/usr/bin/env python3
"""Physical hardware gate for negotiated native-rate and seek-scan playback."""
from __future__ import annotations

from sagetv_dev_mcp.config import default_server_address, default_smb_mappings, default_smb_value, default_server_value

import argparse
import json
import sys
import time

from mcp_lifecycle_test import MCPProcess, call_dict, initialize, require, wait_automation_ready


PLAY_STATE = 2
PAUSE_STATE = 3


def state(client: MCPProcess) -> dict:
    return call_dict(client, "dev_player_state", timeout=30.0)


def position(snapshot: dict) -> int:
    return int(snapshot.get("health_playerPositionMs", -1) or -1)


def wait_playing(client: MCPProcess, timeout_s: float = 15.0) -> dict:
    deadline = time.monotonic() + timeout_s
    last: dict = {}
    while time.monotonic() < deadline:
        last = state(client)
        if (int(last.get("state", -1)) == PLAY_STATE
                and bool(last.get("health_isPlaying"))
                and position(last) >= 0):
            return last
        time.sleep(0.25)
    raise RuntimeError(f"player did not become healthy: {last}")


def wait_rendered_recovery(client: MCPProcess, timeout_s: float = 20.0) -> dict:
    deadline = time.monotonic() + timeout_s
    last: dict = {}
    while time.monotonic() < deadline:
        last = state(client)
        if (bool(last.get("health_isPlaying"))
                and int(last.get("health_videoRendered", 0) or 0) > 0):
            return last
        time.sleep(0.25)
    raise RuntimeError(f"video did not recover after scan stopped: {last}")


def measure(client: MCPProcess, seconds: float) -> tuple[dict, dict, int]:
    before = wait_playing(client)
    time.sleep(seconds)
    after = state(client)
    return before, after, position(after) - position(before)


def observe_cadence(client: MCPProcess, seconds: float) -> dict:
    before = wait_playing(client)
    started_ns = time.monotonic_ns()
    time.sleep(seconds)
    after = state(client)
    wall_ms = max(1, (time.monotonic_ns() - started_ns) // 1_000_000)

    def value(snapshot: dict, key: str) -> int:
        try:
            return int(snapshot.get(key, 0) or 0)
        except (TypeError, ValueError):
            return 0

    def delta(key: str) -> int:
        return value(after, key) - value(before, key)

    media_ms = position(after) - position(before)
    return {
        "requestedObserveMs": int(seconds * 1000),
        "wallElapsedMs": wall_ms,
        "playerPositionDeltaMs": media_ms,
        "realtimeRatio": media_ms / wall_ms,
        "videoOutputDelta": delta("health_videoRendered"),
        "videoDroppedDelta": delta("health_videoDropped"),
        "videoSkippedDelta": delta("health_videoSkipped"),
        "audioOutputDelta": delta("health_audioRendered"),
        "audioDroppedDelta": delta("health_audioDropped"),
        "videoDecoder": after.get("health_videoDecoder", ""),
        "videoDecoderKind": after.get("health_videoDecoderKind", ""),
        "audioDecoder": after.get("health_audioDecoder", ""),
        "audioDecoderKind": after.get("health_audioDecoderKind", ""),
        "playerError": after.get("health_playerError", after.get("playerError", "")),
        "stillPlaying": bool(after.get("health_isPlaying")),
    }


def set_rate(client: MCPProcess, requested: float) -> dict:
    result = call_dict(client, "dev_playback_rate", {"rate": requested}, timeout=30.0)
    accepted = float(result.get("acceptedRate", 1.0))
    return {**result, "acceptedRate": accepted}


def wait_rate(client: MCPProcess, expected: float, timeout_s: float = 8.0) -> dict:
    deadline = time.monotonic() + timeout_s
    last: dict = {}
    while time.monotonic() < deadline:
        last = state(client)
        if float(last.get("playbackRate", 1.0)) == expected:
            return last
        time.sleep(0.2)
    raise RuntimeError(f"server did not select rate {expected}: {last}")


def main() -> int:
    parser = argparse.ArgumentParser(description="Run Android playback-rate physical gates")
    parser.add_argument("--server-address", default=default_server_address())
    parser.add_argument("--server-port", type=int, default=int(default_server_value("miniclient_port", 31099)))
    media = parser.add_mutually_exclusive_group(required=True)
    media.add_argument("--server-path",
                       help="Exact server path; requires the optional Vibe server watch-file event")
    media.add_argument("--video-name",
                       help="Resolve and play a SageTV MediaFile by name (stock-server compatible)")
    parser.add_argument("--player", choices=("media3", "exoplayer", "gsyplayer"), default="media3")
    parser.add_argument("--streaming", choices=("pull", "smb_direct"), default="pull")
    parser.add_argument("--gsy-engine", choices=("media3", "legacy_exo"), default="media3")
    parser.add_argument("--smb-mappings", default=default_smb_mappings())
    parser.add_argument("--smb-username", default=default_smb_value("username"))
    parser.add_argument("--smb-password", default=default_smb_value("password"))
    parser.add_argument("--playback-timeout-s", type=float, default=60.0)
    parser.add_argument("--cadence-only", action="store_true",
                        help="measure stable 1x playback without requiring native scan rates")
    parser.add_argument("--cadence-observe-s", type=float, default=30.0)
    parser.add_argument("--min-realtime-ratio", type=float, default=0.95)
    parser.add_argument("--server-negotiation", action="store_true",
                        help="also require SageTV smooth_ff/smooth_rew to use negotiated command 30")
    args = parser.parse_args()

    client = MCPProcess()
    try:
        negotiated, _ = initialize(client)
        print(f"PASS: MCP initialize handshake ({negotiated})")
        call_dict(client, "adb_connect", timeout=30.0)
        clean = call_dict(client, "dev_prepare_clean_start", {
            "wake": True, "graceful_timeout_s": 2.0,
        }, timeout=30.0)
        require(bool(clean.get("readyToLaunch")), f"clean start failed: {clean}")

        config = {
            "player": args.player,
            "streaming": args.streaming,
            "decoding": "hardware",
        }
        if args.player == "gsyplayer":
            config["gsy_engine"] = args.gsy_engine
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
        if args.video_name:
            started = call_dict(client, "dev_play_video", {
                "video_name": args.video_name,
                "timeout_s": args.playback_timeout_s,
                "verify_ms": 2500,
            }, timeout=args.playback_timeout_s + 35.0)
        else:
            started = call_dict(client, "dev_play_server_path", {
                "server_path": args.server_path,
                "timeout_s": args.playback_timeout_s,
                "verify_ms": 2500,
            }, timeout=args.playback_timeout_s + 35.0)
        require(bool(started.get("passed")), f"initial playback failed: {started}")
        if args.video_name:
            # A named stock-server launch can inherit SageTV's saved resume
            # point. Normalize only the active player for deterministic rate
            # measurements without modifying the user's watched metadata.
            call_dict(client, "dev_local_seek_absolute", {"target_ms": 0}, timeout=30.0)
        initial = wait_playing(client)
        require(str(initial.get("health_videoDecoderKind", "")).lower() == "hardware",
                f"hardware decoder required: {initial.get('health_videoDecoderKind')}")

        if args.cadence_only:
            cadence = observe_cadence(client, max(2.0, min(args.cadence_observe_s, 900.0)))
            require(cadence["stillPlaying"], f"playback stopped during cadence gate: {cadence}")
            require(not str(cadence["playerError"] or "").strip(),
                    f"player error during cadence gate: {cadence}")
            require(cadence["videoOutputDelta"] > 0,
                    f"video output did not advance: {cadence}")
            require(cadence["audioOutputDelta"] > 0,
                    f"audio output did not advance: {cadence}")
            require(cadence["realtimeRatio"] >= args.min_realtime_ratio,
                    f"media clock below realtime threshold: {cadence}")
            call_dict(client, "dev_player_control", {"action": "stop"}, timeout=30.0)
            print("ANDROID PLAYBACK CADENCE: PASS")
            print(json.dumps({
                "player": args.player,
                "gsyEngine": args.gsy_engine if args.player == "gsyplayer" else "",
                "streaming": args.streaming,
                "cadence": cadence,
            }, indent=2, sort_keys=True))
            return 0

        normal_before, normal_after, normal_delta = measure(client, 2.0)
        require(normal_delta >= 1000, f"1x media clock did not advance: {normal_delta}ms")

        native = set_rate(client, 2.0)
        require(native["acceptedRate"] == 2.0, f"2x rejected: {native}")
        _, native_after, native_delta = measure(client, 2.5)
        require(native_delta >= 3500, f"2x rate did not materially accelerate: {native_delta}ms")
        require(int(native_after.get("health_videoRendered", 0) or 0)
                > int(normal_after.get("health_videoRendered", 0) or 0),
                "2x rate rendered no new video frames")

        reset = set_rate(client, 1.0)
        require(reset["acceptedRate"] == 1.0, f"1x reset rejected: {reset}")
        call_dict(client, "dev_local_seek_absolute", {"target_ms": 120_000}, timeout=30.0)
        time.sleep(2.0)

        forward = set_rate(client, 4.0)
        require(forward["acceptedRate"] == 4.0, f"4x scan rejected: {forward}")
        forward_before, forward_after, forward_delta = measure(client, 7.0)
        require(forward_delta >= 14000, f"4x scan did not move forward: {forward_delta}ms")
        set_rate(client, 1.0)
        wait_rendered_recovery(client)

        reverse = set_rate(client, -4.0)
        require(reverse["acceptedRate"] == -4.0, f"-4x scan rejected: {reverse}")
        reverse_before = wait_playing(client)
        time.sleep(7.0)
        reverse_after = state(client)
        reverse_delta = position(reverse_after) - position(reverse_before)
        require(reverse_delta <= -8000, f"-4x scan did not move backward: {reverse_delta}ms")
        set_rate(client, 1.0)
        wait_rendered_recovery(client)

        set_rate(client, 4.0)
        call_dict(client, "dev_player_control", {"action": "pause"}, timeout=30.0)
        paused_before = state(client)
        require(int(paused_before.get("state", -1)) == PAUSE_STATE,
                f"pause not accepted: {paused_before}")
        time.sleep(2.0)
        paused_after = state(client)
        require(abs(position(paused_after) - position(paused_before)) <= 750,
                f"scan continued while paused: {position(paused_before)} -> {position(paused_after)}")
        call_dict(client, "dev_player_control", {"action": "play"}, timeout=30.0)
        wait_playing(client)

        prior = float(state(client).get("playbackRate", 1.0))
        unsupported = set_rate(client, 3.0)
        require(unsupported["acceptedRate"] == prior,
                f"unsupported rate changed active rate: prior={prior} result={unsupported}")
        set_rate(client, 1.0)
        wait_rendered_recovery(client)

        if args.server_negotiation:
            call_dict(client, "dev_clear_player_events", timeout=30.0)
            call_dict(client, "dev_sage_command", {"command": "smooth_ff"}, timeout=30.0)
            wait_rate(client, 4.0)
            call_dict(client, "dev_sage_command", {"command": "smooth_rew"}, timeout=30.0)
            wait_rate(client, -4.0)
            negotiated_events = call_dict(client, "dev_player_events", timeout=30.0)
            require("server_playback_rate_command" in str(negotiated_events.get("trapRecent", "")),
                    f"SageTV did not send command 30: {negotiated_events}")
            set_rate(client, 1.0)
            wait_rendered_recovery(client)

        call_dict(client, "dev_player_control", {"action": "stop"}, timeout=30.0)
        time.sleep(0.5)
        stopped = state(client)
        require(float(stopped.get("playbackRate", 1.0)) == 1.0,
                f"STOP did not reset rate: {stopped.get('playbackRate')}")
        crash = call_dict(client, "dev_crash_probe", timeout=30.0)
        require(not bool(crash.get("signatureDetected")), f"crash signature detected: {crash}")

        print("ANDROID PLAYBACK RATE: PASS")
        print(json.dumps({
            "player": args.player,
            "streaming": args.streaming,
            "decoder": initial.get("health_videoDecoder"),
            "normalDeltaMs": normal_delta,
            "native2xDeltaMs": native_delta,
            "scan4xDeltaMs": forward_delta,
            "scanMinus4xDeltaMs": reverse_delta,
            "serverNegotiation": args.server_negotiation,
        }, indent=2, sort_keys=True))
        return 0
    except Exception as exc:
        print(f"ANDROID PLAYBACK RATE: FAIL\n{exc}", file=sys.stderr)
        try:
            diagnostics = call_dict(client, "collect_playback_diagnostics", {
                "label": "playback-rate-failure",
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
