package opensagetv.vibe.miniclient.android.video;

import java.util.Arrays;
import java.util.LinkedHashMap;

/** Bounded AAC-LC configuration captured before a queue can drop initial preroll.
 * No channel layout is guessed; samples, timestamps and read order are unchanged.
 */
public final class AacProgramConfig {
    private AacProgramConfig() {}

    public static boolean needed(byte[] asc) {
        return asc != null && asc.length == 2 && ((asc[0]&255)>>>3)==2
                && (((asc[0]&7)<<1)|((asc[1]>>>7)&1))<13
                && ((asc[1]>>>3)&15)==0 && (asc[1]&7)==0;
    }

    public static final class Config {
        private final byte[] asc;
        public final int channels;
        private Config(byte[] asc,int channels){this.asc=asc.clone();this.channels=channels;}
        public byte[] bytes(){return asc.clone();}
    }

    /** One OPENURL owns a store; never static/global or persisted across media. */
    public static final class Store {
        private final LinkedHashMap<String,Entry> tracks=new LinkedHashMap<>();
        public synchronized Config find(String track,byte[] header) {
            Entry entry=track==null?null:tracks.get(track);
            return needed(header)&&entry!=null&&Arrays.equals(header,entry.header)?entry.config:null;
        }
        private synchronized void remember(String track,byte[] header,Config config) {
            if(track==null||!needed(header))return;
            if(tracks.size()>=8&&!tracks.containsKey(track))tracks.remove(tracks.keySet().iterator().next());
            tracks.put(track,new Entry(header.clone(),config));
        }
    }
    private static final class Entry {
        final byte[] header;final Config config;
        Entry(byte[] header,Config config){this.header=header;this.config=config;}
    }

    /** Tee only bytes already read for this sample. Stores at most512 bytes. */
    public static final class Sample {
        private final Store store;private final String track;private final byte[] header;
        private final byte[] prefix=new byte[512];private int used,offered;
        public Sample(Store store,String track,byte[] header){this.store=store;this.track=track;this.header=header.clone();}
        public void observe(byte[] bytes,int offset,int length) {
            if(bytes==null||offset<0||length<0||offset>bytes.length-length)throw new IllegalArgumentException("AAC sample bounds");
            int copy=Math.min(length,prefix.length-used);
            System.arraycopy(bytes,offset,prefix,used,copy);used+=copy;
            offered=(int)Math.min(1048576L,(long)offered+length);
        }
        public Config finish(int size,int offset,boolean encrypted) {
            Config result=null;
            if(!encrypted&&offset==0&&size==offered&&offered<1048576)
                result=parse(header,Arrays.copyOf(prefix,used));
            used=0;offered=0;
            if(result!=null)store.remember(track,header,result);
            return result;
        }
    }

    static Config parse(byte[] asc,byte[] packet) {
        if(!needed(asc)||packet.length==0||((packet[0]&255)>>>5)!=5)return null;
        try {
            Bits bits=new Bits(packet,asc);bits.copy(4);
            if(bits.copy(2)!=1||bits.copy(4)!=(((asc[0]&7)<<1)|((asc[1]>>>7)&1)))return null;
            int front=bits.copy(4),side=bits.copy(4),back=bits.copy(4);
            int lfe=bits.copy(2),associated=bits.copy(3),cc=bits.copy(4);
            if(bits.copy(1)!=0)bits.copy(4);
            if(bits.copy(1)!=0)bits.copy(4);
            if(bits.copy(1)!=0)bits.copy(3);
            int channels=lfe;
            for(int i=0;i<front+side+back;i++){channels+=1+bits.copy(1);bits.copy(4);}
            for(int i=0;i<lfe+associated;i++)bits.copy(4);
            for(int i=0;i<cc;i++)bits.copy(5);
            if(channels<1||channels>8)return null;
            // Raw PCE follows3 bits; ASC PCE follows16. Align independently.
            bits.read=(bits.read+7)&~7;while((bits.write&7)!=0)bits.put(0);
            int comment=bits.copy(8);for(int i=0;i<comment;i++)bits.copy(8);
            return new Config(Arrays.copyOf(bits.output,(bits.write+7)/8),channels);
        } catch(IllegalArgumentException malformed){return null;}
    }

    private static final class Bits {
        final byte[] input,output=new byte[512];int read=3,write=16;
        Bits(byte[] input,byte[] asc){this.input=input;output[0]=asc[0];output[1]=asc[1];}
        int copy(int count) {
            if(count<0||count>8||read+count>input.length*8)throw new IllegalArgumentException("Truncated AAC PCE");
            int value=0;
            for(int i=0;i<count;i++){int bit=(input[read/8]>>>(7-(read&7)))&1;read++;put(bit);value=(value<<1)|bit;}
            return value;
        }
        void put(int bit){if(write>=output.length*8)throw new IllegalArgumentException("AAC PCE bound");output[write/8]|=bit<<(7-(write&7));write++;}
    }
}
