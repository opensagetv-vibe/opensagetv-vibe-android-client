#!/usr/bin/env python3
"""
OpenSageTV Vibe deterministic seek-test generator with embedded CEA-608/CEA-708.

Requirements:
  * Python 3.8+
  * FFmpeg supplied by the unified build environment, PATH, FFMPEG, or placed
    beside this script.

No pip packages, Node.js, npm, or open-cea installation are required.

The caption encoder is a focused Python implementation of the portions needed
for this deterministic test stream. Its CEA-608/708 transport architecture was
informed by Joey Parrish's Apache-2.0 open-cea project, but this implementation
was written specifically for OpenSageTV Vibe and is distributed under this
repository's Apache-2.0 license. See the repository LICENSE file.

This script:
  1. Uses FFmpeg to generate the same 1080i MPEG-2 + AC-3 test assets as the
     command supplied for OpenSageTV Vibe.
  2. Generates an in-band caption timeline every N seconds (default: 0.5).
  3. Encodes CEA-608 CC1 roll-up captions and/or native CEA-708 Service 1.
  4. Inserts ATSC A/53 "GA94" cc_data() user_data into every MPEG-2 picture.
  5. Uses FFmpeg stream-copy to mux the captioned MPEG-2 plus both AC-3 tracks
     into the final MPEG transport stream.

Examples:
  # 2-minute test, both CEA-608 and CEA-708, caption every 0.5 seconds
  python3 generate_a53_seek_fixture.py --duration 2m

  # Current 900-second / 15-minute test
  python3 generate_a53_seek_fixture.py --duration 900

  # Custom caption update interval (for example, every 1 second)
  python3 generate_a53_seek_fixture.py --duration 2m --caption-interval 1.0

  # Short form for the interval
  python3 generate_a53_seek_fixture.py --duration 2m -i 0.5

  # Explicit 15 minutes and custom output
  python3 generate_a53_seek_fixture.py --duration 15m -o VibeSeekTest-15min-CC.ts

  # CEA-608 only
  python3 generate_a53_seek_fixture.py --duration 2m --cc 608

  # Native CEA-708 only
  python3 generate_a53_seek_fixture.py --duration 2m --cc 708
"""

from __future__ import annotations

import argparse
import collections
import contextlib
import mmap
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
from typing import Deque, Iterable, List, Optional, Sequence, Tuple


# ---------------------------------------------------------------------------
# General helpers
# ---------------------------------------------------------------------------

VIDEO_FPS_NUM = 30000
VIDEO_FPS_DEN = 1001
VIDEO_FPS = VIDEO_FPS_NUM / VIDEO_FPS_DEN  # output after tinterlace
SOURCE_FPS = "60000/1001"
VIDEO_FPS_TEXT = "30000/1001"
DEFAULT_DURATION = 900.0
DEFAULT_INTERVAL = 0.5
DEFAULT_OUTPUT = "VibeSeekTest-1080i-MPEG2-AC3-CC.ts"
SYNC_PULSE_DURATION = 0.12


def parse_duration(value: str) -> float:
    """Parse seconds, 120s, 2m, 1.5h, MM:SS, or HH:MM:SS."""
    text = value.strip().lower()
    if not text:
        raise argparse.ArgumentTypeError("duration cannot be empty")

    if ":" in text:
        parts = text.split(":")
        try:
            nums = [float(p) for p in parts]
        except ValueError as exc:
            raise argparse.ArgumentTypeError(f"invalid duration: {value}") from exc
        if len(nums) == 2:
            minutes, seconds = nums
            result = minutes * 60 + seconds
        elif len(nums) == 3:
            hours, minutes, seconds = nums
            result = hours * 3600 + minutes * 60 + seconds
        else:
            raise argparse.ArgumentTypeError("use MM:SS or HH:MM:SS")
    else:
        match = re.fullmatch(r"([0-9]+(?:\.[0-9]+)?)\s*([smh]?)", text)
        if not match:
            raise argparse.ArgumentTypeError(
                "duration must look like 120, 120s, 2m, 1.5h, or 00:02:00"
            )
        amount = float(match.group(1))
        suffix = match.group(2)
        scale = {"": 1.0, "s": 1.0, "m": 60.0, "h": 3600.0}[suffix]
        result = amount * scale

    if result <= 0:
        raise argparse.ArgumentTypeError("duration must be greater than zero")
    return result


def format_clock(seconds: float) -> str:
    total = max(0, int(round(seconds)))
    hours = total // 3600
    minutes = (total % 3600) // 60
    secs = total % 60
    return f"{hours:02d}:{minutes:02d}:{secs:02d}"


def format_caption_clock(seconds: float) -> str:
    """Format caption timeline as HH:MM:SS.mmm (for sub-second updates)."""
    total_ms = max(0, int(round(seconds * 1000.0)))
    hours = total_ms // 3_600_000
    minutes = (total_ms % 3_600_000) // 60_000
    secs = (total_ms % 60_000) // 1000
    millis = total_ms % 1000
    return f"{hours:02d}:{minutes:02d}:{secs:02d}.{millis:03d}"


def local_ffmpeg(script_dir: Path) -> Path:
    configured = os.environ.get("FFMPEG", "").strip()
    if configured:
        path = Path(configured)
        if path.is_file():
            return path
        resolved = shutil.which(configured)
        if resolved:
            return Path(resolved)
        raise FileNotFoundError(f"FFMPEG does not resolve to an executable: {configured}")

    candidates = [script_dir / "ffmpeg.exe", script_dir / "ffmpeg"]
    for path in candidates:
        if path.is_file():
            return path
    resolved = shutil.which("ffmpeg")
    if resolved:
        return Path(resolved)
    raise FileNotFoundError(
        "ffmpeg was not found in FFMPEG, PATH, or beside this script"
    )


def ffmpeg_filter_escape_path(path: Path) -> str:
    # FFmpeg filter syntax needs backslashes and ':' escaped even though
    # subprocess itself is not using a shell.
    text = str(path.resolve()).replace("\\", "/")
    text = text.replace(":", r"\:")
    text = text.replace("'", r"\'")
    return text


def find_font(explicit: Optional[str]) -> Optional[Path]:
    if explicit:
        path = Path(explicit)
        if not path.is_file():
            raise FileNotFoundError(f"font file not found: {path}")
        return path

    candidates: List[Path] = []
    if os.name == "nt":
        windir = Path(os.environ.get("WINDIR", r"C:\Windows"))
        candidates += [
            windir / "Fonts" / "arialbd.ttf",
            windir / "Fonts" / "segoeuib.ttf",
        ]
    else:
        candidates += [
            Path("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"),
            Path("/usr/share/fonts/dejavu/DejaVuSans-Bold.ttf"),
            Path("/usr/local/share/fonts/DejaVuSans-Bold.ttf"),
        ]

    for path in candidates:
        if path.is_file():
            return path
    return None


def run(cmd: Sequence[str], *, dry_run: bool = False) -> None:
    printable = " ".join(subprocess.list2cmdline([part]) for part in cmd)
    print(f"\n> {printable}", flush=True)
    if not dry_run:
        subprocess.run(list(cmd), check=True)


# ---------------------------------------------------------------------------
# CEA-608 encoder (focused CC1 roll-up implementation)
# ---------------------------------------------------------------------------


def odd_parity(byte: int) -> int:
    lower = byte & 0x7F
    # Add MSB when the lower seven bits contain an even number of ones.
    if (bin(lower).count("1") & 1) == 0:
        return lower | 0x80
    return lower


def parity_word(first: int, second: int) -> int:
    return (odd_parity(first) << 8) | odd_parity(second)


def control_word(second: int, channel: int = 0, field: int = 0) -> int:
    # Misc-control first byte: F1 ch1=0x14, F1 ch2=0x1c,
    # F2 ch1=0x15, F2 ch2=0x1d.
    first = 0x14 | (0x08 if channel else 0) | (0x01 if field else 0)
    return parity_word(first, second)


def double_control(word: int) -> List[int]:
    return [word, word]


ROW_TO_FIRST = {
    1: 0x11, 2: 0x11,
    3: 0x12, 4: 0x12,
    5: 0x15, 6: 0x15,
    7: 0x16, 8: 0x16,
    9: 0x17, 10: 0x17,
    11: 0x10,
    12: 0x13, 13: 0x13,
    14: 0x14, 15: 0x14,
}
LOWER_HALF_ROWS = {2, 4, 6, 8, 10, 13, 15}


def pac_word(row: int = 15, column: int = 0, channel: int = 0) -> int:
    if row not in ROW_TO_FIRST:
        raise ValueError("CEA-608 row must be 1..15")
    if column not in (0, 4, 8, 12, 16, 20, 24, 28):
        raise ValueError("CEA-608 PAC column must be 0/4/8/12/16/20/24/28")
    first = ROW_TO_FIRST[row] | (0x08 if channel else 0)
    row_half = 0x20 if row in LOWER_HALF_ROWS else 0x00
    indent_code = 8 + (column // 4)
    second = 0x40 | row_half | (indent_code << 1)
    return parity_word(first, second)


# These are the byte positions that CEA-608's Basic North American character
# set repurposes.  The seek-test text deliberately avoids them.
CEA608_REASSIGNED_ASCII = {0x2A, 0x5C, 0x5E, 0x5F, 0x60, 0x7B, 0x7C, 0x7D, 0x7E}


def encode_608_text(text: str) -> List[int]:
    """Encode the ASCII subset used by this test, two characters per word."""
    encoded: List[int] = []
    bytes7: List[int] = []
    for char in text:
        code = ord(char)
        if 0x20 <= code <= 0x7F and code not in CEA608_REASSIGNED_ASCII:
            bytes7.append(code)
        else:
            bytes7.append(0x20)  # safe substitution

    if len(bytes7) & 1:
        bytes7.append(0x00)  # NUL second byte for a final single character

    for idx in range(0, len(bytes7), 2):
        encoded.append(parity_word(bytes7[idx], bytes7[idx + 1]))
    return encoded


def build_608_rollup_words(text: str, first_cue: bool, rows: int = 3) -> List[int]:
    if rows not in (2, 3, 4):
        raise ValueError("roll-up rows must be 2, 3, or 4")

    # Match the cadence observed in the known-good OTA reference:
    #   RUx, idle, CR, idle, PAC, idle, text...
    # Re-send RUx on EVERY caption row so a decoder that tunes in after
    # playback starts can reacquire roll-up mode.
    idle = parity_word(0x00, 0x00)  # -> 0x80 0x80
    words: List[int] = [
        control_word(0x23 + rows),  # RU2/RU3/RU4
        idle,
        control_word(0x2D),         # CR
        idle,
        # Leave the bottom caption row clear for SageTV's STV playback
        # timeline. This changes only the CC1 roll-up base position.
        pac_word(row=14, column=0, channel=0),
        idle,
    ]
    words += encode_608_text(text)
    return words


# ---------------------------------------------------------------------------
# CEA-708 encoder (native Service 1, focused 3-row timing-window subset)
# ---------------------------------------------------------------------------


def encode_708_text(text: str) -> bytes:
    out = bytearray()
    for char in text:
        if char == "\n":
            out.append(0x0D)  # C0 CR
        else:
            code = ord(char)
            if 0x20 <= code <= 0x7E:
                out.append(code)
            elif 0xA0 <= code <= 0xFF:
                out.append(code)
            else:
                out.append(0x5F)  # underscore substitution
    return bytes(out)


def define_708_window(
    window_id: int = 0,
    rows: int = 3,
    columns: int = 32,
    anchor_vertical: int = 67,
    anchor_horizontal: int = 105,
    anchor_point: int = 7,
) -> bytes:
    # DF0..DF7.  Row/column lock are intentionally enabled.
    p1 = (1 << 5) | (1 << 4) | (1 << 3)  # visible + row lock + col lock
    p2 = anchor_vertical & 0x7F             # absolute positioning
    p3 = anchor_horizontal & 0xFF
    p4 = ((anchor_point & 0x0F) << 4) | ((max(1, min(rows, 15)) - 1) & 0x0F)
    p5 = (max(1, min(columns, 42)) - 1) & 0x3F
    p6 = (1 << 3) | 1  # windowStyleId=1, penStyleId=1
    return bytes([0x98 | (window_id & 7), p1, p2, p3, p4, p5, p6])


def build_708_render_payload(lines: Sequence[str], window_id: int = 0) -> bytes:
    text = "\n".join(lines[-3:])
    out = bytearray()
    out += define_708_window(window_id=window_id, rows=3, columns=32)
    out += bytes([0x80 | window_id])          # CW0
    out += bytes([0x88, 1 << window_id])     # CLW
    out += bytes([0x92, 0x00, 0x00])         # SPL row=0, col=0
    out += encode_708_text(text)
    out += bytes([0x89, 1 << window_id])     # DSW
    return bytes(out)


def encode_service_blocks(service: int, payload: bytes) -> bytes:
    if not (1 <= service <= 63):
        raise ValueError("CEA-708 service must be 1..63")
    out = bytearray()
    for offset in range(0, len(payload), 31):
        chunk = payload[offset:offset + 31]
        if service <= 6:
            out.append((service << 5) | len(chunk))
        else:
            if not chunk:
                continue
            out.append((7 << 5) | len(chunk))
            out.append(service & 0x3F)
        out += chunk
    if len(out) > 127:
        raise ValueError("CEA-708 event exceeds one 127-byte Caption Channel Packet")
    return bytes(out)


def encode_ccp(sequence_number: int, payload: bytes) -> bytes:
    if len(payload) > 127:
        raise ValueError("CEA-708 CCP payload exceeds 127 bytes")
    data = bytearray(payload)
    # Total CCP length must be even -> payload/data length must be odd.
    if len(data) % 2 == 0:
        data.append(0x00)
    packet_size_code = 0 if len(data) == 127 else (len(data) + 1) // 2
    return bytes([((sequence_number & 3) << 6) | packet_size_code]) + bytes(data)


def packet_to_cc_tuples(ccp: bytes) -> bytes:
    if len(ccp) % 2:
        raise ValueError("CEA-708 CCP length must be even")
    out = bytearray()
    for idx in range(0, len(ccp), 2):
        cc_type = 3 if idx == 0 else 2
        out += bytes([0xF8 | 0x04 | cc_type, ccp[idx], ccp[idx + 1]])
    return bytes(out)


# ---------------------------------------------------------------------------
# Shared A/53 cc_data() frame encoder
# ---------------------------------------------------------------------------


def cc_count_per_frame(fps: float) -> int:
    if abs(fps - 24.0) < 0.01:
        return 25
    if abs(fps - 25.0) < 0.01:
        return 24
    if abs(fps - 29.97) < 0.02 or abs(fps - 30.0) < 0.02:
        return 20
    if abs(fps - 50.0) < 0.02:
        return 12
    if abs(fps - 59.94) < 0.02 or abs(fps - 60.0) < 0.02:
        return 10
    raise ValueError(f"unsupported caption frame rate: {fps}")


def leading_608_count(fps: float) -> int:
    # Match the known-good ATSC MPEG-2 reference stream: one CEA-608
    # byte-pair for Field 1 and one for Field 2 per coded picture.
    return 2


def cc_tuple(valid: bool, cc_type: int, data1: int = 0, data2: int = 0) -> bytes:
    return bytes([0xF8 | (0x04 if valid else 0) | (cc_type & 3), data1 & 0xFF, data2 & 0xFF])


class CaptionTransportEncoder:
    """Constant-rate 608 + 708 cc_data() transport for one video stream."""

    def __init__(self, fps: float = VIDEO_FPS, top_field_first: bool = True) -> None:
        self.fps = fps
        self.top_field_first = top_field_first
        self.total_count = cc_count_per_frame(fps)
        self.leading_count = leading_608_count(fps)
        self.f1: Deque[int] = collections.deque()
        self.f2: Deque[int] = collections.deque()
        self.dtvcc_packets: Deque[bytes] = collections.deque()
        self.current_dtvcc: Optional[bytes] = None
        self.current_dtvcc_offset = 0
        self.sequence_number = 0
        self.just_closed_ccp = False

    def push_608_f1(self, words: Iterable[int]) -> None:
        self.f1.extend(words)

    def push_608_f2(self, words: Iterable[int]) -> None:
        self.f2.extend(words)

    def push_708(self, service_payload: bytes) -> None:
        ccp = encode_ccp(self.sequence_number, service_payload)
        self.sequence_number = (self.sequence_number + 1) & 3
        self.dtvcc_packets.append(packet_to_cc_tuples(ccp))

    @staticmethod
    def _write_608(queue: Deque[int], cc_type: int) -> bytes:
        if queue:
            word = queue.popleft()
            return cc_tuple(True, cc_type, (word >> 8) & 0xFF, word & 0xFF)
        # Broadcast-style idle CEA-608 pair. 0x80/0x80 is parity-correct
        # NUL/NUL and remains cc_valid=1, matching the OTA reference.
        return cc_tuple(True, cc_type, 0x80, 0x80)

    def next_frame(self) -> bytes:
        out = bytearray()
        per_field = self.leading_count // 2
        for _ in range(per_field):
            if self.top_field_first:
                out += self._write_608(self.f2, 1)
                out += self._write_608(self.f1, 0)
            else:
                out += self._write_608(self.f1, 0)
                out += self._write_608(self.f2, 1)

        for _ in range(self.leading_count, self.total_count):
            if self.current_dtvcc is None:
                self.current_dtvcc = self.dtvcc_packets.popleft() if self.dtvcc_packets else None
                self.current_dtvcc_offset = 0

            if self.current_dtvcc is not None:
                out += self.current_dtvcc[
                    self.current_dtvcc_offset:self.current_dtvcc_offset + 3
                ]
                self.current_dtvcc_offset += 3
                if self.current_dtvcc_offset >= len(self.current_dtvcc):
                    self.current_dtvcc = None
                    self.just_closed_ccp = True
            else:
                # Invalid DTVCC padding. Use type 3 once after a packet boundary,
                # then type 2 for continuing padding.
                pad_type = 3 if self.just_closed_ccp else 2
                self.just_closed_ccp = False
                out += cc_tuple(False, pad_type, 0, 0)

        expected = self.total_count * 3
        if len(out) != expected:
            raise AssertionError(f"cc_data frame is {len(out)} bytes, expected {expected}")
        return bytes(out)


def build_caption_frames(
    total_frames: int,
    duration: float,
    interval: float,
    cc_mode: str,
    caption_prefix: str,
) -> List[bytes]:
    if interval <= 0:
        raise ValueError("caption interval must be greater than zero")

    actions: List[Tuple[int, str, object]] = []
    cue_times: List[float] = []
    t = 0.0
    while t < duration - 1e-9:
        cue_times.append(t)
        t += interval

    history: List[str] = []
    first_608 = True
    for cue_time in cue_times:
        # Align action to the first video frame at/after the requested cue time.
        frame = int((cue_time * VIDEO_FPS) + 0.999999999)
        text = f"{caption_prefix} {format_caption_clock(cue_time)}"
        history.append(text)

        if cc_mode in ("608", "both"):
            words = build_608_rollup_words(text, first_cue=first_608, rows=3)
            first_608 = False
            actions.append((frame, "608", words))

        if cc_mode in ("708", "both"):
            render = build_708_render_payload(history[-3:])
            service_payload = encode_service_blocks(1, render)
            actions.append((frame, "708", service_payload))

    # Clear the final displayed caption on the last frame.  Besides being
    # polite decoder state management, this gives subtitle extractors a
    # deterministic end boundary for the final roll-up cue.
    final_frame = max(0, total_frames - 1)
    if cc_mode in ("608", "both"):
        actions.append((final_frame, "608", [control_word(0x2C)]))  # EDM
    if cc_mode in ("708", "both"):
        actions.append((final_frame, "708", encode_service_blocks(1, bytes([0x8A, 0x01]))))  # HDW

    actions.sort(key=lambda item: item[0])
    encoder = CaptionTransportEncoder(VIDEO_FPS, top_field_first=False)
    result: List[bytes] = []
    action_index = 0

    for frame_idx in range(total_frames):
        while action_index < len(actions) and actions[action_index][0] <= frame_idx:
            _, kind, payload = actions[action_index]
            action_index += 1
            if kind == "608":
                encoder.push_608_f1(payload)  # type: ignore[arg-type]
            else:
                encoder.push_708(payload)     # type: ignore[arg-type]
        result.append(encoder.next_frame())

    return result


def make_a53_user_data(cc_data: bytes) -> bytes:
    if len(cc_data) % 3:
        raise ValueError("A/53 cc_data length must be a multiple of 3")
    count = len(cc_data) // 3
    if count > 31:
        raise ValueError("A/53 cc_count cannot exceed 31")

    # Matches FFmpeg's MPEG-2 A/53 writer:
    # user_data_start_code + 'GA94' + type 0x03 + flags/count + em_data +
    # cc_data tuples + marker byte.
    return (
        b"\x00\x00\x01\xB2"
        + b"GA94"
        + b"\x03"
        # OTA reference sets reserved bit 7=1, process_cc_data_flag=1.
        + bytes([0xC0 | count])
        + b"\xFF"
        + cc_data
        + b"\xFF"
    )


# ---------------------------------------------------------------------------
# MPEG-2 elementary-stream caption injection
# ---------------------------------------------------------------------------


def parse_temporal_reference(mm: mmap.mmap, picture_start: int) -> int:
    if picture_start + 6 > len(mm):
        raise ValueError("truncated MPEG-2 picture header")
    return (mm[picture_start + 4] << 2) | (mm[picture_start + 5] >> 6)


def find_picture_insertion_points(mm: mmap.mmap) -> List[Tuple[int, int]]:
    """Return (file_offset, display_frame_index) for every MPEG-2 picture."""
    start_prefix = b"\x00\x00\x01"
    pos = 0
    gop_base = 0
    gop_max_tr = -1
    seen_gop = False
    pending_picture: Optional[Tuple[int, int]] = None  # (picture start, display idx)
    insertions: List[Tuple[int, int]] = []

    while True:
        start = mm.find(start_prefix, pos)
        if start < 0 or start + 4 > len(mm):
            break
        code = mm[start + 3]

        if code == 0xB8:  # GOP start
            if seen_gop and gop_max_tr >= 0:
                gop_base += gop_max_tr + 1
            seen_gop = True
            gop_max_tr = -1

        elif code == 0x00:  # picture start
            if pending_picture is not None:
                raise ValueError("MPEG-2 picture had no slice before the next picture")
            tr = parse_temporal_reference(mm, start)
            gop_max_tr = max(gop_max_tr, tr)
            pending_picture = (start, gop_base + tr)

        elif 0x01 <= code <= 0xAF and pending_picture is not None:
            # Insert after the picture header/extensions/user_data and immediately
            # before the first slice, which mirrors FFmpeg's native A/53 placement.
            _, display_index = pending_picture
            insertions.append((start, display_index))
            pending_picture = None

        pos = start + 4

    if pending_picture is not None:
        raise ValueError("last MPEG-2 picture had no slice")
    if not insertions:
        raise ValueError("no MPEG-2 pictures were found")
    return insertions


def inject_captions_mpeg2(
    source: Path,
    destination: Path,
    duration: float,
    interval: float,
    cc_mode: str,
    caption_prefix: str,
) -> Tuple[int, int]:
    with source.open("rb") as src, mmap.mmap(src.fileno(), 0, access=mmap.ACCESS_READ) as mm:
        insertions = find_picture_insertion_points(mm)
        max_display_index = max(display for _, display in insertions)
        total_display_frames = max_display_index + 1
        caption_frames = build_caption_frames(
            total_display_frames,
            duration,
            interval,
            cc_mode,
            caption_prefix,
        )

        last = 0
        with destination.open("wb") as out:
            for offset, display_index in insertions:
                out.write(mm[last:offset])
                if 0 <= display_index < len(caption_frames):
                    out.write(make_a53_user_data(caption_frames[display_index]))
                last = offset
            out.write(mm[last:])

    return len(insertions), total_display_frames


# ---------------------------------------------------------------------------
# FFmpeg command construction
# ---------------------------------------------------------------------------


def build_filter_complex(font: Optional[Path]) -> str:
    if font is not None:
        font_opt = f"fontfile='{ffmpeg_filter_escape_path(font)}':"
    else:
        font_opt = ""

    return (
        "[0:v]"
        "tinterlace=mode=interleave_top,setfield=tff,format=yuv420p,"
        "drawbox=x=70:y=(ih-250)/2:w=1780:h=250:color=black@0.80:t=fill,"
        f"drawtext={font_opt}text='OPENSAGETV VIBE SEEK TEST':"
        "fontcolor=white:fontsize=64:x=(w-text_w)/2:y=(h-250)/2+25,"
        f"drawtext={font_opt}text='PTS %{{pts\\:hms}}  FRAME %{{n}}':"
        "fontcolor=yellow:fontsize=74:x=(w-text_w)/2:y=(h-250)/2+120,"
        "drawbox=x=28:y=28:w=74:h=74:color=white@0.90:t=fill:"
        f"enable='lt(mod(t,1),{SYNC_PULSE_DURATION:.3f})',"
        f"drawtext={font_opt}text='A/V SYNC PULSE = WHOLE SECOND':"
        "fontcolor=black:fontsize=24:x=118:y=48:box=1:boxcolor=white@0.90:"
        f"boxborderw=6:enable='lt(mod(t,1),{SYNC_PULSE_DURATION:.3f})',"
        "drawgrid=width=240:height=135:thickness=2:color=white@0.20[v];"
        f"[1:a]asetpts=PTS-STARTPTS,volume='if(lt(mod(t,1),{SYNC_PULSE_DURATION:.3f}),0.82,0.13)':"
        "eval=frame,pan=5.1|FL=c0|FR=c0|FC=0.50*c0|LFE=0.20*c0|BL=0.35*c0|BR=0.35*c0,"
        "alimiter=limit=0.95[a51];"
        f"[2:a]asetpts=PTS-STARTPTS,volume='if(lt(mod(t,1),{SYNC_PULSE_DURATION:.3f}),0.82,0.13)':"
        "eval=frame,pan=stereo|FL=c0|FR=c0,alimiter=limit=0.95[a20]"
    )


def generate_elementary_streams(
    ffmpeg: Path,
    duration: float,
    temp_dir: Path,
    font: Optional[Path],
    dry_run: bool,
) -> Tuple[Path, Path, Path]:
    video = temp_dir / "video.m2v"
    audio51 = temp_dir / "audio_51.ac3"
    audio20 = temp_dir / "audio_20.ac3"

    duration_text = f"{duration:.6f}".rstrip("0").rstrip(".")
    cmd = [
        str(ffmpeg),
        "-hide_banner", "-nostats", "-loglevel", "warning", "-y",
        "-f", "lavfi", "-i",
        f"testsrc2=size=1920x1080:rate={SOURCE_FPS}:duration={duration_text}",
        "-f", "lavfi", "-i",
        f"sine=frequency=1000:sample_rate=48000:duration={duration_text}",
        "-f", "lavfi", "-i",
        f"sine=frequency=440:sample_rate=48000:duration={duration_text}",
        "-filter_complex", build_filter_complex(font),

        "-map", "[v]",
        "-c:v", "mpeg2video", "-flags", "+ildct+ilme", "-g", "15", "-bf", "2",
        "-b:v", "5000k", "-minrate", "3500k", "-maxrate", "8000k", "-bufsize", "1835k",
        "-f", "mpeg2video", str(video),

        "-map", "[a51]",
        "-c:a", "ac3", "-b:a", "384k", "-ar", "48000",
        "-f", "ac3", str(audio51),

        "-map", "[a20]",
        "-c:a", "ac3", "-b:a", "192k", "-ar", "48000",
        "-f", "ac3", str(audio20),
    ]
    run(cmd, dry_run=dry_run)
    return video, audio51, audio20


def mux_transport_stream(
    ffmpeg: Path,
    captioned_video: Path,
    audio51: Path,
    audio20: Path,
    output: Path,
    dry_run: bool,
) -> None:
    cmd = [
        str(ffmpeg),
        "-hide_banner", "-nostats", "-loglevel", "warning", "-y",
        "-fflags", "+genpts",
        "-r", VIDEO_FPS_TEXT, "-i", str(captioned_video),
        "-i", str(audio51),
        "-i", str(audio20),
        "-map", "0:v:0", "-map", "1:a:0", "-map", "2:a:0",
        "-c", "copy",
        "-metadata", "title=OpenSageTV Vibe deterministic seek test",
        "-metadata:s:a:0", "title=5.1 seek tone with whole-second sync pulses",
        "-metadata:s:a:1", "title=stereo seek tone with whole-second sync pulses",
        "-muxrate", "10000000",
        "-mpegts_flags", "+resend_headers",
        "-shortest",
        "-f", "mpegts",
        str(output),
    ]
    run(cmd, dry_run=dry_run)


def inspect_output(ffmpeg: Path, output: Path) -> None:
    # FFmpeg's input banner normally reports whether the MPEG-2 stream has
    # closed captions.  Do not fail the build if this informational probe
    # changes wording between FFmpeg versions.
    print("\nCaption verification probe:", flush=True)
    cmd = [
        str(ffmpeg), "-hide_banner", "-i", str(output),
        "-map", "0:v:0", "-frames:v", "1", "-f", "null", "-",
    ]
    with contextlib.suppress(subprocess.CalledProcessError):
        subprocess.run(cmd, check=True)


def count_a53_payloads(path: Path) -> int:
    """Count GA94 identifiers without loading a long fixture into memory."""
    signature = b"GA94"
    overlap = b""
    count = 0
    with path.open("rb") as stream:
        while True:
            block = stream.read(1024 * 1024)
            if not block:
                break
            data = overlap + block
            count += data.count(signature)
            overlap = data[-(len(signature) - 1):]
    return count


def probe_video_frame_count(ffmpeg: Path, path: Path) -> int:
    candidates = [ffmpeg.with_name("ffprobe.exe"), ffmpeg.with_name("ffprobe")]
    ffprobe = next((candidate for candidate in candidates if candidate.is_file()), None)
    if ffprobe is None:
        resolved = shutil.which("ffprobe")
        if not resolved:
            raise FileNotFoundError("ffprobe is required for final fixture verification")
        ffprobe = Path(resolved)
    completed = subprocess.run(
        [
            str(ffprobe), "-v", "error", "-count_frames", "-select_streams", "v:0",
            "-show_entries", "stream=nb_read_frames", "-of", "default=nw=1:nk=1",
            str(path),
        ],
        check=True,
        capture_output=True,
        text=True,
    )
    value = completed.stdout.strip().splitlines()[-1]
    frame_count = int(value)
    if frame_count <= 0:
        raise RuntimeError(f"ffprobe reported invalid video frame count: {value}")
    return frame_count


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------


def main(argv: Optional[Sequence[str]] = None) -> int:
    parser = argparse.ArgumentParser(
        description="Generate the OpenSageTV Vibe MPEG-2 seek test with embedded CEA-608/708 captions."
    )
    parser.add_argument(
        "--duration", "-d",
        type=parse_duration,
        default=DEFAULT_DURATION,
        help="file length: seconds or forms such as 120s, 2m, 15m, 00:15:00 (default: 900)",
    )
    parser.add_argument(
        "--caption-interval", "-i",
        type=float,
        default=DEFAULT_INTERVAL,
        help="seconds between new timeline caption lines (default: 0.5)",
    )
    parser.add_argument(
        "--cc",
        choices=("608", "708", "both"),
        default="both",
        help="caption format to embed (default: both)",
    )
    parser.add_argument(
        "--caption-prefix",
        default="PTS",
        help="text before each caption timestamp (default: PTS)",
    )
    parser.add_argument(
        "--font",
        help="optional font file for the visible drawtext overlay",
    )
    parser.add_argument(
        "--output", "-o",
        default=DEFAULT_OUTPUT,
        help=f"output MPEG-TS file (default: {DEFAULT_OUTPUT})",
    )
    parser.add_argument(
        "--keep-temp",
        action="store_true",
        help="keep intermediate .m2v/.ac3 files beside the output",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="print FFmpeg commands without running them",
    )
    args = parser.parse_args(argv)

    if args.caption_interval <= 0:
        parser.error("--caption-interval must be greater than zero")

    script_dir = Path(__file__).resolve().parent
    ffmpeg = local_ffmpeg(script_dir)
    font = find_font(args.font)
    output = Path(args.output).expanduser().resolve()

    print("OpenSageTV Vibe CC test generator")
    print(f"  FFmpeg          : {ffmpeg}")
    print(f"  Duration        : {args.duration:.3f} s ({format_clock(args.duration)})")
    print(f"  Video           : 1920x1080i @ {VIDEO_FPS_TEXT}")
    print(f"  Captions        : CEA-{args.cc if args.cc != 'both' else '608 + CEA-708'}")
    print(f"  Caption interval: {args.caption_interval:g} s")
    print(f"  Output          : {output}")
    if font:
        print(f"  Drawtext font   : {font}")
    else:
        print("  Drawtext font   : FFmpeg/fontconfig default")

    if args.dry_run:
        temp_dir = script_dir / "vibe_seek_cc_dry_run"
        video, audio51, audio20 = generate_elementary_streams(
            ffmpeg, args.duration, temp_dir, font, True
        )
        print("\n[DRY RUN] Python would then inject GA94 A/53 cc_data into:")
        print(f"  {video}")
        print("[DRY RUN] and stream-copy mux the captioned video with:")
        print(f"  {audio51}")
        print(f"  {audio20}")
        return 0

    temp_context: contextlib.AbstractContextManager[str]
    if args.keep_temp:
        keep = output.parent / (output.stem + "_temp")
        keep.mkdir(parents=True, exist_ok=True)
        temp_context = contextlib.nullcontext(str(keep))
    else:
        output.parent.mkdir(parents=True, exist_ok=True)
        temp_context = tempfile.TemporaryDirectory(prefix="vibe_seek_cc_", dir=str(output.parent))

    try:
        with temp_context as temp_name:
            temp_dir = Path(temp_name)
            video, audio51, audio20 = generate_elementary_streams(
                ffmpeg, args.duration, temp_dir, font, False
            )

            captioned_video = temp_dir / "video_with_a53_cc.m2v"
            print("\nInjecting CEA captions into MPEG-2 pictures...", flush=True)
            coded_pictures, display_frames = inject_captions_mpeg2(
                video,
                captioned_video,
                args.duration,
                args.caption_interval,
                args.cc,
                args.caption_prefix,
            )
            print(f"  MPEG-2 pictures : {coded_pictures}")
            print(f"  Display frames  : {display_frames}")
            print(
                f"  Caption cues    : {int((args.duration - 1e-9) // args.caption_interval) + 1}"
            )

            output.parent.mkdir(parents=True, exist_ok=True)
            mux_transport_stream(
                ffmpeg, captioned_video, audio51, audio20, output, False
            )

        if not output.exists() or output.stat().st_size == 0:
            raise RuntimeError("FFmpeg completed but the output file is missing or empty")

        a53_payload_count = count_a53_payloads(output)
        output_frame_count = probe_video_frame_count(ffmpeg, output)
        if a53_payload_count != output_frame_count:
            raise RuntimeError(
                "caption payload verification failed: "
                f"final TS has {output_frame_count} video pictures but "
                f"{a53_payload_count} GA94 payloads"
            )

        print(f"\nDONE: {output}")
        print(f"Size: {output.stat().st_size / (1024 * 1024):.1f} MiB")
        print(f"A/53 GA94 payloads: {a53_payload_count} (one per final TS picture)")
        inspect_output(ffmpeg, output)
        return 0

    except subprocess.CalledProcessError as exc:
        print(f"\nERROR: FFmpeg failed with exit code {exc.returncode}", file=sys.stderr)
        return exc.returncode or 1
    except Exception as exc:
        print(f"\nERROR: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
