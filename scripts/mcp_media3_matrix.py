#!/usr/bin/env python3
"""Run the native Media3 Push vs Pull seek/resume matrix through MCP.

Each streaming mode is started as a fresh MiniClient session using the same native Search
sequence as ``mcp-playback-test``. Seek tests call the active Android player directly with
relative millisecond targets; no SageTV FF/REW command or Android key is simulated. PASS/FAIL
is based on the existing Android video/audio output-health probe.
The resulting JSON report is written under ``artifacts/firetv`` by default so Push and
Pull recovery latency can be compared without changing player code first.
"""
from __future__ import annotations

from sagetv_dev_mcp.config import default_server_address, default_server_value

import argparse
import json
import os
import sys
import time
from datetime import datetime
from pathlib import Path
from typing import Any

from mcp_playback_test import start_recording_via_search
from mcp_config_values import (
    DECODING_SELECTIONS,
    add_fixed_encoding_args,
    decoding_preference,
    fixed_config_from_args,
    normalize_decoding,
    normalize_streaming,
    streaming_preference,
    validate_fixed_config,
)
from mcp_seek_suite import MCPProcess, call_dict, initialize, print_result

DEFAULT_MODES = ("push", "pull")


def parse_modes(value: str) -> list[str]:
    requested = [item.strip() for item in str(value).split(",") if item.strip()]
    if not requested:
        raise ValueError("--modes must contain push and/or pull")
    result: list[str] = []
    for item in requested:
        normalized = normalize_streaming(item)
        if normalized not in DEFAULT_MODES:
            raise ValueError(f"unsupported Media3 matrix mode: {item}; use push or pull")
        if normalized not in result:
            result.append(normalized)
    return result


def safe_checkpoint(client: MCPProcess, label: str) -> dict[str, Any]:
    try:
        result = call_dict(client, "dev_test_checkpoint", {"label": label}, timeout=90.0)
        print("  diagnostics:", json.dumps(result.get("artifacts", {}), sort_keys=True))
        return result
    except Exception as exc:
        print(f"  WARN: diagnostics capture failed: {exc}", file=sys.stderr)
        return {"error": str(exc)}


def compact_seek_result(result: dict[str, Any]) -> dict[str, Any]:
    keys = (
        "passed", "verdict_basis", "deltas_ms", "inputPath", "recoveryMs", "timelineBeforeMs", "timelineAfterMs",
        "playbackAdjustedDeltaMs", "observed_net_ms", "timeline_within_tolerance", "videoRecovered",
        "audioRecovered", "stillPlaying", "videoStillAdvancing", "audioStillAdvancing", "bufferingSeen",
        "loadingSeen", "healthFailureReason", "playerErrorSeen", "firstPlayerError", "decoderChanged",
        "audioDecoderChanged", "final_videoDecoder", "final_videoDecoderKind", "final_audioDecoder",
        "final_audioDecoderKind", "final_surfaceValid", "final_audioTrackPlayState", "final_playbackState",
        "final_bufferedPositionMs", "final_playerPositionMs", "final_lastFileReadPos", "final_bufferLeft",
    )
    return {key: result.get(key) for key in keys if key in result}


def wait_for_state(client: MCPProcess, expected: int, timeout_s: float = 8.0) -> dict[str, Any]:
    deadline = time.monotonic() + timeout_s
    last: dict[str, Any] = {}
    while time.monotonic() < deadline:
        last = call_dict(client, "dev_player_state")
        if int(last.get("state", -1)) == expected:
            return last
        time.sleep(0.15)
    raise RuntimeError(f"player did not reach state={expected}; last state={last.get('state')}")


def run_seek_case(
    client: MCPProcess,
    *,
    mode: str,
    label: str,
    deltas_ms: list[int],
    delay_ms: int,
    settle_ms: int,
    recovery_timeout_ms: int,
    verify_playback_ms: int,
    health_poll_ms: int,
    slow_resume_ms: int,
) -> dict[str, Any]:
    print(f"STEP [{mode}]: {label}: direct player deltas={deltas_ms} ms")
    result = call_dict(
        client,
        "dev_run_relative_seek_check",
        {
            "deltas_ms": deltas_ms,
            "expected_net_ms": sum(deltas_ms),
            "tolerance_ms": 5000,
            "delay_ms": delay_ms,
            "settle_ms": settle_ms,
            "recovery_timeout_ms": recovery_timeout_ms,
            "verify_playback_ms": verify_playback_ms,
            "health_poll_ms": health_poll_ms,
        },
        timeout=max(60.0, recovery_timeout_ms / 1000.0 + verify_playback_ms / 1000.0 + 20.0),
    )
    passed = print_result(f"Media3 {mode} {label}", result)
    recovery_ms = result.get("recoveryMs")
    slow = False
    try:
        slow = passed and recovery_ms is not None and int(recovery_ms) > slow_resume_ms
    except (TypeError, ValueError):
        slow = False
    summary = compact_seek_result(result)
    summary["slowRecovery"] = slow
    summary["slowRecoveryThresholdMs"] = slow_resume_ms
    if not passed:
        summary["checkpoint"] = safe_checkpoint(client, f"media3_{mode}_{label}_fail")
    elif slow:
        print(f"WARN [{mode}]: {label} recovered in {recovery_ms} ms (> {slow_resume_ms} ms); capturing diagnostics")
        summary["checkpoint"] = safe_checkpoint(client, f"media3_{mode}_{label}_slow")
    return summary


def start_mode(
    client: MCPProcess,
    *,
    mode: str,
    server: str,
    port: int,
    decoder: str,
    search_text: str,
    text_char_delay_ms: int,
    connect_timeout_s: float,
    ui_stable_ms: int,
    playback_timeout_s: float,
    verify_ms: int,
    fixed_config: dict[str, Any],
) -> dict[str, Any]:
    print(f"\n=== MEDIA3 {mode.upper()} ===")
    clean = call_dict(
        client,
        "dev_prepare_clean_start",
        {"wake": True, "graceful_timeout_s": 2.0},
        timeout=30.0,
    )
    print("Clean start:", json.dumps(clean, sort_keys=True))
    if not clean.get("readyToLaunch") or clean.get("stopped", {}).get("running"):
        raise RuntimeError(f"Dev app could not be stopped cleanly before launch: {clean}")

    configured = call_dict(
        client,
        "dev_set_player_config",
        {"player": "media3", "streaming": streaming_preference(mode), "decoding": decoding_preference(decoder), "gsy_engine": "auto", **fixed_config},
    )
    print("Configured:", json.dumps(configured, sort_keys=True))

    connected = call_dict(
        client,
        "dev_connect_server",
        {"address": server, "port": port, "save": False},
        timeout=30.0,
    )
    print("Connected:", json.dumps(connected, sort_keys=True))

    connected_state = call_dict(
        client,
        "dev_wait_for_ui",
        {"connected": True, "timeout_s": connect_timeout_s},
        timeout=connect_timeout_s + 10.0,
    )
    if not connected_state.get("passed"):
        raise RuntimeError(f"SageTV did not connect before UI normalization: {connected_state}")

    app_status = call_dict(client, "dev_app_status", {}, timeout=30.0)
    if not app_status.get("running"):
        raise RuntimeError(f"Dev app is not running after direct SageTV connect: {app_status}")
    print(f"PASS [{mode}]: Dev app running after direct connect: " + json.dumps(app_status, sort_keys=True))

    # A fixed MiniClient client ID can reconnect to the server's previous UI context
    # (for example Search with the Android keyboard still active).  Trust the debug
    # APK's explicit automationReady state. If it is stale, use SageTV HOME to
    # normalize the server-side UI before any Search/test input is sent.
    if not connected_state.get("state", {}).get("automationReady", False):
        stale = connected_state.get("state", {})
        print(f"INFO [{mode}]: normalizing stale SageTV UI before automation: "
              f"uiState={stale.get('uiState')} menu={stale.get('menuName')} "
              f"hasTextInput={stale.get('hasTextInput')}")
        call_dict(client, "dev_sage_command", {"command": "home"}, timeout=30.0)

    ready = call_dict(
        client,
        "dev_wait_for_ui",
        {
            "connected": True,
            "automation_ready": True,
            "stable_ms": ui_stable_ms,
            "timeout_s": connect_timeout_s,
        },
        timeout=connect_timeout_s + 10.0,
    )
    if not ready.get("passed"):
        raise RuntimeError(f"Android debug app did not report automationReady before native Search: {ready}")
    state = ready.get("state", {})
    actual_server = str(state.get("serverAddress", "")).strip()
    if actual_server and actual_server != server:
        raise RuntimeError(f"connected to wrong SageTV server: expected {server}, got {actual_server}")
    print(f"PASS [{mode}]: Android debug status automationReady=true; starting native recording sequence")

    search_start = start_recording_via_search(client, search_text, text_char_delay_ms=text_char_delay_ms)
    print(f"PASS [{mode}]: Search opened, Android keyboard verified, text entered, and recording start commands sent")
    print("Search start:", json.dumps(search_start, sort_keys=True))

    playback = call_dict(
        client,
        "dev_wait_for_playback_started",
        {"timeout_s": playback_timeout_s, "verify_ms": verify_ms},
        timeout=playback_timeout_s + 15.0,
    )
    if not playback.get("passed"):
        raise RuntimeError(f"playback did not become healthy: {playback}")
    final_state = call_dict(client, "dev_player_state")
    if str(final_state.get("player", "")).lower() != "media3":
        raise RuntimeError(f"expected Media3 but active snapshot reports {final_state.get('player')!r}")
    expected_streaming = streaming_preference(mode)
    if str(final_state.get("streaming", "")).lower() != expected_streaming:
        raise RuntimeError(f"expected streaming={mode}->{expected_streaming} but snapshot reports {final_state.get('streaming')!r}")
    print(f"PASS [{mode}]: Media3 playback started with real A/V output")
    return {"ready": ready, "playback": playback, "state": final_state}


def pause_resume_case(
    client: MCPProcess,
    *,
    mode: str,
    playback_timeout_s: float,
    verify_ms: int,
    slow_resume_ms: int,
) -> dict[str, Any]:
    print(f"STEP [{mode}]: pause -> play output recovery")
    call_dict(client, "dev_player_control", {"action": "pause"})
    paused = wait_for_state(client, 3)
    time.sleep(0.5)

    started = time.monotonic()
    call_dict(client, "dev_player_control", {"action": "play"})
    playback = call_dict(
        client,
        "dev_wait_for_playback_started",
        {"timeout_s": playback_timeout_s, "verify_ms": verify_ms},
        timeout=playback_timeout_s + 15.0,
    )
    resume_ms = int(round((time.monotonic() - started) * 1000.0))
    passed = bool(playback.get("passed"))
    print(("PASS" if passed else "FAIL") + f": Media3 {mode} pause/resume output recovery={resume_ms} ms")
    result: dict[str, Any] = {
        "passed": passed,
        "resumeMs": resume_ms,
        "slowRecovery": passed and resume_ms > slow_resume_ms,
        "slowRecoveryThresholdMs": slow_resume_ms,
        "pausedState": paused,
        "playback": playback,
    }
    if not passed:
        result["checkpoint"] = safe_checkpoint(client, f"media3_{mode}_pause_resume_fail")
    elif resume_ms > slow_resume_ms:
        print(f"WARN [{mode}]: pause/resume took {resume_ms} ms (> {slow_resume_ms} ms); capturing diagnostics")
        result["checkpoint"] = safe_checkpoint(client, f"media3_{mode}_pause_resume_slow")
    return result


def recovery_table(mode_results: dict[str, dict[str, Any]]) -> dict[str, Any]:
    comparison: dict[str, Any] = {}
    for case in ("single_ff", "single_rew", "rapid_mixed"):
        row: dict[str, Any] = {}
        for mode in DEFAULT_MODES:
            value = mode_results.get(mode, {}).get("checks", {}).get(case, {}).get("recoveryMs")
            if value is not None:
                row[mode] = value
        if row:
            comparison[case] = row
    pause_row: dict[str, Any] = {}
    for mode in DEFAULT_MODES:
        value = mode_results.get(mode, {}).get("checks", {}).get("pause_resume", {}).get("resumeMs")
        if value is not None:
            pause_row[mode] = value
    if pause_row:
        comparison["pause_resume"] = pause_row
    return comparison


def write_report(report: dict[str, Any], requested_path: str) -> Path:
    if requested_path:
        path = Path(requested_path).expanduser()
    else:
        artifact_dir = Path(os.environ.get("SAGETV_ARTIFACT_DIR", Path(__file__).resolve().parents[1] / "artifacts" / "firetv"))
        stamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        path = artifact_dir / f"{stamp}_media3_push_pull_matrix.json"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(report, indent=2, sort_keys=True), encoding="utf-8")
    return path


def main() -> int:
    parser = argparse.ArgumentParser(description="Run fresh-session Media3 Push vs Pull direct-player seek/resume matrix")
    parser.add_argument("--server", default=default_server_address(), help="SageTV server IP/address")
    parser.add_argument("--port", type=int, default=int(default_server_value("miniclient_port", 31099)))
    parser.add_argument("--modes", default="push,pull", help="Comma-separated: push,pull (legacy dynamic alias accepted; default: both)")
    parser.add_argument("--decoding", "--decoder", dest="decoding", type=normalize_decoding, choices=DECODING_SELECTIONS, default="hardware", help="Decoding selection: hardware, software, fallback")
    add_fixed_encoding_args(parser)
    parser.add_argument("--text", required=True, help="Required native SageTV Search text for the test recording")
    parser.add_argument("--text-char-delay-ms", type=int, default=0, help="Text injection pacing: 0 uses MiniClient native key events; >0 enables legacy Android/ADB diagnostic pacing (ms)")
    parser.add_argument("--connect-timeout-s", type=float, default=30.0)
    parser.add_argument("--ui-stable-ms", type=int, default=2000)
    parser.add_argument("--playback-timeout-s", type=float, default=45.0)
    parser.add_argument("--startup-verify-ms", type=int, default=1500)
    parser.add_argument("--settle-ms", type=int, default=300)
    parser.add_argument("--paced-delay-ms", type=int, default=900)
    parser.add_argument("--rapid-delay-ms", type=int, default=350)
    parser.add_argument("--skip-forward-ms", type=int, default=10000, help="Direct Android-player forward seek size used by the matrix")
    parser.add_argument("--skip-backward-ms", type=int, default=10000, help="Direct Android-player backward seek size used by the matrix")
    parser.add_argument("--recovery-timeout-ms", type=int, default=8000)
    parser.add_argument("--verify-playback-ms", type=int, default=2500)
    parser.add_argument("--health-poll-ms", type=int, default=250)
    parser.add_argument("--slow-resume-ms", type=int, default=2000, help="Capture diagnostics when recovery exceeds this value; slow recovery is a warning, not an automatic FAIL")
    parser.add_argument("--report", default="", help="Optional JSON report path; default is artifacts/firetv/<timestamp>_media3_push_pull_matrix.json")
    parser.add_argument("--leave-running", action="store_true", help="Leave the final MiniClient session running")
    args = parser.parse_args()
    fixed_config = fixed_config_from_args(args)
    try:
        validate_fixed_config(fixed_config)
    except ValueError as exc:
        parser.error(str(exc))

    try:
        modes = parse_modes(args.modes)
        search_text = str(args.text).strip()
        if args.skip_forward_ms <= 0 or args.skip_backward_ms <= 0:
            raise ValueError("--skip-forward-ms and --skip-backward-ms must be > 0")
        if args.text_char_delay_ms < 0 or args.text_char_delay_ms > 2000:
            raise ValueError("--text-char-delay-ms must be between 0 and 2000")
    except ValueError as exc:
        parser.error(str(exc))

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
            except Exception as exc:
                failures += 1
                mode_result["passed"] = False
                mode_result["startupError"] = str(exc)
                print(f"FAIL [{mode}]: startup: {exc}", file=sys.stderr)
                mode_result["startupCheckpoint"] = safe_checkpoint(client, f"media3_{mode}_startup_fail")
                continue

            checks = mode_result["checks"]
            basic_failed = False
            for key, label, deltas_ms in (
                ("single_ff", "single direct forward seek", [args.skip_forward_ms]),
                ("single_rew", "single direct backward seek", [-args.skip_backward_ms]),
            ):
                try:
                    result = run_seek_case(
                        client,
                        mode=mode,
                        label=label,
                        deltas_ms=deltas_ms,
                        delay_ms=args.paced_delay_ms,
                        settle_ms=args.settle_ms,
                        recovery_timeout_ms=args.recovery_timeout_ms,
                        verify_playback_ms=args.verify_playback_ms,
                        health_poll_ms=args.health_poll_ms,
                        slow_resume_ms=args.slow_resume_ms,
                    )
                except Exception as exc:
                    result = {"passed": False, "error": str(exc), "checkpoint": safe_checkpoint(client, f"media3_{mode}_{key}_error")}
                    print(f"FAIL [{mode}]: {label}: {exc}", file=sys.stderr)
                checks[key] = result
                if not result.get("passed"):
                    failures += 1
                    basic_failed = True
                    break

            if basic_failed:
                checks["rapid_mixed"] = {"passed": False, "skipped": True, "reason": "basic_seek_failed"}
                checks["pause_resume"] = {"passed": False, "skipped": True, "reason": "basic_seek_failed"}
                mode_result["passed"] = False
                continue

            try:
                checks["rapid_mixed"] = run_seek_case(
                    client,
                    mode=mode,
                    label="rapid direct forward/forward/back/forward/back",
                    deltas_ms=[args.skip_forward_ms, args.skip_forward_ms, -args.skip_backward_ms, args.skip_forward_ms, -args.skip_backward_ms],
                    delay_ms=args.rapid_delay_ms,
                    settle_ms=args.settle_ms,
                    recovery_timeout_ms=args.recovery_timeout_ms,
                    verify_playback_ms=args.verify_playback_ms,
                    health_poll_ms=args.health_poll_ms,
                    slow_resume_ms=args.slow_resume_ms,
                )
            except Exception as exc:
                checks["rapid_mixed"] = {"passed": False, "error": str(exc), "checkpoint": safe_checkpoint(client, f"media3_{mode}_rapid_error")}
            if not checks["rapid_mixed"].get("passed"):
                failures += 1

            try:
                checks["pause_resume"] = pause_resume_case(
                    client,
                    mode=mode,
                    playback_timeout_s=args.playback_timeout_s,
                    verify_ms=args.startup_verify_ms,
                    slow_resume_ms=args.slow_resume_ms,
                )
            except Exception as exc:
                checks["pause_resume"] = {"passed": False, "error": str(exc), "checkpoint": safe_checkpoint(client, f"media3_{mode}_pause_resume_error")}
            if not checks["pause_resume"].get("passed"):
                failures += 1

            mode_result["passed"] = all(bool(item.get("passed")) for item in checks.values())

        comparison = recovery_table(results)
        report = {
            "schema": 1,
            "suite": "media3_push_pull_seek_resume_matrix",
            "server": args.server,
            "port": args.port,
            "decoding": args.decoding,
            "appliedDecoding": decoding_preference(args.decoding),
            "searchText": args.text,
            "textCharDelayMs": args.text_char_delay_ms,
            "seekControlPath": "android_debug_direct_player_seek",
            "pausePlayControlPath": "android_debug_direct_player_api",
            "skipForwardMs": args.skip_forward_ms,
            "skipBackwardMs": args.skip_backward_ms,
            "slowResumeThresholdMs": args.slow_resume_ms,
            "modes": modes,
            "results": results,
            "recoveryComparisonMs": comparison,
            "passed": failures == 0 and all(bool(results[mode].get("passed")) for mode in modes),
        }
        report_path = write_report(report, args.report)

        print("\n=== MEDIA3 PUSH/PULL RECOVERY COMPARISON ===")
        print(json.dumps(comparison, indent=2, sort_keys=True))
        print(f"REPORT: {report_path}")
        if report["passed"]:
            print("MEDIA3 PUSH/PULL SEEK-RESUME MATRIX: PASS")
            return 0
        print(f"MEDIA3 PUSH/PULL SEEK-RESUME MATRIX: FAIL ({failures} failed checks)", file=sys.stderr)
        return 1
    except Exception as exc:
        print(f"MEDIA3 PUSH/PULL SEEK-RESUME MATRIX: FAIL\n{exc}", file=sys.stderr)
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
