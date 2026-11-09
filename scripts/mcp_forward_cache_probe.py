"""Bounded EXT-010 actual command29 probe, never an automatic seek recovery."""
import re
import time

from mcp_lifecycle_test import require
from mcp_seek_suite import call_dict
from sagetv_dev_mcp.config import load_test_environment
from sagetv_dev_mcp.core_mcp_api import CoreMcpApiClient


def probe(client, adb, jump_ms=20000):
    env = load_test_environment()
    require(env.device_serial() == '192.168.10.25:5555'
            and env.server_address() == '192.168.10.175', 'Exact cache-probe scope required')
    api = CoreMcpApiClient.discover(env.server_address())
    before = call_dict(client, 'dev_player_state')
    require(before.get('clientId') == env.device()['client_id']
            and before.get('serverAddress') == env.server_address()
            and before.get('health_pushMode') is True, 'Wrong client/server/transport')
    ctx = api.resolve_context(before['clientId'])
    media_id = api.current_media_file_id(ctx)
    require(media_id is not None, 'Cache probe needs an existing current media file')
    deadline = time.monotonic()+30
    while time.monotonic() < deadline:
        before = call_dict(client, 'dev_player_state')
        if (str(before.get('pushForwardCacheState', '')).startswith('ready=true,')
                and int(before.get('health_bufferedPositionMs', -1))
                    - int(before.get('health_playerPositionMs', -1)) > jump_ms+10000):
            break
        time.sleep(.5)
    else:
        raise RuntimeError('No real selected decoder-queue headroom; no seek sent')
    require(before.get('clientId') == env.device()['client_id']
            and before.get('serverAddress') == env.server_address()
            and before.get('health_pushMode') is True
            and api.current_media_file_id(ctx) == media_id,
            'Source changed before cache input; no control sent')
    target = int(before['health_playerPositionMs'])+jump_ms
    request = adb.dev_control('seek_time', target_ms=target, wire_command29=True)
    require(request.get('accepted') is True and request.get('replyLength') == 0,
            'Actual command29 was not accepted with zero reply bytes')
    deadline = time.monotonic()+12
    while time.monotonic() < deadline:
        after = call_dict(client, 'dev_player_state')
        cache = str(after.get('pushForwardCacheState', ''))
        match = re.search(r'(?:^|,)lastTargetUs=(-?\d+)', cache)
        if match and int(match[1]) == target*1000:
            break
        time.sleep(.3)
    else:
        raise RuntimeError('Actual selected-queue transaction did not reach the exact requested target')
    time.sleep(3)
    settled = call_dict(client, 'dev_player_state')
    require(api.current_media_file_id(ctx) == media_id
            and settled.get('clientId') == env.device()['client_id']
            and settled.get('health_pushMode') is True
            and int(settled.get('serverSeekSequence', 0)) == int(before.get('serverSeekSequence', 0))+1
            and settled.get('serverFlushSequence') == before.get('serverFlushSequence')
            and int(settled.get('health_playerPositionMs', -1)) >= target
            and int(settled.get('health_videoRendered', -1)) > int(before.get('health_videoRendered', -1))
            and int(settled.get('health_audioRendered', -1)) > int(before.get('health_audioRendered', -1))
            and not settled.get('health_playerError'), 'Cache jump did not sustain same-source A/V')
    return {'inputPath': 'explicit_client_media_command_29_not_core_seek',
            'mediaFileId': media_id, 'decoderTargetMs': target,
            'queue': cache, 'flushDelta': 0, 'passed': True}


def reject_outside_window(client, adb):
    """Require no renderer/producer reset for backward and unavailable targets.

    The debug receiver's accepted field only confirms dispatcher admission;
    the playback-thread range check must still refuse these exact targets.
    """
    results = []
    for kind in ('backward', 'beyond-buffer'):
        before = call_dict(client, 'dev_player_state')
        env = load_test_environment()
        require(before.get('clientId') == env.device()['client_id']
                and before.get('serverAddress') == env.server_address()
                and before.get('health_pushMode') is True
                and str(before.get('pushForwardCacheState', '')).startswith('ready=true,'),
                'Exact ready Push period required for rejection probe')
        current = int(before['health_playerPositionMs'])
        target = max(0, current-10000) if kind == 'backward' else int(before['health_bufferedPositionMs'])+60000
        require(kind != 'backward' or target < current-1000, 'No meaningful backward range')
        started = time.monotonic()
        request = adb.dev_control('seek_time', target_ms=target, wire_command29=True)
        require(request.get('replyLength') == 0, 'Rejected input changed stock zero-reply framing')
        time.sleep(2)
        after = call_dict(client, 'dev_player_state')
        elapsed = round((time.monotonic()-started)*1000)
        delta = int(after['health_playerPositionMs'])-current
        old = re.search(r'(?:^|,)seeks=(\d+)', str(before.get('pushForwardCacheState')))
        new = re.search(r'(?:^|,)seeks=(\d+)', str(after.get('pushForwardCacheState')))
        paused = before.get('state') == 3
        require(old and new and old[1] == new[1]
                and before.get('state') == after.get('state')
                and after.get('serverFlushSequence') == before.get('serverFlushSequence')
                and int(after.get('serverSeekSequence', 0)) == int(before.get('serverSeekSequence', 0))+1
                and (abs(delta) <= 100 if paused else 500 <= delta <= elapsed+1500)
                and not after.get('health_playerError'), 'Unavailable cache target disturbed playback')
        results.append({'targetKind': kind, 'paused': paused, 'targetMs': target,
                        'observedDeltaMs': delta, 'elapsedMs': elapsed,
                        'queueSeeksUnchanged': True, 'passed': True})
    return results


def probe_audio_tracks(client, adb):
    """Switch existing tracks, not output mode/offset, then seek their new queues."""
    initial = call_dict(client, 'dev_player_state')
    require(int(initial.get('audioTrackCount', 0)) >= 2, 'Two actual audio tracks required')
    rows = []
    for ordinal in (1, 0):
        selected = call_dict(client, 'dev_set_audio_track', {'index': ordinal})
        require(selected.get('accepted') is True, 'Audio track switch rejected')
        output = call_dict(client, 'dev_wait_for_playback_started',
                           {'timeout_s': 35, 'verify_ms': 1000,
                            'expect_video': True, 'expect_audio': True}, timeout=60)
        require(output.get('passed'), 'Switched track lost actual A/V')
        state = call_dict(client, 'dev_player_state')
        require(state.get('selectedAudioTrack') == selected.get('playerIndex')
                and state.get('audioOffsetMs') == initial.get('audioOffsetMs')
                and state.get('audioOutputMode') == initial.get('audioOutputMode'),
                'Track/offset/output ownership changed unexpectedly')
        rows.append({'ordinal': ordinal, 'selectedPlayerIndex': selected.get('playerIndex'),
                     'cacheProbe': probe(client, adb, jump_ms=5000)})
    return rows
