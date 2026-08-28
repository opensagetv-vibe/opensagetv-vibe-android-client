#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import shutil
from pathlib import Path


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open('rb') as fh:
        for chunk in iter(lambda: fh.read(1024 * 1024), b''):
            h.update(chunk)
    return h.hexdigest()


def repair(workspace: Path, check_only: bool = False) -> int:
    root_gradle = workspace / 'source/dev/build.gradle'
    shared_gradle = workspace / 'source/dev/android-shared/build.gradle'
    canonical = workspace / 'config/dev-root-build.gradle.canonical'

    for path in (shared_gradle, canonical):
        if not path.is_file():
            raise SystemExit(f'ERROR: required Gradle repair source is missing: {path}')

    canonical_text = canonical.read_text(encoding='utf-8', errors='ignore')
    shared_text = shared_gradle.read_text(encoding='utf-8', errors='ignore')
    current_text = root_gradle.read_text(encoding='utf-8', errors='ignore') if root_gradle.exists() else ''

    if 'buildscript {' not in canonical_text or 'media3Version' not in canonical_text:
        raise SystemExit('ERROR: canonical Dev root Gradle file is invalid')
    if "apply plugin: 'com.android.library'" not in shared_text:
        raise SystemExit('ERROR: android-shared/build.gradle does not look like the Android library module')

    expected = sha256(canonical)
    current = sha256(root_gradle) if root_gradle.is_file() else None
    if current == expected:
        print(f'PASS: Dev root Gradle layout is correct ({expected[:12]})')
        return 0

    looks_swapped = current_text == shared_text or (
        "apply plugin: 'com.android.library'" in current_text and 'buildscript {' not in current_text
    )

    if check_only:
        detail = ' (looks replaced by android-shared/build.gradle)' if looks_swapped else ''
        print(f'FAIL: source/dev/build.gradle does not match canonical{detail}')
        return 1

    backup = root_gradle.with_suffix('.gradle.bad')
    if root_gradle.exists():
        shutil.copy2(root_gradle, backup)
    shutil.copy2(canonical, root_gradle)
    repaired = sha256(root_gradle)
    if repaired != expected:
        raise SystemExit('ERROR: Dev root Gradle repair copy verification failed')

    reason = 'swapped android-shared module file' if looks_swapped else 'unexpected root Gradle contents'
    print(f'REPAIRED: source/dev/build.gradle ({reason})')
    if backup.exists():
        print(f'Backup: {backup}')
    print(f'SHA-256: {repaired}')
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description='Repair/verify the Dev root build.gradle against the canonical project copy.')
    parser.add_argument('--workspace', default='/workspace', help='SageTV MiniClient Dev project root')
    parser.add_argument('--check', action='store_true', help='Only check; do not repair')
    args = parser.parse_args()
    return repair(Path(args.workspace).resolve(), args.check)


if __name__ == '__main__':
    raise SystemExit(main())
