package opensagetv.vibe.miniclient.android.video;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import opensagetv.vibe.miniclient.video.LegacyExtenderCaptionBridge;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FixedCaptionSideChannelClientTest
{
    private static final String SESSION_TOKEN =
            "abcdef0123456789abcdef0123456789abcdef0123456789";

    @Test
    public void parsesBoundedCaptionPackets() throws Exception
    {
        FixedCaptionSideChannelClient.CaptionBatch batch =
                FixedCaptionSideChannelClient.parseCaptions(
                        "{\"contractVersion\":1,\"packets\":[" +
                                "[8,1250,\"04c849\"],[9,1283,\"05d061\"]]," +
                                "\"cursor\":9,\"reset\":false}");

        assertEquals(2, batch.packets.length);
        assertEquals(8L, batch.packets[0].sequence);
        assertEquals(1250L, batch.packets[0].ptsMs);
        assertArrayEquals(new byte[] { 0x04, (byte) 0xc8, 0x49 },
                batch.packets[0].data);
        assertEquals(9L, batch.cursor);
        assertFalse(batch.reset);
    }

    @Test
    public void retainsServerCursorResetSignal() throws Exception
    {
        FixedCaptionSideChannelClient.CaptionBatch batch =
                FixedCaptionSideChannelClient.parseCaptions(
                        "{\"contractVersion\":1,\"packets\":[]," +
                                "\"cursor\":31,\"reset\":true}");
        assertEquals(31L, batch.cursor);
        assertTrue(batch.reset);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsUnknownContract() throws Exception
    {
        FixedCaptionSideChannelClient.parseCaptions(
                "{\"contractVersion\":2,\"packets\":[],\"cursor\":0}");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsMalformedOrUnboundedPayload() throws Exception
    {
        FixedCaptionSideChannelClient.parseCaptions(
                "{\"contractVersion\":1,\"packets\":[[1,0,\"not-hex\"]]," +
                        "\"cursor\":1}");
    }

    @Test
    public void reservesClaimsForwardsAndTearsDownWithoutMediaData() throws Exception
    {
        final AtomicInteger teardownCount = new AtomicInteger();
        SimpleHttpServer server = new SimpleHttpServer(teardownCount);
        server.start();

        FixedCaptionSideChannelClient client = new FixedCaptionSideChannelClient();
        Object owner = new Object();
        CountDownLatch forwarded = new CountDownLatch(1);
        LegacyExtenderCaptionBridge bridge = new LegacyExtenderCaptionBridge(
                new LegacyExtenderCaptionBridge.Sink()
                {
                    @Override public void postSubtitleInfo(long pts45Khz,
                            long duration45Khz, byte[] data, int flags)
                    {
                        if ((flags & LegacyExtenderCaptionBridge.CC_SUBTITLE) != 0 &&
                                data != null && data.length == 8)
                            forwarded.countDown();
                    }
                });
        try
        {
            client.startForTest("127.0.0.1", server.port());
            assertTrue(client.attach(owner, bridge));
            client.updatePlaybackClock(owner, 2_000L);
            assertTrue("caption record was not forwarded", forwarded.await(4, TimeUnit.SECONDS));
            assertEquals("active", client.stateForDiagnostics());
            client.detach(owner, true);
            long deadline = System.currentTimeMillis() + 2_000L;
            while (teardownCount.get() == 0 && System.currentTimeMillis() < deadline)
                Thread.sleep(25L);
            assertEquals(1, teardownCount.get());
        }
        finally
        {
            client.stop();
            server.close();
        }
    }

    /** Dependency-free loopback HTTP stub; Android unit tests use a bootclasspath without jdk.httpserver. */
    private static final class SimpleHttpServer implements AutoCloseable, Runnable
    {
        private final ServerSocket socket;
        private final AtomicInteger teardownCount;
        private final AtomicBoolean running = new AtomicBoolean(true);
        private Thread thread;

        SimpleHttpServer(AtomicInteger teardownCount) throws IOException
        {
            this.socket = new ServerSocket(0);
            this.teardownCount = teardownCount;
        }

        int port() { return socket.getLocalPort(); }

        void start()
        {
            thread = new Thread(this, "fixed-caption-test-http");
            thread.setDaemon(true);
            thread.start();
        }

        @Override public void run()
        {
            while (running.get())
            {
                try
                {
                    handle(socket.accept());
                }
                catch (IOException failure)
                {
                    if (running.get()) throw new RuntimeException(failure);
                }
            }
        }

        private void handle(Socket client) throws IOException
        {
            try
            {
                BufferedReader reader = new BufferedReader(new InputStreamReader(
                        client.getInputStream(), StandardCharsets.US_ASCII));
                String request = reader.readLine();
                String line;
                while ((line = reader.readLine()) != null && !line.isEmpty()) { }
                String path = request == null ? "" : request.split(" ")[1];
                if (path.startsWith("/v1/capabilities"))
                    reply(client, 200, "{\"contractVersion\":1,\"state\":\"ready\"," +
                            "\"reservationAvailable\":true}");
                else if (path.startsWith("/v1/sessions/reserve"))
                    reply(client, 200, "{\"contractVersion\":1," +
                            "\"reservationToken\":\"" + SESSION_TOKEN +
                            "\",\"expiresInMs\":60000}");
                else if (path.startsWith("/v1/sessions/claim"))
                    reply(client, 200, "{\"contractVersion\":1," +
                            "\"sessionToken\":\"" + SESSION_TOKEN + "\"}");
                else if (path.startsWith("/v1/captions"))
                    reply(client, 200, "{\"contractVersion\":1," +
                            "\"packets\":[[1,1250,\"04c849\"]]," +
                            "\"cursor\":1,\"reset\":false}");
                else if (path.startsWith("/v1/sessions/status"))
                    reply(client, 200, "{\"contractVersion\":1," +
                            "\"state\":\"active\",\"nextCursor\":2}");
                else if (path.startsWith("/v1/sessions/teardown"))
                {
                    teardownCount.incrementAndGet();
                    reply(client, 200, "{\"ok\":true,\"state\":\"released\"}");
                }
                else
                    reply(client, 404, "{\"ok\":false}");
            }
            finally { client.close(); }
        }

        private static void reply(Socket client, int status, String body) throws IOException
        {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            String reason = status == 200 ? "OK" : status == 401 ? "Unauthorized" : "Not Found";
            OutputStream output = client.getOutputStream();
            output.write(("HTTP/1.1 " + status + " " + reason + "\r\n" +
                    "Content-Type: application/json\r\n" +
                    "Content-Length: " + bytes.length + "\r\n" +
                    "Connection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            output.write(bytes);
            output.flush();
        }

        @Override public void close() throws IOException
        {
            running.set(false);
            socket.close();
            if (thread != null)
            {
                try { thread.join(1_000L); }
                catch (InterruptedException interrupted)
                {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
}
