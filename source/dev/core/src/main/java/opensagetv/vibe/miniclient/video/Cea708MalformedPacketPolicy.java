package opensagetv.vibe.miniclient.video;

/** Recognizes only the observed CEA-708 packet bit-boundary failure. */
public final class Cea708MalformedPacketPolicy
{
    private Cea708MalformedPacketPolicy() { }

    public static boolean isMalformedPacket(IllegalStateException failure)
    {
        for (String prefix : new String[]{"androidx.media3.", "com.google.android.exoplayer2."})
        {
            boolean packet = false;
            boolean bits = false;
            for (StackTraceElement frame : failure.getStackTrace())
            {
                String name = frame.getClassName();
                if (name.startsWith(prefix) && name.endsWith(".Cea708Decoder")
                        && "processCurrentPacket".equals(frame.getMethodName())) packet = true;
                if (name.startsWith(prefix) && name.endsWith(".ParsableBitArray")
                        && "assertValidOffset".equals(frame.getMethodName())) bits = true;
            }
            if (packet && bits) return true;
        }
        return false;
    }
}
