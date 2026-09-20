from pathlib import Path
import sys
import unittest


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "mcp" / "src"))
sys.path.insert(0, str(ROOT / "scripts"))

from sagetv_dev_mcp.config import load_test_environment  # noqa: E402
from mcp_fixture_matrix import selected_checks  # noqa: E402


class FixtureRegistryTests(unittest.TestCase):
    def test_new_enabled_fixture_is_discovered_by_mode_not_id(self):
        environment = load_test_environment(ROOT / "config" / "firetv.example.toml")
        cases = environment.fixture_cases(mode="uk_broadcast", enabled_only=False)
        self.assertEqual(
            {case["id"] for case in cases},
            {"uk_taskmaster", "uk_breakfast", "uk_classic_holby"},
        )
        data = environment.data
        data["fixtures"]["cases"].append({
            "id": "a_future_uk_sample",
            "enabled": True,
            "path_type": "server_path",
            "path": "/var/media/tests/future.ts",
            "modes": {"uk_broadcast": True, "seek": True},
        })
        discovered = environment.__class__(data).fixture_cases(
            mode="uk_broadcast", enabled_only=True
        )
        self.assertIn("a_future_uk_sample", {case["id"] for case in discovered})

    def test_fixture_and_each_operation_mode_can_be_disabled(self):
        case = {
            "modes": {
                "seek": True,
                "skip": False,
                "pause_resume": True,
                "comskip": False,
            }
        }
        self.assertEqual(selected_checks(case), ["absolute_seek", "pause_resume"])

    def test_teletext_probe_mode_has_positive_and_negative_controls(self):
        environment = load_test_environment(ROOT / "config" / "firetv.example.toml")
        cases = environment.fixture_cases(mode="teletext_probe", enabled_only=False)
        expected = {case["id"]: case["expected_teletext_pes"] for case in cases}
        self.assertEqual(expected, {
            "uk_taskmaster": "absent",
            "uk_breakfast": "preserved",
            "uk_classic_holby": "preserved",
        })

    def test_root_workflow_exposes_mode_discovery_runner(self):
        text = (ROOT / "dev.sh").read_text(encoding="utf-8")
        script = (ROOT / "scripts" / "mcp_fixture_matrix.py").read_text(encoding="utf-8")
        self.assertIn("mcp-fixture-matrix)", text)
        self.assertIn("default_fixture_cases(mode=args.fixture_mode", script)
        self.assertNotIn('fixture_case("uk_taskmaster")', script)


if __name__ == "__main__":
    unittest.main()
