from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]


class ProjectManifestTests(unittest.TestCase):
    def test_validator_defaults_to_its_extracted_checkout(self):
        validator = (ROOT / "scripts" / "validate_project.py").read_text(encoding="utf-8")
        self.assertIn("DEFAULT_WORKSPACE = Path(__file__).resolve().parents[1]", validator)
        self.assertIn('str(DEFAULT_WORKSPACE)', validator)

    def test_repository_manifest_is_current(self):
        result = subprocess.run(
            [sys.executable, str(ROOT / "scripts" / "project_manifest.py"), "--check"],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)

    def test_extracted_source_archive_checks_without_git_metadata(self):
        with tempfile.TemporaryDirectory() as directory:
            checkout = Path(directory) / "checkout"
            (checkout / "scripts").mkdir(parents=True)
            shutil.copy2(ROOT / "scripts" / "project_manifest.py",
                         checkout / "scripts" / "project_manifest.py")
            (checkout / "payload.txt").write_text("archive payload\n", encoding="utf-8")
            import hashlib
            digest = hashlib.sha256((checkout / "payload.txt").read_bytes()).hexdigest()
            (checkout / "PROJECT_MANIFEST.sha256").write_text(
                f"{digest}  payload.txt\n", encoding="ascii"
            )
            result = subprocess.run(
                [sys.executable, str(checkout / "scripts" / "project_manifest.py"), "--check"],
                cwd=checkout,
                text=True,
                capture_output=True,
            )
            self.assertEqual(0, result.returncode, result.stdout + result.stderr)
            self.assertIn("PASS: PROJECT_MANIFEST.sha256", result.stdout)


if __name__ == "__main__":
    unittest.main()
