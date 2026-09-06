from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]


class PlayBundlePipelineTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.entrypoint = (ROOT / "docker" / "entrypoint.sh").read_text(encoding="utf-8")
        cls.dev = (ROOT / "dev.sh").read_text(encoding="utf-8")

    def test_builds_and_validates_debug_and_release_candidate_aabs(self):
        self.assertIn(":android-tv:bundleDebug", self.entrypoint)
        self.assertIn(":android-tv:bundleRelease", self.entrypoint)
        self.assertGreaterEqual(self.entrypoint.count("validate --bundle="), 2)
        self.assertIn("build-apks", self.entrypoint)

    def test_bundle_install_is_limited_to_dev_debug_package(self):
        self.assertIn(
            '[[ "$package_name" = opensagetv.vibe.miniclient.debug ]]',
            self.entrypoint,
        )
        self.assertIn('install-apks --apks="$apks_out"', self.entrypoint)

    def test_root_wrapper_exposes_both_bundle_commands(self):
        self.assertIn("bundle-install)", self.dev)
        self.assertIn("dev_command bundle", self.dev)
        self.assertIn("dev_command bundle-install", self.dev)


if __name__ == "__main__":
    unittest.main()
