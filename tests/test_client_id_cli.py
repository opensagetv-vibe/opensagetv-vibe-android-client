from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]


class ClientIdCliTests(unittest.TestCase):
    def test_debug_receiver_exposes_client_id_get_set_and_dynamic_snapshot(self):
        receiver = (ROOT / "source/dev/android-tv/src/debug/java/opensagetv/vibe/miniclient/android/tv/debug/DevTestReceiver.java").read_text(encoding="utf-8")
        client_commands = (ROOT / "source/dev/android-tv/src/debug/java/opensagetv/vibe/miniclient/android/tv/debug/DebugClientIdCommands.java").read_text(encoding="utf-8")
        state_provider = (ROOT / "source/dev/android-tv/src/debug/java/opensagetv/vibe/miniclient/android/tv/debug/DebugStateProvider.java").read_text(encoding="utf-8")
        self.assertIn('"client_id_get".equals(op)', receiver)
        self.assertIn('"client_id_set".equals(op)', receiver)
        self.assertIn('DebugClientIdCommands.status(context)', receiver)
        self.assertIn('DebugClientIdCommands.configure(context, intent)', receiver)
        self.assertIn('prefs.setString(PrefStore.Keys.client_id, id);', client_commands)
        self.assertIn('client.getCurrentConnection().getClientID()', client_commands)
        self.assertIn('out.append(";configuredClientId=")', state_provider)
        self.assertIn('out.append(";debugStatusVersion=21")', state_provider)
        self.assertIn('DebugStateProvider.snapshot(context, MAX_RECOVERY_WATCHDOG_MS)', receiver)
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
        self.assertIn('configured_automated_client_id()', dev)
        self.assertIn('identity.automated_client_id', dev)
        self.assertIn('run_automated_mcp_test()', dev)
        self.assertIn('--client-id)', dev)
        self.assertRegex(dev, r'mcp_client_id\.py"? --ensure "\$client_id" --quiet')
        for script in (
            "mcp_smoke_test.py", "mcp_seek_suite.py", "mcp_search_test.py",
            "mcp_comskip_test.py", "mcp_playback_test.py", "mcp_media3_matrix.py",
            "mcp_media3_comskip_matrix.py", "mcp_player_matrix.py",
            "mcp_player_tuning_matrix.py", "mcp_session_test.py",
        ):
            self.assertIn(f'run_automated_mcp_test {script} "$@"', dev)

    def test_scripted_launch_defaults_to_deterministic_id(self):
        dev = (ROOT / "dev.sh").read_text(encoding="utf-8")
        combined = (ROOT / "update.sh").read_text(encoding="utf-8")
        self.assertIn('run_scripted_launch()', dev)
        self.assertIn('run_scripted_launch "$@"', dev)
        self.assertIn('SCRIPTED LAUNCH CLIENT ID', dev)
        self.assertRegex(dev, r'mcp_client_id\.py"? --ensure "\$client_id" --quiet')
        self.assertIn('SCRIPTED_CLIENT_ID="44:45:56:30:30:31"', combined)
        self.assertIn('./dev.sh launch --client-id "$SCRIPTED_CLIENT_ID"', combined)

    def test_docs_require_first_time_setup_before_automation(self):
        readme = (ROOT / "README.md").read_text(encoding="utf-8")
        diagnostics = (ROOT / "docs" / "PLAYBACK_DIAGNOSTICS.md").read_text(encoding="utf-8")
        self.assertIn('first-time setup', readme.lower())
        self.assertIn('before any automated MCP/player test', readme)
        self.assertIn('Complete first-time setup manually', diagnostics)


if __name__ == "__main__":
    unittest.main()
