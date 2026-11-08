"""A retained STOP recovery must not become a reconnect on ordinary seeks."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
CORE = ROOT / 'source/dev/core/src/main/java/opensagetv/vibe/miniclient'


class RestartReconnectTests(unittest.TestCase):
    def test_shared_wire_owner_acknowledges_flush_before_stock_reconnect(self):
        source = (CORE / 'MiniClientConnection.java').read_text()
        media = source.split('private void MediaThread()', 1)[1]
        ack = media.index('transport.replyCompleted()')
        recovery = media.index('myMedia.takeRetainedPushRestartReconnect()')
        self.assertLess(ack, recovery)
        self.assertIn('command == MediaCmd.MEDIACMD_FLUSH', media[ack:recovery])
        block = media[recovery:media.index('// SageTV closes', recovery)]
        self.assertIn('requestTransportRenegotiationReconnect()', block)
        for forbidden in ('postSageCommandEvent', '.seek(', 'Thread.sleep', 'CoreMcp'):
            self.assertNotIn(forbidden, block)

    def test_only_selected_and_actual_push_retained_stop_can_arm(self):
        source = (CORE / 'MediaCmd.java').read_text()
        flush = source.split('case MEDIACMD_FLUSH:', 1)[1].split('case MEDIACMD_PUSHBUFFER:', 1)[0]
        self.assertIn('"dynamic".equalsIgnoreCase(client.properties().getStreamingMode())', flush)
        self.assertIn('pushMode && playa != null, dvdSessionPending', flush)
        self.assertIn('retainedPlayerStopped', flush)
        self.assertIn('MiniPlayerPlugin.STOPPED_STATE', flush)
        self.assertIn('MiniPlayerPlugin.EOS_STATE', flush)
        self.assertLess(flush.index('retainedPushRestart.flush('), flush.index('playa.flush()'))
        stop = source.split('case MEDIACMD_STOP:', 1)[1].split('case MEDIACMD_PAUSE:', 1)[0]
        self.assertIn('retainedPlayerStopped = true', stop)
        self.assertEqual(3, source.count('retainedPushRestart.reset()'))

    def test_policy_is_one_shot_bounded_and_does_not_own_a_seek_target(self):
        policy = (CORE / 'RetainedPushRestartPolicy.java').read_text()
        for required in ('DISPATCH_BUDGET_MS = 250L', 'used = true',
                         'selectedPush && actualPush && !dvd && stopped'):
            self.assertIn(required, policy)
        for forbidden in ('Thread.sleep', '.seek(', 'new Thread', 'Timer', 'CoreMcpApi'):
            self.assertNotIn(forbidden, policy)


if __name__ == '__main__':
    unittest.main()
