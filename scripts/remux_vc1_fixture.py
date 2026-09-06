#!/usr/bin/env python3
"""Validate and remux a provenance-recorded VC-1 source for local testing.

The output remains under ignored ``artifacts/test-media`` by default.  A source
described as local-test-only (for example, one of Kodi's fair-use samples) must
not be copied into a source handoff or release archive.
"""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess


def run(command: list[str]) -> subprocess.CompletedProcess[str]:
    return subprocess.run(command, check=True, text=True, encoding="utf-8",
                          stdout=subprocess.PIPE, stderr=subprocess.PIPE)


def digest(path: Path) -> str:
    value = hashlib.sha256(path.read_bytes()).hexdigest()
    return value


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True)
    parser.add_argument("--output-dir", default="artifacts/test-media/kodi-codec/vc1")
    parser.add_argument("--source-url", required=True,
                        help="Canonical page or download URL for provenance")
    parser.add_argument("--license-note", required=True,
                        help="Provenance/license note recorded beside derived fixtures")
    parser.add_argument(
        "--redistribution", choices=("local-test-only", "redistributable"),
        default="local-test-only",
        help="Whether derived media may be packaged; default is the safe local-only policy",
    )
    args = parser.parse_args()
    ffmpeg = shutil.which("ffmpeg")
    ffprobe = shutil.which("ffprobe")
    if not ffmpeg or not ffprobe:
        parser.error("ffmpeg and ffprobe are required")
    source = Path(args.input).resolve()
    if not source.is_file():
        parser.error(f"input does not exist: {source}")
    probe = json.loads(run([
        ffprobe, "-v", "error", "-select_streams", "v:0", "-show_entries",
        "stream=codec_name,profile,width,height", "-of", "json", str(source),
    ]).stdout)
    streams = probe.get("streams", [])
    if not streams or streams[0].get("codec_name") != "vc1":
        parser.error("input video codec is not VC-1; WMV1/WMV2/WMV3 are not substitutes")

    output = Path(args.output_dir).resolve()
    output.mkdir(parents=True, exist_ok=True)
    mkv = output / "vc1-advanced-profile.mkv"
    raw = output / "vc1-advanced-profile.vc1"
    for target, fmt in ((mkv, "matroska"), (raw, "vc1")):
        run([ffmpeg, "-hide_banner", "-loglevel", "error", "-y", "-i", str(source),
             "-map", "0:v:0", "-c:v", "copy", "-an", "-f", fmt, str(target)])
    manifest = {
        "schema": 1,
        "sourceFilename": source.name,
        "sourceUrl": args.source_url,
        "sourceSha256": digest(source),
        "licenseNote": args.license_note,
        "redistribution": args.redistribution,
        "packageInSourceOrRelease": args.redistribution == "redistributable",
        "sourceProbe": probe,
        "fixtures": [
            {"name": mkv.name, "sha256": digest(mkv), "container": "matroska"},
            {"name": raw.name, "sha256": digest(raw), "container": "raw-vc1"},
        ],
        "mimeAliasesToTest": ["video/wvc1", "video/VC1", "video/vc1"],
    }
    (output / "vc1-fixture-manifest.json").write_text(
        json.dumps(manifest, indent=2) + "\n", encoding="utf-8"
    )
    print(f"PASS: generated VC-1 remux fixtures in {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
