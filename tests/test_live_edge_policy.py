import pathlib
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[1]


class LiveEdgePolicyTests(unittest.TestCase):
    def test_policy_does_not_treat_local_buffer_as_unknown_growing_duration(self):
        policy = (ROOT / "source/dev/core/src/main/java/opensagetv/vibe/miniclient/video/PlaybackSeekPolicy.java").read_text(
            encoding="utf-8"
        )
        self.assertNotIn("bufferedPositionMs", policy)

    def test_both_exo_backends_bound_only_against_real_duration(self):
        media3 = (ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/media3/Media3MediaPlayerImpl.java").read_text(
            encoding="utf-8"
        )
        legacy = (ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/exoplayer2/Exo2MediaPlayerImpl.java").read_text(
            encoding="utf-8"
        )
        self.assertIn("timeInMillis, durationMs, mediaContext.isTimeshifted()", media3)
        self.assertIn("timeInMillis, durationMs, mediaContext.isTimeshifted()", legacy)
        self.assertIn("buffered position describes Media3's local cache", media3)
        self.assertIn("buffered position is only the end of its local", legacy)

    def test_growing_snapshot_duration_does_not_override_server_timeline(self):
        policy = (ROOT / "source/dev/core/src/main/java/opensagetv/vibe/miniclient/video/PlaybackSeekPolicy.java").read_text(
            encoding="utf-8"
        )
        self.assertIn("durationMs <= 0 || growingMedia", policy)

    def test_live_gate_requires_clamp_event_and_recovered_output(self):
        script = (ROOT / "scripts/mcp_live_tv_test.py").read_text(encoding="utf-8")
        self.assertIn('"--verify-live-edge-clamp"', script)
        self.assertIn('"seek_clamped_live_edge" in event_names', script)
        self.assertIn('"dev_seek_time"', script)


if __name__ == "__main__":
    unittest.main()
