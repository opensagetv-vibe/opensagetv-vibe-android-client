from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]


class PushSeekDeliveryTests(unittest.TestCase):
    def test_gate_does_not_interrupt_connected_viewers_or_select_another_context(self):
        source = (ROOT / 'scripts/mcp_push_seek_delivery_test.py').read_text()
        self.assertLess(source.index("if before.get('connected')"),
                        source.index("'dev_checkpoint_settings'"))
        self.assertIn("before.get('clientId') != env.device().get('client_id')", source)
        self.assertIn("core.resolve_context(before['clientId'])", source)
        self.assertIn("not state.get('health_pushMode')", source)
        self.assertIn("int(ui['media']['mediaFileId']) != args.media_file_id", source)

    def test_gate_requires_new_epoch_frame_and_ready_and_never_replays_a_seek(self):
        source = (ROOT / 'scripts/mcp_push_seek_delivery_test.py').read_text()
        self.assertIn("int(e.get('sequence', 0)) > sequence", source)
        self.assertIn("('server_flush_command', 'first_video_frame', 'playback_state_3')", source)
        self.assertEqual(1, source.count('core.seek(context, target)'))
        self.assertIn("row['landingErrorMs']", source)
        self.assertIn("if args.output.exists()", source)

    def test_settings_and_power_restoration_are_independent_of_exit_failure(self):
        source = (ROOT / 'scripts/mcp_push_seek_delivery_test.py').read_text()
        cleanup = source.split('    finally:', 1)[1]
        self.assertLess(cleanup.index("result['exitFailure']"),
                        cleanup.index("'dev_restore_settings'"))
        self.assertIn("result['restorePower'] = end(", cleanup)
        self.assertIn('client.close()', cleanup)
        self.assertNotIn('clear_data', source)

    def test_active_tests_are_explicit_same_server_and_restore_without_an_external_watch_override(self):
        source = (ROOT / 'scripts/mcp_push_seek_delivery_test.py').read_text()
        self.assertIn("before.get('connected') and not args.current_playback", source)
        self.assertIn("before.get('serverAddress') != server['address']", source)
        self.assertIn('args.switch_recording and not args.current_playback', source)
        self.assertIn("Source changed externally; never overwrite another playback", source)
        self.assertIn("'media_file_id': int(original_ui['media']['mediaFileId'])", source)
        self.assertIn("result['restoreSeek'] = core.seek(context, int(before['mediaTimeMs']))", source)
        self.assertIn("tool_call(client, 'firetv_key', {'key': key})", source)
        self.assertIn('if args.player and args.current_playback:', source)


if __name__ == '__main__':
    unittest.main()
