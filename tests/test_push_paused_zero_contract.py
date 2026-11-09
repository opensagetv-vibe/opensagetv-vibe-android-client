from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
VIDEO = ROOT/'source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video'


class PushPausedZeroContractTest(unittest.TestCase):
    def test_both_renderer_engines_match_real_period_and_render_time(self):
        for engine, name in [('media3', 'Media3MediaPlayerImpl'), ('exoplayer2', 'Exo2MediaPlayerImpl')]:
            source = (VIDEO/engine/(name+'.java')).read_text()
            self.assertIn('eventTime.mediaPeriodId.periodUid.equals(timeline.getUidOfPeriod(index))', source)
            self.assertIn('pausedZeroWitness.rendered(renderTimeMs, currentPeriod)', source)
            self.assertIn('pausedZeroWitness.position(zeroGeneration, position)', source)
            self.assertIn('pausedZeroWitness.reset(android.os.SystemClock.elapsedRealtime())', source)
            self.assertIn('return playerReady && pausedZeroWitness.confirmed()', source)

    def test_base_keeps_unknown_playing_pull_and_dvd_guards(self):
        base = (VIDEO/'BaseMediaPlayerImpl.java').read_text()
        self.assertIn('PushTimelineContinuity.acceptsPausedZero(pushMode,', base)
        self.assertIn('state == PAUSE_STATE', base)
        self.assertIn('mt < 0L || (mt == 0L && !confirmedPausedZero)', base)
        self.assertIn('protected boolean hasConfirmedPausedZeroPosition() { return false; }', base)
        policy = (VIDEO/'PushTimelineContinuity.java').read_text()
        self.assertIn('push && !dvd && paused && serverAnchorMs == 0L && nativeTimeMs == 0L', policy)


if __name__ == '__main__': unittest.main()
