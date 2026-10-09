from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android"


class TouchNavigationDefaultTests(unittest.TestCase):
    def test_only_unset_single_finger_default_is_navigation(self):
        text = (JAVA / "preferences/TouchPreferences.java").read_text()
        single = text.split("public SageCommand getLongPress()", 1)[1].split("public SageCommand getDoubleLongPress()", 1)[0]
        self.assertIn('getString("long_press", SageCommand.NAV_OSD.getKey())', single)
        self.assertNotIn("setString(", single)
        self.assertNotIn("remove(", single)
        self.assertIn('getString("long_press_2", SageCommand.NONE.getKey())', text)
        self.assertIn('getString("long_press_3", SageCommand.NONE.getKey())', text)

    def test_gesture_and_settings_use_the_same_preserving_resolver(self):
        gesture = (JAVA / "ui/UIGestureListener.java").read_text()
        settings = (JAVA / "ui/settings/TouchMappingsFragment.java").read_text()
        self.assertIn("EventRouter.postCommand(client, prefs.getLongPress())", gesture)
        self.assertIn('configureList(prefs, "long_press", prefs.getLongPress())', settings)


if __name__ == "__main__":
    unittest.main()
