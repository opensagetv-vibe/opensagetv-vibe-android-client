"""STOP/FLUSH/PLAY must not depend on an engine still being IDLE."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]


class RestartSurfaceTests(unittest.TestCase):
    def test_shared_play_restores_only_stop_hidden_surface(self):
        source = (ROOT / 'source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/BaseMediaPlayerImpl.java').read_text()
        play = source.split('public void play()', 1)[1].split('public void seek(', 1)[0]
        for expected in ('context.runOnUiThread', 'isCurrentPlaybackSession(playSession)',
                         'state == PLAY_STATE', 'getVisibility() == View.GONE',
                         'context.setupVideoFrame()', 'play_restore_stopped_surface'):
            self.assertIn(expected, play)
        self.assertNotIn('View.INVISIBLE', play)
        self.assertNotIn('player.prepare()', play)
        self.assertNotIn('.seek(', play)

    def test_backend_play_calls_shared_lifecycle_before_start(self):
        base = ROOT / 'source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video'
        for name in ('media3/Media3MediaPlayerImpl.java', 'exoplayer2/Exo2MediaPlayerImpl.java',
                     'ijkplayer/IJKMediaPlayerImpl.java'):
            with self.subTest(backend=name):
                source = (base/name).read_text()
                play = source.split('public void play()', 1)[1].split('\n    @Override', 1)[0]
                self.assertIn('super.play()', play)


if __name__ == '__main__':
    unittest.main()
