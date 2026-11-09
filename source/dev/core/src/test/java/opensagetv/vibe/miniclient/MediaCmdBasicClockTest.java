package opensagetv.vibe.miniclient;

import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

public class MediaCmdBasicClockTest
{
    @After public void restoreWireMode() { MiniClientConnection.detailedBufferStats = false; }

    @Test public void basicWireAddsRealOriginButLocalClockStaysRelative() throws Exception
    {
        MediaCmd command = command(false, false);
        feed(command, 45000, false);
        byte[] reply = new byte[16];
        assertEquals(4, command.ExecuteMediaCommand(MediaCmd.MEDIACMD_GETMEDIATIME, 0, new byte[0], reply));
        assertEquals(2523, MediaCmd.readInt(0, reply));
        assertEquals(2023, command.getMediaTimeMillis());
        assertEquals(500, command.getBasicPushPtsOriginMs());
        assertEquals(2523, command.getLastWireMediaTimeMs());
    }

    @Test public void detailedAndDvdWireContractsDoNotUseBasicOrigin() throws Exception
    {
        for (boolean detailed : new boolean[]{false, true})
        {
            MediaCmd command = command(detailed, !detailed);
            feed(command, 45000, detailed);
            byte[] reply = new byte[16];
            assertEquals(detailed ? 5 : 4, command.ExecuteMediaCommand(
                    MediaCmd.MEDIACMD_GETMEDIATIME, 0, new byte[0], reply));
            assertEquals(2023, MediaCmd.readInt(0, reply));
            assertEquals(-1, command.getBasicPushPtsOriginMs());
        }
    }

    @Test public void flushClearsThePreviousPesOrigin() throws Exception
    {
        MediaCmd command = command(false, false);
        feed(command, 45000, false);
        byte[] reply = new byte[16];
        command.ExecuteMediaCommand(MediaCmd.MEDIACMD_FLUSH, 0, new byte[0], reply);
        assertEquals(-1, command.getBasicPushPtsOriginMs());
        feed(command, 126000, false);
        command.ExecuteMediaCommand(MediaCmd.MEDIACMD_GETMEDIATIME, 0, new byte[0], reply);
        assertEquals(3423, MediaCmd.readInt(0, reply));
    }

    @Test public void pullCannotReuseAnOldPushOrigin() throws Exception
    {
        MediaCmd command = command(false, false);
        feed(command, 45000, false);
        field(command, "pushMode", false);
        byte[] reply = new byte[16];
        command.ExecuteMediaCommand(MediaCmd.MEDIACMD_GETMEDIATIME, 0, new byte[0], reply);
        assertEquals(2023, MediaCmd.readInt(0, reply));
    }

    private static MediaCmd command(boolean detailed, boolean dvd) throws Exception
    {
        MiniClient client = mock(MiniClient.class);
        when(client.getCurrentConnection()).thenReturn(mock(MiniClientConnection.class));
        MiniPlayerPlugin player = mock(MiniPlayerPlugin.class);
        when(player.getMediaTimeMillis(anyLong())).thenReturn(2023L);
        when(player.getBufferLeft()).thenReturn(4096);
        when(player.getState()).thenReturn(MiniPlayerPlugin.PLAY_STATE);
        MediaCmd command = new MediaCmd(client);
        java.lang.reflect.Field active = MediaCmd.class.getDeclaredField("playa");
        active.setAccessible(true); active.set(command, player);
        field(command, "pushMode", true);
        field(command, "dvdSessionPending", dvd);
        MiniClientConnection.detailedBufferStats = detailed;
        return command;
    }

    private static void field(MediaCmd command, String name, boolean value) throws Exception
    {
        java.lang.reflect.Field member = MediaCmd.class.getDeclaredField(name);
        member.setAccessible(true); member.setBoolean(command, value);
    }

    private static void feed(MediaCmd command, long pts, boolean detailed)
    {
        int offset = detailed ? 18 : 8;
        byte[] packet = new byte[offset + 14];
        MediaCmd.writeInt(14, packet, 0);
        if (detailed) MediaCmd.writeInt(5000, packet, 14);
        int p = offset;
        packet[p + 2] = 1; packet[p + 3] = (byte)0xE0; packet[p + 5] = 8;
        packet[p + 6] = (byte)0x80; packet[p + 7] = (byte)0x80; packet[p + 8] = 5;
        packet[p + 9] = (byte)(0x21 | (((pts >>> 30) & 7) << 1));
        packet[p + 10] = (byte)(pts >>> 22);
        packet[p + 11] = (byte)((((pts >>> 15) & 127) << 1) | 1);
        packet[p + 12] = (byte)(pts >>> 7);
        packet[p + 13] = (byte)(((pts & 127) << 1) | 1);
        command.ExecuteMediaCommand(MediaCmd.MEDIACMD_PUSHBUFFER, packet.length, packet, new byte[16]);
    }
}
