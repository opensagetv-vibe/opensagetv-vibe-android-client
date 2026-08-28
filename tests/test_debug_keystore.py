import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


class DebugKeystoreTests(unittest.TestCase):
    def test_build_commands_ensure_debug_keystore(self):
        dev = (ROOT / "dev.sh").read_text()
        self.assertIn("ensure_debug_keystore", dev)
        self.assertIn("/workspace/scripts/ensure_debug_keystore.sh", dev)

    def test_generator_matches_sagetv_gradle_alias(self):
        script = (ROOT / "scripts/ensure_debug_keystore.sh").read_text()
        gradle = (ROOT / "source/dev/android-tv/build.gradle").read_text()
        self.assertIn('KEYSTORE="$ANDROID_DIR/debug.keystore"', script)
        self.assertIn('ALIAS="client"', script)
        self.assertRegex(gradle, r'(?s)debug\s*\{.*?keyAlias\s*=\s*"client"')
        self.assertIn('STOREPASS="android"', script)
        self.assertIn('KEYPASS="android"', script)

    def test_existing_keystore_is_preserved_and_repaired(self):
        script = (ROOT / "scripts/ensure_debug_keystore.sh").read_text()
        self.assertIn("Adding SageTV MiniClient debug signing alias", script)
        self.assertNotIn("rm -f", script)
        self.assertIn("Refusing to replace it automatically", script)


if __name__ == "__main__":
    unittest.main()
