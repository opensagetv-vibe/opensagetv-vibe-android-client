#!/usr/bin/env python3
"""Bounded stock UI commissioning helper; never calls InstallPlugin privately.

Each mutation is one reviewed remote key in the exact Non-Pro/232 UI. The
operator inspects screenshots between decisions; no key sequence is replayed.
"""
import argparse,json,sys,time
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'mcp/src'))
from sagetv_dev_mcp.adb import AdbClient
from sagetv_dev_mcp.config import load_test_environment
from sagetv_dev_mcp.core_mcp_api import CoreMcpApiClient
from mcp_seek_suite import MCPProcess,call_dict,initialize
from mcp_ui_roots import wait_automation_root

parser=argparse.ArgumentParser()
parser.add_argument('action',choices=['connect','key','screenshot','exit'])
parser.add_argument('--key',choices=['Up','Down','Left','Right','Select','Back','Home','Page Up','Page Down'])
parser.add_argument('--task-id',choices=['SETTINGS-005','HLS-006'],default='SETTINGS-005')
args=parser.parse_args()
env=load_test_environment();serial=env.device_serial();server=env.server()['address']
if serial!=env.device_serial('non_pro') or server!=env.server_address('vibe'):raise SystemExit('Exact configured Non-Pro/stock232 required')
api=CoreMcpApiClient.discover(server)
client=None;adb=AdbClient(serial,'opensagetv.vibe.miniclient.debug')
try:
    if args.action=='connect':
        if api._request('server.activity').get('safeToRestart') is not True:raise RuntimeError('Independent idle required')
        client=MCPProcess();initialize(client)
        call_dict(client,'adb_connect',timeout=30)
        call_dict(client,'dev_prepare_clean_start',{'wake':True,'graceful_timeout_s':2.0},timeout=30)
        call_dict(client,'dev_connect_server',{'address':server,'port':31099,'save':False},timeout=30)
        wait_automation_root(client,timeout_s=30,stable_ms=700)
        witness=call_dict(client,'dev_player_state')
        if witness.get('serverAddress')!=server or witness.get('clientId')!=env.device()['client_id']:raise RuntimeError('Wrong connected UI')
    context=api.resolve_context(env.device()['client_id'])
    if context!='444556303031':raise RuntimeError('Wrong commissioned public context')
    if args.action=='key':
        if not args.key:raise RuntimeError('One reviewed key required')
        reply=api.remote_command(context,args.key)
        if not reply.get('ok'):raise RuntimeError('Remote command refused')
        time.sleep(1)
    elif args.action=='exit':
        client=MCPProcess();initialize(client)
        call_dict(client,'dev_exit_session',{'stop_app':True},timeout=30)
    if args.action!='exit':
        out=ROOT/'artifacts/active'/args.task_id;out.mkdir(parents=True,exist_ok=True)
        filename='plugin-ui-'+str(time.time_ns())+'.png';adb.screenshot(out/filename)
        print(json.dumps({'context':context,'screenshot':str(out/filename),'state':api.ui_state(context)}))
finally:
    adb.close()
    if client:client.close()
