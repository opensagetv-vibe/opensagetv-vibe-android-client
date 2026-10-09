from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
CORE = ROOT / "source/dev/core/src/main/java/opensagetv/vibe/miniclient"
DEBUG = ROOT / "source/dev/android-tv/src/debug/java/opensagetv/vibe/miniclient/android/tv/debug"


class ConnectionLifecycleDiagnosticsTests(unittest.TestCase):
    def test_transcode_unsafe_native_fallback_is_rejected_before_core_negotiation(self):
        video = ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video"
        direct = (video / "MimDirectSessionClient.java").read_text()
        self.assertIn('"unsupported_video_stock_fixed"', direct)
        self.assertIn("supportsTranscodePullFallback(nativeVideoCodecs)", direct)
        self.assertLess(direct.index("supportsTranscodePullFallback(nativeVideoCodecs)"),
                        direct.index('Response capabilities = request("GET"'))
        lifecycle = (video.parent / "UIActivityLifeCycleHandler.java").read_text()
        self.assertIn("client.prepareCodecs(nativeVideo,", lifecycle)
        self.assertIn('android.text.TextUtils.join(",", nativeVideo)', lifecycle)
        connection = (CORE / "MiniClientConnection.java").read_text()
        self.assertNotIn("sourceVideoCodecs", connection)
        script = (ROOT / "scripts/mcp_session_test.py").read_text()
        self.assertIn('"direct-only", "direct-and-pull"', script)
        self.assertIn('args.mim_direct_startup_fault == "direct-and-pull"', script)
        self.assertIn('"late_failure_stock_fixed_reconnect"', script)

    def test_mim_direct_late_failure_reconnect_is_typed_bounded_and_stock_compatible(self):
        lifecycle = (ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/UIActivityLifeCycleHandler.java").read_text()
        listener = (ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/VibeEventListener.java").read_text()
        bus = (ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/VibeEventBus.java").read_text()
        base = (ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/BaseMediaPlayerImpl.java").read_text()
        direct = (ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/MimDirectSessionClient.java").read_text()
        self.assertIn("MimDirectFallbackReconnectEvent", listener)
        self.assertIn("event instanceof MimDirectFallbackReconnectEvent", bus)
        self.assertIn("restartMiniClientActivityForStockFixed(server)", lifecycle)
        self.assertIn("requestTransportRenegotiationReconnect()", lifecycle)
        self.assertIn("useInPlaceStockFixedReconnect()", lifecycle)
        # GFX recovery and MIM fallback share the same bounded Activity handoff.
        self.assertIn("pendingReplacementActivity", lifecycle)
        self.assertIn("REPLACEMENT_ACTIVITY_RESTART_DELAY_MS", lifecycle)
        self.assertIn("pendingReplacementActivity == null", lifecycle)
        self.assertIn("new Intent(activity, activity.getClass())", lifecycle)
        self.assertIn("appContext.startActivity(fallbackActivity)", lifecycle)
        self.assertIn("hasRenderedFirstVideoFrame()", base)
        self.assertIn("requestStockFixedReconnectForUnplayablePull()", base)
        self.assertIn('"start_failed_pull_fallback".equals(value)', direct)
        self.assertIn("suppressNextPrepareForStockFixed = false", direct)
        self.assertIn("late_failure_stock_fixed_reconnect", direct)
        self.assertIn("debugForceNextDirectStartFailure", direct)
        self.assertIn("debugFallbackPullUrl", base)
        receiver = (DEBUG / "DevTestReceiver.java").read_text()
        self.assertIn('"mim_direct_late_fallback_fault".equals(op)', receiver)
    def test_diagnostics_are_bounded_and_payload_free(self):
        source = (CORE / "ConnectionLifecycleDiagnostics.java").read_text()
        self.assertIn("MAX_EVENTS = 64", source)
        self.assertIn("AtomicLong NEXT_GENERATION", source)
        self.assertIn("mediaCommandCount", source)
        self.assertIn("gfxQueueMaxDepth", source)
        self.assertIn("eventQueueMaxDepth", source)
        self.assertNotIn("byte[]", source)
        self.assertNotIn("mediaPath", source)
        self.assertNotIn("password", source)

    def test_connection_records_required_ordering_boundaries(self):
        connection = (CORE / "MiniClientConnection.java").read_text()
        event_router = (CORE / "ConnectionEventRouter.java").read_text()
        for marker in (
            'lifecycle("media_socket_ready")',
            'lifecycle("gfx_socket_ready")',
            'lifecycle("connection_published")',
            'lifecycle("media_worker_launch")',
            'lifecycle("media_before_gfx_ready")',
            'lifecycle("gfx_worker_launch")',
            'workerStarted("gfx_read")',
            'mediaCommand(command)',
            'mediaReply(command)',
            'gfxDispatch(command)',
            'closeRequested()',
            'closeComplete()',
        ):
            self.assertIn(marker, connection)
        self.assertIn('workerStarted("event_router")', event_router)

    def test_debug_snapshot_exposes_trace_on_demand(self):
        provider = (DEBUG / "DebugStateProvider.java").read_text()
        server = (ROOT / "mcp/src/sagetv_dev_mcp/server.py").read_text()
        self.assertIn("ConnectionLifecycleDiagnostics.latestCompactWire()", provider)
        self.assertIn('out.append(";debugStatusVersion=22")', provider)
        self.assertIn('"connectionGeneration"', server)
        self.assertIn('"connectionRecent"', server)

    def test_physical_gate_covers_startup_queue_reconnect_and_teardown(self):
        script = (ROOT / "scripts/mcp_connection_order_test.py").read_text()
        dev = (ROOT / "dev.sh").read_text()
        self.assertIn("def validate_startup", script)
        self.assertIn('"media_before_gfx_ready"', script)
        self.assertIn('"connectionEventQueuedCount"', script)
        self.assertIn('"connectionGeneration"', script)
        self.assertIn("wait_closed_trace", script)
        self.assertIn('"HOME"', script)
        self.assertIn('default=default_fixture("seek_server_path"', script)
        self.assertIn("mcp-connection-order-test)", dev)


if __name__ == "__main__":
    unittest.main()
