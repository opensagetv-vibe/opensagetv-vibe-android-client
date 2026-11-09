"""The pinned legacy source shares queue semantics, not Media3 binary types."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT/'source/dev/android-shared/src/main/java/com/google/android/exoplayer2/source'
BACKEND = ROOT/'source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/exoplayer2/Exo2MediaPlayerImpl.java'


class LegacyForwardCacheTests(unittest.TestCase):
    def test_negotiation_and_media3_use_the_same_real_mpeg_audio_alias(self):
        options = (ROOT/'source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/AndroidMiniClientOptions.java').read_text()
        legacy = options.split('if(backend.usesLegacyExoFfmpeg()',1)[1].split('else if(backend == PlayerBackend.MEDIA3',1)[0]
        self.assertIn('FfmpegAudioMimePolicy.canonicalMimeType(', legacy)
        media3 = (ROOT/'source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/media3/Media3FfmpegAudioSupport.java').read_text()
        self.assertIn('FfmpegAudioMimePolicy.canonicalMimeType(mimeType)', media3)

    def test_failed_caption_profile_is_on_demand_and_does_not_reprobe_negotiation(self):
        connection = (ROOT/'source/dev/core/src/main/java/opensagetv/vibe/miniclient/MiniClientConnection.java').read_text()
        getter = connection.split('public String getDiscoveredCodecCapabilitiesForDebug()',1)[1].split('\n    }',1)[0]
        self.assertIn('ConnectionCapabilityProfile profile = capabilityProfile', getter)
        self.assertIn('profile.audioCodecs()', getter)
        self.assertNotIn('.discover(', getter)
        self.assertNotIn('prepareCodecs(', getter)
        caption = (ROOT/'scripts/mcp_caption_test.py').read_text()
        self.assertGreater(caption.index('FAILED-GATE CODEC PROFILE:'),
                           caption.index('ANDROID CAPTIONS ({args.player}): FAIL'))

    def test_pinned_license_and_real_atomic_queue_operation(self):
        period = (SOURCE/'VibeForwardPushMediaPeriod.java').read_text()
        self.assertIn('Copyright (C) 2016 The Android Open Source Project', period)
        self.assertIn('3aee2b9d38d5be4e41153082e12a3c96ea8c79d75e1f94ea7b871f40705fe1a3', period)
        seek = period.split('public long seekToUs(long positionUs)',1)[1].split('@Override',1)[0]
        self.assertIn('ForwardCacheSeekTransaction.seek(queues, positionUs)', seek)
        self.assertIn('trackState.trackEnabledStates[track]', seek)
        self.assertIn('sampleQueues[track].seekTo(targetUs, false)', seek)
        for forbidden in ('pendingResetPositionUs =', 'loader.cancelLoading()',
                          'seekMap.isSeekable() ? positionUs : 0'):
            self.assertNotIn(forbidden, seek)
        self.assertNotIn('androidx.media3', period)

    def test_unread_eof_window_and_source_release(self):
        period = (SOURCE/'VibeForwardPushMediaPeriod.java').read_text()
        window = period.split('public long getBufferedPositionUs()',1)[1].split('@Override',1)[0]
        self.assertLess(window.index('forwardReady = prepared'),
                        window.index('if (loadingFinished) return C.TIME_END_OF_SOURCE;'))
        source = (SOURCE/'VibeForwardPushMediaSource.java').read_text()
        self.assertIn('52f388b53ffd9e3ce10bac62ea3af1dc31879007bed1398754189010bd78693c', source)
        self.assertIn('if (activeForwardPeriod == mediaPeriod) activeForwardPeriod = null;', source)

    def test_backend_fences_ordinary_push_and_preserves_basic_dvd_sources(self):
        backend = BACKEND.read_text()
        self.assertIn('if (!dvdPushMode && mediaSource instanceof VibeForwardPushMediaSource)', backend)
        self.assertIn('MiniClientConnection.detailedBufferStats', backend)
        self.assertIn('mediaSource = dvdPushMode', backend)
        branch = backend.split('public void seek(long timeInMS)',1)[1].split('if (mimDirectController()',1)[0]
        self.assertIn('pushMode && !dvdPushMode', branch)
        self.assertIn('mediaSource != source', branch)
        self.assertIn('!isCurrentPlaybackSession(session)', branch)
        self.assertNotIn('super.seek(', branch)
        self.assertNotIn('resetLegacyCaptionsForDiscontinuity()', branch)


if __name__ == '__main__': unittest.main()
