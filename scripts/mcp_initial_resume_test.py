#!/usr/bin/env python3
"""Affected-only initial bookmark gate using real MCP and public SageTV controls.

Use a commissioned test fixture, never a user's broadcast recording: this gate
plays/seeks/stops it to establish normal server-owned watched history. It does
not clear watched state or replace a server bookmark through a private command.
"""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import subprocess
import sys
import time

from mcp_lifecycle_test import clear_restored_playback, require, wait_automation_ready
from mcp_seek_suite import MCPProcess, call_dict, initialize
from sagetv_dev_mcp.config import load_test_environment
from sagetv_dev_mcp.core_mcp_api import discover_sage_control

ROOT = Path(__file__).resolve().parents[1]


def child_command(device: str, server: str) -> list[str] | None:
    if os.name != 'nt':
        return None
    command = ['docker', 'exec', '-i', '-w', '/workspace/android-client']
    for key, value in {
            'SAGETV_WORKSPACE': '/workspace/android-client',
            'SAGETV_MCP_CONFIG': '/workspace/android-client/config/firetv.toml',
            'SAGETV_TEST_DEVICE_ALIAS': device, 'SAGETV_TEST_SERVER_ALIAS': server,
            'PYTHONPATH': '/workspace/android-client/mcp/src',
            'SAGETV_ARTIFACT_DIR': '/workspace/android-client/artifacts/active/RESUME-001',
            'ANDROID_USER_HOME': '/workspace/android-client/adb',
            'ADB_VENDOR_KEYS': '/workspace/android-client/adb'}.items():
        command += ['-e', key + '=' + value]
    return command + ['opensagetv-vibe-dev',
                      '/opt/opensagetv-vibe/android-python/bin/python3',
                      '-m', 'sagetv_dev_mcp.server']


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--device', default='firetv-non-pro')
    parser.add_argument('--server', default='vibe-test')
    parser.add_argument('--player', choices=('media3', 'exoplayer', 'ijkplayer', 'gsyplayer'),
                        default='media3')
    parser.add_argument('--gsy-engine', choices=('auto', 'media3', 'legacy_exo', 'system'),
                        default='auto')
    parser.add_argument('--fixture', default='resume_avc_aac_ts')
    parser.add_argument('--bookmark-ms', type=int, default=30000)
    parser.add_argument('--hold-seconds', type=int, default=0)
    parser.add_argument('--capture-hdmi', action='store_true')
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    output = args.output.resolve()
    require(output.is_relative_to((ROOT/'artifacts/active/RESUME-001').resolve())
            and not output.exists(), 'Use a new explicit RESUME-001 output')
    require(5000 <= args.bookmark_ms <= 600000, 'Bookmark must be5..600 seconds')
    require(0 <= args.hold_seconds <= 60, 'Capture hold must be0..60 seconds')
    require(not args.capture_hdmi or args.hold_seconds >= 20,
            'HDMI requires at least20 seconds live hold before device mutation')
    os.environ['SAGETV_TEST_DEVICE_ALIAS'] = args.device
    os.environ['SAGETV_TEST_SERVER_ALIAS'] = args.server
    os.environ['SAGETV_ARTIFACT_DIR'] = str(ROOT/'artifacts/active/RESUME-001')
    if os.name == 'nt':
        os.environ['SAGETV_MCP_CONFIG'] = str(ROOT/'config/firetv.toml')
    environment = load_test_environment()
    fixtures = [f for f in environment.fixture_cases(enabled_only=True)
                if f.get('id') == args.fixture]
    require(len(fixtures) == 1, 'Require exactly one enabled commissioned fixture')
    path = str(fixtures[0]['path'])
    # These generated/derived controls are separately commissioned, not real
    # recording-library entries. Refuse arbitrary path/title fallback.
    require('/OpenSageTV_Vibe_Tests/' in path.replace('\\', '/'),
            'Resume seeding is restricted to the commissioned test-fixture tree')
    output.parent.mkdir(parents=True, exist_ok=True)
    report = {'status': 'STARTING', 'player': args.player, 'gsyEngine': args.gsy_engine,
              'fixture': args.fixture, 'server': environment.server_address(),
              'serial': environment.device_serial(), 'bookmarkMs': args.bookmark_ms,
              'serverCoreChanges': False, 'realRecordingWatchHistoryChanged': False}
    client = MCPProcess(command=child_command(args.device, args.server))
    checkpoint = False
    control = None
    context = ''
    cc = None

    def save():
        output.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')

    def step(name):
        report['phase'] = name
        save()
        print('STEP ' + name, flush=True)

    def state():
        return call_dict(client, 'dev_player_state', timeout=35)

    def healthy():
        result = call_dict(client, 'dev_wait_for_playback_started',
                           {'timeout_s': 55, 'verify_ms': 1500,
                            'expect_video': True, 'expect_audio': True}, timeout=80)
        require(bool(result.get('passed')), 'Advancing A/V required: ' + str(result))
        return result

    try:
        step('MCP/ADB readiness')
        initialize(client)
        report['adb'] = call_dict(client, 'adb_connect', timeout=75)
        report['device'] = call_dict(client, 'firetv_device_info')
        require(str(report['device'].get('ro.product.model'))
                == str(environment.device().get('model')), 'Wrong physical target')
        report['checkpoint'] = call_dict(client, 'dev_checkpoint_settings')
        require(bool(report['checkpoint'].get('captured')), 'Settings checkpoint failed')
        checkpoint = True
        step('Configure independent backend and connect selected context')
        clean = call_dict(client, 'dev_prepare_clean_start',
                          {'wake': True, 'graceful_timeout_s': 2.0})
        require(bool(clean.get('readyToLaunch')), 'Could not cleanly prepare Dev')
        report['config'] = call_dict(client, 'dev_set_player_config', {
            'player': args.player, 'streaming': 'pull', 'decoding': 'hardware',
            'gsy_engine': args.gsy_engine, 'gsy_system_probe': args.gsy_engine == 'system',
            'mim_direct_mode': 'off', 'video_deinterlace_mode': 'off'})
        call_dict(client, 'dev_connect_server',
                  {'address': environment.server_address(), 'save': False})
        wait_automation_ready(client, timeout_s=60)
        clear_restored_playback(client)
        identity = state()
        control = discover_sage_control(environment.server_address())
        context = control.resolve_context(str(identity['clientId']))
        report['scope'] = {'serial': environment.device_serial(),
                           'clientId': identity['clientId'], 'context': context}
        cc = control.closed_caption_state(context)
        report['resolved'] = control.resolve_exact_path(path)
        step('Initial normal Watch')
        started = call_dict(client, 'dev_play_server_path', {
            'server_path': path, 'timeout_s': 55, 'verify_ms': 1500,
            'restart_from_beginning': False}, timeout=180)
        report['initialStart'] = started
        require(bool(started.get('passed')), 'Initial fixture playback failed')
        report['initialState'] = state()
        require(report['initialState'].get('health_pushMode') is False,
                'Initial-position gate requires actual Pull, not a server Push fallback')
        step('Public server seek seeds test-fixture bookmark')
        report['seedSeek'] = call_dict(client, 'dev_server_seek_time', {
            'target_ms': args.bookmark_ms, 'tolerance_ms': 2500,
            'timeout_s': 45, 'max_attempts': 1}, timeout=75)
        require(bool(report['seedSeek'].get('passed')), 'Server bookmark positioning failed')
        healthy()
        report['beforeStop'] = state()
        step('Stop and normal reopen; no corrective post-start seek')
        call_dict(client, 'dev_server_media_control', {'operation': 'stop'})
        time.sleep(1.0)
        # Leave the stop popup before the next normal Watch. No fromBeginning
        # or post-start local seek may manufacture a successful resume.
        call_dict(client, 'dev_sage_command', {'command': 'home'})
        time.sleep(1.0)
        resumed = call_dict(client, 'dev_play_server_path', {
            'server_path': path, 'timeout_s': 55, 'verify_ms': 1500,
            'restart_from_beginning': False}, timeout=180)
        report['resumeStart'] = resumed
        require(bool(resumed.get('passed')), 'Server-owned resume failed')
        report['resumeHealth'] = healthy()
        current = state()
        report['resumed'] = current
        require(current.get('health_pushMode') is False,
                'Resume changed transport; cannot qualify Pull initial-position handling')
        target = int(current.get('serverRequestedSeekMs', -1))
        position = int(current.get('mediaTimeMs', -1))
        require(target >= args.bookmark_ms - 5000,
                f'Server did not request a nonzero resume: target={target}')
        require(position >= args.bookmark_ms - 5000,
                f'Backend restarted at zero: position={position}, target={target}')
        # This snapshot follows asynchronous prepare plus two A/V observations.
        # A slow native fallback can legitimately have played for >20 seconds
        # since SEEK. Bound progress by the recorded command age, not an
        # arbitrary wall-clock-independent distance from the original bookmark.
        seek_age_ms = int(current.get('serverSeekAgeMs', -1))
        require(seek_age_ms >= 0, 'Missing initial server-seek timing witness')
        require(-5000 <= position-target <= seek_age_ms+5000,
                f'Resume outside elapsed-time bounds: {position} vs {target}, age={seek_age_ms}')
        report['resumePositionWitness'] = {'targetMs':target, 'positionMs':position,
                                          'serverSeekAgeMs':seek_age_ms}
        report['verdict'] = 'PASS'
        if args.gsy_engine == 'system' and current.get('gsyResolvedEngine') != 'system':
            report['verdict'] = 'PASS_FALLBACK_NOT_NATIVE_SYSTEM'
        marker = {
            'scopeVerified': True, 'serial': environment.device_serial(),
            'clientId': identity['clientId'], 'context': context,
            'expiresAtMs': int((time.time()+args.hold_seconds)*1000)}
        output.with_suffix('.ready.json').write_text(json.dumps(marker)+'\n')
        print('LIVE_CAPTURE_STAGE ' + json.dumps(marker), flush=True)
        save()
        if args.capture_hdmi:
            require(args.hold_seconds >= 20, 'HDMI requires at least20 seconds live hold')
            capture = output.with_suffix('.mp4')
            subprocess.run([sys.executable, str(ROOT/'scripts/capture_hdmi_validation.py'),
                            '--output', str(capture), '--duration', '6',
                            '--ready-file', str(output.with_suffix('.ready.json'))],
                           check=True, timeout=50)
            report['hdmiCapture'] = str(capture.relative_to(ROOT))
            report['visualStatus'] = 'PENDING_INDEPENDENT_REVIEW'
        if args.hold_seconds:
            time.sleep(args.hold_seconds)
        report['status'] = report['verdict']
    except Exception as exc:
        report['status'] = 'FAIL'
        report['error'] = str(exc)
        try: report['failureState'] = state()
        except Exception as state_error: report['stateError'] = str(state_error)
    finally:
        try:
            if control and context and cc is not None:
                report['captionRestore'] = control.set_closed_caption_state(context, cc)
        except Exception as exc:
            report['status'] = 'FAIL'; report['captionRestoreError'] = str(exc)
        try:
            call_dict(client, 'dev_exit_session', {'stop_app': True}, timeout=45)
            if checkpoint:
                report['settingsRestore'] = call_dict(client, 'dev_restore_settings', timeout=100)
                require(bool(report['settingsRestore'].get('restored')), 'Settings restore failed')
        except Exception as exc:
            report['status'] = 'FAIL'; report['restoreError'] = str(exc)
        save()
        client.close()
    print(json.dumps({'status': report['status'], 'output': str(output),
                      'error': report.get('error')}, indent=2))
    return 0 if report['status'].startswith('PASS') else 1


if __name__ == '__main__':
    raise SystemExit(main())
