from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))
from sagetv_dev_mcp.sequence import parse_sequence_script


class SequenceParserTests(unittest.TestCase):
    def test_parses_explicit_multiline_actions_in_order(self):
        actions = parse_sequence_script("""
        command search
        waittextinput 8000
        waitimevisible 8000
        delay 1000
        directtext meet the press
        hideime
        waitimehidden 5000
        command play_pause
        """)
        self.assertEqual(
            [(a.action, a.value) for a in actions],
            [
                ("command", "search"),
                ("waittextinput", 8000),
                ("waitimevisible", 8000),
                ("delay", 1000),
                ("directtext", "meet the press"),
                ("hideime", None),
                ("waitimehidden", 5000),
                ("command", "play_pause"),
            ],
        )

    def test_quoted_sendtext_and_directtext_and_comments(self):
        actions = parse_sequence_script("# note\nsendtext \"meet the press\"\ndirecttext 'other title'\nhideime\ndelay 0")
        self.assertEqual(actions[0].value, "meet the press")
        self.assertEqual(actions[1].value, "other title")
        self.assertIsNone(actions[2].value)
        self.assertEqual(actions[3].value, 0)

    def test_rejects_unknown_or_invalid_delay(self):
        with self.assertRaises(ValueError):
            parse_sequence_script("presskey PLAY")
        with self.assertRaises(ValueError):
            parse_sequence_script("delay nope")
        with self.assertRaises(ValueError):
            parse_sequence_script("delay 60001")
        with self.assertRaises(ValueError):
            parse_sequence_script("waittextinput 0")
        with self.assertRaises(ValueError):
            parse_sequence_script("waittextinput nope")
        with self.assertRaises(ValueError):
            parse_sequence_script("waitimevisible 0")
        with self.assertRaises(ValueError):
            parse_sequence_script("waitimehidden nope")
        with self.assertRaises(ValueError):
            parse_sequence_script("hideime now")


if __name__ == "__main__":
    unittest.main()
