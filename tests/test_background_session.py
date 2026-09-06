from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
CORE = ROOT / "source/dev/core/src/main/java/opensagetv/vibe/miniclient/BackgroundSessionState.java"
OWNER = ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/BackgroundSessionOwner.java"
LIFECYCLE = ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/UIActivityLifeCycleHandler.java"
SESSION_CONFIG = ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/UiSessionConfiguration.java"
PREFS = ROOT / "source/dev/android-shared/src/main/res/xml/prefs.xml"
BACKGROUND_PREFS = ROOT / "source/dev/android-shared/src/main/res/xml/home_background_prefs.xml"
SCRIPT = ROOT / "scripts/mcp_lifecycle_test.py"
MEDIA_SESSION = ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/MediaSessionCallbackHandler.java"


class BackgroundSessionTests(unittest.TestCase):
    def test_legacy_exo_tracks_surface_view_lifecycle(self):
        source = (ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/exoplayer2/Exo2MediaPlayerImpl.java").read_text(encoding="utf-8")
        self.assertIn("setVideoSurfaceView((SurfaceView) context.getVideoView())", source)
        self.assertNotIn("getHolder().getSurface()", source)

    def test_state_machine_uses_exact_connection_and_player_identity(self):
        text = CORE.read_text(encoding="utf-8")
        self.assertIn("connectionIdentity != connection", text)
        self.assertIn("playerIdentity != player", text)
        self.assertIn("BACKGROUND_USER_PAUSED", text)
        self.assertIn("EXPLICIT_EXIT", text)

    def test_visibility_owner_has_cancellable_non_polling_grace(self):
        text = OWNER.read_text(encoding="utf-8")
        self.assertIn("Application.ActivityLifecycleCallbacks", text)
        self.assertIn("postDelayed(pendingBackground, TRANSITION_GRACE_MS)", text)
        self.assertIn("removeCallbacks(pendingBackground)", text)
        self.assertNotIn("Timer", text)
        self.assertNotIn("Thread.sleep", text)

    def test_opt_in_policy_pauses_and_resumes_without_stop(self):
        text = LIFECYCLE.read_text(encoding="utf-8")
        preserve = text[text.index("public void onApplicationBackgrounded") :]
        self.assertIn("SageCommand.PAUSE", preserve)
        self.assertIn("SageCommand.PLAY", preserve)
        self.assertNotIn("SageCommand.STOP", preserve)
        self.assertIn("connection != null && !connection.isConnected()", preserve)

    def test_positive_setting_replaces_ambiguous_legacy_ui(self):
        prefs = PREFS.read_text(encoding="utf-8")
        background_prefs = BACKGROUND_PREFS.read_text(encoding="utf-8")
        lifecycle = LIFECYCLE.read_text(encoding="utf-8")
        session_config = SESSION_CONFIG.read_text(encoding="utf-8")
        self.assertIn('android:key="keep_session_in_background"', background_prefs)
        self.assertIn('android:key="background_session_settings"', prefs)
        self.assertIn('android:key="resume_background_playback"', background_prefs)
        self.assertIn('android:dependency="keep_session_in_background"', background_prefs)
        self.assertNotIn('android:key="app_destroy_on_pause"', prefs)
        self.assertIn("UiSessionConfiguration.load(client.properties())", lifecycle)
        self.assertIn("!legacyDestroy", session_config)

    def test_resume_option_only_controls_app_owned_playback(self):
        state = CORE.read_text(encoding="utf-8")
        lifecycle = LIFECYCLE.read_text(encoding="utf-8")
        session_config = SESSION_CONFIG.read_text(encoding="utf-8")
        self.assertIn("boolean resumeBackgroundPlayback", state)
        self.assertIn("&& resumeBackgroundPlayback", state)
        self.assertIn("Keys.resume_background_playback, true", session_config)

    def test_fire_tv_media_session_pause_is_coalesced_during_background_grace(self):
        state = CORE.read_text(encoding="utf-8")
        owner = OWNER.read_text(encoding="utf-8")
        lifecycle = LIFECYCLE.read_text(encoding="utf-8")
        self.assertIn("wasPlayingBeforeTransition", state)
        self.assertIn("backgroundCoalescedPauseCount", state)
        self.assertIn("onApplicationBackgroundCandidate()", owner)
        self.assertIn("onApplicationBackgroundCandidateCancelled()", owner)
        self.assertIn("backgroundCandidateWasPlaying", lifecycle)
        self.assertIn("if (!hasFocus)", lifecycle)
        self.assertIn("captureBackgroundCandidate();", lifecycle)

    def test_unfocused_fire_tv_media_pause_is_owned_by_background_policy(self):
        media_session = MEDIA_SESSION.read_text(encoding="utf-8")
        self.assertIn("backgroundOwnerWillOwnMediaSessionPause()", media_session)
        self.assertIn("Keys.keep_session_in_background", media_session)
        foreground_pause = media_session[media_session.index("public void onPause()"):
                                         media_session.index("public void onSkipToNext()")]
        self.assertIn("EventRouter.postCommand(client, SageCommand.PAUSE)", foreground_pause)

    def test_physical_gate_requires_same_generation_without_replay_fallback(self):
        text = SCRIPT.read_text(encoding="utf-8")
        self.assertIn('"keep_session_in_background": True', text)
        self.assertIn('"resume_background_playback": args.resume_background_playback', text)
        self.assertIn("--no-resume-background-playback", text)
        self.assertIn("HOME replaced the MiniClient connection", text)
        self.assertIn("Foreground return created a second connection", text)
        self.assertNotIn("foreground return reconnected and restarted", text)


if __name__ == "__main__":
    unittest.main()
