from pathlib import Path
import sys
import unittest

try:
    import tomllib
except ModuleNotFoundError:
    import tomli as tomllib


ROOT = Path(__file__).resolve().parents[1]


class TestEnvironmentWorkflowTests(unittest.TestCase):
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
            "seek_caption", "authored_dvd", "dvd_motion", "long_ota_mpeg2",
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
