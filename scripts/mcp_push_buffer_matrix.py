#!/usr/bin/env python3
"""Measure stock-Core legacy Push write limits on one Android client."""
from __future__ import annotations

import argparse
import json
import pathlib
import statistics
import sys
import time

from sagetv_dev_mcp.config import default_server_address, default_server_value, default_test_value
from mcp_lifecycle_test import MCPProcess, call_dict, initialize, require, wait_automation_ready


VALID_LIMITS = ("auto", "32768", "65536", "131072")


def integer(state: dict, key: str, default: int = -1) -> int:
    try:
        return int(state.get(key, default))
    except (TypeError, ValueError):
        return default


def sample_push(client: MCPProcess, seconds: float) -> tuple[dict, list[dict]]:
    samples: list[dict] = []
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        samples.append(call_dict(client, "dev_player_state", timeout=30.0))
        time.sleep(0.5)
    require(samples, "no Push samples were captured")
    first, last = samples[0], samples[-1]
    calls = max(0, integer(last, "health_dataSourcePushCount", 0)
                - integer(first, "health_dataSourcePushCount", 0))
    pushed = max(0, integer(last, "health_dataSourcePushedBytes", 0)
                 - integer(first, "health_dataSourcePushedBytes", 0))
    used = [integer(item, "health_dataSourceBufferUsedBytes", 0) for item in samples]
    free = [integer(item, "health_dataSourceBufferFreeBytes", 0) for item in samples]
    replies = [integer(item, "lastPushReply", -1) for item in samples]
    report = {
        "sampleSeconds": seconds,
        "pushCalls": calls,
        "pushedBytes": pushed,
        "pushCallsPerSecond": round(calls / max(seconds, 0.001), 3),
        "meanPayloadBytes": round(pushed / calls, 1) if calls else 0,
        "lastPayloadBytes": integer(last, "health_dataSourceLastPushBytes"),
        "maximumPushWriteMs": max(integer(item, "health_dataSourceMaximumPushWriteMs", 0) for item in samples),
        "blockedPushCalls": integer(last, "health_dataSourceBlockedPushCount", 0),
        "bufferUsedBytes": {"min": min(used), "median": statistics.median(used), "max": max(used)},
        "bufferFreeBytes": {"min": min(free), "median": statistics.median(free), "max": max(free)},
        "serverFreeSpaceReplyBytes": {"min": min(replies), "median": statistics.median(replies), "max": max(replies)},
        "loadingSamplesAfterStart": sum(bool(item.get("health_isLoading")) for item in samples),
        "videoDroppedDelta": max(0, integer(last, "health_videoDropped", 0) - integer(first, "health_videoDropped", 0)),
        "audioDroppedDelta": max(0, integer(last, "health_audioDropped", 0) - integer(first, "health_audioDropped", 0)),
        "heapUsedBytes": {"start": integer(first, "clientHeapUsedBytes"), "end": integer(last, "clientHeapUsedBytes")},
    }
    return report, samples


def run_case(client: MCPProcess, args: argparse.Namespace, limit: str) -> dict:
    clean = call_dict(client, "dev_prepare_clean_start", {"wake": True, "graceful_timeout_s": 2.0}, timeout=30.0)
    require(bool(clean.get("readyToLaunch")), f"clean start failed for {limit}: {clean}")
    configured = call_dict(client, "dev_set_player_config", {
        "player": args.player, "streaming": "dynamic", "decoding": "hardware",
        "push_buffer_limit": limit,
    })
    expected = 32768 if limit == "auto" else int(limit)
    require(integer(configured, "pushBufferEffectiveBytes") == expected,
            f"limit {limit} was not persisted: {configured}")
    call_dict(client, "dev_connect_server", {
        "address": args.server_address, "port": args.server_port, "save": False,
    })
    wait_automation_ready(client)
    started_at = time.monotonic()
    started = call_dict(client, "dev_play_server_path", {
        "server_path": args.server_path, "timeout_s": args.timeout_s, "verify_ms": 2500,
    }, timeout=args.timeout_s + 35.0)
    first_frame_ms = round((time.monotonic() - started_at) * 1000)
    require(bool(started.get("passed")), f"Push startup failed for {limit}: {started}")
    state = call_dict(client, "dev_player_state", timeout=30.0)
    require(bool(state.get("health_pushMode")), f"case {limit} did not negotiate Push: {state}")
    require(integer(state, "pushBufferEffectiveBytes") == expected,
            f"active limit mismatch for {limit}: {state}")

    steady, _ = sample_push(client, args.sample_seconds)
    # Capture the baseline after startup has settled.  The initial Push open
    # itself emits a FLUSH and can complete after the first-frame snapshot.
    # Use a distant target so the seek oracle cannot accept ordinary playback
    # progress inside its tolerance without observing a new server FLUSH.
    pre_seek = call_dict(client, "dev_player_state", timeout=30.0)
    flush_before = integer(pre_seek, "serverFlushSequence", 0)
    position = max(0, integer(pre_seek, "sageTimelineMs", 0))
    duration = integer(pre_seek, "health_durationMs", -1)
    target = position - 30_000 if position >= 60_000 else position + 30_000
    if duration > 40_000:
        target = min(target, duration - 30_000)
    seek = call_dict(client, "dev_server_seek_time", {
        "target_ms": target, "tolerance_ms": 2500,
        "timeout_s": min(args.timeout_s, 45.0), "stable_ms": 1500,
    }, timeout=min(args.timeout_s, 45.0) + 35.0)
    after_seek = call_dict(client, "dev_player_state", timeout=30.0)
    require(integer(after_seek, "serverFlushSequence", 0) > flush_before,
            f"seek did not cross a Push FLUSH for {limit}: {after_seek}")
    seek_recovery = call_dict(client, "dev_wait_for_playback_started", {
        "timeout_s": min(args.timeout_s, 30.0), "verify_ms": 1500,
        "expect_video": True, "expect_audio": True,
    }, timeout=min(args.timeout_s, 30.0) + 20.0)
    require(bool(seek_recovery.get("passed")),
            f"Push output did not recover after FLUSH for {limit}: {seek_recovery}")

    call_dict(client, "dev_player_control", {"action": "pause"}, timeout=30.0)
    time.sleep(0.75)
    call_dict(client, "dev_player_control", {"action": "play"}, timeout=30.0)
    resumed = call_dict(client, "dev_wait_for_playback_started", {
        "timeout_s": args.timeout_s, "verify_ms": 1500,
        "expect_video": True, "expect_audio": True,
    }, timeout=args.timeout_s + 20.0)
    require(bool(resumed.get("passed")), f"pause/resume recovery failed for {limit}: {resumed}")

    live = None
    if args.live_channel and not args.skip_live:
        command = call_dict(client, "dev_sage_command", {"command": "live_tv"}, timeout=30.0)
        playback = call_dict(client, "dev_wait_for_playback_started", {
            "timeout_s": args.timeout_s, "verify_ms": 2500,
            "expect_video": True, "expect_audio": True,
        }, timeout=args.timeout_s + 20.0)
        live_state = call_dict(client, "dev_player_state", timeout=30.0)
        require(bool(playback.get("passed")),
                f"recording-to-Live-TV output did not recover for {limit}: {playback}")
        require(bool(live_state.get("health_pushMode")),
                f"Live TV did not retain Push for {limit}: {live_state}")
        require(integer(live_state, "pushBufferEffectiveBytes") == expected,
                f"Live TV lost the negotiated limit for {limit}: {live_state}")
        live = {"passed": True, "command": command,
                "effectiveBytes": integer(live_state, "pushBufferEffectiveBytes")}

    final_state = call_dict(client, "dev_player_state", timeout=30.0)
    crash = call_dict(client, "dev_crash_probe", timeout=30.0)
    require(not bool(crash.get("signatureDetected")), f"crash detected for {limit}: {crash}")
    return {
        "configured": limit,
        "effectiveBytes": expected,
        "firstFrameWallMs": first_frame_ms,
        "steady": steady,
        "seek": {"targetMs": target, "exactLandingPassed": bool(seek.get("landed")),
                 "reachedMs": seek.get("reached_ms"),
                 "flushRecoveryPassed": True,
                 "recoveryMs": seek.get("recoveryMs"),
                 "flushSequenceBefore": flush_before,
                 "flushSequenceAfter": integer(after_seek, "serverFlushSequence", 0)},
        "pauseResumePassed": True,
        "liveTransitionPassed": live is not None,
        "finalHeapUsedBytes": integer(final_state, "clientHeapUsedBytes"),
        "finalPlayerError": final_state.get("health_playerError", ""),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="Measure bounded legacy SageTV Push write negotiation")
    parser.add_argument("--server-address", default=default_server_address())
    parser.add_argument("--server-port", type=int, default=int(default_server_value("miniclient_port", 31099)))
    parser.add_argument("--server-path", required=True)
    parser.add_argument(
        "--player", choices=("media3", "exoplayer"), default="exoplayer",
        help=("backend used for the legacy Push measurement; Exo is the default "
              "because Media3 intentionally advertises MPEG-TS Pull support"),
    )
    parser.add_argument("--limits", default=",".join(VALID_LIMITS))
    parser.add_argument("--sample-seconds", type=float, default=8.0)
    parser.add_argument("--timeout-s", type=float, default=60.0)
    channels = default_test_value("live_channels", ["2.1"])
    parser.add_argument("--live-channel", default=str(channels[0] if channels else ""),
                        help="non-empty value enables the recording-to-Live-TV transition gate")
    parser.add_argument("--skip-live", action="store_true",
                        help="measure recording/FLUSH behavior without changing a live tuner")
    parser.add_argument("--output", type=pathlib.Path)
    args = parser.parse_args()
    limits = [item.strip().lower() for item in args.limits.split(",") if item.strip()]
    invalid = [item for item in limits if item not in VALID_LIMITS]
    if invalid:
        parser.error(f"invalid limits: {invalid}; choose from {VALID_LIMITS}")
    report = {"status": "STARTING", "server": args.server_address,
              "serverPath": args.server_path, "player": args.player, "cases": []}
    client = MCPProcess()
    try:
        negotiated, _ = initialize(client)
        report["mcpProtocol"] = negotiated
        call_dict(client, "adb_connect", timeout=30.0)
        for limit in limits:
            print(f"MEASURE: Push buffer limit {limit}")
            report["cases"].append(run_case(client, args, limit))
            call_dict(client, "dev_exit_session", {"stop_app": True}, timeout=30.0)
        report["status"] = "PASS"
        print(json.dumps(report, indent=2, sort_keys=True))
        if args.output:
            args.output.parent.mkdir(parents=True, exist_ok=True)
            args.output.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
        print("LEGACY PUSH BUFFER MATRIX: PASS")
        return 0
    except Exception as exc:
        report["status"] = "FAIL"
        report["error"] = str(exc)
        if args.output:
            args.output.parent.mkdir(parents=True, exist_ok=True)
            args.output.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
        print(f"LEGACY PUSH BUFFER MATRIX: FAIL\n{exc}", file=sys.stderr)
        return 1
    finally:
        try:
            call_dict(client, "dev_exit_session", {"stop_app": True}, timeout=30.0)
        except Exception:
            pass
        client.close()


if __name__ == "__main__":
    raise SystemExit(main())
