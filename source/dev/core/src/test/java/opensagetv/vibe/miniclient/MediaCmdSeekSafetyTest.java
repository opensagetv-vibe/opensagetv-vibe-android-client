package opensagetv.vibe.miniclient;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class MediaCmdSeekSafetyTest
{
    @Test public void unsupportedSeekHasNoDecoderCallOrWireReply() throws Exception
    {
        MiniPlayerPlugin player = mock(MiniPlayerPlugin.class);
        MediaCmd command = command(player);
        byte[] request = request(123456789012L);
        byte[] reply = new byte[16];
        Arrays.fill(reply, (byte)0x5a);
        byte[] original = reply.clone();
        assertEquals(0, command.ExecuteMediaCommand(MediaCmd.MEDIACMD_SEEK, 8, request, reply));
        assertArrayEquals(original, reply);
        verify(player, never()).seek(anyLong());
        verify(player).onUnsupportedServerSeek(123456789012L);
        assertEquals(123456789012L, command.getLastServerRequestedSeekMs());
        assertEquals(1, command.getLastServerSeekSequence());
    }

    @Test public void supportedSeekPreservesSigned64BitTargetAndZeroReply() throws Exception
    {
        MiniPlayerPlugin player = mock(MiniPlayerPlugin.class);
        when(player.canHandleServerSeek()).thenReturn(true);
        MediaCmd command = command(player);
        assertEquals(0, command.ExecuteMediaCommand(MediaCmd.MEDIACMD_SEEK, 8,
                request(-9876543210L), new byte[0]));
        verify(player).seek(-9876543210L);
        verify(player, never()).onUnsupportedServerSeek(anyLong());
    }

    @Test public void compatibilityDefaultStillAcceptsExistingImplementations()
    {
        MiniPlayerPlugin oldPlugin = mock(MiniPlayerPlugin.class, CALLS_REAL_METHODS);
        assertTrue(oldPlugin.canHandleServerSeek());
    }

    private static byte[] request(long target)
    {
        byte[] bytes = new byte[8];
        MediaCmd.writeInt((int)(target >>> 32), bytes, 0);
        MediaCmd.writeInt((int)target, bytes, 4);
        return bytes;
    }

    private static MediaCmd command(MiniPlayerPlugin player) throws Exception
    {
        MiniClient client = mock(MiniClient.class);
        MediaCmd result = new MediaCmd(client);
        java.lang.reflect.Field field = MediaCmd.class.getDeclaredField("playa");
        field.setAccessible(true); field.set(result, player);
        return result;
    }
}
