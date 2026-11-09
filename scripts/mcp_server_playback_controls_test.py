#!/usr/bin/env python3
"""EXT-011: bounded real stock-server rate/scan controls with restoration."""
import argparse
import json
import os
from pathlib import Path
import time

from android_test_keep_awake import begin, end, state_path
from mcp_seek_suite import MCPProcess, initialize, call_dict
from mcp_lifecycle_test import require, wait_automation_ready
from sagetv_dev_mcp.config import load_test_environment
from sagetv_dev_mcp.core_mcp_api import CoreMcpApiClient

ROOT = Path(__file__).resolve().parents[1]


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--media-file-id', type=int, required=True)
    p.add_argument('--media-type', choices=('T', 'V'), default='T',
                   help='Resolve the explicit existing TV recording or imported video; never tune')
    p.add_argument('--player', choices=('media3', 'exoplayer'), default='media3')
    p.add_argument('--install-apk', default='', help='Optional validated development candidate')
    p.add_argument('--hold-seconds', type=int, default=0)
    p.add_argument('--renderer', choices=('keep', 'opengl', 'gdx'), default='keep')
    p.add_argument('--expect-rate-rejected', action='store_true',
                   help='Negative actual-growing Pull gate; never tune or force rate ownership')
    p.add_argument('--output', type=Path, required=True)
    args = p.parse_args()
    require(0 <= args.hold_seconds <= 45, 'Bounded capture hold required')
    out = args.output.resolve()
    require(out.is_relative_to((ROOT/'artifacts/active/EXT-011').resolve())
            and not out.exists(), 'New explicit EXT-011 output required')
    env = load_test_environment()
    require(env.device_serial() == '192.168.10.25:5555'
            and env.server_address() == '192.168.10.175', 'Non-Pro/stock175 only')
    os.environ['SAGETV_ARTIFACT_DIR'] = str(out.parent)
    out.parent.mkdir(parents=True, exist_ok=True)
    r = {'passed': False, 'player': args.player, 'mediaFileId': args.media_file_id,
         'mediaType': args.media_type,
         'renderer': args.renderer,
         'serial': env.device_serial(), 'server': env.server_address(),
         'serverChanges': False, 'tunerControls': False, 'measurements': []}
    c = MCPProcess()
    checkpoint = connected = awake = False
    api = ctx = cc = None

    def save():
        out.write_text(json.dumps(r, indent=2)+'\n')

    def state():
        return call_dict(c, 'dev_player_state')

    def healthy():
        result = call_dict(c, 'dev_wait_for_playback_started',
                {'timeout_s': 40, 'verify_ms': 1000,
                 'expect_video': True, 'expect_audio': True}, timeout=65)
        require(result.get('passed'), 'Actual advancing A/V required')
        return result

    def rate(value):
        result = call_dict(c, 'dev_playback_rate', {'rate': value})
        if args.expect_rate_rejected:
            require(float(result.get('acceptedRate', -1)) == 1.0
                    and result.get('accepted') is False,
                    'Actual growing source must retain stock rate ownership: '+str(result))
            return result
        require(float(result.get('acceptedRate', 1)) == value,
                'Requested rate rejected: '+str(result))
        return result

    def measure(label, seconds):
        before = state()
        start = time.monotonic()
        time.sleep(seconds)
        after = state()
        row = {'label': label, 'before': before, 'after': after,
               'wallMs': round((time.monotonic()-start)*1000),
               'mediaDeltaMs': int(after.get('health_playerPositionMs', -1))
                    - int(before.get('health_playerPositionMs', -1))}
        r['measurements'].append(row)
        save()
        return row

    try:
        initialize(c)
        r['adb'] = call_dict(c, 'adb_connect')
        r['before'] = state()
        require(not r['before'].get('connected')
                and r['before'].get('clientId') == env.device()['client_id'],
                'Requires exact idle disconnected device')
        api = CoreMcpApiClient.discover(env.server_address())
        r['activity'] = api._request('server.activity')
        rows = api._legacy_client().call('GetMediaFiles', args.media_type, start=0, size=5000,
                fields=('MediaFileID', 'FileDuration', 'MediaTitle')).get('Result', [])
        matched = [row for row in rows if isinstance(row, dict)
                   and row.get('MediaFileID') == args.media_file_id]
        require(len(matched) == 1 and int(matched[0]['FileDuration']) > 600000,
                'Explicit existing long recording/video must resolve before mutation')
        r['source'] = matched[0]
        r['awake'] = begin(env.device_serial(), state_path(env.device_serial()), 'automated')
        awake = True
        r['checkpoint'] = call_dict(c, 'dev_checkpoint_settings')
        require(r['checkpoint'].get('captured'), 'Settings checkpoint failed')
        checkpoint = True
        if args.install_apk:
            r['install'] = call_dict(c, 'install_dev_apk', {'apk_path': args.install_apk}, timeout=200)
            require('Success' in str(r['install'].get('result', '')),
                    'Guarded APK install failed')
        require(call_dict(c, 'dev_prepare_clean_start',
                {'wake': True, 'graceful_timeout_s': 2.0}).get('readyToLaunch'), 'Clean start failed')
        r['config'] = call_dict(c, 'dev_set_player_config',
                {'player': args.player, 'streaming': 'pull', 'mim_direct_mode': 'off'})
        r['connect'] = call_dict(c, 'dev_connect_server',
                {'address': env.server_address(), 'save': False,
                 'renderer': '' if args.renderer == 'keep' else args.renderer}, timeout=45)
        connected = True
        wait_automation_ready(c, timeout_s=60)
        identity = state()
        require(identity.get('clientId') == env.device()['client_id'], 'Wrong client')
        ctx = api.resolve_context(identity['clientId'])
        r['scope'] = {'serial': env.device_serial(), 'clientId': identity['clientId'], 'context': ctx}
        cc = api.closed_caption_state(ctx)
        r['watch'] = api.watch(ctx, args.media_file_id)
        save()
        r['health'] = healthy()
        initial = state()
        require(api.current_media_file_id(ctx) == args.media_file_id
                and initial.get('health_pushMode') is False, 'Actual exact-source Pull required')
        if args.expect_rate_rejected:
            active = api._legacy_client().call('GetCurrentlyRecordingMediaFiles',
                fields=('MediaFileID', 'FileDuration', 'IsFileCurrentlyRecording', 'NumberOfSegments', 'Size'))
            r['recordingWitness'] = active
            writers = [row for row in active.get('Result', []) if isinstance(row, dict)
                       and row.get('MediaFileID') == args.media_file_id
                       and row.get('IsFileCurrentlyRecording') is True]
            require(len(writers) == 1, 'Independent current-recording proof required')
            # A recording-in-progress can resume an OLD completed segment.
            # This explicit negative-gate preparation uses the current public
            # extent to select the writer segment. It is NOT a runtime repair,
            # rate-acceptance workaround, tuner command or a guessed seek0.
            writer_target = int(writers[0]['FileDuration'])-20000
            require(writer_target > 0, 'Current writer extent unavailable')
            r['writerPreparation'] = api.seek(ctx, writer_target)
            healthy()
            initial = state()
            require(api.current_media_file_id(ctx) == args.media_file_id, 'Writer identity changed')
            # Initial seek preparation may publish a finite snapshot even on
            # a growing source. Length alone is NOT growth evidence. Verify
            # the independently observed writer still appends bytes instead.
            later = api._legacy_client().call('GetCurrentlyRecordingMediaFiles',
                fields=('MediaFileID', 'IsFileCurrentlyRecording', 'Size'))
            r['writerGrowthAfter'] = later
            later_writers = [row for row in later.get('Result', []) if isinstance(row, dict)
                            and row.get('MediaFileID') == args.media_file_id
                            and row.get('IsFileCurrentlyRecording') is True]
            require(len(later_writers) == 1
                    and int(later_writers[0]['Size']) > int(writers[0]['Size']),
                    'Actual writer byte growth required, not a finite-length inference')
            require(initial.get('health_playerReady') is True, 'Prepared growing player required')
            r['native2xRejected'] = rate(2.0)
            healthy()
        else:
            position = int(initial.get('mediaTimeMs', -1))
            require(0 <= position < int(matched[0]['FileDuration'])-180000,
                    'Existing bookmark must leave forward scan room; no guessed normalization')
            print('STEP native2x', flush=True)
            r['native2x'] = rate(2.0)
            # Accumulate real played media before the opposite-direction gate even
            # when the existing imported fixture starts at zero. Never manufacture
            # eligibility by removing live guards or by a corrective server seek.
            native = measure('native2x', 10)
            require(native['mediaDeltaMs'] > native['wallMs']*1.5,
                    'Native2x did not accelerate the real player clock')
            rate(1.0)
            healthy()
            print('STEP forward4x', flush=True)
            rate(4.0)
            forward = measure('forward4x', 7)
            require(forward['mediaDeltaMs'] >= 14000, 'Forward scan did not progress')
            rate(1.0)
            healthy()
            print('STEP reverse4x', flush=True)
            rate(-4.0)
            reverse = measure('reverse4x', 7)
            require(reverse['mediaDeltaMs'] <= -5000, 'Reverse scan did not progress')
            rate(1.0)
            healthy()
        normal = measure('retained1x' if args.expect_rate_rejected else 'return1x', 2)
        require(0.75 <= normal['mediaDeltaMs']/normal['wallMs'] <= 1.25,
                'Return-to-play clock was not1x')
        r['finalHealth'] = healthy()
        r['final'] = state()
        if args.hold_seconds:
            ready = out.with_suffix('.ready.json')
            require(not ready.exists(), 'Never reuse a capture marker')
            ready.write_text(json.dumps({'scopeVerified': True, 'serial': env.device_serial(),
                'clientId': identity['clientId'], 'server': env.server_address(),
                'expiresAtMs': int((time.time()+args.hold_seconds)*1000)})+'\n')
            print('HOLD '+str(ready), flush=True)
            time.sleep(args.hold_seconds)
        r['passed'] = not bool(r['final'].get('health_playerError'))
    except Exception as exc:
        r['passed'] = False
        r['failure'] = str(exc)
        try:
            r['failureState'] = state()
            r['events'] = call_dict(c, 'dev_player_events')
        except Exception as inner:
            r['stateError'] = str(inner)
    finally:
        for name, action in (
            ('captionRestore', lambda: api.set_closed_caption_state(ctx, cc) if api and ctx and cc is not None else None),
            ('exit', lambda: call_dict(c, 'dev_exit_session', {'stop_app': True}, timeout=45) if connected else None),
            ('restore', lambda: call_dict(c, 'dev_restore_settings', timeout=100) if checkpoint else None),
            ('powerRestore', lambda: end(env.device_serial(), state_path(env.device_serial()), 'automated') if awake else None)):
            try:
                r[name] = action()
                if name == 'restore' and checkpoint:
                    require(r[name].get('restored'), 'Settings restoration failed')
            except Exception as exc:
                r['passed'] = False
                r[name+'Error'] = str(exc)
        save()
        c.close()
    print(json.dumps({'passed': r['passed'], 'failure': r.get('failure'), 'output': str(out)}), flush=True)
    return 0 if r['passed'] else 1


if __name__ == '__main__':
    raise SystemExit(main())
