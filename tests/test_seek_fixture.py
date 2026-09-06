import importlib.util
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def load_generator():
    path = ROOT / "scripts/generate_a53_seek_fixture.py"
    spec = importlib.util.spec_from_file_location("generate_a53_seek_fixture", path)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(module)
    return module


class SeekFixtureWorkflowTests(unittest.TestCase):
    def test_generator_matches_atc_recording_characteristics(self):
        script = (ROOT / "scripts/generate_seek_fixture.sh").read_text(encoding="utf-8")
        generator = (ROOT / "scripts/generate_a53_seek_fixture.py").read_text(encoding="utf-8")
        self.assertIn("generate_a53_seek_fixture.py", script)
        self.assertIn("--caption-interval 0.5", script)
        self.assertIn("--cc both", script)
        self.assertIn("testsrc2=size=1920x1080:rate={SOURCE_FPS}", generator)
        self.assertIn("tinterlace=mode=interleave_top", generator)
        self.assertIn("PTS %{{pts", generator)
        self.assertIn("FRAME %{{n}}", generator)
        self.assertIn("SYNC_PULSE_DURATION = 0.12", generator)
        self.assertIn("A/V SYNC PULSE = WHOLE SECOND", generator)
        self.assertGreaterEqual(generator.count("lt(mod(t,1),{SYNC_PULSE_DURATION:.3f})"), 3)
        self.assertIn('"-c:v", "mpeg2video"', generator)
        self.assertIn('"-c:a", "ac3"', generator)
        self.assertIn('b"GA94"', generator)
        self.assertIn("build_608_rollup_words", generator)
        self.assertIn("pac_word(row=14, column=0, channel=0)", generator)
        self.assertNotIn("pac_word(row=15, column=0, channel=0)", generator)
        self.assertIn("build_708_render_payload", generator)
        self.assertIn("count_a53_payloads", generator)
        self.assertIn("probe_video_frame_count", generator)
        self.assertIn("a53_payload_count != output_frame_count", generator)
        self.assertIn('edl_path="${output_path%.*}.edl"', script)
        self.assertIn('"120 180" "360 420" "660 720"', script)

    def test_root_workflow_exposes_the_same_fixture_generator(self):
        dev = (ROOT / "dev.sh").read_text(encoding="utf-8")
        readme = (ROOT / "README.md").read_text(encoding="utf-8")
        self.assertIn("seek-fixture)", dev)
        self.assertIn("generate_seek_fixture.sh", dev)
        self.assertIn("dev.cmd seek-fixture", readme)
        self.assertIn("louder audio pulse", readme)

    def test_mcp_exposes_reproducible_restricted_generator(self):
        server = (ROOT / "mcp/src/sagetv_dev_mcp/server.py").read_text(encoding="utf-8")
        self.assertIn("def generate_seek_fixture(", server)
        self.assertIn('SEEK_FIXTURE_DIR = PROJECT_ROOT / "artifacts" / "test-media"', server)
        self.assertIn('re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]*\\.ts"', server)
        self.assertIn('"sha256"', server)
        self.assertIn('"already_exists"', server)
        self.assertIn("def _write_seek_fixture_edl", server)
        self.assertIn('publication["commercialMarkers"]', server)
        self.assertIn('"VibeSeekTest-1080i-MPEG2-AC3-CC.ts"', server)
        self.assertIn('"transport": "ATSC_A53_GA94"', server)
        self.assertIn('metadata["a53Ga94PayloadCount"]', server)
        self.assertIn('metadata["finalVideoFrameCount"]', server)
        self.assertIn('"audioVisualSyncPulse"', server)
        self.assertIn('"durationSeconds": 0.12', server)
        self.assertEqual(1, server.count("def _seek_fixture_metadata"))

    def test_a53_encoder_builds_valid_constant_rate_608_and_708_payloads(self):
        generator = load_generator()
        frames = generator.build_caption_frames(30, 1.0, 0.5, "both", "PTS")
        self.assertEqual(30, len(frames))
        self.assertTrue(all(len(frame) == 60 for frame in frames))
        payload = generator.make_a53_user_data(frames[0])
        self.assertTrue(payload.startswith(b"\x00\x00\x01\xB2GA94\x03"))
        self.assertEqual(20, payload[9] & 0x1F)
        self.assertIn(b"PTS", bytes(generator.encode_708_text("PTS 00:00:00.000")))

    def test_608_bytes_have_odd_parity(self):
        generator = load_generator()
        for byte in range(128):
            encoded = generator.odd_parity(byte)
            self.assertEqual(1, encoded.bit_count() & 1)

    def test_streaming_payload_counter_handles_block_boundaries(self):
        generator = load_generator()
        with tempfile.TemporaryDirectory() as temp_dir:
            path = Path(temp_dir) / "payload.bin"
            path.write_bytes(b"x" * (1024 * 1024 - 2) + b"G" + b"A94" + b"GA94")
            self.assertEqual(2, generator.count_a53_payloads(path))

    def test_duration_parser_accepts_test_workflow_forms(self):
        generator = load_generator()
        self.assertEqual(120.0, generator.parse_duration("2m"))
        self.assertEqual(900.0, generator.parse_duration("00:15:00"))


if __name__ == "__main__":
    unittest.main()
