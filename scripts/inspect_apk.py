#!/usr/bin/env python3
"""Inspect an Android APK against the OpenSageTV Vibe release boundary."""

from __future__ import annotations

import argparse
import json
from pathlib import Path, PurePosixPath
import re
import shutil
import struct
import subprocess
import sys
import zipfile


BASE_RELEASE_PERMISSIONS = {
    "android.permission.ACCESS_NETWORK_STATE",
    "android.permission.ACCESS_WIFI_STATE",
    "android.permission.FOREGROUND_SERVICE",
    "android.permission.INTERNET",
    "android.permission.WAKE_LOCK",
}

FORBIDDEN_RELEASE_MARKERS = (
    b"opensagetv.vibe.miniclient.DEBUG_CONTROL",
    b"opensagetv.vibe.miniclient.android.tv.debug.DevTestReceiver",
    b"sagetv_dev_mcp",
    b"BEGIN PRIVATE KEY",
    b"BEGIN RSA PRIVATE KEY",
    b"BEGIN EC PRIVATE KEY",
    b"BEGIN OPENSSH PRIVATE KEY",
)

FORBIDDEN_ARCHIVE_SUFFIXES = (
    ".jks",
    ".keystore",
    ".pem",
    ".p12",
    ".pfx",
)

# These components were previously merged only because GSY's Exo artifact
# published optional Media3 Cast/Session dependencies unconditionally. SageTV
# does not expose Cast or Android MediaSession playback, so their return must be
# an explicit, reviewed release decision instead of an unnoticed graph change.
FORBIDDEN_RELEASE_MANIFEST_MARKERS = (
    "androidx.media3.session.BluetoothValidationActivity",
    "androidx.profileinstaller.ProfileInstallReceiver",
    "com.google.android.datatransport.",
    "com.google.android.gms.cast.",
)

SUPPORTED_NATIVE_ABIS = ("armeabi-v7a", "arm64-v8a")


def elf_load_alignments(binary: bytes) -> list[int]:
    """Return PT_LOAD alignment values for a little-endian ELF image."""
    if binary[:4] != b"\x7fELF" or binary[5:6] != b"\x01":
        return []
    if binary[4] == 2:
        program_offset = struct.unpack_from("<Q", binary, 32)[0]
        entry_size = struct.unpack_from("<H", binary, 54)[0]
        entry_count = struct.unpack_from("<H", binary, 56)[0]
        alignment_offset = 48
        alignment_format = "<Q"
    elif binary[4] == 1:
        program_offset = struct.unpack_from("<I", binary, 28)[0]
        entry_size = struct.unpack_from("<H", binary, 42)[0]
        entry_count = struct.unpack_from("<H", binary, 44)[0]
        alignment_offset = 28
        alignment_format = "<I"
    else:
        return []
    alignments = []
    for index in range(entry_count):
        offset = program_offset + index * entry_size
        if struct.unpack_from("<I", binary, offset)[0] == 1:
            alignments.append(struct.unpack_from(alignment_format, binary, offset + alignment_offset)[0])
    return alignments


def run_tool(tool: str, *args: str) -> str:
    resolved = shutil.which(tool)
    if not resolved:
        raise RuntimeError(f"required Android tool not found on PATH: {tool}")
    result = subprocess.run(
        [resolved, *args],
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        check=False,
    )
    if result.returncode:
        raise RuntimeError(
            f"{tool} {' '.join(args)} failed ({result.returncode}):\n{result.stdout}"
        )
    return result.stdout


def parse_package(badging: str) -> str:
    match = re.search(r"^package:\s+name='([^']+)'", badging, re.MULTILINE)
    return match.group(1) if match else ""


def parse_permissions(output: str) -> set[str]:
    return set(re.findall(r"uses-permission:\s+name='([^']+)'", output))


def inspect_archive(apk: Path, release: bool) -> tuple[list[str], list[str]]:
    failures: list[str] = []
    names: list[str] = []
    with zipfile.ZipFile(apk) as archive:
        for info in archive.infolist():
            normalized = PurePosixPath(info.filename).as_posix()
            names.append(normalized)
            lowered = normalized.lower()
            if lowered.endswith(FORBIDDEN_ARCHIVE_SUFFIXES):
                failures.append(f"sensitive archive entry: {normalized}")
            if lowered.endswith("google-services.json") or lowered.endswith("firetv.toml"):
                failures.append(f"environment-specific archive entry: {normalized}")
            if not release or info.is_dir():
                continue
            data = archive.read(info)
            for marker in FORBIDDEN_RELEASE_MARKERS:
                if marker in data:
                    failures.append(
                        f"release marker {marker.decode('ascii')} found in {normalized}"
                    )
        native_entries = {
            name: archive.read(name)
            for name in names
            if re.fullmatch(r"lib/[^/]+/[^/]+\.so", name)
        }
        if native_entries:
            libraries_by_abi: dict[str, set[str]] = {}
            for name, binary in native_entries.items():
                _, abi, library = name.split("/", 2)
                libraries_by_abi.setdefault(abi, set()).add(library)
                if abi == "arm64-v8a":
                    alignments = elf_load_alignments(binary)
                    if not alignments or any(value < 16384 for value in alignments):
                        failures.append(
                            f"64-bit native library is not 16 KB LOAD-aligned: "
                            f"{name} ({alignments or 'invalid ELF'})"
                        )
            actual_abis = tuple(sorted(libraries_by_abi))
            expected_abis = tuple(sorted(SUPPORTED_NATIVE_ABIS))
            if actual_abis != expected_abis:
                failures.append(
                    f"native ABI set is {actual_abis}; expected paired ARM ABIs {expected_abis}"
                )
            elif libraries_by_abi[expected_abis[0]] != libraries_by_abi[expected_abis[1]]:
                failures.append(
                    "ARM native library sets differ: "
                    + "; ".join(
                        f"{abi}={sorted(libraries_by_abi[abi])}" for abi in expected_abis
                    )
                )
    return sorted(set(failures)), sorted(names)


def evaluate(
    *,
    variant: str,
    package: str,
    permissions: set[str],
    badging: str,
    manifest_tree: str,
    signer: str,
    archive_failures: list[str],
    allow_record_audio: bool,
    allow_debug_signing: bool,
) -> list[str]:
    failures = list(archive_failures)
    release = variant == "release"
    expected_package = (
        "opensagetv.vibe.miniclient"
        if release
        else "opensagetv.vibe.miniclient.debug"
    )
    if package != expected_package:
        failures.append(f"package is {package!r}; expected {expected_package!r}")

    if release:
        merged = "\n".join((badging, manifest_tree))
        for marker in (
            "DevTestReceiver",
            "opensagetv.vibe.miniclient.DEBUG_CONTROL",
            "opensagetv.vibe.miniclient.android.tv.debug",
            *FORBIDDEN_RELEASE_MANIFEST_MARKERS,
        ):
            if marker in merged:
                failures.append(f"forbidden component in release manifest: {marker}")
        if "application-debuggable" in badging:
            failures.append("release APK is debuggable")

        allowed = set(BASE_RELEASE_PERMISSIONS)
        allowed.add(f"{package}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
        if allow_record_audio:
            allowed.add("android.permission.RECORD_AUDIO")
        unexpected = sorted(permissions - allowed)
        if unexpected:
            failures.append("unexpected release permissions: " + ", ".join(unexpected))

        if not signer.strip():
            failures.append("release signer information is unavailable")
        elif not allow_debug_signing and re.search(
            r"Android Debug|androiddebugkey|SageTV MiniClient Dev Debug|"
            r"dc531a9302b7270a558f5c41492c69ed3a2665243fca6e97977e380604a45e54",
            signer,
            re.IGNORECASE,
        ):
            failures.append("release APK uses an Android debug certificate")

    return sorted(set(failures))


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("--variant", choices=("debug", "release"), required=True)
    parser.add_argument(
        "--allow-record-audio",
        action="store_true",
        help="temporary audit-only exception; strict release inspection rejects it",
    )
    parser.add_argument(
        "--allow-debug-signing",
        action="store_true",
        help="inspection-candidate exception; never use for a production release gate",
    )
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()

    apk = args.apk.expanduser().resolve()
    if not apk.is_file():
        parser.error(f"APK not found: {apk}")

    badging = run_tool("aapt", "dump", "badging", str(apk))
    permission_output = run_tool("aapt", "dump", "permissions", str(apk))
    manifest_tree = run_tool("aapt", "dump", "xmltree", str(apk), "AndroidManifest.xml")
    try:
        signer_output = run_tool("apksigner", "verify", "--print-certs", str(apk))
        signer = "\n".join(
            line for line in signer_output.splitlines() if line.startswith("Signer #")
        )
    except RuntimeError as exc:
        signer = str(exc)

    package = parse_package(badging)
    permissions = parse_permissions(permission_output)
    archive_failures, archive_names = inspect_archive(
        apk, release=args.variant == "release"
    )
    failures = evaluate(
        variant=args.variant,
        package=package,
        permissions=permissions,
        badging=badging,
        manifest_tree=manifest_tree,
        signer=signer,
        archive_failures=archive_failures,
        allow_record_audio=args.allow_record_audio,
        allow_debug_signing=args.allow_debug_signing,
    )
    report = {
        "apk": str(apk),
        "variant": args.variant,
        "package": package,
        "permissions": sorted(permissions),
        "archiveEntryCount": len(archive_names),
        "signer": signer.strip(),
        "strictReleaseGate": not args.allow_record_audio and not args.allow_debug_signing,
        "result": "FAIL" if failures else "PASS",
        "failures": failures,
    }
    rendered = json.dumps(report, indent=2, sort_keys=True) + "\n"
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(rendered, encoding="utf-8")
    print(rendered, end="")
    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
