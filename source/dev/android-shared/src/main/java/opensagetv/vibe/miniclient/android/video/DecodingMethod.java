package opensagetv.vibe.miniclient.android.video;

/**
 * Shared video-decoder policy used by every Android player backend.
 *
 * Preference values are stable because SageTV client profiles persist them.
 */
public enum DecodingMethod
{
    HARDWARE("hardware", "Hardware"),
    SOFTWARE("software", "Software"),
    HARDWARE_PREFERRED("hardware_preferred", "Fallback");

    public static final String DEFAULT_PREFERENCE = "hardware";

    private final String preferenceValue;
    private final String displayName;

    DecodingMethod(String preferenceValue, String displayName)
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

    public boolean allowHardware()
    {
        return this != SOFTWARE;
    }

    public boolean allowSoftware()
    {
        return this != HARDWARE;
    }

    public boolean hardwarePreferred()
    {
        return this == HARDWARE_PREFERRED;
    }

    public static DecodingMethod fromPreference(String value)
    {
        if (value != null)
        {
            for (DecodingMethod method : values())
            {
                if (method.preferenceValue.equalsIgnoreCase(value))
                {
                    return method;
                }
            }
        }
        return HARDWARE;
    }
}
