package opensagetv.vibe.miniclient.android.video.smb;

import opensagetv.vibe.miniclient.android.prefs.AndroidPrefStore;
import opensagetv.vibe.miniclient.prefs.PrefStore;

/** Immutable SMB Direct settings snapshot captured when a player is created. */
public final class SmbDirectConfig
{
    private final String mappings;
    private final String username;
    private final char[] password;
    private final String domain;
    private final boolean autoFallback;
    private final int readAheadBytes;

    private SmbDirectConfig(String mappings, String username, char[] password,
                            String domain, boolean autoFallback, int readAheadBytes)
    {
        this.mappings = mappings == null ? "" : mappings;
        this.username = username == null ? "" : username;
        this.password = password == null ? new char[0] : password.clone();
        this.domain = domain == null ? "" : domain;
        this.autoFallback = autoFallback;
        this.readAheadBytes = Math.max(32 * 1024, Math.min(8 * 1024 * 1024, readAheadBytes));
    }

    public static SmbDirectConfig from(PrefStore prefs)
    {
        String mode = prefs.getStreamingMode();
        return new SmbDirectConfig(
                prefs.getString(AndroidPrefStore.SMB_MAPPINGS, ""),
                prefs.getString(AndroidPrefStore.SMB_USERNAME, ""),
                prefs.getString(AndroidPrefStore.SMB_PASSWORD, "").toCharArray(),
                prefs.getString(AndroidPrefStore.SMB_DOMAIN, ""),
                AndroidPrefStore.STREAMING_MODE_SMB_AUTO.equalsIgnoreCase(mode),
                parseReadAheadBytes(prefs.getString(AndroidPrefStore.SMB_READ_AHEAD_KB, "256")));
    }

    public String getMappings() { return mappings; }
    public String getUsername() { return username; }
    public char[] copyPassword() { return password.clone(); }
    public String getDomain() { return domain; }
    public boolean isAutoFallback() { return autoFallback; }
    public int getReadAheadBytes() { return readAheadBytes; }

    private static int parseReadAheadBytes(String configured)
    {
        try { return Integer.parseInt(configured.trim()) * 1024; }
        catch (Exception ignored) { return 256 * 1024; }
    }

    public void clear()
    {
        java.util.Arrays.fill(password, '\0');
    }
}
