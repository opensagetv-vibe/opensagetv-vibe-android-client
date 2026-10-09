from pathlib import Path
import unittest


class DvdScanAffectedSelectionTest(unittest.TestCase):
    def test_direction_and_position_are_opt_in_decoder_only(self):
        text = (Path(__file__).resolve().parents[1] / "scripts/mcp_dvd_scan_gesture_test.py").read_text()
        self.assertIn('choices=("both", "forward", "reverse"), default="both"', text)
        self.assertIn('parser.error("decoder direction/start selection requires --decoder-only")', text)
        self.assertIn('parser.error("--decoder-start-ms must be nonnegative")', text)

    def test_public_position_requires_new_flush_real_video_and_audio(self):
        text = (Path(__file__).resolve().parents[1] / "scripts/mcp_dvd_scan_gesture_test.py").read_text()
        self.assertIn('control.seek(context, args.decoder_start_ms)', text)
        self.assertIn('int(positioned.get("serverFlushSequence", 0)) > int(before_seek.get("serverFlushSequence", 0))', text)
        self.assertIn('"health_videoRendered", 0)) > 2', text)
        self.assertIn('"health_audioRendered", 0)) > 2', text)
        self.assertNotIn('SetProperty', text)


if __name__ == "__main__":
    unittest.main()
