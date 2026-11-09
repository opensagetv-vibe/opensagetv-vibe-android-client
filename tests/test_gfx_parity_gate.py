from pathlib import Path
import unittest

SOURCE = (Path(__file__).resolve().parents[1]/'scripts/mcp_gfx_parity_test.py').read_text()


class GfxParityGateTest(unittest.TestCase):
    def test_scope_and_settings_are_guarded_before_watch(self):
        self.assertIn("not r['before'].get('connected')", SOURCE)
        self.assertIn("api.resolve_context(identity['clientId'])", SOURCE)
        self.assertLess(SOURCE.index("'dev_checkpoint_settings'"), SOURCE.index('api.watch('))

    def test_readonly_capabilities_no_fault_or_tuner_injection(self):
        self.assertIn("'capabilityChanges': False", SOURCE)
        self.assertNotIn('setProperty', SOURCE)
        self.assertNotIn('tune_channel', SOURCE)
        self.assertIn('NOT a physical input-to-photon time', SOURCE)

    def test_failure_restores_caption_preferences_and_power(self):
        self.assertIn("'dev_restore_settings'", SOURCE)
        self.assertIn('api.set_closed_caption_state(ctx, cc)', SOURCE)
        self.assertIn("'powerRestore'", SOURCE)
        self.assertIn('Settings restoration failed', SOURCE)


if __name__ == '__main__': unittest.main()
