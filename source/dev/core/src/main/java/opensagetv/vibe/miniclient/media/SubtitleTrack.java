package opensagetv.vibe.miniclient.media;

public class SubtitleTrack
{

    private SubtitleCodec codec;
    private int index;
    private String language;
    private String label;
    private boolean supported;
    private int accessibilityChannel;
    private int sourceStreamId;

    public SubtitleTrack(int index, SubtitleCodec codec, String language, String label, boolean supported)
    {
        this(index, codec, language, label, supported, -1, -1);
    }

    public SubtitleTrack(int index, SubtitleCodec codec, String language, String label,
                         boolean supported, int accessibilityChannel)
    {
        this(index, codec, language, label, supported, accessibilityChannel, -1);
    }

    public SubtitleTrack(int index, SubtitleCodec codec, String language, String label,
                         boolean supported, int accessibilityChannel, int sourceStreamId)
    {
        this.index = index;
        this.codec = codec;
        this.language = language;
        this.label = label;
        this.supported = supported;
        this.accessibilityChannel = accessibilityChannel;
        this.sourceStreamId = sourceStreamId;
    }

    @Override
    public String toString()
    {
        String output = "";

        if (getLanguage().isEmpty())
        {
            output += "Unknown";
        }
        else
        {
            output += getLanguage();
        }

        output += " (" + getSubtitleCodec() + ")";

        if (accessibilityChannel > 0 && codec == SubtitleCodec.CEA608)
        {
            output += " CC" + accessibilityChannel;
        }
        else if (accessibilityChannel > 0 && codec == SubtitleCodec.CEA708)
        {
            output += " Service " + accessibilityChannel;
        }

        if(!getLabel().isEmpty())
        {
            output += " " + label;
        }

        if(!isSupported())
        {
            output += " NOT SUPPORTED";
        }

        return output;
    }

    public int getIndex()
    {
        return index;
    }

    public String  getLanguage()
    {
        if(language == null)
        {
            return "";
        }
        else
        {
            return language;
        }
    }

    public SubtitleCodec getSubtitleCodec()
    {
        return this.codec;
    }

    public String getLabel()
    {
        if(label == null)
        {
            return "";
        }
        else
        {
            return label;
        }


    }

    public boolean isSupported()
    {
        return supported;
    }

    public int getAccessibilityChannel()
    {
        return accessibilityChannel;
    }

    /** MPEG-TS source PID when the extractor exposes one, otherwise {@code -1}. */
    public int getSourceStreamId()
    {
        return sourceStreamId;
    }

    /** Extracts a final decimal/hex stream-id component such as {@code 1/5503}. */
    public static int parseSourceStreamId(String value)
    {
        if (value == null) return -1;
        String token = value.trim();
        if (token.isEmpty()) return -1;
        int slash = token.lastIndexOf('/');
        if (slash >= 0) token = token.substring(slash + 1).trim();
        if (token.regionMatches(true, 0, "pid=", 0, 4))
            token = token.substring(4).trim();
        try
        {
            if (token.regionMatches(true, 0, "0x", 0, 2))
                return Integer.parseInt(token.substring(2), 16);
            for (int i = 0; i < token.length(); i++)
                if (!Character.isDigit(token.charAt(i))) return -1;
            return token.isEmpty() ? -1 : Integer.parseInt(token);
        }
        catch (NumberFormatException ignored)
        {
            return -1;
        }
    }
}
