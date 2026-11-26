from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
SHARED=ROOT/'source/dev/android-shared/src/main'
JAVA=SHARED/'java/opensagetv/vibe/miniclient/android'
A='{http://schemas.android.com/apk/res/android}'

class SharedPlaybackSettings(unittest.TestCase):
    def test_hardware_encoder_parity_and_negotiation(self):
        arrays=ET.parse(SHARED/'res/values/playback_settings_arrays.xml').getroot()
        options=next(n for n in arrays if n.get('name')=='values_hardware_encoder')
        self.assertEqual([n.text for n in options],['auto','qsv','nvenc','vaapi','amf','videotoolbox'])
        settings=ET.parse(SHARED/'res/xml/transcoding_prefs.xml').getroot()
        row=next(n for n in settings.iter() if n.get(A+'key')=='mim_direct/hardware_backend')
        self.assertEqual(row.get(A+'defaultValue'),'auto')
        self.assertEqual(row.get(A+'title'),'Hardware Encoder')
        source=(JAVA/'video/MimDirectSessionClient.java').read_text(encoding='utf-8')
        self.assertIn('hardwareBackendRevision',source)
        self.assertIn('!"auto".equals(next.hardwareBackend)&&!hardwareBackendsNegotiated',source)
        self.assertIn('hardwareBackendsNegotiated=false',source)
    def test_both_navigation_layouts_have_one_gear_and_keep_shortcuts(self):
        for path in [SHARED/'res/layout/navigation.xml',ROOT/'source/dev/android-tv/src/main/res/layout-notouch/navigation.xml']:
            tree=ET.parse(path).getroot();nodes={n.get(A+'id'):n for n in tree.iter() if n.get(A+'id')}
            for name in ['nav_playback_settings','nav_active_player_adjustments','nav_audio_output','nav_closed_captions','nav_export_diagnostics']:
                self.assertIn('@+id/'+name,nodes)
            gear=nodes['@+id/nav_playback_settings'];self.assertEqual(gear.get(A+'src'),'@drawable/ic_settings_white_24dp')
            self.assertNotEqual(gear.get(A+'visibility'),'invisible')
            for grid in tree.iter('GridLayout'):
                occupied=[]
                for child in grid:
                    if child.get(A+'visibility') in ('invisible','gone'):continue
                    pos=(child.get(A+'layout_column'),child.get(A+'layout_row'))
                    self.assertNotIn(pos,occupied);occupied.append(pos)
    def test_gear_and_shortcuts_enter_shared_controller(self):
        nav=(JAVA/'NavigationDialog.java').read_text(encoding='utf-8')
        for route in ['showHub(activity)','show(activity)','showAudio(activity)','showCaptions(activity)']:
            self.assertIn('ActivePlayerAdjustmentsDialog.'+route,nav)
        dialog=(JAVA/'ActivePlayerAdjustmentsDialog.java').read_text(encoding='utf-8')
        for group in ['Playback Settings','Transport Settings','Diagnostics','Recovery']:
            self.assertIn('"'+group+'"',dialog)
        for ident in ['video','audio','captions','transport','filters','display']:
            self.assertIn('showPanel("'+ident+'"',dialog)
    def test_hub_blue_sections_contain_actions_not_navigation_wrappers(self):
        source=(JAVA/'ActivePlayerAdjustmentsDialog.java').read_text(encoding='utf-8')
        hub=source.split('private void showHubMenu(){',1)[1].split('private void showPanel(',1)[0]
        for group in ('Transport','Diagnostics','Recovery'):
            self.assertIn('add'+group+'Entries(',hub)
        for wrapper in ('Transport and Fixed Playback','Playback Status, Tests and Export','Restart / Restore Saved Defaults'):
            self.assertNotIn(wrapper,hub)
        self.assertNotIn('showPanel("fixed"',source)
        transport=source.split('private void addTransportEntries(',1)[1].split('private void chooseSavedPlaybackDefault(',1)[0]
        self.assertIn('PlaybackSettingCatalog.FIXED_OUTPUT',transport)
        self.assertIn('Reduce Current Session',transport)
        self.assertIn('Recover Current Session',transport)
    def test_explicit_transcode_start_failure_cannot_silently_open_original_pull(self):
        source=(JAVA/'video/MimDirectSessionClient.java').read_text(encoding='utf-8')
        failed=source.split('catch (Exception failure)',1)[1].split('Claim the only automatic recovery',1)[0]
        self.assertIn('blocksOriginalPullAfterStartFailure(mode,',failed)
        self.assertIn('start_failed_transcode_stopped',failed)
        self.assertIn('Original-file Pull fallback was not used',failed)
    def test_codec_failure_attribution_is_closed_and_on_demand(self):
        source=(JAVA/'video/CodecFailureDetail.java').read_text(encoding='utf-8')
        self.assertIn('depth<6',source)
        self.assertIn('"mimeType","diagnosticInfo","decoderName"',source)
        self.assertNotIn('getMessage(',source)
        self.assertNotIn('printStackTrace(',source)
        probe=(ROOT/'source/dev/android-tv/src/debug/java/opensagetv/vibe/miniclient/android/tv/debug/PlaybackHealthProbe.java').read_text(encoding='utf-8')
        self.assertIn('CodecFailureDetail.describe(playerError)',probe)
        adapter=(ROOT/'mcp/src/sagetv_dev_mcp/server.py').read_text(encoding='utf-8')
        self.assertIn('"health_playerErrorDetail"',adapter)
    def test_shared_catalog_resource_choices_exist_and_pair(self):
        arrays={n.get('name'):[c.text for c in n] for path in (SHARED/'res/values').glob('*.xml') for n in ET.parse(path).getroot() if n.tag=='string-array'}
        catalog=(JAVA/'ui/settings/PlaybackSettingCatalog.java').read_text(encoding='utf-8')
        for labels,values in re.findall(r'R.array.(\w+),R.array.(\w+)',catalog):
            self.assertIn(labels,arrays);self.assertIn(values,arrays)
            self.assertEqual(len(arrays[labels]),len(arrays[values]));self.assertEqual(len(set(arrays[values])),len(arrays[values]))
        self.assertEqual(arrays['values_playback_audio_output'],['true','false'])
        self.assertEqual(arrays['entryvalues_list_decoding_method'],['hardware','software','hardware_preferred'])
    def test_startup_screens_bind_the_same_catalog(self):
        for name in ['SettingsFragment','FixedTranscodingFragment','Media3PlayerSettingsFragment','ExoPlayerSettingsFragment','IJKPlayerSettingsFragment','GSYPlayerSettingsFragment','DiscPlaybackSettingsFragment']:
            self.assertIn('PlaybackSettingPreferences.applyExisting(getPreferenceScreen())',(JAVA/f'ui/settings/{name}.java').read_text(encoding='utf-8'))
        tracks=(JAVA/'ui/settings/PlaybackTrackSettingsFragment.java').read_text(encoding='utf-8')
        self.assertIn('"audio","Audio Settings"',tracks);self.assertIn('"captions","Subtitle Appearance and Offset"',tracks)
    def test_renderer_is_bounded_focusable_event_only_and_namespaced(self):
        source=(JAVA/'PlaybackSettingsPanel.java').read_text(encoding='utf-8')
        for token in ['0xff81d4fa','settings/runtime/','setVisibility(open?View.VISIBLE:View.GONE)','KEYCODE_DPAD_CENTER','ACTION_UP','setFocusableInTouchMode(true)','BoundedScroll','AT_MOST','if(!choice)']:
            self.assertIn(token,source)
        for forbidden in ['postDelayed','Timer','requestCompanion','getPlaya','putBoolean(entry','http://']:
            self.assertNotIn(forbidden,source)
        self.assertIn('MotionEvent.ACTION_DOWN)target.requestFocus()',source)
    def test_fixed_changes_require_confirmation_and_do_not_offer_fake_session_scope(self):
        source=(JAVA/'ActivePlayerAdjustmentsDialog.java').read_text(encoding='utf-8')
        fixed=source.split('private void chooseSavedPlaybackDefault(',1)[1].split('private void showDiagnosticsMenu()',1)[0]
        self.assertIn('confirmSavedPlaybackDefault',fixed);self.assertIn('confirm("Save as device default?"',fixed)
        self.assertNotIn('This playback session',fixed)
        self.assertIn('Current session is unchanged',fixed)
        prefs=(JAVA/'ui/settings/PlaybackSettingPreferences.java').read_text(encoding='utf-8')
        self.assertIn('setPersistent(false)',prefs);self.assertIn('Kind.BOOLEAN',prefs);self.assertIn('putInt',prefs)
        self.assertIn('Minimum must not exceed Maximum',prefs)
        self.assertIn('d==PlaybackSettingCatalog.AUDIO_OUTPUT',prefs)
        self.assertIn('replacement.setOrder(preference.getOrder())',prefs)
    def test_session_and_recovery_do_not_erase_saved_preferences(self):
        source=(JAVA/'ActivePlayerAdjustmentsDialog.java').read_text(encoding='utf-8')
        scope=source.split('private void chooseScope(',1)[1].split('private void reload(',1)[0]
        self.assertIn('Integer.parseInt(index)==1',scope);self.assertIn('PlaybackSettingPreferences.write',scope)
        recovery=source.split('private void showRecoveryMenu()',1)[1].split('private void confirm(',1)[0]
        self.assertIn('ActivePlayerSessionOverrides.reset()',recovery)
        self.assertGreater(recovery.rindex('ActivePlayerSessionOverrides.reset()'),recovery.index('active.setAudioOffsetMillis('))
        self.assertNotIn('SettingsDefaults.restore',recovery);self.assertNotIn('.clear()',recovery)
    def test_fixed_startup_range_uses_the_same_applicability_as_runtime(self):
        source=(JAVA/'ui/settings/FixedTranscodingFragment.java').read_text(encoding='utf-8')
        for name in ('ADJUSTMENT','MINIMUM','MAXIMUM'):
            self.assertIn('output.add(PlaybackSettingCatalog.'+name+')',source)
        self.assertIn('PlaybackSettingCatalog.fixedUnavailable',source)
        self.assertNotIn('Enable Automatic Bitrate Adjustment for a MIM Transcode method',source)
    def test_physical_gate_checks_mcp_interpreter_before_device_mutation(self):
        source=(ROOT/'scripts/mcp_playback_settings_gate.py').read_text(encoding='utf-8')
        self.assertIn("__import__('mcp.server')",source)
        self.assertIn('/opt/opensagetv-vibe/android-python/bin/python3',source)
        self.assertLess(source.index("__import__('mcp.server')"),source.index('    adb.connect()'))
    def test_fixed_gate_does_not_infer_server_hardware_from_client_or_request(self):
        source=(ROOT/'scripts/mcp_playback_settings_gate.py').read_text(encoding='utf-8')
        observed=source.split('def observed_server_encoder(mode):',1)[1].split('\ntry:',1)[0]
        self.assertIn('Unknown (stock-protocol Fixed; no per-session provider status)',observed)
        self.assertIn("tap('Effective Transcoding Output')",observed)
        self.assertIn("args.policy=='hardware_only'",observed)
        self.assertIn("args.policy=='software'",observed)
        self.assertNotIn('health_videoDecoder',observed)
        self.assertIn('serverEncoder=observed_encoder',source)
        self.assertIn('if pce_errors or playback_errors:raise',source)
        self.assertIn('HLS handoff lost advancing audio',source)
        self.assertIn('HLS handoff lost Source-channel AAC configuration',source)
        self.assertIn("cleanup['finalProviderSessions']=0",source)
        self.assertIn("args.phase=='fixed-edges' and args.policy!='server'",source)
    def test_debug_ui_routes_are_closed_and_default_stays_video(self):
        adb=(ROOT/'mcp/src/sagetv_dev_mcp/adb.py').read_text(encoding='utf-8')
        route=adb.split('def show_active_player_adjustments(',1)[1].split('def show_av_sync_test',1)[0]
        self.assertIn('panel: str = "video"',route);self.assertIn('raise ValueError',route)

    def test_reported_seek_gate_preserves_recording_history_and_uses_actual_targets(self):
        source=(ROOT/'scripts/mcp_playback_settings_gate.py').read_text(encoding='utf-8')
        self.assertIn('Reported-source targets require FIXED-002/SETTINGS-005 fixed-routes before device mutation',source)
        self.assertIn('if not args.reported_source:api.clear_watched(media_id)',source)
        self.assertIn('for target in args.seek_target_ms or [30000]:',source)
        self.assertIn("'target_ms':target",source)
        self.assertIn("int(started.get('health_audioChannels',-1)) if args.reported_source else 6",source)

if __name__=='__main__':unittest.main()
