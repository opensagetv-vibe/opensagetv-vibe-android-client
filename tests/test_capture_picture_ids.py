import importlib.util
from pathlib import Path
import sys
import unittest

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'scripts'))
spec=importlib.util.spec_from_file_location('capture_ids',ROOT/'scripts/analyze_capture_picture_ids.py')
module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)


class CapturePictureIdTests(unittest.TestCase):
    def test_wrapped_original_ids_and_duplicate_capture_are_distinguished(self):
        report=module.summarize([254,255,0,0,1],60)
        self.assertEqual({'1':3,'0':1},report['transitions'])
        self.assertEqual(0,report['sourceIdsSkipped'])
        self.assertFalse(report['fullRetentionClaim'])

    def test_nominal_60fps_does_not_qualify_stale_picture_or_unknown_field(self):
        report=module.summarize([0]*120,60)
        self.assertEqual(0,report['advancingPictureRate'])
        self.assertFalse(report['fullRetentionClaim'])
        missed=module.summarize([0,2,None,3,5],60)
        self.assertEqual(2,missed['sourceIdsSkipped'])
        self.assertEqual(2,missed['transitions']['unknown'])

    def test_binary_sampling_rejects_blended_ambiguous_field_pixels(self):
        image=bytes([127])*80*80
        value,_=module.read_id(image,80,(0,0,80,80),20)
        self.assertIsNone(value)


if __name__=='__main__':unittest.main()
