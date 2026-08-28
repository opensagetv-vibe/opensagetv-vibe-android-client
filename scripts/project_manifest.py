#!/usr/bin/env python3
"""Generate or verify the repository's deterministic SHA-256 manifest."""

from __future__ import annotations

import argparse
import hashlib
from pathlib import Path
import subprocess


ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "PROJECT_MANIFEST.sha256"


def repository_files() -> list[str]:
    result = subprocess.run(
        [
            "git",
            "-c",
            f"safe.directory={ROOT}",
            "-C",
            str(ROOT),
            "ls-files",
            "-z",
            "--cached",
            "--others",
            "--exclude-standard",
        ],
        check=True,
        capture_output=True,
    )
    names = result.stdout.decode("utf-8").split("\0")
    return sorted(name for name in names if name and name != MANIFEST.name)


def digest(path: Path) -> str:
    value = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            value.update(block)
    return value.hexdigest()


def expected_lines() -> list[str]:
    return [f"{digest(ROOT / name)}  {name}" for name in repository_files()]


def write_manifest() -> int:
    lines = expected_lines()
    MANIFEST.write_text("\n".join(lines) + "\n", encoding="ascii", newline="\n")
    print(f"WROTE: {MANIFEST} ({len(lines)} files)")
    return 0


def check_manifest() -> int:
    if not MANIFEST.is_file():
        print(f"FAIL: missing {MANIFEST}")
        return 1
    actual = MANIFEST.read_text(encoding="ascii").splitlines()
    expected = expected_lines()
    if actual != expected:
        actual_set = set(actual)
        expected_set = set(expected)
        print("FAIL: PROJECT_MANIFEST.sha256 is stale")
        print(f"  missing/changed entries: {len(expected_set - actual_set)}")
        print(f"  obsolete entries: {len(actual_set - expected_set)}")
        return 1
    print(f"PASS: PROJECT_MANIFEST.sha256 ({len(expected)} files)")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser()
    action = parser.add_mutually_exclusive_group(required=True)
    action.add_argument("--write", action="store_true")
    action.add_argument("--check", action="store_true")
    args = parser.parse_args()
    return write_manifest() if args.write else check_manifest()


if __name__ == "__main__":
    raise SystemExit(main())
