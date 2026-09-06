from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]


class FrameStepProtocolTests(unittest.TestCase):
    def test_command_28_has_a_four_byte_success_reply(self):
        media_cmd = (ROOT / "source/dev/core/src/main/java/opensagetv/vibe/miniclient/MediaCmd.java").read_text(
            encoding="utf-8"
        )
        block = media_cmd.split("case MEDIACMD_FRAMESTEP:", 1)[1].split(
            "case MEDIACMD_SETVIDEORECT:", 1
        )[0]
        self.assertIn("playa.frameStep(frameAmount)", block)
        self.assertIn("writeInt(frameStepAccepted ? 1 : 0, retbuf, 0)", block)
        self.assertIn("return 4", block)

    def test_capability_is_advertised_only_for_guaranteed_pull_modes(self):
        connection = (
            ROOT / "source/dev/core/src/main/java/opensagetv/vibe/miniclient/MiniClientConnection.java"
        ).read_text(encoding="utf-8")
        self.assertIn('"FRAME_STEP".equals(propName)', connection)
        self.assertIn("PlaybackFrameStepPolicy.shouldAdvertise", connection)

    def test_android_backends_define_paused_random_access_step(self):
        paths = [
            "video/media3/Media3MediaPlayerImpl.java",
            "video/exoplayer2/Exo2MediaPlayerImpl.java",
            "video/ijkplayer/IJKMediaPlayerImpl.java",
        ]
        root = ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android"
        for relative in paths:
            source = (root / relative).read_text(encoding="utf-8")
            self.assertIn("boolean frameStep(final int amount)", source, relative)
            self.assertIn("PlaybackFrameStepPolicy.canStep", source, relative)
            self.assertIn("PlaybackFrameStepPolicy.targetPositionMs", source, relative)
            self.assertIn('"frame_step_unsupported"', source, relative)

    def test_push_and_invalid_states_fail_explicitly_in_shared_policy(self):
        policy = (
            ROOT / "source/dev/core/src/main/java/opensagetv/vibe/miniclient/video/PlaybackFrameStepPolicy.java"
        ).read_text(encoding="utf-8")
        self.assertIn("state == MiniPlayerPlugin.PAUSE_STATE", policy)
        self.assertIn("!pushMode", policy)
        self.assertIn("amount != 0", policy)

    def test_repeated_pause_uses_new_contract_and_falls_back_when_unsupported(self):
        router = (
            ROOT / "source/dev/core/src/main/java/opensagetv/vibe/miniclient/uibridge/EventRouter.java"
        ).read_text(encoding="utf-8")
        self.assertNotIn("client.getPlayer().pause();", router)
        self.assertEqual(router.count("client.getPlayer().frameStep(1)"), 2)
        self.assertIn("reports unsupported explicitly", router)


if __name__ == "__main__":
    unittest.main()
