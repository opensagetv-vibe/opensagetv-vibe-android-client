from pathlib import Path
import os
import tempfile
import unittest
from unittest.mock import patch

from sagetv_dev_mcp.config import TestEnvironment, load_config, load_test_environment


SCHEMA_2 = b'''schema = 2
active_device = "primary"
active_server = "vibe"
dev_package = "opensagetv.vibe.miniclient.debug"

[identities]
automated_client_id = "44:45:56:30:30:31"

[devices.primary]
alias = "living-room"
serial = "192.0.2.10:5555"

[devices.secondary]
alias = "bedroom"
serial = "192.0.2.11:5555"

[servers.vibe]
alias = "development"
address = "192.0.2.20"
media_selection_mode = "auto"
webserver_installed = true
web_username = "local-user"
web_password = "local-secret"
smb_url = "smb://192.0.2.20/media/"
smb_username = "share-user"
smb_password = "share-secret"
smb_mappings = [{ sage_prefix = "/var/media/", smb_root = "smb://192.0.2.20/media/" }]

[servers.stock]
alias = "compatibility"
address = "192.0.2.21"
media_selection_mode = "stock_web"
webserver_installed = true

[fixtures]

[[fixtures.cases]]
id = "seek_caption"
enabled = true
path_type = "server_path"
path = "/var/media/tests/seek.ts"
modes = { seek = true, caption = false }

[[fixtures.cases]]
id = "stock_mkv_one"
enabled = true
path_type = "search"
path = "Example MKV"
modes = { stock_mkv = true, seek = true }

[[fixtures.cases]]
id = "stock_mkv_disabled"
enabled = false
path_type = "search"
path = "Disabled MKV"
modes = { stock_mkv = true }

[test_defaults]
live_channels = ["2.1", "5.1"]
'''


class TestEnvironmentConfigTests(unittest.TestCase):
    def setUp(self):
        # Synthetic fixture selections must not inherit the operator's device
        # or server. Nested patch.dict contexts can still exercise overrides.
        selection = patch.dict(os.environ)
        selection.start()
        self.addCleanup(selection.stop)
        for name in (
            "SAGETV_TEST_DEVICE_ALIAS",
            "SAGETV_TEST_SERVER_ALIAS",
            "SAGETV_ADB_SERIAL",
            "SAGETV_TEST_SERVER_ADDRESS",
        ):
            os.environ.pop(name, None)

    def load(self, content: bytes = SCHEMA_2):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        path = Path(temp.name) / "firetv.toml"
        path.write_bytes(content)
        return load_test_environment(path)

    def test_schema_two_supports_multiple_named_and_aliased_entries(self):
        environment = self.load()
        self.assertEqual(environment.device_serial(), "192.0.2.10:5555")
        self.assertEqual(environment.device("bedroom")["serial"], "192.0.2.11:5555")
        self.assertEqual(environment.server_address(), "192.0.2.20")
        self.assertEqual(environment.smb_share()["url"], "smb://192.0.2.20/media/")
        self.assertEqual(environment.server("compatibility")["address"], "192.0.2.21")
        self.assertEqual(environment.fixture("seek_server_path"), "/var/media/tests/seek.ts")
        self.assertTrue(environment.fixture_enabled("seek_caption", mode="seek"))
        self.assertFalse(environment.fixture_enabled("seek_caption", mode="caption"))
        self.assertEqual(environment.fixture_list("mkv_searches"), ["Example MKV"])
        self.assertEqual(environment.validate(), [])

    def test_environment_alias_overrides_do_not_modify_toml(self):
        environment = self.load()
        with patch.dict(os.environ, {
            "SAGETV_TEST_DEVICE_ALIAS": "bedroom",
            "SAGETV_TEST_SERVER_ALIAS": "compatibility",
        }):
            self.assertEqual(environment.device_serial(), "192.0.2.11:5555")
            self.assertEqual(environment.server_address(), "192.0.2.21")

    def test_device_install_budget_defaults_and_alias_selection(self):
        environment = self.load()
        self.assertEqual(environment.install_timeout_seconds(), 180)
        environment.data["devices"]["secondary"]["install_timeout_seconds"] = 600
        self.assertEqual(environment.install_timeout_seconds("bedroom"), 600)
        self.assertEqual(environment.install_timeout_seconds(), 180)
        with patch.dict(os.environ, {"SAGETV_TEST_DEVICE_ALIAS": "bedroom"}), \
                patch("sagetv_dev_mcp.config.load_test_environment", return_value=environment), \
                patch.object(Path, "mkdir"):
            self.assertEqual(load_config().install_timeout_seconds, 600)

    def test_invalid_device_install_budgets_are_rejected(self):
        environment = self.load()
        for value in (True, False, "600", None, 29, 901, float("nan"), float("inf"), -float("inf")):
            with self.subTest(value=value):
                environment.data["devices"]["secondary"]["install_timeout_seconds"] = value
                self.assertIn(
                    "devices.secondary.install_timeout_seconds must be a finite number from 30 to 900",
                    environment.validate(),
                )
                with self.assertRaises(ValueError):
                    environment.install_timeout_seconds("bedroom")

    def test_device_install_budget_accepts_finite_boundaries_and_fraction(self):
        environment = self.load()
        for value in (30, 900, 180.5):
            with self.subTest(value=value):
                environment.data["devices"]["primary"]["install_timeout_seconds"] = value
                self.assertEqual(environment.validate(), [])
                self.assertEqual(environment.install_timeout_seconds(), value)

    def test_schema_one_device_remains_compatible(self):
        environment = self.load(b'device = "192.0.2.30:5555"\ndev_package = "opensagetv.vibe.miniclient.debug"\n')
        self.assertEqual(environment.device_serial(), "192.0.2.30:5555")
        self.assertEqual(environment.install_timeout_seconds(), 180)
        self.assertEqual(environment.validate(), [])

    def test_redacted_summary_never_returns_credentials(self):
        redacted = self.load().redacted()
        self.assertEqual(redacted["servers"]["vibe"]["web_username"], "<redacted>")
        self.assertEqual(redacted["servers"]["vibe"]["web_password"], "<redacted>")
        self.assertEqual(redacted["servers"]["vibe"]["smb_password"], "<redacted>")
        self.assertNotIn("local-secret", str(redacted))
        self.assertNotIn("share-secret", str(redacted))

    def test_duplicate_alias_and_incomplete_mapping_are_actionable(self):
        data = self.load().data
        data["devices"]["secondary"]["alias"] = "living-room"
        data["servers"]["vibe"]["smb_mappings"].append({"sage_prefix": "/missing/root"})
        data["servers"]["stock"]["media_selection_mode"] = "magic"
        data["servers"]["stock"]["webserver_installed"] = "yes"
        errors = TestEnvironment(data).validate()
        self.assertIn("devices aliases must be unique: living-room", errors)
        self.assertIn("servers.vibe.smb_mappings[1] requires sage_prefix and smb_root", errors)
        self.assertIn(
            "servers.stock.media_selection_mode must be auto, stock_web, or vibe_exact_path",
            errors,
        )
        self.assertIn("servers.stock.webserver_installed must be true or false", errors)

    def test_invalid_fixture_case_switches_are_actionable(self):
        data = self.load().data
        data["fixtures"]["cases"][0]["enabled"] = "yes"
        data["fixtures"]["cases"][0]["modes"]["seek"] = "yes"
        data["fixtures"]["cases"][1]["id"] = "seek_caption"
        errors = TestEnvironment(data).validate()
        self.assertIn("fixtures.cases[0].enabled must be true or false", errors)
        self.assertIn("fixtures.cases[0].modes values must be true or false", errors)
        self.assertIn("fixtures.cases ids must be unique: seek_caption", errors)


if __name__ == "__main__":
    unittest.main()
