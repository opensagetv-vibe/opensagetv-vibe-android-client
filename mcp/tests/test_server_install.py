import unittest
from sagetv_dev_mcp import server


class ServerInstallConfigurationTests(unittest.TestCase):
    def test_selected_device_install_budget_reaches_server_adb_client(self):
        self.assertEqual(server.adb.install_timeout_seconds, server.cfg.install_timeout_seconds)


if __name__ == "__main__":
    unittest.main()
