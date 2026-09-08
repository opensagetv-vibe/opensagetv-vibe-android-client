#!/usr/bin/env python3
"""Verify SageTV-STV caption authority and rendered cue delivery."""
from __future__ import annotations

from sagetv_dev_mcp.config import default_server_address, default_server_value

import argparse
import json
import re
import sys
import time

from mcp_lifecycle_test import MCPProcess, call_dict, initialize, require, wait_automation_ready
from mcp_smoke_test import compact_tool_result, tool_call
from mcp_config_values import add_fixed_encoding_args, fixed_config_from_args, validate_fixed_config
from mcp_playback_test import start_recording_via_search


CAPTION_TIME_RE = re.compile(r"(?<!\d)(\d{1,2}):(\d{2}):(\d{2})\.(\d{3})(?!\d)")


def caption_time_count(text: str) -> int:
    return len(CAPTION_TIME_RE.findall(text or ""))


def stable_caption_time_ms(text: str) -> int | None:
    """Return the stable middle/previous roll-up timestamp in milliseconds."""
    matches = CAPTION_TIME_RE.findall(text or "")
    if not matches:
        return None
    # The newest CEA-608 row can be observed midway through a character update.
    # With the normal three-line roll-up, the second-to-last value is the stable
    # middle cue and is intentionally about 0.5 second behind the newest cue.
    hours, minutes, seconds, milliseconds = matches[-2] if len(matches) >= 2 else matches[-1]
    return (((int(hours) * 60) + int(minutes)) * 60 + int(seconds)) * 1000 + int(milliseconds)


def wait_snapshot(client: MCPProcess, predicate, description: str, timeout_s: float) -> dict:
    deadline = time.monotonic() + timeout_s
    last: dict = {}
    while time.monotonic() < deadline:
        last = call_dict(client, "dev_player_state", timeout=30.0)
        if predicate(last):
            return last
        time.sleep(0.25)
    raise RuntimeError(f"timed out waiting for {description}: {last}")


def main() -> int:
    parser = argparse.ArgumentParser(description="Run the Android caption physical gate")
    parser.add_argument("--server-address", default=default_server_address())
    parser.add_argument("--server-port", type=int, default=int(default_server_value("miniclient_port", 31099)))
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument("--server-path")
    source.add_argument(
        "--search-text",
        help="Open a recording through the ordinary SageTV STV search path (for stock servers)",
    )
    source.add_argument(
        "--video-name",
        help="Select a recorded MediaFile through the stock SageTV/Sagex Watch API",
    )
    source.add_argument(
        "--media-file-id",
        type=int,
        help="Select one exact recorded MediaFile ID through the stock SageTV/Sagex Watch API",
    )
    parser.add_argument("--search-media-type", choices=("tv", "videos"), default="tv")
    parser.add_argument("--player", choices=("exoplayer", "media3", "ijkplayer"), default="media3")
    parser.add_argument("--streaming", choices=("dynamic", "push", "pull", "fixed"), default="pull")
    parser.add_argument("--decoding", choices=("hardware", "software", "hardware_preferred"), default="hardware")
    add_fixed_encoding_args(parser)
    parser.add_argument("--track-index", type=int, default=0)
    parser.add_argument(
        "--toggle-off-on",
        action="store_true",
        help=(
            "Disable and re-enable the selected caption track during the same "
            "media session, then require new non-empty cues without restarting playback"
        ),
    )
    parser.add_argument(
        "--continuity-window-s",
        type=float,
        default=5.0,
        help="Seconds of continued non-empty cue progress required after Off -> On",
    )
    parser.add_argument("--preferred-audio-language", default="auto")
    parser.add_argument("--preferred-subtitle-language", default="auto")
    parser.add_argument(
        "--preferred-caption-standard",
        choices=("auto", "cea608", "cea708"),
        default="auto",
    )
    parser.add_argument("--preferred-caption-service", type=int, default=1)
    parser.add_argument(
        "--authority",
        choices=("stv", "debug"),
        default="stv",
        help=(
            "Caption authority. 'stv' requires SageTV to select the track and never "
            "uses the Android debug selector; 'debug' retains the diagnostic selector."
        ),
    )
    parser.add_argument(
        "--legacy-extender-callback",
        action="store_true",
        help=(
            "Verify the standard GFX_SUBTITLES/SUBTITLES_CALLBACKS event-225 path. "
            "This mode requires STV authority, real wire bytes, and no duplicate "
            "Android subtitle overlay."
        ),
    )
    parser.add_argument(
        "--expect-no-legacy-callback",
        action="store_true",
        help=(
            "Verify that an unsupported backend remains playable without advertising "
            "or emitting the legacy event-225 subtitle callback path"
        ),
    )
    parser.add_argument(
        "--cycle-stv-caption-states",
        action="store_true",
        help=(
            "With --legacy-extender-callback, exercise SageTV's standard server-side "
            "Off/CC1/CC2/Off/CC1 states and capture each state without VIDEO_CC_STATE"
        ),
    )
    parser.add_argument("--playback-timeout-s", type=float, default=60.0)
    parser.add_argument("--cue-timeout-s", type=float, default=45.0)
    parser.add_argument("--verify-ms", type=int, default=3000)
    parser.add_argument(
        "--preserve-resume",
        action="store_true",
        help="Honor the server bookmark instead of restarting the deterministic fixture",
    )
    parser.add_argument(
        "--seek-command",
        action="append",
        choices=("ff", "rew", "ff_2", "rew_2"),
        default=[],
        help="Repeatable SageTV-owned seek to run before the caption/timeline gate",
    )
    parser.add_argument(
        "--seek-settle-ms",
        type=int,
        default=None,
        help="Wait after each seek before judging output (default: Push 3000 ms, others 1000 ms)",
    )
    parser.add_argument("--hold-s", type=float, default=0.0,
                        help="Keep selected captions active briefly for visual inspection")
    parser.add_argument(
        "--timeline-hold-s",
        type=float,
        default=12.0,
        help=(
            "Test-only time to keep the paused SageTV timeline visible when "
            "--show-stv-timeline is used (default: 12 seconds; does not change runtime settings)"
        ),
    )
    parser.add_argument(
        "--show-stv-timeline",
        action="store_true",
        help="Pause before capture so captions and the SageTV STV timeline are verified together",
    )
    parser.add_argument(
        "--sync-tolerance-ms",
        type=int,
        default=1000,
        help="Maximum permitted stable-middle-caption versus SageTV timeline drift",
    )
    parser.add_argument(
        "--sync-sample-s",
        type=float,
        default=2.5,
        help="Clock-advance sampling window used with --show-stv-timeline",
    )
    args = parser.parse_args()
    if args.legacy_extender_callback and args.expect_no_legacy_callback:
        parser.error("--legacy-extender-callback and --expect-no-legacy-callback are mutually exclusive")
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
            "preferred_audio_language": args.preferred_audio_language,
            "preferred_subtitle_language": args.preferred_subtitle_language,
            "preferred_caption_standard": args.preferred_caption_standard,
            "preferred_caption_service": args.preferred_caption_service,
            **fixed_config,
        })
        call_dict(client, "dev_connect_server", {
            "address": args.server_address,
            "port": args.server_port,
            "save": False,
        })
        wait_automation_ready(client)
        if args.media_file_id is not None:
            started = call_dict(client, "dev_play_media_file_id", {
                "media_file_id": args.media_file_id,
                "timeout_s": args.playback_timeout_s,
                "verify_ms": args.verify_ms,
                "restart_from_beginning": not args.preserve_resume,
            }, timeout=args.playback_timeout_s + 35.0)
        elif args.video_name:
            started = call_dict(client, "dev_play_video", {
                "video_name": args.video_name,
                "timeout_s": args.playback_timeout_s,
                "verify_ms": args.verify_ms,
            }, timeout=args.playback_timeout_s + 35.0)
        elif args.search_text:
            started = start_recording_via_search(
                client,
                args.search_text,
                search_timeout_s=15.0,
                text_char_delay_ms=40,
                media_type=args.search_media_type,
            )
            playback = call_dict(client, "dev_wait_for_playback_started", {
                "timeout_s": args.playback_timeout_s,
                "verify_ms": args.verify_ms,
            }, timeout=args.playback_timeout_s + 15.0)
            started["playback"] = playback
            started["passed"] = bool(playback.get("passed"))
        else:
            started = call_dict(client, "dev_play_server_path", {
                "server_path": args.server_path,
                "timeout_s": args.playback_timeout_s,
                "verify_ms": args.verify_ms,
                "restart_from_beginning": not args.preserve_resume,
            }, timeout=args.playback_timeout_s + 35.0)
        require(bool(started.get("passed")), f"Initial playback failed: {started}")

        if args.expect_no_legacy_callback:
            fallback_state = wait_snapshot(
                client,
                lambda state: str(state.get("player", "")).casefold() == args.player.casefold()
                    and not bool(state.get("legacyCaptionCallbacksNegotiated", False))
                    and not bool(state.get("legacyCaptionCallbackActive", False)),
                "a playable backend with legacy caption callbacks disabled",
                15.0,
            )
            require(
                int(fallback_state.get("legacyCaptionCallbackCount", 0)) == 0
                and int(fallback_state.get("legacyCaptionCallbackBytes", 0)) == 0
                and int(fallback_state.get("legacyCaptionWireEventCount", 0)) == 0
                and int(fallback_state.get("legacyCaptionWireBytes", 0)) == 0,
                f"unsupported backend emitted legacy caption callback data: {fallback_state}",
            )
            screenshot = tool_call(
                client,
                "take_screenshot",
                {"label": f"caption-{args.player}-{args.streaming}-safe-fallback"},
                timeout=30.0,
            )
            crash = call_dict(client, "dev_crash_probe", timeout=30.0)
            require(not bool(crash.get("signatureDetected")), f"Crash signature detected: {crash}")
            print(
                "PASS: unsupported backend stayed playable without false legacy-caption "
                f"negotiation or event-225 output; capture={compact_tool_result(screenshot)}"
            )
            print(f"ANDROID LEGACY CAPTION SAFE FALLBACK ({args.player}): PASS")
            return 0

        tracks = wait_snapshot(
            client,
            lambda state: int(state.get("subtitleTrackCount", 0)) > args.track_index,
            "a supported subtitle/caption track",
            20.0,
        )
        print(f"PASS: detected caption tracks: {tracks.get('subtitleTracks')}")
        require(
            tracks.get("preferredCaptionStandard") == args.preferred_caption_standard,
            f"caption standard preference was not applied: {tracks}",
        )
        require(
            int(tracks.get("preferredCaptionService", 0)) == args.preferred_caption_service,
            f"caption service preference was not applied: {tracks}",
        )
        if args.legacy_extender_callback:
            require(
                args.authority == "stv",
                "--legacy-extender-callback requires --authority stv",
            )
            require(
                not args.toggle_off_on,
                "--toggle-off-on is an Android-overlay diagnostic and cannot be combined "
                "with --legacy-extender-callback",
            )
            require(
                not args.show_stv_timeline,
                "--show-stv-timeline currently reads Android cue text and cannot be combined "
                "with --legacy-extender-callback",
            )
            before_wire_events = int(tracks.get("legacyCaptionWireEventCount", 0))
            callback_state = wait_snapshot(
                client,
                lambda state: bool(state.get("legacyCaptionCallbacksNegotiated", False))
                    and bool(state.get("legacyCaptionCallbackActive", False))
                    and int(state.get("legacyCaptionCallbackCount", 0)) > 0
                    and int(state.get("legacyCaptionCallbackBytes", 0)) > 0
                    and int(state.get("legacyCaptionWireEventCount", 0)) > before_wire_events
                    and int(state.get("legacyCaptionWireBytes", 0)) > 0
                    and not bool(state.get("subtitleOverlayAttached", False)),
                "legacy-extender caption callbacks on event 225 without a local overlay",
                args.cue_timeout_s,
            )
            print(
                "PASS: legacy-extender captions reached SageTV "
                f"decodedEvents={callback_state.get('legacyCaptionCallbackCount')} "
                f"decodedBytes={callback_state.get('legacyCaptionCallbackBytes')} "
                f"wireEvents={callback_state.get('legacyCaptionWireEventCount')} "
                f"wireBytes={callback_state.get('legacyCaptionWireBytes')} "
                f"overlayAttached={callback_state.get('subtitleOverlayAttached')}"
            )
            if args.cycle_stv_caption_states:
                expected_states = (
                    ("Off", "off"),
                    ("CC1", "cc1"),
                    ("CC2", "cc2"),
                    ("Off", "off"),
                    ("CC1", "cc1"),
                )
                for cycle_index, (requested_state, expected_token) in enumerate(
                        expected_states, start=1):
                    before_cycle_wire = int(
                        callback_state.get("legacyCaptionWireEventCount", 0)
                    )
                    changed = call_dict(client, "dev_set_stv_caption_state", {
                        "state": requested_state,
                    }, timeout=30.0)
                    reported = str(changed.get("state", "")).strip()
                    require(
                        expected_token in reported.casefold(),
                        f"SageTV reported {reported!r} after requesting {requested_state}",
                    )
                    callback_state = wait_snapshot(
                        client,
                        lambda state: int(state.get("legacyCaptionWireEventCount", 0))
                            > before_cycle_wire
                            and not bool(state.get("subtitleOverlayAttached", False)),
                        f"event-225 continuity while SageTV captions are {requested_state}",
                        args.cue_timeout_s,
                    )
                    time.sleep(2.0)
                    capture = tool_call(
                        client,
                        "take_screenshot",
                        {
                            "label": (
                                f"caption-{args.player}-{args.streaming}-stv-"
                                f"{cycle_index}-{requested_state.casefold()}"
                            )
                        },
                        timeout=30.0,
                    )
                    print(
                        f"PASS: SageTV standard CC state {reported!r}; "
                        f"event225={callback_state.get('legacyCaptionWireEventCount')} "
                        f"overlayAttached={callback_state.get('subtitleOverlayAttached')} "
                        f"capture={compact_tool_result(capture)}"
                    )
            for seek_index, command in enumerate(args.seek_command, start=1):
                before_seek_wire = int(callback_state.get("legacyCaptionWireEventCount", 0))
                seek_settle_ms = args.seek_settle_ms
                if seek_settle_ms is None:
                    seek_settle_ms = 3000 if args.streaming in ("dynamic", "push") else 1000
                seek = call_dict(client, "dev_run_seek_check", {
                    "commands": [command],
                    "expected_net_ms": 0,
                    "tolerance_ms": 2_000_000_000,
                    "delay_ms": 0,
                    "settle_ms": max(0, seek_settle_ms),
                    "recovery_timeout_ms": 20_000,
                    "verify_playback_ms": 2500,
                    "health_poll_ms": 250,
                }, timeout=60.0)
                require(bool(seek.get("passed")), f"Seek {seek_index} ({command}) failed: {seek}")
                callback_state = wait_snapshot(
                    client,
                    lambda state: int(state.get("legacyCaptionWireEventCount", 0)) > before_seek_wire
                        and bool(state.get("legacyCaptionCallbackActive", False))
                        and not bool(state.get("subtitleOverlayAttached", False)),
                    f"legacy caption callback recovery after seek {seek_index} ({command})",
                    args.cue_timeout_s,
                )
                print(
                    f"PASS: seek {seek_index}/{len(args.seek_command)} {command} recovered "
                    f"event-225 output in {seek.get('recoveryMs')} ms"
                )
            screenshot = tool_call(
                client,
                "take_screenshot",
                {"label": f"caption-{args.player}-{args.streaming}-legacy-callback"},
                timeout=30.0,
            )
            print(f"PASS: captured SageTV-rendered caption evidence: {compact_tool_result(screenshot)}")
            if args.hold_s > 0:
                print(
                    f"HOLD: legacy callback session remains active for {args.hold_s:.1f}s "
                    "for STV control inspection"
                )
                hold_deadline = time.monotonic() + args.hold_s
                while time.monotonic() < hold_deadline:
                    time.sleep(min(1.5, hold_deadline - time.monotonic()))
            crash = call_dict(client, "dev_crash_probe", timeout=30.0)
            require(not bool(crash.get("signatureDetected")), f"Crash signature detected: {crash}")
            print(f"ANDROID LEGACY CAPTION CALLBACKS ({args.player}): PASS")
            return 0
        before_updates = int(tracks.get("subtitleCueUpdateCount", 0))
        if args.authority == "debug":
            selected = call_dict(client, "dev_set_subtitle_track", {"index": args.track_index}, timeout=30.0)
            require(bool(selected.get("accepted")), f"Caption track selection was rejected: {selected}")
        else:
            print("PASS: Android subtitle selector was not invoked; waiting for SageTV STV authority")
        selected_state = wait_snapshot(
            client,
            lambda state: int(state.get("selectedSubtitleTrack", -1)) == args.track_index,
            f"{args.authority} caption track selection",
            10.0,
        )
        rendered = wait_snapshot(
            client,
            lambda state: int(state.get("subtitleCueUpdateCount", 0)) > before_updates
                and int(state.get("subtitleNonEmptyCueCount", 0)) > 0
                and bool(str(state.get("currentSubtitleCueText", "")).strip())
                and bool(state.get("subtitleOverlayAttached", False)),
            "a non-empty caption cue on an attached overlay",
            args.cue_timeout_s,
        )
        print(
            "PASS: caption cues rendered "
            f"updates={rendered.get('subtitleCueUpdateCount')} "
            f"nonEmpty={rendered.get('subtitleNonEmptyCueCount')} "
            f"text={rendered.get('currentSubtitleCueText')!r} "
            f"overlayAttached={rendered.get('subtitleOverlayAttached')}"
        )
        if args.toggle_off_on:
            if args.authority != "debug":
                raise RuntimeError("--toggle-off-on requires --authority debug")
            before_toggle_updates = int(rendered.get("subtitleCueUpdateCount", 0))
            disabled = call_dict(client, "dev_set_subtitle_track", {"index": -1}, timeout=30.0)
            require(bool(disabled.get("accepted")), f"Caption disable was rejected: {disabled}")
            wait_snapshot(
                client,
                lambda state: int(state.get("selectedSubtitleTrack", 0)) == -1
                    and not bool(state.get("subtitleOverlayAttached", True)),
                "caption renderer disable",
                10.0,
            )
            off_capture = tool_call(
                client,
                "take_screenshot",
                {"label": f"caption-{args.player}-{args.streaming}-off-same-session"},
                timeout=30.0,
            )
            print(f"PASS: captions disabled in the active session: {compact_tool_result(off_capture)}")
            enabled = call_dict(
                client, "dev_set_subtitle_track", {"index": args.track_index}, timeout=30.0
            )
            require(bool(enabled.get("accepted")), f"Caption re-enable was rejected: {enabled}")
            rendered = wait_snapshot(
                client,
                lambda state: int(state.get("selectedSubtitleTrack", -1)) == args.track_index
                    and int(state.get("subtitleCueUpdateCount", 0)) > before_toggle_updates
                    and bool(str(state.get("currentSubtitleCueText", "")).strip())
                    and bool(state.get("subtitleOverlayAttached", False)),
                "caption cues after same-session Off -> On",
                args.cue_timeout_s,
            )
            print(
                "PASS: captions resumed without restarting playback "
                f"updates={rendered.get('subtitleCueUpdateCount')} "
                f"text={rendered.get('currentSubtitleCueText')!r}"
            )
            continuity_start = time.monotonic()
            continuity_deadline = continuity_start + max(2.0, args.continuity_window_s)
            last_non_empty_count = int(rendered.get("subtitleNonEmptyCueCount", 0))
            initial_non_empty_count = last_non_empty_count
            last_progress = continuity_start
            longest_progress_gap_s = 0.0
            while time.monotonic() < continuity_deadline:
                time.sleep(min(0.25, continuity_deadline - time.monotonic()))
                sample = call_dict(client, "dev_player_state", timeout=30.0)
                now = time.monotonic()
                non_empty_count = int(sample.get("subtitleNonEmptyCueCount", 0))
                if non_empty_count > last_non_empty_count:
                    longest_progress_gap_s = max(longest_progress_gap_s, now - last_progress)
                    last_progress = now
                    last_non_empty_count = non_empty_count
                    rendered = sample
            longest_progress_gap_s = max(longest_progress_gap_s, time.monotonic() - last_progress)
            required_progress = max(3, int(max(2.0, args.continuity_window_s)))
            actual_progress = last_non_empty_count - initial_non_empty_count
            require(
                actual_progress >= required_progress,
                "Captions resumed but were not continuous after Off -> On: "
                f"nonEmptyCueDelta={actual_progress} required={required_progress}",
            )
            require(
                longest_progress_gap_s <= 2.0,
                "Caption cue production stalled after Off -> On: "
                f"longestProgressGapS={longest_progress_gap_s:.3f}",
            )
            print(
                "PASS: captions remained continuous after re-enable "
                f"nonEmptyCueDelta={actual_progress} "
                f"longestProgressGapS={longest_progress_gap_s:.3f}"
            )
        for seek_index, command in enumerate(args.seek_command, start=1):
            seek_settle_ms = args.seek_settle_ms
            if seek_settle_ms is None:
                seek_settle_ms = 3000 if args.streaming in ("dynamic", "push") else 1000
            before_seek_cues = int(rendered.get("subtitleCueUpdateCount", 0))
            seek = call_dict(client, "dev_run_seek_check", {
                "commands": [command],
                "expected_net_ms": 0,
                "tolerance_ms": 2_000_000_000,
                "delay_ms": 0,
                "settle_ms": max(0, seek_settle_ms),
                "recovery_timeout_ms": 20_000,
                "verify_playback_ms": 2500,
                "health_poll_ms": 250,
            }, timeout=60.0)
            require(bool(seek.get("passed")), f"Seek {seek_index} ({command}) failed: {seek}")
            rendered = wait_snapshot(
                client,
                lambda state: int(state.get("subtitleCueUpdateCount", 0)) > before_seek_cues
                    and caption_time_count(str(state.get("currentSubtitleCueText", ""))) >= 2
                    and bool(state.get("subtitleOverlayAttached", False)),
                f"caption recovery after seek {seek_index} ({command})",
                args.cue_timeout_s,
            )
            print(
                f"PASS: seek {seek_index}/{len(args.seek_command)} {command} recovered "
                f"A/V and stable captions in {seek.get('recoveryMs')} ms"
            )
        if args.show_stv_timeline:
            sync_before = rendered
            caption_before_ms = stable_caption_time_ms(str(sync_before.get("currentSubtitleCueText", "")))
            timeline_before_ms = int(sync_before.get("sageTimelineMs", -1))
            require(caption_before_ms is not None, f"No fixture timestamp found in caption text: {sync_before}")
            require(timeline_before_ms >= 0, f"SageTV timeline was unavailable: {sync_before}")
            sample_deadline = time.monotonic() + max(0.5, args.sync_sample_s)
            while time.monotonic() < sample_deadline:
                time.sleep(min(0.25, sample_deadline - time.monotonic()))
            sync_after = wait_snapshot(
                client,
                lambda state: (
                    stable_caption_time_ms(str(state.get("currentSubtitleCueText", "")))
                    not in (None, caption_before_ms)
                    and int(state.get("sageTimelineMs", -1)) > timeline_before_ms
                ),
                "advancing fixture captions and SageTV media clock",
                args.cue_timeout_s,
            )
            caption_after_ms = stable_caption_time_ms(str(sync_after.get("currentSubtitleCueText", "")))
            timeline_after_ms = int(sync_after.get("sageTimelineMs", -1))
            caption_delta_ms = int(caption_after_ms) - caption_before_ms
            timeline_delta_ms = timeline_after_ms - timeline_before_ms
            drift_ms = abs(caption_delta_ms - timeline_delta_ms)
            require(
                drift_ms <= args.sync_tolerance_ms,
                "Caption/media-clock advance drift exceeded tolerance: "
                f"captionDeltaMs={caption_delta_ms} timelineDeltaMs={timeline_delta_ms} "
                f"driftMs={drift_ms} toleranceMs={args.sync_tolerance_ms}",
            )
            print(
                "PASS: fixture captions track the SageTV media clock "
                f"captionDeltaMs={caption_delta_ms} timelineDeltaMs={timeline_delta_ms} "
                f"driftMs={drift_ms} toleranceMs={args.sync_tolerance_ms}"
            )
            print(
                "INFO: the captured screenshot is the absolute-offset gate; some SageTV "
                "transport clocks are output-relative while the STV adds the recording offset"
            )
            rendered = wait_snapshot(
                client,
                lambda state: caption_time_count(str(state.get("currentSubtitleCueText", ""))) >= 3
                    and bool(state.get("subtitleOverlayAttached", False)),
                "three complete roll-up caption timestamps before timeline capture",
                args.cue_timeout_s,
            )
            # DPAD RIGHT is SageTV's skip command and must not be used merely to
            # expose the OSD. Pause freezes both clocks and displays the timeline
            # without moving the media position; the session exits after capture.
            tool_call(client, "firetv_key", {"key": "PAUSE"}, timeout=30.0)
            time.sleep(0.5)
            paused = call_dict(client, "dev_player_state", timeout=30.0)
            require(bool(paused.get("subtitleOverlayAttached", False)), "Caption overlay detached while pausing")
            require(bool(str(paused.get("currentSubtitleCueText", "")).strip()), "Caption text vanished while pausing")
            require(
                caption_time_count(str(paused.get("currentSubtitleCueText", ""))) >= 2,
                f"Paused caption roll-up had no stable middle cue: {paused.get('currentSubtitleCueText')!r}",
            )
            print("PASS: SageTV pause exposed the timeline and retained active captions")
        screenshot = tool_call(
            client,
            "take_screenshot",
            {"label": f"caption-{args.player}-{args.streaming}-visible"},
            timeout=30.0,
        )
        print(f"PASS: captured active caption overlay: {compact_tool_result(screenshot)}")
        effective_hold_s = max(
            0.0,
            args.hold_s if args.hold_s > 0 else (args.timeline_hold_s if args.show_stv_timeline else 0.0),
        )
        if effective_hold_s > 0:
            print(
                f"HOLD: test-only caption/timeline evidence remains visible for "
                f"{effective_hold_s:.1f}s"
            )
            hold_deadline = time.monotonic() + effective_hold_s
            while time.monotonic() < hold_deadline:
                time.sleep(min(1.5, hold_deadline - time.monotonic()))
                if args.show_stv_timeline and time.monotonic() < hold_deadline:
                    # Test-only OSD keepalive. MEDIA_PAUSE is idempotent and does
                    # not alter normal SageTV timeout behavior outside this run.
                    tool_call(client, "firetv_key", {"key": "PAUSE"}, timeout=30.0)
        if args.authority == "debug":
            call_dict(client, "dev_set_subtitle_track", {"index": -1}, timeout=30.0)
            wait_snapshot(
                client,
                lambda state: int(state.get("selectedSubtitleTrack", 0)) == -1,
                "caption disable",
                10.0,
            )
        crash = call_dict(client, "dev_crash_probe", timeout=30.0)
        require(not bool(crash.get("signatureDetected")), f"Crash signature detected: {crash}")
        print(f"ANDROID CAPTIONS ({args.player}): PASS")
        return 0
    except Exception as exc:
        print(f"ANDROID CAPTIONS ({args.player}): FAIL\n{exc}", file=sys.stderr)
        try:
            diagnostics = call_dict(client, "collect_playback_diagnostics", {
                "label": f"caption-{args.player}-failure",
            }, timeout=90.0)
            print(json.dumps(diagnostics, indent=2, sort_keys=True), file=sys.stderr)
        except Exception as diagnostic_exc:
            print(f"WARN: caption diagnostics failed: {diagnostic_exc}", file=sys.stderr)
        return 1
    finally:
        try:
            if args.authority == "debug":
                call_dict(client, "dev_set_subtitle_track", {"index": -1}, timeout=30.0)
        except Exception:
            pass
        try:
            call_dict(client, "dev_exit_session", {"stop_app": True}, timeout=30.0)
        except Exception:
            pass
        client.close()


if __name__ == "__main__":
    raise SystemExit(main())
