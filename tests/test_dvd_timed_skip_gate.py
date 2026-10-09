import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from mcp_dvd_timed_skip_test import verify_settled_timeline


def row():
    def sample(t, source, server, video, audio, flush=2):
        return {"serverTimeMs": server, "client": {"serverFlushSequence": flush,
                "health_isPlaying": True, "health_flushed": False, "health_playerError": "",
                "health_capturedMonotonicMs": t, "mediaTimeMs": source,
                "health_videoRendered": video, "health_audioRendered": audio}}
    return {"before": {"serverFlushSequence": 1}, "samples": [
        sample(1000, 3000000, 3010000, 100, 100, flush=1),
        sample(2000, 3000000, 3000300, 3, 3),
        sample(6000, 3004000, 3004200, 240, 128)]}


class DvdTimedSkipGateTest(unittest.TestCase):
    def test_real_decoded_source_and_server_advance_after_flush(self):
        result = verify_settled_timeline(row())
        self.assertEqual(1, result["realtimeRatio"])
        self.assertEqual(4000, result["observedDeviceMs"])

    def test_pre_flush_seek_guess_cannot_satisfy_gate(self):
        data = row()
        for s in data["samples"]:
            s["client"]["serverFlushSequence"] = 1
        with self.assertRaisesRegex(RuntimeError, "post-FLUSH"):
            verify_settled_timeline(data)

    def test_frozen_client_clock_fails(self):
        data = row()
        data["samples"][-1]["client"]["mediaTimeMs"] = 3000000
        with self.assertRaisesRegex(RuntimeError, "source clock"):
            verify_settled_timeline(data)

    def test_flush_sequence_change_does_not_make_old_draining_frames_a_landing(self):
        data = row()
        draining = {"serverTimeMs": 3010000, "client": dict(data["samples"][0]["client"])}
        draining["client"].update(serverFlushSequence=2, health_flushed=True,
                                   health_capturedMonotonicMs=1500)
        data["samples"].insert(1, draining)
        self.assertEqual(4000, verify_settled_timeline(data)["observedDeviceMs"])

    def test_frozen_server_clock_fails_despite_advancing_video(self):
        data = row()
        data["samples"][-1]["serverTimeMs"] = 3000300
        with self.assertRaisesRegex(RuntimeError, "Server timeline"):
            verify_settled_timeline(data)

    def test_new_decoder_counter_epoch_is_not_added_to_old_output(self):
        data = row()
        data["samples"][-1]["client"]["serverFlushSequence"] = 3
        with self.assertRaisesRegex(RuntimeError, "sustained"):
            verify_settled_timeline(data)

    def test_server_time_must_match_actual_source_not_guessed_destination(self):
        data = row()
        for s in data["samples"][1:]:
            s["serverTimeMs"] += 10000
        with self.assertRaisesRegex(RuntimeError, "differs"):
            verify_settled_timeline(data)

    def test_initial_seek_guess_can_expire_before_sustained_clock_agreement(self):
        data = row()
        data["samples"][1]["serverTimeMs"] += 10000
        last = {"serverTimeMs": 3008200, "client": dict(data["samples"][-1]["client"])}
        last["client"].update(health_capturedMonotonicMs=10000, mediaTimeMs=3008000,
                              health_videoRendered=400, health_audioRendered=300)
        data["samples"].append(last)
        result = verify_settled_timeline(data)
        self.assertEqual(1, result["initialClockReanchorSamples"])
        self.assertEqual(4000, result["observedDeviceMs"])
        self.assertEqual(200, result["maxServerClientDifferenceMs"])

    def test_late_divergence_is_not_trimmed_as_startup(self):
        data = row()
        last = {"serverTimeMs": 3018200, "client": dict(data["samples"][-1]["client"])}
        last["client"].update(health_capturedMonotonicMs=10000, mediaTimeMs=3008000,
                              health_videoRendered=400, health_audioRendered=300)
        data["samples"].append(last)
        with self.assertRaisesRegex(RuntimeError, "differs"):
            verify_settled_timeline(data)

    def test_gate_never_changes_keys_properties_or_fabricates_timeline(self):
        text = (ROOT / "scripts/mcp_dvd_timed_skip_test.py").read_text()
        self.assertIn('choices=("android", "remote", "public")', text)
        self.assertIn('control.media_control(context, operation)', text)
        self.assertNotIn('SetProperty', text)
        self.assertNotIn('dev_set_player_config', text)
        self.assertNotIn('command": "time_scroll"', text)


if __name__ == "__main__":
    unittest.main()
