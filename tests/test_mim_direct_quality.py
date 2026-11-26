"""Focused optional Direct consumer boundaries; JVM tests own lifecycle math."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
JAVA=ROOT/'source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android'

class DirectQualityBoundaries(unittest.TestCase):
    def test_buffer_policy_gate_uses_delivery_not_manual_targets(self):
        gate=(ROOT/'scripts/mcp_direct_quality_gate.py').read_text(encoding='utf-8')
        block=gate.split('if args.buffer_adaptive_network:',2)[2].split('if args.preview_only:',1)[0]
        self.assertIn('shared-buffer-automatic-downshift',block)
        self.assertIn('shared-buffer-automatic-recovery',block)
        self.assertIn('shared-buffer-seek-pause-resume',block)
        self.assertIn("if not complete:raise SystemExit('Physical gate or restoration did not pass')",gate)
        self.assertNotIn('plugin.adaptive_test',block)
        self.assertNotIn('requestQuality',block)
        actor=(ROOT/'scripts/direct_network_reverse_gate.py').read_text(encoding='utf-8')
        self.assertIn("if args.buffer_adaptive:task='HLS-005'",actor)
        self.assertIn("command.append('--buffer-adaptive-network')",actor)
        self.assertIn("command+=['--candidate-apk',str(args.candidate_apk)]",actor)

    def test_shared_mim_test_control_remains_explicit_and_restorable(self):
        gate=(ROOT/'scripts/mcp_direct_quality_gate.py').read_text(encoding='utf-8')
        self.assertIn("expected_task='HLS-005'",gate)
        self.assertIn("'plugin.adaptive_test'",gate)
        self.assertIn('expected_revision=p[\'revision\']',gate)
        self.assertIn("'runtimeSiteRestored'",gate)
        debug=(ROOT/'source/dev/android-tv/src/debug/java/opensagetv/vibe/miniclient/android/tv/debug/DebugPlayerConfigCommands.java').read_text(encoding='utf-8')
        self.assertIn('value<250||value>maximum',debug)
        self.assertIn('mim_direct_adaptive_quality',debug)
        self.assertIn('mim_direct_minimum_kbps',debug)
    def test_native_qsv_gate_preserves_stock_clock_and_real_eof(self):
        gate=(ROOT/'scripts/mcp_direct_quality_gate.py').read_text(encoding='utf-8')
        self.assertIn("args.fallback!='off' or args.hls or args.quality_fault",gate)
        self.assertIn("expected_task='MIM-QSV-001'",gate)
        self.assertIn("for target in (10000,70000):",gate)
        self.assertIn("'max_attempts':1",gate)
        self.assertIn("'Native post-seek output stopped'",gate)
        self.assertIn("'native-qsv-actual-decoder-eof-no-replay'",gate)
        self.assertIn("held.get('health_playbackState')==4",gate)

    def test_runtime_policy_keeps_api23_and_honest_ui_receipts(self):
        policy=(JAVA/'video/RuntimeQualityAdvisor.java').read_text(encoding='utf-8')
        self.assertNotIn('Double.isFinite(kbps)',policy)
        self.assertIn('Double.isNaN(kbps)||Double.isInfinite(kbps)',policy)
        session=(JAVA/'video/MimDirectSessionClient.java').read_text(encoding='utf-8')
        self.assertIn('bufferObservation:"inactive"',session)
        self.assertIn('"/v1/direct/buffer?token="',session)
        self.assertIn('"&adaptiveMinimumKbps="+adaptiveMinimumKbps',session)
        self.assertIn('longValue(capabilities.body,"bufferAdaptationRevision",0)==1',session)
        self.assertIn('!eligible||bufferNeedsReset||target==0||target<adaptiveMinimumKbps||target>qualityCeilingKbps',session)
        body=session.split('void observeQualityBuffer(',1)[1].split('boolean requestQuality(',1)[0]
        self.assertNotIn('bytes*8.0/readMs',body)
        self.assertNotIn('Advisor.observe',body)
        menu=(JAVA/'ActivePlayerAdjustmentsDialog.java').read_text(encoding='utf-8')
        self.assertIn('Bitrate change requested; current encoder and playback retained',menu)

    def test_hls_diagnostics_survive_mcp_compaction(self):
        # Actual device output already supplies these; dropping them makes a
        # correctly negotiated HLS session look like a transport failure.
        # Load the pure projection without importing the live MCP server,
        # whose module setup intentionally requires commissioned credentials.
        import ast
        source=ast.parse((ROOT/'mcp/src/sagetv_dev_mcp/server.py').read_text())
        function=next(n for n in source.body if isinstance(n,ast.FunctionDef) and n.name=='_compact_state')
        namespace={}
        exec(compile(ast.Module(body=[function],type_ignores=[]),'<compact-state-test>','exec'),namespace)
        _compact_state=namespace['_compact_state']
        expected={'mimHlsOwned':True,'mimHlsProfile':'responsive','mimRuntimeRateEnabled':False}
        self.assertEqual(_compact_state(dict(expected,unrelatedSecret='redacted')),expected)

    def test_responsive_hls_preserves_legacy_and_initial_source_position(self):
        session=(JAVA/'video/MimDirectSessionClient.java').read_text()
        self.assertIn('requestedHls && supportsResponsiveHls(capabilities.body)',session)
        self.assertIn('if (responsive) path += "&hlsProfile=responsive"',session)
        self.assertIn('responsive && !acceptedResponsiveHls(response.body,requestedBufferAheadMs)',session)
        self.assertIn('acceptedResponsiveHls(response.body,acceptedBufferAheadMs)',session)
        for folder,name in [('media3','Media3MediaPlayerImpl'),('exoplayer2','Exo2MediaPlayerImpl')]:
            source=(JAVA/'video'/folder/(name+'.java')).read_text()
            self.assertIn('ownsResponsiveHls(this)',source)
            self.assertIn('HlsPlaybackPolicy.startBufferMs(',source)
            # Burst production must not make HLS choose its default live edge
            # and silently skip the scheduled/bookmarked beginning.
            branch=source.split('if(directJoin>0)',1)[1].split('player.prepare();',1)[0]
            self.assertIn('player.setMediaSource(mediaSource, 0L)',branch)
            self.assertLess(branch.index('player.setMediaSource(mediaSource,directJoin)'),branch.index('mimDirectController().owns(this)'))
            self.assertIn('else player.setMediaSource(mediaSource, true)',branch)
        gate=(ROOT/'scripts/mcp_direct_quality_gate.py').read_text()
        self.assertIn("expected_task='HLS-002'",gate)
        self.assertIn("before.get('mimHlsProfile')=='responsive'",gate)
        diagnostics=(JAVA/'ClientDiagnosticSnapshot.java').read_text()
        self.assertIn('p.put("hlsProfile",',diagnostics)

    def test_explicit_hls_keeps_defaults_and_existing_adaptive_pipeline(self):
        ns='{http://schemas.android.com/apk/res/android}'
        xml=ET.parse(ROOT/'source/dev/android-shared/src/main/res/xml/transcoding_prefs.xml')
        mode=next(e for e in xml.iter() if e.get(ns+'key')=='mim_direct/mode')
        adaptive=next(e for e in xml.iter() if e.get(ns+'key')=='mim_direct/adjustment_method')
        self.assertEqual(mode.get(ns+'defaultValue'),'off')
        self.assertEqual(adaptive.get(ns+'defaultValue'),'off')
        self.assertIn('Resolution stays fixed',adaptive.get(ns+'summary'))
        arrays=ET.parse(ROOT/'source/dev/android-shared/src/main/res/values/arrays.xml')
        entries=arrays.find("string-array[@name='entries_mim_direct_mode']")
        values=arrays.find("string-array[@name='entryvalues_mim_direct_mode']")
        self.assertEqual(len(entries),len(values))
        self.assertEqual([x.text for x in values],['off','copy','transcode','hls'])
        session=(JAVA/'video/MimDirectSessionClient.java').read_text()
        self.assertIn('requestedHls && !supportsHls(capabilities.body)',session)
        self.assertIn('qualitySupported=MimDirectTransportPolicy.TRANSCODE.equals(requested)',session)
        lifecycle=(JAVA/'UIActivityLifeCycleHandler.java').read_text()
        self.assertIn('MimDirectTransportPolicy.activeMode("fixed",',lifecycle)
        self.assertIn('MIM HLS unavailable; using ordinary Fixed playback',lifecycle)
        stats=(JAVA/'ActivePlayerStatsSnapshot.java').read_text()
        self.assertIn('ownsHls(effective)',stats)
        self.assertIn('out.transport = "HLS"',stats)
        gate=(ROOT/'scripts/mcp_direct_quality_gate.py').read_text()
        self.assertIn("parser.add_argument('--hls'",gate)
        self.assertIn("expected_task='HLS-001'",gate)
        self.assertIn("before.get('mimDirectRequestedMode')=='hls'",gate)
        self.assertIn("before.get('mimHlsOwned')",gate)
        self.assertIn("{'panel':'transport'}",gate)
        self.assertIn("tap('Current-session Bitrate')",gate)

    def test_direct_seek_finishes_only_on_replacement_frame(self):
        base=(JAVA/'video/BaseMediaPlayerImpl.java').read_text()
        attach=base.index('mimDirectSeekPresentation.arm(rebindGeneration)')
        self.assertLess(attach,base.index('setupPlayer(replacementUrl)',attach))
        callback=base[base.index('protected final void notifyPostSeekPushFirstFrame()'):]
        self.assertIn('mimDirectSeekPresentation.presented(mimDirectRebindGeneration)',callback)
        self.assertIn('direct_seek_first_frame_complete',callback)
        self.assertGreaterEqual(base.count('mimDirectSeekPresentation.cancel()'),3)
    def test_physical_gate_keeps_exact_delegate_and_restoration_guards(self):
        gate=(ROOT/'scripts/mcp_direct_quality_gate.py').read_text()
        self.assertIn("'gsy-exoplayer':'legacy_exo'",gate)
        self.assertIn("serial!=env.device_serial('non_pro') or server!=env.server_address('vibe')",gate)
        self.assertIn("api.current_media_file_id(context)==media_id",gate)
        self.assertIn("cleanup['independentFinalActivity']",gate)
        self.assertIn("'dev_restore_settings'",gate)
        self.assertIn("api.set_closed_caption_state(context,original_cc)",gate)
        self.assertIn("if actual_apk_sha!=apk_sha:",gate)
        self.assertLess(gate.index('if actual_apk_sha!=apk_sha:'),gate.index("saved=call_dict(client,'dev_checkpoint_settings')"))
        rendered=gate.index("print('OBSERVED actual candidate rendered-frame commit;")
        self.assertLess(rendered,gate.index('settled=await_state',rendered))
        self.assertIn("'Candidate A/V did not advance'",gate)
        self.assertIn("'Direct seek has no current rendered output/pending completion'",gate)
        self.assertIn("'scoped-direct-seek-pause-resume'",gate)
        self.assertIn("'-scoped-seek-caption.png'",gate)
        actor=(ROOT/'scripts/direct_network_reverse_gate.py').read_text()
        self.assertIn("line.startswith('OBSERVED actual candidate rendered-frame commit;')",actor)
        self.assertIn("'healthy-delivery-no-intermediate-poll'",gate)
        self.assertIn("args.healthy_only",actor)

    def test_opt_in_defaults_off(self):
        xml=ET.parse(ROOT/'source/dev/android-shared/src/main/res/xml/transcoding_prefs.xml')
        ns='{http://schemas.android.com/apk/res/android}'
        row=next(e for e in xml.iter() if e.get(ns+'key')=='mim_direct/adjustment_method')
        self.assertEqual(row.get(ns+'defaultValue'),'off')
        base=(JAVA/'video/BaseMediaPlayerImpl.java').read_text()
        self.assertIn('PrefStore.Keys.mim_direct_adaptive_quality,false',base)
        self.assertIn('!mimQualityHasFrame||seekPending||state!=PLAY_STATE',base)
        self.assertIn('mimQualityBuffering.during(bufferingStamp)',base)
        self.assertIn('mimQualityBuffering.observe(buffering,SystemClock.elapsedRealtime())',base)
    def test_physical_fault_profiles_keep_real_output_and_owned_cleanup(self):
        gate=(ROOT/'scripts/mcp_direct_quality_gate.py').read_text()
        self.assertIn('wait_sessions(2)',gate)
        self.assertIn("'Owned producer count not settled at '",gate)
        self.assertIn("'Late candidate resurrected after seek'",gate)
        self.assertIn("'Restored original A/V not advancing'",gate)
        self.assertIn("'wholePluginUninstalled':False",gate)
        self.assertIn("owned_ui_only=api.ui_context_names()==[context]",gate)
        self.assertIn("activity.get('connectedClientCount')!=0",gate)
        self.assertIn("'Superseding seek did not render',True",gate)
        self.assertIn("'Superseding seek retained a quality failure'",gate)
        self.assertIn("call_dict(client,'dev_server_seek_time'",gate)
        self.assertIn("'max_attempts':1",gate)
        self.assertIn("'FLUSH-only stock wire cannot supply a requested file time'",gate)
        self.assertIn("if args.quality_fault and (not args.network_reverse or args.fallback)",gate)
        self.assertIn("'Direct seek has no current rendered output/pending completion'",gate)

    def test_both_renderer_families_observe_only_current_instances(self):
        for folder,name in [('media3','Media3MediaPlayerImpl'),('exoplayer2','Exo2MediaPlayerImpl')]:
            source=(JAVA/'video'/folder/(name+'.java')).read_text()
            callback=source[source.index('public void onRenderedFirstFrame()'):]
            self.assertLess(callback.index('player != listenerPlayer'),callback.index('notifyMimDirectQualityFrame'))
            self.assertIn('consumeQualityInitialPosition(this)',source)
            self.assertIn('if(directJoin>0)',source)
            self.assertIn('onMimDirectBodyTransfer(uri,bytes,readMs,stamp)',source)
            self.assertIn('bufferingStamp(){return mimDirectBufferingStamp();}',source)
            self.assertIn('measureBody(){return isMimDirectAdaptiveQualityEnabled();}',source)

    def test_measurement_is_published_segment_eof_not_background_polling(self):
        sample=(JAVA/'video/DirectSegmentDelivery.java').read_text()
        self.assertIn('complete&&bytes>=16384&&nanos>=100000000L',sample)
        self.assertIn('if(first){first=false;return;}',sample)
        for folder,name in [('media3','Media3MimDirectHttpDataSource'),('exoplayer2','Exo2MimDirectHttpDataSource')]:
            source=(JAVA/'video'/folder/(name+'.java')).read_text()
            self.assertIn('default boolean measureBody(){return false;}',source)
            self.assertIn('observer.measureBody()',source)
            self.assertIn('delivery!=null&&delivery.measured()',source)

    def test_no_web_or_mcp_playback_dependency(self):
        source=(JAVA/'video/MimDirectQualityClient.java').read_text()
        self.assertNotIn('/SageTVWebPlayer',source)
        self.assertNotIn('/v1/control',source)
        self.assertIn('/v1/direct/quality/prepare?',source)
        self.assertIn('/v1/direct/quality/commit?',source)
        self.assertIn('/v1/direct/quality/abort?',source)
        self.assertNotIn('Double.isFinite',source)  # Android6/API23 compatibility.
        session=(JAVA/'video/MimDirectSessionClient.java').read_text()
        seek=session[session.index('public String restart('):session.index('public String restart(')+8500]
        self.assertIn('quality.seekSource(',seek)
        self.assertNotIn('quality.source(',seek)

if __name__=='__main__':unittest.main()
