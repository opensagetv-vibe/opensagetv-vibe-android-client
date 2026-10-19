#!/usr/bin/env python3
"""Finish an existing first-Watch observation after a typed-response oracle miss.

Never Watch/Seek/reset history or acquire another settings checkpoint. The
original scheduled driver retains session/settings/owned-recording cleanup.
"""
import argparse
import json
import os
from pathlib import Path
import subprocess
import sys
import time

from mcp_initial_resume_test import ROOT,child_command
from mcp_lifecycle_test import require
from mcp_seek_suite import MCPProcess,call_dict,initialize
from mcp_scheduled_resume_ui_test import media_object
from sagetv_dev_mcp.core_mcp_api import discover_sage_control
from sagetv_dev_mcp.config import load_test_environment


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--initial-report',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args()
    active=(ROOT/'artifacts/active/RESUME-001').resolve()
    initial_path=args.initial_report.resolve();output=args.output.resolve()
    require(initial_path.is_relative_to(active) and output.is_relative_to(active)
            and not output.exists(),'Exact existing input and new task output required')
    prior=json.loads(initial_path.read_text())
    require(prior.get('error')=='Watch must select the exact owned test airing while it is still recording',
            'Only the independently proven typed-response oracle miss may continue')
    initial=prior['initial'];metadata=media_object(prior['expectedRecording'])
    media_id=int(prior['initialServer']['media']['mediaFileId'])
    require(metadata.get('MediaFileID')==media_id
            and metadata.get('IsFileCurrentlyRecording') is True,'Initial exact writer mismatch')
    expected=int(prior['airStartMs'])-int(prior['initialServer']['seekStartMs'])
    target=int(initial['serverRequestedSeekMs']);actual=int(initial['health_playerPositionMs'])
    age=int(initial['serverSeekAgeMs'])
    require(prior['initialHealth'].get('passed') is True and expected>=5000
            and abs(target-expected)<=5000 and age>=0
            and -5000<=actual-target<=age+5000,'Original first Watch did not retain actual padding')
    os.environ['SAGETV_MCP_CONFIG']=str(ROOT/'config/firetv.toml')
    os.environ['SAGETV_TEST_SERVER_ALIAS']='stock-compatibility'
    os.environ['SAGETV_TEST_DEVICE_ALIAS']='firetv-non-pro'
    environment=load_test_environment()
    require(prior.get('server')==environment.server_address(),
            'Original report must match the commissioned stock server')
    client=MCPProcess(command=child_command('firetv-non-pro','stock-compatibility'))
    report={'status':'STARTING','parent':str(initial_path.relative_to(ROOT)),
            'initialWitness':{'paddingMs':expected,'targetMs':target,'actualMs':actual,'ageMs':age},
            'qualification':'Same first Watch, no corrective Watch/Seek, original driver owns cleanup'}
    def save(): output.write_text(json.dumps(report,indent=2)+'\n')
    def state(): return call_dict(client,'dev_player_state',timeout=35)
    def healthy():
        value=call_dict(client,'dev_wait_for_playback_started',
                        {'timeout_s':45,'verify_ms':1500,'expect_video':True,'expect_audio':True},timeout=65)
        require(value.get('passed') is True,'Advancing A/V required')
        return value
    try:
        initialize(client)
        control=discover_sage_control(environment.server_address())
        context=control.resolve_context(str(prior['identity']['clientId']))
        require(control.current_media_file_id(context)==media_id,'Active file changed')
        report['beforeHealth']=healthy();before=state();report['before']=before;save()
        require(before.get('clientId')==prior['identity']['clientId'],'Wrong client')
        require(before.get('mediaSourceIdentity')==initial.get('mediaSourceIdentity'),
                'Original source was replaced; cannot continue its first-Watch gate')
        for _ in range(6):time.sleep(10);print('SAME_PADDED_FIRST_WATCH_CONTINUES',flush=True)
        report['finalHealth']=healthy();final=state();report['final']=final
        require(final.get('health_pushMode') is False
                and final.get('mediaSourceIdentity')==initial.get('mediaSourceIdentity'),
                'Actual source/transport changed')
        require(int(final['health_playerPositionMs'])>int(before['health_playerPositionMs'])+40000,
                'Player did not continue advancing')
        require(int(final['serverRequestedSeekMs'])==target
                and int(final['serverSeekAgeMs'])>age+40000,'An additional seek invalidated first Watch')
        recording=control._legacy_client().call('GetCurrentlyRecordingMediaFiles')
        report['recordingAfter']=recording
        require(any(r.get('MediaFileID')==media_id for r in recording.get('Result',[])),
                'Test source stopped recording')
        marker={'scopeVerified':True,'serial':environment.device_serial(),
                'clientId':prior['identity']['clientId'],'context':context,
                'expiresAtMs':int((time.time()+30)*1000)}
        output.with_suffix('.ready.json').write_text(json.dumps(marker)+'\n')
        save()
        subprocess.run([sys.executable,str(ROOT/'scripts/capture_hdmi_validation.py'),
                        '--output',str(output.with_suffix('.mp4')),'--duration','6',
                        '--ready-file',str(output.with_suffix('.ready.json'))],check=True,timeout=50)
        report['status']='PASS_PADDED_FIRST_WATCH_RESPONSE_ORACLE_CORRECTED'
        report['visualStatus']='PENDING_INDEPENDENT_REVIEW'
    except Exception as exc:report['status']='FAIL';report['error']=str(exc)
    finally:save();client.close()
    print(json.dumps({'status':report['status'],'error':report.get('error')}),flush=True)
    return 0 if report['status'].startswith('PASS') else 1


if __name__=='__main__':raise SystemExit(main())
