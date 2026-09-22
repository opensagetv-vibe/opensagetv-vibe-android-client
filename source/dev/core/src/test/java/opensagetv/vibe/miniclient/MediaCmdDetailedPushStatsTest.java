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

    @Test
    public void reportsOrdinaryPushFromThePtsDerivedEpochStart()
    {
        MiniClient client = mock(MiniClient.class);
        MiniClientConnection connection = mock(MiniClientConnection.class);
        MiniPlayerPlugin player = mock(MiniPlayerPlugin.class);
        when(client.getCurrentConnection()).thenReturn(connection);
        // Make the anchor supplied by MediaCmd directly observable.
        when(player.getMediaTimeMillis(anyLong())).thenAnswer(invocation ->
                invocation.getArgument(0, Long.class) + 100L);
        when(player.getBufferLeft()).thenReturn(4096);
        when(player.getState()).thenReturn(MiniPlayerPlugin.PLAY_STATE);

        MediaCmd command = new MediaCmd(client);
        setPlayer(command, player);
        setBooleanField(command, "pushMode", true);
        setBooleanField(command, "dvdSessionPending", false);
        MiniClientConnection.detailedBufferStats = true;

        byte[] first = packetWithPts(90_000L);
        byte[] second = packetWithPts(90_000L + 27L * 90_000L);
        byte[] packet = new byte[18 + first.length + second.length];
        MediaCmd.writeInt(first.length + second.length, packet, 0);
        MediaCmd.writeInt(0x40, packet, 4);
        MediaCmd.writeShort((short) 12000, packet, 8);
        MediaCmd.writeShort((short) 8000, packet, 10);
        MediaCmd.writeShort((short) 6000, packet, 12);
        MediaCmd.writeInt(967116, packet, 14);
        System.arraycopy(first, 0, packet, 18, first.length);
        System.arraycopy(second, 0, packet, 18 + first.length, second.length);

        byte[] response = new byte[16];
        assertEquals(9, command.ExecuteMediaCommand(MediaCmd.MEDIACMD_PUSHBUFFER,
                packet.length, packet, response));
        assertEquals(5, command.ExecuteMediaCommand(MediaCmd.MEDIACMD_GETMEDIATIME,
                0, new byte[0], response));
        assertEquals(940216, MediaCmd.readInt(0, response));
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

    private static void setBooleanField(MediaCmd command, String name, boolean value)
    {
        try
        {
            java.lang.reflect.Field field = MediaCmd.class.getDeclaredField(name);
            field.setAccessible(true);
            field.setBoolean(command, value);
        }
        catch (ReflectiveOperationException e)
        {
            throw new AssertionError(e);
        }
    }

    private static byte[] packetWithPts(long pts)
    {
        byte[] packet = new byte[188];
        packet[0] = 0x47;
        packet[1] = 0x40;
        packet[2] = 0x20;
        packet[3] = 0x10;
        int p = 4;
        packet[p] = 0;
        packet[p + 1] = 0;
        packet[p + 2] = 1;
        packet[p + 3] = (byte) 0xE0;
        packet[p + 6] = (byte) 0x80;
        packet[p + 7] = (byte) 0x80;
        packet[p + 8] = 5;
        packet[p + 9] = (byte) (0x20 | (((pts >> 30) & 0x07) << 1) | 1);
        packet[p + 10] = (byte) (pts >> 22);
        packet[p + 11] = (byte) ((((pts >> 15) & 0x7F) << 1) | 1);
        packet[p + 12] = (byte) (pts >> 7);
        packet[p + 13] = (byte) (((pts & 0x7F) << 1) | 1);
        return packet;
    }
}
