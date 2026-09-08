#!/usr/bin/env python3
"""Commission server-driven live TV and bounded channel changes on the Dev client."""
from __future__ import annotations

from sagetv_dev_mcp.config import default_server_address, default_test_value, default_server_value

import argparse
import json
import re
import sys
import time

from mcp_lifecycle_test import MCPProcess, call_dict, initialize, require, wait_automation_ready
from mcp_config_values import add_fixed_encoding_args, fixed_config_from_args, validate_fixed_config


def wait_for_av(client: MCPProcess, timeout_s: float, verify_ms: int) -> dict:
    result = call_dict(client, "dev_wait_for_playback_started", {
        "timeout_s": timeout_s,
        "verify_ms": verify_ms,
        "expect_video": True,
        "expect_audio": True,
    }, timeout=timeout_s + 20.0)
    require(bool(result.get("passed")), f"Live playback did not produce advancing A/V: {result}")
    return result


def parse_channels(value: str) -> list[str]:
    channels = [channel.strip() for channel in value.split(",") if channel.strip()]
    if not channels:
        raise argparse.ArgumentTypeError("at least one known-good channel is required")
    for channel in channels:
        if not re.fullmatch(r"[0-9]+(?:[.-][0-9]+)?", channel):
            raise argparse.ArgumentTypeError(f"invalid channel: {channel}")
    return channels


def tune_channel(client: MCPProcess, channel: str, timeout_s: float, verify_ms: int) -> dict:
    """Tune an exact dotted channel and require confirmed identity plus healthy A/V."""
    result = call_dict(client, "dev_set_live_channel", {
        "channel": channel,
        "timeout_s": timeout_s,
        "verify_ms": verify_ms,
    }, timeout=timeout_s * 2.0 + 30.0)
    require(bool(result.get("passed")), f"Live channel {channel} did not produce a new healthy media session: {result}")
    confirmed = str(result.get("confirmedChannel", ""))
    require(not confirmed or confirmed == channel,
            f"Live channel {channel} opened a different channel ({confirmed}): {result}")
    return result


def main() -> int:
    parser = argparse.ArgumentParser(description="Run the Android server-driven live-TV physical gate")
    parser.add_argument("--server-address", default=default_server_address())
    parser.add_argument("--server-port", type=int, default=int(default_server_value("miniclient_port", 31099)))
    parser.add_argument("--player", choices=("exoplayer", "media3", "ijkplayer", "gsyplayer"), default="media3")
    parser.add_argument("--gsy-engine", choices=("auto", "media3", "system", "legacy_exo"), default="auto")
    parser.add_argument(
        "--gsy-system-probe",
        action="store_true",
        help="Opt into the experimental native Android MediaPlayer engine when --gsy-engine=system",
    )
    parser.add_argument("--streaming", choices=("dynamic", "pull", "fixed"), default="pull")
    parser.add_argument("--decoding", choices=("hardware", "software", "hardware_preferred"), default="hardware")
    add_fixed_encoding_args(parser)
    configured_channels = ",".join(default_test_value("live_channels", ["2.1", "5.1"]))
    parser.add_argument("--channels", type=parse_channels, default=parse_channels(configured_channels),
                        help="Comma-separated known-good channels used for tuning (default: shared TOML)")
    parser.add_argument("--channel-changes", type=int, default=0)
    parser.add_argument(
        "--current-channel-only",
        action="store_true",
        help=(
            "Verify the channel selected by SageTV's normal Live TV command without "
            "using the Vibe-only direct-channel event. Use this for an unmodified server."
        ),
    )
    parser.add_argument("--timeout-s", type=float, default=60.0)
    parser.add_argument("--verify-ms", type=int, default=3000)
    parser.add_argument(
        "--verify-live-seek",
        action="store_true",
        help=(
            "Issue one server-owned FF and REW during growing Live TV, require "
            "movement in the requested direction, and require healthy A/V recovery"
        ),
    )
    parser.add_argument(
        "--live-seek-preroll-ms",
        type=int,
        default=12_000,
        help="Allow a new growing file to accumulate before the backward/forward seek gate",
    )
    parser.add_argument(
        "--verify-live-edge-clamp",
        action="store_true",
        help="Seek far beyond a growing stream and require Media3 buffered-edge clamping plus A/V recovery",
    )
    args = parser.parse_args()
    args.channel_changes = max(0, min(args.channel_changes, 10))
    if args.current_channel_only and args.channel_changes:
        parser.error("--current-channel-only cannot be combined with --channel-changes")
    fixed_config = fixed_config_from_args(args)
    try:
        validate_fixed_config(fixed_config)
    except ValueError as exc:
        parser.error(str(exc))

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
            "gsy_engine": args.gsy_engine,
            "gsy_system_probe": args.gsy_system_probe,
            **fixed_config,
        })
        call_dict(client, "dev_connect_server", {
            "address": args.server_address,
            "port": args.server_port,
            "save": False,
        })
        wait_automation_ready(client)

        sent = call_dict(client, "dev_sage_command", {"command": "live_tv"}, timeout=30.0)
        require(bool(sent.get("ok", True)), f"Live TV command was rejected: {sent}")
        active = call_dict(client, "dev_wait_for_ui", {
            "connected": True,
            "player_active": True,
            "timeout_s": min(args.timeout_s, 20.0),
        }, timeout=min(args.timeout_s, 20.0) + 10.0)
        require(bool(active.get("passed")), f"Live TV did not create an active media session: {active}")
        if args.current_channel_only:
            wait_for_av(client, args.timeout_s, args.verify_ms)
            print(
                "PASS: stock-compatible Live TV command produced advancing video and audio "
                "on SageTV's current channel"
            )
        else:
            # SageTV's Live TV command resumes the last tuned station, which may be
            # unavailable or outside the explicitly commissioned set. Make the
            # first tune deterministic before judging Fixed/MIM startup health.
            tune_channel(client, args.channels[0], args.timeout_s, args.verify_ms)
            print(
                "PASS: server-driven live TV produced advancing video and audio "
                f"on commissioned channel {args.channels[0]}"
            )

        # A backend can remain active and advance a clock behind an STV menu.
        # That is not a valid physical Live TV result (and a native backend
        # without renderer counters could otherwise produce a false PASS).
        # Require SageTV's full-screen playback destination, then recheck A/V
        # after any bounded promotion command.
        fullscreen = call_dict(
            client,
            "dev_ensure_fullscreen_playback",
            {"timeout_s": min(args.timeout_s, 12.0)},
            timeout=min(args.timeout_s, 12.0) + 10.0,
        )
        require(bool(fullscreen.get("passed")),
                f"Live TV backend was active but full-screen playback was not visible: {fullscreen}")
        wait_for_av(client, args.timeout_s, args.verify_ms)
        print("PASS: Live TV is visibly promoted to the stable full-screen playback destination")

        active_state = call_dict(client, "dev_player_state", timeout=30.0)
        if args.player == "gsyplayer" and args.gsy_engine == "system" and args.gsy_system_probe:
            backend_class = str(active_state.get("health_backendClass", ""))
            require("GSYSystemMediaPlayerImpl" in backend_class,
                    f"GSY System probe silently fell back before the seek gate: {active_state}")
            print(f"PASS: native GSY System backend is active ({backend_class})")

        if args.verify_live_edge_clamp:
            require(args.player in ("media3", "exoplayer"),
                    "Buffered live-edge commissioning currently supports Media3 and legacy Exo")
            call_dict(client, "dev_clear_player_events", timeout=30.0)
            requested_edge_ms = 86_400_000
            edge = call_dict(client, "dev_seek_time", {
                "target_ms": requested_edge_ms,
                "tolerance_ms": 2000,
                "timeout_s": min(60.0, args.timeout_s),
                "stable_ms": args.verify_ms,
            }, timeout=min(60.0, args.timeout_s) + 30.0)
            require(bool(edge.get("passed")), f"Live-edge seek did not recover healthy A/V: {edge}")
            events = call_dict(client, "dev_player_events", timeout=30.0)
            event_names = [str(item.get("event", "")) for item in events.get("events", [])]
            require(
                "seek_clamped_live_edge" in event_names,
                f"{args.player} did not report buffered live-edge clamping: events={event_names} edge={edge}",
            )
            reached_ms = int(edge.get("reached_ms", -1))
            require(0 <= reached_ms < requested_edge_ms,
                    f"Live-edge request was not bounded: requested={requested_edge_ms} reached={reached_ms}")
            print(
                f"PASS: {args.player} clamped an unknown-duration growing seek to its buffered live edge "
                f"and recovered hardware A/V (requested={requested_edge_ms} reached={reached_ms} "
                f"recoveryMs={edge.get('recoveryMs')})"
            )

        if args.verify_live_seek:
            time.sleep(max(0, min(args.live_seek_preroll_ms, 60_000)) / 1000.0)
            # Move backward first so the following FF is not rejected merely
            # because playback is already at the growing file's live edge.
            for command, expected_ms, direction in (("rew", -10_000, -1), ("ff", 30_000, 1)):
                result = call_dict(client, "dev_run_seek_check", {
                    "commands": [command],
                    "expected_net_ms": expected_ms,
                    "tolerance_ms": 30_000,
                    "recovery_timeout_ms": min(60_000, int(args.timeout_s * 1000)),
                    "verify_playback_ms": args.verify_ms,
                }, timeout=min(60.0, args.timeout_s) + 30.0)
                require(bool(result.get("passed")),
                        f"Growing Live TV {command.upper()} did not recover healthy A/V: {result}")
                observed_ms = int(result.get("observed_net_ms", 0))
                require(observed_ms * direction > 1000,
                        f"Growing Live TV {command.upper()} did not move in the requested direction: {result}")
                require(bool(result.get("serverSeekObserved", False)),
                        f"Growing Live TV {command.upper()} produced no server seek: {result}")
                print(
                    f"PASS: {args.player}/{args.gsy_engine} growing Live TV {command.upper()} "
                    f"moved {observed_ms} ms and recovered A/V"
                )

        for iteration in range(1, args.channel_changes + 1):
            channel = args.channels[iteration % len(args.channels)]
            tune_channel(client, channel, args.timeout_s, args.verify_ms)
            print(f"PASS: live channel change {iteration}/{args.channel_changes} to {channel}")

        crash = call_dict(client, "dev_crash_probe", timeout=30.0)
        require(not bool(crash.get("signatureDetected")), f"Crash signature detected: {crash}")
        print("ANDROID LIVE TV: PASS")
        return 0
    except Exception as exc:
        print(f"ANDROID LIVE TV: FAIL\n{exc}", file=sys.stderr)
        try:
            diagnostics = call_dict(client, "collect_playback_diagnostics", {
                "label": "live-tv-failure",
            }, timeout=90.0)
            print(json.dumps(diagnostics, indent=2, sort_keys=True), file=sys.stderr)
        except Exception as diagnostic_exc:
            print(f"WARN: live-TV diagnostics failed: {diagnostic_exc}", file=sys.stderr)
        return 1
    finally:
        try:
            call_dict(client, "dev_exit_session", {"stop_app": True}, timeout=30.0)
        except Exception:
            pass
        client.close()


if __name__ == "__main__":
    raise SystemExit(main())
