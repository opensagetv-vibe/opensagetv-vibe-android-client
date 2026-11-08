from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
CORE = ROOT / 'source/dev/core/src/main/java/opensagetv/vibe/miniclient'


class PushCreditPolicyTests(unittest.TestCase):
    def test_shared_wire_boundary_not_backend_keys_or_decoder_ring(self):
        source = (CORE / 'MediaCmd.java').read_text()
        self.assertIn('pushMode && !dvdSessionPending && playa != null, buffSize', source)
        self.assertLess(source.index('rv = ordinaryPushCredit.available('),
                        source.index('writeInt(rv, retbuf, 0)'))
        self.assertEqual(2, source.count('ordinaryPushCredit.reset()'))
        flush = source.split('case MEDIACMD_FLUSH:', 1)[1].split('case MEDIACMD_PUSHBUFFER:', 1)[0]
        self.assertNotIn('ordinaryPushCredit.reset()', flush)
        policy = (CORE / 'OrdinaryPushCreditPolicy.java').read_text()
        for forbidden in ('Thread.sleep', '.seek(', '.flush(', 'PrefStore', 'new byte['):
            self.assertNotIn(forbidden, policy)

    def test_diagnostics_separate_local_ring_capacity_and_completed_wire_credit(self):
        recorder = (ROOT / 'source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/diagnostics/PushStallDiagnosticRecorder.java').read_text()
        self.assertIn('transport.lastCompletedPushCreditBytes()', recorder)
        self.assertIn('"playerBufferFreeBytes", player.getBufferLeft()', recorder)


if __name__ == '__main__':
    unittest.main()
