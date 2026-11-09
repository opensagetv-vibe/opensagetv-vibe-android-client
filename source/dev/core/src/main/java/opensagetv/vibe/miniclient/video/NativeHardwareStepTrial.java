package opensagetv.vibe.miniclient.video;

/** Nonpersistent commissioning switch; never a negotiated playback capability. */
public final class NativeHardwareStepTrial
{
    private static volatile boolean enabled;
    private NativeHardwareStepTrial() { }
    public static void setEnabled(boolean value) { enabled = value; }
    public static boolean isEnabled() { return enabled; }

    /**
     * Classic Fixed/basic explicitly starts each producer at normalized MPEG
     * time. A new decoder-serial output proves its epoch; detailed original
     * streams have different anchors and must not borrow this proof.
     */
    public static boolean trustNormalizedEpoch(boolean hardwareTrial, boolean detailed,
            boolean actualCurrentEpochOutput)
    {
        return hardwareTrial && !detailed && actualCurrentEpochOutput;
    }

    /** Restrict the prototype to patched ARM glue and explicit hardware Push. */
    public static boolean eligible(boolean push, boolean dvd, String decoding, String arch)
    {
        return enabled && push && !dvd && "hardware".equals(decoding)
                && arch != null && (arch.startsWith("arm") || "aarch64".equals(arch));
    }
}
