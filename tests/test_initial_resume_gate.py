"""Keep the affected-only physical resume oracle honest and isolated."""
from pathlib import Path
import ast
import unittest

ROOT = Path(__file__).resolve().parents[1]


class InitialResumeGateTests(unittest.TestCase):
    def test_scheduled_gate_guards_explicit_capture_owners_and_configured_target(self):
        source=(ROOT/'scripts/mcp_scheduled_resume_ui_test.py').read_text()
        self.assertIn("'--shared-capture-server',action='append',required=True",source)
        self.assertIn('dict.fromkeys([server_address]+args.shared_capture_server)',source)
        self.assertIn("'serial':env.device_serial()",source)
        continuation=(ROOT/'scripts/mcp_padded_watch_continuation.py').read_text()
        self.assertIn("prior.get('server')==environment.server_address()",continuation)
        self.assertIn("'serial':environment.device_serial()",continuation)

    def setUp(self):
        self.source = (ROOT/'scripts/mcp_initial_resume_test.py').read_text()

    def test_generated_exact_fixture_only(self):
        self.assertIn("environment.fixture_cases(enabled_only=True)", self.source)
        self.assertIn("'/OpenSageTV_Vibe_Tests/' in path", self.source)
        self.assertIn("control.resolve_exact_path(path)", self.source)
        self.assertNotIn('clear_watched(', self.source)

    def test_resume_is_normal_watch_not_a_corrective_seek(self):
        resume = self.source.split("step('Stop and normal reopen;", 1)[1]
        self.assertIn("'restart_from_beginning': False", resume)
        self.assertNotIn('dev_server_seek_time', resume)
        self.assertNotIn('dev_seek_time', resume)
        self.assertIn("current.get('serverRequestedSeekMs', -1)", resume)

    def test_actual_pull_required_before_and_after_stop(self):
        self.assertIn("report['initialState'].get('health_pushMode') is False", self.source)
        self.assertIn("current.get('health_pushMode') is False", self.source)

    def test_playing_resume_position_is_bounded_by_observed_seek_age(self):
        self.assertIn("current.get('serverSeekAgeMs', -1)", self.source)
        self.assertIn('-5000 <= position-target <= seek_age_ms+5000', self.source)
        self.assertNotIn('abs(position-target) < 20000', self.source)

    def test_scope_checkpoint_and_restore_are_required(self):
        self.assertIn("control.resolve_context(str(identity['clientId']))", self.source)
        self.assertIn("report['checkpoint'].get('captured')", self.source)
        cleanup = self.source.split('    finally:', 1)[1]
        self.assertIn('dev_restore_settings', cleanup)
        self.assertIn('control.set_closed_caption_state(context, cc)', cleanup)
        self.assertIn('client.close()', cleanup)

    def test_system_fallback_is_not_mislabeled_native_pass(self):
        self.assertIn("current.get('gsyResolvedEngine') != 'system'", self.source)
        self.assertIn('PASS_FALLBACK_NOT_NATIVE_SYSTEM', self.source)

    def test_hdmi_capture_requires_current_identity_stage(self):
        self.assertIn("'scopeVerified': True", self.source)
        self.assertIn("'expiresAtMs':", self.source)
        self.assertIn("'--ready-file'", self.source)
        self.assertIn('PENDING_INDEPENDENT_REVIEW', self.source)
        self.assertLess(self.source.index('not args.capture_hdmi or args.hold_seconds >= 20'),
                        self.source.index('initialize(client)'))

    def test_growing_gate_requires_actual_recording_and_new_source_open(self):
        source = (ROOT/'scripts/mcp_growing_initial_source_test.py').read_text()
        self.assertIn("activity.get('recordingCount') == 0", source)
        self.assertIn("report['liveActivity'].get('recordingCount', 0) > 0", source)
        self.assertIn("report['liveState'].get('health_pushMode') is False", source)
        self.assertIn("report['afterDecodedRebuild'].get('sourceOpenMonotonicMs', -1)", source)
        self.assertIn('PASS_RECREATION_NOT_SCHEDULED_WATCH', source)

    def test_live_seek_keeps_full_timeline_and_segment_clock_separate(self):
        source = (ROOT/'scripts/mcp_growing_initial_source_test.py').read_text()
        self.assertIn('target = server_time-window_start-30000', source)
        self.assertIn("report['beforeRebuild'].get('mediaSourceIdentity') == source_identity", source)
        self.assertIn("report['finalState'].get('mediaSourceIdentity') == source_identity", source)
        self.assertNotIn("'target_ms':15000", source)
        self.assertIn('expected_raw-5000 <= observed_raw <= expected_raw+seek_elapsed_ms+5000', source)

    def test_growing_gate_never_claims_recreation_is_scheduled_watch(self):
        source = (ROOT/'scripts/mcp_growing_initial_source_test.py').read_text()
        self.assertIn('not scheduled Watch', source)
        self.assertIn('control.tune_channel(context, args.channel)', source)
        self.assertNotIn('clear_watched(', source)
        self.assertNotIn('from_beginning=True', source)

    def test_tune_waits_for_tv_and_requires_observed_channel_not_only_ack(self):
        source = (ROOT/'scripts/mcp_growing_initial_source_test.py').read_text()
        self.assertLess(source.index("report['initialTvHealth'] = healthy()"),
                        source.index('control.tune_channel(context, args.channel)'))
        self.assertIn("str(report['tunedState'].get('mediaChannel', '')) == args.channel", source)
        self.assertIn('tune ACK is not playback proof', source)

    def test_shared_capture_activity_is_guarded_before_any_playback_mutation(self):
        source = (ROOT/'scripts/mcp_growing_initial_source_test.py').read_text()
        self.assertIn("'--shared-capture-server', action='append', required=True", source)
        guard = source.split('for host in dict.fromkeys(args.shared_capture_server):', 1)[1]
        guard = guard.split("report['checkpoint']", 1)[0]
        self.assertIn("discover_sage_control(host)._request('server.activity')", guard)
        self.assertIn("owner.get('recordingCount') == 0", guard)
        self.assertIn("owner.get('connectedClientCount') == 0", guard)
        self.assertIn("owner.get('uiContextCount') == 0", guard)
        self.assertLess(source.index('Shared capture owner is active'),
                        source.index("call_dict(client, 'dev_prepare_clean_start'"))
        self.assertNotIn('media_control(', guard)
        cleanup = source.split('    finally:', 1)[1]
        self.assertLess(cleanup.index('if checkpoint:'),
                        cleanup.index("call_dict(client, 'dev_exit_session'"))

    def test_growing_normal_watch_requires_owned_recording_and_no_corrective_seek(self):
        source = (ROOT/'scripts/mcp_growing_initial_source_test.py').read_text()
        self.assertIn('current_id == owned_media_id', source)
        self.assertIn('Missing exact operator menu verification', source)
        resume = source.split("step('Stop and normal Watch while the owned recording continues')",1)[1]
        resume = resume.split("report['rebuildHealth']",1)[0]
        self.assertIn("recordingCount') == 1", resume)
        self.assertIn('control.watch(context, owned_media_id, from_beginning=False)', resume)
        self.assertNotIn('seek(', resume)
        self.assertNotIn("'command':'delete'", source)
        self.assertNotIn('DeleteFile(', source)
        self.assertIn("record_menu_witness('recordFlagCleared', 'record-this-show-visible')", source)

    def test_scheduled_gate_compares_real_padding_without_corrective_seek(self):
        source=(ROOT/'scripts/mcp_scheduled_resume_ui_test.py').read_text()
        self.assertIn("expected=air_start-int(server['seekStartMs'])",source)
        self.assertIn('abs(requested-expected)<=5000',source)
        self.assertIn("initial.get('health_pushMode') is False",source)
        self.assertNotIn('control.seek(',source)
        self.assertNotIn('clear_watched(',source)
        self.assertNotIn('SetRecordingTimes',source)
        self.assertIn("report['ownedRecordingCancelled']=True",source)

    def test_debug_restart_calls_existing_guarded_operation_without_audio_format_change(self):
        source = (ROOT/'source/dev/android-tv/src/debug/java/opensagetv/vibe/miniclient/android/tv/debug/DebugPlayerCommands.java').read_text()
        restart = source.split('else if ("restart_audio".equals(action))', 1)[1].split('        else', 1)[0]
        self.assertIn('player.supportsAudioOutputRestart()', restart)
        self.assertIn('player.restartAudioOutput()', restart)
        self.assertNotIn('setAudioPassthroughEnabled', restart)
        self.assertNotIn('setAudioOffset', restart)

    def test_ijk_relative_native_clock_requires_existing_discontinuity_witness(self):
        source=(ROOT/'scripts/mcp_growing_initial_source_test.py').read_text()
        helper=source.split('def segment_position(state):',1)[1].split('def main():',1)[0]
        self.assertIn('IJKMediaPlayerImpl',helper)
        self.assertIn('growing_pull_seek_relative_clock',helper)
        self.assertIn("state.get('mediaTimeMs', -1)",helper)
        self.assertNotIn('serverRequestedSeekMs',helper)
        self.assertIn('return raw',helper)

    def test_relative_clock_oracle_keeps_other_engines_and_unproven_ijk_raw(self):
        source=(ROOT/'scripts/mcp_growing_initial_source_test.py').read_text()
        node=next(n for n in ast.parse(source).body
                  if isinstance(n,ast.FunctionDef) and n.name=='segment_position')
        namespace={}
        exec(compile(ast.Module(body=[node],type_ignores=[]),'<clock oracle>','exec'),namespace)
        position=namespace['segment_position']
        sample={'playerClass':'IJKMediaPlayerImpl','health_playerPositionMs':8181,
                'mediaTimeMs':139638,'serverRequestedSeekMs':131480}
        self.assertEqual(8181,position(sample))
        sample['trapRecent']='12@growing_pull_seek_relative_clock@123'
        self.assertEqual(139638,position(sample))
        sample['playerClass']='Media3MediaPlayerImpl'
        self.assertEqual(8181,position(sample))

    def test_live_and_scheduled_drivers_preserve_explicit_commissioned_server_scope(self):
        for script in ('mcp_growing_initial_source_test.py','mcp_scheduled_resume_ui_test.py'):
            source=(ROOT/'scripts'/script).read_text()
            self.assertIn("parser.add_argument('--server'",source)
            self.assertIn("child_command('firetv-non-pro',",source)
            self.assertIn('args.server',source)
        scheduled=(ROOT/'scripts/mcp_scheduled_resume_ui_test.py').read_text()
        self.assertIn('server_address=env.server_address()',scheduled)
        self.assertIn('discover_sage_control(server_address)',scheduled)

    def test_existing_recording_mode_observes_writer_and_never_pins_or_tunes(self):
        source=(ROOT/'scripts/mcp_growing_initial_source_test.py').read_text()
        self.assertIn("call('GetCurrentlyRecordingMediaFiles')",source)
        self.assertIn("matching[0].get('IsFileCurrentlyRecording') is True",source)
        self.assertIn('if not args.existing_recording_path:\n            report[\'tune\']',source)
        self.assertIn('if args.normal_reopen and not args.existing_recording_path:',source)
        self.assertIn('Existing recording must still be running after the playback test',source)

    def test_normal_watch_seek_witness_survives_stock_sequence_reset(self):
        source=(ROOT/'scripts/mcp_growing_initial_source_test.py').read_text()
        node=next(n for n in ast.parse(source).body
                  if isinstance(n,ast.FunctionDef) and n.name=='last_server_seek_time')
        namespace={}
        exec(compile(ast.Module(body=[node],type_ignores=[]),'<seek oracle>','exec'),namespace)
        when=namespace['last_server_seek_time']
        before={'serverSeekSequence':2,'trapRecent':'16@server_seek_command@555692533@12'}
        after={'serverSeekSequence':1,'trapRecent':'23@server_openurl_command@555700539@1|24@server_seek_command@555700563@29'}
        self.assertGreater(when(after),when(before))
        self.assertEqual(-1,when({'serverSeekSequence':99,'trapRecent':''}))

    def test_scheduled_reservation_never_treats_busy_encoder_as_idle(self):
        source=(ROOT/'scripts/mcp_scheduled_resume_ui_test.py').read_text()
        self.assertIn("reader.call('GetConfiguredCaptureDeviceInputs')",source)
        self.assertIn('Reserved device is currently recording',source)
        self.assertIn('Reserved device already has scheduled work',source)
        self.assertIn("r.get('AiringID')==args.test_airing_id",source)
        self.assertIn("assigned[0].get('IsManualRecord') is True",source)
        self.assertIn('expected<=current',source)
        self.assertIn("metadata.get('MediaFileID')==media_id",source)
        self.assertIn("report.get('allocationVerified') is True",source)

    def test_sagemc_cleanup_verifies_exact_flag_without_automatic_record_toggle(self):
        source=(ROOT/'scripts/mcp_scheduled_resume_ui_test.py').read_text()
        cleanup=source.split('    finally:',1)[1]
        exact=cleanup.split('if args.test_airing_id:',1)[1].split('                else:',1)[0]
        self.assertIn("call('IsManualRecord','airing:'+str(args.test_airing_id))",exact)
        self.assertIn("flag.get('Result') is False",exact)
        self.assertNotIn("command('record')",exact)
        self.assertIn('command(key)',exact)

    def test_single_sagex_media_objects_accept_both_proven_response_wrappers(self):
        source=(ROOT/'scripts/mcp_scheduled_resume_ui_test.py').read_text()
        node=next(n for n in ast.parse(source).body
                  if isinstance(n,ast.FunctionDef) and n.name=='media_object')
        namespace={}
        exec(compile(ast.Module(body=[node],type_ignores=[]),'<media oracle>','exec'),namespace)
        normalize=namespace['media_object'];item={'MediaFileID':66047642,'IsFileCurrentlyRecording':True}
        self.assertEqual(item,normalize({'MediaFile':item}))
        self.assertEqual(item,normalize({'Result':item}))
        self.assertEqual({},normalize({'Result':[]}))
        self.assertEqual({},normalize({}))

    def test_padded_continuation_never_reopens_or_repositions_existing_first_watch(self):
        source=(ROOT/'scripts/mcp_padded_watch_continuation.py').read_text()
        self.assertNotIn('control.watch(',source)
        self.assertNotIn('control.seek(',source)
        self.assertNotIn('clear_watched(',source)
        self.assertNotIn('dev_checkpoint_settings',source)
        self.assertIn("final.get('mediaSourceIdentity')==initial.get('mediaSourceIdentity')",source)
        self.assertIn("int(final['serverRequestedSeekMs'])==target",source)
        self.assertIn('initialHealth',source)

    def test_sagemc_saved_partial_is_not_restarted_to_clear_historical_manual_flag(self):
        source=(ROOT/'scripts/mcp_scheduled_resume_ui_test.py').read_text()
        self.assertIn("partial.get('IsFileCurrentlyRecording') is False",source)
        self.assertIn("end['Result']<=time.time()*1000",source)
        self.assertIn("report['partialRecordingPreserved']=True",source)
        self.assertNotIn('DeleteFile(',source)


if __name__ == '__main__':
    unittest.main()
