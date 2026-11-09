"""Refresh matching may restore only its own Window preference, never another Activity."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]


class DisplayModeRestoreTest(unittest.TestCase):
    def test_window_preference_not_physical_mode_is_the_baseline(self):
        source = (ROOT/'source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/DisplayRefreshController.java').read_text()
        self.assertIn('MODE_RESTORE.applying(activity.getWindow(), attrs.preferredDisplayModeId', source)
        self.assertIn('MODE_RESTORE.release(activity.getWindow(), attrs.preferredDisplayModeId)', source)
        self.assertNotIn('restoreModeId = state.currentModeId', source)
        self.assertNotIn('static volatile int restoreModeId', source)
        self.assertIn('if (!decision.changeRequired)', source)

    def test_no_metadata_query_is_needed_for_off(self):
        source = (ROOT/'source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/DisplayRefreshController.java').read_text()
        self.assertIn('OFF.equals(policy) ? null : player', source)
        self.assertIn('Looper.myLooper() != Looper.getMainLooper()', source)

    def test_debug_refresh_is_one_shot_current_activity_not_saved_or_server_control(self):
        source = (ROOT/'source/dev/android-tv/src/debug/java/opensagetv/vibe/miniclient/android/tv/debug/DebugSessionCommands.java').read_text()
        method = source.split('static String displayRefresh(', 1)[1].split('static String refreshVideoOutput', 1)[0]
        self.assertIn('getResumedActivityForDebug()', method)
        self.assertIn('Looper.myLooper() != Looper.getMainLooper()', method)
        self.assertIn('"inspect".equals(policy)', method)
        self.assertIn('savedPreferencesChanged=false', method)
        self.assertNotIn('putString', method)
        self.assertNotIn('postDelayed', method)
        self.assertNotIn('seek(', method)

    def test_physical_gate_does_not_auto_play_an_unexplained_failure(self):
        source = (ROOT/'scripts/mcp_display_refresh_test.py').read_text()
        self.assertIn("e.get('event') == 'media_session_pause'", source)
        self.assertIn('mode_time+1000', source)
        self.assertIn("state.get('health_surfaceValid') is True", source)
        self.assertIn("int(state.get('inputEventSequence', -1))", source)
        self.assertIn("api.media_control(ctx, 'play')", source)
        self.assertNotIn('api.seek(', source)
        self.assertIn('This is an explicit TEST Play', source)

    def test_callback_diagnostics_do_not_change_pause_delivery(self):
        source = (ROOT/'source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/MediaSessionCallbackHandler.java').read_text()
        pause = source.split('public void onPause()', 1)[1].split('private boolean', 1)[0]
        self.assertIn('PlaybackDebugTrap.record("media_session_pause", player)', pause)
        self.assertIn('EventRouter.postCommand(client, SageCommand.PAUSE)', pause)
        self.assertNotIn('postDelayed', pause)


if __name__ == '__main__':
    unittest.main()
