package opensagetv.vibe.miniclient.video;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MediaReplacementPolicyTest
{
    private static PlaybackMediaContext completed()
    {
        PlaybackMediaContext context = new PlaybackMediaContext();
        context.update((byte) 1, (byte) 32, "MPEG2-TS", false, 0);
        return context;
    }

    @Test
    public void allowsReadyCompletedPullReplacement()
    {
        MediaReplacementPolicy.Decision decision = MediaReplacementPolicy.evaluate(
                true, true, true, false, false, false, completed(),
                "/recordings/next.ts", false, 0);
        assertTrue(decision.isEligible());
        assertEquals("eligible_completed_pull", decision.getReason());
    }

    @Test
    public void rejectsFirstLoadAndUnavailablePlayer()
    {
        assertReason("first_load", false, true, true, false, false, false,
                completed(), "/recordings/next.ts", false, 0);
        assertReason("no_player", true, false, true, false, false, false,
                completed(), "/recordings/next.ts", false, 0);
        assertReason("player_not_ready", true, true, false, false, false, false,
                completed(), "/recordings/next.ts", false, 0);
    }

    @Test
    public void rejectsCurrentNonPullOrActiveMedia()
    {
        assertReason("current_push", true, true, true, true, false, false,
                completed(), "/recordings/next.ts", false, 0);
        assertReason("current_dvd", true, true, true, false, true, false,
                completed(), "/recordings/next.ts", false, 0);
        assertReason("current_http", true, true, true, false, false, true,
                completed(), "/recordings/next.ts", false, 0);

        PlaybackMediaContext active = new PlaybackMediaContext();
        active.update((byte) 1, (byte) 32, "", true, 0);
        assertReason("current_active_or_legacy_unknown", true, true, true,
                false, false, false, active, "/recordings/next.ts", false, 0);

        PlaybackMediaContext circular = new PlaybackMediaContext();
        circular.update((byte) 1, (byte) 32, "", false, 4096);
        assertReason("current_circular_buffer", true, true, true,
                false, false, false, circular, "/recordings/next.ts", false, 0);
    }

    @Test
    public void rejectsUnsafeNextSources()
    {
        assertReason("next_active_or_legacy_unknown", true, true, true,
                false, false, false, completed(), "/recordings/live.ts", true, 0);
        assertReason("next_circular_buffer", true, true, true,
                false, false, false, completed(), "/recordings/live.ts", false, 4096);
        assertReason("next_push", true, true, true,
                false, false, false, completed(), "push:video", false, 0);
        assertReason("next_dvd", true, true, true,
                false, false, false, completed(), "dvd:/VIDEO_TS", false, 0);
        assertReason("next_http", true, true, true,
                false, false, false, completed(), "https://example.invalid/a.m3u8", false, 0);
        assertReason("next_external_link", true, true, true,
                false, false, false, completed(), "/video/item.exlink", false, 0);
    }

    private static void assertReason(String expected,
                                     boolean contextInitialized,
                                     boolean playerPresent,
                                     boolean playerReady,
                                     boolean currentPush,
                                     boolean currentDvd,
                                     boolean currentHttp,
                                     PlaybackMediaContext current,
                                     String nextUrl,
                                     boolean nextTimeshifted,
                                     long nextBufferSize)
    {
        MediaReplacementPolicy.Decision decision = MediaReplacementPolicy.evaluate(
                contextInitialized, playerPresent, playerReady, currentPush,
                currentDvd, currentHttp, current, nextUrl, nextTimeshifted,
                nextBufferSize);
        assertFalse(decision.isEligible());
        assertEquals(expected, decision.getReason());
    }
}
