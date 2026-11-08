#!/usr/bin/env python3
"""Guarded update and bounded same-backend Push diagnostic smoke; no stall injection."""
import argparse
import json
from pathlib import Path
import time
from mcp_seek_suite import MCPProcess, initialize, call_dict, tool_call
from sagetv_dev_mcp.config import load_test_environment
from sagetv_dev_mcp.core_mcp_api import CoreMcpApiClient


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--install-apk', default='')
    parser.add_argument('--media-file-id', type=int)
    parser.add_argument('--seconds', type=int, default=25)
    args = parser.parse_args()
    if args.output.exists() or not 15 <= args.seconds <= 60:
        parser.error('Use a unique output and a bounded15..60s observation')
    report = {'passed': False, 'samples': []}
    client = MCPProcess()
    checkpointed = False
    core = None
    original_ui = None
    original = None
    context = None
    try:
        initialize(client)
        report['adb'] = call_dict(client, 'adb_connect')
        original = call_dict(client, 'dev_player_state')
        report['before'] = original
        env = load_test_environment()
        if original.get('clientId') != env.device().get('client_id'):
            raise RuntimeError('Selected device/client identity mismatch')
        server_address = original.get('serverAddress') or env.server()['address']
        server = env.server_for_address(server_address)
        core = CoreMcpApiClient(server['core_mcp_base_url'], server['core_mcp_token'])
        if original.get('connected'):
            context = core.resolve_context(original['clientId'])
            original_ui = core.ui_state(context)
            report['originalUi'] = original_ui
        report['checkpoint'] = call_dict(client, 'dev_checkpoint_settings')
        checkpointed = True
        if args.install_apk:
            report['install'] = call_dict(client, 'install_dev_apk', {'apk_path': args.install_apk}, timeout=180)
            tool_call(client, 'launch_dev_app', timeout=45)
            report['connect'] = call_dict(client, 'dev_connect_server', {'address': server_address, 'save': False}, timeout=45)
            deadline = time.monotonic() + 30
            while time.monotonic() < deadline:
                state = call_dict(client, 'dev_player_state')
                if state.get('connected') and state.get('menuName'):
                    break
                time.sleep(1)
        context = core.resolve_context(original['clientId'])
        media_id = args.media_file_id or (original_ui or {}).get('media', {}).get('mediaFileId')
        if not media_id:
            raise RuntimeError('Provide an exact known recording ID, never select another UI')
        report['watch'] = call_dict(client, 'dev_play_media_file_id', {'media_file_id': int(media_id), 'timeout_s': 45}, timeout=90)
        if not report['watch'].get('passed'):
            raise RuntimeError('Actual output did not recover after Watch')
        first = call_dict(client, 'dev_player_state')
        started = time.monotonic()
        while time.monotonic() - started < args.seconds:
            state = call_dict(client, 'dev_player_state')
            transport = json.loads(state.get('mediaTransportJson', '{}'))
            if not state.get('health_pushMode') or not transport.get('enabled'):
                raise RuntimeError('Actual Push/enabled transport diagnostics not observed')
            report['samples'].append({'elapsed': time.monotonic()-started,
                                      'transport': transport, 'client': state})
            time.sleep(2)
        last = report['samples'][-1]['client']
        for key in ('health_videoRendered', 'health_audioRendered'):
            if int(last.get(key, -1)) <= int(first.get(key, -1)):
                raise RuntimeError(f'Actual output did not advance: {key}')
        elapsed = report['samples'][-1]['elapsed']
        if abs(int(last['mediaTimeMs']) - int(first['mediaTimeMs']) - elapsed*1000) > 3000:
            raise RuntimeError('Playback clock did not advance at normal speed')
        a, b = report['samples'][0]['transport'], report['samples'][-1]['transport']
        for key in ('headerCount', 'dataPushCount', 'replyCount', 'payloadBytes'):
            if int(b.get(key, 0)) <= int(a.get(key, 0)):
                raise RuntimeError(f'No observed transport progress: {key}')
        if b.get('failureCount', 0) != a.get('failureCount', 0):
            raise RuntimeError('New media-socket failure during observation')
        report['picture'] = tool_call(client, 'take_screenshot', {'label': args.output.stem+'-playing'})
        report['passed'] = True
        print('PASS: actual Push, A/V/clock and packet/reply counters advance', flush=True)
    except Exception as exc:
        report['failure'] = str(exc)
        print('FAIL:', exc, flush=True)
    finally:
        try:
            if checkpointed:
                report['restore'] = call_dict(client, 'dev_restore_settings')
                if not report['restore'].get('restored'):
                    raise RuntimeError('Private settings were not restored')
                if original_ui and original_ui.get('media', {}).get('mediaFileId'):
                    # A public Seek ACK is not restoration proof. Protect an
                    # externally changed source, then observe real playback at
                    # the saved recording-relative target after update/reload.
                    if core.current_media_file_id(context) != int(original_ui['media']['mediaFileId']):
                        raise RuntimeError('Source changed externally; refuse restoration seek')
                    target = int(original_ui['mediaTimeMs']) - int(original_ui['seekStartMs'])
                    report['restoreSeek'] = core.seek(context, target)
                    deadline = time.monotonic() + 25
                    restored = False
                    while time.monotonic() < deadline:
                        s = call_dict(client, 'dev_player_state')
                        report['restoreObservation'] = s
                        if (s.get('health_isPlaying') and s.get('health_surfaceValid')
                                and abs(int(s.get('mediaTimeMs', -1))-target) < 8000):
                            time.sleep(1.5)
                            last = call_dict(client, 'dev_player_state')
                            report['restoreLast'] = last
                            if (all(int(last.get(k, -1)) > int(s.get(k, -1))
                                    for k in ('health_videoRendered', 'health_audioRendered'))
                                    and last.get('audioOffsetMs') == original.get('audioOffsetMs')):
                                restored = True
                                break
                        time.sleep(.4)
                    if not restored:
                        raise RuntimeError('Saved position/A/V/audio offset did not restore')
                    if original.get('health_isPlaying') is False:
                        report['restorePause'] = core.media_control(context, 'pause')
                        deadline = time.monotonic() + 10
                        while time.monotonic() < deadline:
                            paused = call_dict(client, 'dev_player_state')
                            if paused.get('health_isPlaying') is False:
                                report['restoredPaused'] = True
                                break
                            time.sleep(.4)
                        if not report.get('restoredPaused'):
                            raise RuntimeError('Original pause state did not restore')
                    report['restoredPlayback'] = True
                elif original and not original.get('connected'):
                    report['exit'] = call_dict(client, 'dev_exit_session', timeout=45)
        except Exception as exc:
            report['restorationFailure'] = str(exc)
            report['passed'] = False
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(report, indent=2)+'\n', encoding='utf-8')
        client.close()
    return 0 if report['passed'] else 1


if __name__ == '__main__':
    raise SystemExit(main())
