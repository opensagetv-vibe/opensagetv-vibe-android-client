"""Keep the GROW-001 physical test scoped, truthful and production-neutral."""
import ast
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
SOURCE = (ROOT/'scripts/mcp_growing_rewind_test.py').read_text(encoding='utf-8')


class GrowingRewindTests(unittest.TestCase):
    def test_exact_idle_device_and_active_recording_required(self):
        for text in ("'192.168.10.25:5555'", "'192.168.10.175'",
                     "not r['before'].get('connected')", "api.resolve_context(identity['clientId'])",
                     "r.get('IsFileCurrentlyRecording') is True", "row.get('NumberOfSegments', 0)"):
            self.assertIn(text, SOURCE)
        self.assertLess(SOURCE.index("r['recordingBefore'] = recording_witness"),
                        SOURCE.index("'dev_checkpoint_settings'"))

    def test_never_tunes_or_changes_recording_schedule(self):
        tree = ast.parse(SOURCE)
        forbidden = {'tune_channel', 'record', 'cancel_recording', 'remote_command'}
        for node in ast.walk(tree):
            if isinstance(node, ast.Call) and isinstance(node.func, ast.Attribute):
                self.assertNotIn(node.func.attr, forbidden)
        self.assertIn("int(r['recordingAfter']['Size']) > int(r['recordingBefore']['Size'])", SOURCE)

    def test_actual_transport_and_output_not_ack_is_gate(self):
        self.assertIn("s.get('health_pushMode') is (args.streaming == 'push')", SOURCE)
        self.assertIn("seek_once(0)", SOURCE)
        self.assertIn("('health_videoRendered', 'health_audioRendered')", SOURCE)
        # Execute only this pure function; no ADB or server imports in unit tests.
        function = next(n for n in ast.parse(SOURCE).body
                        if isinstance(n, ast.FunctionDef) and n.name == 'landed')
        ns = {}
        exec(compile(ast.Module(body=[function], type_ignores=[]), '<landed>', 'exec'), ns)
        s = {'health_isPlaying': True, 'health_surfaceValid': True,
             'health_pushMode': True, 'mediaTimeMs': 2000,
             'health_playerPositionMs': 1500000,
             'health_videoRendered': 10, 'health_audioRendered': 20}
        self.assertTrue(ns['landed'](s, 0, 2000))
        s['health_pushMode'] = False
        self.assertFalse(ns['landed'](s, 0, 2000))
        s['health_playerPositionMs'] = 2000
        self.assertTrue(ns['landed'](s, 0, 2000))
        s['health_surfaceValid'] = False
        self.assertFalse(ns['landed'](s, 0, 2000))
        s['health_surfaceValid'] = True
        s.pop('health_playerPositionMs')
        self.assertFalse(ns['landed'](s, 0, 2000))

    def test_cleanup_always_restores_private_state_and_never_cancels_writer(self):
        tail = SOURCE.split('    finally:', 1)[1]
        for text in ('set_closed_caption_state', 'dev_exit_session', 'dev_restore_settings',
                     'powerRestore', 'c.close()'):
            self.assertIn(text, tail)
        self.assertNotIn('cancel', tail)

    def test_pro_update_must_verify_restoration_not_only_accept_seek(self):
        helper = (ROOT/'scripts/mcp_push_transport_diagnostics_test.py').read_text(encoding='utf-8')
        for text in ("core.current_media_file_id(context)", "report['restoreObservation']",
                     "'health_videoRendered', 'health_audioRendered'",
                     "last.get('audioOffsetMs') == original.get('audioOffsetMs')",
                     "report['restoredPlayback'] = True"):
            self.assertIn(text, helper)


if __name__ == '__main__':
    unittest.main()
