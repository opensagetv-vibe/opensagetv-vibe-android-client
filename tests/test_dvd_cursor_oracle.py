"""A late server landing echo must not turn an inaccurate DVD seek into PASS."""
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from mcp_dvd_cursor_test import cursor_target_ms, source_anchor_error_ms


class DvdCursorOracleTest(unittest.TestCase):
    def test_target_uses_key_down_entry_and_actual_cursor_steps(self):
        cursor = {"dvdTimeScrollEntryPositionMs": 548170,
                  "dvdTimeScrollSteps": 1, "mediaTimeMs": 559108}
        self.assertEqual(698170, cursor_target_ms(cursor, 150000))
        cursor["dvdTimeScrollSteps"] = -1
        self.assertEqual(398170, cursor_target_ms(cursor, 150000))

    def test_repeated_opposite_steps_preserve_net_intent_and_clamp_zero(self):
        self.assertEqual(0, cursor_target_ms(
            {"dvdTimeScrollEntryPositionMs": 10000, "dvdTimeScrollSteps": -1}, 150000))
        with self.assertRaises(ValueError):
            cursor_target_ms({}, 0)

    def test_decoded_clock_does_not_hide_server_destination_error(self):
        # A late response is already 3.302 seconds into playback, but its
        # source anchor remains 10.996 seconds beyond the requested position.
        state = {"mediaTimeMs": 712468, "health_playerPositionMs": 3302}
        self.assertEqual(10996, source_anchor_error_ms(state, 698170))

    def test_focused_gate_preserves_scan_deferral_and_precision_threshold(self):
        script = (ROOT / "scripts/mcp_dvd_cursor_test.py").read_text(encoding="utf-8")
        self.assertIn("--skip-dedicated-scan", script)
        self.assertIn("--capture-cursor", script)
        self.assertIn('(() if args.skip_dedicated_scan else ("FF", "REWIND"))', script)
        self.assertIn('"--landing-tolerance-ms", type=int, default=4000', script)
        self.assertIn("abs(error) < args.landing_tolerance_ms", script)
        self.assertNotIn('target = int(committed["mediaTimeMs"])', script)

    def test_immediate_server_echo_cannot_abort_queued_accept(self):
        script = (ROOT / "scripts/mcp_dvd_cursor_test.py").read_text(encoding="utf-8")
        self.assertNotIn('raise RuntimeError("STV did not report a committed', script)
        self.assertIn('"immediateServerStepMs": observed_step', script)
        self.assertIn('int(after.get("serverFlushSequence", 0)) > int(before.get("serverFlushSequence", 0))', script)
        self.assertIn('"health_videoRendered", 0)) > 2', script)
        self.assertIn('"health_audioRendered", 0)) > 2', script)


if __name__ == "__main__":
    unittest.main()
