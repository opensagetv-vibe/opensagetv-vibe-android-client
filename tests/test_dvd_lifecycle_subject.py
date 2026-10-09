import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from mcp_lifecycle_test import authored_dvd_root


class DvdLifecycleSubjectTests(unittest.TestCase):
    def test_only_generated_volume_accepts_automatic_title_selection(self):
        root = "/var/media/OpenSageTV_Vibe_Tests/OpenSageTV_Vibe_Test_DVD"
        self.assertEqual(root, authored_dvd_root(root))
        self.assertEqual(root, authored_dvd_root(root + "/VIDEO_TS/"))
        for unrelated in ("", "/var/media/ALADDIN", root + "-copy", root + "/other"):
            self.assertEqual("", authored_dvd_root(unrelated))

    def test_windows_fixture_path_keeps_exact_volume(self):
        self.assertEqual("V:/tests/OpenSageTV_Vibe_Test_DVD",
                         authored_dvd_root("V:\\tests\\OpenSageTV_Vibe_Test_DVD\\VIDEO_TS"))

    def test_lifecycle_verifies_public_media_and_new_title_cell(self):
        source = (Path(__file__).resolve().parents[1] / "scripts/mcp_lifecycle_test.py").read_text()
        self.assertIn('control.resolve_context(str(initial["clientId"]))', source)
        self.assertIn("Refusing lifecycle title keys on an unverified DVD volume", source)
        self.assertIn('int(s.get("dvdNewCellCount", -1)) > cell', source)
        self.assertIn("not a looping menu", source)
        self.assertIn("Repeated authored title {iteration} did not establish real A/V", source)


if __name__ == "__main__":
    unittest.main()
