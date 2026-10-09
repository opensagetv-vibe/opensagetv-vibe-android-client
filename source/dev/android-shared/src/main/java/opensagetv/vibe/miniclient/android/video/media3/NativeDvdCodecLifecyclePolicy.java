package opensagetv.vibe.miniclient.android.video.media3;

/** A proven decoder-family workaround, never a device profile or ordinary-TV policy. */
final class NativeDvdCodecLifecyclePolicy
{
    private NativeDvdCodecLifecyclePolicy() { }

    static boolean restartNoOutput(String mime, String codecName, int queuedInputs,
            boolean decodedOutputSeen, boolean sourceReady, boolean attempted,
            long firstInputMs, long nowMs)
    {
        // A sparse/empty/slow server stream is not a decoder failure. Require
        // a full observed input window and additional locally queued samples.
        // An output held by frame scheduling is a different problem: never
        // restart that case just because its rendered counter is still zero.
        return releaseBeforeDisable(true, mime, codecName) && !attempted
                && !decodedOutputSeen && sourceReady && queuedInputs >= 8
                && firstInputMs >= 0L && nowMs - firstInputMs >= 4_000L;
    }

    static boolean releaseBeforeDisable(boolean nativeDvd, String mime, String codecName)
    {
        // Stock .175 / AFTMM authored-menu -> title repro: the playback thread
        // blocks in OMX.MTK MPEG-2's MediaCodec.flush during renderer disable.
        // Release that decoder before Media3 considers retaining/flushing it.
        // The Surface, player, Push session, source clock and preferences stay
        // owned by their existing lifecycle; other codecs/transports are unchanged.
        return nativeDvd && "video/mpeg2".equals(mime)
                && codecName != null && codecName.startsWith("OMX.MTK.");
    }
}
