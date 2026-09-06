from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
SHARED = ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android"
DEBUG = ROOT / "source/dev/android-tv/src/debug/java/opensagetv/vibe/miniclient/android/tv/debug"


class AudioFocusControllerTests(unittest.TestCase):
    def test_modern_focus_controller_has_legacy_fallback_and_safe_resume_policy(self):
        text = (SHARED / "util/AudioFocusController.java").read_text(encoding="utf-8")
        for expected in (
            "AudioFocusRequest.Builder",
            "AudioAttributes.USAGE_MEDIA",
            "setWillPauseWhenDucked(true)",
            "Build.VERSION_CODES.O",
            "AudioManager.STREAM_MUSIC",
            "AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK",
            "resumeOnGain = wasPlaying",
            "resumeOnGain = false",
            "callbackGeneration != generation",
            "abandonAudioFocusRequest",
        ):
            self.assertIn(expected, text)

    def test_lifecycle_owns_and_releases_audio_focus(self):
        text = (SHARED / "UIActivityLifeCycleHandler.java").read_text(encoding="utf-8")
        self.assertIn("new AudioFocusController()", text)
        self.assertIn("audioFocusController.request(activity", text)
        self.assertGreaterEqual(text.count("audioFocusController.abandon()"), 2)
        self.assertIn("pauseForFocusLoss()", text)
        self.assertIn("resumeAfterFocusGain()", text)

    def test_obsolete_global_audio_utility_is_removed(self):
        self.assertFalse((SHARED / "util/AudioUtil.java").exists())
        for relative in (
            "source/dev/android-tv/src/main/java/opensagetv/vibe/miniclient/android/tv/MainActivity.java",
            "source/dev/android-tv/src/main/java/opensagetv/vibe/miniclient/android/phone/ServersActivity.java",
        ):
            text = (ROOT / relative).read_text(encoding="utf-8")
            self.assertNotIn("AudioUtil", text)

    def test_competing_focus_owner_is_debug_only_and_supports_all_gate_modes(self):
        text = (DEBUG / "DebugAudioFocusCommands.java").read_text(encoding="utf-8")
        for expected in (
            '"transient".equals(mode)',
            '"duck".equals(mode)',
            '"permanent".equals(mode)',
            "AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK",
            "abandonAudioFocusRequest",
        ):
            self.assertIn(expected, text)
        self.assertFalse(
            (ROOT / "source/dev/android-tv/src/main/java/opensagetv/vibe/miniclient/android/tv/debug/DebugAudioFocusCommands.java").exists()
        )


if __name__ == "__main__":
    unittest.main()
