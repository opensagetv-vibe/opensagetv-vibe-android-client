from pathlib import Path
import os
import tempfile
import unittest
from unittest.mock import patch

from sagetv_dev_mcp.config import TestEnvironment, load_test_environment


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
web_username = "local-user"
web_password = "local-secret"
smb_url = "smb://192.0.2.20/media/"
smb_username = "share-user"
smb_password = "share-secret"
smb_mappings = [{ sage_prefix = "/var/media/", smb_root = "smb://192.0.2.20/media/" }]

[servers.stock]
alias = "compatibility"
address = "192.0.2.21"

[test_defaults]
live_channels = ["2.1", "5.1"]
'''


class TestEnvironmentConfigTests(unittest.TestCase):
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
        self.assertEqual(environment.validate(), [])

    def test_environment_alias_overrides_do_not_modify_toml(self):
        environment = self.load()
        with patch.dict(os.environ, {
            "SAGETV_TEST_DEVICE_ALIAS": "bedroom",
            "SAGETV_TEST_SERVER_ALIAS": "compatibility",
        }):
            self.assertEqual(environment.device_serial(), "192.0.2.11:5555")
            self.assertEqual(environment.server_address(), "192.0.2.21")

    def test_schema_one_device_remains_compatible(self):
        environment = self.load(b'device = "192.0.2.30:5555"\ndev_package = "opensagetv.vibe.miniclient.debug"\n')
        self.assertEqual(environment.device_serial(), "192.0.2.30:5555")
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
        errors = TestEnvironment(data).validate()
        self.assertIn("devices aliases must be unique: living-room", errors)
        self.assertIn("servers.vibe.smb_mappings[1] requires sage_prefix and smb_root", errors)


if __name__ == "__main__":
    unittest.main()
