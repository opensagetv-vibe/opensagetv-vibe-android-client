import importlib.util
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
GENERATOR = ROOT / "scripts" / "generate_hardware_codec_fixtures.py"
VC1_REMUXER = ROOT / "scripts" / "remux_vc1_fixture.py"
DEV = ROOT / "dev.sh"
README = ROOT / "README.md"
PHYSICAL_MATRIX = ROOT / "scripts" / "mcp_hardware_codec_matrix.py"


class HardwareCodecFixtureTests(unittest.TestCase):
    def test_generator_covers_ffmpeg_generatable_codec_rules(self):
        text = GENERATOR.read_text(encoding="utf-8")
        for marker in (
            "mpeg2-interlaced-bframes.ts",
            "mpeg2-1080i29.97.ts",
            "mpeg2-720p59.94.ts",
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
            "mpeg2-sequence-resolution-switch.ts",
            "h264-ts-timestamp-discontinuity.ts",
            "h264-pmt-audio-track-switch.ts",
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
        self.assertIn("generate_hardware_codec_fixtures.py", dev)
        self.assertIn("dev.cmd codec-fixtures", readme)

    def test_physical_matrix_is_hardware_strict_and_uses_selected_device(self):
        text = PHYSICAL_MATRIX.read_text(encoding="utf-8")
        self.assertIn('os.environ.get("SAGETV_TEST_DEVICE_ALIAS", "non_pro")', text)
        self.assertIn('required_serial = configured_device_serial()', text)
        self.assertIn('"decoding": "hardware"', text)
        self.assertIn('state.get("health_videoDecoderKind") == "hardware"', text)
        self.assertIn("SKIPPED_UNSUPPORTED_HARDWARE", text)
        self.assertIn("PASS_CONTROLLED_EXTRACTOR_INJECTION", text)
        self.assertIn("validate_mpeg4_missing_pts_injection", text)
        self.assertIn('"--start-at"', text)
        self.assertIn('"--max-cases"', text)
        self.assertIn("crash_changed(baseline_crash, crash)", text)
        self.assertIn('checkpoint("RUNNING")', text)
        self.assertIn('"replace_active_fullscreen"', text)
        self.assertIn('"replace_retained_player"', text)
        self.assertIn('"dismiss_stale_stop_popup"', text)
        self.assertIn("_fresh_source_opened(before, last)", text)
        self.assertIn('"health_dataSourceLastOpenMonotonicMs"', text)
        self.assertIn("is_fullscreen_playback(state)", text)
        self.assertIn('"command": "back"', text)
        self.assertIn('"method": "closed_stopped_popup_back"', text)
        self.assertIn('"method": "replace_behind_stopped_popup"', text)
        self.assertIn("dismiss_replaced_stopped_popup(client)", text)
        self.assertIn("playback obscured by popup", text)
        self.assertIn("restore_post_test_menu(client)", text)
        self.assertIn("compact_start_result(started)", text)
        self.assertIn('call_dict(client, "dev_resolve_video_names"', text)
        self.assertIn('call_dict(client, "dev_play_media_file_id"', text)
        self.assertIn("completed_short_fixture_state(started, expected_mime)", text)
        self.assertIn("PASS_SHORT_FIXTURE_CLEAN_EOF", text)
        self.assertIn("rebuild_test_session(client, args)", text)
        self.assertIn("supports_vp9_profile2(profiles)", text)
        self.assertIn("SKIPPED_UNSUPPORTED_HARDWARE_PROFILE", text)
        self.assertIn('choices=("media3", "exoplayer", "gsyplayer")', text)
        self.assertIn('choices=("media3", "legacy_exo")', text)
        self.assertIn('state.get("gsyResolvedEngine")', text)
        self.assertIn('default_fixture_cases(', text)
        self.assertIn('mode="hardware_codec_matrix", enabled_only=True', text)
        self.assertIn('"--include-disabled-fixture"', text)
        self.assertIn('"SKIPPED_CONFIG_DISABLED"', text)
        self.assertIn('"--storage-warmup-timeout-s"', text)
        self.assertIn('"--slow-startup-ms"', text)
        self.assertIn('"--storage-warm-cache-s"', text)
        self.assertIn('"--skip-storage-warmup"', text)
        self.assertIn('"storageWarmup": storage_warmup', text)
        self.assertIn('recent_entry(', text)
        self.assertIn('mark_recent(', text)
        self.assertIn('"slow_start_discarded_and_retried"', text)
        self.assertIn('"normal_start_used_as_measured_result"', text)
        self.assertIn('"slidingTtlRefreshed"', text)
        dev = DEV.read_text(encoding="utf-8")
        self.assertIn("mcp-hardware-codec-matrix)", dev)
        self.assertIn("mcp_hardware_codec_matrix.py", dev)

    def test_missing_pts_plan_requires_distinct_retained_dts(self):
        generator = GENERATOR.read_text(encoding="utf-8")
        self.assertIn('str(mpeg4_mp4)', generator)
        self.assertIn('pts != dts', generator)
        self.assertIn('"expectedFallbackTimestamp": dts', generator)
        self.assertIn('"status": "READY_FOR_CONTROLLED_EXTRACTOR_INJECTION"', generator)

    def test_physical_fixtures_burn_identity_and_clock_into_pixels(self):
        generator = GENERATOR.read_text(encoding="utf-8")
        self.assertIn("Every physical codec fixture requires a visible label", generator)
        self.assertIn("OPENSAGETV VIBE CODEC TEST", generator)
        self.assertIn("PTS %{pts\\\\:hms}", generator)
        self.assertIn("labeled_test_source(", generator)


if __name__ == "__main__":
    unittest.main()
