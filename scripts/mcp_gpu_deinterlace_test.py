#!/usr/bin/env python3
"""Focused hardware/GPU test. Independent HDMI is still required for picture proof.

Run in the commissioned SDK Python with an explicit active/task output path.
Bracket direct invocations in test-awake begin/end. Preferences and server CC
are restored in finally; neither stock binaries nor media files are modified.
"""
from __future__ import annotations
import argparse
import json
import math
from pathlib import Path
import time
from mcp_seek_suite import MCPProcess, call_dict, initialize
from sagetv_dev_mcp.config import default_server_address, load_test_environment


class DirectSdkTools:
    """Explicit external-tooling alternative, never MCP-protocol qualification.

    Uses the same bounded tool functions/stock plugin APIs and device/context
    guards. No automatic switch, command replay or transport PASS substitution.
    """
    def __init__(self):
        from sagetv_dev_mcp import server
        self.server=server
    def request(self,method,params=None,timeout=30):
        if method!='tools/call':raise ValueError('direct SDK supports tool functions only')
        value=getattr(self.server,params['name'])(**params.get('arguments',{}))
        return {'result':{'structuredContent':value}}
    def close(self):self.server.adb.close()


def paused_clock_stable(previous,current):
    """A false playing flag can precede the already-submitted audio drain."""
    return bool(previous is not None
        and 'mediaTimeMs' in previous and 'mediaTimeMs' in current
        and previous.get('health_playWhenReady') is False
        and previous.get('health_isPlaying') is False
        and current.get('health_playWhenReady') is False
        and current.get('health_isPlaying') is False
        and abs(int(current.get('mediaTimeMs',0))-int(previous.get('mediaTimeMs',0)))<=10)


def cadence_source_fps(state,fixture_fps):
    """Closed GPU stages lose their FPS; use commissioned source metadata.

    Never substitute measured output rate for source rate or count unknown FPS
    as cadence qualification. The exact fixture path, not a hardcoded ID, owns
    this fallback. Native/bypass output can move while dropping source frames.
    """
    for value in (state.get('videoGpuCodedFps',0),fixture_fps):
        try: fps=float(value)
        except (TypeError,ValueError): continue
        if math.isfinite(fps) and 0<fps<=240:return fps
    return 0.0


def main() -> int:
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--server-path',default='')
    parser.add_argument('--self-test-only',action='store_true')
    parser.add_argument('--snapshot-only',action='store_true')
    parser.add_argument('--prepare-only',action='store_true',help='Gracefully stop only the selected Dev app before update installation')
    parser.add_argument('--restore-only',action='store_true',help='Restore a surviving private preference checkpoint after interrupted testing')
    parser.add_argument('--direct-tools',action='store_true',help='Explicit direct SDK tool-function calls; does not qualify MCP stdio')
    parser.add_argument('--same-activity',action='store_true',help='Next fixture on current matching Pull backend; prove unchanged process/Activity, never restart or renegotiate')
    parser.add_argument('--rewind-before-observation',action='store_true',help='One verified public seek to zero for a short resumed fixture, not runtime recovery')
    parser.add_argument('--output',required=True,type=Path)
    parser.add_argument('--player',choices=('media3','exoplayer','gsyplayer'),default='media3')
    parser.add_argument('--gsy-engine',choices=('auto','legacy_exo','media3','system'),default='')
    parser.add_argument('--streaming',choices=('pull','push','dynamic'),default='pull')
    parser.add_argument('--filter',choices=('fast','motion','inspect'),default='fast')
    parser.add_argument('--field-order',choices=('auto','top','bottom'),default='auto')
    parser.add_argument('--output-rate',choices=('frame','field'),default='frame')
    parser.add_argument('--deinterlace-mode',choices=('auto','off','client','client_gpu'),default='client_gpu')
    parser.add_argument('--expected',choices=('active','bypass','unavailable','native'),default='active')
    parser.add_argument('--controls',action='store_true')
    parser.add_argument('--server-upload',action='store_true',help='One opted-in production diagnostic receipt during matched HDMI playback; restore receiver/client settings')
    parser.add_argument('--hold-seconds',type=int,default=15)
    parser.add_argument('--profile',action='store_true',help='Finite 240-frame debug timing probe')
    parser.add_argument('--probe-fields',action='store_true',help='Explicit finite DEBUG field-ID probe before Watch; timing is unqualified during this measurement')
    parser.add_argument('--hide-overlay',action='store_true',help='Single-variable performance comparison without the stats drawing')
    parser.add_argument('--require-field-cadence',action='store_true',help='Require full 2x coded-field presentation, not only frame-rate processing')
    args=parser.parse_args()
    if not args.server_path and not (args.self_test_only or args.snapshot_only or args.prepare_only or args.restore_only): parser.error('--server-path is required for playback')
    if not 0 <= args.hold_seconds <= 60: parser.error('hold-seconds must be 0..60')
    # Reject raw output outside the owning active evidence tree.
    root=Path(__file__).resolve().parents[1]
    output=args.output.resolve()
    if not output.is_relative_to((root/'artifacts/active').resolve()):
        parser.error('output must be inside artifacts/active/<stable-task-ID>/')
    output.parent.mkdir(parents=True,exist_ok=True)
    result={'passed':False,'player':args.player,'streaming':args.streaming,
            'filter':args.filter,'fieldOrder':args.field_order,'outputRate':args.output_rate,
            'deinterlaceMode':args.deinterlace_mode,
            'expected':args.expected,'server':default_server_address(),
            'harnessTransport':'direct SDK functions; MCP stdio NOT qualified' if args.direct_tools else 'MCP stdio',
            'pictureEvidence':'independent HDMI required; counters are not picture proof'}
    client=DirectSdkTools() if args.direct_tools else MCPProcess()
    checkpoint=False
    original_cc=''
    upload_control=None
    original_receiver=None
    cases=[case for case in load_test_environment().fixture_cases(enabled_only=True)
           if case.get('path_type')=='server_path' and case.get('path')==args.server_path]
    if len(cases)==1:
        result['fixtureExpectation']={'id':cases[0].get('id'),
            'expectedVideoFps':cases[0].get('expected_video_fps',0)}
    try:
        if not args.direct_tools:initialize(client)
        result['adbConnect']=call_dict(client,'adb_connect',timeout=75)
        if args.restore_only:
            result['settingsRestore']=call_dict(client,'dev_restore_settings',timeout=100)
            result['passed']=bool(result['settingsRestore'].get('restored'))
            return 0 if result['passed'] else 1
        if args.prepare_only:
            result['cleanStart']=call_dict(client,'dev_prepare_clean_start',{
                'wake':True,'graceful_timeout_s':5},timeout=120)
            result['passed']=bool(result['cleanStart'].get('readyToLaunch'))
            return 0 if result['passed'] else 1
        if args.snapshot_only:
            result['state']=call_dict(client,'dev_player_state')
            result['scope']=call_dict(client,'dev_server_control_identity',timeout=40)
            state=result['state']
            if args.hold_seconds and state.get('playerActive') and result['scope'].get('scopeVerified'):
                output.with_suffix('.ready.json').write_text(json.dumps({
                    'scopeVerified':True,'scope':result['scope'],
                    'expiresAtMs':int((time.time()+args.hold_seconds)*1000),
                    'state':state.get('videoDeinterlaceState'),
                    'fixture':'current playback observation; no Watch or fixture change'}),encoding='utf-8')
                print('HDMI_READY_READ_ONLY',flush=True)
                for _ in range(args.hold_seconds):time.sleep(1)
                final=call_dict(client,'dev_player_state')
                result['afterHold']=final
                seconds=(int(final.get('health_capturedMonotonicMs',0))-int(state.get('health_capturedMonotonicMs',0)))/1000
                if seconds>0:
                    result['observedRates']={
                        'gpuFps':(int(final.get('videoGpuOutputFrames',0))-int(state.get('videoGpuOutputFrames',0)))/seconds,
                        'decoderFps':(int(final.get('health_videoRendered',0))-int(state.get('health_videoRendered',0)))/seconds,
                        'clockRatio':(int(final.get('mediaTimeMs',0))-int(state.get('mediaTimeMs',0)))/(seconds*1000)}
            result['passed']=True
            return 0
        if args.self_test_only:
            result['selfTest']=call_dict(client,'dev_gpu_filter_self_test',timeout=30)
            if not result['selfTest'].get('passed'): raise RuntimeError('GPU shader self-test failed')
            result['passed']=True
            return 0
        print('CHECKPOINT_AND_SAME_ACTIVITY' if args.same_activity else 'CHECKPOINT_AND_FRESH_SELECTED_DEVICE_START',flush=True)
        saved=call_dict(client,'dev_checkpoint_settings',timeout=30)
        if not saved.get('captured'): raise RuntimeError('settings checkpoint failed')
        checkpoint=True
        if args.server_upload:
            from sagetv_dev_mcp.core_mcp_api import discover_sage_control
            upload_control=discover_sage_control(default_server_address())
            original_receiver=upload_control._request('companion.config_get',setting='diagnostics.enabled')['value']
            if original_receiver!='true':
                upload_control._request('companion.config_set',setting='diagnostics.enabled',
                    expected=original_receiver,value='true',confirm=True)
            call_dict(client,'dev_set_player_config',{'server_diagnostics_enabled':True})
        before=call_dict(client,'dev_player_state')
        if before.get('connected'):
            original_cc=str(call_dict(client,'dev_stv_caption_state').get('state',''))
        # The bounded graceful-exit receiver itself allows up to90s before
        # force-stop. A shorter host RPC deadline kills cleanup in progress;
        # this outer deadline must cover it, without replaying the exit command.
        if args.same_activity:
            expected_class={'media3':'.media3.','exoplayer':'.exoplayer2.','gsyplayer':'.gsy.'}[args.player]
            if (not before.get('connected') or before.get('serverAddress')!=default_server_address()
                    or expected_class not in str(before.get('playerClass',''))
                    or args.streaming!='pull' or before.get('health_pushMode') is not False):
                raise RuntimeError('same-Activity gate requires the current matching backend/server/Pull connection')
            result['sameActivityBefore']=call_dict(client,'dev_app_status',timeout=60)
            if not result['sameActivityBefore'].get('foreground'):
                raise RuntimeError('same-Activity gate requires foreground playback')
        else:
            clean=call_dict(client,'dev_prepare_clean_start',{'wake':True,'graceful_timeout_s':2},timeout=120)
            if not clean.get('readyToLaunch'): raise RuntimeError('fresh process not ready')
        call_dict(client,'dev_set_player_config',{
            'player':args.player,'gsy_engine':args.gsy_engine,'streaming':args.streaming,
            'decoding':'hardware','mim_direct_mode':'off','video_deinterlace_mode':args.deinterlace_mode,
            'video_gpu_filter':args.filter,'video_gpu_field_order':args.field_order,
            'video_gpu_output_rate':args.output_rate})
        if not args.same_activity:
            call_dict(client,'dev_connect_server',{'address':default_server_address(),'save':False},timeout=45)
        # Socket connection precedes the server's STV initialization. A fresh
        # session must have a stable real menu before querying captions/Watch;
        # connected alone allowed GetCurrentSTVFile to race its null STV.
        # A same-Activity handoff already has playback, so never demand Main
        # Menu there or navigate away just to satisfy this harness gate.
        readiness={'connected':True,'timeout_s':30}
        if not args.same_activity:
            readiness.update(menu_present=True,automation_ready=True,stable_ms=500)
        ready=call_dict(client,'dev_wait_for_ui',readiness,timeout=40)
        result['uiReadiness']=ready
        if not ready.get('passed'): raise RuntimeError('connection did not become ready')
        result['scopeBeforeWatch']=call_dict(client,'dev_server_control_identity',timeout=40)
        if not result['scopeBeforeWatch'].get('scopeVerified'):
            raise RuntimeError('selected server UI context was not verified before Watch')
        print('SCOPE_VERIFIED '+json.dumps(result['scopeBeforeWatch']),flush=True)
        if not original_cc: original_cc=str(call_dict(client,'dev_stv_caption_state').get('state',''))
        if not original_cc: raise RuntimeError('server CC checkpoint unavailable')
        if args.probe_fields:
            if not cases or cases[0].get('modes',{}).get('field_geometry') is not True:
                raise RuntimeError('field probe requires the configured generated field-ID fixture')
            result['fieldProbeArm']=call_dict(client,'dev_gpu_field_probe_arm')
            if not result['fieldProbeArm'].get('accepted'):raise RuntimeError('field probe arm rejected')
        print('WATCH_SELECTED_DEVICE_ONLY',flush=True)
        if args.profile and args.expected!='native':
            result['profileRequest']=call_dict(client,'dev_gpu_output_profile',{'next_output':True})
            if not result['profileRequest'].get('accepted'):raise RuntimeError('bounded next-output profile rejected')
        started=call_dict(client,'dev_play_server_path',{'server_path':args.server_path,
            'timeout_s':45,'verify_ms':1500,'restart_from_beginning':True},timeout=365)
        result['start']=started
        current=call_dict(client,'dev_player_state')
        result['deviceIdentity']={key:current.get(key) for key in ('clientId','uiContextHint','serverAddress')}
        normalized=lambda value: ''.join(ch for ch in str(value).lower() if ch.isalnum())
        if normalized(started.get('uiContext'))!=normalized(current.get('clientId')):
            raise RuntimeError('server control context did not match selected Android identity')
        if not started.get('passed'): raise RuntimeError('playback startup failed')
        result['backendWitness']={key:current.get(key) for key in
            ('playerClass','health_backendClass','health_videoDecoder','health_videoDecoderKind')}
        if args.player=='gsyplayer' and args.gsy_engine in ('media3','legacy_exo'):
            delegate='.media3.' if args.gsy_engine=='media3' else '.exoplayer2.'
            if delegate not in str(current.get('health_backendClass','')):
                raise RuntimeError('requested GSY delegate was not proven by the active backend')
        samples=[]
        deadline=time.monotonic()+12
        while time.monotonic()<deadline:
            state=call_dict(client,'dev_player_state',timeout=30)
            samples.append(state)
            value=str(state.get('videoDeinterlaceState',''))
            if (args.expected=='active' and value=='Client GPU'
                or args.expected=='bypass' and value=='Bypass'
                or args.expected=='unavailable' and value=='Unavailable'
                or args.expected=='native' and value=='Device'): break
            time.sleep(.5)
        result['samples']=samples
        state=samples[-1]
        desired={'active':'Client GPU','bypass':'Bypass','unavailable':'Unavailable','native':'Device'}[args.expected]
        if state.get('videoDeinterlaceState')!=desired:
            raise RuntimeError('expected '+desired+', got '+str(state.get('videoDeinterlaceState'))
                               +'/'+str(state.get('videoGpuReason')))
        if args.expected=='active' and int(state.get('videoGpuFilteredFrames',0))<=0:
            raise RuntimeError('no actual filtered decoded frames')
        if args.streaming in ('push','dynamic') and not (state.get('health_pushMode') is True
                and int(state.get('health_dataSourcePushedBytes',0))>0):
            raise RuntimeError('requested Push was not proven by actual push/payload')
        if args.rewind_before_observation:
            result['fixtureRewind']=call_dict(client,'dev_server_seek_time',{'target_ms':0,
                'tolerance_ms':4000,'timeout_s':30},timeout=45)
            if not result['fixtureRewind'].get('passed'):raise RuntimeError('short-fixture rewind failed')
            state=call_dict(client,'dev_player_state')
            if state.get('videoDeinterlaceState')!=desired:raise RuntimeError('processing changed after fixture rewind')
        # Explicit mode owns the production overlay; supplying mode=detailed
        # with visible=False still SHOWS it. Preserve a truthful one-variable
        # hidden-overlay performance comparison rather than trusting that flag.
        overlay_mode='off' if args.hide_overlay else 'detailed'
        result['overlayRequest']=call_dict(client,'dev_set_active_player_overlay',{'mode':overlay_mode})
        if result['overlayRequest'].get('requestedMode')!=overlay_mode:
            raise RuntimeError('requested overlay mode was not accepted')
        # Cross-host capture may start only in this verified live stage. The
        # finite expiry prevents an old marker qualifying Home/after-controls.
        output.with_suffix('.ready.json').write_text(json.dumps({
            'scopeVerified':True,'scope':result['scopeBeforeWatch'],
            'expiresAtMs':int((time.time()+args.hold_seconds)*1000),
            'state':state.get('videoDeinterlaceState'),'outputFrames':state.get('videoGpuOutputFrames'),
            'fixture':args.server_path}),encoding='utf-8')
        print('HDMI_READY',flush=True)
        if args.server_upload:
            initial=call_dict(client,'dev_server_diagnostics')
            last=int(initial.get('serverDiagnosticsLastTime',0))
            result['uploadRequest']=call_dict(client,'dev_server_diagnostics',{'action':'server_upload'})
            deadline=time.monotonic()+35
            while True:
                receipt=call_dict(client,'dev_server_diagnostics')
                if (str(receipt.get('serverDiagnosticsStatus','')).startswith('Stored on server')
                        and int(receipt.get('serverDiagnosticsLastTime',0))>last):break
                if time.monotonic()>=deadline:raise RuntimeError('fresh server diagnostic receipt was not observed')
                time.sleep(.5)
            result['serverReceipt']=receipt
        for _ in range(args.hold_seconds): time.sleep(1)
        final=call_dict(client,'dev_player_state')
        result['afterHold']=final
        if final.get('videoDeinterlaceState')!=desired:
            result['nativeCounterProgressAfterModeChange']=(
                int(final.get('health_videoRendered',0))>int(state.get('health_videoRendered',0)))
            raise RuntimeError('processing changed during observation: '+str(final.get('videoDeinterlaceState'))
                               +'/'+str(final.get('videoGpuReason')))
        native_bypass=str(final.get('videoGpuReason','')).startswith('gpu_native_bypass_')
        output_key='health_videoRendered' if args.expected in ('native','unavailable') or native_bypass else 'videoGpuOutputFrames'
        if int(final.get(output_key,0))<=int(state.get(output_key,0)):
            raise RuntimeError('video presentation did not advance')
        seconds=(int(final.get('health_capturedMonotonicMs',0))-int(state.get('health_capturedMonotonicMs',0)))/1000
        if seconds>3:
            presented=(int(final.get(output_key,0))-int(state.get(output_key,0)))/seconds
            decoder=(int(final.get('health_videoRendered',0))-int(state.get('health_videoRendered',0)))/seconds
            coded=cadence_source_fps(final,result.get('fixtureExpectation',{}).get('expectedVideoFps',0))
            # Minimum frame-rate processing, not a claim of full field cadence.
            # A moving audio/timeline clock cannot qualify 20fps from 30fps input.
            minimum=coded*(2 if args.require_field_cadence else 1)*.9
            result['cadence']={'seconds':seconds,'presentedFps':presented,'decoderFps':decoder,
                'codedFps':coded,'minimumFps':minimum,
                'clockRatio':(int(final.get('mediaTimeMs',0))-int(state.get('mediaTimeMs',0)))/(seconds*1000),
                'passed':presented>=minimum if coded>0 else (False if args.require_field_cadence else None),
                'sourceRateKnown':coded>0,
                'fullFieldCadence':'requires separate field-retention/presentation evidence'}
        if args.controls:
            result['seek']=call_dict(client,'dev_server_seek_time',{'target_ms':25000,
                'tolerance_ms':4000,'timeout_s':30},timeout=45)
            if not result['seek'].get('passed'): raise RuntimeError('seek gate failed')
            result['pauseCommand']=call_dict(client,'dev_server_media_control',{'operation':'pause'})
            # Public Pause acknowledges enqueueing, not the remote renderer's
            # completed transition. Observe it, without replaying Pause, before
            # measuring a paused clock; normal in-flight frames are not drift.
            pause_deadline=time.monotonic()+5
            previous_pause=None
            pause_started=time.monotonic()
            pause_quiet_since=pause_started
            while True:
                a=call_dict(client,'dev_player_state')
                # A single200ms pair with50ms tolerance could accept a clock
                # still creeping150ms/s. Require a quiet half-second against
                # one anchor, not only false flags or adjacent small deltas.
                if paused_clock_stable(previous_pause,a):
                    if time.monotonic()-pause_quiet_since>=.5:break
                else:
                    previous_pause=a;pause_quiet_since=time.monotonic()
                if time.monotonic()>=pause_deadline:raise RuntimeError('pause did not settle')
                time.sleep(.2)
            result['pauseSettleMs']=round((time.monotonic()-pause_started)*1000)
            result['pauseSettled']={key:a.get(key) for key in
                ('mediaTimeMs','health_playWhenReady','health_isPlaying','health_capturedMonotonicMs')}
            time.sleep(1.2);b=call_dict(client,'dev_player_state')
            if (b.get('health_playWhenReady') is not False or b.get('health_isPlaying') is not False
                    or 'mediaTimeMs' not in b):raise RuntimeError('paused state did not remain settled')
            delta=abs(int(b.get('mediaTimeMs',0))-int(a.get('mediaTimeMs',0)))
            result['pauseDeltaMs']=delta
            if delta>100: raise RuntimeError('pause clock advanced')
            call_dict(client,'dev_server_media_control',{'operation':'play'})
            resumed=call_dict(client,'dev_wait_for_playback_started',{'timeout_s':20,'verify_ms':1500},timeout=30)
            result['resume']=resumed
            if not resumed.get('passed'): raise RuntimeError('resume gate failed')
            # Play can be acknowledged while the GPU queue is still in its
            # normal bounded re-anchor state. Observe the fresh decoded frame;
            # never replay Play or treat that transient as a processing loss.
            controls_deadline=time.monotonic()+5
            while True:
                after_controls=call_dict(client,'dev_player_state')
                if after_controls.get('videoDeinterlaceState')==desired:break
                if time.monotonic()>=controls_deadline:break
                time.sleep(.2)
            result['afterControls']=after_controls
            if after_controls.get('videoDeinterlaceState') != desired:
                raise RuntimeError('GPU processing changed after controls: '+str(after_controls.get('videoGpuReason')))
        if result.get('cadence',{}).get('passed') is False:
            raise RuntimeError('GPU video presentation below real-time coded-frame budget')
        if args.same_activity:
            after_activity=call_dict(client,'dev_app_status',timeout=60)
            result['sameActivityAfter']=after_activity
            first=result['sameActivityBefore']
            result['sameActivityVerified']=(bool(first.get('pids')) and first.get('pids')==after_activity.get('pids')
                    and first.get('resumedActivity')==after_activity.get('resumedActivity')
                    and bool(after_activity.get('foreground')))
            if not result['sameActivityVerified']:
                raise RuntimeError('process/foreground Activity changed during repeated surface gate')
        result['passed']=True
    except Exception as exc:
        result['error']=str(exc)
    finally:
        restore_errors=[]
        if args.probe_fields:
            try:result['fieldProbe']=call_dict(client,'dev_gpu_field_probe_status')
            except Exception as exc:result['fieldProbeError']=str(exc)
        if original_cc:
            try:
                result['ccRestore']=call_dict(client,'dev_set_stv_caption_state',{'state':original_cc},timeout=30)
                if str(result['ccRestore'].get('state',''))!=original_cc: raise RuntimeError('server CC restore mismatch')
            except Exception as exc: restore_errors.append('CC: '+str(exc))
        # A disconnected UI or failed caption restoration must NEVER prevent
        # restoring independent device preferences from their private checkpoint.
        if checkpoint:
            try:
                result['settingsRestore']=call_dict(client,'dev_restore_settings',timeout=100)
                if not result['settingsRestore'].get('restored'): raise RuntimeError('preferences restore failed')
            except Exception as exc: restore_errors.append('preferences: '+str(exc))
        if original_receiver is not None:
            try:
                current=upload_control._request('companion.config_get',setting='diagnostics.enabled')['value']
                if current!=original_receiver:
                    upload_control._request('companion.config_set',setting='diagnostics.enabled',
                        expected=current,value=original_receiver,confirm=True)
                result['receiverRestored']=upload_control._request('companion.config_get',setting='diagnostics.enabled')['value']==original_receiver
                if not result['receiverRestored']:raise RuntimeError('diagnostic receiver restore mismatch')
            except Exception as exc:restore_errors.append('receiver: '+str(exc))
        if restore_errors: result['passed']=False;result['restoreError']='; '.join(restore_errors)
        client.close()
        output.write_text(json.dumps(result,indent=2),encoding='utf-8')
    print(json.dumps({'passed':result['passed'],'output':str(output),'error':result.get('error'),
                      'restoreError':result.get('restoreError')},indent=2),flush=True)
    return 0 if result['passed'] else 1

if __name__=='__main__': raise SystemExit(main())
