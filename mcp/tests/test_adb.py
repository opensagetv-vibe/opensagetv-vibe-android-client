from pathlib import Path
from concurrent.futures import ThreadPoolExecutor
import subprocess
import tempfile
import sys
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))
from sagetv_dev_mcp.adb import AdbClient, parse_broadcast_result, parse_telemetry_line


class AdbSafetyTests(unittest.TestCase):

    def test_screenrecord_uses_dedicated_device_temp_and_always_cleans_it(self):
        c = AdbClient("1.2.3.4:5555", "opensagetv.vibe.miniclient.debug")
        with tempfile.TemporaryDirectory() as td:
            output = Path(td) / "capture.mp4"

            def pull(args, **_kwargs):
                self.assertEqual(args[0], "pull")
                output.write_bytes(b"video")
                return subprocess.CompletedProcess(args, 0, stdout="ok", stderr="")

            with patch("sagetv_dev_mcp.adb.time.time_ns", return_value=12345), \
                    patch.object(c, "shell", return_value="") as shell, \
                    patch.object(c, "run", side_effect=pull):
                self.assertEqual(c.screenrecord(output, seconds=5), output)

        commands = [call.args[0] for call in shell.call_args_list]
        remote = "/sdcard/OpenSageTV_Vibe_Test_Temp/screenrecord-12345.mp4"
        self.assertEqual(commands[0], "mkdir -p /sdcard/OpenSageTV_Vibe_Test_Temp")
        self.assertIn(remote, commands[1])
        self.assertEqual(commands[-2], f"rm -f {remote}")
        self.assertEqual(commands[-1], "rmdir /sdcard/OpenSageTV_Vibe_Test_Temp")
        self.assertFalse(shell.call_args_list[-2].kwargs["check"])
        self.assertFalse(shell.call_args_list[-1].kwargs["check"])

    def test_screenrecord_cleans_device_temp_when_pull_fails(self):
        c = AdbClient("1.2.3.4:5555", "opensagetv.vibe.miniclient.debug")
        with tempfile.TemporaryDirectory() as td, \
                patch("sagetv_dev_mcp.adb.time.time_ns", return_value=67890), \
                patch.object(c, "shell", return_value="") as shell, \
                patch.object(c, "run", side_effect=RuntimeError("pull failed")):
            with self.assertRaisesRegex(RuntimeError, "pull failed"):
                c.screenrecord(Path(td) / "capture.mp4", seconds=5)

        commands = [call.args[0] for call in shell.call_args_list]
        self.assertEqual(
            commands[-2],
            "rm -f /sdcard/OpenSageTV_Vibe_Test_Temp/screenrecord-67890.mp4",
        )
        self.assertEqual(commands[-1], "rmdir /sdcard/OpenSageTV_Vibe_Test_Temp")

    def test_run_replaces_non_utf8_vendor_output(self):
        c = AdbClient("1.2.3.4:5555", "opensagetv.vibe.miniclient.debug")
        completed = subprocess.CompletedProcess(
            ["adb"], 0, stdout="valid\ufffdvendor\n", stderr=""
        )
        with patch("subprocess.run", return_value=completed) as run:
            result = c.run(["logcat", "-d"])
        self.assertEqual(result.stdout, "valid\ufffdvendor\n")
        self.assertEqual(run.call_args.kwargs["encoding"], "utf-8")
        self.assertEqual(run.call_args.kwargs["errors"], "replace")

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
            c = AdbClient("1.2.3.4:5555", "opensagetv.vibe.miniclient.debug", adb=str(fake_adb))
            try:
                with patch.object(
                    c,
                    "ensure_nonexpiring_adb_authorization",
                    return_value={"currentValue": "0", "nonExpiring": True},
                ):
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

    def _pty_adb(self, directory):
        """Real terminal echo/CRLF/prompts, plus an old mksh redraw preamble."""
        fake_adb = Path(directory) / "adb-pty"
        fake_adb.write_text(
            r'''#!/usr/bin/env python3
import os
import pty
import select
import signal
import subprocess
import sys

master, slave = pty.openpty()
environment = dict(os.environ, PS1="shell@AM2:/ $ ", PS2="> ")
remote = subprocess.Popen(["/bin/sh", "-i"], stdin=slave, stdout=slave,
                          stderr=slave, env=environment)
os.close(slave)
def terminate(*_args):
    raise SystemExit(0)
signal.signal(signal.SIGTERM, terminate)
try:
    os.write(1, b"shell@AM2:/ $ old getprop\rwrapped command <\b\b\b\r\n")
    while True:
        readable, _, _ = select.select([0, master], [], [])
        for stream in readable:
            try:
                data = os.read(stream, 4096)
            except OSError:
                raise SystemExit(0)
            if not data:
                raise SystemExit(0)
            os.write(master if stream == 0 else 1, data)
finally:
    remote.terminate()
    remote.wait(timeout=1)
    os.close(master)
''', encoding="utf-8")
        fake_adb.chmod(0o755)
        return str(fake_adb)

    def test_persistent_shell_handshake_discards_pty_echo_prompt_and_backspaces(self):
        with tempfile.TemporaryDirectory() as td:
            c = AdbClient("1.2.3.4:5555", "opensagetv.vibe.miniclient.debug",
                          adb=self._pty_adb(td))
            try:
                self.assertEqual(c.shell("printf 'MINIMX\\n'"), "MINIMX\n")
                pid = c.persistent_shell_status()["persistentShellPid"]
                self.assertEqual(c.shell("printf '23\\n'"), "23\n")
                self.assertEqual(c.shell("printf 'shell@AM2:/ $ legitimate\\boutput\\n'"),
                                 "shell@AM2:/ $ legitimate\boutput\n")
                self.assertEqual(c.persistent_shell_status()["persistentShellPid"], pid)
                self.assertEqual(c.persistent_shell_status()["persistentShellCommands"], 3)
                self.assertEqual(c.persistent_shell_status()["persistentShellRestarts"], 1)
            finally:
                c.close()

    def test_persistent_pty_commands_remain_serialized(self):
        with tempfile.TemporaryDirectory() as td:
            c = AdbClient("1.2.3.4:5555", "opensagetv.vibe.miniclient.debug",
                          adb=self._pty_adb(td))
            try:
                with ThreadPoolExecutor(max_workers=4) as pool:
                    results = list(pool.map(
                        lambda value: c.shell(f"printf 'first{value}'; sleep 0.01; printf 'last{value}'"),
                        range(8)))
                self.assertEqual(results, [f"first{i}last{i}" for i in range(8)])
                self.assertEqual(c.persistent_shell_status()["persistentShellRestarts"], 1)
                self.assertEqual(c.persistent_shell_status()["persistentShellCommands"], 8)
            finally:
                c.close()

    def test_persistent_pty_timeout_closes_without_runtime_replay(self):
        with tempfile.TemporaryDirectory() as td:
            c = AdbClient("1.2.3.4:5555", "opensagetv.vibe.miniclient.debug",
                          adb=self._pty_adb(td))
            try:
                with self.assertRaises(subprocess.TimeoutExpired):
                    c.shell("sleep 2", timeout=0.1)
                self.assertFalse(c.persistent_shell_status()["persistentShell"])
                self.assertEqual(c.persistent_shell_status()["persistentShellCommands"], 1)
                self.assertEqual(c.persistent_shell_status()["persistentShellRestarts"], 1)
            finally:
                c.close()

    def test_persistent_pty_closure_does_not_replay_runtime_command(self):
        with tempfile.TemporaryDirectory() as td:
            c = AdbClient("1.2.3.4:5555", "opensagetv.vibe.miniclient.debug",
                          adb=self._pty_adb(td))
            try:
                with self.assertRaisesRegex(RuntimeError, "shell (closed|exited)"):
                    c.shell("exit")
                self.assertFalse(c.persistent_shell_status()["persistentShell"])
                self.assertEqual(c.persistent_shell_status()["persistentShellCommands"], 1)
                self.assertEqual(c.persistent_shell_status()["persistentShellRestarts"], 1)
            finally:
                c.close()

    def test_persistent_shell_startup_timeout_prevents_runtime_command(self):
        c = AdbClient("1.2.3.4:5555", "opensagetv.vibe.miniclient.debug")
        with patch("sagetv_dev_mcp.adb.subprocess.Popen") as popen, \
                patch.object(c, "_read_persistent_shell_result",
                             side_effect=subprocess.TimeoutExpired("startup", 15)), \
                patch.object(c, "_stop_persistent_shell") as stop:
            with self.assertRaises(subprocess.TimeoutExpired):
                c.shell("printf never-issued")
        self.assertEqual(popen.return_value.stdin.write.call_count, 1)
        self.assertNotIn(b"never-issued", popen.return_value.stdin.write.call_args.args[0])
        self.assertEqual(c.persistent_shell_status()["persistentShellCommands"], 0)
        self.assertEqual(c.persistent_shell_status()["persistentShellRestarts"], 1)
        self.assertEqual(stop.call_count, 2)

    def test_connect_sets_and_verifies_nonexpiring_device_authorization(self):
        c = AdbClient("1.2.3.4:5555", "opensagetv.vibe.miniclient.debug")
        connected = subprocess.CompletedProcess(
            ["adb", "connect"], 0, stdout="already connected\n", stderr=""
        )
        with patch.object(c, "run", return_value=connected), \
                patch.object(c, "_start_persistent_shell"), \
                patch.object(c, "_one_shot_shell", side_effect=["null\n", "", "0\n"]) as shell:
            self.assertEqual(c.connect(), "already connected")
        self.assertEqual(
            [call.args[0] for call in shell.call_args_list],
            [
                "settings get global adb_allowed_connection_time",
                "settings put global adb_allowed_connection_time 0",
                "settings get global adb_allowed_connection_time",
            ],
        )
        self.assertEqual(
            c.adb_authorization_status(),
            {
                "setting": "adb_allowed_connection_time",
                "previousValue": "null",
                "currentValue": "0",
                "nonExpiring": True,
                "scope": "device_global",
            },
        )

    def test_connect_fails_when_vendor_does_not_retain_authorization_policy(self):
        c = AdbClient("1.2.3.4:5555", "opensagetv.vibe.miniclient.debug")
        connected = subprocess.CompletedProcess(
            ["adb", "connect"], 0, stdout="connected\n", stderr=""
        )
        with patch.object(c, "run", return_value=connected), \
                patch.object(c, "_start_persistent_shell"), \
                patch.object(c, "_stop_persistent_shell") as stop, \
                patch.object(c, "_one_shot_shell", side_effect=["null\n", "", "null\n"]):
            with self.assertRaisesRegex(RuntimeError, "did not retain"):
                c.connect()
        stop.assert_called_once_with()

    def test_connect_bootstraps_authorization_before_persistent_shell(self):
        c = AdbClient("1.2.3.4:5555", "opensagetv.vibe.miniclient.debug")
        connected = subprocess.CompletedProcess(
            ["adb", "connect"], 0, stdout="connected\n", stderr=""
        )
        order = []
        with patch.object(c, "run", return_value=connected), \
                patch.object(
                    c,
                    "ensure_nonexpiring_adb_authorization",
                    side_effect=lambda: order.append("authorization") or {
                        "currentValue": "0", "nonExpiring": True
                    },
                ), \
                patch.object(
                    c, "_start_persistent_shell",
                    side_effect=lambda: order.append("persistent"),
                ):
            self.assertEqual(c.connect(), "connected")
        self.assertEqual(order, ["authorization", "persistent"])

    def test_connect_recovers_one_stale_offline_tcp_transport(self):
        c = AdbClient("1.2.3.4:5555", "opensagetv.vibe.miniclient.debug")
        responses = [
            subprocess.CompletedProcess(["adb", "connect"], 0, stdout="already connected\n", stderr=""),
            subprocess.CompletedProcess(["adb", "disconnect"], 0, stdout="disconnected\n", stderr=""),
            subprocess.CompletedProcess(["adb", "connect"], 0, stdout="connected\n", stderr=""),
        ]
        with patch.object(c, "run", side_effect=responses) as run, \
                patch.object(
                    c,
                    "ensure_nonexpiring_adb_authorization",
                    side_effect=[
                        RuntimeError("adb: device offline"),
                        {"currentValue": "0", "nonExpiring": True},
                    ],
                ) as authorization, \
                patch.object(c, "_start_persistent_shell"), \
                patch("sagetv_dev_mcp.adb.time.sleep") as sleeping:
            self.assertEqual(c.connect(), "connected")
        self.assertEqual(
            [call.args[0] for call in run.call_args_list],
            [
                ["connect", "1.2.3.4:5555"],
                ["disconnect", "1.2.3.4:5555"],
                ["connect", "1.2.3.4:5555"],
            ],
        )
        self.assertFalse(run.call_args_list[1].kwargs["check"])
        self.assertEqual(authorization.call_count, 2)
        sleeping.assert_called_once_with(0.25)

    def test_connect_does_not_retry_unauthorized_transport(self):
        c = AdbClient("1.2.3.4:5555", "opensagetv.vibe.miniclient.debug")
        connected = subprocess.CompletedProcess(
            ["adb", "connect"], 0, stdout="connected\n", stderr=""
        )
        with patch.object(c, "run", return_value=connected) as run, \
                patch.object(
                    c,
                    "ensure_nonexpiring_adb_authorization",
                    side_effect=RuntimeError("adb: device unauthorized"),
                ), \
                patch.object(c, "_stop_persistent_shell"):
            with self.assertRaisesRegex(RuntimeError, "unauthorized"):
                c.connect()
        run.assert_called_once_with(["connect", "1.2.3.4:5555"], device=False)

    def test_refuses_production_namespace(self):
        c = AdbClient("1.2.3.4:5555", "jvl.sage.miniclient.android.tv.debug")
        with self.assertRaises(PermissionError):
            c._ensure_dev_package("jvl.sage.miniclient.android.tv.debug")

    def test_refuses_wrong_package(self):
        c = AdbClient("1.2.3.4:5555", "opensagetv.vibe.miniclient.debug")
        with self.assertRaises(PermissionError):
            c._ensure_dev_package("com.example.other")

    def test_key_alias(self):
        c = AdbClient("1.2.3.4:5555", "opensagetv.vibe.miniclient.debug")
        with patch.object(c, "shell", return_value="") as shell:
            c.key("ff")
            shell.assert_called_once_with("input keyevent KEYCODE_MEDIA_FAST_FORWARD")

    def test_dpad_key_aliases_match_android_names(self):
        c = AdbClient("1.2.3.4:5555", "opensagetv.vibe.miniclient.debug")
        with patch.object(c, "shell", return_value="") as shell:
            c.key("dpad_down")
            c.key("dpad_center")
        self.assertEqual(
            [call.args[0] for call in shell.call_args_list],
            [
                "input keyevent KEYCODE_DPAD_DOWN",
                "input keyevent KEYCODE_DPAD_CENTER",
            ],
        )

    def test_input_text_encodes_spaces_and_next_uses_enter(self):
        c = AdbClient("1.2.3.4:5555", "opensagetv.vibe.miniclient.debug")
        with patch.object(c, "shell", return_value="") as shell:
            c.input_text("meet the press")
            c.key("next")
        self.assertEqual(shell.call_args_list[0].args[0], "input text meet%sthe%spress")
        self.assertEqual(shell.call_args_list[1].args[0], "input keyevent KEYCODE_ENTER")

    def test_input_text_paced_uses_one_device_shell_with_device_side_delays(self):
        c = AdbClient("1.2.3.4:5555", "opensagetv.vibe.miniclient.debug")
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
        c = AdbClient("1.2.3.4:5555", "opensagetv.vibe.miniclient.debug")
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
        c = AdbClient("1.2.3.4:5555", "opensagetv.vibe.miniclient.debug")
        component = "opensagetv.vibe.miniclient.debug/opensagetv.vibe.miniclient.android.tv.MainActivity"
        with patch.object(c, "wake", return_value={"wakeKey": "KEYCODE_WAKEUP", "homeKey": "KEYCODE_HOME"}) as wake, \
             patch.object(c, "shell", side_effect=[component + "\n", "Starting: Intent {...}\nStatus: ok\n"]) as shell:
            result = c.launch()
        wake.assert_called_once_with()
        self.assertIn("Status: ok", result)
        self.assertIn("cmd package resolve-activity --brief", shell.call_args_list[0].args[0])
        self.assertIn("android.intent.category.LEANBACK_LAUNCHER", shell.call_args_list[0].args[0])
        self.assertEqual(shell.call_args_list[1].args[0], f"am start -W -n {component}")
        self.assertFalse(any("monkey -p" in call.args[0] for call in shell.call_args_list))

    def test_launch_falls_back_to_phone_launcher_when_no_leanback_activity_exists(self):
        c = AdbClient("1.2.3.4:5555", "opensagetv.vibe.miniclient.debug")
        component = "opensagetv.vibe.miniclient.debug/opensagetv.vibe.miniclient.android.phone.ServersActivity"
        with patch.object(c, "wake", return_value={"wakeKey": "KEYCODE_WAKEUP", "homeKey": "KEYCODE_HOME"}), \
             patch.object(c, "shell", side_effect=["No activity found\n", component + "\n", "Status: ok\n"]) as shell:
            result = c.launch()
        self.assertIn("Status: ok", result)
        self.assertIn("android.intent.category.LEANBACK_LAUNCHER", shell.call_args_list[0].args[0])
        self.assertIn("android.intent.category.LAUNCHER", shell.call_args_list[1].args[0])
        self.assertEqual(shell.call_args_list[2].args[0], f"am start -W -n {component}")

    def test_app_status_does_not_launch_package(self):
        c = AdbClient("1.2.3.4:5555", "opensagetv.vibe.miniclient.debug")
        replies = [
            "1234\n",
            "mResumedActivity: ActivityRecord{ x opensagetv.vibe.miniclient.debug/.MainActivity }\n",
            "User 0: installed=true stopped=false enabled=0\n",
        ]
        with patch.object(c, "shell", side_effect=replies) as shell:
            status = c.app_status()
        self.assertTrue(status["running"])
        self.assertTrue(status["foreground"])
        self.assertEqual(status["pids"], ["1234"])
        self.assertEqual(shell.call_args_list[0].args[0], f"pidof {c.dev_package}")

    def test_android6_missing_cmd_uses_installed_phone_component_not_shell_error(self):
        c = AdbClient("1.2.3.4:5555", "opensagetv.vibe.miniclient.debug")
        component = c.dev_package + "/opensagetv.vibe.miniclient.android.phone.ServersActivity"
        with patch.object(c, "wake"), patch.object(c, "shell", side_effect=[
            "/system/bin/sh: cmd: not found\n",
            "/system/bin/sh: cmd: not found\n", "c28493d " + component + "\n",
            "feature:android.hardware.touchscreen\n", "Status: ok\n",
        ]) as shell:
            self.assertIn("Status: ok", c.launch())
        self.assertEqual(shell.call_args_list[-1].args[0], "am start -W -n " + component)

    def test_android6_leanback_metadata_keeps_tv_launcher(self):
        c = AdbClient("1.2.3.4:5555", "opensagetv.vibe.miniclient.debug")
        phone = c.dev_package + "/opensagetv.vibe.miniclient.android.phone.ServersActivity"
        tv = c.dev_package + "/opensagetv.vibe.miniclient.android.tv.MainActivity"
        with patch.object(c, "shell", side_effect=[
            "/system/bin/sh: cmd: not found\n", "/system/bin/sh: cmd: not found\n",
            phone + "\n" + tv, "feature:android.software.leanback\n",
        ]):
            self.assertEqual(c._resolve_launcher_component(), tv)

    def test_android6_fallback_cannot_guess_an_uninstalled_component(self):
        c = AdbClient("1.2.3.4:5555", "opensagetv.vibe.miniclient.debug")
        with patch.object(c, "shell", side_effect=[
            "/system/bin/sh: cmd: not found\n", "/system/bin/sh: cmd: not found\n",
            "other.package/.phone.ServersActivity\n", "feature:android.hardware.touchscreen\n",
        ]):
            self.assertEqual(c._resolve_launcher_component(), "")

    def test_launcher_rejects_components_from_other_packages(self):
        c = AdbClient("1.2.3.4:5555", "opensagetv.vibe.miniclient.debug")
        with patch.object(c, "shell", side_effect=[
            "other.package/.MainActivity\n", c.dev_package + "/.MainActivity\n",
        ]):
            self.assertEqual(c._resolve_launcher_component(), c.dev_package + "/.MainActivity")

    def test_android6_pidof_all_processes_and_unsupported_ps_flag(self):
        c = AdbClient("1.2.3.4:5555", "opensagetv.vibe.miniclient.debug")
        with patch.object(c, "shell", side_effect=[
            "1 2 3 7457\n", "USER PID PPID VSIZE RSS WCHAN PC NAME\n",
            "USER PID PPID VSIZE RSS WCHAN PC NAME\n"
            "root 1 0 0 0 0 0 S /init\n"
            "u0_a123 7457 1 0 0 0 0 S " + c.dev_package + "\n",
            "mResumedActivity: ActivityRecord{ x com.android.launcher3/.Launcher }\n",
            "User 0: installed=true stopped=false enabled=0\n",
        ]) as shell:
            status = c.app_status()
        self.assertEqual(status["pids"], ["7457"])
        self.assertEqual(status["runningSource"], "ps")
        self.assertEqual(shell.call_args_list[2].args[0], "ps")

    def test_android6_pidof_all_processes_cannot_make_stopped_app_running(self):
        c = AdbClient("1.2.3.4:5555", "opensagetv.vibe.miniclient.debug")
        with patch.object(c, "shell", side_effect=[
            "1 2 3\n", "USER PID PPID VSIZE RSS WCHAN PC NAME\n",
            "root 1 0 0 0 0 0 S /init\n",
            "mResumedActivity: ActivityRecord{ x com.android.launcher3/.Launcher }\n",
            "User 0: installed=true stopped=true enabled=0\n",
        ]):
            status = c.app_status()
        self.assertFalse(status["running"])
        self.assertEqual(status["pids"], [])

    def test_pidof_diagnostic_numbers_are_not_running_evidence(self):
        c = AdbClient("1.2.3.4:5555", "opensagetv.vibe.miniclient.debug")
        with patch.object(c, "shell", side_effect=[
            "pidof: error 123\n", "USER PID PPID NAME\n",
            "mResumedActivity: ActivityRecord{ x com.android.launcher3/.Launcher }\n",
            "User 0: installed=true stopped=true enabled=0\n",
        ]):
            self.assertFalse(c.app_status()["running"])


    def test_app_status_treats_resumed_dev_activity_as_running_when_pidof_is_empty(self):
        c = AdbClient("1.2.3.4:5555", "opensagetv.vibe.miniclient.debug")
        replies = [
            "",
            "USER PID PPID NAME\n",
            "mResumedActivity: ActivityRecord{ x opensagetv.vibe.miniclient.debug/opensagetv.vibe.miniclient.android.phone.ServersActivity }\n",
            "User 0: installed=true stopped=false enabled=0\n",
        ]
        with patch.object(c, "shell", side_effect=replies):
            status = c.app_status()
        self.assertTrue(status["running"])
        self.assertTrue(status["foreground"])
        self.assertEqual(status["runningSource"], "resumedActivity")
        self.assertEqual(status["pids"], [])

    def test_app_status_falls_back_to_ps_for_background_dev_process(self):
        c = AdbClient("1.2.3.4:5555", "opensagetv.vibe.miniclient.debug")
        replies = [
            "",
            "u0_a123 7457 1 0 0 opensagetv.vibe.miniclient.debug\n",
            "mResumedActivity: ActivityRecord{ x com.amazon.tv.launcher/.ui.HomeActivity_vNext }\n",
            "User 0: installed=true stopped=false enabled=0\n",
        ]
        with patch.object(c, "shell", side_effect=replies):
            status = c.app_status()
        self.assertTrue(status["running"])
        self.assertFalse(status["foreground"])
        self.assertEqual(status["runningSource"], "ps")
        self.assertEqual(status["pids"], ["7457"])

    def test_app_status_treats_real_pid_as_running_despite_stale_stopped_bit(self):
        c = AdbClient("1.2.3.4:5555", "opensagetv.vibe.miniclient.debug")
        replies = [
            "7457\n",
            "mResumedActivity: ActivityRecord{ x com.amazon.tv.launcher/.ui.HomeActivity_vNext }\n",
            "User 0: installed=true stopped=true enabled=0\n",
        ]
        with patch.object(c, "shell", side_effect=replies):
            status = c.app_status()
        self.assertTrue(status["running"])
        self.assertTrue(status["forceStopped"])
        self.assertEqual(status["runningSource"], "pidof")

    def test_focused_window_uses_window_manager_when_available(self):
        c = AdbClient("1.2.3.4:5555", "opensagetv.vibe.miniclient.debug")
        window = "  mCurrentFocus=Window{123 opensagetv.vibe.miniclient.debug/.MainActivity}\n"
        with patch.object(c, "shell", return_value=window) as shell:
            result = c.focused_window()
        self.assertIn("mCurrentFocus", result)
        shell.assert_called_once_with("dumpsys window windows", timeout=30, check=False)

    def test_focused_window_falls_back_to_resumed_activity_on_fire_os_api30(self):
        c = AdbClient("1.2.3.4:5555", "opensagetv.vibe.miniclient.debug")
        activities = (
            "mResumedActivity: ActivityRecord{fdd5644 u0 "
            "opensagetv.vibe.miniclient.debug/opensagetv.vibe.miniclient.android.gdx.MiniClientGDXActivity}\n"
        )
        with patch.object(c, "shell", side_effect=["mObscuringWindow=null\n", activities]) as shell:
            result = c.focused_window()
        self.assertIn("mResumedActivity", result)
        self.assertEqual(shell.call_args_list[1].args[0], "dumpsys activity activities")

    def test_prepare_clean_start_force_stops_only_if_graceful_exit_leaves_process(self):
        c = AdbClient("1.2.3.4:5555", "opensagetv.vibe.miniclient.debug")
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

    def test_install_dev_apk_preserves_settings_by_default(self):
        c = AdbClient("1.2.3.4:5555", "opensagetv.vibe.miniclient.debug")
        with tempfile.TemporaryDirectory() as td:
            apk = Path(td) / "dev.apk"
            apk.write_bytes(b"apk")
            calls = []

            def fake_run(args, **kwargs):
                args = list(args)
                calls.append(args)
                if args[0] == "install":
                    self.assertEqual(kwargs["timeout"], 180)
                    return subprocess.CompletedProcess(args, 0, stdout="Success\n", stderr="")
                raise AssertionError(args)

            with patch.object(c, "detect_apk_package", return_value="opensagetv.vibe.miniclient.debug"), \
                 patch.object(c, "run", side_effect=fake_run):
                result = c.install_dev_apk(apk)

            self.assertEqual(calls, [["install", "-r", str(apk)]])
            self.assertIn("settings preserved", result)
            self.assertEqual(c.install_timeout_seconds, 180)

    def test_slow_device_install_budget_does_not_retry_or_reset_after_timeout(self):
        c = AdbClient("1.2.3.4:5555", "opensagetv.vibe.miniclient.debug",
                      install_timeout_seconds=600)
        with patch.object(c, "detect_apk_package", return_value=c.dev_package), \
                patch.object(c, "run", side_effect=subprocess.TimeoutExpired("install", 600)) as run:
            with self.assertRaises(subprocess.TimeoutExpired):
                c.install_dev_apk(Path("verified.apk"))
        run.assert_called_once_with(["install", "-r", "verified.apk"], timeout=600)

    def test_install_helper_rejects_invalid_direct_timeout(self):
        for value in (True, "600", 0, 901, float("nan"), float("inf")):
            with self.subTest(value=value), self.assertRaises(ValueError):
                AdbClient("1.2.3.4:5555", "opensagetv.vibe.miniclient.debug",
                          install_timeout_seconds=value)

    def test_install_dev_apk_clean_is_explicit(self):
        c = AdbClient("1.2.3.4:5555", "opensagetv.vibe.miniclient.debug")
        with tempfile.TemporaryDirectory() as td:
            apk = Path(td) / "dev.apk"
            apk.write_bytes(b"apk")
            calls = []

            def fake_run(args, **kwargs):
                calls.append(list(args))
                return subprocess.CompletedProcess(args, 0, stdout="Success\n", stderr="")

            with patch.object(c, "detect_apk_package", return_value="opensagetv.vibe.miniclient.debug"), \
                 patch.object(c, "run", side_effect=fake_run):
                result = c.install_dev_apk(apk, clean=True)

            self.assertEqual(calls[0], ["uninstall", "opensagetv.vibe.miniclient.debug"])
            self.assertEqual(calls[1], ["install", str(apk)])
            self.assertIn("uninstall: Success", result)

    def test_client_id_debug_controls_use_explicit_receiver_ops(self):
        c = AdbClient("1.2.3.4:5555", "opensagetv.vibe.miniclient.debug")
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

    def test_playback_stats_overlay_supports_modes_and_legacy_boolean(self):
        c = AdbClient("1.2.3.4:5555", "opensagetv.vibe.miniclient.debug")
        with patch.object(c, "dev_control", side_effect=[
            {"ok": True, "requestedMode": "toggle"},
            {"ok": True, "requestedVisible": False},
        ]) as control:
            toggled = c.set_active_player_overlay(mode="toggle")
            hidden = c.set_active_player_overlay(visible=False)
        self.assertEqual(toggled["requestedMode"], "toggle")
        self.assertFalse(hidden["requestedVisible"])
        self.assertEqual(control.call_args_list[0].args[0], "active_player_overlay")
        self.assertEqual(control.call_args_list[0].kwargs, {"mode": "toggle"})
        self.assertEqual(control.call_args_list[1].kwargs, {"visible": "false"})

    def test_playback_stats_overlay_rejects_unknown_mode(self):
        c = AdbClient("1.2.3.4:5555", "opensagetv.vibe.miniclient.debug")
        with self.assertRaisesRegex(ValueError, "mode must be"):
            c.set_active_player_overlay(mode="forever")

    def test_parse_debug_broadcast_snapshot(self):
        text = 'Broadcasting: Intent { act=opensagetv.vibe.miniclient.DEBUG_CONTROL }\nBroadcast completed: result=1, data="ok=true;op=snapshot;connected=true;player=media3;state=2;mediaTimeMs=123456"\n'
        result = parse_broadcast_result(text)
        self.assertTrue(result["ok"])
        self.assertTrue(result["connected"])
        self.assertEqual(result["player"], "media3")
        self.assertEqual(result["state"], 2)
        self.assertEqual(result["mediaTimeMs"], 123456)

    def test_ui_context_is_opaque_even_when_it_looks_numeric(self):
        for identity in ("5251555444e60", "000012345678", "abc123"):
            result = parse_broadcast_result(
                'Broadcast completed: result=1, data="ok=true;uiContextHint='
                + identity + ';mediaTimeMs=123;ratio=1.25"')
            self.assertEqual(result["uiContextHint"], identity)
            self.assertIsInstance(result["uiContextHint"], str)
            self.assertEqual(result["mediaTimeMs"], 123)
            self.assertEqual(result["ratio"], 1.25)

    def test_debug_control_builds_explicit_dev_only_broadcast(self):
        c = AdbClient("1.2.3.4:5555", "opensagetv.vibe.miniclient.debug")
        output = 'Broadcast completed: result=1, data="ok=true;op=config;player=media3;streaming=pull;decoding=hardware;gsyEngine=auto"\n'
        with patch.object(c, "shell", return_value=output) as shell:
            result = c.set_player_config(player="media3", streaming="pull", decoding="hardware",
                                         gsy_engine="auto", gsy_system_probe=True)
        command = shell.call_args.args[0]
        self.assertIn("opensagetv.vibe.miniclient.DEBUG_CONTROL", command)
        self.assertIn("opensagetv.vibe.miniclient.debug/opensagetv.vibe.miniclient.android.tv.debug.DevTestReceiver", command)
        self.assertIn("--es player media3", command)
        self.assertIn("--es streaming pull", command)
        self.assertIn("--es gsy_system_probe true", command)
        self.assertEqual(result["player"], "media3")

    def test_unified_graphics_toggle_is_forwarded_as_an_opt_in_next_connection_setting(self):
        c = AdbClient("1.2.3.4:5555", "opensagetv.vibe.miniclient.debug")
        output = ('Broadcast completed: result=1, data="ok=true;op=config;'
                  'unifiedGraphicsSurfaces=true;unifiedGraphicsAppliesNextConnection=true"\n')
        with patch.object(c, "shell", return_value=output) as shell:
            result = c.set_unified_graphics(True)
        command = shell.call_args.args[0]
        self.assertIn("--es unified_graphics_surfaces true", command)
        self.assertTrue(result["unifiedGraphicsSurfaces"])
        self.assertTrue(result["unifiedGraphicsAppliesNextConnection"])

    def test_mim_direct_late_fallback_fault_is_explicit_and_one_shot(self):
        c = AdbClient("1.2.3.4:5555", "opensagetv.vibe.miniclient.debug")
        output = ('Broadcast completed: result=1, data="ok=true;'
                  'op=mim_direct_late_fallback_fault;armed=true;state=ready_transcode"\n')
        with patch.object(c, "shell", return_value=output) as shell:
            result = c.set_mim_direct_late_fallback_fault(True)
        command = shell.call_args.args[0]
        self.assertIn("--es op mim_direct_late_fallback_fault", command)
        self.assertIn("--es enabled true", command)
        self.assertTrue(result["armed"])

    def test_direct_fault_can_keep_real_pull_source(self):
        c = AdbClient("1.2.3.4:5555", "opensagetv.vibe.miniclient.debug")
        with patch.object(c, "shell", return_value='Broadcast completed: result=1, data="ok=true;armed=true"') as shell:
            c.set_mim_direct_late_fallback_fault(True, include_pull_failure=False)
        self.assertIn("--es enabled true", shell.call_args.args[0])
        self.assertIn("--es fail_pull false", shell.call_args.args[0])

    def test_background_recovery_options_are_forwarded_independently(self):
        c = AdbClient("1.2.3.4:5555", "opensagetv.vibe.miniclient.debug")
        output = ('Broadcast completed: result=1, data="ok=true;op=config;'
                  'keepSessionInBackground=true;resumeBackgroundPlayback=false;'
                  'backgroundSessionTimeoutSeconds=60"\n')
        with patch.object(c, "shell", return_value=output) as shell:
            result = c.set_player_config(
                keep_session_in_background=True,
                resume_background_playback=False,
                background_session_timeout_seconds=60,
            )
        command = shell.call_args.args[0]
        self.assertIn("--es keep_session_in_background true", command)
        self.assertIn("--es resume_background_playback false", command)
        self.assertIn("--es background_session_timeout_seconds 60", command)
        self.assertFalse(result["resumeBackgroundPlayback"])

    def test_audio_focus_controls_use_non_foreground_debug_broadcasts(self):
        c = AdbClient("1.2.3.4:5555", "opensagetv.vibe.miniclient.debug")
        with patch.object(c, "dev_control", side_effect=[
            {"ok": True, "op": "audio_focus_request", "mode": "duck", "granted": True},
            {"ok": True, "op": "audio_focus_abandon", "abandoned": True},
        ]) as control:
            requested = c.request_competing_audio_focus("DUCK")
            abandoned = c.abandon_competing_audio_focus()
        self.assertTrue(requested["granted"])
        self.assertTrue(abandoned["abandoned"])
        self.assertEqual(control.call_args_list[0].args[0], "audio_focus_request")
        self.assertEqual(control.call_args_list[0].kwargs, {"foreground": False, "mode": "duck"})
        self.assertEqual(control.call_args_list[1].args[0], "audio_focus_abandon")
        self.assertEqual(control.call_args_list[1].kwargs, {"foreground": False})

    def test_audio_focus_rejects_unknown_mode(self):
        c = AdbClient("1.2.3.4:5555", "opensagetv.vibe.miniclient.debug")
        with self.assertRaisesRegex(ValueError, "transient, duck, or permanent"):
            c.request_competing_audio_focus("exclusive")

    def test_subtitle_control_uses_non_foreground_debug_broadcast(self):
        c = AdbClient("1.2.3.4:5555", "opensagetv.vibe.miniclient.debug")
        with patch.object(c, "dev_control", return_value={
            "ok": True, "op": "subtitle_control", "index": 0, "accepted": True,
        }) as control:
            result = c.set_subtitle_track(0)
        self.assertTrue(result["accepted"])
        self.assertEqual(control.call_args.args[0], "subtitle_control")
        self.assertEqual(control.call_args.kwargs, {"foreground": False, "index": 0})
        with self.assertRaisesRegex(ValueError, "-1 \\(off\\) or >= 0"):
            c.set_subtitle_track(-2)

    def test_audio_track_control_uses_non_foreground_debug_broadcast(self):
        c = AdbClient("1.2.3.4:5555", "opensagetv.vibe.miniclient.debug")
        with patch.object(c, "dev_control", return_value={
            "ok": True, "op": "audio_track_control", "index": 1, "accepted": True,
        }) as control:
            result = c.set_audio_track(1)
        self.assertTrue(result["accepted"])
        self.assertEqual(control.call_args.args[0], "audio_track_control")
        self.assertEqual(control.call_args.kwargs, {
            "foreground": False, "index": 1,
        })
        with self.assertRaisesRegex(ValueError, ">= 0"):
            c.set_audio_track(-1)

    def test_caption_mode_uses_media_cmd_authority_path(self):
        c = AdbClient("1.2.3.4:5555", "opensagetv.vibe.miniclient.debug")
        with patch.object(c, "dev_control", return_value={
            "ok": True, "op": "caption_mode", "mode": "cc1", "accepted": True,
        }) as control:
            result = c.set_caption_mode("CC1")
        self.assertTrue(result["accepted"])
        self.assertEqual(control.call_args.args[0], "caption_mode")
        self.assertEqual(control.call_args.kwargs, {
            "foreground": False, "mode": "cc1",
        })
        with self.assertRaisesRegex(ValueError, "off, cc1, cc2, stv, or dvb"):
            c.set_caption_mode("subtitle")

    def test_active_audio_control_validates_and_uses_non_foreground_broadcast(self):
        c = AdbClient("1.2.3.4:5555", "opensagetv.vibe.miniclient.debug")
        with patch.object(c, "dev_control", return_value={
            "ok": True, "op": "audio_adjustment", "accepted": True,
        }) as control:
            result = c.set_active_audio("ENCODED", True, 25)
        self.assertTrue(result["accepted"])
        self.assertEqual(control.call_args.args[0], "audio_adjustment")
        self.assertEqual(control.call_args.kwargs, {
            "foreground": False,
            "output": "passthrough",
            "passthrough_offset_enabled": "true",
            "offset_ms": 25,
        })
        with self.assertRaisesRegex(ValueError, "output must be decoded or passthrough"):
            c.set_active_audio("automatic")
        with self.assertRaisesRegex(ValueError, "between -4000 and 4000"):
            c.set_active_audio(offset_ms=4001)
        with self.assertRaisesRegex(ValueError, "is required"):
            c.set_active_audio()



    def test_set_player_config_passes_complete_fixed_encoding_block(self):
        c = AdbClient("1.2.3.4:5555", "opensagetv.vibe.miniclient.debug")
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
                fixed_caption_side_channel_enabled=True,
                fixed_caption_side_channel_port=31910,
                mim_direct_mode="copy",
                mim_direct_deinterlace="off",
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
            "--es fixed_caption_side_channel_enabled true",
            "--es fixed_caption_side_channel_port 31910",
            "--es mim_direct_mode copy",
            "--es mim_direct_deinterlace off",
        ):
            self.assertIn(expected, command)
        self.assertEqual(result["fixedVideoBitrateKbps"], 6000)
        self.assertEqual(result["fixedRemuxingPreference"], "off")

    def test_set_player_config_passes_language_and_caption_service_preferences(self):
        c = AdbClient("1.2.3.4:5555", "opensagetv.vibe.miniclient.debug")
        output = ('Broadcast completed: result=1, data="ok=true;op=config;'
                  'preferredAudioLanguage=es;preferredSubtitleLanguage=en;'
                  'preferredCaptionStandard=cea708;preferredCaptionService=3"\n')
        with patch.object(c, "shell", return_value=output) as shell:
            result = c.set_player_config(
                preferred_audio_language="es",
                preferred_subtitle_language="en",
                preferred_caption_standard="cea708",
                preferred_caption_service=3,
            )
        command = shell.call_args.args[0]
        for expected in (
            "--es preferred_audio_language es",
            "--es preferred_subtitle_language en",
            "--es preferred_caption_standard cea708",
            "--es preferred_caption_service 3",
        ):
            self.assertIn(expected, command)
        self.assertEqual(result["preferredCaptionService"], 3)


    def test_player_tuning_builds_debug_only_broadcast(self):
        c = AdbClient("1.2.3.4:5555", "opensagetv.vibe.miniclient.debug")
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
        c = AdbClient("1.2.3.4:5555", "opensagetv.vibe.miniclient.debug")
        output = 'Broadcast completed: result=1, data="ok=true;op=tuning_get;tuningActive=false;tuningMedia3TsSearchMultiplier=8"\n'
        with patch.object(c, "shell", return_value=output) as shell:
            result = c.get_player_tuning()
        self.assertIn("--es op tuning_get", shell.call_args.args[0])
        self.assertFalse(result["tuningActive"])
        self.assertEqual(result["tuningMedia3TsSearchMultiplier"], 8)

    def test_codec_capabilities_uses_dedicated_debug_op(self):
        c = AdbClient("1.2.3.4:5555", "opensagetv.vibe.miniclient.debug")
        output = ('Broadcast completed: result=1, data="ok=true;op=codec_capabilities;'
                  'codecProfileCount=2;codecInterlaceCapability=not_reported_by_android"\n')
        with patch.object(c, "shell", return_value=output) as shell:
            result = c.codec_capabilities()
        self.assertTrue(shell.call_args.args[0].startswith("am broadcast -f 0x10000000 "))
        self.assertNotIn("--receiver-foreground", shell.call_args.args[0])
        self.assertEqual(shell.call_count, 1)
        self.assertIn("--es op codec_capabilities", shell.call_args.args[0])
        self.assertEqual(result["codecProfileCount"], 2)
        self.assertEqual(result["codecInterlaceCapability"], "not_reported_by_android")

    def test_background_broadcast_omits_foreground_intent_flags(self):
        c = AdbClient("1.2.3.4:5555", "opensagetv.vibe.miniclient.debug")
        output = 'Broadcast completed: result=1, data="ok=true;op=skip_check"\n'
        with patch.object(c, "shell", return_value=output) as shell:
            c.dev_control("skip_check", foreground=False, recovery_timeout_ms=180000)
        command = shell.call_args.args[0]
        self.assertTrue(command.startswith("am broadcast -a "))
        self.assertNotIn("0x10000000", command)
        self.assertNotIn("--receiver-foreground", command)
        self.assertEqual(shell.call_args.kwargs["timeout"], 210.0)
        self.assertEqual(shell.call_count, 1)

    def test_session_connect_and_exit_build_debug_broadcasts(self):
        c = AdbClient("1.2.3.4:5555", "opensagetv.vibe.miniclient.debug")
        connect_out = ('Broadcast completed: result=1, data="ok=true;op=connect;source=direct_address;'
                       'serverName=Test;serverAddress=192.168.1.2;serverPort=31099"\n')
        exit_out = 'Broadcast completed: result=1, data="ok=true;op=exit;wasConnected=true;destination=MainActivity"\n'
        with patch.object(c, "app_status", return_value={"foreground": True}), \
             patch.object(c, "shell", side_effect=[connect_out, exit_out]) as shell:
            connected = c.connect_server(server_name="Test", address="192.168.1.2", port=31099,
                                         save=True, renderer="gdx")
            exited = c.exit_session()
        self.assertEqual(connected["serverAddress"], "192.168.1.2")
        self.assertTrue(exited["wasConnected"])
        connect_command = shell.call_args_list[0].args[0]
        self.assertIn("--es op connect", connect_command)
        self.assertIn("--es server_name Test", connect_command)
        self.assertIn("--es address 192.168.1.2", connect_command)
        self.assertIn("--es save true", connect_command)
        self.assertIn("--es renderer gdx", connect_command)
        exit_command = shell.call_args_list[1].args[0]
        self.assertIn("--es op exit", exit_command)

    def test_session_connect_foregrounds_app_before_api30_receiver_activity_launch(self):
        c = AdbClient("1.2.3.4:5555", "opensagetv.vibe.miniclient.debug")
        connected = {"ok": True, "serverAddress": "192.168.1.2"}
        with patch.object(c, "app_status", side_effect=[
                {"foreground": False}, {"foreground": True}]) as status, \
             patch.object(c, "launch", return_value="Status: ok") as launch, \
             patch.object(c, "dev_control", return_value=connected) as control:
            result = c.connect_server(address="192.168.1.2", save=False, renderer="opengl")
        launch.assert_called_once_with()
        self.assertEqual(status.call_count, 2)
        control.assert_called_once_with(
            "connect", server_name="", address="192.168.1.2", port=31099,
            save="false", renderer="opengl")
        self.assertTrue(result["automationForegroundLaunched"])
        self.assertEqual(result["automationForegroundLaunchResult"], "Status: ok")

    def test_session_connect_reuses_matching_healthy_session(self):
        c = AdbClient("1.2.3.4:5555", "opensagetv.vibe.miniclient.debug")
        snapshot = {
            "connected": True,
            "serverName": "Existing",
            "serverAddress": "192.168.1.2",
            "serverPort": 31099,
        }
        with patch.object(c, "app_status", return_value={"foreground": True}), \
             patch.object(c, "player_state_snapshot", return_value=snapshot) as state, \
             patch.object(c, "dev_control") as control:
            result = c.connect_server(address="192.168.1.2", port=31099, save=False)
        state.assert_called_once_with()
        control.assert_not_called()
        self.assertTrue(result["alreadyConnected"])
        self.assertEqual(result["source"], "existing_session")
        self.assertEqual(result["serverAddress"], "192.168.1.2")

    def test_session_connect_does_not_reuse_session_for_renderer_switch(self):
        c = AdbClient("1.2.3.4:5555", "opensagetv.vibe.miniclient.debug")
        connected = {"ok": True, "serverAddress": "192.168.1.2"}
        with patch.object(c, "app_status", return_value={"foreground": True}), \
             patch.object(c, "player_state_snapshot") as state, \
             patch.object(c, "dev_control", return_value=connected) as control:
            c.connect_server(address="192.168.1.2", save=False, renderer="opengl")
        state.assert_not_called()
        control.assert_called_once_with(
            "connect", server_name="", address="192.168.1.2", port=31099,
            save="false", renderer="opengl")

    def test_android_skip_check_builds_debug_broadcast_and_parses_delta(self):
        c = AdbClient("1.2.3.4:5555", "opensagetv.vibe.miniclient.debug")
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
        c = AdbClient("1.2.3.4:5555", "opensagetv.vibe.miniclient.debug")
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
        c = AdbClient("1.2.3.4:5555", "opensagetv.vibe.miniclient.debug")
        output = ('Broadcast completed: result=1, data="ok=true;op=comskip_check;direction=right;'
                  'recoveryTimeoutMs=180000;outputHealthy=false"\n')
        with patch.object(c, "shell", return_value=output) as shell:
            c.android_comskip_check("right", recovery_timeout_ms=180000, verify_playback_ms=3000)
        self.assertGreaterEqual(shell.call_args.kwargs["timeout"], 213.0)

    def test_direct_debug_text_player_relative_seek_and_comskip_build_broadcasts(self):
        c = AdbClient("1.2.3.4:5555", "opensagetv.vibe.miniclient.debug")
        outputs = [
            'Events injected: 8\n',
            'Broadcast completed: result=1, data="ok=true;op=ime_hide;requested=true"\n',
            'Broadcast completed: result=1, data="ok=true;op=player_control;action=pause;accepted=true"\n',
            'Broadcast completed: result=1, data="ok=true;op=frame_step;amount=1;accepted=true"\n',
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
            self.assertTrue(c.frame_step(1)["accepted"])
            self.assertEqual(c.seek_relative(10000)["deltaMs"], 10000)
            self.assertEqual(c.comskip("right")["direction"], "right")
            self.assertTrue(c.android_relative_seek_check([10000, -10000])["outputHealthy"])
        commands = [call.args[0] for call in shell.call_args_list]
        self.assertEqual(commands[0], "input text test")
        self.assertIn("--es op ime_hide", commands[1])
        self.assertIn("--es op player_control", commands[2])
        self.assertIn("--es action pause", commands[2])
        self.assertIn("--es op frame_step", commands[3])
        self.assertIn("--es amount 1", commands[3])
        self.assertIn("--es op seek_relative", commands[4])
        self.assertIn("--es delta_ms 10000", commands[4])
        self.assertIn("--es op comskip", commands[5])
        self.assertIn("--es direction right", commands[5])
        self.assertIn("--es op relative_seek_check", commands[6])
        self.assertIn("--es deltas_ms 10000,-10000", commands[6])

    def test_frame_step_rejects_zero_before_broadcast(self):
        c = AdbClient("1.2.3.4:5555", "opensagetv.vibe.miniclient.debug")
        with self.assertRaisesRegex(ValueError, "non-zero"):
            c.frame_step(0)

    def test_parses_player_telemetry_line_for_future_reuse(self):
        line = "08-26 03:00:00.000 I SageTVDevTelemetry: player=EXO2 event=SNAPSHOT positionMs=12345 isPlaying=true outputFps=29.97"
        event = parse_telemetry_line(line)
        self.assertIsNotNone(event)
        self.assertEqual(event["player"], "EXO2")
        self.assertEqual(event["positionMs"], 12345)

    def test_player_telemetry_is_disabled_during_baseline_recovery(self):
        c = AdbClient("1.2.3.4:5555", "opensagetv.vibe.miniclient.debug")
        telemetry = c.player_telemetry(max_events=10)
        self.assertEqual(telemetry["transport"], "disabled-pretelemetry-baseline")
        self.assertEqual(telemetry["event_count"], 0)
        self.assertIsNone(telemetry["active_player"])

    def test_wait_for_player_event_is_disabled_during_baseline_recovery(self):
        c = AdbClient("1.2.3.4:5555", "opensagetv.vibe.miniclient.debug")
        with self.assertRaises(RuntimeError):
            c.wait_for_player_event("READY", timeout_s=0.1)

    def test_seek_time_builds_required_target_broadcast(self):
        c = AdbClient("1.2.3.4:5555", "opensagetv.vibe.miniclient.debug")
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
