import pathlib
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[1]
JAVA = ROOT / "source" / "dev" / "android-shared" / "src" / "main" / "java" / "opensagetv" / "vibe" / "miniclient" / "android" / "video"


class LiveProgramTransitionTest(unittest.TestCase):
    def read(self, relative):
        return (JAVA / relative).read_text(encoding="utf-8")

    def test_exo_pull_sources_publish_unknown_length_for_growing_media(self):
        for relative in (
            "media3/Media3PullDataSource.java",
            "exoplayer2/Exo2PullDataSource.java",
        ):
            source = self.read(relative)
            growing_branch = source.index("if (effectivelyGrowing)")
            explicit_length_branch = source.index("else if (dataSpec.length != C.LENGTH_UNSET)")
            self.assertLess(growing_branch, explicit_length_branch, relative)
            self.assertIn("bytesRemaining = C.LENGTH_UNSET;", source[growing_branch:explicit_length_branch])
            self.assertIn("waitForGrowth(startPos, 2000)", source)

    def test_exo_error_recovery_keeps_the_pre_error_position(self):
        for relative in (
            "media3/Media3MediaPlayerImpl.java",
            "exoplayer2/Exo2MediaPlayerImpl.java",
        ):
            source = self.read(relative)
            self.assertIn("long recoveryPositionMs = Math.max(0L, player.getCurrentPosition());", source)
            self.assertIn("player.setMediaSource(mediaSource, recoveryPositionMs);", source)
            self.assertIn('"player_error_recovery_position_preserved"', source)

    def test_exo_play_reprepares_a_server_stopped_player(self):
        for relative in (
            "media3/Media3MediaPlayerImpl.java",
            "exoplayer2/Exo2MediaPlayerImpl.java",
        ):
            source = self.read(relative)
            start = source.index("void ExoStart()")
            method = source[start:source.index("protected void releasePlayer()", start)]
            self.assertIn("player.getPlaybackState() == Player.STATE_IDLE", method)
            self.assertIn("context.setupVideoFrame();", method)
            self.assertIn("player.setVideoSurfaceView", method)
            self.assertIn('"play_prepare_from_idle"', method)
            self.assertLess(method.index("player.prepare();"), method.index("player.setPlayWhenReady(true);"))

    def test_server_stop_preserves_the_loaded_session_for_restart(self):
        base = self.read("BaseMediaPlayerImpl.java")
        stop = base[base.index("public void stop()"):
                    base.index("protected void clearSurface()")]
        self.assertIn(
            "beginPlaybackOperation(PlaybackSessionController.Operation.STOP)",
            stop,
        )
        self.assertNotIn("endSession", stop)

        free = base[base.index("public void free()"):
                    base.index("public void load(")]
        self.assertIn(
            "playbackSessions.endSession(PlaybackSessionController.Operation.FREE)",
            free,
        )

    def test_initial_resume_seek_is_bound_to_the_new_media_source(self):
        for relative in (
            "media3/Media3MediaPlayerImpl.java",
            "exoplayer2/Exo2MediaPlayerImpl.java",
        ):
            source = self.read(relative)
            self.assertIn("player.setMediaSource(mediaSource, requestedStartPosition);", source)
            self.assertIn('"initial_seek_attached_to_source"', source)
            self.assertNotIn("player.seekTo(playbackStartPosition);", source)

    def test_sparse_pcr_recordings_use_the_validated_timestamp_search_window(self):
        tuning = self.read("PlayerRuntimeTuning.java")
        self.assertIn("DEFAULT_TS_SEARCH_MULTIPLIER = 16", tuning)
        self.assertIn("value == 8 || value == 16", tuning)
        for relative in (
            "media3/Media3MediaPlayerImpl.java",
            "exoplayer2/Exo2MediaPlayerImpl.java",
        ):
            source = self.read(relative)
            self.assertIn("TsExtractor.DEFAULT_TIMESTAMP_SEARCH_BYTES", source, relative)
            self.assertIn("runtimeConfig.getTsSearchMultiplier()", source, relative)
            self.assertIn("setTsExtractorTimestampSearchBytes", source, relative)

    def test_server_stop_and_deinit_are_visible_in_event_diagnostics(self):
        media_cmd = (
            ROOT
            / "source/dev/core/src/main/java/opensagetv/vibe/miniclient/MediaCmd.java"
        ).read_text(encoding="utf-8")
        self.assertIn('PlaybackDebugEventBridge.recordAsync("server_stop_command", playa);', media_cmd)
        self.assertIn('PlaybackDebugEventBridge.recordAsync("server_deinit_command", playa);', media_cmd)

    def test_startup_media_time_trace_is_bounded_and_records_wire_value(self):
        media_cmd = (
            ROOT
            / "source/dev/core/src/main/java/opensagetv/vibe/miniclient/MediaCmd.java"
        ).read_text(encoding="utf-8")
        self.assertIn("startupMediaTimeTraceRemaining = 12;", media_cmd)
        self.assertIn("monotonicMs() + 4_000L", media_cmd)
        self.assertIn("startupMediaTimeTraceRemaining--", media_cmd)
        self.assertIn('"server_media_time_startup_reply"', media_cmd)
        self.assertIn('";wireInt=" + (int) theTime', media_cmd)

    def test_deinit_reply_proactively_recycles_the_media_socket(self):
        connection = (
            ROOT
            / "source/dev/core/src/main/java/opensagetv/vibe/miniclient/MiniClientConnection.java"
        ).read_text(encoding="utf-8")
        execute = connection.index("retval = myMedia.ExecuteMediaCommand")
        reply = connection.index("connectionDiagnostics.mediaReply(command);", execute)
        recycle = connection.index("command == MediaCmd.MEDIACMD_DEINIT", reply)
        reconnect = connection.index('connectionDiagnostics.reconnect("started")', recycle)
        self.assertLess(reply, recycle)
        self.assertLess(recycle, reconnect)
        self.assertIn('"media_socket_recycle_after_deinit"', connection[recycle:reconnect])

    def test_exo_media_session_teardown_is_deferred_to_the_main_thread(self):
        for relative in (
            "media3/Media3MediaPlayerImpl.java",
            "exoplayer2/Exo2MediaPlayerImpl.java",
        ):
            source = self.read(relative)
            start = source.index("protected void releasePlayer()")
            end = source.index("public Dimension getVideoDimensions()", start)
            method = source[start:end]
            main_thread = method.index("context.runOnUiThread")
            deactivate = method.index("sessionToRelease.setActive(false)")
            release = method.index("sessionToRelease.release()")
            self.assertLess(main_thread, deactivate, relative)
            self.assertLess(main_thread, release, relative)
            self.assertNotIn("sessionToRelease.setActive(false)", method[:main_thread], relative)
            self.assertNotIn("sessionToRelease.release()", method[:main_thread], relative)

    def test_debug_trace_is_bounded_rotating_and_exported_with_diagnostics(self):
        trace = (
            ROOT
            / "source/dev/android-tv/src/debug/java/opensagetv/vibe/miniclient/android/tv/debug/PersistentPlaybackTrace.java"
        ).read_text(encoding="utf-8")
        traps = (
            ROOT
            / "source/dev/android-tv/src/debug/java/opensagetv/vibe/miniclient/android/tv/debug/PlaybackEventTraps.java"
        ).read_text(encoding="utf-8")
        diagnostics = (ROOT / "scripts/player_diagnostics.py").read_text(encoding="utf-8")
        self.assertIn("MAX_BYTES", trace)
        self.assertIn("ROTATION_COUNT", trace)
        self.assertIn("getFilesDir()", trace)
        self.assertIn("PersistentPlaybackTrace.append(toJsonLine(trapped));", traps)
        self.assertIn("export_playback_trace", diagnostics)
        self.assertIn("KEY_ENABLED", trace)
        self.assertIn("existingTracePreserved=true", trace)

    def test_native_players_prepare_again_after_server_stop(self):
        ijk = self.read("ijkplayer/IJKMediaPlayerImpl.java")
        self.assertIn("private boolean stoppedForResume;", ijk)
        self.assertIn("boolean prepareAfterStop = stoppedForResume;", ijk)
        self.assertIn("context.runOnUiThread(new Runnable()", ijk)
        self.assertIn("isCurrentPlaybackSession(resumeSession)", ijk)
        self.assertIn("context.setupVideoFrame();", ijk)
        self.assertIn("playerToResume.setDisplay(", ijk)
        self.assertIn('PlaybackDebugTrap.record("play_resume_from_stopped"', ijk)
        self.assertIn("playerToResume.start();", ijk)
        self.assertNotIn("playerToResume.prepareAsync();", ijk)
        self.assertIn("player_prepared_while_stopped", ijk)

        system = self.read("gsy/GSYSystemMediaPlayerImpl.java")
        self.assertIn("private boolean stoppedForResume;", system)
        self.assertIn("playerReady = false;", system)
        self.assertIn("boolean prepareAfterStop = stoppedForResume;", system)
        self.assertIn("context.setupVideoFrame();", system)
        self.assertIn('PlaybackDebugTrap.record("play_prepare_from_stopped"', system)
        self.assertIn("stoppedForResume = false;", system)
        self.assertIn("context.runOnUiThread(new Runnable()", system)
        self.assertIn("isCurrentPlaybackSession(resumeSession)", system)

        self.assertIn("prepareWhenSurfaceReady();", system)
        self.assertIn("detachSurfaceCallback();", system)

    def test_media3_does_not_retain_a_growing_program_player(self):
        source = self.read("media3/Media3MediaPlayerImpl.java")
        method = source[source.index("public boolean canRetainForNextUrl"):]
        self.assertIn("mediaContext.isTimeshifted() || mediaContext.getBufferSize() > 0", method)

    def test_native_bridges_use_unknown_size_and_session_guarding(self):
        ijk_source = self.read("ijkplayer/IJKPullMediaSource.java")
        system_source = self.read("gsy/SagePullMediaDataSource.java")
        self.assertIn("return effectivelyGrowing ? -1L : dataSource.size();", ijk_source)
        self.assertIn("GROWING_EDGE_WAIT_MS = 10_000L", ijk_source)
        self.assertIn("GROWING_EDGE_WAIT_MS)", ijk_source)
        self.assertIn("return effectivelyGrowing ? -1L : active.size();", system_source)
        self.assertIn("GROWING_EDGE_WAIT_MS = 10_000L", system_source)
        self.assertIn("GROWING_EDGE_WAIT_MS)", system_source)

        for relative in (
            "ijkplayer/IJKMediaPlayerImpl.java",
            "gsy/GSYSystemMediaPlayerImpl.java",
        ):
            player_source = self.read(relative)
            self.assertIn("final PlaybackSessionController.Token listenerSession", player_source)
            self.assertIn("player != listenerPlayer", player_source)

        ijk_player = self.read("ijkplayer/IJKMediaPlayerImpl.java")
        self.assertIn("IMediaPlayer activePlayer = player;", ijk_player)
        self.assertIn("if (activePlayer == null) return 0L;", ijk_player)

    def test_stock_ts_growth_guess_is_probed_before_publishing_unknown_length(self):
        policy = self.read("GrowingPlaybackSourcePolicy.java")
        self.assertIn("LEGACY_GROWTH_PROBE_MS = 750L", policy)
        self.assertIn("if (metadataExplicit) return true;", policy)
        self.assertIn("waitForGrowth(size, LEGACY_GROWTH_PROBE_MS)", policy)
        self.assertIn('"legacy_growth_classified"', policy)

        media_cmd = (
            ROOT
            / "source/dev/core/src/main/java/opensagetv/vibe/miniclient/MediaCmd.java"
        ).read_text(encoding="utf-8")
        self.assertIn(
            "playa.setServerMediaMetadataExplicit(mediaContext.isExplicit());",
            media_cmd,
        )


if __name__ == "__main__":
    unittest.main()
