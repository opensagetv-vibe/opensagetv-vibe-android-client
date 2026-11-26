#!/usr/bin/env python3
"""SETTINGS-002: exact Non-Pro/stock232 shared UI and session/default gates.

Private on-device settings checkpoint; no raw preferences in artifacts, no
Core/plugin update, no tuner or unrelated client controls. Completed fixture only.
"""
import argparse,hashlib,json,os,re,signal,subprocess,sys,time,uuid
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
import xml.etree.ElementTree as ET
from urllib.request import urlopen

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'mcp/src'))
os.environ['PYTHONPATH']=str(ROOT/'mcp/src')
from sagetv_dev_mcp.adb import AdbClient
from sagetv_dev_mcp.config import load_test_environment
from sagetv_dev_mcp.core_mcp_api import CoreMcpApiClient
from mcp_seek_suite import MCPProcess,call_dict,initialize
from mcp_ui_roots import wait_automation_root
from mcp_config_values import FIXED_DEFAULTS
import fixed_gate_policy as fixed_policy
import android_test_keep_awake as awake

parser=argparse.ArgumentParser()
parser.add_argument('--phase',choices=['main','runtime','inline','fixed-routes','fixed-failure','hardware-unavailable','fixed-edges','hls','recovery','reset','final','transcoding','output','output-hevc','output-hevc-motion','all'],default='all')
parser.add_argument('--task-id',choices=['SETTINGS-002','SETTINGS-003','SETTINGS-004','SETTINGS-005','FIXED-001','FIXED-002','HLS-006','HLS-007'],default='SETTINGS-002')
parser.add_argument('--reported-source',choices=['/var/media/tv/FacetheNation-66420956-0.ts'],help='FIXED-002 exact completed broadcast regression, never clear its watch history')
parser.add_argument('--seek-target-ms',type=int,action='append',help='Explicit file-relative targets for the exact reported-source Fixed gate')
parser.add_argument('--route',default='',help='Exact named Fixed row for affected-only reruns')
parser.add_argument('--route-set',choices=['all','vaapi-seek','encoder-pair','hls-stats'],default='all',help='Affected-only route selection, never broad matrix replay')
parser.add_argument('--start-route',default='',help='Resume the matrix at an exact first unfinished row; never repeat completed rows implicitly')
parser.add_argument('--capture-hold',action='store_true',help='Hold one exact --route or the single HLS-session gate for bounded independent HDMI capture')
parser.add_argument('--policy',choices=['server','prefer_hardware','software','hardware_only'],default='server',help='Explicit isolated encoder policy; original saved value is restored')
parser.add_argument('--hardware-encoder',choices=['auto','qsv','nvenc','vaapi','amf','videotoolbox'],default='auto',help='SETTINGS-005 explicit per-session server accelerator; not Android decoding')
parser.add_argument('--audio-channels',choices=['source','2'],default='source',help='Single-variable output comparison; original saved value is restored')
parser.add_argument('--reuse-installed',action='store_true')
parser.add_argument('--skip-playback-screenshots',action='store_true',help='Controlled comparison retaining menus/accessibility, excluding Android surface readback')
args=parser.parse_args()
if (args.reported_source or args.seek_target_ms) and (args.phase!='fixed-routes' or args.task_id not in ('FIXED-002','SETTINGS-005','HLS-006','HLS-007') or not args.reported_source):
    raise SystemExit('Reported-source targets require FIXED-002/SETTINGS-005 fixed-routes before device mutation')
if any(t<0 or t>900000 for t in args.seek_target_ms or []):raise SystemExit('Bounded reported-source target required')
if args.route_set=='vaapi-seek' and (args.task_id!='FIXED-002' or not args.reported_source or args.route or args.start_route):
    raise SystemExit('Affected VAAPI route set requires the exact FIXED-002 source and no competing row selection')
if args.route_set=='encoder-pair' and (args.task_id!='SETTINGS-005' or not args.reported_source or args.route or args.start_route):
    raise SystemExit('Encoder pair requires SETTINGS-005 exact reported source and no competing row selection')
if args.route_set=='hls-stats' and (args.task_id not in ('HLS-006','HLS-007') or not args.reported_source or args.route):
    raise SystemExit('HLS stats requires HLS-006 exact completed source')
if args.phase=='fixed-edges' and args.policy!='server':
    raise SystemExit('Positive compatibility fallback requires Server Default, not an unsupported strict encoder policy; before device mutation')
if args.capture_hold and not (args.phase=='hls' or (args.phase=='fixed-routes' and (args.route or args.route_set in ('vaapi-seek','encoder-pair','hls-stats')))):
    raise SystemExit('HDMI hold requires one exact Fixed route or the single HLS-session gate, before device mutation')
if args.phase not in ('main','reset','transcoding'):
    try:__import__('mcp.server')
    except ImportError:raise SystemExit('Use /opt/opensagetv-vibe/android-python/bin/python3 in opensagetv-vibe-dev; this phase requires the commissioned MCP interpreter, before any device mutation.')
env=load_test_environment();serial=env.device_serial();server=env.server()['address']
if serial!=env.device_serial('non_pro') or server!=env.server_address('vibe'):raise SystemExit('Exact configured Non-Pro/stock232 required')
OUT=ROOT/'artifacts/active'/args.task_id;OUT.mkdir(parents=True,exist_ok=True)
stamp=time.strftime('%Y%m%dT%H%M%SZ',time.gmtime())
print('SESSION '+stamp,flush=True)
package='opensagetv.vibe.miniclient.debug'
apk=ROOT/'source/dev/android-tv/build/outputs/apk/debug/android-tv-debug.apk'
adb=AdbClient(serial,package);client=None;api=None;context=None;original_cc=None
checkpoint=False;lease=False;complete=False;baseline=None;rows=[];cleanup={};failure=None;stage='preflight';apk_sha=None;last_observation={}
def interrupted(signum,frame):raise KeyboardInterrupt('Owned settings gate interrupted')
signal.signal(signal.SIGTERM,interrupted)
if hasattr(signal,'SIGALRM'):
    signal.signal(signal.SIGALRM,interrupted)
    # The user-requested 18-row functional matrix is not a soak. Budget its
    # actual bounded setup/seek/teardown work; single focused phases stay25min.
    signal.alarm(2400 if args.phase=='fixed-routes' and not args.route else 1500)

def values():
    root=ET.fromstring(adb.shell('run-as '+package+' cat shared_prefs/'+package+'_preferences.xml'))
    return {n.attrib['name']:(n.tag,n.attrib.get('value'),(n.text or '') if n.tag=='string' else None,tuple(sorted((c.tag,c.text or '') for c in n))) for n in root}
LIVE={'client_diagnostics_web/status','server_diagnostics/status'}
def configuration(items):return {k:v for k,v in items.items() if k not in LIVE and not k.startswith('settings/')}
def scalar(key):
    item=values().get(key);return None if item is None else item[2] if item[0]=='string' else item[1]
def hierarchy():
    # UIAutomator can return success without a dump while Android's window is
    # changing. Retry only this read-only observation, never a click/input.
    for attempt in range(3):
        remote='/data/local/tmp/vibe-playback-settings-'+uuid.uuid4().hex+'.xml'
        try:
            adb.shell('uiautomator dump '+remote,timeout=30)
            if adb.shell('test -f '+remote+' && echo present',check=False).strip()=='present':
                raw=adb.shell('cat '+remote)
                return ET.fromstring(raw[raw.index('<hierarchy'):raw.rindex('</hierarchy>')+12])
        finally:adb.shell('rm -f '+remote,check=False)
        if attempt<2:time.sleep(.3)
    raise RuntimeError('Read-only accessibility snapshot unavailable after three observations')
def node(label,tree=None,description=False):
    tree=hierarchy() if tree is None else tree
    return next((n for n in tree.iter('node') if n.get('content-desc' if description else 'text','').casefold()==label.casefold()),None)
def move(direction,tree):
    scroll=next((n for n in tree.iter('node') if n.get('scrollable')=='true'),None)
    if scroll is None:return False
    x1,y1,x2,y2=map(int,re.findall(r'\d+',scroll.get('bounds')));middle=(y1+y2)//2
    a,b=middle-(y2-y1)//5,middle+(y2-y1)//5
    start,end=(b,a) if direction=='down' else (a,b)
    adb.shell(f'input swipe {(x1+x2)//2} {start} {(x1+x2)//2} {end} 450');time.sleep(.2);return True
def locate(label,description=False):
    for direction in ['down','up']:
        previous=None
        for _ in range(25):
            tree=hierarchy();found=node(label,tree,description)
            if found is not None:return found
            signature=tuple((n.get('text'),n.get('bounds')) for n in tree.iter('node') if n.get('text'))
            if signature==previous:break
            previous=signature
            if not move(direction,tree):break
    raise RuntimeError('Reviewed menu target unavailable: '+label)
def click(target):
    x1,y1,x2,y2=map(int,re.findall(r'\d+',target.get('bounds','')))
    if x2<=x1 or y2<=y1:raise RuntimeError('Target has no visible bounds')
    adb.shell(f'input tap {(x1+x2)//2} {(y1+y2)//2}');time.sleep(.2)
def tap(label,description=False):click(locate(label,description))
def back():adb.shell('input keyevent 4');time.sleep(.2)

def hide_navigation():
    # Debug navigation is not the Activity's retained instance: its event-only
    # Hide control cannot dismiss that test-owned instance. This existing click
    # explicitly dismisses itself before opening the normal settings hub.
    target=next((n for n in hierarchy().iter('node') if n.get('resource-id','').endswith('/nav_playback_settings')),None)
    if target is None:raise RuntimeError('Navigation settings control unavailable')
    click(target);close_panels()
def section(title,expanded=True):
    for direction in ['down','up']:
        previous=None
        for _ in range(25):
            tree=hierarchy()
            for prefix in ['▼ ','▶ ']:
                found=node(prefix+title,tree)
                if found is not None:
                    if (prefix=='▼ ')==expanded:return
                    click(found);locate(('▼ ' if expanded else '▶ ')+title);return
            signature=tuple((n.get('text'),n.get('bounds')) for n in tree.iter('node') if n.get('text'))
            if signature==previous:break
            previous=signature
            if not move(direction,tree):break
    raise RuntimeError('Reviewed section unavailable: '+title)
def choose(title,entry):tap(title);tap(entry)
def entry_enabled(label):
    tree=hierarchy();parents={c:p for p in tree.iter() for c in p};item=node(label,tree)
    if item is None:item=locate(label);tree=hierarchy();parents={c:p for p in tree.iter() for c in p};item=node(label,tree)
    for _ in range(5):
        if item is None:return False
        if item.get('clickable')=='true':return item.get('enabled')=='true'
        item=parents.get(item)
    return False
def focused_contains(label):
    return any(n.get('focused')=='true' and any(c.get('text','').casefold()==label.casefold() for c in n.iter('node')) for n in hierarchy().iter('node'))
def edit_number(title,value,save=True):
    tap(title);tree=hierarchy();field=next((n for n in tree.iter('node') if n.get('class')=='android.widget.EditText'),None)
    if field is None:raise RuntimeError('Numeric preference editor missing')
    click(field);adb.shell('input keyevent 123 '+' '.join(['67']*12));adb.shell('input text '+str(value))
    # Fire TV's numeric IME overlays the preference dialog. Underlying Cancel/
    # OK may still appear in the hierarchy but are not touchable until it hides.
    # Fire OS can report the IME under the app's package, so package matching
    # does not identify its overlay reliably. The field was explicitly focused
    # above: one Back hides that keyboard before touching the dialog buttons.
    back()
    time.sleep(.8)
    tap('OK' if save else 'Cancel')
    time.sleep(.5)
def record(name,**data):rows.append(dict(gate=name,passed=True,**data));print('PASS '+name,flush=True)
def state():return call_dict(client,'dev_player_state')
def await_state(predicate,seconds=30):
    global last_observation
    end=time.monotonic()+seconds
    while time.monotonic()<end:
        s=state()
        last_observation={key:s.get(key) for key in ('audioOffsetMs','audioOffsetSessionOverrideMs','audioPassthroughSessionOverride','playerActive','state','health_backendClass','health_basicIsPlaying','health_firstVideoFrameRendered','health_videoOutputFps','health_playerPositionMs','health_videoDecoder','health_audioDecoder','health_isPlaying','health_playWhenReady','health_videoRendered','health_audioRendered','health_playbackSource','health_videoMime','health_playerError','mimDirectSessionState','mimDirectVideoTargetKbps','mimDirectQualityState')}
        if s.get('health_playerError'):raise RuntimeError('Active player reports an error')
        if predicate(s):return s
        time.sleep(.3)
    raise RuntimeError('Bounded playback observation did not meet the current gate')
def open_panel(panel):
    receipt=adb.show_active_player_adjustments(panel)
    if not receipt.get('shown'):raise RuntimeError('Closed UI route did not open')
    time.sleep(.3)
    if panel in ('transport','diagnostics','recovery'):section({'transport':'Transport Settings','diagnostics':'Diagnostics','recovery':'Recovery'}[panel])
def close_panels():
    titles={'playback settings','video settings','audio settings','captions / subtitles','transport settings','display settings','diagnostics','recovery','video filters','fixed playback settings','direct quality','hls quality'}
    for _ in range(7):
        tree=hierarchy()
        if not any(n.get('text','').casefold() in titles or n.get('resource-id','').endswith('/nav_playback_settings') for n in tree.iter('node')):return
        back()
    raise RuntimeError('Settings dialogs did not close within their bounded parent chain')
def setup_playback(mode,encoder=None,video_codec=None,backend='media3',gsy_engine='auto',deinterlace='off',fault=False,test_options=None):
    global context,original_cc
    print('START '+mode+' / '+backend+' / '+gsy_engine,flush=True)
    call_dict(client,'dev_prepare_clean_start',{'wake':True,'graceful_timeout_s':2.0},timeout=30)
    config=dict(player=backend,gsy_engine=gsy_engine,streaming='fixed' if mode in ('stock','copy','hls','transcode') else 'pull',decoding='hardware',
        **dict(FIXED_DEFAULTS,mim_direct_mode=mode if mode in ('copy','hls','transcode') else 'off',mim_direct_deinterlace=deinterlace,mim_direct_runtime_rate=False,mim_direct_adaptive_quality=False))
    if mode=='copy':config.update(fixed_remuxing_preference='needed',fixed_remuxing_format='mpegts')
    if mode in ('hls','transcode'):config['fixed_audio_channels']=args.audio_channels
    if test_options:config.update(test_options)
    if args.phase.startswith('output'):config.update(client_web_enabled=True,client_web_port=8272)
    call_dict(client,'dev_set_player_config',config)
    if encoder:
        if not adb.dev_control('main_settings_ui').get('launched'):raise RuntimeError('Main settings route refused')
        section('Transport Settings');tap('Fixed Playback Settings')
        if video_codec:choose('Video Codec',video_codec)
        choose('Video Encoder',encoder);choose('Resolution','CIF (352x240/288)');choose('Frame Rate','24 fps')
        choose('Audio Codec','AAC');choose('Audio Bitrate','96 kbps');choose('Audio Channels','2 (Stereo)')
        if mode=='transcode':locate('Automatic adjustment and Minimum apply to HLS only')
        adb.force_stop()
    call_dict(client,'dev_connect_server',{'address':server,'port':31099,'save':False},timeout=30)
    wait_automation_root(client,timeout_s=30,stable_ms=700)
    connected=state()
    if connected.get('serverAddress')!=server or connected.get('clientId')!=env.device()['client_id']:raise RuntimeError('Exact device/client/server witness mismatch')
    context=api.resolve_context(connected['clientId']);original_cc=api.closed_caption_state(context)
    if not api.set_closed_caption_state(context,'CC1').get('ok'):raise RuntimeError('Owned caption selection refused')
    if not args.reported_source:api.clear_watched(media_id)
    if fault:call_dict(client,'dev_set_mim_direct_late_fallback_fault',{'enabled':True,'include_pull_failure':False})
    watch_began=time.monotonic()
    if not api._request('media.watch',context=context,media_id=media_id,wait_ms=0).get('ok'):raise RuntimeError('Owned fixture Watch refused')
    if (fault or args.phase=='hardware-unavailable') and mode in ('transcode','hls'):return await_state(lambda s:s.get('mimDirectSessionState')=='start_failed_transcode_stopped',25)
    result=await_state(fixed_policy.started,45)
    startup_elapsed=int((time.monotonic()-watch_began)*1000)
    if not args.reported_source and mode in ('transcode','hls') and result.get('mimDirectSessionState')=='active_transcode' and int(result.get('health_playerPositionMs',-1))>startup_elapsed+2000:raise RuntimeError('Owned producer skipped its origin toward the default EVENT live edge')
    record(mode+'-stock232-owned-startup',clientId=connected['clientId'],uiContext=context,videoFrames=result.get('health_videoRendered'),audioFrames=result.get('health_audioRendered'),audioChannels=result.get('health_audioChannels'),decodedAudioChannels=result.get('health_audioOutputChannels'),decoderPositionMs=result.get('health_playerPositionMs'),watchElapsedMs=startup_elapsed,audioDecoder=result.get('health_audioDecoder'),videoDecoder=result.get('health_videoDecoder'))
    return result
def stop_playback():
    global context,original_cc
    if context and original_cc is not None:
        if not api.set_closed_caption_state(context,original_cc).get('ok'):raise RuntimeError('Owned caption state restoration refused')
    call_dict(client,'dev_exit_session',{'stop_app':True},timeout=30);context=None;original_cc=None

def observed_server_encoder(mode):
    # Read the existing explicit diagnostic, not the requested encoder policy
    # or Android's hardware/software decoder. No producer handles are exported.
    if mode=='stock':
        return 'Unknown (stock-protocol Fixed; no per-session provider status)'
    open_panel('diagnostics');tap('Effective Transcoding Output')
    deadline=time.monotonic()+8;found=None
    while time.monotonic()<deadline:
        labels='\n'.join(n.get('text','') for n in hierarchy().iter('node'))
        found=re.search(r'^Server video encoding: (None \(Copy/Remux\)|(?:Hardware|Software) \([A-Za-z0-9_.-]{1,64}\))$',labels,re.MULTILINE)
        if found:break
        time.sleep(.2)
    back();close_panels()
    if not found:raise RuntimeError('Actual provider encoder unavailable; requested policy is not encoder evidence')
    actual=found.group(1)
    if mode in ('transcode','hls'):
        if args.policy=='hardware_only' and not actual.startswith('Hardware ('):raise RuntimeError('Hardware Only did not actually hardware encode')
        if args.policy=='software' and not actual.startswith('Software ('):raise RuntimeError('Software Only did not actually software encode')
        if args.hardware_encoder!='auto' and args.policy!='software' and actual!='Hardware (h264_'+args.hardware_encoder+')':raise RuntimeError('Requested hardware backend not actually used: '+actual)
    return actual

try:
    adb.connect()
    if adb.shell('getprop ro.product.model').strip()!='AFTMM':raise RuntimeError('Wrong commissioned model')
    api=CoreMcpApiClient.discover(server)
    activity=api._request('server.activity')
    if activity.get('safeToRestart') is not True:raise RuntimeError('Independent idle preflight is required; no other client is interrupted')
    media=api.resolve_exact_path(args.reported_source or '/var/media/OpenSageTV_Vibe_Tests/VibeSeekTest-1080i-MPEG2-AC3-CC.ts');media_id=media.get('mediaFileId')
    if not media_id:raise RuntimeError('Canonical completed fixture missing')
    baseline=values();checkpoint=bool(adb.checkpoint_settings().get('captured'))
    if not checkpoint:raise RuntimeError('Private settings checkpoint refused')
    lease=bool(awake.begin(serial,OUT/(stamp+'-awake.json'),'automated').get('active'))
    with apk.open('rb') as stream:apk_sha=hashlib.file_digest(stream,'sha256').hexdigest()
    if not args.reuse_installed:
        if 'Success' not in adb.install_dev_apk(apk):raise RuntimeError('APK update did not complete')
    installed=adb.shell('pm path '+package).strip().removeprefix('package:')
    if not re.fullmatch('/data/app/[A-Za-z0-9._~+/=-]+/base.apk',installed):raise RuntimeError('Ambiguous installed APK path')
    payload=subprocess.run(adb._base(True)+['exec-out','cat',installed],stdin=subprocess.DEVNULL,capture_output=True,timeout=60,check=True)
    if hashlib.sha256(payload.stdout).hexdigest()!=apk_sha:raise RuntimeError('Installed candidate byte mismatch')
    del payload
    if args.phase=='transcoding':
        stage='startup-shared-transcoding-controls'
        if not adb.dev_control('main_settings_ui').get('launched'):raise RuntimeError('Main settings route refused')
        section('Transport Settings');tap('Fixed Playback Settings')
        modes=[('Stock SageTV Pipeline',False,True,False),('Direct Copy (No Re-encoding)',False,False,False),('Transcode (Legacy)',True,True,False),('Transcode Low Latency (HLS)',True,True,True)]
        for label,encoder,audio,adjust in modes:
            choose('Processing Method',label)
            if entry_enabled('Video Encoder')!=encoder:raise RuntimeError('Encoder applicability incorrect for '+label)
            if entry_enabled('Hardware Encoder')!=encoder:raise RuntimeError('Hardware encoder applicability incorrect for '+label)
            if entry_enabled('Audio Codec')!=audio:raise RuntimeError('Audio applicability incorrect for '+label)
            if entry_enabled('Automatic Bitrate Adjustment')!=adjust:raise RuntimeError('Adjustment applicability incorrect for '+label)
        choose('Processing Method','Transcode (Legacy)');tap('Video Encoder')
        for label in ('Use Server Default','Prefer Hardware (Software fallback allowed)','Software Only','Hardware Only (No Software fallback)'):locate(label)
        tap('Hardware Only (No Software fallback)')
        if scalar('mim_direct/encoder_policy')!='hardware_only':raise RuntimeError('Typed encoder choice not saved')
        tap('Hardware Encoder')
        for label in ('Auto (Server Default)','Intel Quick Sync (QSV)','NVIDIA NVENC','VAAPI (Linux)','AMD AMF (Windows)','Apple VideoToolbox (macOS)'):locate(label)
        tap('Intel Quick Sync (QSV)')
        if scalar('mim_direct/hardware_backend')!='qsv':raise RuntimeError('Hardware encoder selection was not persisted')
        choose('Video Encoder','Software Only')
        if entry_enabled('Hardware Encoder') or scalar('mim_direct/hardware_backend')!='qsv':raise RuntimeError('Software policy erased or enabled hardware pin')
        choose('Video Encoder','Hardware Only (No Software fallback)')
        if not entry_enabled('Hardware Encoder'):raise RuntimeError('Hardware encoder did not re-enable')
        choose('Audio Codec','Use Source');choose('Audio Bitrate','Use Source')
        if scalar('fixed_encoding/audio_codec')!='source' or scalar('fixed_encoding/audio_bitrate_kbps')!='source':raise RuntimeError('Source audio settings not saved')
        tap('Video Codec');locate('Auto (H.264 compatible)');locate('H.264');locate('HEVC');back()
        before=configuration(values());tap('Reset Transcoding Settings Only')
        if not focused_contains('Cancel'):raise RuntimeError('Transcoding reset is not Cancel-first')
        adb.shell('input keyevent 23')
        if configuration(values())!=before:raise RuntimeError('Cancelled transcoding reset changed settings')
        tap('Reset Transcoding Settings Only');tap('Reset')
        if scalar('mim_direct/encoder_policy')!='server' or scalar('mim_direct/hardware_backend')!='auto' or scalar('fixed_encoding/audio_codec')!='aac' or scalar('fixed_encoding/audio_bitrate_kbps')!='128':raise RuntimeError('Transcoding reset defaults incorrect')
        after=values()
        for key,value in before.items():
            if not key.startswith(('fixed_encoding/','fixed_remuxing/','mim_direct/')) and after.get(key)!=value:raise RuntimeError('Transcoding reset changed unrelated setting')
        adb.screenshot(OUT/(stamp+'-transcoding-main.png'))
        record('main-transcoding-mode-policy-source-reset-and-unrelated-preferences')
        adb.force_stop()
    if args.phase in ('main','all'):
        stage='startup-shared-defaults'
        if not adb.dev_control('main_settings_ui').get('launched'):raise RuntimeError('Main settings route refused')
        section('Playback Settings');tap('Audio, Broadcast CC, and Subtitle Settings')
        locate('Audio Output');locate('Audio Offset')
        current=configuration(values())
        for key,value in configuration(baseline).items():
            if current.get(key)!=value:raise RuntimeError('Viewing settings rewrote an existing preference')
        before=configuration(values());stage='startup-cancel-numeric';edit_number('Audio Offset',999,False)
        if configuration(values())!=before:raise RuntimeError('Cancelled numeric edit changed saved settings')
        stage='startup-save-numeric';edit_number('Audio Offset',125)
        if scalar('playback_audio_offset_ms')!='125' or values()['playback_audio_offset_ms'][0]!='int':raise RuntimeError('Main audio default is not typed and saved')
        locate('Subtitle Offset');record('main-shared-audio-caption-defaults-and-cancel')
        back();tap('Video Filters and Display Settings');locate('GPU Filter');locate('Refresh-rate Matching');back()
        record('main-shared-filter-display-defaults')
        stage='startup-player-audio-choice-consistency'
        for label in ['Media3 ExoPlayer (Google current)','ExoPlayer Legacy (working baseline)']:
            choose('Default Player',label);tap('Selected Player Settings');tap('Audio Output')
            locate('Decoded PCM stereo');locate('Encoded passthrough');back();back()
            locate('Selected Player Settings')
        record('main-media3-and-legacy-use-shared-typed-audio-choices')
        adb.force_stop()
    if args.phase in ('reset','final','all'):
        stage='startup-reset-shared-defaults'
        if not adb.dev_control('main_settings_ui').get('launched'):raise RuntimeError('Main settings route refused')
        section('General');before=configuration(values());protected=values()
        tap('Reset Settings to Defaults')
        if not focused_contains('Cancel'):raise RuntimeError('Startup reset is not Cancel-first')
        adb.shell('input keyevent 23')
        if configuration(values())!=before:raise RuntimeError('Cancelled startup reset changed saved settings')
        tap('Reset Settings to Defaults');tap('Reset')
        expected={
            'playback_audio_offset_ms':('int','0'),'playback_subtitle_offset_ms':('int','0'),
            'playback_subtitle_safe_area_percent':('int','18'),'playback_subtitle_text_scale_percent':('int','100'),
            'playback_subtitle_text_style':('string','system'),'playback_refresh_settle_ms':('int','0'),
            'playback_refresh_rate_matching':('string','off'),'disable_audio_passthrough':('boolean','true'),
            'playback_passthrough_audio_offset_enabled':('boolean','false'),
            'video_gpu_filter':('string','fast'),'video_gpu_field_order':('string','auto'),
            'video_gpu_output_rate':('string','field'),'ijk_deinterlace_filter':('string','auto'),
            'playback_buffer_preset':('string','balanced'),
            'use_opengl_ui':('boolean','true'),'unified_graphics_surfaces':('boolean','true'),
            'mim_direct/adaptive_quality':('boolean','false'),'mim_direct/runtime_rate_control':('boolean','false')}
        after=values()
        for key,(kind,value) in expected.items():
            if scalar(key)!=value or after.get(key,(None,))[0]!=kind:raise RuntimeError('Confirmed shared reset failed: '+key)
        for key,value in protected.items():
            if (key=='clientid' or key.startswith(('smb_','settings/')) or re.search('password|credential|token|secret|client_id|server_id|profile',key,re.I)) and after.get(key)!=value:
                raise RuntimeError('Shared reset changed protected settings')
        record('shared-typed-default-reset-cancel-and-protected-values',checkedDefaultCount=len(expected))
        adb.force_stop()
    if args.phase not in ('main','reset','transcoding'):
        client=MCPProcess();initialize(client);call_dict(client,'adb_connect',timeout=30)
    if args.phase in ('inline','fixed-routes','fixed-failure','hardware-unavailable','fixed-edges','hls'):
        stage='fixed-route-baseline'
        if not adb.dev_control('main_settings_ui').get('launched'):raise RuntimeError('Main settings route refused')
        section('Transport Settings');tap('Fixed Playback Settings')
        choose('Processing Method','Transcode (Legacy)')
        arrays={n.get('name'):[x.text for x in n] for path in (ROOT/'source/dev/android-shared/src/main/res/values').glob('*.xml') for n in ET.parse(path).getroot() if n.tag=='string-array'}
        for title,labels,vals,value,key in [('Video Encoder','entries_transcode_encoder','values_transcode_encoder',args.policy,'mim_direct/encoder_policy'),('Hardware Encoder','entries_hardware_encoder','values_hardware_encoder',args.hardware_encoder,'mim_direct/hardware_backend'),('Video Codec','entries_transcode_video_codec','values_transcode_video_codec','auto','fixed_encoding/video_codec')]:
            if title=='Hardware Encoder' and args.policy=='software':continue
            if scalar(key)!=value:choose(title,arrays[labels][arrays[vals].index(value)])
        adb.force_stop()
    if args.phase=='inline':
        stage='inline-blue-hub-sections'
        setup_playback('hls',deinterlace='auto')
        before=configuration(values());open_panel('navigation');tap('Playback Settings',description=True)
        section('Transport Settings');locate('Selected / Actual Transport');locate('Processing Method');locate('Video Encoder');locate('Reduce Current Session')
        for wrapper in ('Transport and Fixed Playback','Playback Status, Tests and Export','Restart / Restore Saved Defaults'):
            if node(wrapper) is not None:raise RuntimeError('Navigation-only wrapper still present')
        section('Transport Settings',False);section('Diagnostics');locate('Playback Status');locate('Effective Transcoding Output');locate('Export Diagnostic Report')
        section('Diagnostics',False);section('Recovery');locate('Restart Video Decoder');locate('Restart Audio Output');tap('Restore Saved Playback Defaults')
        if not focused_contains('Cancel'):raise RuntimeError('Inline recovery confirmation is not Cancel-first')
        tap('Cancel');locate('Playback Settings');section('Recovery');locate('Restore Saved Playback Defaults')
        adb.screenshot(OUT/(stamp+'-inline-hub.png'));close_panels()
        if configuration(values())!=before:raise RuntimeError('Inline navigation/cancel changed saved playback settings')
        resumed=state();time.sleep(2);after=state()
        if int(after.get('health_videoRendered',0))<=int(resumed.get('health_videoRendered',0)):raise RuntimeError('Inline hub interrupted playback')
        record('inline-transport-diagnostics-recovery-collapse-confirm-and-return');stop_playback()
    if args.phase=='fixed-failure':
        for mode in ('transcode','hls'):
            stage=mode+'-explicit-startup-failure'
            failed=setup_playback(mode,fault=True)
            time.sleep(3);after=state()
            if int(after.get('health_videoRendered',0))>0 or after.get('health_isPlaying') is True:raise RuntimeError('Failed explicit encoding silently played original media')
            with urlopen(f'http://{server}:31910/v1/capabilities',timeout=5) as response:caps=json.load(response)
            if caps['mimDirect']['activeSessions']!=0:raise RuntimeError('Failed startup leaked producer')
            call_dict(client,'dev_set_mim_direct_late_fallback_fault',{'enabled':False})
            record(stage,sessionState=failed.get('mimDirectSessionState'),untranscodedFallback=False,activeSessions=0);stop_playback()
    if args.phase=='hardware-unavailable':
        if args.hardware_encoder not in ('amf','videotoolbox'):raise RuntimeError('Use a known unavailable Linux hardware backend for this bounded negative gate')
        for mode in ('transcode','hls'):
            stage=mode+'-unavailable-explicit-hardware'
            failed=setup_playback(mode);time.sleep(2);after=state()
            if int(after.get('health_videoRendered',0))>0 or after.get('health_isPlaying') is True:raise RuntimeError('Unavailable hardware silently used another route')
            with urlopen(f'http://{server}:31910/v1/capabilities',timeout=5) as response:caps=json.load(response)
            if caps['mimDirect']['activeSessions']!=0:raise RuntimeError('Unavailable hardware leaked producer')
            record(stage,requestedHardware=args.hardware_encoder,sessionState=failed.get('mimDirectSessionState'),untranscodedFallback=False,activeSessions=0);stop_playback()
    if args.phase=='fixed-edges':
        for name,mode,backend,options,expected in (
            ('copy-remux-off','copy','media3',{'fixed_remuxing_preference':'off'},'SAGETV_PULL'),
            ('hls-missing-provider','hls','media3',{'fixed_caption_side_channel_port':31919},'SAGETV_PUSH'),
            ('transcode-ijk-stock-compatibility','transcode','ijkplayer',{},'SAGETV_PUSH'),
            ('hls-ijk-stock-compatibility','hls','ijkplayer',{},'SAGETV_PUSH'),
            ('hls-adjusted-switching','hls','media3',{'mim_direct_adaptive_quality':True,'mim_direct_runtime_rate':False},'OWNED_HLS'),
            ('hls-adjusted-prefer-in-place','hls','media3',{'mim_direct_adaptive_quality':True,'mim_direct_runtime_rate':True},'OWNED_HLS')):
            stage=name
            if name=='hls-missing-provider':
                import socket
                with socket.socket() as probe:
                    probe.settimeout(2)
                    if probe.connect_ex((server,31919))==0:raise RuntimeError('Missing-provider port is occupied')
            started=setup_playback(mode,backend=backend,test_options={'fixed_caption_side_channel_port':31910,**options})
            if expected=='OWNED_HLS':
                if started.get('mimDirectSessionState')!='active_transcode' or str(started.get('mimHlsOwned')).lower()!='true':raise RuntimeError('Adjusted HLS did not retain ownership')
            elif started.get('health_playbackSource')!=expected:raise RuntimeError('Compatibility route not actually retained')
            if expected=='SAGETV_PUSH' and fixed_policy.video_mime(started)!='video/avc':raise RuntimeError('Compatibility route not encoding')
            with urlopen(f'http://{server}:31910/v1/capabilities',timeout=5) as response:caps=json.load(response)
            if caps['mimDirect']['activeSessions']!=(1 if expected=='OWNED_HLS' else 0):raise RuntimeError('Unexpected Direct producer ownership')
            record(name,actual=started.get('health_playbackSource'),ownedHls=expected=='OWNED_HLS',compatibilityFallback=expected=='SAGETV_PUSH',nativeInPlaceNotInferred=True);stop_playback()
        stage='copy-startup-failure-retains-original-pull-compatibility'
        started=setup_playback('copy',fault=True)
        call_dict(client,'dev_set_mim_direct_late_fallback_fault',{'enabled':False})
        if started.get('mimDirectSessionState')!='start_failed_pull_fallback' or started.get('health_playbackSource')!='SAGETV_PULL' or fixed_policy.video_mime(started)!='video/mpeg2':raise RuntimeError('Non-strict Copy lost its supported original-source fallback')
        with urlopen(f'http://{server}:31910/v1/capabilities',timeout=5) as response:caps=json.load(response)
        if caps['mimDirect']['activeSessions']!=0:raise RuntimeError('Copy fallback retained a producer')
        record(stage,sessionState=started.get('mimDirectSessionState'),actual='SAGETV_PULL',encodedOutputClaimed=False,activeSessions=0);stop_playback()
    if args.phase=='fixed-routes':
        routes=[(mode,backend,engine) for mode in ('stock','copy','transcode','hls') for backend,engine in [('media3','auto'),('exoplayer','auto'),('gsyplayer','media3'),('gsyplayer','legacy_exo')]]
        routes += [('stock','ijkplayer','auto'),('copy','ijkplayer','auto')]
        if args.route_set=='vaapi-seek':routes=[r for r in routes if r[0] in ('transcode','hls') or r[1]=='media3']
        if args.route_set=='encoder-pair':routes=[r for r in routes if r[0] in ('transcode','hls') and r[1]=='media3']
        if args.route_set=='hls-stats':routes=[r for r in routes if r[0]=='hls' or r[0]=='transcode' and r[1]=='media3']
        if args.task_id=='HLS-007':routes=[r for r in routes if r[0]=='hls']
        names=[mode+'-'+backend+'-'+engine for mode,backend,engine in routes]
        if args.route and args.route not in names:raise RuntimeError('Unknown Fixed route')
        if args.start_route and (args.route or args.start_route not in names):raise RuntimeError('Invalid matrix resume row')
        for mode,backend,engine in routes:
            name=mode+'-'+backend+'-'+engine
            capture_this_row=bool(args.capture_hold and (args.route or name==names[-1]))
            if args.route and args.route!=name:continue
            if args.start_route and names.index(name)<names.index(args.start_route):continue
            stage=name
            started=setup_playback(mode,backend=backend,gsy_engine=engine,deinterlace='auto')
            actual=started.get('health_playbackSource')
            if mode=='stock':
                if actual!='SAGETV_PUSH' or fixed_policy.video_mime(started)!='video/avc':raise RuntimeError('Stock Fixed did not actually transcode')
            elif mode=='copy':
                if started.get('mimDirectSessionState')!='active_copy' or fixed_policy.video_mime(started)!='video/mpeg2':raise RuntimeError('Copy did not preserve original video through owned remux')
            else:
                if started.get('mimDirectSessionState')!='active_transcode' or started.get('health_videoMime')!='video/avc':raise RuntimeError('Fixed encoding fell back to another route')
                if mode=='hls' and str(started.get('mimHlsOwned')).lower()!='true':raise RuntimeError('HLS not actually owned')
            before=state();time.sleep(2);after=state()
            if not fixed_policy.advancing(before,after):raise RuntimeError('Fixed startup lacks backend output progress')
            if capture_this_row and backend!='ijkplayer' and not args.reported_source:
                if not args.route:raise RuntimeError('Capture hold requires one exact route')
                marker=OUT/(stamp+'-'+name+'-live-stage.json')
                marker.write_text(json.dumps(dict(scopeVerified=True,device=serial,server=server,clientId=env.device()['client_id'],uiContext=context,mediaFileId=media_id,mode=mode,expiresAtMs=int(time.time()*1000)+25000)))
                print('READY '+str(marker),flush=True);time.sleep(25)
            observed_encoder=observed_server_encoder(mode)
            if args.task_id in ('HLS-006','HLS-007'):
                with urlopen(f'http://{server}:31910/v1/capabilities',timeout=5) as response:caps=json.load(response)
                if caps['mimDirect'].get('hlsBufferRevision')!=1:raise RuntimeError('Negotiated reserve provider missing')
                reserve=[];deadline=time.monotonic()+35
                while True:
                    measured=state();ahead=int(measured.get('health_bufferedPositionMs',-1))-int(measured.get('health_playerPositionMs',-1));reserve.append(ahead)
                    if mode!='hls' or ahead>=12000:break
                    if time.monotonic()>deadline:raise RuntimeError('Completed HLS reserve did not exceed the old six-second producer limit')
                    time.sleep(1)
                if name==names[0]:
                    adb.set_active_player_overlay(mode='off');open_panel('navigation')
                    if args.task_id=='HLS-007':
                        tree=hierarchy()
                        graph=next(n for n in tree.iter('node') if n.get('resource-id','').endswith('/nav_playback_stats'))
                        info=next(n for n in tree.iter('node') if n.get('resource-id','').endswith('/nav_playback_details'))
                        gx1,gy1,gx2,gy2=map(int,re.findall(r'\d+',graph.get('bounds','')))
                        ix1,iy1,ix2,iy2=map(int,re.findall(r'\d+',info.get('bounds','')))
                        if (gy1,gy2)!=(iy1,iy2) or ix1<gx2 or ix2<=ix1:
                            raise RuntimeError('Statistics information icon is not visible immediately right in the same row')
                        record('statistics-icons-adjacent',graphBounds=graph.get('bounds'),infoBounds=info.get('bounds'))
                    tap('Show Playback Graphs',description=True)
                    # Compact MCP omits visibility; dialog accessibility omits
                    # the Activity overlay. Verify the still-open dialog first,
                    # then explicitly close it before inspecting overlay text.
                    if adb.player_state_snapshot().get('activePlayerProcessOverlayVisible') is not True:raise RuntimeError('Graph icon did not show overlay')
                    locate('Show Playback Information',description=True)
                    # Physical screenshots are the overlay oracle: the focused
                    # dialog excludes Activity text from accessibility dumps.
                    # Inspect these captures rather than inventing absent text.
                    time.sleep(2);adb.screenshot(OUT/(stamp+'-graphs-with-menu.png'))
                    tap('Show Playback Information',description=True);locate('Hide Playback Graphs',description=True)
                    adb.screenshot(OUT/(stamp+'-full-information.png'))
                    tap('Hide Playback Graphs',description=True);locate('Hide Playback Information',description=True)
                    adb.screenshot(OUT/(stamp+'-information-only.png'))
                    tap('Hide Playback Information',description=True);locate('Show Playback Graphs',description=True)
                    if adb.player_state_snapshot().get('activePlayerProcessOverlayVisible') is not False:raise RuntimeError('Both icons off retained overlay')
                    hide_navigation()
                adb.set_active_player_overlay(mode='graphs');time.sleep(2)
                if int(measured.get('health_dataSourceNetworkReadBytes',-1))<=0:raise RuntimeError('HTTP media counters unavailable')
                adb.screenshot(OUT/(stamp+'-'+name+'-graphs.png'));adb.set_active_player_overlay(mode='off')
                record(name+'-stats-reserve-and-independent-icons',reserveSamplesMs=reserve,networkBytes=measured.get('health_dataSourceNetworkReadBytes'))
                if args.task_id=='HLS-007':
                    # Short affected refill gate, not a soak or WAN certification.
                    deadline=time.monotonic()+35
                    while True:
                        measured=state()
                        ahead=int(measured.get('health_bufferedPositionMs',-1))-int(measured.get('health_playerPositionMs',-1))
                        if ahead>=17000:break
                        if time.monotonic()>deadline:raise RuntimeError('Completed HLS did not warm near its20s target')
                        time.sleep(1)
                    samples=[];deadline=time.monotonic()+28
                    while time.monotonic()<deadline:
                        measured=state()
                        samples.append({'bufferAheadMs':int(measured.get('health_bufferedPositionMs',-1))-int(measured.get('health_playerPositionMs',-1)),
                            'networkBytes':int(measured.get('health_dataSourceNetworkReadBytes',-1)),
                            'filePositionMs':int(measured.get('sageTimelineMs',-1)),
                            'videoFrames':int(measured.get('health_videoRendered',-1)),
                            'audioFrames':int(measured.get('health_audioRendered',-1)),
                            'isLoading':measured.get('health_isLoading'),
                            'playbackState':measured.get('health_playbackState'),
                            'windowDurationMs':measured.get('health_durationMs'),
                            'playerPositionMs':int(measured.get('health_playerPositionMs',-1))})
                        time.sleep(1)
                    # Retain the actual failing samples before assertions too;
                    # a stale startup snapshot cannot explain refill failure.
                    refill_row=dict(gate=name+'-near-full-refill',passed=False,samples=samples,
                        minimumBufferMs=min(s['bufferAheadMs'] for s in samples) if samples else None,
                        maximumBufferMs=max(s['bufferAheadMs'] for s in samples) if samples else None,
                        remoteBandwidthNotSimulated=True)
                    rows.append(refill_row)
                    if len(samples)<8 or min(s['bufferAheadMs'] for s in samples)<14000:
                        raise RuntimeError('Completed HLS drained below bounded near-full refill reserve')
                    # Raw Exo positions are window-relative and may stay near
                    # constant after the180s rolling playlist moves. Require
                    # actual file-clock and decoded output, not a fake raw clock.
                    if samples[0]['filePositionMs']<0 or samples[-1]['filePositionMs']<=samples[0]['filePositionMs']+15000 or samples[-1]['videoFrames']<=samples[0]['videoFrames'] or samples[-1]['audioFrames']<=samples[0]['audioFrames']:
                        raise RuntimeError('Near-full reserve did not accompany advancing playback')
                    if samples[-1]['networkBytes']<=samples[0]['networkBytes']:
                        raise RuntimeError('No actual media refill during reserve observation')
                    refill_row['passed']=True
                    print('PASS '+refill_row['gate'],flush=True)
            if args.task_id=='SETTINGS-005' and name==names[-1] and args.hardware_encoder!='auto' and args.policy!='software' and mode in ('transcode','hls'):
                open_panel('transport');tap('Hardware Encoder');tap('Auto (Server Default)');tap('Confirm')
                close_panels()
                if scalar('mim_direct/hardware_backend')!='auto':raise RuntimeError('Future hardware default did not save')
                if observed_server_encoder(mode)!=observed_encoder:raise RuntimeError('Future default changed the active encoder')
            if not api.media_control(context,'pause').get('ok'):raise RuntimeError('Pause refused')
            await_state(fixed_policy.paused)
            if not api.media_control(context,'play').get('ok'):raise RuntimeError('Resume refused')
            await_state(fixed_policy.playing)
            # One public file-relative seek; never compare a basic Fixed decoder
            # epoch with the server's absolute recording coordinate.
            for target in args.seek_target_ms or [30000]:
                seek_args={'target_ms':target,'tolerance_ms':2500,'timeout_s':45,'max_attempts':1}
                if args.task_id=='HLS-006':
                    clocks=[]
                    with ThreadPoolExecutor(max_workers=1) as pool:
                        pending=pool.submit(call_dict,client,'dev_server_seek_time',seek_args,timeout=60)
                        while not pending.done():
                            observation=adb.player_state_snapshot();clocks.append(int(observation.get('sageTimelineMs',-1)));time.sleep(.2)
                        landing=pending.result()
                    if any(t==0 for t in clocks):raise RuntimeError('Owned seek exposed a transient zero timeline')
                    record(name+'-confirmed-seek-clock',samplesMs=clocks)
                else:landing=call_dict(client,'dev_server_seek_time',seek_args,timeout=60)
                witness={key:landing.get(key) for key in ('passed','landed','recovered','reached_ms','target_ms','recoveryMs','positionCoordinate','clientDecoderTimeMs','landingWitness','state','before')}
                (OUT/(stamp+'-'+name+'-seek-'+str(target)+'.json')).write_text(json.dumps(witness,indent=2)+'\n',encoding='utf-8')
                if not landing.get('passed'):raise RuntimeError('Fixed file-relative seek did not land with recovered output at '+str(target))
                record(name+'-seek-'+str(target),**{k:v for k,v in witness.items() if k not in ('passed','state','before')})
            seeked=await_state(fixed_policy.playing,45)
            time.sleep(2);settled=state()
            if not fixed_policy.advancing(seeked,settled):raise RuntimeError('Fixed seek lacks recovered backend output')
            if args.task_id=='SETTINGS-005' and mode in ('transcode','hls') and observed_server_encoder(mode)!=observed_encoder:raise RuntimeError('Seek changed the session hardware encoder')
            if args.reported_source and name==names[-1]:
                # One real Android remote event, not another public Seek. Its
                # file-clock jump must exceed ordinary wall-clock progression.
                remote_before=state();remote_start=time.monotonic()
                remote_origin=int(remote_before.get('sageTimelineMs',-1))
                if remote_origin<0:raise RuntimeError('Remote skip has no observed file clock')
                adb.key('FF')
                remote_after=await_state(lambda s:fixed_policy.playing(s) and
                    int(s.get('sageTimelineMs',-1))-remote_origin-int((time.monotonic()-remote_start)*1000)>5000,30)
                time.sleep(2);remote_settled=state()
                if not fixed_policy.advancing(remote_after,remote_settled):raise RuntimeError('Remote skip did not recover advancing output')
                if str(remote_settled.get('mimDirectSessionState','')).startswith('restart_failed'):raise RuntimeError('Remote skip retained the old producer')
                record('reported-source-real-remote-FF-skip',beforeMs=remote_origin,afterMs=remote_settled.get('sageTimelineMs'),
                    elapsedMs=int((time.monotonic()-remote_start)*1000),mappedCommand=remote_settled.get('inputLastMappedCommand'),
                    sessionState=remote_settled.get('mimDirectSessionState'))
                if args.task_id=='HLS-006':
                    rapid_pid=adb.shell('pidof '+package,check=False).strip()
                    rapid_origin=int(remote_settled.get('sageTimelineMs',-1));rapid_clocks=[]
                    for _ in range(5):
                        adb.key('FF');time.sleep(.25)
                        rapid_clocks.append(int(adb.player_state_snapshot().get('sageTimelineMs',-1)))
                    if 0 in rapid_clocks:raise RuntimeError('Rapid skip exposed a transient zero timeline')
                    rapid_after=await_state(lambda s:fixed_policy.playing(s) and int(s.get('sageTimelineMs',-1))>rapid_origin+5000,45)
                    time.sleep(3);rapid_settled=state()
                    if not fixed_policy.advancing(rapid_after,rapid_settled):raise RuntimeError('Rapid skip did not recover advancing output')
                    if adb.shell('pidof '+package,check=False).strip()!=rapid_pid:raise RuntimeError('Rapid skip restarted the Android process')
                    if rapid_settled.get('mimDirectSessionState')!='active_transcode':raise RuntimeError('Rapid skip lost owned producer')
                    record('rapid-remote-skips-recovered',count=5,samplesMs=rapid_clocks,beforeMs=rapid_origin,afterMs=rapid_settled.get('sageTimelineMs'),sameProcess=True)
            if capture_this_row and (backend=='ijkplayer' or args.reported_source):
                marker=OUT/(stamp+'-'+name+'-live-stage.json')
                marker.write_text(json.dumps(dict(scopeVerified=True,device=serial,server=server,clientId=env.device()['client_id'],uiContext=context,mediaFileId=media_id,mode=mode,expiresAtMs=int(time.time()*1000)+60000)))
                print('READY '+str(marker),flush=True);time.sleep(60)
            if mode in ('transcode','hls') and settled.get('mimDirectSessionState')!='active_transcode':raise RuntimeError('Seek lost owned encoding')
            expected_source_channels=int(started.get('health_audioChannels',-1)) if args.reported_source else 6
            if mode in ('transcode','hls') and args.audio_channels=='source' and (expected_source_channels<=0 or int(settled.get('health_audioOutputChannels',-1))!=expected_source_channels):raise RuntimeError('Source-channel AAC output did not retain the observed source channel count')
            pid=adb.shell('pidof '+package,check=False).strip()
            if not re.fullmatch(r'\d+',pid):raise RuntimeError('Owned row diagnostic PID unavailable')
            row_log=adb.run(['logcat','-d','-t','1800','--pid',pid],timeout=15).stdout
            pce_errors=sum('Missing in-band AAC program configuration.' in line for line in row_log.splitlines())
            playback_errors=sum('E ExoPlayerImplInternal: Playback error' in line for line in row_log.splitlines())
            if pce_errors or playback_errors:raise RuntimeError('Normal Fixed row recovered from a decoder error; do not hide it as a clean pass')
            record(name,actual=actual,serverEncoder=observed_encoder,requestedEncoderPolicy=args.policy,videoMime=fixed_policy.video_mime(started),audioChannels=settled.get('health_audioChannels'),decodedAudioChannels=settled.get('health_audioOutputChannels'),audioDecoder=settled.get('health_audioDecoder'),videoDecoder=settled.get('health_videoDecoder'),videoFrames=settled.get('health_videoRendered'),audioFrames=settled.get('health_audioRendered'),nativeVideoOutputFps=settled.get('health_videoOutputFps'),requiresIndependentHdmiAV=backend=='ijkplayer',observedMissingPceErrors=pce_errors,observedPlayerErrors=playback_errors,seekCoordinate='public-server-file-relative')
            stop_playback()
    if args.phase=='output-hevc-motion':
        stage='hevc-no-post-start-ui-motion-comparison'
        setup_playback('transcode','Software Only','HEVC')
        # Single-variable comparison: no Android screenshot/uiautomator/dialog
        # after Watch. Independently inspect HDMI; counters alone are not motion.
        marker=OUT/(stamp+'-hevc-motion-live-stage.json')
        marker.write_text(json.dumps(dict(scopeVerified=True,device=serial,server=server,clientId=env.device()['client_id'],uiContext=context,mediaFileId=media_id,mode='hevc-no-ui',expiresAtMs=int(time.time()*1000)+75000)))
        print('READY '+str(marker),flush=True)
        observations=[]
        for _ in range(13):
            sample=state();observations.append({k:sample.get(k) for k in ('health_playerPositionMs','health_videoRendered','health_audioRendered','health_audioPlaybackHeadFrames','health_bufferedPositionMs','health_isPlaying','health_playerError','health_videoDecoder','health_videoMime')});time.sleep(5)
        text=adb.logcat_tail(3000,r'MediaCodec|Media3|Hls|MimDirect|ExoPlayer')
        text=re.sub(r'https?://\S+','<url>',text);text=re.sub(r'\b[0-9a-fA-F]{32,128}\b','<handle>',text)
        (OUT/(stamp+'-hevc-motion-logcat.txt')).write_text(text,encoding='utf-8')
        (OUT/(stamp+'-hevc-motion-samples.json')).write_text(json.dumps(observations,indent=2))
        record('hevc-counters-only-independent-motion-review-required',physicalMotionReviewRequired=True)
        stop_playback()
    if args.phase in ('output','output-hevc'):
        cases=[('transcode','Software Only','Software (libx265)')] if args.phase=='output-hevc' else [('transcode','Software Only','Software (libx264)'),('hls','Hardware Only (No Software fallback)','Hardware')]
        codec='hevc' if args.phase=='output-hevc' else 'h264'
        for mode,encoder,expected in cases:
            stage=mode+'-owned-output-and-status'
            started=setup_playback(mode,encoder,'HEVC' if codec=='hevc' else 'H.264')
            if started.get('mimDirectNegotiatedMode')!='transcode' or (mode=='hls' and str(started.get('mimHlsOwned')).lower()!='true'):raise RuntimeError('Explicit output did not retain MIM ownership')
            open_panel('transport')
            locate('Video Encoder');locate('Audio Codec');locate('Reset Transcoding Settings Only');close_panels()
            open_panel('diagnostics');tap('Effective Transcoding Output');locate('Effective Transcoding Output')
            labels='\n'.join(n.get('text','') for n in hierarchy().iter('node'))
            for required in ('Server video encoding: '+expected,codec+' 352x198','FPS 24/1','Server audio output: aac','2 channels','Output container: mpegts'):
                if required not in labels:raise RuntimeError('Observed output dialog missing '+required)
            if not args.skip_playback_screenshots:adb.screenshot(OUT/(stamp+'-'+mode+'-effective-output.png'))
            back();close_panels()
            with urlopen(f'http://{serial.rsplit(chr(58),1)[0]}:8272/api/diagnostics',timeout=4) as response:
                exported=json.loads(response.read(524289))
            if 'Server video encoding: '+expected not in exported.get('playback',{}).get('serverOutput',''):raise RuntimeError('JSON diagnostic export lacks current server output')
            with urlopen(f'http://{serial.rsplit(chr(58),1)[0]}:8272/stats',timeout=4) as response:
                exported_text=response.read(524289).decode('utf-8')
            if 'Server video encoding: '+expected not in exported_text:raise RuntimeError('Text diagnostic export lacks current server output')
            before=state()
            marker=OUT/(stamp+'-'+mode+'-live-stage.json')
            marker.write_text(json.dumps(dict(scopeVerified=True,device=serial,server=server,clientId=env.device()['client_id'],uiContext=context,mediaFileId=media_id,mode=mode,expiresAtMs=int(time.time()*1000)+25000)))
            print('READY '+str(marker),flush=True)
            time.sleep(25);after=state()
            if int(after.get('health_videoRendered',0))<=int(before.get('health_videoRendered',0)) or int(after.get('health_audioRendered',0))<=int(before.get('health_audioRendered',0)):raise RuntimeError('A/V did not advance after diagnostic read')
            record(mode+'-actual-output-status-and-av',encoder=encoder,videoFrames=after.get('health_videoRendered'),audioFrames=after.get('health_audioRendered'))
            stop_playback()
        stage='native-pull-clears-server-output'
        setup_playback('pull');open_panel('diagnostics');tap('Effective Transcoding Output')
        labels='\n'.join(n.get('text','') for n in hierarchy().iter('node'))
        if 'Server video encoding: Unknown' not in labels or 'Server decoding: Unknown' not in labels:raise RuntimeError('Previous server output leaked into native Pull')
        back();close_panels();record('native-pull-works-without-mim-and-clears-output');stop_playback()
    if args.phase in ('runtime','all'):
        stage='runtime-hub'
        started=setup_playback('pull')
        if started.get('health_pushMode') is not False:raise RuntimeError('Requested Pull did not negotiate Pull')
        if not api.media_control(context,'pause').get('ok'):raise RuntimeError('Owned pause refused')
        await_state(lambda s:s.get('health_isPlaying') is False and s.get('health_playWhenReady') is False)
        open_panel('navigation');tap('Playback Settings',description=True)
        section('Playback Settings');tap('Video');stage='runtime-video-and-filter-focus';locate('Player Deinterlace')
        tap('Player Deinterlace');locate('Player');locate('Player GPU')
        if node('MIM') is not None:raise RuntimeError('Pull exposes Fixed-only MIM deinterlace')
        back();tap('Video Filters');back()
        if not focused_contains('Video Filters'):raise RuntimeError('Subpanel return did not restore remote row focus')
        section('Advanced Compatibility');locate('Decoder Queueing Mode');back()
        section('Playback Settings');tap('▼ Playback Settings')
        if node('Video') is not None:raise RuntimeError('Collapsed hub exposes hidden child')
        adb.shell('input keyevent 23');locate('Video')
        record('real-gear-shortcut-shared-panels-collapse-center-focus')
        adb.screenshot(OUT/(stamp+'-hub.png'))
        stage='runtime-audio-session-versus-default';tap('Audio');locate('Audio Output');before=configuration(values())
        # Select the visible PCM label with or without its [active] suffix.
        tap('Audio Output');tree=hierarchy();pcm=next((n for n in tree.iter('node') if n.get('text','').startswith('Decoded PCM stereo')),None)
        if pcm is None:raise RuntimeError('Shared PCM output choice missing')
        click(pcm);time.sleep(1)
        tap('Audio Offset');adb.shell('input keyevent 22');back()
        if configuration(values())!=before:raise RuntimeError('Session-only audio adjustment changed saved defaults')
        record('audio-session-only-preserves-saved-defaults')
        section('Device Defaults');tap('Save as Device Defaults')
        if not focused_contains('Cancel'):raise RuntimeError('Audio save does not default to Cancel')
        adb.shell('input keyevent 23')
        if configuration(values())!=before:raise RuntimeError('Cancelled audio default save wrote preferences')
        section('Device Defaults');tap('Save as Device Defaults');tap('Confirm')
        if scalar('playback_audio_offset_ms')==before.get('playback_audio_offset_ms',(None,None,None))[1]:raise RuntimeError('Explicit audio default save did not retain adjusted offset')
        record('audio-explicit-save-and-cancel-focus')
        stage='runtime-caption-transport-recovery';back();tap('Captions / Subtitles');locate('Broadcast CC');locate('Subtitle Appearance');back()
        section('Transport Settings');locate('Selected / Actual Transport')
        if entry_enabled('Processing Method'):raise RuntimeError('Pull exposes Fixed-only settings as active')
        section('Transport Settings',False);section('Recovery');tap('Restore Saved Playback Defaults')
        if not focused_contains('Cancel'):raise RuntimeError('Recovery confirmation is not Cancel-first')
        adb.shell('input keyevent 23');close_panels()
        if not api.media_control(context,'play').get('ok'):raise RuntimeError('Owned resume refused')
        resumed=await_state(lambda s:s.get('health_isPlaying') is True)
        time.sleep(2);after=state()
        if int(after.get('health_videoRendered',0))<=int(resumed.get('health_videoRendered',0)):raise RuntimeError('Playback did not advance after menu closure')
        record('caption-transport-recovery-availability-and-pause-resume')
        stop_playback()
    if args.phase in ('hls','all'):
        stage='hls-saved-next-versus-current-session'
        started=setup_playback('hls')
        if str(started.get('mimHlsOwned')).lower()!='true' or int(started.get('mimDirectVideoTargetKbps',0))!=4000:raise RuntimeError('HLS quality provider not owned at saved Maximum')
        if args.capture_hold:
            marker=OUT/(stamp+'-hls-session-live-stage.json')
            marker.write_text(json.dumps(dict(scopeVerified=True,device=serial,server=server,clientId=env.device()['client_id'],uiContext=context,mediaFileId=media_id,mode='hls',expiresAtMs=int(time.time()*1000)+60000)))
            print('READY '+str(marker),flush=True);time.sleep(60)
        open_panel('transport')
        tap('Maximum Video Bitrate (kbps)')
        arrays=ET.parse(ROOT/'source/dev/android-shared/src/main/res/values/arrays.xml').getroot()
        vals=[n.text for n in arrays.find("string-array[@name='entryvalues_list_video_bitrate_preference']")]
        labels=[n.text for n in arrays.find("string-array[@name='entries_list_video_bitrate_preference']")]
        tap(labels[vals.index('3000')]);locate('Save as device default?')
        if not focused_contains('Cancel'):raise RuntimeError('Fixed default confirmation is not Cancel-first')
        tap('Confirm')
        if scalar('fixed_encoding/video_bitrate_kbps')!='3000':raise RuntimeError('Next-playback Maximum was not explicitly saved')
        if int(state().get('mimDirectVideoTargetKbps',0))!=4000:raise RuntimeError('Saving a next-playback default mutated the active encoder')
        tap('Reduce Current Session')
        reduced=await_state(lambda s:int(s.get('mimDirectVideoTargetKbps',0))==2800 and s.get('mimDirectQualityState') in ('presented','in_place_applied'),45)
        if scalar('fixed_encoding/video_bitrate_kbps')!='3000':raise RuntimeError('Manual session bitrate overwrote the saved Maximum')
        close_panels();time.sleep(2);after=state()
        if int(after.get('health_videoRendered',0))<=int(reduced.get('health_videoRendered',0)):raise RuntimeError('HLS output did not continue after UI handoff')
        if int(after.get('health_audioRendered',0))<=int(reduced.get('health_audioRendered',0)):raise RuntimeError('HLS handoff lost advancing audio')
        if args.audio_channels=='source' and int(after.get('health_audioOutputChannels',-1))!=6:raise RuntimeError('HLS handoff lost Source-channel AAC configuration')
        record('hls-explicit-next-default-versus-frame-confirmed-session-rate',initialKbps=4000,nextDefaultKbps=3000,activeKbps=2800,videoFrames=after.get('health_videoRendered'),audioFrames=after.get('health_audioRendered'),decodedAudioChannels=after.get('health_audioOutputChannels'))
        stop_playback()
    if args.phase in ('recovery','final','all'):
        stage='confirmed-session-restore-and-parent-navigation'
        setup_playback('pull')
        if not api.media_control(context,'pause').get('ok'):raise RuntimeError('Owned pause refused')
        await_state(lambda s:s.get('health_isPlaying') is False and s.get('health_playWhenReady') is False)
        before=configuration(values());saved_offset=int(scalar('playback_audio_offset_ms') or '0')
        open_panel('audio');tap('Audio Offset');adb.shell('input keyevent 22');back()
        changed=state()
        if int(changed.get('audioOffsetMs',0))==saved_offset:raise RuntimeError('Session audio offset was not actually applied')
        close_panels();open_panel('recovery');tap('Restore Saved Playback Defaults')
        if not focused_contains('Cancel'):raise RuntimeError('Session restore confirmation is not Cancel-first')
        tap('Confirm')
        restored=await_state(lambda s:int(s.get('audioOffsetMs',-99999))==saved_offset and str(s.get('audioOffsetSessionOverrideMs'))=='default')
        if configuration(values())!=before:raise RuntimeError('Restoring saved defaults overwrote device preferences')
        if restored.get('health_playWhenReady') is not False:raise RuntimeError('Restoring session defaults lost paused intent')
        record('confirmed-session-restore-keeps-saved-ownership-and-paused-intent')
        stage='caption-availability-scope-and-diagnostic-return'
        close_panels();open_panel('captions')
        # This TS fixture has presentation-time CEA, not ahead-decoded DVD SPU.
        # Do not fake early/negative text timing or click a disabled control.
        if entry_enabled('Subtitle Offset'):raise RuntimeError('Non-DVD text path falsely advertises bounded subtitle offset')
        tap('Subtitle Appearance')
        size=next((n for n in hierarchy().iter('node') if n.get('text','').startswith('Text size: ')),None)
        if size is None:raise RuntimeError('Supported subtitle appearance size entry missing')
        click(size);tap('125%')
        if not focused_contains('This playback session'):raise RuntimeError('Scope chooser did not default to session-only')
        tap('This playback session');locate('Broadcast CC');back()
        open_panel('diagnostics');tap('Server Playback Snapshot');locate('Server playback');back();locate('Playback Status');close_panels()
        if configuration(values())!=before:raise RuntimeError('Caption/session or diagnostic navigation changed saved defaults')
        if not api.media_control(context,'play').get('ok'):raise RuntimeError('Owned resume refused')
        resumed=await_state(lambda s:s.get('health_isPlaying') is True)
        time.sleep(2)
        if int(state().get('health_videoRendered',0))<=int(resumed.get('health_videoRendered',0)):raise RuntimeError('No output after confirmed session restore')
        record('confirmed-session-restore-caption-scope-diagnostic-parent-and-resume')
        stop_playback()
    complete=True
except BaseException as error:
    failure=type(error).__name__+': '+str(error)[:240]
    if failure.endswith('Active player reports an error') and client is not None:
        try:
            observations=[]
            for _ in range(3):
                snap=state()
                observations.append({k:v for k,v in snap.items() if any(w in k.lower() for w in ('decoder','codec','playbacksource','rendered','playererror','isplaying','playwhenready','mimdirectsessionstate'))})
                time.sleep(3)
            (OUT/(stamp+'-post-error-observation.json')).write_text(json.dumps(observations,indent=2)+'\n',encoding='utf-8')
        except Exception:pass
    try:
        # Keep bounded decoder failure evidence before teardown removes the PID.
        pid=adb.shell('pidof '+package,check=False).strip()
        if not re.fullmatch(r'\d+',pid):raise RuntimeError('Exact diagnostic PID unavailable')
        raw=adb.run(['logcat','-d','-t','1800','--pid',pid],timeout=15).stdout
        lines=[line for line in raw.splitlines() if any(word in line for word in ('MediaCodec','ACodec','OMX','DecoderInitialization','Decoder init','ffmpeg_jni','Ffmpeg','ExoPlayerImplInternal','Exception','PLAYER ERROR','Error')) and not any(word in line for word in ('http','token=','password','Authorization'))]
        (OUT/(stamp+'-codec-failure.txt')).write_text('\n'.join(lines[-140:])+'\n',encoding='utf-8')
        (OUT/(stamp+'-codec-capabilities.json')).write_text(json.dumps(adb.codec_capabilities(),indent=2)+'\n',encoding='utf-8')
    except Exception:pass
    try:adb.screenshot(OUT/(stamp+'-failure.png'))
    except Exception:pass
    print('FAIL '+stage+': '+failure,flush=True)
finally:
    try:
        if client is not None:
            if args.phase in ('fixed-failure','fixed-edges'):call_dict(client,'dev_set_mim_direct_late_fallback_fault',{'enabled':False})
            try:stop_playback()
            except Exception as error:cleanup['exitError']=type(error).__name__
        if checkpoint:
            cleanup['checkpointRestored']=bool(adb.restore_settings().get('restored'));adb.force_stop()
            restored=values();cleanup['allOriginalConfigurationValuesRestored']={k:v for k,v in restored.items() if k not in LIVE}=={k:v for k,v in baseline.items() if k not in LIVE}
            cleanup['originalKeysMissing']=len(set(baseline)-set(restored));cleanup['originalPreferenceCount']=len(baseline)
        if lease:cleanup['powerRestored']=not bool(awake.end(serial,OUT/(stamp+'-awake.json')).get('active'))
        if api is not None:
            deadline=time.monotonic()+25
            while True:
                activity=api._request('server.activity')
                if activity.get('safeToRestart') is True:break
                if time.monotonic()>=deadline:raise RuntimeError('Final independent idle proof unavailable')
                time.sleep(.5)
            cleanup['finalActivity']={k:activity.get(k) for k in ('recordingCount','uiContextCount','connectedClientCount','safeToRestart')}
            if args.phase in ('inline','fixed-routes','fixed-failure','hardware-unavailable','fixed-edges','hls'):
                deadline=time.monotonic()+15
                while True:
                    with urlopen(f'http://{server}:31910/v1/capabilities',timeout=5) as response:capabilities=json.load(response)
                    active=capabilities.get('mimDirect',{}).get('activeSessions')
                    if active==0:break
                    if time.monotonic()>=deadline:raise RuntimeError('Provider teardown not proven; active session count is not zero')
                    time.sleep(.5)
                cleanup['finalProviderSessions']=0
    except Exception as error:cleanup['restoreError']=type(error).__name__
    finally:
        adb.close()
        if client is not None:client.close()
        complete=bool(complete and cleanup.get('checkpointRestored') and cleanup.get('allOriginalConfigurationValuesRestored') and cleanup.get('powerRestored') and not any(k.endswith('Error') for k in cleanup))
        report=dict(passed=complete,task=args.task_id,phase=args.phase,device='Non-Pro25/AFTMM',server=server,apkSha256=apk_sha,encoderPolicy=args.policy,hardwareEncoder=args.hardware_encoder,requestedAudioChannels=args.audio_channels,rows=rows,stage=stage,error=failure,lastObservation=last_observation,cleanup=cleanup,coreOrPluginChanges=False,rawPreferencesExported=False)
        with (OUT/(stamp+'-'+args.phase+'.json')).open('x',encoding='utf-8') as stream:json.dump(report,stream,indent=2);stream.write('\n')
        print(json.dumps(report),flush=True)
raise SystemExit(0 if complete else 1)
