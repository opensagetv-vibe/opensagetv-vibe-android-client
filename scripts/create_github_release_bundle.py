#!/usr/bin/env python3
"""Create and verify the local GitHub source/APK publication bundle."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import shutil
import subprocess
import sys
import zipfile

SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from project_manifest import canonical_content


ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "PROJECT_MANIFEST.sha256"
REQUIRED_SOURCE_FILES = {
    "LICENSE",
    "THIRD_PARTY_NOTICES.md",
    "third_party/RUNTIME_DEPENDENCIES.csv",
    "third_party/licenses/LGPL-2.1.txt",
    "third_party/source-offers/README.md",
}
ZIP_TIMESTAMP = (2026, 1, 1, 0, 0, 0)


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def load_manifest() -> dict[str, str]:
    entries: dict[str, str] = {}
    for raw in MANIFEST.read_text(encoding="ascii").splitlines():
        digest, name = raw.split("  ", 1)
        path = PurePosixPath(name)
        if path.is_absolute() or ".." in path.parts or name in entries:
            raise ValueError(f"unsafe or duplicate manifest path: {name}")
        entries[name] = digest
    missing = REQUIRED_SOURCE_FILES - entries.keys()
    if missing:
        raise ValueError("source manifest lacks release material: " + ", ".join(sorted(missing)))
    return entries


def zip_bytes(
    archive: zipfile.ZipFile,
    name: str,
    value: bytes,
    mode: int = 0o100644,
) -> None:
    info = zipfile.ZipInfo(name, ZIP_TIMESTAMP)
    info.create_system = 3
    info.compress_type = zipfile.ZIP_DEFLATED
    info.external_attr = mode << 16
    archive.writestr(info, value)


def source_mode(name: str, value: bytes) -> int:
    """Return a deterministic POSIX mode for an extracted source checkout."""
    path = PurePosixPath(name)
    executable = (
        path.name == "gradlew"
        or path.suffix.lower() in {".sh", ".py"}
        or value.startswith(b"#!")
    )
    return 0o100755 if executable else 0o100644


def create_source_archive(path: Path, version: str, entries: dict[str, str]) -> None:
    prefix = f"opensagetv-vibe-android-client-{version}/"
    with zipfile.ZipFile(path, "w") as archive:
        for name in sorted(entries):
            # Package the same portable LF representation signed by the
            # project manifest, even from a Windows CRLF checkout.
            data = canonical_content(ROOT / name)
            actual = sha256_bytes(data)
            if actual != entries[name]:
                raise ValueError(f"manifest mismatch before packaging: {name}")
            zip_bytes(archive, prefix + name, data, source_mode(name, data))
        zip_bytes(archive, prefix + MANIFEST.name, MANIFEST.read_bytes())


def inspect_source_archive(path: Path, version: str, entries: dict[str, str]) -> None:
    prefix = f"opensagetv-vibe-android-client-{version}/"
    expected = {prefix + name: digest for name, digest in entries.items()}
    expected[prefix + MANIFEST.name] = sha256_file(MANIFEST)
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)):
            raise ValueError("publication source archive contains duplicate paths")
        for name in names:
            item = PurePosixPath(name)
            if item.is_absolute() or ".." in item.parts:
                raise ValueError(f"unsafe publication archive path: {name}")
        if set(names) != set(expected):
            missing = sorted(set(expected) - set(names))
            extra = sorted(set(names) - set(expected))
            raise ValueError(f"publication source archive mismatch; missing={missing} extra={extra}")
        for name, digest in expected.items():
            if sha256_bytes(archive.read(name)) != digest:
                raise ValueError(f"publication source archive hash mismatch: {name}")


def git_state() -> tuple[str, bool | None]:
    """Return Git provenance, or manifest provenance in a Git-less checkout."""
    try:
        revision = subprocess.run(
            ["git", "-C", str(ROOT), "rev-parse", "HEAD"],
            check=True,
            capture_output=True,
            text=True,
        ).stdout.strip()
        dirty = bool(
            subprocess.run(
                ["git", "-C", str(ROOT), "status", "--porcelain"],
                check=True,
                capture_output=True,
                text=True,
            ).stdout.strip()
        )
        return revision, dirty
    except (FileNotFoundError, subprocess.CalledProcessError):
        # Extracted source archives and the unified container deliberately do
        # not require or expose .git. The complete checked manifest is the
        # authoritative identity for that reproducible source snapshot.
        return f"manifest-sha256:{sha256_file(MANIFEST)}", None


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", required=True, type=Path)
    parser.add_argument("--output-dir", type=Path, default=ROOT / "artifacts/releases")
    parser.add_argument(
        "--allow-dirty",
        action="store_true",
        help="permit a manifest-exact local review snapshot before release commits",
    )
    args = parser.parse_args()

    subprocess.run(
        [sys.executable, str(ROOT / "scripts/project_manifest.py"), "--check"],
        check=True,
    )
    version = (ROOT / "VERSION").read_text(encoding="ascii").strip()
    revision, dirty = git_state()
    if dirty is True and not args.allow_dirty:
        raise ValueError("release worktree is dirty; use --allow-dirty only for local review")
    if not args.apk.is_file():
        raise FileNotFoundError(args.apk)

    output = args.output_dir.resolve()
    output.mkdir(parents=True, exist_ok=True)
    source_name = f"OpenSageTV-Vibe-Android-Client-v{version}-source.zip"
    apk_name = f"OpenSageTV-Vibe-Android-Client-v{version}.apk"
    source_path = output / source_name
    apk_path = output / apk_name
    entries = load_manifest()
    create_source_archive(source_path, version, entries)
    inspect_source_archive(source_path, version, entries)
    shutil.copy2(args.apk, apk_path)

    release_manifest = {
        "project": "opensagetv-vibe-android-client",
        "version": version,
        "sourceRevision": revision,
        "sourceRevisionKind": "git" if dirty is not None else "project-manifest",
        "workingTreeDirty": dirty,
        "projectManifestSha256": sha256_file(MANIFEST),
        "artifacts": {
            source_name: sha256_file(source_path),
            apk_name: sha256_file(apk_path),
        },
    }
    manifest_path = output / "github-release-manifest.json"
    manifest_path.write_text(
        json.dumps(release_manifest, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
        newline="\n",
    )
    sums_path = output / "SHA256SUMS.txt"
    sums_path.write_text(
        "".join(
            f"{digest}  {name}\n"
            for name, digest in sorted(release_manifest["artifacts"].items())
        ),
        encoding="ascii",
        newline="\n",
    )

    bundle_path = output / f"OpenSageTV-Vibe-Android-Client-v{version}-github-release.zip"
    with zipfile.ZipFile(bundle_path, "w") as archive:
        for item in (apk_path, source_path, manifest_path, sums_path):
            zip_bytes(archive, item.name, item.read_bytes())
    with zipfile.ZipFile(bundle_path) as archive:
        expected_names = {apk_path.name, source_path.name, manifest_path.name, sums_path.name}
        if set(archive.namelist()) != expected_names:
            raise ValueError("GitHub review bundle has an unexpected file set")
        if sha256_bytes(archive.read(source_name)) != sha256_file(source_path):
            raise ValueError("nested source archive hash mismatch")
        if sha256_bytes(archive.read(apk_name)) != sha256_file(apk_path):
            raise ValueError("nested APK hash mismatch")

    print(f"PASS: source archive inspected ({len(entries)} manifest files)")
    print(f"PASS: GitHub review bundle inspected: {bundle_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
