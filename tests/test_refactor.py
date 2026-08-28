from __future__ import annotations
import importlib.util
from pathlib import Path
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / "scripts" / "apply_dev_refactor.py"
spec = importlib.util.spec_from_file_location("refactor", SCRIPT)
refactor = importlib.util.module_from_spec(spec)
assert spec.loader
spec.loader.exec_module(refactor)

ROOT_BUILD = """buildscript {\n dependencies {\n classpath 'com.google.gms:google-services:4.3.10'\n classpath 'com.google.firebase:firebase-crashlytics-gradle:2.8.1'\n }\n}\n"""
TV_BUILD = """apply plugin: 'com.android.application'\napply plugin: 'com.google.gms.google-services'\napply plugin: 'com.google.firebase.crashlytics'\nandroid {\n defaultConfig { applicationId \"jvl.sage.miniclient.android.tv.debug\" }\n buildTypes {\n release { resValue \"string\", \"app_name\", \"@string/app_name_release\" }\n debug {\n applicationIdSuffix = \".debug\"\n versionNameSuffix = \"-DEBUG\"\n resValue \"string\", \"app_name\", \"@string/app_name_debug\"\n }\n }\n}\ndependencies {\n implementation platform('com.google.firebase:firebase-bom:30.0.0')\n implementation 'com.google.firebase:firebase-crashlytics'\n implementation 'com.google.firebase:firebase-analytics'\n}\n"""
SHARED_BUILD = """dependencies {\n implementation 'com.google.firebase:firebase-crashlytics:18.2.10'\n implementation 'com.google.android.exoplayer:exoplayer-core:2.18.1'\n}\n"""
APP_JAVA = """package sagex.miniclient.android;\nimport com.google.firebase.crashlytics.FirebaseCrashlytics;\nclass MiniclientApplication { void x() {\n FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true);\n FirebaseCrashlytics.getInstance().setUserId(\"x\");\n }}\n"""
MANIFEST = """<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"><meta-data android:name=\"firebase_crashlytics_collection_enabled\" android:value=\"false\" /><application /></manifest>"""

class RefactorTests(unittest.TestCase):
    def make_tree(self, root: Path) -> None:
        (root / "android-tv").mkdir(parents=True)
        (root / "android-shared/src/main/java/sagex/miniclient/android").mkdir(parents=True)
        (root / "android-shared/src/main").mkdir(parents=True, exist_ok=True)
        (root / "build.gradle").write_text(ROOT_BUILD)
        (root / "android-tv/build.gradle").write_text(TV_BUILD)
        (root / "android-tv/google-services.json").write_text("{\"project_info\": {}}")
        (root / "android-shared/build.gradle").write_text(SHARED_BUILD)
        (root / "android-shared/src/main/java/sagex/miniclient/android/MiniclientApplication.java").write_text(APP_JAVA)
        (root / "android-shared/src/main/AndroidManifest.xml").write_text(MANIFEST)

    def test_refactor_isolated_identity_and_firebase_removal(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            self.make_tree(root)
            changes = []
            refactor.patch_root_gradle(root, False, changes)
            refactor.patch_tv_gradle(root, False, changes)
            refactor.remove_google_services_file(root, False, changes)
            refactor.patch_shared_gradle(root, False, changes)
            refactor.patch_application_java(root, False, changes)
            refactor.patch_manifest(root, False, changes)
            all_text = "\n".join(p.read_text() for p in root.rglob("*") if p.is_file())
            self.assertIn('applicationId "org.opensagetv.miniclient.dev"', all_text)
            self.assertIn("SageTV MiniClient Dev Debug", all_text)
            self.assertNotIn("com.google.firebase", all_text)
            self.assertNotIn("com.google.gms:google-services", all_text)
            self.assertNotIn("firebase_crashlytics_collection_enabled", all_text)
            self.assertFalse((root / "android-tv/google-services.json").exists())

if __name__ == "__main__":
    unittest.main()
