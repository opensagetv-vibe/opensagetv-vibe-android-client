from pathlib import Path
import sys
import tempfile
import unittest
import zipfile


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

import inspect_apk


class ApkInspectionTests(unittest.TestCase):
    def test_elf_load_alignment_parser_reads_64_bit_program_headers(self):
        import struct

        binary = bytearray(128)
        binary[:6] = b"\x7fELF\x02\x01"
        struct.pack_into("<Q", binary, 32, 64)
        struct.pack_into("<H", binary, 54, 56)
        struct.pack_into("<H", binary, 56, 1)
        struct.pack_into("<I", binary, 64, 1)
        struct.pack_into("<Q", binary, 64 + 48, 16384)
        self.assertEqual(inspect_apk.elf_load_alignments(bytes(binary)), [16384])

    def test_root_workflow_resolves_relative_apk_under_component_mount(self):
        dev = (ROOT / "dev.sh").read_text(encoding="utf-8")
        self.assertIn('inspect_apk_path="$CONTAINER_WORKSPACE/$inspect_apk_path"', dev)
        self.assertIn('"$inspect_apk_path" "$@"', dev)
        self.assertIn('install_apk_path="$CONTAINER_WORKSPACE/$install_apk_path"', dev)
        self.assertIn('dev_command install "$install_apk_path" "$@"', dev)

    def test_parses_package_and_permissions(self):
        self.assertEqual(
            inspect_apk.parse_package("package: name='org.example.app' versionCode='1'\n"),
            "org.example.app",
        )
        self.assertEqual(
            inspect_apk.parse_permissions(
                "uses-permission: name='android.permission.INTERNET'\n"
                "uses-permission: name='android.permission.WAKE_LOCK'\n"
            ),
            {"android.permission.INTERNET", "android.permission.WAKE_LOCK"},
        )

    def test_release_rejects_debug_surface_audio_permission_and_debug_signer(self):
        failures = inspect_apk.evaluate(
            variant="release",
            package="opensagetv.vibe.miniclient",
            permissions={
                "android.permission.INTERNET",
                "android.permission.RECORD_AUDIO",
            },
            badging="application-debuggable\n",
            manifest_tree="DevTestReceiver opensagetv.vibe.miniclient.DEBUG_CONTROL",
            signer="Signer #1 certificate DN: CN=Android Debug,O=Android,C=US",
            archive_failures=[],
            allow_record_audio=False,
            allow_debug_signing=False,
        )
        combined = "\n".join(failures)
        self.assertIn("RECORD_AUDIO", combined)
        self.assertIn("DevTestReceiver", combined)
        self.assertIn("debuggable", combined)
        self.assertIn("debug certificate", combined)

    def test_clean_release_boundary_passes(self):
        package = "opensagetv.vibe.miniclient"
        failures = inspect_apk.evaluate(
            variant="release",
            package=package,
            permissions={
                "android.permission.INTERNET",
                f"{package}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
            },
            badging=f"package: name='{package}'\n",
            manifest_tree="ServersActivity MainActivity",
            signer="Signer #1 certificate DN: CN=OpenSageTV Vibe Release",
            archive_failures=[],
            allow_record_audio=False,
            allow_debug_signing=False,
        )
        self.assertEqual(failures, [])

    def test_release_rejects_unused_cast_session_and_transport_components(self):
        package = "opensagetv.vibe.miniclient"
        manifest = "\n".join(
            (
                "androidx.media3.session.BluetoothValidationActivity",
                "androidx.profileinstaller.ProfileInstallReceiver",
                "com.google.android.datatransport.runtime.backends.TransportBackendDiscovery",
                "com.google.android.gms.cast.framework.ReconnectionService",
            )
        )
        failures = inspect_apk.evaluate(
            variant="release",
            package=package,
            permissions={
                "android.permission.INTERNET",
                f"{package}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
            },
            badging=f"package: name='{package}'\n",
            manifest_tree=manifest,
            signer="Signer #1 certificate DN: CN=OpenSageTV Vibe Release",
            archive_failures=[],
            allow_record_audio=False,
            allow_debug_signing=False,
        )
        combined = "\n".join(failures)
        for marker in inspect_apk.FORBIDDEN_RELEASE_MANIFEST_MARKERS:
            self.assertIn(marker, combined)

    def test_project_source_has_no_unowned_microphone_surface(self):
        manifest = (
            ROOT
            / "source"
            / "dev"
            / "android-tv"
            / "src"
            / "main"
            / "AndroidManifest.xml"
        ).read_text(encoding="utf-8")
        active_sources = "\n".join(
            path.read_text(encoding="utf-8", errors="replace")
            for module in ("android-tv", "android-shared", "core")
            for path in (ROOT / "source" / "dev" / module / "src").rglob("*.java")
        )
        self.assertNotIn("RECORD_AUDIO", manifest)
        self.assertNotIn("android.hardware.microphone", manifest)
        for microphone_api in (
            "AudioRecord",
            "MediaRecorder",
            "SpeechRecognizer",
        ):
            self.assertNotIn(microphone_api, active_sources)

    def test_archive_scan_rejects_debug_markers_and_private_material(self):
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory) / "candidate.apk"
            with zipfile.ZipFile(apk, "w") as archive:
                archive.writestr("classes.dex", b"opensagetv.vibe.miniclient.DEBUG_CONTROL")
                archive.writestr("assets/release.keystore", b"secret")
            failures, names = inspect_apk.inspect_archive(apk, release=True)
        self.assertIn("classes.dex", names)
        self.assertTrue(any("DEBUG_CONTROL" in item for item in failures))
        self.assertTrue(any("keystore" in item for item in failures))


if __name__ == "__main__":
    unittest.main()
