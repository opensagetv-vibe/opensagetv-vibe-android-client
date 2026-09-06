from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
CORE = ROOT / "source/dev/core/src/main/java/opensagetv/vibe/miniclient"
ANDROID = ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android"


class ConfigurationTelemetryExtractionTests(unittest.TestCase):
    def test_connection_capability_configuration_has_one_owner(self):
        connection = (CORE / "MiniClientConnection.java").read_text(encoding="utf-8")
        profile = (CORE / "ConnectionCapabilityProfile.java").read_text(encoding="utf-8")

        self.assertIn("ConnectionCapabilityProfile.discover(client, log)", connection)
        self.assertNotIn("private List<String> videoCodecs", connection)
        self.assertNotIn("Properties loadProperties", connection)
        self.assertIn('loadProperties("common.profile", log)', profile)
        self.assertIn("client.prepareCodecs(video, audio, push, pull)", profile)
        self.assertIn("String profileProperty", profile)

    def test_activity_session_configuration_is_loaded_once(self):
        handler = (ANDROID / "UIActivityLifeCycleHandler.java").read_text(encoding="utf-8")
        config = (ANDROID / "UiSessionConfiguration.java").read_text(encoding="utf-8")

        self.assertIn("UiSessionConfiguration.load(client.properties())", handler)
        self.assertIn("final boolean keepInBackground", config)
        self.assertIn("final boolean resumePlayback", config)
        self.assertIn("final long timeoutMs", config)
        self.assertIn("Keys.app_destroy_on_pause", config)
        self.assertNotIn("legacyDestroy", handler)

    def test_debug_keyboard_observation_is_not_activity_handler_state(self):
        handler = (ANDROID / "UIActivityLifeCycleHandler.java").read_text(encoding="utf-8")
        state = (ANDROID / "UiKeyboardDebugState.java").read_text(encoding="utf-8")

        self.assertNotIn("debugKeyboardRequested", handler)
        self.assertNotIn("debugKeyboardSuppressed", handler)
        self.assertNotIn("WeakReference<Activity>", handler)
        self.assertIn("UiKeyboardDebugState.visibility()", handler)
        self.assertIn("private static volatile boolean requested", state)
        self.assertIn("private static volatile boolean suppressed", state)
        self.assertIn("WeakReference<Activity>", state)


if __name__ == "__main__":
    unittest.main()
