import pathlib
import sys
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

from mcp_caption_test import (
    caption_clock_diagnostic,
    caption_progressed,
    caption_time_count,
    resolve_requested_track_raw,
    resolve_requested_codec_track,
    mim_direct_owned,
    require_mim_direct_ownership,
    stable_caption_time_ms,
    subtitle_track_codec,
)


class SageTvCaptionAuthorityTests(unittest.TestCase):
    def test_late_cue_from_errored_or_stopped_player_is_not_recovery(self):
        samples = [
            ("DVB", {"subtitleCueUpdateCount": 4, "currentSubtitleCueCount": 1,
                     "subtitleOverlayAttached": True}),
            ("TELETEXT", {"teletextCueUpdateCount": 4, "currentTeletextCueText": "Caption",
                          "teletextOverlayVisible": True}),
        ]
        for codec, state in samples:
            self.assertTrue(caption_progressed(state, codec, 3))
            self.assertFalse(caption_progressed({**state, "health_errorState": True}, codec, 3))
            self.assertFalse(caption_progressed({**state, "health_isPlaying": False}, codec, 3))
    def test_owned_session_rechecks_negotiated_mode_after_controls(self):
        source = (ROOT / "scripts/mcp_session_test.py").read_text(encoding="utf-8")
        self.assertIn("from mcp_caption_test import require_mim_direct_ownership", source)
        self.assertIn('require_mim_direct_ownership(client, args.mim_direct_mode, "after " + label)', source)
        self.assertIn('require_mim_direct_ownership(client, args.mim_direct_mode, "settled after session controls")', source)

    def test_sage_lan_http_optin_is_shared_not_a_device_profile(self):
        import xml.etree.ElementTree as ET
        manifest = ET.parse(ROOT / "source/dev/android-shared/src/main/AndroidManifest.xml")
        self.assertEqual("true", manifest.getroot().find("application").get(
            "{http://schemas.android.com/apk/res/android}usesCleartextTraffic"))

    def test_caption_gate_rejects_legacy_fallback_as_direct_ownership(self):
        state = {"mimDirectNegotiatedMode": "transcode", "mimDirectSessionState": "active_transcode",
                 "playbackSource": "MIM_DIRECT", "health_dataSourceClass": "Media3MimDirectHttpDataSource"}
        self.assertTrue(mim_direct_owned(state, "transcode"))
        state["mimDirectSessionState"] = "active_transcode_startup_seek_suppressed"
        self.assertTrue(mim_direct_owned(state, "transcode"))
        state["playbackSource"] = "PUSH"
        self.assertFalse(mim_direct_owned(state, "transcode"))
        state["playbackSource"] = "MIM_DIRECT"
        state["health_dataSourceClass"] = "Media3PushDataSource"
        self.assertFalse(mim_direct_owned(state, "transcode"))

    def test_live_owned_gate_accepts_real_legacy_http_not_stock_or_unknown_source(self):
        from unittest.mock import patch
        from mcp_live_tv_test import require_mim_direct_ownership as require_live_owned
        state = {"mimDirectNegotiatedMode": "copy", "mimDirectSessionState": "active_copy",
                 "playbackSource": "MIM_DIRECT", "health_dataSourceClass": "Exo2MimDirectHttpDataSource"}
        with patch("mcp_live_tv_test.call_dict", return_value=state):
            self.assertEqual(state, require_live_owned(object(), "copy", "legacy"))
        state["health_dataSourceClass"] = "Exo2PullDataSource"
        with patch("mcp_live_tv_test.call_dict", return_value=state):
            with self.assertRaises(RuntimeError):
                require_live_owned(object(), "copy", "not-owned")

    def test_session_owned_gate_accepts_both_existing_http_observer_classes(self):
        source = (ROOT / "scripts/mcp_session_test.py").read_text()
        self.assertIn('data_source.endswith(("Media3MimDirectHttpDataSource",', source)
        self.assertIn('"Exo2MimDirectHttpDataSource"))', source)

    def test_settled_direct_gate_rechecks_state_after_controls(self):
        from unittest.mock import patch
        with patch("mcp_caption_test.call_dict", return_value={"playbackSource": "PUSH"}) as snapshot:
            with self.assertRaisesRegex(RuntimeError, "settled after controls"):
                require_mim_direct_ownership(object(), "transcode", "settled after controls")
            snapshot.assert_called_once()

    def test_caption_pause_observes_completion_without_replaying_key(self):
        script = (ROOT / "scripts/mcp_caption_test.py").read_text(encoding="utf-8")
        block = script.split("if args.pause_resume:", 1)[1].split("pause_wire =", 1)[0]
        self.assertIn('"requested PAUSE to reach the current player", args.cue_timeout_s', block)
        self.assertEqual(1, block.count('"firetv_key", {"key": "PAUSE"}'))
        self.assertIn("<= 100", block)

    def test_caption_exact_watch_waits_for_restored_stv_playback_to_stop(self):
        script = (ROOT / "scripts/mcp_caption_test.py").read_text(encoding="utf-8")
        setup = script.split("wait_automation_ready(client)", 1)[1]
        self.assertLess(setup.index("clear_restored_playback(client)"),
                        setup.index('"dev_play_server_path"'))
        self.assertLess(setup.index("clear_restored_playback(client)"),
                        setup.index('"dev_play_media_file_id"'))

    def test_caption_codec_oracle_uses_discovered_ids_not_row_zero(self):
        state = {"subtitleTracks": "0:CEA608:en::1:true|1:CEA708:en::1:true|2:DVB:en::-1:true|21504:TELETEXT:eng::-1:true"}
        self.assertEqual(2, resolve_requested_codec_track(state, "dvb"))
        state["selectedSubtitleTrackRaw"] = None
        self.assertEqual(2, resolve_requested_codec_track(state, "dvb"))
        self.assertEqual(21504, resolve_requested_codec_track(state, "teletext"))
        self.assertIsNone(resolve_requested_codec_track(state, "SRT"))

    def test_caption_codec_oracle_keeps_selected_language_service(self):
        state = {"subtitleTracks": "2:DVB:fr::-1:true|7:DVB:en::-1:true",
                 "selectedSubtitleTrackRaw": 7}
        self.assertEqual(7, resolve_requested_codec_track(state, "DVB"))

    def test_caption_clock_is_separate_from_timeline_poll_and_session_scoped(self):
        for backend in ("media3/Media3MediaPlayerImpl.java", "exoplayer2/Exo2MediaPlayerImpl.java"):
            source = (ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video" / backend).read_text(encoding="utf-8")
            ticker = source.split("private void startCaptionClockUpdates", 1)[1].split("private void cancelProgressUpdates", 1)[0]
            self.assertIn("captionClockRunnable != this", ticker)
            self.assertIn("!isCurrentPlaybackSession(session)", ticker)
            self.assertIn("player != expectedPlayer", ticker)
            self.assertIn("CaptionClockCadence.delayMs", ticker)
            self.assertIn("expectedPlayer.isPlaying()", ticker)
            self.assertIn("progressHandler.removeCallbacks(captionClockRunnable)", source)
            # This timer must not change server timeline, recovery policy, or
            # audio clock/output settings just to pace caption delivery.
            ticker = ticker.split("captionClockRunnable = new Runnable()", 1)[1].split("progressHandler.post(captionClockRunnable)", 1)[0]
            for unrelated in ("setPlaybackPosition", "guardGrowingPullPosition", "setPlaybackParameters", "setAudioOffset"):
                self.assertNotIn(unrelated, ticker)

    def test_caption_gate_can_isolate_ui_renderer_without_changing_default(self):
        script = (ROOT / "scripts/mcp_caption_test.py").read_text(encoding="utf-8")
        self.assertIn('"--renderer", choices=("keep", "opengl", "gdx"), default="keep"', script)
        self.assertIn('"renderer": "" if args.renderer == "keep" else args.renderer', script)
        self.assertIn('"save": False', script)
        self.assertIn('"--pause-resume", action="store_true"', script)
        self.assertIn('"health_playerPositionMs" in paused and "health_playerPositionMs" in held', script)
        self.assertIn('> int(held["health_playerPositionMs"])', script)
        self.assertIn('"--pre-seek-hold-s", type=float, default=0.0', script)
        self.assertIn('window = min(30.0, args.pre_seek_hold_s)', script)

    def test_caption_clock_diagnostic_is_bounded_and_does_not_expose_private_state(self):
        import json
        result = json.loads(caption_clock_diagnostic({
            "health_playerPositionMs": 1234, "fixedCaptionLastPollClockMs": 1200,
            "password": "private", "sessionToken": "private"}))
        self.assertEqual(1234, result["health_playerPositionMs"])
        self.assertEqual(1200, result["fixedCaptionLastPollClockMs"])
        self.assertNotIn("password", result)
        self.assertNotIn("sessionToken", result)

    def test_caption_gate_resolves_teletext_row_to_raw_pid_track_id(self):
        state = {
            "subtitleTracks": (
                "0:DVB:en::-1:true|1:CEA608:en::1:true|"
                "2:CEA708:en::1:true|21504:TELETEXT:eng:Teletext 888:-1:true"
            )
        }
        self.assertEqual(21504, resolve_requested_track_raw(state, 3))
        self.assertEqual("TELETEXT", subtitle_track_codec(state, 21504))
        self.assertEqual(0, resolve_requested_track_raw(state, 0))

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
            self.assertIn("track.resetPending()", extractor)

        for relative_path in (
            "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/media3/Media3MediaPlayerImpl.java",
            "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/exoplayer2/Exo2MediaPlayerImpl.java",
        ):
            player = (ROOT / relative_path).read_text(encoding="utf-8")
            self.assertIn("LegacyCaptionExtractorsFactory", player)
            self.assertIn("isSubtitleCallbackEnabled", player)
            self.assertIn("isForwardingCurrentStream", player)
            self.assertIn("scheduleLegacyCaptionDrain(currentPositionMs * 1000L)", player)
            self.assertIn("getBackgroundService().execute", player)
            self.assertIn("|| isFixedCaptionForwardingForDebug()", player)
            self.assertIn("mimDirectController().adjustPosition(", player)

        fixed = (ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/FixedCaptionSideChannelClient.java").read_text(
            encoding="utf-8"
        )
        clock = fixed.split("public void updatePlaybackClock", 1)[1].split(
            "public void setPaused", 1
        )[0]
        self.assertNotIn("drainTo(", clock)
        self.assertIn("NETWORK_POLL_INTERVAL_MS = 250L", fixed)
        self.assertIn("CaptionClockCadence.ACTIVE_DELAY_MS", fixed)
        self.assertIn("if (now < nextPollMs)", fixed)
        self.assertIn("generation != expectedGeneration || bridge != activeBridge", fixed)
        self.assertIn("previousPresentation.shutdown()", fixed)
        network_tick = fixed.split("private void tick(", 1)[1].split("private void releaseActiveSession", 1)[0]
        self.assertNotIn("drainTo(", network_tick)
        self.assertIn('"vibe-fixed-caption-clock"', fixed)

        debug_state = (
            ROOT
            / "source/dev/android-tv/src/debug/java/opensagetv/vibe/miniclient/android/tv/debug/DebugStateProvider.java"
        ).read_text(encoding="utf-8")
        self.assertIn("legacyCaptionCallbacksNegotiated", debug_state)
        self.assertIn("legacyCaptionWireEventCount", debug_state)
        self.assertIn("legacyCaptionWireBytes", debug_state)
        self.assertIn("legacyCaptionCallbackCount", debug_state)
        self.assertIn("legacyCaptionCallbackBytes", debug_state)

    def test_subtitle_callbacks_are_serialized_off_android_ui_thread(self):
        connection = (
            ROOT
            / "source/dev/core/src/main/java/opensagetv/vibe/miniclient/MiniClientConnection.java"
        ).read_text(encoding="utf-8")
        method = connection.split("public void postSubtitleInfo", 1)[1]
        method = method.split("public boolean isSubtitleCallbackEnabled", 1)[0]
        self.assertIn("eventRouterThread.enqueue(new Runnable()", method)
        self.assertIn("final byte[] eventData = data == null ? null : data.clone()", method)
        self.assertIn("Error sending subtitle callback event", method)

    def test_teletext_overlay_cannot_survive_stop_or_end_of_stream(self):
        base = (
            ROOT
            / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/BaseMediaPlayerImpl.java"
        ).read_text(encoding="utf-8")
        self.assertIn("protected final void endTeletextPresentation()", base)
        self.assertIn("overlayGeneration != teletextOverlayGeneration", base)
        stop = base.split("public void stop()", 1)[1].split("protected void clearSurface", 1)[0]
        self.assertIn("endTeletextPresentation();", stop)

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
        self.assertIn("currentPlayer.applyClosedCaptionSlot(channel, type, language)", media_cmd)
        self.assertNotIn("currentPlayer.setPreferredSubtitleTrack()", media_cmd)

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
            self.assertIn("applyPublishedSubtitleSelection()", source)
            self.assertIn("requestedTrack != DISABLE_TRACK", source)
            self.assertIn("requestedTrack != PREFERRED_TRACK", source)
            self.assertIn("Applying explicit active-session subtitle track", source)
            release = source.split("protected void releasePlayer()", 1)[1].split(
                "public Dimension getVideoDimensions()", 1
            )[0]
            self.assertIn("selectedSubtitleTrack = DISABLE_TRACK;", release)
            self.assertIn("RemoveSubTitleView();", release)

    def test_long_press_navigation_exposes_legacy_caption_compatibility(self):
        navigation_java = (
            ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/NavigationDialog.java"
        ).read_text(encoding="utf-8")
        self.assertIn("nav_closed_captions", navigation_java)
        self.assertIn("onClosedCaptions()", navigation_java)
        self.assertIn("ActivePlayerAdjustmentsDialog.showCaptions(activity)", navigation_java)
        strings = (
            ROOT / "source/dev/android-shared/src/main/res/values/strings.xml"
        ).read_text(encoding="utf-8")
        self.assertIn("SageTV broadcast-caption callback", strings)
        self.assertIn("This player did not negotiate SageTV broadcast-caption callbacks", strings)
        for relative_path in (
            "source/dev/android-shared/src/main/res/layout/navigation.xml",
            "source/dev/android-tv/src/main/res/layout/navigation.xml",
            "source/dev/android-tv/src/main/res/layout-notouch/navigation.xml",
        ):
            self.assertIn("nav_closed_captions", (ROOT / relative_path).read_text(encoding="utf-8"))

    def test_cc_icon_exposes_stream_aware_virtual_caption_slots(self):
        dialog = (
            ROOT
            / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/ActivePlayerAdjustmentsDialog.java"
        ).read_text(encoding="utf-8")
        self.assertIn('"Broadcast CC: " + captionModeLabel', dialog)
        self.assertIn('"CC1 Type: "', dialog)
        self.assertIn('"CC1 Language: "', dialog)
        self.assertIn('"CC2 Type: "', dialog)
        self.assertIn('"CC2 Language: "', dialog)
        self.assertIn('rowList.add("Available broadcast CC")', dialog)
        self.assertIn('" (DVB selected)" : " (select DVB)"', dialog)
        self.assertIn('rowList.add("Subtitle stream: " + subtitleTrackValue', dialog)
        self.assertIn('rowList.add("Subtitle appearance: " + subtitleAppearanceValue', dialog)
        self.assertIn("addAvailableCaptionRows", dialog)
        self.assertIn('row.append(" (CC1)")', dialog)
        self.assertIn('row.append(" (CC2)")', dialog)
        self.assertIn("hasObservedCeaCaptionData", dialog)
        self.assertIn("final int readOnlyRows = rowList.size()", dialog)
        self.assertIn("showCaptionPanel", dialog)
        self.assertIn("Window.FEATURE_NO_TITLE", dialog)
        self.assertIn("CaptionSlotPolicy.findTrack", dialog)
        self.assertIn("DVB bitmap - ", dialog)
        self.assertIn("reliableLanguage(track)", dialog)
        chooser = dialog.split("private void chooseCaptionType", 1)[1]
        chooser = chooser.split("private static void addCaptionType", 1)[0]
        self.assertNotIn("CaptionSlotPolicy.TYPE_DVB", chooser)
        detected = dialog.split("private String captionSlotDetected", 1)[1]
        detected = detected.split("private static String compactCaptionTrackLabel", 1)[0]
        self.assertIn("true, false", detected)

    def test_teletext_mappings_use_pid_and_accept_legacy_values(self):
        base = (
            ROOT
            / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/BaseMediaPlayerImpl.java"
        ).read_text(encoding="utf-8")
        self.assertIn('service.pid + ":" + service.language.toLowerCase()', base)
        self.assertIn("legacyTeletextServiceSpec(service)", base)
        self.assertIn("resolvedTeletextTrackForSlot(1)", base)
        self.assertIn("resolvedTeletextTrackForSlot(2)", base)
        self.assertIn("track.getSubtitleCodec() == SubtitleCodec.TELETEXT", base)
        cue_callback = base.split("onTeletextCue", 1)[1].split("};", 1)[0]
        self.assertIn("applyTeletextCcMappings();", cue_callback)
        self.assertLess(cue_callback.index("applyTeletextCcMappings();"),
                        cue_callback.index("teletextLegacyBridge.enqueue(cue)"))

    def test_explicit_local_caption_mode_has_one_renderer(self):
        base = (
            ROOT
            / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/BaseMediaPlayerImpl.java"
        ).read_text(encoding="utf-8")
        self.assertIn("isExplicitLocalCaptionAuthority(connection)", base)
        self.assertIn("teletextLegacyBridgeEnabled = false", base)
        self.assertIn("teletextLegacyBridge.clearPending()", base)
        self.assertIn("postTeletextFlush()", base)
        self.assertIn("teletextLegacyBridge.setTrackMappings(DISABLE_TRACK, DISABLE_TRACK)", base)
        self.assertIn('return "cc1".equals(mode) || "cc2".equals(mode) || "dvb".equals(mode)', base)
        apply_slot = base.split("public boolean applyClosedCaptionSlot", 1)[1]
        apply_slot = apply_slot.split("private SubtitleTrack resolveCaptionSlotTrack", 1)[0]
        self.assertIn("!isExplicitLocalCaptionAuthority(connection)", apply_slot)
        self.assertIn("fixedCaptionAttached", apply_slot)
        self.assertIn("fixedCaptionBridge.isForwardingCurrentStream()", apply_slot)
        self.assertIn("side channel is the source", apply_slot)
        self.assertIn("setSubtitleTrack(track.getIndex())", apply_slot)
        resolver = base.split("private SubtitleTrack resolveCaptionSlotTrack", 1)[1]
        resolver = resolver.split("public boolean applyDvbCaptionTrack", 1)[0]
        self.assertIn("true, false", resolver)
        self.assertIn("false, false", resolver)

    def test_stock_extender_subpicture_command_selects_local_broadcast_pid(self):
        base = (
            ROOT
            / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/BaseMediaPlayerImpl.java"
        ).read_text(encoding="utf-8")
        command = (
            ROOT
            / "source/dev/core/src/main/java/opensagetv/vibe/miniclient/media/LegacyExtenderSubpictureCommand.java"
        ).read_text(encoding="utf-8")
        self.assertIn('lastUri.startsWith("push:dvd")', base)
        self.assertIn("pendingServerSubpictureCommand = streamPosition", base)
        self.assertIn("applyPendingServerSubpictureStream()", base)
        self.assertIn('"cc1".equals(captionMode) || "cc2".equals(captionMode)', base)
        self.assertIn('|| "dvb".equals(captionMode)', base)
        self.assertIn("Subtitles-off", base)
        self.assertIn("track.getSourceStreamId() == sourcePid", command)
        self.assertIn("DISABLE_FLAG = 0x2000", command)
        self.assertIn("PID_MASK = 0x1fff", command)
        for relative_path in (
            "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/media3/Media3MediaPlayerImpl.java",
            "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/exoplayer2/Exo2MediaPlayerImpl.java",
        ):
            player = (ROOT / relative_path).read_text(encoding="utf-8")
            self.assertIn("SubtitleTrack.parseSourceStreamId", player)
            self.assertIn("applyPendingServerSubpictureStream()", player)

    def test_broadcast_caption_clock_does_not_depend_on_stv_osd_polling(self):
        base = (
            ROOT
            / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/BaseMediaPlayerImpl.java"
        ).read_text(encoding="utf-8")
        self.assertIn("TELETEXT_CLOCK_INTERVAL_MS = 100L", base)
        self.assertIn("scheduleTeletextClock();", base)
        self.assertIn("getPlayerMediaTimeMillis(teletextClockServerBaseMs)", base)
        self.assertIn("teletextLegacyBridge.drainTo(mediaTimeMs)", base)
        self.assertIn("updateFixedCaptionClock(mediaTimeMs)", base)
        self.assertIn("(!fixedCaptionAttached && teletextServices.length == 0)", base)
        self.assertIn("getFixedCaptionEvidenceRefreshCountForDebug", base)
        self.assertIn("fixedCaptionEvidenceRefreshState", base)
        self.assertIn('"stv_cc" + channel', base)
        self.assertIn("getSageTvClosedCaptionState()", base)
        self.assertIn("Stock STVs stop polling media time once their OSD is hidden", base)

        caption_test = (ROOT / "scripts/mcp_caption_test.py").read_text(encoding="utf-8")
        self.assertIn("Teletext clock stopped while the SageTV OSD was idle", caption_test)
        self.assertIn("time.sleep(continuity_window_s)", caption_test)
        self.assertIn('continuity_state = call_dict(client, "dev_player_state"', caption_test)

    def test_only_stv_caption_mode_follows_received_server_state(self):
        media_cmd = (ROOT / "source/dev/core/src/main/java/opensagetv/vibe/miniclient/MediaCmd.java").read_text(
            encoding="utf-8"
        )
        arrays = (ROOT / "source/dev/android-shared/src/main/res/values/arrays.xml").read_text(
            encoding="utf-8"
        )
        self.assertIn("setLegacyServerCaptionMode", media_cmd)
        self.assertIn("sageTvClosedCaptionStateReceived", media_cmd)
        self.assertIn("? sageTvClosedCaptionState != 0", media_cmd)
        self.assertIn('if ("off".equals(mode))', media_cmd)
        self.assertIn('if ("cc1".equals(mode) || "cc2".equals(mode))', media_cmd)
        self.assertIn("must not replace an explicit Android choice", media_cmd)
        for mode in ("stv", "off", "cc1", "cc2", "dvb"):
            self.assertIn(f"<item>{mode}</item>", arrays)

    def test_dvb_mode_is_explicit_local_bitmap_mode(self):
        media_cmd = (ROOT / "source/dev/core/src/main/java/opensagetv/vibe/miniclient/MediaCmd.java").read_text(
            encoding="utf-8"
        )
        base = (
            ROOT
            / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/BaseMediaPlayerImpl.java"
        ).read_text(encoding="utf-8")
        dialog = (
            ROOT
            / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/ActivePlayerAdjustmentsDialog.java"
        ).read_text(encoding="utf-8")
        self.assertIn('if ("dvb".equals(mode))', media_cmd)
        self.assertIn("applyDvbCaptionTrack()", base)
        self.assertIn('final String[] labels = { "OFF", "CC1", "CC2", "STV", "DVB" }', dialog)
        self.assertIn("Only Teletext can be emitted through the legacy CEA callback", base)
        self.assertIn('|| "dvb".equals(mode)', media_cmd)
        self.assertIn('else if ("stv".equals(value) && media.hasSageTvClosedCaptionState())', dialog)
        self.assertIn('if ("dvb".equals(mode)) return "DVB bitmap broadcast CC";', dialog)
        media3 = (ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/media3/Media3MediaPlayerImpl.java").read_text(encoding="utf-8")
        exo2 = (ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/exoplayer2/Exo2MediaPlayerImpl.java").read_text(encoding="utf-8")
        self.assertIn("applyConfiguredClosedCaptionSlot();", media3)
        self.assertIn("applyConfiguredClosedCaptionSlot();", exo2)
        self.assertIn("boolean configuredCaptionApplied = applyPublishedSubtitleSelection();", media3)
        self.assertIn("boolean configuredCaptionApplied = applyPublishedSubtitleSelection();", exo2)

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
        self.assertIn('"dev_set_caption_mode"', script)
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
        self.assertIn('"--stv-state-hold-s"', script)
        self.assertIn('"--unified-graphics"', script)
        self.assertIn('"--fixed-caption-side-channel"', script)
        self.assertIn('"fixed_caption_side_channel_enabled"', script)
        self.assertIn("wire delivery alone does not prove rendered text", script)
        self.assertIn("rendered text requires separate screenshot review", script)
        self.assertIn('"--expect-no-legacy-callback"', script)
        self.assertIn('choices=("exoplayer", "media3", "ijkplayer", "gsyplayer")', script)
        self.assertIn('"--gsy-engine"', script)
        self.assertIn("unsupported backend stayed playable", script)
        self.assertIn('"--cycle-stv-caption-states"', script)
        self.assertIn('dev_set_stv_caption_state', script)
        self.assertIn('Off/CC1/CC2/Off/CC1', script)
        self.assertIn("legacyCaptionCallbacksNegotiated", script)
        self.assertIn("legacyCaptionCallbackActive", script)
        self.assertIn("legacyCaptionWireEventCount", script)
        self.assertIn("legacyCaptionWireBytes", script)
        self.assertIn("no duplicate ", script)
        self.assertIn("Android subtitle overlay", script)
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
