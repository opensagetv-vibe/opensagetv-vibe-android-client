package opensagetv.vibe.miniclient.android.video.media3;

import androidx.media3.common.C;
import androidx.media3.common.DataReader;
import androidx.media3.common.Format;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.extractor.ExtractorOutput;
import androidx.media3.extractor.SeekMap;
import androidx.media3.extractor.TrackOutput;
import androidx.media3.extractor.ts.AdtsReader;
import androidx.media3.extractor.ts.TsPayloadReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import opensagetv.vibe.miniclient.android.video.AacProgramConfig;
import opensagetv.vibe.miniclient.android.video.exoplayer2.LegacyAacPcePrerollTest;
import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import static org.junit.Assert.*;

/** Public extractor boundary, including preroll which the player may discard. */
public class Media3AacPcePrerollTest {
    private static final byte[] HEADER={0x11,(byte)0x80};
    // Public upstream test hook: keep strict bounds without consulting the
    // mockable Android jar's null Build.FINGERPRINT in this plain JVM suite.
    @Before public void enforceParserBounds(){ParsableByteArray.setShouldEnforceLimitOnLegacyMethods(true);}
    @After public void restoreParserDefault(){ParsableByteArray.setShouldEnforceLimitOnLegacyMethods(null);}

    @Test public void realReaderCompletesBeforeNegativePrerollMetadata() throws Exception {
        Capture target=new Capture();
        AdtsReader reader=new AdtsReader(false,null);
        reader.createTracks(AacPceHlsExtractorFactory.wrapOutput(target,new AacProgramConfig.Store()),
                new TsPayloadReader.TrackIdGenerator(1,15,8192));
        reader.packetStarted(-21333,TsPayloadReader.FLAG_DATA_ALIGNMENT_INDICATOR);
        byte[] raw=LegacyAacPcePrerollTest.pce();
        ByteArrayOutputStream stream=new ByteArrayOutputStream();
        stream.write(LegacyAacPcePrerollTest.adts(raw));
        stream.write(LegacyAacPcePrerollTest.adts(new byte[]{(byte)0xe0}));
        byte[] data=stream.toByteArray();
        for(int i=0;i<data.length;i+=3)
            reader.consume(new ParsableByteArray(Arrays.copyOfRange(data,i,Math.min(i+3,data.length))));
        assertEquals(6,target.formats.get(0).channelCount);
        assertTrue(target.formats.get(0).initializationData.get(0).length>2);
        assertArrayEquals(raw,target.payloads.get(0));
        assertEquals(Arrays.asList(-21333L,0L),target.times);
    }

    @Test public void dataReaderIsReadOnceAndCacheSurvivesExtractorRecreationOnlyWithinPlayback() throws Exception {
        AacProgramConfig.Store store=new AacProgramConfig.Store();
        Capture target=new Capture();
        TrackOutput track=AacPceHlsExtractorFactory.wrapOutput(target,store).track(7,C.TRACK_TYPE_AUDIO);
        Format incomplete=new Format.Builder().setId("audio7").setSampleMimeType("audio/mp4a-latm")
                .setChannelCount(0).setSampleRate(48000).setInitializationData(Collections.singletonList(HEADER)).build();
        track.format(incomplete);
        byte[] raw=LegacyAacPcePrerollTest.pce();
        int[] reads={0};
        assertEquals(raw.length,track.sampleData((bytes,offset,count)->{
            reads[0]++;System.arraycopy(raw,0,bytes,offset,raw.length);return raw.length;
        },raw.length,false,TrackOutput.SAMPLE_DATA_PART_MAIN));
        track.sampleMetadata(-21333,C.BUFFER_FLAG_KEY_FRAME,raw.length,0,null);
        assertEquals(1,reads[0]);assertEquals(6,target.formats.get(0).channelCount);
        Capture recreated=new Capture();
        AacPceHlsExtractorFactory.wrapOutput(recreated,store).track(7,C.TRACK_TYPE_AUDIO).format(incomplete);
        assertEquals(6,recreated.format.channelCount);
        Capture differentPlayback=new Capture();
        AacPceHlsExtractorFactory.wrapOutput(differentPlayback,new AacProgramConfig.Store())
                .track(7,C.TRACK_TYPE_AUDIO).format(incomplete);
        assertNull(differentPlayback.format);
    }

    @Test public void ordinaryAudioAndVideoPassThroughAndMissingPceDoesNotHoldMetadata() {
        Capture target=new Capture();ExtractorOutput output=AacPceHlsExtractorFactory.wrapOutput(target,new AacProgramConfig.Store());
        assertSame(target,output.track(2,C.TRACK_TYPE_VIDEO));
        TrackOutput audio=output.track(3,C.TRACK_TYPE_AUDIO);
        Format ac3=new Format.Builder().setSampleMimeType("audio/ac3").setChannelCount(6).build();
        audio.format(ac3);assertSame(ac3,target.format);
        Format stereo=new Format.Builder().setSampleMimeType("audio/mp4a-latm")
                .setInitializationData(Collections.singletonList(new byte[]{0x11,(byte)0x90})).setChannelCount(2).build();
        audio.format(stereo);assertSame(stereo,target.format);
        Format incomplete=stereo.buildUpon().setChannelCount(0).setInitializationData(Collections.singletonList(HEADER)).build();
        audio.format(incomplete);
        audio.sampleData(new ParsableByteArray(new byte[]{(byte)0xe0}),1,TrackOutput.SAMPLE_DATA_PART_MAIN);
        audio.sampleMetadata(123,0,1,0,null);
        assertSame(incomplete,target.format);assertEquals(Long.valueOf(123),target.times.get(0));
        assertArrayEquals(new byte[]{(byte)0xe0},target.payloads.get(0));
    }

    private static final class Capture implements ExtractorOutput,TrackOutput {
        Format format;final List<Format> formats=new ArrayList<>();
        final List<byte[]> payloads=new ArrayList<>();final List<Long> times=new ArrayList<>();
        final ByteArrayOutputStream body=new ByteArrayOutputStream();
        @Override public TrackOutput track(int id,int type){return this;}
        @Override public void endTracks(){}
        @Override public void seekMap(SeekMap map){}
        @Override public void format(Format value){format=value;}
        @Override public int sampleData(DataReader input,int length,boolean allowEnd,int part)throws IOException {
            byte[] bytes=new byte[length];int read=input.read(bytes,0,length);if(read>0)body.write(bytes,0,read);return read;
        }
        @Override public void sampleData(ParsableByteArray input,int length,int part){body.write(input.getData(),input.getPosition(),length);input.skipBytes(length);}
        @Override public void sampleMetadata(long time,int flags,int size,int offset,CryptoData crypto){formats.add(format);payloads.add(body.toByteArray());times.add(time);body.reset();}
    }
}
