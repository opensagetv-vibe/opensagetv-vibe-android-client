from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
DEBUG = ROOT / "source/dev/android-tv/src/debug/java/opensagetv/vibe/miniclient/android/tv/debug"


class DebugReceiverDecompositionTests(unittest.TestCase):
    def test_receiver_delegates_completed_contract_slices(self):
        receiver = (DEBUG / "DevTestReceiver.java").read_text(encoding="utf-8")
        for call in (
            "DebugClientIdCommands.status(context)",
            "DebugClientIdCommands.configure(context, intent)",
            "DebugTuningCommands.configure(intent)",
            "DebugPlayerConfigCommands.configure(context, intent)",
            "DebugPlayerCommands.control(context, intent)",
            "DebugPlayerCommands.seekTime(context, intent, op)",
            "DebugPlayerCommands.seekRelative(context, intent)",
            "DebugPlayerCommands.comskip(context, intent)",
            "DebugPlayerCommands.subtitle(context, intent)",
            "DebugAudioFocusCommands.request(context, intent)",
            "DebugAudioFocusCommands.abandon(context)",
            "DebugSessionCommands.connectServer(context, intent)",
            "DebugSessionCommands.exitSession(context)",
            "DebugSessionCommands.sendCommand(context, intent)",
            "DebugSessionCommands.inputTextNative(context, intent)",
            "DebugSessionCommands.hideImeDirect()",
            "DebugSessionCommands.setImeSuppression(intent)",
            "DebugStateProvider.snapshot(context, MAX_RECOVERY_WATCHDOG_MS)",
            "DebugResponseFormatter.success(data)",
            "DebugResponseFormatter.failure(data)",
            "DebugAsyncPlayerChecks.run(this, context, intent, MAX_RECOVERY_WATCHDOG_MS)",
        ):
            self.assertIn(call, receiver)

    def test_response_envelope_is_unchanged(self):
        formatter = (DEBUG / "DebugResponseFormatter.java").read_text(encoding="utf-8")
        self.assertIn('return "ok=true;" + data;', formatter)
        self.assertIn('return "ok=false;" + data;', formatter)

    def test_extracted_slices_are_debug_only(self):
        for name in (
            "DebugClientIdCommands.java",
            "DebugTuningCommands.java",
            "DebugPlayerConfigCommands.java",
            "DebugPlayerCommands.java",
            "DebugStateProvider.java",
            "DebugResponseFormatter.java",
            "DebugValueParser.java",
            "DebugAsyncExecutor.java",
            "DebugSessionCommands.java",
            "DebugAsyncPlayerChecks.java",
            "DebugAudioFocusCommands.java",
        ):
            self.assertTrue((DEBUG / name).is_file(), name)
            self.assertFalse((ROOT / "source/dev/android-tv/src/main/java" / name).exists(), name)

    def test_async_checks_use_named_process_lifetime_executor(self):
        receiver = (DEBUG / "DevTestReceiver.java").read_text(encoding="utf-8")
        executor = (DEBUG / "DebugAsyncExecutor.java").read_text(encoding="utf-8")
        checks = (DEBUG / "DebugAsyncPlayerChecks.java").read_text(encoding="utf-8")
        self.assertNotIn('new Thread(new Runnable()', receiver)
        self.assertNotIn('DebugAsyncExecutor.execute(new Runnable()', receiver)
        self.assertIn('DebugAsyncExecutor.execute(new Runnable()', checks)
        self.assertIn('Executors.newCachedThreadPool', executor)
        self.assertIn('"SageTV-MCP-"', executor)
        self.assertIn('shutdownForProcessTeardown()', executor)

    def test_receiver_no_longer_owns_extracted_session_operations(self):
        receiver = (DEBUG / "DevTestReceiver.java").read_text(encoding="utf-8")
        for method in (
            "private String connectServer(",
            "private String exitSession(",
            "private String sendCommand(",
            "private String inputTextNative(",
            "private String hideImeDirect(",
            "private String setImeSuppression(",
        ):
            self.assertNotIn(method, receiver)


if __name__ == "__main__":
    unittest.main()
