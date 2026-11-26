#!/usr/bin/env python3
"""EXPERIENCE-002 exact Non-Pro/stock232 production-menu handoff gate.

Uses existing MCP/public API and Android UI actions, no injected frame ACK,
bandwidth claim, private Core controls, player polling in production or tokens
in reports. Device screenshots are separate from HDMI/audible-sync evidence.
"""
import argparse,hashlib,json,os,re,shlex,signal,subprocess,sys,time,uuid
from pathlib import Path
import xml.etree.ElementTree as ET
from urllib.request import urlopen
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'mcp/src'))
from mcp_seek_suite import MCPProcess,call_dict,initialize
from mcp_config_values import FIXED_DEFAULTS
from mcp_ui_roots import wait_automation_root
from sagetv_dev_mcp.adb import AdbClient
from sagetv_dev_mcp.config import load_test_environment
from sagetv_dev_mcp.core_mcp_api import CoreMcpApiClient
import android_test_keep_awake as awake
from mcp_playback_experience import read_trace,require_seek

ROOT=Path(__file__).resolve().parents[1]
# The stdio child does not inherit this script's sys.path. Propagate the real
# adapter module path for direct reusable-container runs too.
os.environ['PYTHONPATH']=str(ROOT/'mcp/src')+(os.pathsep+os.environ['PYTHONPATH'] if os.environ.get('PYTHONPATH') else '')
def terminate_owned_gate(signum,frame):raise KeyboardInterrupt('Owned gate cancellation')
signal.signal(signal.SIGTERM,terminate_owned_gate)
parser=argparse.ArgumentParser();parser.add_argument('--player',choices=['media3','exoplayer','gsy-media3','gsy-exoplayer'],default='media3')
parser.add_argument('--recovery',action='store_true',help='Manual two-way action after real cooldown; not measured WAN evidence')
parser.add_argument('--experience',action='store_true',help='Read bounded requested/accepted/unknown-presentation diagnostics; restore opt-in')
parser.add_argument('--quality-fault',choices=['prepare','candidate-media','seek','stop','old-quality','provider-missing'],help='External exact-session fault/capability profile; no production mutation')
parser.add_argument('--fallback',choices=['off','copy'],help='Focused native/Copy playback compatibility, not adaptive encoding')
parser.add_argument('--native-qsv-check',action='store_true',help='MIM-QSV-001 classic Fixed repeated seek, settled captions and actual EOF; requires --fallback off')
parser.add_argument('--capture-ready-file',type=Path,help='Native QSV live-stage marker, restricted to its owning output directory')
parser.add_argument('--hls',action='store_true',help='Explicit HLS selection; reuse existing owned quality/seek/cleanup checks')
parser.add_argument('--require-responsive-hls',action='store_true',help='Require the new accepted responsive profile; legacy HLS is not a PASS')
parser.add_argument('--runtime-rate',action='store_true',help='Require actual in-place rate control; never count a handoff fallback')
parser.add_argument('--mim-test-override',action='store_true',help='HLS-005 exact-session shared MIM test override/clear; no measured-bandwidth input')
parser.add_argument('--buffer-adaptive-network',action='store_true',help='HLS-005 real constrained HLS delivery; automatic shared MIM policy, no override')
parser.add_argument('--candidate-apk',type=Path,help='Explicit built candidate under source/dev/android-tv/build; byte-verify actual installed package')
parser.add_argument('--eof',action='store_true',help='Seek near the canonical fixture end and observe actual decoder EOF')
parser.add_argument('--caption-stress',action='store_true',help='Use canonical long500ms caption fixture for a settled affected-caption follow-up')
parser.add_argument('--network-proxy',choices=['192.168.10.66:31911','192.168.10.77:31911'],help='Explicit bounded external shaper; restores Android global proxy in finally')
parser.add_argument('--network-shaped',action='store_true',help='External stock232 exact-client OS traffic shaper owns the rate limit')
parser.add_argument('--network-reverse',action='store_true',help='Existing232 namespace reverse shaper on optional port31911; preferences restored')
parser.add_argument('--healthy-only',action='store_true',help='Isolate unthrottled native/reverse delivery with no adaptation and no intermediate snapshots')
parser.add_argument('--seek-guard-only',action='store_true',help='Bounded first-frame/seek-custody diagnostic; no quality action')
parser.add_argument('--preview-only',action='store_true',help='Explicit production preview menu/current-source/coexistence; no quality action')
parser.add_argument('--observe-seconds',type=int,choices=range(0,16),default=0,help='Bounded post-seek read-only observation window');args=parser.parse_args()
OUT=Path(os.environ['SAGETV_ARTIFACT_DIR']).resolve()
expected_task='EXPERIENCE-003' if args.preview_only else 'EXPERIENCE-002'
if args.hls:
    if args.preview_only or args.fallback:raise SystemExit('HLS selection cannot be combined with preview/fallback profiles')
    expected_task='HLS-001'
if args.require_responsive_hls:
    if not args.hls:raise SystemExit('Responsive acceptance requires --hls')
    expected_task='HLS-002'
if args.runtime_rate:
    if not args.hls or args.quality_fault or args.fallback:raise SystemExit('Runtime qualification requires explicit HLS without fallback/fault profiles')
    expected_task='HLS-003'
if args.native_qsv_check:
    if args.fallback!='off' or args.hls or args.quality_fault:raise SystemExit('Native QSV check requires only --fallback off')
    expected_task='MIM-QSV-001'
if args.mim_test_override:
    if not args.hls or args.quality_fault or args.fallback or args.network_reverse or args.network_shaped or args.network_proxy:
        raise SystemExit('MIM test override requires only owned HLS scope; runtime-rate remains an explicit strict requirement')
    expected_task='HLS-005'
if args.buffer_adaptive_network:
    if not args.hls or not args.network_reverse or args.mim_test_override or args.quality_fault or args.fallback or args.runtime_rate:
        raise SystemExit('Buffer policy gate requires owned HLS/reverse delivery without manual/fault/runtime profiles')
    expected_task='HLS-005'
if OUT!=(ROOT/'artifacts/active'/expected_task).resolve():raise SystemExit('Use the owning '+expected_task+' output directory')
if args.capture_ready_file and (not args.native_qsv_check or args.capture_ready_file.resolve().parent!=OUT):raise SystemExit('Capture marker requires the native QSV owning output directory')
OUT.mkdir(parents=True,exist_ok=True)
env=load_test_environment();serial=env.device_serial();server=env.server_address()
if serial!=env.device_serial('non_pro') or server!=env.server_address('vibe'):raise SystemExit('Exact configured Non-Pro/stock232 scope required')
if args.quality_fault and (not args.network_reverse or args.fallback):raise SystemExit('Fault profile requires only the external reverse owner')
network_gate=bool(args.network_proxy or args.network_shaped or args.network_reverse) and not args.quality_fault
fixture='/var/media/OpenSageTV_Vibe_Tests/VibeCaptionTest-1080i-CEA608-90s-2s.ts'
if network_gate or args.healthy_only or args.caption_stress or args.runtime_rate or args.mim_test_override:fixture='/var/media/OpenSageTV_Vibe_Tests/VibeSeekTest-1080i-MPEG2-AC3-CC.ts'
api=CoreMcpApiClient.discover(server)
if api._request('server.activity').get('safeToRestart') is not True:raise SystemExit('Independent idle preflight required')
media=api.resolve_exact_path(fixture);media_id=media.get('mediaFileId')
if not media_id:raise SystemExit('Canonical fixture unavailable')
stamp=time.strftime('%Y%m%dT%H%M%SZ',time.gmtime())
apk=ROOT/'artifacts/firetv/OpenSageTV-Vibe-Android-Client-debug.apk'
if args.candidate_apk:
    candidate=args.candidate_apk.resolve()
    if candidate!=(ROOT/'source/dev/android-tv/build/outputs/apk/debug/android-tv-debug.apk').resolve():raise SystemExit('Exact built debug APK required')
    apk=candidate
with apk.open('rb') as apk_file:apk_sha=hashlib.file_digest(apk_file,'sha256').hexdigest()
client=MCPProcess();adb=AdbClient(serial,'opensagetv.vibe.miniclient.debug')
checkpoint=False;lease=False;complete=False;failure=None;rows=[];cleanup={};context=None;original_cc=None;installed_version=None
previous_proxy=None
runtime_site_original=None
last_snapshot={}
class ScopedGateComplete(Exception):pass

def hierarchy():
    remote='/data/local/tmp/vibe-direct-quality-'+uuid.uuid4().hex+'.xml'
    try:
        adb.shell('uiautomator dump '+remote,timeout=30)
        text=adb.shell('cat '+remote)
        return ET.fromstring(text[text.index('<hierarchy'):text.rindex('</hierarchy>')+12])
    finally:adb.shell('rm -f '+remote,check=False)

def tap(title):
    for attempt in range(5):
        tree=hierarchy()
        for node in tree.iter('node'):
            if (node.get('text') or '').split('\n')[0].casefold()!=title.casefold():continue
            coords=list(map(int,re.findall(r'\d+',node.get('bounds',''))))
            if len(coords)==4 and coords[2]>coords[0] and coords[3]>coords[1]:
                adb.shell('input tap '+str((coords[0]+coords[2])//2)+' '+str((coords[1]+coords[3])//2));return
        scroll=next((n for n in tree.iter('node') if n.get('scrollable')=='true'),None)
        if scroll is None:break
        x1,y1,x2,y2=map(int,re.findall(r'\d+',scroll.get('bounds')))
        adb.shell(f'input swipe {(x1+x2)//2} {y2-30} {(x1+x2)//2} {y1+30} 350')
    labels=[n.get('text') for n in hierarchy().iter('node') if n.get('text')]
    raise RuntimeError('Production menu entry not visible: '+title+' / '+str(labels)[:1200])

def state():
    global last_snapshot
    last_snapshot=call_dict(client,'dev_player_state');return last_snapshot
def automatic_value():
    tree=hierarchy();parents={child:parent for parent in tree.iter() for child in parent}
    for node in tree.iter('node'):
        if node.get('text')!='Automatic Bitrate Adjustment':continue
        ancestor=node
        for _ in range(4):
            ancestor=parents.get(ancestor)
            if ancestor is None:break
            values={n.get('text') for n in ancestor.iter('node') if n.get('text','').startswith(('Off • Next playback','Stream Switching • Next playback','Prefer In-place (Fallback to Switching) • Next playback'))}
            if len(values)==1:return 'Off' if values.pop().startswith('Off') else 'On'
    raise RuntimeError('Automatic-quality UI value is not unambiguous')
def await_state(predicate,seconds,label,allow_quality_failure=False):
    end=time.monotonic()+seconds;last={}
    while time.monotonic()<end:
        last=state()
        if last.get('health_playerError'):raise RuntimeError(str(last['health_playerError']))
        if last.get('mimDirectQualityFailure') and not allow_quality_failure:raise RuntimeError('Direct quality phase: '+str(last['mimDirectQualityFailure']))
        if predicate(last):return last
        time.sleep(.2)
    raise RuntimeError(label+'; quality='+str(last.get('mimDirectQualityState'))+'; target='+str(last.get('mimDirectVideoTargetKbps')))
def compact(s):
    return {k:s.get(k) for k in ['versionName','versionCode','clientId','serverAddress','mediaTimeMs',
        'mimDirectRequestedMode','mimHlsOwned','mimHlsProfile','mimDirectSessionState','mimDirectQualityState','mimDirectQualityFailure','mimDirectQualityObservation','mimDirectVideoTargetKbps','health_pushMode',
        'health_videoRendered','health_audioRendered','health_playerPositionMs','health_firstVideoFrameRendered','health_playerError',
        'health_bufferedPositionMs',
        'health_seekPending','health_flushed',
        'serverRequestedSeekMs','serverSeekSequence','serverFlushSequence','serverAnchorSequence','bufferLeft',
        'health_videoDecoder','health_videoDecoderKind','health_audioDecoder','health_audioDecoderKind',
        'fixedCaptionSideChannelState','fixedCaptionReceivedPackets','legacyCaptionWireEventCount','health_isPlaying','health_playWhenReady','health_playbackState']}

def active_sessions():
    with urlopen('http://'+server+':31910/v1/capabilities',timeout=3) as reply:return json.load(reply)['mimDirect']['activeSessions']

def wait_sessions(expected,seconds=12):
    end=time.monotonic()+seconds
    while True:
        if active_sessions()==expected:return
        if time.monotonic()>=end:raise AssertionError('Owned producer count not settled at '+str(expected))
        time.sleep(.2)

try:
    if args.mim_test_override and args.runtime_rate:
        runtime_site_original=api._request('plugin.config_get',plugin_id='sagetvffmpegpluginlinux',setting='stdinctrl.runtime_bitrate_control')['value']
        if runtime_site_original not in ('true','false'):raise RuntimeError('Unknown runtime site setting')
        if runtime_site_original=='false':
            api._request('plugin.config_set',plugin_id='sagetvffmpegpluginlinux',setting='stdinctrl.runtime_bitrate_control',expected='false',value='true',confirm='true')
    initialize(client);call_dict(client,'adb_connect',timeout=30);adb.connect()
    if adb.shell('getprop ro.product.model').strip()!='AFTMM':raise RuntimeError('Wrong model')
    installed=adb.shell('dumpsys package opensagetv.vibe.miniclient.debug')
    actual_version=re.search(r'\bversionName=([^\s]+)',installed)
    if not actual_version or not actual_version.group(1).startswith('0.5.115'):raise RuntimeError('New115 APK is not installed')
    installed_version=actual_version.group(1)
    installed_path=adb.shell('pm path opensagetv.vibe.miniclient.debug').strip()
    if not installed_path.startswith('package:') or '\n' in installed_path:raise RuntimeError('Unambiguous installed APK path required')
    installed_path=installed_path[len('package:'):]
    if not re.fullmatch(r'/data/app/[A-Za-z0-9._~+/=-]+/base\.apk',installed_path):raise RuntimeError('Unexpected installed APK scope')
    digest_output=adb.shell('sha256sum '+shlex.quote(installed_path),check=False).split()
    if digest_output and re.fullmatch('[0-9a-f]{64}',digest_output[0]):actual_apk_sha=digest_output[0]
    else:
        # FireOS/API25 lacks sha256sum. Do not install device tools, weaken the
        # byte witness, reset ADB or stage another APK copy. Hash the exact
        # package's binary SDK exec-out stream in memory, with a60s bound and
        # exact known APK length. Never send binary data into the text shell.
        installed_bytes=subprocess.run(adb._base(True)+['exec-out','cat',installed_path],stdin=subprocess.DEVNULL,
            stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=60)
        if installed_bytes.returncode or len(installed_bytes.stdout)!=apk.stat().st_size:raise RuntimeError('Installed APK byte witness unavailable')
        actual_apk_sha=hashlib.sha256(installed_bytes.stdout).hexdigest();del installed_bytes
    if actual_apk_sha!=apk_sha:raise RuntimeError('Installed APK bytes do not match the canonical build; wait for actual install Success')
    saved=call_dict(client,'dev_checkpoint_settings');assert saved.get('captured'),saved;checkpoint=True
    if args.network_proxy:
        observed_proxy=adb.shell('settings get global http_proxy').strip()
        if not re.fullmatch(r'[A-Za-z0-9.:-]+',observed_proxy):raise RuntimeError('Unrecognized existing proxy; preserve unchanged')
        previous_proxy=observed_proxy
        adb.shell('settings put global http_proxy '+args.network_proxy)
        assert adb.shell('settings get global http_proxy').strip()==args.network_proxy
    lease=bool(awake.begin(serial,awake.state_path(serial),'automated').get('active'))
    clean=call_dict(client,'dev_prepare_clean_start',{'wake':True,'graceful_timeout_s':2.0},timeout=30)
    assert clean.get('readyToLaunch'),clean
    player='gsyplayer' if args.player.startswith('gsy-') else args.player
    engine={'gsy-media3':'media3','gsy-exoplayer':'legacy_exo'}.get(args.player,'auto')
    diagnostic_settings={'client_web_enabled':True,'client_web_port':33717} if args.experience else {}
    call_dict(client,'dev_set_player_config',dict(player=player,gsy_engine=engine,streaming='fixed',decoding='hardware',**diagnostic_settings,
        **dict(FIXED_DEFAULTS,mim_direct_mode='hls' if args.hls else args.fallback or 'transcode',mim_direct_deinterlace='on',fixed_caption_side_channel_enabled=True,
               fixed_caption_side_channel_port=31911 if args.network_reverse else 31910,legacy_server_caption_mode='cc1',
               mim_direct_runtime_rate=args.runtime_rate,mim_direct_adaptive_quality=network_gate or args.mim_test_override or args.buffer_adaptive_network,
               mim_direct_minimum_kbps=1000 if args.mim_test_override or args.buffer_adaptive_network else '')))
    call_dict(client,'dev_connect_server',{'address':server,'port':31099,'save':False},timeout=30)
    wait_automation_root(client,timeout_s=30,stable_ms=700)
    print('READY exact Non-Pro25/stock232 root; starting owned canonical fixture',flush=True)
    connected=state();assert connected.get('serverAddress')==server and connected.get('clientId')==env.device()['client_id']
    context=api.resolve_context(connected['clientId'])
    original_cc=api.closed_caption_state(context)
    assert api.set_closed_caption_state(context,'CC1').get('ok')
    api.clear_watched(media_id);api._request('media.watch',context=context,media_id=media_id,wait_ms=0)
    before=await_state(lambda s:int(s.get('health_videoRendered',0))>5 and int(s.get('health_audioRendered',0))>5,45,'No startup output')
    if args.hls and args.quality_fault!='provider-missing':
        assert before.get('mimDirectRequestedMode')=='hls' and str(before.get('mimHlsOwned')).lower()=='true','HLS did not negotiate an owned HLS session'
    if args.require_responsive_hls:
        assert before.get('mimHlsProfile')=='responsive','Legacy HLS is not responsive profile acceptance'
    fallback_profile=args.fallback or (args.quality_fault if args.quality_fault in ('old-quality','provider-missing') else None)
    if fallback_profile:
        assert int(before.get('mimDirectVideoTargetKbps',0))==0,'Unsupported quality path unexpectedly enabled'
        if fallback_profile=='old-quality':assert before.get('mimDirectSessionState')=='active_transcode'
        if fallback_profile=='provider-missing':assert before.get('mimDirectSessionState')=='unavailable_stock_fixed'
        if fallback_profile=='copy':assert before.get('mimDirectSessionState')=='active_copy','Copy must remain Copy, not a disguised transcode'
    else:
        assert before.get('mimDirectQualityState')==('in_place_ready' if args.runtime_rate else 'ready') and int(before.get('mimDirectVideoTargetKbps',0))>=1000,compact(before)
        assert int(before['mimDirectVideoTargetKbps'])==FIXED_DEFAULTS['fixed_video_bitrate_kbps'],'Negotiated saved Fixed ceiling ignored'
    call_dict(client,'dev_ensure_fullscreen_playback',{'timeout_s':8.0},timeout=20)
    print('READY actual Direct startup; opening production quality menu',flush=True)
    adb.screenshot(OUT/(stamp+'-'+args.player+'-before.png'))
    rows.append({'gate':'actual-direct-startup' if not fallback_profile else 'optional-quality-fallback-startup-'+fallback_profile,'passed':True,'state':compact(before)})
    initial_trace=read_trace() if args.experience else None
    if initial_trace:rows[-1]['experienceTrace']=initial_trace
    if args.mim_test_override:
        # No other server client was active at preflight; select only the sole
        # newly reporting eligible controller, never an arbitrary first row.
        def controllers():return json.loads(api._request('plugin.adaptive_test',plugin_id='sagetvffmpegpluginlinux',operation='status')['sessions'])
        api._request('media.seek',context=context,target_ms=0)
        deadline=time.monotonic()+30;selected=None
        while time.monotonic()<deadline:
            candidates=[p for p in controllers() if p['eligible']]
            if len(candidates)>1:raise AssertionError('Ambiguous active controller')
            if len(candidates)==1:selected=candidates[0];break
            time.sleep(1)
        assert selected and selected['minimumKbps']==1000 and selected['maximumKbps']==4000,controllers()
        handle=selected['sessionId']
        def exact():return next(p for p in controllers() if p['sessionId']==handle)
        def target(kbps):
            p=exact();return api._request('plugin.adaptive_test',plugin_id='sagetvffmpegpluginlinux',operation='override' if kbps else 'clear',
                session_id=handle,expected_revision=p['revision'],target_kbps=kbps,lease_seconds=120 if kbps else 0,confirm='true')
        try:
            for kbps in (2000,2500):
                if kbps==2500:
                    until=time.monotonic()+(16 if args.runtime_rate else 61)
                    while time.monotonic()<until:time.sleep(min(10,until-time.monotonic()))
                before_frames=int(state().get('health_videoRendered',0));target(kbps)
                result=await_state(lambda s:int(s.get('mimDirectVideoTargetKbps',0))==kbps
                    and s.get('mimDirectQualityState')==('in_place_applied' if args.runtime_rate else 'presented')
                    and int(s.get('health_videoRendered',0))>(before_frames+5 if args.runtime_rate else 5),25,'MIM test target not rendered')
                until=time.monotonic()+10
                while exact()['observedKbps']!=kbps:
                    if time.monotonic()>until:raise AssertionError('MIM owner did not observe applied client target')
                    time.sleep(1)
                rows.append({'gate':'shared-mim-test-override','targetKbps':kbps,'passed':True,'state':compact(result)})
            target(0);assert exact()['overrideKbps']==0
            rows.append({'gate':'shared-mim-clear-resumes-policy','passed':True})
        finally:
            if exact()['overrideKbps']:target(0)
        complete=True;raise ScopedGateComplete()
    if args.buffer_adaptive_network:
        # The external owner changes delivered body capacity, never the MIM
        # target, report fields, controller clock or renderer acknowledgement.
        baseline=state();time.sleep(12);healthy=state()
        assert int(healthy['mimDirectVideoTargetKbps'])==4000
        assert int(healthy['mediaTimeMs'])-int(baseline['mediaTimeMs'])>=8000
        rows.append({'gate':'shared-buffer-healthy-holds-maximum','passed':True,'state':compact(healthy)})
        print('READY production automatic-quality buffer-only opt-in; awaiting actual constrained reads/buffering',flush=True)
        reduced=await_state(lambda s:1000<=int(s.get('mimDirectVideoTargetKbps',0))<4000
            and s.get('mimDirectQualityState')=='presented' and int(s.get('health_videoRendered',0))>5,
            110,'No automatic shared-buffer downshift/frame ACK')
        target=int(reduced['mimDirectVideoTargetKbps'])
        print('OBSERVED actual candidate rendered-frame commit; restore network capacity',flush=True)
        rows.append({'gate':'shared-buffer-automatic-downshift','passed':True,'state':compact(reduced)})
        raised=await_state(lambda s:target<int(s.get('mimDirectVideoTargetKbps',0))<=4000
            and s.get('mimDirectQualityState')=='presented' and int(s.get('health_videoRendered',0))>5,
            115,'No automatic shared-buffer recovery/frame ACK')
        new_target=int(raised['mimDirectVideoTargetKbps'])
        rows.append({'gate':'shared-buffer-automatic-recovery','passed':True,'state':compact(raised)})
        assert api.current_media_file_id(context)==media_id
        assert api._request('media.seek',context=context,target_ms=42000).get('ok')
        sought=await_state(lambda s:not s.get('health_seekPending') and s.get('health_isPlaying') is True
            and int(s.get('mediaTimeMs',0))>=42500 and int(s.get('health_videoRendered',0))>5,
            25,'No rendered post-adaptation seek')
        assert int(sought['mimDirectVideoTargetKbps'])==new_target
        assert api.media_control(context,'pause').get('ok')
        held=await_state(lambda s:s.get('health_isPlaying') is False,8,'No pause after adaptation')
        time.sleep(4);still=state()
        assert int(still['mimDirectVideoTargetKbps'])==new_target
        assert abs(int(still['mediaTimeMs'])-int(held['mediaTimeMs']))<600
        assert api.media_control(context,'play').get('ok')
        resumed=await_state(lambda s:s.get('health_isPlaying') is True and
            int(s.get('health_videoRendered',0))>int(still.get('health_videoRendered',0)) and
            int(s.get('health_audioRendered',0))>int(still.get('health_audioRendered',0)),15,'No resumed A/V')
        rows.append({'gate':'shared-buffer-seek-pause-resume','passed':True,'state':compact(resumed)})
        adb.screenshot(OUT/(stamp+'-'+args.player+'-automatic-caption.png'))
        print('PASS shared MIM automatic buffer-only down/up/seek/pause/resume '+args.player,flush=True)
        complete=True;raise ScopedGateComplete()
    if args.preview_only:
        call_dict(client,'dev_show_active_player_adjustments',{'panel':'diagnostics'})
        labels=[n.get('text') for n in hierarchy().iter('node') if n.get('text')]
        supported=not (args.fallback=='off' or args.quality_fault=='provider-missing')
        # The shared menu explains unavailable optional tools without making
        # them focusable/clickable; ordinary Direct/native fallback continues.
        if not supported:
            tree=hierarchy();parents={c:p for p in tree.iter() for c in p}
            item=next((n for n in tree.iter('node') if n.get('text','').casefold()=='source previews'),None)
            assert item is not None,'Unavailable optional tool lacks its explanation'
            while item is not None and item.get('enabled')!='false':item=parents.get(item)
            assert item is not None,'Unsupported provider exposed active preview controls'
            assert any('compatible provider required' in (n.get('text') or '').casefold() for n in tree.iter('node'))
            adb.shell('input keyevent 4')
            after=state();assert int(after.get('health_videoRendered',0))>int(before.get('health_videoRendered',0))
            rows.append({'gate':'missing-preview-preserves-native-controls','passed':True,'state':compact(after)})
        else:
            tap('Source previews')
            def preview_status(prefix):
                end=time.monotonic()+12
                while time.monotonic()<end:
                    texts=[n.get('text') or '' for n in hierarchy().iter('node')]
                    found=next((t for t in texts if t.startswith(prefix)
                        and (prefix!='Source ' or re.match(r'^Source \d+:\d{2}\b',t))),None)
                    if found:return found
                    time.sleep(.2)
                raise AssertionError('Production preview status missing: '+prefix+' / '+str(texts))
            first=preview_status('Source ')
            adb.screenshot(OUT/(stamp+'-'+args.player+'-preview-current.png'))
            time.sleep(2.1);tap('+10 s');second=preview_status('Source ')
            assert second!=first,'Explicit next preview did not advance its reported source bucket'
            adb.screenshot(OUT/(stamp+'-'+args.player+'-preview-next.png'))
            during=state();assert active_sessions()==1,'Preview must not create another playback producer'
            assert int(during.get('health_videoRendered',0))>int(before.get('health_videoRendered',0))
            assert int(during.get('health_audioRendered',0))>int(before.get('health_audioRendered',0))
            rows.append({'gate':'production-read-only-preview-coexists-with-av','passed':True,'first':first,'second':second,'state':compact(during)})
            assert api._request('media.seek',context=context,target_ms=42000).get('ok')
            await_state(lambda s:int(s.get('mediaTimeMs',0))>=42500 and int(s.get('health_videoRendered',0))>5,20,'Preview coexistence seek failed')
            time.sleep(2.1);tap('+10 s')
            # The old dialog's opaque custody cannot silently browse the new
            # seek token or draw a late image into a replacement source.
            preview_status('Preview unavailable')
            tap('Close');open_before=int(state()['mediaTimeMs'])
            call_dict(client,'dev_show_active_player_adjustments',{'panel':'diagnostics'});tap('Source previews')
            reopened=preview_status('Source ')
            minutes,seconds=map(int,re.search(r'Source (\d+):(\d{2})',reopened).groups())
            reopened_ms=(minutes*60+seconds)*1000
            open_after=int(state()['mediaTimeMs'])
            assert open_before//10000*10000<=reopened_ms<=open_after//10000*10000,'Preview bucket must track the current file clock, not a hardcoded minute'
            adb.screenshot(OUT/(stamp+'-'+args.player+'-preview-after-seek.png'))
            tap('Close');time.sleep(2)
            after=state();assert after.get('health_seekPending') is False and after.get('health_isPlaying') is True
            adb.screenshot(OUT/(stamp+'-'+args.player+'-preview-return-caption.png'))
            rows.append({'gate':'preview-custody-invalidated-by-seek-and-reopen','passed':True,'reopened':reopened,'state':compact(after)})
        complete=True;raise ScopedGateComplete()
    if fallback_profile:
        ui_before=api.ui_state(context)
        # Reuse the established verifier. Native basic Fixed deliberately
        # returns a ZERO-BASED decoder epoch; stock Core owns timestampOffset.
        # It verifies the public FILE clock plus a real new FLUSH/A/V recovery,
        # without changing the client or sending a second seek.
        verified=call_dict(client,'dev_server_seek_time',{'target_ms':42000,'tolerance_ms':2000,
            'timeout_s':30.0,'stable_ms':1200,'max_attempts':1},timeout=50)
        reply=verified.get('seekReply',{})
        rows.append({'gate':'fallback-seek-request-observation','passed':bool(verified.get('passed')),
                     'before':{key:ui_before.get(key) for key in ('mediaTimeMs','rawMediaTimeMs','durationMs','seekStartMs','seekEndMs','playbackRate')},
                     'reply':{key:reply.get(key) for key in ('requestedTargetMs','absoluteTargetMs','coordinate','accepted')},
                     'verification':{key:verified.get(key) for key in ('landed','recovered','reached_ms','positionCoordinate','clientDecoderTimeMs','seekAttemptCount')}})
        assert verified.get('passed'),'Fallback seek/output did not pass the established owning-clock verifier'
        assert int(reply['absoluteTargetMs'])>=int(ui_before.get('seekStartMs',0))+42000,'Public bridge clamped before the requested target'
        sought=await_state(lambda s:s.get('health_seekPending') is False and s.get('health_isPlaying') is True
            and int(s.get('health_videoRendered',0))>5,12,'Fallback seek has no rendered output')
        assert int(sought.get('mimDirectVideoTargetKbps',0))==0
        assert api.media_control(context,'pause').get('ok')
        await_state(lambda s:s.get('health_isPlaying') is False,8,'Fallback pause not applied')
        assert api.media_control(context,'play').get('ok')
        resumed=await_state(lambda s:s.get('health_isPlaying') is True and int(s.get('health_audioRendered',0))>5,12,'Fallback resume not advancing')
        time.sleep(2);adb.screenshot(OUT/(stamp+'-'+args.player+'-fallback-caption.png'))
        rows.append({'gate':'optional-quality-fallback-'+fallback_profile,'passed':True,'state':compact(resumed),
                     'wholePluginUninstalled':False,'apiCapabilityProfile':bool(args.quality_fault)})
        if args.experience:
            if verified.get('positionCoordinate')=='stock_server_file_relative':
                trace=read_trace()
                assert trace['sourceGeneration']==initial_trace['sourceGeneration']
                assert trace['requestedPositionMs'] is None and trace['producerAcceptedPositionMs'] is None,'FLUSH-only stock wire cannot supply a requested file time'
                assert 'operation-flush' in [event['phase'] for event in trace['events']]
            else:
                trace=read_trace(42000);require_seek(trace,42000,initial_trace['sourceGeneration'],fallback_profile in ('copy','old-quality'))
            rows[-1]['experienceTrace']=trace
        if args.native_qsv_check:
            hold=25 if args.capture_ready_file else 15
            if args.capture_ready_file:
                args.capture_ready_file.write_text(json.dumps({'scopeVerified':True,'serial':serial,'server':server,
                    'clientId':connected['clientId'],'fixture':fixture,'player':args.player,'stage':'native-qsv-post-42s-seek',
                    'expiresAtMs':round(time.time()*1000)+hold*1000})+'\n')
            print('READY native QSV42s seek; holding'+str(hold)+'s for independent HDMI caption capture',flush=True)
            start_frame=int(resumed.get('health_videoRendered',0));start_time=int(resumed.get('mediaTimeMs',0))
            time.sleep(hold);settled=state()
            assert settled.get('health_isPlaying') is True and int(settled.get('health_videoRendered',0))>start_frame+100,'Native post-seek output stopped'
            assert int(settled.get('mediaTimeMs',0))>start_time+5000,'Native decoder clock stalled'
            adb.screenshot(OUT/(stamp+'-'+args.player+'-native-qsv-caption.png'))
            rows.append({'gate':'native-qsv-settled-post-seek','passed':True,'state':compact(settled)})
            for target in (10000,70000):
                checked=call_dict(client,'dev_server_seek_time',{'target_ms':target,'tolerance_ms':2000,
                    'timeout_s':30.0,'stable_ms':1200,'max_attempts':1},timeout=50)
                assert checked.get('passed'),('Native ordered seek failed',target,checked)
                time.sleep(3);after=state()
                assert after.get('health_isPlaying') is True and int(after.get('health_videoRendered',0))>5
                rows.append({'gate':'native-qsv-ordered-seek','passed':True,'targetMs':target,
                    'verification':{k:checked.get(k) for k in ('recovered','landed','positionCoordinate','seekAttemptCount')},'state':compact(after)})
            duration=int(api.ui_state(context).get('durationMs',0));assert duration>80000
            checked=call_dict(client,'dev_server_seek_time',{'target_ms':duration-8000,'tolerance_ms':2000,
                'timeout_s':30.0,'stable_ms':1200,'max_attempts':1},timeout=50)
            assert checked.get('passed'),'Native EOF-tail seek failed'
            ended=await_state(lambda s:s.get('health_playbackState')==4,25,'Native decoder did not reach EOF')
            time.sleep(2);held=state()
            assert held.get('health_playbackState')==4 and not held.get('health_isPlaying'),'Native EOF replayed'
            rows.append({'gate':'native-qsv-actual-decoder-eof-no-replay','passed':True,'durationMs':duration,'state':compact(ended)})
        complete=True;raise ScopedGateComplete()
    if args.seek_guard_only:
        assert api._request('media.seek',context=context,target_ms=42000).get('ok')
        seek_ready=await_state(lambda s:int(s.get('mediaTimeMs',0))>=42500 and int(s.get('health_videoRendered',0))>5,20,'Seek presentation unavailable')
        print('OBSERVED seek guard after actual frames: '+json.dumps(compact(seek_ready)),flush=True)
        rows.append({'gate':'direct-seek-current-first-frame-clears-pending','passed':seek_ready.get('health_seekPending') is False,'state':compact(seek_ready)})
        assert seek_ready.get('health_seekPending') is False,'Direct replacement presented but seek guard stayed set'
        if args.experience:
            trace=read_trace(42000);require_seek(trace,42000,initial_trace['sourceGeneration'],True)
            rows[-1]['experienceTrace']=trace
        assert api.media_control(context,'pause').get('ok')
        paused=await_state(lambda s:s.get('health_isPlaying') is False,8,'Scoped seek pause not applied')
        time.sleep(1);held=state()
        assert abs(int(held['mediaTimeMs'])-int(paused['mediaTimeMs']))<600
        assert api.media_control(context,'play').get('ok')
        resumed=await_state(lambda s:s.get('health_isPlaying') is True and int(s.get('health_videoRendered',0))>int(held.get('health_videoRendered',0)),12,'Scoped seek resume not advancing')
        time.sleep(2)
        adb.screenshot(OUT/(stamp+'-'+args.player+'-scoped-seek-caption.png'))
        rows.append({'gate':'scoped-direct-seek-pause-resume','passed':True,'state':compact(resumed),
                     'captionVisualAcceptance':'Requires independent review of settled capture'})
        complete=True;raise ScopedGateComplete()
    if args.healthy_only:
        began=time.monotonic();baseline=state();time.sleep(12);healthy=state();elapsed=time.monotonic()-began
        advancement=int(healthy['mediaTimeMs'])-int(baseline['mediaTimeMs'])
        rows.append({'gate':'healthy-delivery-no-intermediate-poll','passed':advancement>=8000,
                     'elapsedMs':round(elapsed*1000),'advancedMs':advancement,'before':compact(baseline),'after':compact(healthy)})
        adb.screenshot(OUT/(stamp+'-'+args.player+'-healthy-caption.png'))
        assert advancement>=8000,'Healthy delivery is not near real-time even without intermediate observations'
        complete=True;raise ScopedGateComplete()
    call_dict(client,'dev_show_active_player_adjustments',{'panel':'transport'});tap('Current-session Bitrate')
    adb.screenshot(OUT/(stamp+'-'+args.player+'-quality-menu.png'))
    if network_gate:
        tap('Fixed Playback Settings')
        assert automatic_value()=='On','Production automatic quality is not enabled'
        adb.shell('input keyevent 4');adb.shell('input keyevent 4');adb.shell('input keyevent 4')
        print('READY production automatic-quality startup opt-in; awaiting actual constrained reads/buffering',flush=True)
    else:tap('Reduce current session')
    if args.quality_fault:
        if args.quality_fault in ('seek','stop'):
            # Prove the actual temporary allocation before superseding it;
            # no fake frame ACK or private server control is involved.
            wait_sessions(2)
            activity=api._request('server.activity')
            # Stock GetConnectedClients lists NetworkClient peers, not the
            # MiniClient UI. Require no such peers AND exactly our public UI.
            owned_ui_only=api.ui_context_names()==[context]
            cleanup['faultStimulusActivity']={key:activity.get(key) for key in ('recordingCount','uiContextCount','connectedClientCount','timestampMs')}
            cleanup['faultStimulusActivity']['ownedUiOnly']=owned_ui_only
            if activity.get('recordingCount')!=0 or activity.get('uiContextCount')!=1 or activity.get('connectedClientCount')!=0 or not owned_ui_only:
                raise RuntimeError('Preserve unrelated activity; abort owned fault stimulus')
            if args.quality_fault=='seek':
                assert api._request('media.seek',context=context,target_ms=42000).get('ok')
                settled=await_state(lambda s:s.get('health_seekPending') is False and s.get('health_isPlaying') is True
                    and int(s.get('mediaTimeMs',0))>=42500 and s.get('mimDirectQualityState')=='ready',25,'Superseding seek did not render',True)
                assert not settled.get('mimDirectQualityFailure'),'Superseding seek retained a quality failure'
                assert int(settled['mimDirectVideoTargetKbps'])==int(before['mimDirectVideoTargetKbps'])
                time.sleep(6);late=state();assert late.get('mimDirectQualityState')=='ready','Late candidate resurrected after seek'
                wait_sessions(1)
            else:
                assert api.media_control(context,'stop').get('ok')
                wait_sessions(0);time.sleep(6);late=state();wait_sessions(0)
                assert late.get('mimDirectQualityState') not in ('preparing','prepared','waiting_for_frame','committing','presented')
            rows.append({'gate':'actual-pending-quality-superseded-by-'+args.quality_fault,'passed':True,'state':compact(late)})
        else:
            # During a candidate error the old backend may be replaced. Only
            # accept restored CURRENT output, never transient counters/errors.
            end=time.monotonic()+40;restored=None
            while time.monotonic()<end:
                sample=state()
                if sample.get('mimDirectQualityState')=='retained_original' and sample.get('health_isPlaying') is True and not sample.get('health_playerError') and int(sample.get('health_videoRendered',0))>5 and int(sample.get('health_audioRendered',0))>5:
                    restored=sample;break
                time.sleep(.5)
            assert restored is not None,'Fault did not restore current original output'
            assert restored.get('mimDirectQualityFailure'),'Fault was not diagnosed'
            assert int(restored['mimDirectVideoTargetKbps'])==int(before['mimDirectVideoTargetKbps'])
            advancing=await_state(lambda s:s.get('health_isPlaying') is True and int(s.get('health_videoRendered',0))>int(restored['health_videoRendered'])
                and int(s.get('health_audioRendered',0))>int(restored['health_audioRendered']),12,'Restored original A/V not advancing',True)
            wait_sessions(1)
            rows.append({'gate':'retain-original-on-'+args.quality_fault+'-503','passed':True,'state':compact(advancing)})
        assert api.current_media_file_id(context)==media_id if args.quality_fault!='stop' else True
        adb.screenshot(OUT/(stamp+'-'+args.player+'-fault-result.png'))
        complete=True;raise ScopedGateComplete()
    target=max(250,int(before['mimDirectVideoTargetKbps'])*7//10)
    switched=await_state(lambda s:s.get('mimDirectQualityState')==('in_place_applied' if args.runtime_rate else 'presented') and
        (250<=int(s.get('mimDirectVideoTargetKbps',0))<int(before['mimDirectVideoTargetKbps']) if network_gate else int(s.get('mimDirectVideoTargetKbps',0))==target),90 if network_gate else 45,'No actual candidate frame/commit')
    target=int(switched['mimDirectVideoTargetKbps'])
    if args.runtime_rate:
        assert switched.get('mediaSourceIdentity')==before.get('mediaSourceIdentity') and before.get('mediaSourceIdentity'),'In-place control changed the media URI'
        assert int(switched.get('health_videoRendered',0))>int(before.get('health_videoRendered',0)),'In-place control replaced/reset the renderer'
        wait_sessions(1)
        rows.append({'gate':'same-source-renderer-inplace-rate','passed':True,'sourceIdentity':before['mediaSourceIdentity'],'targetKbps':target,'oneProducer':True})
    if network_gate:
        # Restore the external network at the actual rendered/committed frame
        # boundary, not after a fixed two-second playback deadline. A snapshot
        # can straddle a backend replacement, whose counters start over.
        if args.runtime_rate:print('OBSERVED actual in-place bitrate applied; restore network capacity',flush=True)
        else:print('OBSERVED actual candidate rendered-frame commit; restore network capacity',flush=True)
    settled=await_state(lambda s:s.get('health_isPlaying') is True and
        int(s.get('mimDirectVideoTargetKbps',0))==target and int(s.get('health_videoRendered',0))>5
        and int(s.get('health_audioRendered',0))>5,15,'Committed candidate did not resume')
    advancing=await_state(lambda s:s.get('health_isPlaying') is True and
        int(s.get('health_videoRendered',0))>int(settled.get('health_videoRendered',0)) and
        int(s.get('health_audioRendered',0))>int(settled.get('health_audioRendered',0)),10,'Candidate A/V did not advance')
    adb.screenshot(OUT/(stamp+'-'+args.player+'-after.png'))
    rows.append({'gate':'measured-shaped-network-frame-handoff' if network_gate else 'production-menu-actual-frame-handoff',
                 'passed':True,'manualStimulusNotWanEvidence':not network_gate,'state':compact(advancing)})
    print('PASS '+args.player+(' actual in-place bitrate/output' if args.runtime_rate else ' actual Direct quality frame ACK/output'),flush=True)
    seek=api._request('media.seek',context=context,target_ms=42000);assert seek.get('ok'),seek
    landed=call_dict(client,'dev_wait_for_media_position',{'target_ms':42000,'tolerance_ms':2500,'timeout_s':20.0},timeout=30)
    assert landed.get('passed'),landed
    # The public position can equal42000 at request acceptance before a new
    # decoder exists. Require actual replacement output, not that held anchor.
    after_seek=await_state(lambda s:s.get('health_seekPending') is False and
        s.get('health_isPlaying') is True and int(s.get('mediaTimeMs',0))>=42500 and
        int(s.get('health_videoRendered',0))>5 and int(s.get('health_audioRendered',0))>5,
        20,'Direct seek has no current rendered output/pending completion')
    assert int(after_seek.get('mimDirectVideoTargetKbps',0))==target
    assert api.current_media_file_id(context)==media_id,'Public UI source changed'
    print('READY stock42s seek; bounded observation '+str(args.observe_seconds)+'s',flush=True)
    if args.observe_seconds:time.sleep(args.observe_seconds)
    rows.append({'gate':'explicit-stock-seek-after-quality','passed':True,'state':compact(after_seek)})
    if args.experience:
        trace=read_trace(42000);require_seek(trace,42000,initial_trace['sourceGeneration'],True)
        rows[-1]['experienceTrace']=trace
    assert api.media_control(context,'pause').get('ok')
    paused=await_state(lambda s:s.get('health_isPlaying') is False,8,'Pause not applied')
    time.sleep(1);held=state()
    assert abs(int(held['mediaTimeMs'])-int(paused['mediaTimeMs']))<600
    assert api.media_control(context,'play').get('ok')
    resumed=await_state(lambda s:s.get('health_isPlaying') is True and int(s.get('health_videoRendered',0))>int(held.get('health_videoRendered',0)),12,'Resume not advancing')
    assert int(resumed.get('mimDirectVideoTargetKbps',0))==target
    adb.screenshot(OUT/(stamp+'-'+args.player+'-seek-caption-resume.png'))
    rows.append({'gate':'pause-resume-after-handoff-seek','passed':True,'state':compact(resumed),
                 'captionVisualAcceptance':'Requires review of owned screenshot; packet counters are not displayed text'})
    if network_gate:
        observed={'gate':'measured-shaped-network-recovery','passed':False,'manualStimulusNotWanEvidence':False,'samples':[]}
        rows.append(observed);end=time.monotonic()+110
        while time.monotonic()<end:
            sample=state();observed['samples'].append(compact(sample))
            if sample.get('mimDirectQualityFailure'):raise RuntimeError('Network recovery phase: '+str(sample['mimDirectQualityFailure']))
            if sample.get('mimDirectQualityState')==('in_place_applied' if args.runtime_rate else 'presented') and target<int(sample.get('mimDirectVideoTargetKbps',0))<=int(before['mimDirectVideoTargetKbps']):
                observed['passed']=True;break
            print('WAIT measured network recovery; observation='+str(sample.get('mimDirectQualityObservation'))+' target='+str(sample.get('mimDirectVideoTargetKbps'))+
                  ' buffered='+str(sample.get('health_bufferedPositionMs'))+' position='+str(sample.get('health_playerPositionMs')),flush=True)
            time.sleep(3)
        assert observed['passed'],'No measured network recovery within bounded observation'
        assert api.current_media_file_id(context)==media_id,'Network adaptation changed source'
        time.sleep(2) # Allow an actual new caption cue, not merely first-frame readiness.
        adb.screenshot(OUT/(stamp+'-'+args.player+'-network-recovery-caption.png'))
    if args.recovery:
        # Keep the canonical90s fixture within its real content duration while
        # waiting the provider's actual cooldown, never change server clocks or
        # manufacture a frame ACK to speed up the gate.
        assert api.media_control(context,'pause').get('ok')
        await_state(lambda s:s.get('health_isPlaying') is False,8,'Cooldown pause not applied')
        end=time.monotonic()+61
        while time.monotonic()<end:
            print('WAIT real recovery cooldown; current target '+str(state().get('mimDirectVideoTargetKbps')),flush=True)
            time.sleep(max(0,min(10,end-time.monotonic())))
        # UI automation itself takes wall time on this older device. Do not
        # let the90s fixture naturally finish and then mislabel a refused
        # post-EOS action as a recovery defect. Pause preserves custody; seek
        # five seconds and require new actual playback before the action.
        assert api._request('media.seek',context=context,target_ms=5000).get('ok')
        assert api.media_control(context,'play').get('ok')
        replay=call_dict(client,'dev_wait_for_media_position',{'target_ms':5000,'tolerance_ms':5000,'timeout_s':20.0},timeout=30)
        assert replay.get('passed'),replay
        await_state(lambda s:s.get('health_isPlaying') is True and s.get('mimDirectQualityState')==('in_place_ready' if args.runtime_rate else 'ready'),12,'Recovery source is not playing')
        call_dict(client,'dev_ensure_fullscreen_playback',{'timeout_s':8.0},timeout=20)
        call_dict(client,'dev_show_active_player_adjustments',{'panel':'transport'});tap('Current-session Bitrate');tap('Recover current session')
        recovered_target=min(int(before['mimDirectVideoTargetKbps']),target*5//4)
        recovered=await_state(lambda s:s.get('mimDirectQualityState')==('in_place_applied' if args.runtime_rate else 'presented') and int(s.get('mimDirectVideoTargetKbps',0))==recovered_target,45,'No recovery frame/commit')
        time.sleep(2) # First-frame ACK is not itself visible-caption acceptance.
        adb.screenshot(OUT/(stamp+'-'+args.player+'-recovery-caption.png'))
        assert int(recovered['mimDirectVideoTargetKbps'])<=FIXED_DEFAULTS['fixed_video_bitrate_kbps']
        rows.append({'gate':'production-menu-recovery-real-cooldown','passed':True,'manualStimulusNotWanEvidence':True,'state':compact(recovered)})
        if args.experience:
            trace=read_trace(5000);require_seek(trace,5000,initial_trace['sourceGeneration'],True)
            rows[-1]['experienceTrace']=trace
    if args.eof:
        # One ordered public seek, then require a real ENDED callback. A held
        # requested position or a completed FFmpeg process is not decoder EOF.
        duration=api.ui_state(context).get('durationMs',0)
        assert duration>10000,'Known canonical duration required'
        assert api._request('media.seek',context=context,target_ms=duration-6000).get('ok')
        tail=await_state(lambda s:s.get('health_isPlaying') is True and int(s.get('health_videoRendered',0))>5,25,'EOF tail has no actual frames')
        ended=await_state(lambda s:s.get('health_playbackState')==4,30,'HLS decoder never ended')
        time.sleep(2);after_end=state()
        assert after_end.get('health_playbackState')==4 and not after_end.get('health_isPlaying'),'EOF looped/restarted'
        rows.append({'gate':'actual-hls-decoder-eof-no-replay','passed':True,'durationMs':duration,'tail':compact(tail),'ended':compact(ended)})
    complete=True
except ScopedGateComplete:pass
except BaseException as error:
    failure=type(error).__name__+': '+str(error)[:1600]
    try:adb.screenshot(OUT/(stamp+'-'+args.player+'-failure.png'))
    except Exception:pass
    raise
finally:
    if context and original_cc is not None:
        try:cleanup['captions']=api.set_closed_caption_state(context,original_cc)
        except Exception as error:cleanup['captionError']=str(error)[:200]
    try:cleanup['exit']=call_dict(client,'dev_exit_session',{'stop_app':True},timeout=30)
    except Exception as error:cleanup['exitError']=str(error)[:200]
    if checkpoint:
        try:cleanup['settings']=call_dict(client,'dev_restore_settings',timeout=30)
        except Exception as error:cleanup['settingsError']=str(error)[:200]
    if lease:
        try:cleanup['power']=awake.end(serial,awake.state_path(serial),'automated')
        except Exception as error:cleanup['powerError']=str(error)[:200]
    if previous_proxy is not None:
        try:
            adb.shell('settings delete global http_proxy' if previous_proxy=='null' else 'settings put global http_proxy '+previous_proxy)
            if adb.shell('settings get global http_proxy').strip()!=previous_proxy:raise RuntimeError('Original proxy not restored')
            cleanup['networkProxyRestored']=True
        except Exception as error:cleanup['networkProxyError']=str(error)[:200]
    adb.close();client.close()
    try:
        end=time.monotonic()+25
        while True:
            activity=api._request('server.activity')
            if activity.get('safeToRestart') is True:break
            if time.monotonic()>=end:raise RuntimeError('Owned UI retirement/independent idle not established')
            time.sleep(.5)
        cleanup['independentFinalActivity']={k:activity[k] for k in ['recordingCount','uiContextCount','connectedClientCount','safeToRestart','timestampMs']}
        end=time.monotonic()+10
        while True:
            with urlopen('http://'+server+':31910/v1/capabilities',timeout=3) as reply:
                sessions=json.load(reply)['mimDirect']['activeSessions']
            if sessions==0:break
            if time.monotonic()>=end:raise RuntimeError('Owned Direct producer remains after UI cleanup')
            time.sleep(.5)
        cleanup['directActiveSessions']=sessions
    except Exception as error:cleanup['activityError']=str(error)[:200]
    if runtime_site_original=='false':
        try:
            api._request('plugin.config_set',plugin_id='sagetvffmpegpluginlinux',setting='stdinctrl.runtime_bitrate_control',expected='true',value='false',confirm='true')
            cleanup['runtimeSiteRestored']=api._request('plugin.config_get',plugin_id='sagetvffmpegpluginlinux',setting='stdinctrl.runtime_bitrate_control')['value']=='false'
        except Exception as error:cleanup['runtimeSiteError']=type(error).__name__
    if any(k.endswith('Error') for k in cleanup):complete=False
    (OUT/(stamp+'-'+args.player+'-quality.json')).write_text(json.dumps({'complete':complete,'server':server,'serial':serial,
        'player':args.player,'fixture':fixture,'installedVersion':installed_version,'apkSha256':apk_sha,'physicalDevice':True,'hdmiPictureVerified':False,
        'requestedSettings':{'streaming':'Fixed (Adjusted)' if args.mim_test_override or args.buffer_adaptive_network else 'Fixed',
            'adaptivePolicy':'shared-mim-buffer-only' if args.mim_test_override or args.buffer_adaptive_network else 'off',
            'minimumKbps':1000 if args.mim_test_override or args.buffer_adaptive_network else None,
            'maximumKbps':FIXED_DEFAULTS['fixed_video_bitrate_kbps'],
            'direct':'HLS' if args.hls else args.fallback or 'Transcode','decoding':'Hardware','deinterlace':'On','captions':'CC1/sidechannel'},
        'rows':rows,'lastState':compact(last_snapshot),'failure':failure,'cleanup':cleanup},indent=2)+'\n')
if not complete:raise SystemExit('Physical gate or restoration did not pass')
