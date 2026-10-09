"""Preserve pending seeks without retaining unqualified native workarounds."""
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[1]
VIDEO=ROOT/'source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/ijkplayer'

class IjkPendingSeekTests(unittest.TestCase):
    def test_unprepared_play_state_preserves_latest_seek_including_zero(self):
        source=(VIDEO/'IJKMediaPlayerImpl.java').read_text()
        seek=source.split('public void seek(long timeInMS)',1)[1].split('private int getAudioTrackPosition',1)[0]
        guard=seek.index('player == null || !playerReady')
        pending=seek.index('preSeekPos = timeInMS;',guard)
        native=seek.index('seekToImpl(timeInMS);')
        self.assertLess(pending,native)
        self.assertIn('return;',seek[pending:native])
        self.assertNotIn('timeInMS > 0',seek)
        prepared=source.split('public void onPrepared(IMediaPlayer mp)',1)[1]
        self.assertIn('seekToImpl(preSeekPos);',prepared)
        self.assertIn('preSeekPos = -1;',prepared)

    def test_failed_tuple_workarounds_are_not_runtime_dependencies(self):
        player=(VIDEO/'IJKMediaPlayerImpl.java').read_text()
        data=(VIDEO/'IJKPullMediaSource.java').read_text()
        for term in ('seek-at-start','IjkPullBootstrapPolicy','nativePrepareSeek','IJK-static-bootstrap'):
            self.assertNotIn(term,player)
        for term in ('AvcTsBootstrapPrefix','IjkAbsoluteReadPolicy','bootstrap.overlay','getBootstrap'):
            self.assertNotIn(term,data)
        self.assertIn('return dataSource.read(position, bytes, offset, size);',data)
        self.assertIn('"fflags", "nobuffer"',(VIDEO/'IjkDecoderOptions.java').read_text())

    def test_lifecycle_phase_images_are_opt_in_and_not_automatic_visual_pass(self):
        source=(ROOT/'scripts/mcp_lifecycle_test.py').read_text()
        self.assertIn('"--capture-phase-images", action="store_true"',source)
        for phase in ('initialImage','homeReturnImage','userResumeImage'):
            block=source.split('evidence["'+phase+'"]',1)[0]
            self.assertTrue(block.rstrip().endswith('if args.capture_phase_images:'))
        self.assertIn('independent visual review, not automatic picture PASS',source)
        self.assertIn('"visualStatus":"PENDING_INDEPENDENT_REVIEW"',source)
        self.assertIn('"restart_from_beginning": args.restart_from_beginning',source)

    def test_surface_rebind_is_guarded_display_only_and_release_removes_observer(self):
        source=(VIDEO/'IJKMediaPlayerImpl.java').read_text()
        bind=source.split('private void bindVideoSurface',1)[1].split('private void unbindVideoSurface',1)[0]
        for guard in ('videoSurfaceCallback==this','videoSurfaceHolder==holder',
                      'player==displayPlayer','isCurrentPlaybackSession(displaySession)',
                      'next.getSurface().isValid()','context.runOnUiThread'):
            self.assertIn(guard,bind)
        self.assertIn('displayPlayer.setDisplay(next)',bind)
        self.assertIn('displayPlayer.setDisplay(null)',bind)
        for action in ('.start()', '.pause()', '.seekTo(', '.prepareAsync()'):
            self.assertNotIn(action,bind)
        unbind=source.split('private void unbindVideoSurface',1)[1].split('protected void releasePlayer()',1)[0]
        self.assertLess(unbind.index('videoSurfaceCallback=null'),unbind.index('holder.removeCallback(callback)'))
        release=source.split('protected void releasePlayer()',1)[1]
        self.assertLess(release.index('unbindVideoSurface();'),release.index('player.release();'))

if __name__=='__main__':
    unittest.main()
