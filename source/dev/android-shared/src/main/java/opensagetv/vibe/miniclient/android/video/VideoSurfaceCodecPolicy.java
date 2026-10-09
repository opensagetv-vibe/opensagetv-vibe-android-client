package opensagetv.vibe.miniclient.android.video;

/** Evidence-scoped additions to the players' built-in Surface replacement policy. */
public final class VideoSurfaceCodecPolicy {
    private VideoSurfaceCodecPolicy() { }

    /**
     * ONN v1 sti6140d360/API34 AVC/MPEG-2 and Pro SNA/API34 MPEG-2:
     * HOME return stalls after output Surface replacement without a player
     * error. The v1 attachment-lifetime-only candidate failed. Pro AVC passes
     * ordinary replacement and must not receive the v1 AVC exception.
     * Request their supported codec-release/reinitialize path on actual Surface
     * replacement instead. It keeps the media source, sample queues, audio and
     * player clock; it is not a seek, retry loop or continuous liveness probe.
     * Do not generalize to untested codecs, Android releases or ONN models.
     *
     * MiniMX AM2 (gxbaby board)/API23 MPEG-2 exhibits the same boundary: native playback
     * renders before HOME, then AudioTrack continues after return while video
     * input/output counters stop. Codec logs show Surface generation changes
     * without decoder release. Use the renderer's existing codec replacement
     * path for this observed combination only, not a whole-player restart or
     * a broad exception for every legacy Amlogic decoder.
     */
    public static boolean recreateOnReplacement(int sdk, String device, String codec) {
        if (sdk == 23 && "AM2".equals(device))
            return "OMX.amlogic.mpeg2.decoder.awesome".equals(codec);
        if (sdk != 34) return false;
        if ("sti6140d360".equals(device))
            return "c2.amlogic.avc.decoder".equals(codec)
                    || "c2.amlogic.mpeg2.decoder".equals(codec);
        return "SNA".equals(device) && "c2.amlogic.mpeg2.decoder".equals(codec);
    }
}
