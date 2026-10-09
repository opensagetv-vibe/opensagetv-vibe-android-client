"""Caption oracles must observe the selected renderer, including native IJK TT."""
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from mcp_caption_test import caption_renderer_disabled, caption_progressed


class CaptionRendererOracleTests(unittest.TestCase):
    def test_teletext_off_does_not_require_nonexistent_native_text_overlay(self):
        self.assertTrue(caption_renderer_disabled({"selectedSubtitleTrack": -1,
            "selectedSubtitleTrackRaw": 8192, "teletextOverlayVisible": False}, "TELETEXT"))

    def test_unknown_or_still_visible_renderer_is_not_off(self):
        for state in ({"selectedSubtitleTrack": -1},
                      {"selectedSubtitleTrack": -1, "teletextOverlayVisible": True},
                      {"selectedSubtitleTrack": 21504, "teletextOverlayVisible": False},
                      {"selectedSubtitleTrack": -1, "teletextOverlayVisible": False,
                       "subtitleOverlayAttached": True}):
            self.assertFalse(caption_renderer_disabled(state, "TELETEXT"))
        self.assertFalse(caption_renderer_disabled({"selectedSubtitleTrack": -1}, "DVB"))

    def test_teletext_seek_requires_its_own_new_nonempty_visible_cue(self):
        good = {"teletextCueUpdateCount": 59, "currentTeletextCueText": "real page text",
                "teletextOverlayVisible": True}
        self.assertTrue(caption_progressed(good, "TELETEXT", 58))
        for changes in ({"teletextCueUpdateCount": 58}, {"currentTeletextCueText": ""},
                        {"teletextOverlayVisible": False}):
            self.assertFalse(caption_progressed(dict(good, **changes), "TELETEXT", 58))

    def test_bitmap_and_cea_still_require_existing_extractor_cue_evidence(self):
        self.assertTrue(caption_progressed({"subtitleCueUpdateCount": 4,
            "currentSubtitleCueCount": 1, "subtitleOverlayAttached": True}, "DVB", 3))
        self.assertFalse(caption_progressed({"teletextCueUpdateCount": 4,
            "teletextOverlayVisible": True}, "DVB", 3))

    def test_local_pause_flag_is_executed_before_final_capture(self):
        source = (Path(__file__).resolve().parents[1] / "scripts/mcp_caption_test.py").read_text()
        local = source.split("before_seek_cues = caption_update_count", 1)[1]
        self.assertIn("if args.pause_resume:", local)
        self.assertIn('"local captions and playback resumed after pause"', local)
        self.assertIn("caption_progressed(state, selected_codec, held_updates)", local)

    def test_native_caption_recovery_uses_native_playing_not_exo_default(self):
        state = {"health_probeSupported": False, "health_isPlaying": False,
                 "health_basicIsPlaying": True, "teletextCueUpdateCount": 60,
                 "currentTeletextCueText": "new native caption", "teletextOverlayVisible": True}
        self.assertTrue(caption_progressed(state, "TELETEXT", 59))
        for changes in ({"health_basicIsPlaying": False}, {"health_errorState": True},
                        {"teletextCueUpdateCount": 59}, {"teletextOverlayVisible": False}):
            self.assertFalse(caption_progressed(dict(state, **changes), "TELETEXT", 59))
        state.pop("health_basicIsPlaying")
        self.assertFalse(caption_progressed(state, "TELETEXT", 59))

    def test_extractor_caption_recovery_still_requires_exo_playing(self):
        state = {"health_probeSupported": True, "health_isPlaying": False,
                 "health_basicIsPlaying": True, "subtitleCueUpdateCount": 4,
                 "currentSubtitleCueCount": 1, "subtitleOverlayAttached": True}
        self.assertFalse(caption_progressed(state, "DVB", 3))
        self.assertTrue(caption_progressed(dict(state, health_isPlaying=True), "DVB", 3))


if __name__ == "__main__":
    unittest.main()
