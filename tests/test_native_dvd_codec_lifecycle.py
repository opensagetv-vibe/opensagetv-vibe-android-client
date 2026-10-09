from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
MEDIA3 = ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/media3"


class NativeDvdCodecLifecycleContractTest(unittest.TestCase):
    def test_first_buffer_diagnostics_are_bounded_debug_only_and_payload_free(self):
        renderer = (MEDIA3 / "NativeDvdVideoRenderer.java").read_text(encoding="utf-8")
        self.assertIn("ApplicationInfo.FLAG_DEBUGGABLE", renderer)
        self.assertIn("debugFirstBuffers && !firstInputRecorded", renderer)
        self.assertIn("debugFirstBuffers && !firstOutputRecorded", renderer)
        self.assertIn("super.processOutputBuffer(positionUs", renderer)
        self.assertNotIn("buffer.data.get(", renderer)
        self.assertNotIn("new Thread", renderer)

    def test_renderer_is_scoped_to_native_dvd_not_transformed_or_tv(self):
        source = (MEDIA3 / "Media3MediaPlayerImpl.java").read_text(encoding="utf-8")
        self.assertIn("dvdPushMode && !dvdTransformedTransport", source)
        factory = (MEDIA3 / "Media3AudioExtensionRenderersFactory.java").read_text(encoding="utf-8")
        self.assertIn("if (nativeDvd && dvdDecoderRecovery)", factory)
        self.assertIn("super.buildVideoRenderers(", factory)

    def test_optional_one_shot_restart_keeps_period_clock_and_codec_factory(self):
        renderer = (MEDIA3 / "NativeDvdVideoRenderer.java").read_text(encoding="utf-8")
        recovery = renderer.split("@Override public void render(", 1)[1].split("@Override protected void onDisabled", 1)[0]
        self.assertIn("startupRestartAttempted = true;", recovery)
        self.assertIn("isSourceReady()", recovery)
        self.assertIn("maybeInitCodecOrBypass();", recovery)
        self.assertNotIn("setMediaSource", recovery)
        self.assertNotIn("seekTo", recovery)
        self.assertNotIn("forceEnable", recovery)
        prefs = (ROOT / "source/dev/android-shared/src/main/res/xml/disc_playback_prefs.xml").read_text(encoding="utf-8")
        self.assertIn('android:key="disc_decoder_compatibility_recovery"', prefs)
        source = (MEDIA3 / "Media3MediaPlayerImpl.java").read_text(encoding="utf-8")
        self.assertIn("prefs.getBoolean(PrefStore.Keys.disc_decoder_compatibility_recovery, true)", source)

    def test_fault_is_debug_one_shot_and_never_a_core_or_socket_mutation(self):
        fault = (MEDIA3 / "NativeDvdCodecStartupFault.java").read_text(encoding="utf-8")
        self.assertIn("ApplicationInfo.FLAG_DEBUGGABLE", fault)
        self.assertIn("armed.getAndSet(false)", fault)
        self.assertIn("debug && NativeDvdCodecLifecyclePolicy.releaseBeforeDisable", fault)
        self.assertNotIn("Socket", fault)
        server = (ROOT / "mcp/src/sagetv_dev_mcp/server.py").read_text(encoding="utf-8")
        self.assertIn("Stock sage.SageTV.api/apiUI cannot control a local Android MediaCodec", server)
        receiver = (ROOT / "source/dev/android-tv/src/debug/java/opensagetv/vibe/miniclient/android/tv/debug/DevTestReceiver.java").read_text(encoding="utf-8")
        self.assertIn('"native_dvd_codec_fault".equals(op)', receiver)

    def test_physical_fault_gate_requires_one_restart_and_disarms_on_failure(self):
        source = (ROOT / "scripts/mcp_dvd_codec_recovery_test.py").read_text(encoding="utf-8")
        self.assertIn('int(state.get("health_videoDecoderInitCount", 0)) == 2', source)
        self.assertIn('int(state.get("health_videoDecoderReleaseCount", 0)) == 1', source)
        self.assertIn('int(after.get("serverFlushSequence", 0)) == int(state["serverFlushSequence"])', source)
        final = source.split("finally:", 1)[1]
        self.assertIn('"enabled": False', final)
        self.assertIn("p.close()", final)
        self.assertIn("Refusing fault/navigation on another DVD volume", source)

    def test_existing_codec_queueing_factory_is_preserved(self):
        factory = (MEDIA3 / "Media3AudioExtensionRenderersFactory.java").read_text(encoding="utf-8")
        self.assertIn("NativeDvdVideoRenderer(context, getCodecAdapterFactory()", factory)

    def test_release_precedes_media3_disable_flush(self):
        renderer = (MEDIA3 / "NativeDvdVideoRenderer.java").read_text(encoding="utf-8")
        self.assertLess(renderer.index("releaseCodec();"), renderer.index("super.onDisabled();"))
        self.assertIn("NativeDvdCodecLifecyclePolicy.releaseBeforeDisable", renderer)
        self.assertNotIn("new Thread", renderer)

    def test_position_reset_releases_only_affected_keyframe_reset(self):
        renderer = (MEDIA3 / "NativeDvdVideoRenderer.java").read_text(encoding="utf-8")
        reset = renderer.split("@Override protected void onPositionReset", 1)[1]
        self.assertIn("resetToKeyFrame && info != null", reset)
        self.assertLess(reset.index("releaseCodec();"), reset.index("super.onPositionReset("))
        self.assertNotIn("shouldReleaseCodecInsteadOfFlushing()", renderer)


if __name__ == "__main__":
    unittest.main()
