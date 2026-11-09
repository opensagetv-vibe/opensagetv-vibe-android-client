#!/usr/bin/env python3
"""Qualify actual native Push frame output, including isolated EXT-018 hardware trials."""
import argparse
import json
import os
from pathlib import Path
import time
from android_test_keep_awake import begin, end, state_path
from mcp_lifecycle_test import require, wait_automation_ready
from mcp_seek_suite import MCPProcess, initialize, call_dict
from sagetv_dev_mcp.config import load_config, load_test_environment
from sagetv_dev_mcp.adb import AdbClient
from sagetv_dev_mcp.core_mcp_api import CoreMcpApiClient

ROOT = Path(__file__).resolve().parents[1]


def presented_clock_error(before, after):
    """Retained native counters need delta comparison, not a guessed zero origin."""
    if ('originKnown=true' in str(before.get('pushNativeClock', '')) and
            'originKnown=true' in str(after.get('pushNativeClock', ''))):
        return ('known-origin', int(after['mediaTimeMs']) -
                int(after['pushEffectiveServerAnchorMs']) - int(after['nativeStepPositionMs']))
    return ('retained-delta', (int(after['mediaTimeMs'])-int(before['mediaTimeMs'])) -
            (int(after['nativeStepPositionMs'])-int(before['health_playerPositionMs'])))


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--streaming', choices=('dynamic', 'fixed'), default='dynamic')
    p.add_argument('--task-id', choices=('EXT-016', 'EXT-018'), default='EXT-016')
    p.add_argument('--decoding', choices=('software', 'hardware'), default='software')
    p.add_argument('--media-file-id', type=int, choices=(65513439, 65500395), default=65513439,
                   help='Exact commissioned generated-clock or UK H264 recording, never a tuner')
    p.add_argument('--exercise-seek-epoch', action='store_true',
                   help='Public producer seek once, verify new output epoch and repeat paused stepping')
    p.add_argument('--install-apk', default='')
    p.add_argument('--step-input', choices=('command28', 'pause-key'), default='command28')
    p.add_argument('--capture-ready-file', type=Path)
    p.add_argument('--output', type=Path, required=True)
    args = p.parse_args()
    out = args.output.resolve()
    require(out.is_relative_to((ROOT/'artifacts/active'/args.task_id).resolve()) and not out.exists(),
            'New explicit stable-task output required')
    require(args.decoding != 'hardware' or args.task_id == 'EXT-018',
            'Hardware trial requires its own EXT-018 evidence')
    env = load_test_environment()
    require(env.device_serial() == '192.168.10.25:5555' and
            env.server_address() == '192.168.10.175', 'Exact non-Pro/stock175 required')
    out.parent.mkdir(parents=True, exist_ok=True)
    ready = args.capture_ready_file.resolve() if args.capture_ready_file else None
    require(ready is None or (ready.parent == out.parent and not ready.exists()),
            'New readiness marker must share owning task directory')
    os.environ['SAGETV_ARTIFACT_DIR'] = str(out.parent)
    r = {'passed': False, 'rows': [], 'streaming': args.streaming, 'stepInput': args.step_input,
         'taskId': args.task_id, 'decoding': args.decoding, 'mediaFileId': args.media_file_id,
         'exerciseSeekEpoch': args.exercise_seek_epoch,
         'serverChanges': False,
         'producerSeeksDuringStep': False, 'tunerControls': False}
    c = MCPProcess()
    api = ctx = cc = trial_adb = None
    checkpoint = awake = connected = opened = False

    def state():
        s = call_dict(c, 'dev_player_state')
        if connected:
            require(s.get('clientId') == env.device()['client_id'] and
                    s.get('serverAddress') == env.server_address(), 'Client/server identity changed')
            require(api.ui_state(ctx).get('media', {}).get('mediaFileId') == args.media_file_id,
                    'Fixture changed; no further control')
        return s

    def step_one(index, phase):
        before = state()
        row = {'index': index, 'phase': phase, 'before': before}
        r['rows'].append(row)
        row['request'] = (call_dict(c, 'dev_sage_command', {'command': 'pause'})
                if args.step_input == 'pause-key' else
                call_dict(c, 'dev_frame_step', {'amount': 1}))
        if args.step_input == 'command28':
            require(row['request'].get('accepted'), 'Native step request rejected')
        deadline = time.monotonic()+5
        while time.monotonic() < deadline:
            after = state()
            if int(after.get('nativeStepCompleted', 0)) > int(before.get('nativeStepCompleted', 0)):
                break
            time.sleep(.1)
        row['after'] = after
        require(int(after.get('nativeStepCompleted', 0)) == int(before.get('nativeStepCompleted', 0))+1,
                'No single actually-presented frame completion')
        require(after.get('state') == 3 and not after.get('health_basicIsPlaying'),
                'Stepping resumed playback/audio')
        # After the actual presented-frame callback, native position is
        # video PTS rather than the paused audio-master estimate. This is
        # the authoritative comparison, plus independent burned frame IDs.
        row['clockComparison'], row['presentedClockErrorMs'] = presented_clock_error(before, after)
        require(abs(row['presentedClockErrorMs']) <= 100,
                'Actual presented-frame clock and logical timeline disagree')
        # First step can switch audio-master paused position to actual
        # displayed video PTS; subsequent steps must move one real frame.
        if index:
            delta = int(after['nativeStepPositionMs']) - int(before['nativeStepPositionMs'])
            require(10 <= delta <= 70, 'Not a single29.97fps native frame')
            row['nativeFrameDeltaMs'] = delta
        require(after.get('serverFlushSequence') == before.get('serverFlushSequence') and
                after.get('serverSeekSequence') == before.get('serverSeekSequence'),
                'Stepping unexpectedly sought/flushed the producer')
        row['image'] = call_dict(c, 'take_screenshot', {'label': f'{args.task_id.lower()}-{phase}-frame-{index+1}'})
        time.sleep(2)
        held = state()
        require(abs(int(held['mediaTimeMs'])-int(after['mediaTimeMs'])) <= 5,
                'Paused presentation clock did not hold')
        row['held'] = held
        row['passed'] = True
        out.write_text(json.dumps(r, indent=2)+'\n')
        print(json.dumps({'stage': 'frame-presented', 'phase': phase, 'index': index,
                          'clockComparison': row['clockComparison'],
                          'clockErrorMs': row['presentedClockErrorMs']}), flush=True)

    try:
        initialize(c)
        r['adb'] = call_dict(c, 'adb_connect')
        r['before'] = state()
        require(not r['before'].get('connected') and
                r['before'].get('clientId') == env.device()['client_id'], 'Exact idle client required')
        api = CoreMcpApiClient.discover(env.server_address())
        r['activity'] = api._request('server.activity')
        r['checkpoint'] = call_dict(c, 'dev_checkpoint_settings')
        require(r['checkpoint'].get('captured'), 'Checkpoint failed')
        checkpoint = True
        r['awake'] = begin(env.device_serial(), state_path(env.device_serial()), 'automated')
        awake = True
        if args.install_apk:
            r['install'] = call_dict(c, 'install_dev_apk', {'apk_path': args.install_apk}, timeout=200)
            require('Success' in str(r['install'].get('result', '')), 'Guarded APK install failed')
        require(call_dict(c, 'dev_prepare_clean_start', {'wake': True,
                'graceful_timeout_s': 2}).get('readyToLaunch'), 'Clean start failed')
        config = {'player': 'ijkplayer', 'decoding': args.decoding, 'streaming': args.streaming,
                  'mim_direct_mode': 'off'}
        if args.streaming == 'fixed':
            config.update(fixed_encoding_preference='always', fixed_remuxing_preference='off',
                          fixed_encoding_format='mpegts', fixed_video_resolution='d1',
                          fixed_video_bitrate_kbps=1000, fixed_audio_codec='aac', fixed_audio_channels='2')
        r['config'] = call_dict(c, 'dev_set_player_config', config)
        r['connect'] = call_dict(c, 'dev_connect_server', {'address': env.server_address(), 'save': False})
        opened = True
        wait_automation_ready(c)
        identity = call_dict(c, 'dev_player_state')
        require(identity.get('clientId') == env.device()['client_id'], 'Wrong connected client')
        ctx = api.resolve_context(identity['clientId'])
        cc = api.closed_caption_state(ctx)
        if args.decoding == 'hardware':
            cfg = load_config()
            trial_adb = AdbClient(serial=cfg.device, dev_package=cfg.dev_package,
                                  adb=cfg.adb, aapt=cfg.aapt)
            r['trialEnable'] = trial_adb.dev_control('hardware_step_trial', enabled='true')
            require(r['trialEnable'].get('enabled') is True, 'Hardware trial not enabled')
        r['watch'] = api.watch(ctx, args.media_file_id)
        connected = True
        print(json.dumps({'stage': 'watch-requested', 'mediaFileId': args.media_file_id}), flush=True)
        r['startup'] = call_dict(c, 'dev_wait_for_playback_started', {'timeout_s': 60,
                'verify_ms': 1500, 'expect_video': True, 'expect_audio': True}, timeout=85)
        require(r['startup'].get('passed'), 'Actual A/V startup failed')
        active = state()
        require(active.get('streaming') == args.streaming and active.get('health_pushMode') is True
                and active.get('decoding') == args.decoding, 'Requested Push decoder policy required')
        if args.decoding == 'hardware':
            require(str(active.get('health_videoDecoderKind', '')).lower() == 'hardware',
                    'Actual hardware decoder required; preference is insufficient')
        r['activeDecoder'] = active
        r['pause'] = api.media_control(ctx, 'pause')
        deadline = time.monotonic()+10
        while time.monotonic() < deadline:
            paused = state()
            if paused.get('state') == 3: break
            time.sleep(.2)
        require(paused.get('state') == 3, 'Real pause failed')
        time.sleep(1)
        r['beforeStep'] = state()
        r['nativeClockAligned'] = abs(int(r['beforeStep']['mediaTimeMs']) -
                (int(r['beforeStep']['pushEffectiveServerAnchorMs']) +
                 int(r['beforeStep']['health_playerPositionMs']))) <= 150
        r['beforeImage'] = call_dict(c, 'take_screenshot', {'label': args.task_id.lower()+'-before-step'})
        for index in range(3):
            step_one(index, 'initial')
        r['reverseRejected'] = call_dict(c, 'dev_frame_step', {'amount': -1})
        require(not r['reverseRejected'].get('accepted'), 'Unsupported reverse step accepted')
        r['play'] = api.media_control(ctx, 'play')
        r['resumed'] = call_dict(c, 'dev_wait_for_playback_started', {'timeout_s': 25,
                'verify_ms': 1500, 'expect_video': True, 'expect_audio': True}, timeout=50)
        require(r['resumed'].get('passed'), 'A/V did not resume')
        r['playingRejected'] = call_dict(c, 'dev_frame_step', {'amount': 1})
        require(not r['playingRejected'].get('accepted'), 'Playing step accepted')
        if args.exercise_seek_epoch:
            r['beforeProducerSeek'] = state()
            print(json.dumps({'stage': 'producer-seek', 'targetMs': 150000}), flush=True)
            r['producerSeek'] = api.seek(ctx, 150000)  # one public request, no replay
            r['afterProducerStartup'] = call_dict(c, 'dev_wait_for_playback_started',
                    {'timeout_s': 60, 'verify_ms': 1500, 'expect_video': True,
                     'expect_audio': True}, timeout=85)
            require(r['afterProducerStartup'].get('passed'), 'A/V after producer seek failed')
            r['newEpoch'] = state()
            require(int(r['newEpoch'].get('serverFlushSequence', 0)) >
                    int(r['beforeProducerSeek'].get('serverFlushSequence', 0)),
                    'No producer FLUSH observed')
            ui = api.ui_state(ctx)
            r['producerSeekUi'] = ui
            require(abs(int(ui.get('mediaTimeMs', 0))-int(ui.get('seekStartMs', 0))-150000) < 10000,
                    'Producer seek did not reach requested file region')
            require('presented=true' in str(r['newEpoch'].get('pushPausedZeroWitness', '')),
                    'No actual video proof for replacement native epoch')
            r['secondPause'] = api.media_control(ctx, 'pause')
            deadline = time.monotonic()+10
            while time.monotonic() < deadline:
                if state().get('state') == 3: break
                time.sleep(.2)
            require(state().get('state') == 3, 'Second pause failed')
            time.sleep(1)
            r['replacementBeforeImage'] = call_dict(c, 'take_screenshot',
                    {'label': args.task_id.lower()+'-replacement-before'})
            for index in range(3):
                step_one(index, 'replacement')
            r['replacementHeldUi'] = api.ui_state(ctx)
            if args.decoding == 'hardware' and args.streaming == 'fixed':
                require('originKnown=true' in str(state().get('pushNativeClock', '')),
                        'Normalized hardware replacement retained an unknown native origin')
            r['secondPlay'] = api.media_control(ctx, 'play')
            r['secondResume'] = call_dict(c, 'dev_wait_for_playback_started',
                    {'timeout_s': 25, 'verify_ms': 1500, 'expect_video': True,
                     'expect_audio': True}, timeout=50)
            require(r['secondResume'].get('passed'), 'A/V after replacement stepping failed')
        r['final'] = state()
        if ready:
            require(r['final'].get('state') == 2 and
                    r['final'].get('health_firstVideoFrameRendered') is True,
                    'No verified live output for independent capture')
            ready.write_text(json.dumps({'scopeVerified': True,
                'expiresAtMs': int(time.time()*1000)+30000, 'stage': 'native-step-resumed',
                'serial': env.device_serial(), 'server': env.server_address(),
                'clientId': env.device()['client_id'], 'mediaFileId': args.media_file_id})+'\n')
            time.sleep(25)
        r['events'] = call_dict(c, 'dev_player_events')
        r['passed'] = True
    except Exception as exc:
        r['failure'] = str(exc)
        try: r['failureState'] = state()
        except Exception as inner: r['snapshotError'] = str(inner)
    finally:
        for name, action in (
            ('trialDisable', lambda: trial_adb.dev_control('hardware_step_trial', enabled='false') if trial_adb else None),
            ('captionRestore', lambda: api.set_closed_caption_state(ctx, cc) if ctx and cc is not None else None),
            ('exit', lambda: call_dict(c, 'dev_exit_session', {'stop_app': True}, timeout=45) if opened else None),
            ('restore', lambda: call_dict(c, 'dev_restore_settings', timeout=100) if checkpoint else None),
            ('powerRestore', lambda: end(env.device_serial(), state_path(env.device_serial()), 'automated') if awake else None)):
            try:
                r[name] = action()
                if name == 'restore' and checkpoint: require(r[name].get('restored'), 'Settings restore failed')
            except Exception as exc:
                r['passed'] = False
                r[name+'Error'] = str(exc)
        out.write_text(json.dumps(r, indent=2)+'\n')
        if trial_adb: trial_adb.close()
        c.close()
    print(json.dumps({'passed': r['passed'], 'failure': r.get('failure'), 'output': str(out)}))
    return 0 if r['passed'] else 1


if __name__ == '__main__': raise SystemExit(main())
