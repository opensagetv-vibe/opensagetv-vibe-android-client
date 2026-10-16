#!/usr/bin/env python3
"""Finite real-MCP connection gate; never launches playback or changes app settings.

Run in the commissioned Android Python/SDK container with explicit aliases.
Each iteration owns and closes its MCP child. The only device write is the
existing idempotent, verified adb_allowed_connection_time=0 bootstrap.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import time

from mcp_smoke_test import MCPProcess, initialize, tool_call


def structured(client, name, timeout=30):
    result = tool_call(client, name, timeout=timeout)
    value = result.get('structuredContent')
    if not isinstance(value, dict):
        for item in result.get('content', []):
            if item.get('type') == 'text':
                try: value = json.loads(item['text'])
                except (ValueError, KeyError): continue
                if isinstance(value, dict): break
    if not isinstance(value, dict): raise RuntimeError('missing structured reply: '+name)
    return value


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--iterations', type=int, default=5)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if not 1 <= args.iterations <= 10: parser.error('iterations must be 1..10')
    root = Path(__file__).resolve().parents[1]
    output = args.output.resolve()
    if not output.is_relative_to((root/'artifacts/active/ADB-003').resolve()):
        parser.error('output must be under artifacts/active/ADB-003')
    report = {'passed': False, 'transport': 'real MCP stdio', 'iterations': [],
              'scope': 'connection only; no app/server playback mutations'}
    try:
        for index in range(args.iterations):
            client = MCPProcess()
            started = time.monotonic()
            try:
                protocol, _ = initialize(client)
                # Cover connect30s + three15s setup calls + one15s idempotent
                # retry + shell handshake15s, with margin; never replay it.
                connected = structured(client, 'adb_connect', timeout=120)
                status = structured(client, 'adb_session_status')
                info = structured(client, 'firetv_device_info')
                if (connected.get('adbAuthorization', {}).get('currentValue') != '0'
                        or connected.get('adbAuthorization', {}).get('nonExpiring') is not True
                        or not status.get('persistentShell')
                        or status.get('persistentShellPid') != connected.get('persistentShellPid')
                        or status.get('target') != connected.get('target')):
                    raise RuntimeError('connection authorization/shell ownership witness failed')
                report['iterations'].append({'iteration': index+1, 'protocol': protocol,
                    'elapsedSeconds': round(time.monotonic()-started, 3),
                    'target': connected['target'], 'authorization': connected['adbAuthorization'],
                    'device': info, 'persistentShellReused': True})
                print('MCP_CONNECTION_PASS '+str(index+1), flush=True)
            finally:
                client.close()
        report['passed'] = True
    except Exception as exc:
        report['error'] = str(exc)
    finally:
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(json.dumps(report, indent=2)+'\n', encoding='utf-8')
    print(json.dumps({'passed': report['passed'], 'completed': len(report['iterations']),
                      'output': str(output), 'error': report.get('error')}))
    return 0 if report['passed'] else 1


if __name__ == '__main__': raise SystemExit(main())
