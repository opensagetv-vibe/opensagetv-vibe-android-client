#!/usr/bin/env python3
"""Physical startup-menu check through the commissioned SDK/client debug API.

This is external client tooling, not proof of the MCP stdio transport. No
server mutation, private protocol event, app-data clearing or Core patch.
"""
import argparse
import json
from pathlib import Path
import re
import time
import uuid
import xml.etree.ElementTree as ET
from sagetv_dev_mcp.adb import AdbClient
from sagetv_dev_mcp.config import configured_device_serial


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output',required=True,type=Path)
    parser.add_argument('--live-menu-only',action='store_true',help='Inspect live choices/back without altering session overrides')
    args=parser.parse_args()
    root=Path(__file__).resolve().parents[1]
    output=args.output.resolve()
    if not output.is_relative_to(root/'artifacts/active/INTERLACE-008'):
        parser.error('output must be under artifacts/active/INTERLACE-008')
    output.parent.mkdir(parents=True,exist_ok=True)
    adb=AdbClient(configured_device_serial(),'opensagetv.vibe.miniclient.debug')
    result={'passed':False,'serial':adb.serial,'harness':'direct SDK client API; no server controls'}
    checkpoint=False
    def hierarchy():
        # This vendor UIAutomator writes via a separate process, so /dev/tty
        # and /dev/stdout report success without delivering their XML. Use one
        # uniquely owned tiny dump, read it, and remove only that exact file.
        remote='/data/local/tmp/vibe-client-gpu-ui-'+uuid.uuid4().hex+'.xml'
        try:
            adb.shell('uiautomator dump '+remote,timeout=30)
            text=adb.shell('cat '+remote)
            return ET.fromstring(text[text.index('<hierarchy'):text.rindex('</hierarchy>')+12])
        finally:adb.shell('rm -f '+remote,check=False)
    def tap(text):
        tree=hierarchy()
        node=next(item for item in tree.iter('node') if item.get('text')==text)
        x1,y1,x2,y2=map(int,re.findall(r'\d+',node.get('bounds')))
        adb.shell(f'input tap {(x1+x2)//2} {(y1+y2)//2}')
        time.sleep(.6)
    def labels():return [node.get('text') for node in hierarchy().iter('node') if node.get('text')]
    def tap_id(name):
        node=next(item for item in hierarchy().iter('node') if item.get('resource-id','').endswith(':id/'+name))
        x1,y1,x2,y2=map(int,re.findall(r'\d+',node.get('bounds')))
        adb.shell(f'input tap {(x1+x2)//2} {(y1+y2)//2}');time.sleep(.6)
    try:
        result['connect']=adb.connect()
        result['model']=adb.shell('getprop ro.product.model').strip()
        if adb.serial!='192.168.10.25:5555' or result['model']!='AFTMM':
            raise RuntimeError('wrong commissioned device; no UI mutation')
        saved=adb.checkpoint_settings()
        if not saved.get('captured'):raise RuntimeError('preference checkpoint failed')
        checkpoint=True
        if args.live_menu_only:
            state=adb.player_state_snapshot()
            if not state.get('playerActive') or state.get('health_errorState'):
                raise RuntimeError('live menu needs healthy current playback')
            result['openLocalMenu']=adb.sage_command('NAV_OSD')
            time.sleep(.6)
            tap_id('nav_active_player_adjustments')
            tap('Deinterlace')
            choices=labels();result['liveChoices']=choices
            if 'Client GPU' not in choices:raise RuntimeError('live GPU choice missing')
            if ('MIM' in choices)!=(state.get('streaming')=='fixed'):
                raise RuntimeError('live MIM visibility does not match Fixed')
            adb.screenshot(output.with_suffix('.live.png'))
            adb.key('BACK');time.sleep(.6)
            result['parentLabels']=labels()
            if 'Deinterlace' not in result['parentLabels']:raise RuntimeError('Back lost video parent')
            adb.key('BACK')
            result['passed']=True
            return 0
        result['open']=adb.shell('run-as opensagetv.vibe.miniclient.debug am start --user 0 -n '
            'opensagetv.vibe.miniclient.debug/opensagetv.vibe.miniclient.android.ui.settings.SettingsActivity')
        time.sleep(1)
        tap('Playback Data Source');tap('Pull')
        tap('Deinterlace')
        choices=labels();result['pullChoices']=choices
        if 'Client GPU' not in choices or 'MIM' in choices:raise RuntimeError('Pull choices incorrect')
        adb.screenshot(output.with_suffix('.pull.png'))
        tap('Client GPU')
        state=adb.player_state_snapshot()
        result['gpuPersisted']=state.get('videoDeinterlaceMode')
        if result['gpuPersisted']!='client_gpu':raise RuntimeError('GPU choice did not persist')
        if 'Deinterlace' not in labels():raise RuntimeError('selection lost parent settings')
        tap('Playback Data Source');tap('Fixed')
        tap('Deinterlace')
        choices=labels();result['fixedChoices']=choices
        if 'Client GPU' not in choices or 'MIM' not in choices:raise RuntimeError('Fixed choices incorrect')
        adb.screenshot(output.with_suffix('.fixed.png'))
        adb.key('BACK')
        result['passed']=True
    except Exception as exc:result['error']=str(exc)
    finally:
        if checkpoint:
            try:
                result['restore']=adb.restore_settings()
                if not result['restore'].get('restored'):raise RuntimeError('preferences restore failed')
            except Exception as exc:result['passed']=False;result['restoreError']=str(exc)
        adb.close()
        output.write_text(json.dumps(result,indent=2),encoding='utf-8')
    print(json.dumps({'passed':result['passed'],'error':result.get('error'),'output':str(output)}),flush=True)
    return 0 if result['passed'] else 1


if __name__=='__main__':raise SystemExit(main())
