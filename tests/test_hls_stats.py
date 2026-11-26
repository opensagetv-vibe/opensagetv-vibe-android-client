"""HLS-006 passive counters, graph modes and confirmed-clock boundaries."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1]
JAVA=ROOT/'source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android'
class HlsStatsTests(unittest.TestCase):
    def test_physical_gate_uses_raw_visibility_and_explicit_navigation_hide(self):
        text=(ROOT/'scripts/mcp_playback_settings_gate.py').read_text()
        self.assertIn("adb.player_state_snapshot().get('activePlayerProcessOverlayVisible')",text)
        self.assertNotIn("if state().get('activePlayerProcessOverlayVisible')",text)
        self.assertIn("endswith('/nav_playback_settings')",text)
        self.assertIn("click(target);close_panels()",text)
        self.assertIn("stamp+'-information-only.png'",text)
    def test_both_http_factories_share_session_media_counters(self):
        for folder,name in [('media3','Media3'),('exoplayer2','Exo2')]:
            text=(JAVA/f'video/{folder}/{name}MimDirectHttpDataSource.java').read_text()
            self.assertIn('final opensagetv.vibe.miniclient.android.video.HttpMediaCounters counters',text)
            self.assertIn('upstream.createDataSource(), counters',text)
            self.assertIn('counters.read(observeTransportStream, read)',text)
            self.assertIn('public long getNetworkReadBytes()',text)
    def test_graphs_show_actual_target_and_do_not_call_loading_a_stall(self):
        stats=(JAVA/'ActivePlayerStatsSnapshot.java').read_text()
        overlay=(JAVA/'ActivePlayerProcessOverlay.java').read_text()
        self.assertIn('if (backendPlaybackState == 2)',stats)
        self.assertIn('else if (loading) value.append(" / Loading ahead")',stats)
        self.assertIn('out.positionMs = media.getMediaTimeMillis()',stats)
        self.assertIn('snapshot.bufferTargetMs',overlay)
        self.assertNotIn('snapshot.dvd ? 8_000L : 30_000L',overlay)
        self.assertIn('Video target ',overlay)
        self.assertIn('if(graphsOnly)details.setVisibility(View.GONE)',overlay)
        self.assertIn('dp(graphsOnly ? 360 : 900)',overlay)
    def test_seek_only_holds_previously_confirmed_clock(self):
        base=(JAVA/'video/BaseMediaPlayerImpl.java').read_text()
        self.assertIn('holdConfirmedPosition(hasStableMediaTime ? lastMediaTime : -1L)',base)
        self.assertNotIn('holdConfirmedPosition(timeInMS)',base)
        self.assertLess(base.index('if (heldDirectPosition >= 0L)'),base.index('if (loadTransitionToken != null)',base.index('public long getMediaTimeMillis')))
    def test_seek_suspends_old_output_before_background_request(self):
        base=(JAVA/'video/BaseMediaPlayerImpl.java').read_text()
        body=base.split('private boolean rebindMimDirectForSeek',1)[1].split('private void completeMimDirectSeek',1)[0]
        self.assertLess(body.index('suspendMimDirectSeekOutput(true)'),body.index('mimDirectSeekWork.submit'))
        for folder,name in [('media3','Media3'),('exoplayer2','Exo2')]:
            backend=(JAVA/f'video/{folder}/{name}MediaPlayerImpl.java').read_text()
            self.assertIn('player.setPlayWhenReady(!suspended && playRequested)',backend)
            self.assertIn('setPlayWhenReady(!mimSeekOutputSuspended)',backend)
    def test_graph_icon_exists_without_grid_collision(self):
        ns='{http://schemas.android.com/apk/res/android}'
        for rel in ('source/dev/android-shared/src/main/res/layout/navigation.xml','source/dev/android-tv/src/main/res/layout-notouch/navigation.xml'):
            root=ET.parse(ROOT/rel).getroot()
            icons=[n for n in root.iter() if n.get(ns+'id')=='@+id/nav_playback_details']
            self.assertEqual(1,len(icons));self.assertEqual('@drawable/ic_playback_info_white_24dp',icons[0].get(ns+'src'))
            grid=next(g for g in root.iter('GridLayout') if icons[0] in list(g))
            graphs=next(n for n in grid if n.get(ns+'id')=='@+id/nav_playback_stats')
            self.assertEqual(graphs.get(ns+'layout_row'),icons[0].get(ns+'layout_row'))
            self.assertEqual(int(graphs.get(ns+'layout_column'))+1,int(icons[0].get(ns+'layout_column')))
            for grid in root.iter('GridLayout'):
                occupied=set()
                for item in grid:
                    if item.get(ns+'layout_column') is None:continue
                    pos=(item.get(ns+'layout_row'),item.get(ns+'layout_column'))
                    self.assertNotIn(pos,occupied);occupied.add(pos)
                    self.assertLess(int(pos[0]),int(grid.get(ns+'rowCount')))
                    self.assertLess(int(pos[1]),int(grid.get(ns+'columnCount')))
    def test_optional_reserve_requires_exact_capability(self):
        text=(JAVA/'video/MimDirectSessionClient.java').read_text()
        self.assertIn('responsive && hlsBufferNegotiated',text)
        self.assertIn('Math.min(20000,target)',text)
        self.assertIn('"hlsBufferRevision"',text)
    def test_independent_graph_and_info_icons_keep_dialog_open(self):
        text=(JAVA/'NavigationDialog.java').read_text()
        for marker,end in [('if (navPlaybackStats != null)','View exportDiagnostics'),('if (playbackInfo != null)','if (exportDiagnostics != null)')]:
            body=text.split(marker,1)[1].split(end,1)[0]
            self.assertNotIn('dismiss()',body)
        self.assertIn('toggleGraphs(activity, currentMedia)',text)
        self.assertIn('toggleInfo(activity,currentMedia)',text)
    def test_graphs_have_no_only_label_and_keep_passive_rate_measurement(self):
        overlay=(JAVA/'ActivePlayerProcessOverlay.java').read_text()
        choices=(JAVA/'ActivePlayerAdjustmentsDialog.java').read_text(encoding='utf-8')
        self.assertNotIn('Graphs only',overlay)
        self.assertNotIn('graphs only',choices)
        self.assertIn('current.mediaBytes - priorMediaBytes',overlay)
        self.assertIn('current.capturedMs - priorCapturedMs',overlay)
    def test_both_delegates_apply_refill_only_to_negotiated_hls(self):
        for folder,name in [('media3','Media3'),('exoplayer2','Exo2')]:
            text=(JAVA/f'video/{folder}/{name}MediaPlayerImpl.java').read_text()
            call=text.split('HlsPlaybackPolicy.refillBufferMs(',1)[1].split('.setPrioritizeTimeOverSizeThresholds',1)[0]
            self.assertIn('runtimeConfig.getPullMinBufferMs()',call)
            self.assertIn('runtimeConfig.getPullMaxBufferMs()',call)
            self.assertIn('ownsCompletedResponsiveHls(this)',call)
    def test_refill_requires_completed_source_and_exact_owner(self):
        text=(JAVA/'video/MimDirectSessionClient.java').read_text()
        accepted=text.split('completedResponsiveHlsActive = HlsPlaybackPolicy.confirmedCompleted(',1)[1].split(';',1)[0]
        self.assertIn('responsive, longValue(response.body,"startupBurstSeconds",0)',accepted)
        self.assertNotIn('!active',accepted) # Fixed timeshifted hint is not writer proof
        body=text.split('public boolean ownsCompletedResponsiveHls(',1)[1].split('public ',1)[0]
        for guard in ['completedResponsiveHlsActive','responsiveHlsActive','hlsSelected','owner == playbackOwner','!sessionToken.isEmpty()']:
            self.assertIn(guard,body)
        self.assertIn('completedResponsiveHlsActive = false;',text)
    def test_both_delegates_map_owned_sliding_hls_clocks_from_actual_metadata(self):
        for folder,name in [('media3','Media3'),('exoplayer2','Exo2')]:
            text=(JAVA/f'video/{folder}/{name}MediaPlayerImpl.java').read_text()
            body=text.split('private long directHlsPeriodPosition(',1)[1].split('public long getPlaybackPosition',1)[0]
            for guard in ['ownsResponsiveHls(this)','timeline.isEmpty()','getCurrentMediaItemIndex()',
                          'getPositionInFirstPeriodMs()','HlsPlaybackPolicy.periodPositionMs(true, offsetMs, positionMs)']:
                self.assertIn(guard,body)
            self.assertIn('directHlsPeriodPosition(listenerPlayer, listenerPlayer.getCurrentPosition())',text)
            self.assertIn('directHlsPeriodPosition(expectedPlayer, expectedPlayer.getCurrentPosition())',text)
            self.assertRegex(text,r'directHlsPeriodPosition\(listenerPlayer,\s*listenerPlayer.getBufferedPosition\(\)\)')
if __name__=='__main__':unittest.main()
