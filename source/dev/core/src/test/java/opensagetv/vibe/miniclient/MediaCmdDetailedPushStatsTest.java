package opensagetv.vibe.miniclient;

import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MediaCmdDetailedPushStatsTest
{
    @After
    public void restoreDetailedStatsFlag()
    {
        MiniClientConnection.detailedBufferStats = false;
    }

    @Test
    public void retainsEveryDetailedPushFieldForBoundedDiagnostics()
    {
        MiniClient client = mock(MiniClient.class);
        MiniClientConnection connection = mock(MiniClientConnection.class);
        MiniPlayerPlugin player = mock(MiniPlayerPlugin.class);
        when(client.getCurrentConnection()).thenReturn(connection);
        when(player.getMediaTimeMillis(anyLong())).thenReturn(4000L);
        when(player.getBufferLeft()).thenReturn(123456);
        when(player.getState()).thenReturn(MiniPlayerPlugin.PLAY_STATE);

        MediaCmd command = new MediaCmd(client);
        setPlayer(command, player);
        MiniClientConnection.detailedBufferStats = true;

        byte[] packet = new byte[22];
        MediaCmd.writeInt(4, packet, 0);
        MediaCmd.writeInt(0x40, packet, 4);
        MediaCmd.writeShort((short) 12000, packet, 8);
        MediaCmd.writeShort((short) 8000, packet, 10);
        MediaCmd.writeShort((short) 6000, packet, 12);
        MediaCmd.writeInt(7000, packet, 14);
        packet[18] = 1;
        packet[19] = 2;
        packet[20] = 3;
        packet[21] = 4;

        byte[] response = new byte[16];
        assertEquals(9, command.ExecuteMediaCommand(MediaCmd.MEDIACMD_PUSHBUFFER,
                packet.length, packet, response));
        assertEquals(12000, command.getServerChannelBandwidthKbps());
        assertEquals(8000, command.getServerStreamBandwidthKbps());
        assertEquals(6000, command.getServerTargetBandwidthKbps());
        assertEquals(7000, command.getServerMuxTimeMs());
        assertEquals(3000, command.getClientBufferTimeMs());
        assertEquals(123456, command.getClientBufferAvailableBytes());
        assertEquals(4, command.getLastPushPayloadBytes());
        assertEquals(0x40, command.getLastPushFlags());
        assertEquals(1, command.getDetailedPushSampleSequence());
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
