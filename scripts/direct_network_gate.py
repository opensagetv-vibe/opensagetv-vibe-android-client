#!/usr/bin/env python3
"""EXPERIENCE-002 real exact-client shaping, outside protected Core files.

The stock public MCP APIs own activity/playback controls. Bandwidth shaping is
not a SageTV API capability: this external tool enters ONLY the verified232
container's network namespace, never the Unraid host/175 namespace. It refuses
existing custom qdiscs, selects only TCP31910 responses destined for Non-Pro25,
and restores the original default in finally. No packages/files/INI installed.
"""
import argparse,importlib.util,json,os,queue,subprocess,threading,time
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
CONTAINER='sagetv-vibe-server-u26-gpu-j11'

def pristine(qdiscs):
    return len(qdiscs)==1 and qdiscs[0].get('kind')=='noqueue' and qdiscs[0].get('handle')=='0:' and qdiscs[0].get('root') is True

def docker_command():
    values={'JAVA_HOME':'/opt/java/jdk17','SAGETV_WORKSPACE':'/workspace/android-client',
        'SAGETV_MCP_CONFIG':'/workspace/android-client/config/firetv.toml','PYTHONPATH':'/workspace/android-client/mcp/src',
        'ANDROID_USER_HOME':'/workspace/android-client/adb','ADB_VENDOR_KEYS':'/workspace/android-client/adb',
        'SAGETV_TEST_DEVICE_ALIAS':'non_pro','SAGETV_TEST_SERVER_ALIAS':'vibe',
        'SAGETV_ARTIFACT_DIR':'/workspace/android-client/artifacts/active/EXPERIENCE-002',
        'PYTHONDONTWRITEBYTECODE':'1','PYTHONUNBUFFERED':'1',
        'PATH':'/opt/java/jdk17/bin:/opt/android-sdk/platform-tools:/opt/opensagetv-vibe/android-python/bin:/usr/local/bin:/usr/bin:/bin'}
    command=['docker','exec']
    for key,value in values.items():command+=['-e',key+'='+value]
    return command+['-w','/workspace/android-client','opensagetv-vibe-dev',
        '/opt/opensagetv-vibe/android-python/bin/python3','scripts/mcp_direct_quality_gate.py','--player','media3','--network-shaped']

def run():
    parser=argparse.ArgumentParser();parser.add_argument('--run-non-pro-stock232',action='store_true');args=parser.parse_args()
    if not args.run_non_pro_stock232:raise SystemExit('Explicit exact test scope required')
    spec=importlib.util.spec_from_file_location('stock_commission',ROOT.parent/'opensagetv-vibe-WebClient-Plugin/scripts/commission-mobile.py')
    C=importlib.util.module_from_spec(spec);spec.loader.exec_module(C);cfg=C.configuration()
    if cfg['address']!='192.168.10.232' or cfg['unraid_container_name']!=CONTAINER:raise RuntimeError('Wrong server namespace')
    activity=C.activity(cfg)
    if activity.get('safeToRestart') is not True:raise RuntimeError('Independent initial idle required')
    out=ROOT/'artifacts/active/EXPERIENCE-002';out.mkdir(parents=True,exist_ok=True)
    stamp=time.strftime('%Y%m%dT%H%M%SZ',time.gmtime());report={'server':'.232 stock','device':'.25 Non-Pro','passed':False,'rates':[]}
    process=None;owned=False;failure=None
    with C.ssh(cfg) as ssh:
        def inspect():
            data=json.loads(C.remote(ssh,'docker inspect '+CONTAINER))[0]
            if data['Name']!='/'+CONTAINER or data['HostConfig']['NetworkMode']=='host' or not any(n.get('IPAddress')=='192.168.10.232' for n in data['NetworkSettings']['Networks'].values()):raise RuntimeError('Namespace identity changed')
            return data['Id'],int(data['State']['Pid'])
        identity,pid=inspect()
        if pid<=1:raise RuntimeError('Container network namespace unavailable')
        def tc(arguments):
            if inspect()!=(identity,pid):raise RuntimeError('Container was replaced/restarted; refuse a stale namespace')
            return C.remote(ssh,'nsenter -t '+str(pid)+' -n tc '+arguments)
        before=json.loads(tc('-j qdisc show dev eth0'))
        if not pristine(before):raise RuntimeError('Preserve existing custom network policy; no shaping applied')
        protected='docker exec -w /opt/sagetv/server '+CONTAINER+' sha256sum -c /usr/local/share/sagetv-stock/protected.sha256'
        if C.remote(ssh,protected).count(': OK')!=20:raise RuntimeError('Protected stock verification failed')
        try:
            tc('qdisc add dev eth0 root handle 7a00: prio bands 3');owned=True
            tc('qdisc add dev eth0 parent 7a00:3 handle 7a30: tbf rate 12000kbit burst 32768 latency 5000ms')
            tc('filter add dev eth0 parent 7a00: protocol ip prio 1 u32 match ip dst 192.168.10.25/32 match ip protocol 6 0xff match ip sport 31910 0xffff flowid 7a00:3')
            report['initialActivity']={k:activity[k] for k in ('timestampMs','recordingCount','uiContextCount','connectedClientCount')}
            print('READY exact232 namespace/Non-Pro25 TCP31910 shaper; other flows unchanged',flush=True)
            process=subprocess.Popen(docker_command(),stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,encoding='utf-8',errors='replace')
            lines=queue.Queue()
            def reader():
                for line in process.stdout:lines.put(line.rstrip())
                lines.put(None)
            threading.Thread(target=reader,daemon=True).start();deadline=time.monotonic()+360
            while True:
                if time.monotonic()>deadline:raise TimeoutError('Bounded network gate deadline')
                try:line=lines.get(timeout=1)
                except queue.Empty:continue
                if line is None:break
                if line.startswith(('READY','PASS','WAIT','OBSERVED')):print(line,flush=True)
                if line.startswith('READY production automatic-quality'):
                    tc('qdisc change dev eth0 parent 7a00:3 handle 7a30: tbf rate 2400kbit burst 32768 latency 5000ms')
                    report['rates'].append({'kbps':2400,'after':'production opt-in','timestampMs':round(time.time()*1000)})
                    print('SHAPED measured body delivery at2400kbps for exact client',flush=True)
                if line.startswith('PASS media3 actual Direct quality'):
                    tc('qdisc change dev eth0 parent 7a00:3 handle 7a30: tbf rate 12000kbit burst 32768 latency 5000ms')
                    report['rates'].append({'kbps':12000,'after':'actual downshift frame','timestampMs':round(time.time()*1000)})
                    print('RESTORED12000kbps for measured upward recovery',flush=True)
            code=process.wait(timeout=10);report['childExitCode']=code
            if code:raise RuntimeError('Affected physical gate failed; retain owning quality report')
            if len(report['rates'])!=2:raise RuntimeError('Both real shaping phases were not witnessed')
            report['passed']=True
        except BaseException as error:
            failure=type(error).__name__+': '+str(error)[:300];raise
        finally:
            if process is not None and process.poll() is None:
                # Signal only the uniquely verified owned container Python
                # driver, not adb/MCP servers or unrelated test processes. Its
                # SIGTERM handler enters the normal settings/power finally.
                ps=subprocess.run(['docker','exec','opensagetv-vibe-dev','ps','-eo','pid,args'],capture_output=True,text=True,timeout=15)
                exact='/opt/opensagetv-vibe/android-python/bin/python3 scripts/mcp_direct_quality_gate.py --player media3 --network-shaped'
                matches=[line.strip().split(None,1)[0] for line in ps.stdout.splitlines() if len(line.strip().split(None,1))==2 and line.strip().split(None,1)[1]==exact]
                if len(matches)==1:subprocess.run(['docker','exec','opensagetv-vibe-dev','kill','-TERM',matches[0]],check=True,timeout=15)
                try:process.wait(timeout=55)
                except subprocess.TimeoutExpired:report['cleanupError']='Owned device driver has not completed restoration'
            if owned:
                current=json.loads(tc('-j qdisc show dev eth0'))
                if not any(q.get('root') and q.get('kind')=='prio' and q.get('handle')=='7a00:' for q in current):raise RuntimeError('Owned root changed; refuse removal of another policy')
                tc('qdisc del dev eth0 root handle 7a00:')
                report['originalNetworkRestored']=json.loads(tc('-j qdisc show dev eth0'))==before
                if not report['originalNetworkRestored']:raise RuntimeError('Original network state not restored')
            else:report['originalNetworkRestored']=json.loads(tc('-j qdisc show dev eth0'))==before
            report['protectedStockFilesVerified']=C.remote(ssh,protected).count(': OK')
            report['failure']=failure
            if report.get('cleanupError') or report['protectedStockFilesVerified']!=20:report['passed']=False
            (out/(stamp+'-network-shaping.json')).write_text(json.dumps(report,indent=2)+'\n')
            print(json.dumps({'passed':report['passed'],'originalNetworkRestored':report.get('originalNetworkRestored'),'protectedStockFilesVerified':report['protectedStockFilesVerified']}),flush=True)

if __name__=='__main__':run()
