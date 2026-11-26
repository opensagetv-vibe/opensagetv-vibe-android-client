package opensagetv.vibe.miniclient.android.video;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

public class AacProgramConfigTest {
    private static final byte[] HEADER={0x11,(byte)0x80};
    private static String fields(boolean rear) {
        return "0000"+"01"+"0011"+"0010"+(rear?"0000":"0001")+(rear?"0001":"0000")
                +"01"+"000"+"0000"+"000"+"10000"+"00000"+"10001"+"0000";
    }
    private static byte[] packed(String bits,byte[] comment) {
        while(bits.length()%8!=0)bits+="0";
        byte[] out=new byte[bits.length()/8+1+comment.length];
        for(int i=0;i<bits.length();i++)if(bits.charAt(i)=='1')out[i/8]|=1<<(7-i%8);
        out[bits.length()/8]=(byte)comment.length;
        System.arraycopy(comment,0,out,bits.length()/8+1,comment.length);return out;
    }
    @Test public void actualSideAndRearPceHaveIndependentAscAlignment() {
        for(boolean rear:new boolean[]{false,true}) {
            byte[] raw=packed("101"+fields(rear),new byte[]{65,66});
            AacProgramConfig.Config config=AacProgramConfig.parse(HEADER,raw);
            assertNotNull(config);assertEquals(6,config.channels);
            assertArrayEquals(packed("0001000110000000"+fields(rear),new byte[]{65,66}),config.bytes());
            for(int length=0;length<raw.length;length++)assertNull(AacProgramConfig.parse(HEADER,Arrays.copyOf(raw,length)));
        }
    }
    @Test public void onlyIncompleteStandardLcIsEligible() {
        assertTrue(AacProgramConfig.needed(HEADER));
        for(byte[] header:new byte[][]{null,new byte[0],{0x11,(byte)0x90},{0x11,(byte)0x80,0},{0x29,(byte)0x80},{0x11,(byte)0x84},{0x16,(byte)0x80}})
            assertFalse(AacProgramConfig.needed(header));
        assertNull(AacProgramConfig.parse(HEADER,new byte[]{(byte)0xe0}));
        byte[] mismatched=packed("1010000010100"+fields(false),new byte[0]);
        assertNull(AacProgramConfig.parse(HEADER,mismatched));
    }
    @Test public void splitSampleIsObservedWithoutChangingItAndStateIsIsolated() {
        byte[] raw=packed("101"+fields(false),new byte[0]);byte[] original=raw.clone();
        AacProgramConfig.Store store=new AacProgramConfig.Store();
        AacProgramConfig.Sample sample=new AacProgramConfig.Sample(store,"1/15",HEADER);
        for(int i=0;i<raw.length;i++)sample.observe(raw,i,1);
        AacProgramConfig.Config config=sample.finish(raw.length,0,false);
        assertEquals(6,config.channels);assertArrayEquals(original,raw);
        assertNotNull(store.find("1/15",HEADER));
        byte[] exposed=config.bytes();exposed[0]=0;
        assertEquals(0x11,store.find("1/15",HEADER).bytes()[0]);
        assertNull(store.find("other",HEADER));assertNull(store.find("1/15",new byte[]{0x11,(byte)0x90}));
        assertNull(new AacProgramConfig.Store().find("1/15",HEADER));
    }
    @Test public void ambiguousOffsetsEncryptionAndWrongLengthsNeverCreateConfiguration() {
        byte[] raw=packed("101"+fields(false),new byte[0]);
        for(int mode=0;mode<3;mode++) {
            AacProgramConfig.Store store=new AacProgramConfig.Store();AacProgramConfig.Sample sample=new AacProgramConfig.Sample(store,"x",HEADER);
            sample.observe(raw,0,raw.length);
            assertNull(sample.finish(raw.length+(mode==0?1:0),mode==1?1:0,mode==2));
            assertNull(store.find("x",HEADER));
            sample.observe(raw,0,raw.length);assertNotNull(sample.finish(raw.length,0,false));
        }
    }
    @Test public void trackStoreIsBoundedAndLargeSamplesKeepOnlyTheirPrefix() {
        AacProgramConfig.Store store=new AacProgramConfig.Store();
        byte[] raw=Arrays.copyOf(packed("101"+fields(false),new byte[0]),2048);
        for(int i=0;i<9;i++){AacProgramConfig.Sample sample=new AacProgramConfig.Sample(store,"t"+i,HEADER);sample.observe(raw,0,raw.length);assertNotNull(sample.finish(raw.length,0,false));}
        assertNull(store.find("t0",HEADER));assertNotNull(store.find("t8",HEADER));
    }
}
