"""An advancing IJK audio clock must not certify blank hardware video."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
PROBE = ROOT / "source/dev/android-tv/src/debug/java/opensagetv/vibe/miniclient/android/tv/debug/PlaybackHealthProbe.java"


class IjkFirstFrameGateTests(unittest.TestCase):
    def test_snapshot_uses_existing_real_first_frame_callback(self):
        text = PROBE.read_text(encoding="utf-8")
        self.assertIn("out.firstVideoFrameRendered = topLevelPlayer.hasRenderedFirstVideoFrame();", text)
        self.assertIn('append(out, p + "firstVideoFrameRendered", firstVideoFrameRendered);', text)
        self.assertNotIn("setOnInfoListener", text)

    def test_only_video_ijk_clock_needs_rendering_start(self):
        text = PROBE.read_text(encoding="utf-8")
        gate = text.split("static boolean readyAndPlaying(Snapshot snapshot)", 1)[1].split("static boolean surfaceHealthy", 1)[0]
        self.assertIn('!snapshot.backendClass.endsWith(".IJKMediaPlayerImpl")', gate)
        self.assertIn("snapshot.videoWidth <= 0", gate)
        self.assertIn("snapshot.firstVideoFrameRendered", gate)
        self.assertIn("snapshot.basicIsPlaying", gate)
        self.assertIn("snapshot.playbackState == EXO_STATE_READY", gate)

    def test_ijk_signal_is_not_prepared_or_playing(self):
        source = ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/ijkplayer/IJKMediaPlayerImpl.java"
        text = source.read_text(encoding="utf-8")
        self.assertIn("firstVideoFrameRendered = false;", text)
        callback = text.split("if (what == IMediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START)", 1)[1].split("return false;", 1)[0]
        self.assertIn("firstVideoFrameRendered = true;", callback)

    def test_decoder_inventory_is_on_demand_and_does_not_promote_output_counters(self):
        text = PROBE.read_text(encoding="utf-8")
        block = text.split('if (className.equals("tv.danmaku.ijk.media.player.IjkMediaPlayer"))', 1)[1].split(
            'out.reason = "renderer_counters_not_available"', 1)[0]
        self.assertIn('invokeOptional(backendPlayer, "getMediaInfo")', block)
        self.assertIn('readStringField(info, "mVideoDecoderImpl")', block)
        self.assertIn('"mediacodec".equalsIgnoreCase(module)', block)
        self.assertIn('AndroidCodecPolicy.isSoftwareCodecName(out.videoDecoderName)', block)
        self.assertIn('"avcodec".equalsIgnoreCase(module)', block)
        self.assertNotIn("out.supported = true", block)
        self.assertNotIn("setOnInfoListener", block)


if __name__ == "__main__":
    unittest.main()
