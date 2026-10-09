import sys
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import mcp_caption_test as caption


class CaptionSettingRestorationTests(unittest.TestCase):
    def test_original_caption_state_is_read_not_assumed_off(self):
        for state in ("Off", "CC1", "CC2"):
            with patch.object(caption, "call_dict", return_value={"state": state}) as call:
                self.assertEqual(state, caption.checkpoint_server_caption_state(object()))
            self.assertEqual(call.call_args.args[1], "dev_stv_caption_state")

    def test_missing_checkpoint_fails_before_controls(self):
        with patch.object(caption, "call_dict", return_value={}), self.assertRaises(RuntimeError):
            caption.checkpoint_server_caption_state(object())

    def test_restore_uses_exact_original_and_verifies_acknowledgement(self):
        with patch.object(caption, "call_dict", return_value={"state": "CC2"}) as call:
            caption.restore_server_caption_state(object(), "CC2")
        self.assertEqual(call.call_args.args[2], {"state": "CC2"})

    def test_wrong_restoration_does_not_pass(self):
        with patch.object(caption, "call_dict", return_value={"state": "Off"}), \
                self.assertRaises(RuntimeError):
            caption.restore_server_caption_state(object(), "CC1")

    def test_restore_is_before_disconnect_and_failure_changes_exit_code(self):
        source = Path(caption.__file__).read_text()
        cleanup = source[source.index("    finally:\n", source.index("def main()")):]
        self.assertLess(cleanup.index("restore_server_caption_state"),
                        cleanup.index('"dev_exit_session"'))
        self.assertIn("if caption_restore_failed:", cleanup)
        self.assertIn('raise RuntimeError("Server caption restoration failed; gate cannot be marked PASS")', cleanup)


if __name__ == "__main__":
    unittest.main()
