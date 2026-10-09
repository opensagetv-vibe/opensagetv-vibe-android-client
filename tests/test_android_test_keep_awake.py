from __future__ import annotations

from pathlib import Path
import sys
import tempfile
import unittest
from unittest import mock

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "mcp" / "src"))

from scripts import android_test_keep_awake as keep_awake


class AndroidTestKeepAwakeTests(unittest.TestCase):
    def setUp(self):
        self.values = {
            ("global", "stay_on_while_plugged_in"): "0",
            ("system", "screen_off_timeout"): "60000",
        }

    def fake_adb(self, serial: str, *arguments: str, check: bool = True) -> str:
        self.assertEqual("test-device:5555", serial)
        if arguments[:2] != ("shell", "settings"):
            return ""
        action, namespace, name = arguments[2:5]
        key = (namespace, name)
        if action == "get":
            return self.values.get(key, "null")
        if action == "put":
            self.values[key] = arguments[5]
            return ""
        if action == "delete":
            self.values.pop(key, None)
            return ""
        self.fail(f"unexpected ADB settings action: {action}")

    def test_begin_and_restore_preserve_exact_user_values(self):
        with tempfile.TemporaryDirectory() as directory, mock.patch.object(
            keep_awake, "adb", side_effect=self.fake_adb
        ):
            checkpoint = Path(directory) / "keep-awake.json"
            started = keep_awake.begin("test-device:5555", checkpoint)
            self.assertTrue(started["active"])
            self.assertEqual("7", self.values[("global", "stay_on_while_plugged_in")])
            self.assertEqual("2147483647", self.values[("system", "screen_off_timeout")])
            restored = keep_awake.restore("test-device:5555", checkpoint)
            self.assertTrue(restored["restored"])
            self.assertEqual("0", self.values[("global", "stay_on_while_plugged_in")])
            self.assertEqual("60000", self.values[("system", "screen_off_timeout")])
            self.assertFalse(checkpoint.exists())

    def test_new_begin_recovers_interrupted_session_before_snapshot(self):
        with tempfile.TemporaryDirectory() as directory, mock.patch.object(
            keep_awake, "adb", side_effect=self.fake_adb
        ):
            checkpoint = Path(directory) / "keep-awake.json"
            keep_awake.begin("test-device:5555", checkpoint, "automated")
            restarted = keep_awake.begin("test-device:5555", checkpoint, "automated")
            self.assertTrue(restarted["recoveredInterruptedSession"])
            keep_awake.restore("test-device:5555", checkpoint)
            self.assertEqual("0", self.values[("global", "stay_on_while_plugged_in")])
            self.assertEqual("60000", self.values[("system", "screen_off_timeout")])

    def test_absent_setting_is_deleted_again_on_restore(self):
        self.values.pop(("global", "stay_on_while_plugged_in"))
        with tempfile.TemporaryDirectory() as directory, mock.patch.object(
            keep_awake, "adb", side_effect=self.fake_adb
        ):
            checkpoint = Path(directory) / "keep-awake.json"
            keep_awake.begin("test-device:5555", checkpoint)
            keep_awake.restore("test-device:5555", checkpoint)
            self.assertNotIn(("global", "stay_on_while_plugged_in"), self.values)

    def test_automated_gate_borrows_manual_session_without_closing_it(self):
        with tempfile.TemporaryDirectory() as directory, mock.patch.object(
            keep_awake, "adb", side_effect=self.fake_adb
        ):
            checkpoint = Path(directory) / "keep-awake.json"
            manual = keep_awake.begin("test-device:5555", checkpoint, "manual")
            self.assertFalse(manual["borrowed"])
            automated = keep_awake.begin("test-device:5555", checkpoint, "automated")
            self.assertTrue(automated["borrowed"])
            automated_end = keep_awake.end("test-device:5555", checkpoint, "automated")
            self.assertTrue(automated_end["borrowed"])
            self.assertTrue(checkpoint.exists())
            self.assertEqual("7", self.values[("global", "stay_on_while_plugged_in")])
            manual_end = keep_awake.end("test-device:5555", checkpoint, "manual")
            self.assertTrue(manual_end["restored"])
            self.assertFalse(checkpoint.exists())
            self.assertEqual("0", self.values[("global", "stay_on_while_plugged_in")])
            self.assertEqual("60000", self.values[("system", "screen_off_timeout")])


if __name__ == "__main__":
    unittest.main()
