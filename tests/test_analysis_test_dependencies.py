"""Keep optional host analysis tests reproducible without changing SDK runtime."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]


class AnalysisTestDependencies(unittest.TestCase):
    def test_numerical_test_dependencies_are_pinned(self):
        requirements = (ROOT / "tests/requirements.txt").read_text()
        pins = {line.strip() for line in requirements.splitlines()
                if line.strip() and not line.startswith("#")}
        self.assertEqual(pins, {"numpy==2.5.1", "scipy==1.18.0"})

    def test_ci_installs_test_only_dependencies_before_contracts(self):
        workflow = (ROOT / ".github/workflows/repository-checks.yml").read_text()
        self.assertLess(workflow.index("pip install -r tests/requirements.txt"),
                        workflow.index("unittest discover -s tests"))

    def test_standard_launcher_preserves_sdk_and_cleans_owned_environment(self):
        launcher = (ROOT / "scripts/run_unit_tests.sh").read_text()
        self.assertIn("mktemp -d /tmp/vibe-unit-tests.", launcher)
        self.assertIn("--system-site-packages", launcher)
        self.assertIn("trap cleanup_unit_test_environment EXIT", launcher)
        self.assertIn('realpath "$unit_test_environment"', launcher)
        self.assertIn('"$resolved_environment" == /tmp/vibe-unit-tests.*', launcher)
        self.assertIn('"$unit_test_environment/bin/python3" -m pip install', launcher)


if __name__ == "__main__":
    unittest.main()
