#!/usr/bin/env python3
"""Bounded STOP/FLUSH/PLAY retained-decoder regression on an idle test device.

The existing debug API models decoder STOP; the real stock Seek supplies FLUSH.
This is a lifecycle seam test, not proof of every STV restart/EOF sequence.
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
    parser.add_argument('--player', choices=['media3', 'exoplayer', 'gsyplayer'], default='media3')
    parser.add_argument('--install-apk', default='')
    args = parser.parse_args()
    if args.output.exists():
        parser.error('Use a new output path')
    report = {'passed': False, 'testKind': 'debug STOP + public stock Seek/FLUSH + debug PLAY'}
    client = MCPProcess()
    checkpointed = connected = False
    serial = None
    try:
        initialize(client)
        report['adb'] = call_dict(client, 'adb_connect')
        before = call_dict(client, 'dev_player_state')
        report['before'] = before
        env = load_test_environment()
        if before.get('connected') or before.get('clientId') != env.device()['client_id']:
            raise RuntimeError('Requires selected idle disconnected device with exact identity')
        serial = env.device()['serial']
        report['keepAwake'] = begin(serial, state_path(serial), 'automated')
        report['checkpoint'] = call_dict(client, 'dev_checkpoint_settings')
        checkpointed = True
        if args.install_apk:
            report['install'] = call_dict(client, 'install_dev_apk',
                {'apk_path': args.install_apk}, timeout=180)
        report['config'] = call_dict(client, 'dev_set_player_config',
            {'player': args.player, 'streaming': 'push'})
        tool_call(client, 'launch_dev_app', {}, timeout=45)
        connected = True
        report['connect'] = call_dict(client, 'dev_connect_server',
            {'address': env.server()['address'], 'save': False}, timeout=45)
        core = CoreMcpApiClient.discover(env.server()['address'])
        deadline = time.monotonic() + 30
        context = None
        while time.monotonic() < deadline:
            state = call_dict(client, 'dev_player_state')
            if state.get('connected') and state.get('menuName'):
                context = core.resolve_context(before['clientId'])
                break
            time.sleep(1)
        if not context:
            raise RuntimeError('Exact UI did not register')
        report['identity'] = {'clientId': before['clientId'], 'context': context,
                              'server': env.server()['address']}
        report['watch'] = call_dict(client, 'dev_play_media_file_id',
            {'media_file_id': 66300914, 'timeout_s': 45}, timeout=90)
        if not report['watch'].get('passed'):
            raise RuntimeError('Initial Watch failed')
        state = call_dict(client, 'dev_player_state')
        if not state.get('health_pushMode'):
            raise RuntimeError('Actual transport is not Push')
        report['stop'] = call_dict(client, 'dev_player_control', {'action': 'stop'})
        time.sleep(1)
        report['stopped'] = call_dict(client, 'dev_player_state')
        if report['stopped'].get('health_surfaceValid'):
            raise RuntimeError('STOP-hidden surface precondition not observed')
        report['seek'] = core.seek(context, 60000)
        time.sleep(1)
        report['flushed'] = call_dict(client, 'dev_player_state')
        if report['flushed'].get('health_playbackState') not in (2, 3):
            raise RuntimeError('FLUSH did not prepare retained decoder before PLAY')
        report['play'] = call_dict(client, 'dev_player_control', {'action': 'play'})
        deadline = time.monotonic() + 20
        while time.monotonic() < deadline:
            state = call_dict(client, 'dev_player_state')
            report['after'] = state
            if (state.get('health_surfaceValid') and state.get('health_surfaceShown')
                    and state.get('health_isPlaying')
                    and int(state.get('health_videoRendered', -1)) > 0
                    and int(state.get('health_audioRendered', -1)) > 0):
                time.sleep(1)
                last = call_dict(client, 'dev_player_state')
                report['last'] = last
                if all(int(last.get(k, -1)) > int(state.get(k, -1))
                       for k in ['health_videoRendered', 'health_audioRendered']):
                    report['passed'] = True
                    break
            time.sleep(.5)
        report['events'] = call_dict(client, 'dev_player_events')
        report['picture'] = tool_call(client, 'take_screenshot', {'label': args.output.stem})
        if not report['passed']:
            raise RuntimeError('PLAY left hidden/no-video output after retained-player FLUSH')
    except Exception as exc:
        report['failure'] = str(exc)
        print('FAIL:', exc, flush=True)
    finally:
        for name, action in [
            ('exit', lambda: call_dict(client, 'dev_exit_session', timeout=45) if connected else None),
            ('restore', lambda: call_dict(client, 'dev_restore_settings') if checkpointed else None),
            ('restorePower', lambda: end(serial, state_path(serial), 'automated') if serial else None),
        ]:
            try:
                report[name] = action()
                if name == 'restore' and checkpointed and not report[name].get('restored'):
                    raise RuntimeError('Preference restoration did not verify')
            except Exception as exc:
                report[name + 'Failure'] = str(exc)
                report['passed'] = False
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(report, indent=2) + '\n')
        client.close()
    print('PASS' if report['passed'] else 'FAILED', flush=True)
    return 0 if report['passed'] else 1


if __name__ == '__main__':
    raise SystemExit(main())
