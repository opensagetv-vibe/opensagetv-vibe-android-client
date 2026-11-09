"""Finite EXT-011 control gate must preserve user/server ownership."""
from pathlib import Path
import unittest

SOURCE = (Path(__file__).resolve().parents[1]/'scripts/mcp_server_playback_controls_test.py').read_text()


class ServerPlaybackControlsContractTest(unittest.TestCase):
    def test_selected_idle_identity_precedes_mutation(self):
        self.assertIn("not r['before'].get('connected')", SOURCE)
        self.assertIn("api.resolve_context(identity['clientId'])", SOURCE)
        self.assertLess(SOURCE.index('Explicit existing long recording'), SOURCE.index("r['awake'] = begin"))
        self.assertIn('Actual exact-source Pull required', SOURCE)

    def test_failure_and_restoration_cannot_be_counted_as_pass(self):
        failure = SOURCE.split('    except Exception as exc:', 1)[1]
        self.assertIn("r['passed'] = False", failure)
        self.assertIn("'captionRestore'", failure)
        self.assertIn("'restore'", failure)
        self.assertIn("'powerRestore'", failure)
        self.assertIn('Settings restoration failed', failure)

    def test_no_tuner_reconnect_or_guessed_seek_to_manufacture_rate_pass(self):
        self.assertEqual(SOURCE.count('api.watch(ctx, args.media_file_id)'), 1)
        self.assertEqual(SOURCE.count('api.seek('), 1)
        self.assertIn("r['writerPreparation'] = api.seek(ctx, writer_target)", SOURCE)
        self.assertNotIn('tune_channel', SOURCE)
        self.assertNotIn('from_beginning', SOURCE)
        self.assertIn('EXT-011', SOURCE)

    def test_imported_control_does_not_weaken_production_live_guards(self):
        self.assertIn("choices=('T', 'V')", SOURCE)
        self.assertIn("'GetMediaFiles', args.media_type", SOURCE)
        self.assertIn("measure('native2x', 10)", SOURCE)
        self.assertIn('Existing bookmark must leave forward scan room', SOURCE)

    def test_growing_negative_requires_independent_writer_and_open_ended_loader(self):
        self.assertIn("'GetCurrentlyRecordingMediaFiles'", SOURCE)
        self.assertIn("row.get('IsFileCurrentlyRecording') is True", SOURCE)
        self.assertIn("int(writers[0]['FileDuration'])-20000", SOURCE)
        self.assertIn("int(later_writers[0]['Size']) > int(writers[0]['Size'])", SOURCE)
        self.assertIn("initial.get('health_playerReady') is True", SOURCE)
        self.assertIn("result.get('accepted') is False", SOURCE)


if __name__ == '__main__':
    unittest.main()
