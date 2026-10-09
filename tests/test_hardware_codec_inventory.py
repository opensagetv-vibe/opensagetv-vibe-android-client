import ast
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "scripts/mcp_hardware_codec_matrix.py"
TREE = ast.parse(SOURCE.read_text())
NODE = next(n for n in TREE.body if isinstance(n, ast.FunctionDef)
            and n.name == "hardware_mime_available")
NAMESPACE = {}
exec(compile(ast.Module(body=[NODE], type_ignores=[]), str(SOURCE), "exec"), NAMESPACE)
available = NAMESPACE["hardware_mime_available"]


class HardwareCodecInventoryTests(unittest.TestCase):
    def test_declared_hardware_is_still_physically_tested(self):
        self.assertIs(available("OMX.amlogic.avc,OMX.amlogic.avc,video/avc,hw", "video/avc"), True)

    def test_software_only_is_unsupported_hardware_not_pass(self):
        self.assertIs(available("OMX.google.vp8,OMX.google.vp8,video/x-vnd.on2.vp8,sw",
                                "video/x-vnd.on2.vp8"), False)

    def test_missing_mime_in_complete_inventory_is_unsupported(self):
        self.assertIs(available("OMX.amlogic.avc,OMX.amlogic.avc,video/avc,hw", "video/av1"), False)

    def test_unknown_or_malformed_inventory_cannot_skip_a_real_gate(self):
        for profile in ("", "corrupt", "OMX.vendor,OMX.vendor,video/avc,unknown",
                        "OMX.google,OMX.google,video/avc,sw|corrupt"):
            with self.subTest(profile=profile):
                self.assertIsNone(available(profile, "video/avc"))

    def test_any_matching_hardware_keeps_gate_even_with_other_partial_rows(self):
        self.assertIs(available("OMX.google,OMX.google,video/avc,sw|corrupt|"
                                "OMX.vendor,OMX.vendor,video/avc,hw", "video/avc"), True)

    def test_skip_does_not_launch_or_claim_playback(self):
        text = SOURCE.read_text()
        block = text.split("if hardware_mime_available(profiles, expected_mime) is False:", 1)[1]
        block = block.split('if name == "vp9-profile2-10bit.mkv"', 1)[0]
        self.assertIn('"SKIPPED_NO_HARDWARE_DECODER"', block)
        self.assertIn('"playbackAttempted": False', block)
        self.assertNotIn("start_fixture(", block)


if __name__ == "__main__":
    unittest.main()
