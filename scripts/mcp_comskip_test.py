#!/usr/bin/env python3
"""MCP dedicated Android debug Comskip playback-health test.

This uses the debug APK's dedicated Comskip operation. It does not inject an Android key,
resolve the normal video-playing arrow mapping, or simulate a long press. The MiniClient does not receive semantic Comskip marker
start/end metadata from the SageTV STV, so the test records the actual recovery landing.
"""
from __future__ import annotations

import argparse
import json
import sys
from typing import Any

from mcp_seek_suite import MCPProcess, call_dict, checkpoint, initialize


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
    passed = bool(result.get("passed", False))
    print(("PASS" if passed else "FAIL") + f": Comskip SageTV command {direction}")
    print(
        "  debug-direct Comskip: "
        f"direction={result.get('arrowDirection')} "
        f"command={result.get('directSageCommand') or result.get('resolvedArrowCommand')} "
        f"markerMetadataAvailable={result.get('comskipMarkerDataAvailable')}"
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
    args = parser.parse_args()

    directions = [args.direction] if args.direction != "both" else ["right", "left"]
    if args.direction == "both" and args.expected_target_ms >= 0:
        parser.error("--expected-target-ms is only valid with a single --direction")

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
            raise RuntimeError("Start a recording with Comskip markers before running this test")
        if int(state.get("state", -1)) != 2:
            raise RuntimeError(f"Start playback before running the Comskip test; current state={state.get('state')}")

        for direction in directions:
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
        client.close()


if __name__ == "__main__":
    raise SystemExit(main())
