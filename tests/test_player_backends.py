from __future__ import annotations

from pathlib import Path
import hashlib
import os
import unittest

ROOT = Path(__file__).resolve().parents[1]
DEV = ROOT / "source/dev"
EXISTING = ROOT / "source/existing"
FROZEN_BASELINE = ROOT / "source/FROZEN_BASELINE.sha256"
SHARED = DEV / "android-shared/src/main/java/opensagetv/vibe/miniclient/android"
RES = DEV / "android-shared/src/main/res"


def unified_dockerfile() -> Path:
    candidates = []
    if os.environ.get("OPENSAGETV_VIBE_BUILD_ENV_ROOT"):
        candidates.append(Path(os.environ["OPENSAGETV_VIBE_BUILD_ENV_ROOT"]) / "Dockerfile")
    candidates.extend(
        (
            ROOT.parent / "opensagetv-vibe-build-env" / "Dockerfile",
            ROOT.parent / "release-manifest" / "Dockerfile",
        )
    )
    for candidate in candidates:
        if candidate.is_file():
            return candidate
    raise AssertionError("opensagetv-vibe-build-env/Dockerfile is unavailable")


class PlayerBackendRefactorTests(unittest.TestCase):
    def test_base_player_matches_baseline_or_reviewed_stock_server_fullscreen_patch(self):
        rel = "android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/BaseMediaPlayerImpl.java"
        baseline_rel = "android-shared/src/main/java/sagex/miniclient/android/video/BaseMediaPlayerImpl.java"
        dev_bytes = (DEV / rel).read_bytes().replace(
            b"opensagetv.vibe.miniclient", b"sagex.miniclient"
        )
        dev_hash = hashlib.sha256(dev_bytes).hexdigest()
        existing_file = EXISTING / baseline_rel
        if existing_file.exists():
            baseline_hash = hashlib.sha256(existing_file.read_bytes()).hexdigest()
        else:
            hashes = {}
            for raw in FROZEN_BASELINE.read_text(encoding="ascii").splitlines():
                line = raw.strip()
                if not line or line.startswith("#"):
                    continue
                digest, path = line.split(None, 1)
                hashes[path.lstrip("*").replace("\\", "/")] = digest.lower()
            baseline_hash = hashes.get(baseline_rel)
        self.assertIsNotNone(baseline_hash, rel)
        # Reviewed exceptions to the frozen v0.5.75 player base are the
        # datasource-neutral embedded-preview promotion, clearing the old
        # session's EOS flag after release during a new load, and the reviewed
        # state-aware delayed promotion fix, and the explicitly authorized
        # generation-based session controller, plus the capability-negotiated
        # DVD highlight/metadata adapter. Keep full-file hashes so
        # unrelated playback changes cannot hide in any exception.
        reviewed_fullscreen_hash = "d270708006d02379b2ebcbc2ff5390a17d303ab22cf7072c31de9ab43adea3b4"
        reviewed_push_load_hash = "a4a2cd8ca0ffca84cc9b5dccb8681d5acabbdf3df7a8e3798e464d7d31e18494"
        reviewed_state_aware_hash = "5697071e39aa4fa5366a8142609539b7fa1745968c597395ea7cb809d6d19b45"
        reviewed_session_controller_hash = "acfb0c04c5dc3c967a5eaac9b814a5274fbe1534f3fd8fadf1c7067bd5f0a0a8"
        reviewed_dvd_adapter_hash = "8665d330cf3864e1564a1bcb4e1d5ac5d718816bbf15985c9bd5e86729b6f253"
        # The current reviewed form combines the generation-based session
        # controller/fullscreen guard with the bounded DVD navigation adapter.
        # Keep this as an exact full-file hash so unrelated player changes
        # still fail this characterization gate.
        reviewed_session_dvd_adapter_hash = "dca5462451aa78336e10fa4d29b7ac3364e304a14259af0e74021fa3fabfcc7d"
        # Adds only a defensive copy of the bounded DVD SPU counters for
        # debug/MCP snapshots; playback behavior remains in the reviewed DVD
        # adapter above.
        reviewed_dvd_spu_diagnostics_hash = "63fc269f13e7e5a75f4e55b8e3b39016e9056ffc6c71baf117e6c8b596ebc9dd"
        # Negotiated Hybrid DVD transport flag plus DVB-subtitle selector
        # routing; the native DVD SPU path remains unchanged.
        reviewed_dvd_mim_transport_hash = "5f3222fe3ae48b589793b74c33c93348a743f5c48c869754b1c09d97836dd2e8"
        # Makes a push: OPENURL authoritative during a Hybrid DVD transition,
        # preventing a synchronous old-session release from downgrading the
        # replacement player to a null/Pull datasource.
        reviewed_dvd_hybrid_push_guard_hash = "a9f89123ce37b9cf0bbe9071f18a6bdd8dc670a1dc6ecaf1870b212e5428529d"
        # Current reviewed form also carries the stock-server caption fallback
        # and bounded display-refresh reload routing. Both remain capability
        # gated and preserve ordinary player transport behavior.
        reviewed_caption_refresh_hash = "61f477c818b640ac2f4cba547baa5db38717319efe2104314e1bbb2ff97c5afe"
        # Adds the reviewed per-load caption-state reset hook used by the
        # extractor-backed legacy-extender subtitle callback bridge.
        reviewed_legacy_caption_bridge_hash = "f34e82d2118603672a02ef1a5141603d0387f3c21b53ea0aa989706b3f5337f8"
        # Prevents the previous player's transient EOS state from leaking
        # through GETMEDIATIME while OPENURL replaces the player on Android's
        # UI thread. Stock SageTV otherwise paints the startup timeline at its
        # end before the first frame resets the clock to zero.
        reviewed_load_transition_time_guard_hash = "e43f76c591d802b31eab760018e8c5985596ce67f52e990aa695b7ab2e1fe712"
        # Preserves the loaded playback generation across SageTV's nonterminal
        # STOP so a later SEEK/PLAY restart reaches the retained backend.
        # DEINIT/FREE remains the stale-callback invalidation boundary.
        reviewed_stock_stop_restart_hash = "a24f1d8c24b921de8d98962d7a018fbf235b6533405a5b9aab356cf004232bd7"
        # Adds only the nonblocking Always-mode diagnostic checkpoint at the
        # established SageTV STOP boundary. The retained-player/session logic
        # above is unchanged; the spool coalesces work on its own I/O owner.
        reviewed_diagnostic_stop_checkpoint_hash = "dc7a77d1ec8cf323e6ea2b5ae9380fdc26a078574416725c7cc13593de4fc12e"
        # Serializes and bounds stock-STV preview promotion retries when PLAY
        # and SETVIDEORECT arrive before a slower device's decoder is ready.
        # The playback-generation guard still permits exactly one TV toggle.
        reviewed_bounded_fullscreen_retry_hash = "1fe3cbea3cf8d22e9a473d1e1f3df6f3676947a4ae24e6c125dada888c17bba3"
        # Adds the datasource-neutral DVB Teletext subtitle session, local
        # overlay, and stock-server CC1/CC2 callback mapping. Decoder-specific
        # video/audio transport remains unchanged.
        # Adds lifecycle-safe Teletext presentation cleanup and bounded debug
        # state; local subtitle overlays cannot survive STOP/EOS or a newer
        # playback generation.
        reviewed_teletext_subtitle_hash = "e88007a3567707106a49a81d0ed850b9c2ba866a696ed62bd9286070e7489048"
        # Adds only stream-aware virtual CC1/CC2 resolution on top of the
        # reviewed Teletext session. It delegates actual selection to each
        # existing backend and does not alter video/audio transport.
        reviewed_virtual_caption_slot_hash = "7c1d584a4b9c91b04a686402dfcbdb258d36d07fcf0a6f1b4f2a16cf0a46cb0e"
        # Ports the stock HD300 command-36 MPEG-TS subpicture PID selection
        # into the existing local DVB/Teletext renderer without changing DVD.
        reviewed_legacy_dvb_subpicture_hash = "4b4c70e3728f3c9e58964b2b0cf5784a31d430e4ad06deb7600b30b0116e19b7"
        # Current reviewed caption-session implementation, including the
        # startup re-resolution and stock command-36 DVB bridge.
        reviewed_caption_session_current_hash = "49a34f00f0abd95dc169f06ae9b835ee805fc529e4f6deeedb108dddb607a46b"
        # Applies an explicit CC1/CC2 type mapping (for example CC1 -> DVB)
        # when stock SageTV publishes VIDEO_CC_STATE, including the async
        # track-discovery and live-settings re-resolution paths.
        reviewed_stv_caption_slot_mapping_hash = "f70e707942f2492eac331623e47183d501712b8f7cced6a4dfaee3d113ff7e24"
        # Separates STV broadcast CC resolution from SRT/PGS/DVD subtitle
        # preference and prevents CC from depending on Subtitles selection.
        reviewed_broadcast_cc_separation_hash = "709df6c7d2cec94a12a109be55cd3b145897531e3c842a3c7b6c7ad381647689"
        # Adds evidence-based Auto resolution: synthetic CEA tracks are not
        # selected until the decoder observes CEA samples; UK DVB/Teletext
        # streams therefore reach their real broadcast caption service.
        reviewed_broadcast_cc_evidence_fallback_hash = "42a1ee6c51bfd5e732d4398c9ded5e2ff00831aa356d90cdab4d12eb8708cd80"
        # Current reviewed caption-session form makes explicit local DVB own
        # the bitmap renderer while CC1/CC2 remain text-caption slots. This
        # was physically gated on stock .175/non-Pro .25 before the audio
        # output work and does not alter player audio/video transport.
        reviewed_explicit_dvb_caption_owner_hash = "6ac992f59c957abb50aba034ceeea9aec808c27a9f2127ea6689e7f6aacbfe68"
        # Exposes the already-selected negotiated DVD MIM transport to bounded
        # diagnostics. Playback, datasource ownership, and decoder behavior are
        # unchanged; this is a read-only getter over the OPENURL-derived flag.
        reviewed_dvd_mim_diagnostics_hash = "44e6f299820e26debf4e892eb1b99ad93f9a1aefb4a797901a16ccd4c2765bd3"
        self.assertIn(dev_hash, {
            baseline_hash,
            reviewed_fullscreen_hash,
            reviewed_push_load_hash,
            reviewed_state_aware_hash,
            reviewed_session_controller_hash,
            reviewed_dvd_adapter_hash,
            reviewed_session_dvd_adapter_hash,
            reviewed_dvd_spu_diagnostics_hash,
            reviewed_dvd_mim_transport_hash,
            reviewed_dvd_hybrid_push_guard_hash,
            reviewed_caption_refresh_hash,
            reviewed_legacy_caption_bridge_hash,
            reviewed_load_transition_time_guard_hash,
            reviewed_stock_stop_restart_hash,
            reviewed_diagnostic_stop_checkpoint_hash,
            reviewed_bounded_fullscreen_retry_hash,
            reviewed_teletext_subtitle_hash,
            reviewed_virtual_caption_slot_hash,
            reviewed_legacy_dvb_subpicture_hash,
            reviewed_caption_session_current_hash,
            reviewed_stv_caption_slot_mapping_hash,
            reviewed_broadcast_cc_separation_hash,
            reviewed_broadcast_cc_evidence_fallback_hash,
            reviewed_explicit_dvb_caption_owner_hash,
            reviewed_dvd_mim_diagnostics_hash,
        }, rel)

    def test_four_backends_have_stable_preference_values(self):
        text = (SHARED / "video/PlayerBackend.java").read_text(encoding="utf-8")
        self.assertIn('EXOPLAYER("exoplayer", "ExoPlayer Legacy")', text)
        self.assertIn('MEDIA3("media3", "Media3 ExoPlayer")', text)
        self.assertIn('IJKPLAYER("ijkplayer", "IJKPlayer")', text)
        self.assertIn('GSYPLAYER("gsyplayer", "GSYVideoPlayer")', text)
        self.assertIn('DEFAULT_PREFERENCE = "exoplayer"', text)
        self.assertIn('MEDIA3_VERSION = "1.11.0"', text)
        self.assertIn('GSY_VERSION = "13.1.0"', text)

    def test_factory_is_only_backend_construction_switch(self):
        factory = (SHARED / "video/PlayerFactory.java").read_text(encoding="utf-8")
        opengl = (SHARED / "opengl/OpenGLRenderer.java").read_text(encoding="utf-8")
        gdx = (SHARED / "gdx/MiniClientGDXRenderer.java").read_text(encoding="utf-8")
        self.assertIn("new Exo2MediaPlayerImpl(activity)", factory)
        self.assertIn("new Media3MediaPlayerImpl(activity)", factory)
        self.assertIn("new IJKMediaPlayerImpl(activity)", factory)
        self.assertIn("new GSYMediaPlayerImpl(activity)", factory)
        for renderer in (opengl, gdx):
            self.assertIn("PlayerFactory.create", renderer)
            self.assertNotIn("new Exo2MediaPlayerImpl", renderer)
            self.assertNotIn("new IJKMediaPlayerImpl", renderer)

    def test_shared_decoding_method_has_requested_names_and_hardware_default(self):
        method = (SHARED / "video/DecodingMethod.java").read_text(encoding="utf-8")
        arrays = (RES / "values/arrays.xml").read_text(encoding="utf-8")
        prefs = (RES / "xml/prefs.xml").read_text(encoding="utf-8")
        self.assertIn('HARDWARE("hardware", "Hardware")', method)
        self.assertIn('SOFTWARE("software", "Software")', method)
        self.assertIn('HARDWARE_PREFERRED("hardware_preferred", "Fallback")', method)
        self.assertIn('DEFAULT_PREFERENCE = "hardware"', method)
        for label in ("Hardware", "Software", "Fallback"):
            self.assertIn(f"<item>{label}</item>", arrays)
        for value in ("hardware", "software", "hardware_preferred"):
            self.assertIn(f"<item>{value}</item>", arrays)
        self.assertIn('android:key="decoding_method"', prefs)
        self.assertIn('android:defaultValue="hardware"', prefs)

    def test_shared_decoding_method_is_visible_on_all_player_settings_pages(self):
        for filename in (
            "exoplayer_prefs.xml",
            "media3player_prefs.xml",
            "ijkplayer_prefs.xml",
            "gsyplayer_prefs.xml",
        ):
            text = (RES / f"xml/{filename}").read_text(encoding="utf-8")
            self.assertIn('android:key="decoding_method"', text, filename)
            self.assertIn('android:defaultValue="hardware"', text, filename)
            self.assertIn('@array/entries_list_decoding_method', text, filename)
            self.assertIn('@array/entryvalues_list_decoding_method', text, filename)

    def test_legacy_exoplayer_uses_shared_decoding_policy_with_bounded_telemetry(self):
        player = (SHARED / "video/exoplayer2/Exo2MediaPlayerImpl.java").read_text(encoding="utf-8")
        selector = (SHARED / "video/exoplayer2/CustomMediaCodecSelector.java").read_text(encoding="utf-8")
        self.assertIn("PrefStore.Keys.decoding_method", player)
        self.assertIn("new CustomMediaCodecSelector(decodingMethod,", player)
        self.assertIn("decoderAttemptTelemetry", player)
        self.assertIn("sessionDecoderExclusions", player)
        self.assertIn("getDecoderCandidatesForDebug", player)
        self.assertIn("decoder-excluded-reprepare-started", player)
        self.assertIn("sessionDecoderExclusions.add(failedDecoder)", player)
        self.assertIn("onAudioUnderrun", player)
        self.assertIn("onAudioSinkError", player)
        self.assertIn("onDownstreamFormatChanged", player)
        self.assertIn("recordTrackChange", player)
        self.assertIn("setEnableDecoderFallback(true)", player)
        self.assertIn("Hardware stays hardware-only", player)
        self.assertIn("case SOFTWARE:", selector)
        self.assertIn("case HARDWARE_PREFERRED:", selector)
        self.assertIn("case HARDWARE:", selector)
        self.assertNotIn("PlayerTelemetry", player)

    def test_media3_backend_uses_shared_decoding_policy_and_bounded_telemetry(self):
        player = (SHARED / "video/media3/Media3MediaPlayerImpl.java").read_text(encoding="utf-8")
        selector = (SHARED / "video/media3/Media3CodecSelector.java").read_text(encoding="utf-8")
        self.assertIn("androidx.media3.exoplayer.ExoPlayer", player)
        self.assertIn("Media3PushDataSource", player)
        self.assertIn("Media3PullDataSource", player)
        self.assertIn("new Media3CodecSelector(decodingMethod,", player)
        self.assertIn("decoderAttemptTelemetry", player)
        self.assertIn("sessionDecoderExclusions", player)
        self.assertIn("getDecoderCandidatesForDebug", player)
        self.assertIn("decoder-excluded-reprepare-started", player)
        self.assertIn("sessionDecoderExclusions.add(failedDecoder)", player)
        self.assertIn("onAudioUnderrun", player)
        self.assertIn("onAudioSinkError", player)
        self.assertIn("onDownstreamFormatChanged", player)
        self.assertIn("recordTrackChange", player)
        self.assertIn("setEnableDecoderFallback(true)", player)
        self.assertIn("Hardware stays hardware-only", player)
        self.assertIn("case SOFTWARE:", selector)
        self.assertIn("case HARDWARE_PREFERRED:", selector)
        self.assertNotIn("PlayerTelemetry", player)
        self.assertNotIn("FfmpegLibrary", player)
        self.assertNotIn("com.google.android.exoplayer2", player)

    def test_media3_track_selection_resolves_renderer_index(self):
        player = (SHARED / "video/media3/Media3MediaPlayerImpl.java").read_text(encoding="utf-8")
        self.assertIn("findRendererIndex(trackInfo, trackType)", player)
        self.assertIn("findRendererIndex(mappedTrackInfo, RenderType)", player)
        self.assertIn("trackInfo.getRendererType(rendererIndex) == trackType", player)
        self.assertIn("getTrackSupport(rendererIndex, groupIndex, trackIndex)", player)
        self.assertNotIn("getTrackGroups(trackType)", player)

    def test_project_version_is_current(self):
        self.assertEqual((ROOT / "VERSION").read_text(encoding="utf-8").strip(), "0.5.93")

    def test_gsy_does_not_merge_unused_cast_or_media_session_surface(self):
        gradle = (DEV / "android-shared/build.gradle").read_text(encoding="utf-8")
        gsy = gradle.split('api("io.github.carguo:gsyvideoplayer-exo2:', 1)[1]
        gsy = gsy.split("// logging", 1)[0]
        self.assertIn('exclude group: "androidx.media3", module: "media3-cast"', gsy)
        self.assertIn('exclude group: "androidx.media3", module: "media3-session"', gsy)



    def test_player_push_datasources_block_until_data_or_eos(self):
        core_push = (DEV / "core/src/main/java/opensagetv/vibe/miniclient/net/PushBufferDataSource.java").read_text(encoding="utf-8")
        self.assertIn("public int readBlocking(long readOffset", core_push)
        self.assertIn("if (len == 0)", core_push)
        self.assertIn("dataAvailableMonitor.wait();", core_push)
        self.assertNotIn("Thread.sleep(50);", core_push)
        self.assertIn("signalDataAvailable();", core_push)
        self.assertIn("return -1;", core_push)

        for rel in (
            "video/exoplayer2/Exo2PushDataSource.java",
            "video/media3/Media3PushDataSource.java",
        ):
            text = (SHARED / rel).read_text(encoding="utf-8")
            self.assertIn("return readBlocking(0, buffer, offset, readLength);", text, rel)
            self.assertNotIn("return read(0, buffer, offset, readLength);", text, rel)

        ijk = (SHARED / "video/ijkplayer/IJKPushMediaSource.java").read_text(encoding="utf-8")
        self.assertIn("return local.readBlocking(position, bytes, offset, size);", ijk)
        self.assertNotIn("Thread.sleep(50);", ijk)
        self.assertIn("sourceMonitor.wait();", ijk)
        self.assertIn("sourceMonitor.notifyAll();", ijk)
        self.assertIn("if (size == 0) return 0;", ijk)
        self.assertNotIn("if (VerboseLogging.DATASOURCE_LOGGING)\n\n        // ijkmediasource", ijk)
        self.assertIn('if (local == null) throw new IOException("IJK PUSH datasource is released")', ijk)

    def test_new_load_clears_previous_session_eos_after_release(self):
        base = (SHARED / "video/BaseMediaPlayerImpl.java").read_text(encoding="utf-8")
        load = base.split("public void load(", 1)[1].split("protected abstract void setupPlayer", 1)[0]
        release = base.split("protected void releasePlayer()", 1)[1].split(
            "protected void applySavedRefreshRateIfPossible", 1
        )[0]
        media_time = base.split("public long getMediaTimeMillis(long lastServerTime)", 1)[1].split(
            "public int getState()", 1
        )[0]
        # releasePlayer() intentionally marks a genuinely ended session EOS.
        # During both load paths, however, the old player is released after the
        # new OPENURL session exists. The owning transition token must suppress
        # that stale EOS and make concurrent GETMEDIATIME return zero.
        self.assertEqual(load.count("releasePlayer();"), 2)
        self.assertEqual(load.count("loadTransitionToken = loadSession;"), 1)
        self.assertEqual(load.count("finishLoadTransition(loadSession);"), 2)
        self.assertEqual(load.count("eos = false;"), 3)
        self.assertIn("if (loadTransitionToken != null)\n            return 0;", media_time)
        self.assertIn("if (loadTransitionToken == null)", release)
        self.assertIn("if (loadTransitionToken == loadSession)", base)

    def test_phase_a_exo_seek_is_async_allows_zero_and_completes_on_seek_discontinuity(self):
        for rel, owner in (
            ("video/exoplayer2/Exo2MediaPlayerImpl.java", "Exo2MediaPlayerImpl"),
            ("video/media3/Media3MediaPlayerImpl.java", "Media3MediaPlayerImpl"),
        ):
            text = (SHARED / rel).read_text(encoding="utf-8")
            seek_impl = text.split("private void seekToImpl(long timeInMillis)", 1)[1].split("public void seek(long timeInMS)", 1)[0]
            self.assertIn("if (timeInMillis >= 0)", seek_impl, rel)
            self.assertRegex(
                seek_impl,
                r"PlaybackSeekPolicy\.clamp\(\s*timeInMillis,\s*durationMs,\s*mediaContext\.isTimeshifted\(\)\)",
                rel,
            )
            self.assertIn("player.seekTo(safePositionMs);", seek_impl, rel)
            self.assertIn("armPullSeekRecovery(safePositionMs);", seek_impl, rel)
            self.assertNotIn("Thread.sleep", seek_impl, rel)
            self.assertNotIn("while (player.getCurrentPosition() < timeInMillis", seek_impl, rel)

    def test_smb_seek_reprepares_to_flush_stale_decoder_frames(self):
        for rel, pull_class in (
            ("video/exoplayer2/Exo2MediaPlayerImpl.java", "Exo2PullDataSource"),
            ("video/media3/Media3MediaPlayerImpl.java", "Media3PullDataSource"),
        ):
            text = (SHARED / rel).read_text(encoding="utf-8")
            seek_impl = text.split("private void seekToImpl(long timeInMillis)", 1)[1].split(
                "public void seek(long timeInMS)", 1
            )[0]
            self.assertIn(f"dataSource instanceof {pull_class}", seek_impl, rel)
            self.assertIn("isSmbModeConfigured()", seek_impl, rel)
            self.assertIn('PlaybackDebugTrap.record("smb_seek_reprepare_before"', seek_impl, rel)
            self.assertIn("player.setMediaSource(mediaSource, safePositionMs);", seek_impl, rel)
            self.assertIn("player.prepare();", seek_impl, rel)
            self.assertIn("player.setPlayWhenReady(resumeWhenReady);", seek_impl, rel)

            self.assertIn("private final PlaybackMediaContext mediaContext", text, rel)
            self.assertIn("mediaContext.update(majorHint, minorHint, encodingHint, timeshifted, bufferSize);", text, rel)

            timeline = text.split("public void onTimelineChanged", 1)[1].split("public void onPositionDiscontinuity", 1)[0]
            self.assertNotIn("seekPending = false", timeline, rel)

            discontinuity = text.split("public void onPositionDiscontinuity", 1)[1].split("public void onVideoSizeChanged", 1)[0]
            self.assertIn("case Player.DISCONTINUITY_REASON_SEEK:", discontinuity, rel)
            self.assertIn("setPlaybackPosition(newPosition.positionMs);", discontinuity, rel)
            self.assertIn("updateMediaSessionPlaybackState(newPosition.positionMs);", discontinuity, rel)
            self.assertIn("seekPending = false;", discontinuity, rel)


    def test_phase_a_pull_resume_latency_uses_larger_reads_and_low_latency_load_control(self):
        buffered = (DEV / "core/src/main/java/opensagetv/vibe/miniclient/net/BufferedPullDataSource.java").read_text(encoding="utf-8")
        self.assertIn("private final int bufferSize;", buffered)
        self.assertIn("public BufferedPullDataSource(String host, int bufferSize)", buffered)
        self.assertIn("this.bufferSize = Math.max(MAX_BUFFER, bufferSize);", buffered)
        self.assertIn("_buffer = new byte[bufferSize];", buffered)
        self.assertIn("return super.read(position, _buffer, 0, bufferSize);", buffered)

        runtime_tuning = (SHARED / "video/PlayerRuntimeTuning.java").read_text(encoding="utf-8")
        self.assertIn("DEFAULT_EXO2_PULL_READ_BYTES = 512 * 1024", runtime_tuning)
        self.assertIn("DEFAULT_MEDIA3_PULL_READ_BYTES = 256 * 1024", runtime_tuning)
        for rel in (
            "video/exoplayer2/Exo2PullDataSource.java",
            "video/media3/Media3PullDataSource.java",
        ):
            text = (SHARED / rel).read_text(encoding="utf-8")
            self.assertIn("private final int pullReadBytes;", text, rel)
            self.assertIn("new RetainedBufferedPullDataSource(host, pullReadBytes)", text, rel)
            self.assertIn("setProbeCacheEnabled(!effectivelyGrowing)", text, rel)

        retained = (DEV / "core/src/main/java/opensagetv/vibe/miniclient/net/RetainedBufferedPullDataSource.java").read_text(encoding="utf-8")
        cache = (DEV / "core/src/main/java/opensagetv/vibe/miniclient/net/BoundedReadCache.java").read_text(encoding="utf-8")
        self.assertIn("MAXIMUM_CACHE_BYTES = 8L * 1024L * 1024L", retained)
        self.assertIn("long refreshedSize = refreshSize();", retained)
        self.assertIn("if (refreshedSize != observedSize)", retained)
        self.assertIn("public synchronized void close() { flush(); }", retained)
        self.assertIn("public synchronized void release()", retained)
        self.assertIn("new LinkedHashMap<Long, byte[]>(16, 0.75f, true)", cache)

        media3_pull = (SHARED / "video/media3/Media3PullDataSource.java").read_text(encoding="utf-8")
        self.assertIn("v0.5.32 1 MiB experiment did not", media3_pull)
        self.assertIn("getNetworkReadCount()", media3_pull)
        self.assertIn("getOpenWaitMs()", media3_pull)
        self.assertIn("hasReachedEndOfInput()", media3_pull)
        self.assertIn("((GrowingDataSource) dataSource).waitForGrowth(startPos, 2000)", media3_pull)

        exo2_pull = (SHARED / "video/exoplayer2/Exo2PullDataSource.java").read_text(encoding="utf-8")
        self.assertIn("getNetworkReadCount()", exo2_pull)
        self.assertIn("getOpenWaitMs()", exo2_pull)
        self.assertIn("accumulateAndCloseDataSource()", exo2_pull)
        self.assertIn("((GrowingDataSource) dataSource).waitForGrowth(startPos, 2000)", exo2_pull)
        for text, name in ((media3_pull, "Media3"), (exo2_pull, "Exo2")):
            self.assertIn("returned zero bytes for non-zero read", text, name)
            self.assertIn("closed during non-zero read", text, name)

        expectations = {
            "video/exoplayer2/Exo2MediaPlayerImpl.java": "com.google.android.exoplayer2.DefaultLoadControl",
            "video/media3/Media3MediaPlayerImpl.java": "androidx.media3.exoplayer.DefaultLoadControl",
        }
        for rel, load_control_import in expectations.items():
            text = (SHARED / rel).read_text(encoding="utf-8")
            self.assertIn("import " + load_control_import + ";", text, rel)
            self.assertIn("runtimeConfig.getPullMinBufferMs()", text, rel)
            self.assertIn("runtimeConfig.getPullMaxBufferMs()", text, rel)
            self.assertIn("runtimeConfig.getPullPlaybackBufferMs()", text, rel)
            self.assertIn("runtimeConfig.getPullRebufferMs()", text, rel)
            self.assertIn("new DefaultLoadControl.Builder()", text, rel)
            self.assertIn(".setBufferDurationsMs(", text, rel)
            self.assertIn(".setPrioritizeTimeOverSizeThresholds(true)", text, rel)
            self.assertIn("builder.setLoadControl(pullLoadControl);", text, rel)
            self.assertIn("Pull playback state=", text, rel)

        media3_player = (SHARED / "video/media3/Media3MediaPlayerImpl.java").read_text(encoding="utf-8")
        self.assertIn("promoteConfirmedPullTailToEos();", media3_player)
        self.assertIn("pull_confirmed_eof_promoted", media3_player)

    def test_phase_a_followup_pull_datasources_return_requested_range_length(self):
        for rel in (
            "video/exoplayer2/Exo2PullDataSource.java",
            "video/media3/Media3PullDataSource.java",
        ):
            text = (SHARED / rel).read_text(encoding="utf-8")
            self.assertIn("private long bytesRemaining = C.LENGTH_UNSET;", text, rel)
            self.assertIn("bytesRemaining = Math.max(0, size - dataSpec.position);", text, rel)
            self.assertIn("if (dataSpec.length != C.LENGTH_UNSET)", text, rel)
            self.assertIn("return reportedLength;", text, rel)
            self.assertNotIn("return size;", text, rel)
            self.assertIn("int bytesToRead = bytesRemaining == C.LENGTH_UNSET", text, rel)
            self.assertIn("bytesRemaining -= bytes;", text, rel)


    def test_phase_a_pull_exo_seek_uses_tuned_progressive_extractors(self):
        expectations = {
            "video/exoplayer2/Exo2MediaPlayerImpl.java": (
                "com.google.android.exoplayer2.extractor.DefaultExtractorsFactory",
                "com.google.android.exoplayer2.extractor.ts.TsExtractor",
                "com.google.android.exoplayer2.SeekParameters",
            ),
            "video/media3/Media3MediaPlayerImpl.java": (
                "androidx.media3.extractor.DefaultExtractorsFactory",
                "androidx.media3.extractor.ts.TsExtractor",
                "androidx.media3.exoplayer.SeekParameters",
            ),
        }
        for rel, imports in expectations.items():
            text = (SHARED / rel).read_text(encoding="utf-8")
            for imported in imports:
                self.assertIn("import " + imported + ";", text, rel)
            self.assertIn("runtimeConfig.getTsSearchMultiplier()", text, rel)
            self.assertIn("setTsExtractorTimestampSearchBytes", text, rel)
            self.assertIn("setConstantBitrateSeekingEnabled(true)", text, rel)
            self.assertIn("new ProgressiveMediaSource.Factory(dataSourceFactory, extractorsFactory)", text, rel)
            self.assertIn("player.setSeekParameters(SeekParameters.CLOSEST_SYNC);", text, rel)
            self.assertIn("pull_seek_policy_", text, rel)
            self.assertIn("Pull seek capability: seekable=", text, rel)
            pull_tuning = text.split("if (!pushMode)", 1)[1]
            self.assertIn("createCaptionAwareExtractorsFactory(true)", pull_tuning, rel)
            self.assertIn("withPassthroughOffset", pull_tuning, rel)

        exo2 = (SHARED / "video/exoplayer2/Exo2MediaPlayerImpl.java").read_text(encoding="utf-8")
        self.assertIn("SeekParameters.NEXT_SYNC", exo2)
        self.assertIn("SeekParameters.PREVIOUS_SYNC", exo2)
        self.assertIn("runtimeConfig.getDirectionalSyncMinDeltaMs()", exo2)
        self.assertIn("runtimeConfig.getSeekRecoveryDelayMs()", exo2)
        self.assertIn("pull_seek_recovery_armed", exo2)
        self.assertIn("pull_seek_recovery_deferred_active_io", exo2)
        self.assertIn("getLastPhysicalReadMonotonicMs()", exo2)
        self.assertIn("PullSeekRecoveryPolicy.shouldDeferForActiveIo(", exo2)
        self.assertIn("pull_seek_reprepare_before", exo2)
        self.assertIn("player.setMediaSource(mediaSource, targetPositionMs);", exo2)

        media3 = (SHARED / "video/media3/Media3MediaPlayerImpl.java").read_text(encoding="utf-8")
        choose_media3 = media3.split("private SeekParameters choosePullSeekParameters", 1)[1].split("private void armPullSeekRecovery", 1)[0]
        self.assertIn("runtimeConfig.getSeekPolicy()", choose_media3)
        self.assertIn("PlaybackSyncPointPolicy.choose(", choose_media3)
        self.assertIn("SeekParameters.NEXT_SYNC", choose_media3)
        self.assertIn("SeekParameters.PREVIOUS_SYNC", choose_media3)
        self.assertIn("return SeekParameters.CLOSEST_SYNC;", choose_media3)
        arm_media3 = media3.split("private void armPullSeekRecovery", 1)[1].split("private void seekToImpl", 1)[0]
        self.assertIn("if (!runtimeConfig.isSeekRecoveryEnabled()", arm_media3)
        self.assertIn("pull_seek_recovery_deferred_active_io", arm_media3)
        self.assertIn("getLastPhysicalReadMonotonicMs()", arm_media3)
        self.assertIn("PullSeekRecoveryPolicy.shouldDeferForActiveIo(", arm_media3)

    def test_pull_ts_extractors_declare_broadcast_caption_services(self):
        exo2 = (SHARED / "video/exoplayer2/Exo2MediaPlayerImpl.java").read_text(encoding="utf-8")
        media3 = (SHARED / "video/media3/Media3MediaPlayerImpl.java").read_text(encoding="utf-8")

        for text, rel in ((exo2, "legacy ExoPlayer"), (media3, "Media3")):
            self.assertIn("FLAG_OVERRIDE_CAPTION_DESCRIPTORS", text, rel)
            self.assertIn("APPLICATION_CEA608", text, rel)
            self.assertIn("APPLICATION_CEA708", text, rel)
            self.assertIn(".setAccessibilityChannel(", text, rel)
            self.assertIn("preferred_caption_service", text, rel)
            self.assertIn("preferredCaptionCodec", text, rel)

        self.assertIn("createCaptionAwareExtractorsFactory(boolean pullMode)", exo2)
        self.assertIn("new DefaultTsPayloadReaderFactory(tsFlags, captionFormats)", exo2)
        self.assertIn("extractors[i] instanceof TsExtractor", exo2)
        self.assertIn("setTsSubtitleFormats(captionFormats)", media3)
        self.assertIn("createCaptionAwareExtractorsFactory(false)", exo2)
        self.assertIn("createCaptionAwareExtractorsFactory(false)", media3)

    def test_phase_a_followup_pull_flush_does_not_reset_player_to_zero(self):
        for rel in (
            "video/exoplayer2/Exo2MediaPlayerImpl.java",
            "video/media3/Media3MediaPlayerImpl.java",
        ):
            text = (SHARED / rel).read_text(encoding="utf-8")
            flush = text.split("public synchronized void flush()", 1)[1].split("protected void setupPlayer", 1)[0]
            self.assertIn("if (!pushMode)", flush, rel)
            self.assertIn("Ignoring destructive player reset for Pull-mode flush", flush, rel)
            pull_guard = flush.split("if (!pushMode)", 1)[1].split("super.flush();", 1)[0]
            self.assertIn("flushed = false;", pull_guard, rel)
            self.assertIn("return;", pull_guard, rel)
            self.assertIn("player.setMediaSource(mediaSource, true);", flush, rel)

    def test_gsy_auto_prefers_media3_and_system_bridge_is_hardened(self):
        gsy = (SHARED / "video/gsy/GSYMediaPlayerImpl.java").read_text(encoding="utf-8")
        auto = gsy.split("if (resolved == GSYPlayerEngine.AUTO)", 1)[1].split("switch (resolved)", 1)[0]
        self.assertIn("resolved = GSYPlayerEngine.MEDIA3;", auto)
        self.assertNotIn("GSYPlayerEngine.SYSTEM : GSYPlayerEngine.MEDIA3", auto)

    def test_gsy_adapter_forwards_extended_player_contract(self):
        gsy = (SHARED / "video/gsy/GSYMediaPlayerImpl.java").read_text(encoding="utf-8")
        for method in (
            "setServerMediaMetadataExplicit", "hasRenderedFirstVideoFrame",
            "supportsSubtitleOffset", "setSubtitleOffsetMillis",
            "supportsTextSubtitlePresentation", "setTextSubtitlePresentation",
            "supportsAudioOffset", "setAudioOffsetMillis", "getContentFrameRateHz",
            "getAudioTrackIds", "getAudioTrackLabels", "getSelectedAudioTrack",
            "getAudioOutputSummary", "supportsAudioPassthroughControl",
            "setAudioPassthroughEnabled", "setPreferredSubtitleTrack",
            "getBufferedPlaybackAheadMillis", "dvdNewCell", "dvdSetClut",
            "dvdSetSpuControl", "isDvdMenuNavigationActive", "dvdSetStc",
            "dvdSetFormat", "dvdSetStream",
        ):
            with self.subTest(method=method):
                self.assertIn(f"{method}(", gsy)
        system_case = gsy.split("case SYSTEM:", 1)[1].split("default:", 1)[0]
        self.assertIn("return new Media3MediaPlayerImpl(context);", system_case)
        self.assertIn("PrefStore.Keys.gsy_system_probe_enabled", system_case)
        self.assertIn("new GSYSystemMediaPlayerImpl(context,", system_case)
        self.assertIn("scheduleSystemFallback", gsy)
        self.assertIn('systemFallbackReason = "android_system_player_error";', gsy)

        engine = (SHARED / "video/gsy/GSYPlayerEngine.java").read_text(encoding="utf-8")
        arrays = (RES / "values/arrays.xml").read_text(encoding="utf-8")
        self.assertIn("Android System MediaPlayer (Media3 fallback)", engine)
        self.assertIn("Android System MediaPlayer (Media3 fallback)", arrays)

        push = (SHARED / "video/gsy/SagePushMediaDataSource.java").read_text(encoding="utf-8")
        self.assertIn("return ensureOpen().readBlocking(position, buffer, offset, size);", push)
        self.assertNotIn("Thread.sleep(10);", push)

        pull = (SHARED / "video/gsy/SagePullMediaDataSource.java").read_text(encoding="utf-8")
        self.assertIn("if (closed) return;", pull)
        self.assertIn("local = source;", pull)
        self.assertIn("source = null;", pull)

        core_pull = (DEV / "core/src/main/java/opensagetv/vibe/miniclient/net/SimplePullDataSource.java").read_text(encoding="utf-8")
        self.assertIn("public synchronized void close()", core_pull)
        self.assertIn("if (position >= size) return -1;", core_pull)
        self.assertIn("Math.min((long) len, size - position)", core_pull)

    def test_phase_a_followup_gsy_system_waits_for_live_surface(self):
        text = (SHARED / "video/gsy/GSYSystemMediaPlayerImpl.java").read_text(encoding="utf-8")
        setup = text.split("protected void setupPlayer(final String sageTVurl)", 1)[1].split("@Override\n    protected void playerFailed()", 1)[0]
        self.assertNotIn("releasePlayer();", setup)
        self.assertIn("prepareWhenSurfaceReady();", setup)
        self.assertIn("SurfaceHolder.Callback", text)
        self.assertIn("holder.getSurface().isValid()", text)
        self.assertIn("player.setDisplay(holder);", text)
        self.assertIn("player.prepareAsync();", text)
        self.assertIn("player.setDisplay(null)", text)
        self.assertIn("detachSurfaceCallback();", text)
        self.assertIn("interface FailureListener", text)
        self.assertIn("failureListener.onSystemPlayerFailed(this);", text)

    def test_ijk_repeated_pause_uses_shared_frame_step_policy(self):
        text = (SHARED / "video/ijkplayer/IJKMediaPlayerImpl.java").read_text(encoding="utf-8")
        pause = text.split("public void pause()", 1)[1].split("public void play()", 1)[0]
        self.assertIn("frameStep(1)", pause)
        self.assertIn("PlaybackFrameStepPolicy.FALLBACK_FRAME_RATE", pause)
        self.assertNotIn("player.getCurrentPosition() + 1000", pause)

    def test_legacy_ijk_runtime_is_restored_and_separate_from_gsy(self):
        root_gradle = (DEV / "build.gradle").read_text(encoding="utf-8")
        shared_gradle = (DEV / "android-shared/build.gradle").read_text(encoding="utf-8")
        self.assertIn("gsyVersion = '13.1.0'", root_gradle)
        for module in ("ijkplayer-java", "ijkplayer-armv7a", "ijkplayer-arm64"):
            self.assertIn(f'api(name: "{module}-${{ijkVersionDev}}", ext: "aar")', shared_gradle)
        self.assertNotIn('api(name: "ijkplayer-x86-${ijkVersionDev}"', shared_gradle)
        self.assertNotIn('api(name: "ijkplayer-exo-${ijkVersionDev}"', shared_gradle)
        self.assertIn('io.github.carguo:gsyvideoplayer-java:${gsyVersion}', shared_gradle)
        self.assertIn('io.github.carguo:gsyvideoplayer-exo2:${gsyVersion}', shared_gradle)
        self.assertNotIn('gsyvideoplayer-ex_so', shared_gradle)
        self.assertIn('exclude group: "io.github.carguo", module: "gsyijkjava"', shared_gradle)
        self.assertIn('exclude group: "com.github.mcxinyu", module: "LibRtmp-Client-for-Android"', shared_gradle)
        self.assertIn('exclude group: "androidx.media3", module: "media3-datasource-rtmp"', shared_gradle)

    def test_tv_native_packaging_uses_complete_paired_arm_abis(self):
        tv_gradle = (DEV / "android-tv/build.gradle").read_text(encoding="utf-8")
        shared_gradle = (DEV / "android-shared/build.gradle").read_text(encoding="utf-8")
        self.assertIn("abiFilters 'armeabi-v7a', 'arm64-v8a'", tv_gradle)
        self.assertNotIn("abiFilters 'armeabi-v7a', 'arm64-v8a', 'x86'", tv_gradle)
        self.assertNotIn('natives "com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-x86"', shared_gradle)
        self.assertNotIn('natives "com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-x86_64"', shared_gradle)
        self.assertIn('include "libgdx.so"', shared_gradle)
        self.assertIn("excludes += ['**/libgdx-freetype.so']", shared_gradle)

    def test_ijk_and_gsy_are_independent_implementations(self):
        player = (SHARED / "video/ijkplayer/IJKMediaPlayerImpl.java").read_text(encoding="utf-8")
        options = (SHARED / "video/ijkplayer/IjkDecoderOptions.java").read_text(encoding="utf-8")
        gsy = (SHARED / "video/gsy/GSYMediaPlayerImpl.java").read_text(encoding="utf-8")
        engine = (SHARED / "video/gsy/GSYPlayerEngine.java").read_text(encoding="utf-8")
        system = (SHARED / "video/gsy/GSYSystemMediaPlayerImpl.java").read_text(encoding="utf-8")
        selector = (SHARED / "video/ijkplayer/CodecSelector.java").read_text(encoding="utf-8")
        self.assertIn("IjkDecoderOptions.apply", player)
        self.assertIn("PrefStore.Keys.decoding_method", options)
        self.assertNotIn("gsy_", options)
        self.assertNotIn("extends IJKMediaPlayerImpl", gsy)
        self.assertIn("implements MiniPlayerPlugin", gsy)
        self.assertIn("PrefStore.Keys.gsy_player_engine", gsy)
        for value in ('AUTO("auto"', 'MEDIA3("media3"', 'SYSTEM("system"', 'LEGACY_EXO("legacy_exo"'):
            self.assertIn(value, engine)
        self.assertIn("extends BaseMediaPlayerImpl<MediaPlayer, MediaDataSource>", system)
        self.assertIn("int safeTimeMs = timeMs <= 0 ? 0 : (timeMs >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) timeMs);", system)
        self.assertIn("player.seekTo(safeTimeMs);", system)
        self.assertNotIn("player.seekTo(timeMs);", system)
        self.assertIn("isKnownBrokenMpeg2HardwareDevice()", selector)
        self.assertIn('"mantis".equalsIgnoreCase(Build.DEVICE)', selector)
        self.assertIn('"AFTMM".equalsIgnoreCase(Build.MODEL)', selector)
        self.assertIn('return true;', player.split("setOnErrorListener", 1)[-1].split("});", 1)[0])

    def test_ijk_codec_selector_no_longer_depends_on_legacy_exoplayer_util(self):
        selector = (SHARED / "video/ijkplayer/CodecSelector.java").read_text(encoding="utf-8")
        self.assertNotIn("com.google.android.exoplayer2.util.Util", selector)
        self.assertIn("Build.VERSION.SDK_INT", selector)
        self.assertIn("Build.DEVICE", selector)
        self.assertIn("Build.MANUFACTURER", selector)

    def test_player_preferences_offer_all_four_with_legacy_exo_default(self):
        arrays = (RES / "values/arrays.xml").read_text(encoding="utf-8")
        prefs = (RES / "xml/prefs.xml").read_text(encoding="utf-8")
        self.assertIn("ExoPlayer Legacy (working baseline)", arrays)
        self.assertIn("Media3 ExoPlayer (Google current)", arrays)
        self.assertIn("IJKPlayer (Original 0.8.8 runtime)", arrays)
        self.assertIn("GSYVideoPlayer (selectable engine)", arrays)
        for value in ("exoplayer", "media3", "ijkplayer", "gsyplayer"):
            self.assertIn(f"<item>{value}</item>", arrays)
        self.assertIn('android:defaultValue="exoplayer"', prefs)
        for key in ("exoplayer_settings", "media3_settings", "ijkplayer_settings", "gsyplayer_settings"):
            self.assertIn(f'android:key="{key}"', prefs)

    def test_fixed_transcoding_offers_mpegts_for_embedded_cea_captions(self):
        arrays = (RES / "values/arrays.xml").read_text(encoding="utf-8")
        debug_config = (DEV / "android-tv/src/debug/java/opensagetv/vibe/miniclient/android/tv/debug/DebugPlayerConfigCommands.java").read_text(encoding="utf-8")
        config_values = (ROOT / "scripts/mcp_config_values.py").read_text(encoding="utf-8")
        self.assertIn("MPEG Transport Stream (MPEG-TS)", arrays)
        self.assertIn("<item>mpegts</item>", arrays)
        self.assertIn('"mpegts".equals(value)', debug_config)
        self.assertIn('FIXED_ENCODING_FORMATS = ("matroska", "dvd", "mpegts")', config_values)
        self.assertIn('"fixed_encoding_format": "mpegts"', config_values)
        prefs = (SHARED / "prefs/AndroidPrefStore.java").read_text(encoding="utf-8")
        self.assertIn('FIXED_ENCODING_FORMAT_DEFAULT = "mpegts"', prefs)
        self.assertIn('FIXED_ENCODING_AUDIO_CODEC_DEFAULT = "aac"', prefs)
        self.assertIn('FIXED_AUDIO_CODECS = ("aac", "ac3", "mp2")', config_values)
        self.assertIn('"fixed_audio_codec": "aac"', config_values)
        caption_test = (ROOT / "scripts/mcp_caption_test.py").read_text(encoding="utf-8")
        self.assertIn("add_fixed_encoding_args(parser)", caption_test)
        self.assertIn("**fixed_config", caption_test)

    def test_gsy_settings_screen_is_wired_and_has_independent_engine_selection(self):
        settings = (SHARED / "ui/settings/SettingsFragment.java").read_text(encoding="utf-8")
        fragment = (SHARED / "ui/settings/GSYPlayerSettingsFragment.java").read_text(encoding="utf-8")
        manifest = (DEV / "android-shared/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
        prefs = (RES / "xml/gsyplayer_prefs.xml").read_text(encoding="utf-8")
        arrays = (RES / "values/arrays.xml").read_text(encoding="utf-8")
        self.assertIn("GSYPlayerSettingsActivity.class", settings)
        self.assertIn("GSYPlayerSettingsActivity", manifest)
        self.assertIn("PlayerBackend.GSY_VERSION", fragment)
        self.assertIn('android:key="gsy_player_engine"', prefs)
        self.assertIn('@array/entries_gsy_player_engine', prefs)
        for value in ("auto", "media3", "system", "legacy_exo"):
            self.assertIn(f"<item>{value}</item>", arrays)
        for obsolete in ("gsy_mediacodec_sync", "gsy_packet_buffering", "gsy_decoders"):
            self.assertNotIn(obsolete, prefs)

    def test_media3_dependency_and_modern_toolchain_are_pinned(self):
        root_gradle = (DEV / "build.gradle").read_text(encoding="utf-8")
        core_gradle = (DEV / "core/build.gradle").read_text(encoding="utf-8")
        shared_gradle = (DEV / "android-shared/build.gradle").read_text(encoding="utf-8")
        self.assertIn("buildscript {", root_gradle)
        self.assertNotIn('apply plugin: "java"', root_gradle)
        self.assertIn('apply plugin: "java"', core_gradle)
        self.assertNotEqual(root_gradle, core_gradle, "Dev root build.gradle was replaced by core/build.gradle")
        wrapper = (DEV / "gradle/wrapper/gradle-wrapper.properties").read_text(encoding="utf-8")
        docker = unified_dockerfile().read_text(encoding="utf-8")
        self.assertIn("media3Version = '1.11.0'", root_gradle)
        self.assertIn("exoVersion = '2.18.1'", root_gradle)
        self.assertIn("gsyVersion = '13.1.0'", root_gradle)
        self.assertIn('androidMinSdkVersion = 23', root_gradle)
        self.assertIn('androidCompileSdkVersion = 36', root_gradle)
        self.assertIn('androidBuildToolsVersion = "36.0.0"', root_gradle)
        self.assertIn("com.android.tools.build:gradle:8.13.2", root_gradle)
        self.assertIn("gradle-8.13-bin.zip", wrapper)
        self.assertIn("eclipse-temurin:17-jdk-jammy", docker)
        self.assertIn('"build-tools;36.0.0"', docker)
        for module in ("media3-common", "media3-datasource", "media3-exoplayer", "media3-ui"):
            self.assertIn(f"androidx.media3:{module}:${{media3Version}}", shared_gradle)

    def test_legacy_exoplayer_modules_are_strictly_aligned_for_gradle8(self):
        root_gradle = (DEV / "build.gradle").read_text(encoding="utf-8")
        shared_gradle = (DEV / "android-shared/build.gradle").read_text(encoding="utf-8")
        self.assertIn("details.requested.group == 'com.google.android.exoplayer'", root_gradle)
        self.assertIn("details.useVersion exoVersion", root_gradle)
        for module in (
            "exoplayer-common", "exoplayer-extractor", "exoplayer-core",
            "exoplayer-ui", "exoplayer-datasource", "exoplayer-decoder",
            "exoplayer-dash", "exoplayer-rtsp", "exoplayer-transformer",
            "exoplayer-smoothstreaming", "exoplayer-hls",
        ):
            self.assertIn(f"com.google.android.exoplayer:{module}:${{exoVersion}}", shared_gradle)
            self.assertNotIn(f"com.google.android.exoplayer:{module}:${{exoVersion}}@aar", shared_gradle)
        self.assertNotIn("exoplayer-testutils", shared_gradle)
        self.assertNotIn('api(name: "ijkplayer-exo-', shared_gradle)

    def test_modern_gradle_explicitly_exposes_android_logging_and_appcompat_theme(self):
        shared_gradle = (DEV / "android-shared/build.gradle").read_text(encoding="utf-8")
        dialog = (SHARED / "AutoConnectDialog.java").read_text(encoding="utf-8")
        self.assertIn('org.slf4j:slf4j-api:1.7.6', shared_gradle)
        self.assertIn('androidx.appcompat:appcompat:1.3.1', shared_gradle)
        self.assertIn('androidx.appcompat.R.style.Theme_AppCompat_Dialog', dialog)
        self.assertNotIn('R.style.Base_Theme_AppCompat_Dialog', dialog)

    def test_android_tv_uses_shared_r_for_shared_drawables(self):
        files = [
            DEV / "android-tv/src/main/java/opensagetv/vibe/miniclient/android/phone/ServersAdapter.java",
            DEV / "android-tv/src/main/java/opensagetv/vibe/miniclient/android/tv/MainFragment.java",
            DEV / "android-tv/src/main/java/opensagetv/vibe/miniclient/android/tv/ServerItemPresenter.java",
        ]
        text = "\n".join(path.read_text(encoding="utf-8") for path in files)
        for drawable in (
            "iconbutton_background", "ic_add_to_queue_white_60dp",
            "ic_tv_white_60dp", "sage_logo_256",
        ):
            self.assertIn(f"opensagetv.vibe.miniclient.android.R.drawable.{drawable}", text)
            self.assertNotIn(f"R.drawable.{drawable}", text.replace(f"opensagetv.vibe.miniclient.android.R.drawable.{drawable}", ""))

    def test_media3_settings_are_wired(self):
        settings = (SHARED / "ui/settings/SettingsFragment.java").read_text(encoding="utf-8")
        manifest = (DEV / "android-shared/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
        self.assertIn("Media3PlayerSettingsActivity.class", settings)
        self.assertIn("Media3PlayerSettingsActivity", manifest)
        self.assertTrue((SHARED / "ui/settings/Media3PlayerSettingsActivity.java").exists())
        self.assertTrue((SHARED / "ui/settings/Media3PlayerSettingsFragment.java").exists())
        self.assertTrue((RES / "xml/media3player_prefs.xml").exists())

    def test_surface_ordering_keeps_android_caption_overlay_above_sagetv_ui(self):
        exo = (SHARED / "video/exoplayer2/Exo2MediaPlayerImpl.java").read_text(encoding="utf-8")
        media3 = (SHARED / "video/media3/Media3MediaPlayerImpl.java").read_text(encoding="utf-8")
        gdx = (SHARED / "gdx/MiniClientGDXActivity.java").read_text(encoding="utf-8")
        # Both Exo generations must bind the SurfaceView owner rather than a
        # one-time raw Surface. Fire OS replaces the holder Surface across HOME
        # and embedded/fullscreen transitions.
        self.assertIn("player.setVideoSurfaceView((SurfaceView) context.getVideoView())", exo)
        self.assertIn("player.setVideoSurfaceView((SurfaceView) context.getVideoView())", media3)
        self.assertIn("glView.setZOrderMediaOverlay(true)", gdx)
        self.assertIn("PixelFormat.RGBA_8888", gdx)
        self.assertNotIn("glView.setZOrderOnTop(true)", gdx)
        for player in (exo, media3):
            self.assertIn("new FrameLayout.LayoutParams(", player)
            self.assertIn("subView.setElevation(100.0f);", player)
            self.assertIn("subView.bringToFront();", player)
            self.assertIn("findViewById(android.R.id.content)", player)

    def test_gdx_renderer_uses_four_backend_factory(self):
        text = (SHARED / "gdx/MiniClientGDXRenderer.java").read_text(encoding="utf-8")
        self.assertIn("PlayerBackend.fromPreference", text)
        self.assertIn("PlayerFactory.create(activity, backend)", text)
        self.assertNotIn('equalsIgnoreCase("exoplayer")', text)
        self.assertNotIn("new Exo2MediaPlayerImpl(activity)", text)
        self.assertNotIn("new IJKMediaPlayerImpl(activity)", text)

    def test_gdx_renderer_does_not_end_a_batch_that_failed_to_begin(self):
        text = (SHARED / "gdx/MiniClientGDXRenderer.java").read_text(encoding="utf-8")
        self.assertIn("boolean batchBegun = false;", text)
        self.assertIn("batchBegun = true;", text)
        self.assertIn("if (batchBegun)", text)
        self.assertIn("Unable to finish renderer batch during lifecycle transition", text)

    def test_combined_update_test_validate_build_install_launch_script_is_resumable(self):
        script = (ROOT / "update.sh").read_text(encoding="utf-8")
        self.assertIn("set -euo pipefail", script)
        self.assertIn('artifacts/downloads', script)
        self.assertIn('changed-files-only', script)
        self.assertIn('release.properties', script)
        self.assertIn('REQUIRES_BUILD', script)
        self.assertIn('artifacts/update_runner', script)
        for command in (
            './dev.sh test', './dev.sh validate', './dev.sh build',
            './dev.sh install', './dev.sh launch --client-id "$SCRIPTED_CLIENT_ID"',
        ):
            self.assertIn(command, script)

    def test_player_diagnostics_are_external_only(self):
        script = (ROOT / "scripts/player_diagnostics.py").read_text(encoding="utf-8")
        dev_sh = (ROOT / "dev.sh").read_text(encoding="utf-8")
        self.assertIn("player-diag", dev_sh)
        self.assertIn("dumpsys SurfaceFlinger", script)
        self.assertIn("adb.logcat_tail(10000)", script)

    def test_audio_icon_exposes_live_decoded_pcm_and_sync_controls(self):
        dialog = (SHARED / "ActivePlayerAdjustmentsDialog.java").read_text(encoding="utf-8")
        navigation = (SHARED / "NavigationDialog.java").read_text(encoding="utf-8")
        layout = (RES / "layout/navigation.xml").read_text(encoding="utf-8")
        tv_layout = (DEV / "android-tv/src/main/res/layout-notouch/navigation.xml").read_text(encoding="utf-8")
        interface = (DEV / "core/src/main/java/opensagetv/vibe/miniclient/MiniPlayerPlugin.java").read_text(encoding="utf-8")
        self.assertIn('android:id="@+id/nav_audio_output"', layout)
        self.assertIn('android:id="@+id/nav_audio_output"', tv_layout)
        self.assertIn("ActivePlayerAdjustmentsDialog.showAudio(activity)", navigation)
        self.assertIn('heading.setText("Audio settings")', dialog)
        self.assertIn('audioSettingRow("Audio output"', dialog)
        self.assertIn('audioSettingRow("Passthrough offset"', dialog)
        self.assertIn('audioSettingRow("Audio offset"', dialog)
        self.assertIn('audioSettingRow("A/V sync test"', dialog)
        self.assertIn('audioSettingRow("Audio stream"', dialog)
        self.assertIn('audioSettingRow("Set as default for all media"', dialog)
        self.assertIn("metrics.widthPixels * 0.66f", dialog)
        self.assertIn("chooseAudioChoice(", dialog)
        self.assertIn('captionButton("Back")', dialog)
        self.assertIn("setOnCancelListener", dialog)
        self.assertIn('"Decoded PCM stereo"', dialog)
        self.assertIn("showAudioOffsetSlider(active)", dialog)
        self.assertIn("stepMs = 25", dialog)
        self.assertIn("minMs = -4_000", dialog)
        self.assertIn("maxMs = 4_000", dialog)
        self.assertIn("KeyEvent.KEYCODE_DPAD_LEFT", dialog)
        self.assertIn("KeyEvent.KEYCODE_DPAD_RIGHT", dialog)
        self.assertIn("KeyEvent.KEYCODE_BACK", dialog)
        self.assertIn("PrefStore.Keys.playback_audio_offset_ms", dialog)
        self.assertIn("showAudioMenu();", dialog)
        self.assertIn("isAudioPassthroughEnabled()", interface)
        self.assertIn("supportsPassthroughAudioOffset()", interface)
        self.assertIn("setPassthroughAudioOffsetEnabled", interface)

    def test_long_press_playback_menu_groups_compact_video_audio_and_caption_controls(self):
        dialog = (SHARED / "ActivePlayerAdjustmentsDialog.java").read_text(encoding="utf-8")
        navigation = (SHARED / "NavigationDialog.java").read_text(encoding="utf-8")
        no_touch = (DEV / "android-tv/src/main/res/layout-notouch/navigation.xml").read_text(
            encoding="utf-8"
        )
        shared_layout = (RES / "layout/navigation.xml").read_text(encoding="utf-8")
        video_menu = dialog.split("private void showMain()", 1)[1].split(
            "private void addVideoRow", 1
        )[0]
        for label in (
            '"Player"', '"Decoding"', '"Codec Queueing"',
            '"Source buffering"', '"Display"', '"DVD playback"',
            '"Restart video decoder"', '"Reset video overrides"',
        ):
            self.assertIn(label, video_menu)
        for removed in (
            "Test Current Video", "Live playback diagnostics", "Playback Stats overlay",
            "Audio track", "Subtitle timing", "Audio output / passthrough",
            "Broadcast captions / CC",
        ):
            self.assertNotIn(removed, video_menu)
        self.assertIn('showSettingsPanel("Video settings"', video_menu)
        self.assertNotIn('captionRow(status(media)', video_menu)
        for group in ("showVideoDisplayMenu", "showVideoDvdMenu"):
            self.assertIn(group, dialog)
        self.assertNotIn("showVideoEngineMenu", dialog)
        for setting in ("Refresh-rate matching", "Timestamp repair", "HDMI settle"):
            self.assertIn(setting, dialog)
        self.assertIn("chooseVideoChoice", dialog)
        self.assertIn("reopenVideoMenu", dialog)
        self.assertIn("resetVideo()", video_menu)
        for gsy_choice in (
            'GSYPlayerEngine.AUTO', 'GSYPlayerEngine.MEDIA3',
            'GSYPlayerEngine.SYSTEM', 'GSYPlayerEngine.LEGACY_EXO',
        ):
            self.assertIn(gsy_choice, dialog)
        self.assertIn('backend.displayName() + " ("', dialog)
        self.assertIn("ActivePlayerSessionOverrides.setGsyEngine", dialog)
        self.assertIn("PrefStore.Keys.gsy_player_engine", dialog)
        self.assertIn("client.eventbus().post(new VideoInfoShow())", navigation)
        self.assertNotIn("ActivePlayerAdjustmentsDialog.showInformation", navigation)
        self.assertNotIn("onSwitchPlayer();", navigation)
        self.assertNotIn("onToggleSmartRemote();", navigation)
        self.assertIn('android:src="@drawable/ic_video_settings_white_24dp"', no_touch)
        self.assertNotIn('android:id="@+id/nav_switch_player"', no_touch)
        self.assertNotIn('android:id="@+id/nav_remote_mode"', no_touch)
        self.assertIn('android:contentDescription="Video Settings"', shared_layout)
        self.assertIn('android:contentDescription="Audio Output and Sync"', shared_layout)
        self.assertIn('android:contentDescription="Subtitles and Broadcast Captions"', shared_layout)
        self.assertIn("name.setSingleLine(true)", dialog)
        self.assertIn("current.setSingleLine(true)", dialog)
        self.assertIn("TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration", dialog)
        self.assertNotIn("setEllipsize", dialog)
        self.assertIn("current.setGravity(Gravity.START | Gravity.CENTER_VERTICAL)", dialog)
        self.assertIn("currentParams.leftMargin = dp(24)", dialog)
        self.assertIn("dp(42), 1.6f", dialog)
        self.assertIn("settingsDialogWidth(rows)", dialog)
        self.assertIn("metrics.widthPixels * 0.54f", dialog)
        self.assertIn("dp(520)", dialog)

    def test_triangle_opens_combined_fullscreen_information_and_caption_menu_is_compact(self):
        dialog = (SHARED / "ActivePlayerAdjustmentsDialog.java").read_text(encoding="utf-8")
        navigation = (SHARED / "NavigationDialog.java").read_text(encoding="utf-8")
        video_info = (SHARED / "VideoInfoDialog.java").read_text(encoding="utf-8")
        video_layout = (RES / "layout/video_info.xml").read_text(encoding="utf-8")
        self.assertIn("client.eventbus().post(new VideoInfoShow())", navigation)
        self.assertNotIn("showInformationMenu", dialog)
        self.assertNotIn("showDiagnostics(MediaCmd", dialog)
        self.assertIn("diagnosticsTextForExport", video_info)
        self.assertIn("R.id.vi_vibeDiagnostics", video_info)
        self.assertIn("private final Activity activity", video_info)
        self.assertNotIn("(Activity) getContext()", video_info)
        self.assertIn('android:text="SageTV Video"', video_layout)
        self.assertIn('android:text="Vibe Diagnostics"', video_layout)
        self.assertIn("<TableLayout", video_layout)
        self.assertIn('android:paddingBottom="2dp"', video_layout)
        self.assertIn('android:id="@+id/vib_export"', video_layout)
        self.assertIn('android:layout_height="match_parent"', video_layout)
        self.assertIn('showCaptionPanel("Subtitles / broadcast CC"', dialog)
        self.assertIn('"Subtitle stream: " + subtitleTrackValue', dialog)
        self.assertIn('"Subtitle appearance: " + subtitleAppearanceValue', dialog)
        self.assertIn('chooseCaptionChoice("Subtitle stream (SRT/PGS/DVD)"', dialog)
        self.assertNotIn('"Subtitle offset: "', dialog)
        self.assertIn("metrics.widthPixels * 0.42f", dialog)
        self.assertIn("selectable ? dp(28)", dialog)

    def test_embedded_bouncing_ball_av_sync_test_uses_real_media_pipeline(self):
        dialog = (SHARED / "video/media3/Media3AvSyncTestDialog.java").read_text(
            encoding="utf-8"
        )
        menu = (SHARED / "ActivePlayerAdjustmentsDialog.java").read_text(
            encoding="utf-8"
        )
        generator = (ROOT / "scripts/generate_av_sync_fixture.py").read_text(
            encoding="utf-8"
        )
        asset = DEV / "android-shared/src/main/assets/vibe_av_sync_ball.ts"
        self.assertTrue(asset.is_file())
        self.assertGreater(asset.stat().st_size, 500_000)
        self.assertIn('Uri.parse("asset:///vibe_av_sync_ball.ts")', dialog)
        self.assertIn("new ExoPlayer.Builder", dialog)
        self.assertIn("Player.REPEAT_MODE_ONE", dialog)
        self.assertIn("Media3AudioExtensionRenderersFactory", dialog)
        self.assertIn("Media3PassthroughOffsetExtractorsFactory", dialog)
        self.assertIn("new SurfaceView(activity)", dialog)
        self.assertIn("player.setVideoSurfaceView(videoSurface)", dialog)
        self.assertNotIn("new PlayerView(activity)", dialog)
        self.assertIn("Unable to start the A/V sync test", dialog)
        self.assertIn("active.setMute(true)", dialog)
        self.assertIn("active.suspendAudioForExclusiveDiagnostic()", dialog)
        self.assertIn("active.resumeAudioAfterExclusiveDiagnostic()", dialog)
        self.assertIn("activeAudioSuspended ? 350L : 0L", dialog)
        self.assertIn("Encoded A/V sync test is unavailable", dialog)
        self.assertIn("active.setMute(previouslyMuted)", dialog)
        self.assertIn("STEP_MS = 25", dialog)
        self.assertIn("OFFSET_APPLY_DEBOUNCE_MS = 250L", dialog)
        self.assertIn("pendingOffsetApply", dialog)
        self.assertIn("500-410*abs(sin(PI*t))", generator)
        self.assertIn("drawbox=x=0:y=596", generator)
        self.assertIn("widthPixels * 0.35f", dialog)
        self.assertIn("Gravity.BOTTOM | Gravity.RIGHT", dialog)
        self.assertIn("LinearLayout.LayoutParams.MATCH_PARENT, dp(24)", dialog)
        self.assertIn("player.setMediaSource(createFixtureSource(), positionMs)", dialog)
        self.assertIn("activity, passthrough, 0", dialog)
        self.assertIn("timingController.setOffsetMillis(offsetMs[0])", dialog)
        self.assertIn('"Calibration offset applied: "', dialog)
        self.assertIn("handler.removeCallbacks(pendingOffsetEvidence)", dialog)
        self.assertIn("0.015*sin(2*PI*220*t)", generator)
        self.assertIn("onFinished(offsetMs[0])", dialog)
        self.assertIn("Media3AvSyncTestDialog.show", menu)
        self.assertIn("Keep the center column completely clear", generator)
        self.assertIn("BALL IMPACT", generator)
        self.assertNotIn("BORDER FLASH", generator)
        self.assertIn("SHOULD COINCIDE", generator)
        self.assertIn('"-c:a", "ac3"', generator)
        self.assertIn('"-ar", "48000"', generator)
        self.assertIn("av-sync-fixture)", (ROOT / "dev.sh").read_text(encoding="utf-8"))

    def test_exclusive_av_sync_diagnostic_releases_and_restores_encoded_audio_sink(self):
        interface = (DEV / "core/src/main/java/opensagetv/vibe/miniclient/MiniPlayerPlugin.java").read_text(
            encoding="utf-8"
        )
        self.assertIn("suspendAudioForExclusiveDiagnostic()", interface)
        self.assertIn("resumeAudioAfterExclusiveDiagnostic()", interface)
        for player_rel in (
            "video/media3/Media3MediaPlayerImpl.java",
            "video/exoplayer2/Exo2MediaPlayerImpl.java",
        ):
            player = (SHARED / player_rel).read_text(encoding="utf-8")
            self.assertIn("exclusiveDiagnosticAudioSuspended", player, player_rel)
            self.assertIn("setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)", player,
                          player_rel)
            self.assertIn("setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)", player,
                          player_rel)
            self.assertIn('"diagnostic_audio_suspended"', player, player_rel)
            self.assertIn('"diagnostic_audio_resumed"', player, player_rel)
        gsy = (SHARED / "video/gsy/GSYMediaPlayerImpl.java").read_text(encoding="utf-8")
        self.assertIn("d().suspendAudioForExclusiveDiagnostic()", gsy)
        self.assertIn("d().resumeAudioAfterExclusiveDiagnostic()", gsy)

    def test_media3_and_legacy_exo_enforce_pcm_and_rebuild_live_source(self):
        cases = (
            ("video/media3/Media3MediaPlayerImpl.java",
             "video/media3/Media3AudioExtensionRenderersFactory.java",
             "video/media3/Media3PcmAudioProcessor.java"),
            ("video/exoplayer2/Exo2MediaPlayerImpl.java",
             "video/exoplayer2/Exo2AudioExtensionRenderersFactory.java",
             "video/exoplayer2/Exo2PcmAudioProcessor.java"),
        )
        for player_rel, factory_rel, processor_rel in cases:
            player = (SHARED / player_rel).read_text(encoding="utf-8")
            factory = (SHARED / factory_rel).read_text(encoding="utf-8")
            processor = (SHARED / processor_rel).read_text(encoding="utf-8")
            self.assertIn("resolveAudioPassthroughEnabled", player, player_rel)
            self.assertIn("retainedAudioRebuildDataSource", player, player_rel)
            self.assertIn('"audio_output_live_rebuild"', player, player_rel)
            self.assertIn("setAudioProcessors", factory, factory_rel)
            self.assertIn("DEFAULT_AUDIO_CAPABILITIES", factory, factory_rel)
            self.assertIn("setEnableFloatOutput(false)", factory, factory_rel)
            self.assertIn("pendingAdjustmentFrames", processor, processor_rel)
            self.assertIn("mixFrame", processor, processor_rel)

    def test_media3_and_legacy_exo_offset_encoded_passthrough_without_changing_bytes(self):
        controller = (SHARED / "video/EncodedPassthroughOffsetController.java").read_text(
            encoding="utf-8"
        )
        self.assertIn("shiftAudioSampleTimeUs", controller)
        self.assertIn("shiftVideoSampleTimeUs", controller)
        self.assertIn("value > 0", controller)
        self.assertIn("value < 0", controller)
        for player_rel, wrapper_rel in (
            ("video/media3/Media3MediaPlayerImpl.java",
             "video/media3/Media3PassthroughOffsetExtractorsFactory.java"),
            ("video/exoplayer2/Exo2MediaPlayerImpl.java",
             "video/exoplayer2/Exo2PassthroughOffsetExtractorsFactory.java"),
        ):
            player = (SHARED / player_rel).read_text(encoding="utf-8")
            wrapper = (SHARED / wrapper_rel).read_text(encoding="utf-8")
            self.assertIn("withPassthroughOffset", player, player_rel)
            self.assertIn("playback_passthrough_audio_offset_enabled", player, player_rel)
            self.assertIn("schedulePassthroughOffsetReanchor", player, player_rel)
            self.assertIn('"passthrough_offset_live_reanchor"', player, player_rel)
            reanchor = player.split(
                "private void schedulePassthroughOffsetReanchor", 1
            )[1].split("public String getSelected", 1)[0]
            self.assertIn("if (pushMode)", reanchor, player_rel)
            self.assertIn('"passthrough_offset_push_deferred"', reanchor, player_rel)
            self.assertIn("controller.setOffsetMillis", player, player_rel)
            self.assertRegex(
                player,
                r"(?:controller|passthroughOffsetController)\.setEnabled",
                player_rel,
            )
            self.assertNotIn("schedulePassthroughOffsetRebuild", player, player_rel)
            self.assertIn("delegate.sampleData", wrapper, wrapper_rel)
            self.assertIn("delegate.sampleMetadata(shifted", wrapper, wrapper_rel)
            self.assertNotIn("AudioProcessor", wrapper, wrapper_rel)

    def test_track_diagnostics_are_change_driven_and_safe_during_teardown(self):
        for player_rel, expected_type in (
            ("video/media3/Media3MediaPlayerImpl.java", "ExoPlayer"),
            ("video/exoplayer2/Exo2MediaPlayerImpl.java", "ExoPlayer"),
        ):
            player = (SHARED / player_rel).read_text(encoding="utf-8")
            tracks = player.split("public void onTracksChanged(Tracks tracks)", 1)[1]
            tracks = tracks.split("public void onPlayerError", 1)[0]
            ready = player.split("public void onPlaybackStateChanged(int playbackState)", 1)[1]
            ready = ready.split("public void onTimelineChanged", 1)[0]
            diagnostics = player.split(
                f"private void debugAvailableTracks({expected_type} expectedPlayer)", 1
            )[1].split("// cncb - Add and remove", 1)[0]

            self.assertIn("debugAvailableTracks(listenerPlayer);", tracks, player_rel)
            self.assertIn("player != listenerPlayer", tracks, player_rel)
            self.assertNotIn("debugAvailableTracks(listenerPlayer);", ready, player_rel)
            self.assertIn("if (expectedPlayer == null)", diagnostics, player_rel)
            self.assertIn("expectedPlayer.getRendererType(i)", diagnostics, player_rel)
            self.assertNotIn("player.getRendererType(i)", diagnostics, player_rel)

        media3 = (SHARED / "video/media3/Media3MediaPlayerImpl.java").read_text(
            encoding="utf-8"
        )
        ready = media3.split("public void onPlaybackStateChanged(int playbackState)", 1)[1]
        ready = ready.split("public void onTimelineChanged", 1)[0]
        self.assertIn("listenerPlayer.getDuration()", ready)
        self.assertNotIn("player.getDuration()", ready)

    def test_ijk_truthfully_reports_fixed_decoded_pcm(self):
        player = (SHARED / "video/ijkplayer/IJKMediaPlayerImpl.java").read_text(encoding="utf-8")
        self.assertIn('return "Decoded PCM (IJK fixed output)";', player)
        self.assertIn("if (enabled)\n            return false;", player)

    def test_audio_output_and_offset_are_exported_for_diagnostics(self):
        stats = (SHARED / "ActivePlayerStatsSnapshot.java").read_text(encoding="utf-8")
        state = (DEV / "android-tv/src/debug/java/opensagetv/vibe/miniclient/android/tv/debug/DebugStateProvider.java").read_text(encoding="utf-8")
        self.assertIn('line(text, "Audio output  " + audioOutput)', stats)
        for field in (
            "audioPassthroughControlSupported", "audioPassthroughEnabled",
            "audioOutputSummary", "audioOffsetSupported", "audioOffsetMs",
            "audioOffsetSessionOverrideMs", "audioPassthroughSessionOverride",
            "passthroughAudioOffsetSupported", "passthroughAudioOffsetEnabled",
            "audioOffsetPath",
        ):
            self.assertIn(field, state)


if __name__ == "__main__":
    unittest.main()
