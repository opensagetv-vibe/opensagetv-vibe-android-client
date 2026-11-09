from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
SOURCE = (ROOT/'scripts/mcp_dvd_parity_test.py').read_text()


class DvdParityGateTest(unittest.TestCase):
    def test_idle_identity_and_existing_sources_before_component(self):
        self.assertIn("not r['before'].get('connected')", SOURCE)
        self.assertIn("env.device()['client_id']", SOURCE)
        self.assertIn("'dvd_motion' if args.case == 'aladdin' else 'authored_dvd'", SOURCE)
        self.assertNotIn('tune_channel', SOURCE)

    def test_keeps_native_transport_approximate_seek_and_mappings(self):
        self.assertIn("'--disc-policy', 'native'", SOURCE)
        self.assertIn("'--seek-tolerance-ms', '12000'", SOURCE)
        self.assertIn("'keyMappingChanges': False", SOURCE)
        self.assertIn("'--mim-direct-mode', 'off'", SOURCE)

    def test_both_components_and_cleanup_must_pass(self):
        self.assertIn("r['disc']['settingsRestore'].get('restored')", SOURCE)
        self.assertIn('menu_code == 0', SOURCE)
        self.assertIn("'powerRestore'", SOURCE)
        disc = (ROOT/'scripts/mcp_disc_test.py').read_text()
        self.assertIn('DVD settings restoration failed; gate cannot PASS', disc)
        self.assertIn('Exact client/server and actual DVD title required for HDMI marker', disc)


if __name__ == '__main__': unittest.main()
