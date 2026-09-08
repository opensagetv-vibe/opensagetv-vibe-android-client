package opensagetv.vibe.miniclient.video;

/** Immutable SageTV media hints retained for player policy decisions. */
public final class PlaybackMediaContext
{
    private byte majorTypeHint;
    private byte minorTypeHint;
    private String encodingHint = "";
    private boolean timeshifted;
    private long bufferSize;
    private boolean metadataExplicit;

    public void update(byte majorTypeHint, byte minorTypeHint, String encodingHint,
                       boolean timeshifted, long bufferSize)
    {
        this.majorTypeHint = majorTypeHint;
        this.minorTypeHint = minorTypeHint;
        this.encodingHint = encodingHint == null ? "" : encodingHint;
        this.timeshifted = timeshifted;
        this.bufferSize = bufferSize;
    }

    public byte getMajorTypeHint()
    {
        return majorTypeHint;
    }

    public byte getMinorTypeHint()
    {
        return minorTypeHint;
    }

    public String getEncodingHint()
    {
        return encodingHint;
    }

    public boolean isTimeshifted()
    {
        return timeshifted;
    }

    public long getBufferSize()
    {
        return bufferSize;
    }

    public void setMetadataExplicit(boolean metadataExplicit)
    {
        this.metadataExplicit = metadataExplicit;
    }

    public boolean isMetadataExplicit()
    {
        return metadataExplicit;
    }
}
