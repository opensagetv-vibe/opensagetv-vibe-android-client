import pathlib
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[1]
BASE = ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/BaseMediaPlayerImpl.java"
CONTROLLER = ROOT / "source/dev/core/src/main/java/opensagetv/vibe/miniclient/video/PlaybackSessionController.java"
BACKENDS = (
    ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/media3/Media3MediaPlayerImpl.java",
    ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/exoplayer2/Exo2MediaPlayerImpl.java",
)


class PlaybackSessionControllerTests(unittest.TestCase):
    def test_controller_serializes_generations_and_operations(self):
        source = CONTROLLER.read_text(encoding="utf-8")
        self.assertIn("synchronized Token beginSession()", source)
        self.assertIn("synchronized Token beginOperation", source)
        self.assertIn("synchronized Token endSession", source)
        for operation in (
            "LOAD", "SEEK", "PLAY", "PAUSE", "FLUSH", "RECONNECT",
            "INACTIVE_FILE", "SURFACE_REPLACEMENT", "RECOVERY", "STOP", "FREE",
        ):
            self.assertIn(operation, source)

    def test_base_rejects_stale_queued_load_and_fullscreen_work(self):
        source = BASE.read_text(encoding="utf-8")
        self.assertIn("playbackSessions.beginSession()", source)
        self.assertIn("Ignoring stale queued load", source)
        self.assertIn("PlaybackSessionController.Operation.STOP", source)
        self.assertIn("PlaybackSessionController.Operation.FREE", source)
        self.assertIn("PlaybackSessionController.Operation.INACTIVE_FILE", source)
        self.assertIn("PlaybackSessionController.Operation.SURFACE_REPLACEMENT", source)
        self.assertIn("Ignoring stale surface update", source)
        self.assertIn("!playbackSessions.isCurrentSession(session)", source)

    def test_exo_backends_guard_listeners_seek_recovery_and_progress(self):
        for path in BACKENDS:
            source = path.read_text(encoding="utf-8")
            guard = "!isCurrentPlaybackSession(listenerSession) || player != listenerPlayer"
            self.assertGreaterEqual(source.count(guard), 8, path.name)
            self.assertIn("Ignoring stale queued seek for a replaced playback session", source)
            self.assertIn("!isCurrentPlaybackSession(session)", source)
            self.assertIn("progressHandler.postDelayed(sessionProgress[0], 500)", source)
            self.assertNotIn("progressHandler.postDelayed(progressRunnable, 500)", source)
            self.assertIn("progressHandler.removeCallbacks(progressRunnable)", source)
            self.assertIn("player != pausePlayer", source)
            self.assertIn("player != playPlayer", source)
            self.assertIn("player != flushPlayer", source)
            self.assertIn("PlaybackSessionController.Operation.RECOVERY", source)


if __name__ == "__main__":
    unittest.main()
