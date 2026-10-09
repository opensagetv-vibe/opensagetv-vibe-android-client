"""Registration corner detection must accept a bezel but reject clipped arms."""

from __future__ import annotations

import sys
from pathlib import Path
import unittest
from unittest import mock

import numpy as np


sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from analyze_av_sync_webcam import (  # noqa: E402
    corner_arm_counts, framing_registration, registration_visibility,
)


class AvSyncFramingTest(unittest.TestCase):
    def test_inset_l_arms_are_found_inside_visible_tv_bezel(self) -> None:
        mask = np.zeros((180, 320), dtype=bool)
        for x, y, dx, dy in ((15, 8, 1, 1), (304, 8, -1, 1),
                             (15, 170, 1, -1), (304, 170, -1, -1)):
            for step in range(17):
                mask[y, x + step * dx] = True
                mask[y + step * dy, x] = True
        counts = corner_arm_counts(mask)
        for name in ("topLeft", "topRight", "bottomLeft", "bottomRight"):
            self.assertGreaterEqual(counts[name]["horizontal"], 14)
            self.assertGreaterEqual(counts[name]["vertical"], 13)
            self.assertGreater(counts[name]["anchorX"], 1)
            self.assertGreater(counts[name]["anchorY"], 1)
            self.assertLess(counts[name]["anchorY"], 178)

    def test_clipped_bottom_anchor_is_exposed_at_image_boundary(self) -> None:
        mask = np.zeros((180, 320), dtype=bool)
        mask[-1, 14:35] = True
        mask[-14:, 14] = True
        counts = corner_arm_counts(mask)
        self.assertEqual(counts["bottomLeft"]["anchorY"], 179)

    def test_direct_hdmi_accepts_full_raster_corners_without_a_bezel(self) -> None:
        mask = np.zeros((180, 320), dtype=bool)
        for x, y, dx, dy in ((2, 2, 1, 1), (317, 2, -1, 1),
                             (2, 177, 1, -1), (317, 177, -1, -1)):
            for step in range(17):
                mask[y, x + step * dx] = True
                mask[y + step * dy, x] = True
        counts = corner_arm_counts(mask)
        camera_visible, *_ = registration_visibility(counts, "camera")
        hdmi_visible, *_ = registration_visibility(counts, "hdmi")
        self.assertFalse(all(camera_visible.values()))
        self.assertTrue(all(hdmi_visible.values()))
        mask[2:20, 317] = False
        missing_visible, *_ = registration_visibility(corner_arm_counts(mask), "hdmi")
        self.assertFalse(all(missing_visible.values()))

    def test_camera_prefers_settled_warm_corners_over_bright_bezel(self) -> None:
        frame = np.full((180, 320, 3), 45, dtype=np.uint8)
        frame[0, :, :] = 245
        frame[-1, :, :] = 245
        frame[:, 0, :] = 245
        frame[:, -1, :] = 245
        for x, y, dx, dy in ((8, 5, 1, 1), (311, 5, -1, 1),
                             (8, 174, 1, -1), (311, 174, -1, -1)):
            for step in range(17):
                if (x, y) != (8, 5) or step < 8:
                    frame[y, x + step * dx] = (240, 230, 185)
                frame[y + step * dy, x] = (240, 230, 185)
        with mock.patch("analyze_av_sync_webcam.run_bytes", return_value=frame.tobytes()) as run:
            result = framing_registration("ffmpeg", Path("capture.mkv"), "camera")
        self.assertTrue(result["allFourYellowCornersVisible"])
        self.assertEqual(result["registrationMethod"], "warm")
        self.assertIn("-ss", run.call_args.args[0])


if __name__ == "__main__":
    unittest.main()
