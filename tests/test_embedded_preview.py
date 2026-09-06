from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]


class EmbeddedPreviewGateTests(unittest.TestCase):
    def test_gate_requires_embedded_destination_and_real_output_health(self):
        harness = (ROOT / "scripts/mcp_embedded_preview_test.py").read_text(encoding="utf-8")
        self.assertIn("videoDestWidth", harness)
        self.assertIn("videoUiWidth", harness)
        self.assertIn('"dev_wait_for_playback_started"', harness)
        self.assertIn('"health_surfaceValid"', harness)
        self.assertIn('"health_errorState"', harness)
        self.assertIn('"take_screenshot"', harness)
        self.assertIn('"dev_crash_probe"', harness)
        self.assertIn('"disc_playback_policy": args.disc_policy', harness)
        self.assertIn('"disc_skip_menus": args.disc_skip_menus', harness)

    def test_unified_workflow_exposes_the_physical_gate(self):
        workflow = (ROOT / "dev.sh").read_text(encoding="utf-8")
        self.assertIn("mcp-embedded-preview-test)", workflow)
        self.assertIn("mcp_embedded_preview_test.py", workflow)


if __name__ == "__main__":
    unittest.main()
