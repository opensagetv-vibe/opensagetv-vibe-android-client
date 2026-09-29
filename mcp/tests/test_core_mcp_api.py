import json
import unittest
from unittest.mock import MagicMock, patch

from sagetv_dev_mcp.core_mcp_api import CoreMcpApiClient, CoreMcpApiError


class _Response:
    def __init__(self, payload):
        self.payload = payload

    def __enter__(self):
        return self

    def __exit__(self, *_args):
        return False

    def read(self):
        return json.dumps(self.payload).encode("utf-8")


class CoreMcpApiClientTest(unittest.TestCase):
    @patch("sagetv_dev_mcp.core_mcp_api.urlopen")
    def test_authenticated_control_call(self, opened):
        opened.return_value = _Response({"ok": True, "contexts": ["444556303031"]})
        client = CoreMcpApiClient("http://server:8270", "secret")
        self.assertEqual(client.ui_context_names(), ["444556303031"])
        request = opened.call_args.args[0]
        self.assertEqual(request.get_header("Authorization"), "Bearer secret")
        self.assertIn(b"action=ui.list", request.data)

    @patch("sagetv_dev_mcp.core_mcp_api.urlopen")
    def test_current_media_file_from_ui_state(self, opened):
        opened.return_value = _Response({
            "ok": True,
            "media": {"mediaFileId": 42, "title": "Fixture"},
        })
        client = CoreMcpApiClient("http://server:8270", "secret")
        self.assertEqual(client.current_media_file_id("444556303031"), 42)

    @patch("sagetv_dev_mcp.core_mcp_api.urlopen")
    def test_exact_path_uses_cold_index_timeout(self, opened):
        opened.return_value = _Response({"ok": True, "mediaFileId": 42})
        client = CoreMcpApiClient("http://server:8270", "secret", timeout_s=15.0)
        client.resolve_exact_path(r"V:\OpenSageTV_Vibe_Tests\fixture.ts")
        self.assertEqual(opened.call_args.kwargs["timeout"], 180.0)

    @patch("sagetv_dev_mcp.core_mcp_api.time.sleep")
    def test_exact_path_retries_transient_post_connect_http_400(self, sleeping):
        client = CoreMcpApiClient("http://server:8270", "secret", timeout_s=15.0)
        client._request = MagicMock(side_effect=[
            CoreMcpApiError("HTTP 400: UI is still initializing"),
            {"ok": True, "mediaFileId": 42},
        ])
        self.assertEqual(client.resolve_exact_path(r"V:\fixture.ts")["mediaFileId"], 42)
        self.assertEqual(client._request.call_count, 2)
        sleeping.assert_called_once_with(0.5)

    @patch("sagetv_dev_mcp.core_mcp_api.urlopen")
    def test_watch_uses_bounded_server_wait_and_media_control_is_direct(self, opened):
        opened.return_value = _Response({"ok": True, "accepted": True})
        client = CoreMcpApiClient("http://server:8270", "secret", timeout_s=15.0)
        client.watch("444556303031", 42, from_beginning=True)
        self.assertEqual(opened.call_args.kwargs["timeout"], 75.0)
        self.assertIn(b"wait_ms=60000", opened.call_args.args[0].data)
        client.media_control("444556303031", "play")
        request = opened.call_args.args[0]
        self.assertIn(b"action=media.control", request.data)
        self.assertIn(b"operation=play", request.data)

    def test_resolve_context_uses_only_unique_context(self):
        client = CoreMcpApiClient("http://server:8270", "secret")
        client.ui_context_names = MagicMock(return_value=["SAGETV_PROCESS_LOCAL_UI", "444556303031"])
        self.assertEqual(client.resolve_context("44:45:56:30:30:31"), "444556303031")

    def test_missing_explicit_token_fails_closed(self):
        environment = MagicMock()
        environment.server_for_address.return_value = {
            "core_mcp_enabled": True,
            "core_mcp_base_url": "http://server:8270",
        }
        with patch("sagetv_dev_mcp.core_mcp_api.load_test_environment", return_value=environment), \
                patch.dict("os.environ", {}, clear=True):
            with self.assertRaises(CoreMcpApiError):
                CoreMcpApiClient.discover("server")


if __name__ == "__main__":
    unittest.main()
