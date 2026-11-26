from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/'source/dev/android-shared/src/main/res'
JAVA=ROOT/'source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android'
A='{http://schemas.android.com/apk/res/android}'
def keyed(tree):return {node.get(A+'key'):node for node in tree.iter() if node.get(A+'key')}

class SettingsMenuLayout(unittest.TestCase):
    def setUp(self):
        self.main=ET.parse(RES/'xml/prefs.xml').getroot();self.fixed=ET.parse(RES/'xml/transcoding_prefs.xml').getroot()
    def test_six_independent_inline_sections(self):
        self.assertEqual([n.get(A+'title') for n in self.main],['Playback Settings','Transport Settings','Mappings','General','Diagnostics','About'])
        for section in self.main:self.assertTrue(section.tag.endswith('CollapsiblePreferenceCategory'))
    def test_playback_and_transport_ownership(self):
        p=keyed(self.main[0]);t=keyed(self.main[1])
        for key in ['use_opengl_ui','use_native_resolution','wait_for_playback_before_first_osd','selected_player_settings']:self.assertIn(key,p)
        for key in ['streaming_mode','fixed_transcoding','push_settings','smb_settings','unified_graphics_surfaces']:self.assertIn(key,t)
        advanced=t['transport/advanced'];self.assertEqual(advanced.tag,'PreferenceCategory')
        self.assertEqual(set(keyed(advanced)),{'transport/advanced','container_codec_settings','unified_graphics_surfaces'})
    def test_only_one_adjustment_ui_and_no_test_buttons(self):
        values=keyed(self.fixed)
        self.assertEqual(values['mim_direct/adjustment_method'].tag,'ListPreference')
        self.assertEqual(values['mim_direct/adjustment_method'].get(A+'persistent'),'false')
        for removed in ['mim_direct/adaptive_quality','mim_direct/runtime_rate_control','mim_direct/test_bitrate','mim_direct/test_bitrate_status']:self.assertNotIn(removed,values)
    def test_stock_controls_and_saved_keys_remain(self):
        stock=keyed(self.fixed)['fixed/standard']
        for key in ['fixed_encoding/preference','fixed_encoding/format','fixed_remuxing/preference','fixed_remuxing/format']:self.assertIn(key,keyed(stock))
        for key in ['fixed_encoding/video_bitrate_kbps','fixed_encoding/video_fps','fixed_encoding/video_resolution','fixed_encoding/audio_codec','fixed_caption_side_channel/port']:self.assertIn(key,keyed(self.fixed))
    def test_stable_transport_values_and_copy_label(self):
        arrays=ET.parse(RES/'values/arrays.xml').getroot()
        self.assertEqual([n.text for n in arrays.find("string-array[@name='entryvalues_list_streaming_mode_preference']")],['dynamic','pull','fixed','smb_direct','smb_auto'])
        self.assertEqual([n.text for n in arrays.find("string-array[@name='entryvalues_mim_direct_mode']")],['off','copy','transcode','hls'])
        self.assertEqual(arrays.find("string-array[@name='entries_mim_direct_mode']")[0].text,'Stock SageTV Pipeline')
    def test_expansion_is_visibility_only_and_remembers_values(self):
        source=(JAVA/'ui/settings/CollapsiblePreferenceCategory.java').read_text(encoding='utf-8')
        self.assertIn('setVisible(expanded)',source);self.assertIn('"/expanded"',source);self.assertIn('view.requestFocus()',source)
        self.assertNotIn('removePreference',source);self.assertNotIn('.clear()',source)
        self.assertIn('Color.rgb(129, 212, 250)',source)
        self.assertIn('setOnKeyListener',source);self.assertIn('KEYCODE_DPAD_CENTER',source)
        self.assertIn('ACTION_UP&&!event.isCanceled()',source)
    def test_reset_is_allowlisted_confirmed_and_cancel_focused(self):
        reset=(JAVA/'ui/settings/SettingsDefaults.java').read_text(encoding='utf-8')
        fragment=(JAVA/'ui/settings/SettingsFragment.java').read_text(encoding='utf-8')
        self.assertNotIn('.clear()',reset);self.assertIn('protectedKey(key)',reset)
        for generated in ['Container.values()','VideoCodec.values()','AudioCodec.values()']:
            self.assertIn(generated,reset)
        self.assertIn('.setNegativeButton("Cancel",null)',fragment)
        self.assertIn('BUTTON_NEGATIVE).requestFocus()',fragment)
        self.assertIn('android:key="reset_to_defaults"',(RES/'xml/prefs.xml').read_text())
    def test_navigation_status_and_no_false_next_playback_label(self):
        fragment=(JAVA/'ui/settings/SettingsFragment.java').read_text(encoding='utf-8')
        self.assertIn('returnFocusKey',fragment);self.assertIn('getPreferenceAdapterPosition(returnFocusKey)',fragment)
        self.assertIn('Configured: ',fragment);self.assertIn('Active: ',fragment);self.assertIn('Not connected',fragment)
        self.assertNotIn('applies on next playback',fragment)
    def test_defaults_review_and_no_sensitive_entries(self):
        review=(ROOT/'docs/SETTINGS_MENU_DEFAULTS.txt').read_text()
        self.assertIn('* Stock SageTV Pipeline',review);self.assertIn('* Push',review)
        self.assertIn('* 4000',review);self.assertNotIn('196977',review)

if __name__=='__main__':unittest.main()
