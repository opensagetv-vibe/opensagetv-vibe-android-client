from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
NATIVE = ROOT/'source/dev/ijkplayer/native'
JAVA = ROOT/'source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/ijkplayer'


class IjkRawCaptionContractTest(unittest.TestCase):
    def test_native_real_side_data_before_drop_with_bounded_owned_payload(self):
        patch = (NATIVE/'raw-cea.patch').read_text()
        for token in ('AV_FRAME_DATA_A53_CC', 'VIBE_CEA_MAX_BYTES', 'nb_messages >= 128',
                      'msg_queue_put_private', 'msg_free_res(&message)', 'videoq.serial',
                      'av_rescale_q', 'start_time', 'no_time_adjust'):
            self.assertIn(token, patch)
        self.assertNotIn('MEDIA_TIMED_TEXT', patch)
        self.assertIn('DeleteLocalRef(env, bytes)', patch)
        self.assertIn('J4A_ExceptionCheck__catchAll', patch)

    def test_player_lease_seek_and_close_fence_callbacks(self):
        player = (JAVA/'IJKMediaPlayerImpl.java').read_text()
        session = (JAVA/'IjkCaptionSession.java').read_text()
        for token in ('rawCaptionSession == captionLease', 'player == listenerPlayer',
                      'captionLease.completeSeek()', 'captions.beginSeek()', 'captions.close()'):
            self.assertIn(token, player)
        self.assertIn('!rawCeaSupported ? null : new IjkCaptionSession(', player)
        self.assertIn('epoch == Epoch.this && clock.current()', session)
        self.assertIn('gate.accept(', session)
        self.assertIn('handler.removeCallbacks(tick)', session)
        self.assertIn('if (flush) selected.bridge.postFlush()', session)
        self.assertIn('executor.execute', session)
        self.assertIn('selected.bridge.isForwardingCurrentStream()', session)
        state = (ROOT/'source/dev/android-tv/src/debug/java/opensagetv/vibe/miniclient/android/tv/debug/DebugStateProvider.java').read_text()
        self.assertIn('ijk.getLegacyCaptionCallbackCountForDebug()', state)
        self.assertIn('ijk.isLegacyCaptionCallbackActiveForDebug()', state)
        self.assertNotIn('setOnTimedTextListener', player)

    def test_inventory_does_not_invent_a_service_language_or_track(self):
        player = (JAVA/'IJKMediaPlayerImpl.java').read_text()
        self.assertIn('captions.observed608()', player)
        self.assertIn('captions.observed708()', player)
        self.assertIn('Observed native data; service not signalled', player)
        self.assertIn('SubtitleCodec.CEA608, ""', player)
        build = (ROOT/'scripts/build_ijk_deinterlace.py').read_text()
        self.assertIn('native/raw-cea.patch', build)
        self.assertIn('native/vibe_cea_packet.h', build)
        self.assertIn('native/push-flush.patch', build)
        self.assertIn('native/vibe_cea_jni.h', build)
        self.assertIn('never authoritative libs', build)
        self.assertIn('never overwrite earlier evidence', build)
        self.assertIn("'patches': {name: digest(native/name)", build)
        self.assertIn("'javaAarSha256'", build)
        options = (JAVA/'IjkDecoderOptions.java').read_text()
        self.assertIn('"vibe-raw-cea"', options)
        self.assertIn('method.preferenceValue(), System.getProperty("os.arch", "")', options)

    def test_push_flush_is_not_an_unknown_file_seek_or_an_unconditional_hw_change(self):
        patch = (NATIVE/'push-flush.patch').read_text()
        for token in ('ffp->vibe_push_input', 'avformat_flush(is->ic)',
                      'buf_ptr = is->ic->pb->buf_end', 'SDL_AoutFlushAudio',
                      'vibe_flush || (is->seek_flags', '&& !vibe_flush'):
            self.assertIn(token, patch)
        self.assertIn('msec == -1L', patch)
        player = (JAVA/'IJKMediaPlayerImpl.java').read_text()
        self.assertIn('"vibe-push-input", softwarePush ? 1 : 0', player)
        self.assertIn('"vibe-hardware-step", hardwareStepTrial ? 1 : 0', player)
        self.assertIn('lastUri == null || !lastUri.startsWith("push:dvd")', player)
        self.assertIn('Pre-prepare FLUSH has no old decoded epoch', player)
        helper = (NATIVE/'vibe_cea_jni.h').read_text()
        self.assertIn('mOnNativeInvokeListener', helper)
        self.assertNotIn('DebugLog', helper)


if __name__ == '__main__': unittest.main()
