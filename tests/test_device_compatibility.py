import pathlib
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[1]


class DeviceCompatibilityContractTest(unittest.TestCase):
    def test_minimum_android_version_is_documented_consistently(self):
        gradle = (ROOT / "source/dev/build.gradle").read_text(encoding="utf-8")
        root_readme = (ROOT / "README.md").read_text(encoding="utf-8")
        source_readme = (ROOT / "source/dev/README.md").read_text(encoding="utf-8")
        device_doc = (ROOT / "docs/DEVICE_COMPATIBILITY.md").read_text(encoding="utf-8")
        self.assertIn("androidMinSdkVersion = 23", gradle)
        self.assertIn("Android 6.0 / API 23", root_readme)
        self.assertIn("Android 6.0 (API 23)", source_readme)
        self.assertIn("minSdkVersion=23", device_doc)
        self.assertIn("Android 5.0/5.1 (API 21/22) cannot install", device_doc)

    def test_packaged_abis_and_diagnostic_requirements_are_durable(self):
        tv_gradle = (ROOT / "source/dev/android-tv/build.gradle").read_text(encoding="utf-8")
        device_doc = (ROOT / "docs/DEVICE_COMPATIBILITY.md").read_text(encoding="utf-8")
        self.assertIn("'armeabi-v7a', 'arm64-v8a'", tv_gradle)
        self.assertIn("Test Current Video", device_doc)
        self.assertIn("decoded PCM", device_doc)
        self.assertIn("cannot honestly claim", device_doc)


if __name__ == "__main__":
    unittest.main()
