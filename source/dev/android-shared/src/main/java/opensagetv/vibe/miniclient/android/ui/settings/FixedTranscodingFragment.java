package opensagetv.vibe.miniclient.android.ui.settings;

import android.os.Bundle;
import android.text.InputType;
import android.content.SharedPreferences;
import android.widget.Toast;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import opensagetv.vibe.miniclient.android.R;
import opensagetv.vibe.miniclient.android.prefs.AndroidPrefStore;
import opensagetv.vibe.miniclient.android.prefs.FixedAdjustmentPolicy;
import opensagetv.vibe.miniclient.prefs.PrefStore;

public class FixedTranscodingFragment extends PreferenceFragmentCompat {
    private SharedPreferences saved;
    @Override public void onCreatePreferences(Bundle state, String rootKey) {
        saved=PreferenceManager.getDefaultSharedPreferences(requireContext());
        AndroidPrefStore.migrateFixedTranscodingListPreferenceTypes(saved);
        AndroidPrefStore.migrateTransportSelection(saved);
        setPreferencesFromResource(R.xml.transcoding_prefs,rootKey);
        PlaybackSettingPreferences.applyExisting(getPreferenceScreen());
        String[][] defaults={
            {AndroidPrefStore.FIXED_ENCODING_PREFERENCE,AndroidPrefStore.FIXED_ENCODING_PREFERENCE_DEFAULT},
            {AndroidPrefStore.FIXED_ENCODING_FORMAT,AndroidPrefStore.FIXED_ENCODING_FORMAT_DEFAULT},
            {AndroidPrefStore.FIXED_REMUXING_PREFERENCE,AndroidPrefStore.FIXED_REMUXING_PREFERENCE_DEFAULT},
            {AndroidPrefStore.FIXED_REMUXING_FORMAT,AndroidPrefStore.FIXED_REMUXING_FORMAT_DEFAULT},
            {AndroidPrefStore.FIXED_ENCODING_VIDEO_BITRATE_KBPS,""+AndroidPrefStore.FIXED_ENCODING_VIDEO_BITRATE_KBPS_DEFAULT},
            {AndroidPrefStore.FIXED_ENCODING_FPS,AndroidPrefStore.FIXED_ENCODING_FPS_DEFAULT},
            {AndroidPrefStore.FIXED_ENCODING_VIDEO_RESOLUTION,AndroidPrefStore.FIXED_ENCODING_VIDEO_RESOLUTION_DEFAULT},
            {AndroidPrefStore.FIXED_ENCODING_AUDIO_CODEC,AndroidPrefStore.FIXED_ENCODING_AUDIO_CODEC_DEFAULT},
            {AndroidPrefStore.FIXED_ENCODING_AUDIO_BITRATE_KBPS,""+AndroidPrefStore.FIXED_ENCODING_AUDIO_BITRATE_KBPS_DEFAULT},
            {AndroidPrefStore.FIXED_ENCODING_AUDIO_CHANNELS,AndroidPrefStore.FIXED_ENCODING_AUDIO_CHANNELS_DEFAULT},
            {PrefStore.Keys.mim_direct_deinterlace,"auto"}
        };
        for(String[] entry:defaults) PreferenceUtils.setDefaultValue(findPreference(entry[0]),entry[1]);
        ListPreference processing=findPreference(PrefStore.Keys.mim_direct_mode);
        processing.setOnPreferenceChangeListener((preference,value)->{
            new android.os.Handler(android.os.Looper.getMainLooper()).post(this::refreshApplicability);return true;
        });
        findPreference(PlaybackSettingCatalog.ENCODER.key).setOnPreferenceChangeListener((preference,value)->{
            new android.os.Handler(android.os.Looper.getMainLooper()).post(this::refreshApplicability);return true;
        });
        ListPreference adjustment=findPreference("mim_direct/adjustment_method");
        adjustment.setOnPreferenceChangeListener((preference,value)->{
            String method=String.valueOf(value);
            if(!FixedAdjustmentPolicy.valid(method))return false;
            if(FixedAdjustmentPolicy.adaptive(method)&&minimum()>maximum()){
                Toast.makeText(requireContext(),"Minimum must not exceed Maximum bitrate.",Toast.LENGTH_LONG).show();return false;
            }
            AndroidPrefStore.setAdjustmentMethod(saved,method);
            new android.os.Handler(android.os.Looper.getMainLooper()).post(this::refreshApplicability);
            return true;
        });
        EditTextPreference minimum=findPreference(PrefStore.Keys.mim_direct_minimum_kbps);
        minimum.setOnBindEditTextListener(field->field.setInputType(InputType.TYPE_CLASS_NUMBER));
        minimum.setOnPreferenceChangeListener((preference,value)->{
            try {int n=Integer.parseInt(String.valueOf(value));if(n>=250&&n<=maximum())return true;}
            catch(NumberFormatException ignored){}
            Toast.makeText(requireContext(),"Minimum must be at least 250 and not exceed Maximum bitrate.",Toast.LENGTH_LONG).show();return false;
        });
        ListPreference maximum=findPreference(AndroidPrefStore.FIXED_ENCODING_VIDEO_BITRATE_KBPS);
        maximum.setOnPreferenceChangeListener((preference,value)->{
            try {if(!saved.getBoolean(PrefStore.Keys.mim_direct_adaptive_quality,false)||Integer.parseInt(String.valueOf(value))>=minimum())return true;}
            catch(NumberFormatException ignored){}
            Toast.makeText(requireContext(),"Maximum must not be below Minimum bitrate.",Toast.LENGTH_LONG).show();return false;
        });
        Preference remux=findPreference(AndroidPrefStore.FIXED_REMUXING_PREFERENCE);
        remux.setOnPreferenceChangeListener((preference,value)->{
            new android.os.Handler(android.os.Looper.getMainLooper()).post(this::refreshApplicability);return true;
        });
        EditTextPreference port=findPreference(PrefStore.Keys.fixed_caption_side_channel_port);
        port.setOnBindEditTextListener(field->field.setInputType(InputType.TYPE_CLASS_NUMBER));
        refreshApplicability();
        findPreference("fixed/reset_transcoding").setOnPreferenceClickListener(preference->{
            androidx.appcompat.app.AlertDialog dialog=new androidx.appcompat.app.AlertDialog.Builder(requireContext()).setTitle("Reset Transcoding Settings Only?")
                .setMessage("Applies on next playback. Current playback, connections, credentials and unrelated preferences will not change.")
                .setNegativeButton("Cancel",null).setPositiveButton("Reset",(resetDialog,which)->{
                    SettingsDefaults.restoreTranscoding(saved);onCreatePreferences(null,null);
                }).create();
            dialog.setOnShowListener(unused->{android.widget.Button cancel=dialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE);cancel.setFocusableInTouchMode(true);cancel.requestFocus();});
            dialog.show();return true;
        });
    }
    private int minimum(){try{return Integer.parseInt(saved.getString(PrefStore.Keys.mim_direct_minimum_kbps,"250"));}catch(NumberFormatException e){return 250;}}
    private int maximum(){try{return Integer.parseInt(saved.getString(AndroidPrefStore.FIXED_ENCODING_VIDEO_BITRATE_KBPS,"4000"));}catch(NumberFormatException e){return 4000;}}
    private void option(String key,boolean enabled,String reason){
        Preference row=findPreference(key);if(row==null)return;
        row.setEnabled(enabled);
        if(row instanceof ListPreference){
            ListPreference choice=(ListPreference)row;
            choice.setSummaryProvider(enabled?ListPreference.SimpleSummaryProvider.getInstance():null);
        }
        if(!enabled)row.setSummary(reason);
    }
    private void refreshApplicability(){
        if(!isAdded())return;
        String mode=saved.getString(PrefStore.Keys.mim_direct_mode,"off");
        boolean stock="off".equals(mode),transcode="hls".equals(mode)||"transcode".equals(mode);
        ListPreference processing=findPreference(PrefStore.Keys.mim_direct_mode);
        processing.setSummaryProvider(preference->((ListPreference)preference).getEntry()+" · Reconnect required");
        ListPreference adjustment=findPreference("mim_direct/adjustment_method");
        adjustment.setValue(AndroidPrefStore.adjustmentMethod(saved));
        for(String key:new String[]{AndroidPrefStore.FIXED_ENCODING_PREFERENCE})
            option(key,stock,"Used by Stock SageTV Pipeline; MIM manages its own packaging");
        java.util.List<PlaybackSettingCatalog.Definition> output=new java.util.ArrayList<>(PlaybackSettingCatalog.FIXED_OUTPUT);
        output.add(PlaybackSettingCatalog.ADJUSTMENT);output.add(PlaybackSettingCatalog.MINIMUM);output.add(PlaybackSettingCatalog.MAXIMUM);
        for(PlaybackSettingCatalog.Definition d:output){
            String reason=PlaybackSettingCatalog.fixedUnavailable(d,mode,!"off".equals(AndroidPrefStore.adjustmentMethod(saved)),saved.getString(AndroidPrefStore.FIXED_REMUXING_PREFERENCE,"needed"),saved.getString(PlaybackSettingCatalog.ENCODER.key,"server"));
            option(d.key,reason==null,reason);
            Preference row=findPreference(d.key);
            if(reason==null&&row instanceof ListPreference)((ListPreference)row).setSummaryProvider(p->
                ((ListPreference)p).getEntry()+" · Next playback"+(d==PlaybackSettingCatalog.HARDWARE_ENCODER?"; explicit choice requires compatible MIM and never changes encoder on failure":"")+(!stock&&(d==PlaybackSettingCatalog.CONTAINER||d==PlaybackSettingCatalog.REMUX_CONTAINER)?"; current MIM segment transport requires MPEG-TS":""));
        }
        if("hls".equals(mode)){adjustment.setSummaryProvider(null);adjustment.setSummary(adjustment.getEntry()+" · Next playback; buffer fill/trend; compatible provider required; resolution stays fixed");}
        option(PrefStore.Keys.mim_direct_deinterlace,transcode,"Requires MIM Transcode; Player Deinterlace is separate");
        boolean adaptive="hls".equals(mode)&&saved.getBoolean(PrefStore.Keys.mim_direct_adaptive_quality,false);
        if(adaptive)findPreference(PrefStore.Keys.mim_direct_minimum_kbps).setSummary(minimum()+" kbps · Next playback; adjustment only");
    }
}
