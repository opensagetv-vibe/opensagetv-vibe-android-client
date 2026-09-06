package opensagetv.vibe.miniclient;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class MediaCmdPlaybackRateTest
{
    @Test
    public void forwardsFloatRateAndReturnsAcceptedFloatBits()
    {
        MiniClient client = mock(MiniClient.class);
        MiniClientConnection connection = mock(MiniClientConnection.class);
        MiniPlayerPlugin player = mock(MiniPlayerPlugin.class);
        when(client.getCurrentConnection()).thenReturn(connection);
        when(player.setPlaybackRate(4.0f)).thenReturn(4.0f);
        MediaCmd command = new MediaCmd(client);
        setPlayer(command, player);

        byte[] request = new byte[4];
        MediaCmd.writeInt(Float.floatToIntBits(4.0f), request, 0);
        byte[] response = new byte[4];
        assertEquals(4, command.ExecuteMediaCommand(
                MediaCmd.MEDIACMD_SETRATE, request.length, request, response));
        assertEquals(4.0f, Float.intBitsToFloat(MediaCmd.readInt(0, response)), 0.0f);
        verify(player).setPlaybackRate(4.0f);
    }

    @Test
    public void malformedOrAbsentPlayerFailsSafeAtNormalRate()
    {
        MiniClient client = mock(MiniClient.class);
        when(client.getCurrentConnection()).thenReturn(mock(MiniClientConnection.class));
        MediaCmd command = new MediaCmd(client);
        byte[] response = new byte[4];
        assertEquals(4, command.ExecuteMediaCommand(
                MediaCmd.MEDIACMD_SETRATE, 0, new byte[0], response));
        assertEquals(1.0f, Float.intBitsToFloat(MediaCmd.readInt(0, response)), 0.0f);
    }

    private static void setPlayer(MediaCmd command, MiniPlayerPlugin player)
    {
        try
        {
            java.lang.reflect.Field field = MediaCmd.class.getDeclaredField("playa");
            field.setAccessible(true);
            field.set(command, player);
        }
        catch (ReflectiveOperationException e)
        {
            throw new AssertionError(e);
        }
    }
}
