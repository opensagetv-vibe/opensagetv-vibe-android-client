from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]


class HardwareCodecCaptureTests(unittest.TestCase):
    def test_capture_is_opt_in_and_does_not_claim_visual_pass(self):
        text = (ROOT / "scripts/mcp_hardware_codec_matrix.py").read_text()
        self.assertIn('"--capture-each-case", action="store_true"', text)
        self.assertIn("if args.capture_each_case:", text)
        capture = text.split("if args.capture_each_case:", 1)[1].split('print(f"PASS:', 1)[0]
        self.assertIn('"PENDING_VISUAL_REVIEW"', capture)
        self.assertIn('"ADB_COMPOSITED_SCREEN_NOT_HDMI_OR_SPEAKER"', capture)
        self.assertIn('call_dict(client, "take_screenshot"', capture)
        self.assertNotIn('"PASS_VISUAL"', capture)
        self.assertNotIn('dev_set_player_config', capture)

    def test_capture_follows_real_decoder_check_and_precedes_cleanup(self):
        text = (ROOT / "scripts/mcp_hardware_codec_matrix.py").read_text()
        decoder = text.index('require(state.get("health_videoDecoderKind") == "hardware"')
        capture = text.index('row["visualEvidence"]')
        cleanup = text.index('post_test_cleanup = restore_post_test_menu(client)', capture)
        self.assertLess(decoder, capture)
        self.assertLess(capture, cleanup)


if __name__ == "__main__":
    unittest.main()
