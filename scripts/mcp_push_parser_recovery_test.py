#!/usr/bin/env python3
"""ISSUE-004: explicit single-fault recovery on one commissioned stock-server UI.

Synthetic fatal parser errors exercise real player callbacks, not the original
field timestamp trigger. All app preferences and test awake settings are restored.
"""
import argparse
import hashlib
import json
from pathlib import Path
import time

from mcp_seek_suite import MCPProcess, initialize, call_dict
from sagetv_dev_mcp.config import load_test_environment
from sagetv_dev_mcp.core_mcp_api import CoreMcpApiClient
from android_test_keep_awake import begin, end, state_path
from mcp_ui_roots import wait_automation_root


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--install-apk', default='')
    parser.add_argument('--players', default='exoplayer,media3,gsy_media3,gsy_legacy')
    parser.add_argument('--modes', default='push,pull')
    parser.add_argument('--paused-fault', action='store_true')
    parser.add_argument('--forward-cache', action='store_true')
    parser.add_argument('--interrupt-selected-session', action='store_true')
    parser.add_argument('--server-path', default='/var/media/OpenSageTV_Vibe_Tests/VibeSeekTest-1080i-MPEG2-AC3-CC.ts')
    args = parser.parse_args()
    if args.output.exists():
        parser.error('Use a unique output path')
    players = args.players.split(',')
    modes = args.modes.split(',')
    if any(mode not in ('push', 'pull') for mode in modes):
        parser.error('unsupported transport')
    if any(p not in ('exoplayer', 'media3', 'gsy_media3', 'gsy_legacy') for p in players):
        parser.error('unsupported Exo delegate')
    report = {'passed': False, 'kind': 'explicit synthetic parser fault, not field reproduction', 'cases': []}
    client = MCPProcess()
    checkpointed = awake = touched = False
    serial = None

    def save():
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(report, indent=2) + '\n')

    def state():
        return call_dict(client, 'dev_player_state')

    def advancing(timeout=30):
        deadline = time.monotonic() + timeout
        previous = None
        while time.monotonic() < deadline:
            now = state()
            if (now.get('health_playbackState') == 3 and now.get('health_isPlaying')
                    and previous and all(int(now.get(k, -1)) > int(previous.get(k, -1))
                        for k in ('health_videoRendered', 'health_audioRendered'))):
                return {'first': previous, 'last': now}
            previous = now
            time.sleep(1)
        raise RuntimeError('A/V did not recover/advance: ' + json.dumps(now))

    try:
        initialize(client)
        env = load_test_environment()
        serial = env.device()['serial']
        report['adb'] = call_dict(client, 'adb_connect')
        before = report['before'] = state()
        if before.get('clientId') != env.device()['client_id']:
            raise RuntimeError('Exact commissioned device identity mismatch')
        if before.get('connected') and not args.interrupt_selected_session:
            raise RuntimeError('Selected client is connected; explicit test-session interruption required')
        core = CoreMcpApiClient.discover(env.server()['address'])
        report['activityBefore'] = core._request('server.activity')
        media = report['fixture'] = core.resolve_exact_path(args.server_path)
        report['checkpoint'] = call_dict(client, 'dev_checkpoint_settings')
        checkpointed = True
        report['keepAwake'] = begin(serial, state_path(serial), 'automated')
        awake = True
        report['exitBefore'] = call_dict(client, 'dev_exit_session', {'stop_app': True}, timeout=45)
        touched = True
        save()
        if args.install_apk:
            apk = Path(args.install_apk)
            report['apkSha256'] = hashlib.sha256(apk.read_bytes()).hexdigest()
            report['install'] = call_dict(client, 'install_dev_apk', {'apk_path': str(apk)}, timeout=180)
            if 'Success' not in str(report['install']):
                raise RuntimeError('APK update did not complete successfully')
        save()
        for selected in players:
            for mode in modes:
                row = {'player': selected, 'selectedMode': mode, 'passed': False}
                report['cases'].append(row)
                print('START', selected, mode, flush=True)
                save()
                call_dict(client, 'dev_exit_session', {'stop_app': True}, timeout=45)
                row['config'] = call_dict(client, 'dev_set_player_config', {
                    'player': 'gsyplayer' if selected.startswith('gsy_') else selected,
                    'gsy_engine': 'legacy_exo' if selected == 'gsy_legacy' else 'media3',
                    'streaming': mode, 'decoding': 'hardware'})
                row['connect'] = call_dict(client, 'dev_connect_server', {
                    'address': env.server()['address'], 'save': False}, timeout=45)
                # ui.contexts can briefly include the just-closed previous UI.
                # Do not route an ordered Watch until the new renderer is ready.
                row['uiReady'] = wait_automation_root(client, stable_ms=1200)
                deadline = time.monotonic() + 30
                while True:
                    try:
                        context = core.resolve_context(before['clientId'])
                        core.ui_state(context)
                        break
                    except Exception:
                        if time.monotonic() >= deadline: raise
                        time.sleep(1)
                row['identity'] = {'serial': serial, 'clientId': before['clientId'], 'context': context}
                row['watch'] = call_dict(client, 'dev_play_media_file_id', {
                    'media_file_id': media['mediaFileId'], 'timeout_s': 45,
                    'restart_from_beginning': True}, timeout=90)
                if not row['watch'].get('passed'):
                    raise RuntimeError('Fixture startup failed: ' + selected + '/' + mode)
                row['startup'] = advancing()
                save()
                if bool(row['startup']['last'].get('health_pushMode')) != (mode == 'push'):
                    raise RuntimeError('Actual transport did not match selected mode')
                if mode == 'push':
                    # No seek or server FLUSH may conceal this same-FIFO recovery.
                    call_dict(client, 'dev_clear_player_events')
                    row['fault'] = call_dict(client, 'dev_player_control', {'action': 'inject_push_parser_error'})
                    print('FAULT', selected, mode, flush=True)
                    row['recovery'] = advancing()
                    row['events'] = call_dict(client, 'dev_player_events')
                    if 'player_error_push_container_resync' not in json.dumps(row['events']):
                        raise RuntimeError('New recovery branch was not exercised')
                    if row['recovery']['last'].get('serverFlushSequence') != row['startup']['last'].get('serverFlushSequence'):
                        raise RuntimeError('Server FLUSH concealed client-only parser recovery')
                    if int(row['recovery']['last'].get('mediaTimeMs', -1)) < int(row['startup']['last']['mediaTimeMs']) - 2000:
                        raise RuntimeError('Same-stream recovery reset the server-visible presentation clock')
                    print('RECOVERED', selected, mode, flush=True)
                    save()
                    if args.forward_cache:
                        # Freeze while selecting the target so ADB latency cannot
                        # turn an in-buffer forward target into a backward request.
                        call_dict(client, 'dev_player_control', {'action': 'pause'})
                        cached = row['forwardBefore'] = state()
                        def cache_seeks(snapshot):
                            values = dict(part.split('=', 1) for part in
                                snapshot['pushForwardCacheState'].split(','))
                            return int(values['seeks'])
                        anchor = int(cached['pushEffectiveServerAnchorMs'])
                        target = int(cached['mediaTimeMs']) - anchor + 8000
                        row['forwardTargetMs'] = target
                        row['forwardCommand'] = call_dict(client, 'dev_local_seek_absolute', {'target_ms': target})
                        row['forwardAfter'] = state()
                        if cache_seeks(row['forwardAfter']) != cache_seeks(cached) + 1:
                            raise RuntimeError('Post-recovery cached seek did not consume its decoder-relative target')
                        if row['forwardAfter'].get('serverFlushSequence') != cached.get('serverFlushSequence'):
                            raise RuntimeError('A server FLUSH concealed cached decoder seek')
                        call_dict(client, 'dev_player_control', {'action': 'play'})
                        row['forwardOutput'] = advancing()
                row['seek'] = call_dict(client, 'dev_server_seek_time', {
                    'target_ms': 42000, 'timeout_s': 30, 'max_attempts': 1}, timeout=70)
                if not row['seek'].get('passed'):
                    raise RuntimeError('Post-recovery seek/FLUSH failed')
                row['afterSeek'] = advancing()
                row['pause'] = call_dict(client, 'dev_player_control', {'action': 'pause'})
                time.sleep(1)
                row['paused'] = state()
                if row['paused'].get('health_isPlaying'):
                    raise RuntimeError('Pause did not hold')
                if mode == 'push' and args.paused_fault:
                    row['pausedFault'] = call_dict(client, 'dev_player_control', {'action': 'inject_push_parser_error'})
                    deadline = time.monotonic() + 30
                    while True:
                        paused = state()
                        if paused.get('health_playWhenReady') or paused.get('health_isPlaying'):
                            raise RuntimeError('Fatal recovery changed paused intent')
                        if paused.get('health_playbackState') == 3:
                            row['pausedRecovery'] = paused
                            if abs(int(paused.get('mediaTimeMs', -1)) - int(row['paused']['mediaTimeMs'])) > 3000:
                                raise RuntimeError('Paused recovery moved/reset the presentation clock')
                            break
                        if time.monotonic() >= deadline:
                            raise RuntimeError('Paused recovery did not reach ready')
                        time.sleep(1)
                call_dict(client, 'dev_player_control', {'action': 'play'})
                row['resume'] = advancing()
                row['passed'] = True
                print('PASS', selected, mode, flush=True)
                save()
        report['passed'] = True
    except Exception as error:
        report['failure'] = str(error)
        try:
            report['failureState'] = state()
            report['failureEvents'] = call_dict(client, 'dev_player_events')
        except Exception as probe_error:
            report['probeFailure'] = str(probe_error)
        print('FAIL', str(error)[:1200], flush=True)
    finally:
        for name, action in (
            ('exit', lambda: call_dict(client, 'dev_exit_session', {'stop_app': True}, timeout=45) if touched else None),
            ('restore', lambda: call_dict(client, 'dev_restore_settings') if checkpointed else None),
            ('restorePower', lambda: end(serial, state_path(serial), 'automated') if awake else None),
        ):
            try:
                report[name] = action()
                if name == 'restore' and checkpointed and not report[name].get('restored'):
                    raise RuntimeError('Exact preference restoration did not verify')
            except Exception as error:
                report[name + 'Failure'] = str(error)
                report['passed'] = False
        save()
        client.close()
    return 0 if report['passed'] else 1


if __name__ == '__main__':
    raise SystemExit(main())
