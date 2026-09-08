#!/usr/bin/env python3
"""MCP dedicated Android debug Comskip playback-health test.

This uses the debug APK's dedicated Comskip operation. It does not inject an Android key,
resolve the normal video-playing arrow mapping, or simulate a long press. The MiniClient does not receive semantic Comskip marker
start/end metadata from the SageTV STV, so the test records the actual recovery landing.
"""
from __future__ import annotations

from sagetv_dev_mcp.config import default_server_address, default_server_value

import argparse
import json
import sys
import time
from typing import Any

from mcp_seek_suite import MCPProcess, call_dict, checkpoint, initialize
from mcp_lifecycle_test import require, wait_automation_ready


def fmt_ms(value: Any) -> str:
    try:
        ms = int(value)
    except (TypeError, ValueError):
        return "n/a"
    if ms < 0:
        return "n/a"
    hours, rem = divmod(ms, 3_600_000)
    minutes, rem = divmod(rem, 60_000)
    seconds, millis = divmod(rem, 1000)
    return f"{hours:02d}:{minutes:02d}:{seconds:02d}.{millis:03d}"


def print_comskip_result(direction: str, result: dict[str, Any]) -> bool:
    observation_complete = bool(result.get("passed", False))
    recovered = bool(result.get("recovered", observation_complete))
    target_ok = result.get("target_within_tolerance") is not False
    passed = observation_complete and recovered and target_ok
    print(("PASS" if passed else "FAIL") + f": Comskip SageTV command {direction}")
    print(
        "  debug-direct Comskip: "
        f"direction={result.get('arrowDirection')} "
        f"command={result.get('directSageCommand') or result.get('resolvedArrowCommand')} "
        f"markerMetadataAvailable={result.get('comskipMarkerDataAvailable')}"
    )
    print(
        "  server seek: "
        f"required={result.get('server_seek_required')} "
        f"observed={result.get('server_seek_observed')} "
        f"sequence={result.get('serverSeekSequenceBefore')}->{result.get('serverSeekSequenceAfter')}"
    )
    print(
        "  landing timeline: "
        f"before={fmt_ms(result.get('timelineBeforeMs'))} "
        f"video={fmt_ms(result.get('videoRecoveryTimelineMs'))} "
        f"audio={fmt_ms(result.get('audioRecoveryTimelineMs'))} "
        f"A/V={fmt_ms(result.get('outputRecoveryTimelineMs'))} "
        f"landing={fmt_ms(result.get('landingTimelineMs'))} "
        f"jump={int(result.get('landingDeltaMs', 0)):+d} ms"
    )
    print(
        "  playback health: "
        f"recovery={result.get('recoveryMs')} ms "
        f"videoRecovered={result.get('videoRecovered')} "
        f"audioRecovered={result.get('audioRecovered')} "
        f"stillPlaying={result.get('stillPlaying')} "
        f"videoStillAdvancing={result.get('videoStillAdvancing')} "
        f"audioStillAdvancing={result.get('audioStillAdvancing')} "
        f"bufferingSeen={result.get('bufferingSeen')} "
        f"failure={result.get('healthFailureReason', '') or 'none'}"
    )
    print(
        "  decoder/output: "
        f"videoDecoder={result.get('final_videoDecoder', '') or result.get('post_videoDecoder', '')} "
        f"videoKind={result.get('final_videoDecoderKind', '') or result.get('post_videoDecoderKind', '')} "
        f"audioDecoder={result.get('final_audioDecoder', '') or result.get('post_audioDecoder', '')} "
        f"audioKind={result.get('final_audioDecoderKind', '') or result.get('post_audioDecoderKind', '')} "
        f"surfaceValid={result.get('final_surfaceValid')} "
        f"audioTrackPlayState={result.get('final_audioTrackPlayState')}"
    )
    if result.get("target_known"):
        print(
            "  expected marker target: "
            f"target={fmt_ms(result.get('expected_target_ms'))} "
            f"error={int(result.get('target_error_ms', 0)):+d} ms "
            f"withinTolerance={result.get('target_within_tolerance')}"
        )
    else:
        print("  INFO: SageTV/STV Comskip marker times are not semantically exposed to the MiniClient; actual A/V recovery landing is reported above.")
    if result.get("playerErrorSeen"):
        print(f"  player error: {result.get('firstPlayerError')}")
    return passed


def print_auto_skip_timing(state: dict[str, Any]) -> None:
    seek_ms = int(state.get("serverSeekMonotonicMs", -1))
    read_ms = int(state.get("sourceFirstReadMonotonicMs", -1))
    first_frame_ms = -1
    for event in state.get("trapEvents", []):
        event_ms = int(event.get("monotonicMs", -1))
        if event.get("event") == "first_video_frame" and event_ms >= seek_ms:
            first_frame_ms = event_ms
            break
    print("  automatic Comskip timing: "
          f"serverSeekMonotonicMs={seek_ms} "
          f"sourceFirstReadDeltaMs={read_ms - seek_ms if read_ms >= seek_ms >= 0 else -1} "
          f"firstRenderedFrameDeltaMs={first_frame_ms - seek_ms if first_frame_ms >= seek_ms >= 0 else -1}")


def main() -> int:
    parser = argparse.ArgumentParser(description="Exercise SageTV Comskip through the dedicated Android debug control")
    parser.add_argument("--direction", choices=("right", "left", "both"), default="right")
    parser.add_argument("--expected-target-ms", type=int, default=-1, help="Optional known Comskip marker target for a single-direction test")
    parser.add_argument("--tolerance-ms", type=int, default=5000)
    parser.add_argument("--delay-ms", type=int, default=350)
    parser.add_argument("--settle-ms", type=int, default=300)
    parser.add_argument("--recovery-timeout-ms", type=int, default=12000)
    parser.add_argument("--verify-playback-ms", type=int, default=3000)
    parser.add_argument("--health-poll-ms", type=int, default=250)
    parser.add_argument("--server-address", default=default_server_address())
    parser.add_argument("--server-port", type=int, default=int(default_server_value("miniclient_port", 31099)))
    parser.add_argument("--server-path", help="Exact SageTV server path to start before testing")
    parser.add_argument("--player", choices=("exoplayer", "media3"), default="media3")
    parser.add_argument("--streaming", choices=("dynamic", "push", "pull", "smb_direct", "smb_auto", "fixed"), default="pull")
    parser.add_argument("--smb-mappings", help="Optional Dev-only SageTV-prefix to SMB-root mappings applied before playback")
    parser.add_argument("--decoding", choices=("hardware", "software", "hardware_preferred"), default="hardware")
    parser.add_argument("--playback-timeout-s", type=float, default=60.0)
    parser.add_argument("--playback-verify-ms", type=int, default=3000)
    parser.add_argument("--start-ms", type=int, default=-1, help="Seek here before the Comskip command; use a point inside a known commercial")
    parser.add_argument("--start-tolerance-ms", type=int, default=30000,
                        help="Allowed MPEG-TS sync-point landing error for the pre-Comskip seek")
    parser.add_argument("--accept-auto-skip-on-position", action="store_true",
                        help="Accept a pre-position seek that the SageTV STV automatically advances to the expected marker target")
    parser.add_argument("--auto-skip-wait-ms", type=int, default=10000,
                        help="When automatic skip acceptance is enabled, wait this long for the STV-owned marker seek")
    parser.add_argument("--summary-only", action="store_true",
                        help="Print concise PASS/FAIL and timing evidence instead of complete player snapshots")
    parser.add_argument("--server-jump-count", type=int, default=0,
                        help="Position through this many SageTV FF_2 commands instead of a client-local seek")
    parser.add_argument("--server-rewind-count", type=int, default=0,
                        help="Position backward through this many SageTV REW_2 commands before forward positioning")
    parser.add_argument("--server-small-jump-count", type=int, default=0,
                        help="After FF_2 positioning, advance through this many SageTV FF commands")
    args = parser.parse_args()

    directions = [args.direction] if args.direction != "both" else ["right", "left"]
    if args.direction == "both" and args.expected_target_ms >= 0:
        parser.error("--expected-target-ms is only valid with a single --direction")
    if args.start_ms >= 0 and (args.server_jump_count or args.server_rewind_count or args.server_small_jump_count):
        parser.error("--start-ms and server positioning jumps are mutually exclusive")
    if args.server_jump_count < 0 or args.server_jump_count > 20:
        parser.error("--server-jump-count must be between 0 and 20")
    if args.server_rewind_count < 0 or args.server_rewind_count > 20:
        parser.error("--server-rewind-count must be between 0 and 20")
    if args.server_small_jump_count < 0 or args.server_small_jump_count > 30:
        parser.error("--server-small-jump-count must be between 0 and 30")

    client = MCPProcess()
    failures = 0
    auto_skip_observed = False
    try:
        negotiated, _ = initialize(client)
        print(f"PASS: MCP initialize handshake ({negotiated})")
        call_dict(client, "adb_connect", timeout=30.0)
        if args.server_path:
            clean = call_dict(client, "dev_prepare_clean_start", {
                "wake": True,
                "graceful_timeout_s": 2.0,
            }, timeout=30.0)
            require(bool(clean.get("readyToLaunch")), f"Dev app clean-start preparation failed: {clean}")
            player_config = {
                "player": args.player,
                "streaming": args.streaming,
                "decoding": args.decoding,
            }
            if args.smb_mappings is not None:
                player_config["smb_mappings"] = args.smb_mappings
            call_dict(client, "dev_set_player_config", player_config)
            call_dict(client, "dev_connect_server", {
                "address": args.server_address,
                "port": args.server_port,
                "save": False,
            })
            wait_automation_ready(client)
            started = call_dict(client, "dev_play_server_path", {
                "server_path": args.server_path,
                "timeout_s": args.playback_timeout_s,
                "verify_ms": args.playback_verify_ms,
            }, timeout=args.playback_timeout_s + 35.0)
            require(bool(started.get("passed")), f"Exact-path playback failed: {started}")
            print(f"PASS: exact-path playback started with {args.player}/{args.streaming}/{args.decoding}")
        if args.start_ms >= 0:
            positioned = call_dict(client, "dev_seek_time", {
                "target_ms": args.start_ms,
                "timeout_s": 30.0,
                "stable_ms": args.playback_verify_ms,
            }, timeout=60.0)
            require(bool(positioned.get("passed")), f"Pre-Comskip seek did not recover A/V: {positioned}")
            time.sleep(1.0)
            position_state = call_dict(client, "dev_player_state", timeout=30.0)
            actual_ms = int(position_state.get("mediaTimeMs", -1))
            requested_ms = int(position_state.get("serverRequestedSeekMs", -1))
            if args.accept_auto_skip_on_position and args.expected_target_ms >= 0:
                deadline = time.monotonic() + max(0, args.auto_skip_wait_ms) / 1000.0
                while (abs(requested_ms - args.expected_target_ms) > args.tolerance_ms
                       or abs(actual_ms - args.expected_target_ms) > args.tolerance_ms) and time.monotonic() < deadline:
                    time.sleep(0.25)
                    position_state = call_dict(client, "dev_player_state", timeout=30.0)
                    actual_ms = int(position_state.get("mediaTimeMs", -1))
                    requested_ms = int(position_state.get("serverRequestedSeekMs", -1))
                if (abs(requested_ms - args.expected_target_ms) <= args.tolerance_ms
                        and abs(actual_ms - args.expected_target_ms) <= args.tolerance_ms):
                    auto_skip_observed = True
                    print("PASS: SageTV STV automatically skipped the generated commercial "
                          f"during positioning; requested={requested_ms} target={args.expected_target_ms} actual={actual_ms}")
                    print_auto_skip_timing(position_state)
            if not auto_skip_observed and abs(actual_ms - args.start_ms) <= args.start_tolerance_ms:
                print(f"PASS: positioned inside known commercial at {actual_ms} ms")
            elif auto_skip_observed:
                pass
            else:
                require(False,
                        f"Pre-Comskip seek landed outside tolerance: requested={args.start_ms} actual={actual_ms}")
        for jump_number in range(1, args.server_rewind_count + 1):
            positioned = call_dict(client, "dev_run_seek_check", {
                "commands": ["rew_2"],
                "expected_net_ms": 0,
                "recovery_timeout_ms": args.recovery_timeout_ms,
                "verify_playback_ms": args.playback_verify_ms,
            }, timeout=args.recovery_timeout_ms / 1000.0 + 60.0)
            if not positioned.get("passed"):
                eventual = call_dict(client, "dev_wait_for_playback_started", {
                    "timeout_s": args.recovery_timeout_ms / 1000.0,
                    "verify_ms": args.playback_verify_ms,
                    "expect_video": True,
                    "expect_audio": True,
                }, timeout=args.recovery_timeout_ms / 1000.0 + 20.0)
                require(bool(eventual.get("passed")),
                        f"SageTV REW_2 positioning jump {jump_number} did not recover A/V: {positioned}; eventual={eventual}")
                positioned["eventualRecovery"] = eventual
            print(f"PASS: SageTV-owned reverse positioning jump {jump_number}/{args.server_rewind_count}; "
                  f"landing={positioned.get('outputRecoveryTimelineMs', positioned.get('timelineAfterMs'))}")
        for jump_number in range(1, args.server_jump_count + 1):
            positioned = call_dict(client, "dev_run_seek_check", {
                "commands": ["ff_2"],
                "expected_net_ms": 0,
                "recovery_timeout_ms": args.recovery_timeout_ms,
                "verify_playback_ms": args.playback_verify_ms,
            }, timeout=args.recovery_timeout_ms / 1000.0 + 60.0)
            if not positioned.get("passed"):
                eventual = call_dict(client, "dev_wait_for_playback_started", {
                    "timeout_s": args.recovery_timeout_ms / 1000.0,
                    "verify_ms": args.playback_verify_ms,
                    "expect_video": True,
                    "expect_audio": True,
                }, timeout=args.recovery_timeout_ms / 1000.0 + 20.0)
                require(bool(eventual.get("passed")),
                        f"SageTV FF_2 positioning jump {jump_number} did not recover A/V: {positioned}; eventual={eventual}")
                positioned["eventualRecovery"] = eventual
            print(f"PASS: SageTV-owned positioning jump {jump_number}/{args.server_jump_count}; "
                  f"landing={positioned.get('outputRecoveryTimelineMs', positioned.get('timelineAfterMs'))}")
        for jump_number in range(1, args.server_small_jump_count + 1):
            positioned = call_dict(client, "dev_run_seek_check", {
                "commands": ["ff"],
                "expected_net_ms": 0,
                "recovery_timeout_ms": args.recovery_timeout_ms,
                "verify_playback_ms": args.playback_verify_ms,
            }, timeout=args.recovery_timeout_ms / 1000.0 + 60.0)
            if not positioned.get("passed"):
                eventual = call_dict(client, "dev_wait_for_playback_started", {
                    "timeout_s": args.recovery_timeout_ms / 1000.0,
                    "verify_ms": args.playback_verify_ms,
                    "expect_video": True,
                    "expect_audio": True,
                }, timeout=args.recovery_timeout_ms / 1000.0 + 20.0)
                require(bool(eventual.get("passed")),
                        f"SageTV FF positioning jump {jump_number} did not recover A/V: {positioned}; eventual={eventual}")
                positioned["eventualRecovery"] = eventual
            print(f"PASS: SageTV-owned small positioning jump {jump_number}/{args.server_small_jump_count}; "
                  f"landing={positioned.get('outputRecoveryTimelineMs', positioned.get('timelineAfterMs'))}")
        if (not auto_skip_observed
                and args.accept_auto_skip_on_position
                and args.expected_target_ms >= 0
                and (args.server_rewind_count or args.server_jump_count or args.server_small_jump_count)):
            deadline = time.monotonic() + max(0, args.auto_skip_wait_ms) / 1000.0
            actual_ms = -1
            requested_ms = -1
            while time.monotonic() < deadline:
                position_state = call_dict(client, "dev_player_state", timeout=30.0)
                actual_ms = int(position_state.get("mediaTimeMs", -1))
                requested_ms = int(position_state.get("serverRequestedSeekMs", -1))
                if (abs(requested_ms - args.expected_target_ms) <= args.tolerance_ms
                        and abs(actual_ms - args.expected_target_ms) <= args.tolerance_ms):
                    auto_skip_observed = True
                    break
                time.sleep(0.25)
            if auto_skip_observed:
                print("PASS: SageTV STV automatically skipped the generated commercial "
                      f"after server-owned positioning; requested={requested_ms} "
                      f"target={args.expected_target_ms} actual={actual_ms}")
                print_auto_skip_timing(position_state)
        fullscreen = call_dict(client, "dev_ensure_fullscreen_playback", {"timeout_s": 8.0}, timeout=30.0)
        require(bool(fullscreen.get("passed")), f"Fullscreen playback was not established before Comskip: {fullscreen}")
        print("PASS: fullscreen playback context established before Comskip")
        state = call_dict(client, "dev_player_state")
        if not args.summary_only:
            print("Current player state:")
            print(json.dumps(state, indent=2, sort_keys=True))
        if not state.get("connected") or not state.get("playerActive"):
            raise RuntimeError("Start a recording with Comskip markers before running this test")
        if int(state.get("state", -1)) != 2:
            raise RuntimeError(f"Start playback before running the Comskip test; current state={state.get('state')}")

        if auto_skip_observed:
            print("PASS: automatic Comskip already exercised the server-owned seek; "
                  "skipping a redundant direct arrow command")
        for direction in ([] if auto_skip_observed else directions):
            result = call_dict(
                client,
                "dev_run_comskip_check",
                {
                    "direction": direction,
                    "expected_target_ms": args.expected_target_ms,
                    "tolerance_ms": args.tolerance_ms,
                    "delay_ms": args.delay_ms,
                    "settle_ms": args.settle_ms,
                    "recovery_timeout_ms": args.recovery_timeout_ms,
                    "verify_playback_ms": args.verify_playback_ms,
                    "health_poll_ms": args.health_poll_ms,
                },
                timeout=90.0,
            )
            if not print_comskip_result(direction, result):
                failures += 1
                checkpoint(client, f"mcp_comskip_{direction}_fail")

        final = call_dict(client, "dev_player_state")
        if not args.summary_only:
            print("Final player state:")
            print(json.dumps(final, indent=2, sort_keys=True))
        if failures:
            print(f"MCP COMSKIP HEALTH SUITE: FAIL ({failures} checks)", file=sys.stderr)
            return 1
        print("MCP COMSKIP HEALTH SUITE: PASS")
        return 0
    except Exception as exc:
        print(f"MCP COMSKIP HEALTH SUITE: FAIL\n{exc}", file=sys.stderr)
        return 1
    finally:
        if args.server_path:
            try:
                call_dict(client, "dev_exit_session", {"stop_app": True}, timeout=30.0)
            except Exception:
                pass
        client.close()


if __name__ == "__main__":
    raise SystemExit(main())
