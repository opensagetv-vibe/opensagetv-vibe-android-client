#!/usr/bin/env python3
"""GROW-002: ordinary stock Watch handoff, never a corrective seek afterwards.

Only existing files are used: no tuner, recording, history-clear or Core edits.
An explicit seed seek in the OLD file establishes a substantially different
clock. The next Watch must establish the new file's clock without STOP/OPENURL
being manufactured by the harness. Preserve settings and server CC on exit.
"""
import argparse
import json
import os
from pathlib import Path
import time

from android_test_keep_awake import begin, end, state_path
from mcp_lifecycle_test import require, wait_automation_ready
from mcp_seek_suite import MCPProcess, initialize, call_dict, tool_call
from sagetv_dev_mcp.config import load_config, load_test_environment
from sagetv_dev_mcp.adb import AdbClient
from sagetv_dev_mcp.core_mcp_api import CoreMcpApiClient

ROOT = Path(__file__).resolve().parents[1]


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--old-id', type=int, required=True)
    p.add_argument('--new-id', type=int, required=True)
    p.add_argument('--seed-ms', type=int, default=4500000)
    p.add_argument('--player', choices=('media3', 'exoplayer', 'gsyplayer', 'ijkplayer'), default='media3')
    p.add_argument('--gsy-engine', choices=('media3', 'legacy_exo', 'system'), default='media3')
    p.add_argument('--decoding', choices=('hardware', 'software', 'hardware_preferred'), default='hardware')
    p.add_argument('--install-apk', default='')
    p.add_argument('--output', type=Path, required=True)
    p.add_argument('--hold-seconds', type=int, default=0)
    p.add_argument('--task-id', choices=('GROW-002', 'EXT-009', 'EXT-010', 'EXT-013'), default='GROW-002')
    p.add_argument('--forward-cache-trial', action='store_true',
                   help='Unadvertised Media3 queue-source epoch qualification; no persisted option')
    p.add_argument('--require-forward-cache', action='store_true',
                   help='Require the default queue source, without enabling a debug override')
    p.add_argument('--exercise-pause', action='store_true')
    p.add_argument('--exercise-frame-step', action='store_true',
                   help='Probe the non-advertised buffered forward-step candidate after pausing')
    p.add_argument('--expect-frame-step-rejected', action='store_true',
                   help='Require safe rejection and a frozen paused timeline instead of a step')
    args = p.parse_args()
    require(not args.forward_cache_trial or (args.task_id == 'EXT-010'
            and args.player in ('media3', 'exoplayer')), 'Queue trial belongs to explicit extractor EXT-010 gate')
    require(not args.require_forward_cache or (args.task_id == 'EXT-010'
            and args.player in ('media3', 'exoplayer') and not args.forward_cache_trial),
            'Default queue qualification must not enable its debug override')
    out = args.output.resolve()
    require(out.is_relative_to((ROOT/'artifacts/active'/args.task_id).resolve())
            and not out.exists(), 'New explicit GROW-002 output required')
    require(args.old_id != args.new_id and (args.seed_ms == 0 or args.seed_ms > 60000)
            and 0 <= args.hold_seconds <= 45, 'Invalid handoff parameters')
    env = load_test_environment()
    require(env.device_serial() == '192.168.10.25:5555'
            and env.server_address() == '192.168.10.175', 'Non-Pro/stock175 only')
    os.environ['SAGETV_ARTIFACT_DIR'] = str(out.parent)
    out.parent.mkdir(parents=True, exist_ok=True)
    r = {'passed': False, 'player': args.player, 'oldId': args.old_id,
         'newId': args.new_id, 'seedMs': args.seed_ms, 'samples': [],
         'serial': env.device_serial(), 'server': env.server_address(),
         'noPostWatchSeek': True, 'serverChanges': False, 'tunerControls': False}
    c = MCPProcess()
    checkpoint = connected = awake = False
    api = ctx = cc = None
    trial_adb = None

    def state():
        return call_dict(c, 'dev_player_state')

    def save():
        out.write_text(json.dumps(r, indent=2)+'\n')

    def healthy():
        health = call_dict(c, 'dev_wait_for_playback_started',
                {'timeout_s': 40, 'verify_ms': 1000,
                 'expect_video': True, 'expect_audio': True}, timeout=65)
        require(health.get('passed'), 'Actual advancing A/V required')
        return health

    def playing(s):
        # IJK has no Exo renderer counters. Use its actual native play state
        # and rendering-start witness, never relabel unsupported counters PASS.
        return (bool(s.get('health_basicIsPlaying'))
                and bool(s.get('health_firstVideoFrameRendered'))
                if args.player == 'ijkplayer' else bool(s.get('health_isPlaying')))

    try:
        initialize(c)
        r['adb'] = call_dict(c, 'adb_connect')
        r['before'] = state()
        require(not r['before'].get('connected')
                and r['before'].get('clientId') == env.device()['client_id'],
                'Requires exact idle disconnected device')
        api = CoreMcpApiClient.discover(env.server_address())
        r['activity'] = api._request('server.activity')
        rows = api._legacy_client().call('GetMediaFiles', 'T', start=0, size=5000,
                fields=('MediaFileID', 'FileStartTime', 'FileEndTime', 'FileDuration',
                        'MediaTitle', 'SegmentFiles')).get('Result', [])
        pair = {int(row['MediaFileID']): row for row in rows
                if isinstance(row, dict) and row.get('MediaFileID') in (args.old_id, args.new_id)}
        require(len(pair) == 2, 'Both existing TV sources must resolve before device mutation')
        r['pairWitness'] = pair
        # VideoFrame.areMediaFileFormatsSwitchable additionally checks station
        # and capture device. Imported controls/non-contiguous recordings cause
        # a legitimate full reload/resume, not this fast-load reproduction.
        require(pair[args.old_id].get('FileEndTime') == pair[args.new_id].get('FileStartTime'),
                'Core fastLoad requires contiguous TV recordings, not arbitrary fixtures')
        require(not args.seed_ms or args.seed_ms+30000 < int(pair[args.old_id]['FileDuration']),
                'Seed must stay safely inside the old recording')
        r['awake'] = begin(env.device_serial(), state_path(env.device_serial()), 'automated')
        awake = True
        r['checkpoint'] = call_dict(c, 'dev_checkpoint_settings')
        require(r['checkpoint'].get('captured'), 'Settings checkpoint failed')
        checkpoint = True
        if args.install_apk:
            r['install'] = call_dict(c, 'install_dev_apk', {'apk_path': args.install_apk}, timeout=200)
            require(r['install'].get('installed') or r['install'].get('success')
                    or 'Success' in str(r['install']), 'Install not verified')
        require(call_dict(c, 'dev_prepare_clean_start',
                {'wake': True, 'graceful_timeout_s': 2.0}).get('readyToLaunch'), 'Clean start failed')
        r['config'] = call_dict(c, 'dev_set_player_config',
                {'player': args.player, 'streaming': 'push', 'gsy_engine': args.gsy_engine,
                 'decoding': args.decoding,
                 'mim_direct_mode': 'off'})
        save()
        if args.forward_cache_trial:
            cfg = load_config()
            trial_adb = AdbClient(serial=cfg.device, dev_package=cfg.dev_package,
                                  adb=cfg.adb, aapt=cfg.aapt)
            r['trialEnable'] = trial_adb.dev_control('push_cache_trial', enabled='true')
            require(r['trialEnable'].get('enabled') is True, 'Trial not enabled')
        r['connect'] = call_dict(c, 'dev_connect_server',
                {'address': env.server_address(), 'save': False}, timeout=45)
        connected = True
        wait_automation_ready(c, timeout_s=60)
        identity = state()
        require(identity.get('clientId') == env.device()['client_id'], 'Wrong client')
        ctx = api.resolve_context(identity['clientId'])
        r['scope'] = {'serial': env.device_serial(), 'clientId': identity['clientId'], 'context': ctx}
        cc = api.closed_caption_state(ctx)
        print('STEP old Watch', flush=True)
        r['oldWatch'] = api.watch(ctx, args.old_id)
        save()
        healthy()
        require(api.current_media_file_id(ctx) == args.old_id, 'Wrong old source')
        prior_events = call_dict(c, 'dev_player_events').get('events', [])
        seed_sequence = max((int(e.get('sequence', 0)) for e in prior_events), default=0)
        if args.seed_ms:
            r['seedSeek'] = api.seek(ctx, args.seed_ms)
            deadline = time.monotonic()+35
            while time.monotonic() < deadline:
                s = state()
                if playing(s) and abs(int(s.get('mediaTimeMs', -1))-args.seed_ms) < 12000:
                    break
                time.sleep(.5)
            else:
                raise RuntimeError('Old seed seek did not land')
        else:
            require(int(state().get('mediaTimeMs', 0)) > 60000,
                    'No-seed handoff requires an already nonzero server-owned old-file bookmark')
        r['oldHealthy'] = healthy()
        r['oldState'] = state()
        r['oldEvents'] = call_dict(c, 'dev_player_events')
        old_events = r['oldEvents'].get('events', [])
        r['seekHandoffEvents'] = [e for e in old_events
                if int(e.get('sequence', 0)) > seed_sequence
                and e.get('event') == 'push_stream_handoff']
        require(not r['seekHandoffEvents'], 'Ordinary explicit seek triggered redundant handoff')
        before_watch_sequence = max((int(e.get('sequence', 0)) for e in old_events), default=0)
        save()
        require(r['oldState'].get('health_pushMode') is True, 'Not actual Push')
        if args.forward_cache_trial or args.require_forward_cache:
            require(r['oldState'].get('pushForwardCacheState') not in ('off', '', None),
                    'Old source did not use the trial period')
        print('STEP normal new Watch without STOP or follow-up seek', flush=True)
        start = time.monotonic()
        r['newWatch'] = api.watch(ctx, args.new_id)
        save()
        for _ in range(20):
            r['samples'].append({'elapsedMs': round((time.monotonic()-start)*1000), 'state': state()})
            time.sleep(.5)
        r['newUi'] = api.ui_state(ctx)
        r['newSourceId'] = api.current_media_file_id(ctx)
        r['events'] = call_dict(c, 'dev_player_events')
        r['handoffEvents'] = [e for e in r['events'].get('events', [])
                if int(e.get('sequence', 0)) > before_watch_sequence
                and e.get('event') == 'push_stream_handoff']
        r['fullReloadEvents'] = [e for e in r['events'].get('events', [])
                if int(e.get('sequence', 0)) > before_watch_sequence
                and e.get('event') == 'server_openurl_command']
        r['picture'] = tool_call(c, 'take_screenshot', {'label': out.stem})
        last = r['samples'][-1]
        r['passed'] = (r['newSourceId'] == args.new_id
                and last['state'].get('health_pushMode') is True
                and playing(last['state'])
                and 0 <= int(last['state'].get('mediaTimeMs', -1)) <= last['elapsedMs']+15000)
        if r['fullReloadEvents']:
            r['passed'] = False
            r['qualification'] = 'FULL_RELOAD_NOT_IMPLICIT_HANDOFF'
        elif r['passed'] and len(r['handoffEvents']) != 1:
            r['passed'] = False
            r['qualification'] = 'EXACTLY_ONE_LOCAL_HANDOFF_REQUIRED'
        r['handoffPassed'] = bool(r['passed'])
        if r['passed']:
            r['newHealth'] = healthy()
            r['sustained'] = state()
            if args.forward_cache_trial or args.require_forward_cache:
                require(r['sustained'].get('pushForwardCacheState') not in ('off', '', None)
                        and r['sustained'].get('pushDetailedBufferStats') is True,
                        'New epoch lost its trial source/detailed clock contract')
            if args.exercise_pause or args.exercise_frame_step:
                call_dict(c, 'dev_player_control', {'action': 'pause'})
                deadline = time.monotonic()+12
                while time.monotonic() < deadline:
                    paused = state()
                    if paused.get('state') == 3 and not playing(paused):
                        before = int(paused.get('mediaTimeMs', -1))
                        time.sleep(1.2)
                        paused_after = state()
                        if abs(int(paused_after.get('mediaTimeMs', -100))-before) <= 10:
                            break
                    time.sleep(.2)
                else:
                    raise RuntimeError('Paused native clock did not become stable')
                r['pauseClock'] = {'before': paused, 'after': paused_after}
                if args.exercise_frame_step:
                    r['frameStep'] = call_dict(c, 'dev_frame_step', {'amount': 1})
                    time.sleep(.8)
                    stepped = state()
                    r['frameStepState'] = stepped
                    delta = int(stepped.get('mediaTimeMs', -1))-int(paused_after.get('mediaTimeMs', -1))
                    if args.expect_frame_step_rejected:
                        require(not r['frameStep'].get('accepted') and abs(delta) <= 10
                                and stepped.get('state') == 3 and not playing(stepped),
                                'Unsupported Push step must reject without moving the paused timeline')
                    else:
                        require(0 < delta <= 100 and stepped.get('state') == 3
                                and not playing(stepped), 'Buffered step did not advance one paused frame')
                    rejected = call_dict(c, 'dev_frame_step', {'amount': -1})
                    r['reverseStep'] = rejected
                    require(not rejected.get('accepted'), 'Reverse Push step must remain rejected')
                call_dict(c, 'dev_player_control', {'action': 'play'})
                r['resumedHealth'] = healthy()
                r['resumedState'] = state()
                require(playing(r['resumedState']), 'Pause/resume did not restore advancing output')
        if not r['passed']:
            r['failure'] = r.get('qualification', 'Normal new Watch did not establish near-start advancing Push clock')
        save()
        if args.hold_seconds:
            out.with_suffix('.ready.json').write_text(json.dumps({
                'scopeVerified': True, **r['scope'],
                'expiresAtMs': int((time.time()+args.hold_seconds)*1000)})+'\n')
            print('LIVE_STAGE_READY '+str(out), flush=True)
            time.sleep(args.hold_seconds)
    except Exception as exc:
        r['passed'] = False
        r['failure'] = str(exc)
        try: r['failureState'] = state()
        except Exception as inner: r['stateError'] = str(inner)
    finally:
        for name, action in (
            ('trialDisable', lambda: trial_adb.dev_control('push_cache_trial', enabled='false') if trial_adb else None),
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
        if trial_adb:
            trial_adb.close()
    print(json.dumps({'passed': r['passed'], 'failure': r.get('failure'), 'output': str(out)}), flush=True)
    return 0 if r['passed'] else 1


if __name__ == '__main__':
    raise SystemExit(main())
