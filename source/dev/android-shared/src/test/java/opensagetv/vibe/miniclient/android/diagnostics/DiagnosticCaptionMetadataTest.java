package opensagetv.vibe.miniclient.android.diagnostics;
import org.junit.Test;
import static org.junit.Assert.*;

public class DiagnosticCaptionMetadataTest {
    @Test public void onlyPacketizedBroadcastTracksHaveKnownPids() {
        assertEquals(Integer.valueOf(5503), DiagnosticCaptionMetadata.transportPid(true, 5503));
        assertEquals(Integer.valueOf(5504), DiagnosticCaptionMetadata.transportPid(true, 5504));
        assertNull(DiagnosticCaptionMetadata.transportPid(false, 5500));
    }
    @Test public void syntheticAbsentAndReservedIdsAreUnavailable() {
        for (int id : new int[]{-1, 0, 8191, 8192, 13692, 21884, Integer.MAX_VALUE})
            assertNull(DiagnosticCaptionMetadata.transportPid(true, id));
    }
}
