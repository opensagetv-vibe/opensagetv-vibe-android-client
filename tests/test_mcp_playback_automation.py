from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
DEV = ROOT / "source/dev"
SHARED = DEV / "android-shared/src/main"
TV = DEV / "android-tv/src"


class MCPPlaybackAutomationTests(unittest.TestCase):
    def test_debug_control_receiver_exists_only_in_debug_source_set(self):
        debug_manifest = TV / "debug/AndroidManifest.xml"
        debug_receiver = TV / "debug/java/sagex/miniclient/android/tv/debug/DevTestReceiver.java"
        main_manifest = TV / "main/AndroidManifest.xml"
        self.assertTrue(debug_manifest.exists())
        self.assertTrue(debug_receiver.exists())
        self.assertIn("org.opensagetv.miniclient.dev.DEBUG_CONTROL", debug_manifest.read_text(encoding="utf-8"))
        self.assertIn("DevTestReceiver", debug_manifest.read_text(encoding="utf-8"))
        self.assertNotIn("DevTestReceiver", main_manifest.read_text(encoding="utf-8"))

    def test_receiver_is_snapshot_based_not_callback_telemetry(self):
        text = (TV / "debug/java/sagex/miniclient/android/tv/debug/DevTestReceiver.java").read_text(encoding="utf-8")
        self.assertIn('"snapshot".equals(op)', text)
        self.assertIn('"skip_check".equals(op)', text)
        self.assertIn("currentSageTimelineMs(mediaCmd, player)", text)
        self.assertIn("MEDIACMD_GETMEDIATIME", text)
        self.assertIn("player.getBufferLeft()", text)
        self.assertIn("player.getLastFileReadPos()", text)
        self.assertIn("EventRouter.postCommand(client, command);", text)
        self.assertNotIn("PlayerTelemetry", text)
        self.assertNotIn("addListener", text)
        self.assertNotIn("setOn", text)

    def test_receiver_can_set_player_stream_decode_and_gsy_preferences(self):
        text = (TV / "debug/java/sagex/miniclient/android/tv/debug/DevTestReceiver.java").read_text(encoding="utf-8")
        self.assertIn("PrefStore.Keys.default_player", text)
        self.assertIn("AndroidPrefStore.STREAMING_MODE", text)
        self.assertIn("PrefStore.Keys.decoding_method", text)
        self.assertIn("PrefStore.Keys.gsy_player_engine", text)
        self.assertIn("appliesNextPlayback=true", text)

    def test_mcp_server_exposes_seek_automation_tools(self):
        server = (ROOT / "mcp/src/sagetv_dev_mcp/server.py").read_text(encoding="utf-8")
        for name in (
            "dev_player_state",
            "dev_set_player_config",
            "dev_search",
            "dev_open_search",
            "dev_wait_for_ime",
            "dev_type_text",
            "dev_input_text_keyboard",
            "dev_input_text_direct",
            "dev_hide_ime",
            "dev_player_control",
            "dev_seek_relative",
            "dev_skip_forward",
            "dev_skip_backward",
            "dev_comskip",
            "dev_search_text",
            "dev_sage_command",
            "dev_sage_command_sequence",
            "dev_run_seek_check",
            "dev_run_relative_seek_check",
            "dev_run_comskip_check",
            "dev_wait_for_media_position",
            "dev_seek_time",
            "dev_local_seek_absolute",
            "dev_test_checkpoint",
        ):
            self.assertIn(f"def {name}(", server)
        self.assertIn('adb.android_skip_check(', server)
        self.assertIn('commands,', server)
        self.assertIn('"measurement": "android_output_health_with_ui_timeline_context"', server)
        self.assertIn('"sageTimelineMs"', server)

    def test_mcp_seek_suite_calibrates_server_skip_intervals(self):
        script = (ROOT / "scripts/mcp_seek_suite.py").read_text(encoding="utf-8")
        self.assertIn('client, "ff", +1, args.calibrate_settle_ms', script)
        self.assertIn('client, "rew", -1, args.calibrate_settle_ms', script)
        self.assertIn('commands_for_delta(30_000, ff_ms, rew_ms)', script)
        self.assertIn('commands_for_delta(-10_000, ff_ms, rew_ms)', script)
        self.assertIn('rapid_targets = [30_000, 30_000, -10_000, 60_000, -30_000]', script)
        self.assertIn('SageTV timeline calibration:', script)
        self.assertIn('Rapid semantic expansion:', script)
        self.assertIn('--calibrate-timeline', script)
        self.assertIn('Timeline calibration skipped: PASS/FAIL is based on real video/audio output recovery', script)
        self.assertIn('rapid mixed FF/REW output-health stress', script)
        self.assertIn('dev_test_checkpoint', script)

    def test_mcp_seek_suite_allows_skip_interval_overrides(self):
        script = (ROOT / "scripts/mcp_seek_suite.py").read_text(encoding="utf-8")
        self.assertIn('--ff-ms', script)
        self.assertIn('--rew-ms', script)
        self.assertIn('--paced-delay-ms', script)
        self.assertIn('--calibration-quantum-ms', script)
        self.assertIn('--calibration-samples', script)
        self.assertIn('Optional timeline calibration: playback paused', script)
        self.assertIn('select_calibration_value', script)
        self.assertIn('all_attempts_ms', script)
        self.assertNotIn('cannot closely represent', script)

    def test_android_skip_check_returns_ui_timeline_difference(self):
        receiver = (TV / "debug/java/sagex/miniclient/android/tv/debug/DevTestReceiver.java").read_text(encoding="utf-8")
        adb = (ROOT / "mcp/src/sagetv_dev_mcp/adb.py").read_text(encoding="utf-8")
        server = (ROOT / "mcp/src/sagetv_dev_mcp/server.py").read_text(encoding="utf-8")
        self.assertIn("timelineBeforeMs", receiver)
        self.assertIn("timelineAfterMs", receiver)
        self.assertIn("timelineDeltaMs", receiver)
        self.assertIn("playbackAdjustedDeltaMs", receiver)
        self.assertIn("SageTV-MCP-SkipCheck", receiver)
        self.assertIn("def android_skip_check(", adb)
        self.assertIn('self.dev_control(\n            "skip_check"', adb)
        self.assertIn('result.get("playbackAdjustedDeltaMs"', server)


    def test_skip_check_verifies_real_video_audio_output_not_only_timeline(self):
        receiver = (TV / "debug/java/sagex/miniclient/android/tv/debug/DevTestReceiver.java").read_text(encoding="utf-8")
        probe = (TV / "debug/java/sagex/miniclient/android/tv/debug/PlaybackHealthProbe.java").read_text(encoding="utf-8")
        server = (ROOT / "mcp/src/sagetv_dev_mcp/server.py").read_text(encoding="utf-8")
        script = (ROOT / "scripts/mcp_seek_suite.py").read_text(encoding="utf-8")
        self.assertIn("healthCheckPerformed", receiver)
        self.assertIn("outputHealthy", receiver)
        self.assertIn("videoRecovered", receiver)
        self.assertIn("audioRecovered", receiver)
        self.assertIn("videoStillAdvancing", receiver)
        self.assertIn("audioStillAdvancing", receiver)
        self.assertIn("videoRecoveryFrames", receiver)
        self.assertIn("audioRecoveryHeadFrames", receiver)
        self.assertIn("healthFailureReason", receiver)
        self.assertIn("bufferingPollCount", receiver)
        self.assertIn("audioDecoderChanged", receiver)
        self.assertIn("videoDecoderReleaseDelta", receiver)
        self.assertIn('verdict = "output_health"', server)
        self.assertIn("timeline_within_tolerance", server)
        self.assertIn("WARN: timeline landed outside numeric tolerance", script)
        self.assertIn("recovery-timeout-ms", script)
        self.assertIn("verify-playback-ms", script)
        # The probe is on-demand reflection, not permanent player analytics telemetry.
        self.assertIn("decoderCounters", probe)
        self.assertIn("audioTrack", probe)
        self.assertIn("getPlaybackHeadPosition", probe)
        self.assertIn("surface.isValid()", probe)
        self.assertNotIn("addAnalyticsListener", probe)
        self.assertNotIn("addListener", probe)

    def test_player_snapshot_includes_decoder_surface_and_audio_health(self):
        probe = (TV / "debug/java/sagex/miniclient/android/tv/debug/PlaybackHealthProbe.java").read_text(encoding="utf-8")
        server = (ROOT / "mcp/src/sagetv_dev_mcp/server.py").read_text(encoding="utf-8")
        for key in (
            "videoDecoder", "videoDecoderKind", "videoRendered", "videoDropped",
            "audioDecoder", "audioDecoderKind", "audioRendered", "audioTrackState", "audioTrackPlayState", "audioPlaybackHeadFrames",
            "surfaceValid", "bufferedPositionMs", "playerError", "dataSourceClass", "videoDecoderInitCount", "audioDecoderInitCount",
        ):
            self.assertIn(key, probe)
        self.assertIn('"health_videoDecoder"', server)
        self.assertIn('"health_audioPlaybackHeadFrames"', server)
        self.assertIn('"health_surfaceValid"', server)

    def test_comskip_uses_direct_sagetv_right_left_and_records_recovery_landing(self):
        receiver = (TV / "debug/java/sagex/miniclient/android/tv/debug/DevTestReceiver.java").read_text(encoding="utf-8")
        adb = (ROOT / "mcp/src/sagetv_dev_mcp/adb.py").read_text(encoding="utf-8")
        server = (ROOT / "mcp/src/sagetv_dev_mcp/server.py").read_text(encoding="utf-8")
        script = (ROOT / "scripts/mcp_comskip_test.py").read_text(encoding="utf-8")
        devsh = (ROOT / "dev.sh").read_text(encoding="utf-8")
        # Dedicated debug Comskip path keeps real A/V recovery instrumentation but never injects an Android key.
        self.assertIn('"comskip".equals(op)', receiver)
        self.assertIn('"comskip_check".equals(op)', receiver)
        self.assertIn('comskipDirect(Context context, Intent intent)', receiver)
        self.assertIn('EventRouter.postCommand(client, command);', receiver)
        self.assertNotIn('MediaMappingPreferences videoMappings', receiver)
        self.assertIn('videoRecoveryTimelineMs', receiver)
        self.assertIn('audioRecoveryTimelineMs', receiver)
        self.assertIn('outputRecoveryTimelineMs', receiver)
        self.assertIn('landingTimelineMs', receiver)
        self.assertIn('def android_comskip_check(', adb)
        self.assertIn('self.dev_control(\n            "comskip_check"', adb)
        self.assertIn('direction=normalized', adb)
        self.assertIn('"directSageCommand": normalized', adb)
        self.assertIn('"inputPath": "android_debug_direct_sage_event"', adb)
        self.assertIn("debug APK's dedicated Comskip operation", server)
        self.assertIn('def dev_run_comskip_check(', server)
        self.assertIn('observation_complete_with_separate_media_recovery_outcome', server)
        self.assertIn('MCP COMSKIP HEALTH SUITE', script)
        self.assertIn('debug-direct Comskip:', script)
        self.assertIn('mcp-comskip-test', devsh)

    def test_debug_direct_automation_controls_bypass_android_key_injection(self):
        receiver = (TV / "debug/java/sagex/miniclient/android/tv/debug/DevTestReceiver.java").read_text(encoding="utf-8")
        lifecycle = (SHARED / "java/sagex/miniclient/android/UIActivityLifeCycleHandler.java").read_text(encoding="utf-8")
        adb = (ROOT / "mcp/src/sagetv_dev_mcp/adb.py").read_text(encoding="utf-8")
        server = (ROOT / "mcp/src/sagetv_dev_mcp/server.py").read_text(encoding="utf-8")
        self.assertNotIn('"input_text".equals(op)', receiver)
        self.assertNotIn('inputTextDirect(', receiver)
        self.assertNotIn('postKeyEvent((int) ch, 0, ch)', receiver)
        self.assertNotIn('inputTextViaAndroidKeyboardForDebug(', lifecycle)
        self.assertNotIn('KeyCharacterMap.VIRTUAL_KEYBOARD', lifecycle)
        self.assertNotIn('target.dispatchKeyEvent(event)', lifecycle)
        self.assertIn('"player_control".equals(op)', receiver)
        self.assertIn('player.play();', receiver)
        self.assertIn('player.pause();', receiver)
        self.assertIn('player.stop();', receiver)
        self.assertIn('"seek_relative".equals(op)', receiver)
        self.assertIn('currentPlayerPositionMs(MediaCmd mediaCmd, MiniPlayerPlugin player)', receiver)
        self.assertIn('requestedTargetPlayerMs', receiver)
        self.assertNotIn('requestedTargetMs = beforeTimelineMs', receiver)
        self.assertIn('player.seek(targetMs);', receiver)
        self.assertIn('"relative_seek_check".equals(op)', receiver)
        self.assertIn('hideImeForDebug()', lifecycle)
        self.assertIn('def native_input_text(', adb)
        self.assertIn('self.dev_control("input_text_native", text=value)', adb)
        self.assertIn('def keyboard_input_text(', adb)
        self.assertIn('def direct_input_text(', adb)
        self.assertIn('def android_relative_seek_check(', adb)
        self.assertIn('def dev_run_relative_seek_check(', server)
        self.assertIn('android_debug_direct_player_relative_seek', server)

    def test_failure_checkpoint_captures_audio_and_crash_diagnostics(self):
        adb = (ROOT / "mcp/src/sagetv_dev_mcp/adb.py").read_text(encoding="utf-8")
        server = (ROOT / "mcp/src/sagetv_dev_mcp/server.py").read_text(encoding="utf-8")
        self.assertIn("def dumpsys_audio", adb)
        self.assertIn("def crash_log", adb)
        self.assertIn('"audio": Path(str(base) + "_audio.txt")', server)
        self.assertIn('"crash": Path(str(base) + "_crash.txt")', server)
        self.assertIn('"state": Path(str(base) + "_state.json")', server)


    def test_long_health_broadcasts_are_not_marked_foreground_and_audio_head_reset_is_supported(self):
        adb = (ROOT / "mcp/src/sagetv_dev_mcp/adb.py").read_text(encoding="utf-8")
        probe = (TV / "debug/java/sagex/miniclient/android/tv/debug/PlaybackHealthProbe.java").read_text(encoding="utf-8")
        receiver = (TV / "debug/java/sagex/miniclient/android/tv/debug/DevTestReceiver.java").read_text(encoding="utf-8")
        script = (ROOT / "scripts/mcp_seek_suite.py").read_text(encoding="utf-8")
        self.assertIn("foreground: bool = True", adb)
        self.assertIn('"skip_check",\n            foreground=False,', adb)
        self.assertIn('"comskip_check",\n            foreground=False,', adb)
        self.assertIn("audioHeadResetDetected", probe)
        self.assertIn("same AudioTrack/session", probe)
        self.assertIn("audioHeadResetDuringRecovery", receiver)
        self.assertIn("SKIP: rapid output-health stress", script)


    def test_session_control_connect_ui_start_and_exit_tools(self):
        receiver = (TV / "debug/java/sagex/miniclient/android/tv/debug/DevTestReceiver.java").read_text(encoding="utf-8")
        adb = (ROOT / "mcp/src/sagetv_dev_mcp/adb.py").read_text(encoding="utf-8")
        server = (ROOT / "mcp/src/sagetv_dev_mcp/server.py").read_text(encoding="utf-8")
        script = (ROOT / "scripts/mcp_session_test.py").read_text(encoding="utf-8")
        devsh = (ROOT / "dev.sh").read_text(encoding="utf-8")
        self.assertIn('"connect".equals(op)', receiver)
        self.assertIn('"exit".equals(op)', receiver)
        self.assertIn('getLastConnectedServer()', receiver)
        self.assertIn('Intent.FLAG_ACTIVITY_NEW_TASK', receiver)
        self.assertIn('client.closeConnection()', receiver)
        self.assertIn('menuName', receiver)
        self.assertIn('popupName', receiver)
        self.assertIn('hasTextInput', receiver)
        self.assertIn('def connect_server(', adb)
        self.assertIn('def exit_session(', adb)
        self.assertIn('def dev_connect_server(', server)
        self.assertIn('def dev_exit_session(', server)
        self.assertIn('def dev_wait_for_ui(', server)
        self.assertIn('menu_present: bool | None = None', server)
        self.assertIn('stable_ms: int = 0', server)
        self.assertIn('def dev_wait_for_playback_started(', server)
        self.assertIn('def dev_play_video(', server)
        self.assertIn('SagexApiClient.discover(server_address)', server)
        self.assertIn('sagex.resolve_context(client_id)', server)
        self.assertIn('sagex.find_media(requested)', server)
        self.assertIn('sagex.watch(context, match.media_file_id)', server)
        self.assertIn('clientId', receiver)
        self.assertIn('uiContextHint', receiver)
        self.assertIn('STEP: apply playback settings before connection/video start', script)
        self.assertIn('STEP: connect SageTV server', script)
        self.assertIn('STEP: play video by name on this MiniClient', script)
        self.assertIn('--video-name', script)
        self.assertNotIn('--nav', script)
        self.assertIn('--exit', script)
        self.assertIn('mcp-session-test', devsh)


    def test_native_end_to_end_playback_start_test_uses_explicit_server_and_exact_sequence(self):
        script = (ROOT / "scripts/mcp_playback_test.py").read_text(encoding="utf-8")
        devsh = (ROOT / "dev.sh").read_text(encoding="utf-8")
        self.assertIn('default="192.168.10.175"', script)
        self.assertIn('default="media3"', script)
        self.assertIn('default="push"', script)
        self.assertIn('default="hardware"', script)
        self.assertIn('"save": False', script)
        self.assertIn('call_dict(client, "dev_connect_server"', script)
        self.assertIn('def start_recording_via_search(', script)
        self.assertIn('call_dict(client, "dev_open_search"', script)
        self.assertIn('"require_ime": False', script)
        self.assertIn('"suppress_ime": True', script)
        self.assertIn('call_dict(client, "dev_type_text"', script)
        self.assertIn('call_dict(client, "dev_hide_ime"', script)
        self.assertIn('"ime_not_visible"', script)
        self.assertIn('call_dict(\n                client, "dev_set_ime_suppression"', script)
        self.assertIn('textEntryVerification', script)
        self.assertNotIn('call_dict(client, "dev_sage_command_sequence"', script)
        self.assertIn('for command in ("ff", "right", "play_pause", "down", "select"):', script)
        self.assertIn('call_dict(client, "dev_sage_command"', script)
        self.assertIn('call_dict(client, "dev_wait_for_playback_started"', script)
        self.assertIn('"automation_ready": True', script)
        self.assertIn('"stable_ms": args.ui_stable_ms', script)
        self.assertIn('--ui-stable-ms', script)
        self.assertNotIn('call_dict(client, "dev_send_sequence", {"sequence": sequence}', script)
        self.assertNotIn('dev_play_video', script)
        self.assertNotIn('SagexApiClient', script)
        self.assertIn('mcp-playback-test)', devsh)

    def test_session_start_verifies_real_output_when_probe_supported(self):
        server = (ROOT / "mcp/src/sagetv_dev_mcp/server.py").read_text(encoding="utf-8")
        self.assertIn('def _playback_health_from_pair(', server)
        self.assertIn('health_videoRendered', server)
        self.assertIn('health_audioPlaybackHeadFrames', server)
        self.assertIn('health_surfaceValid', server)
        self.assertIn('health_isPlaying', server)
        self.assertIn('def _wait_for_playback(', server)

    def test_streaming_label_is_push_without_changing_persisted_value(self):
        arrays = (SHARED / "res/values/arrays.xml").read_text(encoding="utf-8")
        pref_store = (SHARED / "java/sagex/miniclient/android/prefs/AndroidPrefStore.java").read_text(encoding="utf-8")
        self.assertIn("<item>Push</item>", arrays)
        self.assertIn("<item>dynamic</item>", arrays)
        self.assertIn('STREAMING_MODE_DEFAULT = "dynamic";', pref_store)

    def test_main_settings_refresh_player_summaries_on_resume(self):
        settings = (SHARED / "java/sagex/miniclient/android/ui/settings/SettingsFragment.java").read_text(encoding="utf-8")
        strings = (SHARED / "res/values/strings.xml").read_text(encoding="utf-8")
        self.assertIn("refreshPlayerPreferenceSummaries();", settings)
        self.assertIn("public void onResume()", settings)
        self.assertIn("updateListSummary(decodingMethod, R.string.summary_list_decoding_method", settings)
        self.assertIn("Current: %s.", strings)


    def test_debug_readiness_and_clean_start_controls_exist(self):
        root = Path(__file__).resolve().parents[1]
        server = (root / "mcp/src/sagetv_dev_mcp/server.py").read_text(encoding="utf-8")
        adb = (root / "mcp/src/sagetv_dev_mcp/adb.py").read_text(encoding="utf-8")
        receiver = (root / "source/dev/android-tv/src/debug/java/sagex/miniclient/android/tv/debug/DevTestReceiver.java").read_text(encoding="utf-8")
        matrix = (root / "scripts/mcp_media3_matrix.py").read_text(encoding="utf-8")
        self.assertIn("def firetv_wake()", server)
        self.assertIn("def adb_session_status()", server)
        self.assertIn("persistent_shell_status", adb)
        self.assertIn("_persistent_shell_command", adb)
        self.assertIn("def dev_app_status()", server)
        self.assertIn("def kill_dev_app()", server)
        self.assertIn("def dev_prepare_clean_start(", server)
        self.assertIn("automation_ready: bool | None = None", server)
        self.assertIn("def prepare_clean_start", adb)
        self.assertIn('out.append(";uiState=")', receiver)
        self.assertIn('out.append(";automationReady=")', receiver)
        self.assertIn('out.append(";imeRequested=")', receiver)
        self.assertIn('out.append(";imeVisibleKnown=")', receiver)
        self.assertIn('out.append(";imeVisible=")', receiver)
        self.assertIn('out.append(";debugStatusVersion=14")', receiver)
        self.assertIn('out.append(";maxRecoveryWatchdogMs=")', receiver)
        ui_handler = (root / "source/dev/android-shared/src/main/java/sagex/miniclient/android/UIActivityLifeCycleHandler.java").read_text(encoding="utf-8")
        self.assertIn("getImeVisibilityForDebug()", ui_handler)
        self.assertIn("isKeyboardRequestedForDebug()", ui_handler)
        self.assertIn("setKeyboardSuppressedForDebug(boolean suppressed)", ui_handler)
        self.assertIn("isKeyboardSuppressedForDebug()", ui_handler)
        self.assertIn("visible && debugKeyboardSuppressed", ui_handler)
        self.assertIn("WindowInsets.Type.ime()", ui_handler)
        self.assertIn("imeSuppressedForDebug", server)
        self.assertIn("imeVisibleKnown", server)
        self.assertIn("imeVisible", server)
        self.assertIn("private String configuredValues(PrefStore prefs)", receiver)
        self.assertIn("PlaybackHealthProbe.Snapshot health = PlaybackHealthProbe.capture(player);", receiver)
        self.assertIn('out.append(health.compactWire("health_"));', receiver)
        self.assertNotIn("appendPlaybackHealth(out, player);", receiver)
        self.assertIn('"dev_prepare_clean_start"', matrix)
        self.assertIn('"automation_ready": True', matrix)
        self.assertIn('{"command": "home"}', matrix)
        # Deterministic automation must direct-connect into the MiniClient UI; the generic
        # launcher opens ServersActivity and is reserved for manual MCP use.
        self.assertNotIn('"launch_dev_app"', matrix)
        playback = (root / "scripts/mcp_playback_test.py").read_text(encoding="utf-8")
        session = (root / "scripts/mcp_session_test.py").read_text(encoding="utf-8")
        self.assertNotIn('"launch_dev_app"', playback)
        self.assertNotIn('"launch_dev_app"', session)
        self.assertIn('"dev_connect_server"', matrix)
        self.assertIn('"dev_app_status"', matrix)


    def test_generic_debug_seek_time_uses_av_output_counters_for_recovery(self):
        receiver = (ROOT / "source/dev/android-tv/src/debug/java/sagex/miniclient/android/tv/debug/DevTestReceiver.java").read_text(encoding="utf-8")
        server = (ROOT / "mcp/src/sagetv_dev_mcp/server.py").read_text(encoding="utf-8")
        self.assertIn('"seek_time".equals(op)', receiver)
        self.assertIn('target_ms is required', receiver)
        self.assertIn('player.seek(targetMs)', receiver)
        self.assertIn('def dev_seek_time(target_ms: int', server)
        self.assertIn('adb.seek_time(target_ms)', server)
        self.assertIn('_wait_for_playback(timeout_s=timeout_s, verify_ms=stable_ms)', server)
        self.assertIn('videoStillAdvancing', server)
        self.assertIn('audioStillAdvancing', server)
        self.assertIn('android_debug_seek_output_counter_recovery', server)
        self.assertIn('video_audio_output_counters_advancing_position_diagnostic_only', server)
        self.assertNotIn('position_snapped_away_from_target', server)
        self.assertNotIn('ready_but_outside_target_tolerance', server)



    def test_native_text_automation_matches_miniclient_keymap_encoding(self):
        receiver = (ROOT / "source/dev/android-tv/src/debug/java/sagex/miniclient/android/tv/debug/DevTestReceiver.java").read_text(encoding="utf-8")
        adb = (ROOT / "mcp/src/sagetv_dev_mcp/adb.py").read_text(encoding="utf-8")
        server = (ROOT / "mcp/src/sagetv_dev_mcp/server.py").read_text(encoding="utf-8")
        script = (ROOT / "scripts/mcp_playback_test.py").read_text(encoding="utf-8")
        keymap = (ROOT / "source/dev/android-shared/src/main/java/sagex/miniclient/android/ui/keymaps/KeyMapProcessor.java").read_text(encoding="utf-8")
        self.assertIn('"input_text_native".equals(op)', receiver)
        self.assertIn('private String inputTextNative(', receiver)
        self.assertIn('keyCode = Character.toUpperCase(ch);', receiver)
        self.assertIn('modifiers = Keys.SHIFT_MASK;', receiver)
        self.assertIn('keyCode = Keys.VK_SPACE;', receiver)
        self.assertIn('postKeyEvent(keyCode, modifiers, ch)', receiver)
        self.assertIn('postKeyEvent(toSend, androidToSageKeyModifier(event), (char) event.getUnicodeChar())', keymap)
        self.assertIn('def native_input_text(', adb)
        self.assertIn('self.dev_control("input_text_native", text=value)', adb)
        self.assertIn('def dev_input_text_native(', server)
        self.assertIn('def dev_set_ime_suppression(', server)
        self.assertIn('self.dev_control("ime_suppress"', adb)
        self.assertIn('"ime_suppress".equals(op)', receiver)
        self.assertIn('native = adb.native_input_text(requested)', server)
        self.assertIn('"miniclient_native_key_event"', server)
        self.assertIn('call_dict(client, "dev_open_search"', script)
        self.assertIn('"require_ime": False', script)
        self.assertIn('"suppress_ime": True', script)
        self.assertIn('call_dict(client, "dev_type_text"', script)
        self.assertIn('call_dict(client, "dev_hide_ime"', script)
        self.assertIn('"ime_not_visible"', script)
        self.assertIn('call_dict(\n                client, "dev_set_ime_suppression"', script)
        self.assertIn('deferred_until_recording_playback_starts', script)
        self.assertIn('--text-char-delay-ms', script)
        self.assertIn('default=0', script)
        self.assertIn('def dev_open_search(', server)
        self.assertIn('def dev_wait_for_ime(', server)



if __name__ == "__main__":
    unittest.main()


class MCPSearchCommandTests(unittest.TestCase):

    def test_explicit_multiline_mcp_send_sequence_exists(self):
        server = (ROOT / "mcp/src/sagetv_dev_mcp/server.py").read_text(encoding="utf-8")
        devsh = (ROOT / "dev.sh").read_text(encoding="utf-8")
        script = (ROOT / "scripts/mcp_send_sequence.py").read_text(encoding="utf-8")
        self.assertIn("def dev_send_sequence(sequence: str)", server)
        self.assertIn("parse_sequence_script(sequence)", server)
        self.assertIn('item.action == "command"', server)
        self.assertIn('item.action == "sendkey"', server)
        self.assertIn('item.action == "sendtext"', server)
        self.assertIn('item.action == "delay"', server)
        self.assertIn('item.action == "waittextinput"', server)
        self.assertIn('item.action in {"waitimevisible", "waitimehidden"}', server)
        self.assertIn('imeVisibleKnown', server)
        self.assertIn('imeVisible', server)
        self.assertIn('hasTextInput', server)
        self.assertIn("mcp-send-sequence)", devsh)
        self.assertIn('tool_call(\n            client,\n            "dev_send_sequence"', script)

    def test_search_is_first_class_mcp_tool_and_host_command(self):
        server = (ROOT / "mcp/src/sagetv_dev_mcp/server.py").read_text(encoding="utf-8")
        devsh = (ROOT / "dev.sh").read_text(encoding="utf-8")
        script = (ROOT / "scripts/mcp_search_test.py").read_text(encoding="utf-8")
        self.assertIn("def dev_search()", server)
        self.assertIn("def dev_type_text(", server)
        self.assertIn("def dev_search_text(", server)
        self.assertIn('adb.sage_command("search")', server)
        self.assertIn('def dev_input_text_keyboard(', server)
        self.assertIn('adb.keyboard_input_text(requested)', server)
        self.assertIn('adb.sage_command("select")', server)
        self.assertIn('submit: bool = False', server)
        self.assertIn('dismiss_keyboard: bool = True', server)
        self.assertIn('keyboard_dismiss_result = adb.hide_ime()', server)
        self.assertIn("mcp-search-test)", devsh)
        self.assertIn('tool_call(client, "adb_connect"', script)
        self.assertIn('"dev_search_text"', script)
        self.assertIn('parser.add_argument("--text", required=True', script)
        self.assertNotIn('default="meet the press"', script)
        self.assertIn('def dev_search_text(\n    text: str,', server)
        self.assertNotIn('text: str = "meet the press"', server)
        self.assertIn('default="down,right,right,right,play_pause,play_pause"', script)
        self.assertIn('post_commands', server)
        self.assertIn('post_keys', server)
        self.assertIn('play_pause', server)
        self.assertIn('adb.sage_command(command)', server)
        self.assertIn('adb.key_sequence(keys', server)

