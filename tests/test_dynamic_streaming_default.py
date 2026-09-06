from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
DEV = ROOT / "source/dev"


class DynamicStreamingDefaultTests(unittest.TestCase):
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
