#!/usr/bin/env python3
"""EXT-014: focused native DVD parity; keep accepted approximate seek and user mappings."""
import argparse
from contextlib import redirect_stdout, redirect_stderr
import json
import os
from pathlib import Path
import sys

from android_test_keep_awake import begin, end, state_path
from mcp_seek_suite import MCPProcess, initialize, call_dict
from mcp_lifecycle_test import require
from mcp_disc_test import main as disc_main
from mcp_dvd_menu_cycle_test import main as menu_main
from sagetv_dev_mcp.config import load_test_environment

ROOT = Path(__file__).resolve().parents[1]


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--case', choices=('aladdin', 'authored'), required=True)
    p.add_argument('--output', type=Path, required=True)
    args = p.parse_args()
    out = args.output.resolve()
    require(out.is_relative_to((ROOT/'artifacts/active/EXT-014').resolve())
            and not out.exists(), 'New explicit EXT-014 output required')
    env = load_test_environment()
    require(env.device_serial() == '192.168.10.25:5555'
            and env.server_address() == '192.168.10.175', 'Non-Pro/stock175 only')
    fixture_id = 'dvd_motion' if args.case == 'aladdin' else 'authored_dvd'
    fixture = next((f for f in env.fixture_cases() if f['id'] == fixture_id), None)
    require(fixture and fixture.get('enabled'), 'Enabled exact commissioned DVD required')
    os.environ['SAGETV_ARTIFACT_DIR'] = str(out.parent)
    out.parent.mkdir(parents=True, exist_ok=True)
    log = out.with_suffix('.log')
    disc_output = out.with_name(out.stem+'-disc.json')
    menu_output = out.with_name(out.stem+'-menus.json')
    ready = out.with_suffix('.ready.json')
    require(not any(f.exists() for f in (log, disc_output, menu_output, ready)),
            'Never overwrite an earlier DVD gate')
    r = {'passed': False, 'case': args.case, 'fixture': fixture['path'],
         'serverChanges': False, 'keyMappingChanges': False, 'tunerControls': False,
         'log': str(log)}
    c = MCPProcess()
    awake = child_started = False

    def component(entry, arguments, stream):
        original = sys.argv
        try:
            sys.argv = ['ext014-component']+arguments
            with redirect_stdout(stream), redirect_stderr(stream):
                return entry()
        finally:
            sys.argv = original

    try:
        initialize(c)
        r['adb'] = call_dict(c, 'adb_connect')
        r['before'] = call_dict(c, 'dev_player_state')
        require(not r['before'].get('connected')
                and r['before'].get('clientId') == env.device()['client_id'],
                'Requires exact idle disconnected device')
        r['awake'] = begin(env.device_serial(), state_path(env.device_serial()), 'automated')
        awake = True
        command = ['--server-path', fixture['path'], '--player', 'media3',
                   '--disc-policy', 'native', '--streaming', 'dynamic',
                   '--mim-direct-mode', 'off', '--timeout-s', '90',
                   '--output', str(disc_output), '--screenshot',
                   '--verify-command-recovery', '--visual-title-hold-s', '25',
                   '--capture-ready-file', str(ready)]
        if args.case == 'aladdin':
            command += ['--skip-menus', '--start-ms', '540000', '--seek-tolerance-ms', '12000',
                        '--cadence-observe-s', '10', '--command', 'pause', '--command', 'play']
        else:
            command += ['--command', 'select', '--command', 'dvd_subtitle_toggle',
                        '--command', 'pause', '--command', 'play', '--leave-playing']
        child_started = True
        with log.open('w', buffering=1) as stream:
            code = component(disc_main, command, stream)
            r['discExitCode'] = code
            require(code == 0 and disc_output.exists(), 'Native DVD gate failed; inspect retained log')
            r['disc'] = json.loads(disc_output.read_text())
            require(r['disc'].get('passed') and r['disc']['settingsRestore'].get('restored'),
                    'Actual disc and preference restoration must pass')
            if args.case == 'authored':
                menu_code = component(menu_main, ['--output', str(menu_output),
                    '--cycles', '1', '--capture', '--command-input', 'firetv'], stream)
                r['menuExitCode'] = menu_code
                require(menu_code == 0, 'Authored root/language/title transitions failed')
                r['menus'] = json.loads(menu_output.read_text())
        r['passed'] = True
    except Exception as exc:
        r['passed'] = False
        r['failure'] = str(exc)
    finally:
        # Disc component owns preference checkpoint/restore in its finally.
        # Wrapper owns power and the final exact-device exit, including failures.
        for name, action in (
            ('exit', lambda: call_dict(c, 'dev_exit_session', {'stop_app': True}, timeout=45)
             if child_started else None),
            ('powerRestore', lambda: end(env.device_serial(), state_path(env.device_serial()), 'automated')
             if awake else None)):
            try: r[name] = action()
            except Exception as exc:
                r['passed'] = False
                r[name+'Error'] = str(exc)
        out.write_text(json.dumps(r, indent=2)+'\n')
        c.close()
    print(json.dumps({'passed': r['passed'], 'failure': r.get('failure'), 'output': str(out)}))
    return 0 if r['passed'] else 1


if __name__ == '__main__': raise SystemExit(main())
