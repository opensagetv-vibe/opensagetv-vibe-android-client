#!/usr/bin/env python3
"""Operator-observed stock STV padded recording gate, using existing MCP tools.

Schedule ONLY a new test recording through Guide/Record UI; inspect every menu.
No synthetic metadata, private Core calls, watched resets or corrective seeks.
"""
import argparse
from datetime import datetime
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
from sagetv_dev_mcp.core_mcp_api import discover_sage_control
from sagetv_dev_mcp.config import load_test_environment


def media_object(response):
    """Sagex collection APIs use Result; typed single objects use MediaFile."""
    result=response.get('Result',response.get('MediaFile',{}))
    return result if isinstance(result,dict) else {}


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--air-start',required=True,help='Actual guide programme start with timezone')
    parser.add_argument('--output',required=True,type=Path)
    parser.add_argument('--server',default='vibe-test',help='Commissioned server alias')
    parser.add_argument('--shared-capture-server',action='append',required=True,
                        help='Every other capture owner address; read-only idle guard')
    parser.add_argument('--setup-lead-minutes',type=int,default=10,
                        help='Preparation lead; actual captured padding is independently required')
    parser.add_argument('--reserved-capture-device',action='append',default=[],
                        help='Exact idle device(s) eligible for the new owned test recording')
    parser.add_argument('--test-airing-id',type=int,default=0,
                        help='Exact new owned airing required with a reserved capture device')
    args=parser.parse_args()
    air_date=datetime.fromisoformat(args.air_start)
    require(air_date.utcoffset() is not None,'Programme start requires an explicit timezone')
    air_start=int(air_date.timestamp()*1000)
    require(1<=args.setup_lead_minutes<=30,'Setup lead must be1..30 minutes')
    require(not args.reserved_capture_device or args.test_airing_id>0,
            'Reserved capture requires an exact test airing ID')
    require(air_start-int(time.time()*1000)>=args.setup_lead_minutes*60000,
            'Insufficient declared preparation lead before the programme')
    output=args.output.resolve()
    require(output.is_relative_to((ROOT/'artifacts/active/RESUME-001').resolve())
            and not output.exists(),'New explicit task output required')
    os.environ.update(SAGETV_TEST_DEVICE_ALIAS='firetv-non-pro',
                      SAGETV_TEST_SERVER_ALIAS=args.server,
                      SAGETV_MCP_CONFIG=str(ROOT/'config/firetv.toml'))
    env=load_test_environment()
    server_address=env.server_address()
    c=MCPProcess(command=child_command('firetv-non-pro',args.server))
    report={'status':'STARTING','airStartMs':air_start,'server':server_address,
            'serial':env.device_serial()}
    checkpoint=False; owned=False; context=''; control=None; cc=None
    def save():
        output.write_text(json.dumps(report,indent=2)+'\n')
    def state(): return call_dict(c,'dev_player_state')
    def shot(label):
        result=tool_call(c,'take_screenshot',{'label':output.stem+'-'+label})
        report.setdefault('screens',[]).append(result);save()
        print('SCREEN '+json.dumps(result),flush=True)
    def healthy():
        result=call_dict(c,'dev_wait_for_playback_started',
                         {'timeout_s':45,'verify_ms':1500,'expect_video':True,'expect_audio':True},timeout=65)
        require(bool(result.get('passed')),'Advancing A/V required')
        return result
    def command(key): return call_dict(c,'dev_sage_command',{'command':key})
    allowed={'guide','up','down','left','right','record','select','back','info',
             'home','options','ch_up','ch_down'} | set('0123456789')
    try:
        initialize(c); report['adb']=call_dict(c,'adb_connect',timeout=75)
        info=call_dict(c,'firetv_device_info');report['device']=info
        require(info.get('ro.product.model')=='AFTMM','Wrong commissioned device')
        for host in dict.fromkeys([server_address]+args.shared_capture_server):
            activity=discover_sage_control(host)._request('server.activity')
            deadline=time.monotonic()+60
            while any(activity.get(k)!=0 for k in ('uiContextCount','connectedClientCount')):
                require(time.monotonic()<deadline,'Capture owner has another UI/client')
                print('WAIT_FOR_IDLE_UI '+host,flush=True)
                time.sleep(5)
                activity=discover_sage_control(host)._request('server.activity')
            report[host]=activity
            require(all(activity.get(k)==0 for k in
                        ('uiContextCount','connectedClientCount')),'Capture owner has another UI/client')
            if host==server_address and args.reserved_capture_device:
                reader=discover_sage_control(host)._legacy_client()
                inputs=reader.call('GetConfiguredCaptureDeviceInputs').get('Result',[])
                report['reservedCaptureBefore']={}
                for device in args.reserved_capture_device:
                    require(any(str(i).startswith(device+' ') for i in inputs),
                            'Reserved capture device is not configured')
                    current=reader.call('GetCaptureDeviceCurrentRecordFile',device)
                    scheduled=reader.call('GetScheduledRecordingsForDeviceForTime',
                                          device,air_start-300000,air_start+1800000)
                    report['reservedCaptureBefore'][device]={'current':current,'schedule':scheduled}
                    # Sagex omits Result for a null MediaFile. Require an exact
                    # configured device and an independently empty schedule too.
                    require(current in ({},{'Result':None}), 'Reserved device is currently recording')
                    require(scheduled.get('Result')==[],'Reserved device already has scheduled work')
                report['protectedRecordingsBefore']=reader.call('GetCurrentlyRecordingMediaFiles')
            else:
                require(activity.get('recordingCount')==0,'Capture owner active')
        report['checkpoint']=call_dict(c,'dev_checkpoint_settings');checkpoint=True
        require(report['checkpoint'].get('captured') is True,'Checkpoint failed')
        call_dict(c,'dev_prepare_clean_start',{'wake':True,'graceful_timeout_s':2.0})
        call_dict(c,'dev_set_player_config',{'player':'media3','streaming':'pull',
                    'decoding':'hardware','mim_direct_mode':'off','video_deinterlace_mode':'off'})
        call_dict(c,'dev_connect_server',{'address':server_address,'save':False})
        wait_automation_ready(c,timeout_s=60); clear_restored_playback(c)
        identity=state();report['identity']=identity
        control=discover_sage_control(server_address)
        context=control.resolve_context(str(identity['clientId']))
        cc=control.closed_caption_state(context)
        command('guide');shot('guide')
        sequence=0
        while True:
            key=input('UI command / owned-recording-created / watch-verified: ').strip()
            if key=='snapshot': shot('snapshot-'+str(time.time_ns()));continue
            if key=='owned-recording-created':
                owned=True;report['ownedRecordingCreated']=True;save();continue
            if key=='allocation-verified':
                require(bool(args.reserved_capture_device),'No reserved device declared')
                report['reservedCaptureScheduleAfter']={}
                rows=[]
                for device in args.reserved_capture_device:
                    assigned_schedule=control._legacy_client().call(
                        'GetScheduledRecordingsForDeviceForTime',device,
                        air_start-300000,air_start+1800000)
                    report['reservedCaptureScheduleAfter'][device]=assigned_schedule
                    rows.extend(assigned_schedule.get('Result',[]))
                assigned=[r for r in rows if r.get('AiringID')==args.test_airing_id]
                require(len(assigned)==1 and assigned[0].get('IsManualRecord') is True,
                        'Owned airing is not assigned to the independently idle reserved device')
                start=int(assigned[0]['ScheduleStartTime'])
                require(start<=air_start-5000 and time.time()*1000<start-30000,
                        'Verify assignment and real padding before the capture starts')
                protected=control._legacy_client().call('GetCurrentlyRecordingMediaFiles')
                report['protectedRecordingsAfterSetup']=protected
                expected={r['MediaFileID'] for r in report['protectedRecordingsBefore'].get('Result',[])}
                current={r['MediaFileID'] for r in protected.get('Result',[])}
                require(expected<=current,'Existing recording was interrupted during setup')
                report['allocationVerified']=True
                save();print('ALLOCATION '+json.dumps(report['reservedCaptureScheduleAfter']),flush=True)
                continue
            if key=='watch-verified': break
            require(key in allowed,'Unsupported UI command')
            report.setdefault('commands',[]).append(command(key))
            time.sleep(0.5);sequence+=1;shot(str(sequence))
        require(owned,'A new owned recording must be explicitly witnessed')
        require(not args.reserved_capture_device or report.get('allocationVerified') is True,
                'Reserved-capture allocation was not independently verified')
        report['initialHealth']=healthy();initial=state();report['initial']=initial
        server=control.ui_state(context);report['initialServer']=server
        media_id=int(server['media']['mediaFileId'])
        if args.test_airing_id:
            expected_media=control._legacy_client().call('GetMediaFileForAiring',
                                                        'airing:'+str(args.test_airing_id))
            report['expectedRecording']=expected_media
            metadata=media_object(expected_media)
            require(metadata.get('MediaFileID')==media_id
                    and metadata.get('IsFileCurrentlyRecording') is True,
                    'Watch must select the exact owned test airing while it is still recording')
        expected=air_start-int(server['seekStartMs'])
        requested=int(initial.get('serverRequestedSeekMs',-1))
        actual=int(initial.get('health_playerPositionMs',-1));age=int(initial.get('serverSeekAgeMs',-1))
        report['initialWitness']={'expectedPaddingMs':expected,'requestedMs':requested,
                                  'actualMs':actual,'requestAgeMs':age}
        require(initial.get('health_pushMode') is False,'Actual Pull required')
        require(expected>=5000 and abs(requested-expected)<=5000,
                'Initial server target must equal the independently observed pre-padding')
        require(age>=0 and -5000<=actual-requested<=age+5000,'Initial Watch returned to zero')
        identity_hash=initial['mediaSourceIdentity'];save()
        for _ in range(6): time.sleep(10);print('PADDED_GROWTH',flush=True)
        report['finalHealth']=healthy();final=state();report['final']=final
        require(final.get('mediaSourceIdentity')==identity_hash,'Unexpected capture segment change')
        require(int(final['health_playerPositionMs'])>actual+40000,'Padded playback did not advance')
        command('info');shot('final-timeline')
        marker={'scopeVerified':True,'serial':env.device_serial(),
                'clientId':identity['clientId'],'context':context,'expiresAtMs':int((time.time()+30)*1000)}
        output.with_suffix('.ready.json').write_text(json.dumps(marker)+'\n')
        subprocess.run([sys.executable,str(ROOT/'scripts/capture_hdmi_validation.py'),
                        '--output',str(output.with_suffix('.mp4')),'--duration','6',
                        '--ready-file',str(output.with_suffix('.ready.json'))],check=True,timeout=50)
        report['mediaId']=media_id;report['status']='PASS_PADDED_INITIAL_WATCH'
    except Exception as exc:
        report['status']='FAIL';report['error']=str(exc)
        try: report['failureState']=state()
        except Exception: pass
    finally:
        if owned:
            try:
                if args.test_airing_id:
                    # SageMC Record toggles immediately; SageTV7 opens a menu.
                    # Never replay Record assuming the other STV's behavior.
                    shot('cleanup-owned-airing')
                    while True:
                        flag=control._legacy_client().call('IsManualRecord','airing:'+str(args.test_airing_id))
                        report['cleanupManualFlag']=flag
                        if flag.get('Result') is False: break
                        require(flag.get('Result') is True,'Cannot verify owned manual flag')
                        # SageMC's Yes preserves the partial file by shortening
                        # this owned schedule to now. Its historical manual
                        # flag legitimately remains true; do not toggle Record
                        # again or delete the retained file to force it false.
                        reader=control._legacy_client()
                        end=reader.call('GetScheduleEndTime','airing:'+str(args.test_airing_id))
                        partial=media_object(reader.call('GetMediaFileForAiring',
                                                         'airing:'+str(args.test_airing_id)))
                        if (isinstance(end.get('Result'),int) and end['Result']<=time.time()*1000
                                and partial.get('IsFileCurrentlyRecording') is False
                                and partial.get('IsMediaFileObject') is True):
                            report['ownedRecordingEnded']=True
                            report['partialRecordingPreserved']=True
                            report['cleanupScheduleEnd']=end
                            break
                        key=input('Inspect owned-airing cleanup; enter one UI command: ').strip()
                        require(key in allowed,'Unsupported cleanup UI command')
                        command(key);time.sleep(0.5);shot('cleanup-'+str(time.time_ns()))
                    report['ownedRecordingCancelled']=flag.get('Result') is False
                else:
                    command('record');time.sleep(1);shot('cancel-owned')
                    require(input('Inspect Cancel this Recording; type cancel-owned: ').strip()=='cancel-owned',
                            'Missing cancellation witness')
                    command('record');time.sleep(2);command('record');time.sleep(1);shot('cancelled')
                    require(input('Inspect Record this show; type cleared: ').strip()=='cleared',
                            'Missing cleared flag witness')
                    command('back');report['ownedRecordingCancelled']=True
            except Exception as exc: report['status']='FAIL';report['cleanupError']=str(exc)
        try:
            if cc is not None: control.set_closed_caption_state(context,cc)
            if checkpoint:
                call_dict(c,'dev_exit_session',{'stop_app':True},timeout=50)
                report['restore']=call_dict(c,'dev_restore_settings',timeout=100)
                require(report['restore'].get('restored') is True,'Restore failed')
        except Exception as exc:report['status']='FAIL';report['restoreError']=str(exc)
        save();c.close()
    print(json.dumps({'status':report['status'],'error':report.get('error')}),flush=True)
    return 0 if report['status'].startswith('PASS') else 1

if __name__=='__main__':raise SystemExit(main())
