from types import SimpleNamespace
import unittest
from unittest.mock import Mock, patch

from sagetv_dev_mcp import cli
from sagetv_dev_mcp.adb import AdbClient
from sagetv_dev_mcp.config import Config


class CliCleanupTests(unittest.TestCase):
    def setUp(self):
        self.adb = Mock(spec=AdbClient)
        self.adb.device_info.return_value = {"ro.product.model": "MINIMX"}
        factory = patch.object(cli, "client", return_value=(self.adb, SimpleNamespace()))
        factory.start()
        self.addCleanup(factory.stop)
        output = patch("builtins.print")
        output.start()
        self.addCleanup(output.stop)

    def invoke(self, *arguments):
        with patch("sys.argv", ["sagetv-cli", *arguments]):
            return cli.main()

    def test_connect_early_return_closes_owned_shell_once(self):
        self.assertEqual(self.invoke("connect"), 0)
        self.adb.connect.assert_called_once_with()
        self.adb.devices.assert_called_once_with()
        self.adb.device_info.assert_called_once_with()
        self.adb.close.assert_called_once_with()
        self.adb.run.assert_not_called()

    def test_ordinary_dispatch_preserves_arguments_and_closes_once(self):
        self.assertEqual(self.invoke("logcat", "--lines", "12", "--pattern", "SageTV"), 0)
        self.adb.connect.assert_called_once_with()
        self.adb.logcat_tail.assert_called_once_with(12, "SageTV")
        self.adb.close.assert_called_once_with()

    def test_connection_exception_is_preserved_and_closes_once(self):
        for command in ("connect", "device-info"):
            with self.subTest(command=command):
                self.adb.reset_mock()
                self.adb.connect.side_effect = RuntimeError("unauthorized")
                with self.assertRaisesRegex(RuntimeError, "unauthorized"):
                    self.invoke(command)
                self.adb.connect.assert_called_once_with()
                self.adb.device_info.assert_not_called()
                self.adb.close.assert_called_once_with()
                self.adb.run.assert_not_called()

    def test_device_info_exception_is_preserved_and_closes_once(self):
        for command in ("connect", "device-info"):
            with self.subTest(command=command):
                self.adb.reset_mock()
                self.adb.device_info.side_effect = RuntimeError("device offline")
                with self.assertRaisesRegex(RuntimeError, "device offline"):
                    self.invoke(command)
                self.adb.connect.assert_called_once_with()
                self.adb.device_info.assert_called_once_with()
                self.adb.close.assert_called_once_with()
                self.adb.run.assert_not_called()


class CliClientConstructionTests(unittest.TestCase):
    def test_device_install_budget_reaches_owned_adb_client(self):
        config = Config(device="192.0.2.10:5555", install_timeout_seconds=600)
        with patch.object(cli, "load_config", return_value=config), \
                patch.object(cli, "AdbClient") as constructor:
            client, returned_config = cli.client()
        self.assertIs(client, constructor.return_value)
        self.assertIs(returned_config, config)
        self.assertEqual(constructor.call_args.kwargs["install_timeout_seconds"], 600)


if __name__ == "__main__":
    unittest.main()
