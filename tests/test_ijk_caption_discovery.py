"""Guard the event-driven IJK caption retry, not diagnostic-driven selection."""
from pathlib import Path
import unittest

VIDEO = Path(__file__).resolve().parents[1] / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video"


class IjkCaptionDiscoveryTests(unittest.TestCase):
    def test_inventory_is_published_before_optional_notification(self):
        base = (VIDEO / "BaseMediaPlayerImpl.java").read_text()
        callback = base.split("onTeletextServicesChanged(", 1)[1].split("@Override public void onTeletextCue", 1)[0]
        self.assertLess(callback.index("teletextServices ="), callback.index("onTeletextSubtitleTracksChanged();"))
        default = base.split("protected void onTeletextSubtitleTracksChanged()", 1)[1].split("@Override", 1)[0]
        self.assertNotIn("applyConfiguredClosedCaptionSlot();", default)

    def test_native_prepare_and_late_discovery_share_guarded_retry(self):
        ijk = (VIDEO / "ijkplayer/IJKMediaPlayerImpl.java").read_text()
        prepared = ijk.split("public void onPrepared(IMediaPlayer mp)", 1)[1].split("player.prepareAsync();", 1)[0]
        self.assertIn("onTeletextSubtitleTracksChanged();", prepared)
        hook = ijk.split("protected void onTeletextSubtitleTracksChanged()", 1)[1].split("public SubtitleTrack[] getSubtitleTracks()", 1)[0]
        for guard in ("context.runOnUiThread", "!playerReady", "captionPlayer == null", "player != captionPlayer", "!isCurrentPlaybackSession(captionSession)"):
            self.assertIn(guard, hook)
        self.assertIn("applyConfiguredClosedCaptionSlot();", hook)
        self.assertNotIn("setSubtitleTrack(0)", hook)
        self.assertNotIn("player.start()", hook)

    def test_caption_inventory_getters_remain_read_only(self):
        ijk = (VIDEO / "ijkplayer/IJKMediaPlayerImpl.java").read_text()
        getter = ijk.split("public SubtitleTrack[] getSubtitleTracks()", 1)[1].split("public void setSubtitleTrack", 1)[0]
        self.assertNotIn("applyConfiguredClosedCaptionSlot", getter)
        self.assertNotIn("onTeletextSubtitleTracksChanged();", getter)


if __name__ == "__main__":
    unittest.main()
