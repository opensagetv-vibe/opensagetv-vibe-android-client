#!/usr/bin/env python3
"""EXT-010: distinguish real stock Push/Fixed seek commands and producer flushes.

This is observation, not a runtime workaround. Each public Seek is sent once;
independent far-target preparation is explicit, never hidden recovery/retry.
"""
import argparse
import json
import os
import re
from pathlib import Path
import time
from android_test_keep_awake import begin, end, state_path
from mcp_lifecycle_test import require, wait_automation_ready
from mcp_seek_suite import MCPProcess, initialize, call_dict
from sagetv_dev_mcp.config import load_config, load_test_environment
from sagetv_dev_mcp.adb import AdbClient
from sagetv_dev_mcp.core_mcp_api import CoreMcpApiClient
from mcp_forward_cache_probe import reject_outside_window

ROOT = Path(__file__).resolve().parents[1]


def relative_server_ms(ui):
    # Fixed resets the decoder epoch; Core owns/adds timestampOffset. A client
    # position23s can correctly mean recording10:23 after a producer seek.
    return int(ui['mediaTimeMs']) - int(ui['seekStartMs'])


def output_progressed(before, after):
    if after.get('health_probeSupported') is False:
        # IJK has no Exo render counters. Keep this weaker witness explicit;
        # readable captured target frames remain a separate physical review.
        return (after.get('health_firstVideoFrameRendered') is True and
                after.get('health_basicIsPlaying') is True and
                int(after.get('health_playerPositionMs', -1)) >
                int(before.get('health_playerPositionMs', -1)))
    return (int(after.get('health_videoRendered', -1)) >
            int(before.get('health_videoRendered', -1)) and
            int(after.get('health_audioRendered', -1)) >
            int(before.get('health_audioRendered', -1)))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--streaming', choices=('dynamic', 'fixed'), required=True)
    parser.add_argument('--media-file-id', type=int, required=True)
    parser.add_argument('--fixed-container', choices=('mpegts', 'dvd'), default='mpegts')
    parser.add_argument('--player', choices=('media3', 'exoplayer', 'gsyplayer', 'ijkplayer'), default='media3')
    parser.add_argument('--decoding', choices=('hardware', 'software'), default='hardware')
    parser.add_argument('--gsy-engine', choices=('media3', 'legacy_exo'), default='media3')
    parser.add_argument('--paused-only', action='store_true', help='Affected paused producer-seek gate only')
    parser.add_argument('--probe-unsupported-seek', action='store_true',
            help='Inject real zero-reply command29 in playing/paused state; never calls backend seek directly')
    parser.add_argument('--forward-cache-trial', action='store_true',
            help='Debug-only Media3 selected-queue trial; no persisted preference or capability claim')
    parser.add_argument('--cache-command-probe', action='store_true',
            help='Explicit local command29 probe for near/paused targets; not a claim Core chose cache seeking')
    parser.add_argument('--probe-cache-rejections', action='store_true')
    parser.add_argument('--production-cache', action='store_true',
            help='Qualify default detailed Push cache source without enabling the debug trial')
    parser.add_argument('--trial-caption-state', choices=('Off','CC1','CC2'), default='Off')
    parser.add_argument('--capture-ready-file', type=Path)
    parser.add_argument('--prepare-ms', type=int, help='Explicit independent fixture positioning before the measured near seek')
    parser.add_argument('--install-apk', default='')
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    queue_gate = args.forward_cache_trial or args.production_cache
    extractor_backend = args.player in ('media3', 'exoplayer') or (
            args.player == 'gsyplayer' and args.gsy_engine in ('media3', 'legacy_exo'))
    require(not args.production_cache or (args.streaming == 'dynamic'
            and extractor_backend and not args.forward_cache_trial
            and not args.probe_unsupported_seek), 'Default cache gate requires detailed extractor Push')
    require(not args.forward_cache_trial or (extractor_backend and
            args.streaming == 'fixed' and not args.probe_unsupported_seek),
            'Forward queue trial is isolated to actual extractor delegates; no unsupported-probe mixing')
    require(not args.cache_command_probe or queue_gate,
            'Positive command injection requires the isolated queue trial')
    require(not args.probe_cache_rejections or queue_gate,
            'Cache rejection probes require the actual queue source')
    out = args.output.resolve()
    require(out.is_relative_to((ROOT/'artifacts/active/EXT-010').resolve())
            and not out.exists(), 'New explicit EXT-010 output required')
    env = load_test_environment()
    require(env.device_serial() == '192.168.10.25:5555'
            and env.server_address() == '192.168.10.175', 'Exact non-Pro/stock175 required')
    os.environ['SAGETV_ARTIFACT_DIR'] = str(out.parent)
    out.parent.mkdir(parents=True, exist_ok=True)
    ready = args.capture_ready_file.resolve() if args.capture_ready_file else None
    require(ready is None or (ready.parent == out.parent and not ready.exists()),
            'New readiness marker must share owning task directory')
    report = {'passed': False, 'selected': args.streaming, 'rows': [],
              'forwardCacheTrial': args.forward_cache_trial,
              'productionCache': args.production_cache,
              'cacheCommandProbe': args.cache_command_probe,
              'serverChanges': False, 'tunerControls': False, 'hiddenRetries': False}
    c = MCPProcess()
    api = ctx = cc = None
    awake = checkpoint = connected = False
    trial_adb = None

    def save(): out.write_text(json.dumps(report, indent=2)+'\n')
    def state(): return call_dict(c, 'dev_player_state')
    def scope():
        current = state()
        ui = api.ui_state(ctx)
        require(current.get('serverAddress') == env.server_address()
                and current.get('clientId') == env.device()['client_id']
                and ui.get('media', {}).get('mediaFileId') == args.media_file_id,
                'Source/client/server changed; no further control authorized')
        require(current.get('health_pushMode') is True, 'Actual Push pipeline required')
        require(current.get('pushDetailedBufferStats') is (args.streaming != 'fixed' or args.forward_cache_trial),
                'Negotiated detailed/basic wire mode disagrees with this exact test config')
        current['qualificationServerUi'] = ui
        current['qualificationRelativeMs'] = relative_server_ms(ui)
        return current

    def seek_phase(label, target, paused=False):
        before = scope()
        row = {'phase': label, 'targetMs': target, 'paused': paused, 'before': before,
               'explicitTestPreparation': label in ('far-producer-seek', 'explicit-source-preparation'), 'samples': []}
        report['rows'].append(row)
        started = time.monotonic()
        if args.cache_command_probe and label in ('near-forward', 'paused-forward'):
            # Core's command29 target is decoder/transcode-epoch relative, not
            # an absolute recording timestamp. This injection is explicit test
            # input, not a substitute stock-API result or automatic recovery.
            native_target = int(before['health_playerPositionMs']) + target - before['qualificationRelativeMs']
            row['requestedDecoderMs'] = native_target
            row['request'] = trial_adb.dev_control('seek_time', target_ms=native_target,
                                                   wire_command29=True)
            row['inputPath'] = 'explicit_client_media_command_29_not_core_seek'
            require(row['request'].get('accepted') is True and
                    int(row['request'].get('replyLength', -1)) == 0,
                    'Actual command29 was not accepted with zero reply framing')
        else:
            row['request'] = api.seek(ctx, target)  # exactly once, no timeout replay
        # A near target is20s ahead, beyond this12s observation budget. Normal
        # playback must not eventually wander into the target and fake a seek.
        deadline = started + (12 if label == 'near-forward' else 25)
        while time.monotonic() < deadline:
            sample = scope()
            row['samples'].append(sample)
            elapsed_ms = round((time.monotonic()-started)*1000)
            observed = sample['qualificationRelativeMs']
            near = abs(observed-target) <= 2000 if paused else target-2000 <= observed <= target+elapsed_ms+2000
            progressed = paused or (len(row['samples']) >= 2 and
                    output_progressed(row['samples'][-2], sample))
            witnessed = int(sample.get('serverSeekSequence', 0)) > int(before.get('serverSeekSequence', 0)) or int(sample.get('serverFlushSequence', 0)) > int(before.get('serverFlushSequence', 0))
            if near and progressed and witnessed and not sample.get('health_playerError'):
                # Outlast Core's temporary seek guess and prove a held paused
                # frame or sustained real output, not just an acknowledged API.
                time.sleep(6)
                verified = scope()
                row['verification'] = verified
                elapsed_ms = round((time.monotonic()-started)*1000)
                verified_ms = verified['qualificationRelativeMs']
                if paused:
                    stable = (verified.get('state') == 3
                            and (int(verified.get('health_videoRendered', 0)) > 0
                                 or verified.get('health_firstVideoFrameRendered') is True)
                            and abs(int(verified.get('health_playerPositionMs', -1))
                                    - int(sample.get('health_playerPositionMs', -1))) <= 100
                            and abs(verified_ms-target) <= 2000)
                else:
                    stable = (target-2000 <= verified_ms <= target+elapsed_ms+2000
                            and output_progressed(sample, verified))
                row['outputWitness'] = ('nativeClockAndFirstFrame' if
                        verified.get('health_probeSupported') is False else 'decoderRenderCounters')
                row['passed'] = stable and not verified.get('health_playerError')
                break
            time.sleep(.3)
        row['elapsedMs'] = round((time.monotonic()-started)*1000)
        row['after'] = state()
        row['passed'] = row.get('passed', False)
        if args.cache_command_probe and label in ('near-forward', 'paused-forward'):
            before_cache = str(before.get('pushForwardCacheState', ''))
            after_cache = str(row['after'].get('pushForwardCacheState', ''))
            old = re.search(r'(?:^|,)seeks=(\d+)', before_cache)
            new = re.search(r'(?:^|,)seeks=(\d+)', after_cache)
            actual_target = re.search(r'(?:^|,)lastTargetUs=(-?\d+)', after_cache)
            row['actualQueueTransaction'] = bool(old and new and actual_target
                    and int(new[1]) > int(old[1])
                    and int(actual_target[1]) == row['requestedDecoderMs']*1000)
            row['passed'] = row['passed'] and row['actualQueueTransaction']
        row['command29Delta'] = int(row['after'].get('serverSeekSequence', 0)) - int(before.get('serverSeekSequence', 0))
        row['producerFlushDelta'] = int(row['after'].get('serverFlushSequence', 0)) - int(before.get('serverFlushSequence', 0))
        row['events'] = call_dict(c, 'dev_player_events')
        print(json.dumps({k: row[k] for k in ('phase', 'targetMs', 'passed', 'command29Delta', 'producerFlushDelta', 'elapsedMs')}), flush=True)
        row['screenshot'] = call_dict(c, 'take_screenshot', {'label': 'ext010-'+label})
        save()
        return row

    try:
        initialize(c)
        report['adb'] = call_dict(c, 'adb_connect')
        report['before'] = state()
        require(not report['before'].get('connected')
                and report['before'].get('clientId') == env.device()['client_id'],
                'Requires exact idle disconnected device')
        api = CoreMcpApiClient.discover(env.server_address())
        report['activity'] = api._request('server.activity')
        sources = api._legacy_client().call('GetMediaFiles', 'V', start=0, size=5000,
                fields=('MediaFileID', 'FileDuration', 'MediaTitle')).get('Result', [])
        matches = [s for s in sources if isinstance(s, dict) and s.get('MediaFileID') == args.media_file_id]
        require(len(matches) == 1 and int(matches[0]['FileDuration']) >= 850000,
                'Exact existing long imported fixture must resolve first')
        report['source'] = matches[0]
        report['awake'] = begin(env.device_serial(), state_path(env.device_serial()), 'automated')
        awake = True
        report['checkpoint'] = call_dict(c, 'dev_checkpoint_settings')
        require(report['checkpoint'].get('captured'), 'Checkpoint failed')
        checkpoint = True
        if args.install_apk:
            report['install'] = call_dict(c, 'install_dev_apk', {'apk_path': args.install_apk}, timeout=200)
            require('Success' in str(report['install'].get('result', '')), 'Guarded APK install failed')
        require(call_dict(c, 'dev_prepare_clean_start', {'wake': True, 'graceful_timeout_s': 2}).get('readyToLaunch'), 'Clean start failed')
        config = {'player': args.player, 'gsy_engine': args.gsy_engine,
                  'streaming': args.streaming, 'decoding': args.decoding,
                  'mim_direct_mode': 'off'}
        if args.streaming == 'fixed':
            # .175 has no GPU driver. Use a bounded low-resolution encode;
            # software-server throughput is not an Android decoder verdict.
            config.update(fixed_encoding_preference='always', fixed_encoding_format=args.fixed_container,
                          fixed_video_resolution='d1', fixed_video_bitrate_kbps=1000,
                          fixed_audio_codec='aac', fixed_audio_channels='2',
                          fixed_remuxing_preference='off')
        if queue_gate:
            config['legacy_server_caption_mode'] = 'stv'
        report['config'] = call_dict(c, 'dev_set_player_config', config)
        if queue_gate:
            cfg = load_config()
            trial_adb = AdbClient(serial=cfg.device, dev_package=cfg.dev_package,
                    adb=cfg.adb, aapt=cfg.aapt)
            if args.forward_cache_trial:
                report['trialEnable'] = trial_adb.dev_control('push_cache_trial', enabled='true')
                require(report['trialEnable'].get('enabled') is True, 'Trial not enabled')
        report['connect'] = call_dict(c, 'dev_connect_server', {'address': env.server_address(), 'save': False}, timeout=45)
        connected = True
        wait_automation_ready(c, timeout_s=60)
        identity = state()
        require(identity.get('clientId') == env.device()['client_id'], 'Wrong client')
        ctx = api.resolve_context(identity['clientId'])
        report['scope'] = {'serial': env.device_serial(), 'clientId': identity['clientId'], 'context': ctx}
        cc = api.closed_caption_state(ctx)
        report['captionCheckpoint'] = cc
        if queue_gate:
            report['trialCaptionsOff' if args.trial_caption_state == 'Off' else 'trialCaptionsOn'] = (
                    api.set_closed_caption_state(ctx, args.trial_caption_state))
        report['watch'] = api.watch(ctx, args.media_file_id)
        report['startup'] = call_dict(c, 'dev_wait_for_playback_started',
                {'timeout_s': 55, 'verify_ms': 1500, 'expect_video': True, 'expect_audio': True}, timeout=80)
        require(report['startup'].get('passed'), 'Startup output unavailable; do not classify seek')
        initial = scope()
        require(initial.get('streaming') == args.streaming, 'Selected transport changed')
        if args.prepare_ms is not None:
            require(0 <= args.prepare_ms <= int(matches[0]['FileDuration'])-120000,
                    'Explicit test position needs headroom')
            preparation = seek_phase('explicit-source-preparation', args.prepare_ms)
            require(preparation['passed'], 'Independent fixture preparation failed; no hidden retry')
            initial = scope()  # Never reuse the pre-preparation bookmark for headroom.
        if args.probe_unsupported_seek:
            cfg = load_config()
            local = AdbClient(serial=cfg.device, dev_package=cfg.dev_package,
                    adb=cfg.adb, aapt=cfg.aapt)
            try:
                for paused_probe in (False, True):
                    if paused_probe:
                        api.media_control(ctx, 'pause')
                        deadline = time.monotonic()+8
                        while time.monotonic() < deadline and scope().get('state') != 3:
                            time.sleep(.2)
                        time.sleep(1)
                    probe_started = time.monotonic()
                    before = scope()
                    require(before.get('state') == (3 if paused_probe else 2),
                            'Probe requires proven playing/paused state')
                    target = int(before['mediaTimeMs'])+60000
                    request = local.dev_control('seek_time', target_ms=target, wire_command29=True)
                    probe = {'paused': paused_probe, 'before': before, 'request': request,
                             'passed': False}
                    report.setdefault('unsupportedSeekProbes', []).append(probe)
                    save()  # Preserve an incorrect injector/reply before asserting.
                    require(str(request.get('accepted')).lower() == 'false'
                            and int(request.get('replyLength', -1)) == 0,
                            'Unsupported command must reject locally with zero wire bytes')
                    time.sleep(3)
                    after = scope()
                    delta = int(after['mediaTimeMs'])-int(before['mediaTimeMs'])
                    elapsed = round((time.monotonic()-probe_started)*1000)
                    probe.update(after=after, observedDeltaMs=delta, elapsedMs=elapsed)
                    save()
                    require(int(after['serverSeekSequence']) == int(before['serverSeekSequence'])+1
                            and after['serverFlushSequence'] == before['serverFlushSequence'],
                            'Not exactly one real command29 without producer replacement')
                    require(after.get('state') == before.get('state') and
                            ((abs(delta) <= 100) if paused_probe else
                             (elapsed < 45000 and 500 <= delta <= elapsed+1500)),
                            'Rejected command changed paused/playing timeline')
                    probe.update(after=after, passed=True)
                    save()
                    if paused_probe:
                        api.media_control(ctx, 'play')
                        resumed = call_dict(c, 'dev_wait_for_playback_started',
                                {'timeout_s': 20, 'verify_ms': 1000,
                                 'expect_video': True, 'expect_audio': True}, timeout=45)
                        require(resumed.get('passed'), 'Probe pause did not resume A/V')
            finally:
                local.close()
            initial = scope()
        # Wait only for the existing decoder to accumulate future data; no
        # command/invented seek map is used to manufacture a buffer witness.
        report['bufferWitnessQualification'] = ('native-cache-depth-unavailable' if
                initial.get('health_probeSupported') is False else 'reported-decoder-depth')
        deadline = time.monotonic()+(25 if queue_gate else 12)
        while initial.get('health_probeSupported') is not False and time.monotonic() < deadline:
            initial = scope()
            if int(initial.get('health_bufferedPositionMs', 0))-int(initial.get('health_playerPositionMs', 0)) > (35000 if queue_gate else 12000):
                break
            time.sleep(.5)
        report['bufferWitness'] = initial
        if args.probe_cache_rejections:
            report['playingCacheRejections'] = reject_outside_window(c, trial_adb)
            initial = scope()
        target = initial['qualificationRelativeMs']+20000
        if not args.paused_only:
            require(target < int(matches[0]['FileDuration'])-60000, 'Insufficient headroom')
            near_result = seek_phase('near-forward', target)
            if queue_gate:
                require(near_result['passed'] and near_result['command29Delta'] > 0
                        and near_result['producerFlushDelta'] == 0,
                        'Queue trial needs actual command29 target without producer replacement')
            # This separately labelled far seek tests stock producer replacement.
            # It never changes a failed near-forward row into a PASS.
            far = seek_phase('far-producer-seek', 600000)
            require(far['passed'], 'Far producer seek failed; abort further commands')
        report['pause'] = api.media_control(ctx, 'pause')
        deadline = time.monotonic()+8
        while time.monotonic() < deadline:
            paused = scope()
            if paused.get('state') == 3: break
            time.sleep(.2)
        require(paused.get('state') == 3, 'Pause did not complete')
        if args.probe_cache_rejections:
            report['pausedCacheRejections'] = reject_outside_window(c, trial_adb)
            paused = scope()
        paused_target = ((150000 if paused['qualificationRelativeMs'] > 300000 else 600000)
                         if args.paused_only else paused['qualificationRelativeMs']+5000)
        seek_phase('paused-producer-seek' if args.paused_only else 'paused-forward', paused_target, paused=True)
        report['resume'] = api.media_control(ctx, 'play')
        report['resumed'] = call_dict(c, 'dev_wait_for_playback_started',
                {'timeout_s': 30, 'verify_ms': 1000, 'expect_video': True, 'expect_audio': True}, timeout=55)
        report['passed'] = all(row['passed'] for row in report['rows']) and bool(report['resumed'].get('passed'))
        if ready:
            final = scope()
            require(report['passed'] and final.get('state') == 2 and
                    final.get('health_firstVideoFrameRendered') is True,
                    'Independent capture requires proven actual output')
            ready.write_text(json.dumps({'scopeVerified': True,
                'expiresAtMs': int(time.time()*1000)+30000, 'stage': 'cache-resumed',
                'serial': env.device_serial(), 'server': env.server_address(),
                'clientId': env.device()['client_id'], 'mediaFileId': args.media_file_id})+'\n')
            time.sleep(25)
    except Exception as error:
        report['failure'] = str(error)
        try: report['failureState'] = state()
        except Exception as inner: report['snapshotError'] = str(inner)
    finally:
        for name, action in (
            ('trialDisable', lambda: trial_adb.dev_control('push_cache_trial', enabled='false')
             if trial_adb else None),
            ('captionRestore', lambda: api.set_closed_caption_state(ctx, cc) if api and ctx and cc is not None else None),
            ('exit', lambda: call_dict(c, 'dev_exit_session', {'stop_app': True}, timeout=45) if connected else None),
            ('restore', lambda: call_dict(c, 'dev_restore_settings', timeout=100) if checkpoint else None),
            ('powerRestore', lambda: end(env.device_serial(), state_path(env.device_serial()), 'automated') if awake else None)):
            try:
                report[name] = action()
                if name == 'restore' and checkpoint: require(report[name].get('restored'), 'Settings restore failed')
            except Exception as error:
                report['passed'] = False
                report[name+'Error'] = str(error)
        save()
        c.close()
        if trial_adb: trial_adb.close()
    print(json.dumps({'passed': report['passed'], 'failure': report.get('failure'), 'output': str(out)}))
    return 0 if report['passed'] else 1


if __name__ == '__main__': raise SystemExit(main())
