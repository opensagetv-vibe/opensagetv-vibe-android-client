from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
SETTINGS = ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/ui/settings"
SETTINGS_XML = ROOT / "source/dev/android-shared/src/main/res/xml"


class AndroidXSettingsTests(unittest.TestCase):
    def test_settings_package_no_longer_uses_platform_preference_api(self):
        offenders = []
        for path in SETTINGS.glob("*.java"):
            text = path.read_text(encoding="utf-8")
            if "android.preference." in text or "extends PreferenceFragment\n" in text:
                offenders.append(path.name)
        self.assertEqual(offenders, [])

    def test_all_preference_fragments_use_androidx(self):
        expected = {
            "CodecContainerFragment.java",
            "ExoPlayerSettingsFragment.java",
            "FixedRemuxingFragment.java",
            "FixedTranscodingFragment.java",
            "GSYPlayerSettingsFragment.java",
            "IJKPlayerSettingsFragment.java",
            "Media3PlayerSettingsFragment.java",
            "MediaMappingsFragment.java",
            "SettingsFragment.java",
            "TouchMappingsFragment.java",
        }
        for name in expected:
            text = (SETTINGS / name).read_text(encoding="utf-8")
            self.assertIn("extends PreferenceFragmentCompat", text, name)
            self.assertIn("onCreatePreferences", text, name)

    def test_preference_fragment_hosts_use_appcompat(self):
        for path in SETTINGS.glob("*Activity.java"):
            text = path.read_text(encoding="utf-8")
            self.assertIn("extends AppCompatActivity", text, path.name)

    def test_codec_dialog_uses_androidx_fragment_manager(self):
        text = (SETTINGS / "CodecDialogFragment.java").read_text(encoding="utf-8")
        self.assertIn("androidx.fragment.app.DialogFragment", text)
        self.assertIn("androidx.fragment.app.FragmentManager", text)
        self.assertNotIn("android.app.DialogFragment", text)

    def test_media_mapping_switches_match_the_compat_fragment_type(self):
        text = (SETTINGS_XML / "media_mappings_prefs.xml").read_text(encoding="utf-8")
        self.assertEqual(text.count("<SwitchPreferenceCompat"), 4)
        self.assertNotIn("<SwitchPreference\n", text)
        fragment = (SETTINGS / "MediaMappingsFragment.java").read_text(encoding="utf-8")
        self.assertIn("SwitchPreferenceCompat spSmartRemote", fragment)


if __name__ == "__main__":
    unittest.main()
