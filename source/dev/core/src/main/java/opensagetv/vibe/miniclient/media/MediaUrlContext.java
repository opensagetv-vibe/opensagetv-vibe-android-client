package opensagetv.vibe.miniclient.media;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

/** Backward-compatible SageTV media metadata carried in an stv URL fragment. */
public final class MediaUrlContext
{
    public static final String MARKER = "#sagetv-media-v1;";

    private final String url;
    private final byte majorTypeHint;
    private final byte minorTypeHint;
    private final String encodingHint;
    private final String channelHint;
    private final boolean active;
    private final long bufferSize;
    private final boolean explicit;

    private MediaUrlContext(String url, byte majorTypeHint, byte minorTypeHint,
                            String encodingHint, String channelHint,
                            boolean active, long bufferSize,
                            boolean explicit)
    {
        this.url = url;
        this.majorTypeHint = majorTypeHint;
        this.minorTypeHint = minorTypeHint;
        this.encodingHint = encodingHint;
        this.channelHint = channelHint;
        this.active = active;
        this.bufferSize = bufferSize;
        this.explicit = explicit;
    }

    public static MediaUrlContext parse(String value, boolean fallbackActive)
    {
        String source = value == null ? "" : value;
        int marker = source.indexOf(MARKER);
        if (marker < 0)
        {
            return new MediaUrlContext(source, (byte) 0, (byte) 0, "", "",
                    fallbackActive, 0, false);
        }

        byte major = 0;
        byte minor = 0;
        String encoding = "";
        String channel = "";
        boolean active = fallbackActive;
        long buffer = 0;
        String[] fields = source.substring(marker + MARKER.length()).split(";");
        for (String field : fields)
        {
            int equals = field.indexOf('=');
            if (equals <= 0) continue;
            String name = field.substring(0, equals);
            String data = field.substring(equals + 1);
            try
            {
                if ("active".equals(name)) active = "1".equals(data) || "true".equalsIgnoreCase(data);
                else if ("buffer".equals(name)) buffer = Math.max(0, Long.parseLong(data));
                else if ("major".equals(name)) major = (byte) Integer.parseInt(data);
                else if ("minor".equals(name)) minor = (byte) Integer.parseInt(data);
                else if ("channel".equals(name)) channel = URLDecoder.decode(data, "UTF-8");
                else if ("encoding".equals(name)) encoding = URLDecoder.decode(data, "UTF-8");
            }
            catch (NumberFormatException ignored)
            {
                // Preserve safe defaults for malformed optional fields.
            }
            catch (UnsupportedEncodingException impossible)
            {
                encoding = "";
            }
        }
        return new MediaUrlContext(source.substring(0, marker), major, minor,
                encoding, channel, active, buffer, true);
    }

    public String getUrl() { return url; }
    public byte getMajorTypeHint() { return majorTypeHint; }
    public byte getMinorTypeHint() { return minorTypeHint; }
    public String getEncodingHint() { return encodingHint; }
    public String getChannelHint() { return channelHint; }
    public boolean isActive() { return active; }
    public long getBufferSize() { return bufferSize; }
    public boolean isExplicit() { return explicit; }
}
