#!/usr/bin/env python3
"""Generate Vibe's embedded bouncing-ball A/V synchronization fixture.

The ball reaches the impact line once every two seconds while the 48 kHz
stereo AC-3 track emits a short 1 kHz click from the same source clock. The result is
deterministic, redistributable test content intended for the in-client A/V Sync
Test.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_OUTPUT = (
    ROOT
    / "source"
    / "dev"
    / "android-shared"
    / "src"
    / "main"
    / "assets"
    / "vibe_av_sync_ball.ts"
)


def executable(name: str, override: str) -> str:
    if override:
        path = Path(override).expanduser().resolve()
        if path.is_file():
            return str(path)
        raise FileNotFoundError(f"{name} was not found: {path}")
    found = shutil.which(name)
    if found:
        return found
    raise FileNotFoundError(f"{name} was not found in PATH")


def run(command: list[str], *, capture: bool = False) -> subprocess.CompletedProcess[str]:
    print("+ " + " ".join(command))
    return subprocess.run(
        command,
        check=True,
        text=True,
        stdout=subprocess.PIPE if capture else None,
        stderr=subprocess.PIPE if capture else None,
    )


def build(ffmpeg: str, output: Path, duration: float) -> None:
    output.parent.mkdir(parents=True, exist_ok=True)
    frame_rate = "60000/1001"
    video_filter = (
        "[0:v]"
        # Four yellow registration corners prove that a physical-camera
        # recording contains the complete TV raster rather than a zoomed crop.
        "drawbox=x=8:y=8:w=92:h=8:color=yellow:t=fill,"
        "drawbox=x=8:y=8:w=8:h=92:color=yellow:t=fill,"
        "drawbox=x=iw-100:y=8:w=92:h=8:color=yellow:t=fill,"
        "drawbox=x=iw-16:y=8:w=8:h=92:color=yellow:t=fill,"
        "drawbox=x=8:y=ih-16:w=92:h=8:color=yellow:t=fill,"
        "drawbox=x=8:y=ih-100:w=8:h=92:color=yellow:t=fill,"
        "drawbox=x=iw-100:y=ih-16:w=92:h=8:color=yellow:t=fill,"
        "drawbox=x=iw-16:y=ih-100:w=8:h=92:color=yellow:t=fill,"
        "drawbox=x=0:y=596:w=iw:h=6:color=0x80c8ff:t=fill,"
        # Flash red for 75 ms from the common-clock impact. This is long enough
        # to remain visible at 30/60 fps while still occupying less than half
        # of the 200 ms hold. The audio click itself remains precisely 25 ms.
        "drawbox=x=0:y=596:w=iw:h=6:color=red:t=fill:"
        "enable='lt(mod(t\\,2)\\,0.075)',"
        # Keep the center column completely clear for the full-height bounce.
        # Test identity and instructions live in the unused side columns.
        "drawtext=text='OpenSageTV Vibe':"
        "fontcolor=white:fontsize=34:x=122:y=42,"
        "drawtext=text='A/V SYNC TEST':"
        "fontcolor=0x80c8ff:fontsize=40:x=122:y=84,"
        "drawtext=text='48 kHz AC-3 stereo':"
        "fontcolor=white:fontsize=21:x=122:y=142,"
        "drawtext=text='59.94 fps H.264':"
        "fontcolor=white:fontsize=21:x=122:y=174,"
        "drawtext=text='BALL IMPACT':"
        "fontcolor=white:fontsize=32:x=w-text_w-122:y=42,"
        "drawtext=text='+ AUDIO CLICK':"
        "fontcolor=0x80c8ff:fontsize=28:x=w-text_w-122:y=96,"
        "drawtext=text='SHOULD COINCIDE':"
        "fontcolor=white:fontsize=24:x=w-text_w-122:y=140,"
        "drawtext=text='PTS %{pts\\:hms}   FRAME %{n}':"
        "fontcolor=white:fontsize=25:x=122:y=h-54:"
        "box=1:boxcolor=black@0.60:boxborderw=8[base];"
        "[1:v]format=rgba,"
        # Retain Vibe's independent outlined-ball design. A lower-luminance
        # ring reduces bloom in physical-camera evidence.
        "geq=r='105':g='205':b='255':"
        "a='if(between((X-W/2)*(X-W/2)+(Y-H/2)*(Y-H/2),"
        "(W/2-13)*(W/2-13),(W/2-5)*(W/2-5)),255,0)'"
        "[ball];"
        "[base][ball]overlay=x='(W-w)/2':"
        # Hold the measurable impact for 200 ms, then use smooth frame-by-frame
        # travel for the remaining 1.8 seconds. The stable impact removes
        # display/camera persistence blur without quantizing the moving ball.
        # The 96 px ring's visible outer edge ends 91 px below its overlay
        # origin. An impact origin of 505 therefore touches line row 596.
        "y='505-415*abs(sin(PI*max(mod(t\\,2)-0.2\\,0)/1.8))':"
        "eval=frame,format=yuv420p[v]"
    )
    # Each click begins on a two-second boundary and lasts 25 ms. A quiet continuous
    # 220 Hz reference keeps HDMI, AVR, and capture-device audio gates awake;
    # otherwise some hardware passes only the first click after a long run of
    # encoded digital silence. The click remains dominant and easy to locate.
    audio_source = (
        f"aevalsrc=exprs='0.015*sin(2*PI*220*t)+if(lt(mod(t,2),0.025),"
        f"0.74*sin(2*PI*1000*t),0)':s=48000:d={duration:.6f}"
    )
    command = [
        ffmpeg,
        "-hide_banner",
        "-loglevel", "error",
        "-y",
        "-f", "lavfi",
        "-i", f"color=c=0x07121f:s=1280x720:r={frame_rate}:d={duration:.6f}",
        "-f", "lavfi",
        "-i", f"color=c=white@0.0:s=96x96:r={frame_rate}:d={duration:.6f}",
        "-f", "lavfi",
        "-i", audio_source,
        "-filter_complex", video_filter,
        "-map", "[v]",
        "-map", "2:a:0",
        "-c:v", "libx264",
        "-preset", "medium",
        "-crf", "20",
        "-profile:v", "high",
        "-level:v", "4.1",
        "-pix_fmt", "yuv420p",
        "-r", frame_rate,
        "-g", "60",
        "-keyint_min", "60",
        "-sc_threshold", "0",
        "-c:a", "ac3",
        "-b:a", "192k",
        "-ar", "48000",
        "-ac", "2",
        "-metadata", "title=OpenSageTV Vibe Bouncing Ball AV Sync Test",
        "-metadata:s:a:0", "language=eng",
        "-metadata:s:a:0", "title=48 kHz impact clicks",
        "-shortest",
        "-muxdelay", "0",
        "-muxpreload", "0",
        "-f", "mpegts",
        str(output),
    ]
    run(command)


def validate(ffprobe: str, output: Path, duration: float) -> dict[str, object]:
    result = run(
        [
            ffprobe,
            "-v", "error",
            "-show_entries",
            "format=duration:stream=index,codec_type,codec_name,width,height,"
            "avg_frame_rate,sample_rate,channels,start_time",
            "-of", "json",
            str(output),
        ],
        capture=True,
    )
    probe = json.loads(result.stdout)
    streams = probe.get("streams", [])
    video = next((item for item in streams if item.get("codec_type") == "video"), None)
    audio = next((item for item in streams if item.get("codec_type") == "audio"), None)
    if not video or video.get("codec_name") != "h264":
        raise RuntimeError("fixture does not contain H.264 video")
    if (video.get("width"), video.get("height")) != (1280, 720):
        raise RuntimeError("fixture video is not 1280x720")
    if not audio or audio.get("codec_name") != "ac3":
        raise RuntimeError("fixture does not contain AC-3 audio")
    if audio.get("sample_rate") != "48000" or audio.get("channels") != 2:
        raise RuntimeError("fixture audio is not 48 kHz stereo")
    actual_duration = float(probe.get("format", {}).get("duration", 0.0))
    if actual_duration < duration - 0.25:
        raise RuntimeError(
            f"fixture duration {actual_duration:.3f}s is shorter than requested {duration:.3f}s"
        )
    digest = hashlib.sha256(output.read_bytes()).hexdigest()
    return {
        "path": str(output),
        "bytes": output.stat().st_size,
        "sha256": digest,
        "durationSeconds": actual_duration,
        "impactIntervalSeconds": 2.0,
        "impactPhaseSeconds": 0.0,
        "impactHoldMilliseconds": 200,
        "motionStepMilliseconds": 0,
        "motionMode": "smooth-two-second-bounce-with-200ms-impact-hold",
        "clickDurationMilliseconds": 25,
        "impactLineFlashMilliseconds": 75,
        "impactLineIdleColor": "blue",
        "impactLineEventColor": "red",
        "video": video,
        "audio": audio,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    # Keep the embedded fixture long enough to validate either extreme of the
    # +/-4 second slider without crossing Media3's repeat boundary.  A short
    # fixture gives shifted audio and video different effective end times;
    # testing near -4 seconds can then measure the loop transition instead of
    # the requested clock offset.
    parser.add_argument("--duration", type=float, default=60.0)
    parser.add_argument("--ffmpeg", default=os.environ.get("FFMPEG", ""))
    parser.add_argument("--ffprobe", default=os.environ.get("FFPROBE", ""))
    parser.add_argument("--metadata", type=Path)
    args = parser.parse_args()
    if args.duration < 4.0:
        parser.error("--duration must be at least 4 seconds")
    ffmpeg = executable("ffmpeg", args.ffmpeg)
    ffprobe = executable("ffprobe", args.ffprobe)
    output = args.output.expanduser().resolve()
    build(ffmpeg, output, args.duration)
    metadata = validate(ffprobe, output, args.duration)
    if args.metadata:
        metadata_path = args.metadata.expanduser().resolve()
        metadata_path.parent.mkdir(parents=True, exist_ok=True)
        metadata_path.write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(metadata, indent=2))
    print(f"PASS: generated bouncing-ball A/V sync fixture: {output}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (FileNotFoundError, RuntimeError, subprocess.CalledProcessError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        raise SystemExit(1)
