package opensagetv.vibe.miniclient.android.video.media3;

import android.net.Uri;
import androidx.media3.common.C;
import androidx.media3.common.DataReader;
import androidx.media3.common.Format;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.common.util.TimestampAdjuster;
import androidx.media3.exoplayer.analytics.PlayerId;
import androidx.media3.exoplayer.hls.DefaultHlsExtractorFactory;
import androidx.media3.exoplayer.hls.HlsExtractorFactory;
import androidx.media3.exoplayer.hls.HlsMediaChunkExtractor;
import androidx.media3.extractor.ExtractorInput;
import androidx.media3.extractor.ExtractorOutput;
import androidx.media3.extractor.SeekMap;
import androidx.media3.extractor.TrackOutput;
import androidx.media3.extractor.text.SubtitleParser;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import opensagetv.vibe.miniclient.android.video.AacProgramConfig;

/** Same bounded configuration policy as legacy Exo, using public Media3 APIs. */
final class AacPceHlsExtractorFactory implements HlsExtractorFactory {
    private HlsExtractorFactory delegate=new DefaultHlsExtractorFactory();
    private final AacProgramConfig.Store store;
    AacPceHlsExtractorFactory(AacProgramConfig.Store store){this.store=store;}
    @Override public HlsMediaChunkExtractor createExtractor(Uri uri,Format format,List<Format> captions,
            TimestampAdjuster adjuster,Map<String,List<String>> headers,ExtractorInput input,PlayerId id)throws IOException {
        return wrap(delegate.createExtractor(uri,format,captions,adjuster,headers,input,id));
    }
    @Override public HlsExtractorFactory setSubtitleParserFactory(SubtitleParser.Factory factory){delegate=delegate.setSubtitleParserFactory(factory);return this;}
    @Override public HlsExtractorFactory experimentalParseSubtitlesDuringExtraction(boolean enabled){delegate=delegate.experimentalParseSubtitlesDuringExtraction(enabled);return this;}
    @Override public HlsExtractorFactory experimentalSetCodecsToParseWithinGopSampleDependencies(int codecs){delegate=delegate.experimentalSetCodecsToParseWithinGopSampleDependencies(codecs);return this;}
    @Override public Format getOutputTextFormat(Format format){return delegate.getOutputTextFormat(format);}
    private HlsMediaChunkExtractor wrap(final HlsMediaChunkExtractor source) {
        return new HlsMediaChunkExtractor() {
            @Override public void init(ExtractorOutput out){source.init(wrapOutput(out,store));}
            @Override public boolean read(ExtractorInput in)throws IOException{return source.read(in);}
            @Override public boolean isPackedAudioExtractor(){return source.isPackedAudioExtractor();}
            @Override public boolean isReusable(){return source.isReusable();}
            @Override public HlsMediaChunkExtractor recreate(){return wrap(source.recreate());}
            @Override public void onTruncatedSegmentParsed(){source.onTruncatedSegmentParsed();}
        };
    }
    static ExtractorOutput wrapOutput(ExtractorOutput output,AacProgramConfig.Store store) {
        return new ExtractorOutput() {
            @Override public TrackOutput track(int id,int type) {
                TrackOutput track=output.track(id,type);
                return type==C.TRACK_TYPE_AUDIO?new AudioOutput(track,store):track;
            }
            @Override public void endTracks(){output.endTracks();}
            @Override public void seekMap(SeekMap map){output.seekMap(map);}
        };
    }
    private static final class AudioOutput implements TrackOutput {
        private final TrackOutput target;private final AacProgramConfig.Store store;
        private Format format;private AacProgramConfig.Sample sample;private boolean sent;
        AudioOutput(TrackOutput target,AacProgramConfig.Store store){this.target=target;this.store=store;}
        @Override public void durationUs(long duration){target.durationUs(duration);}
        @Override public void format(Format value) {
            format=value;sample=null;sent=false;
            if("audio/mp4a-latm".equals(value.sampleMimeType)&&!value.initializationData.isEmpty()
                    &&AacProgramConfig.needed(value.initializationData.get(0))) {
                byte[] header=value.initializationData.get(0);
                AacProgramConfig.Config known=store.find(value.id,header);
                if(known==null){sample=new AacProgramConfig.Sample(store,value.id,header);return;}
                format=complete(value,known);
            }
            target.format(format);sent=true;
        }
        private static Format complete(Format value,AacProgramConfig.Config config) {
            List<byte[]> init=new ArrayList<>(value.initializationData);init.set(0,config.bytes());
            return value.buildUpon().setInitializationData(init).setChannelCount(config.channels).build();
        }
        @Override public int sampleData(DataReader input,int length,boolean allowEnd,int part)throws IOException {
            AacProgramConfig.Sample current=sample;
            if(current==null||part!=SAMPLE_DATA_PART_MAIN)return target.sampleData(input,length,allowEnd,part);
            return target.sampleData((bytes,offset,count)->{
                int read=input.read(bytes,offset,count);if(read>0)current.observe(bytes,offset,read);return read;
            },length,allowEnd,part);
        }
        @Override public void sampleData(ParsableByteArray input,int length,int part) {
            if(sample!=null&&part==SAMPLE_DATA_PART_MAIN)sample.observe(input.getData(),input.getPosition(),length);
            target.sampleData(input,length,part);
        }
        @Override public void sampleMetadata(long time,int flags,int size,int offset,CryptoData crypto) {
            if(sample!=null) {
                AacProgramConfig.Config config=sample.finish(size,offset,crypto!=null);
                if(config!=null){format=complete(format,config);target.format(format);sent=true;sample=null;}
                else if(!sent){target.format(format);sent=true;}
            }
            target.sampleMetadata(time,flags,size,offset,crypto);
        }
    }
}
