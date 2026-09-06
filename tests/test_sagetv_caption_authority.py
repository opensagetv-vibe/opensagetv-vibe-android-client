import pathlib
import sys
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

from mcp_caption_test import caption_time_count, stable_caption_time_ms


class SageTvCaptionAuthorityTests(unittest.TestCase):
    def test_android_implements_legacy_extender_subtitle_callback_producer(self):
        profile = (
            ROOT
            / "source/dev/core/src/main/java/opensagetv/vibe/miniclient/profiles/android.properties"
        ).read_text(encoding="utf-8")
        connection = (
            ROOT
            / "source/dev/core/src/main/java/opensagetv/vibe/miniclient/MiniClientConnection.java"
        ).read_text(encoding="utf-8")
        bridge = (
            ROOT
            / "source/dev/core/src/main/java/opensagetv/vibe/miniclient/video/LegacyExtenderCaptionBridge.java"
        ).read_text(encoding="utf-8")
        policy = (
            ROOT
            / "source/dev/core/src/main/java/opensagetv/vibe/miniclient/video/LegacySubtitleCallbackPolicy.java"
        ).read_text(encoding="utf-8")
        self.assertIn("GFX_SUBTITLES=TRUE", profile)
        self.assertIn("LegacySubtitleCallbackPolicy", connection)
        self.assertIn("postSubtitleInfo(long pts", connection)
        self.assertIn("CC_SUBTITLE = 0x10", bridge)
        self.assertIn("onCeaSample", bridge)
        self.assertIn("sink.postSubtitleInfo", bridge)
        self.assertIn('"gsyplayer"', policy)
        self.assertNotIn('|| "ijkplayer"', policy)

        for relative_path in (
            "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/media3/LegacyCaptionExtractorsFactory.java",
            "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/exoplayer2/LegacyCaptionExtractorsFactory.java",
        ):
            extractor = (ROOT / relative_path).read_text(encoding="utf-8")
            self.assertIn("SAMPLE_DATA_PART_MAIN", extractor)
            self.assertIn("bridge.onCeaSample", extractor)

        for relative_path in (
            "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/media3/Media3MediaPlayerImpl.java",
            "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/exoplayer2/Exo2MediaPlayerImpl.java",
        ):
            player = (ROOT / relative_path).read_text(encoding="utf-8")
            self.assertIn("LegacyCaptionExtractorsFactory", player)
            self.assertIn("isSubtitleCallbackEnabled", player)
            self.assertIn("isForwardingCurrentStream", player)

        debug_state = (
            ROOT
            / "source/dev/android-tv/src/debug/java/opensagetv/vibe/miniclient/android/tv/debug/DebugStateProvider.java"
        ).read_text(encoding="utf-8")
        self.assertIn("legacyCaptionCallbacksNegotiated", debug_state)
        self.assertIn("legacyCaptionWireEventCount", debug_state)
        self.assertIn("legacyCaptionWireBytes", debug_state)
        self.assertIn("legacyCaptionCallbackCount", debug_state)
        self.assertIn("legacyCaptionCallbackBytes", debug_state)

    def test_server_caption_property_controls_media_player(self):
        connection = (ROOT / "source/dev/core/src/main/java/opensagetv/vibe/miniclient/MiniClientConnection.java").read_text(
            encoding="utf-8"
        )
        media_cmd = (ROOT / "source/dev/core/src/main/java/opensagetv/vibe/miniclient/MediaCmd.java").read_text(
            encoding="utf-8"
        )
        self.assertIn('"VIDEO_CC_STATE".equals(propName)', connection)
        self.assertIn("setSageTvClosedCaptionState(ccState)", connection)
        self.assertIn("applySageTvClosedCaptionState();", media_cmd)
        self.assertIn("currentPlayer.setSubtitleTrack(MiniPlayerPlugin.DISABLE_TRACK)", media_cmd)
        self.assertIn("currentPlayer.setPreferredSubtitleTrack()", media_cmd)

    def test_player_retains_selection_until_tracks_are_ready(self):
        for relative_path in (
            "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/media3/Media3MediaPlayerImpl.java",
            "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/exoplayer2/Exo2MediaPlayerImpl.java",
        ):
            source = (ROOT / relative_path).read_text(encoding="utf-8")
            self.assertIn("requestedSubtitleTrack = streamPos", source)
            self.assertIn("Applying pending SageTV subtitle track", source)
            self.assertIn("requestedSubtitleTrack = PREFERRED_TRACK", source)
            self.assertIn("resolvePreferredSubtitleTrack()", source)

    def test_long_press_navigation_exposes_legacy_caption_compatibility(self):
        navigation_java = (
            ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/NavigationDialog.java"
        ).read_text(encoding="utf-8")
        self.assertIn("nav_closed_captions", navigation_java)
        self.assertIn("onClosedCaptions()", navigation_java)
        self.assertIn("setLegacyServerCaptionMode", navigation_java)
        self.assertIn("hasSageTvClosedCaptionState", navigation_java)
        strings = (
            ROOT / "source/dev/android-shared/src/main/res/values/strings.xml"
        ).read_text(encoding="utf-8")
        self.assertIn("standard SageTV extender callback", strings)
        self.assertIn("This player did not negotiate SageTV subtitle callbacks", strings)
        for relative_path in (
            "source/dev/android-shared/src/main/res/layout/navigation.xml",
            "source/dev/android-tv/src/main/res/layout/navigation.xml",
            "source/dev/android-tv/src/main/res/layout-notouch/navigation.xml",
        ):
            self.assertIn("nav_closed_captions", (ROOT / relative_path).read_text(encoding="utf-8"))

    def test_legacy_caption_choices_do_not_override_received_stv_state(self):
        media_cmd = (ROOT / "source/dev/core/src/main/java/opensagetv/vibe/miniclient/MediaCmd.java").read_text(
            encoding="utf-8"
        )
        arrays = (ROOT / "source/dev/android-shared/src/main/res/values/arrays.xml").read_text(
            encoding="utf-8"
        )
        self.assertIn("setLegacyServerCaptionMode", media_cmd)
        self.assertIn("sageTvClosedCaptionStateReceived", media_cmd)
        self.assertIn("? sageTvClosedCaptionState != 0", media_cmd)
        for mode in ("stv", "off", "cc1", "cc2"):
            self.assertIn(f"<item>{mode}</item>", arrays)

    def test_track_preferences_select_below_stv_caption_authority(self):
        prefs = (ROOT / "source/dev/android-shared/src/main/res/xml/playback_track_prefs.xml").read_text(
            encoding="utf-8"
        )
        fragment = (
            ROOT
            / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/ui/settings/PlaybackTrackSettingsFragment.java"
        ).read_text(encoding="utf-8")
        policy = (
            ROOT / "source/dev/core/src/main/java/opensagetv/vibe/miniclient/media/TrackPreferencePolicy.java"
        ).read_text(encoding="utf-8")
        self.assertIn('android:key="preferred_audio_language"', prefs)
        self.assertIn('android:key="preferred_subtitle_language"', prefs)
        self.assertIn('android:key="preferred_caption_standard"', prefs)
        self.assertIn('android:key="preferred_caption_service"', prefs)
        self.assertNotIn("CheckBoxPreference", prefs)
        self.assertIn("explicit fallback", fragment)
        self.assertIn("legacy_server_caption_mode", prefs)
        self.assertIn("findPreferredSubtitleTrack", policy)

    def test_caption_renderer_is_reenabled_after_off(self):
        """Off disables the renderer as well as text tracks; On must undo both."""
        for relative_path in (
            "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/media3/Media3MediaPlayerImpl.java",
            "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/exoplayer2/Exo2MediaPlayerImpl.java",
        ):
            player = (ROOT / relative_path).read_text(encoding="utf-8")
            disable = player.index("setRendererDisabled(rendererIndex, true)")
            enable = player.index("setRendererDisabled(rendererIndex, false)", disable)
            select = player.index("setTrackTypeDisabled(trackType, false)", enable)
            self.assertLess(disable, enable, relative_path)
            self.assertLess(enable, select, relative_path)

    def test_callback_players_detach_local_overlay_when_sagetv_renders_captions(self):
        for relative_path in (
            "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/media3/Media3MediaPlayerImpl.java",
            "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/exoplayer2/Exo2MediaPlayerImpl.java",
        ):
            player = (ROOT / relative_path).read_text(encoding="utf-8")
            callback_branch = player[player.index("boolean serverRendersCaptions") :]
            callback_branch = callback_branch[:1500]
            self.assertIn("legacyCaptionBridge.isForwardingCurrentStream()", callback_branch)
            self.assertIn("subView.setCues(java.util.Collections.<Cue>emptyList())", callback_branch)
            self.assertIn("RemoveSubTitleView();", callback_branch)

    def test_physical_caption_gate_defaults_to_stv_authority(self):
        script = (ROOT / "scripts/mcp_caption_test.py").read_text(encoding="utf-8")
        self.assertIn('choices=("stv", "debug")', script)
        self.assertIn('default="stv"', script)
        self.assertIn('"--toggle-off-on"', script)
        self.assertIn("caption cues after same-session Off -> On", script)
        self.assertIn('"--continuity-window-s"', script)
        self.assertIn("captions remained continuous after re-enable", script.lower())
        self.assertIn('if args.authority == "debug":', script)
        self.assertIn("Android subtitle selector was not invoked", script)
        self.assertIn('"--show-stv-timeline"', script)
        self.assertIn('{"key": "PAUSE"}', script)
        self.assertNotIn('{"key": "RIGHT"}', script)
        self.assertIn('"--sync-tolerance-ms"', script)
        self.assertIn('default=1000', script)
        self.assertIn('"--sync-sample-s"', script)
        self.assertIn('"--timeline-hold-s"', script)
        self.assertIn('default=12.0', script)
        self.assertIn("does not change runtime settings", script)
        self.assertIn('"restart_from_beginning": not args.preserve_resume', script)
        self.assertIn('"--seek-command"', script)
        self.assertIn('"--seek-settle-ms"', script)
        self.assertIn('3000 if args.streaming in ("dynamic", "push") else 1000', script)
        self.assertIn('"dev_run_seek_check"', script)
        self.assertIn("caption recovery after seek", script)
        self.assertIn("caption_delta_ms - timeline_delta_ms", script)
        self.assertIn("drift_ms <= args.sync_tolerance_ms", script)
        self.assertIn("the captured screenshot is the absolute-offset gate", script)
        self.assertIn("had no stable middle cue", script)
        self.assertIn("DPAD RIGHT is SageTV's skip command", script)
        self.assertIn("test-only caption/timeline evidence remains visible", script)
        self.assertIn('"--legacy-extender-callback"', script)
        self.assertIn('"--expect-no-legacy-callback"', script)
        self.assertIn('choices=("exoplayer", "media3", "ijkplayer")', script)
        self.assertIn("unsupported backend stayed playable", script)
        self.assertIn('"--cycle-stv-caption-states"', script)
        self.assertIn('dev_set_stv_caption_state', script)
        self.assertIn('Off/CC1/CC2/Off/CC1', script)
        self.assertIn("legacyCaptionCallbacksNegotiated", script)
        self.assertIn("legacyCaptionCallbackActive", script)
        self.assertIn("legacyCaptionWireEventCount", script)
        self.assertIn("legacyCaptionWireBytes", script)
        self.assertIn("without a local overlay", script)
        self.assertIn("event-225 output", script)

    def test_fixture_caption_timestamp_parser_uses_stable_middle_cue(self):
        self.assertEqual(
            stable_caption_time_ms("PTS 00:09:38.000\nPTS 00:09:38.500\nPTS 00:09:39.000"),
            578500,
        )
        self.assertEqual(stable_caption_time_ms("PTS 01:02:03.004"), 3723004)
        self.assertIsNone(stable_caption_time_ms("ordinary broadcast caption"))
        self.assertEqual(caption_time_count("PTS 00:00:01.000 PTS 00:00:01.500 PTS 00:00:02.000"), 3)
        self.assertEqual(caption_time_count("00 PT:13:26S 00.000"), 0)

    def test_caption_overlay_clears_sagetv_timeline(self):
        policy = (
            ROOT
            / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/TextSubtitlePresentation.java"
        ).read_text(encoding="utf-8")
        self.assertIn("DEFAULT_SAFE_AREA_PERCENT = 18", policy)
        for relative_path in (
            "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/media3/Media3MediaPlayerImpl.java",
            "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/exoplayer2/Exo2MediaPlayerImpl.java",
        ):
            source = (ROOT / relative_path).read_text(encoding="utf-8")
            self.assertIn("setBottomPaddingFraction(subtitleSafeAreaPercent / 100.0f)", source)
            self.assertIn("TextSubtitlePresentation.safeAreaPercent(prefs)", source)


if __name__ == "__main__":
    unittest.main()
