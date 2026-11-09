"""Boundaries for the Pro's stale 2512-ms Push duration regression."""
from pathlib import Path
import importlib.util
import sys
import unittest
from unittest import mock

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/media3"


class PushEpochTimeoutTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        sys.path.insert(0, str(ROOT / "scripts"))
        sys.path.insert(0, str(ROOT / "mcp/src"))
        spec = importlib.util.spec_from_file_location(
            "push_epoch_gate", ROOT / "scripts/mcp_push_epoch_timeout_test.py")
        cls.gate = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(cls.gate)

    def test_server_epoch_is_not_used_as_relative_seek(self):
        self.assertEqual(12345, self.gate.relative_server_ms(
            {"mediaTimeMs": 1791511212358, "seekStartMs": 1791511200013}))

    def test_physical_gate_uses_real_push_selection_not_backend_spoofing(self):
        source = (ROOT / "scripts/mcp_push_epoch_timeout_test.py").read_text(encoding="utf-8")
        self.assertIn('{"streaming": "push"}', source)
        self.assertNotIn('{"player": "exoplayer"', source)
        for event in ("server_flush_command", "first_video_frame", "playback_state_3"):
            self.assertIn(event, source)
        self.assertIn('row["landingErrorMs"]', source)

    def test_continuity_requires_clock_and_both_outputs(self):
        first = {"health_pushMode": True, "mediaTimeMs": 10000,
                 "health_videoRendered": 50, "health_audioRendered": 80}
        last = {"health_pushMode": True, "mediaTimeMs": 160000,
                "health_videoRendered": 9000, "health_audioRendered": 4800}
        self.gate.sample_verdict(first, last, 150)
        for invalid in (dict(last, health_pushMode=False),
                        dict(last, mediaTimeMs=12000),
                        dict(last, health_videoRendered=50),
                        dict(last, health_audioRendered=80)):
            with self.assertRaises(RuntimeError):
                self.gate.sample_verdict(first, invalid, 150)

    def test_restore_refuses_another_source(self):
        core = mock.Mock()
        core.ui_state.return_value = {"media": {"mediaFileId": 456}}
        with self.assertRaisesRegex(RuntimeError, "source changed"):
            self.gate.restore_playback(None, core, "ctx", 123, 10000, {}, {})
        core.seek.assert_not_called()
        core.media_control.assert_not_called()

    def test_eof_restore_reopens_and_verifies_output_not_only_ack(self):
        core = mock.Mock()
        core.ui_state.return_value = {"media": {"mediaFileId": 123}}
        order = []
        core.media_control.side_effect = lambda *a: order.append("stop") or {"ok": True}
        core.seek.side_effect = lambda *a: order.append("seek") or {"accepted": True}
        snapshots = iter([
            {"passed": True},
            {"health_playbackState": 3, "health_isPlaying": True, "mediaTimeMs": 10000,
             "health_videoRendered": 10, "health_audioRendered": 10},
            {"health_playbackState": 3, "health_isPlaying": True, "mediaTimeMs": 10000,
             "health_videoRendered": 11, "health_audioRendered": 11},
        ])
        def command(client, name, *args, **kwargs):
            order.append(name)
            return next(snapshots)
        result = {"eosVerified": True}
        with mock.patch.object(self.gate, "call_dict", side_effect=command), \
                mock.patch.object(self.gate.time, "sleep"), \
                mock.patch.object(self.gate.time, "monotonic", return_value=0):
            self.gate.restore_playback(None, core, "ctx", 123, 10000,
                                       {"health_isPlaying": True}, result)
        self.assertEqual(["stop", "dev_play_media_file_id", "seek"], order[:3])
        self.assertIn("restoredPlayback", result)

    def test_flush_replaces_cached_timeline_not_server_byte_pipe(self):
        source = (JAVA / "Media3MediaPlayerImpl.java").read_text(encoding="utf-8")
        flush = source.split('record("flush_reprepare_before"', 1)[1].split(
            'record("flush_reprepare_after"', 1)[0]
        self.assertIn("mediaSource = createOrdinaryPushSource(", flush)
        factory = source.split('private MediaSource createOrdinaryPushSource(', 1)[1].split('@Override', 1)[0]
        self.assertIn('new VibeForwardPushMediaSource.Factory(', factory)
        self.assertIn('new ProgressiveMediaSource.Factory(', factory)
        self.assertIn('return factory.createMediaSource(item);', factory)
        self.assertIn("final Media3PushDataSource pushSource = (Media3PushDataSource) dataSource;", flush)
        self.assertIn("return pushSource;", flush)
        self.assertIn("withPassthroughOffset(createCaptionAwareExtractorsFactory(false))", flush)
        self.assertIn("mediaSource.getMediaItem());", flush)
        self.assertNotIn("new Media3PushDataSource", flush)
        self.assertNotIn("seekTo(", flush)

    def test_only_end_detector_changes_and_dvd_keeps_separate_flush(self):
        source = (JAVA / "Media3MediaPlayerImpl.java").read_text(encoding="utf-8")
        self.assertIn("pushMode, dvdPushMode, httpls", source)
        self.assertIn("server_push_stale_duration_timeout_relaxed", source)
        self.assertNotIn("setStuckBufferingTimeoutMs(", source)
        self.assertNotIn("setStuckPlayingTimeoutMs(", source)
        self.assertNotIn("setStuckSuppressedTimeoutMs(", source)
        flush = source.split("public synchronized void flush()", 1)[1]
        self.assertLess(flush.index("dvdRepreparePending = true;"),
                        flush.index('record("flush_reprepare_before"'))

    def test_push_explicit_network_wake_does_not_depend_on_disabled_detector_default(self):
        source = (JAVA / 'Media3MediaPlayerImpl.java').read_text()
        wake = source.split('if (serverOwnedPush)', 1)[1].split(
            'if (serverOwnedPush ||', 1)[0]
        self.assertIn('builder.setWakeMode(C.WAKE_MODE_NETWORK)', wake)
        self.assertIn('server_push_network_wake_mode', wake)
        self.assertLess(source.index('builder.setWakeMode(C.WAKE_MODE_NETWORK)'),
                        source.index('player = builder.build()'))
        manifest = (ROOT / 'source/dev/android-shared/src/main/AndroidManifest.xml').read_text()
        self.assertIn('android.permission.WAKE_LOCK', manifest)
        self.assertNotIn('createWifiLock(', source)

    def test_displays_and_debug_checks_use_the_same_clock_as_wire_replies(self):
        core = (ROOT / "source/dev/core/src/main/java/opensagetv/vibe/miniclient/MediaCmd.java").read_text(encoding="utf-8")
        self.assertIn("public long getMediaTimeMillis()", core)
        self.assertIn("playa.getMediaTimeMillis(getEffectiveServerStartPosition())", core)
        files = [ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/ActivePlayerStatsSnapshot.java"]
        debug = ROOT / "source/dev/android-tv/src/debug/java/opensagetv/vibe/miniclient/android/tv/debug"
        files.extend(debug / name for name in (
            "DebugStateProvider.java", "DebugPlayerCommands.java", "DebugAsyncPlayerChecks.java"))
        for path in files:
            text = path.read_text(encoding="utf-8")
            self.assertNotIn("getMediaTimeMillis(mediaCmd.getLastServerStartPosition())", text)
            self.assertNotIn("getMediaTimeMillis(media.getLastServerStartPosition())", text)
            self.assertRegex(text, r"(?:mediaCmd|media)\.getMediaTimeMillis\(\)")


if __name__ == "__main__":
    unittest.main()
