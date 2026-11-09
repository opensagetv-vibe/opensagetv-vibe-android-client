#!/usr/bin/env python3
"""EXT-017: measure existing STV rendering before enabling optional GFX capabilities."""
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
    p.add_argument('--renderer', choices=('opengl', 'gdx'), required=True)
    p.add_argument('--output', type=Path, required=True)
    p.add_argument('--hold-seconds', type=int, default=20)
    args = p.parse_args()
    out = args.output.resolve()
    require(out.is_relative_to((ROOT/'artifacts/active/EXT-017').resolve())
            and not out.exists(), 'New explicit EXT-017 output required')
    require(0 <= args.hold_seconds <= 45, 'Bounded capture hold required')
    env = load_test_environment()
    require(env.device_serial() == '192.168.10.25:5555'
            and env.server_address() == '192.168.10.175', 'Non-Pro/stock175 only')
    os.environ['SAGETV_ARTIFACT_DIR'] = str(out.parent)
    out.parent.mkdir(parents=True, exist_ok=True)
    r = {'passed': False, 'renderer': args.renderer, 'serverChanges': False,
         'capabilityChanges': False, 'tunerControls': False, 'samples': []}
    c = MCPProcess()
    checkpoint = connected = awake = False
    api = ctx = cc = None

    def state(): return call_dict(c, 'dev_player_state')

    def capture(label):
        return call_dict(c, 'take_screenshot', {'label': 'ext017-'+args.renderer+'-'+label})

    def measure(label, command):
        before = state()
        start = time.monotonic()
        reply = call_dict(c, 'dev_sage_command', {'command': command})
        # This is control round-trip time, NOT a physical input-to-photon time.
        elapsed = round((time.monotonic()-start)*1000)
        time.sleep(.5)
        after = state()
        r['samples'].append({'label': label, 'command': command, 'reply': reply,
            'controlRoundTripMs': elapsed, 'before': before, 'after': after,
            'dispatchedDelta': int(after.get('connectionGfxDispatchCount', 0))
                - int(before.get('connectionGfxDispatchCount', 0))})

    try:
        initialize(c)
        r['adb'] = call_dict(c, 'adb_connect')
        r['before'] = state()
        require(not r['before'].get('connected')
                and r['before'].get('clientId') == env.device()['client_id'],
                'Requires exact idle disconnected device')
        api = CoreMcpApiClient.discover(env.server_address())
        r['activity'] = api._request('server.activity')
        rows = api._legacy_client().call('GetMediaFiles', 'V', start=0, size=5000,
            fields=('MediaFileID', 'MediaTitle')).get('Result', [])
        require(any(isinstance(row, dict) and row.get('MediaFileID') == 65513439
                    and row.get('MediaTitle') == 'VibeSeekTest-1080i-MPEG2-AC3-CC'
                    for row in rows), 'Existing canonical source identity required')
        r['checkpoint'] = call_dict(c, 'dev_checkpoint_settings')
        require(r['checkpoint'].get('captured'), 'Settings checkpoint failed')
        checkpoint = True
        r['awake'] = begin(env.device_serial(), state_path(env.device_serial()), 'automated')
        awake = True
        require(call_dict(c, 'dev_prepare_clean_start',
            {'wake': True, 'graceful_timeout_s': 2.0}).get('readyToLaunch'), 'Clean start failed')
        r['config'] = call_dict(c, 'dev_set_player_config',
            {'player': 'media3', 'streaming': 'pull', 'mim_direct_mode': 'off'})
        r['connect'] = call_dict(c, 'dev_connect_server',
            {'address': env.server_address(), 'save': False, 'renderer': args.renderer}, timeout=45)
        connected = True
        wait_automation_ready(c, timeout_s=60)
        identity = state()
        require(identity.get('clientId') == env.device()['client_id'], 'Wrong client')
        ctx = api.resolve_context(identity['clientId'])
        r['scope'] = {'serial': env.device_serial(), 'clientId': identity['clientId'], 'context': ctx}
        cc = api.closed_caption_state(ctx)
        r['watch'] = api.watch(ctx, 65513439)
        r['health'] = call_dict(c, 'dev_wait_for_playback_started',
            {'timeout_s': 40, 'verify_ms': 1500, 'expect_video': True, 'expect_audio': True}, timeout=65)
        require(r['health'].get('passed'), 'Actual advancing A/V required')
        r['playing'] = state()
        require(api.current_media_file_id(ctx) == 65513439
                and r['playing'].get('health_pushMode') is False, 'Exact-source Pull required')
        measure('info-open', 'info')
        r['infoCapture'] = capture('info')
        measure('info-toggle', 'info')
        measure('home-preview', 'home')
        r['previewCapture'] = capture('preview')
        for i, command in enumerate(('down', 'down', 'up', 'up')):
            measure('menu-'+str(i), command)
        r['menuCapture'] = capture('menu')
        r['final'] = state()
        if args.hold_seconds:
            ready = out.with_suffix('.ready.json')
            require(not ready.exists(), 'Never reuse a capture marker')
            ready.write_text(json.dumps({'scopeVerified': True, 'serial': env.device_serial(),
                'clientId': identity['clientId'], 'server': env.server_address(),
                'expiresAtMs': int((time.time()+args.hold_seconds)*1000)})+'\n')
            print('HOLD '+str(ready), flush=True)
            time.sleep(args.hold_seconds)
        r['afterHold'] = state()
        require(not r['afterHold'].get('health_playerError'), 'Player failed during UI controls')
        require(int(r['afterHold'].get('health_videoRendered', 0))
                > int(r['playing'].get('health_videoRendered', 0)), 'Video stopped during STV controls')
        r['passed'] = True
    except Exception as exc:
        r['passed'] = False
        r['failure'] = str(exc)
    finally:
        for name, action in (
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
