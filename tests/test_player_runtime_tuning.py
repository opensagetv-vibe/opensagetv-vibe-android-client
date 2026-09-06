from argparse import Namespace
from pathlib import Path
import importlib.util
import sys
import unittest

ROOT = Path(__file__).resolve().parents[1]
SCRIPTS = ROOT / "scripts"
MCP_SRC = ROOT / "mcp" / "src"
sys.path.insert(0, str(SCRIPTS))
sys.path.insert(0, str(MCP_SRC))
spec = importlib.util.spec_from_file_location("mcp_player_tuning_matrix", SCRIPTS / "mcp_player_tuning_matrix.py")
module = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = module
assert spec.loader is not None
spec.loader.exec_module(module)


class PlayerRuntimeTuningTests(unittest.TestCase):
    def test_matrix_supports_exact_generated_fixture_path(self):
        matrix = (SCRIPTS / "mcp_player_tuning_matrix.py").read_text()
        self.assertIn('p.add_argument("--server-path"', matrix)
        self.assertIn("server_path=args.server_path", matrix)
        self.assertIn('"serverPath": args.server_path', matrix)
        self.assertIn("and not args.server_path", matrix)
        self.assertIn("exact server path will be replayed", matrix)

    def test_grid_expands_cartesian_values(self):
        args = Namespace(
            player="media3", profiles="", ts_search="4,8", seek_policy="closest,next",
            pull_read_kb="256,512", min_buffer_ms="5000", max_buffer_ms="20000",
            playback_buffer_ms="500", rebuffer_ms="1000", seek_recovery="off",
            seek_recovery_ms="10000", codec_mode="auto", directional_sync_min_delta_ms="2000",
        )
        grid = module.make_grid(args)
        self.assertEqual(len(grid), 8)
        self.assertIn(4, {x["tsSearchMultiplier"] for x in grid})
        self.assertIn("next", {x["seekPolicy"] for x in grid})
        self.assertIn(512, {x["pullReadKb"] for x in grid})


    def test_streaming_accepts_push_pull_csv(self):
        self.assertEqual(module.streaming_modes("push,pull"), ["push", "pull"])
        self.assertEqual(module.streaming_modes("pull,push"), ["pull", "push"])

    def test_push_grid_ignores_pull_only_dimensions(self):
        args = Namespace(
            player="media3", profiles="", ts_search="4,8", seek_policy="closest,next",
            pull_read_kb="256,512", min_buffer_ms="5000", max_buffer_ms="20000",
            playback_buffer_ms="500", rebuffer_ms="1000", seek_recovery="off",
            seek_recovery_ms="10000", codec_mode="auto,async,sync", directional_sync_min_delta_ms="2000",
        )
        pull_grid = module.make_grid(args, "pull")
        push_grid = module.make_grid(args, "push")
        self.assertEqual(len(pull_grid), 24)
        self.assertEqual(len(push_grid), 3)
        self.assertEqual({x["codecMode"] for x in push_grid}, {"auto", "async", "sync"})
        defaults = module.backend_defaults("media3")
        for combo in push_grid:
            for key in module.PUSH_IGNORED_TUNING_KEYS:
                self.assertEqual(combo[key], defaults[key])

    def test_push_profiles_collapse_equivalent_pull_only_profiles(self):
        args = Namespace(
            player="media3", profiles="default,next_sync,large_read,auto_codec,async_codec,sync_codec,no_reprepare",
            ts_search="", seek_policy="", pull_read_kb="", min_buffer_ms="", max_buffer_ms="",
            playback_buffer_ms="", rebuffer_ms="", seek_recovery="", seek_recovery_ms="",
            codec_mode="", directional_sync_min_delta_ms="",
        )
        grid = module.make_grid(args, "push")
        self.assertEqual(len(grid), 3)
        self.assertEqual({x["codecMode"] for x in grid}, {"auto", "async", "sync"})

    def test_profiles_are_backend_aware(self):
        self.assertEqual(module.named_profile("media3", "default")["codecMode"], "sync")
        self.assertEqual(module.named_profile("exoplayer", "default")["codecMode"], "sync")
        self.assertEqual(module.named_profile("media3", "auto_codec")["codecMode"], "auto")
        self.assertEqual(module.named_profile("media3", "default")["pullReadKb"], 256)
        self.assertEqual(module.named_profile("exoplayer", "default")["pullReadKb"], 512)
        self.assertEqual(module.named_profile("media3", "large_read")["pullReadKb"], 512)
        self.assertEqual(module.named_profile("exoplayer", "large_read")["pullReadKb"], 1024)
        self.assertFalse(module.named_profile("media3", "no_reprepare")["seekRecovery"])

    def test_backend_config_maps_generic_values_to_explicit_debug_fields(self):
        c = module.backend_defaults("media3")
        c["seekPolicy"] = "next"
        c["codecMode"] = "async"
        mapped = module.config_to_mcp("media3", c)
        self.assertEqual(mapped["media3_seek_policy"], "next")
        self.assertEqual(mapped["media3_codec_mode"], "async")
        self.assertEqual(mapped["media3_pull_read_kb"], 256)
        self.assertNotIn("exo2_seek_policy", mapped)

    def test_debug_runtime_tuning_is_wired_into_players_and_snapshot(self):
        tuning = (ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/PlayerRuntimeTuning.java").read_text()
        media3 = (ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/media3/Media3MediaPlayerImpl.java").read_text()
        exo2 = (ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/exoplayer2/Exo2MediaPlayerImpl.java").read_text()
        receiver = (ROOT / "source/dev/android-tv/src/debug/java/opensagetv/vibe/miniclient/android/tv/debug/DevTestReceiver.java").read_text()
        tuning_commands = (ROOT / "source/dev/android-tv/src/debug/java/opensagetv/vibe/miniclient/android/tv/debug/DebugTuningCommands.java").read_text()
        state_provider = (ROOT / "source/dev/android-tv/src/debug/java/opensagetv/vibe/miniclient/android/tv/debug/DebugStateProvider.java").read_text()
        self.assertIn("DEFAULT_MEDIA3_PULL_READ_BYTES = 256 * 1024", tuning)
        self.assertIn("DEFAULT_EXO2_PULL_READ_BYTES = 512 * 1024", tuning)
        self.assertIn('DEFAULT_CODEC_MODE = "sync"', tuning)
        self.assertIn("applyMedia3CodecPreference", tuning)
        self.assertIn("applyExo2CodecPreference", tuning)
        self.assertIn("media3CodecModeOverridden", tuning)
        self.assertIn("exo2CodecModeOverridden", tuning)
        self.assertIn("runtimeConfig.getTsSearchMultiplier()", media3)
        self.assertIn("runtimeConfig.getTsSearchMultiplier()", exo2)
        self.assertIn("forceEnableMediaCodecAsynchronousQueueing", media3)
        self.assertIn("forceDisableMediaCodecAsynchronousQueueing", exo2)
        self.assertIn('"tuning".equals(op)', receiver)
        self.assertIn('DebugTuningCommands.configure(intent)', receiver)
        self.assertIn('PlayerRuntimeTuning.configure(', tuning_commands)
        self.assertIn('"op=tuning;" + PlayerRuntimeTuning.compactWire()', tuning_commands)
        self.assertIn('debugStatusVersion=21', state_provider)
        self.assertIn('PlayerRuntimeTuning.compactWire()', receiver)

    def test_each_player_captures_one_backend_neutral_runtime_config(self):
        config = (ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/PlayerRuntimeConfig.java").read_text()
        media3 = (ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/media3/Media3MediaPlayerImpl.java").read_text()
        exo2 = (ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/exoplayer2/Exo2MediaPlayerImpl.java").read_text()
        media3_source = (ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/media3/Media3PullDataSource.java").read_text()
        exo2_source = (ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/exoplayer2/Exo2PullDataSource.java").read_text()
        self.assertIn("public final class PlayerRuntimeConfig", config)
        self.assertIn("enum Backend { MEDIA3, LEGACY_EXO }", config)
        self.assertIn("public static PlayerRuntimeConfig capture(Backend backend)", config)
        self.assertNotIn("void set", config)
        for player, backend in ((media3, "MEDIA3"), (exo2, "LEGACY_EXO")):
            capture = f"PlayerRuntimeConfig.capture(PlayerRuntimeConfig.Backend.{backend})"
            self.assertGreaterEqual(player.count(capture), 2)
            self.assertIn("runtimeConfig.getPullReadBytes()", player)
            self.assertIn("runtimeConfig.getSeekPolicy()", player)
            self.assertIn("runtimeConfig.getSeekRecoveryDelayMs()", player)
            self.assertNotIn("PlayerRuntimeTuning.getMedia3PullMinBufferMs()", player)
            self.assertNotIn("PlayerRuntimeTuning.getExo2PullMinBufferMs()", player)
        for source in (media3_source, exo2_source):
            self.assertIn("private final int pullReadBytes;", source)
            self.assertIn("new BufferedPullDataSource(host, pullReadBytes)", source)


    def test_codec_mode_is_persisted_in_player_settings_with_sync_default(self):
        media3_prefs = (ROOT / "source/dev/android-shared/src/main/res/xml/media3player_prefs.xml").read_text()
        exo2_prefs = (ROOT / "source/dev/android-shared/src/main/res/xml/exoplayer_prefs.xml").read_text()
        arrays = (ROOT / "source/dev/android-shared/src/main/res/values/arrays.xml").read_text()
        pref_store = (ROOT / "source/dev/core/src/main/java/opensagetv/vibe/miniclient/prefs/PrefStore.java").read_text()
        media3 = (ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/media3/Media3MediaPlayerImpl.java").read_text()
        exo2 = (ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/exoplayer2/Exo2MediaPlayerImpl.java").read_text()
        self.assertIn('android:key="media3_codec_mode"', media3_prefs)
        self.assertIn('android:key="exo2_codec_mode"', exo2_prefs)
        self.assertIn('android:defaultValue="sync"', media3_prefs)
        self.assertIn('android:defaultValue="sync"', exo2_prefs)
        self.assertIn('Sync (Recommended)', arrays)
        self.assertIn('String media3_codec_mode = "media3_codec_mode"', pref_store)
        self.assertIn('String exo2_codec_mode = "exo2_codec_mode"', pref_store)
        self.assertIn('PrefStore.Keys.media3_codec_mode', media3)
        self.assertIn('PrefStore.Keys.exo2_codec_mode', exo2)

    def test_tuning_matrix_connects_adb_before_first_case(self):
        matrix = (SCRIPTS / "mcp_player_tuning_matrix.py").read_text()
        init_at = matrix.index("initialize(client)")
        adb_at = matrix.index('call_dict(client, "adb_connect"', init_at)
        loop_at = matrix.index("for streaming in modes:", adb_at)
        self.assertLess(init_at, adb_at)
        self.assertLess(adb_at, loop_at)
        self.assertIn('call_dict(client, "adb_session_status"', matrix[adb_at:loop_at])

    def test_tuning_matrix_fast_replay_caches_exact_media_id(self):
        matrix = (SCRIPTS / "mcp_player_tuning_matrix.py").read_text()
        server = (ROOT / "mcp/src/sagetv_dev_mcp/server.py").read_text()
        self.assertIn('--startup-mode', matrix)
        self.assertIn('default="fast"', matrix)
        self.assertIn('def fast_start_case(', matrix)
        self.assertIn('"dev_current_media_file"', matrix)
        self.assertIn('"dev_play_media_file_id"', matrix)
        self.assertIn('falling back to full isolated startup', matrix)
        self.assertIn('"freshPlayerPerCombination": True', matrix)
        self.assertIn('"fullAppRestartPerCombination": args.startup_mode == "isolated"', matrix)
        self.assertIn('def dev_current_media_file()', server)
        self.assertRegex(
            server,
            r'def\s+dev_play_media_file_id\(\s*media_file_id:\s*int',
        )
        self.assertIn('sagex.watch(context, media_file_id)', server)

    def test_dev_shell_exposes_tuning_commands(self):
        dev = (ROOT / "dev.sh").read_text()
        self.assertIn("mcp-player-tune)", dev)
        self.assertIn("mcp-player-tuning-matrix)", dev)
        matrix = (SCRIPTS / "mcp_player_tuning_matrix.py").read_text()
        self.assertIn('"slowRecoveryMs": args.slow_recovery_ms', matrix)
        self.assertIn('compiledDefaultsResetBeforeEachCombination', matrix)


if __name__ == "__main__":
    unittest.main()
