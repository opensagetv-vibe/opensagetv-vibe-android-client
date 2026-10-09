import importlib.util
from pathlib import Path
import sys
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
sys.path.insert(0, str(ROOT / "mcp/src"))
spec = importlib.util.spec_from_file_location("tablet_layout_gate", ROOT / "scripts/mcp_tablet_layout_test.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class TabletLayoutGateTests(unittest.TestCase):
    def bounds(self):
        names = [("nav_video_info", "nav_toggle_ar"),
                 ("nav_export_diagnostics", "nav_active_player_adjustments"),
                 ("nav_test_current_video", "nav_audio_output"),
                 ("nav_playback_stats", "nav_closed_captions")]
        return {name: (x * 100, y * 100, x * 100 + 80, y * 100 + 80)
                for x, pair in enumerate(names) for y, name in enumerate(pair)}

    def test_aligned_pairs_pass(self):
        module.assert_aligned_icons(self.bounds())

    def test_missing_or_wrong_column_is_rejected(self):
        bounds = self.bounds()
        bounds.pop("nav_audio_output")
        with self.assertRaises(RuntimeError):
            module.assert_aligned_icons(bounds)
        bounds = self.bounds()
        bounds["nav_audio_output"] = (0, 100, 80, 180)
        with self.assertRaises(RuntimeError):
            module.assert_aligned_icons(bounds)

    def test_hierarchy_wrapper_is_parsed(self):
        xml = 'noise\n<?xml version="1.0"?><hierarchy><node resource-id="app:id/nav_audio_output" bounds="[100,200][180,280]"/></hierarchy>\nnoise'
        self.assertEqual(module.icon_bounds(xml)["nav_audio_output"], (100, 200, 180, 280))

    def test_owned_device_temp_file_is_removed_on_dump_failure(self):
        class Adb:
            def __init__(self):
                self.commands = []
            def shell(self, command, **kwargs):
                self.commands.append(command)
                if command.startswith("uiautomator"):
                    raise RuntimeError("dump failed")
                return ""
        adb = Adb()
        with self.assertRaises(RuntimeError):
            module.read_icon_bounds(adb)
        self.assertEqual(len(adb.commands), 2)
        self.assertEqual(adb.commands[1], "rm -f " + adb.commands[0].split()[-1])
        self.assertTrue(adb.commands[1].startswith("rm -f /data/local/tmp/vibe-tablet-layout-"))


if __name__ == "__main__":
    unittest.main()
