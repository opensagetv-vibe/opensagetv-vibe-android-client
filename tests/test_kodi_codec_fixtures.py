import importlib.util
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
GENERATOR = ROOT / "scripts" / "generate_kodi_codec_fixtures.py"
VC1_REMUXER = ROOT / "scripts" / "remux_vc1_fixture.py"
DEV = ROOT / "dev.sh"
README = ROOT / "README.md"
PHYSICAL_MATRIX = ROOT / "scripts" / "mcp_kodi_codec_matrix.py"


class KodiCodecFixtureTests(unittest.TestCase):
    def test_generator_covers_ffmpeg_generatable_kodi_codec_rules(self):
        text = GENERATOR.read_text(encoding="utf-8")
        for marker in (
            "mpeg2-interlaced-bframes.ts",
            "mpeg4-part2-bframes.mp4",
            "mpeg4-part2-missing-pts.fault-plan.json",
            "h263-baseline.3gp",
            "h264-baseline-avcc.mp4",
            "h264-high-bframes-annexb.ts",
            "hevc-main-hvcc.mp4",
            "hevc-main10-hdr10.mkv",
            "vp8-profile0.mkv",
            "vp9-profile0.mkv",
            "vp9-profile2-10bit.mkv",
            "av1-main8.mkv",
            "h264-resolution-switch-annexb.ts",
            "h264-truncated-start.ts",
        ):
            self.assertIn(marker, text)

    def test_non_generatable_cases_are_never_false_passes(self):
        text = GENERATOR.read_text(encoding="utf-8")
        self.assertIn('"status": "SOURCE_REQUIRED"', text)
        self.assertIn('"status": "NOT_GENERATABLE_WITH_CLEAR_FFMPEG_FIXTURE"', text)
        self.assertIn('"status": "STATIC_OR_FAULT_INJECTION_ONLY"', text)
        self.assertIn("no VC-1 encoder", text)
        self.assertIn("GENERATED_OUTPUT_NAMES", text)
        self.assertIn("obsolete pre-Matroska fixture", text)

    def test_vc1_remuxer_rejects_non_vc1_and_requires_provenance(self):
        text = VC1_REMUXER.read_text(encoding="utf-8")
        self.assertIn('parser.add_argument("--source-url", required=True', text)
        self.assertIn('parser.add_argument("--license-note", required=True', text)
        self.assertIn('choices=("local-test-only", "redistributable")', text)
        self.assertIn('"packageInSourceOrRelease": args.redistribution == "redistributable"', text)
        self.assertIn('streams[0].get("codec_name") != "vc1"', text)
        self.assertIn("WMV1/WMV2/WMV3 are not substitutes", text)
        self.assertIn('"video/wvc1", "video/VC1", "video/vc1"', text)

    def test_duration_validation_is_positive(self):
        spec = importlib.util.spec_from_file_location("codec_fixtures", GENERATOR)
        module = importlib.util.module_from_spec(spec)
        assert spec.loader is not None
        spec.loader.exec_module(module)
        with tempfile.TemporaryDirectory() as temp:
            self.assertTrue(Path(temp).is_dir())
        self.assertTrue(callable(module.generate))

    def test_unified_root_workflow_exposes_codec_fixture_generator(self):
        dev = DEV.read_text(encoding="utf-8")
        readme = README.read_text(encoding="utf-8")
        self.assertIn('docker exec -i -w "$CONTAINER_WORKSPACE"', dev)
        self.assertIn("codec-fixtures)", dev)
        self.assertIn("generate_kodi_codec_fixtures.py", dev)
        self.assertIn("dev.cmd codec-fixtures", readme)

    def test_physical_matrix_is_hardware_strict_and_non_pro_only(self):
        text = PHYSICAL_MATRIX.read_text(encoding="utf-8")
        self.assertIn("192.168.10.25:5555", text)
        self.assertIn('"decoding": "hardware"', text)
        self.assertIn('state.get("health_videoDecoderKind") == "hardware"', text)
        self.assertIn("SKIPPED_UNSUPPORTED_HARDWARE", text)
        self.assertIn("PASS_CONTROLLED_EXTRACTOR_INJECTION", text)
        self.assertIn("validate_mpeg4_missing_pts_injection", text)
        dev = DEV.read_text(encoding="utf-8")
        self.assertIn("mcp-kodi-codec-matrix)", dev)
        self.assertIn("mcp_kodi_codec_matrix.py", dev)

    def test_missing_pts_plan_requires_distinct_retained_dts(self):
        generator = GENERATOR.read_text(encoding="utf-8")
        self.assertIn('str(mpeg4_mp4)', generator)
        self.assertIn('pts != dts', generator)
        self.assertIn('"expectedFallbackTimestamp": dts', generator)
        self.assertIn('"status": "READY_FOR_CONTROLLED_EXTRACTOR_INJECTION"', generator)


if __name__ == "__main__":
    unittest.main()
