import pathlib
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[1]


class PushBufferLimitSourceContractTests(unittest.TestCase):
    def test_stock_core_bounded_choices_are_exposed_in_settings(self):
        arrays = (ROOT / "source/dev/android-shared/src/main/res/values/arrays.xml").read_text(encoding="utf-8")
        prefs = (ROOT / "source/dev/android-shared/src/main/res/xml/prefs.xml").read_text(encoding="utf-8")
        push_values = arrays.split('<string-array name="entryvalues_push_buffer_limit">', 1)[1].split('</string-array>', 1)[0]
        for value in ("auto", "32768", "65536", "131072"):
            self.assertIn(f"<item>{value}</item>", push_values)
        self.assertNotIn("<item>524288</item>", push_values)
        self.assertIn('android:key="push_buffer_limit"', prefs)

    def test_property_is_negotiated_without_changing_internal_ring_capacity(self):
        connection = (ROOT / "source/dev/core/src/main/java/opensagetv/vibe/miniclient/MiniClientConnection.java").read_text(encoding="utf-8")
        policy = (ROOT / "source/dev/core/src/main/java/opensagetv/vibe/miniclient/LegacyPushBufferLimitPolicy.java").read_text(encoding="utf-8")
        source = (ROOT / "source/dev/core/src/main/java/opensagetv/vibe/miniclient/net/PushBufferDataSource.java").read_text(encoding="utf-8")
        self.assertIn('"PUSH_BUFFER_LIMIT".equals(propName)', connection)
        self.assertIn("CORE_MAX_BYTES = 128 * 1024", policy)
        self.assertIn("PIPE_SIZE = 4 * 1024 * 1024", source)

    def test_debug_measurement_reports_limit_and_heap(self):
        state = (ROOT / "source/dev/android-tv/src/debug/java/opensagetv/vibe/miniclient/android/tv/debug/DebugStateProvider.java").read_text(encoding="utf-8")
        self.assertIn('";pushBufferEffectiveBytes="', state)
        self.assertIn('";clientHeapUsedBytes="', state)
        probe = (ROOT / "source/dev/android-tv/src/debug/java/opensagetv/vibe/miniclient/android/tv/debug/PlaybackHealthProbe.java").read_text(encoding="utf-8")
        self.assertIn('"getBlockedPushCount"', probe)
        self.assertIn('p + "dataSourceMaximumPushWriteMs"', probe)
        mcp_server = (ROOT / "mcp/src/sagetv_dev_mcp/server.py").read_text(encoding="utf-8")
        for key in (
            "pushBufferLimit",
            "pushBufferEffectiveBytes",
            "clientHeapUsedBytes",
            "health_dataSourceBlockedPushCount",
            "health_dataSourceMaximumPushWriteMs",
        ):
            self.assertIn(f'"{key}"', mcp_server)

    def test_physical_gate_defaults_to_a_real_legacy_push_path(self):
        script = (ROOT / "scripts" / "mcp_push_buffer_matrix.py").read_text(encoding="utf-8")
        self.assertIn('default="exoplayer"', script)
        self.assertIn('require(bool(state.get("health_pushMode"))', script)
        self.assertIn('"health_dataSourcePushCount"', script)
        self.assertIn('"dev_player_control"', script)
        self.assertNotIn('"dev_player_action"', script)
        self.assertIn('target = position - 30_000 if position >= 60_000 else position + 30_000', script)
        self.assertIn('flush_before = integer(pre_seek, "serverFlushSequence", 0)', script)
        self.assertIn('"flushRecoveryPassed": True', script)
        self.assertIn('and not args.skip_live', script)
        self.assertNotIn('"dev_set_live_channel"', script)
        self.assertIn('"command": "live_tv"', script)


if __name__ == "__main__":
    unittest.main()
