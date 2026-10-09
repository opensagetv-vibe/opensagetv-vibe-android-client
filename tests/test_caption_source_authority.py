from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
VIDEO = ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video"


class CaptionSourceAuthorityTests(unittest.TestCase):
    def test_malformed_708_guard_uses_checked_text_error_not_player_recovery(self):
        for folder, name in (("media3", "Media3"), ("exoplayer2", "Exo2")):
            source = (VIDEO / folder / (name + "Cea708DecoderFactory.java")).read_text()
            self.assertIn("MimeTypes.APPLICATION_CEA708.equals(format.sampleMimeType)", source)
            self.assertIn("? new GuardedDecoder(decoder) : decoder", source)
            self.assertIn("if (!Cea708MalformedPacketPolicy.isMalformedPacket(failure)) throw failure", source)
            self.assertIn("throw new SubtitleDecoderException", source)
            self.assertNotIn("catch (RuntimeException", source)
            self.assertNotIn("delegate.flush();", source.split("catch (IllegalStateException", 1)[1].split("setPositionUs", 1)[0])
            self.assertNotIn("reprepare", source)
            factory = (VIDEO / folder / (name + "AudioExtensionRenderersFactory.java")).read_text()
            self.assertIn("new " + name + "Cea708DecoderFactory()", factory)
        modern = (VIDEO / "media3/Media3Cea708DecoderFactory.java").read_text()
        self.assertIn("delegate.setOutputStartTimeUs(value)", modern)

    def test_real_native_evidence_is_available_for_both_exo_and_gsy_delegates(self):
        for path in ("media3/Media3MediaPlayerImpl.java", "exoplayer2/Exo2MediaPlayerImpl.java"):
            source = (VIDEO / path).read_text()
            callback = source.split("public boolean hasObservedCeaCaptionData()", 1)[1].split("}", 1)[0]
            self.assertIn("legacyCaptionBridge.isForwardingCurrentStream()", callback)
            self.assertNotIn("shouldForwardExtractor", source)

    def test_side_channel_keeps_cursor_but_never_accumulates_duplicate_callbacks(self):
        policy = (ROOT / "source/dev/core/src/main/java/opensagetv/vibe/miniclient/video/LegacySubtitleCallbackPolicy.java").read_text()
        self.assertIn("return !nativeCeaObserved", policy)
        self.assertNotIn("setBoolean", policy)
        self.assertNotIn("sleep", policy)
        side = (VIDEO / "FixedCaptionSideChannelClient.java").read_text()
        self.assertIn("owner instanceof MiniPlayerPlugin", side)
        self.assertIn("((MiniPlayerPlugin) owner).hasObservedCeaCaptionData()", side)
        self.assertIn("if (shouldForwardSourceCaptions())", side)
        self.assertIn("else activeBridge.clearPending()", side)
        self.assertIn("cursor = batch.cursor", side)
        self.assertIn("current != null && !shouldForwardSourceCaptions()", side)


if __name__ == "__main__":
    unittest.main()
