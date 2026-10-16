"""The failed DIAG-011 blanket-flag experiment must not leak into release code."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
VIDEO = ROOT / 'source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video'

class BroadcastAvcStartup(unittest.TestCase):
    def test_stall_timestamp_probe_is_field_only_and_debug_only(self):
        probe = (ROOT / 'source/dev/android-tv/src/debug/java/opensagetv/vibe/miniclient/android/tv/debug/PlaybackHealthProbe.java').read_text(encoding='utf-8')
        for key in ('videoReadPositionUs', 'audioReadPositionUs', 'videoPendingOutputPtsUs', 'audioPendingOutputPtsUs'):
            self.assertIn('long ' + key + ' = -1;', probe)
            self.assertIn('append(out, p + "' + key + '", ' + key + ');', probe)
        self.assertIn('readLongField(renderer, "readingPositionUs", -1)', probe)
        self.assertNotIn('invokeOptional(renderer, "isReady"', probe)

    def test_both_ts_factories_retain_the_qualified_caption_policy(self):
        for name in ('media3/Media3MediaPlayerImpl.java', 'exoplayer2/Exo2MediaPlayerImpl.java'):
            with self.subTest(backend=name):
                text = (VIDEO / name).read_text(encoding='utf-8')
                factory = text.split('private ExtractorsFactory createCaptionAwareExtractorsFactory', 1)[1]
                factory = factory.split('return ', 1)[0]
                self.assertIn('DefaultTsPayloadReaderFactory.FLAG_OVERRIDE_CAPTION_DESCRIPTORS', factory)
                self.assertNotIn('FLAG_ALLOW_NON_IDR_KEYFRAMES', factory)
                self.assertNotIn('FLAG_DETECT_ACCESS_UNITS', factory)
                self.assertIn('setTsExtractorTimestampSearchBytes', factory)

if __name__ == '__main__': unittest.main()
