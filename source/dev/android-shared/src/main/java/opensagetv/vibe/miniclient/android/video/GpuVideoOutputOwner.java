package opensagetv.vibe.miniclient.android.video;

/** Optional capability; does not change MiniClient/server or generic player ABI. */
public interface GpuVideoOutputOwner {
    GpuDeinterlaceOutput gpuVideoOutput();
    String gpuVideoUnavailableReason();
    /** Bounded values captured once BEFORE the failed stage releases resources. */
    default String gpuFailureWitness() { return ""; }
}
