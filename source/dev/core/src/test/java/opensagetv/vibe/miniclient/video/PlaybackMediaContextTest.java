package opensagetv.vibe.miniclient.video;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PlaybackMediaContextTest
{
    @Test
    public void preservesAllSageTvLoadHints()
    {
        PlaybackMediaContext context = new PlaybackMediaContext();
        context.update((byte) 1, (byte) 2, "MPEG2-TS", true, 64 * 1024L);

        assertEquals(1, context.getMajorTypeHint());
        assertEquals(2, context.getMinorTypeHint());
        assertEquals("MPEG2-TS", context.getEncodingHint());
        assertTrue(context.isTimeshifted());
        assertEquals(64 * 1024L, context.getBufferSize());
    }
}
