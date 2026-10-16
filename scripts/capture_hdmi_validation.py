#!/usr/bin/env python3
"""Capture repeatable Android TV HDMI evidence with the Vibe FFmpeg build.

This is intentionally a Python entry point so Windows PowerShell execution
policy does not affect physical-device testing.
"""

from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import subprocess
import sys
import time


PROJECT_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_OUTPUT = PROJECT_ROOT / "artifacts" / "firetv" / "dvd-hdmi-validation.mp4"
DEFAULT_FFMPEG = Path(
    os.environ.get(
        "VIBE_FFMPEG_PATH",
        PROJECT_ROOT.parent
        / "opensagetv-vibe-ffmpeg-mim"
        / "output"
        / "windows-x64"
        / "ffmpeg.real.exe",
    )
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Capture the HDMI input to an H.264/AAC MP4 using Vibe FFmpeg."
    )
    parser.add_argument(
        "--output",
        "-o",
        type=Path,
        default=DEFAULT_OUTPUT,
        help=f"output MP4 path (default: {DEFAULT_OUTPUT})",
    )
    parser.add_argument(
        "--duration",
        "-d",
        type=int,
        default=18,
        help="capture duration in seconds (default: 18)",
    )
    parser.add_argument(
        "--video-device",
        default="USB Video",
        help="DirectShow video device name",
    )
    parser.add_argument(
        "--audio-device",
        default="Digital Audio Interface (USB Digital Audio)",
        help="DirectShow audio device name",
    )
    parser.add_argument(
        "--ffmpeg",
        type=Path,
        default=DEFAULT_FFMPEG,
        help="path to ffmpeg.real.exe (or set VIBE_FFMPEG_PATH)",
    )
    parser.add_argument('--ready-file',type=Path,
                        help='Optional live-stage identity/expiry marker from the affected test driver')
    parser.add_argument('--ready-timeout',type=int,default=60,
                        help='Bounded background wait for that marker (seconds, 1..180)')
    parser.add_argument('--capture-mode',choices=('1080p30','720p60'),default='1080p30',
                        help='720p60 is an explicit capture-rate qualification mode; frame count alone is not scanout proof')
    return parser.parse_args()


def wait_for_live_stage(path: Path, duration: int, timeout: int) -> dict:
    """Do not turn a late Home/menu recording into playback evidence."""
    if not 1 <= timeout <= 180: raise ValueError('ready-timeout must be 1..180')
    deadline=time.monotonic()+timeout
    while time.monotonic()<deadline:
        if path.exists():
            try: marker=json.loads(path.read_text(encoding='utf-8'))
            except json.JSONDecodeError: time.sleep(.2); continue
            if marker.get('scopeVerified') is not True:
                raise ValueError('capture scope is unverified')
            # Include graph-open allowance; never start near the hold's end.
            if int(marker.get('expiresAtMs',0))-int(time.time()*1000)<(duration+5)*1000:
                raise ValueError('capture marker is stale or its live stage is too short')
            return marker
        time.sleep(.2)
    raise TimeoutError('verified live capture stage was not reached')


def main() -> int:
    args = parse_args()
    if args.duration <= 0:
        raise SystemExit("--duration must be greater than zero")

    ffmpeg = args.ffmpeg.expanduser().resolve()
    if not ffmpeg.is_file():
        raise SystemExit(f"Vibe FFmpeg was not found at {ffmpeg}")
    marker=None
    if args.ready_file:
        ready=args.ready_file.resolve()
        if not ready.is_relative_to((PROJECT_ROOT/'artifacts/active').resolve()):
            raise SystemExit('ready-file must be inside project artifacts/active')
        marker=wait_for_live_stage(ready,args.duration,args.ready_timeout)

    output = args.output.expanduser()
    if not output.is_absolute():
        output = PROJECT_ROOT / output
    output = output.resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    log_path = output.with_suffix(".ffmpeg.log")

    output.unlink(missing_ok=True)
    log_path.unlink(missing_ok=True)

    # This inexpensive UVC bridge advertises 60 fps but alternates a valid
    # JPEG with a non-image transport packet at that mode. Capture its stable
    # 30 fps MJPEG mode and encode timestamped H.264/AAC evidence.
    command = [
        str(ffmpeg),
        "-hide_banner",
        "-nostdin",
        "-y",
        "-thread_queue_size",
        "1024",
        "-rtbufsize",
        "512M",
        "-f",
        "dshow",
        "-vcodec",
        "mjpeg",
        "-video_size",
        "1920x1080",
        "-framerate",
        "30",
        "-i",
        f"video={args.video_device}:audio={args.audio_device}",
        "-t",
        str(args.duration),
        "-map",
        "0:v:0",
        "-map",
        "0:a:0",
        "-c:v",
        "libx264",
        "-preset",
        "veryfast",
        "-tune",
        "zerolatency",
        "-b:v",
        "16M",
        "-pix_fmt",
        "yuv420p",
        "-r",
        "30",
        "-fps_mode",
        "cfr",
        "-c:a",
        "aac",
        "-b:a",
        "192k",
        "-ar",
        "48000",
        "-ac",
        "2",
        "-movflags",
        "+faststart",
        str(output),
    ]
    if args.capture_mode=='720p60':
        # The bridge's unstable1080p60 claim does not establish720p60 either.
        # Keep the default untouched and qualify distinct source picture IDs
        # independently in this explicit mode. No CFR duplicates are inserted.
        command[command.index('-video_size')+1]='1280x720'
        command[command.index('-framerate')+1]='60'
        at=command.index('-r');del command[at:at+2]
        command[command.index('-fps_mode')+1]='passthrough'

    creation_flags = subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0
    with log_path.open("wb") as log_file:
        process = subprocess.run(
            command,
            stdout=log_file,
            stderr=subprocess.STDOUT,
            timeout=args.duration + 20,
            creationflags=creation_flags,
            check=False,
        )

    if process.returncode != 0:
        raise SystemExit(
            f"FFmpeg HDMI capture failed with exit code {process.returncode}. "
            f"See {log_path}"
        )

    if not output.is_file():
        raise SystemExit(f"HDMI capture did not create {output}. See {log_path}")
    size = output.stat().st_size
    if size <= 0:
        raise SystemExit(f"HDMI capture is empty: {output}")

    print(
        json.dumps(
            {
                "output": str(output),
                "bytes": size,
                "durationSeconds": args.duration,
                "captureMode":args.capture_mode,
                "cadenceQualification":"requires distinct source-frame/field evidence, not encoded frame count",
                "videoDevice": args.video_device,
                "audioDevice": args.audio_device,
                "ffmpeg": str(ffmpeg),
                "log": str(log_path),
                "verifiedLiveStage": marker,
            },
            indent=2,
        )
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
