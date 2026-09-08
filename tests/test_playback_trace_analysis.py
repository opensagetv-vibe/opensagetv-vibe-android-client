import pathlib
import sys
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "mcp" / "src"))

from sagetv_dev_mcp.trace_analysis import analyze_jsonl


class PlaybackTraceAnalysisTest(unittest.TestCase):
    def test_summarizes_switch_and_seek_recovery(self):
        trace = "\n".join((
            '{"sequence":1,"event":"server_deinit_command","wallMs":10,"monotonicMs":100,"connectionGeneration":2,"playbackSessionGeneration":4}',
            '{"sequence":2,"event":"media_socket_recycle_after_deinit","wallMs":11,"monotonicMs":110,"connectionGeneration":2,"playbackSessionGeneration":-1}',
            '{"sequence":3,"event":"server_openurl_command","wallMs":12,"monotonicMs":140,"connectionGeneration":2,"playbackSessionGeneration":5}',
            '{"sequence":4,"event":"server_seek_command","detail":"requestedMs=120000","wallMs":13,"monotonicMs":150,"connectionGeneration":2,"playbackSessionGeneration":5}',
            '{"sequence":5,"event":"initial_seek_attached_to_source","detail":"appliedMs=120000","wallMs":14,"monotonicMs":160,"connectionGeneration":2,"playbackSessionGeneration":5}',
            '{"sequence":6,"event":"first_video_frame","wallMs":15,"monotonicMs":700,"connectionGeneration":2,"playbackSessionGeneration":5}',
        ))
        result = analyze_jsonl(trace)
        self.assertEqual(6, result["recordCount"])
        self.assertTrue(result["switchCycles"][0]["socketRecycled"])
        self.assertEqual(40, result["switchCycles"][0]["switchToOpenUrlMs"])
        self.assertEqual(120000, result["seeks"][0]["requestedMs"])
        self.assertEqual(120000, result["seeks"][0]["appliedMs"])
        self.assertEqual(550, result["seeks"][0]["seekToFirstFrameMs"])
        self.assertEqual([], result["warnings"])

    def test_flags_missing_recycle_parse_failure_and_error(self):
        trace = "\n".join((
            '{"sequence":8,"event":"server_deinit_command","wallMs":20,"monotonicMs":200}',
            'not-json',
            '{"sequence":10,"event":"player_error_timeout","wallMs":30,"monotonicMs":300}',
        ))
        result = analyze_jsonl(trace)
        self.assertTrue(any("no recorded media-socket recycle" in item for item in result["warnings"]))
        self.assertTrue(any("error/exception/timeout" in item for item in result["warnings"]))
        self.assertGreaterEqual(len(result["sequenceGaps"]), 1)


if __name__ == "__main__":
    unittest.main()
