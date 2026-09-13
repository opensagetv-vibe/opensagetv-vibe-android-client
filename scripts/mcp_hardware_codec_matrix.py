#!/usr/bin/env python3
"""Run the generated Vibe hardware-codec matrix on the commissioned Fire TV.

The harness deliberately requires a real hardware decoder for every positive
row.  Unsupported AV1/VC-1/DRM cases are not silently converted to software
passes.  The malformed row passes only when playback fails safely and the app
remains alive without a crash signature.
"""
from __future__ import annotations

from sagetv_dev_mcp.config import (
    configured_device_serial,
    default_fixture_cases,
    default_server_address,
    default_server_value,
    default_test_value,
)

import argparse
import json
import os
from pathlib import Path
import sys
import time

from mcp_lifecycle_test import MCPProcess, call_dict, initialize, require, wait_automation_ready
from media_warm_cache import mark_recent, media_identity, recent_entry


CASES = (
    ("mpeg2-interlaced-bframes.ts", "video/mpeg2", 2500),
    ("mpeg2-1080i29.97.ts", "video/mpeg2", 5000),
    ("mpeg2-720p59.94.ts", "video/mpeg2", 5000),
    ("mpeg4-part2-bframes.mp4", "video/mp4v-es", 2500),
    ("h264-baseline-avcc.mp4", "video/avc", 2500),
    ("h264-high-bframes-annexb.ts", "video/avc", 2500),
    ("hevc-main-hvcc.mp4", "video/hevc", 2500),
    ("hevc-main10-hdr10.mkv", "video/hevc", 2500),
    ("vp8-profile0.mkv", "video/x-vnd.on2.vp8", 2500),
    ("vp9-profile0.mkv", "video/x-vnd.on2.vp9", 2500),
    ("vp9-profile2-10bit.mkv", "video/x-vnd.on2.vp9", 2500),
    # Wait across the segment boundary so one successful first frame cannot
    # falsely pass a decoder that fails to reconfigure at the second size.
    ("h264-resolution-switch-annexb.ts", "video/avc", 7500),
    ("mpeg2-sequence-resolution-switch.ts", "video/mpeg2", 7500),
    ("h264-ts-timestamp-discontinuity.ts", "video/avc", 7500),
    ("h264-pmt-audio-track-switch.ts", "video/avc", 7500),
)


def validate_mpeg4_missing_pts_injection(plan_path: Path) -> dict:
    """Apply the Kodi PTS-missing rule to recorded extractor packet metadata.

    This intentionally does not corrupt the MP4 or rewrite production player
    timestamps.  The real MP4 is device-tested above; this controlled adapter
    proves that selected PTS values are removed while their independent DTS is
    retained and selected as the bounded fallback.
    """
    plan = json.loads(plan_path.read_text(encoding="utf-8"))
    require(plan.get("status") == "READY_FOR_CONTROLLED_EXTRACTOR_INJECTION",
            f"fault plan is not ready: {plan.get('status')}")
    packets = plan.get("packets") or []
    require(bool(packets), "fault plan has no selected packets")
    for packet in packets:
        require(packet.get("injectedPts") is None,
                f"PTS was not removed: {packet}")
        require(packet.get("sourceDts") is not None,
                f"DTS was not retained: {packet}")
        require(packet.get("sourcePts") != packet.get("sourceDts"),
                f"fixture does not exercise reordered PTS/DTS: {packet}")
        resolved = packet.get("sourceDts") if packet.get("injectedPts") is None \
                else packet.get("injectedPts")
        require(resolved == packet.get("expectedFallbackTimestamp"),
                f"DTS fallback mismatch: {packet}")
    return {
        "name": "mpeg4-part2-missing-pts",
        "status": "PASS_CONTROLLED_EXTRACTOR_INJECTION",
        "fixture": plan.get("fixture"),
        "injectedPacketCount": len(packets),
        "policy": plan.get("fallback"),
        "scope": (
            "Real MPEG-4 B-frame playback is hardware-tested separately; "
            "no production rewrite is enabled because current Media3 exposes "
            "one TrackOutput presentation timestamp rather than Kodi's PTS/DTS pair."
        ),
    }


def compact_state(state: dict) -> dict:
    keys = (
        "health_videoMime", "health_videoDecoder", "health_videoDecoderKind",
        "health_videoWidth", "health_videoHeight", "health_videoRendered",
        "health_videoDropped", "health_playerError", "health_errorState",
        "health_surfaceValid", "health_isPlaying", "mediaTimeMs",
        "health_dataSourceOpenCount", "health_dataSourceLastOpenMonotonicMs",
        "sourceOpenMonotonicMs", "health_videoDecoderInitCount",
        "playerActive", "menuName", "videoDestWidth", "videoDestHeight",
        "popupName",
        "videoUiWidth", "videoUiHeight",
        "gsyEngine", "gsyResolvedEngine",
        "health_audioMime", "health_audioDecoder", "health_audioDecoderKind",
        "health_audioChannels", "health_audioRendered", "health_audioQueuedInput",
        "health_audioPlaybackHeadFrames", "health_audioTrackPresent",
        "health_audioTrackState", "health_audioTrackPlayState",
        "health_audioPassthroughActive", "health_audioPassthroughMode",
        "connected", "serverAddress", "clientId",
        "fullscreenPromotionSent", "fullscreenPromotionCheckScheduled",
        "fullscreenPromotionCheckCount", "fullscreenPromotionCommandCount",
        "fullscreenPromotionStablePreviewCount",
        "fullscreenPromotionStableFullscreenCount",
        "fullscreenPromotionLastDecision",
    )
    return {key: state.get(key) for key in keys}


def compact_start_result(started: dict) -> dict:
    """Retain the health evidence needed to diagnose a failed matrix row."""
    playback = started.get("playback") if isinstance(started.get("playback"), dict) else {}
    return {
        "passed": bool(started.get("passed")),
        "reason": started.get("reason"),
        "launchMethod": started.get("launchMethod"),
        "mediaFileId": started.get("mediaFileId") or (
            (started.get("matched") or {}).get("mediaFileId")
            if isinstance(started.get("matched"), dict) else None
        ),
        "playbackPassed": bool(playback.get("passed")),
        "playbackFailureReason": playback.get("failureReason"),
        "playbackHealth": playback.get("health"),
        "playbackBefore": playback.get("before"),
        "playbackAfter": playback.get("after") or playback.get("state"),
        "fullscreen": started.get("fullscreen"),
        "stalePopupDismissal": started.get("stalePopupDismissal"),
    }


def completed_short_fixture_state(started: dict, expected_mime: str) -> dict | None:
    """Return proof for a short fixture that decoded cleanly before UI probing.

    ``dev_start_video`` validates playback before it validates presentation. A
    sub-ten-second fixture can render every audio/video buffer and return to the
    Browser before the later fullscreen probe observes it. Accept that case
    only when the captured fullscreen state proves clean EOF, the expected
    hardware decoder, and substantial audio/video output. A startup jump to
    the end (one frame/no audio) therefore remains a failure.
    """
    playback = started.get("playback") if isinstance(started.get("playback"), dict) else {}
    fullscreen = started.get("fullscreen") if isinstance(started.get("fullscreen"), dict) else {}
    state = fullscreen.get("state") if isinstance(fullscreen.get("state"), dict) else {}
    try:
        duration_ms = int(state.get("health_durationMs") or 0)
        position_ms = int(state.get("health_playerPositionMs") or -1)
        rendered_video = int(state.get("health_videoRendered") or 0)
        rendered_audio = int(state.get("health_audioRendered") or 0)
        playback_state = int(state.get("health_playbackState") or -1)
    except (TypeError, ValueError):
        return None
    audio_expected = bool(state.get("health_audioMime"))
    clean_eof = (
        bool(playback.get("passed"))
        and playback_state == 4
        and duration_ms > 0
        and position_ms >= max(0, duration_ms - 1000)
        and str(state.get("health_videoMime") or "") == expected_mime
        and str(state.get("health_videoDecoderKind") or "") == "hardware"
        and rendered_video >= 10
        and (not audio_expected or rendered_audio >= 10)
        and bool(state.get("health_surfaceValid"))
        and not bool(state.get("health_errorState"))
        and not str(state.get("health_playerError") or "")
    )
    return state if clean_eof else None


def supports_vp9_profile2(codec_profiles: str) -> bool:
    """Return true only when a hardware VP9 decoder advertises Profile 2 (value 4).

    NVIDIA Shield firmware advertises VP9 decoding but commonly lists only
    Android profile value 1 (Profile 0). Sending Profile 2 to that decoder
    causes a vendor codec failure and can block the debug receiver long enough
    to create an ANR, so MIME-only capability checks are insufficient.
    """
    for entry in str(codec_profiles or "").split("|"):
        fields = entry.split(",")
        if len(fields) < 4 or fields[2].lower() != "video/x-vnd.on2.vp9" or fields[3] != "hw":
            continue
        profile_field = next((value for value in fields if value.startswith("profiles=")), "")
        pairs = profile_field.removeprefix("profiles=").split(".")
        if any(pair.split("_", 1)[0] == "4" for pair in pairs if "_" in pair):
            return True
    return False


def crash_changed(baseline: dict, current: dict) -> bool:
    """Reject only a crash/restart added during this run, not stale logcat."""
    baseline_pids = tuple(sorted(str(value) for value in (baseline.get("pids") or [])))
    current_pids = tuple(sorted(str(value) for value in (current.get("pids") or [])))
    process_gone = baseline.get("running") is True and current.get("running") is False
    process_restart = bool(baseline_pids and current_pids and baseline_pids != current_pids)
    baseline_signature = str(baseline.get("signatureFingerprint") or "")
    current_signature = str(current.get("signatureFingerprint") or "")
    new_signature = (bool(current.get("signatureDetected")) and bool(current_signature)
                     and current_signature != baseline_signature)
    return bool(process_gone or process_restart or new_signature)


def is_fullscreen_playback(state: dict) -> bool:
    if not bool(state.get("playerActive")):
        return False
    if "osd" in str(state.get("menuName") or "").strip().lower():
        return True
    try:
        width = int(state.get("videoDestWidth") or 0)
        height = int(state.get("videoDestHeight") or 0)
        ui_width = int(state.get("videoUiWidth") or 0)
        ui_height = int(state.get("videoUiHeight") or 0)
    except (TypeError, ValueError):
        return False
    if min(width, height, ui_width, ui_height) <= 0:
        return False
    return not (width * 100 < ui_width * 75 and height * 100 < ui_height * 75)


def active_fullscreen_replacement_state(client: MCPProcess) -> tuple[dict, dict]:
    """Keep an established fullscreen player active for the next Watch.

    Stopping every short fixture returns SageMC to Home and makes the following
    Watch visibly traverse its preview window before entering fullscreen.  A
    direct Watch while the OSD/player is active is the normal SageTV media
    replacement path and avoids that artificial matrix-only delay.
    """
    state = call_dict(client, "dev_player_state", timeout=30.0)
    if (bool(state.get("connected"))
            and str(state.get("popupName") or "").strip()):
        return state, {"method": "replace_behind_stopped_popup"}
    if (bool(state.get("connected"))
            and bool(state.get("playerActive"))
            and not str(state.get("popupName") or "").strip()):
        # Short fixtures commonly reach clean EOF before the next row starts.
        # SageMC then retains the fullscreen player even though isPlaying is
        # false (and the surface may already be released).  A new Watch is the
        # safe replacement operation.  STOP/BACK here can close the UI context
        # and make the following exact MediaFile request fail spuriously.
        return state, {
            "method": (
                "replace_active_fullscreen"
                if is_fullscreen_playback(state)
                else "replace_retained_player"
            )
        }
    return state, {"method": "requires_idle_cleanup"}


def dismiss_replaced_stopped_popup(client: MCPProcess) -> dict:
    """Dismiss the old SageMC StopPopup after a replacement is rendering."""
    before = call_dict(client, "dev_player_state", timeout=30.0)
    if not str(before.get("popupName") or "").strip():
        return {"method": "popup_already_cleared", "state": compact_state(before)}
    call_dict(client, "dev_sage_command", {"command": "back"}, timeout=30.0)
    deadline = time.monotonic() + 8.0
    last = before
    while time.monotonic() < deadline:
        last = call_dict(client, "dev_player_state", timeout=30.0)
        clean_output = (
            int(last.get("health_videoRendered") or 0) >= 10
            and not bool(last.get("health_errorState"))
            and not str(last.get("health_playerError") or "")
        )
        if (bool(last.get("connected"))
                and not str(last.get("popupName") or "").strip()
                and (bool(last.get("health_isPlaying")) or clean_output)):
            return {
                "method": "back_after_replacement",
                "completedBeforeObservation": not bool(last.get("health_isPlaying")),
                "state": compact_state(last),
            }
        time.sleep(0.15)
    raise RuntimeError(f"replacement popup did not clear over active playback: {compact_state(last)}")


def ensure_idle(client: MCPProcess, args: argparse.Namespace) -> dict:
    """Stop the prior short fixture or rebuild only the MiniClient session."""
    state = call_dict(client, "dev_player_state", timeout=30.0)
    if str(state.get("popupName") or "").strip():
        # BACK dismisses SageMC's completed-playback popup without executing
        # its Stop/Home actions, either of which can close this MiniClient UI
        # context and invalidate the next exact Watch.
        call_dict(client, "dev_sage_command", {"command": "back"}, timeout=30.0)
        deadline = time.monotonic() + 12.0
        while time.monotonic() < deadline:
            state = call_dict(client, "dev_player_state", timeout=30.0)
            if (not str(state.get("popupName") or "").strip()
                    and not bool(state.get("playerActive"))):
                return {"method": "closed_stopped_popup_back"}
            time.sleep(0.20)
    if not bool(state.get("playerActive")):
        if "osd" in str(state.get("menuName") or "").strip().lower():
            call_dict(client, "dev_sage_command", {"command": "back"}, timeout=30.0)
            deadline = time.monotonic() + 5.0
            while time.monotonic() < deadline:
                state = call_dict(client, "dev_player_state", timeout=30.0)
                if "osd" not in str(state.get("menuName") or "").strip().lower():
                    return {"method": "closed_inactive_playback_osd", "command": "back"}
                time.sleep(0.20)
            return {"method": "inactive_playback_osd_back_timeout", "command": "back"}
        return {"method": "already_idle"}
    call_dict(client, "dev_sage_command", {"command": "stop"}, timeout=30.0)
    deadline = time.monotonic() + 18.0
    while time.monotonic() < deadline:
        state = call_dict(client, "dev_player_state", timeout=30.0)
        if not bool(state.get("playerActive")):
            return {"method": "sage_stop"}
        time.sleep(0.25)

    # A completed short item can leave an STV/player teardown pending. Rebuild
    # the control session rather than attributing that race to the next codec.
    call_dict(client, "dev_exit_session", {"stop_app": False}, timeout=30.0)
    call_dict(client, "dev_connect_server", {
        "address": args.server_address,
        "port": args.server_port,
        "save": False,
    }, timeout=30.0)
    wait_automation_ready(client, timeout_s=60.0)
    state = call_dict(client, "dev_player_state", timeout=30.0)
    require(not bool(state.get("playerActive")),
            f"player remained active after bounded session rebuild: {compact_state(state)}")
    return {"method": "session_rebuild"}


def restore_post_test_menu(client: MCPProcess) -> dict:
    """Leave neither active playback nor a stale playback OSD after a run."""
    state = call_dict(client, "dev_player_state", timeout=30.0)
    commands = []
    if bool(state.get("playerActive")):
        call_dict(client, "dev_sage_command", {"command": "stop"}, timeout=30.0)
        commands.append("stop")
        time.sleep(0.50)
        state = call_dict(client, "dev_player_state", timeout=30.0)
    if str(state.get("popupName") or "").strip():
        call_dict(client, "dev_sage_command", {"command": "back"}, timeout=30.0)
        commands.append("back_popup")
        time.sleep(0.50)
        state = call_dict(client, "dev_player_state", timeout=30.0)
    if "osd" in str(state.get("menuName") or "").strip().lower():
        call_dict(client, "dev_sage_command", {"command": "back"}, timeout=30.0)
        commands.append("back")
        time.sleep(0.50)
        state = call_dict(client, "dev_player_state", timeout=30.0)
    return {"commands": commands, "state": compact_state(state)}


def start_fixture(client: MCPProcess, args: argparse.Namespace, name: str,
                  path: str, verify_ms: int, media_file_ids: dict[str, list[int]],
                  dismiss_stale_stop_popup: bool = False,
                  timeout_s: float | None = None) -> dict:
    """Prefer stock-compatible indexed MediaFile control, then negotiated path."""
    effective_timeout_s = args.timeout_s if timeout_s is None else timeout_s
    exact_first = (
        args.media_selection_mode == "vibe_exact_path"
        or (args.media_selection_mode == "auto" and not args.webserver_installed)
    )
    if exact_first:
        direct = call_dict(client, "dev_play_server_path", {
            "server_path": path,
            "timeout_s": effective_timeout_s,
            "verify_ms": verify_ms,
            "restart_from_beginning": True,
        }, timeout=effective_timeout_s + 40.0)
        direct["launchMethod"] = "vibe_exact_path"
        if bool(direct.get("passed")):
            return direct
        if not args.webserver_installed:
            return direct
        direct_failure = direct
    else:
        direct_failure = None
    known_ids = media_file_ids.get(Path(name).stem, [])
    if known_ids:
        indexed = call_dict(client, "dev_play_media_file_id", {
            "media_file_id": sorted(known_ids)[-1],
            "timeout_s": effective_timeout_s,
            "verify_ms": verify_ms,
            "dismiss_stale_stop_popup": bool(dismiss_stale_stop_popup),
        }, timeout=effective_timeout_s + 40.0)
        indexed["batchResolvedMediaFileIds"] = sorted(known_ids)
    else:
        indexed = call_dict(client, "dev_play_video", {
        # SageTV AddMediaFile exposes the filename stem as MediaTitle even
        # though the segment path retains the extension.
            "video_name": Path(name).stem,
            "timeout_s": effective_timeout_s,
            "verify_ms": verify_ms,
        }, timeout=effective_timeout_s + 40.0)
    if (str(indexed.get("reason") or "") == "ambiguous_video_name"
            and str(indexed.get("matchMode") or "").startswith("exact")):
        exact_ids = sorted({
            int(match.get("mediaFileId"))
            for match in (indexed.get("matches") or [])
            if int(match.get("mediaFileId") or 0) > 0
        })
        if exact_ids:
            duplicate_result = indexed
            indexed = call_dict(client, "dev_play_media_file_id", {
                "media_file_id": exact_ids[-1],
                "timeout_s": effective_timeout_s,
                "verify_ms": verify_ms,
                "dismiss_stale_stop_popup": bool(dismiss_stale_stop_popup),
            }, timeout=effective_timeout_s + 40.0)
            indexed["duplicateExactTitleResult"] = duplicate_result
            indexed["selectedDuplicateMediaFileId"] = exact_ids[-1]
    if bool(indexed.get("passed")):
        indexed["launchMethod"] = "stock_web_indexed_mediafile"
        if direct_failure is not None:
            indexed["vibeExactPathResult"] = direct_failure
        return indexed
    if str(indexed.get("reason") or "") != "video_not_found":
        return indexed
    if args.media_selection_mode == "stock_web":
        indexed["launchMethod"] = "stock_web_indexed_mediafile"
        indexed["exactPathFallbackSuppressed"] = True
        return indexed
    direct = call_dict(client, "dev_play_server_path", {
        "server_path": path,
        "timeout_s": effective_timeout_s,
        "verify_ms": verify_ms,
        "restart_from_beginning": True,
    }, timeout=effective_timeout_s + 40.0)
    direct["launchMethod"] = "auto_vibe_exact_path_fallback"
    direct["indexedResult"] = indexed
    return direct


def rebuild_test_session(client: MCPProcess, args: argparse.Namespace) -> dict:
    """Recover deterministically after a decoder error or debug-receiver ANR."""
    clean = call_dict(client, "dev_prepare_clean_start", {
        "wake": True, "graceful_timeout_s": 1.0,
    }, timeout=30.0)
    require(bool(clean.get("readyToLaunch")), f"clean recovery failed: {clean}")
    player_config = {
        "player": args.player,
        "streaming": args.streaming,
        "decoding": "hardware",
    }
    if args.player == "gsyplayer":
        player_config["gsy_engine"] = args.gsy_engine
    call_dict(client, "dev_set_player_config", player_config, timeout=30.0)
    call_dict(client, "dev_connect_server", {
        "address": args.server_address,
        "port": args.server_port,
        "save": False,
    }, timeout=30.0)
    wait_automation_ready(client, timeout_s=60.0)
    state = call_dict(client, "dev_player_state", timeout=30.0)
    require(bool(state.get("connected")), f"recovery did not reconnect: {compact_state(state)}")
    return {"method": "clean_app_session_rebuild", "state": compact_state(state)}


def _fresh_source_opened(before: dict, current: dict) -> bool:
    if not bool(before.get("playerActive")):
        return True
    for key in (
        "health_dataSourceLastOpenMonotonicMs",
        "sourceOpenMonotonicMs",
    ):
        old_value = int(before.get(key) or 0)
        new_value = int(current.get(key) or 0)
        if new_value > old_value:
            return True
    old_count = int(before.get("health_dataSourceOpenCount") or 0)
    new_count = int(current.get("health_dataSourceOpenCount") or 0)
    return new_count != old_count


def wait_for_expected_decoder(client: MCPProcess, expected_mime: str,
                              before: dict, timeout_s: float = 15.0) -> dict:
    """Wait for a fresh datasource open and advancing hardware decoder."""
    deadline = time.monotonic() + max(1.0, timeout_s)
    last = {}
    initial_rendered = None
    while time.monotonic() < deadline:
        last = call_dict(client, "dev_player_state", timeout=30.0)
        mime = str(last.get("health_videoMime") or "")
        rendered = int(last.get("health_videoRendered") or 0)
        if mime == expected_mime and _fresh_source_opened(before, last):
            if initial_rendered is None:
                initial_rendered = rendered
            if (last.get("health_videoDecoderKind") == "hardware"
                    and not last.get("health_playerError")
                    and not bool(last.get("health_errorState"))
                    and rendered > initial_rendered):
                return last
        time.sleep(0.20)
    return last


def main() -> int:
    configured_codec_roots = [
        str(case.get("path") or "").rstrip("/")
        for case in default_fixture_cases(
            mode="hardware_codec_matrix", enabled_only=True
        )
        if str(case.get("path_type") or "") == "server_root"
        and str(case.get("path") or "").strip()
    ]
    parser = argparse.ArgumentParser(description="Run generated codec fixtures with strict hardware evidence")
    parser.add_argument("--server-address", default=default_server_address())
    parser.add_argument("--server-port", type=int, default=int(default_server_value("miniclient_port", 31099)))
    parser.add_argument(
        "--media-selection-mode",
        choices=("auto", "stock_web", "vibe_exact_path"),
        default=str(default_server_value("media_selection_mode", "auto")),
        help="How this server resolves configured media paths",
    )
    parser.add_argument(
        "--webserver-installed", action=argparse.BooleanOptionalAction,
        default=bool(default_server_value("webserver_installed", False)),
        help="Whether Sagex/stock Web MediaFile lookup is available on this server",
    )
    parser.add_argument(
        "--server-root",
        default=(configured_codec_roots[0] if configured_codec_roots else
                 "/var/media/OpenSageTV_Vibe_Tests/OpenSageTV-Vibe-Hardware-Codec-Test"),
    )
    parser.add_argument(
        "--include-disabled-fixture", action="store_true",
        help="Explicitly run hardware_codec_matrix even when disabled in firetv.toml",
    )
    parser.add_argument(
        "--player", choices=("media3", "exoplayer", "gsyplayer"), default="media3"
    )
    parser.add_argument(
        "--gsy-engine", choices=("media3", "legacy_exo"), default="media3",
        help="hardware delegate to prove when --player gsyplayer is selected",
    )
    parser.add_argument("--streaming", choices=("pull", "push"), default="pull")
    parser.add_argument("--timeout-s", type=float, default=60.0)
    parser.add_argument(
        "--storage-warmup-timeout-s", type=float,
        default=float(default_test_value("storage_warmup_timeout_seconds", 120)),
        help="First-access allowance used only until a fixture proves normally warm",
    )
    parser.add_argument(
        "--slow-startup-ms", type=int,
        default=int(default_test_value("storage_slow_startup_ms", 10000)),
        help="First fixture startup above this duration is discarded and repeated",
    )
    parser.add_argument(
        "--storage-warm-cache-s", type=float,
        default=float(default_test_value("storage_warm_cache_seconds", 600)),
        help="Sliding recent-use interval for normal startup gates",
    )
    parser.add_argument(
        "--skip-storage-warmup", action="store_true",
        help="Disable per-file warm-up only for intentional cold-start testing",
    )
    parser.add_argument(
        "--start-at",
        choices=tuple(name for name, _mime, _verify_ms in CASES),
        help="Resume at this fixture instead of repeating previously observed rows",
    )
    parser.add_argument(
        "--max-cases", type=int,
        help="Run only this many positive codec rows (focused physical validation)",
    )
    args = parser.parse_args()
    if args.player != "gsyplayer" and args.gsy_engine != "media3":
        parser.error("--gsy-engine applies only to --player gsyplayer")
    if args.media_selection_mode == "stock_web" and not args.webserver_installed:
        parser.error("stock_web requires --webserver-installed/config true")
    if args.max_cases is not None and args.max_cases < 1:
        parser.error("--max-cases must be at least 1")
    if args.storage_warmup_timeout_s < 15.0 or args.storage_warmup_timeout_s > 300.0:
        parser.error("--storage-warmup-timeout-s must be between 15 and 300 seconds")
    if args.slow_startup_ms < 1000 or args.slow_startup_ms > 120000:
        parser.error("--slow-startup-ms must be between 1000 and 120000")
    if args.storage_warm_cache_s < 0 or args.storage_warm_cache_s > 3600:
        parser.error("--storage-warm-cache-s must be between 0 and 3600 seconds")

    results: list[dict] = []
    storage_warmup: list[dict] = []
    artifact_root = Path(os.environ.get("SAGETV_ARTIFACT_DIR", "artifacts/firetv"))
    artifact_root.mkdir(parents=True, exist_ok=True)
    resume_suffix = f"-from-{Path(args.start_at).stem}" if args.start_at else ""
    limit_suffix = f"-first-{args.max_cases}" if args.max_cases else ""
    delegate_suffix = f"-{args.gsy_engine}" if args.player == "gsyplayer" else ""
    selected_device_alias = os.environ.get("SAGETV_TEST_DEVICE_ALIAS", "non_pro").strip() or "non_pro"
    device_tag = "aftmm" if selected_device_alias == "non_pro" else selected_device_alias.replace("_", "-")
    artifact = artifact_root / (
        f"hardware-codec-matrix-{args.player}{delegate_suffix}-{args.streaming}"
        f"-{device_tag}{resume_suffix}{limit_suffix}.json"
    )

    def checkpoint(status: str) -> None:
        artifact.write_text(json.dumps({
            "status": status,
            "deviceAlias": selected_device_alias,
            "deviceRestriction": configured_device_serial(),
            "player": args.player,
            "gsyEngine": args.gsy_engine if args.player == "gsyplayer" else "",
            "streaming": args.streaming,
            "decoding": "hardware",
            "startAt": args.start_at or CASES[0][0],
            "partialRun": bool(args.start_at or args.max_cases),
            "storageWarmupEnabled": not args.skip_storage_warmup,
            "storageWarmup": storage_warmup,
            "results": results,
        }, indent=2, sort_keys=True), encoding="utf-8")

    if not args.include_disabled_fixture and not configured_codec_roots:
        results.append({
            "name": "hardware_codec_matrix",
            "status": "SKIPPED_CONFIG_DISABLED",
            "reason": (
                "fixture or hardware_codec_matrix mode is disabled in "
                "config/firetv.toml"
            ),
        })
        checkpoint("SKIPPED_CONFIG_DISABLED")
        print("SKIP: hardware codec fixture/mode disabled in firetv.toml")
        return 0

    selected_cases = CASES
    if args.start_at:
        start_index = next(
            index for index, case in enumerate(CASES) if case[0] == args.start_at
        )
        selected_cases = CASES[start_index:]
    if args.max_cases is not None:
        selected_cases = selected_cases[:args.max_cases]
    checkpoint("STARTING")
    client = MCPProcess()
    try:
        negotiated, _ = initialize(client)
        print(f"PASS: MCP initialize handshake ({negotiated})")
        connection = call_dict(client, "adb_connect", timeout=30.0)
        serial = str(connection.get("serial") or connection.get("device") or "")
        required_serial = configured_device_serial()
        require(serial.endswith(required_serial) or required_serial in json.dumps(connection),
                f"physical codec matrix requires configured device {selected_device_alias} "
                f"({required_serial}), got: {connection}")
        clean = call_dict(client, "dev_prepare_clean_start", {
            "wake": True, "graceful_timeout_s": 2.0,
        }, timeout=30.0)
        require(bool(clean.get("readyToLaunch")), f"clean start failed: {clean}")
        player_config = {
            "player": args.player,
            "streaming": args.streaming,
            "decoding": "hardware",
        }
        if args.player == "gsyplayer":
            player_config["gsy_engine"] = args.gsy_engine
        call_dict(client, "dev_set_player_config", player_config, timeout=30.0)
        call_dict(client, "dev_connect_server", {
            "address": args.server_address,
            "port": args.server_port,
            "save": False,
        }, timeout=30.0)
        wait_automation_ready(client, timeout_s=60.0)
        baseline_crash = call_dict(client, "dev_crash_probe", timeout=30.0)
        capabilities = call_dict(client, "dev_codec_capabilities", timeout=30.0)
        profiles = str(capabilities.get("codecProfiles") or "")
        if args.webserver_installed and args.media_selection_mode != "vibe_exact_path":
            resolved = call_dict(client, "dev_resolve_video_names", {
                "video_names": [
                    *[Path(name).stem for name, _mime, _verify_ms in selected_cases],
                    Path("h264-truncated-start.ts").stem,
                ],
            }, timeout=90.0)
            media_file_ids = {
                name: [
                    int(match.get("mediaFileId"))
                    for match in (details.get("matches") or [])
                    if int(match.get("mediaFileId") or 0) > 0
                ]
                for name, details in (resolved.get("matches") or {}).items()
            }
        else:
            media_file_ids = {}

        first_row = True
        for name, expected_mime, verify_ms in selected_cases:
            path = args.server_root.rstrip("/") + "/" + name
            row = {"name": name, "serverPath": path, "expectedMime": expected_mime}
            if name == "vp9-profile2-10bit.mkv" and not supports_vp9_profile2(profiles):
                row.update({
                    "status": "SKIPPED_UNSUPPORTED_HARDWARE_PROFILE",
                    "reason": "no hardware VP9 decoder advertises Android VP9 Profile 2",
                })
                results.append(row)
                checkpoint("RUNNING")
                print("SKIP: vp9-profile2-10bit.mkv: hardware advertises no VP9 Profile 2")
                first_row = False
                continue
            try:
                before, replacement = active_fullscreen_replacement_state(client)
                if replacement["method"] == "requires_idle_cleanup":
                    row["priorCleanup"] = ensure_idle(client, args)
                    before = call_dict(client, "dev_player_state", timeout=30.0)
                else:
                    row["priorCleanup"] = replacement
                row["before"] = compact_state(before)
                warm_identity = media_identity(
                    server=args.server_address,
                    port=args.server_port,
                    server_path=path,
                )
                warm_cache_path = artifact_root / ".media-warm-cache.json"
                recent_warm = None if args.skip_storage_warmup else recent_entry(
                    warm_cache_path, warm_identity, args.storage_warm_cache_s
                )
                adaptive_probe = bool(not args.skip_storage_warmup and recent_warm is None)
                warm = {
                    "name": name,
                    "serverPath": path,
                    "enabled": not args.skip_storage_warmup,
                    "recentWarmHit": recent_warm is not None,
                    "recentWarmAgeSeconds": recent_warm.get("ageSeconds") if recent_warm else None,
                    "slowStartupThresholdMs": args.slow_startup_ms,
                    "outcome": "disabled_for_intentional_cold_start_test" if args.skip_storage_warmup
                               else "recent_media_uses_normal_gates" if recent_warm
                               else "pending_first_start",
                }
                storage_warmup.append(warm)
                startup_started = time.monotonic()
                started = start_fixture(
                    client,
                    args,
                    name,
                    path,
                    verify_ms,
                    media_file_ids,
                    dismiss_stale_stop_popup=(
                        replacement["method"] == "replace_behind_stopped_popup"
                    ),
                    timeout_s=max(args.timeout_s, args.storage_warmup_timeout_s)
                              if adaptive_probe else None,
                )
                startup_ms = int(round((time.monotonic() - startup_started) * 1000.0))
                row["startResult"] = compact_start_result(started)
                if (replacement["method"] == "replace_behind_stopped_popup"
                        and str(call_dict(client, "dev_player_state", timeout=30.0).get(
                            "popupName"
                        ) or "").strip()):
                    row["popupDismissal"] = dismiss_replaced_stopped_popup(client)
                completed_state = completed_short_fixture_state(started, expected_mime)
                require(bool(started.get("passed")) or completed_state is not None,
                        f"playback did not pass: {started.get('reason')}")
                if not args.skip_storage_warmup:
                    mark_recent(warm_cache_path, warm_identity, startup_ms=startup_ms)
                    warm["slidingTtlRefreshed"] = True
                    warm["firstStartupMs"] = startup_ms
                    if adaptive_probe and startup_ms > args.slow_startup_ms:
                        warm["outcome"] = "slow_start_discarded_and_retried"
                        warm["discardedStartupMs"] = startup_ms
                        rebuild_test_session(client, args)
                        baseline_crash = call_dict(client, "dev_crash_probe", timeout=30.0)
                        before = call_dict(client, "dev_player_state", timeout=30.0)
                        row["beforeMeasuredRetry"] = compact_state(before)
                        retry_started = time.monotonic()
                        started = start_fixture(
                            client, args, name, path, verify_ms, media_file_ids,
                        )
                        measured_startup_ms = int(round(
                            (time.monotonic() - retry_started) * 1000.0
                        ))
                        row["startResult"] = compact_start_result(started)
                        completed_state = completed_short_fixture_state(started, expected_mime)
                        require(bool(started.get("passed")) or completed_state is not None,
                                f"measured retry did not pass: {started.get('reason')}")
                        warm["measuredRetryStartupMs"] = measured_startup_ms
                        mark_recent(
                            warm_cache_path, warm_identity,
                            startup_ms=measured_startup_ms,
                        )
                    elif adaptive_probe:
                        warm["outcome"] = "normal_start_used_as_measured_result"
                state = completed_state or wait_for_expected_decoder(client, expected_mime, before)
                actual_mime = str(state.get("health_videoMime") or "")
                require(actual_mime == expected_mime,
                        f"expected MIME {expected_mime}, got {actual_mime or '<empty>'}")
                require(state.get("health_videoDecoderKind") == "hardware",
                        f"not hardware decoded: {state.get('health_videoDecoder')}")
                if args.player == "gsyplayer":
                    require(str(state.get("gsyResolvedEngine") or "") == args.gsy_engine,
                            f"GSY resolved {state.get('gsyResolvedEngine')}, expected {args.gsy_engine}")
                require(not state.get("health_playerError") and not bool(state.get("health_errorState")),
                        f"player error: {state.get('health_playerError')}")
                if completed_state is None:
                    require(not str(state.get("popupName") or "").strip(),
                            f"playback obscured by popup: {state.get('popupName')}")
                row.update({
                    "status": "PASS_SHORT_FIXTURE_CLEAN_EOF" if completed_state is not None else "PASS",
                    "state": compact_state(state),
                })
                print(f"PASS: {name}: {actual_mime} -> {state.get('health_videoDecoder')}")
            except Exception as exc:
                row.update({"status": "FAIL", "reason": str(exc)})
                print(f"FAIL: {name}: {exc}", file=sys.stderr)
                try:
                    row["failureState"] = compact_state(
                        call_dict(client, "dev_player_state", timeout=15.0)
                    )
                except Exception as state_exc:
                    row["failureStateError"] = str(state_exc)
                try:
                    row["failureRecovery"] = rebuild_test_session(client, args)
                    # The bounded recovery intentionally replaces the app PID;
                    # establish a new crash baseline so later containment rows
                    # are judged only against unplanned restarts/signatures.
                    baseline_crash = call_dict(client, "dev_crash_probe", timeout=30.0)
                except Exception as recovery_exc:
                    row["failureRecovery"] = {"failed": True, "reason": str(recovery_exc)}
            results.append(row)
            checkpoint("RUNNING")
            first_row = False

        # Stock SageTV does not retain H.263 on this Pull capability path; it
        # negotiates MPEG-2 output. Keep the generated source for server/
        # compatibility work, but never misreport that result as native H.263
        # hardware-decoder proof.
        results.append({
            "name": "h263-baseline.3gp",
            "status": "SKIPPED_STOCK_SERVER_NEGOTIATES_MPEG2",
            "reason": "stock SageTV changes the wire codec, so this is not native H.263 decoder evidence",
        })
        checkpoint("RUNNING")

        malformed_path = args.server_root.rstrip("/") + "/h264-truncated-start.ts"
        malformed = {"name": "h264-truncated-start.ts", "serverPath": malformed_path}
        try:
            malformed["priorCleanup"] = ensure_idle(client, args)
            started = start_fixture(
                client,
                args,
                "h264-truncated-start.ts",
                malformed_path,
                2000,
                media_file_ids,
            )
            crash = call_dict(client, "dev_crash_probe", timeout=30.0)
            require(not crash_changed(baseline_crash, crash), f"new crash/restart: {crash}")
            status = call_dict(client, "dev_app_status", timeout=30.0)
            require(bool(status.get("running")), f"app died on malformed fixture: {status}")
            malformed.update({
                "status": "PASS_SAFE_FAILURE_OR_RECOVERY",
                "playbackPassed": bool(started.get("passed")),
                "crash": crash,
            })
            print("PASS: malformed startup failed/recovered without process death")
        except Exception as exc:
            malformed.update({"status": "FAIL", "reason": str(exc)})
            print(f"FAIL: malformed containment: {exc}", file=sys.stderr)
        results.append(malformed)
        checkpoint("RUNNING")

        fault_plan = Path(os.environ.get(
            "SAGETV_CODEC_FIXTURE_DIR", "artifacts/test-media/hardware-codec"
        )) / "mpeg4-part2-missing-pts.fault-plan.json"
        try:
            injection = validate_mpeg4_missing_pts_injection(fault_plan)
            results.append(injection)
            print("PASS: controlled MPEG-4 missing-PTS extractor injection")
        except Exception as exc:
            results.append({
                "name": "mpeg4-part2-missing-pts",
                "status": "FAIL",
                "reason": str(exc),
            })
            print(f"FAIL: MPEG-4 missing-PTS injection: {exc}", file=sys.stderr)

        results.extend([
            {
                "name": "av1-main8.mkv",
                "status": "SKIPPED_UNSUPPORTED_HARDWARE" if "video/av01,hw" not in profiles else "NOT_RUN",
                "reason": (
                    f"{capabilities.get('codecDevice') or selected_device_alias}/"
                    f"API-{capabilities.get('codecApi') or 'unknown'} advertises no AV1 hardware decoder"
                ),
            },
            {
                "name": "secure-decoder-drm",
                "status": "SKIPPED_AUTHORIZED_ASSET_REQUIRED",
                "reason": "clear FFmpeg media cannot validate a secure DRM session",
            },
        ])
        post_test_cleanup = restore_post_test_menu(client)
        crash = call_dict(client, "dev_crash_probe", timeout=30.0)
        passed = all(str(row.get("status", "")).startswith("PASS") or
                     str(row.get("status", "")).startswith("SKIPPED") or
                     str(row.get("status", "")).startswith("PENDING")
                     for row in results) and not crash_changed(baseline_crash, crash)
        report = {
            "passed": passed,
            "deviceRestriction": required_serial,
            "deviceAlias": selected_device_alias,
            "player": args.player,
            "gsyEngine": args.gsy_engine if args.player == "gsyplayer" else "",
            "streaming": args.streaming,
            "decoding": "hardware",
            "mediaSelectionMode": args.media_selection_mode,
            "webserverInstalled": args.webserver_installed,
            "storageWarmup": storage_warmup,
            "capabilityDevice": capabilities.get("codecDevice"),
            "capabilityApi": capabilities.get("codecApi"),
            "results": results,
            "baselineCrashProbe": baseline_crash,
            "postTestCleanup": post_test_cleanup,
            "finalCrashProbe": crash,
        }
        artifact.write_text(json.dumps(report, indent=2, sort_keys=True), encoding="utf-8")
        print(f"{'PASS' if passed else 'FAIL'}: evidence {artifact}")
        return 0 if passed else 1
    except Exception as exc:
        print(f"HARDWARE CODEC MATRIX: FAIL: {exc}", file=sys.stderr)
        return 1
    finally:
        try:
            restore_post_test_menu(client)
        except Exception:
            pass
        try:
            call_dict(client, "dev_exit_session", {"stop_app": False}, timeout=30.0)
        except Exception:
            pass
        client.close()


if __name__ == "__main__":
    raise SystemExit(main())
