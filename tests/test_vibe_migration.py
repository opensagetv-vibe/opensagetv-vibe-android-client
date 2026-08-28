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


if __name__ == "__main__":
    unittest.main()
