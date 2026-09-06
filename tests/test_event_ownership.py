from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
DEV = ROOT / "source/dev"
ANDROID = DEV / "android-shared/src/main/java/opensagetv/vibe/miniclient/android"


class EventOwnershipTests(unittest.TestCase):
    def test_android_has_no_otto_source_or_dependency(self):
        gradle = (DEV / "android-shared/build.gradle").read_text(encoding="utf-8")
        java = "\n".join(
            path.read_text(encoding="utf-8")
            for path in (DEV / "android-shared/src/main/java").rglob("*.java")
        )
        self.assertNotIn("com.squareup:otto", gradle)
        self.assertNotIn("com.squareup.otto", java)
        self.assertFalse((ANDROID / "OttoBusImpl.java").exists())
        verification = (ROOT / "source/dev/gradle/verification-metadata.xml").read_text(
            encoding="utf-8"
        )
        self.assertNotIn('group="com.squareup" name="otto"', verification)
        self.assertNotIn('group="com.squareup" name="otto-parent"', verification)

    def test_explicit_listener_owns_every_current_android_event(self):
        listener = (ANDROID / "VibeEventListener.java").read_text(encoding="utf-8")
        bus = (ANDROID / "VibeEventBus.java").read_text(encoding="utf-8")
        expected = (
            "ConnectedEvent", "ConnectionLost", "DebugSageCommandEvent",
            "ShowKeyboardEvent", "ShowNavigationEvent", "BackPressedEvent",
            "ChangePlayerOneTime", "CloseAppEvent", "HideKeyboardEvent",
            "HideNavigationEvent", "HideSystemUIEvent", "MessageEvent",
            "ToggleAspectRatioEvent", "DebugKeyEvent", "VideoInfoRefresh",
            "VideoInfoShow",
        )
        for event in expected:
            self.assertIn(event, listener)
            self.assertIn(f"event instanceof {event}", bus)

    def test_listener_lifetimes_and_order_registry_are_explicit(self):
        lifecycle = (ANDROID / "UIActivityLifeCycleHandler.java").read_text(encoding="utf-8")
        dialog = (ANDROID / "VideoInfoDialog.java").read_text(encoding="utf-8")
        registry = (
            DEV
            / "core/src/main/java/opensagetv/vibe/miniclient/OrderedListenerRegistry.java"
        ).read_text(encoding="utf-8")
        self.assertIn("implements MACAddressResolver, AndroidUIController, BackgroundSessionOwner.Listener, VibeEventListener", lifecycle)
        self.assertIn("extends Dialog implements VibeEventListener", dialog)
        self.assertIn("CopyOnWriteArrayList", registry)
        self.assertIn("listeners.addIfAbsent(listener)", registry)


if __name__ == "__main__":
    unittest.main()
