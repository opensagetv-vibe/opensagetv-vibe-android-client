"""Initial scheduled-start/resume must use the same source contract as later seeks."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
VIDEO = ROOT / 'source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video'


class InitialPlaybackSeekTests(unittest.TestCase):
    def test_frozen_seek_extent_does_not_bound_growing_reads(self):
        for folder, backend in (('media3', 'Media3'), ('exoplayer2', 'Exo2')):
            data = (VIDEO/folder/f'{backend}PullDataSource.java').read_text()
            self.assertIn('seekableSnapshot.resolveExtent(effectivelyGrowing, size)', data)
            self.assertIn('long knownAvailableSize = Math.max(size, snapshotExtent);', data)
            growing = data.split('if (effectivelyGrowing)', 1)[1].split('else if (dataSpec.length', 1)[0]
            self.assertIn('bytesRemaining = C.LENGTH_UNSET;', growing)
            self.assertIn('snapshotExtent - dataSpec.position', growing)

    def test_snapshot_diagnostics_are_passive_and_exposed_by_both_sources(self):
        probe = (ROOT/'source/dev/android-tv/src/debug/java/opensagetv/vibe/miniclient/android/tv/debug/PlaybackHealthProbe.java').read_text()
        for folder, backend in (('media3', 'Media3'), ('exoplayer2', 'Exo2')):
            data = (VIDEO/folder/f'{backend}PullDataSource.java').read_text()
            for field in ('InitialPhysicalSize', 'InitialReportedLength', 'LastReportedLength',
                          'SnapshotPreparationCount', 'SnapshotFirstEndOpenCount'):
                self.assertIn(f'long get{field}()', data)
                self.assertIn(f'source.get{field}()', probe)
                self.assertIn(f'dataSource{field}', probe)
            self.assertIn('if (openCount == 1L) initialReportedLength = reportedLength;', data)

    def test_growing_reprepare_owns_a_new_loader_and_retires_the_old_session(self):
        for folder, backend in (('media3', 'Media3'), ('exoplayer2', 'Exo2')):
            source = (VIDEO/folder/f'{backend}MediaPlayerImpl.java').read_text()
            block = source.split('private final Runnable growingPullSeekRunnable', 1)[1]
            block = block.split('private final PlaybackRateController', 1)[0]
            self.assertIn('pull = previousPull.newPreparationSource();', block)
            self.assertIn('dataSource = pull;', block)
            self.assertLess(block.index('expectedPlayer.prepare();'),
                            block.index('previousPull.retireAfterCurrentLoad();'))
            data = (VIDEO/folder/f'{backend}PullDataSource.java').read_text()
            fork = data.split('newPreparationSource()', 1)[1].split('retireAfterCurrentLoad()', 1)[0]
            self.assertIn('growthPolicy.isConfiguredPotentiallyGrowing()', fork)
            self.assertIn('growthPolicy.isMetadataExplicit(), smbConfig, pullReadBytes', fork)
            cleanup = data.split('public synchronized void close()', 1)[1].split('private void accumulate', 1)[0]
            self.assertIn('finally', cleanup)
            self.assertIn('if (sessionRetired) releaseRetainedResources();', cleanup)

    def test_audio_recreation_retains_push_bytes_but_not_a_retiring_pull_loader(self):
        for relative in ('media3/Media3MediaPlayerImpl.java', 'exoplayer2/Exo2MediaPlayerImpl.java'):
            source = (VIDEO / relative).read_text()
            rebuild = source.split('private boolean requestAudioOutputRebuild(', 1)[1]
            self.assertIn('retainedAudioRebuildDataSource = pushMode ? expectedDataSource : null;', rebuild)
            self.assertIn('if (!pushMode) releaseDataSource();', rebuild)
            self.assertLess(rebuild.index('if (!pushMode) releaseDataSource();'),
                            rebuild.index('setupPlayer(expectedUrl);'))

    def test_growing_reprepare_replaces_the_cached_unseekable_source_timeline(self):
        for relative in ('media3/Media3MediaPlayerImpl.java', 'exoplayer2/Exo2MediaPlayerImpl.java'):
            source = (VIDEO / relative).read_text()
            block = source.split('private final Runnable growingPullSeekRunnable', 1)[1]
            block = block.split('private final PlaybackRateController', 1)[0]
            self.assertIn('mediaSource = new ProgressiveMediaSource.Factory(', block)
            self.assertIn('withPassthroughOffset(createCaptionAwareExtractorsFactory(true))', block)
            self.assertIn('.createMediaSource(mediaSource.getMediaItem());', block)
            self.assertLess(block.index('.createMediaSource('),
                            block.index('expectedPlayer.setMediaSource(mediaSource, targetMs);'))

    def test_both_extractors_prepare_nonzero_initial_pull_seek_maps_before_attach(self):
        for folder, backend in (('media3', 'Media3'), ('exoplayer2', 'Exo2')):
            source = (VIDEO / folder / f'{backend}MediaPlayerImpl.java').read_text()
            block = source.split('long requestedStartPosition = pendingStartupSeek.take();', 1)[1]
            block = block.split('player.prepare();', 1)[0]
            self.assertIn('!pushMode && requestedStartPosition > 0L', block)
            self.assertIn(f'dataSource instanceof {backend}PullDataSource', block)
            self.assertLess(block.index('beginSeekableSnapshotPreparation();'),
                            block.index('player.setMediaSource(mediaSource, requestedStartPosition);'))
            self.assertNotIn('playbackStartPosition = -1', block)

    def test_every_independent_backend_resets_only_at_synchronous_load(self):
        for relative in ('media3/Media3MediaPlayerImpl.java',
                         'exoplayer2/Exo2MediaPlayerImpl.java',
                         'ijkplayer/IJKMediaPlayerImpl.java',
                         'gsy/GSYSystemMediaPlayerImpl.java'):
            source = (VIDEO / relative).read_text()
            hook = source.split('protected void onPlaybackLoadStarted()', 1)[1].split('}', 1)[0]
            self.assertIn('pendingStartupSeek.reset();', hook, relative)
            setup = source.split('protected void setupPlayer(', 1)[1]
            self.assertNotIn('pendingStartupSeek.reset();', setup, relative)
            self.assertNotIn('preSeekPos = -1;', setup, relative)

    def test_native_initial_seek_precedes_play_without_losing_newer_requests(self):
        for relative, signature, start in (
                ('ijkplayer/IJKMediaPlayerImpl.java', 'onPrepared(IMediaPlayer mp)', 'player.start();'),
                ('gsy/GSYSystemMediaPlayerImpl.java', 'onPrepared(MediaPlayer mp)', 'mp.start();')):
            source = (VIDEO / relative).read_text()
            prepared = source.split(signature, 1)[1]
            self.assertLess(prepared.index('pendingStartupSeek.take();'),
                            prepared.index('seekToImpl(requestedPositionMs);'), relative)
            self.assertLess(prepared.index('seekToImpl(requestedPositionMs);'),
                            prepared.index(start), relative)

    def test_extractor_construction_drains_late_requests_when_readiness_is_published(self):
        for relative in ('media3/Media3MediaPlayerImpl.java', 'exoplayer2/Exo2MediaPlayerImpl.java'):
            source = (VIDEO / relative).read_text()
            ready = source.split('this.playerReady = true;', 1)[1].split('finally', 1)[0]
            self.assertIn('long queuedPositionMs = pendingStartupSeek.take();', ready)
            self.assertIn('if (!pushMode && queuedPositionMs >= 0L) seekToImpl(queuedPositionMs);', ready)

    def test_system_fallback_replays_the_server_bookmark_to_new_delegate(self):
        source = (VIDEO / 'gsy/GSYMediaPlayerImpl.java').read_text()
        fallback = source.split('private void scheduleSystemFallback(', 1)[1].split('private MiniPlayerPlugin d()', 1)[0]
        self.assertIn('long requestedPositionMs = lastRequestedSeekMs;', fallback)
        self.assertIn('delegate.seek(requestedPositionMs);', fallback)
        self.assertLess(fallback.index('delegate.load('), fallback.index('delegate.seek('))
        load = source.split('public void load(', 1)[1].split('getMediaTimeMillis', 1)[0]
        self.assertIn('lastRequestedSeekMs = -1L;', load)


if __name__ == '__main__':
    unittest.main()
