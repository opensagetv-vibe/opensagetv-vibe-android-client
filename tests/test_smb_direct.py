import pathlib
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[1]


class SmbDirectArchitectureTests(unittest.TestCase):
    def read(self, relative):
        return (ROOT / relative).read_text(encoding="utf-8")

    def test_smb_modes_negotiate_as_pull_without_none_codecs(self):
        policy = self.read("source/dev/core/src/main/java/opensagetv/vibe/miniclient/StreamingModePolicy.java")
        connection = self.read("source/dev/core/src/main/java/opensagetv/vibe/miniclient/MiniClientConnection.java")
        self.assertIn('"smb_direct".equalsIgnoreCase(mode)', policy)
        self.assertIn('"smb_auto".equalsIgnoreCase(mode)', policy)
        self.assertGreaterEqual(connection.count("StreamingModePolicy.isForcedPull"), 2)
        self.assertNotIn('"smb_direct"', connection)

    def test_shadow_session_has_only_bounded_random_access_probes(self):
        shadow = self.read("source/dev/core/src/main/java/opensagetv/vibe/miniclient/net/ShadowMediaServerSession.java")
        session = self.read("source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/smb/SmbDirectSession.java")
        self.assertIn('command("OPEN " + originalPath)', shadow)
        self.assertIn('command("SIZE")', shadow)
        self.assertIn('command("CLOSE")', shadow)
        self.assertNotIn('command("READ ', shadow)
        self.assertIn('"READ " + safePosition + " 1\\r\\n"', shadow)
        self.assertIn("if (size <= 0) return 0", shadow)
        self.assertIn("if (randomAccess && shadow != null) shadow.probeRead(position);", session)
        self.assertIn("exactly one shadow byte per non-sequential SMB access", session)

    def test_both_exo_backends_use_one_smb_selector(self):
        media3 = self.read("source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/media3/Media3PullDataSource.java")
        exo2 = self.read("source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/exoplayer2/Exo2PullDataSource.java")
        for source in (media3, exo2):
            self.assertIn("new SmbSourceSelector", source)
            self.assertIn("isSmbModeConfigured", source)
            self.assertIn("getShadowReadBytes", source)

    def test_credential_fields_are_separate_and_not_returned(self):
        mapper = self.read("source/dev/core/src/main/java/opensagetv/vibe/miniclient/net/SmbPathMapper.java")
        config = self.read("source/dev/android-tv/src/debug/java/opensagetv/vibe/miniclient/android/tv/debug/DebugPlayerConfigCommands.java")
        state = self.read("source/dev/android-tv/src/debug/java/opensagetv/vibe/miniclient/android/tv/debug/DebugStateProvider.java")
        self.assertIn("credentials must not be embedded", mapper)
        self.assertIn("smbAuthConfigured", config)
        self.assertNotIn("SMB_PASSWORD", state)
        self.assertNotIn("getSmbPassword", state)

    def test_debug_smb_paths_preserve_case(self):
        config = self.read("source/dev/android-tv/src/debug/java/opensagetv/vibe/miniclient/android/tv/debug/DebugPlayerConfigCommands.java")
        self.assertIn('String smbMappings = text(intent.getStringExtra("smb_mappings"))', config)
        self.assertIn('String smbProfileDirectory = text(intent.getStringExtra("smb_profile_directory"))', config)
        self.assertNotIn('String smbMappings = clean(intent.getStringExtra("smb_mappings"))', config)

    def test_ui_and_mcp_expose_smb_modes_and_proof_fields(self):
        arrays = self.read("source/dev/android-shared/src/main/res/values/arrays.xml")
        mcp = self.read("mcp/src/sagetv_dev_mcp/server.py")
        self.assertIn("smb_direct", arrays)
        self.assertIn("smb_auto", arrays)
        for key in ("playbackSource", "sageOriginalPath", "smbMappedPath",
                    "shadowReadBytes", "smbBytesRead", "smbFallbackReason"):
            self.assertIn(f'"{key}"', mcp)

    def test_smb_socket_survives_healthy_player_idle_periods(self):
        session = self.read(
            "source/dev/android-shared/src/main/java/"
            "opensagetv/vibe/miniclient/android/video/smb/SmbDirectSession.java"
        )
        self.assertIn(".withTimeout(30, TimeUnit.SECONDS)", session)
        self.assertIn(".withSoTimeout(0)", session)
        self.assertNotIn(".withSoTimeout(10, TimeUnit.SECONDS)", session)

    def test_smb_range_close_keeps_playback_owned_session_open(self):
        read_ahead = self.read(
            "source/dev/core/src/main/java/opensagetv/vibe/miniclient/net/ReadAheadDataSource.java"
        )
        selector = self.read(
            "source/dev/android-shared/src/main/java/"
            "opensagetv/vibe/miniclient/android/video/smb/SmbSourceSelector.java"
        )
        self.assertIn("close() { flush(); }", read_ahead)
        self.assertNotIn("delegate.close()", read_ahead)
        self.assertIn("public synchronized void release()", selector)

    def test_smb_cleanup_has_one_retained_idempotent_owner(self):
        session = self.read(
            "source/dev/android-shared/src/main/java/"
            "opensagetv/vibe/miniclient/android/video/smb/SmbDirectSession.java"
        )
        self.assertIn("Executors.newSingleThreadExecutor", session)
        self.assertIn('new Thread(runnable, "SmbDirect-Cleanup")', session)
        self.assertIn("releaseRequested.compareAndSet(false, true)", session)
        self.assertIn("cleanupExecutor.execute", session)
        self.assertIn("cleanupExecutor.shutdown()", session)
        release = session.split("public void release()", 1)[1].split(
            "private void cleanupAndShutdown()", 1
        )[0]
        self.assertNotIn("cleanup.start()", release)

    def test_debug_profile_acceptance_uses_the_production_repository_without_applying_ids(self):
        receiver = self.read("source/dev/android-tv/src/debug/java/opensagetv/vibe/miniclient/android/tv/debug/DevTestReceiver.java")
        commands = self.read("source/dev/android-tv/src/debug/java/opensagetv/vibe/miniclient/android/tv/debug/DebugProfileCommands.java")
        adb = self.read("mcp/src/sagetv_dev_mcp/adb.py")
        server = self.read("mcp/src/sagetv_dev_mcp/server.py")
        self.assertIn('"profile_list".equals(op)', receiver)
        self.assertIn("new SmbProfileRepository", commands)
        self.assertIn("MiniClientProfilePreferences.snapshot", commands)
        self.assertIn("credentialsPresent=false", commands)
        self.assertNotIn("applyClientIds", commands)
        self.assertIn('def smb_profile(', adb)
        for operation in ("list", "save", "load", "delete"):
            self.assertIn(f'def dev_smb_profile_{operation}', server)
        self.assertIn("dev_open_smb_profile_settings", server)
        self.assertIn("SmbProfileSettingsActivity", commands)

    def test_profile_acceptance_script_covers_overwrite_integrity_and_cleanup(self):
        script = self.read("scripts/mcp_smb_profile_test.py")
        self.assertIn("dev_smb_profile_save", script)
        self.assertIn('"overwrite": False', script)
        self.assertIn('"overwrite": True', script)
        self.assertIn("credentialsPresent", script)
        self.assertIn("dev_smb_profile_delete", script)
        self.assertIn("SMB PROFILE TEST PASSED", script)

    def test_seek_probe_correlates_source_decoder_and_exact_render_event_times(self):
        checks = self.read(
            "source/dev/android-tv/src/debug/java/"
            "opensagetv/vibe/miniclient/android/tv/debug/DebugAsyncPlayerChecks.java"
        )
        traps = self.read(
            "source/dev/android-tv/src/debug/java/"
            "opensagetv/vibe/miniclient/android/tv/debug/PlaybackEventTraps.java"
        )
        for field in (
            "seekRequestMonotonicMs",
            "firstSourceReadAfterRequestMonotonicMs",
            "firstDecoderInputAfterRequestMonotonicMs",
            "firstDecoderInputObservationPrecisionMs",
            "firstRenderedFrameAfterRequestMonotonicMs",
            "sourceReadAfterRequestMs",
            "decoderInputAfterRequestMs",
            "renderedFrameAfterRequestMs",
        ):
            self.assertIn(field, checks)
        self.assertIn('"server_seek_command"', checks)
        self.assertIn('"first_video_frame"', checks)
        self.assertIn("firstEventMonotonicAfter", traps)

    def test_physical_ab_script_uses_one_generated_fixture_and_proves_byte_ownership(self):
        script = self.read("scripts/mcp_smb_pull_ab_test.py")
        workflow = self.read("dev.sh")
        self.assertIn("VibeSeekTest-1080i-MPEG2-AC3-CC.ts", script)
        self.assertIn('for mode in ("pull", "smb_direct")', script)
        self.assertIn('final_source != "SAGETV_PULL"', script)
        self.assertIn('final_source != "SMB_DIRECT"', script)
        self.assertIn("final_dataSourceNetworkReadBytes", script)
        self.assertIn("final_smbBytesRead", script)
        self.assertIn("SMB PULL A/B TEST PASSED", script)
        self.assertIn("mcp-smb-pull-ab-test)", workflow)
        self.assertIn("mcp_smb_pull_ab_test.py", workflow)


if __name__ == "__main__":
    unittest.main()
