package opensagetv.vibe.miniclient.android.video.smb;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import opensagetv.vibe.miniclient.android.config.SmbServerProfileStore;
import opensagetv.vibe.miniclient.android.prefs.AndroidPrefStore;
import opensagetv.vibe.miniclient.net.SmbMappedPath;
import opensagetv.vibe.miniclient.prefs.PrefStore;

/** Immutable SMB Direct settings snapshot captured when a player is created. */
public final class SmbDirectConfig
{
    private final String mappings;
    private final String username;
    private final char[] password;
    private final String domain;
    private final List<CredentialRule> profileCredentials;
    private final boolean autoFallback;
    private final int readAheadBytes;

    private SmbDirectConfig(String mappings, String username, char[] password,
                            String domain, List<CredentialRule> profileCredentials,
                            boolean autoFallback, int readAheadBytes)
    {
        this.mappings = mappings == null ? "" : mappings;
        this.username = username == null ? "" : username;
        this.password = password == null ? new char[0] : password.clone();
        this.domain = domain == null ? "" : domain;
        this.profileCredentials = profileCredentials == null
                ? Collections.<CredentialRule>emptyList() : profileCredentials;
        this.autoFallback = autoFallback;
        this.readAheadBytes = Math.max(32 * 1024, Math.min(8 * 1024 * 1024, readAheadBytes));
    }

    public static SmbDirectConfig from(PrefStore prefs)
    {
        String mode = prefs.getStreamingMode();
        char[] password = prefs.getString(AndroidPrefStore.SMB_PASSWORD, "").toCharArray();
        try
        {
            return new SmbDirectConfig(
                    prefs.getString(AndroidPrefStore.SMB_MAPPINGS, ""),
                    prefs.getString(AndroidPrefStore.SMB_USERNAME, ""), password,
                    prefs.getString(AndroidPrefStore.SMB_DOMAIN, ""),
                    parseProfileCredentials(prefs.getString(SmbServerProfileStore.STORAGE_KEY, "")),
                    AndroidPrefStore.STREAMING_MODE_SMB_AUTO.equalsIgnoreCase(mode),
                    parseReadAheadBytes(prefs.getString(AndroidPrefStore.SMB_READ_AHEAD_KB, "256")));
        }
        finally { java.util.Arrays.fill(password, '\0'); }
    }

    public String getMappings() { return mappings; }
    public String getUsername() { return username; }
    public char[] copyPassword() { return password.clone(); }
    public String getDomain() { return domain; }
    public boolean isAutoFallback() { return autoFallback; }
    public int getReadAheadBytes() { return readAheadBytes; }

    Credentials credentialsFor(SmbMappedPath mapped)
    {
        if (mapped != null)
            for (CredentialRule rule : profileCredentials)
                if (rule.matches(mapped)) return rule.copy();
        return new Credentials(username, password, domain);
    }

    private static int parseReadAheadBytes(String configured)
    {
        try { return Integer.parseInt(configured.trim()) * 1024; }
        catch (Exception ignored) { return 256 * 1024; }
    }

    public void clear()
    {
        java.util.Arrays.fill(password, '\0');
        for (CredentialRule rule : profileCredentials) rule.clear();
    }

    private static List<CredentialRule> parseProfileCredentials(String encoded)
    {
        ArrayList<CredentialRule> result = new ArrayList<CredentialRule>();
        if (encoded == null || encoded.trim().isEmpty()) return result;
        try
        {
            JSONArray profiles = new JSONArray(encoded);
            for (int index = 0; index < profiles.length(); index++)
            {
                JSONObject item = profiles.getJSONObject(index);
                boolean anonymous = item.optBoolean("anonymous", true);
                char[] password = anonymous ? new char[0]
                        : item.optString("password", "").toCharArray();
                try
                {
                    result.add(new CredentialRule(item.getString("host"),
                            item.optInt("port", 445), item.getString("share"),
                            anonymous ? "" : item.optString("username", ""), password,
                            anonymous ? "" : item.optString("domain", "")));
                }
                finally { java.util.Arrays.fill(password, '\0'); }
            }
        }
        catch (Exception ignored)
        {
            for (CredentialRule rule : result) rule.clear();
            result.clear();
        }
        return Collections.unmodifiableList(result);
    }

    static final class Credentials
    {
        final String username;
        final char[] password;
        final String domain;

        Credentials(String username, char[] password, String domain)
        {
            this.username = username == null ? "" : username;
            this.password = password == null ? new char[0] : password.clone();
            this.domain = domain == null ? "" : domain;
        }

        void clear() { java.util.Arrays.fill(password, '\0'); }
    }

    private static final class CredentialRule
    {
        final String host;
        final int port;
        final String share;
        final String username;
        final char[] password;
        final String domain;

        CredentialRule(String host, int port, String share, String username, char[] password, String domain)
        {
            this.host = host == null ? "" : host;
            this.port = port;
            this.share = share == null ? "" : share;
            this.username = username == null ? "" : username;
            this.password = password == null ? new char[0] : password.clone();
            this.domain = domain == null ? "" : domain;
        }

        boolean matches(SmbMappedPath mapped)
        {
            return port == mapped.getPort() && host.equalsIgnoreCase(mapped.getServer())
                    && share.equalsIgnoreCase(mapped.getShare());
        }

        Credentials copy() { return new Credentials(username, password, domain); }
        void clear() { java.util.Arrays.fill(password, '\0'); }
    }
}
