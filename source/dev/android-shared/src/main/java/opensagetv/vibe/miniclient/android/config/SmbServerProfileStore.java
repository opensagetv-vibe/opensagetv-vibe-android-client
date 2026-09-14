package opensagetv.vibe.miniclient.android.config;

import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Device-local profile store. The credential-bearing blob is excluded from exported profiles. */
public final class SmbServerProfileStore
{
    public static final String STORAGE_KEY = "smb_servers/credentials";
    public static final String SELECTED_KEY = "smb_servers/selected";

    private final SharedPreferences preferences;

    public SmbServerProfileStore(SharedPreferences preferences)
    {
        if (preferences == null) throw new IllegalArgumentException("preferences are required");
        this.preferences = preferences;
    }

    public List<SmbServerProfile> list()
    {
        ArrayList<SmbServerProfile> result = new ArrayList<SmbServerProfile>();
        String encoded = preferences.getString(STORAGE_KEY, "");
        if (encoded == null || encoded.trim().isEmpty()) return result;
        try
        {
            JSONArray profiles = new JSONArray(encoded);
            for (int index = 0; index < profiles.length(); index++)
            {
                try { result.add(read(profiles.getJSONObject(index))); }
                catch (Exception ignored) { }
            }
        }
        catch (Exception ignored) { }
        Collections.sort(result, new Comparator<SmbServerProfile>()
        {
            @Override public int compare(SmbServerProfile left, SmbServerProfile right)
            { return left.getName().compareToIgnoreCase(right.getName()); }
        });
        return result;
    }

    public SmbServerProfile find(String id)
    {
        if (id == null || id.trim().isEmpty()) return null;
        SmbServerProfile found = null;
        for (SmbServerProfile profile : list())
        {
            if (found == null && id.equals(profile.getId())) found = profile;
            else profile.clear();
        }
        return found;
    }

    public SmbServerProfile save(String id, String name, String host, int port, String share,
                                 boolean anonymous, String username, char[] password, String domain)
    {
        String targetId = id == null || id.trim().isEmpty()
                ? "server-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12)
                : id.trim();
        SmbServerProfile replacement = new SmbServerProfile(targetId, name, host, port, share,
                anonymous, username, password, domain);
        List<SmbServerProfile> profiles = list();
        for (SmbServerProfile profile : profiles)
        {
            if (!targetId.equals(profile.getId())
                    && replacement.getHost().equalsIgnoreCase(profile.getHost())
                    && replacement.getPort() == profile.getPort()
                    && replacement.getShare().equalsIgnoreCase(profile.getShare()))
            {
                clearExcept(profiles, null);
                replacement.clear();
                throw new IllegalArgumentException("That SMB server and share is already configured");
            }
        }
        ArrayList<SmbServerProfile> updated = new ArrayList<SmbServerProfile>();
        boolean replaced = false;
        for (SmbServerProfile profile : profiles)
        {
            if (targetId.equals(profile.getId()))
            {
                updated.add(replacement);
                profile.clear();
                replaced = true;
            }
            else updated.add(profile);
        }
        if (!replaced) updated.add(replacement);
        persist(updated);
        clearExcept(updated, replacement);
        preferences.edit().putString(SELECTED_KEY, replacement.getId()).apply();
        return replacement;
    }

    public void delete(String id)
    {
        ArrayList<SmbServerProfile> updated = new ArrayList<SmbServerProfile>();
        for (SmbServerProfile profile : list())
        {
            if (profile.getId().equals(id)) profile.clear();
            else updated.add(profile);
        }
        persist(updated);
        if (id != null && id.equals(preferences.getString(SELECTED_KEY, "")))
            preferences.edit().remove(SELECTED_KEY).apply();
        clearExcept(updated, null);
    }

    private void persist(List<SmbServerProfile> profiles)
    {
        JSONArray encoded = new JSONArray();
        try
        {
            for (SmbServerProfile profile : profiles)
            {
                char[] password = profile.copyPassword();
                try
                {
                    JSONObject item = new JSONObject();
                    item.put("id", profile.getId());
                    item.put("name", profile.getName());
                    item.put("host", profile.getHost());
                    item.put("port", profile.getPort());
                    item.put("share", profile.getShare());
                    item.put("anonymous", profile.isAnonymous());
                    item.put("username", profile.getUsername());
                    item.put("password", new String(password));
                    item.put("domain", profile.getDomain());
                    encoded.put(item);
                }
                finally { java.util.Arrays.fill(password, '\0'); }
            }
        }
        catch (Exception e) { throw new IllegalStateException("Unable to encode SMB server profiles", e); }
        if (!preferences.edit().putString(STORAGE_KEY, encoded.toString()).commit())
            throw new IllegalStateException("Unable to save SMB server profiles");
    }

    private static SmbServerProfile read(JSONObject item) throws Exception
    {
        char[] password = item.optString("password", "").toCharArray();
        try
        {
            return new SmbServerProfile(item.getString("id"), item.getString("name"),
                    item.getString("host"), item.optInt("port", 445), item.getString("share"),
                    item.optBoolean("anonymous", true), item.optString("username", ""),
                    password, item.optString("domain", ""));
        }
        finally { java.util.Arrays.fill(password, '\0'); }
    }

    private static void clearExcept(List<SmbServerProfile> profiles, SmbServerProfile retained)
    {
        for (SmbServerProfile profile : profiles) if (profile != retained) profile.clear();
    }
}
