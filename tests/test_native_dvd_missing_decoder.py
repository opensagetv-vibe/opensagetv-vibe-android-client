from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
VIDEO = ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video"


class NativeDvdMissingDecoderTests(unittest.TestCase):
    def test_each_backend_checks_its_selector_before_creating_audio_player(self):
        for path in ("media3/Media3MediaPlayerImpl.java", "exoplayer2/Exo2MediaPlayerImpl.java"):
            source = (VIDEO / path).read_text()
            setup = source.split("protected void setupPlayer", 1)[1]
            guard = setup.split("NativeDvdVideoSupportPolicy.shouldReject", 1)[1]
            guard = guard.split("catch (", 1)[0]
            self.assertIn("dvdTransformedTransport", guard)
            self.assertIn("MimeTypes.VIDEO_MPEG2, false, false).size()", guard)
            self.assertIn("rejectNativeDvdVideoUnavailable();", guard)
            self.assertIn('"native".equals(prefs.getString(PrefStore.Keys.disc_playback_policy', setup)
            rejection = source.split("private void rejectNativeDvdVideoUnavailable()", 1)[1].split(
                "protected void setupPlayer", 1)[0]
            self.assertIn("eos = true", rejection)
            self.assertIn("state = EOS_STATE", rejection)
            self.assertIn("playerReady = false", rejection)
            self.assertIn("context.showErrorMessage", rejection)
            self.assertIn("native_dvd_video_decoder_unavailable", rejection)
            self.assertIn("player.stop()", rejection)
            self.assertNotIn("setString", guard)
            self.assertLess(setup.index("NativeDvdVideoSupportPolicy.shouldReject"),
                            setup.index("builder.build()"))

    def test_transitional_native_url_waits_for_actual_unsupported_video(self):
        for path in ("media3/Media3MediaPlayerImpl.java", "exoplayer2/Exo2MediaPlayerImpl.java"):
            source = (VIDEO / path).read_text()
            callback = source.split("public void onTracksChanged(Tracks tracks)", 1)[1].split(
                "public void onPlayerError", 1)[0]
            # Unsupported renderer groups can report an unknown group type;
            # actual format MIME is the qualified video-discovery boundary.
            self.assertIn('mime.startsWith("video/")', callback)
            self.assertIn("MimeTypes.VIDEO_MPEG2.equals", callback)
            self.assertIn("&& !supported", callback)
            self.assertIn("group.isTrackSupported(i, true)", callback)
            self.assertIn("shouldRejectDiscoveredVideo", callback)

    def test_valid_unsupported_oracle_is_separate_from_invalid_files_and_playback_pass(self):
        script = (ROOT / "scripts/mcp_disc_test.py").read_text()
        self.assertIn('"--expect-unsupported-native-video"', script)
        check = script.split("if args.expect_unsupported_native_video:", 1)[1].split(
            'if target_kind == "path" and target_value in expected_failures:', 1)[0]
        self.assertIn('"native_dvd_video_decoder_unavailable"', check)
        self.assertIn('"playbackPassed": False', check)
        self.assertIn('"health_audioRendered"', check)
        self.assertIn('"signatureFingerprint"', check)


if __name__ == "__main__":
    unittest.main()
