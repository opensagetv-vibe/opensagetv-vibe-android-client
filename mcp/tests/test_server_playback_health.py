import unittest
from unittest import mock

from sagetv_dev_mcp import server


class PlaybackHealthTest(unittest.TestCase):
    def test_compact_state_retains_safe_direct_session_attribution_not_raw_uri(self):
        fields = {"health_directSourceSession": "unavailable", "health_directMediaItemSession": "current",
                  "health_directErrorSession": "retired", "health_directErrorAsset": "playlist",
                  "health_directErrorCode": "http_404_unknown_media", "health_directErrorSegmentIndex": -1}
        self.assertEqual(fields, server._compact_state({**fields,
                         "directSourceUri": "private endpoint", "sessionToken": "private"}))

    def test_compact_state_preserves_native_playing_without_promoting_probe(self):
        fields = {"health_probeSupported": False, "health_isPlaying": False,
                  "health_basicIsPlaying": True}
        self.assertEqual(fields, server._compact_state({**fields, "privateToken": "private"}))

    def test_ijk_audio_clock_cannot_certify_blank_video(self):
        before = {"playerActive": True, "state": 2, "mediaTimeMs": 1000,
                  "playerClass": "example.IJKMediaPlayerImpl", "health_videoWidth": 1920,
                  "health_firstVideoFrameRendered": False}
        after = {**before, "mediaTimeMs": 2000}
        passed, details = server._playback_health_from_pair(before, after)
        self.assertFalse(passed)
        self.assertTrue(details["timeline_advancing_fallback"])
        self.assertFalse(details["video_first_frame_verified"])
        self.assertIn("health_firstVideoFrameRendered", server._compact_state(after))

    def test_ijk_first_frame_plus_clock_retains_weaker_fallback(self):
        before = {"playerActive": True, "state": 2, "mediaTimeMs": 1000,
                  "playerClass": "example.IJKMediaPlayerImpl", "health_videoWidth": 1920,
                  "health_firstVideoFrameRendered": True}
        passed, details = server._playback_health_from_pair(before, {**before, "mediaTimeMs": 2000})
        self.assertTrue(passed)
        self.assertEqual("timeline_fallback", details["verdict_basis"])
        self.assertTrue(details["video_first_frame_verified"])

    def test_ijk_audio_only_and_older_debug_fallback_unchanged(self):
        before = {"playerActive": True, "state": 2, "mediaTimeMs": 1000,
                  "playerClass": "example.IJKMediaPlayerImpl", "health_videoWidth": 1920,
                  "health_firstVideoFrameRendered": False}
        self.assertTrue(server._playback_health_from_pair(before, {**before, "mediaTimeMs": 2000}, expect_video=False)[0])
        before.pop("health_firstVideoFrameRendered")
        self.assertTrue(server._playback_health_from_pair(before, {**before, "mediaTimeMs": 2000})[0])

    def test_compact_state_retains_existing_dvd_push_drain_probes(self):
        fields = {"dvdEpochPushedBytes": 24000000, "dvdDecoderBufferedAheadMs": 2400,
                  "lastPushReply": 0, "dvdLastReadBytes": 23000000}
        self.assertEqual(fields, server._compact_state({**fields, "notNeeded": 42}))

    def test_compact_state_retains_existing_dvd_normal_source_clock(self):
        fields = {"dvdNormalSourceClock": "count=1,capacity=64,presentationUs=2000000,mappedSourceMs=3200000"}
        self.assertEqual(fields, server._compact_state({**fields, "notNeeded": 42}))

    @staticmethod
    def _preview_state():
        return {
            "playerActive": True,
            "menuName": "Main Menu",
            # The Android SurfaceView covers the display even when SageTV asks
            # the player to render only in its upper-right preview rectangle.
            "health_surfaceWidth": 1920,
            "health_surfaceHeight": 1080,
            "videoDestWidth": 572,
            "videoDestHeight": 322,
            "videoUiWidth": 1920,
            "videoUiHeight": 1080,
        }

    @staticmethod
    def _fullscreen_state():
        return {
            "playerActive": True,
            "menuName": "MediaPlayer OSD",
            "health_surfaceWidth": 1920,
            "health_surfaceHeight": 1080,
            "videoDestWidth": 1920,
            "videoDestHeight": 1080,
            "videoUiWidth": 1920,
            "videoUiHeight": 1080,
        }

    def test_full_surface_does_not_make_main_menu_preview_fullscreen(self):
        self.assertFalse(server._is_fullscreen_playback(self._preview_state()))

    def test_full_destination_without_menu_hint_is_fullscreen(self):
        state = self._fullscreen_state()
        state["menuName"] = ""
        self.assertTrue(server._is_fullscreen_playback(state))

    def test_compact_state_retains_source_open_and_first_read_timestamps(self):
        fields = {
            "sourceOpenMonotonicMs": 100,
            "sourceFirstReadMonotonicMs": 125,
            "sourceFirstReadPosition": 4096,
            "health_dataSourceLastOpenMonotonicMs": 100,
            "health_dataSourceFirstReadAfterOpenMonotonicMs": 125,
            "health_dataSourceFirstReadAfterOpenPosition": 4096,
            "videoDestWidth": 1920,
            "videoDestHeight": 1080,
            "videoUiWidth": 1920,
            "videoUiHeight": 1080,
        }
        compact = server._compact_state({**fields, "unrelated": "discarded"})
        self.assertEqual(fields, compact)

    def test_compact_state_retains_device_capture_clock_for_cadence(self):
        self.assertEqual({"health_capturedMonotonicMs": 28578},
                         server._compact_state({"health_capturedMonotonicMs": 28578,
                                                "unrelated": "discarded"}))

    def test_compact_state_retains_background_session_ownership(self):
        fields = {
            "appVisibilityState": "BACKGROUND_APP_PAUSED",
            "backgroundTransitionGeneration": 1,
            "pausedByBackground": True,
            "wasPlayingBeforeBackground": True,
            "resumeBackgroundPlayback": False,
            "backgroundPauseRequestCount": 1,
            "backgroundPlayRequestCount": 0,
            "backgroundPreservedCount": 0,
            "backgroundLostCount": 0,
            "appStartedActivityCount": 0,
            "appBackground": True,
        }
        compact = server._compact_state({**fields, "unrelated": "discarded"})
        self.assertEqual(fields, compact)

    def test_compact_state_retains_bounded_image_allocation_recovery(self):
        fields = {
            "imageAllocationRecoveryAttempts": 1,
            "imageAllocationRecoveryEvictions": 1,
            "imageAllocationRecoverySuccesses": 1,
            "imageAllocationRecoveryFailures": 0,
        }
        compact = server._compact_state({**fields, "unrelated": "discarded"})
        self.assertEqual(fields, compact)

    def test_compact_state_retains_fixed_caption_packet_diagnostics(self):
        fields = {
            "fixedCaptionSideChannelState": "active",
            "fixedCaptionReceivedPackets": 25,
            "fixedCaptionLastPacketPtsMs": 12450,
            "fixedCaptionLastPollClockMs": 12300,
        }
        self.assertEqual(fields, server._compact_state({**fields, "unrelated": "discarded"}))

    def test_stream_expectations_detect_audio_and_video(self):
        self.assertEqual(
            (True, True),
            server._stream_expectations({
                "health_videoMime": "video/mpeg2",
                "health_audioMime": "audio/ac3",
            }),
        )

    def test_output_counters_prove_real_playback(self):
        before = {
            "health_probeSupported": True,
            "health_videoDecoder": "c2.android.mpeg2.decoder",
            "health_audioDecoder": "c2.android.ac3.decoder",
            "health_videoRendered": 10,
            "health_audioRendered": 20,
        }
        after = {
            **before,
            "health_surfaceValid": True,
            "health_videoRendered": 11,
            "health_audioRendered": 21,
        }

        healthy, details = server._playback_health_from_pair(before, after)

        self.assertTrue(healthy)
        self.assertTrue(details["video_expected"])
        self.assertTrue(details["audio_expected"])
        self.assertEqual("av_output_counters", details["verdict_basis"])

    def test_detected_stream_cannot_pass_without_advancing_output(self):
        state = {
            "health_probeSupported": True,
            "health_videoWidth": 1920,
            "health_videoHeight": 1080,
            "health_audioTrackPresent": True,
            "health_surfaceValid": True,
            "health_videoRendered": 10,
            "health_audioRendered": 20,
        }

        healthy, details = server._playback_health_from_pair(state, dict(state))

        self.assertFalse(healthy)
        self.assertFalse(details["video_advancing"])
        self.assertFalse(details["audio_advancing"])

    def test_completed_short_fixture_with_real_outputs_is_healthy(self):
        healthy, details = server._completed_short_media_health({
            "health_playbackState": 4,
            "health_durationMs": 7941,
            "health_playerPositionMs": 8029,
            "health_videoRendered": 476,
            "health_audioRendered": 250,
            "health_surfaceValid": True,
            "health_errorState": False,
            "health_playerError": "",
        })

        self.assertTrue(healthy)
        self.assertEqual("completed_short_media_outputs", details["verdict_basis"])

    def test_jump_to_end_without_sustained_output_is_not_healthy(self):
        healthy, _ = server._completed_short_media_health({
            "health_playbackState": 4,
            "health_durationMs": 7941,
            "health_playerPositionMs": 7941,
            "health_videoRendered": 1,
            "health_audioRendered": 0,
            "health_surfaceValid": True,
        })

        self.assertFalse(healthy)

    def test_late_fixed_reconnect_ignores_only_its_transient_player_error(self):
        reconnect_error = {
            "mimDirectSessionState": "late_failure_stock_fixed_reconnect_requested",
            "health_errorState": True,
            "health_playerError": "injected pull startup failure",
        }
        healthy_before = {
            "mimDirectSessionState": "late_failure_stock_fixed_reconnect",
            "health_probeSupported": True,
            "playerActive": True,
            "health_videoDecoder": "c2.android.avc.decoder",
            "health_audioDecoder": "c2.android.aac.decoder",
            "health_surfaceValid": True,
            "health_videoRendered": 10,
            "health_audioRendered": 20,
        }
        healthy_after = {
            **healthy_before,
            "health_videoRendered": 11,
            "health_audioRendered": 21,
        }
        states = [reconnect_error, healthy_before, healthy_after]

        with mock.patch.object(
            server.adb,
            "player_state_snapshot",
            side_effect=lambda: states.pop(0) if states else healthy_after,
        ), mock.patch.object(server.time, "sleep", return_value=None), \
                mock.patch.object(server, "_crash_probe_snapshot", return_value={}):
            result = server._wait_for_playback(
                timeout_s=1.0,
                verify_ms=250,
                allow_mim_late_fixed_reconnect=True,
            )

        self.assertTrue(result["passed"])
        self.assertTrue(result["mimLateFixedReconnectObserved"])

    def test_late_fixed_reconnect_does_not_hide_unrelated_player_error(self):
        unrelated_error = {
            "mimDirectSessionState": "active_transcode",
            "health_errorState": True,
            "health_playerError": "real decoder failure",
        }
        with mock.patch.object(
            server.adb, "player_state_snapshot", return_value=unrelated_error
        ), mock.patch.object(server, "_crash_probe_snapshot", return_value={}):
            result = server._wait_for_playback(
                timeout_s=1.0,
                verify_ms=250,
                allow_mim_late_fixed_reconnect=True,
            )

        self.assertFalse(result["passed"])
        self.assertEqual("player_error", result["failureReason"]["code"])
        self.assertFalse(result["mimLateFixedReconnectObserved"])

    def test_fullscreen_promotion_rejects_transient_osd_toggle(self):
        preview = self._preview_state()
        snapshots = [preview, self._fullscreen_state(), preview]

        def snapshot():
            return snapshots.pop(0) if snapshots else preview

        with mock.patch.object(server.adb, "player_state_snapshot", side_effect=snapshot), \
                mock.patch.object(server.adb, "sage_command", return_value={"ok": True}):
            result = server._promote_preview_to_fullscreen(timeout_s=0.5)

        self.assertFalse(result["passed"])
        self.assertEqual("fullscreen_surface_not_observed", result["reason"])

    def test_fullscreen_promotion_requires_stable_osd(self):
        fullscreen = self._fullscreen_state()
        with mock.patch.object(server.adb, "player_state_snapshot", return_value=fullscreen), \
                mock.patch.object(server.adb, "sage_command") as command:
            result = server._promote_preview_to_fullscreen(timeout_s=1.5)

        self.assertTrue(result["passed"])
        self.assertTrue(result["alreadyFullscreen"])
        self.assertGreaterEqual(result["stableMs"], 750)
        command.assert_not_called()

    def test_fullscreen_promotion_uses_miniclient_tv_command(self):
        preview = self._preview_state()
        remote = mock.Mock()
        remote.remote_command.return_value = {"accepted": True}
        with mock.patch.object(server.adb, "player_state_snapshot", return_value=preview), \
                mock.patch.object(server.adb, "sage_command", return_value={"ok": True}) as command:
            result = server._promote_preview_to_fullscreen(
                timeout_s=2.2,
                server_api=remote,
                ui_context="444556303031",
            )

        self.assertFalse(result["passed"])
        command.assert_called_once_with("tv")
        remote.remote_command.assert_not_called()

    def test_early_fullscreen_observes_preview_without_sending_toggle(self):
        preview = self._preview_state()
        states = [{"playerActive": False}]
        remote = mock.Mock()
        remote.remote_command.return_value = {"accepted": True}
        with mock.patch.object(
            server.adb,
            "player_state_snapshot",
            side_effect=lambda: states.pop(0) if states else preview,
        ), mock.patch.object(
            server.adb,
            "sage_command",
            return_value={"ok": True},
        ) as command:
            result = server._request_fullscreen_when_player_active(
                timeout_s=0.5,
                server_api=remote,
                ui_context="444556303031",
            )

        self.assertFalse(result["requested"])
        self.assertEqual("client_owned_promotion_not_yet_observed", result["reason"])
        remote.remote_command.assert_not_called()
        command.assert_not_called()

    def test_client_owned_fullscreen_verification_never_sends_toggle(self):
        preview = self._preview_state()
        with mock.patch.object(server.adb, "player_state_snapshot", return_value=preview), \
                mock.patch.object(server.adb, "sage_command") as command:
            result = server._promote_preview_to_fullscreen(
                timeout_s=1.0,
                allow_command=False,
            )

        self.assertFalse(result["passed"])
        self.assertEqual("client_owned_fullscreen_surface_not_observed", result["reason"])
        command.assert_not_called()

    def test_fullscreen_recovery_does_not_double_toggle_client_command_in_flight(self):
        preview = self._preview_state()
        preview["fullscreenPromotionSent"] = True
        preview["fullscreenPromotionCommandCount"] = 1
        with mock.patch.object(server.adb, "player_state_snapshot", return_value=preview), \
                mock.patch.object(server.adb, "sage_command") as command:
            result = server._promote_preview_to_fullscreen(
                timeout_s=2.2,
                allow_command=True,
            )

        self.assertFalse(result["passed"])
        self.assertTrue(result["clientCommandInFlight"])
        command.assert_not_called()


if __name__ == "__main__":
    unittest.main()
