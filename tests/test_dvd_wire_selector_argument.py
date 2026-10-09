"""Match the Core's physical DVD selectors, not zero-based UI track numbers."""
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from mcp_disc_test import parse_dvd_wire_selector


class DvdWireSelectorArgumentTests(unittest.TestCase):
    def test_private_ac3_substream_code_can_be_hexadecimal(self):
        self.assertEqual(48513, parse_dvd_wire_selector("0xBD81"))

    def test_enabled_subpicture_code_is_not_logical_index(self):
        self.assertEqual(64, parse_dvd_wire_selector("0x40"))

    def test_existing_decimal_arguments_keep_working(self):
        for value in ("48513", "64", "064", "-1", "0"):
            self.assertEqual(int(value, 10), parse_dvd_wire_selector(value))

    def test_invalid_selector_is_rejected(self):
        with self.assertRaises(ValueError):
            parse_dvd_wire_selector("track1")

    def test_visual_capture_window_is_bounded_and_rejects_root_menu(self):
        source = (Path(__file__).resolve().parents[1] / "scripts/mcp_disc_test.py").read_text()
        self.assertIn("0.0 <= args.visual_title_hold_s <= 60.0", source)
        self.assertIn('state.get("dvdHighlightVisible") is False', source)
        self.assertIn('print(f"VISUAL TITLE HOLD:', source)


if __name__ == "__main__":
    unittest.main()
