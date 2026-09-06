from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
DEV = ROOT / "source" / "dev"


class Api36TargetTests(unittest.TestCase):
    def test_target_sdk_is_36_without_changing_compile_sdk(self):
        build = (DEV / "build.gradle").read_text()
        self.assertIn('androidCompileSdkVersion = 36', build)
        self.assertIn('androidBuildToolsVersion = "36.0.0"', build)
        self.assertIn('androidTargetSdkVersion = 36', build)

    def test_tv_launchers_are_explicitly_exported_and_api36_compat_is_scoped(self):
        manifest = (
            DEV / "android-tv" / "src" / "main" / "AndroidManifest.xml"
        ).read_text()
        self.assertGreaterEqual(manifest.count('android:exported="true"'), 2)
        self.assertIn('android:enableOnBackInvokedCallback="false"', manifest)
        self.assertIn(
            'android.window.PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY',
            manifest,
        )

    def test_obsolete_permissions_are_removed_and_logs_use_fileprovider(self):
        tv_manifest = (
            DEV / "android-tv" / "src" / "main" / "AndroidManifest.xml"
        ).read_text()
        manifest = (
            DEV / "android-shared" / "src" / "main" / "AndroidManifest.xml"
        ).read_text()
        self.assertNotIn('RECORD_AUDIO', tv_manifest)
        self.assertNotIn('android.hardware.microphone', tv_manifest)
        self.assertNotIn('WRITE_EXTERNAL_STORAGE', manifest)
        self.assertNotIn('READ_EXTERNAL_STORAGE', manifest)
        self.assertNotIn('READ_PHONE_STATE', manifest)
        self.assertIn('androidx.core.content.FileProvider', manifest)
        self.assertIn('${applicationId}.fileprovider', manifest)

        app_util = (
            DEV
            / "android-shared"
            / "src"
            / "main"
            / "java"
            / "opensagetv"
            / "vibe"
            / "miniclient"
            / "android"
            / "AppUtil.java"
        ).read_text()
        self.assertNotIn('getExternalStoragePublicDirectory', app_util)
        self.assertIn('getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)', app_util)
        self.assertIn('WindowInsetsController', app_util)

        settings = (
            DEV
            / "android-shared"
            / "src"
            / "main"
            / "java"
            / "opensagetv"
            / "vibe"
            / "miniclient"
            / "android"
            / "ui"
            / "settings"
            / "SettingsFragment.java"
        ).read_text()
        self.assertIn('FileProvider.getUriForFile', settings)
        self.assertIn('ClipData.newRawUri', settings)
        self.assertIn('if (context == null)', settings)
        self.assertIn('Intent.FLAG_GRANT_READ_URI_PERMISSION', settings)
        self.assertIn('files[i].lastModified() > fileToShare.lastModified()', settings)

        prefs = (
            DEV / "android-shared" / "src" / "main" / "res" / "xml" / "prefs.xml"
        ).read_text()
        logging_section = prefs[prefs.index('android:key="use_log_to_sdcard"') - 100:]
        logging_section = logging_section[:logging_section.index('</PreferenceCategory>')]
        self.assertNotIn('android:enabled="false"', logging_section)


if __name__ == "__main__":
    unittest.main()
