import importlib.util,unittest
from pathlib import Path
from unittest.mock import patch

spec=importlib.util.spec_from_file_location('direct_network_proxy',Path(__file__).resolve().parents[1]/'scripts/direct_network_proxy.py')
proxy=importlib.util.module_from_spec(spec);spec.loader.exec_module(proxy)

class NetworkProxyBoundaries(unittest.TestCase):
    def test_buffer_observations_drop_tokens_and_payloads(self):
        shaper=proxy.Shaper()
        for _ in range(90):
            shaper.buffer_report('/v1/direct/buffer?token=secret&positionMs=10&bufferMs=6000&buffering=false&eligible=true',
                b'{"reason":"buffer-stable","samples":2,"attempts":0,"targetKbps":0,"testSessionId":"private"}')
        self.assertEqual(80,len(shaper.buffer_reports))
        self.assertNotIn('secret',proxy.json.dumps(shaper.buffer_reports))
        self.assertNotIn('private',proxy.json.dumps(shaper.buffer_reports))
        self.assertEqual(6000,shaper.buffer_reports[-1]['bufferMs'])
        self.assertTrue(shaper.buffer_reports[-1]['eligible'])
    def test_older_capability_profile_preserves_direct_not_quality(self):
        shaper=proxy.Shaper('old-quality')
        value=proxy.json.loads(shaper.old_capabilities(b'{"mimDirect":{"available":true,"contractVersion":1,"qualityHandoffRevision":1,"qualitySeekRevision":1,"qualityStartRevision":1,"qualityRecoveryRevision":1}}'))
        self.assertEqual({'available':True,'contractVersion':1},value['mimDirect'])
        self.assertEqual(1,shaper.fault_count)
    def test_absent_api_profile_only_refuses_capability_probe(self):
        shaper=proxy.Shaper('provider-missing')
        self.assertTrue(shaper.rejects('GET','/v1/capabilities'))
        self.assertFalse(shaper.rejects('POST','/v1/direct/teardown'))
    def test_prepare_fault_cannot_stop_or_corrupt_existing_media(self):
        shaper=proxy.Shaper('prepare')
        self.assertTrue(shaper.rejects('POST','/v1/direct/quality/prepare'))
        self.assertFalse(shaper.rejects('POST','/v1/direct/teardown'))
        self.assertFalse(shaper.rejects('GET','/v1/direct/media/'+'a'*64+'/seg_000001.ts'))
        self.assertEqual(1,shaper.fault_count)
    def test_candidate_fault_is_exact_ts_only_not_readiness_or_original(self):
        shaper=proxy.Shaper('candidate-media');shaper.candidate_reply('{"sessionToken":"'+'b'*64+'"}')
        self.assertTrue(shaper.rejects('GET','/v1/direct/media/'+'b'*64+'/seg_000001.ts'))
        self.assertFalse(shaper.rejects('GET','/v1/direct/media/'+'b'*64+'/stream.m3u8'))
        self.assertFalse(shaper.rejects('GET','/v1/direct/media/'+'a'*64+'/seg_000001.ts'))
        self.assertFalse(shaper.rejects('POST','/v1/direct/quality/commit'))
        with self.assertRaises(ValueError):shaper.candidate_reply('{"sessionToken":"arbitrary-url"}')
    def test_route_timings_cannot_export_handles_or_paths(self):
        shaper=proxy.Shaper();shaper.timing('segment',1,1.025,1.125,1200)
        self.assertEqual({'requests':1,'headerMs':25,'bodyMs':100,'maxHeaderMs':25,'maxBodyMs':100,'bodyBytes':1200},shaper.route_timings['segment'])
        with self.assertRaises(ValueError):shaper.timing('/v1/private-handle',1,2,3,0)
    def test_forwarder_preserves_known_length_http11_reuse(self):
        source=(Path(__file__).resolve().parents[1]/'scripts/direct_network_proxy.py').read_text()
        self.assertIn("protocol_version='HTTP/1.1'",source)
        self.assertIn("reusable=response.getheader('Content-Length') is not None",source)
        self.assertIn('self.close_connection=not reusable',source)
    def test_not_a_general_forward_proxy(self):
        self.assertEqual('/v1/capabilities',proxy.destination('http://192.168.10.232:31910/v1/capabilities'))
        for url in ['https://192.168.10.232:31910/v1/capabilities','http://192.168.10.175:31910/v1/capabilities',
                    'http://192.168.10.232:80/v1/capabilities','http://user@192.168.10.232:31910/v1/capabilities',
                    'http://192.168.10.232:31910/SageTV','http://192.168.10.232:31910/v1/capabilities#fragment']:
            with self.assertRaises(ValueError):proxy.destination(url)
    def test_reverse_mode_cannot_forward_arbitrary_origins(self):
        self.assertEqual('/v1/capabilities',proxy.destination('/v1/capabilities',True))
        for url in ['http://192.168.10.232:31910/v1/capabilities','//other/v1/captions','/outside','/v1/captions#fragment']:
            with self.assertRaises(ValueError):proxy.destination(url,True)
    def test_aggregate_reservations_and_profile_are_bounded(self):
        shaper=proxy.Shaper()
        with patch.object(proxy.time,'monotonic',side_effect=[100,120,170]),patch.object(proxy.time,'sleep') as sleep:
            shaper.delay(8192);shaper.delay(8192);shaper.delay(8192)
        waits=[c.args[0] for c in sleep.call_args_list]
        self.assertAlmostEqual(8192*8/12000000,waits[0]);self.assertAlmostEqual(8192*8/2400000,waits[1])
        self.assertAlmostEqual(waits[0],waits[2]);self.assertEqual(24576,shaper.bytes)
    def test_device_proxy_restore_is_owned_even_on_failed_gate(self):
        source=(Path(__file__).resolve().parents[1]/'scripts/mcp_direct_quality_gate.py').read_text()
        self.assertIn("cleanup['networkProxyRestored']=True",source)
        self.assertIn("settings delete global http_proxy' if previous_proxy=='null'",source)
        self.assertLess(source.index('finally:' ,source.index('except BaseException as error:')),source.index("cleanup['networkProxyRestored']"))

if __name__=='__main__':unittest.main()
