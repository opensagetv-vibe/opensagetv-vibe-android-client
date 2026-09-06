from __future__ import annotations
from datetime import datetime
import atexit
from pathlib import Path
import hashlib
import json
import re
import subprocess
import tempfile
import time
from urllib.parse import unquote, urlparse
from mcp.server import MCPServer

from .adb import AdbClient
from .config import load_config
from .sagex_api import SagexApiClient, SagexApiError
from .sequence import parse_sequence_script

cfg = load_config()
adb = AdbClient(serial=cfg.device, dev_package=cfg.dev_package, adb=cfg.adb, aapt=cfg.aapt)
atexit.register(adb.close)
mcp = MCPServer("SageTV Dev Fire TV MCP")
PROJECT_ROOT = Path(__file__).resolve().parents[3]
SEEK_FIXTURE_DIR = PROJECT_ROOT / "artifacts" / "test-media"



def _snapshot_media_time(state: dict) -> int:
    value = state.get("sageTimelineMs", state.get("mediaTimeMs"))
    if value is None:
        raise RuntimeError(f"No active player mediaTimeMs in snapshot: {state}")
    return int(value)


def _snapshot_player_position(state: dict) -> int:
    """Return the backend-local player position used by MiniPlayerPlugin.seek().

    SageTV's visible timeline is normally ``serverAnchorMs + playerPositionMs``. Direct
    debug seeks call ``MiniPlayerPlugin.seek(targetMs)``, so verification must use the
    backend-local position rather than the anchored SageTV timeline.
    """
    value = state.get("health_playerPositionMs")
    if value is not None:
        try:
            parsed = int(value)
            if parsed >= 0:
                return parsed
        except (TypeError, ValueError):
            pass

    timeline = state.get("sageTimelineMs", state.get("mediaTimeMs"))
    anchor = state.get("serverAnchorMs")
    if timeline is not None and anchor is not None:
        try:
            return max(0, int(timeline) - int(anchor))
        except (TypeError, ValueError):
            pass
    if timeline is not None:
        return max(0, int(timeline))
    raise RuntimeError(f"No active player position in snapshot: {state}")


def _require_debug_seek_coordinate_version() -> int:
    """Require the debug APK that uses backend-local coordinates for direct seeks."""
    state = adb.player_state_snapshot()
    try:
        version = int(state.get("debugStatusVersion", 0) or 0)
    except (TypeError, ValueError):
        version = 0
    if version < 6:
        raise RuntimeError(
            f"Installed debug APK is too old for verified direct-seek coordinates "
            f"(debugStatusVersion={version}, need >=6). Rebuild/install v0.5.57 or newer."
        )
    return version


def _require_debug_watchdog_capacity(requested_ms: int) -> int:
    """Require the installed debug APK to advertise the requested per-step watchdog capacity."""
    requested_ms = max(250, min(int(requested_ms), 300000))
    state = adb.player_state_snapshot()
    try:
        supported_ms = int(state.get("maxRecoveryWatchdogMs", 0) or 0)
    except (TypeError, ValueError):
        supported_ms = 0
    if supported_ms < requested_ms:
        version = state.get("debugStatusVersion", "unknown")
        raise RuntimeError(
            f"Installed debug APK cannot guarantee a {requested_ms} ms per-step watchdog "
            f"(debugStatusVersion={version}, maxRecoveryWatchdogMs={supported_ms}). "
            "Rebuild/install the current debug APK before running long media observations."
        )
    return supported_ms


def _parse_trap_recent(value: object) -> list[dict]:
    text = str(value or "").strip()
    if not text:
        return []
    events: list[dict] = []
    keys = (
        "sequence", "event", "monotonicMs", "snapshotLagMs", "playerPositionMs",
        "videoRendered", "videoQueuedInput", "videoDecoderInitCount", "videoDecoderReleaseCount",
        "audioRendered", "audioQueuedInput", "audioDecoderInitCount", "audioDecoderReleaseCount",
        "audioHeadFrames", "audioSessionId", "playbackState", "isPlaying", "isLoading",
        "videoExpected", "audioExpected", "surfaceValid", "probeSupported",
        "videoDecoderKind", "audioDecoderKind",
    )
    bool_keys = {"isPlaying", "isLoading", "videoExpected", "audioExpected", "surfaceValid", "probeSupported"}
    string_keys = {"event", "videoDecoderKind", "audioDecoderKind"}
    for raw in text.split("|"):
        parts = raw.split("@")
        if len(parts) != len(keys):
            continue
        event: dict = {}
        for index, key in enumerate(keys):
            if key in string_keys:
                event[key] = parts[index]
                continue
            try:
                value_int = int(parts[index])
            except (TypeError, ValueError):
                event[key] = parts[index]
                continue
            event[key] = bool(value_int) if key in bool_keys else value_int
        events.append(event)
    return events


def _compact_state(state: dict) -> dict:
    keys = (
        "player", "streaming", "decoding", "gsyEngine", "gsySystemProbe",
        "gsyResolvedEngine", "gsySystemFallbackCount", "gsySystemFallbackReason",
        "preferredAudioLanguage", "preferredSubtitleLanguage",
        "preferredCaptionStandard", "preferredCaptionService",
        "keepSessionInBackground", "resumeBackgroundPlayback",
        "backgroundSessionTimeoutSeconds",
        "discPlaybackPolicy", "discSkipMenus", "discSkipPreviews",
        "discCompatibilityFallback", "discMpeg2TimestampRepair",
        "connected", "playerActive",
        "legacyCaptionCallbacksNegotiated", "legacyCaptionWireEventCount",
        "legacyCaptionWireBytes", "legacyCaptionCallbackActive",
        "imageAllocationRecoveryAttempts", "imageAllocationRecoveryEvictions",
        "imageAllocationRecoverySuccesses", "imageAllocationRecoveryFailures",
        "legacyCaptionCallbackCount", "legacyCaptionCallbackBytes",
        "serverName", "serverAddress", "serverPort", "clientId", "uiContextHint", "menuName", "popupName", "hasTextInput",
        "debugStatusVersion", "maxRecoveryWatchdogMs", "uiState", "automationReady", "imeRequested", "imeSuppressedForDebug", "imeVisibleKnown", "imeVisible",
        "connectionTraceAvailable", "connectionGeneration", "connectionServer",
        "connectionCreatedMonotonicMs", "connectionAlive", "connectionCloseRequested",
        "connectionCloseComplete", "connectionMediaWorkerRunning",
        "connectionGfxWorkerRunning", "connectionGfxReadWorkerRunning",
        "connectionEventRouterRunning", "connectionMediaCommandCount",
        "connectionMediaReplyCount", "connectionGfxReadCount",
        "connectionGfxDispatchCount", "connectionGfxReplyCount",
        "connectionEventQueuedCount", "connectionEventDequeuedCount",
        "connectionReconnectCount", "connectionGfxQueueDepth",
        "connectionGfxQueueMaxDepth", "connectionEventQueueDepth",
        "connectionEventQueueMaxDepth", "connectionEventSequence", "connectionRecent",
        "appVisibilityState", "backgroundTransitionGeneration", "pausedByBackground",
        "wasPlayingBeforeBackground", "backgroundMonotonicMs", "foregroundMonotonicMs",
        "backgroundPauseRequestCount", "backgroundPlayRequestCount",
        "backgroundPreservedCount", "backgroundLostCount",
        "appStartedActivityCount", "appBackground",
        "backgroundSessionTimeoutMs", "backgroundSessionTimeoutDeadlineMonotonicMs",
        "backgroundSessionTimeoutCount",
        "playerClass", "state", "mediaTimeMs", "sageTimelineMs", "playbackRate", "timelineSource", "serverAnchorMs",
        "serverRequestedSeekMs", "serverSeekSequence", "serverSeekMonotonicMs", "serverSeekWallMs", "serverSeekAgeMs",
        "serverFlushSequence", "serverFlushMonotonicMs", "serverFlushAgeMs",
        "serverAnchorSequence", "serverAnchorMonotonicMs", "serverAnchorAgeMs", "bufferLeft",
        "serverChannelBandwidthKbps", "serverStreamBandwidthKbps", "serverTargetBandwidthKbps",
        "serverMuxTimeMs", "clientBufferTimeMs", "clientBufferAvailableBytes",
        "lastPushPayloadBytes", "lastPushFlags", "lastPushReply",
        "dvdSessionPending", "dvdPushedBytes", "dvdLastReadBytes",
        "dvdDrainPollCount", "dvdDrainReadyCount", "detailedPushSampleSequence",
        "dvdInitCount", "dvdPushCommandCount", "dvdPushMediaCount",
        "dvdNewCellCount", "dvdClutCount", "dvdSpuControlCount",
        "dvdStcCount", "dvdStreamCount", "dvdLastStreamType",
        "dvdLastStreamPosition", "dvdLastAudioStreamPosition",
        "dvdLastSubtitleStreamPosition", "dvdFormatCount", "dvdTransientEosCount",
        "discOldServerNativeFallback", "discMimRuntimeFallback",
        "discCompatibilityReason",
        "dvdRequestedAudioStream", "dvdAppliedAudioStream",
        "dvdSelectedAudioFormatId", "dvdAvailableAudioFormatIds",
        "dvdSpuFragments", "dvdCompletedSpuPackets", "dvdMalformedSpuPackets",
        "dvdDecodedSpuEvents", "dvdDroppedSpuEvents", "dvdOverlaysPresented",
        "dvdOverlaysCleared", "dvdOverlayEventsScheduled", "dvdOverlayEventsApplied",
        "dvdOverlayEventsStale", "dvdLastOverlayEventUs", "dvdLastOverlayClockUs",
        "dvdLastOverlayOpaquePixels", "dvdHighlightVisible",
        "dvdHighlightX1", "dvdHighlightY1", "dvdHighlightX2", "dvdHighlightY2",
        "dvdHighlightPaletteWord",
        "dvdLatestVideoSampleUs", "dvdLatestAudioSampleUs", "dvdAvSampleDeltaUs",
        "dvdVideoTimestampCorrectionCount", "dvdMpeg2TimestampRepairEnabled",
        "dvdMpeg2ReportedFrameRateHz", "dvdMpeg2SequenceFrameRateHz",
        "dvdMpeg2EffectiveFieldDurationUs", "dvdMpeg2TelecineCadenceSeen",
        "dvdDiscontinuityRebaseCount", "dvdPtsTrace", "dvdFrameMetadataCount",
        "dvdStc45Khz", "dvdLogicalClockBaseMs", "dvdRenderedVideoClockDeltaUs",
        "dvdLastFramePresentationDeltaUs", "dvdLastFrameReleaseDeltaUs",
        "dvdMaxFrameReleaseDeltaUs", "dvdFrameReleaseGapCount",
        "dvdFrameReleaseNonPositiveCount", "dvdFrameReleaseUnder10MsCount",
        "dvdFrameRelease10To25MsCount", "dvdFrameRelease25To45MsCount",
        "dvdFrameRelease45To75MsCount", "dvdFrameRelease75To100MsCount",
        "dvdFrameCadenceTrace",
        "detailedPushSampleMonotonicMs", "detailedPushSampleWallMs", "detailedPushSampleAgeMs",
        "lastFileReadPos", "videoWidth", "videoHeight",
        "videoDestX", "videoDestY", "videoDestWidth", "videoDestHeight",
        "videoUiWidth", "videoUiHeight", "videoLayoutStateError",
        "fastSwitchAttemptCount", "fastSwitchSuccessCount",
        "fastSwitchFallbackCount", "fastSwitchAwaitingFirstFrame",
        "fastSwitchLastReason", "fastSwitchTargetUrl",
        "subtitleTrackCount", "selectedSubtitleTrack", "selectedSubtitleTrackRaw", "subtitleTracks",
        "subtitleCueUpdateCount", "subtitleNonEmptyCueCount", "lastSubtitleCueText", "currentSubtitleCueText",
        "subtitleOverlayAttached", "subtitleStateError",
        "health_probeSupported", "health_probeProvider", "health_probeReason",
        "health_topLevelPlayerClass", "health_backendClass", "health_backendPlayerClass", "health_dataSourceClass",
        "health_dataSourceOpenCount", "health_dataSourceOpenWaitMs", "health_dataSourceLastOpenPosition",
        "sourceOpenMonotonicMs", "sourceFirstReadMonotonicMs", "sourceFirstReadPosition",
        "health_dataSourceLastOpenMonotonicMs", "health_dataSourceFirstReadAfterOpenMonotonicMs",
        "health_dataSourceFirstReadAfterOpenPosition",
        "health_dataSourceNetworkReadCount", "health_dataSourceNetworkReadRequestedBytes",
        "health_dataSourceNetworkReadBytes", "health_dataSourceNetworkReadWaitMs",
        "health_dataSourceNetworkReadMaxRequestedBytes", "health_dataSourceNetworkReadErrors",
        "health_dataSourceNetworkLastReadPosition",
        "health_dataSourceReadCount", "health_dataSourceReadRequestedBytes", "health_dataSourceReadBytes",
        "health_dataSourceReadWaitMs", "health_dataSourceReadRateKbps",
        "health_dataSourcePushCount", "health_dataSourcePushedBytes",
        "playbackSource", "sageOriginalPath", "smbMappedPath", "smbConnected",
        "shadowMediaServerConnected", "shadowOpenSent", "shadowSizeSent", "shadowReadBytes",
        "smbBytesRead", "smbReadCount", "smbSeekCount", "smbLastReadLatencyMs",
        "smbFallbackCount", "smbFallbackReason",
        "health_playbackSource", "health_sageOriginalPath", "health_smbMappedPath",
        "health_smbConnected", "health_shadowMediaServerConnected", "health_shadowOpenSent",
        "health_shadowSizeSent", "health_shadowReadBytes", "health_smbBytesRead",
        "health_smbReadCount", "health_smbSeekCount", "health_smbLastReadLatencyMs",
        "health_smbFallbackCount", "health_smbFallbackReason",
        "health_bufferLeft", "health_lastFileReadPos",
        "health_pushMode", "health_playerReady", "health_seekPending", "health_flushed", "health_errorState", "health_retryCount",
        "health_playbackState", "health_playWhenReady", "health_isPlaying", "health_isLoading",
        "health_playerPositionMs", "health_bufferedPositionMs", "health_durationMs", "health_playerError",
        "health_videoMime", "health_videoCodecString", "health_videoDecoder", "health_videoDecoderKind",
        "health_videoWidth", "health_videoHeight", "health_videoRendered", "health_videoSkipped", "health_videoDropped",
        "health_videoQueuedInput", "health_videoDecoderInitCount", "health_videoDecoderReleaseCount",
        "health_mpeg2InterlaceObservation", "health_mpeg2SequenceExtensionSeen",
        "health_mpeg2ProgressiveFrameCount", "health_mpeg2InterlacedFrameCount",
        "health_mpeg2FieldPictureCount",
        "health_audioMime", "health_audioCodecString", "health_audioDecoder", "health_audioDecoderKind",
        "health_audioChannels", "health_audioFormatSampleRate", "health_audioRendered", "health_audioSkipped", "health_audioDropped",
        "health_audioQueuedInput", "health_audioDecoderInitCount", "health_audioDecoderReleaseCount",
        "health_audioTrackPresent", "health_audioTrackState", "health_audioTrackPlayState", "health_audioTrackSampleRate",
        "health_audioSessionId", "health_audioPlaybackHeadFrames",
        "health_surfaceKnown", "health_surfaceValid", "health_surfaceShown", "health_surfaceWidth", "health_surfaceHeight",
        "trapEventCount", "trapSequence", "trapLastEvent", "trapLastEventSequence", "trapLastEventMonotonicMs",
        "trapLastEventWallMs", "trapLastSnapshotMonotonicMs", "trapLastSnapshotLagMs",
        "trapLastPlayerPositionMs", "trapLastMiniState", "trapLastPlaybackState",
        "trapLastIsPlaying", "trapLastIsLoading", "trapLastVideoExpected", "trapLastAudioExpected",
        "trapLastVideoRendered", "trapLastVideoQueuedInput", "trapLastVideoDecoderInitCount", "trapLastVideoDecoderReleaseCount",
        "trapLastAudioRendered", "trapLastAudioQueuedInput", "trapLastAudioDecoderInitCount", "trapLastAudioDecoderReleaseCount",
        "trapLastAudioHeadFrames", "trapLastAudioSessionId", "trapLastSurfaceValid", "trapLastProbeSupported",
        "trapLastVideoDecoderKind", "trapLastAudioDecoderKind", "trapRecent",
    )
    return {key: state.get(key) for key in keys if key in state}

def _counter_advanced(before: int | None, after: int | None) -> bool:
    if before is None or after is None:
        return False
    try:
        b = int(before)
        a = int(after)
    except (TypeError, ValueError):
        return False
    if b < 0 or a < 0:
        return False
    if a > b:
        return True
    # Decoder/AudioTrack counters can reset during a seek/reprepare. A reset only
    # proves resumed output after the replacement counter has advanced above zero.
    return a < b and a > 0


def _stream_expectations(*states: dict) -> tuple[bool, bool]:
    """Determine which A/V outputs must advance without allowing a vacuous pass."""
    video_expected = any(bool(
        state.get("health_videoMime")
        or state.get("health_videoDecoder")
        or (
            int(state.get("health_videoWidth", 0) or 0) > 0
            and int(state.get("health_videoHeight", 0) or 0) > 0
        )
    ) for state in states if isinstance(state, dict))
    audio_expected = any(bool(
        state.get("health_audioMime")
        or state.get("health_audioDecoder")
        or state.get("health_audioTrackPresent")
        or int(state.get("health_audioChannels", 0) or 0) > 0
    ) for state in states if isinstance(state, dict))
    return video_expected, audio_expected


def _playback_health_from_pair(
    before: dict,
    after: dict,
    *,
    expect_video: bool | None = None,
    expect_audio: bool | None = None,
) -> tuple[bool, dict]:
    player_active = bool(after.get("playerActive"))
    state_playing = int(after.get("state", -1)) == 2
    probe_supported = bool(after.get("health_probeSupported", False) or before.get("health_probeSupported", False))
    details = {
        "player_active": player_active,
        "state_playing": state_playing,
        "probe_supported": probe_supported,
    }

    if probe_supported:
        # When real decoder/audio-output counters are available, they are the
        # authoritative recovery signal. Wrapper playerActive/state/ready/surface
        # fields are still captured for diagnostics but do not block PASS because
        # several backends can leave those fields stale while A/V output advances.
        ready = bool(after.get("health_playerReady", False))
        is_playing = bool(after.get("health_isPlaying", False))
        detected_video, detected_audio = _stream_expectations(before, after)
        video_expected = detected_video if expect_video is None else bool(expect_video)
        audio_expected = detected_audio if expect_audio is None else bool(expect_audio)
        surface_ok = (not video_expected) or bool(after.get("health_surfaceValid", False))
        video_advancing = (not video_expected) or _counter_advanced(
            before.get("health_videoRendered"), after.get("health_videoRendered")
        )
        audio_head_advancing = _counter_advanced(
            before.get("health_audioPlaybackHeadFrames"), after.get("health_audioPlaybackHeadFrames")
        )
        audio_buffers_advancing = _counter_advanced(
            before.get("health_audioRendered"), after.get("health_audioRendered")
        )
        audio_advancing = (not audio_expected) or audio_head_advancing or audio_buffers_advancing
        no_error = not bool(after.get("health_errorState", False)) and not bool(after.get("health_playerError"))
        details.update({
            "ready": ready,
            "is_playing": is_playing,
            "video_expected": video_expected,
            "audio_expected": audio_expected,
            "surface_ok": surface_ok,
            "video_advancing": video_advancing,
            "audio_advancing": audio_advancing,
            "audio_head_advancing": audio_head_advancing,
            "audio_buffers_advancing": audio_buffers_advancing,
            "no_error": no_error,
            "verdict_basis": "av_output_counters",
        })
        return video_advancing and audio_advancing and surface_ok and no_error, details

    # Fallback only for backends where the detailed output probe is unavailable.
    if not player_active or not state_playing:
        return False, details
    before_t = before.get("sageTimelineMs", before.get("mediaTimeMs"))
    after_t = after.get("sageTimelineMs", after.get("mediaTimeMs"))
    try:
        advancing = int(after_t) > int(before_t)
    except (TypeError, ValueError):
        advancing = False
    details["timeline_advancing_fallback"] = advancing
    details["verdict_basis"] = "timeline_fallback"
    return advancing, details


def _dvd_static_menu_health(state: dict) -> tuple[bool, dict]:
    """Recognize a decoded DVD infinite-still menu as healthy output.

    A DVD menu with ``still=inf`` intentionally stops advancing after its
    short MPEG presentation segment.  Requiring decoder/audio counters to
    continue advancing therefore misclassifies a valid, interactive menu as a
    playback stall.  Keep this exception narrow: the MiniDVD session, decoded
    video, a valid surface, and a non-empty composed DVD SPU overlay must all
    be present, and any player error still fails the gate.
    """
    player_active = bool(state.get("playerActive"))
    session_pending = bool(state.get("dvdSessionPending"))
    video_rendered = int(state.get("health_videoRendered", 0) or 0)
    overlay_presented = int(state.get("dvdOverlaysPresented", 0) or 0)
    overlay_pixels = int(state.get("dvdLastOverlayOpaquePixels", 0) or 0)
    surface_ok = bool(state.get("health_surfaceValid", False))
    no_error = not bool(state.get("health_errorState", False)) and not bool(
        state.get("health_playerError")
    )
    passed = (
        player_active
        and session_pending
        and video_rendered > 0
        and overlay_presented > 0
        and overlay_pixels > 0
        and surface_ok
        and no_error
    )
    return passed, {
        "player_active": player_active,
        "dvd_session_pending": session_pending,
        "video_rendered": video_rendered,
        "overlay_presented": overlay_presented,
        "overlay_opaque_pixels": overlay_pixels,
        "surface_ok": surface_ok,
        "no_error": no_error,
        "verdict_basis": "dvd_static_menu" if passed else "dvd_static_menu_incomplete",
    }


def _startup_terminal_failure(state: dict) -> dict | None:
    menu = str(state.get("menuName") or "")
    popup = str(state.get("popupName") or "")
    context = str(state.get("uiContextHint") or "")
    normalized_ui = " ".join((menu, popup, context)).lower().replace("_", "")
    if "asktodeleterecording" in normalized_ui or (
        "delete" in normalized_ui and "recording" in normalized_ui
    ):
        return {
            "code": "eof_delete_prompt",
            "message": "SageTV reached AskToDeleteRecording before startup completed",
            "menuName": menu,
            "popupName": popup,
            "uiContextHint": context,
        }
    player_error = str(state.get("health_playerError") or "")
    if bool(state.get("health_errorState")) or player_error:
        return {
            "code": "player_error",
            "message": player_error or "player entered an error state",
        }
    return None


def _is_resume_restart_prompt(state: dict) -> bool:
    popup = str(state.get("popupName") or "").lower()
    return "resume" in popup and "restart" in popup


def _resolve_resume_restart_prompt(
    timeout_s: float = 5.0,
    restart_from_beginning: bool = False,
) -> dict:
    """Resolve SageTV's Resume/Restart prompt for an exact-path test start.

    SageTV creates the MiniPlayer before showing this popup, so ``playerActive``
    alone is not proof that playback was allowed to start.  Keep this handling
    narrowly scoped to the unambiguous Resume/Restart prompt. Normal callers
    retain the historical default Resume choice. Deterministic fixture tests
    may explicitly choose Restart so saved watch positions cannot leak between
    matrix cases.
    """
    deadline = time.monotonic() + max(0.5, min(float(timeout_s), 15.0))
    last: dict = {}
    active_without_prompt_since: float | None = None
    while time.monotonic() < deadline:
        last = adb.player_state_snapshot()
        if _is_resume_restart_prompt(last):
            navigation = None
            if restart_from_beginning:
                navigation = adb.sage_command("down")
            selected = adb.sage_command("select")
            return {
                "selected": True,
                "choice": "restart_from_beginning" if restart_from_beginning else "default_resume",
                "popupName": str(last.get("popupName") or ""),
                "navigation": navigation,
                "command": selected,
            }
        if bool(last.get("playerActive")):
            if active_without_prompt_since is None:
                active_without_prompt_since = time.monotonic()
            elif time.monotonic() - active_without_prompt_since >= 1.0:
                break
        time.sleep(0.1)
    return {
        "selected": False,
        "reason": "resume_restart_prompt_not_present",
        "state": _compact_state(last),
    }


def _wait_for_playback(
    timeout_s: float = 45.0,
    verify_ms: int = 1500,
    expect_video: bool = True,
    expect_audio: bool = True,
) -> dict:
    timeout_s = max(1.0, min(float(timeout_s), 300.0))
    verify_ms = max(250, min(int(verify_ms), 10000))
    started = time.monotonic()
    deadline = started + timeout_s
    pending_crash_milestones_ms = [ms for ms in (5000, 15000, 30000) if ms < int(timeout_s * 1000.0)]
    long_wait_probes: list[dict] = []
    try:
        baseline_crash_probe = _crash_probe_snapshot()
    except Exception as exc:
        baseline_crash_probe = {"unavailable": True, "error": str(exc)}
    last = {}
    last_health = {}
    while time.monotonic() < deadline:
        first = adb.player_state_snapshot()
        last = first
        terminal_failure = _startup_terminal_failure(first)
        if terminal_failure:
            return {
                "passed": False,
                "verify_ms": verify_ms,
                "failureReason": terminal_failure,
                "state": _compact_state(first),
                "baselineCrashProbe": baseline_crash_probe,
                "longWaitProbes": long_wait_probes,
            }
        static_menu_healthy, static_menu_details = _dvd_static_menu_health(first)
        if static_menu_healthy:
            return {
                "passed": True,
                "verify_ms": verify_ms,
                "health": static_menu_details,
                "before": _compact_state(first),
                "after": _compact_state(first),
                "baselineCrashProbe": baseline_crash_probe,
                "longWaitProbes": long_wait_probes,
            }
        probe_available = bool(first.get("health_probeSupported", False))
        candidate_active = bool(first.get("playerActive")) and int(first.get("state", -1)) == 2
        if probe_available or candidate_active:
            time.sleep(verify_ms / 1000.0)
            second = adb.player_state_snapshot()
            terminal_failure = _startup_terminal_failure(second)
            if terminal_failure:
                return {
                    "passed": False,
                    "verify_ms": verify_ms,
                    "failureReason": terminal_failure,
                    "state": _compact_state(second),
                    "baselineCrashProbe": baseline_crash_probe,
                    "longWaitProbes": long_wait_probes,
                }
            healthy, details = _playback_health_from_pair(
                first,
                second,
                expect_video=expect_video,
                expect_audio=expect_audio,
            )
            last_health = details
            last = second
            if healthy:
                return {
                    "passed": True,
                    "verify_ms": verify_ms,
                    "health": details,
                    "before": _compact_state(first),
                    "after": _compact_state(second),
                    "baselineCrashProbe": baseline_crash_probe,
                    "longWaitProbes": long_wait_probes,
                }

        elapsed_ms = int(round((time.monotonic() - started) * 1000.0))
        while pending_crash_milestones_ms and elapsed_ms >= pending_crash_milestones_ms[0]:
            milestone_ms = pending_crash_milestones_ms.pop(0)
            try:
                probe = _crash_probe_snapshot()
                change = _crash_probe_change(baseline_crash_probe, probe)
                entry = {
                    "milestoneMs": milestone_ms,
                    "elapsedMs": elapsed_ms,
                    "state": _compact_state(last),
                    "crashProbe": probe,
                    **change,
                }
            except Exception as exc:
                entry = {
                    "milestoneMs": milestone_ms,
                    "elapsedMs": elapsed_ms,
                    "state": _compact_state(last),
                    "crashProbe": {"unavailable": True, "error": str(exc)},
                    "crashDetected": False,
                }
            long_wait_probes.append(entry)
            if entry.get("crashDetected"):
                return {
                    "passed": False,
                    "verify_ms": verify_ms,
                    "state": _compact_state(last),
                    "crashDetected": True,
                    "crashDetails": entry,
                    "baselineCrashProbe": baseline_crash_probe,
                    "longWaitProbes": long_wait_probes,
                }
        time.sleep(0.25)
    return {
        "passed": False,
        "verify_ms": verify_ms,
        "failureReason": {
            "code": "startup_output_timeout",
            "message": "expected playback output did not become healthy before timeout",
            "expectVideo": bool(expect_video),
            "expectAudio": bool(expect_audio),
            "waitingForVideo": bool(expect_video) and not bool(last_health.get("video_advancing")),
            "waitingForAudio": bool(expect_audio) and not bool(last_health.get("audio_advancing")),
            "surfaceInvalid": bool(expect_video) and not bool(last_health.get("surface_ok")),
            "playerError": last_health.get("no_error") is False,
        },
        "health": last_health,
        "state": _compact_state(last),
        "crashDetected": False,
        "baselineCrashProbe": baseline_crash_probe,
        "longWaitProbes": long_wait_probes,
    }


def _is_fullscreen_playback(state: dict) -> bool:
    """Return true only for SageTV's full playback destination, not its SurfaceView."""
    if not bool(state.get("playerActive")):
        return False
    menu = str(state.get("menuName", "")).strip().lower()
    if "osd" in menu:
        return True

    try:
        width = int(state.get("videoDestWidth", 0) or 0)
        height = int(state.get("videoDestHeight", 0) or 0)
        screen_width = int(state.get("videoUiWidth", 0) or 0)
        screen_height = int(state.get("videoUiHeight", 0) or 0)
    except (TypeError, ValueError):
        return False
    if min(width, height, screen_width, screen_height) <= 0:
        return False
    embedded = width * 100 < screen_width * 75 and height * 100 < screen_height * 75
    return not embedded


def _promote_preview_to_fullscreen(timeout_s: float = 8.0) -> dict:
    """Enter the STV playback screen after Watch() has opened its preview player."""
    before = adb.player_state_snapshot()
    if not bool(before.get("playerActive")):
        return {"passed": False, "reason": "player_not_active", "state": _compact_state(before)}

    started = time.monotonic()
    deadline = started + max(0.5, min(float(timeout_s), 15.0))
    last = before
    stable_since = started if _is_fullscreen_playback(before) else None

    # A Vibe-aware server enters MediaPlayer OSD asynchronously after accepting
    # its private watch-file event. Wait for that transition before sending TV;
    # otherwise the delayed TV command can become a second toggle and return the
    # STV to Main Menu. Older/stock servers still receive TV after this grace.
    passive_deadline = min(deadline, started + 2.0)
    while time.monotonic() < passive_deadline:
        last = adb.player_state_snapshot()
        if _is_fullscreen_playback(last):
            if stable_since is None:
                stable_since = time.monotonic()
            elif time.monotonic() - stable_since >= 0.75:
                return {
                    "passed": True,
                    "alreadyFullscreen": True,
                    "stableMs": int((time.monotonic() - stable_since) * 1000),
                    "state": _compact_state(last),
                }
        else:
            stable_since = None
        time.sleep(0.10)

    command = None
    if time.monotonic() < deadline:
        command = adb.sage_command("tv")
    stable_since = None
    while time.monotonic() < deadline:
        last = adb.player_state_snapshot()
        if _is_fullscreen_playback(last):
            if stable_since is None:
                stable_since = time.monotonic()
            elif time.monotonic() - stable_since >= 0.75:
                result = {
                    "passed": True,
                    "alreadyFullscreen": False,
                    "stableMs": int((time.monotonic() - stable_since) * 1000),
                    "state": _compact_state(last),
                }
                if command is not None:
                    result["command"] = command
                return result
        else:
            stable_since = None
        time.sleep(0.10)
    return {
        "passed": False,
        "reason": "fullscreen_surface_not_observed",
        "command": command,
        "state": _compact_state(last),
    }


def _run_sage_sequence(commands: list[str], delay_ms: int, settle_ms: int) -> dict:
    delay_ms = max(0, min(int(delay_ms), 10000))
    settle_ms = max(0, min(int(settle_ms), 30000))
    before = adb.player_state_snapshot()
    before_ms = _snapshot_media_time(before)
    started = time.monotonic()
    sent = []
    for command in commands:
        sent.append(adb.sage_command(command))
        if delay_ms:
            time.sleep(delay_ms / 1000.0)
    if settle_ms:
        time.sleep(settle_ms / 1000.0)
    after = adb.player_state_snapshot()
    elapsed_ms = int(round((time.monotonic() - started) * 1000.0))
    after_ms = _snapshot_media_time(after)
    raw_delta_ms = after_ms - before_ms
    was_playing = int(before.get("state", 0)) == 2 and int(after.get("state", 0)) == 2
    playback_adjusted_delta_ms = raw_delta_ms - elapsed_ms if was_playing else raw_delta_ms
    return {
        "commands": commands,
        "delay_ms": delay_ms,
        "settle_ms": settle_ms,
        "elapsed_ms": elapsed_ms,
        "raw_timeline_delta_ms": raw_delta_ms,
        "playback_adjusted_delta_ms": playback_adjusted_delta_ms,
        "before": _compact_state(before),
        "after": _compact_state(after),
        "sent": sent,
    }


def artifact(name: str, suffix: str) -> Path:
    safe = "".join(c if c.isalnum() or c in "-_" else "_" for c in name)[:80] or "capture"
    stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    return cfg.artifact_dir / f"{stamp}_{safe}{suffix}"


def _seek_fixture_metadata(path: Path) -> dict:
    probe = subprocess.run(
        [
            "ffprobe", "-v", "error", "-count_frames",
            "-show_entries",
            "format=filename,duration,size,bit_rate:stream=index,codec_name,codec_type,width,height,r_frame_rate,field_order,sample_rate,channels,nb_read_frames",
            "-of", "json", str(path),
        ],
        cwd=PROJECT_ROOT,
        check=True,
        capture_output=True,
        text=True,
        # A 15-minute 10-Mbit fixture is about 1.1 GiB. Counting every final
        # video frame is intentionally stronger than a header-only probe and
        # can exceed one minute while the unified container is compiling.
        timeout=300,
    )
    digest = hashlib.sha256()
    a53_signature = b"GA94"
    a53_overlap = b""
    a53_payload_count = 0
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
            a53_data = a53_overlap + block
            a53_payload_count += a53_data.count(a53_signature)
            a53_overlap = a53_data[-(len(a53_signature) - 1):]
    metadata = json.loads(probe.stdout)
    metadata["sha256"] = digest.hexdigest()
    metadata["artifactPath"] = str(path.relative_to(PROJECT_ROOT))
    metadata["a53Ga94PayloadCount"] = a53_payload_count
    if a53_payload_count <= 0:
        raise RuntimeError(f"generated fixture has no ATSC A/53 GA94 caption payloads: {path}")
    video_streams = [stream for stream in metadata.get("streams", []) if stream.get("codec_type") == "video"]
    if len(video_streams) != 1:
        raise RuntimeError(f"generated fixture must have exactly one video stream: {path}")
    final_video_frames = int(video_streams[0].get("nb_read_frames", 0) or 0)
    metadata["finalVideoFrameCount"] = final_video_frames
    if final_video_frames <= 0 or a53_payload_count != final_video_frames:
        raise RuntimeError(
            "generated fixture does not contain exactly one A/53 GA94 payload "
            f"per final video picture: frames={final_video_frames} payloads={a53_payload_count}"
        )
    return metadata


def _publish_fixture_to_smb(path: Path, smb_url: str, username: str, password: str) -> dict:
    parsed = urlparse(str(smb_url).strip())
    components = [unquote(part) for part in parsed.path.split("/") if part]
    if parsed.scheme.lower() != "smb" or not parsed.hostname or len(components) < 2:
        raise ValueError("publish_smb_url must include an SMB host, share, directory, and fixture filename")
    share = components[0]
    remote_path = "/".join(components[1:])
    if not re.fullmatch(r"[A-Za-z0-9._-]+", share):
        raise ValueError("SMB share name contains unsupported characters")
    if not re.fullmatch(r"[A-Za-z0-9._/-]+\.(?:ts|edl)", remote_path):
        raise ValueError("SMB destination path contains unsupported characters or is not a .ts/.edl fixture file")
    if Path(remote_path).name != path.name:
        raise ValueError("SMB destination filename must match the generated fixture filename")

    command = ["smbclient", f"//{parsed.hostname}/{share}"]
    auth_file: str | None = None
    try:
        if username:
            with tempfile.NamedTemporaryFile("w", encoding="utf-8", prefix="vibe-smb-auth-", delete=False) as auth:
                auth.write(f"username = {username}\npassword = {password}\n")
                auth_file = auth.name
            Path(auth_file).chmod(0o600)
            command.extend(["-A", auth_file])
        else:
            command.append("-N")
        command.extend(["-c", f'put "{path}" "{remote_path}"'])
        completed = subprocess.run(
            command,
            cwd=PROJECT_ROOT,
            check=False,
            capture_output=True,
            text=True,
            timeout=1800,
        )
    except FileNotFoundError as exc:
        raise RuntimeError("smbclient is missing from the unified OpenSageTV Vibe build image") from exc
    finally:
        if auth_file:
            Path(auth_file).unlink(missing_ok=True)
    if completed.returncode != 0:
        detail = (completed.stderr or completed.stdout or "unknown smbclient error").strip()
        raise RuntimeError(f"SMB fixture publish failed (exit={completed.returncode}): {detail[-2000:]}")
    return {
        "published": True,
        "smbUrl": smb_url,
        "authentication": "credentials" if username else "anonymous",
    }


def _write_seek_fixture_edl(media_path: Path, duration_s: int) -> Path:
    edl_path = media_path.with_suffix(".edl")
    intervals = ((120, 180), (360, 420), (660, 720))
    rows = [f"{start}.000\t{end}.000\t0\n" for start, end in intervals if duration_s >= end]
    edl_path.write_text("".join(rows), encoding="utf-8")
    return edl_path


@mcp.tool()
def generate_seek_fixture(
    duration_s: int = 900,
    filename: str = "VibeSeekTest-1080i-MPEG2-AC3-CC.ts",
    force: bool = False,
    publish_smb_url: str = "",
    smb_username: str = "",
    smb_password: str = "",
) -> dict:
    """Create/recreate the deterministic captioned SageTV seek fixture.

    Output is deliberately restricted to the project's artifacts/test-media directory.
    Video carries real ATSC A/53 CEA-608 CC1 and CEA-708 Service 1 payloads at
    0.5-second intervals. The returned FFprobe metadata and SHA-256 make the
    exact test input auditable.
    """
    duration_s = int(duration_s)
    if duration_s < 1 or duration_s > 3600:
        raise ValueError("duration_s must be between 1 and 3600")
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]*\.ts", filename):
        raise ValueError("filename must be a simple .ts basename containing only letters, numbers, dot, underscore, or dash")

    output = SEEK_FIXTURE_DIR / filename
    generated = True
    reason = "created" if not force else "recreated"
    if output.exists() and not force:
        generated = False
        reason = "already_exists"
    else:
        generator = PROJECT_ROOT / "scripts" / "generate_seek_fixture.sh"
        if not generator.is_file():
            raise RuntimeError(f"seek fixture generator is missing: {generator}")
        SEEK_FIXTURE_DIR.mkdir(parents=True, exist_ok=True)
        completed = subprocess.run(
            ["bash", str(generator), str(output), str(duration_s)],
            cwd=PROJECT_ROOT,
            check=False,
            capture_output=True,
            text=True,
            timeout=max(180, duration_s * 4),
        )
        if completed.returncode != 0:
            output.unlink(missing_ok=True)
            detail = (completed.stderr or completed.stdout or "unknown FFmpeg error").strip()
            raise RuntimeError(f"seek fixture generation failed (exit={completed.returncode}): {detail[-4000:]}")

    metadata = _seek_fixture_metadata(output)
    actual_duration_s = int(float(metadata.get("format", {}).get("duration", duration_s)))
    edl_path = _write_seek_fixture_edl(output, actual_duration_s)
    publication = None
    if publish_smb_url.strip():
        publication = _publish_fixture_to_smb(output, publish_smb_url, smb_username, smb_password)
        edl_url = publish_smb_url.rsplit("/", 1)[0] + "/" + edl_path.name
        publication["commercialMarkers"] = _publish_fixture_to_smb(
            edl_path, edl_url, smb_username, smb_password
        )
    return {
        "generated": generated,
        "reason": reason,
        "publication": publication,
        "commercialMarkerPath": str(edl_path.relative_to(PROJECT_ROOT)),
        "commercialMarkers": ((120, 180), (360, 420), (660, 720)),
        "captions": {
            "transport": "ATSC_A53_GA94",
            "services": ("CEA-608 CC1", "CEA-708 Service 1"),
            "intervalSeconds": 0.5,
        },
        "audioVisualSyncPulse": {
            "intervalSeconds": 1.0,
            "durationSeconds": 0.12,
            "audioTracks": ("AC-3 5.1 1000 Hz", "AC-3 stereo 440 Hz"),
            "visualMarker": "white upper-left box and whole-second label",
        },
        "credentialsReturned": False,
        **metadata,
    }


@mcp.tool()
def adb_connect() -> dict:
    """Connect ADB and establish one persistent device shell for the MCP server lifetime."""
    connected = adb.connect()
    return {
        "connect": connected,
        "devices": adb.devices(),
        "target": cfg.device,
        **adb.persistent_shell_status(),
    }

@mcp.tool()
def adb_session_status() -> dict:
    """Report whether MCP is reusing one persistent ADB shell for runtime test commands."""
    return {"target": cfg.device, **adb.persistent_shell_status()}

@mcp.tool()
def firetv_device_info() -> dict:
    """Return model, Android/Fire OS API level and build fingerprint for the configured Fire TV."""
    return adb.device_info()

@mcp.tool()
def dev_package_info() -> str:
    """Return Android package details for the isolated Dev development app only."""
    return adb.package_info()

@mcp.tool()
def install_dev_apk(apk_path: str = "") -> dict:
    """Install/replace the Dev APK only after verifying its package ID. With no path, install the default built artifact."""
    apk = Path(apk_path).expanduser().resolve() if apk_path else cfg.artifact_dir / "OpenSageTV-Vibe-Android-Client-debug.apk"
    return {"package": cfg.dev_package, "apk": str(apk), "result": adb.install_dev_apk(apk)}

@mcp.tool()
def launch_dev_app() -> str:
    """Launch the isolated SageTV MiniClient Dev app."""
    return adb.launch()

@mcp.tool()
def firetv_wake() -> dict:
    """Wake the configured Android/Fire TV without launching the MiniClient."""
    return adb.wake()

_CRASH_SIGNATURES = (
    ("fatal_exception", re.compile(r"\bFATAL EXCEPTION\b", re.IGNORECASE)),
    ("android_runtime", re.compile(r"\bAndroidRuntime\b.*(?:FATAL|Exception)", re.IGNORECASE)),
    ("fatal_signal", re.compile(r"\bFatal signal\s+\d+\b", re.IGNORECASE)),
    ("sigsegv", re.compile(r"\bSIGSEGV\b", re.IGNORECASE)),
    ("sigabrt", re.compile(r"\bSIGABRT\b", re.IGNORECASE)),
    ("native_tombstone", re.compile(r"\btombstone\b|\bDEBUG\s*:\s*backtrace\b", re.IGNORECASE)),
)


def _crash_probe_snapshot(lines: int = 800) -> dict:
    """Return a compact crash/process fingerprint suitable for repeated watchdog probes."""
    text = adb.crash_log(lines=max(50, min(int(lines), 2000)))
    status = adb.app_status()
    fingerprint = hashlib.sha256(text.encode("utf-8", errors="replace")).hexdigest()
    all_lines = text.splitlines()
    matches: list[dict] = []
    package = cfg.dev_package.lower()
    for index, line in enumerate(all_lines):
        kinds = [name for name, pattern in _CRASH_SIGNATURES if pattern.search(line)]
        if not kinds:
            continue
        lo = max(0, index - 8)
        hi = min(len(all_lines), index + 12)
        context = all_lines[lo:hi]
        relevant = package in "\n".join(context).lower()
        matches.append({
            "type": kinds[0],
            "relevantToDevPackage": relevant,
            "line": line.strip()[:400],
            "context": [item.strip()[:400] for item in context if item.strip()][-12:],
        })
    relevant_matches = [item for item in matches if item.get("relevantToDevPackage")]
    signature_payload = "\n".join(
        str(item.get("type", "")) + "|" + str(item.get("line", ""))
        for item in relevant_matches
    )
    signature_fingerprint = hashlib.sha256(
        signature_payload.encode("utf-8", errors="replace")
    ).hexdigest() if signature_payload else ""
    return {
        "package": cfg.dev_package,
        "running": bool(status.get("running")),
        "foreground": bool(status.get("foreground")),
        "pids": sorted(str(pid) for pid in (status.get("pids") or [])),
        "runningSource": status.get("runningSource", ""),
        "crashFingerprint": fingerprint,
        "crashLogLineCount": len(all_lines),
        "signatureDetected": bool(relevant_matches),
        "signatureFingerprint": signature_fingerprint,
        "signatureTypes": sorted({str(item.get("type")) for item in relevant_matches}),
        "signatures": relevant_matches[-4:],
    }


def _crash_probe_change(baseline: dict, current: dict) -> dict:
    baseline_pids = tuple(sorted(str(x) for x in (baseline.get("pids") or [])))
    current_pids = tuple(sorted(str(x) for x in (current.get("pids") or [])))
    baseline_sig = str(baseline.get("signatureFingerprint") or "")
    current_sig = str(current.get("signatureFingerprint") or "")
    process_gone = baseline.get("running") is True and current.get("running") is False
    process_restart = bool(baseline_pids and current_pids and baseline_pids != current_pids)
    new_signature = bool(current.get("signatureDetected")) and bool(current_sig) and current_sig != baseline_sig
    return {
        "crashDetected": bool(process_gone or process_restart or new_signature),
        "processGone": process_gone,
        "processRestartDetected": process_restart,
        "newCrashSignature": new_signature,
        "baselinePids": list(baseline_pids),
        "currentPids": list(current_pids),
    }


@mcp.tool()
def dev_crash_probe(lines: int = 800) -> dict:
    """Compact Dev-app crash/process probe for long media waits; safe to call repeatedly at watchdog milestones."""
    return _crash_probe_snapshot(lines)


@mcp.tool()
def dev_app_status() -> dict:
    """Check whether the isolated Dev MiniClient process is running and whether it is foreground."""
    return adb.app_status()

@mcp.tool()
def kill_dev_app() -> dict:
    """Force-stop the isolated Dev MiniClient and verify that its process is gone."""
    before = adb.app_status()
    result = adb.force_stop() if before.get("running") else "already stopped"
    deadline = time.monotonic() + 15.0
    after = adb.app_status()
    while after.get("running") and time.monotonic() < deadline:
        time.sleep(0.25)
        after = adb.app_status()
    force_stop_committed = bool(after.get("forceStopped") and not after.get("foreground"))
    return {"before": before, "forceStop": result, "after": after,
            "stopped": not bool(after.get("running")) or force_stop_committed,
            "terminationPending": bool(after.get("running") and force_stop_committed)}

@mcp.tool()
def dev_prepare_clean_start(wake: bool = True, graceful_timeout_s: float = 2.0) -> dict:
    """Prepare deterministic automation startup: graceful exit, force-stop fallback, verify stopped, optionally wake."""
    return adb.prepare_clean_start(wake=wake, graceful_timeout_s=graceful_timeout_s)

@mcp.tool()
def stop_dev_app() -> str:
    """Force-stop the isolated SageTV MiniClient Dev app."""
    return adb.force_stop()

@mcp.tool()
def uninstall_dev_app() -> str:
    """Uninstall ONLY the configured Dev package; production SageTV package operations are blocked."""
    return adb.uninstall()

@mcp.tool()
def firetv_key(key: str) -> str:
    """Send a Fire TV/Android remote key such as UP, DOWN, LEFT, RIGHT, SELECT, BACK, PLAY_PAUSE, FF or REWIND."""
    return adb.key(key)

@mcp.tool()
def firetv_key_sequence(keys: list[str], delay_ms: int = 300) -> list[dict]:
    """Send a repeatable sequence of remote keys with a delay between each key."""
    return adb.key_sequence(keys, delay_ms)

@mcp.tool()
def dev_player_state() -> dict:
    """Read current player/configuration state plus exact-event debug trap summary from the debug APK."""
    state = _compact_state(adb.player_state_snapshot())
    state["trapEvents"] = _parse_trap_recent(state.get("trapRecent"))
    return state


@mcp.tool()
def dev_codec_capabilities() -> dict:
    """Read video decoder capabilities and process-local init/failure observations from the debug APK."""
    return adb.codec_capabilities()


@mcp.tool()
def dev_ensure_fullscreen_playback(timeout_s: float = 8.0) -> dict:
    """Promote an active SageTV preview/subwindow to fullscreen and verify its playback surface."""
    return _promote_preview_to_fullscreen(timeout_s=timeout_s)


@mcp.tool()
def dev_smb_profile_list() -> dict:
    """List validated MiniClient profile files through the debug APK's configured SMB profile share."""
    return adb.smb_profile("list")


@mcp.tool()
def dev_smb_profile_save(name: str, overwrite: bool = False) -> dict:
    """Save the current preferences as a credential-free profile; overwrite must be explicit."""
    return adb.smb_profile("save", name=name, overwrite=overwrite)


@mcp.tool()
def dev_smb_profile_load(name: str) -> dict:
    """Load and validate a remote profile without applying settings or its separately held Client ID."""
    return adb.smb_profile("load", name=name)


@mcp.tool()
def dev_smb_profile_delete(name: str) -> dict:
    """Delete a named test profile from the configured SMB share."""
    return adb.smb_profile("delete", name=name)


@mcp.tool()
def dev_open_smb_profile_settings() -> dict:
    """Open the production SMB configuration-profile settings UI in the debug APK."""
    return adb.open_smb_profile_settings()

@mcp.tool()
def dev_player_events() -> dict:
    """Read the debug APK exact-event playback trap ring, including event-time A/V counters."""
    result = adb.player_event_traps()
    result["events"] = _parse_trap_recent(result.get("trapRecent"))
    return result

@mcp.tool()
def dev_clear_player_events() -> dict:
    """Clear the debug APK exact-event playback trap ring before a focused playback action."""
    return adb.clear_player_event_traps()


@mcp.tool()
def dev_set_datasource_capture(enabled: bool = True) -> dict:
    """Enable/disable a bounded raw Push-byte capture for the next playback."""
    return adb.set_datasource_capture(enabled)

@mcp.tool()
def dev_set_player_config(
    player: str = "",
    streaming: str = "",
    decoding: str = "",
    gsy_engine: str = "",
    gsy_system_probe: bool | None = None,
    preferred_audio_language: str = "",
    preferred_subtitle_language: str = "",
    preferred_caption_standard: str = "",
    preferred_caption_service: int = 0,
    fixed_encoding_preference: str = "",
    fixed_encoding_format: str = "",
    fixed_video_bitrate_kbps: int = 0,
    fixed_video_fps: str = "",
    fixed_key_frame_interval: int = 0,
    fixed_use_b_frames: bool | None = None,
    fixed_video_resolution: str = "",
    fixed_audio_codec: str = "",
    fixed_audio_bitrate_kbps: int = 0,
    fixed_audio_channels: str = "",
    fixed_remuxing_preference: str = "",
    fixed_remuxing_format: str = "",
    smb_mappings: str = "",
    smb_username: str = "",
    smb_password: str = "",
    smb_domain: str = "",
    smb_clear_auth: bool = False,
    smb_profile_directory: str = "",
    smb_profile_username: str = "",
    smb_profile_password: str = "",
    smb_profile_domain: str = "",
    smb_profile_clear_auth: bool = False,
    keep_session_in_background: bool | None = None,
    resume_background_playback: bool | None = None,
    background_session_timeout_seconds: int | None = None,
    disc_playback_policy: str = "",
    disc_skip_menus: bool | None = None,
    disc_skip_previews: bool | None = None,
    disc_compatibility_fallback: bool | None = None,
    disc_mpeg2_timestamp_repair: str = "",
) -> dict:
    """Set all debug APK playback preferences for the next playback.

    User selections: streaming push/pull/fixed/smb_direct/smb_auto; decoding
    hardware/software/fallback. Preferred BCP-47 audio/subtitle languages and
    CEA-608/708 service selection apply on the next playback without changing
    SageTV STV caption Off/On authority. SMB credentials are accepted only by the Dev
    control path and are never returned by this tool.
    Disc/DVD policy, menu/preview skipping, and compatibility fallback are also
    configurable and are reported in every snapshot. Fixed encoding parameters may
    also be supplied: encoding preference needed/always,
    container matroska/dvd, video bitrate/fps/keyframe/B-frames/resolution, audio
    codec/bitrate/channels, and fixed remuxing preference/format.
    """
    return adb.set_player_config(
        player=player, streaming=streaming, decoding=decoding, gsy_engine=gsy_engine,
        gsy_system_probe=gsy_system_probe,
        preferred_audio_language=preferred_audio_language,
        preferred_subtitle_language=preferred_subtitle_language,
        preferred_caption_standard=preferred_caption_standard,
        preferred_caption_service=preferred_caption_service or "",
        fixed_encoding_preference=fixed_encoding_preference,
        fixed_encoding_format=fixed_encoding_format,
        fixed_video_bitrate_kbps=fixed_video_bitrate_kbps or "",
        fixed_video_fps=fixed_video_fps,
        fixed_key_frame_interval=fixed_key_frame_interval or "",
        fixed_use_b_frames=fixed_use_b_frames,
        fixed_video_resolution=fixed_video_resolution,
        fixed_audio_codec=fixed_audio_codec,
        fixed_audio_bitrate_kbps=fixed_audio_bitrate_kbps or "",
        fixed_audio_channels=fixed_audio_channels,
        fixed_remuxing_preference=fixed_remuxing_preference,
        fixed_remuxing_format=fixed_remuxing_format,
        smb_mappings=smb_mappings,
        smb_username=smb_username,
        smb_password=smb_password,
        smb_domain=smb_domain,
        smb_clear_auth=smb_clear_auth,
        smb_profile_directory=smb_profile_directory,
        smb_profile_username=smb_profile_username,
        smb_profile_password=smb_profile_password,
        smb_profile_domain=smb_profile_domain,
        smb_profile_clear_auth=smb_profile_clear_auth,
        keep_session_in_background=keep_session_in_background,
        resume_background_playback=resume_background_playback,
        background_session_timeout_seconds=background_session_timeout_seconds,
        disc_playback_policy=disc_playback_policy,
        disc_skip_menus=disc_skip_menus,
        disc_skip_previews=disc_skip_previews,
        disc_compatibility_fallback=disc_compatibility_fallback,
        disc_mpeg2_timestamp_repair=disc_mpeg2_timestamp_repair,
    )

@mcp.tool()
def dev_client_id() -> dict:
    """Read the configured and currently active SageTV MiniClient client ID."""
    return adb.get_client_id()

@mcp.tool()
def dev_set_client_id(client_id: str = "", generate: bool = False) -> dict:
    """Persist a client ID for future connections, or generate a fresh original-style ID.

    Plain text up to six characters is converted to SageTV's six-byte colonized form.
    Example: DEV001 -> 44:45:56:30:30:31. A live connection keeps its existing
    active ID until the Dev app reconnects/restarts.
    """
    if not generate and not str(client_id or "").strip():
        raise ValueError("client_id is required unless generate=true")
    return adb.set_client_id(value=client_id, generate=generate)

@mcp.tool()
def dev_player_tuning() -> dict:
    """Read the current in-memory Dev playback tuning overrides and effective defaults."""
    return adb.get_player_tuning()

@mcp.tool()
def dev_set_player_tuning(
    reset: bool = False,
    media3_ts_search_multiplier: int = 0,
    exo2_ts_search_multiplier: int = 0,
    media3_pull_read_kb: int = 0,
    exo2_pull_read_kb: int = 0,
    media3_min_buffer_ms: int = -1,
    media3_max_buffer_ms: int = -1,
    media3_playback_buffer_ms: int = -1,
    media3_rebuffer_ms: int = -1,
    exo2_min_buffer_ms: int = -1,
    exo2_max_buffer_ms: int = -1,
    exo2_playback_buffer_ms: int = -1,
    exo2_rebuffer_ms: int = -1,
    directional_sync_min_delta_ms: int = -1,
    media3_seek_recovery_enabled: bool | None = None,
    exo2_seek_recovery_enabled: bool | None = None,
    media3_seek_recovery_delay_ms: int = 0,
    exo2_seek_recovery_delay_ms: int = 0,
    media3_seek_policy: str = "",
    exo2_seek_policy: str = "",
    media3_codec_mode: str = "",
    exo2_codec_mode: str = "",
) -> dict:
    """Set Dev-only in-memory player tuning. Values apply to the next created player/datasource.

    A process restart restores compiled defaults. Supported seek policies: closest,next,previous,directional.
    Codec modes: auto,async,sync. Pull read sizes are KiB.
    """
    return adb.set_player_tuning(
        reset=reset,
        media3_ts_search_multiplier=media3_ts_search_multiplier or "",
        exo2_ts_search_multiplier=exo2_ts_search_multiplier or "",
        media3_pull_read_kb=media3_pull_read_kb or "",
        exo2_pull_read_kb=exo2_pull_read_kb or "",
        media3_min_buffer_ms=(media3_min_buffer_ms if media3_min_buffer_ms >= 0 else ""),
        media3_max_buffer_ms=(media3_max_buffer_ms if media3_max_buffer_ms >= 0 else ""),
        media3_playback_buffer_ms=(media3_playback_buffer_ms if media3_playback_buffer_ms >= 0 else ""),
        media3_rebuffer_ms=(media3_rebuffer_ms if media3_rebuffer_ms >= 0 else ""),
        exo2_min_buffer_ms=(exo2_min_buffer_ms if exo2_min_buffer_ms >= 0 else ""),
        exo2_max_buffer_ms=(exo2_max_buffer_ms if exo2_max_buffer_ms >= 0 else ""),
        exo2_playback_buffer_ms=(exo2_playback_buffer_ms if exo2_playback_buffer_ms >= 0 else ""),
        exo2_rebuffer_ms=(exo2_rebuffer_ms if exo2_rebuffer_ms >= 0 else ""),
        directional_sync_min_delta_ms=(directional_sync_min_delta_ms if directional_sync_min_delta_ms >= 0 else ""),
        media3_seek_recovery_enabled=media3_seek_recovery_enabled,
        exo2_seek_recovery_enabled=exo2_seek_recovery_enabled,
        media3_seek_recovery_delay_ms=media3_seek_recovery_delay_ms or "",
        exo2_seek_recovery_delay_ms=exo2_seek_recovery_delay_ms or "",
        media3_seek_policy=media3_seek_policy,
        exo2_seek_policy=exo2_seek_policy,
        media3_codec_mode=media3_codec_mode,
        exo2_codec_mode=exo2_codec_mode,
    )

@mcp.tool()
def dev_connect_server(server_name: str = "", address: str = "", port: int = 31099,
                       save: bool = True, renderer: str = "") -> dict:
    """Connect to SageTV, optionally selecting the ``opengl`` or ``gdx`` UI renderer."""
    return adb.connect_server(server_name=server_name, address=address, port=port,
                              save=save, renderer=renderer)


@mcp.tool()
def dev_exit_session(stop_app: bool = False) -> dict:
    """Disconnect from SageTV and return to the MiniClient server screen. If stop_app is true, force-stop the Dev package after disconnecting."""
    result = adb.exit_session()
    if stop_app:
        result["force_stop"] = adb.force_stop()
        deadline = time.monotonic() + 15.0
        status = adb.app_status()
        while status.get("running") and time.monotonic() < deadline:
            time.sleep(0.25)
            status = adb.app_status()
        force_stop_committed = bool(status.get("forceStopped") and not status.get("foreground"))
        result["status"] = status
        result["stopped"] = not bool(status.get("running")) or force_stop_committed
        result["terminationPending"] = bool(status.get("running") and force_stop_committed)
    else:
        result["stopped"] = False
    return result


@mcp.tool()
def dev_wait_for_ui(
    menu_contains: str = "",
    popup_contains: str = "",
    connected: bool | None = None,
    player_active: bool | None = None,
    menu_present: bool | None = None,
    automation_ready: bool | None = None,
    stable_ms: int = 0,
    timeout_s: float = 20.0,
) -> dict:
    """Wait for MiniClient UI state and optionally require a stable SageTV menu hint.

    ``connected=True`` alone can become true while the SageTV UI is still loading.
    ``menu_present=True`` requires a real non-empty SageTV menu hint.
    ``automation_ready=True`` trusts the debug APK's own automationReady signal, which is
    true only on a clean Main Menu with no active text input/player. ``stable_ms`` requires
    the matching UI state to remain unchanged for that interval before PASS.
    """
    timeout_s = max(0.5, min(float(timeout_s), 120.0))
    stable_ms = max(0, min(int(stable_ms), 30_000))
    menu_match = str(menu_contains).strip().lower()
    popup_match = str(popup_contains).strip().lower()
    deadline = time.monotonic() + timeout_s
    stable_since: float | None = None
    stable_key: tuple | None = None
    last = {}
    while time.monotonic() < deadline:
        last = adb.player_state_snapshot()
        menu_name = str(last.get("menuName") or "").strip()
        popup_name = str(last.get("popupName") or "").strip()
        menu_ok = not menu_match or menu_match in menu_name.lower()
        popup_ok = not popup_match or popup_match in popup_name.lower()
        connected_ok = connected is None or bool(last.get("connected")) == connected
        player_ok = player_active is None or bool(last.get("playerActive")) == player_active
        menu_present_ok = menu_present is None or bool(menu_name) == menu_present
        automation_ready_ok = automation_ready is None or bool(last.get("automationReady")) == automation_ready
        matches = menu_ok and popup_ok and connected_ok and player_ok and menu_present_ok and automation_ready_ok

        now = time.monotonic()
        if matches:
            if stable_ms <= 0:
                return {"passed": True, "state": _compact_state(last), "stableMs": 0}
            candidate_key = (
                bool(last.get("connected")),
                menu_name,
                popup_name,
                bool(last.get("hasTextInput")),
                bool(last.get("playerActive")),
                str(last.get("uiState") or ""),
                bool(last.get("automationReady")),
            )
            if candidate_key != stable_key:
                stable_key = candidate_key
                stable_since = now
            elif stable_since is not None and (now - stable_since) * 1000.0 >= stable_ms:
                return {"passed": True, "state": _compact_state(last), "stableMs": stable_ms}
        else:
            stable_key = None
            stable_since = None
        time.sleep(0.25)
    return {"passed": False, "state": _compact_state(last), "stableMs": stable_ms}


@mcp.tool()
def dev_wait_for_playback_started(
    timeout_s: float = 45.0,
    verify_ms: int = 1500,
    expect_video: bool = True,
    expect_audio: bool = True,
) -> dict:
    """Wait for a started recording and verify that actual video/audio output continues advancing, using the existing debug health snapshot when supported."""
    return _wait_for_playback(
        timeout_s=timeout_s,
        verify_ms=verify_ms,
        expect_video=expect_video,
        expect_audio=expect_audio,
    )


@mcp.tool()
def dev_set_live_channel(channel: str, timeout_s: float = 60.0, verify_ms: int = 3000) -> dict:
    """Tune live TV to an exact dotted channel and verify its identity plus advancing A/V.

    This uses the opt-in OpenSageTV Vibe MiniClient protocol extension so an
    ATSC channel such as ``2.1`` is not rewritten to the legacy ``2-1`` numeric
    UI form. The server must set
    ``miniclient/enable_vibe_channel_set_event=true``.
    """
    requested = str(channel).strip()
    if not re.fullmatch(r"[0-9]+(?:\.[0-9]+)?", requested):
        raise ValueError("valid dotted channel is required")
    timeout_s = max(5.0, min(float(timeout_s), 180.0))
    verify_ms = max(500, min(int(verify_ms), 30000))
    before = adb.player_state_snapshot()
    if not bool(before.get("connected")) or not bool(before.get("playerActive")):
        raise RuntimeError("active live playback is required before setting a channel")
    before_uri = str(before.get("mediaUri", ""))
    before_channel = str(before.get("mediaChannel", "")).strip()
    before_flush_sequence = int(before.get("serverFlushSequence", 0) or 0)
    before_ack_sequence = int(before.get("vibeChannelAckSequence", 0) or 0)

    if before_channel == requested:
        playback = _wait_for_playback(timeout_s=timeout_s, verify_ms=verify_ms)
        passed = bool(playback.get("passed", False))
        return {
            "passed": passed,
            "reason": "already_active" if passed else "channel_playback_not_healthy",
            "channel": requested,
            "confirmedChannel": before_channel,
            "alreadyActive": True,
            "mediaChanged": False,
            "beforeMediaUri": before_uri,
            "afterMediaUri": before_uri,
            "request": {"skipped": True, "reason": "requested_channel_already_active"},
            "playback": playback,
            "transport": "sagetv_media_state_url",
            "serverProperty": "miniclient/enable_vibe_channel_set_event=true",
        }

    request = adb.dev_control("set_live_channel", channel=requested)

    deadline = time.monotonic() + timeout_s
    changed_state: dict = {}
    while time.monotonic() < deadline:
        changed_state = adb.player_state_snapshot()
        changed_uri = str(changed_state.get("mediaUri", ""))
        changed_channel = str(changed_state.get("mediaChannel", "")).strip()
        changed_flush_sequence = int(changed_state.get("serverFlushSequence", 0) or 0)
        changed_ack_sequence = int(changed_state.get("vibeChannelAckSequence", 0) or 0)
        session_changed = changed_flush_sequence > before_flush_sequence or (
            bool(changed_uri) and changed_uri != before_uri
        )
        acknowledged_current = changed_channel == requested and changed_ack_sequence > before_ack_sequence
        if bool(changed_state.get("playerActive")) and (
                (changed_channel == requested and (session_changed or acknowledged_current)) or
                (not changed_channel and changed_uri and changed_uri != before_uri)):
            break
        time.sleep(0.25)
    changed_uri = str(changed_state.get("mediaUri", ""))
    changed_channel = str(changed_state.get("mediaChannel", "")).strip()
    media_changed = bool(changed_state.get("playerActive")) and bool(changed_uri) and changed_uri != before_uri
    channel_confirmed = changed_channel == requested
    flush_changed = int(changed_state.get("serverFlushSequence", 0) or 0) > before_flush_sequence
    ack_changed = int(changed_state.get("vibeChannelAckSequence", 0) or 0) > before_ack_sequence
    legacy_transition_confirmed = not changed_channel and media_changed
    transition_confirmed = media_changed or flush_changed or ack_changed
    acknowledged_already_active = channel_confirmed and ack_changed and not before_channel
    if (not channel_confirmed and not legacy_transition_confirmed) or (
            not transition_confirmed and not acknowledged_already_active):
        return {
            "passed": False,
            "reason": "channel_identity_not_confirmed",
            "channel": requested,
            "confirmedChannel": changed_channel,
            "beforeMediaUri": before_uri,
            "afterMediaUri": changed_uri,
            "beforeFlushSequence": before_flush_sequence,
            "afterFlushSequence": int(changed_state.get("serverFlushSequence", 0) or 0),
            "beforeAckSequence": before_ack_sequence,
            "afterAckSequence": int(changed_state.get("vibeChannelAckSequence", 0) or 0),
            "request": request,
            "state": _compact_state(changed_state),
            "serverProperty": "miniclient/enable_vibe_channel_set_event=true",
        }

    playback = _wait_for_playback(timeout_s=timeout_s, verify_ms=verify_ms)
    passed = bool(playback.get("passed", False))
    return {
        "passed": passed,
        "reason": "ok" if passed else "channel_playback_not_healthy",
        "channel": requested,
        "confirmedChannel": changed_channel,
        "alreadyActive": False,
        "acknowledgedAlreadyActive": acknowledged_already_active,
        "mediaChanged": media_changed,
        "flushChanged": flush_changed,
        "ackChanged": ack_changed,
        "beforeFlushSequence": before_flush_sequence,
        "afterFlushSequence": int(changed_state.get("serverFlushSequence", 0) or 0),
        "beforeAckSequence": before_ack_sequence,
        "afterAckSequence": int(changed_state.get("vibeChannelAckSequence", 0) or 0),
        "beforeMediaUri": before_uri,
        "afterMediaUri": changed_uri,
        "request": request,
        "playback": playback,
        "transport": "miniclient_vibe_channel_set_event",
        "serverProperty": "miniclient/enable_vibe_channel_set_event=true",
    }


@mcp.tool()
def dev_current_media_file() -> dict:
    """Return the current SageTV MediaFile ID for this connected Dev MiniClient UI context.

    This is used by focused tuning sweeps to replay the exact same recording without
    repeating the slower Search UI workflow for every fresh player instance.
    """
    state = adb.player_state_snapshot()
    if not bool(state.get("connected")):
        raise RuntimeError("MiniClient must be connected before dev_current_media_file")
    server_address = str(state.get("serverAddress", "")).strip()
    client_id = str(state.get("clientId", "")).strip()
    if not server_address or not client_id:
        raise RuntimeError(f"Connected MiniClient snapshot is missing server/client identity: {_compact_state(state)}")
    sagex = SagexApiClient.discover(server_address)
    context = sagex.resolve_context(client_id)
    media_file_id = sagex.current_media_file_id(context)
    return {
        "ok": media_file_id is not None,
        "mediaFileId": media_file_id,
        "serverAddress": server_address,
        "clientId": client_id,
        "uiContext": context,
        "sagexBase": sagex.base_url,
    }


@mcp.tool()
def dev_stv_caption_state() -> dict:
    """Read SageTV's server-owned CC state for the connected MiniClient UI."""
    state = adb.player_state_snapshot()
    if not bool(state.get("connected")):
        raise RuntimeError("MiniClient must be connected before reading STV captions")
    server_address = str(state.get("serverAddress", "")).strip()
    client_id = str(state.get("clientId", "")).strip()
    sagex = SagexApiClient.discover(server_address)
    context = sagex.resolve_context(client_id)
    return {
        "state": sagex.closed_caption_state(context),
        "serverAddress": server_address,
        "clientId": client_id,
        "uiContext": context,
    }


@mcp.tool()
def dev_set_stv_caption_state(state: str) -> dict:
    """Set Off/CC1/CC2 through SageTV's standard server CC handler."""
    snapshot = adb.player_state_snapshot()
    if not bool(snapshot.get("connected")):
        raise RuntimeError("MiniClient must be connected before setting STV captions")
    server_address = str(snapshot.get("serverAddress", "")).strip()
    client_id = str(snapshot.get("clientId", "")).strip()
    sagex = SagexApiClient.discover(server_address)
    context = sagex.resolve_context(client_id)
    reply = sagex.set_closed_caption_state(context, state)
    return {
        "requestedState": str(state),
        "state": sagex.closed_caption_state(context),
        "reply": reply,
        "serverAddress": server_address,
        "clientId": client_id,
        "uiContext": context,
    }


@mcp.tool()
def dev_play_media_file_id(
    media_file_id: int,
    timeout_s: float = 45.0,
    verify_ms: int = 1500,
    restart_from_beginning: bool = False,
) -> dict:
    """Watch one exact SageTV MediaFile ID and verify real A/V output.

    Unlike dev_play_video this does not enumerate/search the media library. It is the
    fast deterministic replay path for runtime player-tuning matrices after the first
    Search-selected recording has been identified.
    """
    media_file_id = int(media_file_id)
    if media_file_id <= 0:
        raise ValueError("media_file_id must be > 0")
    state = adb.player_state_snapshot()
    if not bool(state.get("connected")):
        raise RuntimeError("MiniClient must be connected before dev_play_media_file_id")
    server_address = str(state.get("serverAddress", "")).strip()
    client_id = str(state.get("clientId", "")).strip()
    if not server_address or not client_id:
        raise RuntimeError(f"Connected MiniClient snapshot is missing server/client identity: {_compact_state(state)}")
    sagex = SagexApiClient.discover(server_address)
    context = sagex.resolve_context(client_id)
    watch_reply = sagex.watch(context, media_file_id)
    resume_prompt = _resolve_resume_restart_prompt(
        timeout_s=min(15.0, max(5.0, timeout_s / 3.0)),
        restart_from_beginning=restart_from_beginning,
    )
    restart_reply = None
    if restart_from_beginning and not bool(resume_prompt.get("selected")):
        # Some older STVs immediately apply the saved resume point without an
        # inspectable Resume/Restart popup.  Do not race that initial server
        # seek: wait until the MiniPlayer has opened, then issue the explicit
        # server-owned restart request so both SageTV and Android move to zero.
        restart_deadline = time.monotonic() + min(15.0, max(5.0, timeout_s / 3.0))
        while time.monotonic() < restart_deadline:
            restart_state = adb.player_state_snapshot()
            if bool(restart_state.get("playerActive")) and int(
                restart_state.get("serverSeekSequence", 0)
            ) > 0:
                restart_reply = sagex.seek(context, 0)
                break
            time.sleep(0.1)
    playback = _wait_for_playback(timeout_s=timeout_s, verify_ms=verify_ms)
    fullscreen = _promote_preview_to_fullscreen() if bool(playback.get("passed", False)) else {
        "passed": False, "reason": "playback_not_healthy"
    }
    current_id = None
    current_id_error = ""
    try:
        current_id = sagex.current_media_file_id(context)
    except Exception as exc:
        current_id_error = str(exc)
    media_verified = current_id is None or int(current_id) == media_file_id
    passed = bool(playback.get("passed", False)) and bool(fullscreen.get("passed", False)) and media_verified
    return {
        "passed": passed,
        "reason": "ok" if passed else ("current_media_mismatch" if not media_verified else "playback_not_healthy"),
        "mediaFileId": media_file_id,
        "serverAddress": server_address,
        "sagexBase": sagex.base_url,
        "clientId": client_id,
        "uiContext": context,
        "watchReply": watch_reply,
        "restartFromBeginning": bool(restart_from_beginning),
        "resumePrompt": resume_prompt,
        "restartReply": restart_reply,
        "currentMediaFileId": current_id,
        "currentMediaFileIdError": current_id_error,
        "mediaVerified": media_verified,
        "fullscreen": fullscreen,
        "playback": playback,
    }


@mcp.tool()
def dev_play_video(video_name: str, timeout_s: float = 45.0, verify_ms: int = 1500) -> dict:
    """Play a SageTV MediaFile on this connected MiniClient by name. The caller supplies only the video/recording name; MCP resolves the server API, this client's UI context, the unique MediaFile, invokes Watch, and verifies real A/V output."""
    requested = str(video_name).strip()
    if not requested:
        raise ValueError("video_name is required")
    state = adb.player_state_snapshot()
    if not bool(state.get("connected")):
        raise RuntimeError("MiniClient must be connected before dev_play_video")
    server_address = str(state.get("serverAddress", "")).strip()
    client_id = str(state.get("clientId", "")).strip()
    if not server_address:
        raise RuntimeError(f"Connected MiniClient snapshot has no serverAddress: {_compact_state(state)}")
    if not client_id:
        raise RuntimeError(f"Connected MiniClient snapshot has no clientId: {_compact_state(state)}")

    sagex = SagexApiClient.discover(server_address)
    context = sagex.resolve_context(client_id)
    matches, match_mode = sagex.find_media(requested)
    if not matches:
        return {
            "passed": False,
            "reason": "video_not_found",
            "requestedName": requested,
            "matchMode": match_mode,
            "serverAddress": server_address,
            "sagexBase": sagex.base_url,
            "uiContext": context,
            "matches": [],
        }
    if len(matches) != 1:
        return {
            "passed": False,
            "reason": "ambiguous_video_name",
            "requestedName": requested,
            "matchMode": match_mode,
            "serverAddress": server_address,
            "sagexBase": sagex.base_url,
            "uiContext": context,
            "matches": [m.as_dict() for m in matches[:25]],
        }

    match = matches[0]
    watch_reply = sagex.watch(context, match.media_file_id)
    playback = _wait_for_playback(timeout_s=timeout_s, verify_ms=verify_ms)
    fullscreen = _promote_preview_to_fullscreen() if bool(playback.get("passed", False)) else {
        "passed": False, "reason": "playback_not_healthy"
    }
    current_id = None
    current_id_error = ""
    try:
        current_id = sagex.current_media_file_id(context)
    except Exception as exc:
        current_id_error = str(exc)
    media_verified = current_id is None or int(current_id) == int(match.media_file_id)
    passed = bool(playback.get("passed", False)) and bool(fullscreen.get("passed", False)) and media_verified
    return {
        "passed": passed,
        "reason": "ok" if passed else ("current_media_mismatch" if not media_verified else "playback_not_healthy"),
        "requestedName": requested,
        "matchMode": match_mode,
        "matched": match.as_dict(),
        "serverAddress": server_address,
        "sagexBase": sagex.base_url,
        "clientId": client_id,
        "uiContext": context,
        "watchReply": watch_reply,
        "currentMediaFileId": current_id,
        "currentMediaFileIdError": current_id_error,
        "mediaVerified": media_verified,
        "fullscreen": fullscreen,
        "playback": playback,
    }


@mcp.tool()
def dev_play_server_path(
    server_path: str,
    timeout_s: float = 45.0,
    verify_ms: int = 1500,
    restart_from_beginning: bool = False,
) -> dict:
    """Play one exact indexed SageTV MediaFile path in this MiniClient UI session.

    This uses the opt-in OpenSageTV Vibe MiniClient protocol extension instead
    of Sagex/Jetty or STV navigation. The server must set
    ``miniclient/enable_vibe_watch_file_event=true``. A pre-existing playback
    session is stopped first so an ignored/unsupported request cannot produce a
    false pass from media that was already advancing.
    """
    requested = str(server_path).strip()
    if not requested:
        raise ValueError("server_path is required")
    if "\x00" in requested or "\r" in requested or "\n" in requested:
        raise ValueError("server_path must be one line and must not contain NUL")
    state = adb.player_state_snapshot()
    if not bool(state.get("connected")):
        raise RuntimeError("MiniClient must be connected before dev_play_server_path")

    stop_result: dict | None = None
    stopped_state = state
    if bool(state.get("playerActive")):
        stop_result = adb.dev_control("command", command="stop")
        stop_deadline = time.monotonic() + min(15.0, max(3.0, float(timeout_s) / 3.0))
        while time.monotonic() < stop_deadline:
            stopped_state = adb.player_state_snapshot()
            if not bool(stopped_state.get("playerActive")):
                break
            time.sleep(0.2)
        if bool(stopped_state.get("playerActive")):
            return {
                "passed": False,
                "reason": "existing_playback_did_not_stop",
                "requestedServerPath": requested,
                "stopResult": stop_result,
                "state": _compact_state(stopped_state),
            }

    request = adb.dev_control(
        "watch_server_file",
        server_path=requested,
        restart_from_beginning="true" if restart_from_beginning else "false",
    )
    resume_prompt = _resolve_resume_restart_prompt(
        timeout_s=min(8.0, timeout_s),
        restart_from_beginning=bool(restart_from_beginning),
    )
    playback = _wait_for_playback(timeout_s=timeout_s, verify_ms=verify_ms)
    fullscreen = _promote_preview_to_fullscreen() if bool(playback.get("passed", False)) else {
        "passed": False, "reason": "playback_not_healthy"
    }
    passed = bool(playback.get("passed", False)) and bool(fullscreen.get("passed", False))
    return {
        "passed": passed,
        "reason": "ok" if passed else "server_path_playback_not_started",
        "requestedServerPath": requested,
        "transport": "miniclient_vibe_watch_file_event",
        "serverProperty": "miniclient/enable_vibe_watch_file_event=true",
        "stopResult": stop_result,
        "request": request,
        "resumePrompt": resume_prompt,
        "restartFromBeginning": bool(restart_from_beginning),
        "fullscreen": fullscreen,
        "playback": playback,
    }


@mcp.tool()
def dev_start_recording(
    navigation_commands: list[str],
    open_recordings: bool = True,
    recordings_menu_contains: str = "record",
    command_delay_ms: int = 500,
    ui_timeout_s: float = 20.0,
    playback_timeout_s: float = 45.0,
    verify_ms: int = 1500,
) -> dict:
    """Open SageTV Recordings, run a configurable SageCommand navigation sequence (for the user's STV layout), then verify real playback starts."""
    if not navigation_commands:
        raise ValueError("navigation_commands must select/start a known recording for the user's SageTV STV layout")
    command_delay_ms = max(0, min(int(command_delay_ms), 10000))
    steps: list[dict] = []
    if open_recordings:
        steps.append({"recordings": adb.sage_command("recordings")})
        menu_match = str(recordings_menu_contains).strip().lower()
        if menu_match:
            deadline = time.monotonic() + max(0.5, min(float(ui_timeout_s), 120.0))
            menu_state = {}
            while time.monotonic() < deadline:
                menu_state = adb.player_state_snapshot()
                if menu_match in str(menu_state.get("menuName", "")).lower():
                    break
                time.sleep(0.25)
            steps.append({"recordings_menu": _compact_state(menu_state)})
    sent = []
    for command in navigation_commands:
        sent.append(adb.sage_command(str(command).strip().lower()))
        if command_delay_ms:
            time.sleep(command_delay_ms / 1000.0)
    playback = _wait_for_playback(timeout_s=playback_timeout_s, verify_ms=verify_ms)
    return {
        "passed": bool(playback.get("passed", False)),
        "navigation_commands": navigation_commands,
        "open_recordings": open_recordings,
        "steps": steps,
        "sent": sent,
        "playback": playback,
    }


@mcp.tool()
def dev_search() -> dict:
    """Open SageTV Search in the currently connected MiniClient using the native SageTV Search command."""
    return adb.sage_command("search")




@mcp.tool()
def dev_open_search(timeout_s: float = 8.0, require_ime: bool = True, suppress_ime: bool = False) -> dict:
    """Open SageTV Search and verify the UI reached text-input state.

    ``suppress_ime=True`` is the debug/MCP-native text mode: the app suppresses Android
    soft-keyboard display before Search is opened while still honoring SageTV's
    ``hasTextInput`` hint. Manual/non-MCP behavior is unchanged.
    """
    timeout_s = max(0.5, min(float(timeout_s), 30.0))
    suppression = None
    if suppress_ime:
        suppression = adb.set_ime_suppression(True)
        require_ime = False
    before = adb.player_state_snapshot()
    try:
        command = adb.sage_command("search")
    except Exception as exc:
        if suppress_ime:
            try:
                adb.set_ime_suppression(False)
            except Exception:
                pass
        return {
            "passed": False,
            "phase": "command_search",
            "error": str(exc),
            "before": _compact_state(before),
            "imeSuppression": suppression,
        }

    deadline = time.monotonic() + timeout_s
    last = before
    text_input_seen_ms = -1
    ime_seen_ms = -1
    started = time.monotonic()
    while time.monotonic() < deadline:
        last = adb.player_state_snapshot()
        elapsed_ms = int(round((time.monotonic() - started) * 1000.0))
        if text_input_seen_ms < 0 and bool(last.get("hasTextInput", False)):
            text_input_seen_ms = elapsed_ms
        ime_known = bool(last.get("imeVisibleKnown", False))
        ime_visible = bool(last.get("imeVisible", False))
        if ime_seen_ms < 0 and ime_known and ime_visible:
            ime_seen_ms = elapsed_ms
        text_ok = bool(last.get("hasTextInput", False))
        ime_ok = (not require_ime) or (ime_known and ime_visible)
        if text_ok and ime_ok:
            return {
                "passed": True,
                "phase": "ready",
                "searchCommand": command,
                "textInputSeenMs": text_input_seen_ms,
                "imeVisibleSeenMs": ime_seen_ms,
                "state": _compact_state(last),
                "imeSuppression": suppression,
            }
        time.sleep(0.10)

    phase = "wait_text_input" if not bool(last.get("hasTextInput", False)) else "wait_ime_visible"
    if suppress_ime:
        try:
            adb.set_ime_suppression(False)
        except Exception:
            pass
    return {
        "passed": False,
        "phase": phase,
        "searchCommand": command,
        "textInputSeenMs": text_input_seen_ms,
        "imeVisibleSeenMs": ime_seen_ms,
        "state": _compact_state(last),
        "imeSuppression": suppression,
    }


@mcp.tool()
def dev_wait_for_ime(visible: bool, timeout_s: float = 5.0) -> dict:
    """Wait until Android debug status positively verifies the IME visible or hidden."""
    timeout_s = max(0.25, min(float(timeout_s), 30.0))
    deadline = time.monotonic() + timeout_s
    last: dict = {}
    while time.monotonic() < deadline:
        last = adb.player_state_snapshot()
        known = bool(last.get("imeVisibleKnown", False))
        actual = bool(last.get("imeVisible", False))
        if known and actual == bool(visible):
            return {"passed": True, "visible": bool(visible), "state": _compact_state(last)}
        time.sleep(0.10)
    return {"passed": False, "visible": bool(visible), "state": _compact_state(last)}


@mcp.tool()
def dev_type_text(text: str, submit: bool = False, char_delay_ms: int = 0) -> dict:
    """Inject text into SageTV Search.

    char_delay_ms=0 uses the debug APK's native MiniClient key-event channel and
    reproduces KeyMapProcessor's keyCode/keyChar encoding. A positive delay is
    retained only as the legacy Android/ADB diagnostic mode.
    """
    requested = str(text)
    delay_ms = max(0, min(int(char_delay_ms), 2000))
    if delay_ms == 0:
        native = adb.native_input_text(requested)
        injection = {
            "inputPath": native.get("inputPath", "miniclient_native_key_event"),
            "charsRequested": len(requested),
            "charsIssued": int(native.get("charsSent", len(requested))),
            "charDelayMs": 0,
            "elapsedMs": 0,
            "events": [],
            "raw": native.get("raw_broadcast", ""),
            "nativeResult": native,
        }
    else:
        injection = adb.input_text_paced(requested, char_delay_ms=delay_ms)
    next_result = adb.key("ENTER") if submit else ""
    return {
        "passed": True,
        "commandCompleted": True,
        "fieldContentVerified": False,
        "textRequested": requested,
        "inputPath": injection.get("inputPath", "android_os_input_text"),
        "charsRequested": injection.get("charsRequested", len(requested)),
        "charsIssued": injection.get("charsIssued", 0),
        "charDelayMs": injection.get("charDelayMs", delay_ms),
        "elapsedMs": injection.get("elapsedMs", 0),
        "effectiveCharMs": injection.get("effectiveCharMs", 0.0),
        "events": injection.get("events", []),
        "raw": injection.get("raw", ""),
        "submitted": bool(submit),
        "next": next_result,
    }


@mcp.tool()
def dev_input_text_native(text: str) -> dict:
    """Send text directly through the MiniClient/SageTV keyboard event channel; no Android IME/ADB text injection."""
    return adb.native_input_text(text)


@mcp.tool()
def dev_input_text_keyboard(text: str) -> dict:
    """Type through Android's OS input service into the focused MiniClient view.

    Call only after IME visibility has been positively verified.  This avoids
    debug-receiver-side View.dispatchKeyEvent(), which can block on Fire TV.
    """
    requested = str(text)
    if not requested:
        raise ValueError("text must not be empty")
    return adb.keyboard_input_text(requested)


@mcp.tool()
def dev_input_text_direct(text: str) -> dict:
    """Compatibility alias for dev_input_text_keyboard; retained for v0.5.39 callers."""
    return dev_input_text_keyboard(text)


@mcp.tool()
def dev_hide_ime() -> dict:
    """Hide the Android IME directly from the debug Activity; no BACK key is injected."""
    return adb.hide_ime()


@mcp.tool()
def dev_set_ime_suppression(enabled: bool) -> dict:
    """Debug-only: suppress or restore Android IME display for MCP native SageTV text entry."""
    return adb.set_ime_suppression(bool(enabled))


@mcp.tool()
def dev_player_control(action: str) -> dict:
    """Call the active Android MiniPlayerPlugin directly: play, pause, or stop."""
    return adb.player_control(action)


@mcp.tool()
def dev_seek_relative(delta_ms: int) -> dict:
    """Seek the active Android player directly by a caller-supplied signed millisecond delta."""
    return adb.seek_relative(int(delta_ms))


@mcp.tool()
def dev_frame_step(amount: int = 1) -> dict:
    """Step the paused Pull/SMB player by a non-zero signed frame count."""
    return adb.frame_step(int(amount))


@mcp.tool()
def dev_playback_rate(rate: float) -> dict:
    """Debug-only command-30 rate request for bounded native/seek-scan testing."""
    return adb.playback_rate(float(rate))


@mcp.tool()
def dev_media3_fast_switch_file(server_path: str) -> dict:
    """Debug-only physical gate: replace the active completed Media3 Pull/SMB file without recreating the player."""
    return adb.media3_fast_switch_file(server_path)


@mcp.tool()
def dev_skip_forward(skip_ms: int) -> dict:
    """Direct Android-player skip forward by the required caller-supplied milliseconds."""
    value = int(skip_ms)
    if value <= 0:
        raise ValueError("skip_ms must be > 0")
    return adb.seek_relative(value)


@mcp.tool()
def dev_skip_backward(skip_ms: int) -> dict:
    """Direct Android-player skip backward by the required caller-supplied milliseconds."""
    value = int(skip_ms)
    if value <= 0:
        raise ValueError("skip_ms must be > 0")
    return adb.seek_relative(-value)


@mcp.tool()
def dev_comskip(direction: str) -> dict:
    """Run Comskip without Android key injection. The debug APK posts SageTV RIGHT/LEFT internally so the STV can resolve its marker target."""
    return adb.comskip(direction)


@mcp.tool()
def dev_request_competing_audio_focus(mode: str = "transient") -> dict:
    """Debug-only: request competing transient, duck, or permanent audio focus."""
    return adb.request_competing_audio_focus(mode)


@mcp.tool()
def dev_abandon_competing_audio_focus() -> dict:
    """Release the debug-only competing focus owner so transient focus can return."""
    return adb.abandon_competing_audio_focus()


@mcp.tool()
def dev_set_subtitle_track(index: int) -> dict:
    """Debug-only: select a present subtitle/caption track, or use -1 for off."""
    return adb.set_subtitle_track(index)


@mcp.tool()
def dev_search_text(
    text: str,
    submit: bool = False,
    dismiss_keyboard: bool = True,
    input_timeout_s: float = 8.0,
    keyboard_settle_ms: int = 0,
    post_commands: str = "",
    post_keys: str = "",
    post_submit_settle_ms: int = 1000,
    key_delay_ms: int = 350,
) -> dict:
    """Open native SageTV Search and enter text through MiniClient-native keyboard events.

    Android IME display is suppressed only for this debug/MCP operation. Normal manual
    text entry remains unchanged. No Android BACK key is sent; an already-visible IME
    is hidden directly only when visibility is positively detected.
    """
    requested = str(text).strip()
    if not requested:
        raise ValueError("text must not be empty")

    suppression = adb.set_ime_suppression(True)
    search = None
    input_state: dict = {}
    typed = None
    next_result = ""
    keyboard_dismiss_result = {"skipped": True, "reason": "ime_not_visible"}
    commands = [k.strip() for k in str(post_commands).split(",") if k.strip()]
    keys = [k.strip() for k in str(post_keys).split(",") if k.strip()]
    post_command_results = []
    post_key_results = []
    final_state: dict = {}
    try:
        search = adb.sage_command("search")
        deadline = time.monotonic() + max(0.5, min(float(input_timeout_s), 30.0))
        while time.monotonic() < deadline:
            input_state = adb.player_state_snapshot()
            if bool(input_state.get("hasTextInput", False)):
                break
            time.sleep(0.20)
        if not bool(input_state.get("hasTextInput", False)):
            raise RuntimeError(
                "SageTV Search opened but no text-input state was detected; refusing to type blindly"
            )

        settle_ms = max(0, min(int(keyboard_settle_ms), 5000))
        if settle_ms:
            time.sleep(settle_ms / 1000.0)
        typed = adb.native_input_text(requested)
        time.sleep(0.10)

        after_text = adb.player_state_snapshot()
        if (not submit and dismiss_keyboard
                and bool(after_text.get("imeVisibleKnown", False))
                and bool(after_text.get("imeVisible", False))):
            keyboard_dismiss_result = adb.hide_ime()

        next_result = adb.sage_command("select") if submit else ""

        if commands or keys:
            settle_after_submit = max(0, min(int(post_submit_settle_ms), 10000))
            if settle_after_submit:
                time.sleep(settle_after_submit / 1000.0)
            delay_s = max(0, min(int(key_delay_ms), 10000)) / 1000.0
            for command in commands:
                post_command_results.append(adb.sage_command(command))
                if delay_s:
                    time.sleep(delay_s)
            if keys:
                post_key_results = adb.key_sequence(keys, delay_ms=max(0, min(int(key_delay_ms), 10000)))
        else:
            time.sleep(0.30 if submit else 0.05)

        final_state = adb.player_state_snapshot()
    finally:
        restore = adb.set_ime_suppression(False)

    return {
        "passed": True,
        "text": requested,
        "search": search,
        "inputState": _compact_state(input_state),
        "typed": typed,
        "submitted": bool(submit),
        "next": next_result,
        "imeSuppression": suppression,
        "imeSuppressionRestore": restore,
        "keyboardDismissed": not bool(keyboard_dismiss_result.get("skipped", False)),
        "keyboardDismiss": keyboard_dismiss_result,
        "postCommands": commands,
        "postCommandResults": post_command_results,
        "postKeys": keys,
        "postKeyResults": post_key_results,
        "finalState": _compact_state(final_state),
    }


@mcp.tool()
def dev_send_sequence(sequence: str) -> dict:
    """Execute one explicit multiline MCP input sequence.

    Supported actions are:
      command <sage-command>  - send a native SageTV command
      sendkey <android-key>    - inject an Android key event
      sendtext <text>          - legacy Android text injection
      keyboardtext <text>      - type through Android OS input service into focused MiniClient view
      directtext <text>        - compatibility alias for keyboardtext
      hideime                  - hide the Android IME directly (no BACK key)
      delay <milliseconds>     - wait before the next action
      waittextinput <timeout-ms> - wait until Android reports an active text-input field
      waitimevisible <timeout-ms> - wait until Android verifies the soft keyboard is visible
      waitimehidden <timeout-ms> - wait until Android verifies the soft keyboard is hidden

    Actions execute strictly in the order provided; no implicit keys, text, or delays are added.
    """
    actions = parse_sequence_script(sequence)
    results: list[dict] = []
    for item in actions:
        if item.action == "command":
            result = adb.sage_command(str(item.value))
        elif item.action == "sendkey":
            result = adb.key(str(item.value))
        elif item.action == "sendtext":
            result = adb.input_text(str(item.value))
        elif item.action in {"keyboardtext", "directtext"}:
            result = adb.keyboard_input_text(str(item.value))
        elif item.action == "hideime":
            result = adb.hide_ime()
        elif item.action == "delay":
            delay_ms = int(item.value)
            time.sleep(delay_ms / 1000.0)
            result = f"slept {delay_ms} ms"
        elif item.action == "waittextinput":
            timeout_ms = int(item.value)
            deadline = time.monotonic() + (timeout_ms / 1000.0)
            last_state: dict = {}
            while time.monotonic() < deadline:
                last_state = adb.player_state_snapshot()
                if bool(last_state.get("hasTextInput", False)):
                    break
                time.sleep(0.10)
            if not bool(last_state.get("hasTextInput", False)):
                raise RuntimeError(
                    f"line {item.line}: timed out after {timeout_ms} ms waiting for Android text input; "
                    f"uiState={last_state.get('uiState')!r} menu={last_state.get('menuName')!r}"
                )
            result = {
                "passed": True,
                "timeoutMs": timeout_ms,
                "uiState": last_state.get("uiState"),
                "menuName": last_state.get("menuName"),
                "hasTextInput": True,
            }
        elif item.action in {"waitimevisible", "waitimehidden"}:
            timeout_ms = int(item.value)
            want_visible = item.action == "waitimevisible"
            deadline = time.monotonic() + (timeout_ms / 1000.0)
            last_state: dict = {}
            matched = False
            while time.monotonic() < deadline:
                last_state = adb.player_state_snapshot()
                known = bool(last_state.get("imeVisibleKnown", False))
                visible = bool(last_state.get("imeVisible", False))
                if known and visible == want_visible:
                    matched = True
                    break
                time.sleep(0.10)
            if not matched:
                desired = "visible" if want_visible else "hidden"
                raise RuntimeError(
                    f"line {item.line}: timed out after {timeout_ms} ms waiting for Android IME {desired}; "
                    f"imeVisibleKnown={last_state.get('imeVisibleKnown')!r} "
                    f"imeVisible={last_state.get('imeVisible')!r} "
                    f"imeRequested={last_state.get('imeRequested')!r} "
                    f"uiState={last_state.get('uiState')!r} menu={last_state.get('menuName')!r}"
                )
            result = {
                "passed": True,
                "timeoutMs": timeout_ms,
                "imeVisibleKnown": True,
                "imeVisible": want_visible,
                "imeRequested": last_state.get("imeRequested"),
                "uiState": last_state.get("uiState"),
                "menuName": last_state.get("menuName"),
            }
        else:  # pragma: no cover - parser guarantees the action set
            raise RuntimeError(f"unsupported parsed action: {item.action}")
        results.append({
            "line": item.line,
            "action": item.action,
            "value": item.value,
            "result": result,
        })
    return {
        "passed": True,
        "actionCount": len(actions),
        "results": results,
    }


@mcp.tool()
def dev_sage_command(command: str) -> dict:
    """Send an exact SageTV command by SageCommand key (for example ff, rew, ff_2, rew_2, pause, play, play_pause, stop)."""
    return adb.sage_command(command)

@mcp.tool()
def dev_sage_command_sequence(commands: list[str], delay_ms: int = 350, settle_ms: int = 1500) -> dict:
    """Run a repeatable SageTV command sequence against the currently playing recording and return before/after timeline state."""
    return _run_sage_sequence(commands, delay_ms, settle_ms)

@mcp.tool()
def dev_run_seek_check(
    commands: list[str],
    expected_net_ms: int,
    tolerance_ms: int = 4000,
    delay_ms: int = 350,
    settle_ms: int = 300,
    recovery_timeout_ms: int = 8000,
    verify_playback_ms: int = 2500,
    health_poll_ms: int = 250,
) -> dict:
    """Run a SageTV skip sequence and verify that real video/audio output resumes and stays active. Timeline error is diagnostic only when output-health probing is available."""
    result = adb.android_skip_check(
        commands,
        delay_ms=delay_ms,
        settle_ms=settle_ms,
        recovery_timeout_ms=recovery_timeout_ms,
        verify_playback_ms=verify_playback_ms,
        health_poll_ms=health_poll_ms,
    )
    observed = int(result.get("playbackAdjustedDeltaMs", result.get("timelineDeltaMs", 0)))
    error_ms = observed - int(expected_net_ms)
    tolerance_ms = max(0, int(tolerance_ms))
    timeline_within_tolerance = abs(error_ms) <= tolerance_ms
    health_performed = bool(result.get("healthCheckPerformed", False))
    if health_performed:
        passed = bool(result.get("outputHealthy", False))
        verdict = "output_health"
    else:
        passed = timeline_within_tolerance
        verdict = "timeline_fallback"
    result.update({
        "commands": commands,
        "expected_net_ms": int(expected_net_ms),
        "observed_net_ms": observed,
        "error_ms": error_ms,
        "tolerance_ms": tolerance_ms,
        "timeline_within_tolerance": timeline_within_tolerance,
        "passed": passed,
        "verdict_basis": verdict,
        "measurement": "android_output_health_with_ui_timeline_context",
    })
    return result

@mcp.tool()
def dev_run_relative_seek_check(
    deltas_ms: list[int],
    expected_net_ms: int | None = None,
    tolerance_ms: int = 4000,
    delay_ms: int = 350,
    settle_ms: int = 300,
    recovery_timeout_ms: int = 8000,
    verify_playback_ms: int = 2500,
    health_poll_ms: int = 250,
) -> dict:
    """Run direct Android-player relative seeks and verify real A/V recovery. No SageTV skip command or Android key is sent."""
    values = [int(value) for value in deltas_ms]
    if not values:
        raise ValueError("deltas_ms must contain at least one value")
    _require_debug_seek_coordinate_version()
    expected = sum(values) if expected_net_ms is None else int(expected_net_ms)
    requested_watchdog_ms = max(250, min(int(recovery_timeout_ms), 300000))
    _require_debug_watchdog_capacity(requested_watchdog_ms)
    step_started = time.monotonic()
    result = adb.android_relative_seek_check(
        values,
        delay_ms=delay_ms,
        settle_ms=settle_ms,
        recovery_timeout_ms=requested_watchdog_ms,
        verify_playback_ms=verify_playback_ms,
        health_poll_ms=health_poll_ms,
    )
    step_elapsed_ms = int(round((time.monotonic() - step_started) * 1000.0))
    applied_watchdog_ms = int(result.get("recoveryTimeoutMs", requested_watchdog_ms))
    if applied_watchdog_ms != requested_watchdog_ms:
        raise RuntimeError(
            f"Debug APK watchdog mismatch: requested {requested_watchdog_ms} ms but Android applied "
            f"{applied_watchdog_ms} ms. Rebuild/install the current debug APK before trusting watchdog results."
        )
    observed = int(result.get("playbackAdjustedDeltaMs", result.get("timelineDeltaMs", 0)))
    error_ms = observed - expected
    tolerance_ms = max(0, int(tolerance_ms))
    timeline_within_tolerance = abs(error_ms) <= tolerance_ms
    health_performed = bool(result.get("healthCheckPerformed", False))
    passed = bool(result.get("outputHealthy", False)) if health_performed else timeline_within_tolerance
    result.update({
        "deltas_ms": values,
        "expected_net_ms": expected,
        "observed_net_ms": observed,
        "error_ms": error_ms,
        "tolerance_ms": tolerance_ms,
        "timeline_within_tolerance": timeline_within_tolerance,
        "passed": passed,
        "watchdog_requested_ms": requested_watchdog_ms,
        "watchdog_applied_ms": applied_watchdog_ms,
        "watchdog_step_elapsed_ms": step_elapsed_ms,
        "watchdog_verified": applied_watchdog_ms == requested_watchdog_ms,
        "verdict_basis": "output_health" if health_performed else "timeline_fallback",
        "measurement": "android_debug_direct_player_relative_seek_with_output_health",
    })
    return result


@mcp.tool()
def dev_run_comskip_check(
    direction: str = "right",
    expected_target_ms: int = -1,
    tolerance_ms: int = 5000,
    delay_ms: int = 350,
    settle_ms: int = 300,
    watchdog_ms: int = 180000,
    recovery_timeout_ms: int = 0,
    verify_playback_ms: int = 3000,
    health_poll_ms: int = 250,
) -> dict:
    """Run the debug APK's dedicated Comskip operation and verify real A/V recovery.

    No Android key is injected and no video-arrow preference is resolved. The debug APK
    posts SageTV RIGHT/LEFT directly through EventRouter because the STV/server owns the
    commercial-marker target; the Android player itself does not receive marker metadata.
    """
    effective_watchdog_ms = int(recovery_timeout_ms) if int(recovery_timeout_ms) > 0 else int(watchdog_ms)
    effective_watchdog_ms = max(250, min(effective_watchdog_ms, 300000))
    _require_debug_watchdog_capacity(effective_watchdog_ms)
    step_started = time.monotonic()
    result = adb.android_comskip_check(
        direction=direction,
        delay_ms=delay_ms,
        settle_ms=settle_ms,
        recovery_timeout_ms=effective_watchdog_ms,
        verify_playback_ms=verify_playback_ms,
        health_poll_ms=health_poll_ms,
    )
    step_elapsed_ms = int(round((time.monotonic() - step_started) * 1000.0))
    applied_watchdog_ms = int(result.get("recoveryTimeoutMs", effective_watchdog_ms))
    if applied_watchdog_ms != effective_watchdog_ms:
        raise RuntimeError(
            f"Debug APK watchdog mismatch: requested {effective_watchdog_ms} ms but Android applied "
            f"{applied_watchdog_ms} ms. Rebuild/install the current debug APK before trusting watchdog results."
        )
    landing_ms = int(result.get("landingTimelineMs", result.get("timelineAfterMs", -1)))
    target_known = int(expected_target_ms) >= 0
    target_error_ms = landing_ms - int(expected_target_ms) if target_known and landing_ms >= 0 else 0
    target_within_tolerance = (abs(target_error_ms) <= max(0, int(tolerance_ms))) if target_known else None
    server_seek_observed = bool(result.get("serverSeekObserved", False))
    health_performed = bool(result.get("healthCheckPerformed", False))
    output_healthy = bool(result.get("outputHealthy", False)) if health_performed else bool(result.get("stillPlaying", False))
    recovered = output_healthy and (target_within_tolerance is not False) and (server_seek_observed or not target_known)
    watchdog_expired = not recovered
    result.update({
        "expected_target_ms": int(expected_target_ms),
        "target_known": target_known,
        "target_error_ms": target_error_ms,
        "target_tolerance_ms": max(0, int(tolerance_ms)),
        "target_within_tolerance": target_within_tolerance,
        "server_seek_required": target_known,
        "server_seek_observed": server_seek_observed,
        "watchdog_ms": effective_watchdog_ms,
        "watchdog_requested_ms": effective_watchdog_ms,
        "watchdog_applied_ms": applied_watchdog_ms,
        "watchdog_step_elapsed_ms": step_elapsed_ms,
        "watchdog_verified": applied_watchdog_ms == effective_watchdog_ms,
        "watchdog_expired": watchdog_expired,
        "recovered": recovered,
        "observation_complete": True,
        "cause": "undetermined" if watchdog_expired else "none",
        # Observation completed successfully even when media recovery outlives the watchdog.
        # Callers must use `recovered` / `watchdog_expired` for the media outcome.
        "passed": True,
        "verdict_basis": "observation_complete_with_separate_media_recovery_outcome",
        "measurement": "android_debug_direct_comskip_event_with_output_recovery_timeline",
    })
    return result


@mcp.tool()
def dev_wait_for_media_position(target_ms: int, tolerance_ms: int = 2000, timeout_s: float = 15.0) -> dict:
    """Poll one-shot debug snapshots until the SageTV media timeline reaches the requested target tolerance."""
    target_ms = max(0, int(target_ms))
    tolerance_ms = max(0, int(tolerance_ms))
    timeout_s = max(0.1, min(float(timeout_s), 120.0))
    deadline = time.monotonic() + timeout_s
    last = {}
    while time.monotonic() < deadline:
        last = adb.player_state_snapshot()
        current = _snapshot_media_time(last)
        if abs(current - target_ms) <= tolerance_ms:
            return {"passed": True, "target_ms": target_ms, "tolerance_ms": tolerance_ms, "state": _compact_state(last)}
        time.sleep(0.25)
    return {"passed": False, "target_ms": target_ms, "tolerance_ms": tolerance_ms, "state": _compact_state(last)}

@mcp.tool()
def dev_seek_time(target_ms: int, tolerance_ms: int = 2000, timeout_s: float = 15.0, stable_ms: int = 1200) -> dict:
    """Debug-only Android player seek verified by recovered A/V output.

    ``target_ms`` is still passed directly to the active MiniPlayerPlugin, but
    the automation verdict intentionally does NOT depend on where the backend
    lands.  A seek passes when real video/audio output counters resume advancing
    after the action.  Position/timeline values are retained only as diagnostics.

    This backend-neutral rule avoids false 180-second waits caused by stale
    wrapper seek/readiness flags or backend-specific seek landing behavior.
    """
    target_ms = int(target_ms)
    if target_ms < 0:
        raise ValueError("target_ms must be >= 0")
    _require_debug_seek_coordinate_version()
    tolerance_ms = max(0, int(tolerance_ms))  # retained for API/report compatibility
    stable_ms = max(250, min(int(stable_ms), 10000))
    timeout_s = max(0.1, min(float(timeout_s), 300.0))

    before = adb.player_state_snapshot()
    accepted = adb.seek_time(target_ms)
    started = time.monotonic()
    playback = _wait_for_playback(timeout_s=timeout_s, verify_ms=stable_ms)
    recovery_ms = int(round((time.monotonic() - started) * 1000.0))
    after = playback.get("after") or playback.get("state") or {}
    health = playback.get("health") or {}

    try:
        reached_ms = _snapshot_player_position(after)
    except RuntimeError:
        reached_ms = -1

    recovered = bool(playback.get("passed", False))
    return {
        "passed": recovered,
        "recovered": recovered,
        "outputHealthy": recovered,
        "watchdog_expired": not recovered,
        "recoveryMs": recovery_ms,
        "target_ms": target_ms,
        "reached_ms": reached_ms,
        "tolerance_ms": tolerance_ms,
        "stable_ms": stable_ms,
        "videoStillAdvancing": bool(health.get("video_advancing", False)),
        "audioStillAdvancing": bool(health.get("audio_advancing", False)),
        "stillPlaying": bool(health.get("state_playing", False)),
        "videoExpected": bool(health.get("video_expected", False)),
        "audioExpected": bool(health.get("audio_expected", False)),
        "accepted": accepted,
        "playback": playback,
        "before": _compact_state(before),
        "state": after,
        "measurement": "android_debug_seek_output_counter_recovery",
        "verdict_basis": "video_audio_output_counters_advancing_position_diagnostic_only",
    }


@mcp.tool()
def dev_server_seek_time(target_ms: int, tolerance_ms: int = 2000,
                         timeout_s: float = 30.0, stable_ms: int = 1200) -> dict:
    """Seek through SageTV's UI VideoFrame and verify landing plus A/V recovery.

    This is the required positioning path for server-owned Push and DVD
    sessions.  A local MiniPlayerPlugin seek cannot reposition MiniDVDPlayer's
    server-side DVD VM or its outgoing byte stream.
    """
    target_ms = int(target_ms)
    if target_ms < 0:
        raise ValueError("target_ms must be >= 0")
    tolerance_ms = max(0, min(int(tolerance_ms), 30000))
    timeout_s = max(1.0, min(float(timeout_s), 180.0))
    stable_ms = max(250, min(int(stable_ms), 10000))

    before = adb.player_state_snapshot()
    if not bool(before.get("connected")) or not bool(before.get("playerActive")):
        raise RuntimeError("MiniClient must have active playback before dev_server_seek_time")
    server_address = str(before.get("serverAddress", "")).strip()
    client_id = str(before.get("clientId", "")).strip()
    if not server_address or not client_id:
        raise RuntimeError(f"Playback snapshot is missing server/client identity: {_compact_state(before)}")

    # Use the same authenticated MiniClient event channel as playback.  The
    # test server intentionally does not require Sagex/Jetty, and DVD seeking
    # must be scoped to this exact UI connection.
    started = time.monotonic()
    deadline = started + timeout_s
    last = before
    reached_ms = -1
    replies: list[dict] = []
    requested_ms = target_ms
    previous_stc_count = int(before.get("dvdStcCount", -1))
    max_attempts = 3
    for attempt in range(max_attempts):
        replies.append(adb.server_seek_time(requested_ms))
        anchor_ms = -1
        # DVD VM sector interpolation can land on a nearby VOBU. Observe the
        # exact STC anchor emitted for that landing, then issue at most two
        # bounded correction seeks. This makes a requested comparison scene
        # deterministic without lying about the media clock or touching the
        # decoder's cell-local position.
        while time.monotonic() < deadline:
            last = adb.player_state_snapshot()
            try:
                reached_ms = _snapshot_media_time(last)
            except RuntimeError:
                reached_ms = -1
            stc_count = int(last.get("dvdStcCount", -1))
            if stc_count > previous_stc_count:
                previous_stc_count = stc_count
                anchor_ms = int(last.get("dvdLogicalClockBaseMs", -1))
                break
            if reached_ms >= 0 and abs(reached_ms - target_ms) <= tolerance_ms:
                break
            time.sleep(0.25)
        if reached_ms >= 0 and abs(reached_ms - target_ms) <= tolerance_ms:
            break
        if anchor_ms < 0 or attempt + 1 >= max_attempts:
            break
        error_ms = anchor_ms - target_ms
        if abs(error_ms) <= tolerance_ms:
            reached_ms = anchor_ms
            break
        corrected_ms = max(0, requested_ms - error_ms)
        if abs(corrected_ms - requested_ms) < 100:
            break
        requested_ms = corrected_ms
    landed = reached_ms >= 0 and abs(reached_ms - target_ms) <= tolerance_ms
    remaining_s = max(0.1, deadline - time.monotonic())
    playback = _wait_for_playback(timeout_s=remaining_s, verify_ms=stable_ms) if landed else {
        "passed": False, "reason": "seek_landing_timeout", "state": last,
    }
    recovered = bool(playback.get("passed", False))
    return {
        "passed": landed and recovered,
        "landed": landed,
        "recovered": recovered,
        "target_ms": target_ms,
        "reached_ms": reached_ms,
        "tolerance_ms": tolerance_ms,
        "recoveryMs": int(round((time.monotonic() - started) * 1000.0)),
        "seekReply": replies[-1] if replies else {},
        "seekReplies": replies,
        "seekAttemptCount": len(replies),
        "finalRequestedMs": requested_ms,
        "playback": playback,
        "before": _compact_state(before),
        "state": _compact_state(last),
        "measurement": "sagetv_videoframe_event_seek_with_output_recovery",
        "verdict_basis": "server_seek_landed_and_video_audio_output_recovered",
    }


@mcp.tool()
def dev_show_active_player_adjustments() -> dict:
    """Open the debug APK's live Active Player Adjustments screen."""
    return adb.show_active_player_adjustments()


@mcp.tool()
def dev_set_active_player_overlay(visible: bool = True) -> dict:
    """Show or hide the bounded 30-second active-player diagnostics overlay."""
    return adb.set_active_player_overlay(visible)


@mcp.tool()
def dev_local_seek_absolute(target_ms: int) -> dict:
    """Compatibility/debug backend-isolation seek. Prefer dev_seek_time for verified time-based test control."""
    return adb.local_player_seek(target_ms)

@mcp.tool()
def dev_test_checkpoint(label: str = "checkpoint", log_lines: int = 1200) -> dict:
    """Capture a one-shot player snapshot, screenshot, relevant logcat, media/codec dump and focused window for a regression checkpoint."""
    base = artifact(label, "")
    paths = {
        "logcat": Path(str(base) + "_logcat.txt"),
        "crash": Path(str(base) + "_crash.txt"),
        "media": Path(str(base) + "_media.txt"),
        "audio": Path(str(base) + "_audio.txt"),
        "window": Path(str(base) + "_window.txt"),
        "state": Path(str(base) + "_state.json"),
        "screenshot": Path(str(base) + "_screen.png"),
    }
    state = _compact_state(adb.player_state_snapshot())
    paths["state"].write_text(json.dumps(state, indent=2, sort_keys=True), encoding="utf-8")
    paths["logcat"].write_text(adb.logcat_tail(log_lines), encoding="utf-8")
    paths["crash"].write_text(adb.crash_log(), encoding="utf-8")
    paths["media"].write_text(adb.dumpsys_media_codec(), encoding="utf-8")
    paths["audio"].write_text(adb.dumpsys_audio(), encoding="utf-8")
    paths["window"].write_text(adb.focused_window(), encoding="utf-8")
    adb.screenshot(paths["screenshot"])
    return {
        "state": state,
        "artifacts": {key: str(value) for key, value in paths.items()},
    }

@mcp.tool()
def clear_logcat() -> str:
    """Clear device logcat before a reproducible playback test."""
    adb.clear_logcat()
    return "logcat cleared"

@mcp.tool()
def get_logcat(lines: int = 800, pattern: str = "") -> str:
    """Read recent Fire TV logcat; optionally filter with a case-insensitive regular expression."""
    return adb.logcat_tail(lines, pattern)

@mcp.tool()
def wait_for_log(pattern: str, timeout_s: float = 15.0) -> str:
    """Wait for a diagnostic/player log marker. Useful once MiniClient telemetry markers are added."""
    return adb.wait_for_log(pattern, timeout_s)

@mcp.tool()
def get_player_telemetry(max_events: int = 80) -> dict:
    """Return recent structured Dev player telemetry, preferring the app-private telemetry file and falling back to logcat."""
    return adb.player_telemetry(max_events=max_events)

@mcp.tool()
def wait_for_player_event(event_name: str, timeout_s: float = 15.0) -> dict:
    """Wait for a structured player telemetry event such as FIRST_VIDEO_FRAME, SEEK_COMPLETE or FIRST_VIDEO_FRAME_AFTER_SEEK."""
    return adb.wait_for_player_event(event_name, timeout_s)

@mcp.tool()
def take_screenshot(label: str = "screen") -> str:
    """Capture the Fire TV screen. Video surfaces may not always appear; use player telemetry as authoritative proof."""
    return str(adb.screenshot(artifact(label, ".png")))

@mcp.tool()
def record_screen(seconds: int = 15, label: str = "playback") -> str:
    """Record a short device screen video when supported by the Fire OS build."""
    return str(adb.screenrecord(artifact(label, ".mp4"), seconds))

@mcp.tool()
def media_codec_diagnostics() -> str:
    """Collect best-effort MediaCodec/extractor/SurfaceFlinger dumps from the real Fire TV hardware."""
    return adb.dumpsys_media_codec()

@mcp.tool()
def focused_window() -> str:
    """Return the currently focused Android application/window."""
    return adb.focused_window()

@mcp.tool()
def collect_playback_diagnostics(label: str = "playback", log_lines: int = 2500) -> dict:
    """Save a broad playback failure bundle: state, logcat/crash, codec/surface, audio, window and screenshot."""
    base = artifact(label, "")
    paths = {
        "logcat": Path(str(base) + "_logcat.txt"),
        "crash": Path(str(base) + "_crash.txt"),
        "media": Path(str(base) + "_media.txt"),
        "audio": Path(str(base) + "_audio.txt"),
        "window": Path(str(base) + "_window.txt"),
        "state": Path(str(base) + "_state.json"),
        "screenshot": Path(str(base) + "_screen.png"),
    }
    state = _compact_state(adb.player_state_snapshot())
    paths["logcat"].write_text(adb.logcat_tail(log_lines), encoding="utf-8")
    paths["crash"].write_text(adb.crash_log(), encoding="utf-8")
    paths["media"].write_text(adb.dumpsys_media_codec(), encoding="utf-8")
    paths["audio"].write_text(adb.dumpsys_audio(), encoding="utf-8")
    paths["window"].write_text(adb.focused_window(), encoding="utf-8")
    paths["state"].write_text(json.dumps(state, indent=2, sort_keys=True), encoding="utf-8")
    adb.screenshot(paths["screenshot"])
    result = {k: str(v) for k, v in paths.items()}
    result["state_snapshot"] = state
    return result


def main() -> None:
    mcp.run(transport="stdio")

if __name__ == "__main__":
    main()
