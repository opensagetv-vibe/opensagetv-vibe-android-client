package opensagetv.vibe.miniclient.android.video;
import org.junit.Test;
import static org.junit.Assert.*;
public class CodecFailureDetailTest {
    public static final class AudioFormat {
        public String sampleMimeType="audio/mp4a-latm";
        public int channelCount=0;
        public java.util.List<byte[]> initializationData=java.util.Collections.singletonList(new byte[]{0x11,(byte)0x80});
    }
    public static final class InitFailure extends Exception {
        public AudioFormat rendererFormat=new AudioFormat();
        public String mimeType="audio/mp4a-latm",diagnosticInfo="android.media.MediaCodec.error_neg_1010",decoderName="OMX.google.aac.decoder";
        InitFailure(){super("https://secret.example/session?password=never");}
    }
    @Test public void closedCauseAttributesCodecWithoutMessages(){
        String detail=CodecFailureDetail.describe(new RuntimeException("private media path",new InitFailure()));
        assertTrue(detail.contains("mimeType=audio/mp4a-latm"));assertTrue(detail.contains("decoderName=OMX.google.aac.decoder"));
        assertTrue(detail.contains("channels=0"));assertTrue(detail.contains("ascBytes=2 ascChannels=0"));
        assertFalse(detail.contains("secret"));assertFalse(detail.contains("private"));assertFalse(detail.contains("password"));
    }
    @Test public void rejectsArbitraryFieldsAndBoundsCycles(){
        InitFailure error=new InitFailure();error.decoderName="https://secret.example";
        assertFalse(CodecFailureDetail.describe(error).contains("secret"));
        RuntimeException a=new RuntimeException(),b=new RuntimeException();a.initCause(b);b.initCause(a);
        assertTrue(CodecFailureDetail.describe(a).length()<200);assertEquals("",CodecFailureDetail.describe(null));
    }
}
