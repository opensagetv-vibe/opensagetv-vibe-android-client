#!/usr/bin/env python3
"""Qualify initial-source attachment on a real growing live-TV Pull source.

Default is decoder recreation; --normal-reopen exercises ordinary growing
Watch. Neither proves scheduled pre-padding Watch. Use existing TV/channel/
audio/stock Record controls; no Core flags or fake SIZE replies.
"""
import argparse
import json
import os
from pathlib import Path
import subprocess
import sys
import time

from mcp_initial_resume_test import ROOT, child_command
from mcp_lifecycle_test import require, clear_restored_playback, wait_automation_ready
from mcp_seek_suite import MCPProcess, call_dict, initialize
from mcp_smoke_test import tool_call
from sagetv_dev_mcp.config import load_test_environment
from sagetv_dev_mcp.core_mcp_api import discover_sage_control


def segment_position(state):
    """Observe the production clock, including IJK's proven relative-clock path.

    Never accept an arbitrary advertised target in place of actual playback.
    IJK's existing discontinuity callback is required before using its mapped
    clock; raw native position and the mapping witness remain in the report.
    """
    raw = int(state.get('health_playerPositionMs', -1))
    if ('IJKMediaPlayerImpl' in str(state.get('playerClass', ''))
            and 'growing_pull_seek_relative_clock' in str(state.get('trapRecent', ''))):
        return int(state.get('mediaTimeMs', -1))
    return raw


def last_server_seek_time(state):
    """Seek sequence resets on stock DEINIT/OPEN; trap monotonic time does not."""
    times=[]
    for event in str(state.get('trapRecent','')).split('|'):
        fields=event.split('@')
        if len(fields)>2 and fields[1]=='server_seek_command':
            times.append(int(fields[2]))
    return max(times,default=-1)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--player', choices=('media3', 'exoplayer','ijkplayer','gsyplayer'), default='media3')
    parser.add_argument('--gsy-engine', choices=('auto','media3','legacy_exo','system'), default='auto')
    parser.add_argument('--channel', default='2.1')
    parser.add_argument('--server', default='vibe-test', help='Commissioned server alias')
    parser.add_argument('--existing-recording-path', default='',
                        help='Watch an exact currently recording file; never tune, pin or cancel it')
    parser.add_argument('--shared-capture-server', action='append', required=True,
                        help='Every other server sharing the capture encoder; read-only idle guard')
    parser.add_argument('--capture-hdmi', action='store_true')
    parser.add_argument('--normal-reopen', action='store_true',
                        help='Operator-verified stock STV recording pin, Stop and normal Watch')
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    require(args.normal_reopen or args.player in ('media3','exoplayer'),
            'Native/delegate gates require normal Watch, not the Exo-only audio rebuild')
    require(not args.existing_recording_path or args.normal_reopen,
            'Existing recording is only supported by the normal Watch gate')
    output = args.output.resolve()
    require(output.is_relative_to((ROOT/'artifacts/active/RESUME-001').resolve())
            and not output.exists(), 'New explicit RESUME-001 output required')
    os.environ['SAGETV_TEST_DEVICE_ALIAS'] = 'firetv-non-pro'
    os.environ['SAGETV_TEST_SERVER_ALIAS'] = args.server
    os.environ['SAGETV_ARTIFACT_DIR'] = str(ROOT/'artifacts/active/RESUME-001')
    os.environ['SAGETV_MCP_CONFIG'] = str(ROOT/'config/firetv.toml')
    env = load_test_environment()
    client = MCPProcess(command=child_command('firetv-non-pro', args.server))
    report = {'status':'STARTING', 'player':args.player, 'channel':args.channel,
              'server':env.server_address(), 'serial':env.device_serial(),
              'qualification':'real growing initial-source recreation, not scheduled Watch'}
    checkpoint = False
    control = None
    context = ''
    cc = None
    owned_recording = False
    owned_media_id = None

    def save():
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(json.dumps(report, indent=2)+'\n')

    def step(name):
        report['phase'] = name
        save()
        print('STEP '+name, flush=True)

    def state():
        return call_dict(client, 'dev_player_state', timeout=35)

    def healthy():
        result = call_dict(client, 'dev_wait_for_playback_started',
                           {'timeout_s':45, 'verify_ms':1500,
                            'expect_video':True, 'expect_audio':True}, timeout=65)
        report['latestHealth'] = result
        require(bool(result.get('passed')), 'Advancing A/V required')
        return result

    def record_menu_witness(label, confirmation):
        # Use the stock STV's existing Record command, not a new private API.
        # The operator must inspect its actual text before any schedule change.
        current_id = control.current_media_file_id(context)
        require(current_id == owned_media_id, 'Refuse Record for a different MediaFile')
        call_dict(client, 'dev_sage_command', {'command':'record'})
        ui = call_dict(client, 'dev_wait_for_ui',
                       {'popup_contains':'Handle Record Command', 'timeout_s':15})
        require(bool(ui.get('passed')), 'Stock Record menu did not open')
        capture = tool_call(client, 'take_screenshot', {'label':output.stem+'-'+label})
        report[label] = {'ui':ui, 'capture':capture}
        save()
        print('RECORD_MENU_WITNESS '+json.dumps(capture), flush=True)
        require(input('Inspect menu; type '+confirmation+': ').strip() == confirmation,
                'Missing exact operator menu verification')

    try:
        step('Readiness and private settings checkpoint')
        initialize(client)
        report['adb'] = call_dict(client, 'adb_connect', timeout=75)
        info = call_dict(client, 'firetv_device_info')
        require(info.get('ro.product.model') == env.device().get('model'), 'Wrong device')
        control = discover_sage_control(env.server_address())
        activity = control._request('server.activity')
        if args.existing_recording_path:
            # Read-only public recording metadata establishes the exact writer.
            # Do not turn a Watch test into a tuner/recording interruption.
            recordings = control._legacy_client().call('GetCurrentlyRecordingMediaFiles')
            rows = recordings.get('Result', [])
            require(isinstance(rows, list), 'Cannot verify active recording metadata')
            matching = [r for r in rows if args.existing_recording_path in r.get('SegmentFiles', [])]
            require(len(matching) == 1 and matching[0].get('IsFileCurrentlyRecording') is True,
                    'Exact existing file must actually be recording')
            report['existingRecordingBefore'] = matching[0]
            owned_media_id = int(matching[0]['MediaFileID'])
            require(str(matching[0].get('Airing',{}).get('AiringChannelNumber','')) == args.channel,
                    'Existing recording metadata does not match the requested channel')
        else:
            require(activity.get('recordingCount') == 0, 'Do not commission TV during recordings')
        report['activityBefore'] = activity
        report['sharedCaptureActivityBefore'] = {}
        for host in dict.fromkeys(args.shared_capture_server):
            owner = discover_sage_control(host)._request('server.activity')
            report['sharedCaptureActivityBefore'][host] = owner
            require(owner.get('recordingCount') == 0
                    and owner.get('connectedClientCount') == 0
                    and owner.get('uiContextCount') == 0,
                    'Shared capture owner is active; do not tune or stop its encoder')
        report['checkpoint'] = call_dict(client, 'dev_checkpoint_settings')
        require(bool(report['checkpoint'].get('captured')), 'Checkpoint failed')
        checkpoint = True
        clean = call_dict(client, 'dev_prepare_clean_start',
                          {'wake':True, 'graceful_timeout_s':2.0})
        require(bool(clean.get('readyToLaunch')), 'Clean start failed')
        report['config'] = call_dict(client, 'dev_set_player_config',
                {'player':args.player, 'streaming':'pull', 'decoding':'hardware',
                 'gsy_engine':args.gsy_engine,'gsy_system_probe':args.gsy_engine=='system',
                 'mim_direct_mode':'off', 'video_deinterlace_mode':'off'})
        call_dict(client, 'dev_connect_server', {'address':env.server_address(), 'save':False})
        wait_automation_ready(client, timeout_s=60)
        clear_restored_playback(client)
        identity = state()
        context = control.resolve_context(str(identity['clientId']))
        report['context'] = context
        cc = control.closed_caption_state(context)
        step('Start genuine live capture through existing public TV controls')
        if args.existing_recording_path:
            report['existingWatch'] = control.watch(context, owned_media_id, from_beginning=False)
        else:
            report['tv'] = control.remote_command(context, 'TV')
        # ChannelSet only applies once TV owns a player. An enqueue ACK during
        # startup is not a tune witness and must not label the old channel as
        # an alternate-channel result. Observe readiness, issue once, then poll.
        report['initialTvHealth'] = healthy()
        if not args.existing_recording_path:
            report['tune'] = control.tune_channel(context, args.channel)
        deadline = time.monotonic()+45
        while True:
            report['tunedState'] = state()
            if args.existing_recording_path:
                # Stock Core need not implement optional MEDIA_STATE_URL, so
                # mediaChannel can be absent. Exact public MediaFile identity
                # plus independent active-writer channel metadata is required.
                if control.current_media_file_id(context) == owned_media_id:
                    break
            elif str(report['tunedState'].get('mediaChannel', '')) == args.channel:
                break
            require(time.monotonic() < deadline,
                    'Requested channel never became active; tune ACK is not playback proof')
            time.sleep(1)
        report['liveHealth'] = healthy()
        report['liveState'] = state()
        require(report['liveState'].get('health_pushMode') is False, 'Actual Pull required')
        report['decodedOutput'] = call_dict(client, 'dev_set_active_audio', {'output':'decoded'})
        healthy()
        report['liveActivity'] = control._request('server.activity')
        require(report['liveActivity'].get('recordingCount', 0) > 0,
                'No actual server recording; cannot qualify a growing source')
        report['liveServerState'] = control.ui_state(context)
        if args.existing_recording_path:
            require(int(report['liveServerState']['media']['mediaFileId']) == owned_media_id,
                    'Watch must resolve the exact independently verified active recording')
            report['existingRecordingHistory'] = 'Normal Watch/Seek history only; never reset or cleared'
        if args.normal_reopen and not args.existing_recording_path:
            stv = control._request('captions.get', context=context).get('stvFile', '')
            require(str(stv).replace('\\','/').endswith('/SageTV7/SageTV7.xml'),
                    'Recording-pin UI sequence is qualified only for stock SageTV7')
            owned_media_id = int(report['liveServerState']['media']['mediaFileId'])
            record_menu_witness('beforeRecord', 'record-this-show-visible')
            # In this stock dialog Record executes the focused-independent
            # Record(Airing) branch. Never select Delete or change watched data.
            owned_recording = True
            call_dict(client, 'dev_sage_command', {'command':'record'})
            time.sleep(2)
            record_menu_witness('manualRecord', 'cancel-this-recording-visible')
            call_dict(client, 'dev_sage_command', {'command':'back'})
        step('Accumulate live history without synthetic active metadata')
        # Bounded intervals keep the runner responsive while the tuner appends.
        for _ in range(5):
            time.sleep(10)
            print('LIVE_CAPTURE_APPEND', flush=True)
        report['historyState'] = state()
        report['historyServerState'] = control.ui_state(context)
        step('Seek within real recording, then recreate source at that nonzero position')
        # A TV MediaFile can contain several capture segments and gaps. Core's
        # public Seek is relative to its full available window, whereas this
        # decoder's clock is relative to the active segment. A literal15s can
        # select an older completed segment and falsely "test" growth there.
        server_time = int(report['historyServerState']['mediaTimeMs'])
        window_start = int(report['historyServerState']['seekStartMs'])
        expected_raw = segment_position(report['historyState'])-30000
        target = server_time-window_start-30000
        require(target > 0 and expected_raw >= 12000, 'Insufficient current-segment history')
        source_identity = str(report['historyState'].get('mediaSourceIdentity', ''))
        require(bool(source_identity), 'Missing current segment identity')
        report['seekCoordinates'] = {'serverRelativeTargetMs':target,
                                    'expectedSegmentPositionMs':expected_raw,
                                    'sourceIdentity':source_identity}
        seek_started = time.monotonic()
        report['seek'] = control.seek(context, target)
        report['seekHealth'] = healthy()
        report['beforeRebuild'] = state()
        seek_elapsed_ms = int((time.monotonic()-seek_started)*1000)
        observed_raw = segment_position(report['beforeRebuild'])
        report['seekLandingWitness'] = {'expectedSegmentPositionMs':expected_raw,
                                       'observedSegmentPositionMs':observed_raw,
                                       'elapsedMs':seek_elapsed_ms}
        require(report['beforeRebuild'].get('mediaSourceIdentity') == source_identity,
                'Seek selected a different segment; not a growing-source gate')
        require(expected_raw-5000 <= observed_raw <= expected_raw+seek_elapsed_ms+5000,
                'Public Seek did not reach its actual mapped segment position')
        if args.normal_reopen:
            step('Stop and normal Watch while the owned recording continues')
            report['stop'] = control.media_control(context, 'stop')
            control.remote_command(context, 'Home')
            time.sleep(2)
            report['recordingDuringStop'] = control._request('server.activity')
            require(report['recordingDuringStop'].get('recordingCount') == 1,
                    'Recording did not remain active across Stop')
            report['normalWatch'] = control.watch(context, owned_media_id, from_beginning=False)
        else:
            report['audioRestart'] = call_dict(client, 'dev_player_control', {'action':'restart_audio'})
            require(bool(report['audioRestart'].get('accepted')), 'Rebuild not accepted')
        report['rebuildHealth'] = healthy()
        report['afterRebuild'] = state()
        if args.normal_reopen:
            resumed = report['afterRebuild']
            requested = int(resumed.get('serverRequestedSeekMs', -1))
            actual = segment_position(resumed)
            age = int(resumed.get('serverSeekAgeMs', -1))
            require(requested >= 12000 and age >= 0
                    and -5000 <= actual-requested <= age+5000,
                    'Normal growing Watch lost its server-owned bookmark')
            require(resumed.get('mediaSourceIdentity') == source_identity,
                    'Normal Watch selected a completed/different capture segment')
            report['normalResumeWitness'] = {'targetMs':requested,'actualMs':actual,'ageMs':age}
        require(segment_position(report['afterRebuild']) >=
                max(12000, segment_position(report['beforeRebuild'])-3000),
                'Growing initial-source attachment returned to zero')
        report['afterDecodedRebuild'] = report['afterRebuild']
        if args.normal_reopen:
            require(last_server_seek_time(report['afterDecodedRebuild']) >
                    last_server_seek_time(report['beforeRebuild']) >= 0,
                    'Normal Watch must issue a fresh server seek after Stop')
        else:
            require(int(report['afterDecodedRebuild'].get('sourceOpenMonotonicMs', -1)) >
                    int(report['beforeRebuild'].get('sourceOpenMonotonicMs', -1)),
                    'No new source open observed; do not count a no-op as initial-source recreation')
        step('Read beyond the source preparation snapshot')
        # Cross the original duration boundary; a finite read cap would stall here.
        for _ in range(6):
            time.sleep(10)
            print('GROWING_CONTINUITY', flush=True)
        report['continuityHealth'] = healthy()
        report['finalState'] = state()
        require(report['finalState'].get('mediaSourceIdentity') == source_identity,
                'Recording segment changed; continuity across this source is unqualified')
        require(int(report['finalState'].get('mediaTimeMs', -1)) >
                int(report['afterDecodedRebuild'].get('mediaTimeMs', -1))+40000,
                'Growing source did not continue beyond its preparation snapshot')
        if args.existing_recording_path:
            report['existingRecordingAfter'] = control._legacy_client().call('GetCurrentlyRecordingMediaFiles')
            require(any(int(r.get('MediaFileID',-1)) == owned_media_id
                        and r.get('IsFileCurrentlyRecording') is True
                        for r in report['existingRecordingAfter'].get('Result',[])),
                    'Existing recording must still be running after the playback test')
        if args.capture_hdmi:
            marker = {'scopeVerified':True, 'serial':env.device_serial(),
                      'clientId':identity['clientId'], 'context':context,
                      'expiresAtMs':int((time.time()+30)*1000)}
            output.with_suffix('.ready.json').write_text(json.dumps(marker)+'\n')
            capture = output.with_suffix('.mp4')
            subprocess.run([sys.executable, str(ROOT/'scripts/capture_hdmi_validation.py'),
                    '--output',str(capture), '--duration','6',
                    '--ready-file',str(output.with_suffix('.ready.json'))], check=True, timeout=50)
            report['hdmiCapture'] = str(capture.relative_to(ROOT))
            report['visualStatus'] = 'PENDING_INDEPENDENT_REVIEW'
        report['status'] = ('PASS_GROWING_NORMAL_WATCH' if args.normal_reopen
                            else 'PASS_RECREATION_NOT_SCHEDULED_WATCH')
    except Exception as exc:
        report['status'] = 'FAIL'
        report['error'] = str(exc)
        try: report['failureState'] = state()
        except Exception as inner: report['stateError'] = str(inner)
        try:
            report['failureCheckpoint'] = call_dict(client, 'dev_test_checkpoint',
                    {'label':'RESUME-001-growing-source-failure', 'log_lines':400}, timeout=50)
        except Exception as inner: report['checkpointError'] = str(inner)
    finally:
        try:
            if owned_recording:
                # Cancel only the manual flag created by this run. An exact
                # MediaFile and a visible Cancel item are mandatory witnesses.
                record_menu_witness('cancelOwnedRecord', 'cancel-owned-test-visible')
                call_dict(client, 'dev_sage_command', {'command':'record'})
                time.sleep(2)
                record_menu_witness('recordFlagCleared', 'record-this-show-visible')
                call_dict(client, 'dev_sage_command', {'command':'back'})
                owned_recording = False
                report['ownedRecordingCancelled'] = True
        except Exception as exc:
            report['status']='FAIL'; report['recordingCleanupError']=str(exc)
        try:
            if control and context and cc is not None:
                report['captionRestore'] = control.set_closed_caption_state(context, cc)
            if checkpoint:
                # A refused shared-encoder guard has not acquired this session.
                # Never stop an existing user client during preflight failure.
                call_dict(client, 'dev_exit_session', {'stop_app':True}, timeout=50)
                report['restore'] = call_dict(client, 'dev_restore_settings', timeout=100)
                require(bool(report['restore'].get('restored')), 'Restore failed')
        except Exception as exc:
            report['status']='FAIL'; report['restoreError']=str(exc)
        save()
        client.close()
    print(json.dumps({'status':report['status'], 'error':report.get('error'),
                      'output':str(output)}), flush=True)
    return 0 if report['status'].startswith('PASS') else 1


if __name__ == '__main__':
    raise SystemExit(main())
