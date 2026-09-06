import pathlib
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[1]
SHARED = ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video"
DEBUG = ROOT / "source/dev/android-tv/src/debug/java/opensagetv/vibe/miniclient/android/tv/debug"


class CodecCapabilityProfileTest(unittest.TestCase):
    def test_profile_records_platform_capabilities_without_guessing_interlace(self):
        text = (SHARED / "DeviceCodecCapabilityProfile.java").read_text(encoding="utf-8")
        for required in (
            "FEATURE_AdaptivePlayback",
            "FEATURE_SecurePlayback",
            "FEATURE_TunneledPlayback",
            "getSupportedWidths",
            "getSupportedHeights",
            "profileLevels",
            "not_reported_by_android",
        ):
            self.assertIn(required, text)

    def test_mpeg2_interlace_reporting_is_bitstream_observation_not_android_claim(self):
        scanner = (ROOT / "source/dev/core/src/main/java/opensagetv/vibe/miniclient/video/Mpeg2PictureTimestampCompleter.java").read_text(encoding="utf-8")
        debug = (DEBUG / "DebugCodecCapabilityCommands.java").read_text(encoding="utf-8")
        self.assertIn("getInterlaceObservation()", scanner)
        self.assertIn('return "unknown"', scanner)
        self.assertIn('"codecInterlaceObserved="', debug)
        self.assertIn('"codecDeinterlaceControl=not_exposed_by_android;"', debug)
        for relative in (
            "media3/LegacyCaptionExtractorsFactory.java",
            "exoplayer2/LegacyCaptionExtractorsFactory.java",
        ):
            wrapper = (SHARED / relative).read_text(encoding="utf-8")
            self.assertIn("ObservingVideoTrackOutput", wrapper)
            self.assertIn("observer.consume", wrapper)
        mcp_server = (ROOT / "mcp/src/sagetv_dev_mcp/server.py").read_text(encoding="utf-8")
        disc_gate = (ROOT / "scripts/mcp_disc_test.py").read_text(encoding="utf-8")
        self.assertIn('"health_mpeg2InterlaceObservation"', mcp_server)
        self.assertIn('state.get("health_mpeg2SequenceExtensionSeen")', disc_gate)

    def test_runtime_observations_are_on_demand_and_not_an_automatic_blacklist(self):
        text = (SHARED / "CodecRuntimeObservations.java").read_text(encoding="utf-8")
        self.assertNotIn("blacklist.add", text)
        self.assertIn("explicit on-demand decoder snapshot", text)
        self.assertIn("initCount", text)
        self.assertIn("releaseCount", text)

    def test_profile_adds_no_continuous_player_analytics_callbacks(self):
        for relative in (
            "media3/Media3MediaPlayerImpl.java",
            "exoplayer2/Exo2MediaPlayerImpl.java",
        ):
            text = (SHARED / relative).read_text(encoding="utf-8")
            self.assertNotIn("import androidx.media3.exoplayer.analytics.AnalyticsListener", text)
            self.assertNotIn("import com.google.android.exoplayer2.analytics.AnalyticsListener", text)
            active_lines = [
                line.strip() for line in text.splitlines()
                if "addAnalyticsListener(" in line and not line.strip().startswith("//")
            ]
            self.assertEqual([], active_lines)
            self.assertNotIn("CodecRuntimeObservations", text)

    def test_both_selectors_use_platform_profile_and_preserve_explicit_fallback(self):
        for relative in (
            "media3/Media3CodecSelector.java",
            "exoplayer2/CustomMediaCodecSelector.java",
        ):
            text = (SHARED / relative).read_text(encoding="utf-8")
            self.assertIn("DeviceCodecCapabilityProfile.current().isSoftwareDecoder", text)
            hardware = text.index("case HARDWARE_PREFERRED:")
            self.assertLess(text.index("selected.addAll(hardware);", hardware),
                            text.index("selected.addAll(software);", hardware))

    def test_user_decoder_filter_applies_to_every_mediacodec_selector(self):
        policy = (SHARED / "AndroidCodecPolicy.java").read_text(encoding="utf-8")
        self.assertIn('return "disabled_"', policy)
        self.assertIn("isCodecDisabled", policy)
        for relative in (
            "media3/Media3CodecSelector.java",
            "exoplayer2/CustomMediaCodecSelector.java",
            "ijkplayer/CodecSelector.java",
        ):
            text = (SHARED / relative).read_text(encoding="utf-8")
            self.assertIn("AndroidCodecPolicy.isCodecDisabled", text)

    def test_kodi_derived_hardware_guards_are_explicit(self):
        profile = (SHARED / "DeviceCodecCapabilityProfile.java").read_text(encoding="utf-8")
        media3 = (SHARED / "media3/Media3MediaPlayerImpl.java").read_text(encoding="utf-8")
        for codec_prefix in ('"omx.mtk"', '"c2.mtk"', '"omx.nvidia"'):
            self.assertIn(codec_prefix, profile)
        self.assertIn("hasMpeg2HardwareDecoderNeedingMissingPtsRepair", media3)
        self.assertIn("setTunnelingEnabled(false)", media3)

    def test_debug_receiver_exports_profile_only_on_request(self):
        receiver = (DEBUG / "DevTestReceiver.java").read_text(encoding="utf-8")
        command = (DEBUG / "DebugCodecCapabilityCommands.java").read_text(encoding="utf-8")
        self.assertIn('"codec_capabilities".equals(op)', receiver)
        self.assertIn("DeviceCodecCapabilityProfile.current().compactWire()", command)
        self.assertIn("PlaybackHealthProbe.capture(player)", command)
        self.assertIn("CodecRuntimeObservations.compactWire(", command)
        self.assertIn("DeviceAudioCapabilityProfile.current(context).compactWire()", command)

    def test_audio_capabilities_separate_decoder_sink_and_passthrough_claims(self):
        profile = (SHARED / "DeviceAudioCapabilityProfile.java").read_text(encoding="utf-8")
        options = (ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/AndroidMiniClientOptions.java").read_text(encoding="utf-8")
        for required in (
            "hasDecoder(AudioCodec codec)",
            "sinkSupportsEncoded(AudioCodec codec)",
            "audioPassthroughAdvertised=false",
            "audioPassthroughState=not_inferred_from_mime",
        ):
            self.assertIn(required, profile)
        self.assertIn("deviceAudio.canPlay(allCodecs[i])", options)
        for relative in ("media3/Media3MediaPlayerImpl.java", "exoplayer2/Exo2MediaPlayerImpl.java"):
            player = (SHARED / relative).read_text(encoding="utf-8")
            self.assertNotIn('"encoded/passthrough-capable"', player)
            self.assertIn("output mode is selected by Android AudioSink", player)

    def test_root_workflow_exposes_physical_codec_gate(self):
        workflow = (ROOT / "dev.sh").read_text(encoding="utf-8")
        script = (ROOT / "scripts/mcp_codec_capability_test.py").read_text(encoding="utf-8")
        self.assertIn("mcp-codec-capability-test)", workflow)
        self.assertIn('"decoding": "hardware_preferred"', script)
        self.assertIn('"video/mpeg2,hw"', script)
        self.assertIn('"video/mpeg2,sw"', script)
        self.assertIn("codec-capability-{args.player}-{args.streaming}.json", script)
        self.assertIn("codecInterlaceObserved", script)
        self.assertIn("codecDeinterlaceControl", script)

    def test_root_workflow_exposes_audio_capability_matrix(self):
        workflow = (ROOT / "dev.sh").read_text(encoding="utf-8")
        script = (ROOT / "scripts/mcp_audio_capability_matrix.py").read_text(encoding="utf-8")
        self.assertIn("mcp-audio-capability-matrix)", workflow)
        self.assertIn('"AC3":', script)
        self.assertIn('"EAC3":', script)
        self.assertIn('"DTS":', script)
        self.assertIn("audioPassthroughAdvertised", script)
        self.assertIn('state.get("playbackSource") != "SAGETV_PULL"', script)


if __name__ == "__main__":
    unittest.main()
