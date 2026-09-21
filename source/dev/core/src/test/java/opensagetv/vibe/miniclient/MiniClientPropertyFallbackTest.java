package opensagetv.vibe.miniclient;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MiniClientPropertyFallbackTest
{
    @Test
    public void unknownGetPropertyIsUnsupportedWithoutFailure()
    {
        assertEquals("", MiniClientConnection.unsupportedGetPropertyValue(
                "FUTURE_UNKNOWN_CAPABILITY"));
    }

    @Test
    public void unknownSetPropertyIsAcknowledgedWithoutFailure()
    {
        assertEquals(0, MiniClientConnection.unsupportedSetPropertyResult(
                "FUTURE_UNKNOWN_STATE"));
    }
}
