import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from mcp_dvd_menu_cycle_test import phase_ready


class DvdMenuCycleGateTest(unittest.TestCase):
    def test_server_control_is_explicit_not_a_remote_input_verdict(self):
        source = (Path(__file__).resolve().parents[1] / "scripts/mcp_dvd_menu_cycle_test.py").read_text(encoding="utf-8")
        self.assertIn('choices=("firetv", "server"), default="firetv"', source)
        self.assertIn("not a remote-input pass", source)
        self.assertIn("Refusing authored-fixture keys on an unverified DVD volume", source)
        self.assertIn('report["failureArtifacts"]', source)
        self.assertIn('control.resolve_context(str(initial["clientId"]))', source)

    def test_only_fresh_audible_visible_epoch_can_pass(self):
        state = {"dvdHighlightVisible": True, "health_isPlaying": True,
                 "health_videoRendered": 10, "health_audioRendered": 10,
                 "dvdNewCellCount": 2, "health_playerError": ""}
        self.assertTrue(phase_ready(state, True, 1, True))
        self.assertFalse(phase_ready(state, False, 1, True))
        self.assertFalse(phase_ready(state, True, 2, True))
        self.assertFalse(phase_ready({**state, "health_videoRendered": 0}, True, 1, True))
        self.assertFalse(phase_ready({**state, "health_playerError": "failed"}, True, 1, True))

    def test_initial_root_may_already_be_active_but_still_requires_real_output(self):
        state = {"dvdHighlightVisible": True, "health_isPlaying": True,
                 "health_videoRendered": 30, "health_audioRendered": 30}
        self.assertTrue(phase_ready(state, True, -1, False))
        self.assertFalse(phase_ready({}, True, -1, False))


if __name__ == "__main__":
    unittest.main()
