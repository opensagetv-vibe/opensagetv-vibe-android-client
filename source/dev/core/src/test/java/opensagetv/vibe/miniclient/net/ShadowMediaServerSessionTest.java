package opensagetv.vibe.miniclient.net;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.FutureTask;

import static org.junit.Assert.*;

public class ShadowMediaServerSessionTest
{
    @Test
    public void sendsOpenSizeOneByteProbeAndClose() throws Exception
    {
        final ServerSocket server = new ServerSocket(0);
        final List<String> commands = new ArrayList<String>();
        FutureTask<Void> worker = new FutureTask<Void>(() -> {
            try (Socket socket = server.accept();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
                 BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8")))
            {
                String line;
                while ((line = reader.readLine()) != null)
                {
                    commands.add(line);
                    if (line.startsWith("SIZE")) writer.write("123456\r\n");
                    else if (line.startsWith("READ ")) writer.write("X");
                    else writer.write("OK\r\n");
                    writer.flush();
                    if (line.equals("CLOSE")) break;
                }
            }
            return null;
        });
        Thread thread = new Thread(worker, "shadow-media-test");
        thread.start();

        ShadowMediaServerSession session = new ShadowMediaServerSession(
                "127.0.0.1", server.getLocalPort(), 1000, 1000);
        assertEquals(123456L, session.open("stv://server/D:\\TV\\Show.ts"));
        assertTrue(session.isConnected());
        assertTrue(session.isOpenSent());
        assertTrue(session.isSizeSent());
        assertEquals(0L, session.getReadBytes());
        assertEquals(1, session.probeRead(456));
        assertEquals(1, session.probeRead(789));
        assertEquals(2L, session.getReadBytes());
        session.close();
        worker.get();
        server.close();

        assertEquals(5, commands.size());
        assertEquals("OPEN D:\\TV\\Show.ts", commands.get(0));
        assertEquals("SIZE", commands.get(1));
        assertEquals("READ 456 1", commands.get(2));
        assertEquals("READ 789 1", commands.get(3));
        assertEquals("CLOSE", commands.get(4));
    }
}
