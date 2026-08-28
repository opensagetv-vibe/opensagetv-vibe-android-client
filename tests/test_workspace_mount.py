from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]

class WorkspaceMountTests(unittest.TestCase):
    def test_compose_defaults_to_project_directory(self):
        text = (ROOT / "docker-compose.yml").read_text()
        self.assertIn('source: "${OPENSAGETV_VIBE_ANDROID_ROOT:-.}"', text)
        self.assertIn('name: opensagetv-vibe-android-client', text)
        self.assertIn('container_name: opensagetv-vibe-android-dev', text)

    def test_test_runner_is_packaged(self):
        self.assertTrue((ROOT / "scripts/run_unit_tests.sh").is_file())

    def test_dev_sh_exports_absolute_workspace(self):
        text = (ROOT / "dev.sh").read_text(encoding="utf-8")
        self.assertIn('export OPENSAGETV_VIBE_ANDROID_ROOT=', text)
        self.assertIn('SAGETV_WINDOWS_ROOT:-$ROOT', text)

    def test_commands_reuse_one_named_container(self):
        text = (ROOT / "dev.sh").read_text(encoding="utf-8")
        self.assertIn('"${COMPOSE[@]}" up -d dev', text)
        self.assertIn('"${COMPOSE[@]}" exec -T dev', text)
        self.assertNotIn('run --rm', text)

if __name__ == "__main__":
    unittest.main()
