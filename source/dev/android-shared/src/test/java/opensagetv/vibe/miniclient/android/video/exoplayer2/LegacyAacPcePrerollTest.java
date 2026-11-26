package opensagetv.vibe.miniclient.android.video.exoplayer2;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.SeekMap;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.extractor.ts.AdtsReader;
import com.google.android.exoplayer2.extractor.ts.TsPayloadReader;
import com.google.android.exoplayer2.upstream.DataReader;
import com.google.android.exoplayer2.util.ParsableByteArray;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import opensagetv.vibe.miniclient.android.video.AacProgramConfig;
import org.junit.Test;
import static org.junit.Assert.*;

/** Exact legacy reader boundary: the first PCE can precede presentation zero. */
public class LegacyAacPcePrerollTest {
    @Test public void completeFormatIsAvailableBeforePrerollCanBeDiscarded() throws Exception {
        Capture capture=new Capture();
        AacProgramConfig.Store store=new AacProgramConfig.Store();
        feed(AacPceHlsExtractorFactory.wrapOutput(capture,store));
        assertTrue(capture.formatsAtMetadata.get(0).initializationData.get(0).length>2);
        assertEquals(6,capture.format.channelCount);
        assertArrayEquals(pce(),capture.payloads.get(0));
        assertEquals(Long.valueOf(-21333),capture.times.get(0));
        assertEquals(Long.valueOf(0),capture.times.get(1));
        assertNotNull(store.find(capture.format.id,new byte[]{0x11,(byte)0x80}));
    }
    @Test public void readerKeepsPceInPrerollPayloadButNotInAsc() throws Exception {
        Capture capture=new Capture();
        feed(capture);
        assertEquals(2,capture.format.initializationData.get(0).length);
        assertEquals(0,capture.format.channelCount);
        assertEquals(2,capture.payloads.size());
        assertEquals(5,(capture.payloads.get(0)[0]&255)>>>5);
        assertEquals(Long.valueOf(-21333),capture.times.get(0));
        assertEquals(Long.valueOf(0),capture.times.get(1));
        assertEquals(7,(capture.payloads.get(1)[0]&255)>>>5);
    }

    static void feed(ExtractorOutput output) throws Exception {
        AdtsReader reader=new AdtsReader(false);
        reader.createTracks(output,new TsPayloadReader.TrackIdGenerator(1,15,8192));
        reader.packetStarted(-21333,TsPayloadReader.FLAG_DATA_ALIGNMENT_INDICATOR);
        ByteArrayOutputStream stream=new ByteArrayOutputStream();
        stream.write(adts(pce()));stream.write(adts(new byte[]{(byte)0xe0}));
        byte[] bytes=stream.toByteArray();
        // Deliberately fragment headers/body to exercise the real reader.
        for(int i=0;i<bytes.length;i+=3)
            reader.consume(new ParsableByteArray(java.util.Arrays.copyOfRange(bytes,i,Math.min(i+3,bytes.length))));
    }

    public static byte[] pce() {
        String bits="101"+"0000"+"01"+"0011"+"0010"+"0001"+"0000"+"01"+"000"+"0000"+"000"
                +"10000"+"00000"+"10001"+"0000";
        while(bits.length()%8!=0)bits+="0";
        bits+="00000000"+"11100000"; // no comment, ID_END and padding
        byte[] bytes=new byte[bits.length()/8];
        for(int i=0;i<bits.length();i++)if(bits.charAt(i)=='1')bytes[i/8]|=1<<(7-i%8);
        return bytes;
    }

    public static byte[] adts(byte[] raw) {
        int size=raw.length+7;
        byte[] bytes={(byte)0xff,(byte)0xf1,0x4c,(byte)(size>>>11),(byte)(size>>>3),(byte)((size<<5)|31),(byte)0xfc};
        byte[] out=java.util.Arrays.copyOf(bytes,size);System.arraycopy(raw,0,out,7,raw.length);return out;
    }

    static final class Capture implements ExtractorOutput,TrackOutput {
        Format format;final List<byte[]> payloads=new ArrayList<>();final List<Long> times=new ArrayList<>();
        final List<Format> formatsAtMetadata=new ArrayList<>();
        final ByteArrayOutputStream body=new ByteArrayOutputStream();
        @Override public TrackOutput track(int id,int type){assertEquals(C.TRACK_TYPE_AUDIO,type);return this;}
        @Override public void endTracks(){}
        @Override public void seekMap(SeekMap map){}
        @Override public void format(Format value){format=value;}
        @Override public int sampleData(DataReader input,int length,boolean allowEnd,int part)throws IOException {
            byte[] b=new byte[length];int read=input.read(b,0,length);if(read>0)body.write(b,0,read);return read;
        }
        @Override public void sampleData(ParsableByteArray input,int length,int part){body.write(input.getData(),input.getPosition(),length);input.skipBytes(length);}
        @Override public void sampleMetadata(long time,int flags,int size,int offset,CryptoData crypto){formatsAtMetadata.add(format);payloads.add(body.toByteArray());times.add(time);body.reset();}
    }
}
