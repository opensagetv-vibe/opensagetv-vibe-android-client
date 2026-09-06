package opensagetv.vibe.miniclient;

import org.junit.Test;

import java.io.IOException;

import opensagetv.vibe.miniclient.prefs.PrefStore;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class MediaCmdDvdProtocolTest
{
    @Test
    public void runtimeMimFailureUrlIsReportedAndNextInitResetsIt()
    {
        MiniClient client = mock(MiniClient.class);
        MiniClientConnection connection = mock(MiniClientConnection.class);
        MiniPlayerPlugin player = mock(MiniPlayerPlugin.class);
        when(client.getCurrentConnection()).thenReturn(connection);
        when(connection.newPlayerPlugin(anyString())).thenReturn(player);

        MediaCmd command = new MediaCmd(client);
        byte[] response = new byte[16];
        command.ExecuteMediaCommand(MediaCmd.MEDIACMD_INIT, 4, new byte[4], response);

        byte[] open = openUrl("push:dvd?vibe_transport=native&format=mpegps&fallback=mim_failure");
        assertEquals(4, command.ExecuteMediaCommand(MediaCmd.MEDIACMD_OPENURL,
                open.length, open, response));
        assertEquals(true, command.isDvdMimRuntimeFallback());

        command.ExecuteMediaCommand(MediaCmd.MEDIACMD_INIT, 4, new byte[4], response);
        assertEquals(false, command.isDvdMimRuntimeFallback());
    }

    @Test
    public void initDefersPlayerUntilDvdPushAndThenReusesIt() throws IOException
    {
        MiniClient client = mock(MiniClient.class);
        MiniClientConnection connection = mock(MiniClientConnection.class);
        MiniPlayerPlugin player = mock(MiniPlayerPlugin.class);
        when(client.getCurrentConnection()).thenReturn(connection);
        when(connection.newPlayerPlugin("push:dvd")).thenReturn(player);
        when(player.getBufferLeft()).thenReturn(1024 * 1024);

        MediaCmd command = new MediaCmd(client);
        byte[] response = new byte[16];
        byte[] init = new byte[4];
        assertEquals(4, command.ExecuteMediaCommand(MediaCmd.MEDIACMD_INIT,
                init.length, init, response));
        verify(connection, never()).newPlayerPlugin(anyString());

        byte[] push = new byte[12];
        MediaCmd.writeInt(4, push, 0);
        push[8] = 0;
        push[9] = 0;
        push[10] = 1;
        push[11] = (byte) 0xBA;
        assertEquals(4, command.ExecuteMediaCommand(MediaCmd.MEDIACMD_PUSHBUFFER,
                push.length, push, response));

        assertSame(player, command.getPlaya());
        verify(player).setPushMode(true);
        verify(player).load((byte) 0, (byte) 0, "MPEG2-PS", "push:dvd", null, false, 0);
        verify(player).pushData(push, 8, 4);

        command.ExecuteMediaCommand(MediaCmd.MEDIACMD_PUSHBUFFER,
                push.length, push, response);
        verify(connection).newPlayerPlugin("push:dvd");
    }

    private static byte[] openUrl(String url)
    {
        byte[] encoded = url.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] command = new byte[encoded.length + 5];
        MediaCmd.writeInt(encoded.length + 1, command, 0);
        System.arraycopy(encoded, 0, command, 4, encoded.length);
        return command;
    }

    @Test
    public void dvdMetadataCommandsValidatePayloadAndAlwaysReply()
    {
        MiniClient client = mock(MiniClient.class);
        MiniClientConnection connection = mock(MiniClientConnection.class);
        MiniPlayerPlugin player = mock(MiniPlayerPlugin.class);
        when(client.getCurrentConnection()).thenReturn(connection);
        when(connection.newPlayerPlugin("push:dvd")).thenReturn(player);

        MediaCmd command = new MediaCmd(client);
        byte[] response = new byte[16];
        command.ExecuteMediaCommand(MediaCmd.MEDIACMD_INIT, 4, new byte[4], response);

        byte[] cell = new byte[8];
        MediaCmd.writeInt(4, cell, 0);
        cell[4] = 9;
        cell[5] = 8;
        cell[6] = 7;
        cell[7] = 6;
        assertEquals(4, command.ExecuteMediaCommand(MediaCmd.MEDIACMD_DVD_NEWCELL,
                cell.length, cell, response));
        verify(player).dvdNewCell(4, new byte[] {9, 8, 7, 6});

        byte[] malformed = new byte[5];
        MediaCmd.writeInt(64, malformed, 0);
        assertEquals(4, command.ExecuteMediaCommand(MediaCmd.MEDIACMD_DVD_CLUT,
                malformed.length, malformed, response));
        verify(player, never()).dvdSetClut(org.mockito.ArgumentMatchers.anyInt(), any(byte[].class));

        byte[] value = new byte[4];
        MediaCmd.writeInt(1234, value, 0);
        assertEquals(4, command.ExecuteMediaCommand(MediaCmd.MEDIACMD_DVD_STC,
                value.length, value, response));
        verify(player).dvdSetStc(1234);
        assertEquals(4, command.ExecuteMediaCommand(MediaCmd.MEDIACMD_DVD_FORMAT,
                value.length, value, response));
        verify(player).dvdSetFormat(1234);
    }

    @Test
    public void mediaTimeBeforeFirstDvdPushAlwaysReplies()
    {
        MiniClient client = mock(MiniClient.class);
        MiniClientConnection connection = mock(MiniClientConnection.class);
        PrefStore properties = mock(PrefStore.class);
        when(client.getCurrentConnection()).thenReturn(connection);
        when(client.properties()).thenReturn(properties);
        when(properties.getInt(PrefStore.Keys.video_buffer_size, 4 * 1024 * 1024))
                .thenReturn(4 * 1024 * 1024);
        when(properties.getInt(PrefStore.Keys.audio_buffer_size, 2 * 1024 * 1024))
                .thenReturn(2 * 1024 * 1024);

        MediaCmd command = new MediaCmd(client);
        byte[] response = new byte[16];
        command.ExecuteMediaCommand(MediaCmd.MEDIACMD_INIT, 4, new byte[4], response);

        assertEquals(4, command.ExecuteMediaCommand(MediaCmd.MEDIACMD_GETMEDIATIME,
                0, new byte[0], response));
        assertEquals(0, MediaCmd.readInt(0, response));
        verify(connection, never()).newPlayerPlugin(anyString());
    }

    @Test
    public void dvdMediaTimeReplyStaysFourBytesWhenDetailedStatsAreNegotiated()
    {
        MiniClient client = mock(MiniClient.class);
        MiniClientConnection connection = mock(MiniClientConnection.class);
        when(client.getCurrentConnection()).thenReturn(connection);

        boolean previous = MiniClientConnection.detailedBufferStats;
        MiniClientConnection.detailedBufferStats = true;
        try
        {
            MediaCmd command = new MediaCmd(client);
            byte[] response = new byte[16];
            command.ExecuteMediaCommand(MediaCmd.MEDIACMD_INIT, 4, new byte[4], response);

            assertEquals(4, command.ExecuteMediaCommand(MediaCmd.MEDIACMD_GETMEDIATIME,
                    0, new byte[0], response));
            assertEquals(0, MediaCmd.readInt(0, response));
        }
        finally
        {
            MiniClientConnection.detailedBufferStats = previous;
        }
    }

    @Test
    public void dvdDrainPollReturnsNativeReadySentinel() throws IOException
    {
        MiniClient client = mock(MiniClient.class);
        MiniClientConnection connection = mock(MiniClientConnection.class);
        MiniPlayerPlugin player = mock(MiniPlayerPlugin.class);
        when(client.getCurrentConnection()).thenReturn(connection);
        when(connection.newPlayerPlugin("push:dvd")).thenReturn(player);
        when(player.getBufferLeft()).thenReturn((4 * 1024 * 1024) - 1);

        MediaCmd command = new MediaCmd(client);
        byte[] response = new byte[16];
        command.ExecuteMediaCommand(MediaCmd.MEDIACMD_INIT, 4, new byte[4], response);

        byte[] poll = new byte[8];
        MediaCmd.writeInt(0, poll, 0);
        MediaCmd.writeInt(0x100, poll, 4);
        assertEquals(4, command.ExecuteMediaCommand(MediaCmd.MEDIACMD_PUSHBUFFER,
                poll.length, poll, response));
        assertEquals(-2, MediaCmd.readInt(0, response));
        verify(player, never()).pushData(any(byte[].class),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    public void dvdPushReplyStaysFourBytesWhenDetailedStatsAreNegotiated() throws IOException
    {
        MiniClient client = mock(MiniClient.class);
        MiniClientConnection connection = mock(MiniClientConnection.class);
        MiniPlayerPlugin player = mock(MiniPlayerPlugin.class);
        when(client.getCurrentConnection()).thenReturn(connection);
        when(connection.newPlayerPlugin("push:dvd")).thenReturn(player);
        when(player.getBufferLeft()).thenReturn(4 * 1024 * 1024);

        boolean previous = MiniClientConnection.detailedBufferStats;
        MiniClientConnection.detailedBufferStats = true;
        try
        {
            MediaCmd command = new MediaCmd(client);
            byte[] response = new byte[16];
            command.ExecuteMediaCommand(MediaCmd.MEDIACMD_INIT, 4, new byte[4], response);

            byte[] poll = new byte[8];
            MediaCmd.writeInt(0, poll, 0);
            MediaCmd.writeInt(0x100, poll, 4);
            assertEquals(4, command.ExecuteMediaCommand(MediaCmd.MEDIACMD_PUSHBUFFER,
                    poll.length, poll, response));
            assertEquals(-2, MediaCmd.readInt(0, response));
        }
        finally
        {
            MiniClientConnection.detailedBufferStats = previous;
        }
    }

    @Test
    public void firstEmptyDvdPollAdvertisesCapacityWithOnlyFourReplyBytes()
    {
        MiniClient client = mock(MiniClient.class);
        MiniClientConnection connection = mock(MiniClientConnection.class);
        PrefStore properties = mock(PrefStore.class);
        when(client.getCurrentConnection()).thenReturn(connection);
        when(client.properties()).thenReturn(properties);
        when(properties.getInt(PrefStore.Keys.video_buffer_size, 4 * 1024 * 1024))
                .thenReturn(4 * 1024 * 1024);
        when(properties.getInt(PrefStore.Keys.audio_buffer_size, 2 * 1024 * 1024))
                .thenReturn(2 * 1024 * 1024);

        boolean previous = MiniClientConnection.detailedBufferStats;
        MiniClientConnection.detailedBufferStats = true;
        try
        {
            MediaCmd command = new MediaCmd(client);
            byte[] response = new byte[16];
            command.ExecuteMediaCommand(MediaCmd.MEDIACMD_INIT, 4, new byte[4], response);

            byte[] poll = new byte[8];
            MediaCmd.writeInt(0, poll, 0);
            MediaCmd.writeInt(0, poll, 4);
            assertEquals(4, command.ExecuteMediaCommand(MediaCmd.MEDIACMD_PUSHBUFFER,
                    poll.length, poll, response));
            assertEquals(4 * 1024 * 1024, MediaCmd.readInt(0, response));
            verify(connection, never()).newPlayerPlugin(anyString());
        }
        finally
        {
            MiniClientConnection.detailedBufferStats = previous;
        }
    }

    @Test
    public void preDataDvdInitializationFlushDoesNotRepreparePlayer()
    {
        MiniClient client = mock(MiniClient.class);
        MiniClientConnection connection = mock(MiniClientConnection.class);
        MiniPlayerPlugin player = mock(MiniPlayerPlugin.class);
        when(client.getCurrentConnection()).thenReturn(connection);
        when(connection.newPlayerPlugin("push:dvd")).thenReturn(player);

        MediaCmd command = new MediaCmd(client);
        byte[] response = new byte[16];
        command.ExecuteMediaCommand(MediaCmd.MEDIACMD_INIT, 4, new byte[4], response);

        byte[] cell = new byte[8];
        MediaCmd.writeInt(4, cell, 0);
        command.ExecuteMediaCommand(MediaCmd.MEDIACMD_DVD_NEWCELL,
                cell.length, cell, response);
        assertEquals(4, command.ExecuteMediaCommand(MediaCmd.MEDIACMD_FLUSH,
                0, new byte[0], response));
        verify(player, never()).flush();
    }

    @Test
    public void dvdDrainUsesActualFreeSpaceAfterCounterRebase() throws IOException
    {
        MiniClient client = mock(MiniClient.class);
        MiniClientConnection connection = mock(MiniClientConnection.class);
        MiniPlayerPlugin player = mock(MiniPlayerPlugin.class);
        when(client.getCurrentConnection()).thenReturn(connection);
        when(connection.newPlayerPlugin("push:dvd")).thenReturn(player);
        when(player.getBufferLeft()).thenReturn((4 * 1024 * 1024) - 1);
        when(player.getLastFileReadPos()).thenReturn(0L);

        MediaCmd command = new MediaCmd(client);
        byte[] response = new byte[16];
        command.ExecuteMediaCommand(MediaCmd.MEDIACMD_INIT, 4, new byte[4], response);

        byte[] data = new byte[(96 * 1024) + 8];
        MediaCmd.writeInt(96 * 1024, data, 0);
        data[8] = 0;
        data[9] = 0;
        data[10] = 1;
        data[11] = (byte) 0xBA;
        command.ExecuteMediaCommand(MediaCmd.MEDIACMD_PUSHBUFFER,
                data.length, data, response);

        byte[] poll = new byte[8];
        MediaCmd.writeInt(0, poll, 0);
        MediaCmd.writeInt(0x100, poll, 4);
        assertEquals(4, command.ExecuteMediaCommand(MediaCmd.MEDIACMD_PUSHBUFFER,
                poll.length, poll, response));
        assertEquals(-2, MediaCmd.readInt(0, response));
    }

    @Test
    public void dvdDrainComparesReadPositionWithCurrentFlushEpoch() throws IOException
    {
        MiniClient client = mock(MiniClient.class);
        MiniClientConnection connection = mock(MiniClientConnection.class);
        MiniPlayerPlugin player = mock(MiniPlayerPlugin.class);
        when(client.getCurrentConnection()).thenReturn(connection);
        when(connection.newPlayerPlugin("push:dvd")).thenReturn(player);
        when(player.getBufferLeft()).thenReturn(3 * 1024 * 1024);

        MediaCmd command = new MediaCmd(client);
        byte[] response = new byte[16];
        command.ExecuteMediaCommand(MediaCmd.MEDIACMD_INIT, 4, new byte[4], response);

        byte[] firstEpoch = dvdPushPacket(128 * 1024);
        command.ExecuteMediaCommand(MediaCmd.MEDIACMD_PUSHBUFFER,
                firstEpoch.length, firstEpoch, response);
        command.ExecuteMediaCommand(MediaCmd.MEDIACMD_FLUSH,
                0, new byte[0], response);

        byte[] secondEpoch = dvdPushPacket(96 * 1024);
        command.ExecuteMediaCommand(MediaCmd.MEDIACMD_PUSHBUFFER,
                secondEpoch.length, secondEpoch, response);
        when(player.getLastFileReadPos()).thenReturn(96L * 1024L);

        byte[] poll = new byte[8];
        MediaCmd.writeInt(0, poll, 0);
        MediaCmd.writeInt(0x100, poll, 4);
        command.ExecuteMediaCommand(MediaCmd.MEDIACMD_PUSHBUFFER,
                poll.length, poll, response);

        assertEquals(224L * 1024L, command.getDvdPushedBytes());
        assertEquals(96L * 1024L, command.getDvdEpochPushedBytes());
        assertEquals(-2, MediaCmd.readInt(0, response));
    }

    @Test
    public void dvdDrainCompletesImmediatelyAfterPostDataFlush() throws IOException
    {
        MiniClient client = mock(MiniClient.class);
        MiniClientConnection connection = mock(MiniClientConnection.class);
        MiniPlayerPlugin player = mock(MiniPlayerPlugin.class);
        when(client.getCurrentConnection()).thenReturn(connection);
        when(connection.newPlayerPlugin("push:dvd")).thenReturn(player);
        // Reproduce a retained authored still frame: neither the ordinary
        // free-space threshold nor the decoded-ahead threshold is satisfied.
        when(player.getBufferLeft()).thenReturn(3 * 1024 * 1024);
        when(player.getBufferedPlaybackAheadMillis()).thenReturn(30_000L);

        MediaCmd command = new MediaCmd(client);
        byte[] response = new byte[16];
        command.ExecuteMediaCommand(MediaCmd.MEDIACMD_INIT, 4, new byte[4], response);
        byte[] firstEpoch = dvdPushPacket(128 * 1024);
        command.ExecuteMediaCommand(MediaCmd.MEDIACMD_PUSHBUFFER,
                firstEpoch.length, firstEpoch, response);

        command.ExecuteMediaCommand(MediaCmd.MEDIACMD_FLUSH,
                0, new byte[0], response);
        verify(player).flush();

        byte[] poll = new byte[8];
        MediaCmd.writeInt(0, poll, 0);
        MediaCmd.writeInt(0x100, poll, 4);
        command.ExecuteMediaCommand(MediaCmd.MEDIACMD_PUSHBUFFER,
                poll.length, poll, response);

        assertEquals(0L, command.getDvdEpochPushedBytes());
        assertEquals(-2, MediaCmd.readInt(0, response));
    }

    private static byte[] dvdPushPacket(int payloadSize)
    {
        byte[] data = new byte[payloadSize + 8];
        MediaCmd.writeInt(payloadSize, data, 0);
        data[8] = 0;
        data[9] = 0;
        data[10] = 1;
        data[11] = (byte) 0xBA;
        return data;
    }

    @Test
    public void syntheticBandwidthProbeDoesNotCreateDvdPlayerAndKeepsDetailedReply()
    {
        MiniClient client = mock(MiniClient.class);
        MiniClientConnection connection = mock(MiniClientConnection.class);
        when(client.getCurrentConnection()).thenReturn(connection);

        boolean previous = MiniClientConnection.detailedBufferStats;
        MiniClientConnection.detailedBufferStats = true;
        try
        {
            MediaCmd command = new MediaCmd(client);
            byte[] response = new byte[16];
            command.ExecuteMediaCommand(MediaCmd.MEDIACMD_INIT, 4, new byte[4], response);

            int payloadSize = 16 * 1024;
            byte[] probe = new byte[payloadSize + 18];
            MediaCmd.writeInt(payloadSize, probe, 0);
            MediaCmd.writeInt(0, probe, 4);
            for (int i = 0; i < payloadSize; i++)
                probe[18 + i] = (byte) (i & 0xFF);

            assertEquals(9, command.ExecuteMediaCommand(MediaCmd.MEDIACMD_PUSHBUFFER,
                    probe.length, probe, response));
            verify(connection, never()).newPlayerPlugin(anyString());
        }
        finally
        {
            MiniClientConnection.detailedBufferStats = previous;
        }
    }
}
