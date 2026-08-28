from pathlib import Path
import importlib.util
import sys
import unittest

ROOT = Path(__file__).resolve().parents[1]
SCRIPTS = ROOT / "scripts"
sys.path.insert(0, str(SCRIPTS))
spec = importlib.util.spec_from_file_location("mcp_seek_suite", SCRIPTS / "mcp_seek_suite.py")
module = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(module)


class MCPSeekCalibrationTests(unittest.TestCase):
    def test_observed_primary_skip_uses_preference_quantum(self):
        self.assertEqual(module.snap_skip_ms(8846), 10000)
        self.assertEqual(module.snap_skip_ms(-11112), -10000)
        self.assertEqual(module.snap_skip_ms(9653), 10000)
        self.assertEqual(module.snap_skip_ms(-10319), -10000)

    def test_calibration_quantum_can_be_lowered_for_unusual_custom_interval(self):
        self.assertEqual(module.snap_skip_ms(8846, 1000), 9000)
        self.assertEqual(module.snap_skip_ms(-11112, 1000), -11000)

    def test_calibration_rejects_wrong_direction_outlier(self):
        snapped, center, valid = module.select_calibration_value(
            [-10045, 56407, -9860, -10120], -1, 5000
        )
        self.assertEqual(snapped, -10000)
        self.assertEqual(center, -10045)
        self.assertEqual(valid, [-10045, -9860, -10120])

    def test_calibration_uses_median_of_valid_samples(self):
        snapped, center, valid = module.select_calibration_value(
            [9300, 10100, 18750, 9800], +1, 5000
        )
        self.assertEqual(snapped, 10000)
        self.assertEqual(center, 9950)
        self.assertEqual(valid, [9300, 10100, 18750, 9800])

    def test_semantic_plus_30_expands_from_ten_second_ff(self):
        commands, represented = module.commands_for_delta(30000, 10000, -10000)
        self.assertEqual(commands, ["ff", "ff", "ff"])
        self.assertEqual(represented, 30000)

    def test_manual_rapid_semantics_net_plus_80(self):
        targets = [30000, 30000, -10000, 60000, -30000]
        commands = []
        represented = 0
        for target in targets:
            part, delta = module.commands_for_delta(target, 10000, -10000)
            commands.extend(part)
            represented += delta
        self.assertEqual(represented, 80000)
        self.assertEqual(commands.count("ff"), 12)
        self.assertEqual(commands.count("rew"), 4)

    def test_non_divisible_custom_interval_uses_closest_practical_plan(self):
        commands, represented = module.commands_for_delta(30000, 9000, -11000)
        self.assertEqual(commands, ["ff", "ff", "ff"])
        self.assertEqual(represented, 27000)

    def test_non_divisible_rapid_plan_validates_against_represented_total(self):
        targets = [30000, 30000, -10000, 60000, -30000]
        represented = 0
        commands = []
        for target in targets:
            part, delta = module.commands_for_delta(target, 9000, -11000)
            commands.extend(part)
            represented += delta
        self.assertEqual(represented, 73000)
        self.assertEqual(commands.count("ff"), 13)
        self.assertEqual(commands.count("rew"), 4)

    def test_calibration_uses_android_skip_check_measurement(self):
        script = (SCRIPTS / "mcp_seek_suite.py").read_text(encoding="utf-8")
        self.assertIn('"dev_run_seek_check"', script)
        self.assertIn('result.get("timelineDeltaMs"', script)
        self.assertNotIn('"dev_sage_command_sequence",\n            {"commands": [command]', script)


if __name__ == "__main__":
    unittest.main()
