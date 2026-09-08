import sys
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "mcp" / "src"))

from sagetv_dev_mcp.sagex_api import SageWebApiClient, SagexApiClient


class FakeSageWebApiClient(SageWebApiClient):
    def __init__(self):
        super().__init__("http://sagetv.test:8080/sage", "user", "password")
        self.calls = []

    def _request(self, path, params=None):
        self.calls.append((path, params or {}))
        if path == "Home":
            return (
                '<a href="ExtenderDetails?context=444556303031">DEV001</a>'
                '<a href="ExtenderDetails?context=455359414144">other</a>'
            )
        if path == "Search":
            return (
                '<a href="DetailedInfo?MediaFileId=10">Aladdin sequel</a>'
                '<a href="DetailedInfo?MediaFileId=20">Aladdin</a>'
            )
        if path == "DetailedInfo":
            title = "Aladdin" if int(params["MediaFileId"]) == 20 else "Aladdin sequel"
            return f"<title>Detailed Information for {title}</title>"
        if path in ("MediaFileCommand", "SageCommand"):
            return "ok"
        raise AssertionError((path, params))


class FakeSagexApiClient(SagexApiClient):
    def __init__(self):
        super().__init__("http://sagetv.test:8080/sagex/api")
        self.calls = []

    def call(self, command, *args, **kwargs):
        self.calls.append((command, args, kwargs))
        return {"Result": None}


class SageWebApiClientTests(unittest.TestCase):
    def test_resolves_normalized_miniclient_context(self):
        client = FakeSageWebApiClient()
        self.assertEqual("444556303031", client.resolve_context("44:45:56:30:30:31"))

    def test_finds_exact_imported_dvd_and_requests_dvd_filter(self):
        client = FakeSageWebApiClient()
        matches, mode = client.find_media("Aladdin")
        self.assertEqual("exact_web", mode)
        self.assertEqual([(20, "Aladdin")], [(m.media_file_id, m.title) for m in matches])
        search = next(params for path, params in client.calls if path == "Search")
        self.assertEqual("MediaFiles", search["searchType"])
        self.assertEqual("on", search["DVD"])

    def test_watch_uses_stock_web_interface_watch_now(self):
        client = FakeSageWebApiClient()
        result = client.watch("444556303031", 20)
        self.assertEqual("sage_web_watch_now", result["transport"])
        path, params = client.calls[-1]
        self.assertEqual("MediaFileCommand", path)
        self.assertEqual("WatchNow", params["command"])
        self.assertEqual("444556303031", params["context"])
        self.assertEqual(20, params["MediaFileId"])

    def test_remote_command_targets_exact_extender_context(self):
        client = FakeSageWebApiClient()
        result = client.remote_command("444556303031", "Full Screen")
        self.assertEqual("sage_web_remote", result["transport"])
        path, params = client.calls[-1]
        self.assertEqual("SageCommand", path)
        self.assertEqual("yes", params["RetImage"])
        self.assertEqual("Full Screen", params["command"])
        self.assertEqual("444556303031", params["context"])

    def test_clear_watched_removes_complete_stock_server_watch_record(self):
        client = FakeSageWebApiClient()
        result = client.clear_watched(20)
        self.assertTrue(result["accepted"])
        self.assertEqual("sage_web_clear_watched", result["transport"])
        path, params = client.calls[-1]
        self.assertEqual("MediaFileCommand", path)
        self.assertEqual("ClearWatched", params["command"])
        self.assertEqual(20, params["MediaFileId"])

    def test_clear_watched_uses_mediafile_reference_with_sagex(self):
        client = FakeSagexApiClient()
        client.clear_watched(20)
        self.assertEqual(("ClearWatched", ("mediafile:20",), {}), client.calls[-1])


if __name__ == "__main__":
    unittest.main()
