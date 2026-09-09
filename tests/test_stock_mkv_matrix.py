from argparse import Namespace
import importlib.util
from pathlib import Path
import sys
import unittest


ROOT = Path(__file__).resolve().parents[1]
SCRIPTS = ROOT / "scripts"
MCP_SRC = ROOT / "mcp" / "src"
sys.path.insert(0, str(SCRIPTS))
sys.path.insert(0, str(MCP_SRC))
spec = importlib.util.spec_from_file_location("mcp_stock_mkv_matrix", SCRIPTS / "mcp_stock_mkv_matrix.py")
module = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = module
assert spec.loader is not None
spec.loader.exec_module(module)


def passing_report():
    checks = {
        name: {"status": "RECOVERED"}
        for name in ("absolute_seek", "seek_forward", "seek_backward", "pause_resume")
    }
    return {
        "infrastructureFailures": 0,
        "startupMediaFailures": 0,
        "results": {
            "media3__pull__hardware": {
                "startupStatus": "RECOVERED",
                "executionComplete": True,
                "requestedChecks": list(checks),
                "checks": checks,
                "startup": {"state": {
                    "health_backendClass": "Media3MediaPlayerImpl",
                    "health_videoDecoderKind": "hardware",
                }},
            }
        },
    }


class StockMkvMatrixTests(unittest.TestCase):
    def test_default_matrix_covers_all_players_and_runs_system_last(self):
        self.assertEqual(module.DEFAULT_PLAYERS, "exoplayer,media3,ijkplayer,gsyplayer")
        self.assertEqual(module.DEFAULT_GSY_ENGINES.split(",")[-1], "system")
        self.assertNotIn("comskip", module.DEFAULT_CHECKS)

    def test_child_matrix_is_hardware_pull_and_strict_operations(self):
        args = Namespace(
            server="192.0.2.20", port=31099, players=module.DEFAULT_PLAYERS,
            gsy_engines=module.DEFAULT_GSY_ENGINES, checks=module.DEFAULT_CHECKS,
            start_ms=60000, skip_forward_ms=30000, skip_backward_ms=10000,
            watchdog_ms=45000, slow_recovery_ms=5000, playback_timeout_s=60.0,
            verify_playback_ms=3000, text_char_delay_ms=0,
        )
        command = module.build_child_command(args, "Example MKV", Path("child.json"))
        self.assertIn("--hardware-only", command)
        self.assertEqual(command[command.index("--streaming") + 1], "pull")
        self.assertEqual(command[command.index("--video-name") + 1], "Example MKV")
        self.assertNotIn("--text", command)

    def test_evaluation_accepts_complete_recovery_and_preserves_backend_evidence(self):
        result = module.evaluate_report(passing_report())
        self.assertTrue(result["passed"])
        case = result["cases"]["media3__pull__hardware"]
        self.assertEqual(case["effectiveBackendClass"], "Media3MediaPlayerImpl")
        self.assertEqual(case["videoDecoderKind"], "hardware")

    def test_evaluation_rejects_media_failure_even_when_exploratory_matrix_exits_zero(self):
        report = passing_report()
        report["results"]["media3__pull__hardware"]["checks"]["seek_forward"]["status"] = "MEDIA_NOT_RECOVERED"
        result = module.evaluate_report(report)
        self.assertFalse(result["passed"])
        self.assertIn("media3__pull__hardware/seek_forward: MEDIA_NOT_RECOVERED", result["issues"])

    def test_workflow_and_example_configuration_expose_stock_mkv_gate(self):
        dev = (ROOT / "dev.sh").read_text(encoding="utf-8")
        example = (ROOT / "config" / "firetv.example.toml").read_text(encoding="utf-8")
        self.assertIn("mcp-stock-mkv-matrix)", dev)
        self.assertIn("mcp_stock_mkv_matrix.py", dev)
        self.assertIn("mkv_searches = [", example)


if __name__ == "__main__":
    unittest.main()
