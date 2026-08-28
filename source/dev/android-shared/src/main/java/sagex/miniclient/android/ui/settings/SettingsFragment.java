package sagex.miniclient.android.ui.settings;

import android.content.Context;
import android.content.Intent;
import android.graphics.Point;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.ListPreference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;
import android.widget.Toast;



import java.io.File;
import java.io.FilenameFilter;

import sagex.miniclient.android.AppUtil;
import sagex.miniclient.android.MiniclientApplication;
import sagex.miniclient.android.R;
import sagex.miniclient.android.prefs.AndroidPrefStore;
import sagex.miniclient.android.util.NetUtil;
import sagex.miniclient.prefs.PrefStore;
import sagex.miniclient.prefs.PrefStore.Keys;
import sagex.miniclient.uibridge.Dimension;
import sagex.miniclient.util.ClientIDGenerator;
import sagex.miniclient.util.Utils;


/**
 * Created by seans on 24/10/15.
 */
public class SettingsFragment extends PreferenceFragment
{
    PrefStore prefs;


    @Override
    public void onCreate(Bundle savedInstanceState)
    {




        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.prefs);

        try
        {
            prefs = MiniclientApplication.get(getActivity()).getClient().properties();
            
            //prefs.setEnabled(this, Prefs.Key.use_log_to_sdcard, !getResources().getBoolean(R.bool.istv));

            Preference p;

            p = this.findPreference(Keys.exit_on_standby);
            if (p != null)
            {
                p.setDefaultValue(true);
            }
            
            p = this.findPreference(Keys.app_destroy_on_pause);
            if (p != null)
            {
                p.setDefaultValue(true);
            }

            p = this.findPreference(Keys.disable_sleep);
            if (p != null)
            {
                p.setDefaultValue(true);
            }
            
            p = this.findPreference("reset_to_defaults");
            if (p != null)
            {
                p.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener()
                {
                    @Override
                    public boolean onPreferenceClick(Preference preference)
                    {
                        clearAllPreferences();
                        return true;
                    }
                });
            }
            
            
            Preference touchPref = this.findPreference("touch_mappings");
            
            touchPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener()
            {
                @Override
                public boolean onPreferenceClick(Preference preference)
                {
                    Intent i = new Intent(SettingsFragment.this.getActivity(), TouchMappingsActivity.class);
                    startActivity(i);
                    
                    return true;
                }
            });
    
            
            
            Preference mediaKeyPref = this.findPreference("media_key_mappings");
            
            mediaKeyPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener()
            {
                @Override
                public boolean onPreferenceClick(Preference preference)
                {
                    Intent i = new Intent(SettingsFragment.this.getActivity(), MediaMappingsActivity.class);
                    startActivity(i);
                    
                    return true;
                }
            });
    
            
            
            p = findPreference(Keys.use_log_to_sdcard);
            p.setOnPreferenceChangeListener(new OnPreferenceChangeListener()
            {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue)
                {
                    AppUtil.initLogging(SettingsFragment.this.getActivity(), (Boolean) newValue);
                    return true;
                }
            });
            
            Preference share_log = findPreference("share_log");
            share_log.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener()
            {
                @Override
                public boolean onPreferenceClick(Preference preference)
                {
                    shareLog();
                    return true;
                }
            });
            
            final ListPreference defaultPlayer = (ListPreference) findPreference(Keys.default_player);
            final ListPreference decodingMethod = (ListPreference) findPreference(Keys.decoding_method);
            final ListPreference loglevel = (ListPreference) findPreference(Keys.log_level);
            loglevel.setOnPreferenceChangeListener(new OnPreferenceChangeListener()
            {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue)
                {
                    AppUtil.setLogLevel((String) newValue);
                    updateListSummary(loglevel, R.string.summary_list_loglevels_preference, newValue);
                    return true;
                }
            });

            defaultPlayer.setOnPreferenceChangeListener(new OnPreferenceChangeListener()
            {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue)
                {
                    updateListSummary(defaultPlayer, R.string.summary_list_default_player, newValue);
                    return true;
                }
            });

            decodingMethod.setOnPreferenceChangeListener(new OnPreferenceChangeListener()
            {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue)
                {
                    updateListSummary(decodingMethod, R.string.summary_list_decoding_method, newValue);
                    return true;
                }
            });

            final Preference fixedTranscoding = this.findPreference("fixed_transcoding");
            final Preference fixedRemuxing = this.findPreference("fixed_remuxing");
            final ListPreference streammode = (ListPreference) findPreference(AndroidPrefStore.STREAMING_MODE);
            fixedTranscoding.setEnabled(prefs.getStreamingMode().equals("fixed"));
            
            streammode.setOnPreferenceChangeListener(new OnPreferenceChangeListener()
            {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue)
                {
                    updateListSummary(streammode, R.string.summary_list_streaming_mode_preference, newValue);

                    fixedTranscoding.setEnabled(newValue.equals("fixed"));
                    fixedRemuxing.setEnabled(newValue.equals("fixed"));

                    return true;
                }
            });
    
            
    
            fixedTranscoding.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener()
            {
                @Override
                public boolean onPreferenceClick(Preference preference)
                {
                    Intent i = new Intent(SettingsFragment.this.getActivity(), FixedTranscodingActivity.class);
                    startActivity(i);
            
                    return true;
                }
            });

            fixedRemuxing.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener()
            {
                @Override
                public boolean onPreferenceClick(Preference preference)
                {
                    Intent i = new Intent(SettingsFragment.this.getActivity(), FixedRemuxingActivity.class);
                    startActivity(i);

                    return true;
                }
            });

            final Preference containerCodec = this.findPreference("container_codec_settings");

            containerCodec.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener()
            {
                @Override
                public boolean onPreferenceClick(Preference preference)
                {
                    Intent i = new Intent(SettingsFragment.this.getActivity(), CodecContainerActivity.class);
                    startActivity(i);

                    return true;
                }
            });

            Preference exoplayerSettingsPref = this.findPreference("exoplayer_settings");

            exoplayerSettingsPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener()
            {
                @Override
                public boolean onPreferenceClick(Preference preference)
                {
                    Intent i = new Intent(SettingsFragment.this.getActivity(), ExoPlayerSettingsActivity.class);
                    startActivity(i);

                    return true;
                }
            });

            Preference media3SettingsPref = this.findPreference("media3_settings");

            media3SettingsPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener()
            {
                @Override
                public boolean onPreferenceClick(Preference preference)
                {
                    Intent i = new Intent(SettingsFragment.this.getActivity(), Media3PlayerSettingsActivity.class);
                    startActivity(i);

                    return true;
                }
            });

            Preference ijkplayerSettingsPref = this.findPreference("ijkplayer_settings");

            ijkplayerSettingsPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener()
            {
                @Override
                public boolean onPreferenceClick(Preference preference)
                {
                    Intent i = new Intent(SettingsFragment.this.getActivity(), IJKPlayerSettingsActivity.class);
                    startActivity(i);

                    return true;
                }
            });

            Preference gsyplayerSettingsPref = this.findPreference("gsyplayer_settings");

            gsyplayerSettingsPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener()
            {
                @Override
                public boolean onPreferenceClick(Preference preference)
                {
                    Intent i = new Intent(SettingsFragment.this.getActivity(), GSYPlayerSettingsActivity.class);
                    startActivity(i);

                    return true;
                }
            });

            final Preference memCache = findPreference(Keys.image_cache_size_mb);
            memCache.setOnPreferenceChangeListener(new OnPreferenceChangeListener()
            {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue)
                {
                    if (MiniclientApplication.get(getActivity()).getClient().getImageCache() != null)
                    {
                        MiniclientApplication.get(getActivity()).getClient().getImageCache().reloadSettings();
                    }
                    return true;
                }
            });
            
            final Preference diskCache = findPreference(Keys.disk_image_cache_size_mb);
            diskCache.setOnPreferenceChangeListener(new OnPreferenceChangeListener()
            {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue)
                {
                    if (MiniclientApplication.get(getActivity()).getClient().getImageCache() != null)
                    {
                        MiniclientApplication.get(getActivity()).getClient().getImageCache().reloadSettings();
                    }
                    return true;
                }
            });
            
            
            updateListSummary(defaultPlayer, R.string.summary_list_default_player,
                    prefs.getString(Keys.default_player, "exoplayer"));
            updateListSummary(decodingMethod, R.string.summary_list_decoding_method,
                    prefs.getString(Keys.decoding_method, "hardware"));
            updateListSummary(loglevel, R.string.summary_list_loglevels_preference,
                    prefs.getString(Keys.log_level, "debug"));
            updateListSummary(streammode, R.string.summary_list_streaming_mode_preference, prefs.getStreamingMode());
            
            final Preference version = findPreference("version");
            //version.setSummary(Version.VERSION);
            version.setSummary(MiniclientApplication.get(this.getActivity()).getVersionName());

            final Preference versionCode = findPreference("versionCode");
            versionCode.setSummary(MiniclientApplication.get(this.getActivity()).getVersionCode() + "");

            final Preference ipaddress = findPreference("ipaddress");
            ipaddress.setSummary(NetUtil.getIPAddress(true));
            
            Dimension size = getMaxScreenSize();
            final Preference screensize = findPreference("screensize");
            screensize.setSummary(size.getWidth() + "x" + size.getHeight());
            
            final Preference appmemory = findPreference("appmemory");
            appmemory.setSummary(Utils.toMB(Runtime.getRuntime().maxMemory()) + "mb");



            final Preference clientid = (Preference) findPreference(Keys.client_id);

            final ClientIDGenerator gen = new ClientIDGenerator();

            if (prefs.getString(Keys.client_id) == null)
            {
                prefs.setString(Keys.client_id, gen.generateId());
            }

            updateClientIDSummary(clientid, prefs.getString(Keys.client_id), gen);
            clientid.setOnPreferenceChangeListener(new OnPreferenceChangeListener()
            {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue)
                {
                    if (newValue == null) return false;
                    String val = (String) newValue;
                    if (val.trim().length() == 0) return false;
                    if (val.indexOf(':') < 0)
                    {
                        val = gen.generateId(val);
                        prefs.setString(Keys.client_id, val);
                        updateClientIDSummary(preference, val, gen);
                        return false;
                    }
                    updateClientIDSummary(preference, val, gen);
                    return true;
                }
            });


        }
        catch (Throwable t)
        {
            t.printStackTrace();
        }
    }
    
    void updateClientIDSummary(Preference clientid, String value, ClientIDGenerator gen)
    {
        clientid.setSummary(value + " (" + gen.id2string(value) + ")");
    }


    
    private void updateListSummary(ListPreference pref, int resId, Object value)
    {
        if (pref == null) return;
        String stringValue = String.valueOf(value);
        int index = pref.findIndexOfValue(stringValue);
        CharSequence displayValue = index >= 0 ? pref.getEntries()[index] : stringValue;
        pref.setSummary(getResources().getString(resId, displayValue));
    }

    private void refreshPlayerPreferenceSummaries()
    {
        if (prefs == null) return;
        updateListSummary((ListPreference) findPreference(Keys.default_player),
                R.string.summary_list_default_player,
                prefs.getString(Keys.default_player, "exoplayer"));
        updateListSummary((ListPreference) findPreference(Keys.decoding_method),
                R.string.summary_list_decoding_method,
                prefs.getString(Keys.decoding_method, "hardware"));
        updateListSummary((ListPreference) findPreference(AndroidPrefStore.STREAMING_MODE),
                R.string.summary_list_streaming_mode_preference, prefs.getStreamingMode());
    }

    @Override
    public void onResume()
    {
        super.onResume();
        refreshPlayerPreferenceSummaries();
    }
    
    public Dimension getMaxScreenSize()
    {
        WindowManager wm = (WindowManager) MiniclientApplication.get().getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        return new Dimension(size.x, size.y);
    }
    
    private void shareLog()
    {
        Intent intentShareFile = new Intent(Intent.ACTION_SEND);
        File logDir = AppUtil.getLogDir();
        
        File[] files = logDir.listFiles(new FilenameFilter()
        {
            @Override
            public boolean accept(File dir, String name)
            {
                return (name.endsWith(".txt"));
            }
        });
        
        if (files == null || files.length == 0)
        {
            Log.i("MINICLIENT_LOG", "No Files to share in " + logDir.getAbsolutePath());
            return;
        }
        
        File fileToShare = files[0];
        Log.i("MINICLIENT_LOG", "Sharing " + fileToShare.getAbsolutePath());
        
        if (fileToShare.exists())
        {
            intentShareFile.setType("application/text");
            intentShareFile.putExtra(Intent.EXTRA_STREAM, Uri.parse(fileToShare.toURI().toString()));
            
            intentShareFile.putExtra(Intent.EXTRA_SUBJECT,
                    "Sharing MiniClient Log File...");
            intentShareFile.putExtra(Intent.EXTRA_TEXT, "Sharing MiniClient Log File...");
            
            startActivity(Intent.createChooser(intentShareFile, "Share MiniClient Log File"));
        }
    }
    
    void clearAllPreferences()
    {
        PreferenceManager.getDefaultSharedPreferences(getActivity()).edit().clear().apply();
        Toast.makeText(getActivity(), "Preferences have been reset to defaults", Toast.LENGTH_LONG).show();
    }
}
