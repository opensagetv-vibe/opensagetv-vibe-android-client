#!/usr/bin/env python3
"""EXT-012: bracket the existing physical caption gate with exact-device restoration."""
import argparse
from contextlib import redirect_stdout, redirect_stderr
import json
import os
from pathlib import Path
import sys

from android_test_keep_awake import begin, end, state_path
from mcp_seek_suite import MCPProcess, initialize, call_dict
from mcp_lifecycle_test import require
from mcp_caption_test import main as caption_main
from sagetv_dev_mcp.config import load_test_environment

ROOT = Path(__file__).resolve().parents[1]


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--case', choices=('cea', 'teletext', 'dvb'), required=True)
    p.add_argument('--player', choices=('media3', 'exoplayer', 'ijkplayer'), default='media3')
    p.add_argument('--owner-task', choices=('EXT-010', 'EXT-012', 'EXT-013'), default='EXT-012')
    p.add_argument('--forward-cache-trial', action='store_true')
    p.add_argument('--production-cache', action='store_true')
    p.add_argument('--exercise-cache-audio-tracks', action='store_true')
    p.add_argument('--decoding', choices=('hardware', 'software'), default='hardware')
    p.add_argument('--install-apk', default='')
    p.add_argument('--streaming', choices=('pull', 'dynamic', 'fixed'), default='pull')
    p.add_argument('--expect-no-callback', action='store_true')
    p.add_argument('--output', type=Path, required=True)
    args = p.parse_args()
    queue_gate = args.forward_cache_trial or args.production_cache
    require(not (args.forward_cache_trial and args.production_cache),
            'Default source must not enable a debug trial override')
    require(not args.exercise_cache_audio_tracks or queue_gate,
            'Cache tracks require actual queue trial')
    require(not queue_gate or (args.owner_task == 'EXT-010'
            and args.player in ('media3', 'exoplayer') and args.streaming == 'dynamic'),
            'Caption queue trial requires EXT-010 Media3 ordinary Push')
    out = args.output.resolve()
    require(out.is_relative_to((ROOT/'artifacts/active'/args.owner_task).resolve())
            and not out.exists(), 'New explicit owning-task output required')
    require(args.player != 'ijkplayer' or args.owner_task == 'EXT-013',
            'Native IJK qualification belongs to EXT-013')
    require(args.streaming == 'pull' or args.owner_task in ('EXT-010', 'EXT-013'),
            'Other caption transports require their own explicit owning task')
    require(not args.expect_no_callback or (args.owner_task == 'EXT-013'
            and args.player == 'ijkplayer' and args.case == 'cea'),
            'Negative IJK capability gate requires the actual CEA fixture')
    env = load_test_environment()
    require(env.device_serial() == '192.168.10.25:5555'
            and env.server_address() == '192.168.10.175', 'Non-Pro/stock175 only')
    cases = {f['id']: f for f in env.fixture_cases()}
    fixture_id = {'cea': 'seek_caption',
                  'teletext': 'uk_breakfast', 'dvb': 'uk_taskmaster'}[args.case]
    require(fixture_id in cases and cases[fixture_id].get('enabled', True),
            'Exact commissioned fixture required')
    os.environ['SAGETV_ARTIFACT_DIR'] = str(out.parent)
    out.parent.mkdir(parents=True, exist_ok=True)
    log = out.with_suffix('.log')
    require(not log.exists(), 'Never overwrite an earlier physical run')
    r = {'passed': False, 'case': args.case, 'fixture': cases[fixture_id]['path'],
         'player': args.player, 'serial': env.device_serial(),
         'server': env.server_address(), 'log': str(log),
         'forwardCacheTrial': args.forward_cache_trial, 'productionCache': args.production_cache,
         'serverChanges': False, 'tunerControls': False}
    c = MCPProcess()
    checkpoint = awake = child_started = False
    try:
        initialize(c)
        r['adb'] = call_dict(c, 'adb_connect')
        r['before'] = call_dict(c, 'dev_player_state')
        require(not r['before'].get('connected')
                and r['before'].get('clientId') == env.device()['client_id'],
                'Requires exact idle disconnected device')
        r['checkpoint'] = call_dict(c, 'dev_checkpoint_settings')
        require(r['checkpoint'].get('captured'), 'Settings checkpoint failed')
        checkpoint = True
        r['awake'] = begin(env.device_serial(), state_path(env.device_serial()), 'automated')
        awake = True
        if args.install_apk:
            r['install'] = call_dict(c, 'install_dev_apk', {'apk_path': args.install_apk}, timeout=200)
            require('Success' in str(r['install'].get('result', '')), 'Guarded APK install failed')
        command = [str(ROOT/'scripts/mcp_caption_test.py'),
                   '--server-path', r['fixture'], '--player', args.player,
                   '--streaming', args.streaming, '--decoding', args.decoding, '--authority', 'stv',
                   '--mim-direct-mode', 'off',
                   '--legacy-server-caption-mode', 'dvb' if args.case == 'dvb' else 'stv',
                   '--seek-command', 'ff', '--pause-resume', '--hold-s', '3']
        if args.forward_cache_trial:
            command += ['--forward-cache-trial']
        if args.production_cache:
            command += ['--production-cache']
        if args.exercise_cache_audio_tracks:
            command += ['--exercise-cache-audio-tracks']
        if args.expect_no_callback:
            command += ['--expect-no-legacy-callback']
        elif args.case == 'dvb':
            command += ['--track-codec', 'DVB', '--toggle-off-on']
        else:
            command += ['--legacy-extender-callback', '--cycle-stv-caption-states']
        child_started = True
        original_argv = sys.argv
        with log.open('w', buffering=1) as stream:
            # Same-process finally guarantees its server-CC restoration also
            # runs on interruption; do not timeout-kill a child before cleanup.
            try:
                sys.argv = command
                with redirect_stdout(stream), redirect_stderr(stream):
                    result = caption_main()
            finally:
                sys.argv = original_argv
        r['exitCode'] = result
        require(result == 0, 'Caption child failed; inspect retained log')
        r['passed'] = True
    except Exception as exc:
        r['passed'] = False
        r['failure'] = str(exc)
    finally:
        # The child owns stock CC checkpoint/restore. Parent owns preferences
        # and power even if that child fails or times out before its teardown.
        for name, action in (
            ('exit', lambda: call_dict(c, 'dev_exit_session', {'stop_app': True}, timeout=45)
             if child_started else None),
            ('restore', lambda: call_dict(c, 'dev_restore_settings', timeout=100)
             if checkpoint else None),
            ('powerRestore', lambda: end(env.device_serial(), state_path(env.device_serial()), 'automated')
             if awake else None)):
            try:
                r[name] = action()
                if name == 'restore' and checkpoint:
                    require(r[name].get('restored'), 'Settings restoration failed')
            except Exception as exc:
                r['passed'] = False
                r[name+'Error'] = str(exc)
        out.write_text(json.dumps(r, indent=2)+'\n')
        c.close()
    print(json.dumps({'passed': r['passed'], 'failure': r.get('failure'), 'output': str(out)}))
    return 0 if r['passed'] else 1


if __name__ == '__main__':
    raise SystemExit(main())
