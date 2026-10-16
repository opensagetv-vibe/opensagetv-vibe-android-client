#!/usr/bin/env python3
"""Stock Linux/Windows upload-only acceptance through real container MCP and SSH.

No Sage.jar/FFmpeg/config edits, service restarts or extension viewer routes.
The receiver's storage is read through the commissioned SSH account, never
made publicly downloadable. Restore settings independently in every finally.
Run on native Windows after the normal guarded APK/receiver installation.
Linux storage uses commissioned Unraid SSH, without any container/Core update.
"""
import argparse
import hashlib
import io
import json
import re
import stat
import time
import tomllib
import zipfile
from pathlib import Path
from urllib.parse import urlencode
from urllib.request import Request, urlopen
import paramiko
from mcp_seek_suite import MCPProcess, call_dict, initialize
from mcp_smoke_test import tool_call

ROOT = Path(__file__).resolve().parents[1]
STOCK_CORE = 'd76ded981b9bc51e25b9cec821b6abeb771b46c2996dc45e453349b5e703fcb0'
UPLOAD_ONLY = '5a623266c80be25f8fe4839d1ca38fe2652ac7de72b9dad3f3e41561c1df8698'
NAME = re.compile(r'[0-9a-f]{12}-[0-9]{13}-[0-9a-f]{32}\.zip\Z')


def mapped_fixture(data, selected, path):
    """Use generic configured paths/share mappings, never a hard-coded case ID."""
    normalized = path.replace('\\', '/').rstrip('/')
    urls = set()
    for server in data['servers'].values():
        for mapping in server.get('smb_mappings', []):
            prefix = mapping['sage_prefix'].replace('\\', '/').rstrip('/')
            if normalized.startswith(prefix + '/'):
                urls.add(mapping['smb_root'].rstrip('/') + '/' + normalized[len(prefix)+1:])
    if len(urls) != 1:
        raise ValueError('Fixture must have one commissioned share mapping')
    url = urls.pop()
    paths = set()
    for mapping in selected.get('smb_mappings', []):
        prefix = mapping['smb_root'].rstrip('/')
        if url.startswith(prefix + '/'):
            paths.add(mapping['sage_prefix'].replace('\\', '/').rstrip('/') + '/' + url[len(prefix)+1:])
    if len(paths) != 1:
        raise ValueError('Selected Windows server must map the exact fixture')
    result=paths.pop()
    return result.replace('/', '\\') if selected.get('windows_sagetv_path') else result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--server', default='windows_fixed')
    parser.add_argument('--device-alias', default='firetv-non-pro')
    parser.add_argument('--fixture-path', default='', help='Enabled startup fixture generic path; no hard-coded fixture ID')
    parser.add_argument('--reconnect-only', action='store_true', help='Qualify only the remaining idle reconnect/exit sub-gate')
    parser.add_argument('--exit-only', action='store_true', help='Qualify only the enabled clean-exit/no-recurring-upload sub-gate')
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    output = args.output.resolve()
    active = (ROOT/'artifacts/active/DIAG-002').resolve()
    if not output.is_relative_to(active) or output.exists():
        raise ValueError('Use a new explicit artifacts/active/DIAG-002 output')
    output.parent.mkdir(parents=True, exist_ok=True)
    data = tomllib.loads((ROOT/'config/firetv.toml').read_text())
    selected = data['servers'][args.server]
    assert selected['address'] in ('192.168.10.185', '192.168.10.175') and selected.get('stock') is True
    windows=selected['address']=='192.168.10.185'
    ssh_config=selected if windows else data['servers']['vibe']
    ssh_host=selected['address'] if windows else ssh_config['unraid_host']
    assert ssh_host in ('192.168.10.185','192.168.10.175')
    cases = [case for case in data['fixtures']['cases'] if case.get('enabled')
             and case.get('modes', {}).get('startup')
             and (case.get('path') == args.fixture_path if args.fixture_path else case.get('modes', {}).get('cea608'))]
    if len(cases) != 1:
        raise ValueError('Select one enabled startup fixture in the registry')
    fixture = mapped_fixture(data, selected, cases[0]['path'])
    report = {'task': 'DIAG-002/VCE-011', 'server': selected['address'],
              'fixtureCase': cases[0]['id'], 'fixture': fixture, 'passed': False,
              'harnessTransport': 'real MCP stdio in commissioned SDK container',
              'storageReadback': 'commissioned SSH/SFTP, not an extension web viewer'}
    command = ['docker', 'exec', '-i', '-w', '/workspace/android-client']
    for key, value in {'SAGETV_WORKSPACE': '/workspace/android-client',
                       'SAGETV_TEST_DEVICE_ALIAS': args.device_alias,
                       'SAGETV_TEST_SERVER_ALIAS': selected['alias'],
                       'SAGETV_MCP_CONFIG': '/workspace/android-client/config/firetv.toml',
                       'PYTHONPATH': '/workspace/android-client/mcp/src',
                       'ANDROID_USER_HOME': '/workspace/android-client/adb',
                       'ADB_VENDOR_KEYS': '/workspace/android-client/adb'}.items():
        command += ['-e', key+'='+value]
    command += ['opensagetv-vibe-dev', '/opt/opensagetv-vibe/android-python/bin/python3',
                '-m', 'sagetv_dev_mcp.server']
    client = MCPProcess(command=command)
    ssh = paramiko.SSHClient()
    ssh.load_system_host_keys()
    ssh.set_missing_host_key_policy(paramiko.RejectPolicy())
    sftp = None
    checkpoint = False
    receiver_original = None
    cc_original = ''
    started = time.monotonic()

    def control(action, **fields):
        request = Request(selected['core_mcp_base_url'].rstrip('/')+'/v1/control',
                          data=urlencode(dict(action=action, **fields)).encode(),
                          headers={'Authorization': 'Bearer '+selected['core_mcp_token']})
        with urlopen(request, timeout=15) as response:
            result = json.load(response)
        if result.get('ok') is not True:
            raise RuntimeError('Stock plugin control rejected: '+action)
        return result

    def receiver(value=None):
        before = control('companion.config_get', setting='diagnostics.enabled')['value']
        if value is not None and before != str(value).lower():
            return control('companion.config_set', setting='diagnostics.enabled',
                           expected=before, value=str(value).lower(), confirm=True)['value']
        return before

    def names():
        try:
            return sorted(item.filename for item in sftp.listdir_attr(storage)
                          if NAME.fullmatch(item.filename) and stat.S_ISREG(item.st_mode))
        except FileNotFoundError:
            return []

    def digest(path):
        assert stat.S_ISREG(sftp.lstat(path).st_mode)
        with sftp.open(path, 'rb') as stream:
            return hashlib.sha256(stream.read()).hexdigest()

    def status():
        return call_dict(client, 'dev_server_diagnostics', timeout=35)

    def poll(predicate, budget=40):
        deadline = time.monotonic()+budget
        while True:
            current = status()
            if predicate(current):
                return current
            if time.monotonic() >= deadline:
                raise RuntimeError('Diagnostic status deadline: '+str(current.get('serverDiagnosticsStatus')))
            time.sleep(.7)

    def validate_receipt(before, initial_time, require_player=True):
        receipt = poll(lambda value: str(value.get('serverDiagnosticsStatus', '')).startswith('Stored on server')
                       and int(value.get('serverDiagnosticsLastTime', 0)) > initial_time)
        checksum = re.search(r'\b[0-9a-f]{64}\b', receipt['serverDiagnosticsStatus'])
        assert checksum, 'Receipt must include the actual archive SHA-256'
        candidates = [name for name in names() if name not in before]
        matching = []
        for name in candidates:
            item = sftp.lstat(storage+'/'+name)
            assert stat.S_ISREG(item.st_mode) and 0 < item.st_size <= 8*1024*1024
            with sftp.open(storage+'/'+name, 'rb') as file:
                archive = file.read()
            if hashlib.sha256(archive).hexdigest() == checksum.group():
                matching.append((name, archive))
        assert len(matching) == 1, 'Receipt must match one independently read server file'
        name, archive = matching[0]
        with zipfile.ZipFile(io.BytesIO(archive)) as zipped:
            entries = zipped.infolist()
            assert 0 < len(entries) <= 32
            # Match the production bundle/store interface: nested bounded log
            # and trace text plus the checksum manifest are intentional, not
            # arbitrary paths or binary/media. A .txt-only oracle rejected a
            # valid, already checksum-verified archive during initial testing.
            assert all(re.fullmatch(r'[A-Za-z0-9_-]+(?:/[A-Za-z0-9_-]+)?\.(?:txt|log|jsonl|sha256)', entry.filename)
                       and not entry.is_dir() and entry.file_size <= 512*1024 for entry in entries)
            assert sum(entry.file_size for entry in entries) <= 16*1024*1024
            text = '\n'.join(zipped.read(entry).decode('utf-8') for entry in entries)
            assert '[client-statistics]' in text and 'connection=true' in text
            if require_player:
                assert 'backend=' in text
            assert selected['address'] not in text and fixture not in text and 'smb://' not in text
            assert zipped.testzip() is None
        return {'receipt': receipt, 'serverFilename': name, 'sha256': checksum.group(),
                'bytes': len(archive), 'entries': len(entries), 'privacyPassed': True}

    def health():
        state = call_dict(client, 'dev_player_state')
        return {key: state.get(key) for key in ('health_videoRendered', 'health_audioRendered',
                'health_isPlaying', 'health_playerError', 'health_videoDecoder', 'serverAddress', 'clientId')}

    try:
        print('CONNECT_AND_GUARD_STOCK_SERVER', flush=True)
        initialize(client)
        report['connection'] = call_dict(client, 'adb_connect', timeout=75)
        prefix='windows' if windows else 'unraid'
        ssh.connect(ssh_host, port=ssh_config.get(prefix+'_ssh_port', 22),
                    username=ssh_config[prefix+'_ssh_username'], password=ssh_config[prefix+'_ssh_password'], timeout=12)
        sftp = ssh.open_sftp(); sftp.get_channel().settimeout(20)
        base = selected['windows_sagetv_path'].replace('\\', '/').rstrip('/') if windows else '/mnt/user/appdata/sagetv/server'
        assert base in ('C:/Program Files/SageTV/SageTV','/mnt/user/appdata/sagetv/server')
        storage = base+'/vibe-client-logs'
        report['coreBefore'] = digest(base+'/Sage.jar')
        report['receiverJar'] = digest(base+'/JARs/VibeClientExtensionPlugin.jar')
        assert report['coreBefore'] == STOCK_CORE and report['receiverJar'] == UPLOAD_ONLY
        receiver_original = receiver()
        report['receiverOriginal'] = receiver_original
        saved = call_dict(client, 'dev_checkpoint_settings')
        checkpoint = saved.get('captured') is True
        assert checkpoint
        clean = call_dict(client, 'dev_prepare_clean_start', {'wake': True, 'graceful_timeout_s': 2}, timeout=120)
        assert clean.get('readyToLaunch') is True
        call_dict(client, 'dev_set_player_config', {'player': 'media3', 'streaming': 'pull', 'decoding': 'hardware',
                  'video_deinterlace_mode': 'off', 'mim_direct_mode': 'off',
                  'server_diagnostics_enabled': False, 'server_diagnostics_port': 8272})
        call_dict(client, 'dev_connect_server', {'address': selected['address'], 'save': False}, timeout=45)
        ready = call_dict(client, 'dev_wait_for_ui', {'connected': True, 'timeout_s': 40,
                          'menu_present': True, 'automation_ready': True, 'stable_ms': 500}, timeout=50)
        assert ready.get('passed') is True
        report['scopeBeforeWatch'] = call_dict(client, 'dev_server_control_identity', timeout=40)
        assert report['scopeBeforeWatch'].get('scopeVerified') is True
        cc_original = str(call_dict(client, 'dev_stv_caption_state').get('state', ''))
        assert cc_original
        receiver(True)
        if not args.reconnect_only and not args.exit_only:
            print('OFF_NO_WRITE_GATE', flush=True)
            before = names()
            call_dict(client, 'dev_server_diagnostics', {'action': 'server_upload'})
            time.sleep(3)
            assert names() == before
            report['offNoWrites'] = True
            print('EXACT_FIXTURE_WATCH', flush=True)
            report['watch'] = call_dict(client, 'dev_play_server_path', {'server_path': fixture,
                    'timeout_s': 45, 'verify_ms': 1500, 'restart_from_beginning': True}, timeout=365)
            if report['watch'].get('passed') is not True:
                report['startupLog'] = tool_call(client, 'get_logcat', {'lines': 10000,
                             'pattern': 'ParserException|UnrecognizedInput|ExoPlayer|Media3Pull|ERROR_CODE|load_error'}, timeout=45)
                raise RuntimeError('Fixture startup failed: '+str(report['watch'].get('playback', {}).get('failureReason')))
            output.with_suffix('.ready.json').write_text(json.dumps({
                'scopeVerified': True, 'scope': report['scopeBeforeWatch'],
                'expiresAtMs': int((time.time()+60)*1000), 'fixture': fixture,
                'state': 'Windows upload acceptance with active native playback'}))
            report['beforeUpload'] = health()
            assert report['beforeUpload']['serverAddress'] == selected['address']
            initial_time = int(status().get('serverDiagnosticsLastTime', 0))
            call_dict(client, 'dev_set_player_config', {'server_diagnostics_enabled': True})
            print('ENABLED_RECEIPT_AND_PRIVACY', flush=True)
            report['enabledUpload'] = validate_receipt(before, initial_time)
            before = names(); initial_time = int(status().get('serverDiagnosticsLastTime', 0))
            call_dict(client, 'dev_server_diagnostics', {'action': 'server_upload'})
            report['manualUpload'] = validate_receipt(before, initial_time)
            print('MANUAL_RECEIPT_VERIFIED', flush=True)
            report['afterUpload'] = health()
            for key in ('health_videoRendered', 'health_audioRendered'):
                assert int(report['afterUpload'][key]) > int(report['beforeUpload'][key])
            assert report['afterUpload']['health_isPlaying'] is True and not report['afterUpload']['health_playerError']
            call_dict(client, 'dev_set_player_config', {'server_diagnostics_enabled': False})
            before = names()
            call_dict(client, 'dev_server_diagnostics', {'action': 'server_upload'})
            time.sleep(3)
            assert names() == before
            report['disableNoWrites'] = True
            print('DISABLED_NO_WRITES_VERIFIED', flush=True)
            before_health = health()
            call_dict(client, 'dev_set_player_config', {'server_diagnostics_port': 8273, 'server_diagnostics_enabled': True})
            report['unreachable'] = poll(lambda value: str(value.get('serverDiagnosticsStatus', '')).startswith('Unavailable'))
            assert names() == before
            after_health = health()
            for key in ('health_videoRendered', 'health_audioRendered'):
                assert int(after_health[key]) > int(before_health[key])
            assert after_health['health_isPlaying'] is True and not after_health['health_playerError']
            report['unreachableNoWritesHealthy'] = True
            call_dict(client, 'dev_set_player_config', {'server_diagnostics_enabled': False, 'server_diagnostics_port': 8272})
            receiver(False)
            before = names()
            call_dict(client, 'dev_set_player_config', {'server_diagnostics_enabled': True})
            report['receiverDisabled'] = poll(lambda value: str(value.get('serverDiagnosticsStatus', '')).startswith('Unavailable'))
            assert names() == before
            report['receiverDisabledNoWrites'] = True
            print('UNREACHABLE_AND_DISABLED_RECEIVER_VERIFIED', flush=True)
            call_dict(client, 'dev_set_player_config', {'server_diagnostics_enabled': False})
        receiver(True)
        # Same-server reconnect must create a fresh receipt, not count a stale
        # earlier upload. Exit remains bounded/best-effort; a disconnected
        # session must not keep sending its old report in the background.
        before=names(); initial_time=int(status().get('serverDiagnosticsLastTime',0))
        if not args.exit_only:
            call_dict(client, 'dev_exit_session', {'stop_app': False}, timeout=100)
            call_dict(client, 'dev_set_player_config', {'server_diagnostics_enabled': True})
            time.sleep(2)
            assert names()==before
            report['disconnectedNoWrites']=True
            call_dict(client, 'dev_connect_server', {'address':selected['address'],'save':False}, timeout=45)
            ready=call_dict(client,'dev_wait_for_ui',{'connected':True,'timeout_s':40,
                             'menu_present':True,'automation_ready':True,'stable_ms':500}, timeout=50)
            assert ready.get('passed') is True
            report['reconnectScope']=call_dict(client,'dev_server_control_identity',timeout=40)
            assert report['reconnectScope'].get('scopeVerified') is True
            call_dict(client,'dev_server_diagnostics',{'action':'server_upload'})
            report['reconnectUpload']=validate_receipt(before,initial_time,require_player=False)
            print('SAME_SERVER_RECONNECT_RECEIPT_VERIFIED',flush=True)
        else:
            call_dict(client,'dev_set_player_config',{'server_diagnostics_enabled':True})
            report['exitSetupReceipt']=validate_receipt(before,initial_time,require_player=False)
        before=names()
        report['enabledExit']=call_dict(client,'dev_exit_session',{'stop_app':False},timeout=100)
        time.sleep(2)
        first_exit_names=names()
        time.sleep(3)
        assert names()==first_exit_names, 'Exited/disconnected session must not keep uploading'
        assert len(first_exit_names)-len(before)<=2, 'Final checkpoint must remain bounded'
        report['exitFinalReportCount']=len(first_exit_names)-len(before)
        report['exitNoRepeatedWrites']=True
        # Final upload is documented best-effort, not a forced-stop guarantee.
        # Record the observed count rather than requiring or inventing a file.
        report['exitState']=call_dict(client,'dev_player_state')
        assert report['exitState'].get('connected') is False
        report['coreAfter'] = digest(base+'/Sage.jar')
        assert report['coreAfter'] == report['coreBefore']
        report['passed'] = True
    except Exception as error:
        report['error'] = str(error)
        raise
    finally:
        report['restoreErrors'] = []
        if checkpoint:
            try:
                call_dict(client, 'dev_set_player_config', {'server_diagnostics_enabled': False})
                # This upload test never changes server CC. Do not try to
                # reapply an unchanged value to a UI context already removed
                # by the deliberately-tested exit (nor reconnect to do so).
                report['serverCcUnchanged'] = cc_original
                if not report.get('enabledExit'):
                    report['exit'] = call_dict(client, 'dev_exit_session', {'stop_app': True}, timeout=100)
            except Exception as error:
                report['restoreErrors'].append('session: '+str(error))
            try:
                report['settingsRestore'] = call_dict(client, 'dev_restore_settings', timeout=100)
                assert report['settingsRestore'].get('restored') is True
            except Exception as error:
                report['restoreErrors'].append('preferences: '+str(error))
        if receiver_original is not None:
            try:
                receiver(receiver_original)
                report['receiverRestored'] = receiver() == receiver_original
                assert report['receiverRestored']
            except Exception as error:
                report['restoreErrors'].append('receiver: '+str(error))
        if sftp:
            sftp.close()
        ssh.close(); client.close()
        report['elapsedSeconds'] = round(time.monotonic()-started, 3)
        if report['restoreErrors']:
            report['passed'] = False
        output.write_text(json.dumps(report, indent=2)+'\n')
        print(json.dumps({'passed': report['passed'], 'report': str(output), 'error': report.get('error'),
                          'restoreErrors': report['restoreErrors']}), flush=True)
    return 0 if report['passed'] else 1


if __name__ == '__main__':
    raise SystemExit(main())
