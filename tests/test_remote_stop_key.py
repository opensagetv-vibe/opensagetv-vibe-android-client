import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


class RemoteStopKeyTests(unittest.TestCase):
    def test_stop_mapping_and_physical_gate_are_instrumented(self):
        keymap = (ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/ui/keymaps/KeyMapProcessor.java").read_text(encoding="utf-8")
        state = (ROOT / "source/dev/android-tv/src/debug/java/opensagetv/vibe/miniclient/android/tv/debug/DebugStateProvider.java").read_text(encoding="utf-8")
        runner = (ROOT / "scripts/mcp_stop_key_test.py").read_text(encoding="utf-8")
        dev = (ROOT / "dev.sh").read_text(encoding="utf-8")
        self.assertIn("KEYCODE_MEDIA_STOP", (ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/ui/keymaps/DefaultKeyMap.java").read_text(encoding="utf-8"))
        self.assertIn("recordInputEvent(keyCode, event);", keymap)
        self.assertIn("inputLastScanCode", state)
        self.assertIn('"inputLastMappedCommand"', runner)
        self.assertIn('"keys": ["STOP"]', runner)
        self.assertIn("mcp-stop-key-test)", dev)


if __name__ == "__main__":
    unittest.main()
