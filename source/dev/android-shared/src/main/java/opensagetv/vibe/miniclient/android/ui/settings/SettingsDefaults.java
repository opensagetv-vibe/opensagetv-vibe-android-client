package opensagetv.vibe.miniclient.android.ui.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.XmlResourceParser;
import java.util.LinkedHashMap;
import java.util.Map;
import opensagetv.vibe.miniclient.android.R;
import opensagetv.vibe.miniclient.android.prefs.AndroidPrefStore;
import opensagetv.vibe.miniclient.prefs.PrefStore;
import org.xmlpull.v1.XmlPullParser;

/** Explicit displayed-preference allowlist; never clear the whole store. */
public final class SettingsDefaults {
    public static void restoreTranscoding(SharedPreferences saved){
        SharedPreferences.Editor edit=saved.edit();
        java.util.List<PlaybackSettingCatalog.Definition> definitions=new java.util.ArrayList<>(PlaybackSettingCatalog.FIXED_OUTPUT);
        java.util.Collections.addAll(definitions,PlaybackSettingCatalog.PROCESSING,PlaybackSettingCatalog.MINIMUM,PlaybackSettingCatalog.MAXIMUM);
        for(PlaybackSettingCatalog.Definition d:definitions)edit.putString(d.key,d.defaultValue);
        edit.putString(AndroidPrefStore.FIXED_ENCODING_PREFERENCE,AndroidPrefStore.FIXED_ENCODING_PREFERENCE_DEFAULT)
            .putString(PrefStore.Keys.mim_direct_deinterlace,"auto")
            .putBoolean(PrefStore.Keys.mim_direct_adaptive_quality,false)
            .putBoolean(PrefStore.Keys.mim_direct_runtime_rate,false).apply();
    }
    private static final String ANDROID="http://schemas.android.com/apk/res/android";
    private SettingsDefaults() { }
    public static boolean protectedKey(String key) {
        return key==null || key.equals("clientid") || key.startsWith("smb_")
                || key.startsWith("settings/") || key.toLowerCase(java.util.Locale.ROOT).matches(".*(password|credential|token|secret|client_id|server_id|profile|mapping_json).*");
    }
    public static void restore(Context context,SharedPreferences saved) {
        Map<String,Object> defaults=new LinkedHashMap<>();
        int[] resources={R.xml.prefs,R.xml.transcoding_prefs,R.xml.push_prefs,R.xml.remuxing_prefs,
                R.xml.exoplayer_prefs,R.xml.media3player_prefs,R.xml.ijkplayer_prefs,R.xml.gsyplayer_prefs,
                R.xml.media_mappings_prefs,R.xml.touch_mappings_prefs,R.xml.playback_track_prefs,
                R.xml.disc_playback_prefs,R.xml.home_background_prefs,R.xml.diagnostics_prefs};
        // Parse every resource BEFORE writing; malformed defaults never cause a
        // partial reset. Never touch databases, files, native keys or profiles.
        try {
            for(int resource:resources)try(XmlResourceParser parser=context.getResources().getXml(resource)) {
                while(parser.next()!=XmlPullParser.END_DOCUMENT) {
                    if(parser.getEventType()!=XmlPullParser.START_TAG)continue;
                    String tag=parser.getName(),key=parser.getAttributeValue(ANDROID,"key");
                    if(protectedKey(key)||"false".equals(parser.getAttributeValue(ANDROID,"persistent")))continue;
                    boolean toggle=tag.contains("CheckBoxPreference")||tag.contains("SwitchPreference");
                    if(!toggle&&!tag.contains("ListPreference")&&!tag.contains("EditTextPreference"))continue;
                    String value=parser.getAttributeValue(ANDROID,"defaultValue");
                    defaults.put(key,toggle?Boolean.valueOf(value):value);
                }
            }
        }catch(Exception error){throw new IllegalStateException("Unable to read settings defaults; nothing reset",error);}
        defaults.put(AndroidPrefStore.FIXED_ENCODING_PREFERENCE,AndroidPrefStore.FIXED_ENCODING_PREFERENCE_DEFAULT);
        defaults.put(AndroidPrefStore.FIXED_ENCODING_FORMAT,AndroidPrefStore.FIXED_ENCODING_FORMAT_DEFAULT);
        defaults.put(AndroidPrefStore.FIXED_ENCODING_VIDEO_BITRATE_KBPS,""+AndroidPrefStore.FIXED_ENCODING_VIDEO_BITRATE_KBPS_DEFAULT);
        defaults.put(AndroidPrefStore.FIXED_ENCODING_FPS,AndroidPrefStore.FIXED_ENCODING_FPS_DEFAULT);
        defaults.put(AndroidPrefStore.FIXED_ENCODING_VIDEO_RESOLUTION,AndroidPrefStore.FIXED_ENCODING_VIDEO_RESOLUTION_DEFAULT);
        defaults.put(AndroidPrefStore.FIXED_ENCODING_AUDIO_CODEC,AndroidPrefStore.FIXED_ENCODING_AUDIO_CODEC_DEFAULT);
        defaults.put(AndroidPrefStore.FIXED_ENCODING_AUDIO_BITRATE_KBPS,""+AndroidPrefStore.FIXED_ENCODING_AUDIO_BITRATE_KBPS_DEFAULT);
        defaults.put(AndroidPrefStore.FIXED_ENCODING_AUDIO_CHANNELS,AndroidPrefStore.FIXED_ENCODING_AUDIO_CHANNELS_DEFAULT);
        defaults.put(AndroidPrefStore.FIXED_REMUXING_FORMAT,AndroidPrefStore.FIXED_REMUXING_FORMAT_DEFAULT);
        defaults.put(PrefStore.Keys.mim_direct_adaptive_quality,false);
        defaults.put(PrefStore.Keys.mim_direct_runtime_rate,false);
        // These displayed compatibility choices are generated at runtime,
        // rather than declared as ListPreferences in codec_container_prefs.
        for(opensagetv.vibe.miniclient.media.Container value:opensagetv.vibe.miniclient.media.Container.values())
            defaults.put("container/"+value.getName()+"/support","automatic");
        for(opensagetv.vibe.miniclient.media.VideoCodec value:opensagetv.vibe.miniclient.media.VideoCodec.values())
            defaults.put("codec/video/"+value.getName()+"/support","automatic");
        for(opensagetv.vibe.miniclient.media.AudioCodec value:opensagetv.vibe.miniclient.media.AudioCodec.values())
            defaults.put("codec/audio/"+value.getName()+"/support","automatic");
        SharedPreferences.Editor edit=saved.edit();
        for(PlaybackSettingCatalog.Definition d:PlaybackSettingCatalog.ALL){
            if(d.kind==PlaybackSettingCatalog.Kind.ADJUSTMENT)continue;
            if(d.kind==PlaybackSettingCatalog.Kind.BOOLEAN)defaults.put(d.key,Boolean.valueOf(d.defaultValue));
            else if(d.kind==PlaybackSettingCatalog.Kind.INTEGER)defaults.put(d.key,Integer.valueOf(d.defaultValue));
            else defaults.put(d.key,d.defaultValue);
        }
        for(Map.Entry<String,Object> entry:defaults.entrySet()) {
            Object value=entry.getValue();
            if(value==null)edit.remove(entry.getKey());
            else if(value instanceof Boolean)edit.putBoolean(entry.getKey(),(Boolean)value);
            else if(value instanceof Integer)edit.putInt(entry.getKey(),(Integer)value);
            else edit.putString(entry.getKey(),String.valueOf(value));
        }
        edit.apply();
    }
}
