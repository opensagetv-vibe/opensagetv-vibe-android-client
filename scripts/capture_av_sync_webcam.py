#!/usr/bin/env python3
"""Capture physical A/V-sync evidence from a directly attached C920.

FFmpeg opens the C920 video and microphone in one DirectShow graph, preserving
their relative timestamps in one Matroska file. Logi Capture, virtual-camera
layers, GStreamer, and separate audio/video recorder processes are deliberately
not used.
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
DEFAULT_CAMERA = "HD Pro Webcam C920"
DEFAULT_MICROPHONE = "Microphone (HD Pro Webcam C920)"
POST_OPEN_RESET_DELAY_SECONDS = 1.0


def reset_camera(camera: str) -> dict[str, object]:
    reset_script = PROJECT_ROOT / "scripts" / "reset_directshow_camera.ps1"
    reset = subprocess.run(
        [
            "powershell.exe",
            "-NoLogo",
            "-NoProfile",
            "-ExecutionPolicy",
            "Bypass",
            "-File",
            str(reset_script),
            "-Camera",
            camera,
        ],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        timeout=30.0,
        creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0,
        check=False,
    )
    if reset.returncode != 0:
        detail = reset.stderr.strip() or reset.stdout.strip()
        raise RuntimeError(f"DirectShow camera reset failed: {detail}")
    try:
        return json.loads(reset.stdout.strip())
    except json.JSONDecodeError as exc:
        raise RuntimeError(
            f"DirectShow camera reset returned invalid JSON: {reset.stdout.strip()}"
        ) from exc


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--duration", type=float, default=20.0)
    parser.add_argument("--camera", default=DEFAULT_CAMERA)
    parser.add_argument("--microphone", default=DEFAULT_MICROPHONE)
    parser.add_argument("--ffmpeg", type=Path, default=DEFAULT_FFMPEG)
    parser.add_argument(
        "--video-only",
        action="store_true",
        help=(
            "Capture camera video without the microphone. This is only a "
            "motion-clarity preflight and cannot close the A/V-sync gate."
        ),
    )
    parser.add_argument("--width", type=int, default=1280)
    parser.add_argument("--height", type=int, default=720)
    parser.add_argument("--fps", type=int, default=30)
    parser.add_argument(
        "--skip-camera-reset",
        action="store_true",
        help="Do not reset DirectShow zoom/pan/tilt; diagnostic use only",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    if args.duration < 3.0 or args.duration > 300.0:
        raise SystemExit("--duration must be between 3 and 300 seconds")
    if args.width <= 0 or args.height <= 0 or args.fps <= 0:
        raise SystemExit("capture dimensions and frame rate must be positive")

    ffmpeg = args.ffmpeg.expanduser().resolve()
    if not ffmpeg.is_file():
        raise SystemExit(f"Vibe FFmpeg was not found at {ffmpeg}")

    output = args.output.expanduser()
    if not output.is_absolute():
        output = PROJECT_ROOT / output
    output = output.resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    log_path = output.with_suffix(output.suffix + ".ffmpeg.log")
    output.unlink(missing_ok=True)
    log_path.unlink(missing_ok=True)

    camera_reset: dict[str, object] | None = None
    if not args.skip_camera_reset:
        try:
            camera_reset = {"beforeOpen": reset_camera(args.camera)}
        except RuntimeError as exc:
            raise SystemExit(str(exc)) from exc

    source = f"video={args.camera}"
    if not args.video_only:
        source += f":audio={args.microphone}"
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
        f"{args.width}x{args.height}",
        "-framerate",
        str(args.fps),
        "-i",
        source,
        "-t",
        f"{args.duration:.3f}",
        "-map",
        "0:v:0",
    ]
    if not args.video_only:
        command += ["-map", "0:a:0"]
    command += [
        "-c:v",
        "libx264",
        "-preset",
        "ultrafast",
        "-tune",
        "zerolatency",
        "-pix_fmt",
        "yuv420p",
        "-fps_mode",
        "passthrough",
    ]
    if not args.video_only:
        command += [
            "-c:a",
            "aac",
            "-b:a",
            "192k",
            "-ar",
            "48000",
            "-ac",
            "2",
        ]
    command += [str(output)]

    creation_flags = subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0
    with log_path.open("wb") as log_file:
        process = subprocess.Popen(
            command,
            stdout=log_file,
            stderr=subprocess.STDOUT,
            creationflags=creation_flags,
        )
        if camera_reset is not None:
            # The C920 driver applies its saved zoom/pan/tilt when the capture
            # graph starts, after a pre-open reset. Reset once more while that
            # exact DirectShow stream owns the device. This preserves one
            # camera/microphone clock and prevents every FFmpeg run from
            # silently returning to a center crop.
            time.sleep(POST_OPEN_RESET_DELAY_SECONDS)
            try:
                camera_reset["afterOpen"] = reset_camera(args.camera)
            except RuntimeError as exc:
                process.terminate()
                process.wait(timeout=5.0)
                raise SystemExit(str(exc)) from exc
        try:
            return_code = process.wait(timeout=args.duration + 25.0)
        except subprocess.TimeoutExpired as exc:
            process.terminate()
            try:
                process.wait(timeout=5.0)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=5.0)
            raise SystemExit("FFmpeg C920 capture timed out") from exc
    if return_code != 0:
        raise SystemExit(
            f"FFmpeg C920 capture failed with exit code {return_code}. "
            f"See {log_path}"
        )
    if not output.is_file() or output.stat().st_size <= 0:
        raise SystemExit(f"FFmpeg capture completed without media: {output}")

    payload = {
        "output": str(output),
        "bytes": output.stat().st_size,
        "durationSeconds": args.duration,
        "camera": args.camera,
        "microphone": None if args.video_only else args.microphone,
        "video": f"{args.width}x{args.height}@{args.fps}",
        "audio": "none" if args.video_only else "AAC 48 kHz stereo",
        "evidence": "motion-clarity preflight" if args.video_only else "A/V evidence",
        "cameraReset": camera_reset or {"skipped": True},
        "ffmpeg": str(ffmpeg),
        "log": str(log_path),
    }
    print(json.dumps(payload, indent=2))
    if args.video_only:
        print("PASS: captured direct C920 motion-clarity preflight")
    else:
        print("PASS: captured direct C920 video and microphone on one FFmpeg clock")
    return 0


if __name__ == "__main__":
    sys.exit(main())
