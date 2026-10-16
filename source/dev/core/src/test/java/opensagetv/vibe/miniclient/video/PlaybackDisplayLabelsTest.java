package opensagetv.vibe.miniclient.video;
import org.junit.Test;
import static org.junit.Assert.*;
public class PlaybackDisplayLabelsTest
{
    @Test public void dynamicIsPushOnlyWhenAnActualPusherIsPresent()
    {
        assertEquals("Push", PlaybackDisplayLabels.transport(false, true, "dynamic"));
        assertEquals("Pull", PlaybackDisplayLabels.transport(false, false, "dynamic"));
    }
    @Test public void ownedProducerIsDirectAndARequestedFixedFallbackCanStillBePull()
    {
        assertEquals("Direct", PlaybackDisplayLabels.transport(true, false, "fixed"));
        assertEquals("Fixed", PlaybackDisplayLabels.transport(false, true, "fixed"));
        assertEquals("Pull", PlaybackDisplayLabels.transport(false, false, "fixed"));
    }
    @Test public void casingIsForDisplayOnlyAndUnknownRemainsUnknown()
    {
        assertEquals("Software", PlaybackDisplayLabels.decoderKind("software"));
        assertEquals("Hardware", PlaybackDisplayLabels.decoderKind("hardware"));
        assertEquals("Unknown", PlaybackDisplayLabels.decoderKind("Unknown"));
        assertEquals("", PlaybackDisplayLabels.decoderKind(null));
    }
}
