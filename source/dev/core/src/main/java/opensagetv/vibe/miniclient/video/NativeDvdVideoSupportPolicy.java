package opensagetv.vibe.miniclient.video;

/** Fail closed only when native DVD's required video decoder is proven absent. */
public final class NativeDvdVideoSupportPolicy
{
    private NativeDvdVideoSupportPolicy() { }

    /**
     * Native DVD MPEG-PS is not an audio-only programme. Exo otherwise selects
     * its supported AC-3 stream and starts sound with no picture when the codec
     * selector has no MPEG-2 candidate. Use that policy-filtered selector, not
     * a model blacklist or the server's source capabilities. A failed query is
     * unknown (-1), not proof of absence. Transformed DVD uses H.264 and must
     * never be rejected by this native-input check.
     */
    public static boolean shouldReject(boolean dvdPush, boolean transformed,
                                       int selectedMpeg2DecoderCount)
    {
        return dvdPush && !transformed && selectedMpeg2DecoderCount == 0;
    }

    /** Auto/Hybrid may start with an empty native URL before changing transport. */
    public static boolean shouldRejectDiscoveredVideo(boolean nativeDvd,
            boolean unsupportedMpeg2Discovered, boolean supportedVideoDiscovered)
    {
        return nativeDvd && unsupportedMpeg2Discovered && !supportedVideoDiscovered;
    }
}
