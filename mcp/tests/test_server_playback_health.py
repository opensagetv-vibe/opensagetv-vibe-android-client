import unittest
from unittest import mock

from sagetv_dev_mcp import server


class PlaybackHealthTest(unittest.TestCase):
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


if __name__ == "__main__":
    unittest.main()
