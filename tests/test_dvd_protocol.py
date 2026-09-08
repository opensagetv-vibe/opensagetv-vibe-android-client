from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
MEDIA = ROOT / "source/dev/core/src/main/java/opensagetv/vibe/miniclient/MediaCmd.java"
PLUGIN = ROOT / "source/dev/core/src/main/java/opensagetv/vibe/miniclient/MiniPlayerPlugin.java"
BASE = ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/BaseMediaPlayerImpl.java"
OVERLAY = ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/DvdHighlightOverlay.java"
CONNECTION = ROOT / "source/dev/core/src/main/java/opensagetv/vibe/miniclient/MiniClientConnection.java"
MEDIA3 = ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/media3/Media3MediaPlayerImpl.java"
LEGACY_EXO = ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/exoplayer2/Exo2MediaPlayerImpl.java"
DVD_FACTORY = ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/media3/ResilientDvdPsExtractorsFactory.java"
DVD_PS = ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/media3/DvdPsExtractor.java"
DVD_AC3 = ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/media3/DvdAc3Reader.java"
DVD_SPU = ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/DvdSubpictureDecoder.java"
DVD_SPU_CORE = ROOT / "source/dev/core/src/main/java/opensagetv/vibe/miniclient/dvd/DvdSpuDecoder.java"
DVD_SPU_COMPOSITOR = ROOT / "source/dev/core/src/main/java/opensagetv/vibe/miniclient/dvd/DvdSpuCompositor.java"
DVD_YUV_PALETTE = ROOT / "source/dev/core/src/main/java/opensagetv/vibe/miniclient/dvd/YuvPalette.java"
DVD_AUDIO_STREAM_CODE = ROOT / "source/dev/core/src/main/java/opensagetv/vibe/miniclient/dvd/DvdAudioStreamCode.java"
PUSH_SOURCE = ROOT / "source/dev/core/src/main/java/opensagetv/vibe/miniclient/net/PushBufferDataSource.java"
MEDIA3_PUSH_SOURCE = ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/media3/Media3PushDataSource.java"
DVD_PREFS = ROOT / "source/dev/android-shared/src/main/res/xml/disc_playback_prefs.xml"
ROOT_PREFS = ROOT / "source/dev/android-shared/src/main/res/xml/prefs.xml"
DEBUG_STATE = ROOT / "source/dev/android-tv/src/debug/java/opensagetv/vibe/miniclient/android/tv/debug/DebugStateProvider.java"
MCP_SERVER = ROOT / "mcp/src/sagetv_dev_mcp/server.py"
DVD_MCP_HARNESS = ROOT / "scripts/mcp_disc_test.py"
HDMI_CAPTURE = ROOT / "scripts/capture_hdmi_validation.py"
HDMI_CAPTURE_CMD = ROOT / "capture_hdmi_validation.cmd"
PLAYER_FACTORY = ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/PlayerFactory.java"
DISC_POLICY = ROOT / "source/dev/core/src/main/java/opensagetv/vibe/miniclient/video/DiscPlaybackPolicy.java"
ACTIVE_ADJUSTMENTS = ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/ActivePlayerAdjustmentsDialog.java"
SESSION_OVERRIDES = ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/ActivePlayerSessionOverrides.java"
TEXT_SUBTITLE_PRESENTATION = ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/TextSubtitlePresentation.java"
ACTIVE_PROCESS_OVERLAY = ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/ActivePlayerProcessOverlay.java"
ACTIVE_STATS_SNAPSHOT = ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/ActivePlayerStatsSnapshot.java"


class DvdProtocolTests(unittest.TestCase):
    def test_active_text_subtitle_presentation_is_bounded_and_dvd_safe(self):
        plugin = PLUGIN.read_text(encoding="utf-8")
        dialog = ACTIVE_ADJUSTMENTS.read_text(encoding="utf-8")
        policy = TEXT_SUBTITLE_PRESENTATION.read_text(encoding="utf-8")
        for method in (
            "supportsTextSubtitlePresentation",
            "setTextSubtitlePresentation",
            "getTextSubtitleSafeAreaPercent",
            "getTextSubtitleScalePercent",
            "getTextSubtitleStyle",
        ):
            self.assertIn(method, plugin)
            self.assertIn(method, dialog)
        debug_state = DEBUG_STATE.read_text(encoding="utf-8")
        for field in (
            "textSubtitlePresentationSupported",
            "textSubtitleSafeAreaPercent",
            "textSubtitleScalePercent",
            "textSubtitleStyle",
        ):
            self.assertIn(field, debug_state)
        self.assertIn("Math.max(5, Math.min(35, value))", policy)
        self.assertIn("Math.max(75, Math.min(150, value))", policy)
        self.assertIn("DVD SPU subtitles are authored bitmaps", dialog)
        for relative_path in (MEDIA3, LEGACY_EXO):
            player = relative_path.read_text(encoding="utf-8")
            self.assertIn("applyTextSubtitlePresentation", player)
            self.assertIn("setBottomPaddingFraction", player)
            self.assertIn("setFractionalTextSize", player)
            self.assertIn("setUserDefaultStyle", player)

    def test_playback_stats_overlay_is_bounded_opt_in_and_lifecycle_safe(self):
        overlay = ACTIVE_PROCESS_OVERLAY.read_text(encoding="utf-8")
        dialog = ACTIVE_ADJUSTMENTS.read_text(encoding="utf-8")
        debug_state = DEBUG_STATE.read_text(encoding="utf-8")
        lifecycle = (ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/UIActivityLifeCycleHandler.java").read_text(encoding="utf-8")
        self.assertIn("DISPLAY_MS = 30_000L", overlay)
        self.assertIn("WeakReference<Activity>", overlay)
        self.assertIn("MAIN.removeCallbacks", overlay)
        self.assertIn("removeView", overlay)
        self.assertIn("Playback Stats overlay", dialog)
        self.assertIn("☑ Playback Stats enabled", dialog)
        self.assertIn("☐ Playback Stats disabled", dialog)
        self.assertIn("final boolean wasVisible", dialog)
        self.assertIn("Show compact until turned off", dialog)
        self.assertIn("Show detailed until turned off", dialog)
        self.assertIn("Show detailed for 30 seconds", dialog)
        self.assertIn("Export redacted detailed snapshot", dialog)
        self.assertIn("activePlayerProcessOverlayVisible", debug_state)
        self.assertGreaterEqual(lifecycle.count("ActivePlayerProcessOverlay.hide();"), 2)

    def test_playback_stats_are_mode_aware_redacted_and_have_live_health_bars(self):
        overlay = ACTIVE_PROCESS_OVERLAY.read_text(encoding="utf-8")
        snapshot = ACTIVE_STATS_SNAPSHOT.read_text(encoding="utf-8")
        for label in ("Media network activity", "Buffer health", "CPU usage"):
            self.assertIn(label, overlay)
        self.assertNotIn("CONNECTION_GRAPH_REFERENCE_KBPS", overlay)
        self.assertIn("resizeToContent(body)", overlay)
        self.assertIn("details.getPaint().measureText(line)", overlay)
        self.assertIn("if (desired <= widestContentPx) return", overlay)
        self.assertIn('"  |  peak "', overlay)
        for section in (
            "SMB cache", "Shadow", "Server", "DVD cadence", "Captions",
        ):
            self.assertIn(section, snapshot)
        self.assertIn("if (smb)", snapshot)
        self.assertIn("else if (push || dvd)", snapshot)
        self.assertIn("if (dvd)", snapshot)
        self.assertIn("No media path, server address, credentials, or client ID", snapshot)
        self.assertIn("redactForExport(detailedText(activityKbps))", snapshot)
        self.assertIn('"<smb-path>"', snapshot)
        self.assertIn('"<unc-path>"', snapshot)
        self.assertIn('"<media-path>"', snapshot)
        self.assertIn('"<server>"', snapshot)
        self.assertNotIn("getLastOpenUrlForDebug", snapshot)
        self.assertNotIn("getSageOriginalPath", snapshot)
        self.assertNotIn("getSmbMappedPath", snapshot)
        for irrelevant_example_field in (
            "Video ID / sCPN", "Viewport / Frames", "Current / Optimal Res",
            "Volume / Normalized", "Mystery Text", "Date",
        ):
            self.assertNotIn(irrelevant_example_field, snapshot)
            self.assertNotIn(irrelevant_example_field, overlay)

    def test_compact_player_overlay_has_deterministic_mcp_control(self):
        overlay = ACTIVE_PROCESS_OVERLAY.read_text(encoding="utf-8")
        receiver = (ROOT / "source/dev/android-tv/src/debug/java/opensagetv/vibe/miniclient/android/tv/debug/DevTestReceiver.java").read_text(encoding="utf-8")
        adb = (ROOT / "mcp/src/sagetv_dev_mcp/adb.py").read_text(encoding="utf-8")
        server = (ROOT / "mcp/src/sagetv_dev_mcp/server.py").read_text(encoding="utf-8")
        snapshot = ACTIVE_STATS_SNAPSHOT.read_text(encoding="utf-8")
        self.assertIn("setVisible(Activity activity, MediaCmd media, boolean visible)", overlay)
        self.assertIn('"active_player_overlay".equals(op)', receiver)
        self.assertIn("def set_active_player_overlay(", adb)
        self.assertIn("def dev_set_active_player_overlay(", server)
        self.assertIn('"toggle".equals(mode)', overlay)
        self.assertIn('"detailed_30s".equals(mode)', overlay)
        self.assertIn('intent.getStringExtra("mode")', (ROOT / "source/dev/android-tv/src/debug/java/opensagetv/vibe/miniclient/android/tv/debug/DebugSessionCommands.java").read_text(encoding="utf-8"))
        self.assertIn('{"toggle", "off", "compact", "detailed", "detailed_30s"}', adb)
        navigation = (ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/NavigationDialog.java").read_text(encoding="utf-8")
        notouch = (ROOT / "source/dev/android-tv/src/main/res/layout-notouch/navigation.xml").read_text(encoding="utf-8")
        self.assertIn("nav_playback_stats", navigation)
        self.assertIn('setMode(activity, currentMedia, "toggle")', navigation)
        self.assertIn('@+id/nav_playback_stats', notouch)
        self.assertIn('@drawable/ic_equalizer_white_24dp', notouch)
        self.assertIn('new FileReader("/proc/stat")', overlay)
        self.assertIn('new FileReader("/proc/uptime")', overlay)
        self.assertIn("readProcUptimeCpuTicks", overlay)
        self.assertIn("readSysfsCpuIdleTicks", overlay)
        self.assertIn('new File("/sys/devices/system/cpu")', overlay)
        self.assertIn('new File(state, "time")', overlay)
        self.assertIn("elapsedMs * cpuIdleCpuCount", overlay)
        self.assertIn("device[2] == priorDeviceSource", overlay)
        self.assertIn("uptimeMs * availableCpus", overlay)
        self.assertIn("android.os.Process.getElapsedCpuTime()", overlay)
        self.assertIn("Runtime.getRuntime().availableProcessors()", overlay)
        self.assertIn("snapshot.appCpuPercent = Math.min(snapshot.appCpuPercent", overlay)
        self.assertIn("cpu.updateCpu", overlay)
        self.assertIn("setSecondaryProgress", overlay)
        self.assertIn('"Vibe " + ActivePlayerStatsSnapshot.percent', overlay)
        self.assertIn('"Other " + ActivePlayerStatsSnapshot.percent', overlay)
        self.assertIn("new ForegroundColorSpan(color)", overlay)
        self.assertNotIn('line(text, "CPU  device "', snapshot)
        self.assertNotIn('line(text, "Buffer  "', snapshot)
        self.assertNotIn('line(text, "Measured media activity', snapshot)
        self.assertNotIn('"Link capacity (Android estimate)"', overlay)
        self.assertIn("details = text(10.5f, Typeface.NORMAL, Color.WHITE)", overlay)
        self.assertNotIn("details.setTypeface(Typeface.MONOSPACE)", overlay)

    def test_hdmi_settle_is_bounded_and_only_schedules_server_owned_dvd_reload(self):
        dialog = ACTIVE_ADJUSTMENTS.read_text(encoding="utf-8")
        controller = (ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/DisplayRefreshController.java").read_text(encoding="utf-8")
        overrides = (ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/ActivePlayerSessionOverrides.java").read_text(encoding="utf-8")
        self.assertIn("HDMI settle before DVD decoder reload", dialog)
        self.assertIn("scheduleControlledDvdReload", controller)
        self.assertIn("!media.isDvdSessionPending()", controller)
        self.assertIn("Math.max(0, Math.min(1_500, delayMs))", controller)
        self.assertIn("currentMedia.requestControlledPlayerReload()", controller)
        self.assertNotIn("currentMedia.getPlaya().pause()", controller)
        self.assertIn("setRefreshSettleMs", overrides)

    def test_display_refresh_application_is_marshaled_to_android_main_thread(self):
        controller = (ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/DisplayRefreshController.java").read_text(encoding="utf-8")
        self.assertIn("Looper.myLooper() != Looper.getMainLooper()", controller)
        self.assertIn('"application_scheduled_on_main"', controller)
        self.assertIn("OFF.equals(policy) ? null : player", controller)

    def test_hybrid_openurl_authoritatively_creates_a_push_datasource(self):
        base = BASE.read_text(encoding="utf-8")
        media3 = MEDIA3.read_text(encoding="utf-8")
        load = base.split("public void load(", 1)[1].split(
            "protected abstract void setupPlayer", 1
        )[0]
        self.assertIn('urlString.startsWith("push:")', load)
        self.assertIn("pushMode = true", load)
        dvd_setup = media3.split("protected void setupPlayer", 1)[1]
        self.assertIn("!(dataSource instanceof Media3PushDataSource)", dvd_setup)
        self.assertIn("dataSource = new Media3PushDataSource()", dvd_setup)
        self.assertIn("dvdSource.activateReaderGeneration(generation)", dvd_setup)

    def test_all_disc_user_controls_are_grouped_in_one_submenu(self):
        root_prefs = ROOT_PREFS.read_text(encoding="utf-8")
        disc_prefs = DVD_PREFS.read_text(encoding="utf-8")
        self.assertIn('android:key="disc_playback_settings"', root_prefs)
        for key in (
            "disc_playback_policy",
            "disc_skip_menus",
            "disc_skip_previews",
            "disc_compatibility_fallback",
        ):
            self.assertIn(f'android:key="{key}"', disc_prefs)
            self.assertNotIn(f'android:key="{key}"', root_prefs)

    def test_disc_settings_are_automation_configurable_and_visible(self):
        config = (ROOT / "source/dev/android-tv/src/debug/java/opensagetv/vibe/miniclient/android/tv/debug/DebugPlayerConfigCommands.java").read_text(encoding="utf-8")
        provider = (ROOT / "source/dev/android-tv/src/debug/java/opensagetv/vibe/miniclient/android/tv/debug/DebugStateProvider.java").read_text(encoding="utf-8")
        for key in (
            "disc_playback_policy",
            "disc_skip_menus",
            "disc_skip_previews",
            "disc_compatibility_fallback",
        ):
            self.assertIn(key, config)
        for field in (
            "discPlaybackPolicy",
            "discSkipMenus",
            "discSkipPreviews",
            "discCompatibilityFallback",
            "discOldServerNativeFallback",
            "discMimRuntimeFallback",
            "discCompatibilityReason",
        ):
            self.assertIn(field, provider)
        harness = DVD_MCP_HARNESS.read_text(encoding="utf-8")
        for option in (
            "--disc-policy", "--skip-menus", "--skip-previews",
            "--no-native-fallback",
        ):
            self.assertIn(option, harness)
        self.assertIn('"discOldServerNativeFallback"', harness)
        self.assertIn('"discMimRuntimeFallback"', harness)
        self.assertIn('"discCompatibilityReason"', harness)

    def test_complete_server_dvd_command_family_is_bounded(self):
        media = MEDIA.read_text(encoding="utf-8")
        for command in ("DVD_NEWCELL", "DVD_CLUT", "DVD_SPUCTRL", "DVD_STC", "DVD_STREAMS", "DVD_FORMAT"):
            self.assertIn(f"MEDIACMD_{command}", media)
        self.assertIn("payloadSize <= len - 4", media)
        self.assertIn("ensureDvdPushPlayer();", media)
        self.assertIn('newPlayerPlugin("push:dvd")', media)

    def test_navigation_metadata_contract_is_backend_compatible(self):
        plugin = PLUGIN.read_text(encoding="utf-8")
        base = BASE.read_text(encoding="utf-8")
        self.assertIn("default void dvdSetSpuControl", plugin)
        self.assertIn("public void dvdSetSpuControl", base)
        self.assertIn("DvdHighlightOverlay.attach", base)
        self.assertIn("DvdHighlightOverlay.detach", base)

    def test_selected_dvd_button_rectangle_is_exposed_to_debug_automation(self):
        decoder = DVD_SPU.read_text(encoding="utf-8")
        diagnostics = (ROOT / "source/dev/core/src/main/java/opensagetv/vibe/miniclient/dvd/DvdDiagnostics.java").read_text(encoding="utf-8")
        provider = DEBUG_STATE.read_text(encoding="utf-8")
        mcp = MCP_SERVER.read_text(encoding="utf-8")
        for field in (
            "highlightVisible", "highlightX1", "highlightY1",
            "highlightX2", "highlightY2", "highlightPaletteWord",
        ):
            self.assertIn(field, diagnostics)
            self.assertIn(f"diagnostics.{field}", decoder)
        for field in (
            "dvdHighlightVisible", "dvdHighlightX1", "dvdHighlightY1",
            "dvdHighlightX2", "dvdHighlightY2", "dvdHighlightPaletteWord",
        ):
            self.assertIn(field, provider)
            self.assertIn(field, mcp)

    def test_pre_player_dvd_handshake_remains_visible_to_mcp(self):
        provider = (ROOT / "source/dev/android-tv/src/debug/java/opensagetv/vibe/miniclient/android/tv/debug/DebugStateProvider.java").read_text(encoding="utf-8")
        before_player_gate = provider.split("if (!playerActive || mediaCmd == null)", 1)[0]
        for field in (
            "dvdSessionPending", "dvdInitCount", "dvdPushCommandCount",
            "dvdDrainPollCount", "dvdNewCellCount", "dvdStcCount",
        ):
            self.assertIn(field, before_player_gate)

    def test_dvd_init_publishes_push_capacity_without_openurl(self):
        media = MEDIA.read_text(encoding="utf-8")
        init_case = media.split("case MEDIACMD_INIT:", 1)[1].split(
            "case MEDIACMD_DEINIT:", 1
        )[0]
        self.assertIn("resetDvdProtocolStats();", init_case)
        self.assertIn("maxPrebufferSize = DESIRED_VIDEO_PREBUFFER_SIZE;", init_case)
        self.assertNotIn("maxPrebufferSize = 0", init_case)

    def test_dvd_push_replies_never_append_normal_detailed_stats(self):
        media = MEDIA.read_text(encoding="utf-8")
        push_case = media.split("case MEDIACMD_PUSHBUFFER:", 1)[1].split(
            "case MEDIACMD_GETVOLUME:", 1
        )[0]
        self.assertIn("dvdSessionPending && (playa != null || buffSize == 0", push_case)
        self.assertNotIn("!(dvdSessionPending && playa != null)", push_case)
        # Ordinary pre-OPENURL bandwidth probes contain a payload and retain
        # their negotiated detailed reply; MiniDVD's empty poll stays 4-byte.
        self.assertIn("buffSize == 0", push_case)

    def test_subpicture_overlay_is_noninteractive_and_bitmap_backed(self):
        overlay = OVERLAY.read_text(encoding="utf-8")
        base = BASE.read_text(encoding="utf-8")
        self.assertIn("setClickable(false)", overlay)
        self.assertIn("setFocusable(false)", overlay)
        self.assertIn("canvas.drawBitmap", overlay)
        self.assertNotIn("drawRect", overlay)
        self.assertIn("controller.getVideoView()", overlay)
        self.assertIn("matchVideoView", overlay)
        self.assertIn("getLocationInWindow", overlay)
        self.assertIn("dvdHighlightOverlay.matchVideoView", base)

    def test_dvd_spu_uses_authored_rle_clut_and_highlight_palette(self):
        decoder = DVD_SPU.read_text(encoding="utf-8")
        core_decoder = DVD_SPU_CORE.read_text(encoding="utf-8")
        compositor = DVD_SPU_COMPOSITOR.read_text(encoding="utf-8")
        yuv = DVD_YUV_PALETTE.read_text(encoding="utf-8")
        extractor = DVD_PS.read_text(encoding="utf-8")
        base = BASE.read_text(encoding="utf-8")
        self.assertIn("DvdSpuAssembler", decoder)
        self.assertIn("DvdSpuCompositor", decoder)
        self.assertIn("decodeField", core_decoder)
        self.assertIn("CMD_CHG_COLCON", core_decoder)
        self.assertIn("highlight.contains", compositor)
        self.assertIn("toArgb", yuv)
        self.assertIn("substreamId >= 0x20 && substreamId <= 0x3F", extractor)
        self.assertIn("dvdSubpictureDecoder.setClut(payload)", base)
        self.assertIn("dvdSubpictureDecoder.setHighlight(highlight)", base)

    def test_client_advertises_server_owned_navigation(self):
        connection = CONNECTION.read_text(encoding="utf-8")
        self.assertIn('"DVD_REMOTE_NAV".equals(propName)', connection)
        dvd_nav = connection.split('"DVD_REMOTE_NAV".equals(propName)', 1)[1].split(
            'else if ("VIBE_DISC_TRANSPORTS"', 1
        )[0]
        self.assertIn('propVal = "TRUE"', dvd_nav)
        self.assertIn('"VIBE_DISC_TRANSPORTS".equals(propName)', connection)
        self.assertIn('propVal = "native,mim_ts_v1"', connection)
        for prop in (
            "VIBE_DISC_POLICY",
            "VIBE_DISC_SKIP_MENUS",
            "VIBE_DISC_SKIP_PREVIEWS",
            "VIBE_DISC_NATIVE_FALLBACK",
        ):
            self.assertIn(prop, connection)

    def test_disc_policy_fails_closed_or_reports_native_fallback(self):
        policy = DISC_POLICY.read_text(encoding="utf-8")
        self.assertIn("Effective.UNAVAILABLE", policy)
        self.assertIn("allowNativeFallback", policy)
        self.assertIn("advertisesRemoteNavigation", policy)
        self.assertIn("Hybrid DISC playback requires", policy)

    def test_wrapped_dvd_push_url_selects_dvd_specific_extractor(self):
        media3 = MEDIA3.read_text(encoding="utf-8")
        factory = DVD_FACTORY.read_text(encoding="utf-8")
        self.assertIn('"push:dvd".equals(lastUri)', media3)
        self.assertIn('sageTVurl.endsWith("/push:dvd")', media3)
        self.assertIn("new DvdPsExtractor(state, subpictureDecoder,", factory)

    def test_legacy_exo_bypasses_generic_sniffing_for_dvd_push_epochs(self):
        legacy = LEGACY_EXO.read_text(encoding="utf-8")
        self.assertIn('"push:dvd".equals(lastUri)', legacy)
        self.assertIn('sageTVurl.endsWith("/push:dvd")', legacy)
        self.assertIn("createDvdExtractorsFactory()", legacy)
        self.assertIn("new PsExtractor()", legacy)
        self.assertIn("dvdPushMode", legacy.split("public synchronized void flush", 1)[0])

    def test_uncommissioned_disc_backends_resolve_to_media3(self):
        factory = PLAYER_FACTORY.read_text(encoding="utf-8")
        self.assertIn("resolveForUrl", factory)
        self.assertIn('"push:dvd".equals(urlString)', factory)
        self.assertIn("return PlayerBackend.MEDIA3", factory)
        for renderer in (
            ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/opengl/OpenGLRenderer.java",
            ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/gdx/MiniClientGDXRenderer.java",
        ):
            text = renderer.read_text(encoding="utf-8")
            self.assertIn("PlayerFactory.resolveForUrl(requestedBackend, urlString)", text)
            self.assertIn("msg_disc_backend_fallback", text)
            self.assertIn("msg_disc_old_server_native", text)
            self.assertIn("msg_disc_mim_runtime_fallback", text)
            self.assertIn('urlString.contains("fallback=mim_failure")', text)
            self.assertIn("DiscPlaybackPolicy.Effective.UNAVAILABLE", text)

    def test_dvd_private_ac3_is_split_and_access_unit_aligned(self):
        extractor = DVD_PS.read_text(encoding="utf-8")
        reader = DVD_AC3.read_text(encoding="utf-8")
        self.assertIn("substreamId < 0x80 || substreamId > 0x87", extractor)
        self.assertIn("firstAccessUnitPointer", extractor)
        self.assertIn("alignToAccessUnit()", extractor)
        self.assertIn("frameSizeCode <= 37", reader)
        self.assertIn("bitstreamId <= 10", reader)

    def test_dvd_audio_accepts_forward_newcell_timeline_offsets(self):
        reader = DVD_AC3.read_text(encoding="utf-8")
        self.assertIn("if (deltaUs > 0)", reader)
        self.assertIn("NEWCELL applies the SageTV DVD VM's timeline offset", reader)

    def test_packed_dvd_audio_stream_id_is_mapped_to_media3_format_id(self):
        media3 = MEDIA3.read_text(encoding="utf-8")
        stream_code = DVD_AUDIO_STREAM_CODE.read_text(encoding="utf-8")
        audio = media3.split("public void setAudioTrack", 1)[1].split(
            "public synchronized void flush", 1
        )[0]
        self.assertIn("resolveDvdAudioTrackGroup(streamPos)", audio)
        self.assertIn("group.getFormat(trackIndex).id", audio)
        self.assertIn("dvdFormatIdMatchesStream", audio)
        self.assertIn("requestedDvdAudioStream", audio)
        self.assertIn("DvdAudioStreamCode.decode(streamPos)", audio)
        self.assertIn("CodecFamily.MPEG_AUDIO", audio)
        self.assertIn("formatIdMatchesNumericId(formatId, stream.pesStreamId)", audio)
        for family in ("AC3", "DTS", "SDDS", "LPCM", "MPEG_AUDIO"):
            self.assertIn(family, stream_code)
        self.assertIn("appliedDvdAudioStream", audio)
        self.assertIn("onTracksChanged(Tracks tracks)", media3)
        self.assertIn("applyRequestedDvdAudioTrack", audio)
        self.assertIn("getSelectedDvdAudioFormatIdForDebug", media3)
        self.assertIn("dvdSelectedAudioFormatId", DEBUG_STATE.read_text(encoding="utf-8"))
        self.assertNotIn("changeTrack(C.TRACK_TYPE_AUDIO, streamPos, 0)", audio)
        self.assertIn("initialAudioTrackIndex = normalizedStreamPos", audio)

    def test_imported_spu_health_is_exposed_to_bounded_debug_snapshots(self):
        base = BASE.read_text(encoding="utf-8")
        debug = DEBUG_STATE.read_text(encoding="utf-8")
        mcp = MCP_SERVER.read_text(encoding="utf-8")
        self.assertIn('"discOldServerNativeFallback"', mcp)
        self.assertIn('"discMimRuntimeFallback"', mcp)
        self.assertIn('"discCompatibilityReason"', mcp)
        self.assertIn("getDvdSubpictureDiagnosticsForDebug", base)
        for field in (
            "dvdSpuFragments",
            "dvdCompletedSpuPackets",
            "dvdMalformedSpuPackets",
            "dvdDecodedSpuEvents",
            "dvdDroppedSpuEvents",
            "dvdOverlaysPresented",
            "dvdOverlaysCleared",
        ):
            self.assertIn(field, debug)
            self.assertIn(f'"{field}"', mcp)

    def test_dvd_video_preserves_decode_order_b_frame_pts(self):
        extractor = DVD_PS.read_text(encoding="utf-8")
        self.assertIn("timeUs = C.TIME_UNSET", extractor)
        self.assertIn("timeUs = adjustedPtsUs", extractor)
        self.assertIn("B-frame PTS values are intentionally non-monotonic", extractor)
        self.assertNotIn("adjustedPtsUs > lastPacketPtsUs", extractor)

    def test_dvd_video_observes_b_frame_pts_without_rewriting_them(self):
        extractor = DVD_PS.read_text(encoding="utf-8")
        self.assertIn("class ObservingVideoTrackOutput", extractor)
        self.assertIn("timestampState.noteVideoSample(timeUs, false)", extractor)
        self.assertIn("delegate.sampleMetadata(timeUs", extractor)
        self.assertNotIn("outputTimeUs = lastTimeUs + frameStepUs", extractor)
        self.assertNotIn("MAX_PLAUSIBLE_FRAME_STEP_US", extractor)

    def test_dvd_av_timestamp_delta_is_exposed_for_physical_sync_validation(self):
        extractor = DVD_PS.read_text(encoding="utf-8")
        factory = DVD_FACTORY.read_text(encoding="utf-8")
        media3 = MEDIA3.read_text(encoding="utf-8")
        provider = (ROOT / "source/dev/android-tv/src/debug/java/opensagetv/vibe/miniclient/android/tv/debug/DebugStateProvider.java").read_text(encoding="utf-8")
        self.assertIn("noteVideoSample", extractor)
        self.assertIn("noteAudioSample", extractor)
        self.assertIn("getLatestVideoSampleUs", factory)
        self.assertIn("getDvdAvSampleDeltaUsForDebug", media3)
        self.assertIn("dvdAvSampleDeltaUs", provider)
        self.assertIn("getDvdRenderedVideoClockDeltaUsForDebug", media3)
        self.assertIn("dvdRenderedVideoClockDeltaUs", provider)

    def test_media3_reports_server_dvd_stc_across_zero_based_periods(self):
        media3 = MEDIA3.read_text(encoding="utf-8")
        provider = (ROOT / "source/dev/android-tv/src/debug/java/opensagetv/vibe/miniclient/android/tv/debug/DebugStateProvider.java").read_text(encoding="utf-8")
        player_time = media3.split("public long getPlayerMediaTimeMillis", 1)[1].split(
            "public void dvdSetStc", 1
        )[0]
        set_stc = media3.split("public void dvdSetStc", 1)[1].split(
            "public void stop", 1
        )[0]
        flush = media3.split("public synchronized void flush()", 1)[1].split(
            "protected void setupPlayer", 1
        )[0]
        self.assertIn("dvdLogicalClockBaseMs + position", player_time)
        self.assertIn("0xFFFFFFFFL", set_stc)
        self.assertIn("45_000L", set_stc)
        self.assertIn("dvdLogicalClockBaseMs += getPlaybackPosition()", flush)
        self.assertIn("dvdStc45Khz", provider)
        self.assertIn("dvdLogicalClockBaseMs", provider)

    def test_dvd_subpicture_decoder_filters_to_server_selected_stream(self):
        decoder = (ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/DvdSubpictureDecoder.java").read_text(encoding="utf-8")
        media = MEDIA.read_text(encoding="utf-8")
        base = BASE.read_text(encoding="utf-8")
        self.assertIn("setSubpictureStream", decoder)
        self.assertIn("packet.streamId != selectedSubstreamId", decoder)
        self.assertIn("streamPosition == 62", decoder)
        self.assertIn("(streamPosition & 0x80) != 0", decoder)
        self.assertIn("(streamPosition & 0x40) == 0", decoder)
        self.assertNotIn("legacyDisableEncodingSeen", decoder)
        self.assertIn("!subpicturesDisabled || highlight.visible", decoder)
        hide_case = decoder.split("if (!frame.display)", 1)[1].split(
            "continue;", 1
        )[0]
        self.assertIn("visibleFrame = null", hide_case)
        disable_case = decoder.split("if (streamPosition == 62)", 1)[1].split(
            "else", 1
        )[0]
        self.assertNotIn("selectedSubstreamId =", disable_case)
        self.assertIn("CMD_FORCE_DISPLAY", DVD_SPU_CORE.read_text(encoding="utf-8"))
        self.assertNotIn("subpicturesDisabled || substreamId", decoder)
        self.assertIn("playa.dvdSetStream(streamType, streamPos)", media)
        self.assertIn("dvdSubpictureDecoder.setSubpictureStream(streamPosition)", base)
        self.assertIn("dvdLastStreamPosition = streamPos", media)
        self.assertIn("dvdLastSubtitleStreamPosition = streamPos", media)
        self.assertIn("dvdLastStreamPosition", DEBUG_STATE.read_text(encoding="utf-8"))
        self.assertIn("dvdLastSubtitleStreamPosition", DEBUG_STATE.read_text(encoding="utf-8"))

    def test_buffered_dvd_spu_events_share_the_selected_stream_generation(self):
        decoder = DVD_SPU.read_text(encoding="utf-8")
        push = decoder.split("public synchronized void pushFragment", 1)[1].split(
            "public synchronized void pushFragment", 1
        )[0]
        self.assertIn("long generation = eventGeneration;", push)
        self.assertNotIn("long generation = ++eventGeneration;", push)
        self.assertIn("eventGeneration++;", decoder.split("public synchronized void reset", 1)[1])
        self.assertIn("eventGeneration++;", decoder.split("public synchronized void setSubpictureStream", 1)[1])

    def test_repeated_dvd_presentation_state_does_not_invalidate_queued_spu_events(self):
        decoder = DVD_SPU.read_text(encoding="utf-8")
        clear_highlight = decoder.split(
            "public synchronized void clearHighlight", 1
        )[1].split("public synchronized boolean isHighlightActive", 1)[0]
        set_clut = decoder.split(
            "public synchronized void setClut", 1
        )[1].split("public synchronized void setHighlight", 1)[0]
        set_highlight = decoder.split(
            "public synchronized void setHighlight", 1
        )[1].split("public synchronized void pushFragment", 1)[0]
        self.assertNotIn("eventGeneration++", clear_highlight)
        self.assertNotIn("eventGeneration++", set_clut)
        self.assertNotIn("eventGeneration++", set_highlight)
        self.assertIn("changed |= clut[i] != value", set_clut)
        self.assertIn("sameHighlight(highlight, next)", set_highlight)

    def test_debug_build_can_capture_exact_push_bytes_for_dvd_diagnostics(self):
        receiver = (ROOT / "source/dev/android-tv/src/debug/java/opensagetv/vibe/miniclient/android/tv/debug/DevTestReceiver.java").read_text(encoding="utf-8")
        self.assertIn('"capture_datasource".equals(op)', receiver)
        self.assertIn("VerboseLogging.LOG_DATASOURCE_BYTES_TO_FILE = enabled", receiver)
        self.assertIn('new File(context.getFilesDir(), "captures")', receiver)
        self.assertIn("captureDir.mkdirs()", receiver)
        self.assertIn("appliesNextPlayback=true", receiver)
        adb = (ROOT / "mcp/src/sagetv_dev_mcp/adb.py").read_text(encoding="utf-8")
        server = (ROOT / "mcp/src/sagetv_dev_mcp/server.py").read_text(encoding="utf-8")
        self.assertIn("def set_datasource_capture", adb)
        self.assertIn('self.dev_control(\n            "capture_datasource"', adb)
        self.assertIn("def dev_set_datasource_capture", server)

    def test_dvd_push_uses_bounded_time_buffer_not_media3_fifty_second_default(self):
        media3 = MEDIA3.read_text(encoding="utf-8")
        load_control = media3.split("if (dvdPushMode)", 1)[1].split(
            "else if (!pushMode && !httpls)", 1
        )[0]
        self.assertIn("setBufferDurationsMs(2_000, 8_000, 0, 0)", load_control)
        self.assertIn("setPrioritizeTimeOverSizeThresholds(true)", load_control)

    def test_dvd_frame_release_cadence_is_exposed_for_physical_judder_diagnosis(self):
        media3 = MEDIA3.read_text(encoding="utf-8")
        provider = (ROOT / "source/dev/android-tv/src/debug/java/opensagetv/vibe/miniclient/android/tv/debug/DebugStateProvider.java").read_text(encoding="utf-8")
        self.assertIn("setVideoFrameMetadataListener", media3)
        self.assertIn("dvdFrameReleaseGapCount", media3)
        self.assertIn("else if (deltaUs <= 100_000L)", media3)
        self.assertIn("dvdFrameReleaseGapCount++", media3)
        self.assertIn("dvdLastFramePresentationDeltaUs", provider)
        self.assertIn("dvdLastFrameReleaseDeltaUs", provider)
        self.assertIn("dvdFrameCadenceTrace", provider)
        self.assertIn("DVD_FRAME_CADENCE_TRACE_SIZE = 64", media3)
        self.assertIn("dvdFrameReleaseUnder10MsCount", media3)
        self.assertIn("dvdFrameRelease10To25MsCount", media3)
        self.assertIn("dvdFrameRelease25To45MsCount", media3)
        self.assertIn("dvdFrameRelease45To75MsCount", media3)
        self.assertIn("dvdFrameRelease75To100MsCount", media3)

    def test_dvd_newcell_applies_historical_45khz_pts_offset(self):
        base = BASE.read_text(encoding="utf-8")
        extractor = DVD_PS.read_text(encoding="utf-8")
        factory = DVD_FACTORY.read_text(encoding="utf-8")
        media3 = MEDIA3.read_text(encoding="utf-8")
        self.assertIn("dvdPtsOffset90Khz = (long) offset45Khz * 2L", base)
        self.assertIn("timestampState.applyPtsOffset(pts)", extractor)
        self.assertIn("timestampState.applyPtsOffset(dts)", extractor)
        self.assertIn("new DvdPsExtractor(state, subpictureDecoder,", factory)
        self.assertIn("dvdExtractorsFactory.setPtsOffset90Khz(getDvdPtsOffset90KhzForDebug())", media3)

    def test_dvd_newcell_reanchors_elementary_stream_clocks_without_player_reset(self):
        extractor = DVD_PS.read_text(encoding="utf-8")
        self.assertIn("cellGeneration++", extractor)
        self.assertIn("getCellGeneration()", extractor)
        self.assertIn("payloadReader.seek();", extractor)
        self.assertIn("substreams.valueAt(i).seek();", extractor)

    def test_dvd_newcell_offset_is_applied_at_its_push_byte_boundary(self):
        extractor = DVD_PS.read_text(encoding="utf-8")
        factory = DVD_FACTORY.read_text(encoding="utf-8")
        media3 = MEDIA3.read_text(encoding="utf-8")
        self.assertIn("class PtsBoundary", extractor)
        self.assertIn("queuePtsOffset90Khz", extractor)
        self.assertIn("advanceToPosition(input.getPosition())", extractor)
        self.assertIn("queuePtsOffset90Khz", factory)
        self.assertIn("getPushedBytes()", media3)
        dvd_new_cell = media3.split("public void dvdNewCell", 1)[1].split(
            "private void cancelProgressUpdates", 1
        )[0]
        self.assertIn("factory.queuePtsOffset90Khz", dvd_new_cell)
        self.assertNotIn("factory.setPtsOffset90Khz", dvd_new_cell)

    def test_dvd_newcell_rebases_absolute_stc_to_continuous_media3_timeline(self):
        extractor = DVD_PS.read_text(encoding="utf-8")
        self.assertIn("pendingCellAnchorUs", extractor)
        self.assertIn("pendingCellNormalization = true", extractor)
        self.assertIn("normalizeCellTimeUs", extractor)
        self.assertIn("presentationShiftUs = pendingCellAnchorUs + 1L - adjustedTimeUs", extractor)
        self.assertGreaterEqual(extractor.count("timestampState.normalizeCellTimeUs("), 2)
        advance = extractor.split("synchronized void advanceToPosition", 1)[1].split(
            "long applyPtsOffset", 1
        )[0]
        self.assertIn("latestVideoPesUs", advance)
        self.assertIn("latestAudioPesUs", advance)
        self.assertIn("MAX_CONTINUOUS_AV_DELTA_US = 30_000_000L", extractor)
        self.assertIn("latestContinuousAvTimeUs()", extractor)
        self.assertIn("discontinuityRebaseCount++", extractor)

    def test_dvd_flush_discards_old_byte_epoch_pts_boundaries(self):
        extractor = DVD_PS.read_text(encoding="utf-8")
        media3 = MEDIA3.read_text(encoding="utf-8")
        set_offset = extractor.split(
            "synchronized void setPtsOffset90Khz", 1
        )[1].split("synchronized void queuePtsOffset90Khz", 1)[0]
        self.assertIn("pendingPtsBoundaries.clear()", set_offset)
        flush = media3.split("public synchronized void flush()", 1)[1].split(
            "protected void setupPlayer", 1
        )[0]
        scheduler = media3.split(
            "private synchronized void schedulePendingDvdReprepare", 1
        )[1].split("public void setPlaybackPosition", 1)[0]
        self.assertIn("dvdRepreparePending = true", flush)
        self.assertNotIn("factory.beginRepreparedEpoch", flush)
        self.assertIn("nativeFactory.beginRepreparedEpoch", scheduler)

    def test_dvd_drain_accounting_uses_current_flush_epoch(self):
        media_cmd = MEDIA.read_text(encoding="utf-8")
        self.assertIn("dvdEpochPushedBytes", media_cmd)
        self.assertIn("dvdEpochPushedBytes = 0", media_cmd)
        self.assertIn("dvdEpochPushedBytes += buffSize", media_cmd)
        self.assertIn(
            "dvdEpochPushedBytes - Math.max(0, dvdReadBytes)", media_cmd
        )

    def test_program_end_is_only_an_intersegment_boundary_for_dvd_push(self):
        extractor = DVD_PS.read_text(encoding="utf-8")
        program_end = extractor.split("if (startCode == PROGRAM_END_CODE)", 1)[1].split(
            "if (startCode == PACK_START_CODE)", 1
        )[0]
        self.assertIn("input.skipFully(4)", program_end)
        self.assertIn("return RESULT_CONTINUE", program_end)
        self.assertNotIn("RESULT_END_OF_INPUT", program_end)

    def test_dvd_push_eos_is_a_transient_segment_boundary(self):
        media = MEDIA.read_text(encoding="utf-8")
        push = PUSH_SOURCE.read_text(encoding="utf-8")
        media3 = MEDIA3.read_text(encoding="utf-8")
        push_case = media.split("case MEDIACMD_PUSHBUFFER:", 1)[1].split(
            "case MEDIACMD_GETVOLUME:", 1
        )[0]
        self.assertIn("flags == 0x80 && dvdSessionPending", push_case)
        self.assertIn("dvdTransientEosCount++", push_case)
        self.assertIn("TransientPushSegmentPlayer", push_case)
        self.assertIn("signalPushSegmentEnd()", push_case)
        self.assertIn("dvdDrainSignaledEpochBytes != dvdEpochPushedBytes", push_case)
        self.assertIn("inputDrained && !decoderDrained", push_case)
        self.assertIn("signalActiveReadGenerationEnd", media3)
        self.assertIn("dvdSegmentReaderEnded && !dvdRepreparePending", media3)
        self.assertIn('PlaybackDebugTrap.record("dvd_segment_reprepare_pending"', media3)
        self.assertIn("generation == endedReadGeneration", push)
        self.assertIn("endedReadGeneration = Long.MIN_VALUE", push)
        ordinary_eos = push_case.split("else if (flags == 0x80)", 1)[1]
        self.assertIn("playa.setServerEOS()", ordinary_eos)

    def test_dvd_ac3_preserves_clock_when_pes_has_no_pts(self):
        reader = DVD_AC3.read_text(encoding="utf-8")
        self.assertIn("pesTimeUs == C.TIME_UNSET", reader)
        self.assertIn("return;", reader.split("pesTimeUs == C.TIME_UNSET", 1)[1])
        self.assertIn("if (deltaUs > 0)", reader)

    def test_media3_dvd_flush_preserves_static_menu_frame(self):
        media3 = MEDIA3.read_text(encoding="utf-8")
        flush_case = media3.split("public synchronized void flush()", 1)[1].split(
            "protected void setupPlayer", 1
        )[0]
        self.assertIn('PlaybackDebugTrap.record("dvd_flush_preserve_surface"', flush_case)
        self.assertLess(flush_case.index("if (dvdPushMode)"), flush_case.index("player.setMediaSource"))
        self.assertIn("dvdRepreparePending = true", flush_case)
        self.assertIn("schedulePendingDvdReprepare", media3)
        self.assertIn("dvdPlayer.setMediaSource(replacementSource, 0L)", media3)
        self.assertIn("dvdPlayer.stop()", media3)
        scheduler = media3.split(
            "private synchronized void schedulePendingDvdReprepare", 1
        )[1].split("public void setPlaybackPosition", 1)[0]
        self.assertNotIn("dvdPlayer.clearVideoSurface()", scheduler)
        self.assertNotIn("dvdPlayer.setVideoSurfaceView", scheduler)
        self.assertNotIn("setRendererDisabled", scheduler)
        self.assertIn("without ever latching", scheduler)
        self.assertNotIn("dvdPlayer.setMediaSource(mediaSource, true)", flush_case)

    def test_dvd_reprepare_suspends_old_loader_until_new_source_is_ready(self):
        media3 = MEDIA3.read_text(encoding="utf-8")
        push = PUSH_SOURCE.read_text(encoding="utf-8")
        flush_case = media3.split("public synchronized void flush()", 1)[1].split(
            "protected void setupPlayer", 1
        )[0]
        scheduler = media3.split(
            "private synchronized void schedulePendingDvdReprepare", 1
        )[1].split("public void setPlaybackPosition", 1)[0]
        self.assertIn("dvdSource.suspendReads()", flush_case)
        self.assertNotIn("dvdSource.resumeReads()", flush_case)
        self.assertIn("dvdSource.resumeReads()", scheduler)
        self.assertLess(flush_case.index("dvdSource.suspendReads()"), flush_case.index("super.flush()"))
        self.assertIn("if (readsSuspended)", push)
        self.assertIn("public void suspendReads()", push)
        self.assertIn("public void resumeReads()", push)

    def test_dvd_flush_resets_equal_sized_cell_drain_guard(self):
        media = MEDIA.read_text(encoding="utf-8")
        flush_case = media.split("case MEDIACMD_FLUSH:", 1)[1].split(
            "case MEDIACMD_PUSHBUFFER:", 1
        )[0]
        self.assertIn("dvdEpochPushedBytes = 0", flush_case)
        self.assertIn("dvdDrainSignaledEpochBytes = Long.MIN_VALUE", flush_case)
        self.assertLess(
            flush_case.index("dvdEpochPushedBytes = 0"),
            flush_case.index("dvdDrainSignaledEpochBytes = Long.MIN_VALUE"),
        )

    def test_dvd_reprepare_invalidates_the_cancelled_media3_reader(self):
        media3 = MEDIA3.read_text(encoding="utf-8")
        push = PUSH_SOURCE.read_text(encoding="utf-8")
        media3_push = MEDIA3_PUSH_SOURCE.read_text(encoding="utf-8")
        self.assertIn("dvdSource.activateReaderGeneration(generation)", media3)
        self.assertIn("dvdSource.readerFactory(generation)", media3)
        self.assertIn("readBlockingForGeneration", push)
        self.assertIn("generation != activeReadGeneration", push)
        self.assertIn("class ReaderLease", media3_push)

    def test_mim_dvd_reprepare_builds_a_fresh_mpeg_ts_reader_generation(self):
        media3 = MEDIA3.read_text(encoding="utf-8")
        scheduler = media3.split(
            "private synchronized void schedulePendingDvdReprepare", 1
        )[1].split("public void setPlaybackPosition", 1)[0]
        self.assertIn("ExtractorsFactory dvdEpochExtractorsFactory", media3)
        self.assertIn("dvdEpochExtractorsFactory = pushExtractors", media3)
        self.assertIn("ExtractorsFactory epochFactory = dvdEpochExtractorsFactory", scheduler)
        self.assertIn("dvdSource.readerFactory(generation), epochFactory", scheduler)
        self.assertNotIn("dvdSource != null && factory != null", scheduler)

    def test_complete_single_frame_dvd_menu_sequence_is_published(self):
        extractor = DVD_PS.read_text(encoding="utf-8")
        pes_reader = extractor.split("private static final class PesReader", 1)[1].split(
            "private static final class DvdPrivateStreamPesReader", 1
        )[0]
        self.assertIn("containsSequenceEndCode", pes_reader)
        self.assertIn("(bytes[i + 3] & 0xFF) == 0xB7", pes_reader)
        self.assertIn("payloadReader.endOfInputReached()", pes_reader)
        self.assertIn("if (inputFinalized)", pes_reader)
        self.assertIn("stillFrameRepeater.completeSequence()", pes_reader)

    def test_static_dvd_menu_paces_bounded_future_video_samples_with_audio(self):
        extractor = DVD_PS.read_text(encoding="utf-8")
        repeater = extractor.split("private static final class StillFrameRepeater", 1)[1].split(
            "private static final class PesReader", 1
        )[0]
        self.assertIn("REPEAT_INTERVAL_US = 500_000L", repeater)
        self.assertIn("repeatLastSampleAt", repeater)
        self.assertIn("while (output.getLastSampleTimeUs()", repeater)
        self.assertIn("stillFrameRepeater.repeatThrough", extractor)
        self.assertIn("MAX_CAPTURED_SAMPLE_BYTES = 2 * 1024 * 1024", extractor)
        self.assertIn("sequenceSampleCount == 1", extractor)
        self.assertIn("sequenceComplete = output != null && output.isSinglePictureSequence()", repeater)
        self.assertIn("stillFrameRepeater.beginSequence()", extractor)
        self.assertIn("if (captureCandidate)", extractor)
        self.assertIn("captureCandidate = false", extractor)
        self.assertIn("MIN_VIDEO_ONLY_STILL_DURATION_US = 2_000_000L", extractor)
        self.assertIn("ensureDecoderStartupWindow()", extractor)
        self.assertIn("extendDecoderStartupWindow(output)", repeater)
        self.assertIn(
            "sequenceComplete = sequenceComplete || output.isSinglePictureSequence()",
            repeater,
        )

    def test_dvd_preserves_authored_pts_and_repairs_only_missing_picture_pts(self):
        extractor = DVD_PS.read_text(encoding="utf-8")
        observer = extractor.split("private static final class ObservingVideoTrackOutput", 1)[1].split(
            "private static final class StillFrameRepeater", 1
        )[0]
        completer = (ROOT / "source/dev/core/src/main/java/opensagetv/vibe/miniclient/video/Mpeg2PictureTimestampCompleter.java").read_text(encoding="utf-8")
        self.assertIn("timestampCompleter.completeTimestamp", observer)
        self.assertIn("delegate.sampleMetadata(outputTimeUs, flags, size, offset", observer)
        self.assertNotIn("pendingMetadata", observer)
        self.assertNotIn("shouldDefer", observer)
        self.assertIn("repeatFirstField", completer)
        self.assertIn("displayFieldCount", completer)
        self.assertIn("picture.pictureType == PICTURE_TYPE_I", completer)
        self.assertIn("picture.authoredTimestamp", completer)
        self.assertIn("telecineCadenceSeen", completer)
        self.assertIn("MAX_REPAIR_DELTA_US", completer)
        self.assertIn("disc_mpeg2_timestamp_repair", MEDIA3.read_text(encoding="utf-8"))

    def test_dvd_newcell_resets_scanner_and_sample_byte_coordinates_together(self):
        extractor = DVD_PS.read_text(encoding="utf-8")
        observer = extractor.split(
            "private static final class ObservingVideoTrackOutput", 1
        )[1].split("private static final class StillFrameRepeater", 1)[0]
        begin_sequence = observer.split("void beginSequence()", 1)[1].split(
            "boolean isSinglePictureSequence()", 1
        )[0]
        self.assertIn("totalBytesForwarded = 0L", begin_sequence)
        self.assertIn("timestampCompleter.reset()", begin_sequence)
        self.assertLess(
            begin_sequence.index("totalBytesForwarded = 0L"),
            begin_sequence.index("timestampCompleter.reset();"),
        )
        self.assertIn("describeVideoTimestampNear", extractor)
        self.assertIn("timestampDecisionName", extractor)

    def test_static_dvd_menu_defers_reprepare_until_replacement_mpeg_arrives(self):
        media3 = MEDIA3.read_text(encoding="utf-8")
        push_data = media3.split("public void pushData", 1)[1].split(
            "private synchronized void schedulePendingDvdReprepare", 1
        )[0]
        scheduler = media3.split("private synchronized void schedulePendingDvdReprepare", 1)[1].split(
            "public void setPlaybackPosition", 1
        )[0]
        self.assertIn("dvdRepreparePending && buffSize > 0", push_data)
        self.assertIn("schedulePendingDvdReprepare()", push_data)
        self.assertIn("nativeFactory.beginRepreparedEpoch", scheduler)
        self.assertIn("dvdSource.resumeReads()", scheduler)

    def test_paused_dvd_epoch_renders_its_still_before_honoring_pause(self):
        media3 = MEDIA3.read_text(encoding="utf-8")
        scheduler = media3.split(
            "private synchronized void schedulePendingDvdReprepare", 1
        )[1].split("public void setPlaybackPosition", 1)[0]
        self.assertIn("dvdRenderedFirstFrameInEpoch = false", scheduler)
        self.assertIn("dvdPauseAfterFirstFrame = !playRequested", scheduler)
        self.assertIn("playRequested || dvdPauseAfterFirstFrame", scheduler)
        self.assertIn("dvd_pause_wait_first_frame", media3)
        self.assertIn("dvd_pause_after_first_frame", media3)
        self.assertIn("listenerPlayer.setPlayWhenReady(false)", media3)

    def test_reprepared_dvd_epoch_rebases_first_audio_and_video_to_zero(self):
        extractor = DVD_PS.read_text(encoding="utf-8")
        factory = DVD_FACTORY.read_text(encoding="utf-8")
        epoch = extractor.split("synchronized void beginRepreparedEpoch", 1)[1].split(
            "synchronized void queuePtsOffset90Khz", 1
        )[0]
        self.assertIn("pendingCellAnchorUs = 0", epoch)
        self.assertIn("pendingCellNormalization = true", epoch)
        self.assertIn("flushPlaybackAnchorUs = 0", epoch)
        self.assertIn("latestVideoPesUs = C.TIME_UNSET", epoch)
        self.assertIn("latestAudioPesUs = C.TIME_UNSET", epoch)
        self.assertIn("nextState.beginRepreparedEpoch", factory)
        self.assertIn("timestampState = nextState", factory)

    def test_dvd_drain_uses_extractor_sample_edge_not_only_cached_media3_buffer(self):
        media3 = MEDIA3.read_text(encoding="utf-8")
        ahead = media3.split("public long getBufferedPlaybackAheadMillis()", 1)[1].split(
            "public void setPlaybackPosition", 1
        )[0]
        self.assertIn("factory.getLatestVideoSampleUs()", ahead)
        self.assertIn("factory.getLatestAudioSampleUs()", ahead)
        self.assertIn("bufferedEdgeMs", ahead)
        self.assertIn("bufferedEdgeMs - getPlaybackPosition()", ahead)

    def test_tiny_authored_dvd_cells_have_zero_start_and_rebuffer_thresholds(self):
        media3 = MEDIA3.read_text(encoding="utf-8")
        dvd_load = media3.split("if (dvdPushMode)", 1)[1].split(
            "else if (!pushMode && !httpls)", 1
        )[0]
        self.assertIn(".setBufferDurationsMs(2_000, 8_000, 0, 0)", dvd_load)
        self.assertIn("480 ms menu-transition cell", dvd_load)

    def test_audio_only_navigation_cell_finishes_track_discovery_before_its_end(self):
        extractor = DVD_PS.read_text(encoding="utf-8")
        self.assertIn("MAX_AUDIO_ONLY_SEARCH_LENGTH = 64 * 1024", extractor)
        self.assertIn("MAX_VIDEO_ONLY_SEARCH_LENGTH = 256 * 1024", extractor)
        self.assertIn("foundVideo ? MAX_VIDEO_ONLY_SEARCH_LENGTH", extractor)
        self.assertIn("output.endTracks()", extractor)
        self.assertIn("180 KiB transition cell", extractor)
        discovery = extractor.split("A private_stream_1 packet may contain only SPU data", 1)[1].split(
            "return RESULT_CONTINUE", 1
        )[0]
        self.assertIn("if (!foundAllTracks)", discovery)

    def test_spu_only_private_stream_does_not_finalize_tracks_before_late_ac3(self):
        extractor = DVD_PS.read_text(encoding="utf-8")
        private_creation = extractor.split("if (streamId == PRIVATE_STREAM_1)", 1)[1].split(
            "else if ((streamId & AUDIO_STREAM_MASK)", 1
        )[0]
        self.assertNotIn("foundAudio = true", private_creation)
        self.assertIn("audioTrackCount()", extractor)
        self.assertIn("privateAudioTracks > discoveredPrivateAudioTrackCount", extractor)
        self.assertIn("foundAudio = true", extractor)
        self.assertLess(extractor.index("reader.consume(packet)"),
                        extractor.index("A private_stream_1 packet may contain only SPU data"))

    def test_dvd_audio_override_is_marked_applied_only_after_trackgroup_commit(self):
        media3 = MEDIA3.read_text(encoding="utf-8")
        apply = media3.split("private void applyRequestedDvdAudioTrack()", 1)[1].split(
            "/** Resolves SageTV's packed DVD private-stream id", 1
        )[0]
        self.assertIn("clearOverridesOfType(C.TRACK_TYPE_AUDIO)", apply)
        self.assertLess(apply.index("trackSelector.setParameters(builder.build())"),
                        apply.index("appliedDvdAudioStream = streamPos"))

    def test_finite_dvd_cell_finishes_track_discovery_at_eof(self):
        extractor = DVD_PS.read_text(encoding="utf-8")
        end_input = extractor.split("private void endInput()", 1)[1].split(
            "private interface PacketReader", 1
        )[0]
        self.assertIn("if (!foundAllTracks)", end_input)
        self.assertIn("foundAllTracks = true", end_input)
        self.assertIn("output.endTracks()", end_input)
        self.assertIn("PARSING_CONTAINER_MALFORMED", end_input)

    def test_next_dvd_cell_bytes_keep_decoder_and_surface_alive(self):
        media3 = MEDIA3.read_text(encoding="utf-8")
        self.assertNotIn("resetDvdDecoderBeforeReplacementBytes", media3)
        self.assertNotIn("dvdBoundaryResetPending", media3)
        extractor = DVD_PS.read_text(encoding="utf-8")
        self.assertIn("beginFlushEpoch", extractor)
        self.assertIn("pendingInputEpochBase", extractor)
        self.assertIn("bytePosition - inputEpochBasePosition", extractor)

    def test_consecutive_dvd_flushes_preserve_pending_av_clock_anchor(self):
        extractor = DVD_PS.read_text(encoding="utf-8")
        flush_epoch = extractor.split("synchronized void beginFlushEpoch", 1)[1].split(
            "synchronized void queuePtsOffset90Khz", 1
        )[0]
        self.assertIn("else if (!pendingCellNormalization)", flush_epoch)
        self.assertNotIn("pendingCellNormalization = false", flush_epoch)
        self.assertIn("latestVideoPesUs", flush_epoch)
        self.assertIn("latestAudioPesUs", flush_epoch)
        self.assertIn("playbackAnchorUs", flush_epoch)
        self.assertIn("observedAnchorUs = playbackAnchorUs", flush_epoch)
        self.assertIn("flushPlaybackAnchorUs = playbackAnchorUs", flush_epoch)
        self.assertIn("flushPlaybackAnchorUs = C.TIME_UNSET", extractor)

    def test_dvd_spu_packets_do_not_consume_pending_av_clock_rebase(self):
        extractor = DVD_PS.read_text(encoding="utf-8")
        private_reader = extractor.split(
            "private static final class DvdPrivateStreamPesReader", 1
        )[1]
        spu_case = private_reader.split(
            "if (substreamId >= 0x20 && substreamId <= 0x3F)", 1
        )[1].split("if (substreamId < 0x80", 1)[0]
        self.assertNotIn("normalizeCellTimeUs", spu_case)
        audio_case = private_reader.split("if (substreamId < 0x80", 1)[1]
        self.assertIn("normalizeCellTimeUs(timeUs)", audio_case)
        self.assertIn("reader.packetStarted(audioTimeUs, PES_DATA_ALIGNMENT_INDICATOR)", audio_case)

    def test_dvd_drain_waits_for_media3_render_queue(self):
        media = MEDIA.read_text(encoding="utf-8")
        plugin = PLUGIN.read_text(encoding="utf-8")
        media3 = MEDIA3.read_text(encoding="utf-8")
        self.assertIn("getBufferedPlaybackAheadMillis", plugin)
        self.assertIn("getBufferedPlaybackAheadMillis", media3)
        self.assertIn("dvdDecoderBufferedAheadMs <= 500", media)
        self.assertIn("&& decoderDrained", media)
        self.assertIn("setBufferDurationsMs(2_000, 8_000, 0, 0)", media3)

    def test_missing_dvd_text_track_is_rejected_before_media3_lookup(self):
        media3 = MEDIA3.read_text(encoding="utf-8")
        self.assertIn("groupIndex >= trackGroup.length", media3)
        self.assertIn("trackIndex >= selectedGroup.length", media3)

    def test_physical_dvd_harness_uses_protocol_state_constants_and_stream_evidence(self):
        harness = DVD_MCP_HARNESS.read_text(encoding="utf-8")
        server = MCP_SERVER.read_text(encoding="utf-8")
        self.assertIn('command_state.get("state", -1)) == 3', harness)
        self.assertIn('command_state.get("state", -1)) == 2', harness)
        self.assertIn("--screenshot-each-command", harness)
        self.assertIn("--verify-command-recovery", harness)
        self.assertIn('"dev_wait_for_playback_started"', harness)
        self.assertIn('"select", "enter", "center", "dpad_center"', harness)
        self.assertIn('"verify_ms": args.verify_ms', harness)
        for key in (
            "dvdRequestedAudioStream",
            "dvdAppliedAudioStream",
            "dvdSelectedAudioFormatId",
            "dvdAvailableAudioFormatIds",
        ):
            self.assertIn(f'"{key}"', harness)
            self.assertIn(f'"{key}"', server)

    def test_hdmi_validation_capture_requires_video_and_audio_devices(self):
        capture = HDMI_CAPTURE.read_text(encoding="utf-8")
        wrapper = HDMI_CAPTURE_CMD.read_text(encoding="utf-8")
        self.assertIn("USB Video", capture)
        self.assertIn("Digital Audio Interface (USB Digital Audio)", capture)
        self.assertIn(":dshow-vdev=", capture)
        self.assertIn(":dshow-adev=", capture)
        self.assertIn("dshow-size=1920x1080", capture)
        self.assertIn("mux=mp4", capture)
        self.assertIn("vcodec=h264", capture)
        self.assertIn(":dshow-fps=30", capture)
        self.assertNotIn("file/avi", capture)
        self.assertIn("if size <= 0", capture)
        self.assertIn("capture_hdmi_validation.py", wrapper)
        self.assertNotIn("powershell", wrapper.lower())

    def test_controlled_dvd_reload_reseeks_only_after_replacement_release(self):
        media = MEDIA.read_text(encoding="utf-8")
        request = media.split("public boolean requestControlledPlayerReload()", 1)[1].split(
            "public void close()", 1
        )[0]
        flush = media.split("if (restartPlayerOnNextFlush)", 1)[1].split(
            "else if (dvdSessionPending", 1
        )[0]
        correction = media.split("private void correctControlledReloadLanding", 1)[1].split(
            "static boolean containsMpegPsPackHeader", 1
        )[0]
        self.assertIn("controlledReloadAwaitingReplacementStc = false", request)
        self.assertLess(flush.index("replacedPlayer.free()"),
                        flush.index("postVibeSeekEvent(controlledReloadTargetMs)"))
        self.assertIn("controlledReloadAwaitingReplacementStc", correction)
        self.assertIn("Math.abs(errorMs) <= 2_500L", correction)
        self.assertIn("controlledReloadCorrectionCount >= 2", correction)

    def test_active_adjustments_use_bounded_real_dvd_subtitle_offset(self):
        plugin = PLUGIN.read_text(encoding="utf-8")
        base = BASE.read_text(encoding="utf-8")
        dialog = ACTIVE_ADJUSTMENTS.read_text(encoding="utf-8")
        overrides = SESSION_OVERRIDES.read_text(encoding="utf-8")
        self.assertIn("supportsSubtitleOffset", plugin)
        self.assertIn("supportsAudioOffset", plugin)
        self.assertIn("presentationTimeUs + subtitleOffsetMs * 1_000L", base)
        self.assertIn("Math.max(-2_000, Math.min(2_000, offsetMs))", base)
        self.assertIn("return dvdTimingReceived && !dvdMimTransport", base)
        self.assertIn("-2000, -1000, -500, -250, 0, 250, 500, 1000, 2000", dialog)
        self.assertIn("offset is unsupported by this active output", dialog)
        self.assertIn("subtitleOffsetMs", overrides)

    def test_active_adjustments_expose_real_tracks_and_guard_passthrough(self):
        plugin = PLUGIN.read_text(encoding="utf-8")
        dialog = ACTIVE_ADJUSTMENTS.read_text(encoding="utf-8")
        media3 = MEDIA3.read_text(encoding="utf-8")
        for method in (
            "getAudioTrackIds", "getAudioTrackLabels", "getSelectedAudioTrack",
            "getAudioOutputSummary", "supportsAudioPassthroughControl",
        ):
            self.assertIn(method, plugin)
            self.assertIn(method, dialog)
        self.assertIn("getCurrentTracks().getGroups()", media3)
        self.assertIn("Passthrough cannot be changed safely", dialog)
        self.assertIn("no output mode was changed", dialog)
        self.assertIn("Preferred configured track", dialog)
        self.assertIn("active.setPreferredSubtitleTrack()", dialog)
        self.assertIn("tracks[selected - 5].getIndex()", dialog)


if __name__ == "__main__":
    unittest.main()
