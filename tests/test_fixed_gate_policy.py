import sys,unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'scripts'))
import fixed_gate_policy as policy

class FixedGatePolicyTest(unittest.TestCase):
    def ijk(self,**changes):
        value=dict(health_backendClass='example.IJKMediaPlayerImpl',playerActive=True,state=2,
            health_basicIsPlaying=True,health_firstVideoFrameRendered=True,health_videoOutputFps=29.97,
            health_playerPositionMs=1000,health_audioDecoder='aac',health_videoDecoder='OMX.MTK.VIDEO.DECODER.AVC',
            health_videoRendered=-1,health_audioRendered=-1,health_isPlaying=False,health_playWhenReady=False)
        value.update(changes);return value
    def test_ijk_uses_actual_native_observations_not_missing_exo_counters(self):
        self.assertTrue(policy.started(self.ijk()))
        self.assertTrue(policy.advancing(self.ijk(),self.ijk(health_playerPositionMs=2000)))
        self.assertFalse(policy.paused(self.ijk()))
    def test_clock_alone_and_first_frame_alone_cannot_pass(self):
        for changes in (dict(health_firstVideoFrameRendered=False),dict(health_videoOutputFps=0),
                        dict(health_videoOutputFps=-1),dict(health_basicIsPlaying=False),
                        dict(health_surfaceKnown=True,health_surfaceValid=False),dict(health_playerError='failed')):
            self.assertFalse(policy.started(self.ijk(**changes)))
        self.assertFalse(policy.advancing(self.ijk(),self.ijk()))
    def test_audio_inventory_is_required_but_not_called_a_rendered_counter(self):
        self.assertFalse(policy.started(self.ijk(health_audioDecoder='')))
        self.assertEqual(-1,self.ijk()['health_audioRendered'])
    def test_codec_comes_from_decoder_not_selected_mode(self):
        self.assertEqual('video/avc',policy.video_mime(self.ijk()))
        self.assertEqual('video/mpeg2',policy.video_mime(self.ijk(health_videoDecoder='mpeg2video')))
        self.assertEqual('',policy.video_mime(self.ijk(health_videoDecoder='unknown',requestedMode='stock')))
    def test_exo_counter_oracle_is_not_weakened(self):
        self.assertFalse(policy.started(dict(health_isPlaying=True,health_videoRendered=-1,health_audioRendered=-1)))
        before=dict(health_videoRendered=10,health_audioRendered=10)
        self.assertFalse(policy.advancing(before,dict(health_videoRendered=11,health_audioRendered=10)))
        self.assertTrue(policy.advancing(before,dict(health_videoRendered=11,health_audioRendered=11)))
    def test_pause_observes_backend_state_not_exo_default_false(self):
        self.assertFalse(policy.paused(self.ijk()))
        self.assertTrue(policy.paused(self.ijk(health_basicIsPlaying=False)))

if __name__=='__main__':unittest.main()
