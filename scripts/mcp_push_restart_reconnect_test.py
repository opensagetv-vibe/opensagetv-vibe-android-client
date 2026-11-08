#!/usr/bin/env python3
"""Targeted retained-STOP/zero-seek gate; idle commissioned device only.

The public Core MCP bridge owns Watch/Seek. Existing local debug STOP models
the retained decoder seam; the production client owns any reconnect/reload.
This is not a substitute for the natural STV Restart acceptance.
"""
import argparse
import json
from pathlib import Path
import time
from mcp_seek_suite import MCPProcess, initialize, call_dict, tool_call
from sagetv_dev_mcp.config import load_test_environment
from sagetv_dev_mcp.core_mcp_api import CoreMcpApiClient
from android_test_keep_awake import begin, end, state_path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--player', default='media3', choices=['media3','exoplayer','ijkplayer','gsyplayer'])
    parser.add_argument('--install-apk', default='')
    parser.add_argument('--natural-stv-restart', action='store_true',
                        help='Use the existing SageMC EOF StopPopup Restart entry')
    parser.add_argument('--stop-before-eof', action='store_true',
                        help='Natural gate: invoke the STV Stop popup before EOF')
    args = parser.parse_args()
    if args.output.exists():
        parser.error('Use a new output path')
    if args.stop_before_eof and not args.natural_stv_restart:
        parser.error('--stop-before-eof requires --natural-stv-restart')
    r = {'passed': False, 'testKind': 'natural SageMC Restart' if args.natural_stv_restart
         else 'retained debug STOP + stock Seek0 + client recovery'}
    c = MCPProcess()
    checkpointed = connected = False
    serial = None
    api = ctx = original_cc = None
    try:
        initialize(c)
        r['adb'] = call_dict(c, 'adb_connect')
        original = call_dict(c, 'dev_player_state')
        r['before'] = original
        env = load_test_environment()
        if original.get('connected') or original.get('clientId') != env.device()['client_id']:
            raise RuntimeError('Requires the exact commissioned idle disconnected device')
        serial = env.device()['serial']
        r['awake'] = begin(serial, state_path(serial), 'automated')
        r['checkpoint'] = call_dict(c, 'dev_checkpoint_settings')
        checkpointed = True
        if args.install_apk:
            r['install'] = call_dict(c, 'install_dev_apk', {'apk_path': args.install_apk}, timeout=180)
        r['config'] = call_dict(c, 'dev_set_player_config', {'player':args.player,'streaming':'push'})
        tool_call(c, 'launch_dev_app', timeout=45)
        connected = True
        r['connect'] = call_dict(c, 'dev_connect_server', {'address':env.server()['address'],'save':False}, timeout=45)
        api = CoreMcpApiClient.discover(env.server()['address'])
        deadline = time.monotonic() + 30
        while time.monotonic() < deadline:
            s = call_dict(c, 'dev_player_state')
            if s.get('connected') and s.get('menuName'):
                break
            time.sleep(.5)
        ctx = api.resolve_context(original['clientId'])
        original_cc = api.closed_caption_state(ctx)
        r['originalCaptionState'] = original_cc
        r['identity'] = {'clientId':original['clientId'],'context':ctx,'server':env.server()['address']}
        r['watch'] = call_dict(c, 'dev_play_media_file_id', {'media_file_id':66300914,'timeout_s':45}, timeout=90)
        if not r['watch'].get('passed'):
            raise RuntimeError('Initial exact-source Watch did not recover A/V')
        s = call_dict(c, 'dev_player_state')
        if not s.get('health_pushMode'):
            raise RuntimeError('Actual Push was not observed')
        r['tailSeek'] = api.seek(ctx, 3593000 if args.stop_before_eof else 3598352)
        time.sleep(.6)
        r['tail'] = call_dict(c, 'dev_player_state')
        print('Initial Watch and tail seek complete', flush=True)
        if args.natural_stv_restart:
            if args.stop_before_eof:
                r['stvStop'] = api.remote_command(ctx, 'Stop')
            deadline = time.monotonic() + 20
            while time.monotonic() < deadline:
                s = call_dict(c, 'dev_player_state')
                r['stopPopupObservation'] = s
                if s.get('popupName') == 'StopPopup' and s.get('state') in (4, 5):
                    break
                time.sleep(.5)
            else:
                raise RuntimeError('Natural SageMC EOF StopPopup did not appear')
            r['stopped'] = s
            r['beforeRestartPicture'] = tool_call(c, 'take_screenshot',
                {'label':args.output.stem+'-before-restart'})
            # This known SageMC StopPopup defaults to Stop; its next entry is
            # Restart. Never select another popup or change remote mappings.
            r['restartDown'] = api.remote_command(ctx, 'Down')
            r['restartRequest'] = api.remote_command(ctx, 'Select')
            print('SageMC Restart selected', flush=True)
        else:
            r['stop'] = call_dict(c, 'dev_player_control', {'action':'stop'})
            time.sleep(.25)
            r['stopped'] = call_dict(c, 'dev_player_state')
            r['restartRequest'] = api.seek(ctx, 0)
        # The local STOP seam does not change Core's PLAY state. Public Play
        # may therefore be an intentional server no-op. Complete the SAME
        # local seam with debug PLAY; never mistake that no-op for a product
        # failure. A concurrently reloading reader can temporarily have no
        # player; its server-owned PLAY will arrive after OPENURL instead.
        if not args.natural_stv_restart:
            try:
                r['play'] = call_dict(c, 'dev_player_control', {'action':'play'})
            except Exception as exc:
                r['playDuringReload'] = str(exc)
        deadline = time.monotonic() + 20
        while time.monotonic() < deadline:
            s = call_dict(c, 'dev_player_state')
            r['after'] = s
            position = int(s.get('mediaTimeMs', -1))
            if (s.get('health_isPlaying') and s.get('health_surfaceValid')
                    and s.get('health_surfaceShown') and 0 <= position < 15000
                    and (not args.natural_stv_restart or not s.get('popupName'))
                    and int(s.get('health_videoRendered', -1)) > 0
                    and int(s.get('health_audioRendered', -1)) > 0):
                time.sleep(1)
                last = call_dict(c, 'dev_player_state')
                r['last'] = last
                if all(int(last.get(k, -1)) > int(s.get(k, -1))
                       for k in ['health_videoRendered','health_audioRendered']):
                    r['restartPassed'] = True
                    print('Restart output advancing near the beginning', flush=True)
                    break
            time.sleep(.5)
        r['serverAfter'] = api.ui_state(ctx)
        r['events'] = call_dict(c, 'dev_player_events')
        r['picture'] = tool_call(c, 'take_screenshot', {'label':args.output.stem})
        if not r.get('restartPassed'):
            r['diagnostics'] = call_dict(c, 'collect_playback_diagnostics',
                {'label':args.output.stem+'-failure'}, timeout=90)
            raise RuntimeError('Retained zero restart did not recover actual A/V near the beginning')
        r['positiveSeek'] = api.seek(ctx, 120000)
        deadline = time.monotonic() + 20
        while time.monotonic() < deadline:
            s = call_dict(c, 'dev_player_state')
            r['positiveAfter'] = s
            if s.get('health_isPlaying') and 117000 <= int(s.get('mediaTimeMs', -1)) <= 132000:
                r['passed'] = True
                break
            time.sleep(.5)
        if not r['passed']:
            raise RuntimeError('Subsequent ordinary positive seek did not recover')
    except Exception as exc:
        r['failure'] = str(exc)
    finally:
        for name, action in [
            ('restoreCaption', lambda: api.set_closed_caption_state(ctx, original_cc)
             if api and ctx and original_cc is not None else None),
            ('exit', lambda: call_dict(c, 'dev_exit_session', timeout=45) if connected else None),
            ('restore', lambda: call_dict(c, 'dev_restore_settings') if checkpointed else None),
            ('restorePower', lambda: end(serial, state_path(serial), 'automated') if serial else None),
        ]:
            try:
                r[name] = action()
                if name == 'restore' and checkpointed and not r[name].get('restored'):
                    raise RuntimeError('Preference restoration did not verify')
            except Exception as exc:
                r[name+'Failure'] = str(exc)
                r['passed'] = False
        args.output.parent.mkdir(parents=True,exist_ok=True)
        args.output.write_text(json.dumps(r,indent=2)+'\n')
        c.close()
    print(json.dumps({'passed':r['passed'],'failure':r.get('failure'),
        'output':str(args.output),'restartPassed':r.get('restartPassed')},indent=2))
    return 0 if r['passed'] else 1


if __name__ == '__main__':
    raise SystemExit(main())
