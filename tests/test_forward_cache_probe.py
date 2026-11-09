from pathlib import Path
import sys
from types import SimpleNamespace
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT/'scripts'))
import mcp_forward_cache_probe as gate


class ForwardCacheProbeTests(unittest.TestCase):
    def test_track_gate_keeps_offset_and_requires_new_selected_queue_proof(self):
        calls = []
        initial = {'audioTrackCount': 2, 'audioOffsetMs': -400, 'audioOutputMode': 'passthrough'}
        active = dict(initial)
        def command(client, name, args=None, **kwargs):
            calls.append(name)
            if name == 'dev_player_state': return dict(active)
            if name == 'dev_set_audio_track':
                active['selectedAudioTrack'] = args['index']+10
                return {'accepted': True, 'playerIndex': active['selectedAudioTrack']}
            if name == 'dev_wait_for_playback_started': return {'passed': True}
            self.fail('Unexpected output/offset mutation: '+name)
        with patch.object(gate, 'call_dict', side_effect=command), \
             patch.object(gate, 'probe', return_value={'passed': True}) as seek:
            rows = gate.probe_audio_tracks(None, None)
        self.assertEqual([1,0], [r['ordinal'] for r in rows])
        self.assertEqual(2, seek.call_count)
        self.assertNotIn('dev_set_active_audio', calls)

    def test_rejection_probe_checks_real_command_clock_and_unchanged_epoch(self):
        before = dict(clientId='test-id', serverAddress='192.168.10.175',
                      health_pushMode=True, pushForwardCacheState='ready=true,seeks=0',
                      health_playerPositionMs=20000, health_bufferedPositionMs=50000,
                      serverSeekSequence=2, serverFlushSequence=3, state=2)
        after = {**before, 'health_playerPositionMs': 21000, 'serverSeekSequence': 3}
        env = SimpleNamespace(device=lambda: {'client_id': 'test-id'},
                              server_address=lambda: '192.168.10.175')
        adb = SimpleNamespace(dev_control=lambda *a, **kw: {'replyLength': 0})
        with patch.object(gate, 'load_test_environment', return_value=env), \
             patch.object(gate, 'call_dict', side_effect=[before, after, before, after]), \
             patch.object(gate.time, 'sleep'), \
             patch.object(gate.time, 'monotonic', side_effect=[0,1,2,3]):
            result = gate.reject_outside_window(None, adb)
        self.assertEqual(['backward', 'beyond-buffer'], [r['targetKind'] for r in result])
        self.assertEqual([10000,110000], [r['targetMs'] for r in result])

    def test_rejected_target_must_not_flush_or_jump(self):
        before = dict(clientId='test-id', serverAddress='192.168.10.175',
                      health_pushMode=True, pushForwardCacheState='ready=true,seeks=0',
                      health_playerPositionMs=20000, health_bufferedPositionMs=50000,
                      serverSeekSequence=2, serverFlushSequence=3, state=2)
        env = SimpleNamespace(device=lambda: {'client_id': 'test-id'},
                              server_address=lambda: '192.168.10.175')
        for changes in ({'health_playerPositionMs': 0}, {'serverFlushSequence': 4},
                        {'pushForwardCacheState': 'ready=true,seeks=1'}, {'state': 0}):
            after = {**before, 'health_playerPositionMs': 21000, 'serverSeekSequence': 3, **changes}
            with self.subTest(changes=changes), \
                 patch.object(gate, 'load_test_environment', return_value=env), \
                 patch.object(gate, 'call_dict', side_effect=[before, after]), \
                 patch.object(gate.time, 'sleep'), \
                 patch.object(gate.time, 'monotonic', side_effect=[0,1]), \
                 self.assertRaises(RuntimeError):
                gate.reject_outside_window(None, SimpleNamespace(dev_control=lambda *a, **kw: {'replyLength': 0}))

    def test_caption_trial_retains_guards_and_cleanup_on_failures(self):
        source = (ROOT/'scripts/mcp_caption_test.py').read_text()
        self.assertIn("args.streaming != 'dynamic'", source)
        self.assertIn('probe_forward_cache(client, trial_adb)', source)
        cleanup = source.split('        caption_restore_failed = False', 1)[1]
        self.assertIn("enabled='false'", cleanup)
        self.assertIn('trial_adb.close()', cleanup)
        self.assertLess(cleanup.index('except Exception as exc:'), cleanup.index('restore_server_caption_state'))


if __name__ == '__main__': unittest.main()
