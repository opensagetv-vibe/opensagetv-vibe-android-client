"""The wire property must reflect an implemented backend path, not ring size."""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


class PushBufferSeekContractTest(unittest.TestCase):
    def test_connection_uses_backend_gate(self):
        source = (ROOT / "source/dev/core/src/main/java/opensagetv/vibe/miniclient/MiniClientConnection.java").read_text()
        branch = source.split('"PUSH_BUFFER_SEEKING".equals(propName)', 1)[1].split('else if', 1)[0]
        self.assertIn("PushBufferSeekPolicy", branch)
        self.assertIn("PrefStore.Keys.default_player", branch)
        self.assertIn("PrefStore.Keys.gsy_player_engine", branch)
        self.assertNotIn('propVal = "TRUE";', branch)

    def test_policy_does_not_confuse_stream_selection_with_seeking(self):
        source = (ROOT / "source/dev/core/src/main/java/opensagetv/vibe/miniclient/video/PushBufferSeekPolicy.java").read_text()
        self.assertNotIn("getStreamingMode", source)
        self.assertNotIn("PUSH_BUFFER_LIMIT", source)
        self.assertIn('return false;', source)
        self.assertNotIn('return true;', source)


if __name__ == "__main__":
    unittest.main()
