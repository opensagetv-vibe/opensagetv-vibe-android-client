package opensagetv.vibe.miniclient.android.video;
import org.junit.Test;
import static org.junit.Assert.*;
public class MimOutputStatusTest {
    @Test public void requestedBackendIsNotActualHardwareEvidence(){
        String result=MimOutputStatus.summary("{\"outputSettings\":{\"hardwareBackend\":\"qsv\"}}");
        assertTrue(result.contains("Requested hardware encoder: qsv"));assertTrue(result.contains("encoding: Unknown"));
    }
    @Test public void requestedHardwareIsNotExecutionProof(){assertTrue(MimOutputStatus.summary("{\"outputSettings\":{\"encoderPolicy\":\"hardware_only\"}}").contains("encoding: Unknown"));}
    @Test public void mixedPipelineSeparatesHardwareEncodeFromSoftwareDecode(){
        String result=MimOutputStatus.summary("{\"execution\":{\"path\":\"mixed\",\"encoder\":\"h264_qsv\",\"hardwareEncode\":true,\"hardwareDecode\":false}}");
        assertTrue(result.contains("encoding: Hardware (h264_qsv)"));assertTrue(result.contains("decoding: Software"));
    }
    @Test public void softwareAndCopyAreDifferent(){
        assertTrue(MimOutputStatus.summary("{\"execution\":{\"path\":\"software\",\"encoder\":\"libx264\",\"hardwareEncode\":false}}").contains("Software (libx264)"));
        assertTrue(MimOutputStatus.summary("{\"execution\":{\"path\":\"copy\"}}").contains("None (Copy/Remux)"));
    }
    @Test public void pendingFalseFlagsAreNotSoftwareEvidence(){assertTrue(MimOutputStatus.summary("{\"execution\":{\"path\":\"unknown\",\"hardwareEncode\":false,\"hardwareDecode\":false}}").contains("decoding: Unknown"));}
    @Test public void unknownOutputIsNotReplacedWithRequestedTargets(){assertFalse(MimOutputStatus.summary("{\"videoTargetKbps\":4000}").contains("4000"));}
    @Test public void secretsAndPathsAreNotExported(){String value=MimOutputStatus.summary("{\"sessionToken\":\"secret\",\"execution\":{\"encoder\":\"/private/path\"},\"source\":\"smb://password\"}");assertFalse(value.contains("secret"));assertFalse(value.contains("password"));assertFalse(value.contains("/private"));}
}
