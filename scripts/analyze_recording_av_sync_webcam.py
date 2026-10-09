#!/usr/bin/env python3
"""Estimate physical A/V delay by matching a webcam recording to its source.

This complements the authored impact/click fixture. It is useful for speech,
but its result is diagnostic: an independent pulse fixture remains the timing
calibration gate. Both matches use the same recorded webcam clock.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import subprocess

import numpy as np
from scipy import signal


FPS = 5
WIDTH = 96
HEIGHT = 54
SAMPLE_RATE = 8000


def decode(ffmpeg: Path, source: Path, *, start: float | None, duration: float,
           kind: str, crop: str | None = None) -> np.ndarray:
    command = [str(ffmpeg), "-hide_banner", "-loglevel", "error", "-nostdin"]
    if start is not None:
        command += ["-ss", str(start)]
    command += ["-i", str(source), "-t", str(duration)]
    if kind == "audio":
        command += ["-map", "0:a:0", "-vn", "-ac", "1", "-ar", str(SAMPLE_RATE),
                    "-f", "f32le", "pipe:1"]
        dtype = np.float32
    else:
        filters = [f"fps={FPS}"]
        if crop:
            filters.append(f"crop={crop}")
        filters += [f"scale={WIDTH}:{HEIGHT}", "format=gray"]
        command += ["-map", "0:v:0", "-an", "-vf",
                    ",".join(filters),
                    "-f", "rawvideo", "pipe:1"]
        dtype = np.uint8
    result = subprocess.run(command, capture_output=True, check=False)
    if result.returncode:
        raise RuntimeError(result.stderr.decode(errors="replace"))
    samples = np.frombuffer(result.stdout, dtype=dtype)
    if kind == "video":
        pixels = WIDTH * HEIGHT
        samples = samples[:len(samples) // pixels * pixels].reshape(-1, pixels)
    return samples


def audio_source_position(source: np.ndarray, camera: np.ndarray) -> tuple[float, float]:
    # Band-limit both streams to speech and normalize sliding source energy.
    sos = signal.butter(3, [300, 3000], btype="bandpass", fs=SAMPLE_RATE,
                        output="sos")
    source = signal.sosfilt(sos, source).astype(np.float32)
    camera = signal.sosfilt(sos, camera).astype(np.float32)
    source -= source.mean()
    camera -= camera.mean()
    if len(source) < len(camera) or len(camera) < SAMPLE_RATE * 3:
        raise ValueError("Audio excerpts are too short to correlate")
    scores = signal.fftconvolve(source, camera[::-1], mode="valid")
    source_power = np.concatenate(([0.0], np.cumsum(source.astype(np.float64) ** 2)))
    window_power = source_power[len(camera):] - source_power[:-len(camera)]
    denominator = np.sqrt(np.maximum(window_power, 1e-10) *
                          max(float(np.dot(camera, camera)), 1e-10))
    normalized = scores / denominator
    best = int(np.argmax(normalized))
    return best / SAMPLE_RATE, float(normalized[best])


def video_source_position(source: np.ndarray, camera: np.ndarray) -> tuple[float, float, float]:
    if len(source) < len(camera) or len(camera) < FPS * 3:
        raise ValueError("Video excerpts are too short to correlate")
    # Normalize each frame to tolerate different camera exposure and TV level.
    def unit_frames(frames: np.ndarray) -> np.ndarray:
        frames = frames.astype(np.float32)
        frames -= frames.mean(axis=1, keepdims=True)
        frames /= np.maximum(np.linalg.norm(frames, axis=1, keepdims=True), 1e-6)
        return frames

    spatial_source = unit_frames(source)
    spatial_camera = unit_frames(camera)
    # A talking-head shot has a nearly static background. Match changes across
    # time as well as spatial appearance so the background cannot falsely
    # select an arbitrary point several seconds away.
    motion_source = source.astype(np.float32)
    motion_camera = camera.astype(np.float32)
    motion_camera -= motion_camera.mean(axis=0, keepdims=True)
    camera_norm = max(float(np.linalg.norm(motion_camera)), 1e-6)
    spatial = np.empty(len(source) - len(camera) + 1, dtype=np.float32)
    motion = np.empty_like(spatial)
    for position in range(len(spatial)):
        window = motion_source[position:position + len(camera)]
        centered = window - window.mean(axis=0, keepdims=True)
        motion[position] = (np.einsum("ij,ij->", centered, motion_camera) /
                            max(float(np.linalg.norm(centered)) * camera_norm, 1e-6))
        spatial[position] = np.einsum(
            "ij,ij->", spatial_source[position:position + len(camera)],
            spatial_camera) / len(camera)
    best = int(np.argmax(motion))
    return best / FPS, float(spatial[best]), float(motion[best])


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path, help="Original SageTV recording")
    parser.add_argument("camera", type=Path, help="C920 video+mic Matroska capture")
    parser.add_argument("--source-start-s", type=float, required=True)
    parser.add_argument("--source-duration-s", type=float, default=120)
    parser.add_argument("--camera-duration-s", type=float, default=20)
    parser.add_argument("--camera-crop", help="Optional FFmpeg crop W:H:X:Y removing visible TV bezel")
    parser.add_argument("--ffmpeg", type=Path, default=Path(__file__).resolve().parents[2] /
                        "opensagetv-vibe-ffmpeg-mim/output/windows-x64/ffmpeg.real.exe")
    parser.add_argument("--json-output", type=Path)
    args = parser.parse_args()
    if args.source_duration_s <= args.camera_duration_s + 5:
        parser.error("Source search window must exceed the camera duration by five seconds")
    if not args.ffmpeg.is_file():
        parser.error(f"FFmpeg not found: {args.ffmpeg}")

    source_audio = decode(args.ffmpeg, args.source, start=args.source_start_s,
                          duration=args.source_duration_s, kind="audio")
    camera_audio = decode(args.ffmpeg, args.camera, start=None,
                          duration=args.camera_duration_s, kind="audio")
    source_video = decode(args.ffmpeg, args.source, start=args.source_start_s,
                          duration=args.source_duration_s, kind="video")
    camera_video = decode(args.ffmpeg, args.camera, start=None,
                          duration=args.camera_duration_s, kind="video",
                          crop=args.camera_crop)
    audio_position, audio_score = audio_source_position(source_audio, camera_audio)
    video_position, video_score, motion_score = video_source_position(source_video, camera_video)
    # A positive value means room audio arrives after the matching screen image.
    physical_delay_ms = round((video_position - audio_position) * 1000, 1)
    report = {
        "source": str(args.source), "camera": str(args.camera),
        "sourceStartSeconds": args.source_start_s,
        "cameraCrop": args.camera_crop,
        "cameraAudioMatchesSourceSeconds": round(args.source_start_s + audio_position, 3),
        "cameraVideoMatchesSourceSeconds": round(args.source_start_s + video_position, 3),
        "audioCorrelation": round(audio_score, 4),
        "videoCorrelation": round(video_score, 4),
        "videoTemporalCorrelation": round(motion_score, 4),
        "audioMinusVideoMilliseconds": physical_delay_ms,
        "timeResolutionMilliseconds": 1000 / FPS,
        "status": "diagnostic_only_speech_match",
        "note": "Video is sampled at 5 fps; use the pulse fixture for calibrated timing.",
    }
    encoded = json.dumps(report, indent=2)
    if args.json_output:
        args.json_output.parent.mkdir(parents=True, exist_ok=True)
        args.json_output.write_text(encoded + "\n", encoding="utf-8")
    print(encoded)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
