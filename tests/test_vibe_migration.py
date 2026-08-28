from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]


class VibeMigrationTests(unittest.TestCase):
    def test_repository_identity_files_exist(self):
        self.assertEqual("0.5.75", (ROOT / "VERSION").read_text().strip())
        self.assertTrue((ROOT / "MIGRATION_TO_OPENSAGETV_VIBE.md").is_file())
        self.assertTrue((ROOT / "docs" / "BASELINE_VALIDATION.md").is_file())
        self.assertTrue((ROOT / ".gitattributes").is_file())

    def test_artifact_name_is_consistent(self):
        expected = "OpenSageTV-Vibe-Android-Client-debug.apk"
        entrypoint = (ROOT / "docker" / "entrypoint.sh").read_text()
        cli = (ROOT / "mcp" / "src" / "sagetv_dev_mcp" / "cli.py").read_text()
        server = (ROOT / "mcp" / "src" / "sagetv_dev_mcp" / "server.py").read_text()
        for text in (entrypoint, cli, server):
            self.assertIn(expected, text)

    def test_android_identity_remains_isolated_during_migration(self):
        gradle = (ROOT / "source" / "dev" / "android-tv" / "build.gradle").read_text()
        self.assertIn('applicationId "org.opensagetv.miniclient.dev"', gradle)
        self.assertIn('applicationIdSuffix = ".debug"', gradle)
        self.assertNotIn("jvl.sage.miniclient", gradle)

    def test_normal_workspace_prefers_unified_build_environment(self):
        dev = (ROOT / "dev.sh").read_text(encoding="utf-8")
        self.assertIn("$ROOT/../opensagetv-vibe-build-env", dev)
        self.assertIn("UNIFIED_CONTAINER=", dev)
        self.assertIn("opensagetv-vibe-dev", dev)
        self.assertIn("CONTAINER_WORKSPACE=/workspace/android-client", dev)
        self.assertIn("OPENSAGETV_VIBE_ANDROID_STANDALONE", dev)
        self.assertIn("JAVA_HOME=/opt/java/jdk17", dev)
        self.assertIn("GRADLE_USER_HOME=/work/.gradle/android", dev)


if __name__ == "__main__":
    unittest.main()
