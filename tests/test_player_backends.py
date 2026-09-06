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

    def test_legacy_exoplayer_uses_shared_decoding_policy_without_telemetry(self):
        player = (SHARED / "video/exoplayer2/Exo2MediaPlayerImpl.java").read_text(encoding="utf-8")
        selector = (SHARED / "video/exoplayer2/CustomMediaCodecSelector.java").read_text(encoding="utf-8")
        self.assertIn("PrefStore.Keys.decoding_method", player)
        self.assertIn("new CustomMediaCodecSelector(decodingMethod)", player)
        self.assertIn("setEnableDecoderFallback(decodingMethod.hardwarePreferred())", player)
        self.assertIn("case SOFTWARE:", selector)
        self.assertIn("case HARDWARE_PREFERRED:", selector)
        self.assertIn("case HARDWARE:", selector)
        self.assertNotIn("PlayerTelemetry", player)

    def test_media3_backend_uses_shared_decoding_policy_and_no_internal_telemetry(self):
        player = (SHARED / "video/media3/Media3MediaPlayerImpl.java").read_text(encoding="utf-8")
        selector = (SHARED / "video/media3/Media3CodecSelector.java").read_text(encoding="utf-8")
        self.assertIn("androidx.media3.exoplayer.ExoPlayer", player)
        self.assertIn("Media3PushDataSource", player)
        self.assertIn("Media3PullDataSource", player)
        self.assertIn("new Media3CodecSelector(decodingMethod)", player)
        self.assertIn("setEnableDecoderFallback(decodingMethod.hardwarePreferred())", player)
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
        self.assertEqual((ROOT / "VERSION").read_text(encoding="utf-8").strip(), "0.5.85")

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
        # releasePlayer() intentionally marks the old session EOS. Both load paths
        # must clear that stale flag before SageTV polls the new PUSH session; if it
        # sees EOS, the server pusher exits before sending its first buffer.
        self.assertEqual(load.count("releasePlayer();"), 2)
        self.assertEqual(load.count("releasePlayer();\n                    // releasePlayer() marks"), 1)
        self.assertEqual(load.count("releasePlayer();\n            // Match the UI-thread load path"), 1)
        self.assertEqual(load.count("eos = false;"), 3)
        self.assertLess(load.index("// releasePlayer() marks"), load.index("state = LOADED_STATE;"))

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
                r"PlaybackSeekPolicy\.clamp\(\s*timeInMillis,\s*durationMs,\s*bufferedPositionMs,\s*mediaContext\.isTimeshifted\(\)\)",
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
            self.assertIn("new BufferedPullDataSource(host, pullReadBytes)", text, rel)

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
            self.assertIn("return bytesRemaining;", text, rel)
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
            if "exoplayer2" in rel:
                self.assertIn("ExtractorsFactory extractorsFactory = createCaptionAwareExtractorsFactory(true);", pull_tuning, rel)
            else:
                self.assertIn("ExtractorsFactory extractorsFactory = createCaptionAwareExtractorsFactory(true);", pull_tuning, rel)

        exo2 = (SHARED / "video/exoplayer2/Exo2MediaPlayerImpl.java").read_text(encoding="utf-8")
        self.assertIn("SeekParameters.NEXT_SYNC", exo2)
        self.assertIn("SeekParameters.PREVIOUS_SYNC", exo2)
        self.assertIn("runtimeConfig.getDirectionalSyncMinDeltaMs()", exo2)
        self.assertIn("runtimeConfig.getSeekRecoveryDelayMs()", exo2)
        self.assertIn("pull_seek_recovery_armed", exo2)
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


if __name__ == "__main__":
    unittest.main()
