package opensagetv.vibe.miniclient.android.diagnostics;

/** Diagnostic-only validation, not a change to player track IDs or selection. */
public final class DiagnosticCaptionMetadata {
    private DiagnosticCaptionMetadata() { }

    /** CEA is carried inside video, and extractor track IDs can be synthetic.
     * Only separately packetized DVB/Teletext IDs may be displayed as TS PIDs.
     * PAT (zero), null packets (8191), absent and synthetic IDs are not captions.
     */
    public static Integer transportPid(boolean separateBroadcastStream, int sourceId) {
        return separateBroadcastStream && sourceId > 0 && sourceId < 8191 ? sourceId : null;
    }
}
