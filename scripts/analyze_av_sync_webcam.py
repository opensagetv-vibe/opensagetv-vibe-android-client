#!/usr/bin/env python3
"""Measure ball-impact versus click timing in a physical webcam capture.

The input must contain one of Vibe's deterministic A/V-sync fixtures. Video is
scaled to a small grayscale analysis plane; a temporal background model removes
the static line and labels, leaving the center-column moving ring. Audio is
decoded to mono PCM and a 1 kHz detector locates each generated click. Stream
start timestamps keep both measurements on the Matroska capture clock.
"""

from __future__ import annotations

import argparse
from fractions import Fraction
import json
import math
import os
from pathlib import Path
import shutil
import subprocess
import sys

import numpy as np


PROJECT_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_WINDOWS_FFMPEG = (
    PROJECT_ROOT.parent
    / "opensagetv-vibe-ffmpeg-mim"
    / "output"
    / "windows-x64"
    / "ffmpeg.real.exe"
)
DEFAULT_WINDOWS_FFPROBE = DEFAULT_WINDOWS_FFMPEG.with_name("ffprobe.exe")
ANALYSIS_WIDTH = 320
ANALYSIS_HEIGHT = 180
AUDIO_RATE = 48_000
AUDIO_BLOCK = 480  # 10 ms
REGISTRATION_SAMPLE_FPS = 1
REGISTRATION_MIN_PIXELS = 6
PAIR_TOLERANCE_SECONDS = 0.95  # strictly below half the two-second fixture period


def executable(name: str, override: str, workspace_default: Path | None = None) -> str:
    if override:
        path = Path(override).expanduser().resolve()
        if path.is_file():
            return str(path)
        raise FileNotFoundError(f"{name} was not found: {path}")
    found = shutil.which(name)
    if found:
        return found
    if workspace_default is not None and workspace_default.is_file():
        return str(workspace_default.resolve())
    raise FileNotFoundError(f"{name} was not found in PATH")


def run_bytes(command: list[str]) -> bytes:
    result = subprocess.run(command, check=True, stdout=subprocess.PIPE)
    return result.stdout


def probe(ffprobe: str, capture: Path) -> tuple[dict, dict]:
    result = subprocess.run(
        [
            ffprobe, "-v", "error", "-show_entries",
            "stream=index,codec_type,start_time,avg_frame_rate,sample_rate,width,height",
            "-of", "json", str(capture),
        ],
        check=True,
        text=True,
        stdout=subprocess.PIPE,
    )
    streams = json.loads(result.stdout).get("streams", [])
    video = next((item for item in streams if item.get("codec_type") == "video"), None)
    audio = next((item for item in streams if item.get("codec_type") == "audio"), None)
    if not video:
        raise RuntimeError("capture does not contain video")
    if not audio:
        raise RuntimeError(
            "capture does not contain audio; --video-only is a clarity preflight "
            "and cannot be analyzed for A/V offset"
        )
    return video, audio


def framing_registration(ffmpeg: str, capture: Path,
                         capture_mode: str = "camera") -> dict[str, object]:
    """Find the fixture's four yellow camera-registration corners.

    Those corners are deliberately the only yellow objects in both generated
    fixtures.  Looking for a stable yellow cluster in every image quadrant
    proves that the webcam sees the complete authored frame; timing can still
    be measured without this proof, but that recording is diagnostic-only.
    """
    # The direct C920 graph reapplies its saved zoom when it opens. The capture
    # helper resets it again after one second, so early frames show a transient
    # crop even though the settled recording contains the complete TV. Do not
    # let those pre-reset frames contaminate the maximum-pixel registration.
    settle_seek = ["-ss", "2"] if capture_mode == "camera" else []
    raw = run_bytes([
        ffmpeg, "-hide_banner", "-loglevel", "error", *settle_seek,
        "-i", str(capture),
        "-map", "0:v:0", "-vf",
        (
            f"fps={REGISTRATION_SAMPLE_FPS},"
            f"scale={ANALYSIS_WIDTH}:{ANALYSIS_HEIGHT}:flags=fast_bilinear,"
            "format=rgb24"
        ),
        "-frames:v", "8", "-f", "rawvideo", "-pix_fmt", "rgb24", "-",
    ])
    frame_size = ANALYSIS_WIDTH * ANALYSIS_HEIGHT * 3
    usable = len(raw) - (len(raw) % frame_size)
    frames = np.frombuffer(raw[:usable], dtype=np.uint8).reshape(
        -1, ANALYSIS_HEIGHT, ANALYSIS_WIDTH, 3
    )
    if not len(frames):
        raise RuntimeError("capture contains no frames for registration check")

    # Authored corner marks are static. A temporal median retains them while
    # rejecting brief pre-reset crops, highlights, and moving bright objects;
    # a per-pixel maximum can synthesize false L arms at the camera boundary.
    image = np.median(frames, axis=0).astype(np.uint8)
    red = image[:, :, 0].astype(np.int16)
    green = image[:, :, 1].astype(np.int16)
    blue = image[:, :, 2].astype(np.int16)
    yellow = (
        (red >= 135)
        & (green >= 135)
        & (blue <= 125)
        & ((red + green) >= (2 * blue + 100))
        & (np.abs(red - green) <= 105)
    )
    # The C920 often washes the yellow marks almost to white, but the marks
    # retain a small warm bias. Prefer that bias so a bright neutral TV bezel
    # or room reflection cannot masquerade as a corner at the camera edge.
    warm_white = (
        (red >= 170) & (green >= 165)
        & ((red + green - 2 * blue) >= 20)
        & (np.abs(red - green) <= 80)
    )
    warm_registration = yellow | warm_white
    warm_counts = corner_arm_counts(
        warm_registration,
        minimum_inset=3 if capture_mode == "camera" else 0)
    warm_visible, *_ = registration_visibility(
        warm_counts, capture_mode, washed_warm_camera=True)
    # Retain the neutral fallback for a camera that truly clips all color
    # channels to white. Its bezel-clearance gate remains mandatory.
    bright_neutral = (red >= 150) & (green >= 150) & (blue >= 145)
    registration = warm_registration | bright_neutral
    yellow_counts = corner_arm_counts(yellow)
    neutral_counts = corner_arm_counts(bright_neutral)
    counts = warm_counts if all(warm_visible.values()) else corner_arm_counts(registration)
    visible, horizontal_requirement, vertical_requirement, clearance_x, clearance_y = (
        registration_visibility(
            counts, capture_mode,
            washed_warm_camera=all(warm_visible.values()))
    )
    return {
        "allFourYellowCornersVisible": all(visible.values()),
        "cornerVisible": visible,
        "registrationPixelCounts": counts,
        "yellowPixelCounts": yellow_counts,
        "warmPixelCounts": warm_counts,
        "neutralPixelCounts": neutral_counts,
        "registrationMethod": "warm" if all(warm_visible.values()) else "neutral_fallback",
        "yellowMayAppearWhiteAfterCameraExposure": True,
        "minimumHorizontalArmPixels": horizontal_requirement,
        "minimumVerticalArmPixels": vertical_requirement,
        "minimumAnchorClearancePixels": {"x": clearance_x, "y": clearance_y},
        "captureMode": capture_mode,
        "purpose": ("prove the direct HDMI raster contains the four authored corners"
                    if capture_mode == "hdmi" else
                    "prove the webcam sees the complete authored test frame"),
    }


def registration_visibility(counts: dict[str, dict[str, int]],
                            capture_mode: str, *,
                            washed_warm_camera: bool = False
                            ) -> tuple[dict[str, bool], int, int, int, int]:
    """Require full authored corners, with bezel clearance only for a camera."""
    if capture_mode not in {"camera", "hdmi"}:
        raise ValueError(f"unsupported capture mode: {capture_mode}")
    horizontal_requirement = max(REGISTRATION_MIN_PIXELS,
                                 round(ANALYSIS_WIDTH * 0.045 * 0.7))
    if capture_mode == "camera" and washed_warm_camera:
        # A bright room can wash several pixels of one authored arm to neutral
        # white. Four inset, color-biased L marks are still stronger evidence
        # than four full-length neutral marks at the physical camera boundary.
        horizontal_requirement = max(REGISTRATION_MIN_PIXELS,
                                     round(ANALYSIS_WIDTH * 0.045 * 0.55))
    vertical_requirement = max(REGISTRATION_MIN_PIXELS,
                               round(ANALYSIS_HEIGHT * 0.07 * 0.7))
    if capture_mode == "camera" and washed_warm_camera:
        vertical_requirement = REGISTRATION_MIN_PIXELS
    clearance_x = max(2, round(ANALYSIS_WIDTH * 0.01))
    clearance_y = max(2, round(ANALYSIS_HEIGHT * 0.015))
    visible = {
        name: values["horizontal"] >= horizontal_requirement
        and values["vertical"] >= vertical_requirement
        and (capture_mode == "hdmi"
             or (clearance_x <= values["anchorX"] < ANALYSIS_WIDTH - clearance_x
                 and clearance_y <= values["anchorY"] < ANALYSIS_HEIGHT - clearance_y))
        for name, values in counts.items()
    }
    return visible, horizontal_requirement, vertical_requirement, clearance_x, clearance_y


def corner_arm_counts(registration: np.ndarray, *,
                      minimum_inset: int = 0) -> dict[str, dict[str, int]]:
    """Return strongest intersecting L arms for each oriented frame corner."""
    height, width = registration.shape
    horizontal_length = max(8, round(width * 0.045))
    vertical_length = max(8, round(height * 0.07))
    outer_x = round(width * 0.09)
    outer_y = round(height * 0.14)
    counts: dict[str, dict[str, int]] = {}
    for name, oriented in (
        ("topLeft", registration),
        ("topRight", np.fliplr(registration)),
        ("bottomLeft", np.flipud(registration)),
        ("bottomRight", np.flipud(np.fliplr(registration))),
    ):
        best = {"horizontal": 0, "vertical": 0, "anchorX": 0, "anchorY": 0}
        best_score = -1
        for y in range(minimum_inset, outer_y):
            for x in range(minimum_inset, outer_x):
                horizontal = int(oriented[max(0, y - 1):y + 2,
                                          x:x + horizontal_length].any(axis=0).sum())
                vertical = int(oriented[y:y + vertical_length,
                                        max(0, x - 1):x + 2].any(axis=1).sum())
                score = min(horizontal / horizontal_length,
                            vertical / vertical_length)
                if score > best_score:
                    best_score = score
                    best = {"horizontal": horizontal, "vertical": vertical,
                            "anchorX": width - 1 - x if "Right" in name else x,
                            "anchorY": height - 1 - y if "bottom" in name else y}
        counts[name] = best
    return counts


def groups(indices: np.ndarray, maximum_gap: int = 1) -> list[np.ndarray]:
    if not len(indices):
        return []
    boundaries = np.where(np.diff(indices) > maximum_gap)[0] + 1
    return [part for part in np.split(indices, boundaries) if len(part)]


def video_impacts(ffmpeg: str, capture: Path, stream: dict,
                  debug: bool = False) -> list[float]:
    raw = run_bytes([
        ffmpeg, "-hide_banner", "-loglevel", "error", "-i", str(capture),
        "-map", "0:v:0", "-vf",
        f"scale={ANALYSIS_WIDTH}:{ANALYSIS_HEIGHT}:flags=fast_bilinear,format=gray",
        "-f", "rawvideo", "-pix_fmt", "gray", "-",
    ])
    frame_size = ANALYSIS_WIDTH * ANALYSIS_HEIGHT
    usable = len(raw) - (len(raw) % frame_size)
    frames = np.frombuffer(raw[:usable], dtype=np.uint8).reshape(
        -1, ANALYSIS_HEIGHT, ANALYSIS_WIDTH
    )
    if len(frames) < 60:
        raise RuntimeError("capture is too short for reliable video-impact analysis")

    # The generator deliberately keeps labels out of this central column. A
    # low temporal percentile models the static background/impact line while
    # retaining the bright ring at each stepped position as positive residual.
    x0, x1 = int(ANALYSIS_WIDTH * 0.32), int(ANALYSIS_WIDTH * 0.68)
    y0, y1 = int(ANALYSIS_HEIGHT * 0.08), int(ANALYSIS_HEIGHT * 0.88)
    roi = frames[:, y0:y1, x0:x1].astype(np.float32)
    background = np.percentile(roi, 10, axis=0)
    residual = np.maximum(roi - background[None, :, :], 0.0)
    residual[residual < 18.0] = 0.0
    row_energy = residual.sum(axis=2)
    total_energy = row_energy.sum(axis=1)
    rows = np.arange(row_energy.shape[1], dtype=np.float32)
    centroid = (row_energy * rows[None, :]).sum(axis=1) / np.maximum(total_energy, 1.0)

    # At impact the lower half of the ring overlaps the static line, so its
    # residual energy is lower than the unobstructed in-flight ring. Use a low
    # fraction of the proven dynamic maximum, then select by vertical position.
    confident = total_energy > max(float(total_energy.max()) * 0.01, 1.0)
    if int(confident.sum()) < 20:
        raise RuntimeError("unable to isolate the fixture's center-column ball")
    bottom = float(np.percentile(centroid[confident], 98))
    # Smooth motion passes through the few rows above the line on approach.
    # Restrict an impact to the stable bottom plateau itself; a wider band
    # would report the last in-flight frame roughly 40-60 ms too early.
    # Camera exposure, interlaced display output, and the C920's 30 fps sample
    # phase move the measured ring centroid by a few tenths of an analysis
    # pixel while it is stationary on the line.  A 0.35-pixel plateau band is
    # still far narrower than one in-flight frame, but avoids dropping an
    # entire two-second event because only one held frame equals the absolute
    # percentile maximum.
    candidates = np.where(confident & (centroid >= bottom - 0.35))[0]
    if debug:
        print(
            f"DEBUG video bottomRow={bottom:.3f}",
            file=sys.stderr,
        )
        for index in range(min(len(frames), int(2.0 * float(Fraction(str(stream.get('avg_frame_rate') or '0/1')))))):
            if index % max(1, round(float(Fraction(str(stream.get('avg_frame_rate') or '0/1'))) / 10.0)) == 0:
                print(
                    f"DEBUG frame={index} centroid={centroid[index]:.3f} "
                    f"totalEnergy={total_energy[index]:.3f}",
                    file=sys.stderr,
                )
        for part in groups(candidates, maximum_gap=2)[:3]:
            first = max(0, int(part[0]) - 4)
            last = min(len(centroid), int(part[-1]) + 5)
            detail = ",".join(
                f"{index}:{centroid[index]:.2f}" for index in range(first, last)
            )
            print(f"DEBUG impact-run {detail}", file=sys.stderr)

    rate_text = str(stream.get("avg_frame_rate") or "0/1")
    rate = float(Fraction(rate_text))
    if not math.isfinite(rate) or rate <= 0:
        raise RuntimeError(f"invalid video frame rate: {rate_text}")
    start = float(stream.get("start_time") or 0.0)
    impacts = []
    for part in groups(candidates, maximum_gap=2):
        # Compression and the fixed impact line can make only the upper edge of
        # the ring exceed the temporal threshold. Two consecutive frames are
        # still required, rejecting a one-frame flash/noise false positive.
        if len(part) < 2:
            continue
        # The click occurs when the ring first reaches the line; the remaining
        # frames are the intentional 200 ms clarity hold, not impact time.
        timestamp = start + float(part[0]) / rate
        if not impacts or timestamp - impacts[-1] > 0.55:
            impacts.append(timestamp)
    if not impacts:
        run_lengths = [len(part) for part in groups(candidates, maximum_gap=2)]
        raise RuntimeError(
            "unable to locate stable ball-impact holds: "
            f"bottomRow={bottom:.2f} candidates={len(candidates)} "
            f"runs={run_lengths[:12]}"
        )
    return impacts


def audio_clicks(ffmpeg: str, capture: Path, stream: dict) -> list[float]:
    raw = run_bytes([
        ffmpeg, "-hide_banner", "-loglevel", "error", "-i", str(capture),
        "-map", "0:a:0", "-ac", "1", "-ar", str(AUDIO_RATE),
        "-f", "f32le", "-acodec", "pcm_f32le", "-",
    ])
    samples = np.frombuffer(raw, dtype="<f4")
    usable = len(samples) - (len(samples) % AUDIO_BLOCK)
    blocks = samples[:usable].reshape(-1, AUDIO_BLOCK)
    if len(blocks) < 200:
        raise RuntimeError("capture is too short for reliable click analysis")
    window = np.hanning(AUDIO_BLOCK).astype(np.float32)
    phase = np.exp(
        -2j * np.pi * 1000.0 * np.arange(AUDIO_BLOCK) / AUDIO_RATE
    ).astype(np.complex64)
    strength = np.abs((blocks * window[None, :]) @ phase)
    median = float(np.median(strength))
    mad = float(np.median(np.abs(strength - median)))
    threshold = max(median + 8.0 * max(mad, 1e-7), float(strength.max()) * 0.18)
    candidates = np.where(strength >= threshold)[0]
    start = float(stream.get("start_time") or 0.0)
    clicks = []
    for part in groups(candidates, maximum_gap=2):
        peak = int(part[np.argmax(strength[part])])
        timestamp = start + (peak + 0.5) * AUDIO_BLOCK / AUDIO_RATE
        if not clicks or timestamp - clicks[-1] > 0.55:
            clicks.append(timestamp)
    return clicks


def pair_events(impacts: list[float], clicks: list[float],
                expected_offset_ms: float = 0.0) -> list[dict[str, float]]:
    pairs = []
    expected_seconds = float(expected_offset_ms) / 1000.0
    for impact in impacts:
        if not clicks:
            break
        expected_click = impact + expected_seconds
        click = min(clicks, key=lambda value: abs(value - expected_click))
        # External receivers and TVs can contribute well over 450 ms before a
        # user offset is applied.  The fixture repeats every two seconds, so a
        # tolerance just below half that period admits the physical baseline
        # without ever pairing an event from the adjacent cycle.
        if abs(click - expected_click) <= PAIR_TOLERANCE_SECONDS:
            pairs.append({
                "ballImpactSeconds": round(impact, 6),
                "audioClickSeconds": round(click, 6),
                # Positive means the heard click occurred after ball impact.
                "audioMinusVideoMilliseconds": round((click - impact) * 1000.0, 3),
            })
    return pairs


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("capture", type=Path)
    parser.add_argument(
        "--ffmpeg",
        default=os.environ.get("VIBE_FFMPEG_PATH", os.environ.get("FFMPEG", "")),
    )
    parser.add_argument(
        "--ffprobe",
        default=os.environ.get("VIBE_FFPROBE_PATH", os.environ.get("FFPROBE", "")),
    )
    parser.add_argument("--json-output", type=Path)
    parser.add_argument(
        "--expected-offset-ms",
        type=float,
        default=0.0,
        help=(
            "Applied/expected signed offset; positive means audio later. "
            "Used with the baseline to disambiguate the two-second pattern."
        ),
    )
    parser.add_argument(
        "--baseline-offset-ms",
        type=float,
        default=0.0,
        help=(
            "Raw audio-minus-video delay measured on the same physical route "
            "with the player offset at zero. Pairing expects baseline plus the "
            "selected player offset, and the report subtracts this baseline."
        ),
    )
    parser.add_argument("--debug", action="store_true")
    parser.add_argument(
        "--capture-mode", choices=("camera", "hdmi"), default="camera",
        help="HDMI accepts authored corners at the raster edge; camera requires visible bezel clearance",
    )
    parser.add_argument(
        "--diagnostic-only-allow-cropped",
        action="store_true",
        help=(
            "Report timing even when all four yellow registration corners are "
            "not visible. The result is diagnostic-only and cannot close a "
            "physical A/V-sync gate."
        ),
    )
    args = parser.parse_args()
    capture = args.capture.expanduser().resolve()
    if not capture.is_file():
        parser.error(f"capture was not found: {capture}")
    ffmpeg = executable("ffmpeg", args.ffmpeg, DEFAULT_WINDOWS_FFMPEG)
    ffprobe = executable("ffprobe", args.ffprobe, DEFAULT_WINDOWS_FFPROBE)
    video, audio = probe(ffprobe, capture)
    if args.capture_mode == "hdmi" and (
            int(video.get("width", 0)) != 1920
            or int(video.get("height", 0)) != 1080):
        raise RuntimeError("direct HDMI physical gate requires full 1920x1080 capture")
    registration = framing_registration(ffmpeg, capture, args.capture_mode)
    if (not registration["allFourYellowCornersVisible"]
            and not args.diagnostic_only_allow_cropped):
        raise RuntimeError(
            "capture framing is incomplete: all four yellow registration "
            "corners must be visible; use --diagnostic-only-allow-cropped "
            "only for a non-closing timing diagnostic"
        )
    impacts = video_impacts(ffmpeg, capture, video, debug=args.debug)
    clicks = audio_clicks(ffmpeg, capture, audio)
    expected_measured_offset_ms = (
        float(args.baseline_offset_ms) + float(args.expected_offset_ms)
    )
    pairs = pair_events(impacts, clicks, expected_measured_offset_ms)
    if len(pairs) < 3:
        raise RuntimeError(
            f"insufficient matched events: impacts={len(impacts)} clicks={len(clicks)} "
            f"pairs={len(pairs)}"
        )
    offsets = np.asarray(
        [item["audioMinusVideoMilliseconds"] for item in pairs], dtype=np.float64
    )
    payload = {
        "capture": str(capture),
        "eventPairs": pairs,
        "pairCount": len(pairs),
        "medianAudioMinusVideoMilliseconds": round(float(np.median(offsets)), 3),
        "meanAudioMinusVideoMilliseconds": round(float(np.mean(offsets)), 3),
        "standardDeviationMilliseconds": round(float(np.std(offsets)), 3),
        "physicalBaselineMilliseconds": float(args.baseline_offset_ms),
        "selectedPlayerOffsetMilliseconds": float(args.expected_offset_ms),
        "expectedMeasuredOffsetMilliseconds": expected_measured_offset_ms,
        "medianBaselineCorrectedMilliseconds": round(
            float(np.median(offsets)) - float(args.baseline_offset_ms), 3
        ),
        "medianResidualFromExpectedMilliseconds": round(
            float(np.median(offsets)) - expected_measured_offset_ms, 3
        ),
        "signConvention": "positive means audio click occurred after ball impact",
        "cameraResolutionLimitMilliseconds": 33.4,
        "framingRegistration": registration,
        "captureMode": args.capture_mode,
        "physicalGateEligible": bool(
            registration["allFourYellowCornersVisible"]
        ),
    }
    if args.json_output:
        output = args.json_output.expanduser().resolve()
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(payload, indent=2))
    if payload["physicalGateEligible"]:
        print("PASS: measured physical A/V-sync fixture capture")
    else:
        print("DIAGNOSTIC-ONLY: timing measured, but the four-corner framing gate failed")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (FileNotFoundError, RuntimeError, subprocess.CalledProcessError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        raise SystemExit(1)
