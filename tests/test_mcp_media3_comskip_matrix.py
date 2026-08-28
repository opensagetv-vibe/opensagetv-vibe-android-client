from pathlib import Path
import importlib.util
import sys
import unittest

ROOT = Path(__file__).resolve().parents[1]
SCRIPTS = ROOT / "scripts"
sys.path.insert(0, str(SCRIPTS))
spec = importlib.util.spec_from_file_location("mcp_media3_comskip_matrix", SCRIPTS / "mcp_media3_comskip_matrix.py")
module = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(module)


class MCPMedia3ComskipMatrixTests(unittest.TestCase):
    def test_directions_are_deduplicated_and_validated(self):
        self.assertEqual(module.parse_directions("right,left,right"), ["right", "left"])
        with self.assertRaises(ValueError):
            module.parse_directions("up")

    def test_comparison_contains_recovery_jump_direct_command_and_verdict(self):
        results = {
            "push": {"checks": {"right": {"recoveryMs": 500, "landingDeltaMs": 120000, "directSageCommand": "right", "passed": True}}},
            "pull": {"checks": {"right": {"recoveryMs": 900, "landingDeltaMs": 118000, "directSageCommand": "right", "passed": True}}},
        }
        row = module.comparison_table(results)["right"]
        self.assertEqual(row["push"]["recoveryMs"], 500)
        self.assertEqual(row["pull"]["landingDeltaMs"], 118000)
        self.assertEqual(row["push"]["directSageCommand"], "right")
        self.assertTrue(row["push"]["passed"])

    def test_pull_io_delta_reports_per_command_network_work(self):
        result = {
            "before_dataSourceOpenCount": 4,
            "final_dataSourceOpenCount": 7,
            "before_dataSourceOpenWaitMs": 40,
            "final_dataSourceOpenWaitMs": 70,
            "before_dataSourceNetworkReadCount": 10,
            "final_dataSourceNetworkReadCount": 16,
            "before_dataSourceNetworkReadRequestedBytes": 1000,
            "final_dataSourceNetworkReadRequestedBytes": 5000,
            "before_dataSourceNetworkReadBytes": 900,
            "final_dataSourceNetworkReadBytes": 4700,
            "before_dataSourceNetworkReadWaitMs": 100,
            "final_dataSourceNetworkReadWaitMs": 900,
            "before_dataSourceNetworkReadErrors": 0,
            "final_dataSourceNetworkReadErrors": 1,
            "final_dataSourceNetworkReadMaxRequestedBytes": 262144,
        }
        io = module.pull_io_delta(result)
        self.assertEqual(io["openCountDelta"], 3)
        self.assertEqual(io["networkReadCountDelta"], 6)
        self.assertEqual(io["networkReadWaitMsDelta"], 800)
        self.assertEqual(io["networkReadMaxRequestedBytes"], 262144)
        self.assertEqual(io["networkReadErrorsDelta"], 1)

    def test_matrix_uses_fresh_native_start_and_output_health_comskip_tool(self):
        script = (SCRIPTS / "mcp_media3_comskip_matrix.py").read_text(encoding="utf-8")
        devsh = (ROOT / "dev.sh").read_text(encoding="utf-8")
        self.assertIn("from mcp_media3_matrix import", script)
        self.assertIn("start_mode", script)
        self.assertIn('"dev_run_comskip_check"', script)
        self.assertIn('directSageCommand', script)
        self.assertIn('same path proven manually with ``mcp-send-sequence``', script)
        self.assertIn('"expected_target_ms": -1', script)
        self.assertIn('slowRecovery', script)
        self.assertIn('pull_io_delta', script)
        self.assertIn('pull I/O:', script)
        self.assertIn('markerMetadataAvailable', script)
        self.assertIn('mcp-media3-comskip-matrix)', devsh)
        self.assertIn('parser.add_argument("--text", required=True', script)
        self.assertNotIn('default="meet the press"', script)
        self.assertIn('parser.add_argument("--watchdog-ms", type=int, default=180000', script)
        self.assertIn('WATCHDOG_EXPIRED', script)
        server = (ROOT / 'mcp/src/sagetv_dev_mcp/server.py').read_text(encoding='utf-8')
        self.assertIn('watchdog_requested_ms', server)
        self.assertIn('watchdog_applied_ms', server)
        self.assertIn('watchdog_verified', server)
        self.assertIn('_require_debug_watchdog_capacity', server)
        self.assertIn('maxRecoveryWatchdogMs', server)

    def test_matrix_does_not_change_player_or_use_sagex_watch(self):
        script = (SCRIPTS / "mcp_media3_comskip_matrix.py").read_text(encoding="utf-8")
        self.assertNotIn("dev_local_seek_absolute", script)
        self.assertIn('"dev_seek_time"', script)
        self.assertIn('parser.add_argument("--start-ms", type=int, default=0', script)
        self.assertNotIn("dev_play_video", script)
        self.assertNotIn("SagexApiClient", script)

    def test_comskip_matrix_passes_requested_start_time_to_debug_seek(self):
        script = (ROOT / "scripts/mcp_media3_comskip_matrix.py").read_text(encoding="utf-8")
        self.assertIn('"target_ms": args.start_ms', script)
        self.assertIn('"stable_ms": args.seek_stable_ms', script)
        self.assertIn('parser.add_argument("--seek-stable-ms", type=int, default=1200', script)
        self.assertIn('mode_result["startSeek"] = start_seek', script)
        self.assertIn('playback did not recover after debug start-time seek', script)


if __name__ == "__main__":
    unittest.main()
