from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]


class PlaybackRateProtocolTests(unittest.TestCase):
    def read(self, relative):
        return (ROOT / relative).read_text(encoding="utf-8")

    def test_policy_is_bounded_and_pull_only(self):
        policy = self.read(
            "source/dev/core/src/main/java/opensagetv/vibe/miniclient/video/PlaybackRatePolicy.java"
        )
        self.assertIn("MIN_NATIVE_RATE = 0.5f", policy)
        self.assertIn("MAX_NATIVE_RATE = 2.0f", policy)
        self.assertIn("MIN_SCAN_RATE = 4.0f", policy)
        self.assertIn("MAX_SCAN_RATE = 256.0f", policy)
        self.assertIn("SCAN_INTERVAL_MS = 3000L", policy)
        self.assertIn("StreamingModePolicy.isForcedPull", policy)
        self.assertIn('!"system".equalsIgnoreCase(gsyEngine)', policy)

    def test_command_property_and_gsy_delegation_are_wired(self):
        media = self.read("source/dev/core/src/main/java/opensagetv/vibe/miniclient/MediaCmd.java")
        connection = self.read("source/dev/core/src/main/java/opensagetv/vibe/miniclient/MiniClientConnection.java")
        plugin = self.read("source/dev/core/src/main/java/opensagetv/vibe/miniclient/MiniPlayerPlugin.java")
        gsy = self.read(
            "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/gsy/GSYMediaPlayerImpl.java"
        )
        self.assertIn("MEDIACMD_SETRATE = 30", media)
        self.assertIn('"VIBE_PLAYBACK_RATE"', connection)
        self.assertIn("default float setPlaybackRate(float rate)", plugin)
        self.assertIn("d().setPlaybackRate(rate)", gsy)
        self.assertIn("d().getPlaybackRate()", gsy)

    def test_debug_mcp_and_physical_gate_exist(self):
        receiver = self.read(
            "source/dev/android-tv/src/debug/java/opensagetv/vibe/miniclient/android/tv/debug/DevTestReceiver.java"
        )
        state = self.read(
            "source/dev/android-tv/src/debug/java/opensagetv/vibe/miniclient/android/tv/debug/DebugStateProvider.java"
        )
        adb = self.read("mcp/src/sagetv_dev_mcp/adb.py")
        server = self.read("mcp/src/sagetv_dev_mcp/server.py")
        script = self.read("scripts/mcp_playback_rate_test.py")
        dev = self.read("dev.sh")
        self.assertIn('"playback_rate".equals(op)', receiver)
        self.assertIn("player.getPlaybackRate()", state)
        self.assertIn("def playback_rate", adb)
        self.assertIn("def dev_playback_rate", server)
        self.assertIn('call_dict(client, "dev_playback_rate"', script)
        self.assertIn('"command": "smooth_ff"', script)
        self.assertIn('"command": "smooth_rew"', script)
        self.assertIn("server_playback_rate_command", script)
        self.assertIn('"decoding": "hardware"', script)
        self.assertIn("mcp-playback-rate-test)", dev)

    def test_server_trick_play_mute_reaches_both_exo_backends(self):
        for relative in (
            "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/"
            "android/video/media3/Media3MediaPlayerImpl.java",
            "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/"
            "android/video/exoplayer2/Exo2MediaPlayerImpl.java",
        ):
            backend = self.read(relative)
            self.assertIn("public void setMute(final boolean muted)", backend)
            self.assertIn("player.setVolume(muted ? 0.0f : 1.0f)", backend)
            self.assertIn("player.setVolume(serverMuted ? 0.0f : 1.0f)", backend)


if __name__ == "__main__":
    unittest.main()
