#!/usr/bin/env python3
"""Bounded exact232 reverse shaper using existing host Python in its network
namespace. No installs, Core edits, kernel modules, host network/traffic policy,
device global proxy, or third encoder. Remote code is stdin-controlled and
expires after420s; only Non-Pro25 can reach its temporary31911 listener."""
import argparse,importlib.util,json,queue,shlex,subprocess,threading,time
from pathlib import Path
from direct_network_gate import docker_command,CONTAINER
ROOT=Path(__file__).resolve().parents[1]

def run():
    parser=argparse.ArgumentParser();parser.add_argument('--run-non-pro-stock232',action='store_true');parser.add_argument('--healthy-only',action='store_true');parser.add_argument('--experience',action='store_true');parser.add_argument('--player',choices=['media3','exoplayer','gsy-media3','gsy-exoplayer'],default='media3');parser.add_argument('--fault',choices=['none','prepare','candidate-media','seek','stop','old-quality','provider-missing'],default='none')
    parser.add_argument('--hls',action='store_true');parser.add_argument('--runtime-rate',action='store_true')
    parser.add_argument('--buffer-adaptive',action='store_true',help='HLS-005 actual shared MIM buffer-only automatic policy')
    parser.add_argument('--candidate-apk',choices=['/workspace/android-client/source/dev/android-tv/build/outputs/apk/debug/android-tv-debug.apk'],help='Exact container-native built debug APK; do not normalize it as a Windows host Path')
    args=parser.parse_args()
    # Additive flags retain the existing exact-device/network/cleanup owner.
    # Runtime rate requires explicit HLS and its separate task output.
    if args.healthy_only and args.fault!='none':raise SystemExit('Choose one test profile')
    if args.runtime_rate and (not args.hls or args.fault!='none'):raise SystemExit('Runtime rate requires explicit HLS without handoff-only fault profiles')
    if args.buffer_adaptive and (not args.hls or args.runtime_rate or args.fault!='none' or args.healthy_only):raise SystemExit('Buffer-only gate requires isolated HLS without other profiles')
    if not args.run_non_pro_stock232:raise SystemExit('Explicit scope required')
    web=ROOT.parent/'opensagetv-vibe-WebClient-Plugin'
    if not web.is_dir():web=ROOT.parent/'web-client-plugin'
    spec=importlib.util.spec_from_file_location('stock_commission',web/'scripts/commission-mobile.py')
    C=importlib.util.module_from_spec(spec);spec.loader.exec_module(C);cfg=C.configuration()
    if cfg['address']!='192.168.10.232' or cfg['unraid_container_name']!=CONTAINER:raise RuntimeError('Wrong namespace')
    activity=C.activity(cfg)
    if activity.get('safeToRestart') is not True:raise RuntimeError('Independent initial idle required')
    task='HLS-003' if args.runtime_rate else ('HLS-001' if args.hls and args.fault!='none' else 'HLS-002') if args.hls else 'EXPERIENCE-002'
    if args.buffer_adaptive:task='HLS-005'
    output=ROOT/'artifacts/active'/task;output.mkdir(parents=True,exist_ok=True)
    stamp=time.strftime('%Y%m%dT%H%M%SZ',time.gmtime());result={'passed':False,'rates':[],'server':'.232 stock','device':'.25 Non-Pro','player':args.player,'fault':args.fault}
    child=None;control=None;remote=None;events=queue.Queue()
    with C.ssh(cfg) as ssh:
        d=json.loads(C.remote(ssh,'docker inspect '+CONTAINER))[0]
        if d['Name']!='/'+CONTAINER or d['HostConfig']['NetworkMode']=='host' or not any(n.get('IPAddress')=='192.168.10.232' for n in d['NetworkSettings']['Networks'].values()):raise RuntimeError('Container identity not verified')
        identity=d['Id'];pid=int(d['State']['Pid'])
        if pid<=1:raise RuntimeError('Missing network namespace')
        prefix='nsenter -t '+str(pid)+' -n '
        if C.remote(ssh,prefix+"ss -H -lnt 'sport = :31911'").strip():raise RuntimeError('Preserve occupied31911; no replacement')
        protected='docker exec -w /opt/sagetv/server '+CONTAINER+' sha256sum -c /usr/local/share/sagetv-stock/protected.sha256'
        if C.remote(ssh,protected).count(': OK')!=20:raise RuntimeError('Protected stock verification failed')
        before_qdisc=C.remote(ssh,prefix+'tc -j qdisc show dev eth0')
        def observe(stream,kind):
            for line in stream:events.put((kind,line.rstrip()))
            events.put((kind,None))
        try:
            source=(ROOT/'scripts/direct_network_proxy.py').read_text()
            injected='delay-prepare' if args.fault in ('seek','stop') else args.fault
            control,remote,errors=ssh.exec_command('exec '+prefix+'python3 -u -c '+shlex.quote(source)+' --namespace-stock232 --fault '+injected,timeout=380)
            threading.Thread(target=observe,args=(remote,'proxy'),daemon=True).start()
            deadline=time.monotonic()+20
            while True:
                if time.monotonic()>deadline:raise TimeoutError('Namespace shaper readiness')
                try:kind,line=events.get(timeout=1)
                except queue.Empty:continue
                if line is None:raise RuntimeError('Namespace shaper exited before readiness')
                data=json.loads(line)
                if data.get('ready'):
                    if data.get('proxy')!='192.168.10.232:31911':raise RuntimeError('Wrong listener witness')
                    break
            print('READY exact232 reverse shaper; no host qdisc/modules/device proxy changes',flush=True)
            command=docker_command();command[-1]='--network-reverse'
            command[-2]=args.player
            if args.healthy_only:command.append('--healthy-only')
            if args.fault!='none':command+=['--quality-fault',args.fault]
            if args.experience:command.append('--experience')
            if args.hls:
                command.append('--hls')
                if args.fault=='none':command.append('--require-responsive-hls')
            if args.runtime_rate:command.append('--runtime-rate')
            if args.buffer_adaptive:command.append('--buffer-adaptive-network')
            if args.candidate_apk:command+=['--candidate-apk',str(args.candidate_apk)]
            command=[('SAGETV_ARTIFACT_DIR=/workspace/android-client/artifacts/active/'+task) if value.startswith('SAGETV_ARTIFACT_DIR=') else value for value in command]
            child=subprocess.Popen(command,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,encoding='utf-8',errors='replace')
            threading.Thread(target=observe,args=(child.stdout,'child'),daemon=True).start();deadline=time.monotonic()+360
            while True:
                if time.monotonic()>deadline:raise TimeoutError('Bounded network qualification deadline')
                try:kind,line=events.get(timeout=1)
                except queue.Empty:continue
                if kind=='proxy':
                    if line is None:raise RuntimeError('Namespace shaper stopped during physical gate')
                    continue
                if line is None:break
                result.setdefault('childOutputTail',[]).append(line[:500])
                del result['childOutputTail'][:-25]
                if line.startswith(('READY','PASS','WAIT','OBSERVED')):print(line,flush=True)
                rate=2400 if line.startswith('READY production automatic-quality') else 12000 if line.startswith('OBSERVED actual candidate rendered-frame commit;') else None
                if line.startswith('OBSERVED actual in-place bitrate applied;'):rate=12000
                if rate:
                    control.write(json.dumps({'rateKbps':rate})+'\n');control.flush()
                    result['rates'].append({'kbps':rate,'timestampMs':round(time.time()*1000)})
                    print('SHAPED published TS bodies '+str(rate)+'kbps',flush=True)
            result['childExitCode']=child.wait(timeout=10)
            if result['childExitCode']:raise RuntimeError('Physical gate failed; retain its exact quality report')
            result['passed']=args.healthy_only or args.fault!='none' or len(result['rates'])==2
        except BaseException as error:
            result['failure']=type(error).__name__+': '+str(error)[:250];raise
        finally:
            if child is not None and child.poll() is None:
                ps=subprocess.run(['docker','exec','opensagetv-vibe-dev','ps','-eo','pid,args'],capture_output=True,text=True,timeout=15)
                exact=' '.join(command[command.index('/opt/opensagetv-vibe/android-python/bin/python3'):])
                matches=[line.strip().split(None,1)[0] for line in ps.stdout.splitlines() if len(line.strip().split(None,1))==2 and line.strip().split(None,1)[1]==exact]
                if len(matches)==1:subprocess.run(['docker','exec','opensagetv-vibe-dev','kill','-TERM',matches[0]],check=True,timeout=15)
                try:child.wait(timeout=55)
                except subprocess.TimeoutExpired:result['driverCleanupError']='Owned device restoration remains incomplete'
            if control is not None:
                try:control.write('{"stop":true}\n');control.flush();control.close()
                except Exception:pass
                deadline=time.monotonic()+15
                while time.monotonic()<deadline:
                    try:kind,line=events.get(timeout=1)
                    except queue.Empty:continue
                    if kind!='proxy' or line is None:continue
                    data=json.loads(line)
                    if data.get('stopped'):result['proxyCounters']=data;break
            after=json.loads(C.remote(ssh,'docker inspect '+CONTAINER))[0]
            if after['Id']!=identity or int(after['State']['Pid'])!=pid:raise RuntimeError('Namespace changed during cleanup; no stale probe')
            result['listenerRetired']=not bool(C.remote(ssh,prefix+"ss -H -lnt 'sport = :31911'").strip())
            result['networkPolicyUnchanged']=C.remote(ssh,prefix+'tc -j qdisc show dev eth0')==before_qdisc
            result['protectedStockFilesVerified']=C.remote(ssh,protected).count(': OK')
            counters=result.get('proxyCounters',{}).get('bytesByRateKbps',{})
            valid_delivery=args.fault=='provider-missing' or bool(counters.get('12000'))
            if not args.healthy_only and args.fault=='none':valid_delivery=valid_delivery and bool(counters.get('2400'))
            if args.fault in ('seek','stop'):
                # The child independently proves TWO real owned producers
                # before seek/STOP. Cancellation can correctly precede the
                # origin's first ready reply, so its optional reply delay may
                # never execute. Do not mislabel that as missing cancellation.
                valid_delivery=valid_delivery and bool(result.get('proxyCounters',{}).get('prepareRequests'))
            elif args.fault!='none':valid_delivery=valid_delivery and bool(result.get('proxyCounters',{}).get('faultedRequests'))
            if not result['listenerRetired'] or not result['networkPolicyUnchanged'] or result['protectedStockFilesVerified']!=20 or not valid_delivery:result['passed']=False
            (output/(stamp+'-reverse-network.json')).write_text(json.dumps(result,indent=2)+'\n')
            summary={key:result.get(key) for key in ('passed','listenerRetired','networkPolicyUnchanged','protectedStockFilesVerified')}
            summary['proxyCounters']={key:value for key,value in result.get('proxyCounters',{}).items() if key!='bufferReports'}
            summary['bufferReportCount']=len(result.get('proxyCounters',{}).get('bufferReports',[]))
            print(json.dumps(summary),flush=True)
    if not result['passed']:raise SystemExit(1)

if __name__=='__main__':run()
