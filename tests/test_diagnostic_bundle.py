import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SHARED = ROOT / "source/dev/android-shared/src/main"


class DiagnosticBundleContracts(unittest.TestCase):
    def read(self, relative):
        return (SHARED / relative).read_text(encoding="utf-8")

    def test_diagnostics_smb_is_independent_and_has_all_modes(self):
        prefs = self.read(
            "java/opensagetv/vibe/miniclient/android/prefs/AndroidPrefStore.java"
        )
        xml = self.read("res/xml/smb_profile_prefs.xml")
        self.assertIn('SMB_DIAGNOSTICS_DIRECTORY = "smb_diagnostics/directory"', prefs)
        self.assertIn('SMB_DIAGNOSTICS_USERNAME = "smb_diagnostics/auth/username"', prefs)
        self.assertIn('SMB_DIAGNOSTICS_PASSWORD = "smb_diagnostics/auth/password"', prefs)
        self.assertIn('DIAGNOSTICS_MODE_OFF = "off"', prefs)
        self.assertIn('DIAGNOSTICS_MODE_ON_REQUEST = "on_request"', prefs)
        self.assertIn('DIAGNOSTICS_MODE_ALWAYS = "always"', prefs)
        self.assertIn('android:key="smb_diagnostics/test"', xml)
        self.assertIn('android:key="smb_direct/test"', xml)
        self.assertIn('android:key="smb_profiles/test"', xml)

    def test_bundle_is_bounded_redacted_and_hash_identified(self):
        bundle = self.read(
            "java/opensagetv/vibe/miniclient/android/diagnostics/DiagnosticBundleManager.java"
        )
        redactor = self.read(
            "java/opensagetv/vibe/miniclient/android/diagnostics/DiagnosticRedactor.java"
        )
        self.assertIn("MAX_LOG_FILES = 3", bundle)
        self.assertIn("MAX_LOG_BYTES = 512 * 1024", bundle)
        self.assertIn('new Entry("checksums.sha256"', bundle)
        self.assertIn('MessageDigest.getInstance("SHA-256")', bundle)
        self.assertIn("DiagnosticRedactor.redact", bundle)
        for contract in ("SMB_CREDENTIALS", "SECRET_ASSIGNMENT", "CLIENT_ID", "CLIENT_CONTEXT", "IPV4", "WINDOWS_PATH", "UNIX_MEDIA_PATH"):
            self.assertIn(contract, redactor)

    def test_smb_writes_are_atomic_and_connection_tests_clean_up(self):
        repository = self.read(
            "java/opensagetv/vibe/miniclient/android/diagnostics/SmbDiagnosticsRepository.java"
        )
        self.assertIn('".tmp-" + UUID.randomUUID()', repository)
        self.assertIn("remote.rename(destination, false)", repository)
        self.assertIn("remote.rename(destination, true)", repository)
        self.assertIn("share.rm(remoteName)", repository)
        self.assertIn("SMB test read/hash verification failed", repository)
        self.assertIn("AuthenticationContext.guest()", repository)
        self.assertIn("pruneOwnArtifacts", repository)
        self.assertIn("Older unique artifacts are never candidates", repository)
        for stage in ('"URL validation"', '"DNS/connect"', '"authentication"',
                      '"share open"', '"path/read-write verification"'):
            self.assertIn(stage, repository)
        self.assertIn("stageSummary", repository)

    def test_always_mode_spools_locally_and_flushes_at_lifecycle_boundaries(self):
        spool = self.read(
            "java/opensagetv/vibe/miniclient/android/diagnostics/DiagnosticSessionSpool.java"
        )
        self.assertIn("REFRESH_MS = 60_000L", spool)
        self.assertIn('checkpoint("app-background-or-exit")', spool)
        self.assertIn('enqueue("clean-exit")', spool)
        self.assertIn(".log.pending", spool)
        self.assertIn("uploadPending()", spool)
        self.assertIn("MAX_PENDING_FILES = 4", spool)

    def test_long_press_has_dedicated_export_icon(self):
        layout = self.read("res/layout/navigation.xml")
        tv_layout = (ROOT / "source/dev/android-tv/src/main/res/layout/navigation.xml").read_text(encoding="utf-8")
        no_touch_layout = (ROOT / "source/dev/android-tv/src/main/res/layout-notouch/navigation.xml").read_text(encoding="utf-8")
        navigation = self.read(
            "java/opensagetv/vibe/miniclient/android/NavigationDialog.java"
        )
        for actual_layout in (layout, tv_layout, no_touch_layout):
            self.assertIn('android:id="@+id/nav_export_diagnostics"', actual_layout)
            self.assertIn('android:contentDescription="Export Diagnostics"', actual_layout)
        self.assertIn("R.id.nav_export_diagnostics", navigation)
        self.assertIn("DiagnosticExportController.show", navigation)

    def test_current_video_test_has_its_own_icon_and_is_bundled(self):
        layouts = (
            self.read("res/layout/navigation.xml"),
            (ROOT / "source/dev/android-tv/src/main/res/layout/navigation.xml").read_text(encoding="utf-8"),
            (ROOT / "source/dev/android-tv/src/main/res/layout-notouch/navigation.xml").read_text(encoding="utf-8"),
        )
        for layout in layouts:
            self.assertIn('android:id="@+id/nav_test_current_video"', layout)
            self.assertIn('android:src="@drawable/ic_video_test_white_24dp"', layout)
            self.assertIn('android:contentDescription="Test Current Video"', layout)
        navigation = self.read(
            "java/opensagetv/vibe/miniclient/android/NavigationDialog.java"
        )
        runner = self.read(
            "java/opensagetv/vibe/miniclient/android/CurrentVideoDiagnosticTest.java"
        )
        store = self.read(
            "java/opensagetv/vibe/miniclient/android/diagnostics/CurrentVideoTestStore.java"
        )
        bundle = self.read(
            "java/opensagetv/vibe/miniclient/android/diagnostics/DiagnosticBundleManager.java"
        )
        self.assertIn("R.id.nav_test_current_video", navigation)
        self.assertIn("ActivePlayerAdjustmentsDialog.testCurrentVideo", navigation)
        for check in ("sustainedPlaybackCadence", "pause", "resume", "seek-first", "seek-repeat"):
            self.assertIn(check, runner)
        for counter in ("videoDecoderInitDelta", "videoDecoderReleaseDelta",
                        "audioDecoderInitDelta", "audioDecoderReleaseDelta"):
            self.assertIn(counter, runner)
        self.assertIn("MAX_REPORTS = 4", store)
        self.assertIn("DiagnosticRedactor.redact(report)", store)
        self.assertIn("addCurrentVideoTests(app, entries)", bundle)
        self.assertIn('"current-video-tests/test-"', bundle)

    def test_current_video_test_has_bounded_teletext_pes_preservation_probe(self):
        runner = self.read(
            "java/opensagetv/vibe/miniclient/android/CurrentVideoDiagnosticTest.java"
        )
        probe = (ROOT / "source/dev/core/src/main/java/opensagetv/vibe/miniclient/media/TeletextPesProbe.java").read_text(encoding="utf-8")
        pull = (ROOT / "source/dev/core/src/main/java/opensagetv/vibe/miniclient/net/BufferedPullDataSource.java").read_text(encoding="utf-8")
        push = (ROOT / "source/dev/core/src/main/java/opensagetv/vibe/miniclient/net/PushBufferDataSource.java").read_text(encoding="utf-8")
        smb = self.read(
            "java/opensagetv/vibe/miniclient/android/video/smb/SmbDirectSession.java"
        )
        self.assertIn("MAX_ANALYZED_BYTES = 64L * 1024L * 1024L", probe)
        self.assertIn("AtomicReference<Session>", probe)
        self.assertIn("tag == 0x56", probe)
        self.assertIn("unit == 0x02 || unit == 0x03", probe)
        self.assertIn("TeletextPesProbe.begin()", runner)
        self.assertIn("TeletextPesProbe.finish()", runner)
        self.assertIn('report.append("\\n[teletextPesPreservation]\\n")', runner)
        self.assertIn('TeletextPesProbe.observe("pull"', pull)
        self.assertIn('TeletextPesProbe.observe("push"', push)
        self.assertIn('TeletextPesProbe.observe("smb-direct"', smb)
        self.assertNotIn("payload=", probe)
        self.assertNotIn("subtitleText", probe)
        receiver = (ROOT / "source/dev/android-tv/src/debug/java/opensagetv/vibe/miniclient/android/tv/debug/DevTestReceiver.java").read_text(encoding="utf-8")
        commands = (ROOT / "source/dev/android-tv/src/debug/java/opensagetv/vibe/miniclient/android/tv/debug/DebugSessionCommands.java").read_text(encoding="utf-8")
        self.assertIn('"test_current_video".equals(op)', receiver)
        self.assertIn("ActivePlayerAdjustmentsDialog.testCurrentVideo(activity)", commands)

    def test_issue_form_requests_reproduction_and_redacted_bundle(self):
        form = (ROOT / ".github/ISSUE_TEMPLATE/playback-bug.yml").read_text(encoding="utf-8")
        for field in ("client_version", "device", "server", "player", "transport", "decoding", "media", "steps", "result"):
            self.assertIn(f"id: {field}", form)
        self.assertIn("redacted support bundle", form)
        self.assertIn("SHA-256", form)
        self.assertIn("Diagnostic export", form)
        self.assertIn("diagnostics-long-press-menu.png", form)
        self.assertTrue((ROOT / "docs/images/diagnostics-long-press-menu.png").is_file())
        self.assertTrue((ROOT / "docs/images/diagnostics-export-dialog.png").is_file())

    def test_feature_request_form_collects_problem_and_compatibility(self):
        form = (ROOT / ".github/ISSUE_TEMPLATE/feature-request.yml").read_text(
            encoding="utf-8"
        )
        self.assertIn("name: New feature request", form)
        self.assertIn('labels: ["enhancement"]', form)
        for field in ("existing", "problem", "proposal", "area", "compatibility", "alternatives", "additional"):
            self.assertIn(f"id: {field}", form)
        self.assertIn("stock SageTV server compatibility preferred", form)
        self.assertIn("A diagnostic bundle is not required", form)


if __name__ == "__main__":
    unittest.main()
