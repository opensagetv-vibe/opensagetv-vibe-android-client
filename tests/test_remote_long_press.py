import pathlib
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[1]
PROCESSOR = ROOT / (
    "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/"
    "android/ui/keymaps/KeyMapProcessor.java"
)


class RemoteLongPressTest(unittest.TestCase):
    def test_platform_and_elapsed_long_press_forms_are_supported(self):
        text = PROCESSOR.read_text(encoding="utf-8")
        self.assertIn("event.isLongPress()", text)
        self.assertIn("KeyEvent.FLAG_LONG_PRESS", text)
        self.assertIn("platformLongPress || elapsedLongPress", text)
        self.assertIn("event.getEventTime() - event.getDownTime()", text)
        self.assertIn("boolean releaseLongPress = !longPress", text)
        self.assertIn("handleKeyPress(keyMap, keyCode, event, true)", text)
        self.assertIn("new Handler(Looper.getMainLooper())", text)
        self.assertIn("schedulePendingLongPress(keyMap, keyCode, event)", text)
        self.assertIn("longPressHandler.postDelayed(pendingLongPressTask, delayMs)", text)
        self.assertIn("longPressHandler.removeCallbacks(pendingLongPressTask)", text)

    def test_activity_lifecycle_cancels_pending_remote_hold(self):
        listener = (
            ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/"
            "miniclient/android/ui/MiniClientKeyListener.java"
        ).read_text(encoding="utf-8")
        lifecycle = (
            ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/"
            "miniclient/android/UIActivityLifeCycleHandler.java"
        ).read_text(encoding="utf-8")
        self.assertIn("public void shutdown()", listener)
        self.assertGreaterEqual(lifecycle.count("keyListener.shutdown();"), 3)

    def test_dvd_menu_only_intercepts_short_remote_presses(self):
        text = PROCESSOR.read_text(encoding="utf-8")
        self.assertIn("if (!longPress && dvdMenuCommand != null)", text)
        self.assertNotIn("\n        if (dvdMenuCommand != null)\n", text)

    def test_miniclient_activities_own_remote_dispatch_above_render_surfaces(self):
        lifecycle = (
            ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/"
            "miniclient/android/UIActivityLifeCycleHandler.java"
        ).read_text(encoding="utf-8")
        self.assertIn("public boolean dispatchKeyEvent(KeyEvent event)", lifecycle)
        self.assertIn("listener.onKey(target, event.getKeyCode(), event)", lifecycle)

        for renderer in ("opengl/MiniClientOpenGLActivity.java", "gdx/MiniClientGDXActivity.java"):
            activity = (
                ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/"
                f"miniclient/android/{renderer}"
            ).read_text(encoding="utf-8")
            self.assertIn("public boolean dispatchKeyEvent(KeyEvent event)", activity)
            self.assertIn("uiActivityLifeCycleHandler.dispatchKeyEvent(event)", activity)

    def test_remote_navigation_dialog_uses_direct_foreground_ui_owner(self):
        text = PROCESSOR.read_text(encoding="utf-8")
        self.assertIn("longPress && command == SageCommand.NAV_OSD", text)
        self.assertIn("uiHandler.showHideSoftRemote(true);", text)

    def test_all_android_tv_navigation_layouts_contain_active_adjustments(self):
        for qualifier in ("layout", "layout-notouch"):
            layout = (
                ROOT / "source/dev/android-tv/src/main/res" / qualifier / "navigation.xml"
            ).read_text(encoding="utf-8")
            self.assertIn('android:id="@+id/nav_active_player_adjustments"', layout)

        dialog = (
            ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/"
            "miniclient/android/NavigationDialog.java"
        ).read_text(encoding="utf-8")
        self.assertIn("if (activePlayerAdjustments != null)", dialog)


if __name__ == "__main__":
    unittest.main()
