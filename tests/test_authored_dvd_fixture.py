from pathlib import Path
import runpy
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]
GENERATOR = ROOT / "scripts/create_authored_dvd_fixture.py"


class AuthoredDvdFixtureTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = GENERATOR.read_text(encoding="utf-8")
        cls.generator = runpy.run_path(str(GENERATOR))

    def test_defaults_and_relative_paths_are_project_rooted(self):
        self.assertIn("PROJECT_ROOT = SCRIPT_DIR.parent", self.source)
        self.assertIn("PROJECT_ROOT / expanded", self.source)
        self.assertIn('default=PROJECT_ROOT / "artifacts/test-media/VIBE_AUTHORED_DVD"', self.source)

    def test_dvd_vm_and_miniclient_subtitle_selectors_are_not_conflated(self):
        self.assertIn('"g2=0; subtitle=64; jump menu 2;"', self.source)
        self.assertIn('"g2=1; subtitle=65; jump menu 2;"', self.source)
        self.assertIn('"g2=63; subtitle=63; jump menu 2;"', self.source)
        self.assertIn('"selectors": [64, 65, 128]', self.source)
        self.assertIn('"legacyDisableSelectorSupportedByClient": 62', self.source)

    def test_title_jumps_apply_persisted_language_selections_without_a_router_menu(self):
        self.assertIn("def play_with_current_selections(destination: str)", self.source)
        self.assertIn('"audio=0; if (g1 eq 1) { audio=1; } "', self.source)
        self.assertIn('"if (g2 eq 1) { subtitle=65; } "', self.source)
        self.assertNotIn("selection_router =", self.source)

    def test_subtitle_text_uses_the_shared_video_clock(self):
        build_cues = self.generator["build_cues"]
        write_srt = self.generator["write_srt"]
        subtitle = self.generator["SUBTITLE_STREAMS"][0]
        cues = build_cues(6.0, [0.0, 4.0], 2.0, 1.55)
        self.assertEqual("00:00:02.000", cues[1]["startTimestamp"])
        self.assertEqual(60, cues[1]["nearestVideoFrame"])
        self.assertEqual(1, cues[1]["chapter"])

        with tempfile.TemporaryDirectory() as temp_dir:
            srt = Path(temp_dir) / "english.srt"
            write_srt(srt, cues, subtitle, "main")
            text = srt.read_text(encoding="utf-8")
        self.assertIn("ENG DVD SPU | MAIN | CUE 002", text)
        self.assertIn("PTS 00:00:02.000 | FRAME~000060 | CHAPTER 1", text)

    def test_menu_authoring_uses_compatible_loop_and_explicit_navigation(self):
        self.assertIn('DEFAULT_MENU_VIDEO_DURATION = 8.0', self.source)
        self.assertIn('<post>jump cell 1;</post>', self.source)
        self.assertNotIn('pause="inf" palette=', self.source)
        self.assertIn('up={quoteattr(button.up)} down={quoteattr(button.down)}', self.source)
        self.assertIn('text_y = button.rect.y0 + 4', self.source)

    def test_cached_assets_are_configuration_checked(self):
        self.assertIn('WORK_ASSET_SCHEMA_VERSION = 3', self.source)
        self.assertIn('"--reuse-work"', self.source)
        self.assertIn('"--reuse-titles"', self.source)
        self.assertIn('cached_config != work_config', self.source)

    def test_fixture_keeps_full_validation_contract(self):
        for value in (
            '"DVD_BOOT_001"', '"DVD_MENU_001"', '"DVD_AUDIO_001"',
            '"DVD_SPU_001"', '"DVD_PROTOCOL_001"',
            'print(f"PASS: {len(test_cases)} Codex playback test cases")',
        ):
            self.assertIn(value, self.source)


if __name__ == "__main__":
    unittest.main()
