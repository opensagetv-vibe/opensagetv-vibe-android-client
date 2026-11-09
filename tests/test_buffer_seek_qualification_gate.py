from pathlib import Path
import unittest

SOURCE = (Path(__file__).resolve().parents[1]/'scripts/mcp_buffer_seek_qualification_test.py').read_text()


class BufferSeekQualificationGateTest(unittest.TestCase):
    def test_exact_idle_context_before_watch_and_no_server_mutation(self):
        for token in ("not report['before'].get('connected')", "env.device()['client_id']",
                      'api.resolve_context', "ui.get('media', {}).get('mediaFileId')", "'server.activity'"):
            self.assertIn(token, SOURCE)
        self.assertLess(SOURCE.index('api.resolve_context'), SOURCE.index('api.watch('))
        self.assertNotIn('tune_channel', SOURCE)
        self.assertNotIn('restart_server', SOURCE)

    def test_selected_fixed_is_separate_from_actual_push(self):
        self.assertIn("choices=('dynamic', 'fixed')", SOURCE)
        self.assertIn("current.get('health_pushMode') is True", SOURCE)
        self.assertIn("initial.get('streaming') == args.streaming", SOURCE)
        self.assertIn("'mim_direct_mode': 'off'", SOURCE)
        self.assertIn('no GPU driver', SOURCE)
        self.assertIn("current.get('pushDetailedBufferStats') is (args.streaming != 'fixed' or args.forward_cache_trial)", SOURCE)
        self.assertIn("args.player in ('media3', 'exoplayer')", SOURCE)
        self.assertIn("'trialDisable'", SOURCE)

    def test_does_not_count_natural_progress_or_hide_failed_near_seek(self):
        self.assertIn("+20000", SOURCE)
        self.assertIn("12 if label == 'near-forward'", SOURCE)
        self.assertIn('and witnessed', SOURCE)
        self.assertIn("label in ('far-producer-seek', 'explicit-source-preparation')", SOURCE)
        self.assertIn("all(row['passed'] for row in report['rows'])", SOURCE)
        self.assertEqual(SOURCE.count('api.seek(ctx, target)'), 1)
        for token in ('captionRestore', 'dev_restore_settings', 'powerRestore'):
            self.assertIn(token, SOURCE)

    def test_does_not_confuse_fixed_decoder_epoch_with_server_recording_time(self):
        import ast
        function = next(n for n in ast.parse(SOURCE).body
                        if isinstance(n, ast.FunctionDef) and n.name == 'relative_server_ms')
        scope = {}
        exec(compile(ast.Module(body=[function], type_ignores=[]), '<pure-clock>', 'exec'), scope)
        relative = scope['relative_server_ms']
        self.assertEqual(623000, relative({'mediaTimeMs': 623000, 'seekStartMs': 0}))
        self.assertEqual(33000, relative({'mediaTimeMs': 1700033000, 'seekStartMs': 1700000000}))
        self.assertIn("current['qualificationServerUi']", SOURCE)
        self.assertIn('time.sleep(6)', SOURCE)

    def test_native_fallback_is_not_a_fabricated_exo_render_counter(self):
        import ast
        function = next(n for n in ast.parse(SOURCE).body
                        if isinstance(n, ast.FunctionDef) and n.name == 'output_progressed')
        scope = {}
        exec(compile(ast.Module(body=[function], type_ignores=[]), '<native-output>', 'exec'), scope)
        advanced = scope['output_progressed']
        before = {'health_playerPositionMs': 1000}
        after = {'health_probeSupported': False, 'health_firstVideoFrameRendered': True,
                 'health_basicIsPlaying': True, 'health_playerPositionMs': 1500}
        self.assertTrue(advanced(before, after))
        self.assertFalse(advanced(before, {**after, 'health_firstVideoFrameRendered': False}))
        self.assertFalse(advanced(before, {**after, 'health_basicIsPlaying': False}))
        self.assertFalse(advanced(before, {**after, 'health_probeSupported': True}))
        self.assertIn("'nativeClockAndFirstFrame'", SOURCE)
        self.assertIn('initial = scope()  # Never reuse the pre-preparation bookmark', SOURCE)


if __name__ == '__main__': unittest.main()
