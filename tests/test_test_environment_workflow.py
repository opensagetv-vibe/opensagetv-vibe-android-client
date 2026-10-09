from pathlib import Path
import sys
import unittest

try:
    import tomllib
except ModuleNotFoundError:
    import tomli as tomllib


ROOT = Path(__file__).resolve().parents[1]


class TestEnvironmentWorkflowTests(unittest.TestCase):
    def test_all_present_vibe_project_task_workflows_share_server_boundary_policy(self):
        expected = None
        checked = 0
        # The reusable container mounts this checkout as android-client,
        # not its GitHub repository name. Always validate the owning checkout;
        # additionally validate sibling Vibe repos when present on the host.
        projects = {ROOT, *ROOT.parent.glob("opensagetv-vibe-*")}
        for project in sorted(projects):
            if not project.is_dir():
                continue
            for name in ("AGENTS.md", "WORKFLOW.md"):
                path = project / name
                self.assertTrue(path.is_file(), f"New active Vibe project lacks {path}")
                text = path.read_text(encoding="utf-8")
                marker = "## Task fix and server-boundary policy\n"
                self.assertIn(marker, text, str(path))
                tail = text.split(marker, 1)[1]
                # Policy is the first subsection; stop at the following blank
                # line separating it from that project's preserved instructions.
                block = tail.split("\n\n\n", 1)[0]
                # The block ends after the same explicit authority boundary,
                # irrespective of the next pre-existing heading/prose layout.
                ending = "interruption of recordings/other users still require their own authority."
                self.assertIn(ending, block, str(path))
                block = block[:block.index(ending) + len(ending)]
                if expected is None:
                    expected = block
                self.assertEqual(expected, block, str(path))
                checked += 1
        self.assertGreaterEqual(checked, 2)  # Own standalone checkout remains covered.

    def test_proven_plugin_dependency_fix_is_authorized_without_repeat_approval(self):
        rules = (ROOT / "AGENTS.md").read_text(encoding="utf-8")
        workflow = (ROOT / "WORKFLOW.md").read_text(encoding="utf-8")
        self.assertIn("Do not ask again solely because the fix crosses", rules)
        self.assertIn("fix and test the", workflow)
        self.assertIn("without asking again solely for", workflow)
        for text in (rules, workflow):
            self.assertIn("plugin installation/update", text)
            self.assertIn(".175", text)
            self.assertIn("user settings" if text is rules else "preserved settings", text)

    def test_non_stock_core_fix_requires_proven_client_plugin_gap(self):
        rules = (ROOT / "AGENTS.md").read_text(encoding="utf-8")
        workflow = (ROOT / "WORKFLOW.md").read_text(encoding="utf-8")
        for text in (rules, workflow):
            self.assertIn(".232", text)
            self.assertIn("production", text)
            self.assertIn("stock/older-client", text)
            self.assertIn(".175", text)
            self.assertIn("Non-stock `.232` restarts are authorized", text)
            self.assertIn("Always ask the user before restarting stock `.175`", text)
            self.assertIn("bounded restart window is active", text)
            self.assertIn("check expiry and revocation", text)
        self.assertIn("Core remains the last option", workflow)

    def test_focused_android_gradle_documents_jdk17_not_core_default(self):
        workflow = (ROOT / "WORKFLOW.md").read_text(encoding="utf-8")
        self.assertIn("-e JAVA_HOME=/opt/java/jdk17 -e JDK_HOME=/opt/java/jdk17", workflow)
        self.assertIn("default JDK is **11 for SageTV Core**", workflow)
        self.assertIn("bash ./gradlew <tasks>", workflow)

    def test_explicit_adb_serial_survives_native_and_linux_container_boundaries(self):
        windows = (ROOT / "dev.ps1").read_text(encoding="utf-8")
        linux = (ROOT / "dev.sh").read_text(encoding="utf-8")
        # Both Windows fast path and WSL export list must forward the override;
        # otherwise readiness for a new tablet silently targets active Fire TV.
        self.assertEqual(2, windows.count("'SAGETV_ADB_SERIAL'"))
        self.assertIn("SAGETV_TEST_DEVICE_ALIAS SAGETV_ADB_SERIAL SAGETV_TEST_SERVER_ALIAS", linux)

    def test_tracked_example_is_complete_and_sanitized(self):
        path = ROOT / "config/firetv.example.toml"
        data = tomllib.loads(path.read_text(encoding="utf-8"))
        self.assertEqual(data["schema"], 2)
        for section in ("devices", "servers", "fixtures", "capture", "test_defaults", "safety"):
            self.assertIn(section, data)
        self.assertIn("smb_url", data["servers"]["vibe"])
        self.assertIn("smb_url", data["servers"]["stock"])
        self.assertEqual(data["servers"]["stock"]["media_selection_mode"], "stock_web")
        self.assertIs(data["servers"]["stock"]["webserver_installed"], True)
        self.assertIn(data["servers"]["vibe"]["media_selection_mode"], ("auto", "vibe_exact_path"))
        self.assertIsInstance(data["servers"]["vibe"]["webserver_installed"], bool)
        cases = data["fixtures"]["cases"]
        ids = {case["id"] for case in cases}
        self.assertEqual(len(ids), len(cases))
        self.assertTrue({
            "seek_caption", "pbs_av_sync", "authored_dvd", "dvd_motion", "long_ota_mpeg2",
            "stock_mkv_one", "stock_mkv_two", "stock_mkv_three",
            "hardware_codec_matrix", "uk_taskmaster", "uk_breakfast",
            "uk_classic_holby",
        }.issubset(ids))
        for case in cases:
            self.assertIsInstance(case["enabled"], bool, case["id"])
            self.assertIn(case["path_type"], ("server_path", "server_root", "search"))
            self.assertTrue(case["path"])
            self.assertTrue(case["modes"])
            self.assertTrue(all(isinstance(value, bool) for value in case["modes"].values()))
        av_sync = next(case for case in cases if case["id"] == "pbs_av_sync")
        self.assertEqual(av_sync["path_type"], "server_path")
        self.assertEqual(av_sync["expected_video_mime"], "video/mpeg2")
        self.assertEqual(av_sync["expected_audio_mime"], "audio/ac3")
        self.assertTrue(av_sync["modes"]["physical_capture"])
        text = path.read_text(encoding="utf-8")
        self.assertNotIn("seek_server_path", text)
        self.assertNotIn("caption_server_path", text)
        text = path.read_text(encoding="utf-8")
        self.assertNotIn("192.168.10.", text)
        self.assertNotIn('web_password = "frey"', text)
        self.assertNotIn('password = "sagetv"', text)

    def test_local_configuration_is_ignored_and_workflow_validates_it(self):
        ignore = (ROOT / ".gitignore").read_text(encoding="utf-8")
        dev = (ROOT / "dev.sh").read_text(encoding="utf-8")
        entrypoint = (ROOT / "docker/entrypoint.sh").read_text(encoding="utf-8")
        self.assertIn("/config/firetv.toml", ignore)
        self.assertIn("config-check)", dev)
        self.assertIn("test_environment_config.py", dev)
        self.assertIn("config-check)", entrypoint)

    def test_raw_adb_is_scoped_to_the_active_device(self):
        shell = (ROOT / "dev.sh").read_text(encoding="utf-8")
        self.assertIn("configured_device_serial", shell)
        self.assertIn('dev_exec adb -s "$serial" "$@"', shell)
        self.assertIn('run_scoped_adb "$@"', shell)
        self.assertIn("`adb logcat -d` for dump-and-exit", shell)
        self.assertIn("*)\n        break", shell)
        fast_path = (ROOT / "scripts/container_scoped_adb.sh").read_text(encoding="utf-8")
        self.assertIn('exec adb -s "$serial" "$@"', fast_path)
        self.assertIn("*)\n      break", fast_path)

    def test_documented_adb_path_uses_unified_wrapper(self):
        workflow = (ROOT / "WORKFLOW.md").read_text(encoding="utf-8")
        agent_rules = (ROOT / "AGENTS.md").read_text(encoding="utf-8")
        environment = (ROOT / "docs/TEST_ENVIRONMENT.md").read_text(encoding="utf-8")
        for text in (workflow, agent_rules, environment):
            self.assertIn("dev.cmd connect", text)
            self.assertIn("dev.cmd adb", text)
            self.assertIn("opensagetv-vibe-dev", text)
        self.assertIn("Do not install, discover, or invoke a bare host `adb`", workflow)

    def test_windows_adb_uses_running_unified_container_before_wsl(self):
        powershell = (ROOT / "dev.ps1").read_text(encoding="utf-8")
        helper = (ROOT / "scripts/container_scoped_adb.sh").read_text(encoding="utf-8")
        workflow = (ROOT / "WORKFLOW.md").read_text(encoding="utf-8")
        environment = (ROOT / "docs/TEST_ENVIRONMENT.md").read_text(encoding="utf-8")
        direct_at = powershell.index("if ($commandName -in @('connect', 'adb'))")
        wsl_at = powershell.index("Get-Command wsl.exe")
        self.assertLess(direct_at, wsl_at)
        self.assertIn("opensagetv-vibe-dev", powershell)
        self.assertIn("container_scoped_adb.sh", powershell)
        self.assertIn("ANDROID_USER_HOME=/workspace/android-client/adb", powershell)
        self.assertIn("SAGETV_WORKSPACE=/workspace/android-client", powershell)
        self.assertIn("SAGETV_WORKSPACE=/workspace/android-client", workflow)
        self.assertIn("SAGETV_WORKSPACE", environment)
        self.assertIn("ADB_VENDOR_KEYS=/workspace/android-client/adb", powershell)
        self.assertIn('exec adb -s "$serial" "$@"', helper)

    def test_physical_scripts_use_selected_server_instead_of_private_address(self):
        offenders = []
        for path in sorted((ROOT / "scripts").glob("mcp_*.py")):
            text = path.read_text(encoding="utf-8")
            if "192.168.10.232" in text:
                offenders.append(path.name)
        self.assertEqual(offenders, [])

    def test_one_command_commissioning_is_safe_and_cross_platform(self):
        cmd = (ROOT / "commission_test_environment.cmd").read_text(encoding="utf-8")
        powershell = (ROOT / "scripts/commission-test-environment.ps1").read_text(encoding="utf-8")
        shell = (ROOT / "commission_test_environment.sh").read_text(encoding="utf-8")
        guide = (ROOT / "docs/COMMISSIONING.md").read_text(encoding="utf-8")
        self.assertIn("commission-test-environment.ps1", cmd)
        for text in (powershell, shell):
            self.assertIn("config-check", text)
            self.assertIn("preflight", text)
            self.assertIn("seek-fixture", text)
            self.assertIn("codec-fixtures", text)
            self.assertIn("dvd-fixture", text)
            self.assertIn("config/firetv", text.replace("\\", "/"))
        self.assertIn("does not overwrite a remote share automatically", guide)


if __name__ == "__main__":
    unittest.main()
