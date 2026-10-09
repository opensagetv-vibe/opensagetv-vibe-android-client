import unittest
from unittest import mock
from sagetv_dev_mcp import server


class DvdHeldInputTests(unittest.TestCase):
    def test_timescroll_cursor_intent_is_preserved_in_compact_snapshot(self):
        state = {"dvdTimeScrollActive": True, "dvdTimeScrollEntryPositionMs": 540000,
                 "dvdTimeScrollSteps": -1}
        compact = server._compact_state(state)
        for key, value in state.items(): self.assertEqual(value, compact[key])

    def test_one_ordered_gesture_is_sent_once(self):
        with mock.patch.object(server.adb, "dev_control", return_value={"queued": True}) as control:
            self.assertEqual({"queued": True}, server.dev_dvd_arrow_hold("left", 6500))
            control.assert_called_once_with("dvd_arrow_hold", keycode=21, hold_ms=6500)

    def test_out_of_scope_keys_and_durations_never_reach_device(self):
        with mock.patch.object(server.adb, "dev_control") as control:
            for key, duration in (("HOME", 100), ("UP", 19), ("LEFT", 12001)):
                with self.assertRaises(ValueError): server.dev_dvd_arrow_hold(key, duration)
            control.assert_not_called()

    def test_dedicated_scan_hold_uses_the_same_foreground_physical_input_path(self):
        with mock.patch.object(server.adb, "dev_control", return_value={"queued": True}) as control:
            server.dev_dvd_arrow_hold("FF", 9500)
            control.assert_called_once_with("dvd_arrow_hold", keycode=90, hold_ms=9500)
            control.reset_mock()
            server.dev_dvd_arrow_hold("RW", 200)
            control.assert_called_once_with("dvd_arrow_hold", keycode=89, hold_ms=200)


if __name__ == "__main__": unittest.main()
