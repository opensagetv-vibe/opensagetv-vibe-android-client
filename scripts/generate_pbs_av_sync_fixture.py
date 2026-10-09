#!/usr/bin/env python3
"""Generate a server fixture matching the PBS NewsHour playback profile.

The output is deterministic, redistributable test content: MPEG-TS containing
top-field-first 1920x1080 MPEG-2 video at 30000/1001 and 48 kHz stereo AC-3.
The ball reaches the impact line exactly when the common source clock creates
an audio click, making transport/player offset visible without copyrighted
recording content.
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
    source_rate = "60000/1001"
    video_filter = (
        "[0:v]"
        "drawbox=x=12:y=12:w=138:h=12:color=yellow:t=fill,"
        "drawbox=x=12:y=12:w=12:h=138:color=yellow:t=fill,"
        "drawbox=x=iw-150:y=12:w=138:h=12:color=yellow:t=fill,"
        "drawbox=x=iw-24:y=12:w=12:h=138:color=yellow:t=fill,"
        "drawbox=x=12:y=ih-24:w=138:h=12:color=yellow:t=fill,"
        "drawbox=x=12:y=ih-150:w=12:h=138:color=yellow:t=fill,"
        "drawbox=x=iw-150:y=ih-24:w=138:h=12:color=yellow:t=fill,"
        "drawbox=x=iw-24:y=ih-150:w=12:h=138:color=yellow:t=fill,"
        "drawbox=x=0:y=884:w=iw:h=6:color=0x80c8ff:t=fill,"
        "drawbox=x=0:y=884:w=iw:h=6:color=red:t=fill:"
        "enable='lt(mod(t\\,2)\\,0.075)',"
        "drawtext=text='OpenSageTV Vibe':fontcolor=white:fontsize=44:x=180:y=48,"
        "drawtext=text='SERVER / PUSH A/V SYNC TEST':"
        "fontcolor=0x80c8ff:fontsize=46:x=180:y=104,"
        "drawtext=text='1080i29.97 MPEG-2 / 48 kHz stereo AC-3':"
        "fontcolor=white:fontsize=28:x=180:y=168,"
        "drawtext=text='BALL IMPACT + AUDIO CLICK':"
        "fontcolor=white:fontsize=38:x=w-text_w-180:y=52,"
        "drawtext=text='SHOULD COINCIDE':"
        "fontcolor=0x80c8ff:fontsize=34:x=w-text_w-180:y=108,"
        "drawtext=text='PTS %{pts\\:hms}   FRAME %{n}':"
        "fontcolor=white:fontsize=30:x=180:y=h-68:"
        "box=1:boxcolor=black@0.60:boxborderw=10[base];"
        "[1:v]format=rgba,"
        "geq=r='105':g='205':b='255':"
        "a='if(between((X-W/2)*(X-W/2)+(Y-H/2)*(Y-H/2),"
        "(W/2-18)*(W/2-18),(W/2-7)*(W/2-7)),255,0)'[ball];"
        "[base][ball]overlay=x='(W-w)/2':"
        # Match the embedded fixture's 200 ms impact hold followed by smooth
        # frame-by-frame travel for the remaining 1.8 seconds.
        # The 108 px ring's visible outer edge ends 101 px below its overlay
        # origin. An impact origin of 783 therefore touches line row 884.
        "y='783-623*abs(sin(PI*max(mod(t\\,2)-0.2\\,0)/1.8))':"
        "eval=frame,format=yuv420p,"
        "tinterlace=mode=interleave_top[v]"
    )
    # The quiet reference tone keeps HDMI/AVR gates awake. The 25 ms click at
    # every two seconds is generated from the same clock as the ball.
    audio_source = (
        f"aevalsrc=exprs='0.015*sin(2*PI*220*t)+if(lt(mod(t,2),0.025),"
        f"0.74*sin(2*PI*1000*t),0)':s=48000:d={duration:.6f}"
    )
    run([
        ffmpeg,
        "-hide_banner", "-loglevel", "error", "-y",
        "-f", "lavfi", "-i",
        f"color=c=0x07121f:s=1920x1080:r={source_rate}:d={duration:.6f}",
        "-f", "lavfi", "-i",
        f"color=c=white@0.0:s=108x108:r={source_rate}:d={duration:.6f}",
        "-f", "lavfi", "-i", audio_source,
        "-filter_complex", video_filter,
        "-map", "[v]", "-map", "2:a:0",
        "-c:v", "mpeg2video",
        "-b:v", "6400k", "-minrate", "6400k", "-maxrate", "6400k",
        "-bufsize", "1835k", "-flags", "+ildct+ilme", "-field_order", "tt",
        "-pix_fmt", "yuv420p", "-r", "30000/1001",
        "-g", "15", "-keyint_min", "15", "-sc_threshold", "0",
        "-c:a", "ac3", "-b:a", "384k", "-ar", "48000", "-ac", "2",
        "-metadata", "title=OpenSageTV Vibe PBS-profile A/V Sync Test",
        "-metadata:s:a:0", "language=eng",
        "-metadata:s:a:0", "title=48 kHz impact clicks",
        "-shortest", "-muxdelay", "0", "-muxpreload", "0",
        "-muxrate", "7000000", "-mpegts_flags", "+resend_headers",
        "-f", "mpegts", str(output),
    ])


def validate(ffprobe: str, output: Path, duration: float) -> dict[str, object]:
    result = run([
        ffprobe, "-v", "error", "-show_entries",
        "format=duration,format_name,bit_rate:"
        "stream=index,codec_type,codec_name,width,height,avg_frame_rate,"
        "field_order,sample_rate,channels,bit_rate,start_time",
        "-of", "json", str(output),
    ], capture=True)
    probe = json.loads(result.stdout)
    streams = probe.get("streams", [])
    video = next((item for item in streams if item.get("codec_type") == "video"), None)
    audio = next((item for item in streams if item.get("codec_type") == "audio"), None)
    if not video or video.get("codec_name") != "mpeg2video":
        raise RuntimeError("fixture does not contain MPEG-2 video")
    if (video.get("width"), video.get("height")) != (1920, 1080):
        raise RuntimeError("fixture video is not 1920x1080")
    if video.get("avg_frame_rate") != "30000/1001":
        raise RuntimeError(f"fixture video is not 29.97 fps: {video.get('avg_frame_rate')}")
    if video.get("field_order") not in {"tt", "tb", "top"}:
        raise RuntimeError(f"fixture is not top-field-first: {video.get('field_order')}")
    if not audio or audio.get("codec_name") != "ac3":
        raise RuntimeError("fixture does not contain AC-3 audio")
    if audio.get("sample_rate") != "48000" or audio.get("channels") != 2:
        raise RuntimeError("fixture audio is not 48 kHz stereo")
    actual_duration = float(probe.get("format", {}).get("duration", 0.0))
    if actual_duration < duration - 0.25:
        raise RuntimeError(
            f"fixture duration {actual_duration:.3f}s is shorter than requested {duration:.3f}s"
        )
    return {
        "path": str(output),
        "bytes": output.stat().st_size,
        "sha256": hashlib.sha256(output.read_bytes()).hexdigest(),
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
        "profile": "pbs-1080i-mpeg2-ac3",
        "format": probe.get("format", {}),
        "video": video,
        "audio": audio,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, required=True)
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
    print(f"PASS: generated PBS-profile server A/V sync fixture: {output}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (FileNotFoundError, RuntimeError, subprocess.CalledProcessError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        raise SystemExit(1)
