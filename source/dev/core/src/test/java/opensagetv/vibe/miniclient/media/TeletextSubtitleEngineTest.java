package opensagetv.vibe.miniclient.media;

import org.junit.After;
import org.junit.Assume;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TeletextSubtitleEngineTest
{
    private final RecordingListener listener = new RecordingListener();

    @After public void stop() { TeletextSubtitleEngine.deactivate(listener); }

    @Test
    public void decodesPage888AcrossArbitraryDatasourceChunks()
    {
        byte[] stream = concat(
                packet(0, true, psi(pat(0x64))),
                packet(0x64, true, psi(pmt(0x12c))),
                packet(0x12c, true, pes(90000L, "VIBE TELETEXT WORKS")),
                packet(0x1fff, false, new byte[0]));

        TeletextSubtitleEngine.activate(listener);
        TeletextSubtitleEngine.observe("pull", 0L, stream, 0, 113);
        TeletextSubtitleEngine.observe("pull", 113L, stream, 113, stream.length - 113);

        assertEquals(1, listener.services.size());
        assertEquals(888, listener.services.get(0).page);
        assertTrue(listener.texts.toString(), listener.texts.contains("VIBE TELETEXT WORKS"));
    }

    /** Optional commissioning gate: -Dvibe.teletext.sample=/path/to/original.ts. */
    @Test
    public void decodesCommissionedBroadcastSampleWhenProvided() throws Exception
    {
        String path = System.getProperty("vibe.teletext.sample", "");
        Assume.assumeTrue(!path.isEmpty() && new File(path).isFile());
        TeletextSubtitleEngine.activate(listener);
        byte[] bytes = new byte[65536];
        long position = 0L;
        FileInputStream input = new FileInputStream(path);
        try
        {
            int read;
            while ((read = input.read(bytes)) > 0)
            {
                TeletextSubtitleEngine.observe("fixture", position, bytes, 0, read);
                position += read;
            }
        }
        finally { input.close(); }
        assertTrue("page-888 service missing", listener.hasPage(888));
        assertTrue("no decoded Teletext cue text", !listener.texts.isEmpty());
    }

    private static final class RecordingListener implements TeletextSubtitleEngine.Listener
    {
        final List<TeletextSubtitleEngine.Service> services = new ArrayList<TeletextSubtitleEngine.Service>();
        final List<String> texts = new ArrayList<String>();

        @Override public void onTeletextServicesChanged(TeletextSubtitleEngine.Service[] found)
        {
            services.clear();
            services.addAll(Arrays.asList(found));
            if (found.length > 0) TeletextSubtitleEngine.selectTrack(found[0].trackId);
        }

        @Override public void onTeletextCue(TeletextSubtitleEngine.Cue cue)
        {
            if (!cue.text.isEmpty()) texts.add(cue.text);
        }

        boolean hasPage(int page)
        {
            for (TeletextSubtitleEngine.Service service : services)
                if (service.page == page) return true;
            return false;
        }
    }

    private static byte[] pat(int pmtPid)
    {
        return new byte[]{0, (byte) 0xb0, 0x0d, 0, 1, (byte) 0xc1, 0, 0,
                0, 1, (byte) (0xe0 | (pmtPid >> 8)), (byte) pmtPid, 0, 0, 0, 0};
    }

    private static byte[] pmt(int pid)
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int sectionLength = 9 + 5 + 7 + 4;
        write(out, 2, 0xb0 | (sectionLength >> 8), sectionLength,
                0, 1, 0xc1, 0, 0, 0xe0 | (pid >> 8), pid, 0xf0, 0,
                6, 0xe0 | (pid >> 8), pid, 0xf0, 7,
                0x56, 5, 'e', 'n', 'g', 0x10, 0x88, 0, 0, 0, 0);
        return out.toByteArray();
    }

    private static byte[] pes(long pts, String text)
    {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        write(body, 0x80, 0x80, 5);
        byte[] encodedPts = encodePts(pts);
        body.write(encodedPts, 0, encodedPts.length);
        body.write(0x10);
        writeUnit(body, 0, "", true);
        writeUnit(body, 14, text, false);
        byte[] payload = body.toByteArray();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write(out, 0, 0, 1, 0xbd, payload.length >> 8, payload.length);
        out.write(payload, 0, payload.length);
        return out.toByteArray();
    }

    private static void writeUnit(ByteArrayOutputStream out, int row, String text,
            boolean header)
    {
        write(out, 3, 44, 0xf5, 0xe4);
        int address = row << 3; // magazine eight is encoded as zero.
        int encodedAddress = reverse8(address);
        out.write(HAMMING[(encodedAddress >> 4) & 15]);
        out.write(HAMMING[encodedAddress & 15]);
        if (header)
        {
            out.write(HAMMING[1]); // BCD 8, bit-reversed nibble
            out.write(HAMMING[1]);
            for (int i = 0; i < 6; i++) out.write(HAMMING[0]);
            for (int i = 0; i < 32; i++) out.write(reverse8(' '));
        }
        else
        {
            for (int i = 0; i < 40; i++)
                out.write(reverse8(i < text.length() ? text.charAt(i) : ' '));
        }
    }

    private static final int[] HAMMING = {0xA8, 0x0B, 0x26, 0x85, 0x92, 0x31,
            0x1C, 0xBF, 0x40, 0xE3, 0xCE, 0x6D, 0x7A, 0xD9, 0xF4, 0x57};

    private static int reverse8(int value)
    {
        value = ((value >>> 1) & 0x55) | ((value << 1) & 0xaa);
        value = ((value >>> 2) & 0x33) | ((value << 2) & 0xcc);
        return (((value >>> 4) & 15) | ((value << 4) & 0xf0)) & 0xff;
    }

    private static byte[] encodePts(long pts)
    {
        return new byte[]{(byte) (0x21 | (((pts >> 30) & 7) << 1)),
                (byte) (pts >> 22), (byte) ((((pts >> 15) & 0x7f) << 1) | 1),
                (byte) (pts >> 7), (byte) (((pts & 0x7f) << 1) | 1)};
    }

    private static byte[] psi(byte[] section)
    {
        byte[] result = new byte[section.length + 1];
        System.arraycopy(section, 0, result, 1, section.length);
        return result;
    }

    private static byte[] packet(int pid, boolean start, byte[] payload)
    {
        byte[] result = new byte[188];
        Arrays.fill(result, (byte) 0xff);
        result[0] = 0x47;
        result[1] = (byte) ((start ? 0x40 : 0) | ((pid >> 8) & 31));
        result[2] = (byte) pid;
        result[3] = 0x10;
        System.arraycopy(payload, 0, result, 4, Math.min(184, payload.length));
        return result;
    }

    private static byte[] concat(byte[]... values)
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] value : values) out.write(value, 0, value.length);
        return out.toByteArray();
    }

    private static void write(ByteArrayOutputStream out, int... values)
    {
        for (int value : values) out.write(value & 0xff);
    }
}
