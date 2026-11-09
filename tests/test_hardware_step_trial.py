from pathlib import Path
import ast
import unittest

ROOT = Path(__file__).resolve().parents[1]


class HardwareStepTrialTest(unittest.TestCase):
    def test_retained_counter_clock_oracle_does_not_assume_zero_origin(self):
        path = ROOT / 'scripts/mcp_native_paused_step_test.py'
        tree = ast.parse(path.read_text())
        function = next(node for node in tree.body if isinstance(node, ast.FunctionDef)
                        and node.name == 'presented_clock_error')
        namespace = {}
        exec(compile(ast.Module(body=[function], type_ignores=[]), str(path), 'exec'), namespace)
        error = namespace['presented_clock_error']
        before = dict(pushNativeClock='originKnown=false', mediaTimeMs=5544,
                      health_playerPositionMs=8096)
        after = dict(pushNativeClock='originKnown=false', mediaTimeMs=5577,
                     nativeStepPositionMs=8129, pushEffectiveServerAnchorMs=0)
        self.assertEqual(('retained-delta', 0), error(before, after))
        after['mediaTimeMs'] = 5680
        self.assertEqual(('retained-delta', 103), error(before, after))
        before['pushNativeClock'] = after['pushNativeClock'] = 'originKnown=true'
        after.update(mediaTimeMs=8129, nativeStepPositionMs=8129)
        self.assertEqual(('known-origin', 0), error(before, after))

    def test_actual_codec_required_without_expanding_software_fifo_flush(self):
        native = ROOT / 'source/dev/ijkplayer/native'
        header = (native / 'vibe_paused_step.h').read_text()
        self.assertIn('ffp->stat.vdec_type == FFP_PROPV_DECODER_MEDIACODEC', header)
        self.assertIn('if (!software && !hardware) return;', header)
        flush = (native / 'push-flush.patch').read_text()
        self.assertNotIn('vibe_hardware_step', flush)
        player = (ROOT / 'source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/ijkplayer/IJKMediaPlayerImpl.java').read_text()
        self.assertIn('"vibe-push-input", softwarePush ? 1 : 0', player)
        self.assertIn('!rawCeaSupported ? null : new IjkCaptionSession(', player)
        self.assertIn('.trustNormalizedEpoch(hardwareStepTrial,', player)
        self.assertIn('extra == nativeOutputEpoch.get()', player)
        policy = (ROOT / 'source/dev/core/src/main/java/opensagetv/vibe/miniclient/video/NativeHardwareStepTrial.java').read_text()
        self.assertIn('hardwareTrial && !detailed && actualCurrentEpochOutput', policy)

    def test_trial_is_off_by_default_and_only_exposed_in_debug_receiver(self):
        policy = (ROOT / 'source/dev/core/src/main/java/opensagetv/vibe/miniclient/video/NativeHardwareStepTrial.java').read_text()
        self.assertIn('private static volatile boolean enabled;', policy)
        self.assertIn('enabled && push && !dvd', policy)
        self.assertNotIn('PrefStore', policy)
        receiver = ROOT / 'source/dev/android-tv/src/debug/java/opensagetv/vibe/miniclient/android/tv/debug/DevTestReceiver.java'
        self.assertIn('"hardware_step_trial".equals(op)', receiver.read_text())

    def test_physical_gate_requires_real_hardware_and_restores_trial(self):
        gate = (ROOT / 'scripts/mcp_native_paused_step_test.py').read_text()
        self.assertIn("active.get('health_videoDecoderKind', '')", gate)
        self.assertIn("('trialDisable', lambda:", gate)
        self.assertIn('if trial_adb: trial_adb.close()', gate)
        for token in ('nativeStepCompleted', 'nativeStepPositionMs', 'serverFlushSequence',
                      'serverSeekSequence', 'captionRestore', 'powerRestore'):
            self.assertIn(token, gate)

    def test_replacement_gate_requires_new_video_epoch_not_just_a_seek_ack(self):
        gate = (ROOT / 'scripts/mcp_native_paused_step_test.py').read_text()
        for token in ('args.exercise_seek_epoch', 'api.seek(ctx, 150000)',
                      "'presented=true'", "'No actual video proof for replacement native epoch'",
                      "step_one(index, 'replacement')", "'secondResume'"):
            self.assertIn(token, gate)
        self.assertEqual(1, gate.count('api.seek(ctx, 150000)'))
        self.assertIn("int(ui.get('seekStartMs', 0))", gate)
