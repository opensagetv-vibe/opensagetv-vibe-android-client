"""Physical caption wrapper must not leak preferences or select another viewer."""
from pathlib import Path
import unittest

SOURCE = (Path(__file__).resolve().parents[1]/'scripts/mcp_caption_selection_test.py').read_text()


class CaptionSelectionGateTest(unittest.TestCase):
    def test_exact_idle_device_before_settings_and_child(self):
        self.assertIn("not r['before'].get('connected')", SOURCE)
        self.assertIn("env.device()['client_id']", SOURCE)
        self.assertLess(SOURCE.index('Requires exact idle'), SOURCE.index("'dev_checkpoint_settings'"))
        self.assertLess(SOURCE.index("'dev_checkpoint_settings'"), SOURCE.index('result = caption_main()'))
        self.assertNotIn('subprocess.run', SOURCE)

    def test_child_failure_still_restores_settings_and_power(self):
        cleanup = SOURCE.split('    finally:', 1)[1]
        self.assertIn("'dev_restore_settings'", cleanup)
        self.assertIn('Settings restoration failed', cleanup)
        self.assertIn('state_path(env.device_serial())', cleanup)
        self.assertIn("r['passed'] = False", cleanup)

    def test_dvb_does_not_claim_stock_cc_slot_callback(self):
        self.assertIn("if args.case == 'dvb':", SOURCE)
        self.assertIn("['--track-codec', 'DVB', '--toggle-off-on']", SOURCE)
        self.assertIn("['--legacy-extender-callback', '--cycle-stv-caption-states']", SOURCE)
        self.assertNotIn('tune_channel', SOURCE)


if __name__ == '__main__':
    unittest.main()
