#!/usr/bin/env python3
"""Bounded, exact-recording ordinary-Push seek/delivery comparison.

Uses public stock APIs; never changes Core, keys or audio preferences. Default
mode requires an idle commissioned device. Explicit current-playback testing
keeps the same file/connection and restores its original position/play state.
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
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--media-file-id', required=True, type=int)
    parser.add_argument('--install-apk', default='')
    parser.add_argument('--current-playback', action='store_true',
                        help='Explicitly test the selected active playback without Watch/reconnect')
    parser.add_argument('--cycles', type=int, default=1)
    parser.add_argument('--switch-recording', action='store_true',
                        help='Current-playback only: temporarily Watch the exact supplied recording')
    parser.add_argument('--input', choices=('public', 'remote'), default='public')
    parser.add_argument('--player', choices=('', 'exoplayer', 'media3', 'gsyplayer'), default='',
                        help='Idle-device comparisons only; checkpointed/restored backend choice')
    args = parser.parse_args()
    if args.output.exists():
        parser.error('Use a unique output path')
    if not 1 <= args.cycles <= 4 or (args.current_playback and args.install_apk):
        parser.error('Use1..4 cycles; current-playback never installs/reconnects')
    if args.switch_recording and not args.current_playback:
        parser.error('Switch-recording requires explicit current-playback restoration')
    if args.player and args.current_playback:
        parser.error('Current-playback never changes the selected backend')
    result = {'passed': False, 'seeks': []}
    client = MCPProcess()
    checkpointed = False
    connected_by_test = False
    awake_serial = None
    original_ui = None
    core = None
    context = None
    try:
        initialize(client)
        result['adb'] = call_dict(client, 'adb_connect')
        before = call_dict(client, 'dev_player_state')
        result['before'] = before
        env = load_test_environment()
        if before.get('clientId') != env.device().get('client_id'):
            raise RuntimeError('Exact device/client identity mismatch')
        if before.get('connected') and not args.current_playback:
            raise RuntimeError('Requires an idle disconnected test device; do not interrupt a viewer')
        if args.current_playback and (not before.get('connected') or not before.get('health_pushMode')):
            raise RuntimeError('Current playback must already be connected through actual Push')
        server = env.server()
        if args.current_playback:
            if before.get('serverAddress') != server['address']:
                raise RuntimeError('Current playback server does not match selected server')
            core = CoreMcpApiClient.discover(server['address'])
            context = core.resolve_context(before['clientId'])
            original_ui = core.ui_state(context)
            if (original_ui.get('dvd') or not original_ui.get('media', {}).get('mediaFileId') or
                    (not args.switch_recording and
                     int(original_ui['media']['mediaFileId']) != args.media_file_id)):
                raise RuntimeError('Current source must match exact ordinary recording; no Watch sent')
        awake_serial = env.device()['serial']
        result['keepAwake'] = begin(awake_serial, state_path(awake_serial), 'automated')
        if not args.current_playback:
            result['checkpoint'] = call_dict(client, 'dev_checkpoint_settings')
            checkpointed = True
            if args.install_apk:
                result['install'] = call_dict(client, 'install_dev_apk',
                                             {'apk_path': args.install_apk}, timeout=180)
            config = {'streaming': 'push'}
            if args.player:
                config['player'] = args.player
            result['config'] = call_dict(client, 'dev_set_player_config', config)
            tool_call(client, 'launch_dev_app', {}, timeout=45)
            connected_by_test = True
            result['connect'] = call_dict(client, 'dev_connect_server',
                                         {'address': server['address'], 'save': False}, timeout=45)
            core = CoreMcpApiClient.discover(server['address'])
            deadline = time.monotonic() + 30
            while time.monotonic() < deadline:
                state = call_dict(client, 'dev_player_state')
                if state.get('connected') and state.get('menuName'):
                    context = core.resolve_context(before['clientId'])
                    break
                time.sleep(1)
        if context is None:
            raise RuntimeError('Selected UI did not register; no Watch sent')
        result['identity'] = {'clientId': before['clientId'], 'context': context,
                              'server': server['address']}
        if not args.current_playback or args.switch_recording:
            result['watch'] = call_dict(client, 'dev_play_media_file_id',
                {'media_file_id': args.media_file_id, 'timeout_s': 45}, timeout=90)
            if not result['watch'].get('passed'):
                raise RuntimeError('Watch did not produce advancing playback')
        if args.current_playback:
            result['originalUi'] = original_ui
        # Alternate the exact fast/slow incident targets and nearby backward
        # positions. One request per row; never replay an ambiguous timeout.
        targets = (4599075, 4613031, 4599075, 4613031, 4584075, 4613031)
        if args.current_playback and not args.switch_recording:
            position = int(before['mediaTimeMs'])
            duration = int(original_ui['durationMs'])
            targets = tuple(max(30000, min(duration - 60000, position + delta))
                            for delta in (10000, 20000, -10000, 30000, -20000, 10000))
        for index, target in enumerate(targets * args.cycles):
            state = call_dict(client, 'dev_player_state')
            ui = core.ui_state(context)
            if not state.get('health_pushMode') or int(ui['media']['mediaFileId']) != args.media_file_id:
                raise RuntimeError('Actual Push/exact source not observed')
            events = call_dict(client, 'dev_player_events').get('events', [])
            sequence = max((int(e.get('sequence', 0)) for e in events), default=0)
            row = {'targetMs': target, 'before': state}
            if args.input == 'remote':
                key = ('RIGHT', 'RIGHT', 'LEFT', 'LEFT', 'RIGHT', 'LEFT')[index % 6]
                target = int(state['mediaTimeMs']) + (10000 if key == 'RIGHT' else -10000)
                row.update(targetMs=target, remoteKey=key)
            result['seeks'].append(row)
            started = time.monotonic()
            if args.input == 'remote':
                row['ack'] = tool_call(client, 'firetv_key', {'key': key})
            else:
                row['ack'] = core.seek(context, target)
            row['requestAckMs'] = round((time.monotonic() - started) * 1000)
            deadline = started + 25
            stall_announced = False
            while time.monotonic() < deadline:
                events = [e for e in call_dict(client, 'dev_player_events').get('events', [])
                          if int(e.get('sequence', 0)) > sequence]
                named = {e.get('event'): e for e in events}
                if all(k in named for k in ('server_flush_command', 'first_video_frame', 'playback_state_3')):
                    flush = int(named['server_flush_command']['monotonicMs'])
                    row['flushToFrameMs'] = int(named['first_video_frame']['monotonicMs']) - flush
                    row['flushToReadyMs'] = int(named['playback_state_3']['monotonicMs']) - flush
                    row['beforeSnapshotToFrameMs'] = (int(named['first_video_frame']['monotonicMs'])
                        - int(state['health_capturedMonotonicMs']))
                    row['events'] = events
                    break
                if not stall_announced and time.monotonic() - started > 2:
                    stall_announced = True
                    row['slowSnapshot'] = call_dict(client, 'dev_player_state')
                    print('SEEK_DELIVERY_SLOW ' + str(target), flush=True)
                time.sleep(.25)
            if 'flushToReadyMs' not in row:
                raise RuntimeError('No fresh post-FLUSH frame/READY within25s')
            # READY can precede position-cache settlement. Require continuing
            # output and a settled clock; do not accept an ACK as landing proof.
            time.sleep(3)
            row['after'] = call_dict(client, 'dev_player_state')
            row['elapsedMs'] = round((time.monotonic() - started) * 1000)
            # A stalled request does not play during its wait. Use the device
            # clock since actual first-frame for landing, and fail excessive
            # request latency separately. Host RPC/thread-dump time is not
            # decoded playback and must not look like a wrong seek target.
            decoded_ms = max(0, int(row['after']['health_capturedMonotonicMs'])
                             - int(named['first_video_frame']['monotonicMs']))
            row['landingErrorMs'] = int(row['after']['mediaTimeMs']) - target - decoded_ms
            row['transport'] = json.loads(row['after'].get('mediaTransportJson', '{}'))
            print(f"target={target} frame={row['flushToFrameMs']}ms ready={row['flushToReadyMs']}ms "
                  f"landingError={row['landingErrorMs']}ms", flush=True)
            if abs(row['landingErrorMs']) > 5000:
                raise RuntimeError('Settled playback did not follow the requested timeline')
            if row['beforeSnapshotToFrameMs'] > 6500:
                raise RuntimeError('Seek delivery remained slow before the fresh frame')
        result['passed'] = True
    except Exception as exc:
        result['failure'] = str(exc)
        print('FAIL:', exc, flush=True)
    finally:
        try:
            if original_ui is not None and result['seeks']:
                current = core.ui_state(context)
                if int(current.get('media', {}).get('mediaFileId', 0)) != args.media_file_id:
                    raise RuntimeError('Source changed externally; never overwrite another playback')
                if args.switch_recording:
                    result['restoreWatch'] = call_dict(client, 'dev_play_media_file_id',
                        {'media_file_id': int(original_ui['media']['mediaFileId']),
                         'timeout_s': 45}, timeout=90)
                    if not result['restoreWatch'].get('passed'):
                        raise RuntimeError('Original recording did not resume advancing playback')
                result['restoreSeek'] = core.seek(context, int(before['mediaTimeMs']))
                # Acknowledge restoration only after the same source and a
                # settled clock near the saved position are independently seen.
                restored_at = time.monotonic()
                deadline = restored_at + 25
                while time.monotonic() < deadline:
                    restored = call_dict(client, 'dev_player_state')
                    delta = int(restored.get('mediaTimeMs', -1)) - int(before['mediaTimeMs'])
                    if restored.get('health_playbackState') == 3 and abs(delta) < 5000:
                        result['restoredPlayback'] = restored
                        break
                    time.sleep(.5)
                if 'restoredPlayback' not in result:
                    raise RuntimeError('Restored target did not settle within25s')
                if not before.get('health_isPlaying'):
                    result['restorePause'] = core.media_control(context, 'pause')
        except Exception as exc:
            result['playbackRestorationFailure'] = str(exc)
            result['passed'] = False
        try:
            if connected_by_test:
                result['exit'] = call_dict(client, 'dev_exit_session', timeout=45)
        except Exception as exc:
            result['exitFailure'] = str(exc)
            result['passed'] = False
        try:
            if checkpointed:
                result['restore'] = call_dict(client, 'dev_restore_settings')
                if not result['restore'].get('restored'):
                    raise RuntimeError('Checkpoint restoration did not verify')
        except Exception as exc:
            result['restoreFailure'] = str(exc)
            result['passed'] = False
        try:
            if awake_serial:
                result['restorePower'] = end(awake_serial, state_path(awake_serial), 'automated')
        except Exception as exc:
            result['restorePowerFailure'] = str(exc)
            result['passed'] = False
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
        client.close()
    return 0 if result['passed'] else 1


if __name__ == '__main__':
    raise SystemExit(main())
