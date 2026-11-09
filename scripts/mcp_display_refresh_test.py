#!/usr/bin/env python3
"""EXT-015: optional Android display-mode ownership on a real PAL server source."""
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
    p.add_argument('--output', type=Path, required=True)
    p.add_argument('--install-apk', default='')
    p.add_argument('--failure-hold-seconds', type=int, default=0)
    args = p.parse_args()
    require(0 <= args.failure_hold_seconds <= 30, 'Bounded failure capture hold required')
    out = args.output.resolve()
    require(out.is_relative_to((ROOT/'artifacts/active/EXT-015').resolve())
            and not out.exists(), 'New explicit EXT-015 output required')
    env = load_test_environment()
    require(env.device_serial() == '192.168.10.25:5555'
            and env.server_address() == '192.168.10.175', 'Non-Pro/stock175 only')
    fixture = next((f for f in env.fixture_cases() if f['id'] == 'decoded_field_ids_pal'), None)
    require(fixture and fixture.get('enabled'), 'Enabled canonical PAL fixture required')
    os.environ['SAGETV_ARTIFACT_DIR'] = str(out.parent)
    out.parent.mkdir(parents=True, exist_ok=True)
    r = {'passed': False, 'serverChanges': False, 'tunerControls': False,
         'savedRefreshPreferenceChanged': False, 'fixture': fixture['path']}
    c = MCPProcess()
    checkpoint = awake = connected = display_touched = False
    api = ctx = cc = None

    def refresh(policy):
        result = call_dict(c, 'dev_display_refresh', {'policy': policy})
        require(result.get('ok'), 'Refresh request failed: '+str(result))
        return result

    def healthy(phase):
        result = call_dict(c, 'dev_wait_for_playback_started',
            {'timeout_s': 12, 'verify_ms': 1500, 'expect_video': True, 'expect_audio': True}, timeout=30)
        r['lastHealth'] = result
        if not result.get('passed'):
            state = call_dict(c, 'dev_player_state')
            events = call_dict(c, 'dev_player_events').get('events', [])
            mode_name = 'display_refresh_apply' if phase == 'switch' else 'display_refresh_restore'
            modes = [e for e in events if e.get('event') == mode_name]
            mode = modes[-1] if modes else {}
            mode_time = int(mode.get('monotonicMs', -1))
            platform = [e for e in events if e.get('event') == 'media_session_pause'
                        and mode_time >= 0 and mode_time <= int(e.get('monotonicMs', -1)) <= mode_time+1000]
            require(int(state.get('state', -1)) == 3 and state.get('health_surfaceValid') is True
                    and not state.get('health_playerError') and platform
                    and int(state.get('inputEventSequence', -1)) == int(identity.get('inputEventSequence', -2)),
                    'A/V continuity failed without a proven mode-adjacent platform PAUSE')
            # This is an explicit TEST Play, not production auto-resume. The
            # stock PAUSE was honored; never mask a codec failure or silently
            # discard an indistinguishable user/voice MediaSession request.
            witness = {'phase': phase, 'modeEvent': mode, 'pauseEvents': platform,
                       'state': state, 'explicitTestPlay': api.media_control(ctx, 'play')}
            r.setdefault('platformPauses', []).append(witness)
            result = call_dict(c, 'dev_wait_for_playback_started',
                {'timeout_s': 12, 'verify_ms': 1500, 'expect_video': True, 'expect_audio': True}, timeout=30)
        require(result.get('passed'), 'A/V must advance after mode policy changes')
        return result

    try:
        initialize(c)
        r['adb'] = call_dict(c, 'adb_connect')
        before = call_dict(c, 'dev_player_state')
        require(not before.get('connected') and before.get('clientId') == env.device()['client_id'],
                'Requires exact idle disconnected device')
        r['checkpoint'] = call_dict(c, 'dev_checkpoint_settings')
        require(r['checkpoint'].get('captured'), 'Settings checkpoint failed')
        checkpoint = True
        r['awake'] = begin(env.device_serial(), state_path(env.device_serial()), 'automated')
        awake = True
        if args.install_apk:
            r['install'] = call_dict(c, 'install_dev_apk', {'apk_path': args.install_apk}, timeout=200)
            require('Success' in str(r['install'].get('result', '')), 'Guarded install failed')
        require(call_dict(c, 'dev_prepare_clean_start',
            {'wake': True, 'graceful_timeout_s': 2.0}).get('readyToLaunch'), 'Clean start failed')
        r['config'] = call_dict(c, 'dev_set_player_config',
            {'player': 'media3', 'streaming': 'pull', 'mim_direct_mode': 'off',
             'video_deinterlace_mode': 'off'})
        r['connect'] = call_dict(c, 'dev_connect_server',
            {'address': env.server_address(), 'save': False}, timeout=45)
        connected = True
        wait_automation_ready(c, timeout_s=60)
        identity = call_dict(c, 'dev_player_state')
        require(identity.get('clientId') == env.device()['client_id'], 'Wrong client')
        api = CoreMcpApiClient.discover(env.server_address())
        ctx = api.resolve_context(identity['clientId'])
        r['scope'] = {'serial': env.device_serial(), 'clientId': identity['clientId'], 'context': ctx}
        cc = api.closed_caption_state(ctx)
        r['watch'] = call_dict(c, 'dev_play_server_path',
            {'server_path': fixture['path'], 'timeout_s': 45, 'verify_ms': 1500,
             'restart_from_beginning': True}, timeout=80)
        require(r['watch'].get('passed'), 'Exact canonical PAL playback failed')
        r['baseline'] = refresh('inspect')
        baseline = int(r['baseline']['preferredModeId'])
        r['initialOff'] = refresh('off')
        require(int(r['initialOff']['preferredModeId']) == baseline,
                'Off without ownership changed the Window preference')
        display_touched = True
        r['apply'] = refresh('always')
        if r['apply'].get('applied'):
            target = int(r['apply']['preferredModeId'])
            deadline = time.monotonic()+12
            while True:
                actual = refresh('inspect')
                if int(actual['currentModeId']) == target: break
                require(time.monotonic() < deadline, 'Android never selected the reported target')
                time.sleep(.5)
            r['selected'] = actual
            r['switchStatus'] = 'PHYSICAL_MODE_SELECTED'
            r['switchedHealth'] = healthy('switch')
        else:
            r['switchStatus'] = 'NO_SWITCH: '+str(r['apply'].get('reason'))
            require(r['apply'].get('reason') in ('no_compatible_mode_at_current_resolution',
                    'current_mode_matches'), 'Unexpected display policy refusal')
        r['restoreMode'] = refresh('off')
        require(int(r['restoreMode']['preferredModeId']) == baseline,
                'Original preferred display mode, including Auto0, was not restored')
        r['secondOff'] = refresh('off')
        require(int(r['secondOff']['preferredModeId']) == baseline,
                'Repeated Off overwrote the already-restored preference')
        display_touched = False
        r['finalHealth'] = healthy('restore')
        r['final'] = call_dict(c, 'dev_player_state')
        r['passed'] = True
    except Exception as exc:
        r['passed'] = False
        r['failure'] = str(exc)
        if connected:
            try:
                r['failureState'] = call_dict(c, 'dev_player_state')
                r['failureEvents'] = call_dict(c, 'dev_player_events')
                r['failureCapture'] = call_dict(c, 'take_screenshot', {'label': 'ext015-mode-failure'})
                if args.failure_hold_seconds:
                    out.with_suffix('.ready.json').write_text(json.dumps({
                        'scopeVerified': True, 'stage': 'FAILED display policy continuity',
                        'serial': env.device_serial(), 'clientId': env.device()['client_id'],
                        'expiresAtMs': int((time.time()+args.failure_hold_seconds)*1000)})+'\n')
                    print('FAILURE_HOLD', flush=True)
                    time.sleep(args.failure_hold_seconds)
            except Exception as detail:
                r['failureCaptureError'] = str(detail)
    finally:
        for name, action in (
            ('emergencyModeRestore', lambda: refresh('off') if display_touched else None),
            ('captionRestore', lambda: api.set_closed_caption_state(ctx, cc)
             if api and ctx and cc is not None else None),
            ('exit', lambda: call_dict(c, 'dev_exit_session', {'stop_app': True}, timeout=45)
             if connected else None),
            ('restore', lambda: call_dict(c, 'dev_restore_settings', timeout=100)
             if checkpoint else None),
            ('powerRestore', lambda: end(env.device_serial(), state_path(env.device_serial()), 'automated')
             if awake else None)):
            try:
                r[name] = action()
                if name == 'restore' and checkpoint:
                    require(r[name].get('restored'), 'Settings restoration failed')
            except Exception as exc:
                r['passed'] = False
                r[name+'Error'] = str(exc)
        out.write_text(json.dumps(r, indent=2)+'\n')
        c.close()
    print(json.dumps({'passed': r['passed'], 'failure': r.get('failure'), 'output': str(out)}))
    return 0 if r['passed'] else 1


if __name__ == '__main__': raise SystemExit(main())
