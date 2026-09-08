from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
CORE = ROOT / "source/dev/core/src/main/java/opensagetv/vibe/miniclient"
DEBUG = ROOT / "source/dev/android-tv/src/debug/java/opensagetv/vibe/miniclient/android/tv/debug"


class ConnectionLifecycleDiagnosticsTests(unittest.TestCase):
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
        self.assertIn('out.append(";debugStatusVersion=21")', provider)
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
