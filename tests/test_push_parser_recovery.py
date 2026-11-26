"""Guard bounded, source-owned MPEG FIFO recovery in both Exo backends."""
import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
VIDEO = ROOT / 'source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video'


class PushParserRecoveryTests(unittest.TestCase):
    def test_both_backends_reattach_validated_container_without_local_seek(self):
        for package, backend in [('exoplayer2', 'Exo2'), ('media3', 'Media3')]:
            with self.subTest(backend=backend):
                source = (VIDEO / package / (backend + 'MediaPlayerImpl.java')).read_text()
                self.assertTrue('ordinaryPushRecoveryExtractors.beginRecovery()' in source, backend)
                branch = source.split('ordinaryPushRecoveryExtractors.beginRecovery()', 1)[1].split('else', 1)[0]
                self.assertIn('pushRecoveryClock.restartDecoder(recoveryPositionMs)', branch)
                self.assertIn('currentPlaybackPosition = 0L;', branch)
                self.assertIn('resetLegacyCaptionsForDiscontinuity();', branch)
                self.assertIn('setMediaSource(mediaSource, 0L)', branch)
                self.assertIn('setPlayWhenReady(resumePlayback)', branch)
                self.assertNotIn('seekTo(', branch)
                self.assertIn('pushMode && !dvdPushMode', source.split('ordinaryPushRecoveryExtractors.beginRecovery()', 1)[0][-350:])
                self.assertGreaterEqual(source.count('pushRecoveryClock.reset();'), 2)
                self.assertIn('pushRecoveryClock.position(position)', source)
                self.assertIn('pushRecoveryClock.decoderTarget(timeInMS)', source)
                self.assertIn('active.seekTo(decoderTargetMs)', source)

    def test_factory_is_per_source_and_outside_caption_tap(self):
        for package, backend in [('exoplayer2', 'Exo2'), ('media3', 'Media3')]:
            source = (VIDEO / package / (backend + 'MediaPlayerImpl.java')).read_text()
            factory = source.split('private ExtractorsFactory createCaptionAwareExtractorsFactory(boolean pullMode)', 1)[1].split('private ', 1)[0]
            self.assertIn('if (!pullMode)', factory)
            self.assertIn('new PushRecoveryExtractorsFactory(', factory)
            self.assertIn('new LegacyCaptionExtractorsFactory(', factory)

    def test_implementations_stay_mirrored(self):
        legacy = (VIDEO / 'exoplayer2/PushRecoveryExtractorsFactory.java').read_text()
        modern = (VIDEO / 'media3/PushRecoveryExtractorsFactory.java').read_text()
        self.assertEqual(legacy.replace('.exoplayer2;', '.media3;').replace('com.google.android.exoplayer2', 'androidx.media3'), modern)

    def test_fault_injection_is_debug_only_and_refuses_other_transports(self):
        debug = ROOT / 'source/dev/android-tv/src/debug/java/opensagetv/vibe/miniclient/android/tv/debug/PushParserRecoveryFault.java'
        source = debug.read_text()
        self.assertIn('"pushMode"', source)
        self.assertIn('"dvdPushMode"', source)
        self.assertEqual(2, source.count('createMessage('))
        self.assertEqual(2, source.count('ExoPlaybackException.createForSource('))
        self.assertFalse(list((ROOT / 'source/dev/android-tv/src/main').rglob('PushParserRecoveryFault.java')))

    def test_physical_gate_waits_for_new_ui_and_checks_clock_not_only_output(self):
        source = (ROOT / 'scripts/mcp_push_parser_recovery_test.py').read_text()
        self.assertLess(source.index("row['uiReady'] = wait_automation_root"), source.index("row['watch'] ="))
        self.assertIn('Server FLUSH concealed client-only parser recovery', source)
        self.assertIn('Same-stream recovery reset the server-visible presentation clock', source)
        self.assertIn('Paused recovery moved/reset the presentation clock', source)
        self.assertIn("call_dict(client, 'dev_restore_settings')", source)
        self.assertIn("end(serial, state_path(serial), 'automated')", source)


if __name__ == '__main__':
    unittest.main()
