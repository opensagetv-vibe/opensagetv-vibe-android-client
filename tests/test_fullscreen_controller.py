from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
ACTIVE = [
    ROOT / "source/dev/android-shared/src/main",
    ROOT / "source/dev/android-tv/src/main",
]


class FullscreenControllerTests(unittest.TestCase):
    def test_direct_system_ui_calls_are_centralized_in_app_util(self):
        offenders = []
        for tree in ACTIVE:
            for path in tree.rglob("*.java"):
                if path.name == "AppUtil.java":
                    continue
                if "setSystemUiVisibility(" in path.read_text(encoding="utf-8"):
                    offenders.append(str(path.relative_to(ROOT)))
        self.assertEqual(offenders, [])

    def test_connecting_activity_uses_main_looper_and_cancels_callbacks(self):
        path = ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/connect/ConnectingActivity.java"
        text = path.read_text(encoding="utf-8")
        self.assertIn("new Handler(Looper.getMainLooper())", text)
        self.assertIn("AppUtil.hideSystemUI(ConnectingActivity.this)", text)
        self.assertIn("AppUtil.showSystemUI(this)", text)
        self.assertIn("removeCallbacksAndMessages(null)", text)

    def test_api30_fullscreen_is_safe_before_decor_attachment(self):
        app_util = (ROOT / "source/dev/android-shared/src/main/java/"
                    "opensagetv/vibe/miniclient/android/AppUtil.java").read_text(encoding="utf-8")
        settings = (ROOT / "source/dev/android-shared/src/main/java/"
                    "opensagetv/vibe/miniclient/android/ui/settings/SettingsActivity.java").read_text(encoding="utf-8")
        servers = (ROOT / "source/dev/android-tv/src/main/java/"
                   "opensagetv/vibe/miniclient/android/phone/ServersActivity.java").read_text(encoding="utf-8")

        self.assertIn("decorView.getWindowInsetsController()", app_util)
        self.assertNotIn("window.getInsetsController()", app_util)
        self.assertIn("if (controller == null)", app_util)
        self.assertIn("setLegacySystemBarsHidden(decorView, hidden)", app_util)
        self.assertIn("catch (RuntimeException e)", app_util)
        self.assertLess(settings.index("setContentView"), settings.index("hideSystemUIOnTV"))
        self.assertLess(servers.index("super.onCreate"), servers.index("hideSystemUIOnTV"))
        self.assertLess(servers.index("setContentView"), servers.index("hideSystemUIOnTV"))

    def test_initialization_error_ui_is_null_safe(self):
        handler = (ROOT / "source/dev/android-shared/src/main/java/"
                   "opensagetv/vibe/miniclient/android/UIActivityLifeCycleHandler.java").read_text(encoding="utf-8")
        self.assertIn("plaseWaitText == null || errorMessage == null", handler)
        self.assertIn("MiniClient error UI is unavailable", handler)

    def test_stock_server_preview_promotion_is_conditional_and_generation_safe(self):
        base = (ROOT / "source/dev/android-shared/src/main/java/"
                "opensagetv/vibe/miniclient/android/video/BaseMediaPlayerImpl.java").read_text(encoding="utf-8")
        policy = (ROOT / "source/dev/core/src/main/java/"
                  "opensagetv/vibe/miniclient/video/FullscreenPlaybackPolicy.java").read_text(encoding="utf-8")
        self.assertIn("final PlaybackSessionController.Token session", base)
        self.assertIn("!playbackSessions.isCurrentSession(session)", base)
        self.assertIn("state != PLAY_STATE || !playerReady || player == null", base)
        self.assertIn("FULLSCREEN_PROMOTION_DELAY_MS = 2500", base)
        self.assertIn("FullscreenPlaybackPolicy.isPlaybackMenu", base)
        self.assertIn("FullscreenPlaybackPolicy.shouldPromote", base)
        self.assertIn("EventRouter.postCommand(context.getClient(), SageCommand.TV)", base)
        self.assertIn("videoWidth * 100", policy)
        self.assertIn("videoHeight * 100", policy)


if __name__ == "__main__":
    unittest.main()
