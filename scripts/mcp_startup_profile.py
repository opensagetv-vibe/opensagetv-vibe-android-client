#!/usr/bin/env python3
"""STARTUP-001 focused owned stock232/Non-Pro timing, not a full player matrix.

Nonblocking public Watch and bounded snapshots measure first observed rendered
A/V, followed by independent sustained-output verification, not exact photon.
No default buffering change is inferred from a single fast/slow observation.
"""
import json,os,sys,time
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'mcp/src'))
from mcp_config_values import FIXED_DEFAULTS,streaming_preference
from mcp_seek_suite import MCPProcess,call_dict,initialize
from mcp_ui_roots import wait_automation_root
from sagetv_dev_mcp.config import load_test_environment
from sagetv_dev_mcp.core_mcp_api import CoreMcpApiClient
import android_test_keep_awake as awake

ROOT=Path(__file__).resolve().parents[1]
OUT=Path(os.environ['SAGETV_ARTIFACT_DIR']).resolve()
if OUT!=(ROOT/'artifacts/active/STARTUP-001').resolve():raise SystemExit('Use the owned STARTUP-001 output directory')
OUT.mkdir(parents=True,exist_ok=True)
env=load_test_environment();serial=env.device_serial();server=env.server_address()
if serial!=env.device_serial('non_pro') or server!=env.server_address('vibe'):raise SystemExit('Exact configured Non-Pro/stock232 scope required')
fixture='/var/media/OpenSageTV_Vibe_Tests/VibeCaptionTest-1080i-CEA608-90s-2s.ts'
api=CoreMcpApiClient.discover(server)
if api._request('server.activity').get('recordingCount')!=0:raise SystemExit('Recording activity; defer')
media=api.resolve_exact_path(fixture);media_id=media.get('mediaFileId')
if not media_id:raise SystemExit('Canonical fixture unavailable')
rows=[];client=MCPProcess();checkpoint=False;lease=False;cleanup={};failure=None;complete=False
stamp=time.strftime('%Y%m%dT%H%M%SZ',time.gmtime())
try:
    initialize(client);call_dict(client,'adb_connect',timeout=30)
    saved=call_dict(client,'dev_checkpoint_settings');assert saved.get('captured'),saved
    checkpoint=True
    lease=bool(awake.begin(serial,awake.state_path(serial),'automated').get('active'))
    cases=[('push1','media3','push','off',None),('pull1','media3','pull','off',None),
           ('direct1','media3','fixed','transcode',None),('pull-fast','media3','pull','off',{'playbackBufferMs':250}),
           ('push2','media3','push','off',None),('pull2','media3','pull','off',None),
           ('direct2','media3','fixed','transcode',None),('legacy-push','exoplayer','push','off',None)]
    if os.environ.get('SAGETV_STARTUP_CASES')=='events':cases=[cases[0],cases[1],cases[3]]
    for label,player,transport,direct,tuning in cases:
        if api._request('server.activity').get('recordingCount')!=0:raise RuntimeError('Recording activity changed')
        api.clear_watched(media_id) # Synthetic fixture only, never user media.
        fixed=dict(FIXED_DEFAULTS,mim_direct_mode=direct,mim_direct_deinterlace='off')
        clean=call_dict(client,'dev_prepare_clean_start',{'wake':True,'graceful_timeout_s':2.0},timeout=30)
        assert clean.get('readyToLaunch'),clean
        call_dict(client,'dev_set_player_config',dict(player=player,streaming=streaming_preference(transport),
            decoding='hardware',gsy_engine='auto',**fixed))
        call_dict(client,'dev_set_player_tuning',dict(reset=True,**(tuning or {})))
        call_dict(client,'dev_connect_server',{'address':server,'port':31099,'save':False},timeout=30)
        wait_automation_root(client,timeout_s=30,stable_ms=700)
        before=call_dict(client,'dev_player_state')
        assert before.get('serverAddress')==server and before.get('clientId')==env.device()['client_id'],before.get('clientId')
        context=api.resolve_context(before['clientId'])
        if os.environ.get('SAGETV_STARTUP_CASES')=='events':call_dict(client,'dev_clear_player_events')
        # The broader playback helper deliberately waits for resume/fullscreen
        # dialogs. Measure actual decoder startup with ordinary nonblocking
        # public Watch instead; this fixture's watched state was just cleared.
        started=time.monotonic();api._request('media.watch',context=context,media_id=media_id,wait_ms=0)
        ack_ms=round((time.monotonic()-started)*1000);first=None;samples=[];poll_max=0
        deadline=time.monotonic()+60
        while time.monotonic()<deadline:
            poll_started=time.monotonic();state=call_dict(client,'dev_player_state')
            poll_max=max(poll_max,round((time.monotonic()-poll_started)*1000))
            elapsed=round((time.monotonic()-started)*1000)
            samples.append({'ms':elapsed,'video':state.get('health_videoRendered'),'audio':state.get('health_audioRendered'),
                'position':state.get('health_playerPositionMs'),'firstFrame':state.get('health_firstVideoFrameRendered')})
            if state.get('health_playerError'):raise RuntimeError(str(state['health_playerError']))
            if int(state.get('health_videoRendered',0))>0 and int(state.get('health_audioRendered',0))>0:
                first=elapsed;break
            time.sleep(.1)
        if first is None:raise RuntimeError('No actual rendered A/V within startup budget')
        initial=state;time.sleep(2);state=call_dict(client,'dev_player_state')
        assert int(state.get('health_videoRendered',0))>int(initial['health_videoRendered']),state
        assert int(state.get('health_audioRendered',0))>int(initial['health_audioRendered']),state
        assert not state.get('health_playerError'),state.get('health_playerError')
        if api.current_media_file_id(context)!=media_id:raise RuntimeError('Unexpected source in exact owned UI')
        if state.get('serverAddress')!=server:raise RuntimeError('Wrong playback server')
        if transport=='push' and not state.get('health_pushMode'):raise RuntimeError('Selected Push did not negotiate Push')
        if transport=='pull' and state.get('health_pushMode'):raise RuntimeError('Selected Pull unexpectedly negotiated Push')
        if direct!='off' and 'transcode' not in str(state.get('mimDirectSessionState','')).lower():
            raise RuntimeError('Direct Transcode not actually active: '+str(state.get('mimDirectSessionState')))
        events=call_dict(client,'dev_player_events').get('events',[]) if os.environ.get('SAGETV_STARTUP_CASES')=='events' else []
        row={'case':label,'player':player,'selected':transport,
             'watchAckMs':ack_ms,'firstObservedAvMs':first,'pollMaxMs':poll_max,'verificationMs':2000,'samples':samples,
             'persistedStreaming':streaming_preference(transport),
             'context':context,'serial':serial,'tuning':tuning,
             'callbackEvents':events,
             'runtime':{k:v for k,v in state.items() if k.startswith(('health_','mimDirect'))},'passed':True}
        rows.append(row);print(json.dumps({'case':label,'firstObservedAvMs':first,'watchAckMs':ack_ms,'pollMaxMs':poll_max,
            'actualPush':state.get('health_pushMode'),'directState':state.get('mimDirectSessionState'),'passed':True}),flush=True)
        time.sleep(2)
    complete=True
except BaseException as error:
    failure=type(error).__name__+': '+str(error)[:1000];raise
finally:
    try:cleanup['exit']=call_dict(client,'dev_exit_session',{'stop_app':True},timeout=30)
    except Exception as error:cleanup['exitError']=str(error)[:300]
    if checkpoint:
        try:cleanup['settings']=call_dict(client,'dev_restore_settings',timeout=30)
        except Exception as error:cleanup['settingsError']=str(error)[:300]
    if lease:
        try:cleanup['power']=awake.end(serial,awake.state_path(serial),'automated')
        except Exception as error:cleanup['powerError']=str(error)[:300]
    client.close()
    (OUT/(stamp+'-startup.json')).write_text(json.dumps({'complete':complete,'server':server,'fixture':fixture,
        'physicalDevice':True,'hdmiPictureVerified':False,'rows':rows,'failure':failure,'cleanup':cleanup},indent=2)+'\n')
