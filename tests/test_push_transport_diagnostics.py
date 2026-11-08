from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
CORE = ROOT / 'source/dev/core/src/main/java/opensagetv/vibe/miniclient'
RECORDER = ROOT / 'source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/diagnostics/PushStallDiagnosticRecorder.java'


class PushTransportDiagnosticsTests(unittest.TestCase):
    def test_observer_is_numeric_bounded_and_not_a_recovery_path(self):
        text = (CORE / 'MediaTransportDiagnostics.java').read_text()
        for prohibited in ('new Thread(', 'synchronized ', 'Thread.sleep', '.seek(', '.connect(', 'getAllStackTraces'):
            self.assertNotIn(prohibited, text)
        self.assertIn('Math.min(16, stack.length)', text)
        self.assertIn('before == revision', text)

    def test_reader_retains_order_and_disabled_readfully(self):
        text = (CORE / 'MiniClientConnection.java').read_text().split('private void MediaThread()', 1)[1]
        for marker in ('automatic_push_stall_diagnostics', 'transport.waitingForHeader()',
                       'transport.headerReceived(command, len)', 'transport.readPayload(is, cmdbuffer, len, observeTransport)'):
            self.assertIn(marker, text)
        reader = (CORE / 'MediaTransportDiagnostics.java').read_text()
        self.assertIn('input.readFully(buffer, 0, length)', reader)
        self.assertIn('if (count < 0) throw new EOFException()', reader)
        self.assertLess(text.index('transport.replyStarted('), text.index('os.write(retbuf, 0, retval)'))
        self.assertLess(text.index('os.flush()'), text.index('transport.replyCompleted()'))
        self.assertEqual(1, text.count('myMedia.ExecuteMediaCommand(command, len, cmdbuffer, retbuf)'))

    def test_incident_has_correlatable_id_time_stages_and_one_off_thread_capture(self):
        text = RECORDER.read_text()
        for marker in ('IncidentEvidence', 'threadCaptureScheduled', 'now - startedMs >= MINIMUM_INCIDENT_MS',
                       'incidentEvidence.id', 'startedWallMs', 'startedMonotonicMs', 'mediaWorkerStack',
                       'observedTransport.workerStack()', 'transport.snapshotJson()', 'incident.transport',
                       'unavailable_client_only', 'unavailable_no_portable_socket_api'):
            self.assertIn(marker, text)
        self.assertIn('if (!enabled()) return;', text)
        self.assertNotIn('getAllStackTraces', text)

    def test_snapshot_survives_real_mcp_projection(self):
        server = (ROOT / 'mcp/src/sagetv_dev_mcp/server.py').read_text()
        self.assertIn('"mediaTransportJson"', server)
        self.assertIn('mediaTransport.snapshotJson()', (CORE / 'ConnectionLifecycleDiagnostics.java').read_text())


if __name__ == '__main__':
    unittest.main()
