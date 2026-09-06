#!/usr/bin/env python3
"""Automated current-playback output-health regression suite through the real MCP server.

The user starts one known recording first. By default, PASS/FAIL is based on actual
video renderer output, Android AudioTrack advancement, surface validity, and sustained
playback after FF/REW stress. SageTV timeline deltas remain diagnostic only. Optional
--calibrate-timeline mode retains semantic +30/-10 testing when numeric timing matters.
"""
from __future__ import annotations

import argparse
import json
import sys
import time
from statistics import median
from typing import Any

from mcp_smoke_test import MCPProcess, initialize, tool_call


def structured(result: dict[str, Any]) -> dict[str, Any]:
    value = result.get("structuredContent")
    if isinstance(value, dict):
        return value
    content = result.get("content", [])
    if isinstance(content, list):
        for item in content:
            if isinstance(item, dict) and item.get("type") == "text":
                try:
                    parsed = json.loads(str(item.get("text", "")))
                except json.JSONDecodeError:
                    continue
                if isinstance(parsed, dict):
                    return parsed
    raise RuntimeError(f"Expected structured MCP result, got: {result!r}")


def call_dict(client: MCPProcess, name: str, arguments: dict[str, Any] | None = None, timeout: float = 30.0) -> dict[str, Any]:
    return structured(tool_call(client, name, arguments or {}, timeout=timeout))


def print_result(label: str, result: dict[str, Any]) -> bool:
    passed = bool(result.get("passed", False))
    status = "PASS" if passed else "FAIL"
    observed = result.get("observed_net_ms", result.get("playbackAdjustedDeltaMs", result.get("playback_adjusted_delta_ms")))
    expected = result.get("expected_net_ms")
    error = result.get("error_ms")
    commands = result.get("commands")
    basis = result.get("verdict_basis", "timeline")
    suffix = f" commands={commands}" if commands else ""
    print(f"{status}: {label}: output={basis} timelineObserved={observed} ms expected={expected} ms error={error} ms{suffix}")
    if result.get("healthCheckPerformed"):
        print(
            "  output health: "
            f"recovery={result.get('recoveryMs')} ms "
            f"videoRecovered={result.get('videoRecovered')} "
            f"audioRecovered={result.get('audioRecovered')} "
            f"stillPlaying={result.get('stillPlaying')} "
            f"videoStillAdvancing={result.get('videoStillAdvancing')} "
            f"audioStillAdvancing={result.get('audioStillAdvancing')} "
            f"bufferingSeen={result.get('bufferingSeen')} "
            f"loadingSeen={result.get('loadingSeen')} "
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
        print(
            "  output counters: "
            f"videoRecoveryFrames={result.get('videoRecoveryFrames')} "
            f"videoVerifyFrames={result.get('videoVerifyFrames')} "
            f"audioRecoveryBuffers={result.get('audioRecoveryBuffers')} "
            f"audioVerifyBuffers={result.get('audioVerifyBuffers')} "
            f"audioRecoveryHeadFrames={result.get('audioRecoveryHeadFrames')} "
            f"audioVerifyHeadFrames={result.get('audioVerifyHeadFrames')} "
            f"audioHeadResetRecovery={result.get('audioHeadResetDuringRecovery')} "
            f"audioHeadResetVerify={result.get('audioHeadResetDuringVerify')} "
            f"decoderChanged={result.get('decoderChanged')} "
            f"audioDecoderChanged={result.get('audioDecoderChanged')}"
        )
    if passed and result.get("timeline_within_tolerance") is False:
        print("  WARN: timeline landed outside numeric tolerance, but real video/audio output recovered and remained active")
    if result.get("playerErrorSeen"):
        print(f"  player error observed: {result.get('firstPlayerError')}")
    return passed


def checkpoint(client: MCPProcess, label: str) -> None:
    try:
        result = call_dict(client, "dev_test_checkpoint", {"label": label}, timeout=90.0)
        print("  diagnostics:", json.dumps(result.get("artifacts", {}), sort_keys=True))
    except Exception as exc:
        print(f"  diagnostics capture failed: {exc}", file=sys.stderr)


def snap_skip_ms(observed_ms: int, quantum_ms: int = 5000) -> int:
    """Infer configured skip intent without learning small player landing errors.

    A coarse default quantum keeps an observed +8.8/-11.1 second pair from becoming
    a bogus +9/-11 calibration when the configured primary skip is actually about 10s.
    Users with unusual intervals can use --ff-ms/--rew-ms or lower the quantum.
    """
    if observed_ms == 0:
        return 0
    if quantum_ms <= 0:
        raise ValueError("quantum_ms must be > 0")
    return int(round(observed_ms / float(quantum_ms))) * quantum_ms


def select_calibration_value(observations_ms: list[int], expected_sign: int, quantum_ms: int) -> tuple[int, int, list[int]]:
    """Choose a robust configured skip value from same-direction calibration samples."""
    valid = [
        int(value)
        for value in observations_ms
        if value != 0 and (1 if value > 0 else -1) == expected_sign
    ]
    if not valid:
        raise RuntimeError(f"No valid {'forward' if expected_sign > 0 else 'reverse'} calibration samples: {observations_ms}")
    center = int(round(float(median(valid))))
    snapped = snap_skip_ms(center, quantum_ms)
    if snapped == 0 or (1 if snapped > 0 else -1) != expected_sign:
        raise RuntimeError(
            f"Unable to infer configured skip from samples {valid}: median={center} ms snapped={snapped} ms"
        )
    return snapped, center, valid


def wait_for_state(client: MCPProcess, target_state: int, timeout_s: float = 8.0) -> dict[str, Any]:
    deadline = time.monotonic() + timeout_s
    last: dict[str, Any] = {}
    while time.monotonic() < deadline:
        last = call_dict(client, "dev_player_state")
        if int(last.get("state", -1)) == target_state:
            return last
        time.sleep(0.15)
    raise RuntimeError(f"Player did not reach state={target_state}; last state={last.get('state')}")


def calibrate_command(
    client: MCPProcess,
    command: str,
    expected_sign: int,
    settle_ms: int,
    quantum_ms: int,
    sample_count: int = 3,
    max_attempts: int = 6,
) -> tuple[int, dict[str, Any]]:
    """Calibrate one primary skip while playback is paused.

    Multiple samples are used because a delayed seek can otherwise contaminate the next
    measurement. Wrong-direction and zero samples are retained for diagnostics but rejected
    from the median used to infer the configured SageTV skip interval.
    """
    attempts: list[int] = []
    results: list[dict[str, Any]] = []
    valid: list[int] = []
    for _ in range(max(1, max_attempts)):
        result = call_dict(
            client,
            "dev_run_seek_check",
            {
                "commands": [command],
                "expected_net_ms": 0,
                "tolerance_ms": 2_000_000_000,
                "delay_ms": 0,
                "settle_ms": settle_ms,
            },
            timeout=60.0,
        )
        observed = int(result.get("timelineDeltaMs", result.get("observed_net_ms", 0)))
        attempts.append(observed)
        results.append(result)
        if observed != 0 and (1 if observed > 0 else -1) == expected_sign:
            valid.append(observed)
            if len(valid) >= max(1, sample_count):
                break

    if len(valid) < max(1, sample_count):
        raise RuntimeError(
            f"Unable to calibrate SageTV {command!r}: valid samples={valid}, all attempts={attempts}"
        )

    snapped, center, selected = select_calibration_value(valid, expected_sign, quantum_ms)
    summary = dict(results[-1] if results else {})
    summary.update({
        "samples_ms": selected,
        "all_attempts_ms": attempts,
        "median_observed_ms": center,
        "playbackAdjustedDeltaMs": center,
        "observed_net_ms": center,
    })
    return snapped, summary


def commands_for_delta(target_ms: int, ff_ms: int, rew_ms: int) -> tuple[list[str], int]:
    """Return the closest practical same-direction SageTV primary-skip plan.

    SageTV primary FF/REW values are user configurable and are not necessarily symmetric
    or exact divisors of the semantic test steps.  The harness must validate the player
    against the commands it can actually send rather than aborting because (for example)
    a +9 second FF cannot exactly represent +30 seconds.
    """
    if target_ms == 0:
        return [], 0
    command = "ff" if target_ms > 0 else "rew"
    unit = abs(ff_ms if target_ms > 0 else rew_ms)
    if unit <= 0:
        raise RuntimeError(f"Invalid calibrated {command} increment: {unit} ms")

    count = max(1, int(round(abs(target_ms) / float(unit))))
    represented = count * unit * (1 if target_ms > 0 else -1)
    return [command] * count, represented


def main() -> int:
    parser = argparse.ArgumentParser(description="Run MCP playback-health checks against the currently playing SageTV recording")
    parser.add_argument("--tolerance-ms", type=int, default=5000, help="Timeline tolerance when optional calibration is enabled")
    parser.add_argument("--delay-ms", type=int, default=350, help="Inter-command delay for the rapid output-health stress sequence")
    parser.add_argument("--paced-delay-ms", type=int, default=900, help="Inter-command delay for normal FF/REW checks")
    parser.add_argument("--settle-ms", type=int, default=300, help="Short post-command delay before output-health polling")
    parser.add_argument("--recovery-timeout-ms", type=int, default=8000, help="Maximum time for video/audio output to resume after a skip")
    parser.add_argument("--verify-playback-ms", type=int, default=2500, help="After recovery, require video/audio to keep advancing for this long")
    parser.add_argument("--health-poll-ms", type=int, default=250, help="Output-health polling interval")
    parser.add_argument("--calibrate-timeline", action="store_true", help="Optionally pause and calibrate SageTV FF/REW distances for timeline diagnostics")
    parser.add_argument("--calibrate-settle-ms", type=int, default=1800)
    parser.add_argument("--calibration-samples", type=int, default=3, help="Valid paused samples required for optional FF/REW timeline calibration")
    parser.add_argument("--calibration-quantum-ms", type=int, default=5000, help="Round optional FF/REW calibration to this preference quantum")
    parser.add_argument("--ff-ms", type=int, default=0, help="Override primary FF distance for optional semantic timeline tests")
    parser.add_argument("--rew-ms", type=int, default=0, help="Override primary REW magnitude for optional semantic timeline tests")
    parser.add_argument("--large-command", default="", help="Optional SageCommand key for the user's comskip/large-skip action")
    parser.add_argument("--large-expected-ms", type=int, default=None, help="Optional expected large-skip timeline distance; output health remains authoritative")
    parser.add_argument("--rapid-only", action="store_true", help="Skip single FF/REW and pause/resume checks; run only the rapid output-health stress")
    parser.add_argument("--rapid-repeats", type=int, default=1, help="Repeat the rapid output-health stress this many times")
    parser.add_argument("--rapid-reset-ms", type=int, default=None, help="Before each rapid repeat, use the debug seek control to recover at this absolute fixture time")
    args = parser.parse_args()
    if args.rapid_repeats < 1:
        parser.error("--rapid-repeats must be >= 1")
    if args.rapid_reset_ms is not None and args.rapid_reset_ms < 0:
        parser.error("--rapid-reset-ms must be >= 0")

    client = MCPProcess()
    failures = 0
    try:
        negotiated, _ = initialize(client)
        print(f"PASS: MCP initialize handshake ({negotiated})")
        call_dict(client, "adb_connect", timeout=30.0)

        state = call_dict(client, "dev_player_state")
        print("Current player state:")
        print(json.dumps(state, indent=2, sort_keys=True))
        if not state.get("connected") or not state.get("playerActive"):
            raise RuntimeError("Start a known test recording before running the automated playback-health suite")
        if int(state.get("state", -1)) != 2:
            raise RuntimeError(f"Start playback before running the suite; current state={state.get('state')}")

        timeline_calibration = args.calibrate_timeline or args.ff_ms > 0 or args.rew_ms > 0
        ff_ms = int(args.ff_ms) if args.ff_ms > 0 else 10_000
        rew_ms = -abs(int(args.rew_ms)) if args.rew_ms > 0 else -10_000
        ff_cal: dict[str, Any] = {"override": args.ff_ms > 0}
        rew_cal: dict[str, Any] = {"override": args.rew_ms > 0}

        if timeline_calibration and (args.ff_ms <= 0 or args.rew_ms <= 0):
            call_dict(client, "dev_sage_command", {"command": "pause"})
            wait_for_state(client, 3)
            print("Optional timeline calibration: playback paused to measure configured FF/REW distance")
            try:
                if args.ff_ms <= 0:
                    ff_ms, ff_cal = calibrate_command(
                        client, "ff", +1, args.calibrate_settle_ms,
                        args.calibration_quantum_ms, args.calibration_samples
                    )
                if args.rew_ms <= 0:
                    rew_ms, rew_cal = calibrate_command(
                        client, "rew", -1, args.calibrate_settle_ms,
                        args.calibration_quantum_ms, args.calibration_samples
                    )
            finally:
                call_dict(client, "dev_sage_command", {"command": "play"})
                wait_for_state(client, 2)

            print(
                "SageTV timeline calibration: "
                f"ff={ff_ms:+d} ms (median={ff_cal.get('median_observed_ms', 'override')}, samples={ff_cal.get('samples_ms', 'override')}), "
                f"rew={rew_ms:+d} ms (median={rew_cal.get('median_observed_ms', 'override')}, samples={rew_cal.get('samples_ms', 'override')})"
            )
        else:
            print("Timeline calibration skipped: PASS/FAIL is based on real video/audio output recovery; timeline deltas are informational only.")

        if timeline_calibration:
            plus30_cmds, plus30_expected = commands_for_delta(30_000, ff_ms, rew_ms)
            minus10_cmds, minus10_expected = commands_for_delta(-10_000, ff_ms, rew_ms)
            normal_checks = [
                ("skip +30", plus30_cmds, plus30_expected),
                ("skip -10", minus10_cmds, minus10_expected),
            ]
            rapid_targets = [30_000, 30_000, -10_000, 60_000, -30_000]
            rapid_commands: list[str] = []
            rapid_expected = 0
            expansion: list[dict[str, Any]] = []
            for target in rapid_targets:
                commands, represented = commands_for_delta(target, ff_ms, rew_ms)
                rapid_commands.extend(commands)
                rapid_expected += represented
                expansion.append({"target_ms": target, "commands": commands, "represented_ms": represented})
            print("Rapid semantic expansion:", json.dumps(expansion, separators=(",", ":")))
            rapid_label = "rapid semantic +30,+30,-10,+60,-30"
        else:
            # Default: exercise the same kind of pressure without trusting configured skip distance.
            normal_checks = [
                ("single FF", ["ff"], 0),
                ("single REW", ["rew"], 0),
            ]
            rapid_commands = [
                "ff", "ff", "ff", "ff", "ff", "ff", "rew",
                "ff", "ff", "ff", "ff", "ff", "ff", "rew", "rew", "rew",
            ]
            rapid_expected = 0
            rapid_label = "rapid mixed FF/REW output-health stress"

        basic_health_failed = False
        for label, commands, expected in ([] if args.rapid_only else normal_checks):
            print(f"Plan: {label}: commands={commands} timelineExpected={expected if timeline_calibration else 'INFO only'}")
            result = call_dict(
                client,
                "dev_run_seek_check",
                {
                    "commands": commands,
                    "expected_net_ms": expected,
                    "tolerance_ms": args.tolerance_ms if timeline_calibration else 2_000_000_000,
                    "delay_ms": args.paced_delay_ms,
                    "settle_ms": args.settle_ms,
                    "recovery_timeout_ms": args.recovery_timeout_ms,
                    "verify_playback_ms": args.verify_playback_ms,
                    "health_poll_ms": args.health_poll_ms,
                },
                timeout=60.0,
            )
            if not print_result(label, result):
                failures += 1
                basic_health_failed = True
                checkpoint(client, "mcp_" + label.replace(" ", "_").replace(",", ""))

        if basic_health_failed:
            print("SKIP: rapid output-health stress and pause/resume because a basic FF/REW recovery check failed; preserving the failing playback state for diagnostics")
            final = call_dict(client, "dev_player_state")
            print("Final player state:")
            print(json.dumps(final, indent=2, sort_keys=True))
            print(f"MCP PLAYBACK HEALTH SUITE: FAIL ({failures} checks)", file=sys.stderr)
            return 1

        rapid_recoveries: list[int] = []
        for repeat_index in range(1, args.rapid_repeats + 1):
            if args.rapid_reset_ms is not None:
                reset = call_dict(
                    client,
                    "dev_seek_time",
                    {
                        "target_ms": args.rapid_reset_ms,
                        "tolerance_ms": args.tolerance_ms,
                        "timeout_s": max(15.0, args.recovery_timeout_ms / 1000.0),
                        "stable_ms": max(1200, args.verify_playback_ms),
                    },
                    timeout=max(30.0, args.recovery_timeout_ms / 1000.0 + 20.0),
                )
                if not reset.get("passed"):
                    failures += 1
                    print(f"FAIL: rapid repeat {repeat_index} reset to {args.rapid_reset_ms} ms did not recover")
                    checkpoint(client, f"mcp_rapid_reset_{repeat_index}_fail")
                    break
                print(
                    f"PASS: rapid repeat {repeat_index} reset: target={args.rapid_reset_ms} ms "
                    f"reached={reset.get('reached_ms')} ms recovery={reset.get('recoveryMs')} ms"
                )

            print(
                f"Rapid output-health stress repeat {repeat_index}/{args.rapid_repeats}: commands={len(rapid_commands)} "
                + (f"timelineExpected={rapid_expected:+d} ms" if timeline_calibration else "timelineExpected=INFO only")
            )
            rapid_result = call_dict(
                client,
                "dev_run_seek_check",
                {
                    "commands": rapid_commands,
                    "expected_net_ms": rapid_expected,
                    "tolerance_ms": args.tolerance_ms if timeline_calibration else 2_000_000_000,
                    "delay_ms": args.delay_ms,
                    "settle_ms": args.settle_ms,
                    "recovery_timeout_ms": args.recovery_timeout_ms,
                    "verify_playback_ms": args.verify_playback_ms,
                    "health_poll_ms": args.health_poll_ms,
                },
                timeout=90.0,
            )
            repeat_label = rapid_label if args.rapid_repeats == 1 else f"{rapid_label} repeat {repeat_index}"
            if print_result(repeat_label, rapid_result):
                rapid_recoveries.append(int(rapid_result.get("recoveryMs", -1)))
            else:
                failures += 1
                checkpoint(client, f"mcp_rapid_output_health_repeat_{repeat_index}")
                break

        if rapid_recoveries:
            print(
                "Rapid stress recovery summary: "
                f"passes={len(rapid_recoveries)}/{args.rapid_repeats} "
                f"min={min(rapid_recoveries)} ms median={int(median(rapid_recoveries))} ms "
                f"max={max(rapid_recoveries)} ms"
            )

        # Keep the simple transport state check for pause/resume. The skip checks above are the
        # authoritative video/audio output checks; pause intentionally stops output.
        if not args.rapid_only:
            call_dict(client, "dev_sage_command", {"command": "pause"})
            time.sleep(0.75)
            paused = call_dict(client, "dev_player_state")
            pause_ok = int(paused.get("state", -1)) == 3
            print(("PASS" if pause_ok else "FAIL") + f": pause state={paused.get('state')}")
            if not pause_ok:
                failures += 1
                checkpoint(client, "mcp_pause_fail")

            call_dict(client, "dev_sage_command", {"command": "play"})
            time.sleep(0.75)
            resumed = call_dict(client, "dev_player_state")
            play_ok = int(resumed.get("state", -1)) == 2
            print(("PASS" if play_ok else "FAIL") + f": resume state={resumed.get('state')}")
            if not play_ok:
                failures += 1
                checkpoint(client, "mcp_resume_fail")

        if args.large_command:
            large_expected = args.large_expected_ms if args.large_expected_ms is not None else 0
            large_tolerance = args.tolerance_ms if args.large_expected_ms is not None else 2_000_000_000
            print("Large/comskip: timeline distance is diagnostic; video/audio output recovery is authoritative")
            result = call_dict(
                client,
                "dev_run_seek_check",
                {
                    "commands": [args.large_command],
                    "expected_net_ms": large_expected,
                    "tolerance_ms": large_tolerance,
                    "delay_ms": args.delay_ms,
                    "settle_ms": args.settle_ms,
                    "recovery_timeout_ms": args.recovery_timeout_ms,
                    "verify_playback_ms": args.verify_playback_ms,
                    "health_poll_ms": args.health_poll_ms,
                },
                timeout=60.0,
            )
            if not print_result("large/comskip", result):
                failures += 1
                checkpoint(client, "mcp_large_skip_fail")

        final = call_dict(client, "dev_player_state")
        print("Final player state:")
        print(json.dumps(final, indent=2, sort_keys=True))
        if failures:
            print(f"MCP PLAYBACK HEALTH SUITE: FAIL ({failures} checks)", file=sys.stderr)
            return 1
        print("MCP PLAYBACK HEALTH SUITE: PASS")
        return 0
    except Exception as exc:
        print(f"MCP PLAYBACK HEALTH SUITE: FAIL\n{exc}", file=sys.stderr)
        return 1
    finally:
        client.close()


if __name__ == "__main__":
    raise SystemExit(main())
