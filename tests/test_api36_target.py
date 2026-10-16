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

    def test_tv_launchers_are_exported_and_temporary_api36_bridges_are_removed(self):
        manifest = (
            DEV / "android-tv" / "src" / "main" / "AndroidManifest.xml"
        ).read_text()
        self.assertGreaterEqual(manifest.count('android:exported="true"'), 2)
        self.assertIn('android:enableOnBackInvokedCallback="true"', manifest)
        self.assertNotIn('android:enableOnBackInvokedCallback="false"', manifest)
        self.assertNotIn(
            'android.window.PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY',
            manifest,
        )

    def test_plain_playback_activities_register_api33_back_callbacks(self):
        shared = DEV / "android-shared" / "src" / "main" / "java"
        package = shared / "opensagetv" / "vibe" / "miniclient" / "android"
        controller = (package / "PredictiveBackController.java").read_text()
        lifecycle = (package / "UIActivityLifeCycleHandler.java").read_text()
        opengl = (package / "opengl" / "MiniClientOpenGLActivity.java").read_text()
        gdx = (package / "gdx" / "MiniClientGDXActivity.java").read_text()
        debug_state = (
            DEV
            / "android-tv"
            / "src"
            / "debug"
            / "java"
            / "opensagetv"
            / "vibe"
            / "miniclient"
            / "android"
            / "tv"
            / "debug"
            / "DebugStateProvider.java"
        ).read_text()
        mcp_server = (ROOT / "mcp" / "src" / "sagetv_dev_mcp" / "server.py").read_text()

        self.assertIn("Build.VERSION.SDK_INT >= 33", controller)
        self.assertIn("registerOnBackInvokedCallback", controller)
        self.assertIn("unregisterOnBackInvokedCallback", controller)
        self.assertIn("activity.finishAfterTransition()", controller)
        self.assertIn("boolean dispatchSystemBack()", lifecycle)
        self.assertIn("KeyEvent.KEYCODE_BACK", lifecycle)
        for activity in (opengl, gdx):
            self.assertIn("PredictiveBackController", activity)
            self.assertIn("predictiveBackController.register()", activity)
            self.assertIn("predictiveBackController.unregister()", activity)
            self.assertIn("predictiveBackController.dispatchLegacy()", activity)
        for key in (
            "systemBackInvocationCount",
            "systemBackLastActivity",
            "systemBackLastHandled",
        ):
            self.assertIn(key, debug_state)
            self.assertIn(f'"{key}"', mcp_server)

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
            DEV / "android-shared" / "src" / "main" / "res" / "xml" / "diagnostics_prefs.xml"
        ).read_text()
        logging_section = prefs[prefs.index('android:key="use_log_to_sdcard"') - 100:]
        logging_section = logging_section[:logging_section.index('</PreferenceCategory>')]
        self.assertNotIn('android:enabled="false"', logging_section)


if __name__ == "__main__":
    unittest.main()
