package sagex.miniclient.android.video;

/**
 * Stable preference identifiers for the selectable Android playback backends.
 *
 * Keep the preference values stable because SageTV client profiles may persist them
 * across application updates.
 */
public enum PlayerBackend
{
    EXOPLAYER("exoplayer", "ExoPlayer Legacy"),
    MEDIA3("media3", "Media3 ExoPlayer"),
    IJKPLAYER("ijkplayer", "IJKPlayer"),
    GSYPLAYER("gsyplayer", "GSYVideoPlayer");

    public static final String DEFAULT_PREFERENCE = "exoplayer";
    public static final String MEDIA3_VERSION = "1.11.0";
    public static final String GSY_VERSION = "13.1.0";

    private final String preferenceValue;
    private final String displayName;

    PlayerBackend(String preferenceValue, String displayName)
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

    /**
     * Legacy ExoPlayer and Media3 both rely primarily on Android MediaCodec capabilities.
     * Legacy IJK keeps its original native runtime. GSY is an independent engine selector.
     */
    public boolean usesPlatformCodecCapabilities()
    {
        return this == EXOPLAYER || this == MEDIA3;
    }

    /**
     * Only the frozen legacy ExoPlayer backend loads the local ExoPlayer FFmpeg extension.
     * Media3 intentionally starts with platform decoders only so it does not bind to a
     * version-mismatched legacy extension.
     */
    public boolean usesLegacyExoFfmpeg()
    {
        return this == EXOPLAYER;
    }

    public static PlayerBackend fromPreference(String value)
    {
        if (value != null)
        {
            for (PlayerBackend backend : values())
            {
                if (backend.preferenceValue.equalsIgnoreCase(value))
                {
                    return backend;
                }
            }
        }
        return EXOPLAYER;
    }

    /**
     * Deterministic order for the navigation drawer's one-time/permanent switch action.
     */
    public PlayerBackend next()
    {
        switch (this)
        {
            case EXOPLAYER:
                return MEDIA3;
            case MEDIA3:
                return IJKPLAYER;
            case IJKPLAYER:
                return GSYPLAYER;
            case GSYPLAYER:
            default:
                return EXOPLAYER;
        }
    }
}
