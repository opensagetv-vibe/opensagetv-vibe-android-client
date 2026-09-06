package opensagetv.vibe.miniclient.android.tv.debug;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import java.util.Arrays;
import java.util.List;

import opensagetv.vibe.miniclient.android.config.MiniClientProfilePreferences;
import opensagetv.vibe.miniclient.android.config.SmbProfileRepository;
import opensagetv.vibe.miniclient.android.prefs.AndroidPrefStore;
import opensagetv.vibe.miniclient.android.ui.settings.SmbProfileSettingsActivity;
import opensagetv.vibe.miniclient.config.MiniClientProfile;

/** Debug-only physical SMB profile repository operations for MCP acceptance. */
final class DebugProfileCommands
{
    private DebugProfileCommands() { }

    static String openUi(Context context)
    {
        Intent activity = new Intent(context, SmbProfileSettingsActivity.class);
        activity.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(activity);
        return "op=profile_ui;launched=true";
    }

    static void run(BroadcastReceiver receiver, Context context, Intent intent)
    {
        final BroadcastReceiver.PendingResult pending = receiver.goAsync();
        final Context app = context.getApplicationContext();
        final String op = DebugValueParser.clean(intent.getStringExtra("op"));
        final String name = DebugValueParser.clean(intent.getStringExtra("name"));
        final boolean overwrite = DebugValueParser.parseBoolean(intent.getStringExtra("overwrite"), false);
        DebugAsyncExecutor.execute(new Runnable()
        {
            @Override public void run()
            {
                SmbProfileRepository repository = null;
                try
                {
                    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(app);
                    char[] password = preferences.getString(AndroidPrefStore.SMB_PROFILE_PASSWORD, "").toCharArray();
                    try
                    {
                        repository = new SmbProfileRepository(
                                preferences.getString(AndroidPrefStore.SMB_PROFILE_DIRECTORY, ""),
                                preferences.getString(AndroidPrefStore.SMB_PROFILE_USERNAME, ""),
                                password,
                                preferences.getString(AndroidPrefStore.SMB_PROFILE_DOMAIN, ""));
                    }
                    finally { Arrays.fill(password, '\0'); }

                    String data;
                    if ("profile_list".equals(op))
                    {
                        List<String> profiles = repository.list();
                        data = "op=profile_list;count=" + profiles.size()
                                + ";profiles=" + DebugValueParser.safe(join(profiles));
                    }
                    else if ("profile_save".equals(op))
                    {
                        MiniClientProfile profile = MiniClientProfilePreferences.snapshot(preferences, name);
                        repository.save(profile, overwrite);
                        data = "op=profile_save;name=" + DebugValueParser.safe(MiniClientProfile.fileName(name))
                                + ";overwrite=" + overwrite
                                + ";settingsCount=" + profile.getSettings().size()
                                + ";clientIdCount=" + profile.getClientIds().size();
                    }
                    else if ("profile_load".equals(op))
                    {
                        MiniClientProfile profile = repository.load(name);
                        data = "op=profile_load;name=" + DebugValueParser.safe(MiniClientProfile.fileName(profile.getName()))
                                + ";schema=" + MiniClientProfile.SCHEMA_VERSION
                                + ";settingsCount=" + profile.getSettings().size()
                                + ";clientIdCount=" + profile.getClientIds().size()
                                + ";credentialsPresent=false";
                    }
                    else if ("profile_delete".equals(op))
                    {
                        repository.delete(name);
                        data = "op=profile_delete;name=" + DebugValueParser.safe(MiniClientProfile.fileName(name));
                    }
                    else throw new IllegalArgumentException("unsupported profile operation");

                    pending.setResultCode(1);
                    pending.setResultData(DebugResponseFormatter.success(data));
                }
                catch (Throwable t)
                {
                    pending.setResultCode(-1);
                    pending.setResultData(DebugResponseFormatter.failure(
                            "exception=" + DebugValueParser.safe(t.getClass().getSimpleName())
                                    + ";message=" + DebugValueParser.safe(t.getMessage())));
                }
                finally
                {
                    if (repository != null) repository.clear();
                    pending.finish();
                }
            }
        });
    }

    private static String join(List<String> values)
    {
        StringBuilder out = new StringBuilder();
        for (String value : values)
        {
            if (out.length() > 0) out.append('|');
            out.append(value);
        }
        return out.toString();
    }
}
