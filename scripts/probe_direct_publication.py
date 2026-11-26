#!/usr/bin/env python3
"""EXPERIENCE-002 isolated stock232 producer timing, not Android/frame proof.

Use only the imported canonical fixture through the existing public provider.
No Core/control additions, raw handles, paths in output, or retained producer.
"""
import argparse,importlib.util,json,time
from pathlib import Path
from urllib.parse import urlencode
from urllib.request import Request,urlopen

ROOT=Path(__file__).resolve().parents[1]
SOURCE='/var/media/OpenSageTV_Vibe_Tests/VibeSeekTest-1080i-MPEG2-AC3-CC.ts'

def run():
    parser=argparse.ArgumentParser();parser.add_argument('--run-stock232',action='store_true');args=parser.parse_args()
    if not args.run_stock232:raise SystemExit('Explicit exact stock232 scope required')
    spec=importlib.util.spec_from_file_location('stock_commission',ROOT.parent/'opensagetv-vibe-WebClient-Plugin/scripts/commission-mobile.py')
    C=importlib.util.module_from_spec(spec);spec.loader.exec_module(C);cfg=C.configuration()
    if cfg['address']!='192.168.10.232' or not C.activity(cfg).get('safeToRestart'):raise RuntimeError('Exact stock232 idle required')
    base='http://192.168.10.232:31910';token=None;rows=[];report={'purpose':'Isolated origin publication; not client or physical presentation evidence'}
    out=ROOT/'artifacts/active/EXPERIENCE-002';out.mkdir(parents=True,exist_ok=True)
    stamp=time.strftime('%Y%m%dT%H%M%SZ',time.gmtime())
    def post(action,**fields):
        with urlopen(Request(base+'/v1/direct/'+action+'?'+urlencode(fields),data=b''),timeout=30) as reply:return json.load(reply)
    def active():
        with urlopen(base+'/v1/capabilities',timeout=3) as reply:return json.load(reply)['mimDirect']['activeSessions']
    if active()!=0:raise RuntimeError('Preserve existing producer')
    try:
        started=time.monotonic();session=post('start',source=SOURCE,mode='transcode',startMs=42000,videoKbps=1500,deinterlace='on')
        token=session['sessionToken'];media=session['mediaUrl']
        if not media.startswith('/v1/direct/media/'+token+'/'):raise RuntimeError('Provider media lineage mismatch')
        report['startReplyMs']=round((time.monotonic()-started)*1000)
        for index in range(11):
            with urlopen(base+media,timeout=3) as reply:playlist=reply.read(65537).decode('utf-8')
            if len(playlist)>65536 or not playlist.startswith('#EXTM3U'):raise RuntimeError('Invalid bounded media playlist')
            durations=[float(line[8:].split(',')[0]) for line in playlist.splitlines() if line.startswith('#EXTINF:')]
            target=next((float(line.split(':',1)[1]) for line in playlist.splitlines() if line.startswith('#EXT-X-TARGETDURATION:')),None)
            row={'elapsedMs':round((time.monotonic()-started)*1000),'segments':len(durations),'publishedSeconds':round(sum(durations),3),'targetDurationSeconds':target}
            rows.append(row);print(json.dumps(row),flush=True)
            if index<10:time.sleep(2)
        report['passed']=True
    except BaseException as error:
        report['failure']=type(error).__name__;raise
    finally:
        if token:
            try:post('teardown',token=token);report['ownedTeardown']=True
            except Exception:report['ownedTeardown']=False
        report['activeSessionsAfter']=active();report['rows']=rows
        report['finalActivity']={key:value for key,value in C.activity(cfg).items() if key in ['safeToRestart','recordingCount','uiContextCount','connectedClientCount','timestampMs']}
        (out/(stamp+'-origin-publication.json')).write_text(json.dumps(report,indent=2)+'\n')
        print(json.dumps({key:report.get(key) for key in ['passed','startReplyMs','ownedTeardown','activeSessionsAfter']}),flush=True)

if __name__=='__main__':run()
