import pathlib
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[1]


class LiveEdgePolicyTests(unittest.TestCase):
    def test_policy_uses_buffered_edge_only_for_unknown_growing_media(self):
        policy = (ROOT / "source/dev/core/src/main/java/opensagetv/vibe/miniclient/video/PlaybackSeekPolicy.java").read_text(
            encoding="utf-8"
        )
        self.assertIn("effectiveDurationMs <= 0 && growingMedia && bufferedPositionMs > 0", policy)

    def test_both_exo_backends_use_the_shared_buffered_edge_policy(self):
        media3 = (ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/media3/Media3MediaPlayerImpl.java").read_text(
            encoding="utf-8"
        )
        legacy = (ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/exoplayer2/Exo2MediaPlayerImpl.java").read_text(
            encoding="utf-8"
        )
        self.assertIn("durationMs, bufferedPositionMs, mediaContext.isTimeshifted()", media3)
        self.assertIn("durationMs, bufferedPositionMs, mediaContext.isTimeshifted()", legacy)

    def test_live_gate_requires_clamp_event_and_recovered_output(self):
        script = (ROOT / "scripts/mcp_live_tv_test.py").read_text(encoding="utf-8")
        self.assertIn('"--verify-live-edge-clamp"', script)
        self.assertIn('"seek_clamped_live_edge" in event_names', script)
        self.assertIn('"dev_seek_time"', script)


if __name__ == "__main__":
    unittest.main()
