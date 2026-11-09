#!/usr/bin/env python3
"""Read-only ABI and archive-entry gate before reviewing a native IJK candidate."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
TOOLS = Path('/opt/android-sdk/ndk/21.0.6113669/toolchains/llvm/prebuilt/linux-x86_64/bin')


def inspect(library):
    exported = subprocess.check_output([TOOLS/'llvm-nm', '-D', '--defined-only', library], text=True)
    dynamic = subprocess.check_output([TOOLS/'llvm-readelf', '-d', library], text=True)
    return ({line.split()[-1] for line in exported.splitlines() if line.strip()},
            {line.split('[', 1)[1].split(']', 1)[0]
             for line in dynamic.splitlines() if '(NEEDED)' in line})


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--candidate-dir', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    candidate = args.candidate_dir.resolve()
    output = args.output.resolve()
    assert candidate.is_relative_to((ROOT/'artifacts/active').resolve())
    assert output.is_relative_to((ROOT/'artifacts/active').resolve()) and not output.exists()
    records = []
    with tempfile.TemporaryDirectory(prefix='vibe-ijk-abi-') as temporary:
        for short, abi in [('armv7a', 'armeabi-v7a'), ('arm64', 'arm64-v8a')]:
            name = f'ijkplayer-{short}-0.8.8-SNAPSHOT.aar'
            baseline = ROOT/'source/dev/libs'/name
            proposed = candidate/name
            library = f'jni/{abi}/libijkplayer.so'
            with zipfile.ZipFile(baseline) as old, zipfile.ZipFile(proposed) as new:
                assert set(old.namelist()) == set(new.namelist()), 'Archive entries changed'
                for entry in old.namelist():
                    if entry != library:
                        assert old.read(entry) == new.read(entry), f'Non-player entry changed: {entry}'
                before = Path(temporary)/('baseline-'+short+'.so')
                after = Path(temporary)/('candidate-'+short+'.so')
                before.write_bytes(old.read(library))
                after.write_bytes(new.read(library))
                old_exports, old_needed = inspect(before)
                new_exports, new_needed = inspect(after)
                assert old_exports == new_exports, 'Native export ABI changed'
                assert old_needed == new_needed, 'Native runtime dependencies changed'
            records.append({'abi': abi, 'exports': len(new_exports), 'exportsIdentical': True,
                            'needed': sorted(new_needed), 'neededIdentical': True,
                            'nonPlayerEntriesIdentical': True,
                            'candidateSha256': hashlib.file_digest(proposed.open('rb'), 'sha256').hexdigest(),
                            'baselineSha256': hashlib.file_digest(baseline.open('rb'), 'sha256').hexdigest()})
    output.write_text(json.dumps(records, indent=2)+'\n')
    print(json.dumps(records))


if __name__ == '__main__': main()
