"""Focused source/camera matching tests for speech-based A/V diagnostics."""

from __future__ import annotations

import sys
from pathlib import Path
import unittest

import numpy as np


sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from analyze_recording_av_sync_webcam import (  # noqa: E402
    FPS, SAMPLE_RATE, audio_source_position, video_source_position,
)


class RecordingAvSyncWebcamTest(unittest.TestCase):
    def test_audio_finds_source_position_despite_room_noise(self) -> None:
        random = np.random.default_rng(7)
        source = random.normal(size=SAMPLE_RATE * 12).astype(np.float32)
        start = SAMPLE_RATE * 4
        camera = source[start:start + SAMPLE_RATE * 3].copy()
        camera += random.normal(scale=0.15, size=len(camera)).astype(np.float32)
        position, score = audio_source_position(source, camera)
        self.assertAlmostEqual(position, 4.0, delta=1 / SAMPLE_RATE)
        self.assertGreater(score, 0.2)

    def test_video_uses_temporal_changes_not_static_background(self) -> None:
        random = np.random.default_rng(11)
        background = random.uniform(30, 200, size=240)
        source = np.repeat(background[None, :], 80, axis=0)
        source[:, 80:120] += random.normal(0, 25, size=(80, 40))
        source = np.uint8(np.clip(source, 0, 255))
        camera = np.uint8(np.clip(source[25:50].astype(np.float32) * 0.82 + 15, 0, 255))
        position, spatial, motion = video_source_position(source, camera)
        self.assertAlmostEqual(position, 25 / FPS)
        self.assertGreater(spatial, 0.8)
        self.assertGreater(motion, 0.6)


if __name__ == "__main__":
    unittest.main()
