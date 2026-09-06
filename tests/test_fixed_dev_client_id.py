from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
DEV = ROOT / "source/dev"
EXAMPLE = "44:45:56:30:30:31"


class ClientIdBehaviorTests(unittest.TestCase):
    def test_fixed_id_constant_removed(self):
        text = (DEV / "core/src/main/java/opensagetv/vibe/miniclient/util/ClientIDGenerator.java").read_text(encoding="utf-8")
        self.assertNotIn("DEV_FIXED_CLIENT_ID", text)
        self.assertIn("public String generateId()", text)

    def test_android_resolver_generates_once_and_persists(self):
        text = (DEV / "android-shared/src/main/java/opensagetv/vibe/miniclient/android/UIActivityLifeCycleHandler.java").read_text(encoding="utf-8")
        block = text[text.index("public String getMACAddress()") : text.index("public PlayerSurfaceView getVideoView")]
        self.assertIn("client.properties().getString(Keys.client_id)", block)
        self.assertIn("id = gen.generateId();", block)
        self.assertIn("client.properties().setString(Keys.client_id, id);", block)
        self.assertIn("return id;", block)

    def test_shared_random_resolver_generates_once_and_persists(self):
        text = (DEV / "core/src/main/java/opensagetv/vibe/miniclient/util/RandomMACAddressResolver.java").read_text(encoding="utf-8")
        self.assertIn("prefStore.getString(PrefStore.Keys.client_id)", text)
        self.assertIn("id = gen.generateId();", text)
        self.assertIn("prefStore.setString(PrefStore.Keys.client_id, id);", text)

    def test_connection_layer_restores_original_server_override(self):
        text = (DEV / "core/src/main/java/opensagetv/vibe/miniclient/MiniClientConnection.java").read_text(encoding="utf-8")
        self.assertIn("this.myID = myID;", text)
        self.assertIn("this.myID = msi.macAddress;", text)
        self.assertNotIn("Using fixed Dev CLIENT ID", text)
        self.assertIn("public String getClientID()", text)

    def test_settings_restore_editable_client_id(self):
        text = (DEV / "android-shared/src/main/java/opensagetv/vibe/miniclient/android/ui/settings/SettingsFragment.java").read_text(encoding="utf-8")
        self.assertIn("prefs.setString(Keys.client_id, gen.generateId());", text)
        self.assertIn("clientid.setOnPreferenceChangeListener", text)
        self.assertNotIn("clientid.setEnabled(false);", text)

    def test_current_test_id_example_is_documented(self):
        readme = (ROOT / "README.md").read_text(encoding="utf-8")
        self.assertIn(EXAMPLE, readme)
        self.assertIn("DEV001", readme)


if __name__ == "__main__":
    unittest.main()
