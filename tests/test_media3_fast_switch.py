from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]


class Media3FastSwitchTests(unittest.TestCase):
    def read(self, relative):
        return (ROOT / relative).read_text(encoding="utf-8")

    def test_policy_is_conservative_and_reasoned(self):
        policy = self.read(
            "source/dev/core/src/main/java/opensagetv/vibe/miniclient/video/"
            "MediaReplacementPolicy.java"
        )
        for reason in (
            "first_load",
            "player_not_ready",
            "current_push",
            "current_dvd",
            "current_http",
            "current_active_or_legacy_unknown",
            "current_circular_buffer",
            "next_active_or_legacy_unknown",
            "next_circular_buffer",
            "next_push",
            "next_dvd",
            "next_http",
            "next_external_link",
            "eligible_completed_pull",
        ):
            self.assertIn(f'"{reason}"', policy)

    def test_both_renderers_retain_only_existing_media3_backend(self):
        for renderer in (
            "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/"
            "android/opengl/OpenGLRenderer.java",
            "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/"
            "android/gdx/MiniClientGDXRenderer.java",
        ):
            text = self.read(renderer)
            self.assertIn("player instanceof Media3MediaPlayerImpl", text)
            self.assertIn("canRetainForNextUrl(urlString)", text)
            self.assertIn("return player;", text)
            self.assertIn("if (player != null) player.free();", text)

    def test_media3_replaces_source_and_has_one_full_load_fallback(self):
        media3 = self.read(
            "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/"
            "android/video/media3/Media3MediaPlayerImpl.java"
        )
        self.assertIn("MediaReplacementPolicy.evaluate", media3)
        self.assertIn("player.setMediaSource(replacementSource, true)", media3)
        self.assertIn("fastSwitchFallbackPending = true", media3)
        self.assertIn(
            'fallbackFromFastSwitch("asynchronous_" + error.getErrorCodeName())',
            media3,
        )
        self.assertIn("setupPlayer(fastSwitchTargetUrl)", media3)
        self.assertIn('fastSwitchLastReason = "first_frame_rendered"', media3)

    def test_fast_switch_has_bounded_first_frame_watchdog(self):
        media3 = self.read(
            "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/"
            "android/video/media3/Media3MediaPlayerImpl.java"
        )
        self.assertIn("FAST_SWITCH_FIRST_FRAME_TIMEOUT_MS = 8_000L", media3)
        self.assertIn("armFastSwitchWatchdog()", media3)
        self.assertIn('fallbackFromFastSwitch("first_frame_timeout")', media3)
        self.assertIn("cancelFastSwitchWatchdog();", media3)
        self.assertEqual(
            media3.count('fallbackFromFastSwitch("asynchronous_" + error.getErrorCodeName())'),
            1,
        )

    def test_debug_physical_command_preserves_case_sensitive_server_path(self):
        commands = self.read(
            "source/dev/android-tv/src/debug/java/opensagetv/vibe/miniclient/"
            "android/tv/debug/DebugPlayerCommands.java"
        )
        receiver = self.read(
            "source/dev/android-tv/src/debug/java/opensagetv/vibe/miniclient/"
            "android/tv/debug/DevTestReceiver.java"
        )
        method = commands.split("static String fastSwitchFile", 1)[1].split(
            "private static String normalizeArrowDirection", 1
        )[0]
        self.assertIn('text(intent.getStringExtra("server_path"))', method)
        self.assertNotIn('clean(intent.getStringExtra("server_path"))', method)
        self.assertIn('"fast_switch_file".equals(op)', receiver)

        adb = self.read("mcp/src/sagetv_dev_mcp/adb.py")
        server = self.read("mcp/src/sagetv_dev_mcp/server.py")
        dev = self.read("dev.sh")
        self.assertIn("def media3_fast_switch_file", adb)
        self.assertIn("def dev_media3_fast_switch_file", server)
        self.assertIn("mcp-fast-switch-test)", dev)
        self.assertIn("mcp_media3_fast_switch_test.py", dev)

    def test_debug_state_exposes_target_and_outcome(self):
        state = self.read(
            "source/dev/android-tv/src/debug/java/opensagetv/vibe/miniclient/"
            "android/tv/debug/DebugStateProvider.java"
        )
        for field in (
            "fastSwitchAttemptCount",
            "fastSwitchSuccessCount",
            "fastSwitchFallbackCount",
            "fastSwitchAwaitingFirstFrame",
            "fastSwitchLastReason",
            "fastSwitchTargetUrl",
        ):
            self.assertIn(field, state)


if __name__ == "__main__":
    unittest.main()
