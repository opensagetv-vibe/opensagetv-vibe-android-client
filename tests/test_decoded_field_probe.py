import importlib.util
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[1]
class DecodedFieldProbeTests(unittest.TestCase):
    def test_field_probe_routes_by_enabled_mode_not_fixture_id(self):
        driver=(ROOT/'scripts/mcp_gpu_deinterlace_test.py').read_text()
        self.assertIn("cases[0].get('modes',{}).get('field_geometry') is not True",driver)
        self.assertNotIn("cases[0].get('id')!='decoded_field_ids'",driver)
    def test_only_debug_source_set_reads_finite_fixture_pixels(self):
        owner=(ROOT/'source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/GpuDeinterlaceOutput.java').read_text()
        self.assertNotIn('glReadPixels',owner)
        self.assertIn('if (!debug || probe == null || count < 1 || count > 64',owner)
        self.assertIn('--probeRemaining == 0',owner)
        self.assertLess(owner.index('frameProbe.frame(externalTexture'),owner.index('long delta=previousInputNs'))
        probe=(ROOT/'source/dev/android-tv/src/debug/java/opensagetv/vibe/miniclient/android/video/GpuDecodedFieldProbe.java').read_text()
        self.assertIn('glReadPixels(0,0,8,4',probe)
        self.assertIn('if(samples.length()<64)',probe)
        self.assertIn('JSONObject.NULL',probe)
        self.assertNotIn('setPlayback',probe)
    def test_field_id_precedes_real_interlace_and_cannot_overwrite(self):
        script=ROOT/'scripts/generate_field_id_fixture.py'
        spec=importlib.util.spec_from_file_location('fields',script)
        module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
        for order,mode in [('top','interleave_top'),('bottom','interleave_bottom')]:
            value=module.field_filter(order)
            self.assertLess(value.index('N/pow'),value.index('tinterlace='))
            self.assertIn(mode,value)
            self.assertIn('lt(Y,64)+gte(Y,H-64)',value)
        progressive=module.field_filter('top',True)
        self.assertIn('N/pow',progressive)
        self.assertIn('setfield=prog',progressive)
        self.assertNotIn('tinterlace',progressive)
        self.assertIn('refuse to overwrite',script.read_text())
        self.assertIn("choices=('ntsc','pal')",script.read_text())
        self.assertEqual('testsrc2=size=720x576:rate=50',module.source_filter('pal','sd'))
        self.assertEqual('testsrc2=size=720x480:rate=60000/1001',module.source_filter('ntsc','sd'))
        self.assertEqual('testsrc2=size=1920x1080:rate=50',module.source_filter('pal','hd'))
        self.assertEqual('testsrc2=size=1920x1080:rate=60000/1001',module.source_filter('ntsc','hd'))
        for order,mode in [('top','interleave_top'),('bottom','interleave_bottom')]:
            faster=module.fast_field_filter(order)
            self.assertIn(mode,faster)
            self.assertLess(faster.index('floor(n/128)'),faster.index('tinterlace'))
            self.assertEqual(18,faster.count('drawbox='))
            self.assertNotIn('pow(',faster)
        self.assertNotIn('tinterlace',module.fast_field_filter('top',True))

if __name__=='__main__': unittest.main()
