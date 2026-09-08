#!/usr/bin/env python3
"""Generate or verify the repository's deterministic SHA-256 manifest."""

from __future__ import annotations

import argparse
import hashlib
from pathlib import Path
import subprocess


ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "PROJECT_MANIFEST.sha256"


def is_generated_delivery_artifact(name: str) -> bool:
    """Return True for root-level ZIPs produced by handoff/update packaging."""
    path = Path(name)
    if len(path.parts) != 1 or path.suffix.lower() != ".zip":
        return False
    lower_name = path.name.lower()
    return "-ai-handoff-v" in lower_name or "changed-files-only" in lower_name


def repository_files() -> list[str]:
    try:
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
            check=False,
            capture_output=True,
        )
    except FileNotFoundError:
        result = None

    if result is not None and result.returncode == 0:
        names = result.stdout.decode("utf-8").split("\0")
    else:
        # GitHub source archives intentionally contain no .git directory. Their
        # signed file set is the manifest itself, so verify those exact paths
        # instead of making extracted users install or initialize Git first.
        if not MANIFEST.is_file():
            detail = "git unavailable" if result is None else result.stderr.decode(
                "utf-8", errors="replace"
            ).strip()
            raise RuntimeError(
                "cannot enumerate a non-Git checkout without PROJECT_MANIFEST.sha256"
                + (f": {detail}" if detail else "")
            )
        names = []
        seen: set[str] = set()
        for line in MANIFEST.read_text(encoding="ascii").splitlines():
            if "  " not in line:
                raise ValueError("malformed PROJECT_MANIFEST.sha256 entry")
            _, name = line.split("  ", 1)
            path = Path(name)
            if path.is_absolute() or ".." in path.parts or name in seen:
                raise ValueError(f"unsafe or duplicate manifest path: {name}")
            seen.add(name)
            names.append(name)
    return sorted(
        name
        for name in names
        if name
        and name != MANIFEST.name
        and not is_generated_delivery_artifact(name)
        and (ROOT / name).is_file()
    )


def canonical_content(path: Path) -> bytes:
    """Return portable bytes while preserving genuinely binary content.

    Git intentionally checks Windows command/PowerShell files out as CRLF and
    shell/source files as LF.  A release manifest must describe the same source
    on both platforms, so valid UTF-8 text with conventional line endings is
    hashed in LF form.  Binary data, invalid UTF-8, and files with lone CR bytes
    remain byte-exact.
    """
    data = path.read_bytes()
    if b"\x00" in data:
        return data
    try:
        data.decode("utf-8")
    except UnicodeDecodeError:
        return data
    normalized = data.replace(b"\r\n", b"\n")
    return data if b"\r" in normalized else normalized


def digest(path: Path) -> str:
    value = hashlib.sha256()
    value.update(canonical_content(path))
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

        def by_path(lines: list[str]) -> dict[str, str]:
            values: dict[str, str] = {}
            for line in lines:
                if "  " in line:
                    value, name = line.split("  ", 1)
                    values[name] = value
            return values

        actual_by_path = by_path(actual)
        expected_by_path = by_path(expected)
        changed_paths = sorted(
            name
            for name in actual_by_path.keys() & expected_by_path.keys()
            if actual_by_path[name] != expected_by_path[name]
        )
        missing_paths = sorted(expected_by_path.keys() - actual_by_path.keys())
        obsolete_paths = sorted(actual_by_path.keys() - expected_by_path.keys())

        for label, names in (
            ("changed", changed_paths),
            ("missing", missing_paths),
            ("obsolete", obsolete_paths),
        ):
            if names:
                print(f"  {label} paths ({len(names)}):")
                for name in names[:20]:
                    print(f"    {name}")
                if len(names) > 20:
                    print(f"    ... and {len(names) - 20} more")
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
