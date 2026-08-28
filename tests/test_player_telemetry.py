from __future__ import annotations

from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
SHARED = ROOT / "source/dev/android-shared/src/main/java/sagex/miniclient/android/video"
BASE = SHARED / "BaseMediaPlayerImpl.java"
EXO = SHARED / "exoplayer2/Exo2MediaPlayerImpl.java"
IJK = SHARED / "ijkplayer/IJKMediaPlayerImpl.java"
SERVER = ROOT / "mcp/src/sagetv_dev_mcp/server.py"
ENTRYPOINT = ROOT / "docker/entrypoint.sh"


class PlayerTelemetryRollbackTests(unittest.TestCase):
    def test_base_has_no_runtime_telemetry_hooks(self):
        text = BASE.read_text(encoding="utf-8")
        self.assertNotIn("PlayerTelemetry", text)
        self.assertNotIn("telemetry.", text)
        self.assertNotIn("telemetryLoadStartMonoMs", text)
        self.assertNotIn("telemetrySeekRequestMonoMs", text)

    def test_exoplayer_has_no_runtime_telemetry_hooks(self):
        text = EXO.read_text(encoding="utf-8")
        self.assertNotIn("PlayerTelemetry", text)
        self.assertNotIn("telemetry.", text)
        self.assertNotIn("import com.google.android.exoplayer2.analytics.AnalyticsListener;", text)
        self.assertNotIn("telemetryProcessedFrames", text)

    def test_ijk_has_no_runtime_telemetry_hooks(self):
        text = IJK.read_text(encoding="utf-8")
        self.assertNotIn("PlayerTelemetry", text)
        self.assertNotIn("telemetry.", text)
        self.assertNotIn("telemetrySnapshotDue", text)

    def test_mcp_infrastructure_can_remain(self):
        text = SERVER.read_text(encoding="utf-8")
        self.assertIn("def get_player_telemetry", text)
        self.assertIn("def wait_for_player_event", text)

    def test_dev_build_is_clean(self):
        text = ENTRYPOINT.read_text(encoding="utf-8")
        self.assertIn("./gradlew --no-daemon clean :android-tv:assembleDebug", text)


if __name__ == "__main__":
    unittest.main()
