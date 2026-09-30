package opensagetv.vibe.miniclient.android.video;

import java.io.IOException;

import opensagetv.vibe.miniclient.net.GrowingDataSource;
import opensagetv.vibe.miniclient.net.ISageTVDataSource;

/**
 * Resolves the ambiguity in stock SageTV OPENURL commands. Historical clients
 * guessed that every streamable extension was active. That protects live TV at
 * EOF, but publishing an unknown length for a completed MPEG-TS file prevents
 * extractors from seeking to a saved position. Explicit Vibe metadata remains
 * authoritative; a legacy guess is verified by observing actual SIZE growth.
 */
public final class GrowingPlaybackSourcePolicy
{
    public static final long LEGACY_GROWTH_PROBE_MS = 750L;

    private final boolean configuredPotentiallyGrowing;
    private final boolean metadataExplicit;
    private boolean resolved;
    private boolean growing;
    private long initialSize = -1L;
    private long observedSize = -1L;

    public GrowingPlaybackSourcePolicy(boolean configuredPotentiallyGrowing,
                                       boolean metadataExplicit)
    {
        this.configuredPotentiallyGrowing = configuredPotentiallyGrowing;
        this.metadataExplicit = metadataExplicit;
    }

    public synchronized boolean resolve(ISageTVDataSource source, long size)
            throws IOException
    {
        if (!configuredPotentiallyGrowing) return false;
        if (metadataExplicit) return true;
        if (resolved) return growing;

        initialSize = size;
        observedSize = source instanceof GrowingDataSource
                ? ((GrowingDataSource) source).waitForGrowth(size, LEGACY_GROWTH_PROBE_MS)
                : source.size();
        growing = observedSize > size;
        resolved = true;
        PlaybackDebugTrap.recordDetailed("legacy_growth_classified", null,
                "initialSize=" + initialSize + ";observedSize=" + observedSize
                        + ";growing=" + growing + ";probeMs=" + LEGACY_GROWTH_PROBE_MS);
        return growing;
    }

    /**
     * Returns the conservative policy Media3 must use while its player is
     * being built, before the loader thread has opened the media source.
     *
     * <p>Legacy SageTV OPENURL commands mark streamable files as potentially
     * active but do not say whether the file is actually growing.  Proving
     * that state requires MediaServer I/O and therefore belongs in
     * {@link #resolve(ISageTVDataSource, long)}, which Media3 invokes from its
     * loader thread.  Player construction runs on Android's main thread, so it
     * must only consume the non-blocking candidate state here.</p>
     */
    public boolean shouldPreparePlayerForGrowth()
    {
        return configuredPotentiallyGrowing;
    }

    public boolean isConfiguredPotentiallyGrowing()
    {
        return configuredPotentiallyGrowing;
    }

    public boolean isMetadataExplicit()
    {
        return metadataExplicit;
    }
}
