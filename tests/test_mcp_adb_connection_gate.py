"""The connection gate exercises MCP, never direct SDK playback controls."""
import importlib.util
from pathlib import Path
import sys
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT/'scripts'))
spec = importlib.util.spec_from_file_location('adb_connection_gate', ROOT/'scripts/mcp_adb_connection_test.py')
gate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gate)


class ConnectionGateTests(unittest.TestCase):
    def test_real_mcp_structured_reply_is_preserved(self):
        reply = {'target': 'commissioned', 'persistentShell': True}
        with patch.object(gate, 'tool_call', return_value={'structuredContent': reply}) as call:
            self.assertIs(reply, gate.structured(object(), 'adb_session_status'))
            self.assertEqual('adb_session_status', call.call_args.args[1])

    def test_older_text_reply_is_parsed_without_direct_sdk_fallback(self):
        with patch.object(gate, 'tool_call', return_value={'content': [
                {'type': 'text', 'text': '{"target":"commissioned"}'}]}):
            self.assertEqual({'target': 'commissioned'}, gate.structured(object(), 'adb_connect'))

    def test_missing_response_cannot_be_counted_as_pass(self):
        with patch.object(gate, 'tool_call', return_value={'content': [{'type': 'text', 'text': 'bad'}]}):
            with self.assertRaisesRegex(RuntimeError, 'missing structured reply'):
                gate.structured(object(), 'adb_connect')

    def test_gate_is_finite_and_closes_its_child_without_playback(self):
        text = (ROOT/'scripts/mcp_adb_connection_test.py').read_text(encoding='utf-8')
        self.assertIn('1 <= args.iterations <= 10', text)
        self.assertIn('finally:\n                client.close()', text)
        self.assertIn("structured(client, 'adb_connect', timeout=120)", text)
        self.assertNotIn('DirectSdkTools', text)
        self.assertNotIn('dev_play_server_path', text)
        self.assertNotIn('kill-server', text)
        self.assertNotIn('dev_set_player_config', text)


if __name__ == '__main__': unittest.main()
