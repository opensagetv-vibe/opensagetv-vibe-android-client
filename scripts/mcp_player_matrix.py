#!/usr/bin/env python3
"""Comprehensive SageTV MiniClient player/backend regression harness through MCP.

The harness exercises every requested top-level player configuration, including every
GSYVideoPlayer engine selection.  Media recovery outcomes are observations; a slow or
watchdog-expired media operation does not abort the matrix.  Only automation/infrastructure
errors are counted as harness failures.

Default full matrix:
  * players: exoplayer, media3, ijkplayer, gsyplayer
  * streaming: push, pull, fixed
  * decoding: hardware, software, fallback
  * GSY engines: auto, media3, system, legacy_exo

That expands to 63 configuration cases: 27 non-GSY + 36 GSY.
"""
from __future__ import annotations

import argparse
import json
import os
import sys
import time
from dataclasses import asdict, dataclass
from datetime import datetime
from pathlib import Path
from typing import Any, Iterable

from mcp_media3_matrix import MCPProcess, call_dict, initialize, safe_checkpoint
from mcp_playback_test import start_recording_via_search
from mcp_config_values import (
    DECODING_SELECTIONS,
    STREAMING_SELECTIONS,
    add_fixed_encoding_args,
    decoding_preference,
    fixed_config_from_args,
    fixed_expected_snapshot,
    normalize_csv,
    streaming_preference,
    validate_fixed_config,
)

PLAYERS = ("exoplayer", "media3", "ijkplayer", "gsyplayer")
STREAMING_MODES = STREAMING_SELECTIONS
DECODERS = DECODING_SELECTIONS
GSY_ENGINES = ("auto", "media3", "system", "legacy_exo")
CHECKS = ("absolute_seek", "seek_forward", "seek_backward", "pause_resume", "comskip_right", "comskip_left")
DEFAULT_CRASH_PROBE_MILESTONES_MS = (5000, 15000, 30000)

ISSUE_STATUSES = (
    "SLOW_RECOVERY",
    "PLAYER_CRASHED",
    "WATCHDOG_EXPIRED",
    "MEDIA_NOT_RECOVERED",
    "STARTUP_PLAYBACK_FAILED",
    "STARTUP_PLAYER_CRASHED",
    "INFRA_ERROR",
)
# v0.5.65 default issue profile derived from 20260827_232446_player_full_matrix.json.
# Empty tuples mean the configuration itself fails at startup, so issue-only mode
# starts the case and records the startup result without running media operations.
KNOWN_ISSUE_PROFILE: dict[str, tuple[str, ...]] = {
    "exoplayer__pull__hardware": ("comskip_right", "comskip_left"),
    "media3__pull__hardware": ("comskip_right",),
    "media3__push__hardware": ("absolute_seek", "seek_forward", "seek_backward", "comskip_left"),
    "gsyplayer__pull__hardware__gsy_auto": ("comskip_right", "comskip_left"),
    "gsyplayer__pull__hardware__gsy_media3": ("comskip_right", "comskip_left"),
    "gsyplayer__pull__hardware__gsy_legacy_exo": ("comskip_right", "comskip_left"),
    "gsyplayer__push__hardware__gsy_media3": ("absolute_seek", "seek_forward", "seek_backward"),
    "gsyplayer__pull__hardware__gsy_system": (),
    "gsyplayer__push__hardware__gsy_system": (),
}


class PlaybackStartupError(RuntimeError):
    """A player/backend reached the app but failed to produce healthy media at startup."""

    def __init__(self, playback: dict[str, Any]):
        self.playback = playback
        super().__init__(f"playback did not become healthy: {playback}")


@dataclass(frozen=True)
class PlayerCase:
    player: str
    streaming: str
    decoder: str
    gsy_engine: str = "auto"

    @property
    def id(self) -> str:
        base = f"{self.player}__{self.streaming}__{self.decoder}"
        return f"{base}__gsy_{self.gsy_engine}" if self.player == "gsyplayer" else base


def _csv(value: str, allowed: Iterable[str], label: str) -> list[str]:
    allowed_tuple = tuple(allowed)
    requested = [x.strip().lower() for x in str(value).split(",") if x.strip()]
    if not requested:
        raise ValueError(f"{label} must not be empty")
    invalid = [x for x in requested if x not in allowed_tuple]
    if invalid:
        raise ValueError(f"unsupported {label}: {', '.join(invalid)}")
    out: list[str] = []
    for item in requested:
        if item not in out:
            out.append(item)
    return out


def build_cases(players: list[str], streaming: list[str], decoders: list[str], gsy_engines: list[str]) -> list[PlayerCase]:
    cases: list[PlayerCase] = []
    for player in players:
        engines = gsy_engines if player == "gsyplayer" else ["auto"]
        for mode in streaming:
            for decoder in decoders:
                for engine in engines:
                    cases.append(PlayerCase(player, mode, decoder, engine))
    return cases


def filter_cases(cases: list[PlayerCase], requested_ids: str) -> list[PlayerCase]:
    if not str(requested_ids).strip():
        return cases
    wanted = [x.strip() for x in str(requested_ids).split(",") if x.strip()]
    known = {case.id: case for case in cases}
    missing = [case_id for case_id in wanted if case_id not in known]
    if missing:
        raise ValueError(f"unknown --case-id value(s): {', '.join(missing)}")
    return [known[case_id] for case_id in wanted]


def exclude_cases(
    cases: list[PlayerCase],
    excluded_players: list[str],
    excluded_gsy_engines: list[str],
    excluded_case_ids: str,
) -> tuple[list[PlayerCase], list[str]]:
    excluded_ids = [x.strip() for x in str(excluded_case_ids).split(",") if x.strip()]
    all_known_ids = {
        case.id
        for case in build_cases(list(PLAYERS), list(STREAMING_MODES), list(DECODERS), list(GSY_ENGINES))
    }
    missing = [case_id for case_id in excluded_ids if case_id not in all_known_ids]
    if missing:
        raise ValueError(f"unknown --exclude-case-id value(s): {', '.join(missing)}")

    excluded_id_set = set(excluded_ids)
    kept: list[PlayerCase] = []
    removed: list[str] = []
    for case in cases:
        if case.player in excluded_players:
            removed.append(case.id)
            continue
        if case.player == "gsyplayer" and case.gsy_engine in excluded_gsy_engines:
            removed.append(case.id)
            continue
        if case.id in excluded_id_set:
            removed.append(case.id)
            continue
        kept.append(case)
    return kept, removed


def issue_profile_from_report(report_path: str | Path) -> dict[str, tuple[str, ...]]:
    path = Path(report_path).expanduser()
    try:
        report = json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError as exc:
        raise ValueError(f"--issues-only report not found: {path}") from exc
    except (OSError, json.JSONDecodeError) as exc:
        raise ValueError(f"unable to read --issues-only report {path}: {exc}") from exc

    results = report.get("results")
    if not isinstance(results, dict):
        raise ValueError(f"--issues-only report has no results object: {path}")

    profile: dict[str, tuple[str, ...]] = {}
    for case_id, case_result in results.items():
        if not isinstance(case_result, dict):
            continue
        startup_status = str(case_result.get("startupStatus", ""))
        issue_checks: list[str] = []
        for check_name, observation in (case_result.get("checks") or {}).items():
            if check_name not in CHECKS or not isinstance(observation, dict):
                continue
            if str(observation.get("status", "")) in ISSUE_STATUSES:
                issue_checks.append(check_name)
        if startup_status and startup_status not in ("RECOVERED", "PER_CHECK_ISOLATED"):
            profile[str(case_id)] = tuple(issue_checks)
        elif issue_checks:
            profile[str(case_id)] = tuple(issue_checks)

    if not profile:
        raise ValueError(f"--issues-only report contains no abnormal startup/check results: {path}")
    return profile


def select_issue_cases(
    cases: list[PlayerCase],
    selected_checks: list[str],
    profile: dict[str, tuple[str, ...]],
) -> tuple[list[PlayerCase], dict[str, list[str]]]:
    selected: list[PlayerCase] = []
    per_case: dict[str, list[str]] = {}
    for case in cases:
        if case.id not in profile:
            continue
        issue_checks = profile[case.id]
        # Startup-only issue cases have an intentionally empty operation list.
        checks_for_case = [name for name in selected_checks if name in issue_checks]
        if issue_checks and not checks_for_case:
            continue
        selected.append(case)
        per_case[case.id] = checks_for_case
    return selected, per_case



def _int_value(value: Any, default: int = -1) -> int:
    try:
        return int(value)
    except (TypeError, ValueError):
        return default


def _counter_delta(before: Any, after: Any) -> int | None:
    b = _int_value(before)
    a = _int_value(after)
    if b < 0 or a < 0:
        return None
    if a >= b:
        return a - b
    # Counters such as AudioTrack playback-head frames can reset/wrap during seek.
    # A changed counter is still useful evidence of an active output pipeline, but
    # report the reset explicitly instead of fabricating a huge wrapped delta.
    return -1


def _counter_advanced(before: int, after: int) -> tuple[bool, bool]:
    """Return (advanced, reset_detected) for decoder-style monotonic counters."""
    if before < 0 or after < 0:
        return False, False
    if after > before:
        return True, False
    if after < before:
        # A replacement/reset counter only proves resumed output once it has produced
        # at least one post-reset unit. 0 by itself is not recovery evidence.
        return after > 0, True
    return False, False


def _audio_head_advanced(previous: dict[str, Any], current: dict[str, Any]) -> tuple[bool, bool]:
    before = _int_value(previous.get('health_audioPlaybackHeadFrames'))
    after = _int_value(current.get('health_audioPlaybackHeadFrames'))
    if before < 0 or after < 0:
        return False, False
    before_session = _int_value(previous.get('health_audioSessionId'))
    after_session = _int_value(current.get('health_audioSessionId'))
    playing = _int_value(current.get('health_audioTrackPlayState')) == 3
    if before_session >= 0 and after_session >= 0 and before_session != after_session:
        return playing and after > 0, True
    if after > before:
        return True, False
    if after < before:
        return playing and after > 0, True
    return False, False


def _stream_expectations(*states: dict[str, Any]) -> tuple[bool, bool]:
    video_expected = any(bool(
        state.get('health_videoMime') or state.get('health_videoDecoder')
        or (_int_value(state.get('health_videoWidth')) > 0 and _int_value(state.get('health_videoHeight')) > 0)
    ) for state in states if isinstance(state, dict))
    audio_expected = any(bool(
        state.get('health_audioMime') or state.get('health_audioDecoder')
        or state.get('health_audioTrackPresent') or _int_value(state.get('health_audioChannels')) > 0
    ) for state in states if isinstance(state, dict))
    return video_expected, audio_expected


def _av_progress(
    previous: dict[str, Any],
    current: dict[str, Any],
    *,
    video_expected_latched: bool | None = None,
    audio_expected_latched: bool | None = None,
) -> tuple[bool, dict[str, Any]]:
    """Evaluate adjacent snapshots using output counters, with expectations latched pre-action."""
    probe_supported = bool(current.get('health_probeSupported') or previous.get('health_probeSupported'))
    pair_video_expected, pair_audio_expected = _stream_expectations(previous, current)
    video_expected = pair_video_expected if video_expected_latched is None else bool(video_expected_latched)
    audio_expected = pair_audio_expected if audio_expected_latched is None else bool(audio_expected_latched)

    video_before = _int_value(previous.get('health_videoRendered'))
    video_after = _int_value(current.get('health_videoRendered'))
    audio_before = _int_value(previous.get('health_audioRendered'))
    audio_after = _int_value(current.get('health_audioRendered'))
    head_before = _int_value(previous.get('health_audioPlaybackHeadFrames'))
    head_after = _int_value(current.get('health_audioPlaybackHeadFrames'))

    video_delta = _counter_delta(video_before, video_after)
    audio_delta = _counter_delta(audio_before, audio_after)
    head_delta = _counter_delta(head_before, head_after)

    video_counter_advanced, video_counter_reset = _counter_advanced(video_before, video_after)
    audio_counter_advanced, audio_counter_reset = _counter_advanced(audio_before, audio_after)
    audio_head_advancing, audio_head_reset = _audio_head_advanced(previous, current)

    video_advancing = (not video_expected) or video_counter_advanced
    audio_buffers_advancing = audio_counter_advanced
    audio_advancing = (not audio_expected) or audio_buffers_advancing or audio_head_advancing

    waiting_for: list[str] = []
    if probe_supported:
        if video_expected and not video_advancing:
            waiting_for.append('video_counter')
        if audio_expected and not audio_advancing:
            waiting_for.append('audio_counter')
        healthy = video_advancing and audio_advancing
        verdict_basis = 'av_output_counters'
    else:
        before_t = _int_value(previous.get('sageTimelineMs', previous.get('mediaTimeMs')))
        after_t = _int_value(current.get('sageTimelineMs', current.get('mediaTimeMs')))
        timeline_advancing = before_t >= 0 and after_t > before_t
        healthy = timeline_advancing
        if not healthy:
            waiting_for.append('timeline_counter_fallback')
        verdict_basis = 'timeline_fallback_no_health_probe'

    details = {
        'probeSupported': probe_supported,
        'verdictBasis': verdict_basis,
        'videoExpected': video_expected,
        'audioExpected': audio_expected,
        'videoExpectationLatched': video_expected_latched is not None,
        'audioExpectationLatched': audio_expected_latched is not None,
        'videoAdvancing': video_advancing,
        'audioAdvancing': audio_advancing,
        'audioBuffersAdvancing': audio_buffers_advancing,
        'audioHeadAdvancing': audio_head_advancing,
        'videoCounterReset': video_counter_reset,
        'audioCounterReset': audio_counter_reset,
        'audioHeadReset': audio_head_reset,
        'videoRenderedBefore': video_before,
        'videoRenderedAfter': video_after,
        'videoRenderedDelta': video_delta,
        'audioRenderedBefore': audio_before,
        'audioRenderedAfter': audio_after,
        'audioRenderedDelta': audio_delta,
        'audioHeadBefore': head_before,
        'audioHeadAfter': head_after,
        'audioHeadDelta': head_delta,
        'waitingFor': waiting_for,
        'playerActive': bool(current.get('playerActive')),
        'miniState': current.get('state'),
        'playerReady': current.get('health_playerReady'),
        'isPlaying': current.get('health_isPlaying'),
        'playbackState': current.get('health_playbackState'),
        'isLoading': current.get('health_isLoading'),
        'surfaceValid': current.get('health_surfaceValid'),
        'playerError': current.get('health_playerError') or '',
        'positionMs': current.get('health_playerPositionMs'),
        'videoDecoder': current.get('health_videoDecoder') or '',
        'videoDecoderKind': current.get('health_videoDecoderKind') or '',
        'audioDecoder': current.get('health_audioDecoder') or '',
        'audioDecoderKind': current.get('health_audioDecoderKind') or '',
    }
    return healthy, details


def _live_wait_line(label: str, elapsed_ms: int, watchdog_ms: int, health: dict[str, Any]) -> None:
    waiting = ','.join(health.get('waitingFor') or []) or 'recovery_confirmation'
    video = 'advancing' if health.get('videoAdvancing') else 'stalled'
    audio = 'advancing' if health.get('audioAdvancing') else 'stalled'
    state = health.get('playbackState') or health.get('miniState') or '?'
    surface = health.get('surfaceValid')
    surface_text = 'ok' if surface is True else ('bad' if surface is False else '?')
    error = str(health.get('playerError') or '')
    if len(error) > 24:
        error = error[:21] + '...'
    text = (
        f"WAIT {label} {elapsed_ms / 1000.0:5.1f}s/{watchdog_ms / 1000.0:.0f}s"
        f" | waitingFor={waiting} | video={video} | audio={audio}"
        f" | state={state} | surface={surface_text} | error={error or 'none'}"
    )
    # Clear and rewrite the SAME terminal line. This intentionally does not append
    # one WAIT line per heartbeat during a long watchdog observation.
    sys.stdout.write('\r\x1b[2K' + text)
    sys.stdout.flush()


def _clear_live_wait_line() -> None:
    sys.stdout.write('\r\x1b[2K')
    sys.stdout.flush()


def _parse_milestones_ms(value: str) -> tuple[int, ...]:
    items: list[int] = []
    for token in str(value or "").split(","):
        token = token.strip()
        if not token:
            continue
        parsed = int(token)
        if parsed < 250:
            raise ValueError("crash-probe milestones must be >= 250 ms")
        if parsed not in items:
            items.append(parsed)
    return tuple(sorted(items))


def _compact_milestone_state(state: dict[str, Any]) -> dict[str, Any]:
    keys = (
        'state', 'sageTimelineMs', 'serverRequestedSeekMs', 'serverAnchorMs',
        'health_playerReady', 'health_isPlaying', 'health_isLoading', 'health_playbackState',
        'health_playerPositionMs', 'health_playerError', 'health_surfaceValid',
        'health_videoRendered', 'health_videoQueuedInput', 'health_videoDecoderInitCount',
        'health_videoDecoderReleaseCount', 'health_audioRendered', 'health_audioQueuedInput',
        'health_audioPlaybackHeadFrames', 'health_audioSessionId',
        'health_dataSourceOpenCount', 'health_dataSourceOpenWaitMs',
        'health_dataSourceNetworkReadCount', 'health_dataSourceNetworkReadBytes',
        'health_dataSourceNetworkReadWaitMs', 'health_dataSourceNetworkReadErrors',
        'health_dataSourceNetworkLastReadPosition', 'trapSequence', 'trapLastEvent',
        'trapLastEventMonotonicMs', 'trapLastVideoRendered', 'trapLastAudioRendered',
        'trapLastAudioHeadFrames',
    )
    return {key: state.get(key) for key in keys if key in state}


def _crash_probe_changed(baseline: dict[str, Any], current: dict[str, Any]) -> dict[str, Any]:
    baseline_fp = str(baseline.get('crashFingerprint') or '')
    current_fp = str(current.get('crashFingerprint') or '')
    baseline_sig_fp = str(baseline.get('signatureFingerprint') or '')
    current_sig_fp = str(current.get('signatureFingerprint') or '')
    baseline_pids = tuple(sorted(str(x) for x in (baseline.get('pids') or [])))
    current_pids = tuple(sorted(str(x) for x in (current.get('pids') or [])))
    fingerprint_changed = bool(current_fp and baseline_fp and current_fp != baseline_fp)
    signature_changed = bool(current_sig_fp and current_sig_fp != baseline_sig_fp)
    process_gone = baseline.get('running') is True and current.get('running') is False
    pid_changed = bool(baseline_pids and current_pids and baseline_pids != current_pids)
    new_crash_signature = bool(current.get('signatureDetected')) and signature_changed
    crash_detected = bool(process_gone or pid_changed or new_crash_signature)
    return {
        'crashDetected': crash_detected,
        'processGone': process_gone,
        'processRestartDetected': pid_changed,
        'newCrashSignature': new_crash_signature,
        'crashFingerprintChanged': fingerprint_changed,
        'signatureFingerprintChanged': signature_changed,
        'baselinePids': list(baseline_pids),
        'currentPids': list(current_pids),
    }


def wait_for_av_recovery(
    client: MCPProcess,
    *,
    label: str,
    watchdog_ms: int,
    health_poll_ms: int,
    before_action: dict[str, Any],
    action_result: Any,
    crash_probe_milestones_ms: tuple[int, ...] = DEFAULT_CRASH_PROBE_MILESTONES_MS,
) -> dict[str, Any]:
    """Poll one persistent ADB/MCP session until actual A/V output advances.

    No target-position, seekPending, ready/isPlaying or surface gate is used for PASS.
    Those fields remain visible in the dynamic wait line and final diagnostics.
    """
    watchdog_ms = max(250, int(watchdog_ms))
    health_poll_ms = max(100, min(int(health_poll_ms), 2000))
    started = time.monotonic()
    try:
        baseline_crash_probe = call_dict(client, 'dev_crash_probe', {}, timeout=30.0)
    except Exception as exc:
        baseline_crash_probe = {'unavailable': True, 'error': str(exc)}
    pending_milestones = [int(x) for x in crash_probe_milestones_ms if 0 < int(x) < watchdog_ms]
    long_wait_probes: list[dict[str, Any]] = []
    crash_detected = False
    crash_details: dict[str, Any] = {}
    poll_error = ''
    previous = call_dict(client, 'dev_player_state', {}, timeout=30.0)
    video_expected_latched, audio_expected_latched = _stream_expectations(before_action, previous)
    last = previous
    last_health: dict[str, Any] = {
        'waitingFor': ['first_av_counter_sample'],
        'videoAdvancing': False,
        'audioAdvancing': False,
        'playbackState': previous.get('health_playbackState'),
        'surfaceValid': previous.get('health_surfaceValid'),
        'playerError': previous.get('health_playerError') or '',
    }
    recovered = False

    while True:
        elapsed_ms = int(round((time.monotonic() - started) * 1000.0))
        if elapsed_ms >= watchdog_ms:
            break
        remaining_ms = watchdog_ms - elapsed_ms
        time.sleep(min(health_poll_ms, remaining_ms) / 1000.0)
        try:
            current = call_dict(client, 'dev_player_state', {}, timeout=30.0)
        except Exception as exc:
            poll_error = str(exc)
            try:
                probe = call_dict(client, 'dev_crash_probe', {}, timeout=30.0)
                change = _crash_probe_changed(baseline_crash_probe, probe)
                long_wait_probes.append({
                    'elapsedMs': elapsed_ms,
                    'reason': 'player_state_poll_error',
                    'crashProbe': probe,
                    **change,
                })
                if change.get('crashDetected'):
                    crash_detected = True
                    crash_details = long_wait_probes[-1]
                    break
            except Exception:
                pass
            raise
        recovered, last_health = _av_progress(
            previous, current,
            video_expected_latched=video_expected_latched,
            audio_expected_latched=audio_expected_latched,
        )
        last = current
        elapsed_ms = int(round((time.monotonic() - started) * 1000.0))

        while pending_milestones and elapsed_ms >= pending_milestones[0]:
            milestone_ms = pending_milestones.pop(0)
            try:
                probe = call_dict(client, 'dev_crash_probe', {}, timeout=30.0)
                change = _crash_probe_changed(baseline_crash_probe, probe)
                entry = {
                    'milestoneMs': milestone_ms,
                    'elapsedMs': elapsed_ms,
                    'state': _compact_milestone_state(current),
                    'health': dict(last_health),
                    'crashProbe': probe,
                    **change,
                }
            except Exception as exc:
                entry = {
                    'milestoneMs': milestone_ms,
                    'elapsedMs': elapsed_ms,
                    'state': _compact_milestone_state(current),
                    'health': dict(last_health),
                    'crashProbe': {'unavailable': True, 'error': str(exc)},
                    'crashDetected': False,
                }
            long_wait_probes.append(entry)
            if entry.get('crashDetected'):
                crash_detected = True
                crash_details = entry
                break
        _live_wait_line(label, elapsed_ms, watchdog_ms, last_health)
        if recovered or crash_detected:
            break
        previous = current

    _clear_live_wait_line()
    recovery_ms = int(round((time.monotonic() - started) * 1000.0))
    try:
        event_traps = call_dict(client, 'dev_player_events', {}, timeout=30.0)
    except Exception as exc:
        event_traps = {'unavailable': True, 'error': str(exc)}
    return {
        'passed': recovered,
        'recovered': recovered,
        'outputHealthy': recovered,
        'watchdog_expired': (not recovered) and (not crash_detected),
        'crashDetected': crash_detected,
        'crashDetails': crash_details,
        'baselineCrashProbe': baseline_crash_probe,
        'longWaitProbes': long_wait_probes,
        'pollError': poll_error,
        'watchdog_ms': watchdog_ms,
        'recoveryMs': recovery_ms,
        'verdict_basis': last_health.get('verdictBasis', 'av_output_counters'),
        'waitingFor': last_health.get('waitingFor', []),
        'health': last_health,
        'before': before_action,
        'after': last,
        'action': action_result,
        'eventTraps': event_traps,
    }


def print_output_health(observation: dict[str, Any]) -> None:
    """Print detailed A/V recovery evidence for every media step."""
    result = observation.get('result', observation)
    if not isinstance(result, dict):
        return
    health = result.get('health') if isinstance(result.get('health'), dict) else {}

    video = health.get('videoAdvancing', result.get('videoStillAdvancing'))
    audio = health.get('audioAdvancing', result.get('audioStillAdvancing'))
    fields = [
        f"basis={result.get('verdict_basis', health.get('verdictBasis', 'unknown'))}",
        f"videoAdvancing={bool(video)}" if video is not None else 'videoAdvancing=?',
        f"audioAdvancing={bool(audio)}" if audio is not None else 'audioAdvancing=?',
        f"recovery={result.get('recoveryMs', result.get('resumeMs', '?'))} ms",
    ]
    print('  output health: ' + ' '.join(fields), flush=True)

    if health:
        print(
            '  counters: '
            f"videoRendered={health.get('videoRenderedBefore')}->{health.get('videoRenderedAfter')} "
            f"delta={health.get('videoRenderedDelta')} | "
            f"audioRendered={health.get('audioRenderedBefore')}->{health.get('audioRenderedAfter')} "
            f"delta={health.get('audioRenderedDelta')} | "
            f"audioHead={health.get('audioHeadBefore')}->{health.get('audioHeadAfter')} "
            f"delta={health.get('audioHeadDelta')}",
            flush=True,
        )
        print(
            '  diagnostics only: '
            f"playerActive={health.get('playerActive')} ready={health.get('playerReady')} "
            f"isPlaying={health.get('isPlaying')} playbackState={health.get('playbackState')} "
            f"loading={health.get('isLoading')} surfaceValid={health.get('surfaceValid')} "
            f"positionMs={health.get('positionMs')} error={health.get('playerError') or 'none'}",
            flush=True,
        )
        print(
            '  decoders: '
            f"video={health.get('videoDecoder') or 'unknown'} ({health.get('videoDecoderKind') or 'unknown'}) "
            f"audio={health.get('audioDecoder') or 'unknown'} ({health.get('audioDecoderKind') or 'unknown'})",
            flush=True,
        )

    before = result.get('before') if isinstance(result.get('before'), dict) else {}
    after = result.get('after') if isinstance(result.get('after'), dict) else {}
    io_keys = (
        'health_dataSourceOpenCount', 'health_dataSourceOpenWaitMs',
        'health_dataSourceNetworkReadCount', 'health_dataSourceNetworkReadRequestedBytes',
        'health_dataSourceNetworkReadBytes', 'health_dataSourceNetworkReadWaitMs',
        'health_dataSourceNetworkReadErrors',
    )
    if any(_int_value(before.get(k)) >= 0 and _int_value(after.get(k)) >= 0 for k in io_keys):
        deltas = {k: max(0, _int_value(after.get(k)) - _int_value(before.get(k))) for k in io_keys
                  if _int_value(before.get(k)) >= 0 and _int_value(after.get(k)) >= 0}
        print(
            '  pull I/O: '
            f"opens={deltas.get('health_dataSourceOpenCount', 0)} "
            f"openWait={deltas.get('health_dataSourceOpenWaitMs', 0)} ms "
            f"READs={deltas.get('health_dataSourceNetworkReadCount', 0)} "
            f"requested={deltas.get('health_dataSourceNetworkReadRequestedBytes', 0)} B "
            f"received={deltas.get('health_dataSourceNetworkReadBytes', 0)} B "
            f"readWait={deltas.get('health_dataSourceNetworkReadWaitMs', 0)} ms "
            f"errors={deltas.get('health_dataSourceNetworkReadErrors', 0)}",
            flush=True,
        )

def status_from_recovery(result: dict[str, Any], slow_ms: int) -> str:
    if bool(result.get("crashDetected", False)):
        return "PLAYER_CRASHED"
    if bool(result.get("watchdog_expired", False)):
        return "WATCHDOG_EXPIRED"
    recovered = bool(result.get("recovered", result.get("passed", False)))
    recovery_ms = result.get("recoveryMs", result.get("resumeMs"))
    try:
        if recovered and recovery_ms is not None and int(recovery_ms) > int(slow_ms):
            return "SLOW_RECOVERY"
    except (TypeError, ValueError):
        pass
    return "RECOVERED" if recovered else "MEDIA_NOT_RECOVERED"


def start_case(
    client: MCPProcess,
    case: PlayerCase,
    *,
    server: str,
    port: int,
    text: str,
    text_char_delay_ms: int,
    connect_timeout_s: float,
    ui_stable_ms: int,
    playback_timeout_s: float,
    verify_ms: int,
    fixed_config: dict[str, Any],
    tuning_config: dict[str, Any] | None = None,
) -> dict[str, Any]:
    print(f"\n=== CASE {case.id} ===")
    clean = call_dict(client, "dev_prepare_clean_start", {"wake": True, "graceful_timeout_s": 2.0}, timeout=30.0)
    if not clean.get("readyToLaunch") or clean.get("stopped", {}).get("running"):
        raise RuntimeError(f"Dev app could not be stopped cleanly: {clean}")

    tuning = None
    if tuning_config is not None:
        tuning = call_dict(client, "dev_set_player_tuning", {"reset": True, **tuning_config})
        if not tuning.get("ok", True):
            raise RuntimeError(f"player tuning was not applied: {tuning}")

    config_request = {
        "player": case.player,
        "streaming": streaming_preference(case.streaming),
        "decoding": decoding_preference(case.decoder),
        "gsy_engine": case.gsy_engine,
        **fixed_config,
    }
    configured = call_dict(client, "dev_set_player_config", config_request)
    expected_configured = {
        "player": case.player,
        "streaming": streaming_preference(case.streaming),
        "decoding": decoding_preference(case.decoder),
        "gsyEngine": case.gsy_engine,
        **fixed_expected_snapshot(fixed_config),
    }
    for key, value in expected_configured.items():
        actual = str(configured.get(key, "")).lower()
        if actual != value:
            raise RuntimeError(
                f"configuration parameter was not applied: {key} selected={value} response={actual or '<missing>'}"
            )
    connected = call_dict(client, "dev_connect_server", {"address": server, "port": port, "save": False}, timeout=30.0)
    connected_state = call_dict(client, "dev_wait_for_ui", {"connected": True, "timeout_s": connect_timeout_s}, timeout=connect_timeout_s + 10.0)
    if not connected_state.get("passed"):
        raise RuntimeError(f"SageTV did not connect: {connected_state}")

    app_status = call_dict(client, "dev_app_status", {}, timeout=30.0)
    if not app_status.get("running"):
        raise RuntimeError(f"Dev app is not running after direct connect: {app_status}")

    if not connected_state.get("state", {}).get("automationReady", False):
        call_dict(client, "dev_sage_command", {"command": "home"}, timeout=30.0)
    ready = call_dict(client, "dev_wait_for_ui", {
        "connected": True,
        "automation_ready": True,
        "stable_ms": ui_stable_ms,
        "timeout_s": connect_timeout_s,
    }, timeout=connect_timeout_s + 10.0)
    if not ready.get("passed"):
        raise RuntimeError(f"automationReady not reached: {ready}")

    search = start_recording_via_search(client, text, text_char_delay_ms=text_char_delay_ms)
    playback = call_dict(client, "dev_wait_for_playback_started", {
        "timeout_s": playback_timeout_s,
        "verify_ms": verify_ms,
    }, timeout=playback_timeout_s + 15.0)
    if not playback.get("passed"):
        raise PlaybackStartupError(playback)

    state = call_dict(client, "dev_player_state")
    status_version = _int_value(state.get("debugStatusVersion"), 0)
    if status_version < 12:
        raise RuntimeError(
            f"installed debug APK is too old for exact-event matrix diagnostics "
            f"(debugStatusVersion={status_version}, need >=12 / v0.5.69)"
        )
    expected = {
        "player": case.player,
        "streaming": streaming_preference(case.streaming),
        "decoding": decoding_preference(case.decoder),
    }
    for key, value in expected.items():
        actual = str(state.get(key, "")).lower()
        if actual and actual != value:
            raise RuntimeError(f"configured {key}={value} but active snapshot reports {actual}")
    if case.player == "gsyplayer":
        actual_engine = str(state.get("gsyEngine", "")).lower()
        if actual_engine and actual_engine != case.gsy_engine:
            raise RuntimeError(f"configured gsyEngine={case.gsy_engine} but snapshot reports {actual_engine}")

    print(
        f"PASS: startup {case.id}; "
        f"streaming={case.streaming}->{streaming_preference(case.streaming)} "
        f"decoding={case.decoder}->{decoding_preference(case.decoder)} "
        f"playerClass={state.get('playerClass', '')} videoDecoder={state.get('health_videoDecoder', '')}"
    )
    return {
        "clean": clean,
        "tuning": tuning,
        "selectedConfig": {"player": case.player, "streaming": case.streaming, "decoding": case.decoder, "gsyEngine": case.gsy_engine},
        "appliedConfig": {"player": case.player, "streaming": streaming_preference(case.streaming), "decoding": decoding_preference(case.decoder), "gsyEngine": case.gsy_engine},
        "configured": configured,
        "connected": connected,
        "ready": ready,
        "search": search,
        "playback": playback,
        "state": state,
    }


def _seek_position_sanity(result: dict[str, Any], target_ms: int, tolerance_ms: int = 10000) -> dict[str, Any]:
    after = result.get('after') if isinstance(result.get('after'), dict) else {}
    position = _int_value(after.get('health_playerPositionMs'))
    recovery_ms = _int_value(result.get('recoveryMs'), 0)
    if position < 0:
        return {'status': 'UNKNOWN', 'toleranceMs': tolerance_ms, 'isVerdict': False}
    expected_at_observation = max(0, int(target_ms) + max(0, recovery_ms))
    error = position - expected_at_observation
    absolute_error = abs(error)
    if absolute_error <= tolerance_ms:
        status = 'NORMAL'
    elif absolute_error <= tolerance_ms * 2:
        status = 'WARN'
    else:
        status = 'SUSPICIOUS'
    return {
        'status': status,
        'isVerdict': False,
        'targetMs': int(target_ms),
        'expectedAtObservationMs': expected_at_observation,
        'observedPlayerPositionMs': position,
        'errorMs': error,
        'absoluteErrorMs': absolute_error,
        'toleranceMs': tolerance_ms,
    }


def _finalize_recovery(result: dict[str, Any], slow_ms: int) -> dict[str, Any]:
    recovered = bool(result.get('recovered', result.get('passed', False)))
    return {
        'status': status_from_recovery(result, slow_ms),
        'cause': 'none' if recovered else 'undetermined',
        'result': result,
    }


def run_absolute_seek(
    client: MCPProcess,
    *,
    label: str,
    target_ms: int,
    watchdog_ms: int,
    health_poll_ms: int,
    slow_ms: int,
    seek_tolerance_ms: int = 10000,
    crash_probe_milestones_ms: tuple[int, ...] = DEFAULT_CRASH_PROBE_MILESTONES_MS,
) -> dict[str, Any]:
    print(f"STEP: {label}; watchdog={watchdog_ms} ms", flush=True)
    call_dict(client, 'dev_clear_player_events', {}, timeout=30.0)
    before = call_dict(client, 'dev_player_state', {}, timeout=30.0)
    action = call_dict(client, 'dev_local_seek_absolute', {'target_ms': target_ms}, timeout=30.0)
    result = wait_for_av_recovery(
        client,
        label=label,
        watchdog_ms=watchdog_ms,
        health_poll_ms=health_poll_ms,
        before_action=before,
        action_result=action,
        crash_probe_milestones_ms=crash_probe_milestones_ms,
    )
    result.update({
        'target_ms': int(target_ms),
        'landing_position_is_verdict': False,
        'measurement': 'host_polled_absolute_seek_av_counter_recovery',
    })
    result['seekPositionSanity'] = _seek_position_sanity(result, int(target_ms), seek_tolerance_ms)
    return _finalize_recovery(result, slow_ms)


def run_relative_seek(
    client: MCPProcess,
    *,
    label: str,
    delta_ms: int,
    watchdog_ms: int,
    health_poll_ms: int,
    slow_ms: int,
    seek_tolerance_ms: int = 10000,
    crash_probe_milestones_ms: tuple[int, ...] = DEFAULT_CRASH_PROBE_MILESTONES_MS,
) -> dict[str, Any]:
    print(f"STEP: {label}; watchdog={watchdog_ms} ms", flush=True)
    call_dict(client, 'dev_clear_player_events', {}, timeout=30.0)
    before = call_dict(client, 'dev_player_state', {}, timeout=30.0)
    action = call_dict(client, 'dev_seek_relative', {'delta_ms': int(delta_ms)}, timeout=30.0)
    result = wait_for_av_recovery(
        client,
        label=label,
        watchdog_ms=watchdog_ms,
        health_poll_ms=health_poll_ms,
        before_action=before,
        action_result=action,
        crash_probe_milestones_ms=crash_probe_milestones_ms,
    )
    target = _int_value(action.get('targetPlayerMs')) if isinstance(action, dict) else -1
    result.update({
        'delta_ms': int(delta_ms),
        'landing_position_is_verdict': False,
        'measurement': 'host_polled_relative_seek_av_counter_recovery',
    })
    if target >= 0:
        result['seekPositionSanity'] = _seek_position_sanity(result, target, seek_tolerance_ms)
    return _finalize_recovery(result, slow_ms)


def run_pause_resume(
    client: MCPProcess,
    *,
    label: str,
    watchdog_ms: int,
    health_poll_ms: int,
    slow_ms: int,
    crash_probe_milestones_ms: tuple[int, ...] = DEFAULT_CRASH_PROBE_MILESTONES_MS,
) -> dict[str, Any]:
    print(f"STEP: {label}; watchdog={watchdog_ms} ms", flush=True)
    call_dict(client, 'dev_clear_player_events', {}, timeout=30.0)
    before = call_dict(client, 'dev_player_state', {}, timeout=30.0)
    paused = call_dict(client, 'dev_player_control', {'action': 'pause'}, timeout=30.0)
    time.sleep(0.5)
    played = call_dict(client, 'dev_player_control', {'action': 'play'}, timeout=30.0)
    result = wait_for_av_recovery(
        client,
        label=label,
        watchdog_ms=watchdog_ms,
        health_poll_ms=health_poll_ms,
        before_action=before,
        action_result={'pause': paused, 'play': played},
        crash_probe_milestones_ms=crash_probe_milestones_ms,
    )
    result['measurement'] = 'host_polled_pause_resume_av_counter_recovery'
    return _finalize_recovery(result, slow_ms)


def run_comskip(
    client: MCPProcess,
    *,
    label: str,
    direction: str,
    watchdog_ms: int,
    health_poll_ms: int,
    slow_ms: int,
    crash_probe_milestones_ms: tuple[int, ...] = DEFAULT_CRASH_PROBE_MILESTONES_MS,
) -> dict[str, Any]:
    """Post native SageTV Comskip then poll A/V counters from the host.

    The STV/server owns the commercial-marker landing position. The matrix intentionally
    ignores where it lands and only verifies that actual video/audio output resumes.
    """
    normalized = str(direction).strip().lower()
    if normalized not in ('right', 'left'):
        raise ValueError('direction must be right or left')

    print(f"STEP: {label}; watchdog={watchdog_ms} ms", flush=True)
    call_dict(client, 'dev_clear_player_events', {}, timeout=30.0)
    before = call_dict(client, 'dev_player_state', {}, timeout=30.0)
    action = call_dict(client, 'dev_comskip', {'direction': normalized}, timeout=30.0)
    result = wait_for_av_recovery(
        client,
        label=label,
        watchdog_ms=watchdog_ms,
        health_poll_ms=health_poll_ms,
        before_action=before,
        action_result=action,
        crash_probe_milestones_ms=crash_probe_milestones_ms,
    )
    result.update({
        'directSageCommand': normalized,
        'inputPath': 'host_short_dev_comskip_plus_polled_av_counters',
        'landing_position_is_verdict': False,
        'measurement': 'host_polled_comskip_av_counter_recovery',
    })
    return _finalize_recovery(result, slow_ms)

def media_needs_restart(observation: dict[str, Any]) -> bool:
    return observation.get("status") in ("PLAYER_CRASHED", "WATCHDOG_EXPIRED", "MEDIA_NOT_RECOVERED")


def write_report(report: dict[str, Any], requested: str) -> Path:
    if requested:
        path = Path(requested).expanduser()
    else:
        artifact_dir = Path(os.environ.get("SAGETV_ARTIFACT_DIR", "/workspace/artifacts/firetv"))
        path = artifact_dir / f"{datetime.now().strftime('%Y%m%d_%H%M%S')}_player_full_matrix.json"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(report, indent=2, sort_keys=True), encoding="utf-8")
    return path


def main() -> int:
    parser = argparse.ArgumentParser(description="Run the complete SageTV MiniClient player/backend MCP regression matrix")
    parser.add_argument("--server", default="192.168.10.175")
    parser.add_argument("--port", type=int, default=31099)
    parser.add_argument("--text", required=True, help="Required SageTV Search text; no default title is ever used")
    parser.add_argument("--text-char-delay-ms", type=int, default=0, help="Text injection pacing for every case: 0 uses MiniClient native key events; >0 enables legacy Android/ADB diagnostic pacing (ms)")
    parser.add_argument("--players", default=",".join(PLAYERS))
    parser.add_argument("--streaming", default=",".join(STREAMING_MODES), help="Comma-separated streaming modes: push,pull,fixed (legacy alias dynamic is accepted)")
    parser.add_argument("--decoding", "--decoders", dest="decoding", default=",".join(DECODERS), help="Comma-separated decoding modes: hardware,software,fallback (legacy alias hardware_preferred is accepted)")
    parser.add_argument("--hardware-only", action="store_true", help="Convenience alias for --decoding hardware")
    add_fixed_encoding_args(parser)
    parser.add_argument("--gsy-engines", default=",".join(GSY_ENGINES))
    parser.add_argument("--checks", default=",".join(CHECKS))
    parser.add_argument("--case-id", default="", help="Comma-separated exact generated case IDs to run")
    parser.add_argument("--exclude-players", default="", help="Comma-separated player families to omit from this run (exoplayer,media3,ijkplayer,gsyplayer). Useful with --issues-only when that player code was not changed.")
    parser.add_argument("--exclude-gsy-engines", default="", help="Comma-separated GSY engines to omit (auto,media3,system,legacy_exo). Example: --exclude-gsy-engines system")
    parser.add_argument("--exclude-case-id", default="", help="Comma-separated exact generated case IDs to omit from this run")
    parser.add_argument("--issues-only", nargs="?", const="known", default="", metavar="REPORT", help="Run only abnormal startup/case+check combinations. With no REPORT, use the v0.5.65 known-issue profile; with a prior matrix JSON REPORT, derive the issue set from that report. Combine with --exclude-players/--exclude-gsy-engines/--exclude-case-id to retest only changed backends.")
    parser.add_argument("--list-cases", action="store_true", help="Print selected configuration cases and exit without connecting to a device")
    parser.add_argument("--watchdog-ms", type=int, default=180000, help="Media recovery observation watchdog (default: 180000 ms / 3 minutes). Expiry is non-fatal.")
    parser.add_argument("--slow-recovery-ms", "--slow_recover_ms", "--slow-recover-ms", dest="slow_recovery_ms", type=int, default=5000, help="Recovery time above which a successful A/V recovery is labeled SLOW_RECOVERY (default: 5000 ms)")
    parser.add_argument("--crash-probe-ms", default="5000,15000,30000", help="Comma-separated long-wait crash/process probe milestones in ms")
    parser.add_argument("--start-ms", type=int, default=0)
    parser.add_argument("--seek-tolerance-ms", type=int, default=10000, help="Diagnostic-only seek landing tolerance; never changes A/V recovery PASS/FAIL")
    parser.add_argument("--seek-timeout-s", type=float, default=0.0, help="Absolute-seek watchdog override in seconds; 0 inherits --watchdog-ms per step")
    parser.add_argument("--seek-stable-ms", type=int, default=1200)
    parser.add_argument("--skip-forward-ms", type=int, default=10000)
    parser.add_argument("--skip-backward-ms", type=int, default=10000)
    parser.add_argument("--verify-playback-ms", type=int, default=3000)
    parser.add_argument("--health-poll-ms", type=int, default=250)
    parser.add_argument("--connect-timeout-s", type=float, default=30.0)
    parser.add_argument("--ui-stable-ms", type=int, default=2000)
    parser.add_argument("--playback-timeout-s", type=float, default=45.0)
    parser.add_argument("--startup-verify-ms", type=int, default=1500)
    parser.add_argument("--report", default="")
    parser.add_argument("--stop-on-infra-error", action="store_true", help="Stop instead of continuing to later configurations after an automation/infrastructure error")
    parser.add_argument("--leave-running", action="store_true")
    args = parser.parse_args()

    try:
        players = _csv(args.players, PLAYERS, "players")
        streaming = normalize_csv(args.streaming, kind="streaming")
        decoders = ["hardware"] if args.hardware_only else normalize_csv(args.decoding, kind="decoding")
        fixed_config = fixed_config_from_args(args)
        validate_fixed_config(fixed_config)
        gsy_engines = _csv(args.gsy_engines, GSY_ENGINES, "GSY engines")
        checks = _csv(args.checks, CHECKS, "checks")
        excluded_players = _csv(args.exclude_players, PLAYERS, "excluded players") if str(args.exclude_players).strip() else []
        excluded_gsy_engines = _csv(args.exclude_gsy_engines, GSY_ENGINES, "excluded GSY engines") if str(args.exclude_gsy_engines).strip() else []
        crash_probe_milestones_ms = _parse_milestones_ms(args.crash_probe_ms)
        cases = filter_cases(build_cases(players, streaming, decoders, gsy_engines), args.case_id)
        issue_checks_by_case: dict[str, list[str]] = {}
        issues_source = ""
        if args.issues_only:
            if args.issues_only == "known":
                issue_profile = KNOWN_ISSUE_PROFILE
                issues_source = "known_v0.5.65_profile_from_20260827_232446"
            else:
                issue_profile = issue_profile_from_report(args.issues_only)
                issues_source = str(Path(args.issues_only).expanduser())
            cases, issue_checks_by_case = select_issue_cases(cases, checks, issue_profile)
        cases, excluded_case_ids = exclude_cases(cases, excluded_players, excluded_gsy_engines, args.exclude_case_id)
        if excluded_case_ids and issue_checks_by_case:
            issue_checks_by_case = {case.id: issue_checks_by_case[case.id] for case in cases if case.id in issue_checks_by_case}
        if not cases:
            raise ValueError("no player configuration cases selected")
        if args.watchdog_ms < 250 or args.watchdog_ms > 300000:
            raise ValueError("--watchdog-ms must be between 250 and 300000")
        if args.slow_recovery_ms < 0 or args.slow_recovery_ms > 300000:
            raise ValueError("--slow-recovery-ms/--slow_recover_ms must be between 0 and 300000")
        if args.start_ms < 0:
            raise ValueError("--start-ms must be >= 0")
        if args.text_char_delay_ms < 0 or args.text_char_delay_ms > 2000:
            raise ValueError("--text-char-delay-ms must be between 0 and 2000")
    except ValueError as exc:
        parser.error(str(exc))

    if args.list_cases:
        for index, case in enumerate(cases, 1):
            if args.issues_only:
                case_checks = issue_checks_by_case.get(case.id, [])
                suffix = "startup-only" if not case_checks else ",".join(case_checks)
                print(f"{index:02d} {case.id} [{suffix}]")
            else:
                print(f"{index:02d} {case.id}")
        print(f"TOTAL CASES: {len(cases)}")
        if excluded_case_ids:
            print(f"EXCLUDED CASES: {len(excluded_case_ids)}")
        if any(case.streaming == "fixed" for case in cases):
            print("FIXED ENCODING PARAMETERS: " + json.dumps(fixed_config, sort_keys=True))
        return 0

    client = MCPProcess()
    results: dict[str, Any] = {}
    infra_failures = 0
    startup_media_failures = 0
    observations = {"RECOVERED": 0, "SLOW_RECOVERY": 0, "PLAYER_CRASHED": 0, "WATCHDOG_EXPIRED": 0, "MEDIA_NOT_RECOVERED": 0}

    def start_for_case(case: PlayerCase) -> dict[str, Any]:
        return start_case(
            client, case,
            server=args.server, port=args.port, text=args.text,
            text_char_delay_ms=args.text_char_delay_ms,
            connect_timeout_s=args.connect_timeout_s,
            ui_stable_ms=args.ui_stable_ms,
            playback_timeout_s=args.playback_timeout_s,
            verify_ms=args.startup_verify_ms,
            fixed_config=fixed_config,
        )

    try:
        negotiated, _ = initialize(client)
        print(f"PASS: MCP initialize handshake ({negotiated})")
        call_dict(client, "adb_connect", timeout=30.0)
        print("PASS: adb_connect")
        adb_session = call_dict(client, "adb_session_status", timeout=30.0)
        print(
            "PASS: persistent ADB shell "
            f"active={adb_session.get('persistentShell')} "
            f"pid={adb_session.get('persistentShellPid')} "
            f"restarts={adb_session.get('persistentShellRestarts')}",
            flush=True,
        )
        print(f"PLAYER MATRIX: {len(cases)} configuration cases; watchdog={args.watchdog_ms} ms; textCharDelay={args.text_char_delay_ms} ms", flush=True)
        print(f"SELECTIONS: decoding={','.join(decoders)} streaming={','.join(streaming)}", flush=True)
        if args.issues_only:
            issue_operation_count = sum(len(issue_checks_by_case.get(case.id, [])) for case in cases)
            startup_only_count = sum(1 for case in cases if not issue_checks_by_case.get(case.id, []))
            print(f"ISSUES ONLY: source={issues_source}; cases={len(cases)}; operations={issue_operation_count}; startupOnly={startup_only_count}", flush=True)
        if any(case.streaming == "fixed" for case in cases):
            print("FIXED ENCODING PARAMETERS: " + json.dumps(fixed_config, sort_keys=True), flush=True)

        for index, case in enumerate(cases, 1):
            case_result: dict[str, Any] = {"index": index, "case": asdict(case), "caseId": case.id, "checks": {}}
            results[case.id] = case_result
            case_checks = issue_checks_by_case.get(case.id, []) if args.issues_only else checks
            case_result["requestedChecks"] = case_checks
            case_result["startupOnlyIssue"] = bool(args.issues_only and not case_checks)
            isolated_issue_checks = bool(args.issues_only and case_checks)
            case_result["issueCheckIsolation"] = "fresh_playback_per_operation" if isolated_issue_checks else "shared_case_session"

            # Issue-only operation runs deliberately do not share a playback session.
            # The previous report proved that one watchdog can poison the next check and
            # turn a media problem into a misleading restart/INFRA_ERROR.  Each selected
            # issue operation now owns a full clean start, config, connect, search and
            # playback verification cycle.  Startup-only issue cases still perform one
            # normal startup so the backend failure itself can be retested.
            if isolated_issue_checks:
                case_result["startupStatus"] = "PER_CHECK_ISOLATED"
                case_result["checkStartups"] = {}
            else:
                try:
                    case_result["startup"] = start_for_case(case)
                    case_result["startupStatus"] = "RECOVERED"
                except PlaybackStartupError as exc:
                    startup_media_failures += 1
                    crashed = bool(exc.playback.get("crashDetected"))
                    case_result["startupStatus"] = "STARTUP_PLAYER_CRASHED" if crashed else "STARTUP_PLAYBACK_FAILED"
                    case_result["startupFailure"] = exc.playback
                    print(f"{case_result['startupStatus']} [{case.id}]: playback did not become healthy", file=sys.stderr)
                    case_result["checkpoint"] = safe_checkpoint(client, f"matrix_{case.id}_{case_result['startupStatus'].lower()}")
                    continue
                except Exception as exc:
                    infra_failures += 1
                    case_result["startupStatus"] = "INFRA_ERROR"
                    case_result["infrastructureError"] = str(exc)
                    print(f"INFRA_ERROR [{case.id}]: startup: {exc}", file=sys.stderr)
                    case_result["checkpoint"] = safe_checkpoint(client, f"matrix_{case.id}_startup_error")
                    if args.stop_on_infra_error:
                        break
                    continue

            restart_required = False
            for check_name in case_checks:
                if isolated_issue_checks:
                    try:
                        case_result["checkStartups"][check_name] = start_for_case(case)
                    except PlaybackStartupError as exc:
                        startup_media_failures += 1
                        crashed = bool(exc.playback.get("crashDetected"))
                        status = "STARTUP_PLAYER_CRASHED" if crashed else "STARTUP_PLAYBACK_FAILED"
                        case_result["checks"][check_name] = {
                            "status": status,
                            "cause": "player_startup",
                            "startupFailure": exc.playback,
                        }
                        print(f"{status} [{case.id}/{check_name}]: isolated playback start did not become healthy", file=sys.stderr)
                        case_result["checks"][check_name]["checkpoint"] = safe_checkpoint(
                            client, f"matrix_{case.id}_{check_name}_{status.lower()}"
                        )
                        continue
                    except Exception as exc:
                        infra_failures += 1
                        case_result["checks"][check_name] = {
                            "status": "INFRA_ERROR",
                            "error": f"isolated startup failed: {exc}",
                        }
                        print(f"INFRA_ERROR [{case.id}/{check_name}]: isolated startup failed: {exc}", file=sys.stderr)
                        case_result["checks"][check_name]["checkpoint"] = safe_checkpoint(
                            client, f"matrix_{case.id}_{check_name}_isolated_startup_infra_error"
                        )
                        if args.stop_on_infra_error:
                            break
                        continue
                elif restart_required:
                    try:
                        case_result.setdefault("restarts", []).append(start_for_case(case))
                        restart_required = False
                    except PlaybackStartupError as exc:
                        startup_media_failures += 1
                        crashed = bool(exc.playback.get("crashDetected"))
                        status = "STARTUP_PLAYER_CRASHED" if crashed else "STARTUP_PLAYBACK_FAILED"
                        case_result["checks"][check_name] = {
                            "status": status,
                            "cause": "player_restart_startup",
                            "startupFailure": exc.playback,
                        }
                        print(f"{status} [{case.id}/{check_name}]: restart playback did not become healthy", file=sys.stderr)
                        case_result["checks"][check_name]["checkpoint"] = safe_checkpoint(
                            client, f"matrix_{case.id}_{check_name}_{status.lower()}"
                        )
                        restart_required = True
                        continue
                    except Exception as exc:
                        infra_failures += 1
                        case_result["checks"][check_name] = {"status": "INFRA_ERROR", "error": f"restart failed: {exc}"}
                        print(f"INFRA_ERROR [{case.id}/{check_name}]: restart failed: {exc}", file=sys.stderr)
                        if args.stop_on_infra_error:
                            break
                        continue

                try:
                    label = f"{case.id}/{check_name}"
                    step_watchdog_ms = args.watchdog_ms
                    if check_name == "absolute_seek":
                        if args.seek_timeout_s > 0:
                            step_watchdog_ms = int(round(args.seek_timeout_s * 1000.0))
                        observation = run_absolute_seek(
                            client, label=label, target_ms=args.start_ms,
                            watchdog_ms=step_watchdog_ms, health_poll_ms=args.health_poll_ms,
                            slow_ms=args.slow_recovery_ms, seek_tolerance_ms=args.seek_tolerance_ms,
                            crash_probe_milestones_ms=crash_probe_milestones_ms,
                        )
                    elif check_name == "seek_forward":
                        observation = run_relative_seek(
                            client, label=label, delta_ms=args.skip_forward_ms,
                            watchdog_ms=args.watchdog_ms, health_poll_ms=args.health_poll_ms,
                            slow_ms=args.slow_recovery_ms, seek_tolerance_ms=args.seek_tolerance_ms,
                            crash_probe_milestones_ms=crash_probe_milestones_ms,
                        )
                    elif check_name == "seek_backward":
                        observation = run_relative_seek(
                            client, label=label, delta_ms=-args.skip_backward_ms,
                            watchdog_ms=args.watchdog_ms, health_poll_ms=args.health_poll_ms,
                            slow_ms=args.slow_recovery_ms, seek_tolerance_ms=args.seek_tolerance_ms,
                            crash_probe_milestones_ms=crash_probe_milestones_ms,
                        )
                    elif check_name == "pause_resume":
                        observation = run_pause_resume(
                            client, label=label, watchdog_ms=args.watchdog_ms,
                            health_poll_ms=args.health_poll_ms, slow_ms=args.slow_recovery_ms,
                            crash_probe_milestones_ms=crash_probe_milestones_ms,
                        )
                    elif check_name == "comskip_right":
                        observation = run_comskip(
                            client, label=label, direction="right", watchdog_ms=args.watchdog_ms,
                            health_poll_ms=args.health_poll_ms, slow_ms=args.slow_recovery_ms,
                            crash_probe_milestones_ms=crash_probe_milestones_ms,
                        )
                    elif check_name == "comskip_left":
                        observation = run_comskip(
                            client, label=label, direction="left", watchdog_ms=args.watchdog_ms,
                            health_poll_ms=args.health_poll_ms, slow_ms=args.slow_recovery_ms,
                            crash_probe_milestones_ms=crash_probe_milestones_ms,
                        )
                    else:
                        raise RuntimeError(f"unhandled check {check_name}")
                    case_result["checks"][check_name] = observation
                    status = observation.get("status", "MEDIA_NOT_RECOVERED")
                    if status in observations:
                        observations[status] += 1
                    print(f"{status} [{case.id}/{check_name}] cause={observation.get('cause', 'undetermined')}")
                    print_output_health(observation)
                    if status in ("SLOW_RECOVERY", "PLAYER_CRASHED", "WATCHDOG_EXPIRED", "MEDIA_NOT_RECOVERED"):
                        observation["checkpoint"] = safe_checkpoint(client, f"matrix_{case.id}_{check_name}_{status.lower()}")
                    # Non-issue full-matrix runs preserve the shared-session behavior.
                    # Issue-only runs always replace the session before the next check.
                    restart_required = False if isolated_issue_checks else media_needs_restart(observation)
                except Exception as exc:
                    infra_failures += 1
                    case_result["checks"][check_name] = {"status": "INFRA_ERROR", "error": str(exc)}
                    print(f"INFRA_ERROR [{case.id}/{check_name}]: {exc}", file=sys.stderr)
                    case_result["checks"][check_name]["checkpoint"] = safe_checkpoint(client, f"matrix_{case.id}_{check_name}_infra_error")
                    restart_required = False if isolated_issue_checks else True
                    if args.stop_on_infra_error:
                        break

            case_result["executionComplete"] = len(case_result["checks"]) == len(case_checks)
            case_result["infrastructurePassed"] = all(x.get("status") != "INFRA_ERROR" for x in case_result["checks"].values())
            if args.stop_on_infra_error and infra_failures:
                break

        report = {
            "schema": 1,
            "suite": "complete_player_backend_matrix",
            "server": args.server,
            "port": args.port,
            "searchText": args.text,
            "watchdogMs": args.watchdog_ms,
            "watchdogSeconds": args.watchdog_ms / 1000.0,
            "slowRecoveryMs": args.slow_recovery_ms,
            "crashProbeMilestonesMs": list(crash_probe_milestones_ms),
            "seekToleranceMs": args.seek_tolerance_ms,
            "seekLandingIsVerdict": False,
            "eventTrapsEnabled": True,
            "issuesOnly": bool(args.issues_only),
            "issuesSource": issues_source,
            "issueChecksByCase": issue_checks_by_case,
            "issueCheckIsolation": "fresh_playback_per_operation" if args.issues_only else "shared_case_session",
            "retestExclusions": {
                "players": excluded_players,
                "gsyEngines": excluded_gsy_engines,
                "caseIds": excluded_case_ids,
                "excludedCaseCount": len(excluded_case_ids),
            },
            "hardwareOnly": bool(args.hardware_only),
            "watchdogExpiryIsFailure": False,
            "causeAttribution": "undetermined_unless_evidence_proves_android_sagetv_server_or_ffmpeg",
            "players": players,
            "streamingModes": streaming,
            "decoders": decoders,
            "fixedEncoding": fixed_config,
            "gsyEngines": gsy_engines,
            "checks": checks,
            "configurationCaseCount": len(cases),
            "expectedFullMatrixCaseCount": 63,
            "infrastructureFailures": infra_failures,
            "startupMediaFailures": startup_media_failures,
            "mediaObservations": observations,
            "executionPassed": infra_failures == 0,
            "results": results,
        }
        path = write_report(report, args.report)
        print("\n=== PLAYER MATRIX SUMMARY ===")
        print(json.dumps({
            "configurationCases": len(cases),
            "infrastructureFailures": infra_failures,
            "startupMediaFailures": startup_media_failures,
            "mediaObservations": observations,
            "watchdogMs": args.watchdog_ms,
            "slowRecoveryMs": args.slow_recovery_ms,
            "crashProbeMilestonesMs": list(crash_probe_milestones_ms),
            "seekToleranceMs": args.seek_tolerance_ms,
        }, indent=2, sort_keys=True))
        print(f"REPORT: {path}")
        if infra_failures:
            print(f"PLAYER MATRIX: COMPLETE WITH {infra_failures} INFRASTRUCTURE ERROR(S)", file=sys.stderr)
            return 1
        print("PLAYER MATRIX: COMPLETE")
        return 0
    finally:
        if not args.leave_running:
            try:
                call_dict(client, "dev_exit_session", {"stop_app": True}, timeout=30.0)
            except Exception:
                pass
        client.close()


if __name__ == "__main__":
    raise SystemExit(main())
