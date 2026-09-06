from pathlib import Path
import unittest
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]
LAYOUT = (
    ROOT
    / "source"
    / "dev"
    / "android-shared"
    / "src"
    / "main"
    / "res"
    / "layout"
    / "fragment_add_server.xml"
)
FRAGMENT = (
    ROOT
    / "source"
    / "dev"
    / "android-shared"
    / "src"
    / "main"
    / "java"
    / "opensagetv"
    / "vibe"
    / "miniclient"
    / "android"
    / "AddServerFragment.java"
)
ANDROID = "{http://schemas.android.com/apk/res/android}"


def _view(root, view_id):
    expected = f"@+id/{view_id}"
    return next(node for node in root.iter() if node.get(ANDROID + "id") == expected)


class AddServerRemoteNavigationTest(unittest.TestCase):
    def test_form_has_explicit_tv_remote_focus_order(self):
        root = ET.parse(LAYOUT).getroot()
        name = _view(root, "server_name")
        address = _view(root, "server_address")
        add_button = _view(root, "button_ok")

        self.assertEqual("@id/server_address", name.get(ANDROID + "nextFocusDown"))
        self.assertEqual("@id/server_address", name.get(ANDROID + "nextFocusForward"))
        self.assertEqual("@id/button_ok", address.get(ANDROID + "nextFocusDown"))
        self.assertEqual("@id/button_ok", address.get(ANDROID + "nextFocusForward"))
        self.assertEqual("@id/server_address", add_button.get(ANDROID + "nextFocusUp"))

    def test_fields_are_single_line_with_tv_ime_actions(self):
        root = ET.parse(LAYOUT).getroot()
        name = _view(root, "server_name")
        address = _view(root, "server_address")

        self.assertEqual("true", name.get(ANDROID + "singleLine"))
        self.assertEqual("actionNext", name.get(ANDROID + "imeOptions"))
        self.assertEqual("true", address.get(ANDROID + "singleLine"))
        self.assertEqual("actionDone", address.get(ANDROID + "imeOptions"))

    def test_fire_os_dpad_events_have_explicit_focus_handlers(self):
        source = FRAGMENT.read_text(encoding="utf-8")

        self.assertIn("serverName.setOnKeyListener", source)
        self.assertIn("serverAddr.setOnKeyListener", source)
        self.assertIn("addButton.setOnKeyListener", source)
        self.assertGreaterEqual(source.count("KeyEvent.KEYCODE_DPAD_DOWN"), 2)
        self.assertGreaterEqual(source.count("KeyEvent.KEYCODE_DPAD_UP"), 2)
        self.assertIn("EditorInfo.IME_ACTION_NEXT", source)
        self.assertIn("EditorInfo.IME_ACTION_DONE", source)

    def test_launcher_add_server_flow_uses_androidx_fragments(self):
        shared = ROOT / "source" / "dev" / "android-shared" / "src" / "main" / "java" / "opensagetv" / "vibe" / "miniclient" / "android"
        tv = ROOT / "source" / "dev" / "android-tv" / "src" / "main"
        add_server = (shared / "AddServerFragment.java").read_text(encoding="utf-8")
        auto_connect = (shared / "AutoConnectDialog.java").read_text(encoding="utf-8")
        main_activity = (tv / "java/opensagetv/vibe/miniclient/android/tv/MainActivity.java").read_text(encoding="utf-8")
        main_fragment = (tv / "java/opensagetv/vibe/miniclient/android/tv/MainFragment.java").read_text(encoding="utf-8")
        servers = (tv / "java/opensagetv/vibe/miniclient/android/phone/ServersActivity.java").read_text(encoding="utf-8")
        layout = (tv / "res/layout/activity_main.xml").read_text(encoding="utf-8")

        self.assertIn("androidx.fragment.app.DialogFragment", add_server)
        self.assertIn("androidx.fragment.app.DialogFragment", auto_connect)
        self.assertNotIn("android.app.DialogFragment", add_server + auto_connect)
        self.assertIn("extends FragmentActivity", main_activity)
        self.assertIn("extends FragmentActivity", servers)
        self.assertIn("getSupportFragmentManager()", main_activity + servers)
        self.assertIn("extends BrowseSupportFragment", main_fragment)
        self.assertIn("getParentFragmentManager()", main_fragment)
        self.assertIn("androidx.fragment.app.FragmentContainerView", layout)


if __name__ == "__main__":
    unittest.main()
