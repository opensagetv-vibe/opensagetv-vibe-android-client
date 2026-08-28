#!/usr/bin/env python3
"""Import an exact SageTV MiniClient source ZIP into the Windows workspace.

Creates two independent trees:
  - /workspace/source/existing : untouched baseline copy
  - /workspace/source/dev    : isolated Dev development copy, then applies Phase 0 refactor

The importer refuses to overwrite non-empty source trees unless --replace is explicitly supplied.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import shutil
import subprocess
import sys
import tempfile
import zipfile
from datetime import datetime, timezone
from pathlib import Path


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def safe_extract(zf: zipfile.ZipFile, dest: Path) -> None:
    root = dest.resolve()
    for info in zf.infolist():
        candidate = (dest / info.filename).resolve()
        if root != candidate and root not in candidate.parents:
            raise RuntimeError(f"Unsafe ZIP member path: {info.filename}")
    zf.extractall(dest)


def locate_repo(extracted: Path) -> Path:
    candidates: list[Path] = []
    for p in [extracted, *[x for x in extracted.rglob("gradlew")]]:
        root = p if p.is_dir() else p.parent
        if (root / "gradlew").is_file() and (root / "android-tv").is_dir() and (root / "android-shared").is_dir():
            candidates.append(root)
    unique = []
    seen = set()
    for p in candidates:
        r = p.resolve()
        if r not in seen:
            seen.add(r)
            unique.append(r)
    if len(unique) != 1:
        pretty = "\n".join(f"- {p}" for p in unique) or "(none)"
        raise RuntimeError(f"Expected exactly one SageTV MiniClient source root in ZIP; found {len(unique)}:\n{pretty}")
    return unique[0]


def ensure_destination(path: Path, replace: bool) -> None:
    if path.exists() and any(path.iterdir()):
        if not replace:
            raise RuntimeError(f"Destination is not empty: {path}. Re-run with --replace only if you intend to replace it.")
        shutil.rmtree(path)
    path.mkdir(parents=True, exist_ok=True)


def copy_tree(src: Path, dest: Path) -> None:
    # copytree with dirs_exist_ok retains executable bits such as gradlew on normal ZIPs when possible.
    shutil.copytree(src, dest, dirs_exist_ok=True, symlinks=True)
    gradlew = dest / "gradlew"
    if gradlew.exists():
        gradlew.chmod(gradlew.stat().st_mode | 0o111)


def git_head(root: Path) -> str | None:
    try:
        return subprocess.check_output(["git", "-C", str(root), "rev-parse", "HEAD"], text=True, stderr=subprocess.DEVNULL).strip()
    except Exception:
        return None


def main() -> int:
    project = Path(__file__).resolve().parents[1]
    ap = argparse.ArgumentParser(description="Import exact SageTV MiniClient ZIP into baseline + Dev source trees")
    ap.add_argument("zip", type=Path, help="Source ZIP path, normally /workspace/incoming/<file>.zip")
    ap.add_argument("--existing", type=Path, default=Path("/workspace/source/existing"))
    ap.add_argument("--dev", type=Path, default=Path("/workspace/source/dev"))
    ap.add_argument("--replace", action="store_true", help="Explicitly replace existing source trees")
    args = ap.parse_args()

    archive = args.zip.expanduser().resolve()
    if not archive.is_file():
        raise SystemExit(f"Source ZIP not found: {archive}")

    digest = sha256(archive)
    with tempfile.TemporaryDirectory(prefix="sagetv-source-") as td:
        extract_root = Path(td)
        with zipfile.ZipFile(archive) as zf:
            safe_extract(zf, extract_root)
        repo = locate_repo(extract_root)

        existing = args.existing.resolve()
        dev = args.dev.resolve()
        ensure_destination(existing, args.replace)
        ensure_destination(dev, args.replace)
        copy_tree(repo, existing)
        copy_tree(repo, dev)

    metadata = {
        "schema": 1,
        "imported_utc": datetime.now(timezone.utc).isoformat(),
        "archive_name": archive.name,
        "archive_sha256": digest,
        "git_head_if_present": git_head(existing),
        "baseline_tree": str(existing),
        "dev_tree": str(dev),
    }
    # Keep source/existing byte-for-byte source-content clean. Store import provenance
    # beside the trees rather than injecting a metadata file into the baseline repo.
    provenance = existing.parent / "SOURCE_IMPORT.json"
    provenance.write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")

    subprocess.run([sys.executable, str(project / "scripts" / "apply_dev_refactor.py"), str(dev)], check=True)
    print(json.dumps(metadata, indent=2))
    print("Source import complete: baseline left untouched; Dev copy received Phase 0 isolation/Firebase refactor.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
