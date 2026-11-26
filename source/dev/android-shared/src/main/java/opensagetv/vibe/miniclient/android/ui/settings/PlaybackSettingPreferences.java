package opensagetv.vibe.miniclient.android.ui.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.InputType;
import android.widget.Toast;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceManager;
import opensagetv.vibe.miniclient.android.prefs.AndroidPrefStore;
import opensagetv.vibe.miniclient.android.prefs.FixedAdjustmentPolicy;
import opensagetv.vibe.miniclient.prefs.PrefStore;

/** Typed persistence and startup presentation for the shared playback catalog. */
public final class PlaybackSettingPreferences {
    public static String read(SharedPreferences saved,PlaybackSettingCatalog.Definition definition){
        if(definition.kind==PlaybackSettingCatalog.Kind.ADJUSTMENT)return AndroidPrefStore.adjustmentMethod(saved);
        Object value=saved.getAll().get(definition.key);return value==null?definition.defaultValue:String.valueOf(value);
    }
    public static String validate(SharedPreferences saved,PlaybackSettingCatalog.Definition d,String value,Context context){
        if(d.values!=0){boolean found=false;for(String option:d.values(context))if(option.equals(value))found=true;
            if(!found)return "Unsupported choice; nothing changed";
        }else if((d.kind==PlaybackSettingCatalog.Kind.INTEGER||d==PlaybackSettingCatalog.MINIMUM)&&!d.validNumber(value))
            return "Enter a value from "+d.min+" to "+d.max;
        if(d==PlaybackSettingCatalog.MINIMUM||d==PlaybackSettingCatalog.MAXIMUM||d==PlaybackSettingCatalog.ADJUSTMENT){
            int min=parse(d==PlaybackSettingCatalog.MINIMUM?value:read(saved,PlaybackSettingCatalog.MINIMUM),250);
            int max=parse(d==PlaybackSettingCatalog.MAXIMUM?value:read(saved,PlaybackSettingCatalog.MAXIMUM),4000);
            boolean adjusted=d==PlaybackSettingCatalog.ADJUSTMENT?!"off".equals(value):!"off".equals(read(saved,PlaybackSettingCatalog.ADJUSTMENT));
            if((d!=PlaybackSettingCatalog.MAXIMUM||adjusted)&&min>max)return "Minimum must not exceed Maximum bitrate";
        }
        return null;
    }
    public static boolean write(SharedPreferences saved,PlaybackSettingCatalog.Definition d,String value,Context context){
        String failure=validate(saved,d,value,context);
        if(failure!=null){Toast.makeText(context,failure,Toast.LENGTH_LONG).show();return false;}
        if(d.kind==PlaybackSettingCatalog.Kind.ADJUSTMENT)AndroidPrefStore.setAdjustmentMethod(saved,value);
        else{SharedPreferences.Editor edit=saved.edit();
            if(d.kind==PlaybackSettingCatalog.Kind.BOOLEAN)edit.putBoolean(d.key,Boolean.parseBoolean(value));
            else if(d.kind==PlaybackSettingCatalog.Kind.INTEGER)edit.putInt(d.key,Integer.parseInt(value));
            else edit.putString(d.key,value);edit.apply();}
        return true;
    }
    /** Apply identical resource choices to existing XML lists, retaining listeners/values. */
    public static void applyExisting(PreferenceGroup screen){
        for(PlaybackSettingCatalog.Definition d:PlaybackSettingCatalog.ALL){
            Preference preference=screen.findPreference(d.key);if(preference==null)continue;
            // Older Exo pages used a negatively named checkbox for this same
            // boolean. Present the shared PCM/passthrough choices everywhere,
            // retaining its key, saved boolean type, ordering and default.
            if(d==PlaybackSettingCatalog.AUDIO_OUTPUT&&!(preference instanceof ListPreference)){
                PreferenceGroup parent=preference.getParent();
                if(parent!=null){
                    Preference replacement=create(screen.getContext(),PreferenceManager.getDefaultSharedPreferences(screen.getContext()),d);
                    replacement.setOrder(preference.getOrder());parent.removePreference(preference);parent.addPreference(replacement);preference=replacement;
                }
            }
            if(preference instanceof ListPreference){ListPreference list=(ListPreference)preference;
                // The main Player picker describes a saved default, not an active player.
                list.setTitle(d==PlaybackSettingCatalog.PLAYER?"Default "+d.title:d.title);
                if(d.labels!=0){list.setEntries(d.labels);list.setEntryValues(d.values);}
            }
        }
    }
    public static void append(PreferenceGroup screen,String group,String title){
        Context context=screen.getContext();SharedPreferences saved=PreferenceManager.getDefaultSharedPreferences(context);
        PreferenceCategory category=new PreferenceCategory(context);category.setKey("settings/defaults/"+group);category.setTitle(title);screen.addPreference(category);
        for(PlaybackSettingCatalog.Definition d:PlaybackSettingCatalog.ALL)if(group.equals(d.group)){
            // Queueing is already exposed by each selected player's settings.
            if(d==PlaybackSettingCatalog.MEDIA3_QUEUE||d==PlaybackSettingCatalog.EXO_QUEUE)continue;
            Preference item=create(context,saved,d);category.addPreference(item);
        }
    }
    public static Preference create(Context context,SharedPreferences saved,PlaybackSettingCatalog.Definition d){
        Preference result;
        if(d.labels!=0){
            ListPreference list=new ListPreference(context);list.setPersistent(false);list.setEntries(d.labels);list.setEntryValues(d.values);
            list.setValue(read(saved,d));list.setSummary(d.display(context,read(saved,d)));
            list.setOnPreferenceChangeListener((preference,value)->{
                if(write(saved,d,String.valueOf(value),context)){list.setValue(read(saved,d));list.setSummary(d.display(context,read(saved,d)));}return false;
            });result=list;
        }else{
            EditTextPreference edit=new EditTextPreference(context);edit.setPersistent(false);edit.setText(read(saved,d));edit.setSummary(d.display(context,read(saved,d)));
            edit.setOnBindEditTextListener(field->field.setInputType(InputType.TYPE_CLASS_NUMBER|(d.min<0?InputType.TYPE_NUMBER_FLAG_SIGNED:0)));
            edit.setOnPreferenceChangeListener((preference,value)->{
                if(write(saved,d,String.valueOf(value).trim(),context)){edit.setText(read(saved,d));edit.setSummary(d.display(context,read(saved,d)));}return false;
            });result=edit;
        }
        // Nonpersistent widgets already hold read(saved,d). Supplying an XML-
        // style default here would let AndroidX replace that value on attach.
        result.setKey(d.key);result.setTitle(d.title);
        String reason=PlaybackSettingCatalog.defaultUnavailable(d,saved.getString(PrefStore.Keys.default_player,"exoplayer"));
        if(reason!=null){result.setEnabled(false);result.setSummary(reason);}
        return result;
    }
    private static int parse(String value,int fallback){try{return Integer.parseInt(value);}catch(RuntimeException e){return fallback;}}
    private PlaybackSettingPreferences(){}
}
