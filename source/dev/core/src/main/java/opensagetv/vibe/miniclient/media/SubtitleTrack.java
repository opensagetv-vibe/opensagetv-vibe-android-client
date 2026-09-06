package opensagetv.vibe.miniclient.media;

public class SubtitleTrack
{

    private SubtitleCodec codec;
    private int index;
    private String language;
    private String label;
    private boolean supported;
    private int accessibilityChannel;

    public SubtitleTrack(int index, SubtitleCodec codec, String language, String label, boolean supported)
    {
        this(index, codec, language, label, supported, -1);
    }

    public SubtitleTrack(int index, SubtitleCodec codec, String language, String label,
                         boolean supported, int accessibilityChannel)
    {
        this.index = index;
        this.codec = codec;
        this.language = language;
        this.label = label;
        this.supported = supported;
        this.accessibilityChannel = accessibilityChannel;
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
}
