from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
SHARED = ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video"
DEBUG = ROOT / "source/dev/android-tv/src/debug/java/opensagetv/vibe/miniclient/android/tv/debug"


class BackendNeutralPlayerComponentTests(unittest.TestCase):
    def test_seek_policy_is_shared_by_media3_and_legacy_exo(self):
        policy = (ROOT / "source/dev/core/src/main/java/opensagetv/vibe/miniclient/video/PlaybackSyncPointPolicy.java").read_text()
        media3 = (SHARED / "media3/Media3MediaPlayerImpl.java").read_text()
        exo2 = (SHARED / "exoplayer2/Exo2MediaPlayerImpl.java").read_text()
        self.assertIn("public final class PlaybackSyncPointPolicy", policy)
        self.assertIn("Target choose(", policy)
        for player in (media3, exo2):
            self.assertIn("PlaybackSyncPointPolicy.choose(", player)

    def test_runtime_configuration_is_an_immutable_backend_snapshot(self):
        config = (SHARED / "PlayerRuntimeConfig.java").read_text()
        self.assertIn("public final class PlayerRuntimeConfig", config)
        self.assertIn("public static PlayerRuntimeConfig capture(Backend backend)", config)
        self.assertNotIn(" void set", config)
        for relative, backend in (
            ("media3/Media3MediaPlayerImpl.java", "MEDIA3"),
            ("exoplayer2/Exo2MediaPlayerImpl.java", "LEGACY_EXO"),
        ):
            player = (SHARED / relative).read_text()
            self.assertIn(
                f"PlayerRuntimeConfig.capture(PlayerRuntimeConfig.Backend.{backend})",
                player,
            )
            self.assertIn("runtimeConfig.getSeekPolicy()", player)
            self.assertIn("runtimeConfig.getSeekRecoveryDelayMs()", player)

    def test_seek_recovery_timer_ownership_is_shared(self):
        monitor = (SHARED / "PullSeekRecoveryMonitor.java").read_text()
        self.assertIn("public final class PullSeekRecoveryMonitor", monitor)
        self.assertIn("private long generation;", monitor)
        self.assertIn("public synchronized boolean arm(", monitor)
        for relative in (
            "media3/Media3MediaPlayerImpl.java",
            "exoplayer2/Exo2MediaPlayerImpl.java",
        ):
            player = (SHARED / relative).read_text()
            self.assertIn("new PullSeekRecoveryMonitor", player)
            self.assertNotIn("pullSeekGeneration", player)
            self.assertNotIn("pullSeekRecoveryRunnable", player)

    def test_primary_backends_use_typed_health_and_datasource_telemetry(self):
        health = (SHARED / "PlaybackHealthSnapshot.java").read_text()
        telemetry = (SHARED / "PlaybackDataSourceTelemetry.java").read_text()
        probe = (DEBUG / "PlaybackHealthProbe.java").read_text()
        self.assertIn("public final class PlaybackHealthSnapshot", health)
        self.assertIn("extends SmbTelemetrySource", telemetry)
        self.assertIn("instanceof PlaybackHealthSource", probe)
        self.assertIn("instanceof PlaybackDataSourceTelemetry", probe)
        self.assertIn("capturePullDataSource(typedDataSource, out)", probe)
        self.assertIn("captureLegacyDataSource(dataSource, out)", probe)
        for relative in (
            "media3/Media3MediaPlayerImpl.java",
            "exoplayer2/Exo2MediaPlayerImpl.java",
        ):
            player = (SHARED / relative).read_text()
            self.assertIn("implements PlaybackHealthSource", player)
            self.assertIn("capturePlaybackHealthSnapshot()", player)
        for relative in (
            "media3/Media3PullDataSource.java",
            "exoplayer2/Exo2PullDataSource.java",
        ):
            source = (SHARED / relative).read_text()
            self.assertIn("PlaybackDataSourceTelemetry", source)
            self.assertNotIn("SmbTelemetrySource", source)


if __name__ == "__main__":
    unittest.main()
