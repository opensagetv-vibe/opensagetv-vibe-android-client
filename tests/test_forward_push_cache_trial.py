from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT/'source/dev/android-shared/src/main/java/androidx/media3/exoplayer/source'


class ForwardPushCacheTrialTest(unittest.TestCase):
    def test_loader_eof_does_not_erase_the_unread_queue_window(self):
        period = (SOURCE/'VibeForwardPushMediaPeriod.java').read_text()
        buffered = period.split('public long getBufferedPositionUs()',1)[1].split('@Override',1)[0]
        self.assertNotIn('if (loadingFinished ||', buffered)
        self.assertNotIn('!sampleQueues[i].isLastSampleQueued()', buffered)
        self.assertLess(buffered.index('forwardReady = prepared'),
                        buffered.index('if (loadingFinished) return C.TIME_END_OF_SOURCE;'))

    def test_only_real_queue_transaction_and_no_byte_seek_map_invention(self):
        period = (SOURCE/'VibeForwardPushMediaPeriod.java').read_text()
        block = period.split('public long seekToUs(long positionUs)', 1)[1].split('@Override', 1)[0]
        self.assertIn('ForwardCacheSeekTransaction.seek(queues, positionUs)', block)
        self.assertIn('sampleQueues[track].seekTo(targetUs, false)', block)
        for forbidden in ('pendingResetPositionUs =', 'loader.cancelLoading()',
                          'sampleQueues[i].reset()', 'seekMap.isSeekable() ? positionUs : 0'):
            self.assertNotIn(forbidden, block)
        self.assertNotIn('return true; // seekable', period)
        self.assertIn('Copyright (C) 2016 The Android Open Source Project', period)
        self.assertIn('6c94579591091bbf00e2571a5163ae197bf30ef99e012e488f98fb68a6347257', period)

    def test_trial_control_is_debug_only_not_a_preference_or_advertisement(self):
        receiver = (ROOT/'source/dev/android-tv/src/debug/java/opensagetv/vibe/miniclient/android/tv/debug/DevTestReceiver.java').read_text()
        self.assertIn('"push_cache_trial".equals(op)', receiver)
        self.assertIn('setDebugForwardCacheTrial(enabled)', receiver)
        policy = (ROOT/'source/dev/core/src/main/java/opensagetv/vibe/miniclient/video/PushBufferSeekPolicy.java').read_text()
        block = policy.split('boolean shouldAdvertise(', 1)[1].split('}', 1)[0]
        self.assertIn('return false;', block)
        self.assertIn('private static volatile boolean debugForwardCacheTrial;', policy)

    def test_default_qualified_source_is_detailed_push_only(self):
        backend = (ROOT/'source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/media3/Media3MediaPlayerImpl.java').read_text()
        factory = backend.split('private MediaSource createOrdinaryPushSource(',1)[1].split('@Override',1)[0]
        self.assertIn('useForwardCacheSource(', factory)
        self.assertIn('MiniClientConnection.detailedBufferStats', factory)
        self.assertIn(': new ProgressiveMediaSource.Factory(', factory)
        self.assertIn('pushMode && !dvdPushMode', backend.split('public boolean canHandleServerSeek()',1)[1].split('public String',1)[0])

    def test_positive_gate_cannot_count_producer_flush_as_cache_success(self):
        gate = (ROOT/'scripts/mcp_buffer_seek_qualification_test.py').read_text()
        for token in ('trialDisable', 'trialCaptionsOff', "near_result['command29Delta'] > 0",
                      "near_result['producerFlushDelta'] == 0", 'trial_adb.close()'):
            self.assertIn(token, gate)
        self.assertIn("args.player == 'gsyplayer' and args.gsy_engine in ('media3', 'legacy_exo')", gate)
        self.assertIn("report['playingCacheRejections']", gate)
        self.assertIn("report['pausedCacheRejections']", gate)


if __name__ == '__main__': unittest.main()
