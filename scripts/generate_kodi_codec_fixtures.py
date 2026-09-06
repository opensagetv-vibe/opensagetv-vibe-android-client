#!/usr/bin/env python3
"""Generate deterministic video fixtures for Kodi-derived Android codec rules.

The generated media is intentionally short.  It is meant to exercise codec
selection, profile rejection/fallback, timestamp reordering, format changes,
interlace handling, drain/EOS, and malformed-input containment on real Android
hardware.  It is not a subjective picture-quality sample.

VC-1 is recorded as unavailable because current FFmpeg can decode and remux
VC-1 but does not provide a VC-1 encoder.  Use remux_vc1_fixture.py with a
legally redistributable source instead of relabeling WMV2 as VC-1.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
from typing import Any, Sequence


GENERATED_OUTPUT_NAMES = (
    "fixture-manifest.json",
    "mpeg2-interlaced-bframes.ts",
    "mpeg4-part2-bframes.avi",  # obsolete pre-MP4 fixture
    "mpeg4-part2-bframes.mp4",
    "mpeg4-part2-bframes.m4v",
    "mpeg4-part2-missing-pts.fault-plan.json",
    "h263-baseline.3gp",
    "h264-baseline-avcc.mp4",
    "h264-high-bframes-annexb.ts",
    "hevc-main-hvcc.mp4",
    "hevc-main10-hdr10.mkv",
    "vp8-profile0.webm",  # obsolete pre-Matroska fixture
    "vp8-profile0.mkv",
    "vp9-profile0.webm",  # obsolete pre-Matroska fixture
    "vp9-profile0.mkv",
    "vp9-profile2-10bit.webm",  # obsolete pre-Matroska fixture
    "vp9-profile2-10bit.mkv",
    "av1-main8.mkv",
    "h264-resolution-switch-annexb.ts",
    "h264-truncated-start.ts",
)


def executable(name: str, configured: str = "") -> str:
    candidate = configured.strip() if configured else name
    resolved = shutil.which(candidate)
    if resolved:
        return resolved
    if Path(candidate).is_file():
        return str(Path(candidate).resolve())
    raise FileNotFoundError(f"{name} executable not found: {candidate}")


def run(command: Sequence[str]) -> subprocess.CompletedProcess[str]:
    result = subprocess.run(
        list(command), check=False, text=True, encoding="utf-8",
        stdout=subprocess.PIPE, stderr=subprocess.PIPE,
    )
    if result.returncode:
        rendered = " ".join(str(value) for value in command)
        raise RuntimeError(
            f"command failed ({result.returncode}): {rendered}\n"
            f"stdout:\n{result.stdout}\nstderr:\n{result.stderr}"
        )
    return result


def ffmpeg_run(ffmpeg: str, arguments: Sequence[str]) -> None:
    command = [ffmpeg, "-hide_banner", "-loglevel", "error", "-y", *arguments]
    run(command)


def ffprobe_json(ffprobe: str, path: Path, *, frames: bool = False) -> dict[str, Any]:
    entries = (
        "stream=index,codec_name,codec_long_name,profile,codec_type,width,height,"
        "pix_fmt,level,field_order,sample_aspect_ratio,display_aspect_ratio,"
        "r_frame_rate,avg_frame_rate,color_space,color_transfer,color_primaries:"
        "format=format_name,duration,size,bit_rate"
    )
    command = [ffprobe, "-v", "error", "-show_streams", "-show_format", "-of", "json"]
    if frames:
        command.extend(["-select_streams", "v:0", "-show_frames",
                        "-show_entries", "frame=width,height,pict_type,key_frame,pts,dts"])
    else:
        command.extend(["-show_entries", entries])
    command.append(str(path))
    return json.loads(run(command).stdout)


def sha256(path: Path) -> str:
    value = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            value.update(block)
    return value.hexdigest()


def has_encoder(ffmpeg: str, name: str) -> bool:
    result = run([ffmpeg, "-hide_banner", "-encoders"])
    return any(line.split() and name in line.split()[1:2]
               for line in result.stdout.splitlines())


def source_inputs(size: str, rate: str, duration: float, frequency: int) -> list[str]:
    return [
        "-f", "lavfi", "-i", f"testsrc2=size={size}:rate={rate}",
        "-f", "lavfi", "-i", f"sine=frequency={frequency}:sample_rate=48000",
        "-t", f"{duration:.3f}", "-shortest",
    ]


def video_record(path: Path, rule: str, probe: dict[str, Any], **extra: Any) -> dict[str, Any]:
    record: dict[str, Any] = {
        "name": path.name,
        "rule": rule,
        "status": "GENERATED",
        "bytes": path.stat().st_size,
        "sha256": sha256(path),
        "probe": probe,
    }
    record.update(extra)
    return record


def generate(output: Path, duration: float, ffmpeg: str, ffprobe: str) -> list[dict[str, Any]]:
    output.mkdir(parents=True, exist_ok=True)
    for name in GENERATED_OUTPUT_NAMES:
        (output / name).unlink(missing_ok=True)
    records: list[dict[str, Any]] = []

    def make(name: str, rule: str, args: Sequence[str], **extra: Any) -> Path:
        path = output / name
        ffmpeg_run(ffmpeg, [*args, str(path)])
        records.append(video_record(path, rule, ffprobe_json(ffprobe, path), **extra))
        return path

    make(
        "mpeg2-interlaced-bframes.ts",
        "MPEG-2 interlace, B-frame timestamp ordering, aspect and clean EOS",
        [*source_inputs("720x480", "60000/1001", duration, 440),
         "-vf", "tinterlace=interleave_top,setsar=8/9", "-r", "30000/1001",
         "-c:v", "mpeg2video", "-profile:v", "main", "-flags", "+ildct+ilme",
         "-top", "1", "-bf", "2", "-g", "15", "-b:v", "5000k",
         "-c:a", "ac3", "-b:a", "192k", "-f", "mpegts"],
        expected_mime="video/mpeg2",
    )

    mpeg4_mp4 = make(
        "mpeg4-part2-bframes.mp4",
        "MPEG-4 Part 2 profile selection and B-frame PTS/DTS reordering",
        [*source_inputs("640x360", "30000/1001", duration, 550),
         "-c:v", "mpeg4", "-bf", "2", "-g", "30", "-q:v", "4",
         "-c:a", "aac", "-b:a", "128k", "-movflags", "+faststart"],
        expected_mime="video/mp4v-es",
    )
    raw_m4v = output / "mpeg4-part2-bframes.m4v"
    ffmpeg_run(ffmpeg, ["-i", str(mpeg4_mp4), "-map", "0:v:0", "-c:v", "copy",
                         "-an", "-f", "m4v", str(raw_m4v)])
    records.append(video_record(
        raw_m4v,
        "Raw MPEG-4 Part 2 input for controlled missing-PTS extractor injection",
        ffprobe_json(ffprobe, raw_m4v),
        expected_mime="video/mp4v-es",
        limitation=(
            "Standard containers reject DTS-present/PTS-NOPTS video packets. "
            "Use the companion fault plan in a development extractor; do not "
            "claim the valid raw file itself contains missing PTS."
        ),
    ))
    # Keep this as a controlled extractor model rather than corrupting the
    # container. MP4 carries both presentation and decode timestamps for the
    # B-frame stream, while the raw .m4v elementary stream does not retain a
    # useful independent container DTS/PTS pair.
    packet_probe = json.loads(run([
        ffprobe, "-v", "error", "-select_streams", "v:0", "-show_packets",
        "-show_entries", "packet=pts,dts,pts_time,dts_time,flags", "-of", "json",
        str(mpeg4_mp4),
    ]).stdout)
    packets = packet_probe.get("packets", [])
    selected = []
    reordered_count = 0
    for index, packet in enumerate(packets):
        pts = packet.get("pts")
        dts = packet.get("dts")
        if pts is not None and dts is not None and pts != dts:
            reordered_count += 1
        if (reordered_count > 0 and reordered_count % 15 == 0
                and pts is not None and dts is not None and pts != dts):
            selected.append({
                "packetIndex": index,
                "sourceDts": dts,
                "sourcePts": pts,
                "injectedPts": None,
                "expectedFallbackTimestamp": dts,
            })
            # Advance past this selection so only one packet is selected for
            # each group of 15 reordered samples.
            reordered_count += 1
    if not selected:
        raise RuntimeError("MPEG-4 B-frame fixture did not expose distinct PTS/DTS packets")
    fault_plan = {
        "schema": 1,
        "fixture": mpeg4_mp4.name,
        "mutation": "replace selected extractor sample PTS with TIME_UNSET while retaining DTS",
        "fallback": "use retained DTS only when PTS is TIME_UNSET",
        "status": "READY_FOR_CONTROLLED_EXTRACTOR_INJECTION",
        "packets": selected,
    }
    (output / "mpeg4-part2-missing-pts.fault-plan.json").write_text(
        json.dumps(fault_plan, indent=2) + "\n", encoding="utf-8"
    )

    make(
        "h263-baseline.3gp",
        "H.263 MIME/profile and maximum-dimension selection",
        [*source_inputs("352x288", "25", duration, 660),
         "-c:v", "h263", "-g", "25", "-b:v", "600k",
         "-c:a", "aac", "-b:a", "96k", "-f", "3gp"],
        expected_mime="video/3gpp",
    )

    make(
        "h264-baseline-avcc.mp4",
        "H.264 Baseline profile and MP4 avcC configuration",
        [*source_inputs("640x360", "30000/1001", duration, 770),
         "-c:v", "libx264", "-preset", "veryfast", "-profile:v", "baseline",
         "-level:v", "3.0", "-pix_fmt", "yuv420p", "-bf", "0", "-g", "30",
         "-c:a", "aac", "-b:a", "128k", "-movflags", "+faststart"],
        expected_mime="video/avc", bitstream_form="avcC",
    )
    make(
        "h264-high-bframes-annexb.ts",
        "H.264 High profile, B-frame ordering and Annex-B conversion",
        [*source_inputs("1280x720", "30000/1001", duration, 880),
         "-c:v", "libx264", "-preset", "veryfast", "-profile:v", "high",
         "-level:v", "4.0", "-pix_fmt", "yuv420p", "-bf", "3", "-g", "30",
         "-c:a", "aac", "-b:a", "128k", "-f", "mpegts"],
        expected_mime="video/avc", bitstream_form="Annex-B",
    )

    if has_encoder(ffmpeg, "libx265"):
        make(
            "hevc-main-hvcc.mp4",
            "HEVC Main profile and MP4 hvcC configuration",
            [*source_inputs("640x360", "30000/1001", duration, 990),
             "-c:v", "libx265", "-preset", "ultrafast", "-pix_fmt", "yuv420p",
             "-x265-params", "log-level=error:keyint=30", "-tag:v", "hvc1",
             "-c:a", "aac", "-b:a", "128k", "-movflags", "+faststart"],
            expected_mime="video/hevc", bitstream_form="hvcC",
        )
        make(
            "hevc-main10-hdr10.mkv",
            "HEVC Main 10 profile plus HDR10 color-metadata capability gate",
            [*source_inputs("640x360", "30000/1001", duration, 1100),
             "-vf", "format=yuv420p10le", "-c:v", "libx265", "-preset", "ultrafast",
             "-pix_fmt", "yuv420p10le", "-color_primaries", "bt2020",
             "-color_trc", "smpte2084", "-colorspace", "bt2020nc",
             "-x265-params", "log-level=error:keyint=30:repeat-headers=1:hdr-opt=1",
             "-c:a", "aac", "-b:a", "128k"],
            expected_mime="video/hevc", expected_profile="Main 10",
        )

    make(
        "vp8-profile0.mkv",
        "VP8 hardware MIME selection and clean EOS",
        [*source_inputs("640x360", "30", duration, 1210),
         "-c:v", "libvpx", "-deadline", "realtime", "-cpu-used", "8",
         "-g", "30", "-b:v", "1000k", "-c:a", "aac", "-b:a", "96k"],
        expected_mime="video/x-vnd.on2.vp8",
    )
    make(
        "vp9-profile0.mkv",
        "VP9 Profile 0 capability/profile selection",
        [*source_inputs("640x360", "30", duration, 1320),
         "-c:v", "libvpx-vp9", "-deadline", "realtime", "-cpu-used", "8",
         "-row-mt", "1", "-g", "30", "-b:v", "1000k", "-pix_fmt", "yuv420p",
         "-c:a", "aac", "-b:a", "96k"],
        expected_mime="video/x-vnd.on2.vp9", expected_profile="Profile 0",
    )
    make(
        "vp9-profile2-10bit.mkv",
        "VP9 Profile 2 rejection or hardware/fallback selection",
        [*source_inputs("640x360", "30", duration, 1430),
         "-vf", "format=yuv420p10le", "-c:v", "libvpx-vp9", "-deadline", "realtime",
         "-cpu-used", "8", "-row-mt", "1", "-g", "30", "-b:v", "1000k",
         "-pix_fmt", "yuv420p10le", "-profile:v", "2",
         "-c:a", "aac", "-b:a", "96k"],
        expected_mime="video/x-vnd.on2.vp9", expected_profile="Profile 2",
    )

    if has_encoder(ffmpeg, "libsvtav1"):
        make(
            "av1-main8.mkv",
            "AV1 Main profile selection; physical SKIP when device advertises no AV1 hardware",
            [*source_inputs("640x360", "30", min(duration, 3.0), 1540),
             "-c:v", "libsvtav1", "-preset", "12", "-crf", "42",
             "-pix_fmt", "yuv420p", "-g", "30", "-c:a", "libopus", "-b:a", "96k"],
            expected_mime="video/av01", expected_profile="Main",
        )

    with tempfile.TemporaryDirectory(prefix="vibe-codec-switch-") as temporary:
        temp = Path(temporary)
        segments = []
        segment_duration = duration / 2.0
        for index, size in enumerate(("640x360", "1280x720"), 1):
            segment = temp / f"segment{index}.ts"
            ffmpeg_run(ffmpeg, [
                "-f", "lavfi", "-i", f"testsrc2=size={size}:rate=30",
                "-t", f"{segment_duration:.3f}", "-an",
                "-c:v", "libx264", "-preset", "veryfast", "-profile:v", "high",
                "-pix_fmt", "yuv420p", "-g", "30", "-keyint_min", "30",
                "-sc_threshold", "0", "-x264-params", "repeat-headers=1",
                "-mpegts_flags", "+resend_headers",
                "-f", "mpegts", str(segment),
            ])
            segments.append(segment)
        switched_video = temp / "resolution-switch-video.ts"
        switched = output / "h264-resolution-switch-annexb.ts"
        concat_list = temp / "segments.txt"
        concat_list.write_text(
            "".join(f"file '{segment.name}'\n" for segment in segments),
            encoding="utf-8",
        )
        ffmpeg_run(ffmpeg, [
            "-f", "concat", "-safe", "0", "-i", str(concat_list),
            "-map", "0:v:0", "-c:v", "copy", "-an",
            "-mpegts_flags", "+resend_headers", "-muxdelay", "0",
            "-f", "mpegts", str(switched_video),
        ])
        ffmpeg_run(ffmpeg, [
            "-i", str(switched_video),
            "-f", "lavfi", "-i", "sine=frequency=1650:sample_rate=48000",
            "-t", f"{duration:.3f}", "-map", "0:v:0", "-map", "1:a:0",
            "-c:v", "copy", "-c:a", "aac", "-b:a", "128k", "-shortest",
            "-mpegts_flags", "+resend_headers", "-f", "mpegts", str(switched),
        ])
        frame_probe = ffprobe_json(ffprobe, switched, frames=True)
        dimensions = sorted({
            f"{frame.get('width')}x{frame.get('height')}"
            for frame in frame_probe.get("frames", []) if frame.get("width") and frame.get("height")
        })
        records.append(video_record(
            switched,
            "Adaptive H.264 sequence-header resolution change and renderer reconfiguration",
            ffprobe_json(ffprobe, switched),
            expected_mime="video/avc", observed_frame_dimensions=dimensions,
        ))

    good = output / "h264-high-bframes-annexb.ts"
    malformed = output / "h264-truncated-start.ts"
    data = good.read_bytes()
    # Deliberately start after several TS packets and truncate the tail. This is
    # a containment fixture, not content expected to play successfully.
    malformed.write_bytes(data[188 * 9:max(188 * 20, len(data) // 3)])
    records.append({
        "name": malformed.name,
        "rule": "Malformed/partial startup must fail or recover without process death",
        "status": "GENERATED_EXPECTED_FAILURE_OR_RECOVERY",
        "bytes": malformed.stat().st_size,
        "sha256": sha256(malformed),
    })

    records.extend([
        {
            "name": "vc1-source-required",
            "rule": "VC-1 MIME aliases video/wvc1, video/VC1 and video/vc1",
            "status": "SOURCE_REQUIRED",
            "reason": "Current FFmpeg has VC-1 decoders/remuxers but no VC-1 encoder.",
            "next": "Run scripts/remux_vc1_fixture.py on a legal VC-1 source.",
        },
        {
            "name": "secure-decoder-drm",
            "rule": "Secure decoder selection and DRM session negotiation",
            "status": "NOT_GENERATABLE_WITH_CLEAR_FFMPEG_FIXTURE",
            "reason": "A clear synthetic file cannot exercise a real Widevine/PlayReady secure session.",
        },
        {
            "name": "zero-dimension-format",
            "rule": "Reject MediaCodec configuration with zero dimensions",
            "status": "STATIC_OR_FAULT_INJECTION_ONLY",
            "reason": "A valid encoded video necessarily supplies dimensions.",
        },
    ])
    return records


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output-dir", default="artifacts/test-media/kodi-codec")
    parser.add_argument("--duration", type=float, default=6.0)
    parser.add_argument("--ffmpeg", default=os.environ.get("FFMPEG", ""))
    parser.add_argument("--ffprobe", default=os.environ.get("FFPROBE", ""))
    args = parser.parse_args()
    if args.duration <= 0:
        parser.error("--duration must be greater than zero")

    output = Path(args.output_dir).resolve()
    ffmpeg = executable("ffmpeg", args.ffmpeg)
    ffprobe = executable("ffprobe", args.ffprobe)
    records = generate(output, args.duration, ffmpeg, ffprobe)
    manifest = {
        "schema": 1,
        "generator": Path(__file__).name,
        "durationSeconds": args.duration,
        "ffmpegVersion": run([ffmpeg, "-hide_banner", "-version"]).stdout.splitlines()[0],
        "fixtures": records,
    }
    manifest_path = output / "fixture-manifest.json"
    manifest_path.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    generated = sum(1 for item in records if str(item.get("status", "")).startswith("GENERATED"))
    unavailable = len(records) - generated
    print(f"PASS: generated {generated} codec fixtures in {output}")
    print(f"INFO: {unavailable} non-generatable/fault-injection cases are explicit in {manifest_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
