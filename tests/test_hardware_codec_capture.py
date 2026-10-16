from pathlib import Path
import importlib.util
import json
import tempfile
import time
import unittest

ROOT = Path(__file__).resolve().parents[1]


class HardwareCodecCaptureTests(unittest.TestCase):
    def test_60hz_capture_is_explicit_and_never_cfr_duplicates(self):
        text=(ROOT/'scripts/capture_hdmi_validation.py').read_text()
        self.assertIn("choices=('1080p30','720p60'),default='1080p30'",text)
        self.assertIn("if args.capture_mode=='720p60':",text)
        self.assertIn("command[command.index('-fps_mode')+1]='passthrough'",text)
        self.assertIn("del command[at:at+2]",text)
        self.assertIn('not encoded frame count',text)

    def test_live_stage_marker_rejects_expired_or_unverified_capture(self):
        spec=importlib.util.spec_from_file_location('hdmi_capture_scope',ROOT/'scripts/capture_hdmi_validation.py')
        module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
        with tempfile.TemporaryDirectory(prefix='vibe-capture-scope-') as temporary:
            path=Path(temporary)/'ready.json'
            path.write_text(json.dumps({'scopeVerified':True,'expiresAtMs':int((time.time()+40)*1000)}),encoding='utf-8')
            self.assertTrue(module.wait_for_live_stage(path,8,1)['scopeVerified'])
            path.write_text(json.dumps({'scopeVerified':True,'expiresAtMs':0}),encoding='utf-8')
            with self.assertRaisesRegex(ValueError,'stale'):
                module.wait_for_live_stage(path,8,1)
            path.write_text(json.dumps({'scopeVerified':False,'expiresAtMs':int((time.time()+40)*1000)}),encoding='utf-8')
            with self.assertRaisesRegex(ValueError,'unverified'):
                module.wait_for_live_stage(path,8,1)

    def test_capture_is_opt_in_and_does_not_claim_visual_pass(self):
        text = (ROOT / "scripts/mcp_hardware_codec_matrix.py").read_text()
        self.assertIn('"--capture-each-case", action="store_true"', text)
        self.assertIn("if args.capture_each_case:", text)
        capture = text.split("if args.capture_each_case:", 1)[1].split('print(f"PASS:', 1)[0]
        self.assertIn('"PENDING_VISUAL_REVIEW"', capture)
        self.assertIn('"ADB_COMPOSITED_SCREEN_NOT_HDMI_OR_SPEAKER"', capture)
        self.assertIn('call_dict(client, "take_screenshot"', capture)
        self.assertNotIn('"PASS_VISUAL"', capture)
        self.assertNotIn('dev_set_player_config', capture)

    def test_capture_follows_real_decoder_check_and_precedes_cleanup(self):
        text = (ROOT / "scripts/mcp_hardware_codec_matrix.py").read_text()
        decoder = text.index('require(state.get("health_videoDecoderKind") == "hardware"')
        capture = text.index('row["visualEvidence"]')
        cleanup = text.index('post_test_cleanup = restore_post_test_menu(client)', capture)
        self.assertLess(decoder, capture)
        self.assertLess(capture, cleanup)


if __name__ == "__main__":
    unittest.main()
