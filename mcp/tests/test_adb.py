from pathlib import Path
import subprocess
import tempfile
import sys
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))
from sagetv_dev_mcp.adb import AdbClient, parse_broadcast_result, parse_telemetry_line


class AdbSafetyTests(unittest.TestCase):

    def test_persistent_shell_reuses_one_adb_shell_process(self):
        with tempfile.TemporaryDirectory() as td:
            fake_adb = Path(td) / "adb"
            fake_adb.write_text(
                """#!/bin/sh
if [ "$1" = "connect" ]; then echo connected; exit 0; fi
if [ "$1" = "devices" ]; then echo 'List of devices attached'; exit 0; fi
if [ "$1" = "-s" ] && [ "$3" = "shell" ]; then exec /bin/sh; fi
exit 2
""",
                encoding="utf-8",
            )
            fake_adb.chmod(0o755)
            c = AdbClient("1.2.3.4:5555", "org.opensagetv.miniclient.dev.debug", adb=str(fake_adb))
            try:
                self.assertEqual(c.connect(), "connected")
                first = c.persistent_shell_status()
                self.assertTrue(first["persistentShell"])
                self.assertEqual(first["persistentShellRestarts"], 1)
                pid = first["persistentShellPid"]
                self.assertEqual(c.shell("printf first"), "first")
                self.assertEqual(c.shell("printf second"), "second")
                after = c.persistent_shell_status()
                self.assertEqual(after["persistentShellPid"], pid)
                self.assertEqual(after["persistentShellRestarts"], 1)
                self.assertEqual(after["persistentShellCommands"], 2)
            finally:
                c.close()
            self.assertFalse(c.persistent_shell_status()["persistentShell"])

    def test_refuses_production_namespace(self):
        c = AdbClient("1.2.3.4:5555", "jvl.sage.miniclient.android.tv.debug")
        with self.assertRaises(PermissionError):
            c._ensure_dev_package("jvl.sage.miniclient.android.tv.debug")

    def test_refuses_wrong_package(self):
        c = AdbClient("1.2.3.4:5555", "org.opensagetv.miniclient.dev.debug")
        with self.assertRaises(PermissionError):
            c._ensure_dev_package("com.example.other")

    def test_key_alias(self):
        c = AdbClient("1.2.3.4:5555", "org.opensagetv.miniclient.dev.debug")
        with patch.object(c, "shell", return_value="") as shell:
            c.key("ff")
            shell.assert_called_once_with("input keyevent KEYCODE_MEDIA_FAST_FORWARD")

    def test_input_text_encodes_spaces_and_next_uses_enter(self):
        c = AdbClient("1.2.3.4:5555", "org.opensagetv.miniclient.dev.debug")
        with patch.object(c, "shell", return_value="") as shell:
            c.input_text("meet the press")
            c.key("next")
        self.assertEqual(shell.call_args_list[0].args[0], "input text meet%sthe%spress")
        self.assertEqual(shell.call_args_list[1].args[0], "input keyevent KEYCODE_ENTER")

    def test_input_text_paced_uses_one_device_shell_with_device_side_delays(self):
        c = AdbClient("1.2.3.4:5555", "org.opensagetv.miniclient.dev.debug")
        with patch.object(c, "shell", return_value="") as shell:
            result = c.input_text_paced("a b", char_delay_ms=10)
        shell.assert_called_once()
        command = shell.call_args.args[0]
        self.assertEqual(
            command,
            "input text a; sleep 0.010; input keyevent KEYCODE_SPACE; sleep 0.010; input text b",
        )
        self.assertGreaterEqual(shell.call_args.kwargs["timeout"], 30.0)
        self.assertEqual(result["charsIssued"], 3)
        self.assertEqual(result["charDelayMs"], 10)
        self.assertEqual(result["inputPath"], "android_os_input_text_paced_single_shell")
        self.assertIn("effectiveCharMs", result)



    def test_wake_sends_home_after_wakeup(self):
        c = AdbClient("1.2.3.4:5555", "org.opensagetv.miniclient.dev.debug")
        replies = [
            "mWakefulness=Dreaming\nDisplay Power: state=ON\n",
            "",
            "",
            "",
            "mWakefulness=Awake\nDisplay Power: state=ON\n",
        ]
        with patch.object(c, "shell", side_effect=replies) as shell, patch("time.sleep"):
            result = c.wake()
        commands = [call.args[0] for call in shell.call_args_list]
        self.assertIn("input keyevent KEYCODE_WAKEUP", commands)
        self.assertIn("input keyevent KEYCODE_HOME", commands)
        self.assertLess(commands.index("input keyevent KEYCODE_WAKEUP"), commands.index("input keyevent KEYCODE_HOME"))
        self.assertEqual(result["homeKey"], "KEYCODE_HOME")

    def test_launch_wakes_and_uses_resolved_activity_before_monkey(self):
        c = AdbClient("1.2.3.4:5555", "org.opensagetv.miniclient.dev.debug")
        component = "org.opensagetv.miniclient.dev.debug/sagex.miniclient.android.tv.MainActivity"
        with patch.object(c, "wake", return_value={"wakeKey": "KEYCODE_WAKEUP", "homeKey": "KEYCODE_HOME"}) as wake, \
             patch.object(c, "shell", side_effect=[component + "\n", "Starting: Intent {...}\nStatus: ok\n"]) as shell:
            result = c.launch()
        wake.assert_called_once_with()
        self.assertIn("Status: ok", result)
        self.assertIn("cmd package resolve-activity --brief", shell.call_args_list[0].args[0])
        self.assertEqual(shell.call_args_list[1].args[0], f"am start -W -n {component}")
        self.assertFalse(any("monkey -p" in call.args[0] for call in shell.call_args_list))

    def test_app_status_does_not_launch_package(self):
        c = AdbClient("1.2.3.4:5555", "org.opensagetv.miniclient.dev.debug")
        replies = [
            "1234\n",
            "mResumedActivity: ActivityRecord{ x org.opensagetv.miniclient.dev.debug/.MainActivity }\n",
        ]
        with patch.object(c, "shell", side_effect=replies) as shell:
            status = c.app_status()
        self.assertTrue(status["running"])
        self.assertTrue(status["foreground"])
        self.assertEqual(status["pids"], ["1234"])
        self.assertEqual(shell.call_args_list[0].args[0], f"pidof {c.dev_package}")


    def test_app_status_treats_resumed_dev_activity_as_running_when_pidof_is_empty(self):
        c = AdbClient("1.2.3.4:5555", "org.opensagetv.miniclient.dev.debug")
        replies = [
            "",
            "USER PID PPID NAME\n",
            "mResumedActivity: ActivityRecord{ x org.opensagetv.miniclient.dev.debug/sagex.miniclient.android.phone.ServersActivity }\n",
        ]
        with patch.object(c, "shell", side_effect=replies):
            status = c.app_status()
        self.assertTrue(status["running"])
        self.assertTrue(status["foreground"])
        self.assertEqual(status["runningSource"], "resumedActivity")
        self.assertEqual(status["pids"], [])

    def test_app_status_falls_back_to_ps_for_background_dev_process(self):
        c = AdbClient("1.2.3.4:5555", "org.opensagetv.miniclient.dev.debug")
        replies = [
            "",
            "u0_a123 7457 1 0 0 org.opensagetv.miniclient.dev.debug\n",
            "mResumedActivity: ActivityRecord{ x com.amazon.tv.launcher/.ui.HomeActivity_vNext }\n",
        ]
        with patch.object(c, "shell", side_effect=replies):
            status = c.app_status()
        self.assertTrue(status["running"])
        self.assertFalse(status["foreground"])
        self.assertEqual(status["runningSource"], "ps")
        self.assertEqual(status["pids"], ["7457"])

    def test_prepare_clean_start_force_stops_only_if_graceful_exit_leaves_process(self):
        c = AdbClient("1.2.3.4:5555", "org.opensagetv.miniclient.dev.debug")
        statuses = [
            {"running": True},
            {"running": True},
            {"running": True},
            {"running": False},
        ]
        with patch.object(c, "app_status", side_effect=statuses), \
             patch.object(c, "request_graceful_exit", return_value={"requested": True}), \
             patch.object(c, "force_stop", return_value=""), \
             patch.object(c, "wake", return_value={"wakeKey": "KEYCODE_WAKEUP"}):
            result = c.prepare_clean_start(wake=True, graceful_timeout_s=0.25)
        self.assertTrue(result["forceStopUsed"])
        self.assertFalse(result["stopped"]["running"])
        self.assertTrue(result["readyToLaunch"])

    def test_install_dev_apk_uninstalls_before_clean_install(self):
        c = AdbClient("1.2.3.4:5555", "org.opensagetv.miniclient.dev.debug")
        with tempfile.TemporaryDirectory() as td:
            apk = Path(td) / "dev.apk"
            apk.write_bytes(b"apk")
            calls = []

            def fake_run(args, **kwargs):
                args = list(args)
                calls.append(args)
                if args[0] == "uninstall":
                    return subprocess.CompletedProcess(args, 0, stdout="Success\n", stderr="")
                if args[0] == "install":
                    return subprocess.CompletedProcess(args, 0, stdout="Success\n", stderr="")
                raise AssertionError(args)

            with patch.object(c, "detect_apk_package", return_value="org.opensagetv.miniclient.dev.debug"), \
                 patch.object(c, "run", side_effect=fake_run):
                result = c.install_dev_apk(apk)

            self.assertEqual(calls[0], ["uninstall", "org.opensagetv.miniclient.dev.debug"])
            self.assertEqual(calls[1], ["install", str(apk)])
            self.assertNotIn("-r", calls[1])
            self.assertIn("uninstall: Success", result)
            self.assertIn("install: Success", result)



    def test_client_id_debug_controls_use_explicit_receiver_ops(self):
        c = AdbClient("1.2.3.4:5555", "org.opensagetv.miniclient.dev.debug")
        with patch.object(c, "dev_control", side_effect=[
            {"ok": True, "op": "client_id_get", "configuredClientId": "44:45:56:30:30:31"},
            {"ok": True, "op": "client_id_set", "configuredClientId": "44:45:56:30:30:31"},
        ]) as control:
            shown = c.get_client_id()
            changed = c.set_client_id(value="DEV001")
        self.assertEqual(shown["configuredClientId"], "44:45:56:30:30:31")
        self.assertEqual(changed["configuredClientId"], "44:45:56:30:30:31")
        self.assertEqual(control.call_args_list[0].args[0], "client_id_get")
        self.assertEqual(control.call_args_list[1].args[0], "client_id_set")
        self.assertEqual(control.call_args_list[1].kwargs["value"], "DEV001")
        self.assertEqual(control.call_args_list[1].kwargs["generate"], "false")

    def test_parse_debug_broadcast_snapshot(self):
        text = 'Broadcasting: Intent { act=org.opensagetv.miniclient.dev.DEBUG_CONTROL }\nBroadcast completed: result=1, data="ok=true;op=snapshot;connected=true;player=media3;state=2;mediaTimeMs=123456"\n'
        result = parse_broadcast_result(text)
        self.assertTrue(result["ok"])
        self.assertTrue(result["connected"])
        self.assertEqual(result["player"], "media3")
        self.assertEqual(result["state"], 2)
        self.assertEqual(result["mediaTimeMs"], 123456)

    def test_debug_control_builds_explicit_dev_only_broadcast(self):
        c = AdbClient("1.2.3.4:5555", "org.opensagetv.miniclient.dev.debug")
        output = 'Broadcast completed: result=1, data="ok=true;op=config;player=media3;streaming=pull;decoding=hardware;gsyEngine=auto"\n'
        with patch.object(c, "shell", return_value=output) as shell:
            result = c.set_player_config(player="media3", streaming="pull", decoding="hardware", gsy_engine="auto")
        command = shell.call_args.args[0]
        self.assertIn("org.opensagetv.miniclient.dev.DEBUG_CONTROL", command)
        self.assertIn("org.opensagetv.miniclient.dev.debug/sagex.miniclient.android.tv.debug.DevTestReceiver", command)
        self.assertIn("--es player media3", command)
        self.assertIn("--es streaming pull", command)
        self.assertEqual(result["player"], "media3")



    def test_set_player_config_passes_complete_fixed_encoding_block(self):
        c = AdbClient("1.2.3.4:5555", "org.opensagetv.miniclient.dev.debug")
        output = ('Broadcast completed: result=1, data="ok=true;op=config;player=media3;streaming=fixed;decoding=hardware;'
                  'gsyEngine=auto;fixedEncodingPreference=always;fixedEncodingFormat=matroska;fixedVideoBitrateKbps=6000;'
                  'fixedVideoFps=SOURCE;fixedKeyFrameInterval=10;fixedUseBFrames=true;fixedVideoResolution=720;'
                  'fixedAudioCodec=ac3;fixedAudioBitrateKbps=192;fixedAudioChannels=6;fixedRemuxingPreference=off;'
                  'fixedRemuxingFormat=matroska"\n')
        with patch.object(c, "shell", return_value=output) as shell:
            result = c.set_player_config(
                player="media3", streaming="fixed", decoding="hardware", gsy_engine="auto",
                fixed_encoding_preference="always", fixed_encoding_format="matroska",
                fixed_video_bitrate_kbps=6000, fixed_video_fps="source",
                fixed_key_frame_interval=10, fixed_use_b_frames=True, fixed_video_resolution="720",
                fixed_audio_codec="ac3", fixed_audio_bitrate_kbps=192, fixed_audio_channels="6",
                fixed_remuxing_preference="off", fixed_remuxing_format="matroska",
            )
        command = shell.call_args.args[0]
        for expected in (
            "--es streaming fixed", "--es fixed_encoding_preference always",
            "--es fixed_encoding_format matroska", "--es fixed_video_bitrate_kbps 6000",
            "--es fixed_video_fps source", "--es fixed_key_frame_interval 10",
            "--es fixed_use_b_frames true", "--es fixed_video_resolution 720",
            "--es fixed_audio_codec ac3", "--es fixed_audio_bitrate_kbps 192",
            "--es fixed_audio_channels 6", "--es fixed_remuxing_preference off",
            "--es fixed_remuxing_format matroska",
        ):
            self.assertIn(expected, command)
        self.assertEqual(result["fixedVideoBitrateKbps"], 6000)
        self.assertEqual(result["fixedRemuxingPreference"], "off")


    def test_player_tuning_builds_debug_only_broadcast(self):
        c = AdbClient("1.2.3.4:5555", "org.opensagetv.miniclient.dev.debug")
        output = ('Broadcast completed: result=1, data="ok=true;op=tuning;tuningActive=true;'
                  'tuningMedia3TsSearchMultiplier=4;tuningMedia3PullReadKb=512;'
                  'tuningMedia3SeekPolicy=next;tuningMedia3CodecMode=async;appliesNextPlayback=true"\n')
        with patch.object(c, "shell", return_value=output) as shell:
            result = c.set_player_tuning(
                reset=True, media3_ts_search_multiplier=4, media3_pull_read_kb=512,
                media3_seek_policy="next", media3_codec_mode="async",
                media3_seek_recovery_enabled=False,
            )
        command = shell.call_args.args[0]
        self.assertIn("--es op tuning", command)
        self.assertIn("--es reset true", command)
        self.assertIn("--es media3_ts_search_multiplier 4", command)
        self.assertIn("--es media3_pull_read_kb 512", command)
        self.assertIn("--es media3_seek_policy next", command)
        self.assertIn("--es media3_codec_mode async", command)
        self.assertIn("--es media3_seek_recovery_enabled false", command)
        self.assertTrue(result["tuningActive"])
        self.assertEqual(result["tuningMedia3PullReadKb"], 512)

    def test_player_tuning_get_uses_dedicated_debug_op(self):
        c = AdbClient("1.2.3.4:5555", "org.opensagetv.miniclient.dev.debug")
        output = 'Broadcast completed: result=1, data="ok=true;op=tuning_get;tuningActive=false;tuningMedia3TsSearchMultiplier=8"\n'
        with patch.object(c, "shell", return_value=output) as shell:
            result = c.get_player_tuning()
        self.assertIn("--es op tuning_get", shell.call_args.args[0])
        self.assertFalse(result["tuningActive"])
        self.assertEqual(result["tuningMedia3TsSearchMultiplier"], 8)

    def test_session_connect_and_exit_build_debug_broadcasts(self):
        c = AdbClient("1.2.3.4:5555", "org.opensagetv.miniclient.dev.debug")
        connect_out = ('Broadcast completed: result=1, data="ok=true;op=connect;source=direct_address;'
                       'serverName=Test;serverAddress=192.168.1.2;serverPort=31099"\n')
        exit_out = 'Broadcast completed: result=1, data="ok=true;op=exit;wasConnected=true;destination=MainActivity"\n'
        with patch.object(c, "shell", side_effect=[connect_out, exit_out]) as shell:
            connected = c.connect_server(server_name="Test", address="192.168.1.2", port=31099, save=True)
            exited = c.exit_session()
        self.assertEqual(connected["serverAddress"], "192.168.1.2")
        self.assertTrue(exited["wasConnected"])
        connect_command = shell.call_args_list[0].args[0]
        self.assertIn("--es op connect", connect_command)
        self.assertIn("--es server_name Test", connect_command)
        self.assertIn("--es address 192.168.1.2", connect_command)
        self.assertIn("--es save true", connect_command)
        exit_command = shell.call_args_list[1].args[0]
        self.assertIn("--es op exit", exit_command)

    def test_android_skip_check_builds_debug_broadcast_and_parses_delta(self):
        c = AdbClient("1.2.3.4:5555", "org.opensagetv.miniclient.dev.debug")
        output = ('Broadcast completed: result=1, data="ok=true;op=skip_check;commands=ff,ff,ff;'
                  'timelineBeforeMs=100000;timelineAfterMs=132200;timelineDeltaMs=32200;'
                  'playbackAdjustedDeltaMs=30050;elapsedMs=2150;beforeState=2;afterState=2"\n')
        with patch.object(c, "shell", return_value=output) as shell:
            result = c.android_skip_check(["ff", "ff", "ff"], delay_ms=350, settle_ms=1500)
        command = shell.call_args.args[0]
        self.assertIn("--es op skip_check", command)
        self.assertIn("--es commands ff,ff,ff", command)
        self.assertEqual(result["timelineDeltaMs"], 32200)
        self.assertEqual(result["playbackAdjustedDeltaMs"], 30050)

    def test_android_comskip_check_uses_dedicated_debug_operation_and_parses_landing(self):
        c = AdbClient("1.2.3.4:5555", "org.opensagetv.miniclient.dev.debug")
        output = ('Broadcast completed: result=1, data="ok=true;op=comskip_check;commands=right;direction=right;'
                  'timelineBeforeMs=100000;videoRecoveryTimelineMs=161000;'
                  'audioRecoveryTimelineMs=161050;outputRecoveryTimelineMs=161100;'
                  'landingTimelineMs=161100;landingDeltaMs=61100;'
                  'healthCheckPerformed=true;outputHealthy=true"\n')
        with patch.object(c, "shell", return_value=output) as shell:
            result = c.android_comskip_check("right")
        command = shell.call_args.args[0]
        self.assertIn("--es op comskip_check", command)
        self.assertIn("--es direction right", command)
        self.assertNotIn("--es commands", command)
        self.assertEqual(result["directSageCommand"], "right")
        self.assertEqual(result["resolvedArrowCommand"], "right")
        self.assertEqual(result["inputPath"], "android_debug_direct_sage_event")
        self.assertEqual(result["landingTimelineMs"], 161100)
        self.assertTrue(result["outputHealthy"])

    def test_long_debug_media_watchdog_extends_adb_transport_timeout(self):
        c = AdbClient("1.2.3.4:5555", "org.opensagetv.miniclient.dev.debug")
        output = ('Broadcast completed: result=1, data="ok=true;op=comskip_check;direction=right;'
                  'recoveryTimeoutMs=180000;outputHealthy=false"\n')
        with patch.object(c, "shell", return_value=output) as shell:
            c.android_comskip_check("right", recovery_timeout_ms=180000, verify_playback_ms=3000)
        self.assertGreaterEqual(shell.call_args.kwargs["timeout"], 213.0)

    def test_direct_debug_text_player_relative_seek_and_comskip_build_broadcasts(self):
        c = AdbClient("1.2.3.4:5555", "org.opensagetv.miniclient.dev.debug")
        outputs = [
            'Events injected: 8\n',
            'Broadcast completed: result=1, data="ok=true;op=ime_hide;requested=true"\n',
            'Broadcast completed: result=1, data="ok=true;op=player_control;action=pause;accepted=true"\n',
            'Broadcast completed: result=1, data="ok=true;op=seek_relative;deltaMs=10000;targetMs=30000;accepted=true"\n',
            'Broadcast completed: result=1, data="ok=true;op=comskip;direction=right;accepted=true"\n',
            'Broadcast completed: result=1, data="ok=true;op=relative_seek_check;deltasMs=10000,-10000;outputHealthy=true"\n',
        ]
        with patch.object(c, "shell", side_effect=outputs) as shell:
            text_result = c.keyboard_input_text("test")
            self.assertEqual(text_result["charsSent"], 4)
            self.assertEqual(text_result["inputPath"], "android_os_input_text")
            self.assertTrue(c.hide_ime()["requested"])
            self.assertTrue(c.player_control("pause")["accepted"])
            self.assertEqual(c.seek_relative(10000)["deltaMs"], 10000)
            self.assertEqual(c.comskip("right")["direction"], "right")
            self.assertTrue(c.android_relative_seek_check([10000, -10000])["outputHealthy"])
        commands = [call.args[0] for call in shell.call_args_list]
        self.assertEqual(commands[0], "input text test")
        self.assertIn("--es op ime_hide", commands[1])
        self.assertIn("--es op player_control", commands[2])
        self.assertIn("--es action pause", commands[2])
        self.assertIn("--es op seek_relative", commands[3])
        self.assertIn("--es delta_ms 10000", commands[3])
        self.assertIn("--es op comskip", commands[4])
        self.assertIn("--es direction right", commands[4])
        self.assertIn("--es op relative_seek_check", commands[5])
        self.assertIn("--es deltas_ms 10000,-10000", commands[5])

    def test_parses_player_telemetry_line_for_future_reuse(self):
        line = "08-26 03:00:00.000 I SageTVDevTelemetry: player=EXO2 event=SNAPSHOT positionMs=12345 isPlaying=true outputFps=29.97"
        event = parse_telemetry_line(line)
        self.assertIsNotNone(event)
        self.assertEqual(event["player"], "EXO2")
        self.assertEqual(event["positionMs"], 12345)

    def test_player_telemetry_is_disabled_during_baseline_recovery(self):
        c = AdbClient("1.2.3.4:5555", "org.opensagetv.miniclient.dev.debug")
        telemetry = c.player_telemetry(max_events=10)
        self.assertEqual(telemetry["transport"], "disabled-pretelemetry-baseline")
        self.assertEqual(telemetry["event_count"], 0)
        self.assertIsNone(telemetry["active_player"])

    def test_wait_for_player_event_is_disabled_during_baseline_recovery(self):
        c = AdbClient("1.2.3.4:5555", "org.opensagetv.miniclient.dev.debug")
        with self.assertRaises(RuntimeError):
            c.wait_for_player_event("READY", timeout_s=0.1)

    def test_seek_time_builds_required_target_broadcast(self):
        c = AdbClient("1.2.3.4:5555", "org.opensagetv.miniclient.dev.debug")
        output = 'Broadcast completed: result=1, data="ok=true;op=seek_time;targetMs=30000;beforeMs=120000;accepted=true"\n'
        with patch.object(c, "shell", return_value=output) as shell:
            result = c.seek_time(30000)
        command = shell.call_args.args[0]
        self.assertIn("--es op seek_time", command)
        self.assertIn("--es target_ms 30000", command)
        self.assertEqual(result["targetMs"], 30000)
        self.assertTrue(result["accepted"])


if __name__ == "__main__":
    unittest.main()
