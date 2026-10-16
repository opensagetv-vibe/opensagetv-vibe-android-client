"""Source validator must recognize guarded binding without scanning build trees."""
import importlib.util
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT/'scripts'))
spec=importlib.util.spec_from_file_location('source_validator', ROOT/'scripts/validate_project.py')
validator=importlib.util.module_from_spec(spec);spec.loader.exec_module(validator)


class SourceValidationTests(unittest.TestCase):
    def test_original_native_binding_is_preserved(self):
        self.assertTrue(validator.native_surface_binding_present(
            'player.setVideoSurfaceView((SurfaceView) context.getVideoView());'))

    def test_scoped_helper_requires_callsite_and_native_not_only_gpu_output(self):
        markers=['private void bindVideoOutput(final ExoPlayer expected)',
                 'bindVideoOutput(player);',
                 'expected.setVideoSurfaceView((SurfaceView) context.getVideoView());']
        self.assertTrue(validator.native_surface_binding_present('\n'.join(markers)))
        for removed in range(len(markers)):
            self.assertFalse(validator.native_surface_binding_present(
                '\n'.join(value for i,value in enumerate(markers) if i!=removed)))

    def test_generated_tree_is_pruned_before_file_stat(self):
        with tempfile.TemporaryDirectory() as temporary:
            root=Path(temporary)
            (root/'build').mkdir();(root/'src').mkdir()
            (root/'build'/'broken.gradle').write_text('firebase',encoding='utf-8')
            (root/'src'/'active.gradle').write_text('firebase',encoding='utf-8')
            original=Path.is_file
            def checked(path):
                if 'build' in path.parts: raise OSError('inaccessible generated Linux link')
                return original(path)
            with patch.object(Path,'is_file',checked): hits=list(validator.active_firebase_hits(root))
            self.assertEqual([(Path('src/active.gradle'),1,'firebase')],hits)


if __name__=='__main__':unittest.main()
