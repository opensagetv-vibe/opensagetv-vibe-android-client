#!/usr/bin/env python3
"""Verify active multi-audio selection without changing SageTV Core."""
from __future__ import annotations

import argparse
import json
import sys
import time

from mcp_lifecycle_test import MCPProcess, call_dict, initialize, require, wait_automation_ready
from sagetv_dev_mcp.config import default_server_address, default_server_value


def wait_for_av(client: MCPProcess, timeout_s: float, verify_ms: int) -> dict:
    result = call_dict(client, "dev_wait_for_playback_started", {
        "timeout_s": timeout_s,
        "verify_ms": verify_ms,
        "expect_video": True,
        "expect_audio": True,
    }, timeout=timeout_s + 20.0)
    require(bool(result.get("passed")), f"playback did not retain advancing A/V: {result}")
    return result


def main() -> int:
    parser = argparse.ArgumentParser(description="Run the selectable-audio-track physical gate")
    parser.add_argument("--server-path", required=True)
    parser.add_argument("--server-address", default=default_server_address())
    parser.add_argument("--server-port", type=int,
                        default=int(default_server_value("miniclient_port", 31099)))
    parser.add_argument("--player", choices=("media3", "exoplayer", "gsyplayer"),
                        default="media3")
    parser.add_argument("--gsy-engine", choices=("auto", "media3", "legacy_exo"),
                        default="auto")
    parser.add_argument("--streaming", choices=("pull", "dynamic", "fixed"), default="pull")
    parser.add_argument("--first-index", type=int, default=0)
    parser.add_argument("--second-index", type=int, default=1)
    parser.add_argument("--offset-ms", type=int, default=-400)
    parser.add_argument("--output", choices=("passthrough", "decoded"),
                        default="passthrough")
    parser.add_argument("--timeout-s", type=float, default=60.0)
    parser.add_argument("--verify-ms", type=int, default=3000)
    args = parser.parse_args()
    if not -4000 <= args.offset_ms <= 4000:
        parser.error("--offset-ms must be between -4000 and 4000")
    if args.first_index < 0 or args.second_index < 0 or args.first_index == args.second_index:
        parser.error("track indexes must be distinct non-negative ordinals")

    evidence: dict = {"serverPath": args.server_path, "rows": []}
    client = MCPProcess()
    try:
        negotiated, _ = initialize(client)
        print(f"PASS: MCP initialize handshake ({negotiated})")
        call_dict(client, "adb_connect", timeout=30.0)
        clean = call_dict(client, "dev_prepare_clean_start", {
            "wake": True, "graceful_timeout_s": 2.0,
        }, timeout=30.0)
        require(bool(clean.get("readyToLaunch")), f"clean start failed: {clean}")
        call_dict(client, "dev_set_player_config", {
            "player": args.player,
            "streaming": args.streaming,
            "decoding": "hardware",
            "gsy_engine": args.gsy_engine,
        }, timeout=30.0)
        call_dict(client, "dev_connect_server", {
            "address": args.server_address,
            "port": args.server_port,
            "save": False,
        }, timeout=30.0)
        wait_automation_ready(client)
        started = call_dict(client, "dev_play_server_path", {
            "server_path": args.server_path,
            "timeout_s": args.timeout_s,
            "verify_ms": args.verify_ms,
            "restart_from_beginning": True,
        }, timeout=args.timeout_s + 40.0)
        require(bool(started.get("passed")), f"exact-path playback failed: {started}")
        initial = call_dict(client, "dev_player_state", timeout=30.0)
        track_count = int(initial.get("audioTrackCount") or 0)
        require(track_count > max(args.first_index, args.second_index),
                f"fixture exposes only {track_count} audio track(s): {initial}")
        audio_adjustment = {
            "output": args.output,
            "offset_ms": args.offset_ms,
        }
        if args.output == "passthrough":
            audio_adjustment["passthrough_offset_enabled"] = True
        adjusted = call_dict(client, "dev_set_active_audio", audio_adjustment,
                             timeout=30.0)
        require(bool(adjusted.get("accepted")), f"active audio adjustment failed: {adjusted}")
        # Output/passthrough changes intentionally rebuild the player and then
        # perform one exact retained-position re-anchor. Do not race a user
        # track selection against that in-flight replacement; wait for the new
        # renderer instance to become the stable active session first.
        time.sleep(2.0)
        wait_for_av(client, args.timeout_s, args.verify_ms)

        for ordinal in (args.second_index, args.first_index):
            selected = call_dict(client, "dev_set_audio_track", {"index": ordinal}, timeout=30.0)
            require(bool(selected.get("accepted")), f"track {ordinal} was rejected: {selected}")
            time.sleep(1.0)
            health = wait_for_av(client, args.timeout_s, args.verify_ms)
            state = call_dict(client, "dev_player_state", timeout=30.0)
            player_index = int(selected.get("playerIndex"))
            require(int(state.get("selectedAudioTrack")) == player_index,
                    f"track {ordinal} did not remain selected: selected={selected} state={state}")
            require(int(state.get("audioOffsetMs")) == args.offset_ms,
                    f"track {ordinal} lost the active offset: {state}")
            evidence["rows"].append({
                "ordinal": ordinal,
                "playerIndex": player_index,
                "selectedAudioTrack": state.get("selectedAudioTrack"),
                "audioOffsetMs": state.get("audioOffsetMs"),
                "audioOutputMode": state.get("audioOutputMode"),
                "audioOffsetPath": state.get("audioOffsetPath"),
                "videoDecoder": state.get("health_videoDecoder"),
                "audioDecoder": state.get("health_audioDecoder"),
                "health": health.get("health", {}),
                "recoveryMs": health.get("recoveryMs"),
            })
            print(f"PASS: selected audio track ordinal {ordinal} (player id {player_index}) with advancing A/V")

        crash = call_dict(client, "dev_crash_probe", timeout=30.0)
        require(not bool(crash.get("signatureDetected")), f"crash signature detected: {crash}")
        evidence["passed"] = True
        print(json.dumps(evidence, indent=2, sort_keys=True))
        print("ANDROID AUDIO TRACK: PASS")
        return 0
    except Exception as exc:
        evidence["passed"] = False
        evidence["error"] = str(exc)
        print(json.dumps(evidence, indent=2, sort_keys=True), file=sys.stderr)
        print(f"ANDROID AUDIO TRACK: FAIL\n{exc}", file=sys.stderr)
        return 1
    finally:
        try:
            call_dict(client, "dev_exit_session", {"stop_app": True}, timeout=30.0)
        except Exception:
            pass
        client.close()


if __name__ == "__main__":
    raise SystemExit(main())
