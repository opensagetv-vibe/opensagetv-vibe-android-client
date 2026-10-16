#!/usr/bin/env python3
"""Read finite generated-fixture geometry through the real commissioned MCP.

Run only while the separately scoped playback gate holds the field-ID fixture.
This explicitly starts one DEBUG probe, never Watch/seek/install/preferences.
Pixels qualify decoded input geometry, not filtered output/display cadence.
"""
import argparse
import json
from pathlib import Path
import time
from mcp_smoke_test import MCPProcess, initialize
from mcp_adb_connection_test import structured

def classify(samples):
    counts={'woven':0,'progressive':0,'indeterminate':0}
    ids=[]
    for sample in samples:
        rows=sample.get('rowIds',[])
        if len(rows)!=4 or any(value is None for value in rows):
            counts['indeterminate']+=1
        elif len(set(rows))==1:
            counts['progressive']+=1;ids.append(rows[0])
        elif rows[0]==rows[2] and rows[1]==rows[3] and (
                (rows[1]-rows[0])%256==1 or (rows[0]-rows[1])%256==1):
            counts['woven']+=1
        else:counts['indeterminate']+=1
    # Diagnostic continuity only; the small readback itself can affect timing.
    gaps=[(b-a)%256 for a,b in zip(ids,ids[1:])]
    return {'decodedGeometry':counts,'progressiveFieldIdSteps':gaps,
            'scanoutQualified':False,'timingQualified':False}

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args()
    root=Path(__file__).resolve().parents[1];output=args.output.resolve()
    if not output.is_relative_to((root/'artifacts/active/INTERLACE-004').resolve()):
        parser.error('output must be under artifacts/active/INTERLACE-004')
    report={'passed':False,'scope':'finite DEBUG generated-field-ID input geometry only'}
    client=MCPProcess()
    try:
        initialize(client)
        report['connection']=structured(client,'adb_connect',timeout=120)
        report['scopeWitness']=structured(client,'dev_server_control_identity',timeout=40)
        if not report['scopeWitness'].get('scopeVerified'):raise RuntimeError('scope unverified')
        report['start']=structured(client,'dev_gpu_field_probe')
        if not report['start'].get('accepted'):raise RuntimeError('probe unavailable')
        deadline=time.monotonic()+12
        while True:
            status=structured(client,'dev_gpu_field_probe_status')
            if status.get('complete') is True:break
            if time.monotonic()>=deadline:raise TimeoutError('finite probe did not complete')
            time.sleep(.2)
        samples=json.loads(status.get('samples','[]'))
        report['samples']=samples;report['analysis']=classify(samples)
        if status.get('error') or len(samples)!=64:raise RuntimeError('incomplete/error measurement')
        report['passed']=True
    except Exception as error:report['error']=str(error)
    finally:
        client.close();output.parent.mkdir(parents=True,exist_ok=True)
        output.write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({key:report.get(key) for key in ('passed','analysis','error')}))
    return 0 if report['passed'] else 1

if __name__=='__main__':raise SystemExit(main())
