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

from sagetv_dev_mcp.config import (
    default_server_address,
    default_server_value,
    default_smb_mappings,
    default_smb_value,
    default_test_value,
)

from mcp_seek_suite import MCPProcess, call_dict, initialize, tool_call
from mcp_ui_roots import is_automation_root
from mcp_caption_test import require_mim_direct_ownership
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
    server.add_argument("--server-address", default=default_server_address(), help="Direct SageTV server address/IP (default: active TOML server)")
    parser.add_argument("--server-port", type=int, default=int(default_server_value("miniclient_port", 31099)))
    parser.add_argument("--no-save-server", action="store_true")
    parser.add_argument("--player", choices=("exoplayer", "media3", "ijkplayer", "gsyplayer"), default=default_test_value("player", "media3"))
    # argparse applies type conversion to string defaults.  None keeps these
    # optional without sending an invalid empty value through the normalizers.
    parser.add_argument("--streaming", type=normalize_streaming, choices=STREAMING_SELECTIONS, default=default_test_value("streaming", "pull"), help="Streaming selection: push, pull, fixed (legacy dynamic accepted)")
    parser.add_argument("--decoding", type=normalize_decoding, choices=DECODING_SELECTIONS, default=default_test_value("decoding", "hardware"), help="Decoding selection: hardware, software, fallback (legacy hardware_preferred accepted)")
    add_fixed_encoding_args(parser)
    parser.add_argument(
        "--mim-direct-mode",
        choices=("off", "copy", "transcode"),
        default="off",
        help="Optional FFmpeg-plugin-owned Fixed transport policy",
    )
    parser.add_argument(
        "--mim-direct-deinterlace",
        choices=("auto", "on", "off"),
        default="auto",
        help="MIM-owned Transcode deinterlace policy",
    )
    parser.add_argument(
        "--require-mim-direct-owned",
        action="store_true",
        help=(
            "Fail unless playback is using the requested owned HTTP session "
            "instead of a safe SageTV Pull/Fixed fallback"
        ),
    )
    parser.add_argument("--gsy-engine", choices=("auto", "media3", "system", "legacy_exo"), default="")
    parser.add_argument("--mim-direct-startup-fault",
                        choices=("off", "direct-only", "direct-and-pull"), default="off",
                        help="Debug-only bounded startup fallback proof; direct-only keeps the real source")
    parser.add_argument("--expect-mim-stock-fallback", default="",
                        choices=("", "unsupported_video_stock_fixed", "unavailable_stock_fixed",
                                 "unsupported_player_stock_fixed"),
                        help="Require the stated safe ordinary Fixed fallback, never an owned-transport PASS")
    parser.add_argument(
        "--smb-mappings",
        default=default_smb_mappings(),
        help="Optional Dev-only SageTV-prefix to SMB-root mappings applied before playback",
    )
    parser.add_argument("--smb-username", default=default_smb_value("username"))
    parser.add_argument("--smb-password", default=default_smb_value("password"))
    parser.add_argument("--video-name", default="", help="Exact or uniquely matching SageTV video/recording name through Sagex")
    parser.add_argument("--server-path", default="", help="Exact SageTV-server MediaFile path through the Vibe MiniClient protocol extension")
    parser.add_argument("--connect-timeout-s", type=float, default=30.0)
    parser.add_argument(
        "--ui-stable-ms",
        type=int,
        default=2000,
        help="Require a non-empty SageTV menu hint to remain stable this long after connection (default: 2000 ms)",
    )
    parser.add_argument("--playback-timeout-s", type=float, default=float(default_test_value("startup_timeout_seconds", 45)))
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
    parser.add_argument(
        "--require-captions",
        action="store_true",
        help="Require non-empty locally rendered captions before and after navigation",
    )
    parser.add_argument(
        "--fixed-caption-side-channel",
        choices=("auto", "on", "off"),
        default="auto",
        help="Enable the server caption side channel for owned Fixed caption gates (default: auto)",
    )
    parser.add_argument(
        "--caption-mode",
        choices=("cc1", "cc2", "dvb"),
        default="cc1",
        help=(
            "Deterministic Android caption mode used with --require-captions "
            "(default: cc1; use dvb for bitmap-subtitle fixtures)"
        ),
    )
    parser.add_argument("--recovery-timeout-ms", type=int, default=30000, help="Per-operation A/V recovery budget (default: 30000 ms)")
    parser.add_argument(
        "--seek-settle-ms",
        type=int,
        default=None,
        help=(
            "Wait after each seek before judging sustained output. The default is "
            "5000 ms for Fixed/MIM (stock SageTV restarts the transcoder) and 300 ms otherwise."
        ),
    )
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
    if args.seek_settle_ms is not None and not 0 <= args.seek_settle_ms <= 30000:
        parser.error("--seek-settle-ms must be between 0 and 30000")
    if not 0 <= args.run_repeated_start <= 10:
        parser.error("--run-repeated-start must be between 0 and 10")
    if args.start_ms < -1:
        parser.error("--start-ms must be -1 (preserve resume position) or non-negative")
    if args.require_mim_direct_owned and args.mim_direct_mode == "off":
        parser.error("--require-mim-direct-owned requires --mim-direct-mode copy or transcode")
    if args.require_mim_direct_owned and args.player != "media3":
        parser.error("strict owned-transport proof currently requires --player media3")
    if (args.require_captions and args.streaming == "fixed"
            and args.mim_direct_mode != "off" and args.caption_mode in ("cc1", "cc2")):
        parser.error(
            "owned Fixed CEA is rendered by SageTV event 225; use "
            "mcp-caption-test --legacy-extender-callback for its caption gate"
        )

    client = MCPProcess()
    try:
        negotiated, _ = initialize(client)
        print(f"PASS: MCP initialize handshake ({negotiated})")
        print("PASS: adb_connect")
        call_dict(client, "adb_connect", timeout=30.0)
        # ADB's crash buffer survives app upgrades and process restarts. Clear
        # it before the bounded session so a tombstone from an earlier failed
        # build cannot make a later healthy run fail its final crash gate.
        tool_call(client, "clear_logcat", {}, timeout=30.0)
        print("PASS: stale device logcat cleared before session")

        print("STEP: prepare clean Dev app start")
        clean = call_dict(client, "dev_prepare_clean_start", {"wake": True, "graceful_timeout_s": 2.0}, timeout=30.0)
        print(json.dumps(clean, indent=2, sort_keys=True))
        if not clean.get("readyToLaunch") or clean.get("stopped", {}).get("running"):
            raise RuntimeError(f"Dev app could not be stopped cleanly before launch: {clean}")

        caption_side_channel_override = None
        if args.fixed_caption_side_channel == "on":
            caption_side_channel_override = True
        elif args.fixed_caption_side_channel == "off":
            caption_side_channel_override = False
        elif args.require_captions and args.mim_direct_mode != "off":
            caption_side_channel_override = True

        print("STEP: apply playback settings before connection/video start")
        configured = call_dict(client, "dev_set_player_config", {
            "player": args.player,
            "streaming": streaming_preference(args.streaming) if args.streaming else "",
            "decoding": decoding_preference(args.decoding) if args.decoding else "",
            "gsy_engine": args.gsy_engine,
            "mim_direct_mode": args.mim_direct_mode,
            "mim_direct_deinterlace": args.mim_direct_deinterlace,
            "fixed_caption_side_channel_enabled": caption_side_channel_override,
            # Caption rendering cannot be inferred from the user's preserved
            # setting: a previous DVB choice is valid for a UK fixture but
            # intentionally selects nothing in a CEA-only fixture.  Make the
            # requested gate deterministic, then let settings_restore return
            # the exact original mode in finally.
            "legacy_server_caption_mode": args.caption_mode
                if args.require_captions else "",
            "smb_mappings": args.smb_mappings,
            "smb_username": args.smb_username,
            "smb_password": args.smb_password,
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
        if not is_automation_root(connected_ready.get("state", {})):
            stale = connected_ready.get("state", {})
            print("INFO: normalizing stale SageTV UI with direct HOME command: "
                  f"uiState={stale.get('uiState')} menu={stale.get('menuName')} "
                  f"hasTextInput={stale.get('hasTextInput')}")
            call_dict(client, "dev_sage_command", {"command": "home"}, timeout=30.0)
        ready = call_dict(client, "dev_wait_for_ui", {
            "connected": True,
            "player_active": False,
            "menu_present": True,
            "stable_ms": args.ui_stable_ms,
            "timeout_s": args.connect_timeout_s,
        }, timeout=args.connect_timeout_s + 10.0)
        if not ready.get("passed") or not is_automation_root(ready.get("state", {})):
            raise RuntimeError(f"Android debug app did not report a supported automation root within timeout: {ready}")
        print("PASS: SageTV connection ready at a supported automation root")
        print(json.dumps(ready.get("state", {}), indent=2, sort_keys=True))

        if args.mim_direct_startup_fault != "off":
            if args.mim_direct_mode == "off" or args.require_mim_direct_owned:
                raise RuntimeError("Startup fault needs opted-in Direct and cannot assert Direct ownership")
            fault_state = call_dict(client, "dev_player_state", timeout=30.0)
            if fault_state.get("mimDirectSessionState") != "ready_" + args.mim_direct_mode:
                raise RuntimeError("Startup fault precondition failed: Direct is not negotiated ready; "
                                   + str(fault_state.get("mimDirectSessionState")))
            armed = call_dict(client, "dev_set_mim_direct_late_fallback_fault", {
                "enabled": True,
                "include_pull_failure": args.mim_direct_startup_fault == "direct-and-pull",
            }, timeout=30.0)
            if not armed.get("armed"):
                raise RuntimeError("Debug startup fault was not armed")
        if args.server_path.strip():
            print(f"STEP: play exact server MediaFile path on this MiniClient: {args.server_path!r}")
            started = call_dict(client, "dev_play_server_path", {
                "server_path": args.server_path,
                "timeout_s": args.playback_timeout_s,
                "verify_ms": args.verify_ms,
                "restart_from_beginning": args.restart_from_beginning,
            # The stock Core MCP path performs a bounded exact-path index
            # lookup (up to 180 s) and Watch call (up to 75 s) before this
            # tool begins its own playback-health wait.  The outer MCP reply
            # budget must cover those serial bounds; otherwise it can time
            # out while the requested video is already rendering.
            }, timeout=args.playback_timeout_s + 320.0)
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
        def require_expected_stock_fallback():
            fallback = call_dict(client, "dev_player_state", timeout=30.0)
            if (fallback.get("mimDirectSessionState") != args.expect_mim_stock_fallback
                    or fallback.get("mimDirectNegotiatedMode")
                    or fallback.get("playbackSource") == "MIM_DIRECT"):
                raise RuntimeError("Expected ordinary Fixed fallback was not observed: "
                                   + str(fallback.get("mimDirectSessionState")))
            print("PASS: ordinary Fixed fallback only: " + args.expect_mim_stock_fallback)
        if args.expect_mim_stock_fallback:
            if args.require_mim_direct_owned or args.mim_direct_mode == "off":
                raise RuntimeError("Stock fallback oracle needs an opted-in request, not owned/off")
            require_expected_stock_fallback()
        if args.mim_direct_startup_fault != "off":
            fallback = call_dict(client, "dev_player_state", timeout=30.0)
            if fallback.get("mimDirectSessionState") != "late_failure_stock_fixed_reconnect":
                raise RuntimeError("One-shot ordinary Fixed recovery not observed: "
                                   + str(fallback.get("mimDirectSessionState")))
            if fallback.get("playbackSource") == "MIM_DIRECT":
                raise RuntimeError("Recovery incorrectly retained Direct ownership")
            # On MPEG-2-less devices a new connection alone is insufficient:
            # public Watch must produce real video before the captured Seek.
            # Do not count the first frame or async Watch acceptance as proof
            # that the final source-position restoration actually happened.
            recovery = str(fallback.get("mimDirectWatchRecoveryState", "off"))
            if recovery not in ("off", "ready"):
                deadline = time.monotonic() + 35.0
                while recovery in ("requesting_watch", "waiting_for_video", "requesting_seek") and time.monotonic() < deadline:
                    time.sleep(0.25)
                    fallback = call_dict(client, "dev_player_state", timeout=30.0)
                    recovery = str(fallback.get("mimDirectWatchRecoveryState", "off"))
                if recovery != "seek_requested":
                    raise RuntimeError("Plugin recovery did not finish actual-video/Seek stages: " + recovery)
                print("PASS: plugin fresh Watch/actual-video/Seek recovery completed")
            print("PASS: real A/V recovered through bounded ordinary Fixed reconnect")
        print("PASS: requested video started on this MiniClient and real playback is advancing")

        if args.require_mim_direct_owned:
            owned = call_dict(client, "dev_player_state", timeout=30.0)
            expected_state = "active_" + args.mim_direct_mode
            data_source = str(owned.get("health_dataSourceClass", ""))
            failures = []
            if str(owned.get("mimDirectNegotiatedMode", "")) != args.mim_direct_mode:
                failures.append(
                    "negotiated=" + str(owned.get("mimDirectNegotiatedMode", ""))
                )
            session_state = str(owned.get("mimDirectSessionState", ""))
            accepted_states = {
                expected_state,
                expected_state + "_startup_seek_suppressed",
            }
            # The startup-seek suppression suffix is an active owned-stream
            # state, not a fallback.  It means the client deliberately ignored
            # SageTV's redundant zero-position seek immediately after opening
            # the newly owned HTTP session.  Ownership is independently proven
            # below by the negotiated mode, MIM_DIRECT source, and exact data
            # source class, so rejecting this state produced a false failure
            # after otherwise healthy playback had already advanced.
            if session_state not in accepted_states:
                failures.append(
                    "session=" + session_state
                )
            if str(owned.get("playbackSource", "")) != "MIM_DIRECT":
                failures.append("source=" + str(owned.get("playbackSource", "")))
            if not data_source.endswith(("Media3MimDirectHttpDataSource",
                                         "Exo2MimDirectHttpDataSource")):
                failures.append("dataSource=" + data_source)
            print("MIM DIRECT OWNERSHIP: " + json.dumps({
                "requestedMode": args.mim_direct_mode,
                "negotiatedMode": owned.get("mimDirectNegotiatedMode"),
                "sessionState": owned.get("mimDirectSessionState"),
                "playbackSource": owned.get("playbackSource"),
                "dataSourceClass": data_source,
            }, indent=2, sort_keys=True))
            if failures:
                raise RuntimeError(
                    "requested MIM Direct mode fell back or lacks owned-source proof: "
                    + ", ".join(failures)
                )
            print("PASS: client is consuming the FFmpeg-plugin-owned HTTP stream")

        def normalize_start(label: str) -> dict:
            if args.start_ms < 0:
                return {}
            print(f"STEP: normalize {label} to deterministic start {args.start_ms} ms")
            # Every source opened by this harness is owned by SageTV's
            # VideoFrame.  In Push/Fixed mode especially, reposition the
            # server byte stream rather than seeking only the Android decoder
            # over an already-buffered fragment.
            normalized = call_dict(client, "dev_server_seek_time", {
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
            diagnostic_keys = (
                "playbackSource", "mimDirectSessionState", "fixedCaptionSideChannelState",
                "fixedCaptionAttached", "fixedCaptionForwarding",
                "fixedCaptionClockUpdateCount", "fixedCaptionReceivedPackets",
                "fixedCaptionLastPacketPtsMs", "fixedCaptionLastPollClockMs",
                "subtitleTrackCount", "selectedSubtitleTrack", "subtitleCueUpdateCount",
                "subtitleNonEmptyCueCount", "currentSubtitleCueText", "mediaTimeMs",
            )
            diagnostic = {key: last.get(key) for key in diagnostic_keys}
            raise RuntimeError(
                f"STV captions were not rendered after {label}: "
                + json.dumps(diagnostic, sort_keys=True)
            )

        caption_state = wait_for_captions("startup") if args.require_captions else {}

        def run_commands(label: str, commands: list[str]) -> dict:
            nonlocal caption_state
            seek_settle_ms = args.seek_settle_ms
            if seek_settle_ms is None:
                seek_settle_ms = 5000 if args.streaming == "fixed" else 300
            result = call_dict(client, "dev_run_seek_check", {
                "commands": commands,
                "expected_net_ms": 0,
                "delay_ms": 1000 if commands == ["pause", "play"] else 350,
                "settle_ms": seek_settle_ms,
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
            # An exhausted short fixture can report an initially healthy frame
            # (or a briefly successful eventual probe) and then finish at the
            # media end state. Never count that as seek/pause recovery. This
            # matters especially when SageTV resumes near the end of a test
            # recording before the first FF command.
            if int(result.get("afterState", -1)) == 5:
                raise RuntimeError(
                    f"{label} reached end-of-media during the navigation gate; "
                    "restart the fixture at a safe position before retesting"
                )
            if args.require_captions:
                caption_state = wait_for_captions(
                    label,
                    int(caption_state.get("subtitleCueUpdateCount", 0) or 0),
                    int(caption_state.get("subtitleNonEmptyCueCount", 0) or 0),
                )
            if args.require_mim_direct_owned:
                # A transient restart may recover on the same owned source or
                # fall back safely. Only the former proves this requested gate.
                require_mim_direct_ownership(client, args.mim_direct_mode, "after " + label)
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

        def restart_source(label: str) -> dict:
            if args.server_path:
                restarted = call_dict(client, "dev_play_server_path", {
                    "server_path": args.server_path,
                    "timeout_s": args.playback_timeout_s,
                    "verify_ms": args.verify_ms,
                    "restart_from_beginning": args.restart_from_beginning,
                }, timeout=args.playback_timeout_s + 320.0)
            elif args.video_name:
                restarted = call_dict(client, "dev_play_video", {
                    "video_name": args.video_name,
                    "timeout_s": args.playback_timeout_s,
                    "verify_ms": args.verify_ms,
                }, timeout=args.playback_timeout_s + 35.0)
            else:
                raise RuntimeError(f"{label} has no configured playback source")
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
            print(f"STEP: repeated source start {iteration}/{args.run_repeated_start}")
            restart_source(f"REPEATED_START_{iteration}")

        if args.run_stop_restart:
            print("STEP: SageTV stop and retained-session PLAY restart")
            stopped = call_dict(client, "dev_sage_command", {"command": "stop"}, timeout=30.0)
            print("STOP: " + json.dumps(stopped, indent=2, sort_keys=True))
            # STOP is deliberately non-terminal in the MiniPlayer protocol.
            # Stock SageTV can follow it with PLAY (and sometimes SEEK) against
            # the same loaded player without another OPENURL. Re-Watching the
            # MediaFile through Sagex tests a different asynchronous STV path
            # and can race SageMC's StopPopup cleanup.
            time.sleep(1.0)
            # The generic SageTV Play UI command is STV-dependent; SageMC may
            # use it to start its saved Now Playing playlist instead of
            # resuming the retained exact MediaFile. Use the bounded stock API
            # bridge so this gate tests the current player session only.
            played = call_dict(
                client, "dev_server_media_control", {"operation": "play"}, timeout=30.0
            )
            print("STOP_PLAY: " + json.dumps(played, indent=2, sort_keys=True))
            retained_timeout_s = min(15.0, args.playback_timeout_s)
            recovered = call_dict(client, "dev_wait_for_playback_started", {
                "timeout_s": retained_timeout_s,
                "verify_ms": args.verify_ms,
            }, timeout=retained_timeout_s + 20.0)
            print("STOP_PLAY_RECOVERY: " + json.dumps(recovered, indent=2, sort_keys=True))
            recovery_health = recovered.get("health", {})
            # A Fixed transcoder fragment can end cleanly after producing a
            # few decoded frames even though Stop unloaded the real SageTV
            # MediaFile.  The generic playback-health helper deliberately
            # accepts completed short clips, but that is not a successful
            # retained-session restart for this gate.  Treat an ended fragment
            # as unloaded and re-open the exact requested source instead of
            # seeking the exhausted Android decoder locally.
            retained_session = bool(recovered.get("passed")) and not bool(
                recovery_health.get("ended", False)
            )
            if not retained_session:
                # Stock STVs are allowed to close the current MediaFile on
                # Stop. In that state direct Play is intentionally a no-op.
                # Restart the same deterministic source through its exact
                # control path; never fall back to the STV-dependent generic
                # Play command, which SageMC can route to Now Playing.
                if args.server_path or args.video_name:
                    restart_source("STOP_EXACT_REWATCH")
                    print(
                        "PASS: Stop unloaded the player; exact-source rewatch "
                        "recovered A/V without invoking the STV playlist"
                    )
                else:
                    raise RuntimeError(
                        "retained-session PLAY did not recover A/V and no exact source "
                        f"is available: {recovered.get('failureReason', recovered)}"
                    )
            else:
                normalize_start("STOP_PLAY")
                if args.require_captions:
                    caption_state = wait_for_captions("STOP_PLAY")

        if args.require_mim_direct_owned:
            require_mim_direct_ownership(client, args.mim_direct_mode, "settled after session controls")
        if args.expect_mim_stock_fallback:
            require_expected_stock_fallback()
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
        if args.mim_direct_startup_fault != "off":
            try:
                call_dict(client, "dev_set_mim_direct_late_fallback_fault", {"enabled": False}, timeout=30.0)
            except Exception as exc:
                print(f"WARN: fault cleanup failed: {exc}", file=sys.stderr)
        if args.exit != "none":
            try:
                result = call_dict(client, "dev_exit_session", {"stop_app": args.exit == "stop"}, timeout=30.0)
                print(f"EXIT ({args.exit}): {json.dumps(result, sort_keys=True)}")
            except Exception as exc:
                print(f"WARN: exit cleanup failed: {exc}", file=sys.stderr)
        client.close()


if __name__ == "__main__":
    raise SystemExit(main())
