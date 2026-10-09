import unittest
from unittest.mock import patch

from sagetv_dev_mcp import server


class ExitNavigationTests(unittest.TestCase):
    def wait(self, states, timeout=5):
        clock = [0.0]

        def now():
            clock[0] += 0.1
            return clock[0]

        with patch.object(server.adb, "app_status", side_effect=states), \
                patch.object(server.time, "monotonic", side_effect=now), \
                patch.object(server.time, "sleep"):
            return server._wait_exit_background_settled(timeout)

    @staticmethod
    def browser(name):
        return {"running": True, "foreground": True,
                "resumedActivity": "opensagetv.vibe.miniclient.debug/" + name}

    def test_background_must_stabilize_before_stop(self):
        activity = {"running": True, "foreground": False,
                    "resumedActivity": "com.android.launcher3/.Launcher"}
        self.assertEqual(self.wait([activity] * 12),
                         {"settled": True, "basis": "stable_background"})

    def test_browser_to_home_transition_starts_stability_window(self):
        tv = self.browser("opensagetv.vibe.miniclient.android.tv.MainActivity")
        home = {"running": True, "foreground": False,
                "resumedActivity": "com.android.launcher3/.Launcher"}
        self.assertEqual(self.wait([tv, tv, home] + [home] * 12)["basis"],
                         "stable_background")

    def test_already_stopped_needs_no_browser(self):
        self.assertEqual(self.wait([{"running": False}])["basis"], "already_stopped")

    def test_non_browser_wait_is_bounded_without_runtime_commands(self):
        playback = self.browser("opensagetv.vibe.miniclient.android.gdx.MiniClientGDXActivity")
        self.assertFalse(self.wait([playback] * 15, timeout=1)["settled"])

    def test_session_only_exit_keeps_existing_behavior(self):
        with patch.object(server.adb, "exit_session", return_value={"ok": True}), \
                patch.object(server, "_wait_exit_background_settled") as wait, \
                patch.object(server.adb, "force_stop") as stop:
            self.assertFalse(server.dev_exit_session(stop_app=False)["stopped"])
        wait.assert_not_called()
        stop.assert_not_called()

    def test_stop_waits_then_force_stops_exactly_once(self):
        order = []
        with patch.object(server.adb, "exit_session", side_effect=lambda: order.append("exit") or {}), \
                patch.object(server.adb, "key", side_effect=lambda key: order.append(key) or ""), \
                patch.object(server, "_wait_exit_background_settled",
                             side_effect=lambda: order.append("wait") or {"settled": True}), \
                patch.object(server.adb, "force_stop", side_effect=lambda: order.append("stop") or ""), \
                patch.object(server.adb, "app_status", return_value={"running": False}):
            result = server.dev_exit_session(stop_app=True)
        self.assertEqual(order, ["exit", "HOME", "wait", "stop"])
        self.assertTrue(result["stopped"])


if __name__ == "__main__":
    unittest.main()
