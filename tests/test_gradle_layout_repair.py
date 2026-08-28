from __future__ import annotations

import importlib.util
import shutil
import tempfile
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]


class GradleLayoutRepairTests(unittest.TestCase):
    def test_repair_restores_root_when_android_shared_file_overwrites_it(self):
        spec = importlib.util.spec_from_file_location('repair_dev_gradle', ROOT / 'scripts/repair_dev_gradle.py')
        module = importlib.util.module_from_spec(spec)
        assert spec.loader is not None
        spec.loader.exec_module(module)

        with tempfile.TemporaryDirectory() as td:
            ws = Path(td)
            (ws / 'source/dev/android-shared').mkdir(parents=True)
            (ws / 'config').mkdir(parents=True)
            canonical = (ROOT / 'config/dev-root-build.gradle.canonical').read_bytes()
            shared = (ROOT / 'source/dev/android-shared/build.gradle').read_bytes()
            (ws / 'config/dev-root-build.gradle.canonical').write_bytes(canonical)
            (ws / 'source/dev/android-shared/build.gradle').write_bytes(shared)
            (ws / 'source/dev/build.gradle').write_bytes(shared)

            self.assertEqual(module.repair(ws, check_only=True), 1)
            self.assertEqual(module.repair(ws, check_only=False), 0)
            self.assertEqual((ws / 'source/dev/build.gradle').read_bytes(), canonical)
            self.assertTrue((ws / 'source/dev/build.gradle.bad').exists())

    def test_dev_sh_repairs_before_test_validate_and_build(self):
        text = (ROOT / 'dev.sh').read_text(encoding='utf-8')
        self.assertIn('repair_dev_gradle()', text)
        import re
        for command in ('test', 'validate', 'build'):
            match = re.search(rf'^  {command}\)\n(?P<body>.*?^    ;;)', text, re.MULTILINE | re.DOTALL)
            self.assertIsNotNone(match, command)
            self.assertIn('repair_dev_gradle', match.group('body'))


if __name__ == '__main__':
    unittest.main()
