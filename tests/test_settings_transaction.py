from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
DEBUG = ROOT / "source/dev/android-tv/src/debug/java/opensagetv/vibe/miniclient/android/tv/debug"


class SettingsTransactionContracts(unittest.TestCase):
    def test_device_local_checkpoint_preserves_all_supported_preference_types(self):
        source = (DEBUG / "DebugSettingsCheckpoint.java").read_text(encoding="utf-8")
        self.assertIn("PreferenceManager.getDefaultSharedPreferences(context).getAll()", source)
        self.assertIn("context.getSharedPreferences(BACKUP_NAME", source)
        self.assertIn("target.edit().clear()", source)
        for token in ("putString(", "putBoolean(", "putInt(", "putLong(",
                      "putFloat(", "putStringSet("):
            self.assertIn(token, source)
        self.assertIn("valuesRemainPrivate=true", source)
        self.assertNotIn("entry.getValue().toString", source)

    def test_debug_and_mcp_surfaces_expose_checkpoint_and_restore(self):
        receiver = (DEBUG / "DevTestReceiver.java").read_text(encoding="utf-8")
        adb = (ROOT / "mcp/src/sagetv_dev_mcp/adb.py").read_text(encoding="utf-8")
        server = (ROOT / "mcp/src/sagetv_dev_mcp/server.py").read_text(encoding="utf-8")
        self.assertIn('"settings_checkpoint".equals(op)', receiver)
        self.assertIn('"settings_restore".equals(op)', receiver)
        self.assertIn('return self.dev_control("settings_checkpoint")', adb)
        self.assertIn('return self.dev_control("settings_restore")', adb)
        self.assertIn("def dev_checkpoint_settings()", server)
        self.assertIn("def dev_restore_settings()", server)

    def test_every_official_automated_mcp_test_restores_settings(self):
        dev = (ROOT / "dev.sh").read_text(encoding="utf-8")
        start = dev.index("run_automated_mcp_test()")
        end = dev.index("\n}\n", start)
        function = dev[start:end]
        stale_restore = function.index('mcp_settings_transaction.py" restore')
        checkpoint = function.index('mcp_settings_transaction.py" checkpoint')
        client_id = function.index('mcp_client_id.py" --ensure')
        test_script = function.index('scripts/$script')
        final_restore = function.rindex('mcp_settings_transaction.py" restore')
        self.assertLess(stale_restore, checkpoint)
        self.assertLess(checkpoint, client_id)
        self.assertLess(client_id, test_script)
        self.assertLess(test_script, final_restore)
        self.assertIn("set +e", function)
        self.assertIn("set -e", function)
        self.assertIn('return "$restore_status"', function)


if __name__ == "__main__":
    unittest.main()
