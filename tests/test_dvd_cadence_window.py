"""A delayed diagnostic reply must not be reported as slow DVD playback."""
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from mcp_disc_test import cadence_window, cadence_subject_ready


class DvdCadenceWindowTest(unittest.TestCase):
    def test_only_active_title_output_is_a_cadence_subject(self):
        title = {"playerActive": True, "health_isPlaying": True,
                 "dvdHighlightVisible": False, "health_videoRendered": 12,
                 "health_audioRendered": 12}
        self.assertTrue(cadence_subject_ready(title))
        for field, value in (("playerActive", False), ("health_isPlaying", False),
                             ("dvdHighlightVisible", True), ("health_videoRendered", -1),
                             ("health_audioRendered", 0)):
            self.assertFalse(cadence_subject_ready({**title, field: value}))

    def test_delivery_latency_is_not_part_of_device_output_interval(self):
        elapsed, source = cadence_window(
            {"health_capturedMonotonicMs": 100_000},
            {"health_capturedMonotonicMs": 131_268}, 38_543)
        self.assertEqual(31_268, elapsed)
        self.assertEqual("device_health_monotonic", source)
        self.assertEqual(1.0, 31_268 / elapsed)

    def test_old_apk_keeps_explicit_legacy_host_fallback(self):
        self.assertEqual((30_001, "host_delivery_clock_legacy_fallback"),
                         cadence_window({}, {}, 30_001))

    def test_reset_or_reversed_device_clock_does_not_invent_a_window(self):
        self.assertEqual((5000, "host_delivery_clock_legacy_fallback"),
                         cadence_window({"health_capturedMonotonicMs": 20_000},
                                        {"health_capturedMonotonicMs": 1000}, 5000))


if __name__ == "__main__":
    unittest.main()
