from pathlib import Path
import subprocess
import sys
import unittest


ROOT = Path(__file__).resolve().parents[1]


class ProjectManifestTests(unittest.TestCase):
    def test_repository_manifest_is_current(self):
        result = subprocess.run(
            [sys.executable, str(ROOT / "scripts" / "project_manifest.py"), "--check"],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)


if __name__ == "__main__":
    unittest.main()
