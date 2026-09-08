import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


class LifecycleSchedulingTests(unittest.TestCase):
    def test_server_discovery_has_named_cancellable_owner(self):
        source = (
            ROOT
            / "source/dev/core/src/main/java/opensagetv/vibe/miniclient/ServerDiscovery.java"
        ).read_text(encoding="utf-8")
        client = (
            ROOT / "source/dev/core/src/main/java/opensagetv/vibe/miniclient/MiniClient.java"
        ).read_text(encoding="utf-8")

        self.assertIn("Executors.newSingleThreadExecutor", source)
        self.assertIn('"SageTV-ServerDiscovery-"', source)
        self.assertIn("pendingDiscovery.cancel(true)", source)
        self.assertIn("discoveryExecutor.shutdownNow()", source)
        self.assertIn("activeDiscoveryExecutor().submit", source)
        self.assertIn("discoveryExecutor = createDiscoveryExecutor()", source)
        self.assertIn("discoveryExecutor = null", source)
        self.assertIn("serverDiscovery.shutdown()", client)
        self.assertNotIn("Thread t = new Thread", source)

    def test_tv_background_work_is_main_looper_and_teardown_owned(self):
        source = (
            ROOT
            / "source/dev/android-tv/src/main/java/opensagetv/vibe/miniclient/android/tv/MainFragment.java"
        ).read_text(encoding="utf-8")

        self.assertIn("new Handler(Looper.getMainLooper())", source)
        self.assertIn("postDelayed(mBackgroundUpdater", source)
        self.assertGreaterEqual(source.count("removeCallbacks(mBackgroundUpdater)"), 3)
        self.assertNotIn("new Timer(", source)
        self.assertNotIn("extends TimerTask", source)

    def test_keyboard_delay_is_main_looper_and_teardown_owned(self):
        source = (
            ROOT
            / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/UIActivityLifeCycleHandler.java"
        ).read_text(encoding="utf-8")

        self.assertIn("new Handler(Looper.getMainLooper())", source)
        self.assertIn("mainHandler.postDelayed(pendingKeyboardTask, 200)", source)
        self.assertIn("mainHandler.removeCallbacks(pendingKeyboardTask)", source)
        self.assertGreaterEqual(source.count("cancelKeyboardTask();"), 2)
        self.assertNotIn("miniClientView.postDelayed", source)

    def test_primary_player_progress_updates_are_main_looper_and_cancelled(self):
        for relative in (
            "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/exoplayer2/Exo2MediaPlayerImpl.java",
            "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/media3/Media3MediaPlayerImpl.java",
        ):
            source = (ROOT / relative).read_text(encoding="utf-8")
            self.assertIn("new Handler(Looper.getMainLooper())", source)
            self.assertIn("progressHandler.removeCallbacks(progressRunnable)", source)
            self.assertGreaterEqual(source.count("cancelProgressUpdates();"), 2)
            self.assertNotIn("handler = new Handler()", source)

    def test_service_destruction_cannot_terminate_live_application_client(self):
        service = (
            ROOT
            / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/MiniclientService.java"
        ).read_text(encoding="utf-8")
        application = (
            ROOT
            / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/MiniclientApplication.java"
        ).read_text(encoding="utf-8")

        on_destroy = service.split("public void onDestroy()", 1)[1].split(
            "public IBinder onBind", 1
        )[0]
        on_terminate = application.split("public void onTerminate()", 1)[1].split(
            "public void onLowMemory()", 1
        )[0]
        self.assertNotIn("getClient().shutdown()", on_destroy)
        self.assertIn("client.shutdown();", on_terminate)

    def test_optional_caption_work_cannot_reject_openurl(self):
        for relative in (
            "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/exoplayer2/Exo2MediaPlayerImpl.java",
            "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/media3/Media3MediaPlayerImpl.java",
        ):
            source = (ROOT / relative).read_text(encoding="utf-8")
            self.assertIn("RejectedExecutionException", source)
            self.assertIn("Skipping legacy-caption flush during client teardown", source)
            self.assertIn("Skipping legacy-caption drain during client teardown", source)


if __name__ == "__main__":
    unittest.main()
