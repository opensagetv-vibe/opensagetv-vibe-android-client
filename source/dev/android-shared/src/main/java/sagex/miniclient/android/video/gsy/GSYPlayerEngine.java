package sagex.miniclient.android.video.gsy;

/**
 * Underlying engine selected from the GSYVideoPlayer settings page.
 *
 * Legacy IJK intentionally is NOT an option here.  It is a separate top-level
 * SageTV backend with its original 0.8.8 Java/JNI runtime.
 */
public enum GSYPlayerEngine
{
    AUTO("auto", "Auto"),
    MEDIA3("media3", "Media3 / ExoPlayer"),
    SYSTEM("system", "Android System MediaPlayer (Media3 fallback)"),
    LEGACY_EXO("legacy_exo", "Legacy ExoPlayer");

    public static final String DEFAULT_PREFERENCE = "auto";

    private final String preferenceValue;
    private final String displayName;

    GSYPlayerEngine(String preferenceValue, String displayName)
    {
        this.preferenceValue = preferenceValue;
        this.displayName = displayName;
    }

    public String preferenceValue()
    {
        return preferenceValue;
    }

    public String displayName()
    {
        return displayName;
    }

    public static GSYPlayerEngine fromPreference(String value)
    {
        if (value != null)
        {
            for (GSYPlayerEngine engine : values())
            {
                if (engine.preferenceValue.equalsIgnoreCase(value))
                {
                    return engine;
                }
            }
        }
        return AUTO;
    }
}
