package opensagetv.vibe.miniclient.android.ui.settings;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.graphics.Point;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.core.content.FileProvider;
import androidx.preference.ListPreference;
import androidx.preference.CheckBoxPreference;
import androidx.preference.Preference;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;



import java.io.File;
import java.io.FilenameFilter;

import opensagetv.vibe.miniclient.android.AppUtil;
import opensagetv.vibe.miniclient.android.MiniclientApplication;
import opensagetv.vibe.miniclient.android.R;
import opensagetv.vibe.miniclient.android.prefs.AndroidPrefStore;
import opensagetv.vibe.miniclient.android.diagnostics.ServerDiagnosticExporter;
import opensagetv.vibe.miniclient.android.util.NetUtil;
import opensagetv.vibe.miniclient.prefs.PrefStore;
import opensagetv.vibe.miniclient.prefs.PrefStore.Keys;
import opensagetv.vibe.miniclient.uibridge.Dimension;
import opensagetv.vibe.miniclient.util.ClientIDGenerator;
import opensagetv.vibe.miniclient.util.Utils;


/**
 * Created by seans on 24/10/15.
 */
public class SettingsFragment extends PreferenceFragmentCompat
{
    PrefStore prefs;
    private String returnFocusKey;
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener serverDiagnosticListener =
            (shared, key) -> {
                if (AndroidPrefStore.SERVER_DIAGNOSTICS_ENABLED.equals(key)
                        || AndroidPrefStore.SERVER_DIAGNOSTICS_STATUS.equals(key)
                        || AndroidPrefStore.CLIENT_WEB_ENABLED.equals(key)
                        || AndroidPrefStore.CLIENT_WEB_STATUS.equals(key)) {
                    refreshServerDiagnosticPreferences();
                    refreshSections();
                }
            };


    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey)
    {




        AndroidPrefStore.migrateTransportSelection(PreferenceManager.getDefaultSharedPreferences(requireContext()));
        setPreferencesFromResource(R.xml.prefs, rootKey);
        PlaybackSettingPreferences.applyExisting(getPreferenceScreen());
        findPreference("playback_defaults_settings").setOnPreferenceClickListener(preference->{
            startActivity(new Intent(requireContext(),PlaybackDefaultsActivity.class));return true;
        });
        if(savedInstanceState!=null)returnFocusKey=savedInstanceState.getString("settings_return_focus");

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
            
            p = this.findPreference(Keys.keep_session_in_background);
            if (p != null)
            {
                p.setDefaultValue(false);
            }

            p = this.findPreference(Keys.resume_background_playback);
            if (p != null)
            {
                p.setDefaultValue(true);
            }

            Preference backgroundSettings = findPreference("background_session_settings");
            if (backgroundSettings != null)
            {
                backgroundSettings.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener()
                {
                    @Override public boolean onPreferenceClick(Preference preference)
                    {
                        startActivity(new Intent(SettingsFragment.this.getActivity(),
                                HomeBackgroundSettingsActivity.class));
                        return true;
                    }
                });
            }

            Preference diagnosticsSettings = findPreference("diagnostics_settings");
            Preference serverUpload = findPreference("server_diagnostics/upload_now");
            if (serverUpload != null) serverUpload.setOnPreferenceClickListener(preference -> {
                ServerDiagnosticExporter.uploadNow();
                Toast.makeText(requireContext(), "Diagnostic upload requested; see status on this page", Toast.LENGTH_SHORT).show();
                return true;
            });
            Preference serverPort = findPreference(AndroidPrefStore.SERVER_DIAGNOSTICS_PORT);
            if (serverPort != null) serverPort.setOnPreferenceChangeListener((preference, value) -> {
                try {
                    int port = Integer.parseInt(String.valueOf(value));
                    return port >= 1024 && port <= 65535;
                } catch (NumberFormatException error) { return false; }
            });
            Preference clientWebPort = findPreference(AndroidPrefStore.CLIENT_WEB_PORT);
            if (clientWebPort != null) clientWebPort.setOnPreferenceChangeListener((preference, value) -> {
                try {
                    int port = Integer.parseInt(String.valueOf(value));
                    return port >= 1024 && port <= 65535;
                } catch (NumberFormatException error) { return false; }
            });
            if (diagnosticsSettings != null)
            {
                diagnosticsSettings.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener()
                {
                    @Override public boolean onPreferenceClick(Preference preference)
                    {
                        startActivity(new Intent(SettingsFragment.this.getActivity(),
                                DiagnosticsSettingsActivity.class));
                        return true;
                    }
                });
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
    
            
            
            Preference smbProfiles = findPreference("smb_settings");
            if (smbProfiles != null)
            {
                smbProfiles.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener()
                {
                    @Override public boolean onPreferenceClick(Preference preference)
                    {
                        startActivity(new Intent(SettingsFragment.this.getActivity(), SmbProfileSettingsActivity.class));
                        return true;
                    }
                });
            }
            Preference playbackTrackSettings = findPreference("playback_track_settings");
            if (playbackTrackSettings != null)
            {
                playbackTrackSettings.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener()
                {
                    @Override public boolean onPreferenceClick(Preference preference)
                    {
                        startActivity(new Intent(SettingsFragment.this.getActivity(),
                                PlaybackTrackSettingsActivity.class));
                        return true;
                    }
                });
            }

            Preference discPlaybackSettings = findPreference("disc_playback_settings");
            if (discPlaybackSettings != null)
            {
                discPlaybackSettings.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener()
                {
                    @Override public boolean onPreferenceClick(Preference preference)
                    {
                        startActivity(new Intent(SettingsFragment.this.getActivity(),
                                DiscPlaybackSettingsActivity.class));
                        return true;
                    }
                });
            }
            final ListPreference defaultPlayer = (ListPreference) findPreference(Keys.default_player);
            final ListPreference decodingMethod = (ListPreference) findPreference(Keys.decoding_method);

            defaultPlayer.setOnPreferenceChangeListener(new OnPreferenceChangeListener()
            {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue)
                {
                    updateListSummary(defaultPlayer, R.string.summary_list_default_player, newValue);
                    refreshSections(String.valueOf(newValue), null);
                    return true;
                }
            });

            decodingMethod.setOnPreferenceChangeListener(new OnPreferenceChangeListener()
            {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue)
                {
                    updateListSummary(decodingMethod, R.string.summary_list_decoding_method, newValue);
                    getListView().post(() -> refreshSections());
                    return true;
                }
            });

            final Preference fixedTranscoding = this.findPreference("fixed_transcoding");

            final ListPreference streammode = (ListPreference) findPreference(AndroidPrefStore.STREAMING_MODE);
            final ListPreference pushBufferLimit = (ListPreference) findPreference(Keys.push_buffer_limit);
            fixedTranscoding.setEnabled(prefs.getStreamingMode().equals("fixed"));
            configureDeinterlacePreference(prefs.getStreamingMode());
            
            streammode.setOnPreferenceChangeListener(new OnPreferenceChangeListener()
            {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue)
                {
                    updateListSummary(streammode, R.string.summary_list_streaming_mode_preference, newValue);
                    configureDeinterlacePreference(String.valueOf(newValue));
                    refreshSections(null, String.valueOf(newValue));

                    return true;
                }
            });

            if (pushBufferLimit != null)
            {
                pushBufferLimit.setOnPreferenceChangeListener((preference, newValue) ->
                {
                    updateListSummary(pushBufferLimit,
                            R.string.push_buffer_limit_summary, newValue);
                    return true;
                });
            }
    
            
    
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

            findPreference("push_settings").setOnPreferenceClickListener(preference -> {
                startActivity(new Intent(requireContext(), PushSettingsActivity.class)); return true;
            });
            findPreference("selected_player_settings").setOnPreferenceClickListener(preference -> {
                opensagetv.vibe.miniclient.android.video.PlayerBackend backend =
                    opensagetv.vibe.miniclient.android.video.PlayerBackend.fromPreference(prefs.getString(Keys.default_player,"exoplayer"));
                Class<?> screen;
                switch (backend) {
                    case MEDIA3: screen=Media3PlayerSettingsActivity.class; break;
                    case IJKPLAYER: screen=IJKPlayerSettingsActivity.class; break;
                    case GSYPLAYER: screen=GSYPlayerSettingsActivity.class; break;
                    default: screen=ExoPlayerSettingsActivity.class;
                }
                startActivity(new Intent(requireContext(),screen)); return true;
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
            updateListSummary(streammode, R.string.summary_list_streaming_mode_preference, prefs.getString(AndroidPrefStore.STREAMING_MODE,AndroidPrefStore.STREAMING_MODE_DEFAULT));
            
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
                R.string.summary_list_streaming_mode_preference, prefs.getString(AndroidPrefStore.STREAMING_MODE,AndroidPrefStore.STREAMING_MODE_DEFAULT));
        updateListSummary((ListPreference) findPreference(Keys.push_buffer_limit),
                R.string.push_buffer_limit_summary,
                prefs.getString(Keys.push_buffer_limit, "auto"));
    }

    @Override
    public void onResume()
    {
        super.onResume();
        refreshPlayerPreferenceSummaries();
        PreferenceManager.getDefaultSharedPreferences(requireContext())
                .registerOnSharedPreferenceChangeListener(serverDiagnosticListener);
        refreshServerDiagnosticPreferences();
        refreshSections();
        if(returnFocusKey!=null)getListView().post(()->{
            androidx.recyclerview.widget.RecyclerView.Adapter<?> adapter=getListView().getAdapter();
            if(!(adapter instanceof androidx.preference.PreferenceGroupAdapter))return;
            int position=((androidx.preference.PreferenceGroupAdapter)adapter).getPreferenceAdapterPosition(returnFocusKey);
            if(position<0)return;
            getListView().scrollToPosition(position);
            getListView().post(()->{
                androidx.recyclerview.widget.RecyclerView.ViewHolder holder=getListView().findViewHolderForAdapterPosition(position);
                if(holder!=null)holder.itemView.requestFocus();
            });
        });
    }

    @Override public void onPause() {
        android.view.View focused=getListView().findFocus();
        androidx.recyclerview.widget.RecyclerView.ViewHolder holder=focused==null?null:getListView().findContainingViewHolder(focused);
        if(holder!=null&&getListView().getAdapter() instanceof androidx.preference.PreferenceGroupAdapter){
            int position=holder.getBindingAdapterPosition();
            if(position>=0){Preference item=((androidx.preference.PreferenceGroupAdapter)getListView().getAdapter()).getItem(position);if(item!=null)returnFocusKey=item.getKey();}
        }
        PreferenceManager.getDefaultSharedPreferences(requireContext())
                .unregisterOnSharedPreferenceChangeListener(serverDiagnosticListener);
        super.onPause();
    }

    @Override public void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putString("settings_return_focus",returnFocusKey);}

    private void refreshServerDiagnosticPreferences() {
        if (!isAdded()) return;
        android.content.SharedPreferences current = PreferenceManager.getDefaultSharedPreferences(requireContext());
        CheckBoxPreference webToggle = findPreference(AndroidPrefStore.CLIENT_WEB_ENABLED);
        if (webToggle != null) webToggle.setChecked(current.getBoolean(AndroidPrefStore.CLIENT_WEB_ENABLED, false));
        CheckBoxPreference serverToggle = findPreference(AndroidPrefStore.SERVER_DIAGNOSTICS_ENABLED);
        if (serverToggle != null) serverToggle.setChecked(current.getBoolean(AndroidPrefStore.SERVER_DIAGNOSTICS_ENABLED, false));
        Preference webStatus = findPreference(AndroidPrefStore.CLIENT_WEB_STATUS);
        if (webStatus != null) webStatus.setSummary(PreferenceManager.getDefaultSharedPreferences(requireContext())
                .getString(AndroidPrefStore.CLIENT_WEB_STATUS, "Off"));
        Preference upload = findPreference("server_diagnostics/upload_now");
        if (upload != null) {
            android.content.SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(requireContext());
            upload.setEnabled(shared.getBoolean(AndroidPrefStore.SERVER_DIAGNOSTICS_ENABLED, false));
            upload.setSummary(shared.getString(AndroidPrefStore.SERVER_DIAGNOSTICS_STATUS,
                    "No upload yet. Enable server receiver and client uploads first."));
        }
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
        Context context = getActivity();
        if (context == null)
        {
            Log.w("MINICLIENT_LOG", "Cannot share log from a detached settings fragment");
            return;
        }

        Intent intentShareFile = new Intent(Intent.ACTION_SEND);
        File logDir = AppUtil.getLogDir(context);
        
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
        for (int i = 1; i < files.length; i++)
        {
            if (files[i].lastModified() > fileToShare.lastModified())
                fileToShare = files[i];
        }
        Log.i("MINICLIENT_LOG", "Sharing " + fileToShare.getAbsolutePath());
        
        if (fileToShare.exists())
        {
            intentShareFile.setType("text/plain");
            Uri logUri = FileProvider.getUriForFile(
                    context,
                    context.getPackageName() + ".fileprovider",
                    fileToShare);
            intentShareFile.putExtra(Intent.EXTRA_STREAM, logUri);
            intentShareFile.setClipData(ClipData.newRawUri("MiniClient log", logUri));
            intentShareFile.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            
            intentShareFile.putExtra(Intent.EXTRA_SUBJECT,
                    "Sharing MiniClient Log File...");
            intentShareFile.putExtra(Intent.EXTRA_TEXT, "Sharing MiniClient Log File...");
            
            startActivity(Intent.createChooser(intentShareFile, "Share MiniClient Log File"));
        }
    }
    
    private void configureDeinterlacePreference(String streaming)
    {
        final ListPreference preference=findPreference(Keys.video_deinterlace_mode);
        if(preference==null)return;
        CharSequence[] labels=getResources().getTextArray(R.array.entries_video_deinterlace_mode);
        CharSequence[] values=getResources().getTextArray(R.array.entryvalues_video_deinterlace_mode);
        final boolean fixed="fixed".equals(streaming);
        if(fixed) {
            labels=java.util.Arrays.copyOf(labels,labels.length+1);
            values=java.util.Arrays.copyOf(values,values.length+1);
            labels[labels.length-1]="MIM";values[values.length-1]="mim";
        }
        preference.setEntries(labels);preference.setEntryValues(values);
        preference.setSummaryProvider(item -> {
            ListPreference choice=(ListPreference)item;
            String saved=choice.getValue();
            CharSequence entry=choice.getEntry();
            return ("mim".equals(saved) && !fixed ? "Auto (MIM requires Fixed)"
                    : entry==null ? "Auto" : entry);
        });
    }

    private void refreshSections() { refreshSections(null, null); }

    private void refreshSections(String playerOverride, String transportOverride) {
        if (prefs == null) return;
        String player=playerOverride == null ? prefs.getString(Keys.default_player,"exoplayer") : playerOverride;
        String transport=transportOverride == null ? prefs.getStreamingMode() : transportOverride;
        String playerName=opensagetv.vibe.miniclient.android.video.PlayerBackend.fromPreference(player).displayName();
        Preference selected=findPreference("selected_player_settings");
        if (selected != null) selected.setSummary(playerName + " settings and information");
        for (int i=0;i<getPreferenceScreen().getPreferenceCount();i++) {
            Preference item=getPreferenceScreen().getPreference(i);
            if (item instanceof CollapsiblePreferenceCategory) ((CollapsiblePreferenceCategory)item).restoreExpansion();
        }
        setSectionSummary("playback", playerName+" · "+entryLabel(Keys.decoding_method,prefs.getString(Keys.decoding_method,"hardware")));
        String configured=entryLabel(AndroidPrefStore.STREAMING_MODE,transport);
        if ("fixed".equals(transport)) {
            String processing=prefs.getString(Keys.mim_direct_mode,"off");
            String method=AndroidPrefStore.adjustmentMethod(PreferenceManager.getDefaultSharedPreferences(requireContext()));
            configured+=" · "+("hls".equals(processing)?"HLS":"transcode".equals(processing)?"Legacy":"copy".equals(processing)?"Copy":"Stock")
                +" · "+("in_place".equals(method)?"In-place preferred":"switch".equals(method)?"Switching":"Adjustment Off");
        }
        opensagetv.vibe.miniclient.MiniClientConnection connection=MiniclientApplication.get().getClient().getCurrentConnection();
        String actual="Not connected";
        if (connection != null) {
            java.util.Map<String,Object> snapshot=opensagetv.vibe.miniclient.android.ClientDiagnosticSnapshot.capture(requireActivity(),MiniclientApplication.get().getClient(),connection);
            Object value=snapshot.get("playback");
            if (value instanceof java.util.Map && Boolean.TRUE.equals(((java.util.Map<?,?>)value).get("active")))
                actual="Active: "+String.valueOf(((java.util.Map<?,?>)value).get("transport"));
            else actual="Connected · no active playback";
        }
        setSectionSummary("transport","Configured: "+configured+" · "+actual);
        setSectionSummary("diagnostics","Web server "+(prefs.getBoolean(AndroidPrefStore.CLIENT_WEB_ENABLED,false)?"On":"Off")
                +" · Server upload "+(prefs.getBoolean(AndroidPrefStore.SERVER_DIAGNOSTICS_ENABLED,false)?"On":"Off"));
        setSectionSummary("about",MiniclientApplication.get().getVersionName());
        Preference fixed=findPreference("fixed_transcoding");
        Preference buffering=findPreference(Keys.playback_buffer_preset);
        String bufferReason=PlaybackSettingCatalog.defaultUnavailable(PlaybackSettingCatalog.BUFFER,player);
        if(buffering!=null){buffering.setEnabled(bufferReason==null);
            buffering.setSummary(bufferReason==null?PlaybackSettingCatalog.BUFFER.display(requireContext(),prefs.getString(Keys.playback_buffer_preset,"balanced"))+" · Pull / SMB only":bufferReason);}
        fixed.setEnabled("fixed".equals(transport));
        fixed.setSummary("fixed".equals(transport)?"Processing, bitrate adjustment and stock remux options":"Select Fixed as Transport Method to configure this route");
        Preference push=findPreference("push_settings");
        push.setEnabled("dynamic".equals(transport));
        push.setSummary("dynamic".equals(transport)?"Legacy Push write capacity":"Select Push as Transport Method to configure this route");
    }
    private void setSectionSummary(String key,String value) {
        Preference section=findPreference("settings/section/"+key); if(section!=null)section.setSummary(value);
    }
    private String entryLabel(String key,String value) {
        ListPreference choice=findPreference(key); if(choice==null)return value;
        int index=choice.findIndexOfValue(value);return index<0?value:choice.getEntries()[index].toString();
    }
    private String entryLabelFromArray(int labels,int values,String value) {
        String[] options=getResources().getStringArray(values), names=getResources().getStringArray(labels);
        for(int i=0;i<options.length;i++)if(options[i].equals(value))return names[i];return value;
    }

    void clearAllPreferences()
    {
        androidx.appcompat.app.AlertDialog dialog=new androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Reset settings to defaults?")
            .setMessage("Restore displayed playback, transport, mapping, general and diagnostic options. Server entries, credentials, client ID, SMB mappings/profiles, files and authorization keys are preserved.")
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Reset",(unused,which)->{
                SettingsDefaults.restore(requireContext(),PreferenceManager.getDefaultSharedPreferences(requireContext()));
                Toast.makeText(requireContext(),"Settings restored; connections and credentials preserved",Toast.LENGTH_LONG).show();
                requireActivity().recreate();
            }).create();
        dialog.setOnShowListener(unused->{
            dialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE).setFocusableInTouchMode(true);
            dialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE).requestFocus();
        });
        dialog.show();
    }
}
