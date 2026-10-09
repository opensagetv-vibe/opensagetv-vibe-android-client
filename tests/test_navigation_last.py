"""Last-channel button uses stock event 60 and fills the Page Up/Down column."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ANDROID = "{http://schemas.android.com/apk/res/android}"


class LastChannelMenuTests(unittest.TestCase):
    def test_two_settings_rows_share_columns_without_empty_leading_cell(self):
        for relative in ("android-shared/src/main/res/layout/navigation.xml",
                         "android-tv/src/main/res/layout/navigation.xml",
                         "android-tv/src/main/res/layout-notouch/navigation.xml"):
            with self.subTest(layout=relative):
                root = ET.parse(ROOT / "source/dev" / relative).getroot()
                parent = {child: node for node in root.iter() for child in node}
                icons = {node.get(ANDROID + "id"): node for node in root.iter("ImageView")}
                top = [icons["@+id/nav_" + name] for name in
                       ("video_info", "export_diagnostics", "test_current_video", "playback_stats")]
                bottom = [icons["@+id/nav_" + name] for name in
                          ("toggle_ar", "active_player_adjustments", "audio_output", "closed_captions")]
                for a, b in zip(top, bottom):
                    self.assertIs(parent[a], parent[b])
                    self.assertEqual(a.get(ANDROID + "layout_column"), b.get(ANDROID + "layout_column"))
                columns = [int(node.get(ANDROID + "layout_column")) for node in bottom]
                self.assertEqual(list(range(columns[0], columns[0] + 4)), columns)
                # Touch has a dedicated four-column grid. The TV notouch
                # layout shares its larger grid with other remote controls.
                if "layout-notouch" not in relative:
                    self.assertEqual(columns[-1] + 1, int(parent[top[0]].get(ANDROID + "columnCount")))
                for row in (top, bottom):
                    self.assertEqual(1, len({node.get(ANDROID + "layout_row") for node in row}))

    def test_all_active_layouts_fill_the_middle_cell(self):
        for relative in ("android-shared/src/main/res/layout/navigation.xml",
                         "android-tv/src/main/res/layout/navigation.xml",
                         "android-tv/src/main/res/layout-notouch/navigation.xml"):
            with self.subTest(layout=relative):
                root = ET.parse(ROOT / "source/dev" / relative).getroot()
                parent = {child: node for node in root.iter() for child in node}
                icons = {node.get(ANDROID + "id"): node for node in root.iter("ImageView")}
                up, last, down = [icons["@+id/nav_" + key] for key in ("pgup", "last", "pgdn")]
                self.assertIs(parent[up], parent[last])
                self.assertIs(parent[last], parent[down])
                self.assertEqual(up.get(ANDROID + "layout_column"), last.get(ANDROID + "layout_column"))
                self.assertEqual(last.get(ANDROID + "layout_column"), down.get(ANDROID + "layout_column"))
                self.assertLess(int(up.get(ANDROID + "layout_row")), int(last.get(ANDROID + "layout_row")))
                self.assertLess(int(last.get(ANDROID + "layout_row")), int(down.get(ANDROID + "layout_row")))
                self.assertEqual("prev_channel", last.get(ANDROID + "tag"))
                self.assertEqual("@string/nav_last_channel", last.get(ANDROID + "contentDescription"))
                self.assertEqual("@drawable/ic_last_channel_white_24dp", last.get(ANDROID + "src"))

    def test_button_dispatches_existing_stock_command_without_dismissing_menu(self):
        source = (ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/"
                  "miniclient/android/NavigationDialog.java").read_text(encoding="utf-8")
        self.assertIn("R.id.nav_pgup, R.id.nav_last", source)
        self.assertIn("SageCommand.parseByKey(key)", source)
        self.assertIn("EventRouter.postCommand(client, sageCommand)", source)
        command = (ROOT / "source/dev/core/src/main/java/opensagetv/vibe/miniclient/SageCommand.java").read_text(encoding="utf-8")
        self.assertIn('PREV_CHANNEL(60, "prev_channel"', command)

    def test_icon_is_a_matching_vector_not_a_bitmap_dependency(self):
        icon = ET.parse(ROOT / "source/dev/android-shared/src/main/res/drawable/ic_last_channel_white_24dp.xml").getroot()
        self.assertEqual("vector", icon.tag)
        self.assertEqual("24dp", icon.get(ANDROID + "width"))
        self.assertEqual("24dp", icon.get(ANDROID + "height"))
        self.assertEqual(2, len(icon.findall("path")))


if __name__ == "__main__": unittest.main()
