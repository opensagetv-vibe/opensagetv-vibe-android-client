#!/usr/bin/env python3
"""SETTINGS-001 actual Non-Pro menu/update/reset proof, no server mutations.

Exact development APK, private on-device checkpoint, keyboard focus checks and
production settings UI. Never export preference values/credentials to artifacts.
"""
import hashlib,json,os,re,signal,subprocess,sys,time,uuid
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'mcp/src'))
from sagetv_dev_mcp.adb import AdbClient
from sagetv_dev_mcp.config import load_test_environment
import android_test_keep_awake as awake

OUT=ROOT/'artifacts/active/SETTINGS-001';OUT.mkdir(parents=True,exist_ok=True)
stamp=time.strftime('%Y%m%dT%H%M%SZ',time.gmtime())
env=load_test_environment()
if env.device_serial()!='192.168.10.25:5555':raise SystemExit('Exact commissioned Non-Pro25 required')
package='opensagetv.vibe.miniclient.debug'
adb=AdbClient(env.device_serial(),package)
apk=ROOT/'source/dev/android-tv/build/outputs/apk/debug/android-tv-debug.apk'
rows=[];cleanup={};checkpoint=False;lease=False;passed=False;failure=None
baseline=None;apk_sha=None
LIVE_STATUS={'client_diagnostics_web/status','server_diagnostics/status'}
class CompletedSelectedScope(Exception):pass
def interrupted(signum,frame):raise KeyboardInterrupt('Owned settings test interrupted; restoring private checkpoint')
signal.signal(signal.SIGTERM,interrupted)
if hasattr(signal,'SIGALRM'):
    signal.signal(signal.SIGALRM,interrupted);signal.alarm(900)

def configuration_only(items):return {key:value for key,value in items.items() if key not in LIVE_STATUS}

def values():
    raw=adb.shell('run-as '+package+' cat shared_prefs/'+package+'_preferences.xml')
    root=ET.fromstring(raw)
    # In-memory only. Opaque credentials/settings never enter reports/exceptions.
    return {n.attrib['name']:(n.tag,n.attrib.get('value'),(n.text or '') if n.tag=='string' else None,
                            tuple(sorted((c.tag,c.text or '') for c in n))) for n in root}

def hierarchy():
    remote='/data/local/tmp/vibe-settings-'+uuid.uuid4().hex+'.xml'
    try:
        adb.shell('uiautomator dump '+remote,timeout=30)
        raw=adb.shell('cat '+remote)
        tree=ET.fromstring(raw[raw.index('<hierarchy'):raw.rindex('</hierarchy>')+12])
        if not any(n.get('package')==package for n in tree.iter('node')):raise RuntimeError('Expected development settings UI not visible')
        return tree
    finally:adb.shell('rm -f '+remote,check=False)

def find(label,tree=None):
    tree=hierarchy() if tree is None else tree
    return next((n for n in tree.iter('node') if n.get('text','').casefold()==label.casefold()),None)

def tap_node(node):
    coords=list(map(int,re.findall(r'\d+',node.get('bounds',''))))
    if len(coords)!=4 or coords[2]<=coords[0] or coords[3]<=coords[1]:raise RuntimeError('Invalid reviewed UI target bounds')
    adb.shell('input tap '+str((coords[0]+coords[2])//2)+' '+str((coords[1]+coords[3])//2));time.sleep(.25)

def scroll(direction,tree=None):
    tree=hierarchy() if tree is None else tree
    node=next((n for n in tree.iter('node') if n.get('scrollable')=='true'),None)
    if node is None:return False
    x1,y1,x2,y2=map(int,re.findall(r'\d+',node.get('bounds')))
    # Overlapping half-pages prevent a fling from skipping an entire heading.
    middle=(y1+y2)//2
    top,bottom=middle-(y2-y1)//5,middle+(y2-y1)//5
    start,end=(bottom,top) if direction=='down' else (top,bottom)
    adb.shell(f'input swipe {(x1+x2)//2} {start} {(x1+x2)//2} {end} 450');time.sleep(.3);return True

def locate(label):
    for direction in ('up','down'):
        previous=None
        for _ in range(28):
            tree=hierarchy();node=find(label,tree)
            if node is not None:return node
            signature=tuple((n.get('text'),n.get('bounds')) for n in tree.iter('node') if n.get('text'))
            if signature==previous:break
            previous=signature
            if not scroll(direction,tree):break
    raise RuntimeError('Menu target unavailable: '+label)

def tap(label):tap_node(locate(label))

def section(title,expanded=True):
    for direction in ('up','down'):
        previous=None
        for _ in range(28):
            tree=hierarchy()
            for prefix in ('▼ ','▶ '):
                node=find(prefix+title,tree)
                if node is None:continue
                if (prefix=='▼ ')==expanded:return node
                tap_node(node);return locate(('▼ ' if expanded else '▶ ')+title)
            signature=tuple((n.get('text'),n.get('bounds')) for n in tree.iter('node') if n.get('text'))
            if signature==previous:break
            previous=signature
            if not scroll(direction,tree):break
    raise RuntimeError('Section heading unavailable: '+title)

def choose(title,entry):
    tap(title);tap(entry)

def start_settings():
    receipt=adb.dev_control('main_settings_ui')
    if not receipt.get('launched'):raise RuntimeError('Main settings launch refused')
    time.sleep(.5);hierarchy()

def row(name):rows.append(dict(gate=name,passed=True));print('PASS '+name,flush=True)

def is_saved(key,value):
    current=values().get(key)
    return current is not None and (current[1] if current[0]!='string' else current[2])==str(value).lower()

def reset_gate():
    section('General',True)
    before_cancel=values();tap('Reset Settings to Defaults')
    tree=hierarchy()
    if not any(n.get('focused')=='true' and n.get('text','').casefold()=='cancel' for n in tree.iter('node')):raise RuntimeError('Reset dialog does not default to Cancel')
    adb.shell('input keyevent 23');time.sleep(.2)
    if configuration_only(values())!=configuration_only(before_cancel):raise RuntimeError('Cancelled reset changed settings')
    tap('Reset Settings to Defaults');tap('Reset');time.sleep(.5)
    for key,value in [('use_opengl_ui',True),('unified_graphics_surfaces',True),('streaming_mode','dynamic'),('mim_direct/adaptive_quality',False),('mim_direct/runtime_rate_control',False)]:
        if not is_saved(key,value):raise RuntimeError('Confirmed reset did not restore approved defaults')
    after_reset=values()
    generated={key:value for key,value in after_reset.items() if key.endswith('/support') and key.startswith(('container/','codec/video/','codec/audio/'))}
    if not generated or any(value[0]!='string' or value[2]!='automatic' for value in generated.values()):raise RuntimeError('Generated codec/container defaults were not reset')
    protected=[key for key in before_cancel if key=='clientid' or key.startswith(('smb_','settings/')) or re.search('password|credential|token|secret|client_id|server_id|profile',key,re.I)]
    if any(after_reset.get(key)!=before_cancel[key] for key in protected):raise RuntimeError('Reset changed protected identity/credential/profile values')
    row('reset-cancel-default-focus-and-confirmed-protected-reset')
    row('reset-generated-codec-container-defaults')
    adb.screenshot(OUT/(stamp+'-reset-complete.png'))

if '--inspect-only' in sys.argv:
    try:
        adb.connect();captured=adb.checkpoint_settings().get('captured')
        if not captured:raise RuntimeError('Diagnostic checkpoint refused')
        start_settings();tree=hierarchy()
        titles=('Playback Settings','Transport Settings','Mappings','General','Diagnostics','About')
        print(json.dumps({'readOnlyHierarchyObservation':True,'headers':[
            {'text':n.get('text'),'description':n.get('content-desc'),'bounds':n.get('bounds')}
            for n in tree.iter('node') if any(t in n.get('text','') or n.get('content-desc','').startswith(t+',') for t in titles)],
            'scrollables':[n.get('bounds') for n in tree.iter('node') if n.get('scrollable')=='true']}),flush=True)
    finally:
        if locals().get('captured'):adb.restore_settings();adb.force_stop()
        adb.close()
    raise SystemExit()

try:
    adb.connect()
    if adb.shell('getprop ro.product.model').strip()!='AFTMM':raise RuntimeError('Wrong model; no mutations')
    baseline=values()
    receipt=adb.checkpoint_settings()
    if not receipt.get('captured'):raise RuntimeError('Private preference checkpoint refused')
    checkpoint=True
    lease=bool(awake.begin(adb.serial,OUT/(stamp+'-awake.json'),'automated').get('active'))
    with apk.open('rb') as stream:apk_sha=hashlib.file_digest(stream,'sha256').hexdigest()
    if '--reuse-installed' not in sys.argv:
        result=adb.install_dev_apk(apk)
        if 'Success' not in result:raise RuntimeError('Update did not complete successfully')
    installed=adb.shell('pm path '+package).strip().removeprefix('package:')
    if not installed.startswith('/data/app/') or '\n' in installed:raise RuntimeError('Unexpected installed APK path')
    installed_bytes=subprocess.run(adb._base(True)+['exec-out','cat',installed],stdin=subprocess.DEVNULL,
                                   capture_output=True,timeout=60,check=True)
    actual=hashlib.sha256(installed_bytes.stdout).hexdigest();del installed_bytes
    if actual!=apk_sha:raise RuntimeError('Installed APK differs from built candidate')
    start_settings()
    current=values()
    for key,value in baseline.items():
        if key in LIVE_STATUS:continue
        if key=='streaming_mode' and value[2]=='fixed_adjusted':
            if not is_saved(key,'fixed'):raise RuntimeError('Legacy adjusted transport migration failed')
        elif current.get(key)!=value:raise RuntimeError('An existing saved preference changed during update')
    row('update-preserves-all-existing-saved-values')
    if '--reset-only' in sys.argv:
        reset_gate();raise CompletedSelectedScope()
    section('Playback Settings',True)
    tap('▼ Playback Settings')
    if find('Default Player') is not None:raise RuntimeError('Collapsed section still exposes children')
    if not any(n.get('focused')=='true' and any(c.get('text')=='▶ Playback Settings' for c in n.iter('node')) for n in hierarchy().iter('node')):
        raise RuntimeError('Collapse lost remote focus')
    adb.shell('input keyevent 23');time.sleep(.4)
    if find('Default Player') is None:raise RuntimeError('Center activation did not expand the focused section')
    row('collapse-center-expand-hidden-children-and-focus')
    # Both states survive a fresh activity; no preference values are removed.
    tap('▼ Playback Settings');adb.shell('input keyevent 4');start_settings()
    locate('▶ Playback Settings');section('Playback Settings',True);row('expansion-persistence')
    for label,screen in [('Media3 ExoPlayer (Google current)','Media3PlayerSettingsActivity'),('IJKPlayer (Original 0.8.8 runtime)','IJKPlayerSettingsActivity'),('GSYVideoPlayer (selectable engine)','GSYPlayerSettingsActivity'),('ExoPlayer Legacy (working baseline)','ExoPlayerSettingsActivity')]:
        choose('Default Player',label);tap('Selected Player Settings')
        resumed=[line for line in adb.shell('dumpsys activity activities').splitlines() if 'mResumedActivity' in line or 'topResumedActivity' in line]
        if not any(screen in line for line in resumed):raise RuntimeError('Selected player routed to wrong settings')
        adb.shell('input keyevent 4');time.sleep(.2)
        locate('Selected Player Settings')
        print('OBSERVED selected-player route '+screen,flush=True)
    row('four-selected-player-routes-and-return')
    section('Playback Settings',False);section('Transport Settings',True)
    choose('Transport Method','Push');tap('Push Settings');locate('Push Buffer Write Limit');adb.shell('input keyevent 4')
    choose('Transport Method','Fixed');tap('Fixed Playback Settings')
    choose('Processing Method','Stock SageTV Pipeline');locate('Remuxing Method')
    choose('Remuxing Method','Off')
    if locate('Remux Container Format').get('enabled')!='false':raise RuntimeError('Off remux container remains editable')
    choose('Processing Method','Direct Copy (No Re-encoding)')
    if locate('Automatic Bitrate Adjustment').get('enabled')!='false':raise RuntimeError('Copy exposes automatic re-encoding control')
    choose('Processing Method','Transcode Low Latency (HLS)')
    for entry,adaptive,inplace in [('Off',False,False),('Stream Switching',True,False),('Prefer In-place (Fallback to Switching)',True,True)]:
        choose('Automatic Bitrate Adjustment',entry)
        if not is_saved('mim_direct/adaptive_quality',adaptive) or not is_saved('mim_direct/runtime_rate_control',inplace):raise RuntimeError('Adjustment selection did not map to correct playback flags')
    row('stock-remux-copy-applicability-and-three-adjustment-methods')
    adb.screenshot(OUT/(stamp+'-fixed-settings.png'))
    adb.shell('input keyevent 4');reset_gate()
    passed=True
except CompletedSelectedScope:
    passed=True
except BaseException as error:
    try:adb.screenshot(OUT/(stamp+'-failure.png'))
    except Exception:pass
    failure=type(error).__name__+': '+str(error)[:240]
    if isinstance(error,AssertionError):failure='AssertionError: scoped menu assertion failed'
    print('FAIL '+failure,flush=True)
finally:
    try:
        if checkpoint:
            restored=adb.restore_settings();cleanup['checkpointRestored']=bool(restored.get('restored'))
            adb.force_stop();restored_values=values();cleanup['allOriginalConfigurationValuesRestored']=configuration_only(restored_values)==configuration_only(baseline)
            cleanup['originalKeysMissing']=len(set(baseline)-set(restored_values))
            cleanup['originalConfigurationValuesDifferent']=sum(restored_values.get(key)!=value for key,value in configuration_only(baseline).items())
            cleanup['liveStatusFieldsDifferent']=sum(restored_values.get(key)!=value for key,value in baseline.items() if key in LIVE_STATUS)
            cleanup['changedKnownPreferenceKeys']=[key for key,value in baseline.items() if restored_values.get(key)!=value
                and re.fullmatch('[A-Za-z0-9_/]+',key) and not re.search('password|credential|token|secret|profile|server|clientid',key,re.I)]
        if lease:cleanup['powerRestored']=not bool(awake.end(adb.serial,OUT/(stamp+'-awake.json')).get('active'))
    finally:
        adb.close()
        restored=cleanup.get('checkpointRestored') and cleanup.get('allOriginalConfigurationValuesRestored') and cleanup.get('powerRestored')
        report=dict(passed=bool(passed and restored),scope='reset-only' if '--reset-only' in sys.argv else 'full',device='Non-Pro25/AFTMM',task='SETTINGS-001',apkSha256=apk_sha,
                    originalPreferenceCount=len(baseline or {}),rows=rows,cleanup=cleanup,error=failure,serverMutations=False,rawPreferenceValuesExported=False)
        with (OUT/(stamp+'-menu-gate.json')).open('x',encoding='utf-8') as stream:json.dump(report,stream,indent=2);stream.write('\n')
        print(json.dumps(report),flush=True)
raise SystemExit(0 if report['passed'] else 1)
