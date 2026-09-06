import pathlib
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[1]


class PushTelemetryTests(unittest.TestCase):
    def test_server_stats_and_datasource_metrics_reach_mcp_state(self):
        media_cmd = (ROOT / "source/dev/core/src/main/java/opensagetv/vibe/miniclient/MediaCmd.java").read_text(encoding="utf-8")
        state = (ROOT / "source/dev/android-tv/src/debug/java/opensagetv/vibe/miniclient/android/tv/debug/DebugStateProvider.java").read_text(encoding="utf-8")
        health = (ROOT / "source/dev/android-tv/src/debug/java/opensagetv/vibe/miniclient/android/tv/debug/PlaybackHealthProbe.java").read_text(encoding="utf-8")
        mcp = (ROOT / "mcp/src/sagetv_dev_mcp/server.py").read_text(encoding="utf-8")

        for key in (
            "serverChannelBandwidthKbps",
            "serverStreamBandwidthKbps",
            "serverTargetBandwidthKbps",
            "serverMuxTimeMs",
            "clientBufferTimeMs",
            "clientBufferAvailableBytes",
            "detailedPushSampleSequence",
        ):
            self.assertIn(key, media_cmd)
            self.assertIn(key, state)
            self.assertIn(key, mcp)

        for key in (
            "dataSourceReadCount",
            "dataSourceReadBytes",
            "dataSourceReadWaitMs",
            "dataSourceReadRateKbps",
            "dataSourcePushedBytes",
        ):
            self.assertIn(key, health)
            self.assertIn("health_" + key, mcp)

        # Pull and SMB datasources report their source directly. Historical
        # Push/Fixed datasources do not, so the debug probe must derive the
        # transport from the authoritative pushMode player contract.
        self.assertIn('out.pushMode && "UNKNOWN".equals(out.playbackSource)', health)
        self.assertIn('out.playbackSource = "SAGETV_PUSH"', health)

    def test_physical_gate_requires_server_and_datasource_metrics(self):
        script = (ROOT / "scripts/mcp_push_telemetry_test.py").read_text(encoding="utf-8")
        workflow = (ROOT / "dev.sh").read_text(encoding="utf-8")
        self.assertIn('"detailedPushSampleSequence"', script)
        self.assertIn('"health_dataSourceReadRateKbps"', script)
        self.assertIn('"health_dataSourcePushedBytes"', script)
        self.assertIn("mcp-push-telemetry-test)", workflow)


if __name__ == "__main__":
    unittest.main()
