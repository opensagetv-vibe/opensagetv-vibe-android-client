#!/usr/bin/env python3
"""Bounded read-only real-MCP log capture to an explicitly owned task path."""
import argparse, json
from pathlib import Path
from mcp_smoke_test import MCPProcess, initialize, tool_call

parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--output',required=True,type=Path)
parser.add_argument('--pattern',default='ExoPlayer|Media3|ParserException|UnrecognizedInput|PullDataSource|MediaServer')
parser.add_argument('--state-only',action='store_true',help='Read client/config/diagnostic status only; no UI-scope mutation')
args=parser.parse_args()
root=Path(__file__).resolve().parents[1]
output=args.output.resolve()
if not output.is_relative_to((root/'artifacts/active').resolve()) or output.exists():
    raise ValueError('Use a new explicit active-task output')
client=MCPProcess()
try:
    initialize(client)
    connection=tool_call(client,'adb_connect',{},timeout=75)
    if args.state_only:
        result={'player':tool_call(client,'dev_player_state',{},timeout=35),
                'diagnostics':tool_call(client,'dev_server_diagnostics',{},timeout=35)}
    else:
        result=tool_call(client,'get_logcat',{'lines':3000,'pattern':args.pattern},timeout=45)
    output.parent.mkdir(parents=True,exist_ok=True)
    output.write_text(json.dumps({'connection':connection,'logcat':result},indent=2)+'\n')
    print(json.dumps({'output':str(output),'readOnly':True}))
finally:client.close()
