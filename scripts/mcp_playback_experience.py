"""Bounded on-demand production diagnostic reader for EXPERIENCE-001 gates.

Never controls playback, invents presented PTS, or retains unrelated fields
from the diagnostic page. The owning gate checkpoints/restores its opt-in.
"""
import json,time
from urllib.request import urlopen

FIELDS=('schemaVersion','active','sourceGeneration','seekSequence',
        'requestedPositionMs','clientAcceptedPositionMs','producerAcceptedPositionMs',
        'producerPrepareMs','presentedPositionMs','stockUiProcessedAtMs')
PHASES={'source-load','seek-requested','client-operation-accepted',
        'owned-producer-prepared','renderer-first-frame-callback-unattributed'} | {
        'operation-'+name for name in ('load','seek','frame_step','playback_rate',
        'play','pause','flush','reconnect','inactive_file','surface_replacement','recovery','stop','free')}

def closed_trace(value):
    if not isinstance(value,dict) or value.get('schemaVersion')!=1 or not isinstance(value.get('active'),bool):
        raise ValueError('Unqualified experience schema')
    result={key:value.get(key) for key in FIELDS}
    for key,item in result.items():
        if key not in ('schemaVersion','active') and item is not None and (type(item) is not int or item<0):
            raise ValueError('Experience field must be nonnegative integer or unknown')
    history=value.get('events')
    if not isinstance(history,list) or len(history)>64:raise ValueError('Experience history exceeds contract')
    result['events']=[]
    for event in history:
        if not isinstance(event,dict) or event.get('phase') not in PHASES:
            raise ValueError('Closed experience phase required')
        row={'phase':event['phase']}
        for key in ('monotonicMs','operationSequence','positionMs'):
            item=event.get(key)
            if item is not None and (type(item) is not int or item<0):raise ValueError('Invalid event observation')
            row[key]=item
        result['events'].append(row)
    return result

def read_trace(expected_target=None):
    # Exact commissioned development device and gate-only opt-in port. No
    # arbitrary endpoint, cookie, credential, raw report or server control.
    end=time.monotonic()+6
    while True:
        with urlopen('http://192.168.10.25:33717/api/diagnostics',timeout=2) as reply:
            body=reply.read(512*1024+1)
        if len(body)>512*1024:raise ValueError('Diagnostic response exceeds bound')
        trace=closed_trace(json.loads(body).get('playbackExperience'))
        if expected_target is None or trace['requestedPositionMs']==expected_target:return trace
        if time.monotonic()>=end:raise AssertionError('Production diagnostic did not expose current seek')
        time.sleep(.5) # The production surface has an existing2s capture cache.

def require_seek(trace,target,source_generation,owned):
    if trace['sourceGeneration']!=source_generation:raise AssertionError('Seek changed source generation')
    if trace['requestedPositionMs']!=target or trace['clientAcceptedPositionMs']!=target:
        raise AssertionError('Request/local acceptance mismatch')
    if trace['seekSequence'] is None:raise AssertionError('Missing seek ownership')
    if owned and trace['producerAcceptedPositionMs']!=target:raise AssertionError('Owned producer acceptance mismatch')
    if not owned and trace['producerAcceptedPositionMs'] is not None:raise AssertionError('Native path fabricated producer acceptance')
    if trace['presentedPositionMs'] is not None or trace['stockUiProcessedAtMs'] is not None:
        raise AssertionError('Untagged backend callback must not fabricate exact landing/UI processing time')
    phases=[row['phase'] for row in trace['events']]
    if 'seek-requested' not in phases or 'client-operation-accepted' not in phases:
        raise AssertionError('Missing lifecycle phases')
