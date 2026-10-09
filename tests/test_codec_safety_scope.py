import ast
import unittest
from pathlib import Path


class CodecSafetyScopeTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = (Path(__file__).resolve().parents[1] / "scripts/mcp_hardware_codec_matrix.py").read_text()

    def test_safety_selection_has_no_positive_rows(self):
        expression = ast.parse("[] if args.safety_only else CASES", mode="eval")
        class Args:
            safety_only = True
        cases = [("positive.ts", "video/mpeg2", 1000)]
        self.assertEqual([], eval(compile(expression, "selection", "eval"), {"args": Args(), "CASES": cases}))
        Args.safety_only = False
        self.assertEqual(cases, eval(compile(expression, "selection", "eval"), {"args": Args(), "CASES": cases}))
        self.assertIn("selected_cases = [] if args.safety_only else CASES", self.source)

    def test_existing_negative_gates_are_retained_and_scope_reported(self):
        self.assertIn('"safetyOnly": args.safety_only', self.source)
        self.assertIn('"positiveRowsRequested": len(selected_cases)', self.source)
        self.assertIn('"/h264-truncated-start.ts"', self.source)
        self.assertIn("SKIPPED_AUTHORIZED_ASSET_REQUIRED", self.source)
        self.assertIn("--safety-only cannot be combined with positive-row selection", self.source)


if __name__ == "__main__":
    unittest.main()
