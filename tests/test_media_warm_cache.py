from pathlib import Path
import tempfile
import unittest

from scripts.media_warm_cache import media_identity, mark_recent, recent_entry


class MediaWarmCacheTests(unittest.TestCase):
    def test_identity_uses_server_and_exact_media(self):
        self.assertEqual(
            media_identity(server="SAGE", port=31099, video_name="Movie"),
            "sage:31099|name:Movie",
        )
        self.assertIn(
            "path:/var/media/Movie.mkv",
            media_identity(server="sage", port=31099, server_path="/var/media/Movie.mkv"),
        )

    def test_recent_entry_expires(self):
        with tempfile.TemporaryDirectory() as root:
            path = Path(root) / "warm.json"
            mark_recent(path, "server|name:file", startup_ms=4200, now=100.0)
            self.assertIsNotNone(recent_entry(path, "server|name:file", 60.0, now=159.0))
            self.assertIsNone(recent_entry(path, "server|name:file", 60.0, now=161.0))

    def test_cache_contains_no_credentials(self):
        with tempfile.TemporaryDirectory() as root:
            path = Path(root) / "warm.json"
            mark_recent(path, "server:31099|name:file", startup_ms=10, now=1.0)
            text = path.read_text(encoding="utf-8")
            self.assertNotIn("password", text.lower())
            self.assertNotIn("username", text.lower())

    def test_each_successful_use_refreshes_the_sliding_timeout(self):
        with tempfile.TemporaryDirectory() as root:
            path = Path(root) / "warm.json"
            key = "server:31099|name:file"
            mark_recent(path, key, startup_ms=100, now=100.0)
            mark_recent(path, key, startup_ms=90, now=150.0)
            refreshed = recent_entry(path, key, 60.0, now=205.0)
            self.assertIsNotNone(refreshed)
            self.assertEqual(refreshed["startupMs"], 90)


if __name__ == "__main__":
    unittest.main()
