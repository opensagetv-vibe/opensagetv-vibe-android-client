from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]


class DiagnosticUploadGateContracts(unittest.TestCase):
    def test_client_control_cannot_enable_or_serve_reports(self):
        source = (ROOT / 'mcp/src/sagetv_dev_mcp/server.py').read_text()
        method = source.split('def dev_server_diagnostics(', 1)[1].split('@mcp.tool()', 1)[0]
        self.assertIn('("status", "server_upload")', method)
        self.assertIn('diagnostics_action=action', method)
        self.assertNotIn('set_player_config', method)
        self.assertNotIn('urlopen', method)

    def test_explicit_child_command_preserves_real_mcp(self):
        source = (ROOT / 'scripts/mcp_smoke_test.py').read_text()
        self.assertIn('command if command is not None else', source)
        self.assertIn('"-m", "sagetv_dev_mcp.server"', source)
        self.assertNotIn('shell=True', source)

    def test_upload_gate_checks_the_real_text_bundle_and_idle_reconnect(self):
        source=(ROOT/'scripts/mcp_server_diagnostic_upload_test.py').read_text()
        self.assertIn('(?:txt|log|jsonl|sha256)', source)
        self.assertIn('require_player=False', source)
        self.assertIn("'connection=true' in text", source)
        self.assertIn('same connected', (ROOT/'source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/diagnostics/ServerDiagnosticExporter.java').read_text())

    def test_windows_gate_reads_stock_hash_and_never_restarts_or_serves_reports(self):
        source=(ROOT/'scripts/mcp_server_diagnostic_upload_test.py').read_text()
        self.assertIn("report['coreAfter'] == report['coreBefore']", source)
        self.assertIn('paramiko.RejectPolicy()', source)
        self.assertIn('scopeVerified', source)
        self.assertNotIn('Stop-Service', source)
        self.assertNotIn('/download?', source)
        self.assertNotIn('/report?', source)
        self.assertIn("case.get('path') == args.fixture_path", source)

    def test_matched_picture_upload_is_opt_in_and_receiver_restore_is_independent(self):
        source=(ROOT/'scripts/mcp_gpu_deinterlace_test.py').read_text()
        self.assertIn("parser.add_argument('--server-upload',action='store_true'",source)
        self.assertIn('if args.server_upload:',source)
        self.assertIn("expected=original_receiver,value='true',confirm=True",source)
        self.assertIn("expected=current,value=original_receiver,confirm=True",source)
        self.assertIn("result['receiverRestored']",source)
        self.assertIn('server diagnostic receipt was not observed',source)


if __name__ == '__main__':
    unittest.main()
