import pathlib
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[1]


class SecureDecoderGateContractTest(unittest.TestCase):
    def test_authorized_drm_gate_is_debug_only(self):
        debug_manifest = (
            ROOT / "source/dev/android-tv/src/debug/AndroidManifest.xml"
        ).read_text(encoding="utf-8")
        main_manifest = (
            ROOT / "source/dev/android-tv/src/main/AndroidManifest.xml"
        ).read_text(encoding="utf-8")
        activity = "DebugDrmValidationActivity"
        self.assertIn(activity, debug_manifest)
        self.assertNotIn(activity, main_manifest)

    def test_gate_uses_published_axinom_vector_and_explicit_header(self):
        source = (
            ROOT
            / "source/dev/android-tv/src/debug/java/opensagetv/vibe/miniclient/android/tv/debug/DebugDrmValidationActivity.java"
        ).read_text(encoding="utf-8")
        self.assertIn("protected_dash_1080p_h264_singlekey/manifest.mpd", source)
        self.assertIn("drm-widevine-licensing.axprod.net/AcquireLicense", source)
        self.assertIn('"X-AxDRM-Message"', source)
        self.assertIn("onVideoDecoderInitialized", source)
        self.assertIn("videoDecoder=", source)
        self.assertIn("RESULT=PROBE", source)
        self.assertIn("RESULT=SECURE_PATH_CONFIRMED_OUTPUT_PROTECTED", source)
        self.assertIn("RESULT=FAIL", source)


if __name__ == "__main__":
    unittest.main()
