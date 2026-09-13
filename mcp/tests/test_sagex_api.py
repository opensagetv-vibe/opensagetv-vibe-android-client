from pathlib import Path
import sys
import unittest
from unittest.mock import Mock, patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))
from sagetv_dev_mcp.sagex_api import (
    MediaMatch,
    SageWebApiClient,
    SagexApiClient,
    SagexApiError,
)


class SagexApiTests(unittest.TestCase):
    def test_extract_media_matches_from_nested_result(self):
        payload = {"Result": [{"MediaFile": {"MediaFileID": "123", "MediaTitle": "Test Video"}}]}
        self.assertEqual(SagexApiClient._extract_media_matches(payload), [MediaMatch(123, "Test Video")])

    def test_resolve_context_matches_colonized_client_id(self):
        c = SagexApiClient("http://server:8080/sagex/api")
        with patch.object(c, "ui_context_names", return_value=["444556303031", "SAGETV_PROCESS_LOCAL_UI"]):
            self.assertEqual(c.resolve_context("44:45:56:30:30:31"), "444556303031")

    def test_find_media_prefers_exact_match(self):
        c = SagexApiClient("http://server:8080/sagex/api")
        payload = {"Result": [
            {"MediaFileID": 1, "MediaTitle": "My Show"},
            {"MediaFileID": 2, "MediaTitle": "My Show Extra"},
        ]}
        with patch.object(c, "call", side_effect=[payload, {"Result": []}]):
            matches, mode = c.find_media("my show", page_size=2)
        self.assertEqual(mode, "exact")
        self.assertEqual([m.media_file_id for m in matches], [1])

    def test_find_media_many_enumerates_index_once(self):
        c = SagexApiClient("http://server:8080/sagex/api")
        payload = {"Result": [
            {"MediaFileID": 1, "MediaTitle": "One"},
            {"MediaFileID": 2, "MediaTitle": "Two"},
        ]}
        with patch.object(c, "call", side_effect=[payload, {"Result": []}]) as call:
            found = c.find_media_many(["One", "Two"], page_size=2)
        self.assertEqual(call.call_count, 2)
        self.assertEqual(found["One"][0], [MediaMatch(1, "One")])
        self.assertEqual(found["Two"][0], [MediaMatch(2, "Two")])

    def test_watch_uses_mediafile_reference_and_context(self):
        c = SagexApiClient("http://server:8080/sagex/api")
        with patch.object(c, "call", return_value={"Result": "OK"}) as call:
            c.watch("444556303031", 123)
        call.assert_called_once_with("Watch", "mediafile:123", context="444556303031")

    def test_refresh_media_index_uses_stock_sagex_scan(self):
        c = SagexApiClient("http://server:8080/sagex/api")
        with patch.object(c, "call", return_value={}) as call:
            result = c.refresh_media_index(wait_until_done=False)
        call.assert_called_once_with("RunLibraryImportScan", "false")
        self.assertTrue(result["requested"])
        self.assertEqual(result["transport"], "sagex_run_library_import_scan")

    def test_web_fallback_reports_scan_unavailable_without_mutation(self):
        c = SageWebApiClient("http://server:8080/sage")
        result = c.refresh_media_index(wait_until_done=False)
        self.assertFalse(result["requested"])
        self.assertEqual(result["reason"], "run_library_import_scan_unavailable")

    def test_watch_accepts_older_sagex_async_task_serialization_failure(self):
        c = SagexApiClient("http://server:8080/sagex/api")
        error = SagexApiError(
            "Cannot Serialize [Type: sage.Catbert$AsyncTaskID]"
        )
        with patch.object(c, "call", side_effect=error):
            result = c.watch("444556303031", 123)
        self.assertTrue(result["accepted"])
        self.assertTrue(result["asynchronous"])
        self.assertEqual(result["serializationCompatibility"], "Catbert$AsyncTaskID")

    def test_watch_does_not_hide_other_sagex_failures(self):
        c = SagexApiClient("http://server:8080/sagex/api")
        with patch.object(c, "call", side_effect=SagexApiError("authentication failed")):
            with self.assertRaisesRegex(SagexApiError, "authentication failed"):
                c.watch("444556303031", 123)

    def test_discovery_respects_web_control_disabled_server(self):
        environment = Mock()
        environment.server_for_address.return_value = {
            "webserver_installed": False,
            "web_control_mode": "none",
        }
        with patch("sagetv_dev_mcp.sagex_api.load_test_environment", return_value=environment):
            with self.assertRaisesRegex(SagexApiError, "Web control is disabled"):
                SagexApiClient.discover("192.0.2.20")

    def test_web_interface_mode_skips_sagex_probe(self):
        environment = Mock()
        environment.server_for_address.return_value = {
            "webserver_installed": True,
            "web_control_mode": "web_interface",
        }
        expected = SageWebApiClient("http://server:8080/sage")
        with patch("sagetv_dev_mcp.sagex_api.load_test_environment", return_value=environment), \
                patch.object(SagexApiClient, "candidate_bases") as candidates, \
                patch.object(SageWebApiClient, "discover", return_value=expected):
            self.assertIs(SagexApiClient.discover("192.0.2.20"), expected)
        candidates.assert_not_called()

    def test_seek_uses_server_video_frame_context(self):
        c = SagexApiClient("http://server:8080/sagex/api")
        with patch.object(c, "call", return_value={"Result": "OK"}) as call:
            c.seek("444556303031", 480000)
        call.assert_called_once_with("Seek", 480000, context="444556303031")

    def test_seek_accepts_older_sagex_async_task_serialization_failure(self):
        c = SagexApiClient("http://server:8080/sagex/api")
        error = SagexApiError(
            "Cannot Serialize [Type: sage.Catbert$AsyncTaskID]"
        )
        with patch.object(c, "call", side_effect=error):
            result = c.seek("444556303031", 0)
        self.assertTrue(result["accepted"])
        self.assertTrue(result["asynchronous"])

    def test_closed_caption_state_uses_standard_sagetv_media_player_api(self):
        c = SagexApiClient("http://server:8080/sagex/api")
        with patch.object(c, "call", return_value={"Result": "CC1"}) as call:
            self.assertEqual(c.closed_caption_state("444556303031"), "CC1")
        call.assert_called_once_with(
            "GetMediaPlayerClosedCaptionState", context="444556303031"
        )

    def test_set_closed_caption_state_normalizes_off_without_video_property(self):
        c = SagexApiClient("http://server:8080/sagex/api")
        with patch.object(c, "call", return_value={"Result": None}) as call:
            c.set_closed_caption_state("444556303031", "off")
        call.assert_called_once_with(
            "SetMediaPlayerClosedCaptionState",
            "Captions Off",
            context="444556303031",
        )


if __name__ == "__main__":
    unittest.main()
