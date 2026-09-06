#!/usr/bin/env python3
"""End-to-end SageTV MiniClient Dev session automation through MCP.

Workflow:
  launch app -> apply player settings -> connect server -> play MediaFile by name on this client ->
  verify real playback -> optional health tests -> exit.

The caller supplies a video/recording name only. MCP resolves the connected SageTV server API,
this MiniClient's UI context, the unique MediaFile, and invokes Watch on that context.
"""
from __future__ import annotations

import argparse
import json
import sys
import time

from mcp_seek_suite import MCPProcess, call_dict, initialize
from mcp_config_values import (
    DECODING_SELECTIONS,
    STREAMING_SELECTIONS,
    add_fixed_encoding_args,
    fixed_config_from_args,
    validate_fixed_config,
    decoding_preference,
    normalize_decoding,
    normalize_streaming,
    streaming_preference,
)


def main() -> int:
    parser = argparse.ArgumentParser(description="Automate a complete SageTV MiniClient Dev test session")
    server = parser.add_mutually_exclusive_group()
    server.add_argument("--server-name", default="", help="Saved MiniClient server name")
    server.add_argument("--server-address", default="", help="Direct SageTV server address/IP")
    parser.add_argument("--server-port", type=int, default=31099)
    parser.add_argument("--no-save-server", action="store_true")
    parser.add_argument("--player", choices=("exoplayer", "media3", "ijkplayer", "gsyplayer"), default="")
    # argparse applies type conversion to string defaults.  None keeps these
    # optional without sending an invalid empty value through the normalizers.
    parser.add_argument("--streaming", type=normalize_streaming, choices=STREAMING_SELECTIONS, default=None, help="Streaming selection: push, pull, fixed (legacy dynamic accepted)")
    parser.add_argument("--decoding", type=normalize_decoding, choices=DECODING_SELECTIONS, default=None, help="Decoding selection: hardware, software, fallback (legacy hardware_preferred accepted)")
    add_fixed_encoding_args(parser)
    parser.add_argument("--gsy-engine", choices=("auto", "media3", "system", "legacy_exo"), default="")
    parser.add_argument(
        "--smb-mappings",
        default="",
        help="Optional Dev-only SageTV-prefix to SMB-root mappings applied before playback",
    )
    parser.add_argument("--video-name", default="", help="Exact or uniquely matching SageTV video/recording name through Sagex")
    parser.add_argument("--server-path", default="", help="Exact SageTV-server MediaFile path through the Vibe MiniClient protocol extension")
    parser.add_argument("--connect-timeout-s", type=float, default=30.0)
    parser.add_argument(
        "--ui-stable-ms",
        type=int,
        default=2000,
        help="Require a non-empty SageTV menu hint to remain stable this long after connection (default: 2000 ms)",
    )
    parser.add_argument("--playback-timeout-s", type=float, default=45.0)
    parser.add_argument("--verify-ms", type=int, default=1500)
    parser.add_argument(
        "--start-ms",
        type=int,
        default=-1,
        help=(
            "After each exact-path start, seek the Android backend to this safe "
            "position before navigation checks; -1 preserves SageTV resume behavior"
        ),
    )
    parser.add_argument(
        "--restart-from-beginning",
        action="store_true",
        help=(
            "Choose Restart instead of the default Resume at SageTV's watched-file "
            "prompt; intended for deterministic fixture/matrix tests"
        ),
    )
    parser.add_argument("--run-seek-health", action="store_true", help="Run the basic single-FF/single-REW health checks after playback starts")
    parser.add_argument("--run-jump-health", action="store_true", help="Run SageTV's larger FF_2/REW_2 jump commands and require A/V recovery")
    parser.add_argument("--run-pause-health", action="store_true", help="Run SageTV pause/play and require A/V recovery")
    parser.add_argument("--run-repeated-start", type=int, default=0, help="Restart the exact recording this many times and require advancing A/V")
    parser.add_argument("--run-stop-restart", action="store_true", help="Stop through SageTV, restart the exact recording, and require advancing A/V")
    parser.add_argument("--require-captions", action="store_true", help="Require STV-selected, non-empty rendered captions before and after navigation")
    parser.add_argument("--recovery-timeout-ms", type=int, default=30000, help="Per-operation A/V recovery budget (default: 30000 ms)")
    parser.add_argument("--run-comskip", choices=("", "right", "left", "both"), default="")
    parser.add_argument("--exit", choices=("session", "stop", "none"), default="session")
    args = parser.parse_args()
    if not args.video_name.strip() and not args.server_path.strip():
        parser.error("one of --server-path or --video-name is required")
    fixed_config = fixed_config_from_args(args)
    try:
        validate_fixed_config(fixed_config)
    except ValueError as exc:
        parser.error(str(exc))
    if not 1000 <= args.recovery_timeout_ms <= 300000:
        parser.error("--recovery-timeout-ms must be between 1000 and 300000")
    if not 0 <= args.run_repeated_start <= 10:
        parser.error("--run-repeated-start must be between 0 and 10")
    if args.start_ms < -1:
        parser.error("--start-ms must be -1 (preserve resume position) or non-negative")

    client = MCPProcess()
    try:
        negotiated, _ = initialize(client)
        print(f"PASS: MCP initialize handshake ({negotiated})")
        print("PASS: adb_connect")
        call_dict(client, "adb_connect", timeout=30.0)

        print("STEP: prepare clean Dev app start")
        clean = call_dict(client, "dev_prepare_clean_start", {"wake": True, "graceful_timeout_s": 2.0}, timeout=30.0)
        print(json.dumps(clean, indent=2, sort_keys=True))
        if not clean.get("readyToLaunch") or clean.get("stopped", {}).get("running"):
            raise RuntimeError(f"Dev app could not be stopped cleanly before launch: {clean}")

        print("STEP: apply playback settings before connection/video start")
        configured = call_dict(client, "dev_set_player_config", {
            "player": args.player,
            "streaming": streaming_preference(args.streaming) if args.streaming else "",
            "decoding": decoding_preference(args.decoding) if args.decoding else "",
            "gsy_engine": args.gsy_engine,
            "smb_mappings": args.smb_mappings,
            **fixed_config,
        })
        print(json.dumps(configured, indent=2, sort_keys=True))

        print("STEP: connect SageTV server")
        connected = call_dict(client, "dev_connect_server", {
            "server_name": args.server_name,
            "address": args.server_address,
            "port": args.server_port,
            "save": not args.no_save_server,
        })
        print(json.dumps(connected, indent=2, sort_keys=True))
        connected_ready = call_dict(client, "dev_wait_for_ui", {
            "connected": True,
            "timeout_s": args.connect_timeout_s,
        }, timeout=args.connect_timeout_s + 10.0)
        if not connected_ready.get("passed"):
            raise RuntimeError(f"MiniClient did not connect within timeout: {connected_ready}")
        app_status = call_dict(client, "dev_app_status", {}, timeout=30.0)
        if not app_status.get("running"):
            raise RuntimeError(f"Dev app is not running after direct SageTV connect: {app_status}")
        print("PASS: Dev app running after direct connect: " + json.dumps(app_status, sort_keys=True))
        if not connected_ready.get("state", {}).get("automationReady", False):
            stale = connected_ready.get("state", {})
            print("INFO: normalizing stale SageTV UI with direct HOME command: "
                  f"uiState={stale.get('uiState')} menu={stale.get('menuName')} "
                  f"hasTextInput={stale.get('hasTextInput')}")
            call_dict(client, "dev_sage_command", {"command": "home"}, timeout=30.0)
        ready = call_dict(client, "dev_wait_for_ui", {
            "connected": True,
            "automation_ready": True,
            "stable_ms": args.ui_stable_ms,
            "timeout_s": args.connect_timeout_s,
        }, timeout=args.connect_timeout_s + 10.0)
        if not ready.get("passed"):
            raise RuntimeError(f"Android debug app did not report automationReady within timeout: {ready}")
        print("PASS: SageTV connection ready; Android debug automationReady=true")
        print(json.dumps(ready.get("state", {}), indent=2, sort_keys=True))

        if args.server_path.strip():
            print(f"STEP: play exact server MediaFile path on this MiniClient: {args.server_path!r}")
            started = call_dict(client, "dev_play_server_path", {
                "server_path": args.server_path,
                "timeout_s": args.playback_timeout_s,
                "verify_ms": args.verify_ms,
                "restart_from_beginning": args.restart_from_beginning,
            }, timeout=args.playback_timeout_s + 35.0)
        else:
            print(f"STEP: play video by name on this MiniClient: {args.video_name!r}")
            started = call_dict(client, "dev_play_video", {
                "video_name": args.video_name,
                "timeout_s": args.playback_timeout_s,
                "verify_ms": args.verify_ms,
            }, timeout=args.playback_timeout_s + 30.0)
        print(json.dumps(started, indent=2, sort_keys=True))
        if not started.get("passed"):
            raise RuntimeError(f"Video did not start with verified A/V playback: {started.get('reason')}")
        print("PASS: requested video started on this MiniClient and real playback is advancing")

        def normalize_start(label: str) -> dict:
            if args.start_ms < 0:
                return {}
            print(f"STEP: normalize {label} to deterministic start {args.start_ms} ms")
            normalized = call_dict(client, "dev_seek_time", {
                "target_ms": args.start_ms,
                "tolerance_ms": 5000,
                "timeout_s": args.recovery_timeout_ms / 1000.0,
                "stable_ms": 1200,
            }, timeout=args.recovery_timeout_ms / 1000.0 + 20.0)
            print(f"START_POSITION_{label}: " + json.dumps(normalized, indent=2, sort_keys=True))
            if not normalized.get("passed"):
                raise RuntimeError(
                    f"{label} could not reach deterministic start {args.start_ms} ms: "
                    f"{normalized.get('failureReason', normalized)}"
                )
            return normalized

        normalize_start("INITIAL")

        def wait_for_captions(
            label: str,
            previous_updates: int = -1,
            previous_non_empty: int = -1,
        ) -> dict:
            deadline = time.monotonic() + 45.0
            last = {}
            while time.monotonic() < deadline:
                last = call_dict(client, "dev_player_state", timeout=30.0)
                updates = int(last.get("subtitleCueUpdateCount", 0) or 0)
                non_empty = int(last.get("subtitleNonEmptyCueCount", 0) or 0)
                selected_track = last.get("selectedSubtitleTrack", -1)
                selected_track = -1 if selected_track is None else int(selected_track)
                if (int(last.get("subtitleTrackCount", 0) or 0) > 0
                        and selected_track >= 0
                        and non_empty > 0
                        and bool(last.get("subtitleOverlayAttached", False))
                        and (previous_updates < 0 or updates > previous_updates)
                        and (previous_non_empty < 0 or non_empty > previous_non_empty)):
                    print(
                        f"PASS: captions retained after {label}; updates={updates} "
                        f"nonEmpty={non_empty} current={last.get('currentSubtitleCueText')!r}"
                    )
                    return last
                time.sleep(0.25)
            raise RuntimeError(f"STV captions were not rendered after {label}: {last}")

        caption_state = wait_for_captions("startup") if args.require_captions else {}

        def run_commands(label: str, commands: list[str]) -> dict:
            nonlocal caption_state
            result = call_dict(client, "dev_run_seek_check", {
                "commands": commands,
                "expected_net_ms": 0,
                "delay_ms": 1000 if commands == ["pause", "play"] else 350,
                "recovery_timeout_ms": args.recovery_timeout_ms,
                "verify_playback_ms": args.verify_ms,
            }, timeout=args.recovery_timeout_ms / 1000.0 + 60.0)
            print(f"{label}: " + json.dumps(result, indent=2, sort_keys=True))
            if not result.get("passed"):
                # A Fixed/MIM restart can briefly satisfy the first-output
                # probe and then re-enter buffering while the replacement
                # stream establishes its new timestamp origin.  Preserve the
                # failed first probe, but allow the remainder of the same
                # recovery budget to prove sustained A/V instead of treating
                # that early sample as the final verdict.
                eventual = call_dict(client, "dev_wait_for_playback_started", {
                    "timeout_s": args.recovery_timeout_ms / 1000.0,
                    "verify_ms": args.verify_ms,
                    "expect_video": True,
                    "expect_audio": True,
                }, timeout=args.recovery_timeout_ms / 1000.0 + 20.0)
                result["initialHealthFailureReason"] = result.get("healthFailureReason", "")
                result["eventualRecovery"] = eventual
                if not eventual.get("passed"):
                    raise RuntimeError(
                        f"{label} playback health failed: "
                        f"{result.get('healthFailureReason', '')}; eventual recovery={eventual}"
                    )
                result["passed"] = True
                print(
                    f"PASS: {label} reached sustained A/V after a transient Fixed/MIM restart; "
                    f"initial={result.get('initialHealthFailureReason', '')}"
                )
            if args.require_captions:
                caption_state = wait_for_captions(
                    label,
                    int(caption_state.get("subtitleCueUpdateCount", 0) or 0),
                    int(caption_state.get("subtitleNonEmptyCueCount", 0) or 0),
                )
            return result

        if args.run_seek_health:
            print("STEP: basic seek health")
            for label, command in (("FF", "ff"), ("REW", "rew")):
                run_commands(label, [command])

        if args.run_jump_health:
            print("STEP: large jump health")
            for label, command in (("FF_2", "ff_2"), ("REW_2", "rew_2")):
                run_commands(label, [command])

        if args.run_pause_health:
            print("STEP: pause/play health")
            run_commands("PAUSE_PLAY", ["pause", "play"])

        if args.run_comskip:
            directions = [args.run_comskip] if args.run_comskip != "both" else ["right", "left"]
            print("STEP: Comskip health")
            for direction in directions:
                result = call_dict(client, "dev_run_comskip_check", {"direction": direction}, timeout=90.0)
                print(f"Comskip {direction}: " + json.dumps(result, indent=2, sort_keys=True))
                if not result.get("passed"):
                    raise RuntimeError(f"Comskip {direction} playback health failed")

        def restart_exact(label: str) -> dict:
            if not args.server_path.strip():
                raise RuntimeError(f"{label} requires --server-path")
            restarted = call_dict(client, "dev_play_server_path", {
                "server_path": args.server_path,
                "timeout_s": args.playback_timeout_s,
                "verify_ms": args.verify_ms,
                "restart_from_beginning": args.restart_from_beginning,
            }, timeout=args.playback_timeout_s + 35.0)
            print(f"{label}: " + json.dumps(restarted, indent=2, sort_keys=True))
            if not restarted.get("passed"):
                raise RuntimeError(f"{label} did not recover A/V: {restarted.get('reason')}")
            normalize_start(label)
            if args.require_captions:
                nonlocal_caption = wait_for_captions(label)
                caption_state.clear()
                caption_state.update(nonlocal_caption)
            return restarted

        for iteration in range(1, args.run_repeated_start + 1):
            print(f"STEP: repeated exact-path start {iteration}/{args.run_repeated_start}")
            restart_exact(f"REPEATED_START_{iteration}")

        if args.run_stop_restart:
            print("STEP: SageTV stop and exact-path restart")
            stopped = call_dict(client, "dev_sage_command", {"command": "stop"}, timeout=30.0)
            print("STOP: " + json.dumps(stopped, indent=2, sort_keys=True))
            time.sleep(1.0)
            restart_exact("STOP_RESTART")

        crash = call_dict(client, "dev_crash_probe", timeout=30.0)
        print("CRASH CHECK: " + json.dumps(crash, indent=2, sort_keys=True))
        if crash.get("signatureDetected"):
            raise RuntimeError(f"crash signature detected: {crash}")

        print("MCP SESSION AUTOMATION: PASS")
        return 0
    except Exception as exc:
        print(f"MCP SESSION AUTOMATION: FAIL\n{exc}", file=sys.stderr)
        return 1
    finally:
        if args.exit != "none":
            try:
                result = call_dict(client, "dev_exit_session", {"stop_app": args.exit == "stop"}, timeout=30.0)
                print(f"EXIT ({args.exit}): {json.dumps(result, sort_keys=True)}")
            except Exception as exc:
                print(f"WARN: exit cleanup failed: {exc}", file=sys.stderr)
        client.close()


if __name__ == "__main__":
    raise SystemExit(main())
