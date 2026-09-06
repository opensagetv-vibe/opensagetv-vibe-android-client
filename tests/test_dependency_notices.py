import pathlib
import csv
import subprocess
import sys
import unittest
import zipfile


ROOT = pathlib.Path(__file__).resolve().parents[1]
NOTICE = ROOT / "THIRD_PARTY_NOTICES.md"
LIBS = ROOT / "source/dev/libs"


class DependencyNoticeTest(unittest.TestCase):
    def test_direct_runtime_families_are_in_the_release_notice(self):
        text = NOTICE.read_text(encoding="utf-8")
        for required in (
            "AndroidX",
            "Legacy Google ExoPlayer",
            "GSYVideoPlayer",
            "libGDX",
            "Guava",
            "SMBJ",
            "Glide",
            "SLF4J",
            "Logback Android",
            "CircularProgressView",
            "JetBrains annotations",
            "JZlib",
            "NanoHTTPD",
            "extension-ffmpeg-2.18.0.aar",
            "ijkplayer-java-0.8.8-SNAPSHOT.aar",
        ):
            self.assertIn(required, text)
        self.assertIn("former Ostermiller", text)

    def test_complete_runtime_inventory_has_no_unknown_or_gpl_dependency(self):
        inventory = ROOT / "third_party/RUNTIME_DEPENDENCIES.csv"
        with inventory.open(encoding="utf-8", newline="") as source:
            rows = list(csv.DictReader(source))
        self.assertEqual(122, len(rows))
        self.assertEqual(len(rows), len({row["coordinate"] for row in rows}))
        for row in rows:
            self.assertTrue(row["license"])
            self.assertNotIn("UNKNOWN", row["license"].upper())
            self.assertNotIn("GPL-2.0", row["license"])
            for license_file in row["license_files"].split(";"):
                self.assertTrue((ROOT / license_file).is_file(), license_file)
        self.assertFalse(any("ostermiller" in row["coordinate"].lower() for row in rows))
        self.assertFalse(any("com.squareup:otto" in row["coordinate"] for row in rows))

    def test_inventory_regenerates_exactly_from_captured_runtime_graph(self):
        report = ROOT / "artifacts/reports/gradle-runtime-dependencies.txt"
        if not report.is_file():
            self.skipTest("captured runtime graph is an ignored diagnostic artifact")
        result = subprocess.run(
            [
                sys.executable,
                str(ROOT / "scripts/generate_runtime_dependency_inventory.py"),
                "--input",
                str(report),
            ],
            check=True,
            capture_output=True,
            text=True,
        )
        self.assertEqual(
            (ROOT / "third_party/RUNTIME_DEPENDENCIES.csv").read_text(encoding="utf-8"),
            result.stdout,
        )

    def test_active_source_and_dependency_metadata_do_not_reference_ostermiller(self):
        roots = [
            ROOT / "source/dev/core",
            ROOT / "source/dev/android-shared",
            ROOT / "source/dev/android-tv",
            ROOT / "source/dev/gradle/verification-metadata.xml",
        ]
        for root in roots:
            paths = [root] if root.is_file() else [path for path in root.rglob("*") if path.is_file()]
            for path in paths:
                if any(part in {"build", ".gradle"} for part in path.parts):
                    continue
                try:
                    text = path.read_text(encoding="utf-8")
                except UnicodeDecodeError:
                    continue
                self.assertNotIn("ostermiller", text.lower(), str(path))

    def test_native_source_availability_hashes_match_distributed_aars(self):
        import hashlib
        import re

        offer = (ROOT / "third_party/source-offers/README.md").read_text(encoding="utf-8")
        for archive in (
            "extension-ffmpeg-2.18.0.aar",
            "ijkplayer-java-0.8.8-SNAPSHOT.aar",
            "ijkplayer-armv7a-0.8.8-SNAPSHOT.aar",
            "ijkplayer-arm64-0.8.8-SNAPSHOT.aar",
            "ijkplayer-x86-0.8.8-SNAPSHOT.aar",
        ):
            digest = hashlib.sha256((LIBS / archive).read_bytes()).hexdigest()
            self.assertRegex(offer, rf"{re.escape(archive)}`? \| `{digest}`")

    def test_native_rebuild_scripts_pin_evidenced_source_versions(self):
        exo = (ROOT / "source/dev/exoplayer/buildffmpegext.sh").read_text(encoding="utf-8")
        ijk = (ROOT / "source/dev/ijkplayer/init-sources.sh").read_text(encoding="utf-8")
        self.assertIn("839f98ff6719cf2db0cbd88cd787a1b19b9cbf47", exo)
        self.assertIn("checkout --detach k0.8.8", ijk)

    def test_ijk_ffmpeg_notice_matches_embedded_non_gpl_configuration(self):
        archive = LIBS / "ijkplayer-arm64-0.8.8-SNAPSHOT.aar"
        with zipfile.ZipFile(archive) as package:
            binary = package.read("jni/arm64-v8a/libijkffmpeg.so")
        self.assertIn(b"--disable-gpl", binary)
        self.assertIn(b"--disable-nonfree", binary)
        self.assertNotIn(b"--enable-gpl", binary)
        self.assertNotIn(b"--enable-nonfree", binary)

    def test_exo_ffmpeg_notice_matches_decoder_only_configuration(self):
        archive = LIBS / "extension-ffmpeg-2.18.0.aar"
        with zipfile.ZipFile(archive) as package:
            binary = package.read("jni/arm64-v8a/libffmpegJNI.so")
        self.assertIn(b"--disable-programs", binary)
        self.assertIn(b"--enable-decoder=aac", binary)
        self.assertNotIn(b"--enable-gpl", binary)
        self.assertNotIn(b"--enable-nonfree", binary)

    def test_exo_ffmpeg_arm64_load_segments_are_16k_aligned(self):
        import struct

        archive = LIBS / "extension-ffmpeg-2.18.0.aar"
        with zipfile.ZipFile(archive) as package:
            binary = package.read("jni/arm64-v8a/libffmpegJNI.so")
        self.assertEqual(binary[:4], b"\x7fELF")
        self.assertEqual(binary[4], 2, "expected a 64-bit ELF")
        self.assertEqual(binary[5], 1, "expected little-endian ELF")
        program_offset = struct.unpack_from("<Q", binary, 32)[0]
        entry_size = struct.unpack_from("<H", binary, 54)[0]
        entry_count = struct.unpack_from("<H", binary, 56)[0]
        load_alignments = []
        for index in range(entry_count):
            offset = program_offset + index * entry_size
            if struct.unpack_from("<I", binary, offset)[0] == 1:
                load_alignments.append(struct.unpack_from("<Q", binary, offset + 48)[0])
        self.assertTrue(load_alignments, "ELF contains no LOAD segments")
        self.assertTrue(
            all(alignment >= 16384 for alignment in load_alignments),
            f"ARM64 LOAD alignments are not 16 KB compatible: {load_alignments}",
        )


if __name__ == "__main__":
    unittest.main()
