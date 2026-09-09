from pathlib import Path
import importlib.util
import sys
import unittest

ROOT = Path(__file__).resolve().parents[1]
SCRIPTS = ROOT / "scripts"
MCP_SRC = ROOT / "mcp" / "src"
sys.path.insert(0, str(SCRIPTS))
sys.path.insert(0, str(MCP_SRC))
spec = importlib.util.spec_from_file_location("mcp_player_matrix", SCRIPTS / "mcp_player_matrix.py")
module = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = module
assert spec.loader is not None
spec.loader.exec_module(module)


class MCPPlayerMatrixTests(unittest.TestCase):
    def test_stock_video_name_bypasses_stv_search_and_uses_sagex_watch(self):
        script = (SCRIPTS / "mcp_player_matrix.py").read_text(encoding="utf-8")
        self.assertIn('parser.add_argument("--video-name"', script)
        self.assertIn('call_dict(client, "dev_play_video", {', script)
        self.assertIn('"reason": "stock_sagex_video_name_requested"', script)

    def test_case_launch_verifies_active_configuration_and_retries_once(self):
        script = (SCRIPTS / "mcp_player_matrix.py").read_text(encoding="utf-8")
        self.assertIn("for activation_attempt in (1, 2):", script)
        self.assertIn('active_config = call_dict(client, "dev_player_state")', script)
        self.assertIn("active player configuration did not apply after retry", script)

    def test_full_matrix_expands_to_105_configuration_cases(self):
        cases = module.build_cases(
            list(module.PLAYERS),
            list(module.STREAMING_MODES),
            list(module.DECODERS),
            list(module.GSY_ENGINES),
        )
        self.assertEqual(len(cases), 105)
        ids = {case.id for case in cases}
        self.assertIn("exoplayer__push__hardware", ids)
        self.assertIn("media3__pull__software", ids)
        self.assertIn("media3__smb_direct__hardware", ids)
        self.assertIn("exoplayer__smb_auto__fallback", ids)
        self.assertIn("ijkplayer__fixed__fallback", ids)
        self.assertIn("gsyplayer__pull__hardware__gsy_auto", ids)
        self.assertIn("gsyplayer__pull__hardware__gsy_media3", ids)
        self.assertIn("gsyplayer__pull__hardware__gsy_system", ids)
        self.assertIn("gsyplayer__pull__hardware__gsy_legacy_exo", ids)

    def test_non_gsy_cases_do_not_multiply_by_gsy_engine(self):
        cases = module.build_cases(["media3"], ["push", "pull", "fixed"], list(module.DECODERS), list(module.GSY_ENGINES))
        self.assertEqual(len(cases), 9)
        self.assertTrue(all(case.gsy_engine == "auto" for case in cases))

    def test_watchdog_expiry_is_media_observation_not_infrastructure_failure(self):
        self.assertEqual(module.status_from_recovery({"watchdog_expired": True, "recovered": False}, 2000), "WATCHDOG_EXPIRED")
        script = (SCRIPTS / "mcp_player_matrix.py").read_text(encoding="utf-8")
        self.assertIn('default=180000', script)
        self.assertIn('"watchdogExpiryIsFailure": False', script)
        self.assertIn('causeAttribution', script)
        self.assertIn('undetermined_unless_evidence_proves_android_sagetv_server_or_ffmpeg', script)

    def test_watchdog_is_per_step_and_absolute_seek_inherits_it(self):
        script = (SCRIPTS / "mcp_player_matrix.py").read_text(encoding="utf-8")
        self.assertIn('default=0.0, help="Absolute-seek watchdog override in seconds; 0 inherits --watchdog-ms per step"', script)
        self.assertIn('args.watchdog_ms / 1000.0', script)
        self.assertIn('watchdog_ms=args.watchdog_ms', script)

    def test_long_matrix_steps_emit_dynamic_single_line_progress_and_report_persistent_adb_session(self):
        script = (SCRIPTS / "mcp_player_matrix.py").read_text(encoding="utf-8")
        self.assertIn('def wait_for_av_recovery', script)
        self.assertIn('def _live_wait_line', script)
        self.assertIn("sys.stdout.write('\\r\\x1b[2K' + text)", script)
        self.assertIn('waitingFor=', script)
        self.assertIn('video={video}', script)
        self.assertIn('audio={audio}', script)
        self.assertIn('adb_session_status', script)
        self.assertIn('PASS: persistent ADB shell', script)
        self.assertIn('def print_output_health', script)
        self.assertIn('counters:', script)
        self.assertIn('diagnostics only:', script)

    def test_all_media_actions_use_host_polled_av_counter_recovery(self):
        script = (SCRIPTS / "mcp_player_matrix.py").read_text(encoding="utf-8")
        self.assertIn("'dev_local_seek_absolute'", script)
        self.assertIn("'dev_seek_relative'", script)
        self.assertIn("'dev_player_control'", script)
        self.assertIn("'dev_comskip'", script)
        self.assertGreaterEqual(script.count('wait_for_av_recovery('), 5)
        self.assertNotIn('call_dict(client, "dev_run_relative_seek_check", {', script)
        self.assertNotIn('call_dict(client, "dev_run_comskip_check", {', script)

    def test_matrix_can_use_exact_server_path_without_search_navigation(self):
        script = (SCRIPTS / "mcp_player_matrix.py").read_text(encoding="utf-8")
        self.assertIn('parser.add_argument("--server-path"', script)
        self.assertIn('one of --text, --video-name, or --server-path is required', script)
        self.assertIn('if server_path.strip():', script)
        self.assertIn('call_dict(client, "dev_play_server_path"', script)
        self.assertIn('"reason": "exact_server_path_requested"', script)
        self.assertIn('"serverPath": args.server_path', script)


    def test_hardware_only_selection_is_35_cases(self):
        cases = module.build_cases(
            list(module.PLAYERS),
            list(module.STREAMING_MODES),
            ["hardware"],
            list(module.GSY_ENGINES),
        )
        self.assertEqual(len(cases), 35)
        self.assertTrue(all(case.decoder == "hardware" for case in cases))
        script = (SCRIPTS / "mcp_player_matrix.py").read_text(encoding="utf-8")
        self.assertIn('--hardware-only', script)
        self.assertIn('Convenience alias for --decoding hardware', script)


    def test_retest_exclusions_can_skip_unchanged_players_engines_and_cases(self):
        cases = module.build_cases(
            ["exoplayer", "media3", "gsyplayer"],
            ["push", "pull"],
            ["hardware"],
            list(module.GSY_ENGINES),
        )
        kept, removed = module.exclude_cases(
            cases,
            ["exoplayer"],
            ["system"],
            "media3__pull__hardware",
        )
        kept_ids = {case.id for case in kept}
        removed_ids = set(removed)
        self.assertNotIn("exoplayer__push__hardware", kept_ids)
        self.assertNotIn("exoplayer__pull__hardware", kept_ids)
        self.assertNotIn("media3__pull__hardware", kept_ids)
        self.assertNotIn("gsyplayer__push__hardware__gsy_system", kept_ids)
        self.assertNotIn("gsyplayer__pull__hardware__gsy_system", kept_ids)
        self.assertIn("media3__push__hardware", kept_ids)
        self.assertIn("gsyplayer__pull__hardware__gsy_media3", kept_ids)
        self.assertIn("exoplayer__push__hardware", removed_ids)
        self.assertIn("media3__pull__hardware", removed_ids)
        self.assertIn("gsyplayer__pull__hardware__gsy_system", removed_ids)

    def test_retest_exclusion_cli_is_reported(self):
        script = (SCRIPTS / "mcp_player_matrix.py").read_text(encoding="utf-8")
        self.assertIn('"--exclude-players"', script)
        self.assertIn('"--exclude-gsy-engines"', script)
        self.assertIn('"--exclude-case-id"', script)
        self.assertIn('"retestExclusions"', script)
        self.assertIn('excludedCaseCount', script)

    def test_slow_recovery_threshold_has_cli_aliases_and_report_field(self):
        script = (SCRIPTS / "mcp_player_matrix.py").read_text(encoding="utf-8")
        self.assertIn('"--slow-recovery-ms", "--slow_recover_ms", "--slow-recover-ms"', script)
        self.assertIn('dest="slow_recovery_ms"', script)
        self.assertIn('"slowRecoveryMs": args.slow_recovery_ms', script)
        self.assertIn('--slow-recovery-ms/--slow_recover_ms must be between 0 and 300000', script)

    def test_fixed_streaming_has_complete_encoding_parameters(self):
        script = (SCRIPTS / "mcp_player_matrix.py").read_text(encoding="utf-8")
        config = (SCRIPTS / "mcp_config_values.py").read_text(encoding="utf-8")
        receiver = (ROOT / "source/dev/android-tv/src/debug/java/opensagetv/vibe/miniclient/android/tv/debug/DebugPlayerConfigCommands.java").read_text(encoding="utf-8")
        state = (ROOT / "source/dev/android-tv/src/debug/java/opensagetv/vibe/miniclient/android/tv/debug/DebugStateProvider.java").read_text(encoding="utf-8")
        for option in (
            "--fixed-encoding-preference", "--fixed-encoding-format", "--fixed-video-bitrate-kbps",
            "--fixed-video-fps", "--fixed-key-frame-interval", "--fixed-use-b-frames",
            "--fixed-video-resolution", "--fixed-audio-codec", "--fixed-audio-bitrate-kbps",
            "--fixed-audio-channels", "--fixed-remuxing-preference", "--fixed-remuxing-format",
        ):
            self.assertIn(option, config)
        self.assertIn('"fixed_encoding_preference": "always"', config)
        self.assertIn('"fixed_remuxing_preference": "off"', config)
        self.assertIn('**fixed_config', script)
        self.assertIn('fixed_expected_snapshot(fixed_config)', script)
        self.assertIn('fixedEncodingPreference', receiver)
        self.assertIn('fixedVideoBitrateKbps', receiver)
        self.assertIn('fixedAudioChannels', receiver)

    def test_comskip_uses_short_native_command_and_host_polled_counter_recovery(self):
        script = (SCRIPTS / "mcp_player_matrix.py").read_text(encoding="utf-8")
        self.assertIn('host_short_dev_comskip_plus_polled_av_counters', script)
        self.assertIn("'dev_comskip'", script)
        self.assertIn('wait_for_av_recovery(', script)
        self.assertNotIn('call_dict(client, "dev_run_comskip_check", {', script)


    def test_counter_reset_to_zero_is_not_recovery(self):
        previous = {
            "health_probeSupported": True,
            "health_videoMime": "video/mpeg2",
            "health_audioMime": "audio/ac3",
            "health_videoRendered": 100,
            "health_audioRendered": 50,
            "health_audioPlaybackHeadFrames": 1000,
            "health_audioSessionId": 7,
            "health_audioTrackPlayState": 3,
        }
        current = dict(previous)
        current.update({
            "health_videoRendered": 0,
            "health_audioRendered": 0,
            "health_audioPlaybackHeadFrames": 0,
        })
        healthy, details = module._av_progress(
            previous, current, video_expected_latched=True, audio_expected_latched=True
        )
        self.assertFalse(healthy)
        self.assertTrue(details["videoCounterReset"])
        self.assertTrue(details["audioCounterReset"])
        self.assertTrue(details["audioHeadReset"])
        self.assertFalse(details["videoAdvancing"])
        self.assertFalse(details["audioAdvancing"])

    def test_positive_post_reset_counter_can_prove_recovery(self):
        previous = {
            "health_probeSupported": True,
            "health_videoMime": "video/mpeg2",
            "health_audioMime": "audio/ac3",
            "health_videoRendered": 100,
            "health_audioRendered": 50,
            "health_audioPlaybackHeadFrames": 1000,
            "health_audioSessionId": 7,
            "health_audioTrackPlayState": 3,
        }
        current = dict(previous)
        current.update({
            "health_videoRendered": 1,
            "health_audioRendered": 1,
            "health_audioPlaybackHeadFrames": 1,
        })
        healthy, details = module._av_progress(
            previous, current, video_expected_latched=True, audio_expected_latched=True
        )
        self.assertTrue(healthy)
        self.assertTrue(details["videoAdvancing"])
        self.assertTrue(details["audioAdvancing"])

    def test_latched_stream_expectations_prevent_vacuous_pass(self):
        previous = {"health_probeSupported": True, "health_videoRendered": -1, "health_audioRendered": -1}
        current = dict(previous)
        healthy, details = module._av_progress(
            previous, current, video_expected_latched=True, audio_expected_latched=True
        )
        self.assertFalse(healthy)
        self.assertEqual(details["waitingFor"], ["video_counter", "audio_counter"])

    def test_seek_position_sanity_is_diagnostic_only(self):
        result = {"after": {"health_playerPositionMs": 50000}, "recoveryMs": 3000}
        sanity = module._seek_position_sanity(result, 10000, 10000)
        self.assertEqual(sanity["status"], "SUSPICIOUS")
        self.assertFalse(sanity["isVerdict"])

    def test_long_wait_crash_probes_have_expected_milestones_and_status(self):
        script = (SCRIPTS / "mcp_player_matrix.py").read_text(encoding="utf-8")
        server = (ROOT / "mcp/src/sagetv_dev_mcp/server.py").read_text(encoding="utf-8")
        self.assertEqual(module.DEFAULT_CRASH_PROBE_MILESTONES_MS, (5000, 15000, 30000))
        self.assertIn("--crash-probe-ms", script)
        self.assertIn("longWaitProbes", script)
        self.assertIn("PLAYER_CRASHED", script)
        self.assertIn("def dev_crash_probe", server)
        self.assertIn("signatureFingerprint", server)
        self.assertEqual(
            module.status_from_recovery({"crashDetected": True, "recovered": False}, 5000),
            "PLAYER_CRASHED",
        )

    def test_crash_probe_change_detects_new_relevant_signature_or_process_restart(self):
        baseline = {
            "running": True,
            "pids": ["123"],
            "crashFingerprint": "a",
            "signatureDetected": False,
            "signatureFingerprint": "",
        }
        unchanged = dict(baseline)
        self.assertFalse(module._crash_probe_changed(baseline, unchanged)["crashDetected"])

        new_signature = dict(baseline)
        new_signature.update({
            "crashFingerprint": "b",
            "signatureDetected": True,
            "signatureFingerprint": "dev-crash-1",
        })
        changed = module._crash_probe_changed(baseline, new_signature)
        self.assertTrue(changed["crashDetected"])
        self.assertTrue(changed["newCrashSignature"])

        restarted = dict(baseline)
        restarted["pids"] = ["456"]
        changed = module._crash_probe_changed(baseline, restarted)
        self.assertTrue(changed["crashDetected"])
        self.assertTrue(changed["processRestartDetected"])

    def test_unrelated_crash_buffer_change_does_not_flag_dev_crash(self):
        baseline = {
            "running": True,
            "pids": ["123"],
            "crashFingerprint": "a",
            "signatureDetected": True,
            "signatureFingerprint": "existing-dev-crash",
        }
        current = dict(baseline)
        current["crashFingerprint"] = "other-app-changed-buffer"
        changed = module._crash_probe_changed(baseline, current)
        self.assertTrue(changed["crashFingerprintChanged"])
        self.assertFalse(changed["signatureFingerprintChanged"])
        self.assertFalse(changed["crashDetected"])

    def test_startup_media_failure_is_not_counted_as_infrastructure(self):
        script = (SCRIPTS / "mcp_player_matrix.py").read_text(encoding="utf-8")
        self.assertIn("class PlaybackStartupError", script)
        self.assertIn('startupStatus"] = "STARTUP_PLAYER_CRASHED" if crashed else "STARTUP_PLAYBACK_FAILED"', script)
        self.assertIn('"startupMediaFailures": startup_media_failures', script)

    def test_android_error_traps_include_backend_error_identity(self):
        media3 = (ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/media3/Media3MediaPlayerImpl.java").read_text(encoding="utf-8")
        exo2 = (ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/exoplayer2/Exo2MediaPlayerImpl.java").read_text(encoding="utf-8")
        ijk = (ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/ijkplayer/IJKMediaPlayerImpl.java").read_text(encoding="utf-8")
        system = (ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/gsy/GSYSystemMediaPlayerImpl.java").read_text(encoding="utf-8")
        self.assertIn('player_error_" + error.getErrorCodeName()', media3)
        self.assertIn('flush_reprepare_error_', media3)
        self.assertIn('player_error_" + error.getErrorCodeName()', exo2)
        self.assertIn('flush_reprepare_error_', exo2)
        self.assertIn('player_error_" + what + "_" + extra', ijk)
        self.assertIn('player_error_" + what + "_" + extra', system)
        self.assertIn('setup_error_', system)
        self.assertIn('surface_prepare_error_', system)

    def test_exact_event_traps_are_collected_for_each_matrix_action(self):
        script = (SCRIPTS / "mcp_player_matrix.py").read_text(encoding="utf-8")
        receiver = (ROOT / "source/dev/android-tv/src/debug/java/opensagetv/vibe/miniclient/android/tv/debug/DevTestReceiver.java").read_text(encoding="utf-8")
        state = (ROOT / "source/dev/android-tv/src/debug/java/opensagetv/vibe/miniclient/android/tv/debug/DebugStateProvider.java").read_text(encoding="utf-8")
        trap = (ROOT / "source/dev/android-tv/src/debug/java/opensagetv/vibe/miniclient/android/tv/debug/PlaybackEventTraps.java").read_text(encoding="utf-8")
        media_cmd = (ROOT / "source/dev/core/src/main/java/opensagetv/vibe/miniclient/MediaCmd.java").read_text(encoding="utf-8")
        server = (ROOT / "mcp/src/sagetv_dev_mcp/server.py").read_text(encoding="utf-8")
        self.assertIn("'dev_clear_player_events'", script)
        self.assertIn("'dev_player_events'", script)
        self.assertIn('"events".equals(op)', receiver)
        self.assertIn('"events_clear".equals(op)', receiver)
        self.assertIn('videoRendered', trap)
        self.assertIn('audioHeadFrames', trap)
        self.assertIn('serverRequestedSeekMs', state)
        self.assertIn('PlaybackDebugEventBridge.recordAsyncDetailed(', media_cmd)
        self.assertIn('"server_seek_command", playa, "requestedMs=" + seekTime', media_cmd)
        self.assertIn('def dev_player_events()', server)

    def test_issue_only_known_profile_targets_current_abnormal_cases(self):
        cases = module.build_cases(
            list(module.PLAYERS),
            ["push", "pull"],
            ["hardware"],
            list(module.GSY_ENGINES),
        )
        selected, per_case = module.select_issue_cases(cases, list(module.CHECKS), module.KNOWN_ISSUE_PROFILE)
        self.assertEqual(len(selected), 9)
        self.assertEqual(sum(len(value) for value in per_case.values()), 16)
        self.assertEqual(sum(1 for value in per_case.values() if not value), 2)
        self.assertEqual(
            per_case["media3__push__hardware"],
            ["absolute_seek", "seek_forward", "seek_backward", "comskip_left"],
        )
        self.assertEqual(per_case["gsyplayer__push__hardware__gsy_system"], [])

    def test_issue_only_can_be_derived_from_previous_report(self):
        import json
        import tempfile
        report = {
            "results": {
                "media3__push__hardware": {
                    "startupStatus": "RECOVERED",
                    "checks": {
                        "absolute_seek": {"status": "WATCHDOG_EXPIRED"},
                        "pause_resume": {"status": "RECOVERED"},
                    },
                },
                "gsyplayer__pull__hardware__gsy_system": {
                    "startupStatus": "STARTUP_PLAYBACK_FAILED",
                    "checks": {},
                },
            }
        }
        with tempfile.TemporaryDirectory() as tempdir:
            path = Path(tempdir) / "matrix.json"
            path.write_text(json.dumps(report), encoding="utf-8")
            profile = module.issue_profile_from_report(path)
        self.assertEqual(profile["media3__push__hardware"], ("absolute_seek",))
        self.assertEqual(profile["gsyplayer__pull__hardware__gsy_system"], ())

    def test_issue_only_report_accepts_per_check_isolation_and_startup_failures(self):
        import json
        import tempfile
        report = {
            "results": {
                "media3__pull__hardware": {
                    "startupStatus": "PER_CHECK_ISOLATED",
                    "checks": {
                        "comskip_right": {"status": "STARTUP_PLAYBACK_FAILED"},
                        "comskip_left": {"status": "RECOVERED"},
                    },
                }
            }
        }
        with tempfile.TemporaryDirectory() as tempdir:
            path = Path(tempdir) / "matrix.json"
            path.write_text(json.dumps(report), encoding="utf-8")
            profile = module.issue_profile_from_report(path)
        self.assertEqual(profile["media3__pull__hardware"], ("comskip_right",))

    def test_issue_only_cli_is_documented_in_script(self):
        script = (SCRIPTS / "mcp_player_matrix.py").read_text(encoding="utf-8")
        self.assertIn('"--issues-only"', script)
        self.assertIn('issuesOnly', script)
        self.assertIn('issueChecksByCase', script)
        self.assertIn('startupOnlyIssue', script)
        self.assertIn('fresh_playback_per_operation', script)
        self.assertIn('checkStartups', script)
        self.assertIn('STARTUP_PLAYBACK_FAILED', script)

    def test_dev_sh_exposes_complete_player_matrix(self):
        devsh = (ROOT / "dev.sh").read_text(encoding="utf-8")
        self.assertIn('mcp-player-matrix)', devsh)
        self.assertIn('run_automated_mcp_test mcp_player_matrix.py "$@"', devsh)


if __name__ == "__main__":
    unittest.main()
