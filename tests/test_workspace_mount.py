from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]

class WorkspaceMountTests(unittest.TestCase):
    def test_compose_defaults_to_project_directory(self):
        text = (ROOT / "docker-compose.yml").read_text()
        self.assertIn('source: "${SAGETV_WINDOWS_ROOT:-.}"', text)
        self.assertNotIn('SAGETV_WINDOWS_ROOT:-C:/SageTV-MiniClient-Dev', text)

    def test_test_runner_is_packaged(self):
        self.assertTrue((ROOT / "scripts/run_unit_tests.sh").is_file())

    def test_dev_sh_exports_absolute_workspace(self):
        text = (ROOT / "dev.sh").read_text(encoding="utf-8")
        self.assertIn('export SAGETV_WINDOWS_ROOT="${SAGETV_WINDOWS_ROOT:-$ROOT}"', text)

if __name__ == "__main__":
    unittest.main()
