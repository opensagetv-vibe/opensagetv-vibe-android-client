from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
NATIVE = ROOT/'source/dev/ijkplayer/native'


class NativePausedStepContractTest(unittest.TestCase):
    def test_only_successful_new_native_output_completes_step(self):
        patch = (NATIVE/'paused-step.patch').read_text()
        header = (NATIVE/'vibe_paused_step.h').read_text()
        self.assertIn('SDL_VoutDisplayYUVOverlay(ffp->vout, vp->bmp) == 0', patch)
        for token in ('ffp->vibe_push_input', 'ffp->vibe_raw_cea', 'is->pause_req',
                      'ms <= is->vibe_step_start_ms', 'frame->serial != is->videoq.serial',
                      'step_to_next_frame_l(ffp)', 'ffp_notify_msg3(ffp, 12065, request',
                      'SDL_AoutFlushAudio', 'request == 0'):
            self.assertIn(token, header)
        self.assertNotIn('stream_seek(', header)
        self.assertNotIn('avformat_seek', header)
        self.assertIn('vibe_step_discard_pcm', patch)
        self.assertIn('skip * is->audio_tgt.frame_size', patch)
        self.assertIn('post_event(env, weak_thiz, MEDIA_INFO, 12065, msg.arg1)', patch)

    def test_gate_preserves_settings_and_verifies_output_not_just_acceptance(self):
        source = (ROOT/'scripts/mcp_native_paused_step_test.py').read_text()
        for token in ("not r['before'].get('connected')", "env.device()['client_id']",
                      'api.resolve_context', 'nativeStepCompleted', 'nativeStepPositionMs',
                      'serverFlushSequence', 'serverSeekSequence', 'dev_restore_settings',
                      'powerRestore', 'captionRestore', 'playingRejected', 'reverseRejected'):
            self.assertIn(token, source)
        self.assertLess(source.index('api.resolve_context'), source.index('api.watch('))
        self.assertNotIn('tune_channel', source)
        self.assertNotIn('restart_server', source)

    def test_native_clock_observation_is_epoch_and_read_order_fenced(self):
        player = (ROOT/'source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/ijkplayer/IJKMediaPlayerImpl.java').read_text()
        block = player.split('public long getPlayerMediaTimeMillis(', 1)[1].split('if (pushMode)', 1)[0]
        self.assertLess(block.index('clockGeneration = pushPresentationClock.generation()'),
                        block.index('activePlayer.getCurrentPosition()'))
        self.assertLess(block.index('observedAtMs ='), block.index('activePlayer.getCurrentPosition()'))
        self.assertIn('position(clockGeneration, time, serverStartTime,', block)
        first_frame = player.split('if (what == IMediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START)', 1)[1].split('else if', 1)[0]
        self.assertIn('beginFreshNativeEpoch', first_frame)
        prepared = player.split('public void onPrepared(', 1)[1].split('player.start();', 1)[0]
        self.assertNotIn('beginFreshNativeEpoch', prepared)

    def test_paused_zero_needs_actual_video_from_java_and_decoder_epoch(self):
        player = (ROOT/'source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/ijkplayer/IJKMediaPlayerImpl.java').read_text()
        native = (NATIVE/'vibe_push_presentation.h').read_text()
        patch = (NATIVE/'presentation-clock.patch').read_text()
        for token in ('extra == nativeOutputEpoch.get()', 'armNativeOutputProof();',
                      'nativeOutputPresented && !seekPending', 'if (pushMode) seekPending = false;'):
            self.assertIn(token, player)
        for token in ('is->videoq.serial + 1', 'vibe_output_min_serial',
                      'frame->serial != is->videoq.serial', 'ffp_notify_msg3(ffp, 12066',
                      'vibe_paused_presentation_clock', '!is->pause_req'):
            self.assertIn(token, native)
        self.assertIn('VIBE_CEA_SERIAL_PROPERTY', patch)
        self.assertIn('OPTION_OFFSET(vibe_output_epoch)', patch)


if __name__ == '__main__': unittest.main()
