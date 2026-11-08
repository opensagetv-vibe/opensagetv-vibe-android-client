from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
DEV = ROOT / "source/dev"


class DynamicStreamingDefaultTests(unittest.TestCase):
    def test_dynamic_is_the_push_label_not_pull_or_auto(self):
        resources = ET.parse(DEV / "android-shared/src/main/res/values/arrays.xml").getroot()
        labels = [item.text for item in resources.find("string-array[@name='entries_list_streaming_mode_preference']")]
        values = [item.text for item in resources.find("string-array[@name='entryvalues_list_streaming_mode_preference']")]
        self.assertEqual(len(labels), len(values))
        mapping = dict(zip(values, labels))
        self.assertEqual(mapping["dynamic"], "Push")
        self.assertEqual(mapping["pull"], "Pull")

    def test_explicit_push_suppresses_pull_after_owned_transport_override(self):
        text = (DEV / "core/src/main/java/opensagetv/vibe/miniclient/MiniClientConnection.java").read_text(encoding="utf-8")
        block = text.split('else if ("PULL_AV_CONTAINERS".equals(propName))', 1)[1].split('else if ("MEDIA_PLAYER_BUFFER_DELAY"', 1)[0]
        self.assertLess(block.index("isMimDirectTransportActive()"), block.index("StreamingModePolicy.isForcedPush"))
        self.assertIn('StreamingModePolicy.isForcedPush(client.properties().getStreamingMode())', block)
        self.assertIn('propVal = "";', block)
        self.assertIn('StreamingModePolicy.isForcedPull', block)
        self.assertIn('"fixed".equalsIgnoreCase', block)

    def test_android_pref_store_defaults_to_dynamic(self):
        text = (DEV / "android-shared/src/main/java/opensagetv/vibe/miniclient/android/prefs/AndroidPrefStore.java").read_text(encoding="utf-8")
        self.assertIn('STREAMING_MODE_DEFAULT = "dynamic";', text)
        self.assertNotIn('STREAMING_MODE_DEFAULT = "fixed";', text)

    def test_preference_xml_defaults_to_dynamic(self):
        text = (DEV / "android-shared/src/main/res/xml/prefs.xml").read_text(encoding="utf-8")
        match = re.search(r'<ListPreference\b[^>]*android:key="streaming_mode"[^>]*/>', text, flags=re.S)
        self.assertIsNotNone(match)
        block = match.group(0)
        self.assertIn('android:defaultValue="dynamic"', block)
        self.assertNotIn('android:defaultValue="fixed"', block)


if __name__ == "__main__":
    unittest.main()
