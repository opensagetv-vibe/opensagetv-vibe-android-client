"""Stock public DVD chapter ordinals are independent of decoder cell delivery."""
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from mcp_dvd_held_arrow_test import chapter_repeat_delta


class ChapterRepeatOracleTests(unittest.TestCase):
    def test_forward_and_reverse_repeated_chapters(self):
        self.assertEqual(3, chapter_repeat_delta(4, 7, "UP"))
        self.assertEqual(3, chapter_repeat_delta(10, 7, "DOWN"))

    def test_one_jump_or_wrong_direction_is_not_repeat_proof(self):
        self.assertEqual(1, chapter_repeat_delta(4, 5, "UP"))
        self.assertEqual(-3, chapter_repeat_delta(7, 4, "UP"))
        self.assertEqual(0, chapter_repeat_delta(7, 7, "DOWN"))

    def test_unknown_ordinals_or_wrong_key_fail_closed(self):
        for args in ((0, 7, "UP"), (7, -1, "DOWN"), (4, 7, "RIGHT")):
            with self.assertRaises(ValueError):
                chapter_repeat_delta(*args)


if __name__ == "__main__":
    unittest.main()
