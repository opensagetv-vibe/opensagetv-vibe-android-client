#!/usr/bin/env python3
"""Capture repeatable Fire TV HDMI validation evidence with VLC.

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


PROJECT_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_OUTPUT = PROJECT_ROOT / "artifacts" / "firetv" / "dvd-hdmi-validation.mp4"
DEFAULT_VLC = Path(os.environ.get("VIBE_VLC_PATH", r"C:\Program Files\VideoLAN\VLC\vlc.exe"))


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Capture the HDMI input to an H.264/AAC MP4 using VLC."
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
        "--vlc",
        type=Path,
        default=DEFAULT_VLC,
        help="path to vlc.exe (or set VIBE_VLC_PATH)",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    if args.duration <= 0:
        raise SystemExit("--duration must be greater than zero")

    vlc = args.vlc.expanduser().resolve()
    if not vlc.is_file():
        raise SystemExit(f"VLC was not found at {vlc}")

    output = args.output.expanduser()
    if not output.is_absolute():
        output = PROJECT_ROOT / output
    output = output.resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    log_path = output.with_suffix(".vlc.log")

    output.unlink(missing_ok=True)
    log_path.unlink(missing_ok=True)

    # This inexpensive UVC bridge advertises 60 fps but alternates a valid
    # JPEG with a non-image transport packet at that mode. Its stable decoded
    # cadence is 30 fps, which is sufficient for detecting duplicate/frozen
    # Fire TV output. VLC decodes MJPEG and writes timestamped H.264/AAC MP4.
    sout = (
        "#transcode{vcodec=h264,vb=16000,fps=30,acodec=mp4a,ab=192,"
        f'channels=2,samplerate=48000}}:standard{{access=file,mux=mp4,dst="{output}"}}'
    )
    command = [
        str(vlc),
        "--intf=dummy",
        "--no-one-instance",
        "--file-logging",
        f"--logfile={log_path}",
        "--verbose=2",
        f"--run-time={args.duration}",
        "--play-and-exit",
        "--no-repeat",
        "--no-loop",
        "--no-random",
        "dshow://",
        f':dshow-vdev={args.video_device}',
        f':dshow-adev={args.audio_device}',
        ":dshow-size=1920x1080",
        ":dshow-fps=30",
        f"--sout={sout}",
    ]

    creation_flags = subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0
    process = subprocess.Popen(command, creationflags=creation_flags)
    try:
        process.wait(timeout=args.duration + 15)
    except subprocess.TimeoutExpired:
        process.terminate()
        try:
            process.wait(timeout=5)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait()

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
                "videoDevice": args.video_device,
                "audioDevice": args.audio_device,
                "log": str(log_path),
            },
            indent=2,
        )
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
