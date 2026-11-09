"""Focused GROW-002 production wiring and reproduction safeguards."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
CORE = ROOT/'source/dev/core/src/main/java/opensagetv/vibe/miniclient'


class PushStreamHandoffContracts(unittest.TestCase):
    def test_queue_trial_is_scoped_and_always_disabled(self):
        source = (ROOT/'scripts/mcp_push_handoff_test.py').read_text()
        self.assertIn("args.task_id == 'EXT-010'", source)
        self.assertIn("args.player in ('media3', 'exoplayer')", source)
        self.assertLess(source.index("'trialEnable'"), source.index("r['connect']"))
        self.assertIn("'trialDisable'", source.split('    finally:', 1)[1])
        self.assertIn('trial_adb.close()', source)

    def test_late_physical_failure_clears_provisional_pass(self):
        source = (ROOT/'scripts/mcp_push_handoff_test.py').read_text()
        failure = source.split('    except Exception as exc:', 1)[1]
        self.assertLess(failure.index("r['passed'] = False"), failure.index("r['failure'] = str(exc)"))

    def test_local_reset_precedes_new_epoch_calibration_and_payload(self):
        source = (CORE/'MediaCmd.java').read_text()
        branch = source[source.index('long previousMuxMs ='):source.index('// SageTV performs its bandwidth estimate')]
        for first, second in [('playa.flush();', 'resetPushTimelineAnchor();'),
                              ('resetPushTimelineAnchor();', 'pushTimelineAnchorEstimator.observe(')]:
            self.assertLess(branch.index(first), branch.index(second))
        self.assertIn('pushMode && !dvdSessionPending', branch)
        self.assertNotIn('playa.seek(', branch)
        self.assertNotIn('closeConnection(', branch)

    def test_explicit_boundary_resets_handoff_observer(self):
        source = (CORE/'MediaCmd.java').read_text()
        reset = source[source.index('private void resetPushTimelineAnchor()'):]
        self.assertIn('pushStreamHandoff.reset();', reset.split('private static long monotonicMs()', 1)[0])

    def test_ijk_native_epoch_is_reset_without_a_free_running_timer(self):
        ijk = (ROOT/'source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/ijkplayer/IJKMediaPlayerImpl.java').read_text()
        self.assertIn('pushPresentationClock.position(clockGeneration, time, serverStartTime,', ijk)
        self.assertIn('long observedAtMs = android.os.SystemClock.elapsedRealtime()', ijk)
        self.assertIn('pushPresentationClock.begin(', ijk)
        self.assertIn('pushPresentationClock.clear();', ijk)
        clock = (CORE/'media/PushPresentationClock.java').read_text()
        self.assertIn('presentedMs = nativeOriginKnown ? nativePositionMs : saturatingAdd(presentedMs, delta);', clock)
        self.assertIn('monotonicMs < lastSampleMs', clock)
        self.assertIn('delta <= saturatingAdd(elapsed, 2_000L)', clock)
        self.assertNotIn('presentedMs += elapsed', clock)
        self.assertNotIn('Thread.sleep', clock)
        self.assertNotIn('Timer', clock)

    def test_reproduction_uses_normal_watch_without_manufacturing_a_zero_seek(self):
        source = (ROOT/'scripts/mcp_push_handoff_test.py').read_text()
        observation = source.split("r['newWatch'] =", 1)[1].split('except Exception as exc:', 1)[0]
        self.assertIn('api.watch(ctx, args.new_id)', observation)
        self.assertNotIn('api.seek(', observation)
        self.assertNotIn('from_beginning=True', observation)
        self.assertIn('dev_restore_settings', source)
        self.assertIn("api.resolve_context(identity['clientId'])", source)
        self.assertIn('Core fastLoad requires contiguous TV recordings', source)
        self.assertIn('FULL_RELOAD_NOT_IMPLICIT_HANDOFF', source)


if __name__ == '__main__':
    unittest.main()
