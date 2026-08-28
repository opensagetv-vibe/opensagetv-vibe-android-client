#!/usr/bin/env python3
"""Run fresh-session Media3 Push vs Pull Comskip recovery checks.

Comskip is invoked with the direct native SageTV ``right`` / ``left`` command, the
same path proven manually with ``mcp-send-sequence``.  It deliberately does not use
the normal video-playing arrow mapping (FF/REW) or simulate an Android long press.
The MiniClient does not receive semantic Comskip marker timestamps from the SageTV STV,
so the run records real decoded A/V recovery and landing/jump diagnostics without blaming a subsystem when recovery is slow.
"""
from __future__ import annotations

import argparse
import json
import os
import sys
from datetime import datetime
from pathlib import Path
from typing import Any

from mcp_media3_matrix import DEFAULT_MODES, MCPProcess, call_dict, initialize, parse_modes, safe_checkpoint, start_mode
from mcp_config_values import (DECODING_SELECTIONS, add_fixed_encoding_args, decoding_preference, fixed_config_from_args, normalize_decoding, validate_fixed_config)

DEFAULT_DIRECTIONS = ("right", "left")


def _int_value(value: Any, default: int = -1) -> int:
    try:
        return int(value)
    except (TypeError, ValueError):
        return default


def pull_io_delta(result: dict[str, Any]) -> dict[str, Any]:
    """Return per-command Pull socket/READ deltas from debug health snapshots."""
    metrics = {
        "openCount": "dataSourceOpenCount",
        "openWaitMs": "dataSourceOpenWaitMs",
        "networkReadCount": "dataSourceNetworkReadCount",
        "networkReadRequestedBytes": "dataSourceNetworkReadRequestedBytes",
        "networkReadBytes": "dataSourceNetworkReadBytes",
        "networkReadWaitMs": "dataSourceNetworkReadWaitMs",
        "networkReadErrors": "dataSourceNetworkReadErrors",
    }
    out: dict[str, Any] = {}
    for label, wire in metrics.items():
        before = _int_value(result.get(f"before_{wire}"))
        final = _int_value(result.get(f"final_{wire}"))
        if before >= 0 and final >= 0:
            out[label + "Delta"] = max(0, final - before)
    max_read = _int_value(result.get("final_dataSourceNetworkReadMaxRequestedBytes"))
    if max_read >= 0:
        out["networkReadMaxRequestedBytes"] = max_read
    last_open = _int_value(result.get("final_dataSourceLastOpenPosition"))
    if last_open >= 0:
        out["lastOpenPosition"] = last_open
    last_read = _int_value(result.get("final_dataSourceNetworkLastReadPosition"))
    if last_read >= 0:
        out["lastNetworkReadPosition"] = last_read
    return out


def parse_directions(value: str) -> list[str]:
    requested = [item.strip().lower() for item in str(value).split(",") if item.strip()]
    if not requested:
        raise ValueError("--directions must contain right and/or left")
    invalid = [item for item in requested if item not in DEFAULT_DIRECTIONS]
    if invalid:
        raise ValueError(f"unsupported Comskip direction(s): {', '.join(invalid)}")
    result: list[str] = []
    for item in requested:
        if item not in result:
            result.append(item)
    return result


def compact_comskip_result(result: dict[str, Any]) -> dict[str, Any]:
    keys = (
        "passed", "verdict_basis", "arrowDirection", "resolvedArrowCommand",
        "directSageCommand", "inputPath",
        "comskipMarkerDataAvailable", "timelineBeforeMs", "videoRecoveryTimelineMs",
        "audioRecoveryTimelineMs", "outputRecoveryTimelineMs", "landingTimelineMs",
        "landingDeltaMs", "recoveryMs", "videoRecovered", "audioRecovered",
        "stillPlaying", "videoStillAdvancing", "audioStillAdvancing", "bufferingSeen",
        "loadingSeen", "healthFailureReason", "playerErrorSeen", "firstPlayerError",
        "decoderChanged", "audioDecoderChanged", "final_videoDecoder",
        "final_videoDecoderKind", "final_audioDecoder", "final_audioDecoderKind",
        "final_surfaceValid", "final_playbackState", "final_bufferedPositionMs",
        "final_playerPositionMs", "final_lastFileReadPos", "final_bufferLeft",
        "expected_target_ms", "target_known", "target_error_ms", "target_within_tolerance",
        "recovered", "watchdog_expired", "watchdog_ms", "observation_complete", "cause",
    )
    return {key: result.get(key) for key in keys if key in result}


def run_comskip_case(
    client: MCPProcess,
    *,
    mode: str,
    direction: str,
    delay_ms: int,
    settle_ms: int,
    watchdog_ms: int,
    verify_playback_ms: int,
    health_poll_ms: int,
    slow_recovery_ms: int,
) -> dict[str, Any]:
    print(f"STEP [{mode}]: debug-direct Comskip {direction}")
    result = call_dict(
        client,
        "dev_run_comskip_check",
        {
            "direction": direction,
            "expected_target_ms": -1,
            "tolerance_ms": 5000,
            "delay_ms": delay_ms,
            "settle_ms": settle_ms,
            "watchdog_ms": watchdog_ms,
            "verify_playback_ms": verify_playback_ms,
            "health_poll_ms": health_poll_ms,
        },
        timeout=max(90.0, watchdog_ms / 1000.0 + verify_playback_ms / 1000.0 + 20.0),
    )
    recovered = bool(result.get("recovered", False))
    watchdog_expired = bool(result.get("watchdog_expired", False))
    recovery_ms = result.get("recoveryMs")
    try:
        slow = recovered and recovery_ms is not None and int(recovery_ms) > slow_recovery_ms
    except (TypeError, ValueError):
        slow = False
    if watchdog_expired:
        status = "WATCHDOG_EXPIRED"
    elif slow:
        status = "SLOW_RECOVERY"
    elif recovered:
        status = "RECOVERED"
    else:
        status = "OBSERVED"

    summary = compact_comskip_result(result)
    pull_io = pull_io_delta(result)
    if pull_io:
        summary["pullIo"] = pull_io
    summary["slowRecovery"] = slow
    summary["slowRecoveryThresholdMs"] = slow_recovery_ms
    summary["status"] = status
    summary["cause"] = result.get("cause", "undetermined" if watchdog_expired else "none")

    jump = result.get("landingDeltaMs")
    print(status + f": Media3 {mode} Comskip {direction}: "
          f"command={result.get('directSageCommand') or result.get('resolvedArrowCommand')} "
          f"recovery={recovery_ms} ms jump={jump} ms")
    print(
        "  output health: "
        f"videoRecovered={result.get('videoRecovered')} audioRecovered={result.get('audioRecovered')} "
        f"stillPlaying={result.get('stillPlaying')} videoStillAdvancing={result.get('videoStillAdvancing')} "
        f"audioStillAdvancing={result.get('audioStillAdvancing')} bufferingSeen={result.get('bufferingSeen')} "
        f"failure={result.get('healthFailureReason', '') or 'none'}"
    )
    print(
        "  decoder/output: "
        f"videoDecoder={result.get('final_videoDecoder', '')} "
        f"videoKind={result.get('final_videoDecoderKind', '')} "
        f"audioDecoder={result.get('final_audioDecoder', '')} "
        f"audioKind={result.get('final_audioDecoderKind', '')} "
        f"surfaceValid={result.get('final_surfaceValid')}"
    )
    if pull_io:
        print(
            "  pull I/O: "
            f"opens={pull_io.get('openCountDelta', 0)} "
            f"openWait={pull_io.get('openWaitMsDelta', 0)} ms "
            f"READs={pull_io.get('networkReadCountDelta', 0)} "
            f"requested={pull_io.get('networkReadRequestedBytesDelta', 0)} B "
            f"received={pull_io.get('networkReadBytesDelta', 0)} B "
            f"readWait={pull_io.get('networkReadWaitMsDelta', 0)} ms "
            f"maxREAD={pull_io.get('networkReadMaxRequestedBytes', 0)} B "
            f"errors={pull_io.get('networkReadErrorsDelta', 0)}"
        )

    if watchdog_expired:
        print(f"WATCHDOG [{mode}]: Comskip {direction} had not recovered after {watchdog_ms} ms; cause remains undetermined (Android app / SageTV server / FFmpeg path). Capturing diagnostics.")
        summary["checkpoint"] = safe_checkpoint(client, f"media3_{mode}_comskip_{direction}_watchdog")
    elif slow:
        print(f"WARN [{mode}]: Comskip {direction} recovered in {recovery_ms} ms (> {slow_recovery_ms} ms); capturing diagnostics")
        summary["checkpoint"] = safe_checkpoint(client, f"media3_{mode}_comskip_{direction}_slow")
    return summary


def comparison_table(results: dict[str, dict[str, Any]]) -> dict[str, Any]:
    table: dict[str, Any] = {}
    for direction in DEFAULT_DIRECTIONS:
        row: dict[str, Any] = {}
        for mode in DEFAULT_MODES:
            check = results.get(mode, {}).get("checks", {}).get(direction, {})
            if check:
                row[mode] = {
                    "recoveryMs": check.get("recoveryMs"),
                    "landingDeltaMs": check.get("landingDeltaMs"),
                    "directSageCommand": check.get("directSageCommand") or check.get("resolvedArrowCommand"),
                    "passed": check.get("passed"),
                    "status": check.get("status"),
                    "recovered": check.get("recovered"),
                    "watchdogExpired": check.get("watchdog_expired"),
                    "cause": check.get("cause"),
                    "pullIo": check.get("pullIo"),
                }
        if row:
            table[direction] = row
    return table


def write_report(report: dict[str, Any], requested_path: str) -> Path:
    if requested_path:
        path = Path(requested_path).expanduser()
    else:
        artifact_dir = Path(os.environ.get("SAGETV_ARTIFACT_DIR", "/workspace/artifacts/firetv"))
        stamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        path = artifact_dir / f"{stamp}_media3_comskip_matrix.json"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(report, indent=2, sort_keys=True), encoding="utf-8")
    return path


def main() -> int:
    parser = argparse.ArgumentParser(description="Run fresh-session Media3 Push vs Pull debug-direct Comskip matrix")
    parser.add_argument("--server", default="192.168.10.175")
    parser.add_argument("--port", type=int, default=31099)
    parser.add_argument("--modes", default="push,pull", help="Comma-separated: push,pull (legacy dynamic alias accepted)")
    parser.add_argument("--directions", default="right,left", help="Comma-separated: right,left")
    parser.add_argument("--decoding", "--decoder", dest="decoding", type=normalize_decoding, choices=DECODING_SELECTIONS, default="hardware", help="Decoding selection: hardware, software, fallback")
    add_fixed_encoding_args(parser)
    parser.add_argument("--text", required=True, help="Required native SageTV Search text for the test recording")
    parser.add_argument("--text-char-delay-ms", type=int, default=0, help="Text injection pacing: 0 uses proven one-shot Android input text; >0 enables experimental per-character delay (ms)")
    parser.add_argument("--connect-timeout-s", type=float, default=30.0)
    parser.add_argument("--ui-stable-ms", type=int, default=2000)
    parser.add_argument("--playback-timeout-s", type=float, default=45.0)
    parser.add_argument("--startup-verify-ms", type=int, default=1500)
    parser.add_argument("--start-ms", type=int, default=0, help="Absolute debug playback time to seek to before Comskip checks; 0 = beginning")
    parser.add_argument("--seek-tolerance-ms", type=int, default=2000)
    parser.add_argument("--seek-timeout-s", type=float, default=20.0)
    parser.add_argument("--seek-stable-ms", type=int, default=1200, help="Require the requested start position to stay valid after Media3 is READY")
    parser.add_argument("--before-comskip-ms", type=int, default=1500, help="Allow normal playback to advance after the requested start time before the first direct Comskip command")
    parser.add_argument("--between-directions-ms", type=int, default=1500, help="Allow recovered playback to advance before the next direct Comskip command")
    parser.add_argument("--delay-ms", type=int, default=350)
    parser.add_argument("--settle-ms", type=int, default=300)
    parser.add_argument("--watchdog-ms", type=int, default=180000, help="Maximum observation time for Comskip A/V recovery (default: 180000 ms / 3 minutes); expiry is recorded, not failed")
    parser.add_argument("--recovery-timeout-ms", type=int, default=None, help=argparse.SUPPRESS)
    parser.add_argument("--verify-playback-ms", type=int, default=3000)
    parser.add_argument("--health-poll-ms", type=int, default=250)
    parser.add_argument("--slow-recovery-ms", type=int, default=2000)
    parser.add_argument("--report", default="")
    parser.add_argument("--leave-running", action="store_true")
    args = parser.parse_args()
    fixed_config = fixed_config_from_args(args)
    try:
        validate_fixed_config(fixed_config)
    except ValueError as exc:
        parser.error(str(exc))

    try:
        modes = parse_modes(args.modes)
        directions = parse_directions(args.directions)
        search_text = str(args.text).strip()
        if args.start_ms < 0:
            raise ValueError("--start-ms must be >= 0")
        if args.text_char_delay_ms < 0 or args.text_char_delay_ms > 2000:
            raise ValueError("--text-char-delay-ms must be between 0 and 2000")
        watchdog_ms = args.recovery_timeout_ms if args.recovery_timeout_ms is not None else args.watchdog_ms
        if watchdog_ms < 250:
            raise ValueError("--watchdog-ms must be >= 250")
        watchdog_ms = min(int(watchdog_ms), 300000)
    except ValueError as exc:
        parser.error(str(exc))

    import time

    client = MCPProcess()
    results: dict[str, dict[str, Any]] = {}
    failures = 0
    try:
        negotiated, _ = initialize(client)
        print(f"PASS: MCP initialize handshake ({negotiated})")
        call_dict(client, "adb_connect", timeout=30.0)
        print("PASS: adb_connect")

        for mode in modes:
            mode_result: dict[str, Any] = {"mode": mode, "player": "media3", "checks": {}}
            results[mode] = mode_result
            try:
                mode_result["startup"] = start_mode(
                    client,
                    mode=mode,
                    server=args.server,
                    port=args.port,
                    decoder=args.decoding,
                    search_text=search_text,
                    text_char_delay_ms=args.text_char_delay_ms,
                    connect_timeout_s=args.connect_timeout_s,
                    ui_stable_ms=args.ui_stable_ms,
                    playback_timeout_s=args.playback_timeout_s,
                    verify_ms=args.startup_verify_ms,
                    fixed_config=fixed_config,
                )
                print(f"STEP [{mode}]: debug seek to passed start time {args.start_ms} ms")
                start_seek = call_dict(
                    client,
                    "dev_seek_time",
                    {
                        "target_ms": args.start_ms,
                        "tolerance_ms": args.seek_tolerance_ms,
                        "timeout_s": args.seek_timeout_s,
                        "stable_ms": args.seek_stable_ms,
                    },
                    timeout=max(30.0, args.seek_timeout_s + 15.0),
                )
                mode_result["startSeek"] = start_seek
                if not start_seek.get("passed"):
                    raise RuntimeError(f"debug seek to start time failed: {start_seek}")
                resumed = call_dict(
                    client,
                    "dev_wait_for_playback_started",
                    {"timeout_s": args.playback_timeout_s, "verify_ms": args.startup_verify_ms},
                    timeout=args.playback_timeout_s + 15.0,
                )
                mode_result["startSeekPlayback"] = resumed
                if not resumed.get("passed"):
                    raise RuntimeError(f"playback did not recover after debug start-time seek: {resumed}")
                print(f"PASS [{mode}]: debug start time stably reached target={args.start_ms} ms landed={start_seek.get('landed_ms')} ms current={start_seek.get('reached_ms')} ms stable={start_seek.get('stable_observed_ms')} ms")
                if args.before_comskip_ms > 0:
                    time.sleep(max(0, min(args.before_comskip_ms, 10000)) / 1000.0)

                for index, direction in enumerate(directions):
                    check = run_comskip_case(
                        client,
                        mode=mode,
                        direction=direction,
                        delay_ms=args.delay_ms,
                        settle_ms=args.settle_ms,
                        watchdog_ms=watchdog_ms,
                        verify_playback_ms=args.verify_playback_ms,
                        health_poll_ms=args.health_poll_ms,
                        slow_recovery_ms=args.slow_recovery_ms,
                    )
                    mode_result["checks"][direction] = check
                    # Media recovery outcome is observational. WATCHDOG_EXPIRED does not abort the matrix.
                    if index + 1 < len(directions) and args.between_directions_ms > 0:
                        time.sleep(max(0, min(args.between_directions_ms, 10000)) / 1000.0)
                mode_result["passed"] = len(mode_result["checks"]) == len(directions)
                mode_result["allRecovered"] = all(bool(x.get("recovered")) for x in mode_result["checks"].values())
                mode_result["watchdogExpired"] = any(bool(x.get("watchdog_expired")) for x in mode_result["checks"].values())
            except Exception as exc:
                failures += 1
                mode_result["passed"] = False
                mode_result["error"] = str(exc)
                print(f"FAIL [{mode}]: {exc}", file=sys.stderr)
                mode_result["checkpoint"] = safe_checkpoint(client, f"media3_{mode}_comskip_matrix_fail")

        comparison = comparison_table(results)
        report = {
            "passed": failures == 0 and all(bool(results[m].get("passed")) for m in modes),
            "player": "media3",
            "decoding": args.decoding,
            "appliedDecoding": decoding_preference(args.decoding),
            "modes": modes,
            "directions": directions,
            "server": args.server,
            "port": args.port,
            "startMs": args.start_ms,
            "seekToleranceMs": args.seek_tolerance_ms,
            "watchdogMs": watchdog_ms,
            "slowRecoveryMs": args.slow_recovery_ms,
            "markerMetadataAvailable": False,
            "causeAttribution": "undetermined_unless_external_evidence_proves_android_sagetv_server_or_ffmpeg",
            "verdictBasis": "observation_completion_separate_from_media_recovery_outcome",
            "comparison": comparison,
            "results": results,
        }
        path = write_report(report, args.report)

        print("\n=== MEDIA3 COMSKIP PUSH/PULL COMPARISON ===")
        print(json.dumps(comparison, indent=2, sort_keys=True))
        print(f"REPORT: {path}")
        if report["passed"]:
            if any(bool(results[m].get("watchdogExpired")) for m in modes):
                print("MEDIA3 PUSH/PULL COMSKIP MATRIX: COMPLETE (WATCHDOG OBSERVATIONS RECORDED)")
            elif not all(bool(results[m].get("allRecovered", False)) for m in modes):
                print("MEDIA3 PUSH/PULL COMSKIP MATRIX: COMPLETE (RECOVERY OUTCOMES RECORDED)")
            else:
                print("MEDIA3 PUSH/PULL COMSKIP MATRIX: PASS")
            return 0
        print(f"MEDIA3 PUSH/PULL COMSKIP MATRIX: FAIL ({failures} failures)", file=sys.stderr)
        return 1
    finally:
        if not args.leave_running:
            try:
                call_dict(client, "dev_exit_session", {"stop_app": True}, timeout=30.0)
            except Exception:
                pass
        client.close()


if __name__ == "__main__":
    raise SystemExit(main())
