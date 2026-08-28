from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]


class ClientIdCliTests(unittest.TestCase):
    def test_debug_receiver_exposes_client_id_get_set_and_dynamic_snapshot(self):
        receiver = (ROOT / "source/dev/android-tv/src/debug/java/sagex/miniclient/android/tv/debug/DevTestReceiver.java").read_text(encoding="utf-8")
        self.assertIn('"client_id_get".equals(op)', receiver)
        self.assertIn('"client_id_set".equals(op)', receiver)
        self.assertIn('prefs.setString(PrefStore.Keys.client_id, id);', receiver)
        self.assertIn('client.getCurrentConnection().getClientID()', receiver)
        self.assertIn('out.append(";configuredClientId=")', receiver)
        self.assertIn('out.append(";debugStatusVersion=14")', receiver)
        self.assertNotIn('DEV_FIXED_CLIENT_ID', receiver)

    def test_mcp_and_cli_expose_client_id_override(self):
        adb = (ROOT / "mcp/src/sagetv_dev_mcp/adb.py").read_text(encoding="utf-8")
        server = (ROOT / "mcp/src/sagetv_dev_mcp/server.py").read_text(encoding="utf-8")
        dev = (ROOT / "dev.sh").read_text(encoding="utf-8")
        cli = (ROOT / "scripts/mcp_client_id.py").read_text(encoding="utf-8")
        self.assertIn('def get_client_id(', adb)
        self.assertIn('def set_client_id(', adb)
        self.assertIn('def dev_client_id()', server)
        self.assertIn('def dev_set_client_id(', server)
        self.assertIn('client-id)', dev)
        self.assertIn('--set', cli)
        self.assertIn('--generate', cli)
        self.assertIn('--ensure', cli)
        self.assertIn('--quiet', cli)
        self.assertIn('DEV001', cli)
        self.assertIn('44:45:56:30:30:31', cli)
        self.assertIn('version < 14', cli)

    def test_automated_tests_default_to_deterministic_id(self):
        dev = (ROOT / "dev.sh").read_text(encoding="utf-8")
        self.assertIn('DEFAULT_AUTOMATED_TEST_CLIENT_ID="44:45:56:30:30:31"', dev)
        self.assertIn('run_automated_mcp_test()', dev)
        self.assertIn('--client-id)', dev)
        self.assertIn('mcp_client_id.py --ensure "$client_id" --quiet', dev)
        for script in (
            "mcp_smoke_test.py", "mcp_seek_suite.py", "mcp_search_test.py",
            "mcp_comskip_test.py", "mcp_playback_test.py", "mcp_media3_matrix.py",
            "mcp_media3_comskip_matrix.py", "mcp_player_matrix.py",
            "mcp_player_tuning_matrix.py", "mcp_session_test.py",
        ):
            self.assertIn(f'run_automated_mcp_test {script} "$@"', dev)

    def test_docs_require_first_time_setup_before_automation(self):
        readme = (ROOT / "README.md").read_text(encoding="utf-8")
        task = (ROOT / "TASK_CODEX.md").read_text(encoding="utf-8")
        self.assertIn('first-time setup', readme.lower())
        self.assertIn('before any automated MCP/player test', readme)
        self.assertIn('FIRST-TIME SETUP', task)


if __name__ == "__main__":
    unittest.main()
