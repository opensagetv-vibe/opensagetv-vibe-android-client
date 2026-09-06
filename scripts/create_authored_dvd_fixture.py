#!/usr/bin/env python3
"""Build a deterministic authored NTSC DVD-Video fixture for SageTV testing.

The generated disc is designed for automated Android MiniClient / MiniDVDPlayer
validation.  It contains:

* a continuously looping root menu and multiple submenus with explicit navigation;
* a main feature and a short special-feature title;
* two AC-3 language tracks with distinct tones and whole-second sync pulses;
* two selectable DVD SPU subtitle languages generated from one cue clock;
* chapters at known authored timestamps;
* burned-in PTS, frame, chapter, title and sync-pulse information;
* a detailed JSON manifest and Codex test plan covering every menu button,
  stream, chapter, subtitle cue and screenshot checkpoint.

Required build commands: ffmpeg, ffprobe, dvdauthor, spumux, and ImageMagick
(`magick` or `convert`).  `fc-match` and `spuunmux` are optional.

The defaults are relative to the project root, not the caller's current working
directory.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import shlex
import shutil
import subprocess
import sys
import textwrap
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Iterable, Sequence
from xml.etree import ElementTree
from xml.sax.saxutils import quoteattr


SCRIPT_DIR = Path(__file__).resolve().parent
PROJECT_ROOT = SCRIPT_DIR.parent

WIDTH = 720
HEIGHT = 480
FPS_NUM = 30_000
FPS_DEN = 1_001
FPS = f"{FPS_NUM}/{FPS_DEN}"
FPS_DECIMAL = FPS_NUM / FPS_DEN
SOURCE_FPS = f"{FPS_NUM * 2}/{FPS_DEN}"
TIMEBASE_HZ = 90_000
FIELD_ORDER = "top-field-first"
DISPLAY_ASPECT = "16:9"

DEFAULT_MAIN_DURATION = 90
DEFAULT_SPECIAL_DURATION = 20
DEFAULT_CUE_INTERVAL = 2.0
DEFAULT_CUE_DURATION = 1.55
DEFAULT_MENU_VIDEO_DURATION = 8.0
SYNC_PULSE_DURATION = 0.12
MAIN_CHAPTERS = (0.0, 20.0, 40.0, 60.0)
SPECIAL_CHAPTERS = (0.0, 10.0)

FIXTURE_ID = "vibe-authored-dvd-v2"
MANIFEST_NAME = "VIBE_DVD_TEST_MANIFEST.json"
TEST_CASES_NAME = "CODEX_DVD_TEST_CASES.json"
TEST_PLAN_NAME = "CODEX_DVD_TEST_PLAN.md"
CODEX_PROMPT_NAME = "CODEX_DVD_TEST_PROMPT.md"
HARNESS_CONTRACT_NAME = "CODEX_DVD_HARNESS_CONTRACT.json"
RESULT_SCHEMA_NAME = "CODEX_DVD_TEST_RESULTS.schema.json"
BUILD_REPORT_NAME = "BUILD_REPORT.json"
CHECKSUM_NAME = "VIDEO_TS_SHA256SUMS.txt"
METADATA_DIR_NAME = "TEST_METADATA"
WORK_CONFIG_NAME = "VIBE_DVD_WORK_CONFIG.json"
WORK_ASSET_SCHEMA_VERSION = 3

COMMAND_LOG: list[dict[str, Any]] = []


@dataclass
class Rect:
    """Spumux coordinates use an exclusive lower-right corner."""

    x0: int
    y0: int
    x1: int
    y1: int

    def authored(self) -> dict[str, int]:
        return {
            "x0Inclusive": self.x0,
            "y0Inclusive": self.y0,
            "x1Exclusive": self.x1,
            "y1Exclusive": self.y1,
        }

    def legacy_spuctrl(self) -> dict[str, int]:
        return {
            "xStartInclusive": self.x0,
            "xEndInclusive": self.x1 - 1,
            "yStartInclusive": self.y0,
            "yEndInclusive": self.y1 - 1,
        }


@dataclass
class ButtonSpec:
    id: str
    label: str
    vm_command: str
    expected: dict[str, Any]
    rect: Rect | None = None
    up: str = ""
    down: str = ""
    left: str = ""
    right: str = ""


@dataclass
class MenuSpec:
    id: str
    number: int
    heading: str
    tone_hz: int
    entry: str | None
    buttons: list[ButtonSpec]
    pre_command: str = "button=1024;"
    video_file: Path | None = None
    image_file: Path | None = None
    highlight_file: Path | None = None
    select_file: Path | None = None
    spu_xml_file: Path | None = None


@dataclass(frozen=True)
class AudioSpec:
    index: int
    language2: str
    language3: str
    label: str
    tone_hz: int
    physical_substream: int

    @property
    def legacy_selector(self) -> int:
        return (0xBD << 8) | self.physical_substream


@dataclass(frozen=True)
class SubtitleSpec:
    index: int
    language2: str
    language3: str
    label: str
    fill_color: str
    physical_substream: int


AUDIO_STREAMS = (
    AudioSpec(0, "en", "eng", "English AC-3", 1000, 0x80),
    AudioSpec(1, "es", "spa", "Spanish AC-3", 440, 0x81),
)

SUBTITLE_STREAMS = (
    SubtitleSpec(0, "en", "eng", "English DVD SPU", "white", 0x20),
    SubtitleSpec(1, "es", "spa", "Spanish DVD SPU", "cyan", 0x21),
)


def tool(name: str, *, required: bool = True) -> str | None:
    path = shutil.which(name)
    if path:
        return path
    if required:
        raise RuntimeError(f"required command is unavailable: {name}")
    return None


def format_command(command: Sequence[str]) -> str:
    if os.name == "nt":
        return subprocess.list2cmdline(list(command))
    return shlex.join(str(value) for value in command)


def run(
    command: Sequence[str],
    *,
    stdin: Path | None = None,
    stdout: Path | None = None,
    cwd: Path | None = None,
    capture: bool = False,
) -> subprocess.CompletedProcess[str] | subprocess.CompletedProcess[bytes]:
    rendered = format_command(command)
    print(f"+ {rendered}", flush=True)
    COMMAND_LOG.append({
        "command": [str(value) for value in command],
        "cwd": str(cwd) if cwd else None,
        "stdin": str(stdin) if stdin else None,
        "stdout": str(stdout) if stdout else None,
    })

    source = stdin.open("rb") if stdin else None
    target = stdout.open("wb") if stdout else None
    try:
        if capture:
            return subprocess.run(
                [str(value) for value in command],
                cwd=str(cwd) if cwd else None,
                stdin=source,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
                check=True,
            )
        return subprocess.run(
            [str(value) for value in command],
            cwd=str(cwd) if cwd else None,
            stdin=source,
            stdout=target,
            check=True,
        )
    finally:
        if source:
            source.close()
        if target:
            target.close()


def run_text(command: Sequence[str]) -> str:
    result = run(command, capture=True)
    assert isinstance(result.stdout, str)
    return result.stdout.strip()


def tool_version(path: str | None, *version_args: str) -> str | None:
    if not path:
        return None
    candidates = [version_args] if version_args else [("--version",), ("-version",), ("-V",)]
    for args in candidates:
        try:
            completed = subprocess.run(
                [path, *args],
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
                check=False,
                timeout=10,
            )
            text = completed.stdout.strip()
            if text:
                return text.splitlines()[0]
        except (OSError, subprocess.SubprocessError):
            continue
    return "version unavailable"


def write_json(path: Path, value: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, sort_keys=False) + "\n", encoding="utf-8")


def timestamp(seconds: float, *, comma: bool = False, hundredths: bool = False) -> str:
    milliseconds = max(0, round(seconds * 1000))
    hours, milliseconds = divmod(milliseconds, 3_600_000)
    minutes, milliseconds = divmod(milliseconds, 60_000)
    secs, milliseconds = divmod(milliseconds, 1000)
    if hundredths:
        return f"{hours:02d}:{minutes:02d}:{secs:02d}.{milliseconds // 10:02d}"
    separator = "," if comma else "."
    return f"{hours:02d}:{minutes:02d}:{secs:02d}{separator}{milliseconds:03d}"


def nearest_frame(seconds: float) -> int:
    return round(seconds * FPS_NUM / FPS_DEN)


def frame_seconds(frame: int) -> float:
    return frame * FPS_DEN / FPS_NUM


def current_chapter(seconds: float, chapters: Sequence[float]) -> int:
    result = 1
    for index, start in enumerate(chapters, 1):
        if seconds + 1e-9 >= start:
            result = index
        else:
            break
    return result


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def safe_remove_tree(path: Path) -> None:
    resolved = path.resolve()
    protected = {
        Path("/").resolve(),
        Path.home().resolve(),
        SCRIPT_DIR.resolve(),
        Path.cwd().resolve(),
    }
    if resolved in protected or len(resolved.parts) < 3:
        raise RuntimeError(f"refusing to recursively delete unsafe path: {resolved}")
    shutil.rmtree(resolved)


def prepare_destination(path: Path, *, overwrite: bool, create: bool = True) -> None:
    if path.exists():
        if not overwrite:
            raise RuntimeError(f"destination already exists; use --overwrite: {path}")
        safe_remove_tree(path)
    if create:
        path.mkdir(parents=True, exist_ok=True)


def path_is_within(path: Path, parent: Path) -> bool:
    try:
        path.relative_to(parent)
        return True
    except ValueError:
        return False


def validate_paths(output: Path, work: Path) -> None:
    """Reject layouts where cleanup of one tree can delete the other tree."""

    if output == work:
        raise ValueError("--output and --work must be different directories")
    if path_is_within(output, work):
        raise ValueError("--output may not be inside --work")
    if path_is_within(work, output):
        raise ValueError("--work may not be inside --output")


def resolve_font(requested: Path | None) -> Path:
    if requested:
        result = requested.expanduser().resolve()
        if not result.is_file():
            raise RuntimeError(f"font does not exist: {result}")
        return result

    fc_match = tool("fc-match", required=False)
    if fc_match:
        try:
            candidate = subprocess.run(
                [fc_match, "-f", "%{file}\n", "DejaVu Sans"],
                stdout=subprocess.PIPE,
                stderr=subprocess.DEVNULL,
                text=True,
                check=True,
                timeout=10,
            ).stdout.splitlines()[0]
            if candidate and Path(candidate).is_file():
                return Path(candidate).resolve()
        except (IndexError, OSError, subprocess.SubprocessError):
            pass

    for candidate in (
        Path("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"),
        Path("/usr/share/fonts/dejavu/DejaVuSans.ttf"),
        Path("/usr/local/share/fonts/DejaVuSans.ttf"),
    ):
        if candidate.is_file():
            return candidate.resolve()
    raise RuntimeError("unable to locate DejaVuSans.ttf; use --font")


def xml_validate(path: Path) -> None:
    try:
        ElementTree.parse(path)
    except ElementTree.ParseError as error:
        raise RuntimeError(f"invalid XML generated at {path}: {error}") from error


def ffmpeg_text(value: str) -> str:
    """Escape literal text for FFmpeg drawtext's text= option."""

    return (
        value.replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace(":", "\\:")
        .replace("%", "\\%")
    )


def assign_button_layout(menu: MenuSpec) -> None:
    x0 = 82
    x1 = 638
    button_height = 40
    gap = 8
    first_y = 100
    for index, button in enumerate(menu.buttons):
        y0 = first_y + index * (button_height + gap)
        if y0 & 1:
            y0 += 1
        y1 = y0 + button_height
        button.rect = Rect(x0=x0, y0=y0, x1=x1, y1=y1)

    for index, button in enumerate(menu.buttons):
        previous_button = menu.buttons[(index - 1) % len(menu.buttons)]
        next_button = menu.buttons[(index + 1) % len(menu.buttons)]
        button.up = previous_button.id
        button.down = next_button.id
        button.left = button.id
        button.right = button.id


def build_menu_specs(chapters: Sequence[float]) -> list[MenuSpec]:
    def play_with_current_selections(destination: str) -> str:
        # A menu domain exposes only its own single audio stream, so entering an
        # intermediate menu PGC can replace SPRM(1) before a title starts. Keep
        # the user's choices in GPRMs and apply them in the same button command
        # that jumps directly into the requested title/chapter.
        return (
            "audio=0; if (g1 eq 1) { audio=1; } "
            "subtitle=63; if (g2 eq 0) { subtitle=64; } "
            "if (g2 eq 1) { subtitle=65; } "
            f"{destination}"
        )

    root = MenuSpec(
        id="root",
        number=1,
        heading="OPENSAGETV VIBE DVD TEST",
        tone_hz=660,
        entry="root",
        pre_command=(
            "if (g0 eq 0) { g1=0; g2=63; g3=1; audio=0; subtitle=63; g0=1; } "
            "button=1024;"
        ),
        buttons=[
            ButtonSpec(
                "root_play_main",
                "Play Main Feature",
                play_with_current_selections("jump title 1 chapter 1;"),
                {"destination": {"type": "title", "titleId": "main_feature", "chapter": 1},
                 "selectionPolicy": "preserve-current-audio-and-subtitle"},
            ),
            ButtonSpec(
                "root_languages",
                "Languages",
                "jump menu 2;",
                {"destination": {"type": "menu", "menuId": "languages"}},
            ),
            ButtonSpec(
                "root_chapters",
                "Chapters",
                "jump menu 5;",
                {"destination": {"type": "menu", "menuId": "chapters"}},
            ),
            ButtonSpec(
                "root_special_features",
                "Special Features",
                "jump menu 6;",
                {"destination": {"type": "menu", "menuId": "special_features"}},
            ),
        ],
    )

    languages = MenuSpec(
        id="languages",
        number=2,
        heading="LANGUAGES AND SUBTITLES",
        tone_hz=720,
        entry="audio subtitle",
        buttons=[
            ButtonSpec(
                "languages_audio_tracks",
                "Audio Tracks",
                "jump menu 3;",
                {"destination": {"type": "menu", "menuId": "audio_tracks"}},
            ),
            ButtonSpec(
                "languages_subtitle_tracks",
                "Subtitle Tracks",
                "jump menu 4;",
                {"destination": {"type": "menu", "menuId": "subtitle_tracks"}},
            ),
            ButtonSpec(
                "languages_play_current",
                "Play with Current Selections",
                play_with_current_selections("jump title 1 chapter 1;"),
                {"destination": {"type": "title", "titleId": "main_feature", "chapter": 1},
                 "selectionPolicy": "preserve-current-audio-and-subtitle"},
            ),
            ButtonSpec(
                "languages_reset_defaults",
                "Reset English Audio / Subtitles Off",
                "g1=0; g2=63; jump menu 2;",
                {"destination": {"type": "menu", "menuId": "languages"},
                 "audioIndex": 0, "subtitleMode": "off"},
            ),
            ButtonSpec(
                "languages_main_menu",
                "Main Menu",
                "jump menu 1;",
                {"destination": {"type": "menu", "menuId": "root"}},
            ),
        ],
    )

    audio_tracks = MenuSpec(
        id="audio_tracks",
        number=3,
        heading="AUDIO TRACK SELECTION",
        tone_hz=780,
        entry=None,
        buttons=[
            ButtonSpec(
                "audio_english",
                "English AC-3 - 1000 Hz",
                "g1=0; audio=0; jump menu 2;",
                {"destination": {"type": "menu", "menuId": "languages"}, "audioIndex": 0},
            ),
            ButtonSpec(
                "audio_spanish",
                "Spanish AC-3 - 440 Hz",
                "g1=1; audio=1; jump menu 2;",
                {"destination": {"type": "menu", "menuId": "languages"}, "audioIndex": 1},
            ),
            ButtonSpec(
                "audio_languages",
                "Return to Languages",
                "jump menu 2;",
                {"destination": {"type": "menu", "menuId": "languages"}},
            ),
            ButtonSpec(
                "audio_main_menu",
                "Main Menu",
                "jump menu 1;",
                {"destination": {"type": "menu", "menuId": "root"}},
            ),
        ],
    )

    subtitle_tracks = MenuSpec(
        id="subtitle_tracks",
        number=4,
        heading="DVD SPU SUBTITLE SELECTION",
        tone_hz=840,
        entry=None,
        buttons=[
            ButtonSpec(
                "subtitle_english",
                "English DVD SPU",
                "g2=0; subtitle=64; jump menu 2;",
                {"destination": {"type": "menu", "menuId": "languages"},
                 "subtitleIndex": 0, "subtitleMode": "enabled"},
            ),
            ButtonSpec(
                "subtitle_spanish",
                "Spanish DVD SPU",
                "g2=1; subtitle=65; jump menu 2;",
                {"destination": {"type": "menu", "menuId": "languages"},
                 "subtitleIndex": 1, "subtitleMode": "enabled"},
            ),
            ButtonSpec(
                "subtitle_off",
                "Subtitles Off",
                "g2=63; subtitle=63; jump menu 2;",
                {"destination": {"type": "menu", "menuId": "languages"},
                 "subtitleMode": "off", "expectedMiniClientSelector": 128},
            ),
            ButtonSpec(
                "subtitle_languages",
                "Return to Languages",
                "jump menu 2;",
                {"destination": {"type": "menu", "menuId": "languages"}},
            ),
            ButtonSpec(
                "subtitle_main_menu",
                "Main Menu",
                "jump menu 1;",
                {"destination": {"type": "menu", "menuId": "root"}},
            ),
        ],
    )

    chapter_buttons: list[ButtonSpec] = []
    for index, seconds in enumerate(chapters, 1):
        chapter_buttons.append(ButtonSpec(
            id=f"chapter_{index}",
            label=f"Chapter {index} - {timestamp(seconds)[:-4]}",
            vm_command=play_with_current_selections(
                f"jump title 1 chapter {index};"
            ),
            expected={
                "destination": {
                    "type": "title",
                    "titleId": "main_feature",
                    "chapter": index,
                    "requestedSeconds": seconds,
                }
            },
        ))
    chapter_buttons.append(ButtonSpec(
        "chapters_main_menu",
        "Main Menu",
        "jump menu 1;",
        {"destination": {"type": "menu", "menuId": "root"}},
    ))
    chapters_menu = MenuSpec(
        id="chapters",
        number=5,
        heading="CHAPTER SELECTION",
        tone_hz=900,
        entry="ptt",
        buttons=chapter_buttons,
    )

    extras = MenuSpec(
        id="special_features",
        number=6,
        heading="SPECIAL FEATURES",
        tone_hz=960,
        entry=None,
        buttons=[
            ButtonSpec(
                "special_play",
                "Play Short Test Feature",
                play_with_current_selections("jump title 2 chapter 1;"),
                {"destination": {"type": "title", "titleId": "special_feature", "chapter": 1},
                 "selectionPolicy": "preserve-current-audio-and-subtitle"},
            ),
            ButtonSpec(
                "special_main_menu",
                "Main Menu",
                "jump menu 1;",
                {"destination": {"type": "menu", "menuId": "root"}},
            ),
        ],
    )

    result = [root, languages, audio_tracks, subtitle_tracks, chapters_menu, extras]
    for menu in result:
        assign_button_layout(menu)
    return result


def build_cues(
    duration: float,
    chapters: Sequence[float],
    interval: float,
    cue_duration: float,
) -> list[dict[str, Any]]:
    cues: list[dict[str, Any]] = []
    cue_index = 1
    start = 0.0
    while start < duration - 0.05:
        end = min(duration - 0.05, start + cue_duration)
        frame = nearest_frame(start)
        cues.append({
            "cue": cue_index,
            "startSeconds": round(start, 3),
            "endSeconds": round(end, 3),
            "startTimestamp": timestamp(start),
            "endTimestamp": timestamp(end),
            "nearestVideoFrame": frame,
            "nearestVideoFrameTimestamp": timestamp(frame_seconds(frame)),
            "chapter": current_chapter(start, chapters),
        })
        cue_index += 1
        start = round(start + interval, 6)
    return cues


def cue_clock_digest(cues: Sequence[dict[str, Any]]) -> str:
    """Return a stable digest for the shared subtitle timing schedule."""

    canonical = [
        {
            "cue": int(cue["cue"]),
            "startSeconds": float(cue["startSeconds"]),
            "endSeconds": float(cue["endSeconds"]),
            "nearestVideoFrame": int(cue["nearestVideoFrame"]),
            "chapter": int(cue["chapter"]),
        }
        for cue in cues
    ]
    payload = json.dumps(canonical, sort_keys=True, separators=(",", ":")).encode("utf-8")
    return hashlib.sha256(payload).hexdigest()


def write_srt(
    path: Path,
    cues: Sequence[dict[str, Any]],
    language: SubtitleSpec,
    title_id: str,
) -> None:
    lines: list[str] = []
    for cue in cues:
        cue_number = int(cue["cue"])
        lines.extend([
            str(cue_number),
            f"{timestamp(float(cue['startSeconds']), comma=True)} --> "
            f"{timestamp(float(cue['endSeconds']), comma=True)}",
            f"{language.language3.upper()} DVD SPU | {title_id.upper()} | CUE {cue_number:03d}",
            f"PTS {cue['startTimestamp']} | FRAME~{int(cue['nearestVideoFrame']):06d} | "
            f"CHAPTER {int(cue['chapter'])}",
            "",
        ])
    path.write_text("\n".join(lines), encoding="utf-8")


def textsub_xml(srt: Path, font: Path, language: SubtitleSpec) -> str:
    return f"""<?xml version="1.0" encoding="UTF-8"?>
<subpictures format="NTSC">
  <stream>
    <textsub filename={quoteattr(str(srt))} characterset="UTF-8"
      fontsize="27" font={quoteattr(str(font))}
      fill-color={quoteattr(language.fill_color)}
      outline-color="black" outline-thickness="3"
      shadow-offset="2, 2" shadow-color="gray"
      horizontal-alignment="center" vertical-alignment="bottom"
      left-margin="34" right-margin="34" top-margin="20" bottom-margin="48"
      subtitle-fps="{FPS_DECIMAL:.8f}" movie-fps="{FPS_DECIMAL:.8f}"
      movie-width="720" movie-height="480" aspect="16:9" force="no" />
  </stream>
</subpictures>
"""


def chapter_filter_fragments(
    font: Path,
    chapters: Sequence[float],
    duration: float,
) -> list[str]:
    result: list[str] = []
    for index, start in enumerate(chapters):
        end = chapters[index + 1] if index + 1 < len(chapters) else duration + 0.001
        label = ffmpeg_text(
            f"EXPECTED CHAPTER {index + 1} | START {timestamp(start)}"
        )
        result.append(
            f"drawtext=fontfile='{font}':text='{label}':fontcolor=white:"
            "fontsize=26:x=(w-text_w)/2:y=292:box=1:boxcolor=black@0.65:"
            f"boxborderw=7:enable='gte(t,{start:.3f})*lt(t,{end:.3f})'"
        )
    return result


def generate_title(
    ffmpeg: str,
    output: Path,
    duration: int,
    title_id: str,
    title_label: str,
    chapters: Sequence[float],
    font: Path,
    video_source: str,
) -> None:
    video_filters = [
        "setpts=PTS-STARTPTS",
        "tinterlace=mode=interleave_top",
        "setfield=tff",
        "format=yuv420p",
        "drawgrid=width=90:height=60:thickness=2:color=white@0.18",
        "drawbox=x=28:y=28:w=66:h=66:color=white@0.88:t=fill:"
        f"enable='lt(mod(t,1),{SYNC_PULSE_DURATION:.3f})'",
        "drawbox=x=28:y=28:w=66:h=66:color=black@0.82:t=4",
        "drawbox=x=30:y=145:w=660:h=190:color=black@0.72:t=fill",
        f"drawtext=fontfile='{font}':text='{ffmpeg_text(title_label)}':"
        "fontcolor=white:fontsize=31:x=(w-text_w)/2:y=160",
        f"drawtext=fontfile='{font}':text='TITLE ID {ffmpeg_text(title_id)}':"
        "fontcolor=cyan:fontsize=23:x=(w-text_w)/2:y=203",
        f"drawtext=fontfile='{font}':text='PTS %{{pts\\:hms}}  FRAME %{{n}}':"
        "fontcolor=yellow:fontsize=34:x=(w-text_w)/2:y=238",
        f"drawtext=fontfile='{font}':text='SYNC PULSE = WHOLE SECOND':"
        "fontcolor=black:fontsize=17:x=101:y=48:box=1:boxcolor=white@0.88:"
        f"boxborderw=5:enable='lt(mod(t,1),{SYNC_PULSE_DURATION:.3f})'",
        f"drawtext=fontfile='{font}':text='AUDIO 0 ENG 1000Hz | AUDIO 1 SPA 440Hz':"
        "fontcolor=white:fontsize=19:x=(w-text_w)/2:y=350:"
        "box=1:boxcolor=black@0.60:boxborderw=5",
    ]
    video_filters.extend(chapter_filter_fragments(font, chapters, float(duration)))

    audio_filter = (
        f"volume='if(lt(mod(t,1),{SYNC_PULSE_DURATION:.3f}),0.82,0.13)':"
        "eval=frame,pan=stereo|FL=c0|FR=c0,alimiter=limit=0.95"
    )
    complex_filter = (
        "[0:v]" + ",".join(video_filters) + "[v];"
        f"[1:a]asetpts=PTS-STARTPTS,{audio_filter}[aeng];"
        f"[2:a]asetpts=PTS-STARTPTS,{audio_filter}[aspa]"
    )

    force_keyframes = ",".join(f"{value:.3f}" for value in chapters)
    run([
        ffmpeg,
        "-hide_banner", "-nostats", "-loglevel", "warning", "-y",
        "-f", "lavfi", "-i",
        f"{video_source}=size={WIDTH}x{HEIGHT}:rate={SOURCE_FPS}:duration={duration}",
        "-f", "lavfi", "-i",
        f"sine=frequency={AUDIO_STREAMS[0].tone_hz}:sample_rate=48000:duration={duration}",
        "-f", "lavfi", "-i",
        f"sine=frequency={AUDIO_STREAMS[1].tone_hz}:sample_rate=48000:duration={duration}",
        "-filter_complex", complex_filter,
        "-map", "[v]", "-map", "[aeng]", "-map", "[aspa]",
        "-map_metadata", "-1",
        "-metadata:s:a:0", "language=eng",
        "-metadata:s:a:0", "title=English AC-3 1000 Hz",
        "-metadata:s:a:1", "language=spa",
        "-metadata:s:a:1", "title=Spanish AC-3 440 Hz",
        "-c:v", "mpeg2video", "-pix_fmt", "yuv420p",
        "-flags:v", "+ildct+ilme",
        "-g", "15", "-bf", "2", "-sc_threshold", "0",
        "-b:v", "5000k", "-minrate", "2500k", "-maxrate", "7000k",
        "-bufsize", "1835008", "-aspect", DISPLAY_ASPECT, "-r", FPS,
        "-force_key_frames", force_keyframes,
        "-c:a", "ac3", "-b:a", "192k", "-ar", "48000", "-ac", "2",
        "-threads", "1",
        "-packetsize", "2048", "-muxrate", "8000000",
        "-muxpreload", "0.5", "-muxdelay", "0.7",
        "-t", str(duration), "-shortest", "-f", "dvd", str(output),
    ])


def add_subtitles(
    spumux: str,
    source: Path,
    destination: Path,
    work: Path,
    font: Path,
    title_id: str,
    cues: Sequence[dict[str, Any]],
) -> list[dict[str, str]]:
    current = source
    generated: list[dict[str, str]] = []
    for stream_index, language in enumerate(SUBTITLE_STREAMS):
        srt = work / f"{destination.stem}-{language.language3}.srt"
        xml = work / f"{destination.stem}-{language.language3}-spumux.xml"
        write_srt(srt, cues, language, title_id)
        xml.write_text(textsub_xml(srt, font, language), encoding="utf-8")
        xml_validate(xml)
        next_path = (
            destination
            if stream_index == len(SUBTITLE_STREAMS) - 1
            else work / f"{destination.stem}-after-spu-{stream_index}.mpg"
        )
        run(
            [spumux, "-m", "dvd", "-s", str(stream_index), str(xml)],
            stdin=current,
            stdout=next_path,
        )
        generated.append({"srt": str(srt), "xml": str(xml)})
        current = next_path
    return generated


def imagemagick_prefix() -> list[str]:
    magick = tool("magick", required=False)
    if magick:
        return [magick]
    convert = tool("convert", required=True)
    assert convert is not None
    return [convert]


def image_color_count(magick_prefix: Sequence[str], path: Path) -> int:
    executable = Path(magick_prefix[0]).name.lower()
    if executable == "magick" or executable.startswith("magick."):
        command = [magick_prefix[0], "identify", "-format", "%k", str(path)]
    else:
        identify = tool("identify", required=False)
        if identify is None:
            raise RuntimeError(
                "ImageMagick 'identify' is required to validate DVD menu palettes"
            )
        command = [identify, "-format", "%k", str(path)]
    raw = run_text(command).strip()
    try:
        return int(raw)
    except ValueError as error:
        raise RuntimeError(f"unable to read PNG color count for {path}: {raw!r}") from error


def draw_menu_image(
    magick_prefix: Sequence[str],
    output: Path,
    menu: MenuSpec,
    font: Path,
) -> None:
    command = [
        *magick_prefix,
        "-size", f"{WIDTH}x{HEIGHT}",
        "gradient:#14223a-#05070d",
        "-font", str(font),
        "-fill", "white", "-gravity", "north",
        "-pointsize", "32", "-annotate", "+0+26", menu.heading,
        "-gravity", "northwest",
        "-fill", "#8ec8ff", "-pointsize", "16",
        "-annotate", "+22+62", f"MENU ID: {menu.id.upper()} | PGC {menu.number} | LOOPING MENU",
    ]
    for index, button in enumerate(menu.buttons, 1):
        if button.rect is None:
            raise RuntimeError(f"button layout missing for {button.id}")
        # With northwest gravity ImageMagick treats the annotate Y coordinate
        # as the top of the text run, not the typographic baseline.  A +28
        # offset placed the label on the lower edge of its DVD button box.
        # Four pixels of top padding centers the 23-point label in the 40-pixel
        # authored navigation rectangle.
        text_y = button.rect.y0 + 4
        command.extend([
            "-fill", "#d9e9ff", "-pointsize", "23",
            "-annotate", f"+112+{text_y}", f"[{index}] {button.label}",
        ])
    command.extend([
        "-fill", "#9ba8b8", "-pointsize", "15",
        "-annotate", "+22+454", "UP/DOWN selects | ENTER activates | YELLOW=selected | GREEN=activate",
        str(output),
    ])
    run(command)


def draw_highlight(
    magick_prefix: Sequence[str],
    output: Path,
    buttons: Sequence[ButtonSpec],
    color: str,
    stroke_width: int,
) -> None:
    command = [
        *magick_prefix,
        "-size", f"{WIDTH}x{HEIGHT}",
        "xc:none",
        "+antialias",
        "-fill", "none",
        "-stroke", color,
        "-strokewidth", str(stroke_width),
    ]
    for button in buttons:
        if button.rect is None:
            raise RuntimeError(f"button layout missing for {button.id}")
        # ImageMagick's rectangle endpoint is inclusive.  Spumux's x1/y1 are
        # exclusive, so draw through x1-1/y1-1.
        command.extend([
            "-draw",
            f"roundrectangle {button.rect.x0},{button.rect.y0} "
            f"{button.rect.x1 - 1},{button.rect.y1 - 1} 8,8",
        ])
    command.append(str(output))
    run(command)


def menu_spu_xml(menu: MenuSpec) -> str:
    if not menu.highlight_file or not menu.select_file:
        raise RuntimeError(f"menu assets are incomplete for {menu.id}")
    button_lines: list[str] = []
    for button in menu.buttons:
        if button.rect is None:
            raise RuntimeError(f"button layout missing for {button.id}")
        button_lines.append(
            "      <button "
            f"name={quoteattr(button.id)} "
            f"x0={quoteattr(str(button.rect.x0))} "
            f"y0={quoteattr(str(button.rect.y0))} "
            f"x1={quoteattr(str(button.rect.x1))} "
            f"y1={quoteattr(str(button.rect.y1))} "
            f"up={quoteattr(button.up)} down={quoteattr(button.down)} "
            f"left={quoteattr(button.left)} right={quoteattr(button.right)} />"
        )
    button_xml = "\n".join(button_lines)
    return f"""<?xml version="1.0" encoding="UTF-8"?>
<subpictures format="NTSC">
  <stream>
    <spu start="00:00:00.00"
      highlight={quoteattr(str(menu.highlight_file))}
      select={quoteattr(str(menu.select_file))}
      transparent="000000" force="yes">
{button_xml}
    </spu>
  </stream>
</subpictures>
"""


def make_menu(
    ffmpeg: str,
    spumux: str,
    magick_prefix: Sequence[str],
    work: Path,
    menu: MenuSpec,
    font: Path,
    duration: float,
) -> None:
    menu.image_file = work / f"menu-{menu.number:02d}-{menu.id}.png"
    menu.highlight_file = work / f"menu-{menu.number:02d}-{menu.id}-highlight.png"
    menu.select_file = work / f"menu-{menu.number:02d}-{menu.id}-select.png"
    base = work / f"menu-{menu.number:02d}-{menu.id}-base.mpg"
    menu.video_file = work / f"menu-{menu.number:02d}-{menu.id}.mpg"
    menu.spu_xml_file = work / f"menu-{menu.number:02d}-{menu.id}-spumux.xml"

    draw_menu_image(magick_prefix, menu.image_file, menu, font)
    draw_highlight(magick_prefix, menu.highlight_file, menu.buttons, "#ffff00", 6)
    draw_highlight(magick_prefix, menu.select_file, menu.buttons, "#00ff00", 8)
    for overlay_asset in (menu.highlight_file, menu.select_file):
        color_count = image_color_count(magick_prefix, overlay_asset)
        if color_count != 2:
            raise RuntimeError(
                f"menu overlay must contain exactly transparent + one solid color; "
                f"got {color_count} colors in {overlay_asset}"
            )

    menu_filter = (
        "[0:v]setpts=PTS-STARTPTS,tinterlace=mode=interleave_top,setfield=tff,"
        "format=yuv420p,"
        "drawbox=x=16:y=16:w=46:h=46:color=white@0.90:t=fill:"
        f"enable='lt(mod(t,1),{SYNC_PULSE_DURATION:.3f})',"
        f"drawtext=fontfile='{font}':text='MENU PTS %{{pts\\:hms}} FRAME %{{n}}':"
        "fontcolor=yellow:fontsize=16:x=450:y=60:box=1:boxcolor=black@0.60:"
        "boxborderw=4[v];"
        f"[1:a]volume=0.16,pan=stereo|FL=c0|FR=c0[a]"
    )
    run([
        ffmpeg,
        "-hide_banner", "-nostats", "-loglevel", "warning", "-y",
        "-loop", "1", "-framerate", SOURCE_FPS, "-i", str(menu.image_file),
        "-f", "lavfi", "-i",
        f"sine=frequency={menu.tone_hz}:sample_rate=48000:duration={duration}",
        "-filter_complex", menu_filter,
        "-map", "[v]", "-map", "[a]",
        "-map_metadata", "-1",
        "-c:v", "mpeg2video", "-pix_fmt", "yuv420p",
        "-flags:v", "+ildct+ilme",
        "-g", "15", "-bf", "2", "-sc_threshold", "0",
        "-b:v", "3500k", "-minrate", "2000k", "-maxrate", "7000k",
        "-bufsize", "1835008", "-aspect", DISPLAY_ASPECT, "-r", FPS,
        "-c:a", "ac3", "-b:a", "192k", "-ar", "48000", "-ac", "2",
        "-threads", "1",
        "-packetsize", "2048", "-muxrate", "6000000",
        "-muxpreload", "0.5", "-muxdelay", "0.7",
        "-t", f"{duration:.3f}", "-shortest", "-f", "dvd", str(base),
    ])

    menu.spu_xml_file.write_text(menu_spu_xml(menu), encoding="utf-8")
    xml_validate(menu.spu_xml_file)
    run([spumux, "-m", "dvd", str(menu.spu_xml_file)], stdin=base, stdout=menu.video_file)


def write_palette(path: Path) -> None:
    """Write the deterministic 16-entry RGB CLUT consumed by dvdauthor."""

    colors = (
        "000000", "ffffff", "ffff00", "00ff00",
        "ff0000", "0000ff", "00ffff", "ff00ff",
        "808080", "c0c0c0", "800000", "008000",
        "000080", "808000", "008080", "800080",
    )
    path.write_text("\n".join(colors) + "\n", encoding="ascii")


def author_xml(
    output: Path,
    menus: Sequence[MenuSpec],
    main_feature: Path,
    special_feature: Path,
    main_chapters: Sequence[float],
    special_chapters: Sequence[float],
    palette: Path,
) -> str:
    menu_blocks: list[str] = []
    for menu in menus:
        if not menu.video_file:
            raise RuntimeError(f"menu video missing for {menu.id}")
        entry = f" entry={quoteattr(menu.entry)}" if menu.entry else ""
        button_xml = "\n".join(
            f"        <button name={quoteattr(button.id)}>{button.vm_command}</button>"
            for button in menu.buttons
        )
        menu_blocks.append(
            f"""      <pgc{entry} palette={quoteattr(str(palette))}>
        <pre>{menu.pre_command}</pre>
        <vob file={quoteattr(str(menu.video_file))} />
{button_xml}
        <post>jump cell 1;</post>
      </pgc>"""
        )

    main_chapter_text = ",".join(timestamp(value) for value in main_chapters)
    special_chapter_text = ",".join(timestamp(value) for value in special_chapters)
    menu_xml = "\n".join(menu_blocks)
    return f"""<?xml version="1.0" encoding="UTF-8"?>
<dvdauthor dest={quoteattr(str(output))} jumppad="yes">
  <vmgm>
    <fpc>jump titleset 1 menu entry root;</fpc>
    <menus lang="en">
      <video format="ntsc" aspect="16:9" resolution="720x480" widescreen="nopanscan" />
      <pgc entry="title">
        <pre>jump titleset 1 menu entry root;</pre>
      </pgc>
    </menus>
  </vmgm>
  <titleset>
    <menus lang="en">
      <video format="ntsc" aspect="16:9" resolution="720x480" widescreen="nopanscan" />
      <audio format="ac3" channels="2" samplerate="48khz" lang="en" />
{menu_xml}
    </menus>
    <titles>
      <video format="ntsc" aspect="16:9" resolution="720x480" widescreen="nopanscan" />
      <audio format="ac3" channels="2" samplerate="48khz" lang="en" />
      <audio format="ac3" channels="2" samplerate="48khz" lang="es" />
      <subpicture lang="en" content="normal" />
      <subpicture lang="es" content="normal" />
      <pgc>
        <vob file={quoteattr(str(main_feature))} chapters={quoteattr(main_chapter_text)} />
        <post>call menu entry root;</post>
      </pgc>
      <pgc>
        <vob file={quoteattr(str(special_feature))} chapters={quoteattr(special_chapter_text)} />
        <post>call menu entry root;</post>
      </pgc>
    </titles>
  </titleset>
</dvdauthor>
"""


def probe_media(ffprobe: str, path: Path) -> dict[str, Any]:
    output = run_text([
        ffprobe,
        "-v", "error",
        "-show_streams", "-show_format",
        "-of", "json",
        str(path),
    ])
    return json.loads(output)


def compact_stream(stream: dict[str, Any]) -> dict[str, Any]:
    keys = (
        "index", "codec_name", "codec_long_name", "codec_type", "codec_tag_string",
        "width", "height", "sample_aspect_ratio", "display_aspect_ratio",
        "field_order", "r_frame_rate", "avg_frame_rate", "sample_rate", "channels",
        "channel_layout", "duration", "start_time", "id",
    )
    result = {key: stream.get(key) for key in keys if key in stream}
    if stream.get("tags"):
        result["tags"] = stream["tags"]
    return result


def validate_title_probe(probe: dict[str, Any], label: str) -> dict[str, Any]:
    streams = probe.get("streams", [])
    videos = [stream for stream in streams if stream.get("codec_type") == "video"]
    audios = [stream for stream in streams if stream.get("codec_type") == "audio"]
    subtitles = [stream for stream in streams if stream.get("codec_type") == "subtitle"]
    failures: list[str] = []

    if len(videos) != 1:
        failures.append(f"expected exactly one video stream, got {len(videos)}")
    else:
        video = videos[0]
        if video.get("codec_name") != "mpeg2video":
            failures.append(f"expected mpeg2video, got {video.get('codec_name')}")
        if (video.get("width"), video.get("height")) != (WIDTH, HEIGHT):
            failures.append(
                f"expected {WIDTH}x{HEIGHT}, got {video.get('width')}x{video.get('height')}"
            )
        if video.get("field_order") not in ("tt", "tb", "unknown"):
            failures.append(f"unexpected field order: {video.get('field_order')}")

    if len(audios) != 2:
        failures.append(f"expected two audio streams, got {len(audios)}")
    for stream in audios:
        if stream.get("codec_name") != "ac3":
            failures.append(f"expected AC-3 audio, got {stream.get('codec_name')}")
        if int(stream.get("channels", 0)) != 2:
            failures.append(f"expected stereo audio, got {stream.get('channels')} channels")

    if len(subtitles) != 2:
        failures.append(f"expected two subtitle streams, got {len(subtitles)}")
    for stream in subtitles:
        if stream.get("codec_name") not in ("dvd_subtitle", "dvdsub"):
            failures.append(f"expected DVD subtitle, got {stream.get('codec_name')}")

    if failures:
        raise RuntimeError(f"{label} validation failed: " + "; ".join(failures))

    return {
        "videoCount": len(videos),
        "audioCount": len(audios),
        "subtitleCount": len(subtitles),
        "streams": [compact_stream(stream) for stream in streams],
        "format": probe.get("format", {}),
    }


def validate_menu_probe(probe: dict[str, Any], label: str) -> dict[str, Any]:
    """Validate the authored menu VOB, including its forced button subpicture."""

    streams = probe.get("streams", [])
    videos = [stream for stream in streams if stream.get("codec_type") == "video"]
    audios = [stream for stream in streams if stream.get("codec_type") == "audio"]
    subtitles = [stream for stream in streams if stream.get("codec_type") == "subtitle"]
    failures: list[str] = []

    if len(videos) < 1:
        failures.append("expected at least one menu video stream")
    elif videos[0].get("codec_name") != "mpeg2video":
        failures.append(f"expected mpeg2video, got {videos[0].get('codec_name')}")

    if len(audios) < 1:
        failures.append("expected at least one menu audio stream")
    elif audios[0].get("codec_name") != "ac3":
        failures.append(f"expected AC-3 menu audio, got {audios[0].get('codec_name')}")

    if len(subtitles) < 1:
        failures.append("expected at least one DVD menu subpicture stream")
    for stream in subtitles:
        if stream.get("codec_name") not in ("dvd_subtitle", "dvdsub"):
            failures.append(f"expected DVD menu subpicture, got {stream.get('codec_name')}")

    if failures:
        raise RuntimeError(f"{label} validation failed: " + "; ".join(failures))

    return {
        "videoCount": len(videos),
        "audioCount": len(audios),
        "subtitleCount": len(subtitles),
        "streams": [compact_stream(stream) for stream in streams],
        "format": probe.get("format", {}),
    }


def required_dvd_files(output: Path) -> list[Path]:
    video_ts = output / "VIDEO_TS"
    return [
        video_ts / "VIDEO_TS.IFO",
        video_ts / "VIDEO_TS.BUP",
        video_ts / "VTS_01_0.IFO",
        video_ts / "VTS_01_0.BUP",
        video_ts / "VTS_01_0.VOB",
        video_ts / "VTS_01_1.VOB",
    ]


def validate_dvd_structure(output: Path) -> None:
    missing = [path for path in required_dvd_files(output) if not path.is_file() or path.stat().st_size == 0]
    if missing:
        joined = ", ".join(str(path) for path in missing)
        raise RuntimeError(f"authored DVD is incomplete; missing/empty: {joined}")


def menu_manifest(menu: MenuSpec, menu_duration: float) -> dict[str, Any]:
    return {
        "id": menu.id,
        "titleset": 1,
        "menuNumber": menu.number,
        "entry": menu.entry,
        "heading": menu.heading,
        "videoLoopDurationSeconds": menu_duration,
        "playback": "continuous-loop",
        "menuAudioToneHz": menu.tone_hz,
        "defaultButton": menu.buttons[0].id,
        "preCommand": menu.pre_command,
        "buttons": [
            {
                "ordinal": index,
                "id": button.id,
                "label": button.label,
                "vmCommand": button.vm_command,
                "navigation": {
                    "up": button.up,
                    "down": button.down,
                    "left": button.left,
                    "right": button.right,
                },
                "authoredRect": button.rect.authored() if button.rect else None,
                "expectedLegacySpuCtrlRect": button.rect.legacy_spuctrl() if button.rect else None,
                "expected": button.expected,
            }
            for index, button in enumerate(menu.buttons, 1)
        ],
        "visualAssertions": {
            "selectedOutline": {"rgb": "#FFFF00", "tolerancePerChannel": 32},
            "activatedOutline": {"rgb": "#00FF00", "tolerancePerChannel": 32},
            "backgroundContains": [menu.heading, f"MENU ID: {menu.id.upper()}"],
            "lastVideoFrameMustRemainVisibleDuringStill": True,
        },
    }


def chapter_manifest(chapters: Sequence[float], duration: float) -> list[dict[str, Any]]:
    result: list[dict[str, Any]] = []
    for index, start in enumerate(chapters, 1):
        next_start = chapters[index] if index < len(chapters) else duration
        frame = nearest_frame(start)
        result.append({
            "chapter": index,
            "requestedSeconds": start,
            "requestedTimestamp": timestamp(start),
            "nearestFrame": frame,
            "nearestFrameSeconds": round(frame_seconds(frame), 6),
            "nearestFrameTimestamp": timestamp(frame_seconds(frame)),
            "endSeconds": next_start,
            "seekLandingToleranceMs": 750,
            "burnInMustContain": f"EXPECTED CHAPTER {index}",
        })
    return result


def title_manifest(
    title_id: str,
    title_number: int,
    duration: int,
    chapters: Sequence[float],
    cues: Sequence[dict[str, Any]],
    source: str,
) -> dict[str, Any]:
    return {
        "id": title_id,
        "titleset": 1,
        "titleNumber": title_number,
        "durationSeconds": duration,
        "sourcePattern": source,
        "video": {
            "codec": "mpeg2video",
            "resolution": {"width": WIDTH, "height": HEIGHT},
            "displayAspect": DISPLAY_ASPECT,
            "frameRate": {"numerator": FPS_NUM, "denominator": FPS_DEN, "text": FPS},
            "sourceFrameRate": SOURCE_FPS,
            "interlaced": True,
            "fieldOrder": FIELD_ORDER,
            "timebaseHz": TIMEBASE_HZ,
            "burnIn": {
                "contains": [
                    f"TITLE ID {title_id}",
                    "PTS HH:MM:SS.mmmmmm",
                    "FRAME n",
                    "EXPECTED CHAPTER n",
                    "AUDIO 0 ENG 1000Hz | AUDIO 1 SPA 440Hz",
                ],
                "wholeSecondPulse": {
                    "topLeftWhiteBoxDurationSeconds": SYNC_PULSE_DURATION,
                    "expectedAudioAmplitudePulseDurationSeconds": SYNC_PULSE_DURATION,
                },
            },
        },
        "audioStreams": [
            {
                "logicalIndex": stream.index,
                "language": stream.language3,
                "dvdLanguage": stream.language2,
                "label": stream.label,
                "codec": "ac3",
                "channels": 2,
                "sampleRateHz": 48000,
                "bitrateBitsPerSecond": 192000,
                "continuousToneHz": stream.tone_hz,
                "wholeSecondAmplitudePulse": True,
                "dvdPesStreamIdHex": "0xBD",
                "dvdPrivateSubstreamIdHex": f"0x{stream.physical_substream:02X}",
                "expectedLegacyStreamSelectorHex": f"0x{stream.legacy_selector:04X}",
            }
            for stream in AUDIO_STREAMS
        ],
        "sharedSubtitleClock": {
            "id": f"{title_id}-shared-spu-clock",
            "sha256": cue_clock_digest(cues),
            "cueCountPerLanguage": len(cues),
            "allLanguageStreamsUseIdenticalCueTimes": True,
            "timebaseHz": TIMEBASE_HZ,
            "videoFrameRate": FPS,
        },
        "subtitleStreams": [
            {
                "logicalIndex": stream.index,
                "language": stream.language3,
                "dvdLanguage": stream.language2,
                "label": stream.label,
                "codec": "dvd_subtitle",
                "dvdPrivateSubstreamIdHex": f"0x{stream.physical_substream:02X}",
                "legacyStreamSelector": stream.index,
                "enabledDvdRegisterValue": 64 + stream.index,
                "fillColor": stream.fill_color,
                "expectedColorRoles": ["transparent", "fill", "outline", "shadow"],
                "expectedMaximumSpuPaletteEntries": 4,
                "outlineColor": "black",
                "shadowColor": "gray",
                "clockGroup": f"{title_id}-shared-spu-clock",
                "clockSha256": cue_clock_digest(cues),
                "cues": [
                    {
                        **cue,
                        "expectedTextLine1": (
                            f"{stream.language3.upper()} DVD SPU | {title_id.upper()} | "
                            f"CUE {int(cue['cue']):03d}"
                        ),
                        "expectedTextLine2": (
                            f"PTS {cue['startTimestamp']} | "
                            f"FRAME~{int(cue['nearestVideoFrame']):06d} | "
                            f"CHAPTER {int(cue['chapter'])}"
                        ),
                    }
                    for cue in cues
                ],
            }
            for stream in SUBTITLE_STREAMS
        ],
        "subtitleOff": {
            "dvdRegisterValue": 63,
            "expectedMiniClientSelector": 128,
            "expectedNormalSubtitleVisibility": False,
        },
        "chapters": chapter_manifest(chapters, float(duration)),
        "postCommand": "call menu entry root;",
    }


def checkpoint(
    checkpoint_id: str,
    title_id: str,
    seconds: float,
    chapters: Sequence[float],
    cues: Sequence[dict[str, Any]],
    subtitle_index: int | None,
) -> dict[str, Any]:
    frame = nearest_frame(seconds)
    active_cue = next(
        (
            cue
            for cue in cues
            if float(cue["startSeconds"]) <= seconds < float(cue["endSeconds"])
        ),
        None,
    )
    result: dict[str, Any] = {
        "id": checkpoint_id,
        "titleId": title_id,
        "seekSeconds": seconds,
        "expectedNearestFrame": frame,
        "expectedNearestFrameTimestamp": timestamp(frame_seconds(frame)),
        "mediaTimeToleranceMs": 750,
        "expectedChapter": current_chapter(seconds, chapters),
        "screenshotFile": f"{checkpoint_id}.png",
        "burnInAssertions": [
            f"TITLE ID {title_id}",
            f"EXPECTED CHAPTER {current_chapter(seconds, chapters)}",
            "PTS",
            "FRAME",
        ],
    }
    if subtitle_index is None:
        result["subtitle"] = {"mode": "off", "mustBeAbsent": True}
    elif active_cue:
        stream = SUBTITLE_STREAMS[subtitle_index]
        result["subtitle"] = {
            "mode": "enabled",
            "logicalIndex": subtitle_index,
            "language": stream.language3,
            "cue": active_cue["cue"],
            "mustContain": [
                f"{stream.language3.upper()} DVD SPU",
                f"CUE {int(active_cue['cue']):03d}",
                f"PTS {active_cue['startTimestamp']}",
            ],
        }
    else:
        result["subtitle"] = {
            "mode": "enabled",
            "logicalIndex": subtitle_index,
            "expectedCue": None,
            "mustBeAbsent": True,
        }
    return result


def build_checkpoints(
    main_duration: int,
    special_duration: int,
    main_cues: Sequence[dict[str, Any]],
    special_cues: Sequence[dict[str, Any]],
) -> list[dict[str, Any]]:
    candidates: list[tuple[str, str, float, Sequence[float], Sequence[dict[str, Any]], int | None]] = [
        ("main_start_no_subs", "main_feature", 0.5, MAIN_CHAPTERS, main_cues, None),
        ("main_eng_sub_06s", "main_feature", 6.5, MAIN_CHAPTERS, main_cues, 0),
        ("main_spa_sub_12s", "main_feature", 12.5, MAIN_CHAPTERS, main_cues, 1),
        ("main_chapter2", "main_feature", 20.5, MAIN_CHAPTERS, main_cues, 0),
        ("main_chapter3", "main_feature", 40.5, MAIN_CHAPTERS, main_cues, 1),
        ("main_chapter4", "main_feature", 60.5, MAIN_CHAPTERS, main_cues, 0),
        ("main_near_eos", "main_feature", max(0.5, main_duration - 1.5), MAIN_CHAPTERS, main_cues, None),
        ("special_start", "special_feature", 0.5, SPECIAL_CHAPTERS, special_cues, 0),
        ("special_chapter2", "special_feature", 10.5, SPECIAL_CHAPTERS, special_cues, 1),
        ("special_near_eos", "special_feature", max(0.5, special_duration - 1.5), SPECIAL_CHAPTERS, special_cues, None),
    ]
    return [checkpoint(*values) for values in candidates]


def test_case(
    case_id: str,
    category: str,
    title: str,
    steps: list[dict[str, Any]],
    assertions: list[dict[str, Any]],
    *,
    priority: str = "required",
    protocol: list[dict[str, Any]] | None = None,
) -> dict[str, Any]:
    return {
        "id": case_id,
        "category": category,
        "priority": priority,
        "title": title,
        "steps": steps,
        "assertions": assertions,
        "expectedProtocol": protocol or [],
    }


def build_action_vocabulary() -> dict[str, dict[str, Any]]:
    """Describe the harness-neutral action names used by the Codex test plan."""

    descriptions = {
        "activateButton": "Activate the named currently authored DVD button and wait for its VM command to take effect.",
        "assertButtonSelected": "Verify the named button is the active DVD highlight.",
        "assertMenu": "Verify the specified authored menu is visible and interactive.",
        "assertSelectedStreams": "Verify current logical audio/subpicture selections and subtitle mode.",
        "backgroundAndForegroundApp": "Send the Android app through background/foreground surface recreation without ending the SageTV session.",
        "captureCompleteDvdSession": "Capture lossless media-command payloads, replies, ordering, timing, and PUSHBUFFER bytes for replay.",
        "captureHighRateFramesAroundActivation": "Capture display frames fast enough to observe the short activated-button palette state.",
        "capturePlayerTelemetry": "Write current player, buffer, PTS, SPU, overlay, and surface state to JSON.",
        "captureProtocolCoverage": "Write aggregate command, flag, reply, packet, stream, and state coverage to JSON.",
        "captureProtocolTrace": "Write an ordered lossless media-command trace with payload metadata and replies.",
        "captureScreenshot": "Capture the current Android display without changing playback state.",
        "captureScreenshotAtMediaTime": "Capture the first stable frame at or immediately after the requested media time.",
        "captureSynchronizedAvWindow": "Capture PCM audio, decoded frames, and player telemetry using one monotonic timestamp origin.",
        "captureVideoAndOverlayGeometry": "Record source crop, destination rectangle, surface bounds, transform, and overlay bounds.",
        "disconnectClient": "Disconnect the Android MiniClient while preserving failure evidence.",
        "enableDetailedDrainTelemetry": "Enable queue-depth and decoder-drain instrumentation before a 0x100 probe.",
        "goToMenu": "Navigate from a known state to the specified authored menu and wait until its default button is highlighted.",
        "goToRootMenu": "Return to the titleset root menu and wait for the default highlight.",
        "launchDvdFolder": "Launch the generated DVD-Video folder through SageTV's server-driven MiniDVDPlayer path.",
        "observeDrainHandshake": "Record every zero-length 0x100 PUSHBUFFER probe, queue depth, decoder state, and reply through -2.",
        "pause": "Issue SageTV pause and wait until media time is stable.",
        "play": "Issue SageTV play/resume and wait until media time advances.",
        "playMainFeature": "Enter title 1 from the root menu using current stream selections.",
        "playMainFeatureWithEnglishSubtitles": "Select English normal DVD SPU subtitles and enter title 1.",
        "playMainFeatureWithSpanishSubtitles": "Select Spanish normal DVD SPU subtitles and enter title 1.",
        "prepareTitleAndSubtitleModeFromCheckpoint": "Apply the title and stream mode defined by the embedded checkpoint object.",
        "pressKey": "Send the named SageTV/DVD remote-control key and wait for the expected state transition.",
        "reconnectClient": "Reconnect the Android MiniClient and wait for a clean SageTV UI session.",
        "recordOverlayTimeline": "Sample SPU visibility, packet/event IDs, palette, rectangle, and frame hash over a media-time range.",
        "repeat": "Execute the nested steps exactly count times and retain evidence for every iteration.",
        "replayPresentationEngine": "Replay a captured protocol/media session into the Java presentation engine using each listed chunk size.",
        "runCases": "Execute the referenced test IDs in their declared order without treating this aggregate case as evidence by itself.",
        "seekMs": "Issue an absolute DVD seek in milliseconds and wait for post-flush playback recovery.",
        "seekNearEnd": "Seek to title duration minus the requested number of seconds.",
        "selectButton": "Move DVD navigation to the named button without activating it.",
        "selectMenuPath": "Activate the listed button IDs in order, waiting for each destination before continuing.",
        "switchAudioUsingDvdMenu": "Use authored DVD menus to select the requested logical AC-3 track and resume the title.",
        "verifyAudioTone": "Measure the dominant PCM frequency and compare it with the expected tone.",
        "waitForChapter": "Wait until server and client telemetry agree on the expected DVD chapter.",
        "waitForMenu": "Wait until the specified menu, highlight, and forced menu SPU are stable.",
        "waitForStableFrame": "Wait for decoded video, media time, and overlay state to stabilize after a transition.",
        "waitForTitle": "Wait until the specified title ID is actively decoding.",
        "waitMs": "Wait the requested wall-clock duration while continuing to collect telemetry.",
    }
    return {
        name: {
            "description": description,
            "harnessMayAdaptImplementation": True,
            "mustPreserveSemanticsAndEvidence": True,
        }
        for name, description in descriptions.items()
    }


def build_harness_contract() -> dict[str, Any]:
    return {
        "schemaVersion": 1,
        "fixtureId": FIXTURE_ID,
        "actionVocabulary": build_action_vocabulary(),
        "clockContract": {
            "monotonicClockRequired": True,
            "allEvidenceRecordsRequireMonotonicNs": True,
            "mediaClockUnits": "milliseconds",
            "mpegPtsClockHz": TIMEBASE_HZ,
            "legacyDvdStcClockHz": 45_000,
            "screenshotTimestampMustUseSameMonotonicOriginAsTelemetry": True,
        },
        "requiredEvidenceStreams": {
            "mediaCommandTrace": {
                "requiredFields": [
                    "sequence", "monotonicNs", "commandId", "commandName",
                    "payloadLength", "payloadSha256", "reply",
                ],
                "pushBufferFields": [
                    "dataLength", "flags", "bufferAvailableReply",
                    "inputQueueBytes", "decoderBufferedDurationMs",
                ],
            },
            "playerTelemetry": {
                "requiredFields": [
                    "monotonicNs", "playerState", "titleId", "chapter",
                    "mediaTimeMs", "selectedAudioIndex", "subtitleMode",
                    "selectedSubtitleIndex", "inputQueueBytes",
                    "decoderBufferedDurationMs",
                ],
            },
            "dvdPresentationTelemetry": {
                "requiredFields": [
                    "monotonicNs", "ptsEpoch", "lastRawPts90k",
                    "lastAdjustedPts90k", "selectedSpuPhysicalId",
                    "spuPacketSequence", "overlayVisible", "overlayRect",
                    "overlayFrameSha256", "pendingSpuEventCount",
                ],
            },
            "androidGeometry": {
                "requiredFields": [
                    "videoSourceRect", "videoDestinationRect",
                    "videoSurfaceBounds", "overlayViewBounds",
                    "dvdCanvasToViewMatrix",
                ],
            },
        },
        "failureEvidence": [
            "current screenshot",
            "preceding 10 seconds of media-command trace",
            "preceding 10 seconds of player and SPU telemetry",
            "Android logcat excerpt",
            "SageTV server log excerpt",
            "active test case and last completed step",
        ],
        "resultFile": "DVD_TEST_RESULTS.json",
        "resultSchemaFile": RESULT_SCHEMA_NAME,
        "summaryFile": "DVD_TEST_SUMMARY.md",
    }


def build_result_schema() -> dict[str, Any]:
    return {
        "$schema": "https://json-schema.org/draft/2020-12/schema",
        "$id": f"urn:opensagetv:{FIXTURE_ID}:dvd-test-results",
        "title": "SageTV authored-DVD playback test results",
        "type": "object",
        "required": [
            "fixtureId", "overallStatus", "environment", "tests",
            "protocolCoverage", "artifactRoot",
        ],
        "properties": {
            "fixtureId": {"const": FIXTURE_ID},
            "overallStatus": {
                "enum": ["passed", "failed", "blocked", "incomplete"]
            },
            "artifactRoot": {"type": "string", "minLength": 1},
            "environment": {"type": "object"},
            "protocolCoverage": {
                "type": "object",
                "required": ["commandsObserved"],
                "properties": {
                    "commandsObserved": {
                        "type": "array",
                        "items": {"type": "integer"},
                        "uniqueItems": True,
                    },
                    "missingCommands": {
                        "type": "array",
                        "items": {"type": "integer"},
                        "uniqueItems": True,
                    },
                },
            },
            "tests": {
                "type": "array",
                "minItems": 1,
                "items": {
                    "type": "object",
                    "required": [
                        "id", "status", "startedMonotonicNs",
                        "endedMonotonicNs", "assertions", "evidenceFiles",
                    ],
                    "properties": {
                        "id": {"type": "string", "minLength": 1},
                        "status": {
                            "enum": ["passed", "failed", "blocked", "skipped"]
                        },
                        "startedMonotonicNs": {"type": "integer", "minimum": 0},
                        "endedMonotonicNs": {"type": "integer", "minimum": 0},
                        "assertions": {"type": "array", "items": {"type": "object"}},
                        "measured": {"type": "object"},
                        "evidenceFiles": {
                            "type": "array",
                            "items": {"type": "string"},
                            "uniqueItems": True,
                        },
                        "failureAnalysis": {"type": ["string", "null"]},
                    },
                },
            },
        },
    }


def build_test_cases(menus: Sequence[MenuSpec], checkpoints: Sequence[dict[str, Any]]) -> list[dict[str, Any]]:
    root = next(menu for menu in menus if menu.id == "root")
    root_sequence = [button.id for button in root.buttons]
    cases: list[dict[str, Any]] = [
        test_case(
            "DVD_BOOT_001",
            "startup",
            "Launch DVD folder and enter the root menu without OPENURL",
            [
                {"action": "launchDvdFolder"},
                {"action": "waitForMenu", "menuId": "root", "timeoutMs": 15000},
                {"action": "captureScreenshot", "file": "dvd_boot_root.png"},
                {"action": "captureProtocolTrace", "file": "dvd_boot_protocol.json"},
            ],
            [
                {"type": "menuVisible", "menuId": "root"},
                {"type": "textVisible", "value": root.heading},
                {"type": "buttonSelected", "buttonId": root.buttons[0].id},
                {"type": "playerCreatedBeforeOpenUrl", "expected": True},
                {"type": "noCrashOrDisconnect", "expected": True},
            ],
            protocol=[
                {"command": 0, "name": "MEDIACMD_INIT", "required": True},
                {"command": 16, "name": "MEDIACMD_OPENURL", "required": False, "expectedAbsentForDvd": True},
                {"command": 23, "name": "MEDIACMD_PUSHBUFFER", "required": True},
            ],
        ),
        test_case(
            "DVD_MENU_001",
            "menu",
            "Root-menu directional navigation and wraparound",
            [
                {"action": "goToRootMenu"},
                *[
                    {"action": "pressKey", "key": "DOWN", "expectButton": button_id}
                    for button_id in root_sequence[1:] + root_sequence[:1]
                ],
                {"action": "pressKey", "key": "UP", "expectButton": root_sequence[-1]},
                {"action": "pressKey", "key": "LEFT", "expectButton": root_sequence[-1]},
                {"action": "pressKey", "key": "RIGHT", "expectButton": root_sequence[-1]},
            ],
            [
                {"type": "buttonSequence", "expected": root_sequence[1:] + root_sequence[:1]},
                {"type": "highlightColor", "rgb": "#FFFF00", "tolerancePerChannel": 32},
                {"type": "highlightInsideExpectedRect", "expected": True},
            ],
            protocol=[
                {"command": 34, "name": "MEDIACMD_DVD_SPUCTRL", "minimumCount": 6},
            ],
        ),
        test_case(
            "DVD_MENU_002",
            "menu",
            "Looping menu remains rendered, advancing, and interactive",
            [
                {"action": "goToRootMenu"},
                {"action": "captureScreenshot", "file": "menu_loop_before.png"},
                {"action": "waitMs", "value": 3500},
                {"action": "captureScreenshot", "file": "menu_loop_after.png"},
                {"action": "pressKey", "key": "DOWN", "expectButton": "root_languages"},
            ],
            [
                {"type": "menuVideoAdvances", "expected": True},
                {"type": "burnedMenuClockAdvances", "expected": True},
                {"type": "loopingMenuAcceptsInput", "expected": True},
                {"type": "menuLoopsWithoutPlaybackFailure", "expected": True},
            ],
            protocol=[
                {"command": 23, "name": "MEDIACMD_PUSHBUFFER", "minimumCount": 2},
                {"command": 34, "name": "MEDIACMD_DVD_SPUCTRL", "minimumCount": 2},
            ],
        ),
        test_case(
            "DVD_MENU_003",
            "navigation",
            "Visit every submenu and use every return-to-main-menu path",
            [
                {"action": "activateButton", "buttonId": "root_languages"},
                {"action": "assertMenu", "menuId": "languages"},
                {"action": "activateButton", "buttonId": "languages_audio_tracks"},
                {"action": "assertMenu", "menuId": "audio_tracks"},
                {"action": "activateButton", "buttonId": "audio_main_menu"},
                {"action": "assertMenu", "menuId": "root"},
                {"action": "activateButton", "buttonId": "root_languages"},
                {"action": "activateButton", "buttonId": "languages_subtitle_tracks"},
                {"action": "assertMenu", "menuId": "subtitle_tracks"},
                {"action": "activateButton", "buttonId": "subtitle_main_menu"},
                {"action": "activateButton", "buttonId": "root_chapters"},
                {"action": "assertMenu", "menuId": "chapters"},
                {"action": "activateButton", "buttonId": "chapters_main_menu"},
                {"action": "activateButton", "buttonId": "root_special_features"},
                {"action": "assertMenu", "menuId": "special_features"},
                {"action": "activateButton", "buttonId": "special_main_menu"},
            ],
            [
                {"type": "allExpectedMenusVisited", "expected": True},
                {"type": "finalMenu", "menuId": "root"},
                {"type": "noStaleOverlayFromPreviousMenu", "expected": True},
            ],
        ),
        test_case(
            "DVD_AUDIO_001",
            "streams",
            "Select and verify both AC-3 language tracks",
            [
                {"action": "selectMenuPath", "buttons": ["root_languages", "languages_audio_tracks", "audio_english", "languages_play_current"]},
                {"action": "verifyAudioTone", "expectedHz": 1000, "toleranceHz": 20},
                {"action": "pressKey", "key": "MENU"},
                {"action": "selectMenuPath", "buttons": ["root_languages", "languages_audio_tracks", "audio_spanish", "languages_play_current"]},
                {"action": "verifyAudioTone", "expectedHz": 440, "toleranceHz": 20},
            ],
            [
                {"type": "audioTrack", "logicalIndex": 0, "toneHz": 1000},
                {"type": "audioTrack", "logicalIndex": 1, "toneHz": 440},
                {"type": "audioTrackSwitchWithoutRestartFailure", "expected": True},
            ],
            protocol=[
                {"command": 36, "name": "MEDIACMD_DVD_STREAM", "streamType": 0,
                 "selectorsHex": ["0xBD80", "0xBD81"]},
            ],
        ),
        test_case(
            "DVD_SPU_001",
            "subtitles",
            "Verify English, Spanish, and subtitles-off modes",
            [
                {"action": "selectMenuPath", "buttons": ["root_languages", "languages_subtitle_tracks", "subtitle_english", "languages_play_current"]},
                {"action": "seekMs", "value": 6500},
                {"action": "captureScreenshot", "file": "subtitle_english.png"},
                {"action": "pressKey", "key": "MENU"},
                {"action": "selectMenuPath", "buttons": ["root_languages", "languages_subtitle_tracks", "subtitle_spanish", "languages_play_current"]},
                {"action": "seekMs", "value": 12500},
                {"action": "captureScreenshot", "file": "subtitle_spanish.png"},
                {"action": "pressKey", "key": "MENU"},
                {"action": "selectMenuPath", "buttons": ["root_languages", "languages_subtitle_tracks", "subtitle_off", "languages_play_current"]},
                {"action": "seekMs", "value": 6500},
                {"action": "captureScreenshot", "file": "subtitle_off.png"},
            ],
            [
                {"type": "subtitleTextContains", "value": "ENG DVD SPU"},
                {"type": "subtitleTextContains", "value": "SPA DVD SPU"},
                {"type": "normalSubtitleAbsentWhenOff", "expected": True},
                {"type": "subtitleClockMatchesBurnedPts", "toleranceMs": 100},
            ],
            protocol=[
                {"command": 36, "name": "MEDIACMD_DVD_STREAM", "streamType": 1,
                 "selectors": [64, 65, 128]},
            ],
        ),
        test_case(
            "DVD_RETURN_001",
            "navigation",
            "Return from active title to root menu with the Menu key",
            [
                {"action": "goToRootMenu"},
                {"action": "activateButton", "buttonId": "root_play_main"},
                {"action": "waitForTitle", "titleId": "main_feature"},
                {"action": "pressKey", "key": "MENU"},
                {"action": "waitForMenu", "menuId": "root"},
            ],
            [
                {"type": "menuVisible", "menuId": "root"},
                {"type": "staleSubtitleCleared", "expected": True},
                {"type": "staleHighlightClearedBeforeNewHighlight", "expected": True},
            ],
        ),
        test_case(
            "DVD_SPECIAL_001",
            "title",
            "Play the short special feature and return to the main menu at EOS",
            [
                {"action": "selectMenuPath", "buttons": ["root_special_features", "special_play"]},
                {"action": "waitForTitle", "titleId": "special_feature"},
                {"action": "seekMs", "value": 10500},
                {"action": "captureScreenshot", "file": "special_feature_chapter2.png"},
                {"action": "seekNearEnd", "secondsBeforeEnd": 1.0},
                {"action": "waitForMenu", "menuId": "root", "timeoutMs": 10000},
            ],
            [
                {"type": "burnInContains", "value": "TITLE ID special_feature"},
                {"type": "chapter", "value": 2},
                {"type": "eosReturnsToRootMenu", "expected": True},
            ],
        ),
        test_case(
            "DVD_PAUSE_001",
            "timing",
            "Pause and resume without advancing burned PTS or subtitle state",
            [
                {"action": "playMainFeatureWithEnglishSubtitles"},
                {"action": "seekMs", "value": 8500},
                {"action": "pause"},
                {"action": "captureScreenshot", "file": "pause_before.png"},
                {"action": "waitMs", "value": 2000},
                {"action": "captureScreenshot", "file": "pause_after.png"},
                {"action": "play"},
                {"action": "waitMs", "value": 1000},
            ],
            [
                {"type": "mediaClockFrozenWhilePaused", "toleranceMs": 100},
                {"type": "overlayRemainsVisibleWhilePaused", "expected": True},
                {"type": "clockAdvancesAfterResume", "expected": True},
            ],
        ),
        test_case(
            "DVD_STRESS_001",
            "stress",
            "Repeated chapter seeks, backward seeks, flushes, and menu transitions",
            [
                {"action": "playMainFeature"},
                {"action": "repeat", "count": 5, "steps": [
                    {"action": "seekMs", "value": 60500},
                    {"action": "seekMs", "value": 20500},
                    {"action": "seekMs", "value": 40500},
                    {"action": "pressKey", "key": "MENU"},
                    {"action": "activateButton", "buttonId": "root_play_main"},
                ]},
            ],
            [
                {"type": "noOldSpuAfterSeek", "expected": True},
                {"type": "noOldHighlightAfterCellChange", "expected": True},
                {"type": "noUnboundedParserBuffer", "expected": True},
                {"type": "noCrashOrDeadlock", "expected": True},
            ],
            protocol=[
                {"command": 22, "name": "MEDIACMD_FLUSH", "minimumCount": 5},
                {"command": 32, "name": "MEDIACMD_DVD_NEWCELL", "minimumCount": 5},
                {"command": 35, "name": "MEDIACMD_DVD_STC", "minimumCount": 1},
            ],
        ),
        test_case(
            "DVD_PROTOCOL_001",
            "protocol",
            "Observe all legacy DVD presentation commands",
            [
                {"action": "runCases", "caseIds": [
                    "DVD_BOOT_001", "DVD_MENU_001", "DVD_AUDIO_001", "DVD_SPU_001",
                    "DVD_SPECIAL_001", "DVD_STRESS_001",
                ]},
                {"action": "captureProtocolCoverage", "file": "dvd_command_coverage.json"},
            ],
            [
                {"type": "legacyCommandsObserved", "commands": [32, 33, 34, 35, 36, 37]},
                {"type": "pushBufferPackHeadersObserved", "startCodeHex": "0x000001BA"},
                {"type": "privateStream1Observed", "startCodeHex": "0x000001BD"},
                {"type": "spuPhysicalStreamsObserved", "idsHex": ["0x20", "0x21"]},
            ],
        ),
        test_case(
            "DVD_RECONNECT_001",
            "lifecycle",
            "Disconnect during playback and relaunch without stale DVD state",
            [
                {"action": "playMainFeatureWithSpanishSubtitles"},
                {"action": "seekMs", "value": 12500},
                {"action": "disconnectClient"},
                {"action": "reconnectClient"},
                {"action": "launchDvdFolder"},
                {"action": "waitForMenu", "menuId": "root"},
            ],
            [
                {"type": "freshRootMenu", "expected": True},
                {"type": "noOldSubtitleBitmap", "expected": True},
                {"type": "noOldPendingSpuEvents", "expected": True},
                {"type": "noOldPtsEpoch", "expected": True},
            ],
        ),
    ]

    # Every authored button must be selected at least once so the Android overlay
    # can be compared against the exact SPUCTRL rectangle in the manifest.
    for menu in menus:
        if menu.id == "root":
            continue
        sequence = [button.id for button in menu.buttons]
        cases.append(test_case(
            f"DVD_MENU_NAV_{menu.number:02d}",
            "menu",
            f"Validate directional navigation and every highlight in {menu.id}",
            [
                {"action": "goToMenu", "menuId": menu.id},
                {"action": "assertButtonSelected", "buttonId": sequence[0]},
                *[
                    {
                        "action": "pressKey",
                        "key": "DOWN",
                        "expectButton": button_id,
                        "captureOverlayTelemetry": f"menu-{menu.id}-{button_id}.json",
                        "captureScreenshot": f"menu-{menu.id}-{button_id}.png",
                    }
                    for button_id in sequence[1:] + sequence[:1]
                ],
                {"action": "pressKey", "key": "UP", "expectButton": sequence[-1]},
                {"action": "pressKey", "key": "LEFT", "expectButton": sequence[-1]},
                {"action": "pressKey", "key": "RIGHT", "expectButton": sequence[-1]},
            ],
            [
                {"type": "buttonSequence", "menuId": menu.id,
                 "expected": sequence[1:] + sequence[:1]},
                {"type": "allButtonRectsMatchManifest", "menuId": menu.id,
                 "coordinateSpace": "expectedLegacySpuCtrlRect"},
                {"type": "highlightColor", "rgb": "#FFFF00", "tolerancePerChannel": 32},
                {"type": "noStaleHighlightBetweenButtons", "expected": True},
            ],
            protocol=[
                {"command": 34, "name": "MEDIACMD_DVD_SPUCTRL",
                 "minimumCount": len(sequence) + 2},
            ],
        ))

    cases.extend([
        test_case(
            "DVD_MENU_ACTION_001",
            "navigation",
            "Exercise the remaining submenu buttons and reset-default action",
            [
                {"action": "selectMenuPath", "buttons": [
                    "root_languages", "languages_audio_tracks", "audio_spanish",
                    "languages_subtitle_tracks", "subtitle_spanish",
                ]},
                {"action": "activateButton", "buttonId": "languages_reset_defaults"},
                {"action": "assertMenu", "menuId": "languages"},
                {"action": "assertSelectedStreams", "audioIndex": 0,
                 "subtitleMode": "off"},
                {"action": "activateButton", "buttonId": "languages_main_menu"},
                {"action": "selectMenuPath", "buttons": [
                    "root_languages", "languages_audio_tracks", "audio_languages",
                ]},
                {"action": "assertMenu", "menuId": "languages"},
                {"action": "activateButton", "buttonId": "languages_subtitle_tracks"},
                {"action": "activateButton", "buttonId": "subtitle_languages"},
                {"action": "assertMenu", "menuId": "languages"},
                {"action": "activateButton", "buttonId": "languages_main_menu"},
            ],
            [
                {"type": "allMissingButtonActionsExecuted", "buttonIds": [
                    "languages_reset_defaults", "languages_main_menu",
                    "audio_languages", "subtitle_languages",
                ]},
                {"type": "selectedAudioIndex", "value": 0},
                {"type": "normalSubtitleMode", "value": "off"},
                {"type": "finalMenu", "menuId": "root"},
            ],
        ),
        test_case(
            "DVD_MENU_ACTIVATE_001",
            "menu",
            "Verify selected and activated button palette composition",
            [
                {"action": "goToRootMenu"},
                {"action": "selectButton", "buttonId": "root_languages"},
                {"action": "captureScreenshot", "file": "root_languages_selected.png"},
                {"action": "captureHighRateFramesAroundActivation", "buttonId": "root_languages",
                 "durationMs": 1200, "outputPrefix": "root_languages_activate"},
                {"action": "activateButton", "buttonId": "root_languages"},
                {"action": "waitForMenu", "menuId": "languages"},
            ],
            [
                {"type": "selectedOutlineColor", "rgb": "#FFFF00",
                 "tolerancePerChannel": 32},
                {"type": "activationOutlineColor", "rgb": "#00FF00",
                 "tolerancePerChannel": 32, "mayUseProtocolTelemetry": True},
                {"type": "highlightAppliedOnlyInsideExpectedRect", "expected": True},
                {"type": "destinationMenu", "menuId": "languages"},
            ],
            protocol=[
                {"command": 34, "name": "MEDIACMD_DVD_SPUCTRL",
                 "requireSelectionAndActivationPaletteEvidence": True},
            ],
        ),
        test_case(
            "DVD_SPU_FORCED_MENU_001",
            "subtitles",
            "Normal subtitles off must not suppress forced DVD menu subpictures",
            [
                {"action": "selectMenuPath", "buttons": [
                    "root_languages", "languages_subtitle_tracks", "subtitle_off",
                ]},
                {"action": "activateButton", "buttonId": "languages_main_menu"},
                {"action": "captureScreenshot", "file": "forced_menu_with_subtitles_off.png"},
                {"action": "activateButton", "buttonId": "root_play_main"},
                {"action": "seekMs", "value": 6500},
                {"action": "captureScreenshot", "file": "normal_spu_off_after_forced_menu.png"},
            ],
            [
                {"type": "menuHighlightVisibleWithSubtitlesOff", "expected": True},
                {"type": "normalSubtitleAbsentInTitle", "expected": True},
                {"type": "forcedAndNormalPolicySeparated", "expected": True},
            ],
        ),
        test_case(
            "DVD_SPU_TIMING_001",
            "timing",
            "Measure SPU start and stop timing at a known cue boundary",
            [
                {"action": "playMainFeatureWithEnglishSubtitles"},
                {"action": "seekMs", "value": 5600},
                {"action": "recordOverlayTimeline", "startMediaMs": 5600,
                 "endMediaMs": 7900, "sampleIntervalMs": 20,
                 "file": "spu_timing_eng_cue4.json"},
                {"action": "captureScreenshotAtMediaTime", "mediaMs": 5900,
                 "file": "spu_before_cue4.png"},
                {"action": "captureScreenshotAtMediaTime", "mediaMs": 6200,
                 "file": "spu_during_cue4.png"},
                {"action": "captureScreenshotAtMediaTime", "mediaMs": 7750,
                 "file": "spu_after_cue4.png"},
            ],
            [
                {"type": "subtitleAbsentAt", "mediaMs": 5900},
                {"type": "subtitleCueVisibleAt", "mediaMs": 6200,
                 "language": "eng", "cue": 4},
                {"type": "subtitleAbsentAt", "mediaMs": 7750},
                {"type": "overlayStartNear", "mediaMs": 6000, "toleranceMs": 120},
                {"type": "overlayStopNear", "mediaMs": 7550, "toleranceMs": 120},
                {"type": "noLateEventFromPreviousPtsEpoch", "expected": True},
            ],
        ),
        test_case(
            "DVD_AVSYNC_001",
            "timing",
            "Measure visual, audio-amplitude, player-clock, and subtitle alignment",
            [
                {"action": "playMainFeatureWithEnglishSubtitles"},
                {"action": "seekMs", "value": 8500},
                {"action": "captureSynchronizedAvWindow", "durationMs": 6500,
                 "audioPcmFile": "avsync_eng.wav", "videoFramesDir": "avsync_eng_frames",
                 "telemetryFile": "avsync_eng_telemetry.json"},
                {"action": "switchAudioUsingDvdMenu", "audioIndex": 1},
                {"action": "seekMs", "value": 8500},
                {"action": "captureSynchronizedAvWindow", "durationMs": 3500,
                 "audioPcmFile": "avsync_spa.wav", "videoFramesDir": "avsync_spa_frames",
                 "telemetryFile": "avsync_spa_telemetry.json"},
            ],
            [
                {"type": "wholeSecondVisualPulseDetected", "minimumCount": 5},
                {"type": "wholeSecondAudioAmplitudePulseDetected", "minimumCount": 5},
                {"type": "audioVisualPulseAlignment", "toleranceMs": 100},
                {"type": "subtitleBurnInPtsAlignment", "toleranceMs": 120},
                {"type": "audioTone", "logicalIndex": 0, "expectedHz": 1000,
                 "toleranceHz": 20},
                {"type": "audioTone", "logicalIndex": 1, "expectedHz": 440,
                 "toleranceHz": 20},
            ],
        ),
        test_case(
            "DVD_STREAM_PERSIST_001",
            "streams",
            "Audio and subtitle selections persist across chapters, menus, and titles",
            [
                {"action": "selectMenuPath", "buttons": [
                    "root_languages", "languages_audio_tracks", "audio_spanish",
                    "languages_subtitle_tracks", "subtitle_english",
                    "languages_play_current",
                ]},
                {"action": "assertSelectedStreams", "audioIndex": 1,
                 "subtitleIndex": 0, "subtitleMode": "enabled"},
                {"action": "pressKey", "key": "NEXT_CHAPTER"},
                {"action": "assertSelectedStreams", "audioIndex": 1,
                 "subtitleIndex": 0, "subtitleMode": "enabled"},
                {"action": "pressKey", "key": "MENU"},
                {"action": "selectMenuPath", "buttons": [
                    "root_special_features", "special_play",
                ]},
                {"action": "assertSelectedStreams", "audioIndex": 1,
                 "subtitleIndex": 0, "subtitleMode": "enabled"},
                {"action": "pressKey", "key": "MENU"},
                {"action": "activateButton", "buttonId": "root_play_main"},
                {"action": "assertSelectedStreams", "audioIndex": 1,
                 "subtitleIndex": 0, "subtitleMode": "enabled"},
            ],
            [
                {"type": "selectionPreservedAcrossChapter", "expected": True},
                {"type": "selectionPreservedAcrossMenu", "expected": True},
                {"type": "selectionPreservedAcrossTitle", "expected": True},
                {"type": "expectedAudioToneHz", "value": 440},
                {"type": "expectedSubtitleLanguage", "value": "eng"},
            ],
        ),
        test_case(
            "DVD_MAIN_EOS_001",
            "title",
            "Main feature reaches EOS and returns to the root menu",
            [
                {"action": "goToRootMenu"},
                {"action": "activateButton", "buttonId": "root_play_main"},
                {"action": "seekNearEnd", "secondsBeforeEnd": 1.0},
                {"action": "captureProtocolTrace", "file": "main_eos_protocol.json"},
                {"action": "waitForMenu", "menuId": "root", "timeoutMs": 10000},
            ],
            [
                {"type": "eosReturnsToRootMenu", "expected": True},
                {"type": "oldTitleSpuCleared", "expected": True},
                {"type": "rootDefaultButtonSelected", "buttonId": "root_play_main"},
            ],
            protocol=[
                {"command": 23, "name": "MEDIACMD_PUSHBUFFER",
                 "flagsIncludeHex": "0x80", "eosFlagMustBeTreatedAsBitmask": True},
            ],
        ),
        test_case(
            "DVD_CHAPTER_NAV_001",
            "chapter",
            "Use next/previous chapter controls across known chapter boundaries",
            [
                {"action": "playMainFeature"},
                {"action": "pressKey", "key": "NEXT_CHAPTER"},
                {"action": "waitForChapter", "chapter": 2},
                {"action": "pressKey", "key": "NEXT_CHAPTER"},
                {"action": "waitForChapter", "chapter": 3},
                {"action": "pressKey", "key": "PREVIOUS_CHAPTER"},
                {"action": "waitForChapter", "chapter": 2},
                {"action": "captureScreenshot", "file": "chapter_next_prev.png"},
            ],
            [
                {"type": "chapterSequence", "expected": [2, 3, 2]},
                {"type": "chapterLandingTimesSeconds", "expected": [20.0, 40.0, 20.0],
                 "toleranceMs": 750},
                {"type": "noStaleSpuAcrossChapterJump", "expected": True},
            ],
        ),
        test_case(
            "DVD_DRAIN_001",
            "protocol",
            "Drain acknowledgement is delayed until every queue is empty",
            [
                {"action": "goToRootMenu"},
                {"action": "enableDetailedDrainTelemetry"},
                {"action": "observeDrainHandshake", "file": "drain_handshake.json"},
            ],
            [
                {"type": "minus2NotReturnedWhileStartupQueueHasBytes", "expected": True},
                {"type": "minus2NotReturnedWhileDataSourceHasBytes", "expected": True},
                {"type": "minus2NotReturnedWhileDecoderHasQueuedMedia", "expected": True},
                {"type": "minus2NotReturnedWhenAnyRequiredMetricUnknown", "expected": True},
                {"type": "minus2EventuallyReturnedAfterDrain", "expected": True},
                {"type": "menuStillEnteredAfterDrain", "expected": True},
            ],
            protocol=[
                {"command": 23, "name": "MEDIACMD_PUSHBUFFER", "size": 0,
                 "flagsIncludeHex": "0x100"},
                {"reply": -2, "onlyWhenAllQueuesDrained": True},
            ],
        ),
        test_case(
            "DVD_SURFACE_001",
            "android-surface",
            "Recreate the Android video/overlay surfaces without losing DVD state",
            [
                {"action": "playMainFeatureWithSpanishSubtitles"},
                {"action": "seekMs", "value": 12500},
                {"action": "captureScreenshot", "file": "surface_before.png"},
                {"action": "backgroundAndForegroundApp"},
                {"action": "waitForStableFrame", "timeoutMs": 5000},
                {"action": "captureScreenshot", "file": "surface_after.png"},
                {"action": "pressKey", "key": "MENU"},
                {"action": "captureScreenshot", "file": "surface_root_menu.png"},
            ],
            [
                {"type": "playbackResumedAfterSurfaceRecreation", "expected": True},
                {"type": "subtitleOverlayRestoredAtCorrectVideoRect", "expected": True},
                {"type": "noDuplicateOverlayView", "expected": True},
                {"type": "titleOverlayClearedOnMenu", "expected": True},
                {"type": "menuHighlightAlignedWithVideoDestination", "expected": True},
            ],
        ),
        test_case(
            "DVD_GEOMETRY_001",
            "android-surface",
            "Verify DVD_FORMAT and overlay geometry against the player video rectangle",
            [
                {"action": "goToRootMenu"},
                {"action": "captureVideoAndOverlayGeometry", "file": "geometry_root.json"},
                {"action": "activateButton", "buttonId": "root_play_main"},
                {"action": "captureVideoAndOverlayGeometry", "file": "geometry_title.json"},
            ],
            [
                {"type": "overlayUsesSameDestinationRectAsVideo", "expected": True},
                {"type": "dvdCanvasSize", "width": 720, "height": 480},
                {"type": "sourceCropAppliedBeforeDestinationScale", "expected": True},
                {"type": "noOverlayPixelsOutsideVideoDestination", "expected": True},
            ],
            protocol=[
                {"command": 37, "name": "MEDIACMD_DVD_FORMAT", "minimumCount": 1},
            ],
        ),
        test_case(
            "DVD_CLUT_ALPHA_001",
            "subtitles",
            "Verify CLUT conversion, four SPU color roles, alpha, and highlight override",
            [
                {"action": "playMainFeatureWithEnglishSubtitles"},
                {"action": "seekMs", "value": 6500},
                {"action": "capturePlayerTelemetry",
                 "file": "clut_alpha_title_english.json"},
                {"action": "captureScreenshot",
                 "file": "clut_alpha_title_english.png"},
                {"action": "pressKey", "key": "MENU"},
                {"action": "selectButton", "buttonId": "root_chapters"},
                {"action": "capturePlayerTelemetry",
                 "file": "clut_alpha_menu_highlight.json"},
                {"action": "captureScreenshot",
                 "file": "clut_alpha_menu_highlight.png"},
            ],
            [
                {"type": "dvdClutEntryCount", "value": 16},
                {"type": "normalSpuColorRolesPresent",
                 "roles": ["transparent", "fill", "outline", "shadow"]},
                {"type": "fourBitAlphaExpandedCorrectly", "expected": True},
                {"type": "yCrCbToArgbConversionWithinTolerance",
                 "tolerancePerChannel": 3},
                {"type": "highlightPaletteOverridesOnlySelectedRectangle",
                 "buttonId": "root_chapters"},
                {"type": "transparentPixelsRemainTransparent", "expected": True},
            ],
            protocol=[
                {"command": 33, "name": "MEDIACMD_DVD_CLUT",
                 "payloadBytes": 64, "minimumCount": 1},
                {"command": 34, "name": "MEDIACMD_DVD_SPUCTRL",
                 "minimumCount": 1},
            ],
        ),
        test_case(
            "DVD_PTS_CELL_001",
            "timing",
            "Validate NEWCELL offset, STC, 33-bit PTS mapping, and stale-event rejection",
            [
                {"action": "playMainFeatureWithEnglishSubtitles"},
                {"action": "seekMs", "value": 39500},
                {"action": "captureProtocolTrace",
                 "file": "pts_cell_transition_protocol.json"},
                {"action": "recordOverlayTimeline", "startMediaMs": 39500,
                 "endMediaMs": 41500, "sampleIntervalMs": 20,
                 "file": "pts_cell_transition_overlay.json"},
                {"action": "waitForChapter", "chapter": 3},
                {"action": "captureScreenshot",
                 "file": "pts_cell_transition_chapter3.png"},
            ],
            [
                {"type": "newCellOffsetAppliedAsSigned45kTimesTwo",
                 "expected": True},
                {"type": "adjustedPesPtsMatchesPlayerClock",
                 "toleranceMs": 100},
                {"type": "stcUses45kProtocolUnits", "expected": True},
                {"type": "ptsArithmeticModulo33Bits", "expected": True},
                {"type": "oldEpochSpuEventsDiscarded", "expected": True},
                {"type": "burnInContains", "value": "EXPECTED CHAPTER 3"},
            ],
            protocol=[
                {"command": 32, "name": "MEDIACMD_DVD_NEWCELL",
                 "minimumCount": 1},
                {"command": 35, "name": "MEDIACMD_DVD_STC",
                 "minimumCount": 1},
            ],
        ),
        test_case(
            "DVD_ENGINE_REPLAY_001",
            "engine-replay",
            "Replay captured DVD protocol and MPEG-PS with adversarial chunk boundaries",
            [
                {"action": "captureCompleteDvdSession", "file": "dvd_session_capture.bin",
                 "commandTraceFile": "dvd_session_commands.json"},
                {"action": "replayPresentationEngine", "chunkSizes":
                    [1, 2, 3, 7, 31, 257, 2048, 32768],
                 "resultFile": "dvd_replay_results.json"},
            ],
            [
                {"type": "identicalSpuPacketSequenceForAllChunkSizes", "expected": True},
                {"type": "identicalOverlayEventSequenceForAllChunkSizes", "expected": True},
                {"type": "identicalRenderedFrameHashesForAllChunkSizes", "expected": True},
                {"type": "packHeadersDoNotBlockPrivateStreamExtraction", "expected": True},
                {"type": "pendingParserAndAssemblerBuffersBounded", "expected": True},
                {"type": "forwardProgressOnEveryReplay", "expected": True},
            ],
        ),
    ])

    for checkpoint_spec in checkpoints:
        cases.append(test_case(
            f"DVD_CHECKPOINT_{checkpoint_spec['id'].upper()}",
            "screenshot-checkpoint",
            f"Validate {checkpoint_spec['id']} against the shared fixture clock",
            [
                {"action": "prepareTitleAndSubtitleModeFromCheckpoint", "checkpoint": checkpoint_spec},
                {"action": "seekMs", "value": round(float(checkpoint_spec["seekSeconds"]) * 1000)},
                {"action": "waitForStableFrame", "timeoutMs": 5000},
                {"action": "captureScreenshot", "file": checkpoint_spec["screenshotFile"]},
                {"action": "capturePlayerTelemetry", "file": f"{checkpoint_spec['id']}.json"},
            ],
            [
                {"type": "mediaTimeNear", "seconds": checkpoint_spec["seekSeconds"],
                 "toleranceMs": checkpoint_spec["mediaTimeToleranceMs"]},
                {"type": "chapter", "value": checkpoint_spec["expectedChapter"]},
                {"type": "burnInContainsAll", "values": checkpoint_spec["burnInAssertions"]},
                {"type": "subtitleExpectation", "value": checkpoint_spec["subtitle"]},
            ],
        ))

    for chapter_index, seconds in enumerate(MAIN_CHAPTERS, 1):
        cases.append(test_case(
            f"DVD_CHAPTER_{chapter_index:02d}",
            "chapter",
            f"Jump directly to main-feature chapter {chapter_index}",
            [
                {"action": "goToRootMenu"},
                {"action": "activateButton", "buttonId": "root_chapters"},
                {"action": "activateButton", "buttonId": f"chapter_{chapter_index}"},
                {"action": "waitForStableFrame", "timeoutMs": 5000},
                {"action": "captureScreenshot", "file": f"chapter_{chapter_index:02d}.png"},
            ],
            [
                {"type": "title", "titleId": "main_feature"},
                {"type": "chapter", "value": chapter_index},
                {"type": "mediaTimeNear", "seconds": seconds, "toleranceMs": 750},
                {"type": "burnInContains", "value": f"EXPECTED CHAPTER {chapter_index}"},
            ],
        ))

    return cases


def iter_nested_dicts(value: Any) -> Iterable[dict[str, Any]]:
    if isinstance(value, dict):
        yield value
        for nested in value.values():
            yield from iter_nested_dicts(nested)
    elif isinstance(value, list):
        for nested in value:
            yield from iter_nested_dicts(nested)


def validate_plan_consistency(
    *,
    main_duration: int,
    special_duration: int,
    menus: Sequence[MenuSpec],
    main_cues: Sequence[dict[str, Any]],
    special_cues: Sequence[dict[str, Any]],
    checkpoints: Sequence[dict[str, Any]],
    test_cases: Sequence[dict[str, Any]],
) -> dict[str, Any]:
    """Fail the build when authored expectations and the Codex plan disagree."""

    errors: list[str] = []
    menu_by_id = {menu.id: menu for menu in menus}
    if len(menu_by_id) != len(menus):
        errors.append("menu IDs are not unique")
    menu_numbers = [menu.number for menu in menus]
    if len(set(menu_numbers)) != len(menu_numbers):
        errors.append("menu numbers are not unique")
    if sorted(menu_numbers) != list(range(1, len(menus) + 1)):
        errors.append(f"menu numbers must be contiguous from 1: {menu_numbers}")
    if "root" not in menu_by_id or menu_by_id.get("root", MenuSpec("", 0, "", 0, None, [])).entry != "root":
        errors.append("a titleset root-entry menu is required")

    all_button_ids: set[str] = set()
    total_buttons = 0
    for menu in menus:
        if not menu.buttons:
            errors.append(f"menu {menu.id} has no buttons")
            continue
        local_ids = {button.id for button in menu.buttons}
        if len(local_ids) != len(menu.buttons):
            errors.append(f"menu {menu.id} has duplicate button IDs")
        duplicate_global = all_button_ids.intersection(local_ids)
        if duplicate_global:
            errors.append(f"button IDs reused across menus: {sorted(duplicate_global)}")
        all_button_ids.update(local_ids)
        total_buttons += len(menu.buttons)

        for button in menu.buttons:
            if button.rect is None:
                errors.append(f"button {button.id} has no rectangle")
            else:
                rect = button.rect
                if not (0 <= rect.x0 < rect.x1 <= WIDTH and 0 <= rect.y0 < rect.y1 <= HEIGHT):
                    errors.append(f"button {button.id} rectangle is out of bounds: {rect}")
                if rect.y0 % 2 or rect.y1 % 2:
                    errors.append(f"button {button.id} y coordinates must be even: {rect}")
            for direction, target in (
                ("up", button.up), ("down", button.down),
                ("left", button.left), ("right", button.right),
            ):
                if target not in local_ids:
                    errors.append(
                        f"button {button.id} {direction} target {target!r} is not in menu {menu.id}"
                    )

            destination = button.expected.get("destination")
            if isinstance(destination, dict):
                destination_type = destination.get("type")
                if destination_type == "menu":
                    target_menu = destination.get("menuId")
                    if target_menu not in menu_by_id:
                        errors.append(f"button {button.id} targets unknown menu {target_menu!r}")
                elif destination_type == "title":
                    target_title = destination.get("titleId")
                    if target_title not in {"main_feature", "special_feature"}:
                        errors.append(f"button {button.id} targets unknown title {target_title!r}")
                    chapter = destination.get("chapter")
                    chapter_count = (
                        len(MAIN_CHAPTERS) if target_title == "main_feature"
                        else len(SPECIAL_CHAPTERS)
                    )
                    if not isinstance(chapter, int) or not 1 <= chapter <= chapter_count:
                        errors.append(f"button {button.id} has invalid chapter destination {chapter!r}")
                else:
                    errors.append(f"button {button.id} has unknown destination type {destination_type!r}")

    title_settings = {
        "main_feature": (float(main_duration), MAIN_CHAPTERS, main_cues),
        "special_feature": (float(special_duration), SPECIAL_CHAPTERS, special_cues),
    }
    cue_digests: dict[str, str] = {}
    for title_id, (duration, chapters, cues) in title_settings.items():
        previous_start = -1.0
        for expected_index, cue in enumerate(cues, 1):
            try:
                cue_number = int(cue["cue"])
                start = float(cue["startSeconds"])
                end = float(cue["endSeconds"])
            except (KeyError, TypeError, ValueError) as error:
                errors.append(f"{title_id} has malformed cue {expected_index}: {error}")
                continue
            if cue_number != expected_index:
                errors.append(
                    f"{title_id} cue numbering mismatch: expected {expected_index}, got {cue_number}"
                )
            if not (0 <= start < end <= duration):
                errors.append(f"{title_id} cue {cue_number} is outside title: {start}..{end}")
            if start <= previous_start:
                errors.append(f"{title_id} cue starts are not strictly increasing at {cue_number}")
            previous_start = start
            if int(cue.get("nearestVideoFrame", -1)) != nearest_frame(start):
                errors.append(f"{title_id} cue {cue_number} nearest-frame value is inconsistent")
            if int(cue.get("chapter", -1)) != current_chapter(start, chapters):
                errors.append(f"{title_id} cue {cue_number} chapter value is inconsistent")
        cue_digests[title_id] = cue_clock_digest(cues)

    checkpoint_ids: set[str] = set()
    for item in checkpoints:
        checkpoint_id = str(item.get("id", ""))
        if not checkpoint_id or checkpoint_id in checkpoint_ids:
            errors.append(f"invalid or duplicate checkpoint ID: {checkpoint_id!r}")
        checkpoint_ids.add(checkpoint_id)
        title_id = item.get("titleId")
        if title_id not in title_settings:
            errors.append(f"checkpoint {checkpoint_id} targets unknown title {title_id!r}")
            continue
        duration, chapters, _ = title_settings[title_id]
        seconds = float(item.get("seekSeconds", -1))
        if not 0 <= seconds < duration:
            errors.append(f"checkpoint {checkpoint_id} is outside title duration: {seconds}")
        if int(item.get("expectedChapter", -1)) != current_chapter(seconds, chapters):
            errors.append(f"checkpoint {checkpoint_id} has an inconsistent chapter")

    test_ids = [str(case.get("id", "")) for case in test_cases]
    if len(set(test_ids)) != len(test_ids):
        errors.append("test case IDs are not unique")
    if any(not case_id for case_id in test_ids):
        errors.append("one or more test cases have an empty ID")

    action_vocabulary = build_action_vocabulary()
    used_actions: set[str] = set()
    referenced_buttons: set[str] = set()
    for case in test_cases:
        if not case.get("steps"):
            errors.append(f"test {case.get('id')} has no steps")
        if not case.get("assertions"):
            errors.append(f"test {case.get('id')} has no assertions")
        for node in iter_nested_dicts(case):
            action = node.get("action")
            if isinstance(action, str):
                used_actions.add(action)
            for key in ("buttonId", "expectButton"):
                value = node.get(key)
                if isinstance(value, str) and value in all_button_ids:
                    referenced_buttons.add(value)
            for key in ("buttons", "buttonIds", "expected"):
                value = node.get(key)
                if isinstance(value, list):
                    referenced_buttons.update(
                        item for item in value
                        if isinstance(item, str) and item in all_button_ids
                    )

    missing_actions = used_actions.difference(action_vocabulary)
    if missing_actions:
        errors.append(f"actions missing from action vocabulary: {sorted(missing_actions)}")
    unused_actions = set(action_vocabulary).difference(used_actions)
    if unused_actions:
        errors.append(f"unused action vocabulary entries: {sorted(unused_actions)}")
    missing_button_coverage = all_button_ids.difference(referenced_buttons)
    if missing_button_coverage:
        errors.append(
            f"buttons absent from Codex test coverage: {sorted(missing_button_coverage)}"
        )

    required_ids = {
        "DVD_BOOT_001", "DVD_MENU_001", "DVD_MENU_002", "DVD_AUDIO_001",
        "DVD_SPU_001", "DVD_SPU_TIMING_001", "DVD_AVSYNC_001",
        "DVD_MAIN_EOS_001", "DVD_SPECIAL_001", "DVD_DRAIN_001",
        "DVD_PROTOCOL_001", "DVD_ENGINE_REPLAY_001", "DVD_SURFACE_001",
        "DVD_CLUT_ALPHA_001", "DVD_PTS_CELL_001",
    }
    missing_required = required_ids.difference(test_ids)
    if missing_required:
        errors.append(f"required test cases are missing: {sorted(missing_required)}")

    if errors:
        raise RuntimeError("fixture plan consistency validation failed:\n- " + "\n- ".join(errors))

    return {
        "status": "passed",
        "menuCount": len(menus),
        "buttonCount": total_buttons,
        "allButtonsReferencedByTests": True,
        "mainCueCountPerLanguage": len(main_cues),
        "specialCueCountPerLanguage": len(special_cues),
        "sharedSubtitleClockDigests": cue_digests,
        "checkpointCount": len(checkpoints),
        "testCaseCount": len(test_cases),
        "actionCount": len(used_actions),
        "uniqueTestIds": True,
        "uniqueMenuAndButtonIds": True,
        "buttonRectanglesInBoundsAndInterlaceAligned": True,
    }


def build_protocol_manifest() -> dict[str, Any]:
    return {
        "playerStartup": {
            "dvdPathMaySendOpenUrl": False,
            "requiredSequencePrefix": ["MEDIACMD_INIT", "MEDIACMD_PUSHBUFFER"],
            "requirePlayerCreationBeforeFirstPushData": True,
        },
        "legacyCommands": [
            {"id": 32, "name": "MEDIACMD_DVD_NEWCELL", "payload": "length-prefixed signed 45-kHz PTS offset"},
            {"id": 33, "name": "MEDIACMD_DVD_CLUT", "payload": "length-prefixed 16 x 32-bit DVD Y/Cr/Cb CLUT"},
            {"id": 34, "name": "MEDIACMD_DVD_SPUCTRL", "payload": "length-prefixed xStart,xEnd,yStart,yEnd,palette"},
            {"id": 35, "name": "MEDIACMD_DVD_STC", "payload": "32-bit 45-kHz STC"},
            {"id": 36, "name": "MEDIACMD_DVD_STREAM", "payload": "stream type + physical selector"},
            {"id": 37, "name": "MEDIACMD_DVD_FORMAT", "payload": "display-format integer"},
        ],
        "drainHandshake": {
            "request": {"command": 23, "size": 0, "flagsMaskHex": "0x100"},
            "replyWhenAllQueuesAndDecoderAreDrained": -2,
            "mustNotReplyMinus2WhilePlayerStateUnknown": True,
        },
        "pushBuffer": {
            "eosFlagIsBitmaskHex": "0x80",
            "doNotCompareFlagsForExactEquality": True,
            "normalDvdContainer": "MPEG-2 Program Stream",
            "packStartCodeHex": "0x000001BA",
            "privateStream1StartCodeHex": "0x000001BD",
        },
        "subpicture": {
            "normalPhysicalStreamsHex": ["0x20", "0x21"],
            "legacyDisableSelectorSupportedByClient": 62,
            "dvdRegisterOffValue": 63,
            "dvdRegisterEnabledValues": [0, 1],
            "expectedMiniClientEnabledSelectors": [64, 65],
            "expectedMiniClientOffSelector": 128,
            "rle": "DVD 2-bit nibble RLE with separate even/odd fields",
        },
    }


def build_manifest(
    *,
    main_duration: int,
    special_duration: int,
    cue_interval: float,
    cue_duration: float,
    menu_duration: float,
    menus: Sequence[MenuSpec],
    main_cues: Sequence[dict[str, Any]],
    special_cues: Sequence[dict[str, Any]],
    checkpoints: Sequence[dict[str, Any]],
    test_cases: Sequence[dict[str, Any]],
    plan_validation: dict[str, Any],
    tool_versions: dict[str, str | None],
    build_mode: str,
    probes: dict[str, Any] | None = None,
    dvd_files: list[dict[str, Any]] | None = None,
) -> dict[str, Any]:
    return {
        "schemaVersion": 2,
        "fixtureId": FIXTURE_ID,
        "name": "OpenSageTV Vibe deterministic authored DVD",
        "buildMode": build_mode,
        "purpose": (
            "End-to-end SageTV MiniDVDPlayer and Android DVD presentation-engine validation"
        ),
        "format": {
            "disc": "DVD-Video folder",
            "region": "unrestricted-authored-test-content",
            "videoSystem": "NTSC",
            "resolution": {"width": WIDTH, "height": HEIGHT},
            "displayAspect": DISPLAY_ASPECT,
            "frameRate": {"numerator": FPS_NUM, "denominator": FPS_DEN, "text": FPS},
            "sourceFrameRate": SOURCE_FPS,
            "interlaced": True,
            "fieldOrder": FIELD_ORDER,
            "timebaseHz": TIMEBASE_HZ,
        },
        "authoringDefaults": {
            "initialAudioIndex": 0,
            "initialSubtitleMode": "off",
            "initialSubtitleDvdRegisterValue": 63,
            "cueIntervalSeconds": cue_interval,
            "cueDurationSeconds": cue_duration,
            "menuVideoDurationSeconds": menu_duration,
            "menuPlayback": "continuous-loop",
            "menuDefaultButtonOrdinal": 1,
        },
        "menus": [menu_manifest(menu, menu_duration) for menu in menus],
        "titles": [
            title_manifest(
                "main_feature", 1, main_duration, MAIN_CHAPTERS, main_cues, "testsrc2"
            ),
            title_manifest(
                "special_feature", 2, special_duration, SPECIAL_CHAPTERS,
                special_cues, "smptebars"
            ),
        ],
        "screenshotCheckpoints": list(checkpoints),
        "protocolExpectations": build_protocol_manifest(),
        "testCaseFile": TEST_CASES_NAME,
        "testCaseCount": len(test_cases),
        "harnessContractFile": HARNESS_CONTRACT_NAME,
        "resultSchemaFile": RESULT_SCHEMA_NAME,
        "planValidation": plan_validation,
        "harnessActionVocabulary": build_action_vocabulary(),
        "coverage": {
            "rootMenu": True,
            "submenus": [menu.id for menu in menus if menu.id != "root"],
            "mainAndSpecialTitles": True,
            "twoAc3Languages": True,
            "twoDvdSpuLanguagesFromSharedClock": True,
            "chapters": True,
            "loopingMenuPlayback": True,
            "menuButtonHighlights": True,
            "legacyCommands32Through37": True,
            "newCellPtsDiscontinuities": True,
            "subtitleOff": True,
            "audioAndSubtitleSwitching": True,
            "returnToMainMenu": True,
            "burnedPtsAndFrameClock": True,
            "wholeSecondAudioVideoPulse": True,
            "notGeneratedByDvdauthorFixture": [
                "8-bit HD-DVD subpictures",
                "malformed MPEG-PS/SPU packets",
                "33-bit PTS rollover in a 26.5-hour stream",
                "mixed forced and non-forced cues in one physical SPU stream",
                "multi-angle ILVU playback",
            ],
        },
        "toolVersions": tool_versions,
        "probes": probes or {},
        "dvdFiles": dvd_files or [],
    }


def render_test_plan(test_cases: Sequence[dict[str, Any]]) -> str:
    lines = [
        "# Codex SageTV DVD Playback Test Plan",
        "",
        f"Fixture: `{FIXTURE_ID}`",
        "",
        "## Required setup",
        "",
        "1. Build the fixture with `create_authored_dvd_fixture.py --overwrite`.",
        "2. Set `enable_ps_dvd_playback=true` in the active SageTV Server `Sage.properties` and restart the server.",
        "3. Import the generated DVD folder into SageTV or navigate to its `VIDEO_TS` folder.",
        "4. Enable MiniDVDPlayer, media-command, player, SPU, PTS, drain, and overlay telemetry.",
        "5. Store screenshots and JSON traces using the filenames in the test cases.",
        "",
        "## Pass/fail rules",
        "",
        "- A test fails on a crash, ANR, player disconnect, decoder deadlock, stale overlay, incorrect menu destination, wrong stream, or timing outside its stated tolerance.",
        "- Do not infer a pass from the absence of an exception. Capture the requested screenshot and telemetry evidence.",
        "- Compare button rectangles against `expectedLegacySpuCtrlRect`, not the exclusive coordinates supplied to spumux.",
        "- DVD VM SetSTN values 64 and 65 enable authored streams 0 and 1; value 63 disables normal subtitles. The expected MiniClient wire selectors are 64, 65, and 128 respectively.",
        "- A zero-length PUSHBUFFER carrying flag `0x100` is a decoder-drain probe. Return `-2` only after every input and decoder queue is drained.",
        "",
        "## Test cases",
        "",
    ]
    for case in test_cases:
        lines.extend([
            f"### {case['id']} - {case['title']}",
            "",
            f"Category: `{case['category']}`  ",
            f"Priority: `{case['priority']}`",
            "",
            "Steps:",
        ])
        for index, step in enumerate(case["steps"], 1):
            lines.append(f"{index}. `{json.dumps(step, sort_keys=True)}`")
        lines.extend(["", "Assertions:"])
        for assertion in case["assertions"]:
            lines.append(f"- `{json.dumps(assertion, sort_keys=True)}`")
        if case.get("expectedProtocol"):
            lines.extend(["", "Protocol evidence:"])
            for expected in case["expectedProtocol"]:
                lines.append(f"- `{json.dumps(expected, sort_keys=True)}`")
        lines.append("")
    return "\n".join(lines) + "\n"


def render_codex_prompt() -> str:
    return textwrap.dedent(
        f"""\
        # Codex task: full SageTV authored-DVD playback validation

        Use `{MANIFEST_NAME}`, `{TEST_CASES_NAME}`, `{TEST_PLAN_NAME}`, and
        `{HARNESS_CONTRACT_NAME}` as the source of truth. Execute every required test case against the Android
        MiniClient with the SageTV server-driven `MiniDVDPlayer` path.

        Requirements:

        1. Do not skip a case because it is slow. Seek near EOS where the case says to do so.
        2. Capture every requested screenshot and protocol/telemetry JSON file.
        3. Verify menu text, selected button, exact expected button rectangle, title,
           chapter, audio track, subtitle cue, media time, and stale-overlay cleanup.
        4. Confirm legacy commands 32-37 are all observed across the complete run.
        5. Confirm DVD playback starts from INIT/PUSHBUFFER even when no OPENURL occurs.
        6. Confirm `0x100` drain probes receive `-2` only after all player queues drain.
        7. On failure, save the latest logcat, SageTV server log excerpt, media-command
           trace, player telemetry, current screenshot, and the preceding 10 seconds of events.
        8. Produce `DVD_TEST_RESULTS.json` with one record per test ID containing status,
           evidence files, measured values, and failure analysis; validate it against
           `{RESULT_SCHEMA_NAME}`.
        9. Produce `DVD_TEST_SUMMARY.md` grouping failures by server protocol, MPEG-PS,
           SPU decode/composition, timing, stream selection, navigation, and lifecycle.
        """
    )


def collect_dvd_files(output: Path) -> list[dict[str, Any]]:
    video_ts = output / "VIDEO_TS"
    files = sorted(path for path in video_ts.rglob("*") if path.is_file())
    return [
        {
            "path": path.relative_to(output).as_posix(),
            "bytes": path.stat().st_size,
            "sha256": sha256(path),
        }
        for path in files
    ]


def write_checksum_file(output: Path, dvd_files: Sequence[dict[str, Any]]) -> None:
    lines = [f"{entry['sha256']}  {entry['path']}" for entry in dvd_files]
    (output / CHECKSUM_NAME).write_text("\n".join(lines) + "\n", encoding="utf-8")


def copy_metadata_files(work: Path, metadata_dir: Path) -> None:
    metadata_dir.mkdir(parents=True, exist_ok=True)
    patterns = ("*.xml", "*.srt", "*.json", "*.png")
    for pattern in patterns:
        for source in sorted(work.glob(pattern)):
            shutil.copy2(source, metadata_dir / source.name)


def deep_verify_subpictures(spuunmux: str | None, feature: Path, work: Path) -> dict[str, Any]:
    if not spuunmux:
        return {"status": "skipped", "reason": "spuunmux is not installed"}
    verify_root = work / "spuunmux-verify"
    verify_root.mkdir(parents=True, exist_ok=True)
    results: list[dict[str, Any]] = []
    for stream_index in range(len(SUBTITLE_STREAMS)):
        stream_dir = verify_root / f"stream-{stream_index}"
        stream_dir.mkdir(parents=True, exist_ok=True)
        output_base = stream_dir / "cue"
        try:
            run([
                spuunmux,
                "-s", str(stream_index),
                "-o", str(output_base),
                str(feature),
            ], cwd=stream_dir)
            generated = sorted(
                path.relative_to(stream_dir).as_posix()
                for path in stream_dir.rglob("*")
                if path.is_file()
            )
            results.append({
                "streamIndex": stream_index,
                "status": "passed" if generated else "failed",
                "generatedFiles": generated,
            })
        except subprocess.CalledProcessError as error:
            results.append({
                "streamIndex": stream_index,
                "status": "failed",
                "returnCode": error.returncode,
            })
    status = "passed" if all(item["status"] == "passed" for item in results) else "failed"
    if status != "passed":
        raise RuntimeError(f"spuunmux deep verification failed: {results}")
    return {"status": status, "streams": results}


def parse_args(argv: Sequence[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--output",
        type=Path,
        default=PROJECT_ROOT / "artifacts/test-media/VIBE_AUTHORED_DVD",
        help="DVD folder to create (default: under the project artifacts directory)",
    )
    parser.add_argument(
        "--duration", "--main-duration",
        dest="main_duration",
        type=int,
        default=DEFAULT_MAIN_DURATION,
        help="main-feature duration in seconds",
    )
    parser.add_argument(
        "--special-duration",
        type=int,
        default=DEFAULT_SPECIAL_DURATION,
        help="special-feature duration in seconds",
    )
    parser.add_argument(
        "--cue-interval",
        type=float,
        default=DEFAULT_CUE_INTERVAL,
        help="shared subtitle cue interval in seconds",
    )
    parser.add_argument(
        "--cue-duration",
        type=float,
        default=DEFAULT_CUE_DURATION,
        help="each normal subtitle cue duration in seconds",
    )
    parser.add_argument(
        "--menu-duration",
        type=float,
        default=DEFAULT_MENU_VIDEO_DURATION,
        help="duration of each continuously looping authored menu",
    )
    parser.add_argument(
        "--work",
        type=Path,
        default=PROJECT_ROOT / "artifacts/test-media/dvd-author-work",
        help="temporary authoring directory",
    )
    parser.add_argument("--font", type=Path, default=None)
    parser.add_argument("--overwrite", action="store_true")
    parser.add_argument("--keep-work", action="store_true")
    parser.add_argument(
        "--reuse-work",
        action="store_true",
        help="reuse verified encoded title/menu assets and rerun authoring/validation",
    )
    parser.add_argument(
        "--reuse-titles",
        action="store_true",
        help="reuse verified title assets but regenerate every authored menu",
    )
    parser.add_argument(
        "--plan-only",
        action="store_true",
        help="write the manifest/test plan without invoking external authoring tools",
    )
    parser.add_argument(
        "--skip-deep-verify",
        action="store_true",
        help="skip optional spuunmux extraction validation",
    )
    return parser.parse_args(argv)


def validate_args(args: argparse.Namespace) -> None:
    if args.reuse_work and args.reuse_titles:
        raise ValueError("--reuse-work and --reuse-titles are mutually exclusive")
    if args.main_duration < 65:
        raise ValueError("--duration must be at least 65 seconds for all main-feature chapters")
    if args.special_duration < 12:
        raise ValueError("--special-duration must be at least 12 seconds")
    if args.cue_interval <= 0.25:
        raise ValueError("--cue-interval must be greater than 0.25 seconds")
    if args.cue_duration <= 0 or args.cue_duration >= args.cue_interval:
        raise ValueError("--cue-duration must be positive and less than --cue-interval")
    if not 2.0 <= args.menu_duration <= 30.0:
        raise ValueError("--menu-duration must be between 2 and 30 seconds")


def main(argv: Sequence[str] | None = None) -> int:
    COMMAND_LOG.clear()
    args = parse_args(argv)
    def project_path(value: Path) -> Path:
        expanded = value.expanduser()
        return (expanded if expanded.is_absolute() else PROJECT_ROOT / expanded).resolve()

    output = project_path(args.output)
    work = project_path(args.work)
    try:
        validate_args(args)
        validate_paths(output, work)
    except ValueError as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 2

    menus = build_menu_specs(MAIN_CHAPTERS)
    main_cues = build_cues(
        args.main_duration, MAIN_CHAPTERS, args.cue_interval, args.cue_duration
    )
    special_cues = build_cues(
        args.special_duration, SPECIAL_CHAPTERS, args.cue_interval, args.cue_duration
    )
    checkpoints = build_checkpoints(
        args.main_duration, args.special_duration, main_cues, special_cues
    )
    test_cases = build_test_cases(menus, checkpoints)

    try:
        plan_validation = validate_plan_consistency(
            main_duration=args.main_duration,
            special_duration=args.special_duration,
            menus=menus,
            main_cues=main_cues,
            special_cues=special_cues,
            checkpoints=checkpoints,
            test_cases=test_cases,
        )
        output.parent.mkdir(parents=True, exist_ok=True)
        prepare_destination(output, overwrite=args.overwrite, create=args.plan_only)
        if args.plan_only:
            manifest = build_manifest(
                main_duration=args.main_duration,
                special_duration=args.special_duration,
                cue_interval=args.cue_interval,
                cue_duration=args.cue_duration,
                menu_duration=args.menu_duration,
                menus=menus,
                main_cues=main_cues,
                special_cues=special_cues,
                checkpoints=checkpoints,
                test_cases=test_cases,
                plan_validation=plan_validation,
                tool_versions={},
                build_mode="plan-only",
            )
            write_json(output / MANIFEST_NAME, manifest)
            write_json(output / TEST_CASES_NAME, {
                "schemaVersion": 1,
                "fixtureId": FIXTURE_ID,
                "actionVocabularyIsHarnessAdaptable": True,
                "actionVocabulary": build_action_vocabulary(),
                "testCases": test_cases,
            })
            write_json(output / HARNESS_CONTRACT_NAME, build_harness_contract())
            write_json(output / RESULT_SCHEMA_NAME, build_result_schema())
            (output / TEST_PLAN_NAME).write_text(render_test_plan(test_cases), encoding="utf-8")
            (output / CODEX_PROMPT_NAME).write_text(render_codex_prompt(), encoding="utf-8")
            print(f"PASS: wrote plan-only fixture metadata: {output}")
            return 0

        if args.reuse_work or args.reuse_titles:
            if not work.is_dir():
                raise RuntimeError(f"requested reuse directory is unavailable: {work}")
        else:
            prepare_destination(work, overwrite=args.overwrite, create=True)
        font = resolve_font(args.font)
        ffmpeg = tool("ffmpeg")
        ffprobe = tool("ffprobe")
        dvdauthor = tool("dvdauthor")
        spumux = tool("spumux")
        spuunmux = tool("spuunmux", required=False)
        assert ffmpeg and ffprobe and dvdauthor and spumux
        magick_prefix = imagemagick_prefix()

        tool_versions = {
            "python": sys.version.splitlines()[0],
            "ffmpeg": tool_version(ffmpeg, "-version"),
            "ffprobe": tool_version(ffprobe, "-version"),
            "dvdauthor": tool_version(dvdauthor, "--version"),
            "spumux": tool_version(spumux, "--version"),
            "spuunmux": tool_version(spuunmux, "--version") if spuunmux else None,
            "imagemagick": tool_version(magick_prefix[0], "-version"),
            "font": str(font),
        }
        work_config = {
            "assetSchemaVersion": WORK_ASSET_SCHEMA_VERSION,
            "mainDurationSeconds": args.main_duration,
            "specialDurationSeconds": args.special_duration,
            "cueIntervalSeconds": args.cue_interval,
            "cueDurationSeconds": args.cue_duration,
            "menuDurationSeconds": args.menu_duration,
            "fontSha256": sha256(font),
        }

        main_base = work / "main-feature-base.mpg"
        main_feature = work / "main-feature.mpg"
        special_base = work / "special-feature-base.mpg"
        special_feature = work / "special-feature.mpg"

        cached_config: dict[str, Any] | None = None
        if args.reuse_work or args.reuse_titles:
            work_config_path = work / WORK_CONFIG_NAME
            if not work_config_path.is_file():
                raise RuntimeError(
                    f"cached assets cannot be verified because {work_config_path} "
                    "is missing; rerun once without a reuse option"
                )
            cached_config = json.loads(work_config_path.read_text(encoding="utf-8"))

        if not args.reuse_work and not args.reuse_titles:
            generate_title(
                ffmpeg,
                main_base,
                args.main_duration,
                "main_feature",
                "VIBE AUTHORED DVD MAIN FEATURE",
                MAIN_CHAPTERS,
                font,
                "testsrc2",
            )
            add_subtitles(
                spumux, main_base, main_feature, work, font, "main_feature", main_cues
            )

            generate_title(
                ffmpeg,
                special_base,
                args.special_duration,
                "special_feature",
                "VIBE DVD SHORT SPECIAL FEATURE",
                SPECIAL_CHAPTERS,
                font,
                "smptebars",
            )
            add_subtitles(
                spumux, special_base, special_feature, work, font,
                "special_feature", special_cues
            )

        if args.reuse_titles:
            title_keys = (
                "mainDurationSeconds", "specialDurationSeconds",
                "cueIntervalSeconds", "cueDurationSeconds", "fontSha256",
            )
            cached_title = {key: cached_config.get(key) for key in title_keys}
            requested_title = {key: work_config.get(key) for key in title_keys}
            if cached_title != requested_title:
                raise RuntimeError(
                    "--reuse-titles title configuration does not match this request; "
                    f"cached={cached_title!r}, requested={requested_title!r}"
                )

        if not args.reuse_work:
            for menu in menus:
                make_menu(
                    ffmpeg,
                    spumux,
                    magick_prefix,
                    work,
                    menu,
                    font,
                    args.menu_duration,
                )
            write_json(work / WORK_CONFIG_NAME, work_config)
        else:
            if cached_config != work_config:
                raise RuntimeError(
                    "--reuse-work asset configuration does not match this request; "
                    f"cached={cached_config!r}, requested={work_config!r}"
                )
            for menu in menus:
                menu.video_file = work / f"menu-{menu.number:02d}-{menu.id}.mpg"

        if args.reuse_titles:
            for menu in menus:
                if menu.video_file is None:
                    menu.video_file = work / f"menu-{menu.number:02d}-{menu.id}.mpg"

        if args.reuse_work or args.reuse_titles:
            reusable = [main_feature, special_feature] + [
                menu.video_file for menu in menus if menu.video_file is not None
            ]
            missing = [path for path in reusable if not path.is_file() or path.stat().st_size == 0]
            if missing:
                raise RuntimeError("--reuse-work assets are missing/empty: "
                                   + ", ".join(str(path) for path in missing))

        palette = work / "vibe.rgb"
        write_palette(palette)
        author = work / "dvdauthor.xml"
        author.write_text(
            author_xml(
                output,
                menus,
                main_feature,
                special_feature,
                MAIN_CHAPTERS,
                SPECIAL_CHAPTERS,
                palette,
            ),
            encoding="utf-8",
        )
        xml_validate(author)
        run([dvdauthor, "-x", str(author)])
        validate_dvd_structure(output)

        main_probe_raw = probe_media(ffprobe, main_feature)
        special_probe_raw = probe_media(ffprobe, special_feature)
        menu_probe_raw = probe_media(ffprobe, menus[0].video_file) if menus[0].video_file else {}

        authored_title_vob = output / "VIDEO_TS" / "VTS_01_1.VOB"
        authored_menu_vob = output / "VIDEO_TS" / "VTS_01_0.VOB"
        authored_title_probe_raw = probe_media(ffprobe, authored_title_vob)
        authored_menu_probe_raw = probe_media(ffprobe, authored_menu_vob)

        probes = {
            "mainFeatureIntermediate": validate_title_probe(
                main_probe_raw, "main feature intermediate MPEG-PS"
            ),
            "specialFeatureIntermediate": validate_title_probe(
                special_probe_raw, "special feature intermediate MPEG-PS"
            ),
            "rootMenuIntermediate": {
                "streams": [compact_stream(stream) for stream in menu_probe_raw.get("streams", [])],
                "format": menu_probe_raw.get("format", {}),
            },
            "authoredTitleVob": validate_title_probe(
                authored_title_probe_raw, "authored VTS_01_1.VOB"
            ),
            "authoredMenuVob": validate_menu_probe(
                authored_menu_probe_raw, "authored VTS_01_0.VOB"
            ),
        }
        write_json(work / "ffprobe-main-feature.json", main_probe_raw)
        write_json(work / "ffprobe-special-feature.json", special_probe_raw)
        write_json(work / "ffprobe-root-menu.json", menu_probe_raw)
        write_json(work / "ffprobe-authored-title-vob.json", authored_title_probe_raw)
        write_json(work / "ffprobe-authored-menu-vob.json", authored_menu_probe_raw)

        if args.skip_deep_verify:
            deep_verify = {"status": "skipped", "reason": "--skip-deep-verify"}
        else:
            deep_verify = deep_verify_subpictures(spuunmux, authored_title_vob, work)
        probes["spuunmux"] = deep_verify

        metadata_dir = output / METADATA_DIR_NAME
        copy_metadata_files(work, metadata_dir)
        dvd_files = collect_dvd_files(output)
        write_checksum_file(output, dvd_files)

        manifest = build_manifest(
            main_duration=args.main_duration,
            special_duration=args.special_duration,
            cue_interval=args.cue_interval,
            cue_duration=args.cue_duration,
            menu_duration=args.menu_duration,
            menus=menus,
            main_cues=main_cues,
            special_cues=special_cues,
            checkpoints=checkpoints,
            test_cases=test_cases,
            plan_validation=plan_validation,
            tool_versions=tool_versions,
            build_mode="authored",
            probes=probes,
            dvd_files=dvd_files,
        )
        write_json(output / MANIFEST_NAME, manifest)
        write_json(output / TEST_CASES_NAME, {
            "schemaVersion": 1,
            "fixtureId": FIXTURE_ID,
            "actionVocabularyIsHarnessAdaptable": True,
            "actionVocabulary": build_action_vocabulary(),
            "testCases": test_cases,
        })
        write_json(output / HARNESS_CONTRACT_NAME, build_harness_contract())
        write_json(output / RESULT_SCHEMA_NAME, build_result_schema())
        (output / TEST_PLAN_NAME).write_text(render_test_plan(test_cases), encoding="utf-8")
        (output / CODEX_PROMPT_NAME).write_text(render_codex_prompt(), encoding="utf-8")
        write_json(output / BUILD_REPORT_NAME, {
            "fixtureId": FIXTURE_ID,
            "status": "passed",
            "output": str(output),
            "work": str(work),
            "toolVersions": tool_versions,
            "commands": COMMAND_LOG,
            "checks": {
                "dvdStructure": "passed",
                "mainFeatureIntermediateStreams": "passed",
                "specialFeatureIntermediateStreams": "passed",
                "authoredTitleVobStreams": "passed",
                "authoredMenuVobStreams": "passed",
                "spuunmuxAuthoredTitleVob": deep_verify,
                "manifestTestCases": len(test_cases),
                "planConsistency": plan_validation,
            },
        })

        print(f"PASS: authored DVD fixture: {output}")
        print("PASS: root menu + 5 submenus + explicit return paths")
        print("PASS: 2 AC-3 audio streams + 2 DVD SPU subtitle streams")
        print(f"PASS: {len(MAIN_CHAPTERS)} main chapters + {len(SPECIAL_CHAPTERS)} special chapters")
        print(f"PASS: {len(test_cases)} Codex playback test cases")

        if not args.keep_work:
            safe_remove_tree(work)
        return 0
    except (OSError, RuntimeError, subprocess.CalledProcessError, json.JSONDecodeError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
