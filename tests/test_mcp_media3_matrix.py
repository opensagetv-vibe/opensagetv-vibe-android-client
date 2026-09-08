from pathlib import Path
import importlib.util
import sys
import unittest

ROOT = Path(__file__).resolve().parents[1]
SCRIPTS = ROOT / "scripts"
sys.path.insert(0, str(SCRIPTS))
spec = importlib.util.spec_from_file_location("mcp_media3_matrix", SCRIPTS / "mcp_media3_matrix.py")
module = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(module)


class MCPMedia3MatrixTests(unittest.TestCase):
    def test_default_modes_are_push_dynamic_then_pull(self):
        self.assertEqual(module.parse_modes("dynamic,pull"), ["push", "pull"])
        self.assertEqual(module.parse_modes("pull,dynamic,pull"), ["pull", "push"])

    def test_invalid_matrix_mode_is_rejected(self):
        with self.assertRaises(ValueError):
            module.parse_modes("fixed")

    def test_recovery_table_compares_same_checks(self):
        results = {
            "push": {"checks": {
                "single_ff": {"recoveryMs": 500},
                "single_rew": {"recoveryMs": 600},
                "rapid_mixed": {"recoveryMs": 700},
                "pause_resume": {"resumeMs": 900},
            }},
            "pull": {"checks": {
                "single_ff": {"recoveryMs": 1500},
                "single_rew": {"recoveryMs": 1600},
                "rapid_mixed": {"recoveryMs": 1700},
                "pause_resume": {"resumeMs": 1900},
            }},
        }
        self.assertEqual(module.recovery_table(results)["single_ff"], {"push": 500, "pull": 1500})
        self.assertEqual(module.recovery_table(results)["pause_resume"], {"push": 900, "pull": 1900})

    def test_matrix_uses_native_start_stable_ui_real_output_and_fresh_sessions(self):
        script = (SCRIPTS / "mcp_media3_matrix.py").read_text(encoding="utf-8")
        devsh = (ROOT / "dev.sh").read_text(encoding="utf-8")
        self.assertIn('from mcp_playback_test import start_recording_via_search', script)
        self.assertIn("from mcp_ui_roots import wait_automation_root", script)
        self.assertIn("ready = wait_automation_root(", script)
        self.assertIn("stable_ms=ui_stable_ms", script)
        self.assertIn('"player": "media3"', script)
        self.assertIn('"streaming": streaming_preference(mode)', script)
        self.assertIn('"save": False', script)
        self.assertIn('"dev_run_relative_seek_check"', script)
        self.assertIn('deltas_ms=[args.skip_forward_ms, args.skip_forward_ms, -args.skip_backward_ms', script)
        self.assertIn('"dev_prepare_clean_start"', script)
        self.assertIn('start_recording_via_search(client, search_text, text_char_delay_ms=text_char_delay_ms)', script)
        self.assertNotIn('"dev_send_sequence"', script)
        self.assertIn('slowRecovery', script)
        self.assertIn('"dev_player_control"', script)
        self.assertIn('"seekControlPath": "android_debug_direct_player_seek"', script)
        self.assertIn('recoveryComparisonMs', script)
        self.assertIn('mcp-media3-matrix)', devsh)
        self.assertIn('parser.add_argument("--text", required=True', script)
        self.assertNotIn('default="meet the press"', script)

    def test_matrix_does_not_change_player_implementation(self):
        script = (SCRIPTS / "mcp_media3_matrix.py").read_text(encoding="utf-8")
        self.assertNotIn("dev_local_seek_absolute", script)
        self.assertNotIn("dev_play_video", script)
        self.assertNotIn("SagexApiClient", script)


if __name__ == "__main__":
    unittest.main()
