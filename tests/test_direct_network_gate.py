import importlib.util,unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('direct_network_gate',ROOT/'scripts/direct_network_gate.py')
gate=importlib.util.module_from_spec(spec);spec.loader.exec_module(gate)
class NetworkGateSafety(unittest.TestCase):
    def test_refuses_existing_custom_network_policy(self):
        base={'kind':'noqueue','handle':'0:','root':True}
        self.assertTrue(gate.pristine([base]))
        for other in [[],[dict(base,kind='fq_codel')],[dict(base,handle='1:')],[base,{'kind':'ingress'}],[dict(base,root=False)]]:
            self.assertFalse(gate.pristine(other))
    def test_exact_device_server_and_existing_development_container(self):
        command=gate.docker_command();joined=' '.join(command)
        self.assertIn('opensagetv-vibe-dev',command);self.assertIn('SAGETV_TEST_DEVICE_ALIAS=non_pro',command)
        self.assertIn('SAGETV_TEST_SERVER_ALIAS=vibe',command);self.assertIn('--network-shaped',command)
        self.assertNotIn('docker run',joined)
    def test_namespace_and_restoration_guards_are_not_global(self):
        source=(ROOT/'scripts/direct_network_gate.py').read_text()
        self.assertIn("if inspect()!=(identity,pid)",source)
        self.assertIn('match ip dst 192.168.10.25/32',source);self.assertIn('match ip sport 31910',source)
        self.assertIn('nsenter -t ',source);self.assertIn("report['originalNetworkRestored']",source)
        self.assertIn("q.get('handle')=='7a00:'",source)
if __name__=='__main__':unittest.main()
