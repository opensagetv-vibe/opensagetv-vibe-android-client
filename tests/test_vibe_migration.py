from pathlib import Path
import hashlib
import json
import unittest
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]


class VibeMigrationTests(unittest.TestCase):
    def test_repository_identity_files_exist(self):
        self.assertEqual("0.5.85", (ROOT / "VERSION").read_text().strip())
        release = (ROOT / "release.properties").read_text(encoding="utf-8")
        self.assertIn("VERSION=0.5.85", release)
        self.assertIn("REQUIRES_BUILD=true", release)
        self.assertTrue((ROOT / "MIGRATION_TO_OPENSAGETV_VIBE.md").is_file())
        self.assertTrue((ROOT / "docs" / "BASELINE_VALIDATION.md").is_file())
        self.assertTrue((ROOT / ".gitattributes").is_file())

    def test_artifact_name_is_consistent(self):
        expected = "OpenSageTV-Vibe-Android-Client-debug.apk"
        entrypoint = (ROOT / "docker" / "entrypoint.sh").read_text()
        cli = (ROOT / "mcp" / "src" / "sagetv_dev_mcp" / "cli.py").read_text()
        server = (ROOT / "mcp" / "src" / "sagetv_dev_mcp" / "server.py").read_text()
        for text in (entrypoint, cli, server):
            self.assertIn(expected, text)

    def test_android_identity_remains_isolated_during_migration(self):
        gradle = (ROOT / "source" / "dev" / "android-tv" / "build.gradle").read_text()
        self.assertIn('applicationId "opensagetv.vibe.miniclient"', gradle)
        self.assertIn('applicationIdSuffix = ".debug"', gradle)
        self.assertNotIn("jvl.sage.miniclient", gradle)
        self.assertGreaterEqual(
            gradle.count("OpenSageTV Vibe"), 2
        )


    def test_vibe_launcher_icon_assets_are_normalized(self):
        import struct

        res = ROOT / "source" / "dev" / "android-shared" / "src" / "main" / "res"
        legacy = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
        adaptive = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}

        def png_size(path):
            data = path.read_bytes()
            self.assertEqual(b"\x89PNG\r\n\x1a\n", data[:8], path)
            return struct.unpack(">II", data[16:24])

        self.assertFalse((ROOT / "branding" / "SageTV-Vibe.png").exists())
        for density, size in legacy.items():
            base = res / f"mipmap-{density}"
            self.assertEqual((size, size), png_size(base / "ic_launcher_v2.png"))
            self.assertEqual((size, size), png_size(base / "ic_launcher_v2_round.png"))
        for density, size in adaptive.items():
            base = res / f"mipmap-{density}"
            self.assertEqual((size, size), png_size(base / "ic_launcher_v2_background.png"))
            self.assertEqual((size, size), png_size(base / "ic_launcher_v2_foreground.png"))

        tv_manifest = (ROOT / "source" / "dev" / "android-tv" / "src" / "main" / "AndroidManifest.xml").read_text()
        shared_manifest = (ROOT / "source" / "dev" / "android-shared" / "src" / "main" / "AndroidManifest.xml").read_text()
        self.assertIn('android:icon="@mipmap/ic_launcher_v2"', tv_manifest)
        self.assertNotIn('android:roundIcon=', tv_manifest)
        self.assertIn('android:roundIcon="@mipmap/ic_launcher_v2_round"', shared_manifest)

        android = "{http://schemas.android.com/apk/res/android}"
        manifest_root = ET.fromstring(tv_manifest)
        application = manifest_root.find("application")
        self.assertEqual("@mipmap/ic_launcher_v2", application.get(android + "icon"))
        main_activity = next(
            activity
            for activity in application.findall("activity")
            if activity.get(android + "name") == ".MainActivity"
        )
        # Vibe owns distinct launcher implementation classes; the old shared
        # launcher class names are not registered in this APK.
        self.assertEqual("@drawable/banner_v2", main_activity.get(android + "banner"))
        self.assertEqual("@drawable/banner_v2", main_activity.get(android + "icon"))
        banner = ROOT / "source/dev/android-tv/src/main/res/drawable/banner_v2.png"
        self.assertEqual((320, 180), png_size(banner))
        self.assertFalse(
            (ROOT / "source/dev/android-tv/src/main/res/mipmap-xhdpi/banner_v2.png").exists()
        )

        launcher_activities = []
        for activity in application.findall("activity"):
            for intent_filter in activity.findall("intent-filter"):
                categories = {
                    node.get(android + "name")
                    for node in intent_filter.findall("category")
                }
                if "android.intent.category.LAUNCHER" in categories:
                    launcher_activities.append(activity.get(android + "name"))
        self.assertEqual(
            ["opensagetv.vibe.miniclient.android.phone.ServersActivity"],
            launcher_activities,
        )
        leanback_activities = []
        for activity in application.findall("activity"):
            for intent_filter in activity.findall("intent-filter"):
                categories = {
                    node.get(android + "name")
                    for node in intent_filter.findall("category")
                }
                if "android.intent.category.LEANBACK_LAUNCHER" in categories:
                    leanback_activities.append(activity.get(android + "name"))
        self.assertEqual([".MainActivity"], leanback_activities)
        launchers = {
            activity.get(android + "name"): activity
            for activity in application.findall("activity")
        }
        self.assertEqual("@mipmap/ic_launcher_v2", application.get(android + "icon"))
        self.assertEqual("@drawable/banner_v2", application.get(android + "banner"))
        self.assertIsNone(application.get(android + "roundIcon"))
        self.assertEqual(
            "@drawable/banner_v2",
            launchers[".MainActivity"].get(android + "icon"),
        )
        self.assertEqual(
            "@drawable/banner_v2",
            launchers[".MainActivity"].get(android + "banner"),
        )
        self.assertEqual(
            "@drawable/banner_v2",
            launchers[".MainActivity"].get(android + "logo"),
        )
        # Match the known-good launcher contract: the standard launcher inherits
        # the square application icon; only the Leanback launcher forces the
        # 16:9 TV banner as its icon/banner artwork.
        self.assertIsNone(
            launchers["opensagetv.vibe.miniclient.android.phone.ServersActivity"].get(android + "icon")
        )
        self.assertFalse(application.findall("activity-alias"))
        tv_launcher_source = (
            ROOT / "source/dev/android-tv/src/main/java/opensagetv/vibe/miniclient/android/tv/MainActivity.java"
        ).read_text(encoding="utf-8")
        phone_launcher_source = (
            ROOT / "source/dev/android-tv/src/main/java/opensagetv/vibe/miniclient/android/phone/ServersActivity.java"
        ).read_text(encoding="utf-8")
        self.assertIn("class MainActivity extends FragmentActivity", tv_launcher_source)
        self.assertIn("class ServersActivity extends FragmentActivity", phone_launcher_source)
        self.assertFalse(
            (ROOT / "source/dev/android-tv/src/main/java/opensagetv/vibe/miniclient/android/tv/VibeTvLauncherActivity.java").exists()
        )
        self.assertFalse(
            (ROOT / "source/dev/android-tv/src/main/java/opensagetv/vibe/miniclient/android/phone/VibeLauncherActivity.java").exists()
        )
        self.assertNotIn("sagex.miniclient", tv_manifest)

    def test_generated_logo_asset_manifest_matches_checkout(self):
        manifest = json.loads(
            (ROOT / "config" / "logo-assets.sha256").read_text(encoding="utf-8")
        )
        self.assertEqual("opensagetv-vibe-logo", manifest["source_project"])
        self.assertEqual("1.8.0", manifest["generator_version"])
        self.assertEqual(29, len(manifest["assets"]))
        for entry in manifest["assets"]:
            path = ROOT / entry["path"]
            self.assertTrue(path.is_file(), entry["path"])
            self.assertEqual(
                entry["sha256"], hashlib.sha256(path.read_bytes()).hexdigest()
            )

    def test_main_apk_owns_standard_and_tv_launcher_entries(self):
        settings = (ROOT / "source/dev/settings.gradle").read_text(encoding="utf-8")
        self.assertNotIn(":firetv-launcher", settings)
        self.assertFalse((ROOT / "source/dev/firetv-launcher").exists())

        manifest = (ROOT / "source/dev/android-tv/src/main/AndroidManifest.xml").read_text(
            encoding="utf-8"
        )
        self.assertIn("android.intent.category.LEANBACK_LAUNCHER", manifest)
        self.assertIn("android.intent.category.LAUNCHER", manifest)
        self.assertIn('android:banner="@drawable/banner_v2"', manifest)
        self.assertIn('android:icon="@mipmap/ic_launcher_v2"', manifest)

        assets = json.loads(
            (ROOT / "config/logo-assets.sha256").read_text(encoding="utf-8")
        )["assets"]
        paths = {entry["path"] for entry in assets}
        self.assertIn(
            "source/dev/android-tv/store-assets/amazon-fire-tv-app-icon-1280x720.png",
            paths,
        )
        self.assertIn(
            "source/dev/android-tv/store-assets/amazon-fire-tv-background-1920x1080.png",
            paths,
        )
        self.assertIn(
            "source/dev/android-tv/store-assets/amazon-tablet-small-icon-114x114.png",
            paths,
        )
        self.assertIn(
            "source/dev/android-tv/store-assets/amazon-tablet-large-icon-512x512.png",
            paths,
        )

        debug_manifest = (
            ROOT / "source/dev/android-tv/src/debug/AndroidManifest.xml"
        ).read_text(encoding="utf-8")
        self.assertNotIn('tools:node="removeAll"', debug_manifest)
        self.assertNotIn("opensagetv.vibe.miniclient.android.tv.MainActivity", debug_manifest)

    def test_normal_workspace_prefers_unified_build_environment(self):
        dev = (ROOT / "dev.sh").read_text(encoding="utf-8")
        self.assertIn("$ROOT/../opensagetv-vibe-build-env", dev)
        self.assertIn('-f "$BUILD_ENV_ROOT/opensagetv-vibe-dev.sh"', dev)
        self.assertIn('bash "$BUILD_ENV_ROOT/opensagetv-vibe-dev.sh"', dev)
        self.assertIn("UNIFIED_CONTAINER=", dev)
        self.assertIn("opensagetv-vibe-dev", dev)
        self.assertIn("CONTAINER_WORKSPACE=/workspace/android-client", dev)
        self.assertNotIn("OPENSAGETV_VIBE_ANDROID_STANDALONE", dev)
        self.assertNotIn("docker compose", dev)
        self.assertIn("JAVA_HOME=/opt/java/jdk17", dev)
        self.assertIn("GRADLE_USER_HOME=/work/.gradle/android", dev)

        windows = (ROOT / "dev.ps1").read_text(encoding="utf-8")
        windows_cmd = (ROOT / "dev.cmd").read_text(encoding="utf-8")
        self.assertIn("OPENSAGETV_VIBE_BUILD_ENV_ROOT", windows)
        self.assertIn("opensagetv-vibe-build-env", windows)
        self.assertIn("wsl.exe", windows)
        self.assertIn("./dev.sh", windows)
        self.assertIn("$linuxProjectRoot = Convert-ToWslPath $projectRoot", windows)
        self.assertIn("'--cd', $linuxProjectRoot", windows)
        self.assertIn("-ExecutionPolicy Bypass", windows_cmd)
        self.assertIn("dev.ps1", windows_cmd)

        update_windows = (ROOT / "update.ps1").read_text(encoding="utf-8")
        update_windows_cmd = (ROOT / "update.cmd").read_text(encoding="utf-8")
        self.assertIn("OPENSAGETV_VIBE_BUILD_ENV_ROOT", update_windows)
        self.assertIn("./update.sh", update_windows)
        self.assertIn("$linuxProjectRoot = Convert-ToWslPath $projectRoot", update_windows)
        self.assertIn("'--cd', $linuxProjectRoot", update_windows)
        self.assertIn("-ExecutionPolicy Bypass", update_windows_cmd)
        self.assertIn("update.ps1", update_windows_cmd)

    def test_tasks_has_one_authoritative_backlog(self):
        tasks = (ROOT / "TASKS.md").read_text(encoding="utf-8")
        agents = (ROOT / "AGENTS.md").read_text(encoding="utf-8")
        diagnostics = (ROOT / "docs" / "PLAYBACK_DIAGNOSTICS.md").read_text(encoding="utf-8")

        self.assertIn("only authoritative task backlog", tasks)
        self.assertIn("- [ ]", tasks)
        self.assertNotIn("- [x]", tasks.lower())
        self.assertFalse((ROOT / "TASK_CODEX.md").exists())
        self.assertIn("TASKS.md` is the only authoritative", agents)
        self.assertNotIn("- [ ]", diagnostics)

    def test_release_history_has_no_per_version_documents(self):
        self.assertFalse(list(ROOT.glob("UPDATE_v*.txt")))
        self.assertFalse(list(ROOT.glob("UPDATE_v*.md")))
        runner = (ROOT / "update.sh").read_text(encoding="utf-8")
        self.assertIn("release.properties", runner)
        self.assertIn("release-deletions.lst", runner)
        self.assertNotIn("UPDATE_v<version>.txt", runner)
        self.assertFalse((ROOT / "unzip_test_valitdate_build_install_lanuch.sh").exists())
        self.assertFalse((ROOT / "test_valitdate_build_install_lanuch.sh").exists())

        manifest_script = (ROOT / "scripts" / "project_manifest.py").read_text(encoding="utf-8")
        self.assertIn("(ROOT / name).is_file()", manifest_script)


if __name__ == "__main__":
    unittest.main()
