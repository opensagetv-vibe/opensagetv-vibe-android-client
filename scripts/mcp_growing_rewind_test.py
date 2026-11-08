#!/usr/bin/env python3
"""GROW-001: rewind an existing stock-server recording that is still growing.

Never tune, create/cancel a recording, or change Core. The exact idle device is
checkpointed; every source witness comes from public recording metadata. This
qualifies the candidate locally, not an unsupplied reporter's recording.
"""
import argparse
import json
import os
from pathlib import Path
import time

from android_test_keep_awake import begin, end, state_path
from mcp_lifecycle_test import require, wait_automation_ready
from mcp_seek_suite import MCPProcess, initialize, call_dict, tool_call
from sagetv_dev_mcp.config import load_test_environment
from sagetv_dev_mcp.core_mcp_api import CoreMcpApiClient

ROOT = Path(__file__).resolve().parents[1]


def recording_witness(api, media_id):
    rows = api._legacy_client().call('GetCurrentlyRecordingMediaFiles').get('Result', [])
    matches = [r for r in rows if int(r.get('MediaFileID', -1)) == media_id
               and r.get('IsFileCurrentlyRecording') is True]
    require(len(matches) == 1, 'Exact source is no longer an active recording')
    row = matches[0]
    require(int(row.get('NumberOfSegments', 0)) == 1,
            'This focused gate requires one segment; never conflate segment clocks')
    return {k: row.get(k) for k in ('MediaFileID', 'SegmentFiles', 'Size',
            'FileDuration', 'FileStartTime', 'FileEndTime', 'IsFileCurrentlyRecording',
            'MediaFileFormatDescription')}


def landed(state, target, elapsed_ms):
    """Use actual backend time for Pull; calibrated stock Push clock otherwise."""
    key = 'mediaTimeMs' if state.get('health_pushMode') else 'health_playerPositionMs'
    position = int(state.get(key, -1))
    return (position >= 0 and bool(state.get('health_isPlaying'))
            and bool(state.get('health_surfaceValid'))
            and abs(position - target) <= 4000 + elapsed_ms
            and int(state.get('health_videoRendered', 0)) > 0
            and int(state.get('health_audioRendered', 0)) > 0)


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--media-file-id', type=int, required=True)
    p.add_argument('--player', choices=('media3', 'exoplayer', 'gsyplayer'), default='media3')
    p.add_argument('--streaming', choices=('push', 'pull'), required=True)
    p.add_argument('--install-apk', default='')
    p.add_argument('--hold-seconds', type=int, default=0,
                   help='Bounded live-stage hold for an independent host HDMI capture')
    p.add_argument('--output', type=Path, required=True)
    args = p.parse_args()
    require(0 <= args.hold_seconds <= 45, 'Capture hold must be0..45 seconds')
    output = args.output.resolve()
    require(output.is_relative_to((ROOT/'artifacts/active/GROW-001').resolve())
            and not output.exists(), 'New explicit GROW-001 output required')
    os.environ['SAGETV_ARTIFACT_DIR'] = str(output.parent)
    env = load_test_environment()
    require(env.device_serial() == '192.168.10.25:5555'
            and env.server_address() == '192.168.10.175',
            'This non-Pro-first qualification is scoped to commissioned25/stock175')
    output.parent.mkdir(parents=True, exist_ok=True)
    r = {'passed': False, 'player': args.player, 'selectedTransport': args.streaming,
         'serial': env.device_serial(), 'server': env.server_address(), 'seeks': [],
         'scope': 'existing growing recording, not proof of discussion356 resolution',
         'serverChanges': False, 'tunerControls': False}
    c = MCPProcess()
    checkpointed = connected = False
    awake_serial = api = ctx = cc = None

    def save():
        output.write_text(json.dumps(r, indent=2)+'\n')

    def state():
        return call_dict(c, 'dev_player_state')

    def source():
        s = state()
        require(s.get('health_pushMode') is (args.streaming == 'push'),
                'Actual transport does not match selected transport')
        require(api.current_media_file_id(ctx) == args.media_file_id,
                'Another recording is selected; no seek sent')
        return s

    def seek_once(target):
        before = source()
        witness = recording_witness(api, args.media_file_id)
        row = {'targetMs': target, 'before': before, 'recording': witness}
        r['seeks'].append(row)
        started = time.monotonic()
        row['ack'] = api.seek(ctx, target)
        deadline = started + 30
        while time.monotonic() < deadline:
            s = state()
            row['after'] = s
            elapsed = round((time.monotonic()-started)*1000)
            if landed(s, target, elapsed):
                time.sleep(1.5)
                final = source()
                row['last'] = final
                require(landed(final, target, elapsed+1500), 'Landing did not remain stable')
                require(all(int(final.get(k, -1)) > int(s.get(k, -1)) for k in
                        ('health_videoRendered', 'health_audioRendered')),
                        'Both A/V outputs must advance after landing')
                row['recoveryMs'] = elapsed
                row['passed'] = True
                print('LANDING '+str(target)+' '+str(elapsed)+'ms', flush=True)
                save()
                return final
            time.sleep(.4)
        raise RuntimeError('No verified advancing landing at '+str(target))

    try:
        initialize(c)
        r['adb'] = call_dict(c, 'adb_connect')
        r['before'] = state()
        require(not r['before'].get('connected')
                and r['before'].get('clientId') == env.device()['client_id'],
                'Requires the exact idle disconnected commissioned device')
        api = CoreMcpApiClient.discover(env.server_address())
        r['activityBefore'] = api._request('server.activity')
        r['recordingBefore'] = recording_witness(api, args.media_file_id)
        require(int(r['recordingBefore']['FileDuration']) >= 180000,
                'Recording must have at least three minutes to rewind')
        awake_serial = env.device_serial()
        r['awake'] = begin(awake_serial, state_path(awake_serial), 'automated')
        r['checkpoint'] = call_dict(c, 'dev_checkpoint_settings')
        require(r['checkpoint'].get('captured'), 'Preference checkpoint failed')
        checkpointed = True
        if args.install_apk:
            r['install'] = call_dict(c, 'install_dev_apk', {'apk_path': args.install_apk}, timeout=200)
            require(r['install'].get('installed') or r['install'].get('success')
                    or 'Success' in str(r['install']), 'Guarded install did not report success')
        r['cleanStart'] = call_dict(c, 'dev_prepare_clean_start',
                {'wake': True, 'graceful_timeout_s': 2.0})
        require(r['cleanStart'].get('readyToLaunch'), 'Clean start failed')
        r['config'] = call_dict(c, 'dev_set_player_config',
                {'player': args.player, 'streaming': args.streaming,
                 'gsy_engine': 'media3', 'mim_direct_mode': 'off'})
        r['connect'] = call_dict(c, 'dev_connect_server',
                {'address': env.server_address(), 'save': False}, timeout=45)
        connected = True
        wait_automation_ready(c, timeout_s=60)
        identity = state()
        require(identity.get('clientId') == env.device()['client_id'], 'Client identity changed')
        ctx = api.resolve_context(identity['clientId'])
        cc = api.closed_caption_state(ctx)
        r['identity'] = {'serial': awake_serial, 'clientId': identity['clientId'], 'context': ctx}
        r['watch'] = call_dict(c, 'dev_play_media_file_id',
                {'media_file_id': args.media_file_id, 'timeout_s': 45}, timeout=90)
        require(r['watch'].get('passed'), 'Watch did not produce advancing A/V')
        r['initial'] = source()
        r['actualTransport'] = 'Push' if r['initial'].get('health_pushMode') else 'Pull'
        witness = recording_witness(api, args.media_file_id)
        # Start behind the current writer, then rewind minutes to the beginning.
        # All requests use the normal stock Seek API exactly once.
        seek_once(int(witness['FileDuration'])-30000)
        seek_once(0)
        seek_once(60000)
        seek_once(15000)
        time.sleep(5)
        r['recordingAfter'] = recording_witness(api, args.media_file_id)
        require(r['recordingAfter']['SegmentFiles'] == r['recordingBefore']['SegmentFiles'],
                'Recording segment changed; gate is unqualified')
        require(int(r['recordingAfter']['Size']) > int(r['recordingBefore']['Size']),
                'Recording bytes did not actually grow')
        r['picture'] = tool_call(c, 'take_screenshot', {'label': output.stem})
        r['events'] = call_dict(c, 'dev_player_events')
        if args.hold_seconds:
            marker = output.with_suffix('.ready.json')
            marker.write_text(json.dumps({'scopeVerified': True, 'serial': awake_serial,
                'clientId': identity['clientId'], 'context': ctx,
                'expiresAtMs': int((time.time()+args.hold_seconds)*1000)})+'\n')
            print('LIVE_STAGE_READY '+str(marker), flush=True)
            time.sleep(args.hold_seconds)
        r['passed'] = True
    except Exception as exc:
        r['failure'] = str(exc)
        try: r['failureState'] = state()
        except Exception as inner: r['stateError'] = str(inner)
    finally:
        for name, action in (
            ('captionRestore', lambda: api.set_closed_caption_state(ctx, cc)
                if api and ctx and cc is not None else None),
            ('exit', lambda: call_dict(c, 'dev_exit_session', {'stop_app': True}, timeout=45)
                if connected else None),
            ('restore', lambda: call_dict(c, 'dev_restore_settings', timeout=100)
                if checkpointed else None),
            ('powerRestore', lambda: end(awake_serial, state_path(awake_serial), 'automated')
                if awake_serial else None)):
            try:
                r[name] = action()
                if name == 'restore' and checkpointed:
                    require(r[name].get('restored'), 'Settings restoration did not verify')
            except Exception as exc:
                r['passed'] = False
                r[name+'Error'] = str(exc)
        save()
        c.close()
    print(json.dumps({'passed': r['passed'], 'failure': r.get('failure'), 'output': str(output)}), flush=True)
    return 0 if r['passed'] else 1


if __name__ == '__main__':
    raise SystemExit(main())
