#!/usr/bin/env python3
"""Run bounded remote-DVD startup/STOP/crash gates through the real MCP path."""
from __future__ import annotations

from sagetv_dev_mcp.config import default_server_address, default_server_value

import argparse
import json
import re
import sys
import time
from pathlib import Path

from mcp_lifecycle_test import MCPProcess, call_dict, initialize, require, wait_automation_ready


def safe_label(path: str) -> str:
    label = re.sub(r"[^A-Za-z0-9._-]+", "-", path.rstrip("/\\").split("/")[-1])
    return label.strip("-")[:64] or "dvd"


def wait_stopped(client: MCPProcess, timeout_s: float = 20.0) -> dict:
    deadline = time.monotonic() + timeout_s
    last: dict = {}
    while time.monotonic() < deadline:
        last = call_dict(client, "dev_player_state", timeout=30.0)
        if not bool(last.get("playerActive")):
            return last
        time.sleep(0.25)
    raise RuntimeError(f"DVD did not stop within {timeout_s:.1f}s: {last}")


def wait_player_state(client: MCPProcess, expected_state: int,
                      timeout_s: float = 15.0) -> tuple[dict, int]:
    """Wait for an asynchronous SageTV control command to reach Android."""
    started = time.monotonic()
    deadline = started + timeout_s
    last: dict = {}
    while time.monotonic() < deadline:
        last = call_dict(client, "dev_player_state", timeout=30.0)
        if int(last.get("state", -1)) == expected_state:
            return last, int((time.monotonic() - started) * 1000)
        if not bool(last.get("playerActive")) or str(last.get("playerError", "")).strip():
            break
        time.sleep(0.1)
    raise RuntimeError(
        f"DVD did not enter player state {expected_state} within {timeout_s:.1f}s: {last}"
    )


def observe_playback_cadence(client: MCPProcess, duration_s: float) -> dict:
    """Measure sustained DVD clock/output progress against monotonic wall time."""
    before = call_dict(client, "dev_player_state", timeout=30.0)
    started_ns = time.monotonic_ns()
    time.sleep(duration_s)
    after = call_dict(client, "dev_player_state", timeout=30.0)
    elapsed_ms = max(1, (time.monotonic_ns() - started_ns) // 1_000_000)

    def delta(key: str) -> int:
        return int(after.get(key, 0)) - int(before.get(key, 0))

    media_delta_ms = delta("mediaTimeMs")
    return {
        "requestedObserveMs": int(duration_s * 1000),
        "wallElapsedMs": elapsed_ms,
        "mediaTimeDeltaMs": media_delta_ms,
        "playerPositionDeltaMs": delta("health_playerPositionMs"),
        "realtimeRatio": media_delta_ms / elapsed_ms,
        "videoOutputDelta": delta("health_videoRendered"),
        "videoDroppedDelta": delta("health_videoDropped"),
        "videoSkippedDelta": delta("health_videoSkipped"),
        "audioOutputDelta": delta("health_audioRendered"),
        "audioDroppedDelta": delta("health_audioDropped"),
        "frameMetadataDelta": delta("dvdFrameMetadataCount"),
        "frameReleaseGapDelta": delta("dvdFrameReleaseGapCount"),
        "frameReleaseNonPositiveDelta": delta("dvdFrameReleaseNonPositiveCount"),
        "frameReleaseUnder10MsDelta": delta("dvdFrameReleaseUnder10MsCount"),
        "frameRelease10To25MsDelta": delta("dvdFrameRelease10To25MsCount"),
        "frameRelease25To45MsDelta": delta("dvdFrameRelease25To45MsCount"),
        "frameRelease45To75MsDelta": delta("dvdFrameRelease45To75MsCount"),
        "frameRelease75To100MsDelta": delta("dvdFrameRelease75To100MsCount"),
        "timestampCorrectionDelta": delta("dvdVideoTimestampCorrectionCount"),
        "before": compact_state(before),
        "after": compact_state(after),
    }


def compact_state(state: dict) -> dict:
    keys = (
        "connected", "automationReady", "playerActive", "state", "menuName",
        "player", "videoDecoder", "audioDecoder", "videoOutputCount",
        "audioOutputCount", "renderedFirstFrameCount", "playerError",
        "mediaTimeMs", "sageTimelineMs", "health_playerPositionMs",
        "health_playWhenReady", "health_isPlaying", "health_isLoading",
        "health_videoRendered", "health_audioRendered",
        "health_videoDropped", "health_videoSkipped", "health_audioDropped",
        "health_videoMime", "health_videoDecoder", "health_videoDecoderKind",
        "health_mpeg2InterlaceObservation", "health_mpeg2SequenceExtensionSeen",
        "health_mpeg2ProgressiveFrameCount", "health_mpeg2InterlacedFrameCount",
        "health_mpeg2FieldPictureCount",
        "dvdSessionPending", "dvdPushedBytes", "dvdInitCount",
        "dvdPushCommandCount", "dvdNewCellCount", "dvdClutCount",
        "dvdSpuControlCount", "dvdStreamCount", "dvdLastStreamType",
        "dvdLastStreamPosition", "dvdLastAudioStreamPosition",
        "dvdLastSubtitleStreamPosition", "dvdSpuFragments",
        "dvdRequestedAudioStream", "dvdAppliedAudioStream",
        "dvdSelectedAudioFormatId", "dvdAvailableAudioFormatIds",
        "dvdCompletedSpuPackets", "dvdMalformedSpuPackets",
        "dvdDecodedSpuEvents", "dvdDroppedSpuEvents",
        "dvdOverlaysPresented", "dvdOverlaysCleared",
        "dvdOverlayEventsScheduled", "dvdOverlayEventsApplied",
        "dvdOverlayEventsStale", "dvdLastOverlayEventUs",
        "dvdLastOverlayClockUs", "dvdLastOverlayOpaquePixels",
        "dvdLatestVideoSampleUs", "dvdLatestAudioSampleUs",
        "dvdAvSampleDeltaUs", "dvdVideoTimestampCorrectionCount",
        "dvdMpeg2TimestampRepairEnabled", "dvdMpeg2ReportedFrameRateHz",
        "dvdMpeg2SequenceFrameRateHz", "dvdMpeg2EffectiveFieldDurationUs",
        "dvdMpeg2TelecineCadenceSeen", "dvdDiscontinuityRebaseCount",
        "dvdPtsTrace", "dvdFrameMetadataCount", "dvdStc45Khz",
        "dvdLogicalClockBaseMs", "dvdRenderedVideoClockDeltaUs",
        "dvdLastFramePresentationDeltaUs", "dvdLastFrameReleaseDeltaUs",
        "dvdMaxFrameReleaseDeltaUs", "dvdFrameReleaseGapCount",
        "dvdFrameReleaseNonPositiveCount", "dvdFrameReleaseUnder10MsCount",
        "dvdFrameRelease10To25MsCount", "dvdFrameRelease25To45MsCount",
        "dvdFrameRelease45To75MsCount", "dvdFrameRelease75To100MsCount",
        "dvdFrameCadenceTrace",
        "dvdHighlightVisible", "dvdHighlightX1", "dvdHighlightY1",
        "dvdHighlightX2", "dvdHighlightY2", "dvdHighlightPaletteWord",
        "discPlaybackPolicy", "discCompatibilityFallback",
        "discOldServerNativeFallback", "discMimRuntimeFallback",
        "discCompatibilityReason",
    )
    return {key: state.get(key) for key in keys if key in state}


def main() -> int:
    # Docker/CI runs this without a TTY. Line-buffer progress so a long private
    # disc sweep never looks stalled while a drive spins up or a title starts.
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(line_buffering=True)
    parser = argparse.ArgumentParser(
        description="Commission one or more indexed remote DVD paths on a real device"
    )
    parser.add_argument("--server-address", default=default_server_address())
    parser.add_argument("--server-port", type=int, default=int(default_server_value("miniclient_port", 31099)))
    parser.add_argument("--server-path", action="append", default=[],
                        help="Exact indexed DVD directory; repeat for multiple discs")
    parser.add_argument("--media-name", action="append", default=[],
                        help=("Stock-server-compatible Sagex/STV MediaFile name; repeat for "
                              "multiple discs. This avoids the optional Vibe exact-path event."))
    parser.add_argument("--expect-startup-failure-path", action="append", default=[],
                        help="Exact invalid/empty DVD path that must fail safely; repeat as needed")
    parser.add_argument("--paths-file", default="",
                        help="UTF-8 file containing one exact indexed DVD directory per line")
    parser.add_argument("--player", choices=("media3", "exoplayer"), default="media3")
    parser.add_argument("--decoding", choices=("hardware", "hardware_preferred"),
                        default="hardware")
    parser.add_argument("--codec-mode", choices=("auto", "async", "sync"),
                        help="Dev-only Media3 codec adapter override for this run")
    parser.add_argument("--disc-policy",
                        choices=("auto", "native", "hybrid", "mim_main_feature"),
                        default="native")
    parser.add_argument("--mpeg2-timestamp-repair",
                        choices=("auto", "on", "off"), default="auto")
    parser.add_argument("--skip-menus", action="store_true")
    parser.add_argument("--skip-previews", action="store_true")
    parser.add_argument("--no-native-fallback", action="store_true")
    parser.add_argument("--timeout-s", type=float, default=90.0,
                        help="Per-disc startup timeout, including possible HDD spin-up")
    parser.add_argument("--verify-ms", type=int, default=2000)
    parser.add_argument("--settle-s", type=float, default=3.0)
    parser.add_argument("--start-ms", type=int, default=-1,
                        help=("Seek the main title to this exact media time before "
                              "settling, cadence measurement, commands, or screenshots"))
    parser.add_argument("--seek-tolerance-ms", type=int, default=2000,
                        help="Maximum permitted difference from --start-ms")
    parser.add_argument("--seek-timeout-s", type=float, default=30.0,
                        help="Maximum time for the initial positioning seek and A/V recovery")
    parser.add_argument("--cadence-observe-s", type=float, default=0.0,
                        help=("Measure sustained main-feature media-clock/output progress "
                              "for this many seconds"))
    parser.add_argument("--capture-datasource", action="store_true",
                        help=("Capture at most 32 MiB of the next DVD Push byte stream "
                              "in the debug app external-files directory"))
    parser.add_argument("--min-realtime-ratio", type=float, default=0.90,
                        help=("Minimum media-time/wall-time ratio required during the "
                              "cadence window"))
    parser.add_argument("--command", action="append", default=[],
                        help=("Optional command sent after startup; repeat in order. "
                              "Prefix one command with sage: or firetv: to override "
                              "--command-input for mixed menu/transport sequences"))
    parser.add_argument("--command-input", choices=("sage", "firetv"), default="sage",
                        help="Route --command through SageCommand or physical Android remote keys")
    parser.add_argument("--command-delay-s", type=float, default=3.0)
    parser.add_argument("--continue-on-failure", action="store_true")
    parser.add_argument("--leave-playing", action="store_true",
                        help="Single-disc diagnostic only: leave the session active")
    parser.add_argument("--screenshot", action="store_true")
    parser.add_argument("--screenshot-each-command", action="store_true",
                        help="Capture physical-state evidence after every command")
    parser.add_argument("--verify-command-recovery", action="store_true",
                        help="Require healthy A/V recovery after transport/track commands")
    parser.add_argument("--expect-audio-selector", type=int,
                        help="Fail unless the final requested and applied DVD audio selector match")
    parser.add_argument("--expect-subtitle-selector", type=int,
                        help="Fail unless the final DVD subtitle selector matches")
    parser.add_argument("--expect-media-time-min-ms", type=int,
                        help="Fail unless final SageTV DVD media time is at least this value")
    parser.add_argument("--expect-media-time-max-ms", type=int,
                        help="Fail unless final SageTV DVD media time is at most this value")
    parser.add_argument("--output", default="",
                        help="Optional JSON evidence path inside the project")
    args = parser.parse_args()

    expected_failures = {
        value.strip() for value in args.expect_startup_failure_path if value.strip()
    }
    paths = [value.strip() for value in args.server_path if value.strip()]
    media_names = [value.strip() for value in args.media_name if value.strip()]
    paths.extend(value for value in expected_failures if value not in paths)
    if args.paths_file:
        paths_file = Path(args.paths_file)
        if not paths_file.is_absolute():
            paths_file = Path(__file__).resolve().parents[1] / paths_file
        for line in paths_file.read_text(encoding="utf-8").splitlines():
            value = line.strip()
            if value and not value.startswith("#"):
                paths.append(value)
    targets = [("path", value) for value in paths]
    targets.extend(("name", value) for value in media_names)
    require(bool(targets),
            "at least one --server-path, --paths-file, or --media-name entry is required")
    require(not args.leave_playing or len(targets) == 1,
            "--leave-playing is valid only for a single disc")
    args.timeout_s = max(15.0, min(float(args.timeout_s), 180.0))
    args.verify_ms = max(500, min(int(args.verify_ms), 10_000))
    args.settle_s = max(0.0, min(float(args.settle_s), 30.0))
    require(args.start_ms >= -1, "--start-ms must be -1 (disabled) or non-negative")
    args.seek_tolerance_ms = max(0, min(int(args.seek_tolerance_ms), 30_000))
    args.seek_timeout_s = max(1.0, min(float(args.seek_timeout_s), 180.0))
    # Long-duration cadence faults on physical TV devices may appear only after
    # several minutes. Keep the run bounded, but do not silently turn an
    # explicitly requested ten-minute commissioning gate into three minutes.
    args.cadence_observe_s = max(0.0, min(float(args.cadence_observe_s), 900.0))
    args.min_realtime_ratio = max(0.0, min(float(args.min_realtime_ratio), 1.25))
    args.command_delay_s = max(0.0, min(float(args.command_delay_s), 30.0))
    require(args.cadence_observe_s == 0.0 or args.skip_menus,
            "--cadence-observe-s requires --skip-menus so a looping/still menu cannot "
            "be mistaken for main-feature playback")
    if args.expect_media_time_min_ms is not None:
        require(args.expect_media_time_min_ms >= 0,
                "--expect-media-time-min-ms must be non-negative")
    if args.expect_media_time_max_ms is not None:
        require(args.expect_media_time_max_ms >= 0,
                "--expect-media-time-max-ms must be non-negative")
    if (args.expect_media_time_min_ms is not None
            and args.expect_media_time_max_ms is not None):
        require(args.expect_media_time_min_ms <= args.expect_media_time_max_ms,
                "DVD media-time minimum cannot exceed maximum")

    client = MCPProcess()
    results: list[dict] = []
    try:
        negotiated, _ = initialize(client)
        print(f"PASS: MCP initialize handshake ({negotiated})")
        call_dict(client, "adb_connect", timeout=30.0)
        clean = call_dict(client, "dev_prepare_clean_start", {
            "wake": True, "graceful_timeout_s": 2.0,
        }, timeout=30.0)
        require(bool(clean.get("readyToLaunch")), f"Dev clean start failed: {clean}")
        if args.codec_mode:
            tuning = call_dict(client, "dev_set_player_tuning", {
                "media3_codec_mode": args.codec_mode,
            })
            require(str(tuning.get("tuningMedia3CodecMode", "")) == args.codec_mode,
                    f"Media3 codec-mode override was not applied: {tuning}")
        call_dict(client, "dev_set_player_config", {
            "player": args.player,
            "streaming": "dynamic",
            "decoding": args.decoding,
            "disc_playback_policy": args.disc_policy,
            "disc_skip_menus": args.skip_menus,
            "disc_skip_previews": args.skip_previews,
            "disc_compatibility_fallback": not args.no_native_fallback,
            "disc_mpeg2_timestamp_repair": args.mpeg2_timestamp_repair,
        })
        call_dict(client, "dev_connect_server", {
            "address": args.server_address,
            "port": args.server_port,
            "save": False,
        })
        wait_automation_ready(client, timeout_s=60.0)

        for index, (target_kind, target_value) in enumerate(targets, 1):
            label = safe_label(target_value)
            result: dict = {
                "index": index,
                "launchMethod": "vibe_exact_path" if target_kind == "path" else "stock_sagex_watch",
                "label": label,
            }
            if target_kind == "path":
                result["serverPath"] = target_value
            else:
                result["mediaName"] = target_value
            try:
                baseline = call_dict(client, "dev_crash_probe", timeout=30.0)
                if args.capture_datasource:
                    capture = call_dict(client, "dev_set_datasource_capture", {
                        "enabled": True,
                    }, timeout=30.0)
                    require(bool(capture.get("enabled")),
                            f"DVD datasource capture did not enable: {capture}")
                    result["datasourceCapture"] = capture
                if target_kind == "path":
                    started = call_dict(client, "dev_play_server_path", {
                        "server_path": target_value,
                        "timeout_s": args.timeout_s,
                        "verify_ms": args.verify_ms,
                        "restart_from_beginning": True,
                    }, timeout=args.timeout_s + 45.0)
                else:
                    started = call_dict(client, "dev_play_video", {
                        "video_name": target_value,
                        "timeout_s": args.timeout_s,
                        "verify_ms": args.verify_ms,
                    }, timeout=args.timeout_s + 45.0)
                result["startup"] = started
                if target_kind == "path" and target_value in expected_failures:
                    require(not bool(started.get("passed")),
                            f"Invalid DVD unexpectedly started: {started}")
                    state = call_dict(client, "dev_player_state", timeout=30.0)
                    require(not bool(state.get("playerActive")),
                            f"Invalid DVD left an active player: {state}")
                    after = call_dict(client, "dev_crash_probe", timeout=30.0)
                    require(not bool(after.get("signatureDetected")),
                            f"Invalid DVD produced a crash signature: {after}")
                    require(baseline.get("signatureFingerprint", "")
                            == after.get("signatureFingerprint", ""),
                            f"Invalid DVD changed the crash signature: {after}")
                    result.update({
                        "passed": True,
                        "expectedStartupFailure": True,
                        "failureReason": started.get("reason", "startup rejected"),
                        "state": compact_state(state),
                    })
                    results.append(result)
                    print(f"PASS expected-safe-failure [{index}/{len(targets)}]: {target_value}")
                    continue
                require(bool(started.get("passed")), f"DVD startup failed: {started}")
                if args.start_ms >= 0:
                    # DVD is a server-owned Push session.  Reposition SageTV's
                    # VideoFrame/MiniDVDPlayer so the server reader and emitted
                    # byte stream move together; a client-local seek cannot do
                    # that and can appear healthy while remaining near 0:00.
                    positioned = call_dict(client, "dev_server_seek_time", {
                        "target_ms": args.start_ms,
                        "tolerance_ms": args.seek_tolerance_ms,
                        "timeout_s": args.seek_timeout_s,
                        "stable_ms": args.verify_ms,
                    }, timeout=args.seek_timeout_s + 30.0)
                    require(bool(positioned.get("passed")),
                            f"DVD positioning seek did not recover A/V: {positioned}")
                    # dev_seek_time deliberately judges transport recovery rather
                    # than landing precision.  A repeatable cadence comparison
                    # additionally requires the requested scene, so enforce the
                    # coordinate here before collecting any evidence.
                    reached_ms = int(positioned.get("reached_ms", -1))
                    require(reached_ms >= 0 and
                            abs(reached_ms - args.start_ms) <= args.seek_tolerance_ms,
                            "DVD positioning seek missed the requested scene: "
                            f"target={args.start_ms}, reached={reached_ms}, "
                            f"tolerance={args.seek_tolerance_ms}")
                    result["startSeek"] = {
                        "passed": True,
                        "targetMs": args.start_ms,
                        "reachedMs": reached_ms,
                        "toleranceMs": args.seek_tolerance_ms,
                        "recoveryMs": positioned.get("recoveryMs"),
                        "measurement": positioned.get("measurement"),
                    }
                    print(f"PASS: positioned {target_value} at {reached_ms} ms "
                          f"(target {args.start_ms} ms)")
                if args.settle_s:
                    time.sleep(args.settle_s)
                if args.cadence_observe_s:
                    cadence = observe_playback_cadence(client, args.cadence_observe_s)
                    result["cadence"] = cadence
                    require(bool(cadence["after"].get("playerActive")),
                            f"DVD became inactive during cadence observation: {cadence}")
                    require(not str(cadence["after"].get("playerError", "")).strip(),
                            f"DVD player error during cadence observation: {cadence}")
                    require(int(cadence["videoOutputDelta"]) > 0,
                            f"DVD video did not advance during cadence observation: {cadence}")
                    require(int(cadence["audioOutputDelta"]) > 0,
                            f"DVD audio did not advance during cadence observation: {cadence}")
                    require(float(cadence["realtimeRatio"]) >= args.min_realtime_ratio,
                            "DVD playback is slower than real time: "
                            f"required >= {args.min_realtime_ratio:.3f}, cadence={cadence}")
                commands = []
                # Keep command evidence in the result as it is produced.  A
                # recovery assertion can fail after the Sage command was sent;
                # retaining the preceding state/screenshot is essential for
                # diagnosing the exact transition instead of only preserving
                # the final failure snapshot.
                result["commands"] = commands
                for command in args.command:
                    command_started = time.monotonic()
                    input_mode = args.command_input
                    command_value = command
                    if ":" in command:
                        possible_mode, possible_command = command.split(":", 1)
                        if possible_mode.lower() in ("sage", "firetv"):
                            input_mode = possible_mode.lower()
                            command_value = possible_command
                    command_key = command_value.strip().lower()
                    if input_mode == "firetv":
                        response = call_dict(client, "firetv_key", {
                            "key": command_value,
                        }, timeout=30.0)
                    else:
                        response = call_dict(client, "dev_sage_command", {
                            "command": command_value,
                        }, timeout=30.0)
                    if args.command_delay_s:
                        time.sleep(args.command_delay_s)
                    state_transition_ms = None
                    if command_key == "pause":
                        command_state, state_transition_ms = wait_player_state(client, 3)
                    elif command_key == "play":
                        command_state, state_transition_ms = wait_player_state(client, 2)
                    else:
                        command_state = call_dict(client, "dev_player_state", timeout=30.0)
                    require(bool(command_state.get("playerActive")),
                            f"DVD became inactive after {command}: {command_state}")
                    require(not str(command_state.get("playerError", "")).strip(),
                            f"DVD player error after {command}: "
                            f"{command_state.get('playerError')}")
                    if command_key == "pause":
                        # MiniPlayerPlugin.PAUSE_STATE is 3.  LOADED_STATE is 1;
                        # accepting it here produced a false PASS for a player
                        # that had not actually entered the paused state.
                        require(int(command_state.get("state", -1)) == 3,
                                f"DVD did not enter PAUSE after {command}: {command_state}")
                    elif command_key == "play":
                        require(int(command_state.get("state", -1)) == 2,
                                f"DVD did not enter PLAY after {command}: {command_state}")
                    recovery = None
                    command_shot = None
                    if args.screenshot_each_command:
                        command_shot = call_dict(client, "take_screenshot", {
                            "name": f"dvd-{index:02d}-{label}-{len(commands) + 1:02d}-{safe_label(command_value)}",
                        }, timeout=30.0)
                    command_record = {
                        "command": command_value,
                        "inputMode": input_mode,
                        "response": response,
                        "recovery": recovery,
                        "state": compact_state(command_state),
                        "commandElapsedMs": int((time.monotonic() - command_started) * 1000),
                        "stateTransitionMs": state_transition_ms,
                        "screenshot": command_shot,
                    }
                    commands.append(command_record)
                    recovery_commands = {
                        "play", "ff_2", "rew_2", "dvd_chapter_up", "dvd_chapter_down",
                        "dvd_audio_change", "dvd_subtitle_change", "dvd_subtitle_toggle",
                        # Physical SELECT/ENTER is the critical menu-to-title
                        # transition.  Omitting it allowed an empty MIM title
                        # source to error immediately after the command while
                        # the DVD test still reported PASS.
                        "select", "enter", "center", "dpad_center",
                    }
                    if args.verify_command_recovery and command_key in recovery_commands:
                        recovery = call_dict(client, "dev_wait_for_playback_started", {
                            "timeout_s": 25.0,
                            "verify_ms": args.verify_ms,
                        }, timeout=35.0)
                        command_record["recovery"] = recovery
                        require(bool(recovery.get("passed")),
                                f"DVD A/V did not recover after {command}: {recovery}")
                        command_state = call_dict(client, "dev_player_state", timeout=30.0)
                        command_record["stateAfterRecovery"] = compact_state(command_state)
                state = call_dict(client, "dev_player_state", timeout=30.0)
                require(bool(state.get("playerActive")), f"DVD player became inactive: {state}")
                require(int(state.get("dvdInitCount", 0)) > 0,
                        f"No MiniDVDPlayer INIT was observed: {state}")
                require(int(state.get("dvdPushedBytes", 0)) > 0,
                        f"No remote DVD media bytes were pushed: {state}")
                require(not str(state.get("playerError", "")).strip(),
                        f"DVD player error: {state.get('playerError')}")
                if args.player in ("media3", "exoplayer"):
                    require(str(state.get("health_videoMime", "")).lower() == "video/mpeg2",
                            f"DVD did not resolve an MPEG-2 video track: {state}")
                    require(state.get("health_mpeg2SequenceExtensionSeen") in (True, "true"),
                            f"DVD MPEG-2 sequence extension was not observed: {state}")
                    require(str(state.get("health_mpeg2InterlaceObservation", "unknown"))
                            != "unknown",
                            f"DVD MPEG-2 interlace state remained unknown: {state}")
                if args.expect_audio_selector is not None:
                    require(int(state.get("dvdRequestedAudioStream", -1))
                            == args.expect_audio_selector,
                            "DVD requested audio selector mismatch: "
                            f"expected {args.expect_audio_selector}, state={state}")
                    require(int(state.get("dvdAppliedAudioStream", -1))
                            == args.expect_audio_selector,
                            "DVD applied audio selector mismatch: "
                            f"expected {args.expect_audio_selector}, state={state}")
                if args.expect_subtitle_selector is not None:
                    require(int(state.get("dvdLastSubtitleStreamPosition", -1))
                            == args.expect_subtitle_selector,
                            "DVD subtitle selector mismatch: "
                            f"expected {args.expect_subtitle_selector}, state={state}")
                final_media_time_ms = int(state.get("mediaTimeMs", -1))
                if args.expect_media_time_min_ms is not None:
                    require(final_media_time_ms >= args.expect_media_time_min_ms,
                            "DVD media time below expected minimum: "
                            f"expected >= {args.expect_media_time_min_ms}, state={state}")
                if args.expect_media_time_max_ms is not None:
                    require(final_media_time_ms <= args.expect_media_time_max_ms,
                            "DVD media time above expected maximum: "
                            f"expected <= {args.expect_media_time_max_ms}, state={state}")
                after = call_dict(client, "dev_crash_probe", timeout=30.0)
                require(not bool(after.get("signatureDetected")),
                        f"Crash signature detected: {after}")
                require(baseline.get("signatureFingerprint", "")
                        == after.get("signatureFingerprint", ""),
                        f"Crash signature changed: {after}")
                shot = None
                if args.screenshot:
                    shot = call_dict(client, "take_screenshot", {
                        "name": f"dvd-{index:02d}-{label}",
                    }, timeout=30.0)
                stopped = {}
                if not args.leave_playing:
                    call_dict(client, "dev_sage_command", {"command": "stop"}, timeout=30.0)
                    stopped = wait_stopped(client)
                result.update({
                    "passed": True,
                    "state": compact_state(state),
                    "commands": commands,
                    "stoppedState": compact_state(stopped),
                    "screenshot": shot,
                })
                print(f"PASS [{index}/{len(targets)}]: {target_value}")
            except Exception as exc:
                result.update({"passed": False, "error": str(exc)})
                print(f"FAIL [{index}/{len(targets)}]: {target_value}: {exc}", file=sys.stderr)
                try:
                    result["diagnostics"] = call_dict(
                        client, "collect_playback_diagnostics",
                        {"label": f"dvd-{index:02d}-{label}-failure"}, timeout=90.0,
                    )
                except Exception as diagnostic_exc:
                    result["diagnosticError"] = str(diagnostic_exc)
                if not args.leave_playing:
                    try:
                        call_dict(client, "dev_sage_command", {"command": "stop"}, timeout=30.0)
                        wait_stopped(client)
                    except Exception:
                        pass
                results.append(result)
                if not args.continue_on_failure:
                    break
                continue
            results.append(result)

        evidence = {
            "passed": len(results) == len(targets) and all(item["passed"] for item in results),
            "serverAddress": args.server_address,
            "player": args.player,
            "decoding": args.decoding,
            "discPolicy": args.disc_policy,
            "skipMenus": args.skip_menus,
            "skipPreviews": args.skip_previews,
            "startMs": args.start_ms,
            "seekToleranceMs": args.seek_tolerance_ms,
            "seekTimeoutSeconds": args.seek_timeout_s,
            "cadenceObserveSeconds": args.cadence_observe_s,
            "datasourceCaptureRequested": args.capture_datasource,
            "minimumRealtimeRatio": args.min_realtime_ratio,
            "nativeFallback": not args.no_native_fallback,
            "expectedAudioSelector": args.expect_audio_selector,
            "expectedSubtitleSelector": args.expect_subtitle_selector,
            "expectedMediaTimeMinMs": args.expect_media_time_min_ms,
            "expectedMediaTimeMaxMs": args.expect_media_time_max_ms,
            "requested": len(targets),
            "completed": len(results),
            "results": results,
        }
        if args.output:
            output = Path(args.output)
            if not output.is_absolute():
                output = Path(__file__).resolve().parents[1] / output
            output.parent.mkdir(parents=True, exist_ok=True)
            output.write_text(json.dumps(evidence, indent=2, sort_keys=True) + "\n",
                              encoding="utf-8")
            print(f"Evidence: {output}")
        print("ANDROID REMOTE DVD MATRIX: " + ("PASS" if evidence["passed"] else "FAIL"))
        return 0 if evidence["passed"] else 1
    except Exception as exc:
        print(f"ANDROID REMOTE DVD MATRIX: FAIL\n{exc}", file=sys.stderr)
        return 1
    finally:
        if not args.leave_playing:
            try:
                call_dict(client, "dev_exit_session", {"stop_app": True}, timeout=30.0)
            except Exception:
                pass
        client.close()


if __name__ == "__main__":
    raise SystemExit(main())
