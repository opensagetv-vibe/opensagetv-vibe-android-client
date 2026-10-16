package opensagetv.vibe.miniclient.video;
import org.junit.Test;
import static org.junit.Assert.*;

public class Mpeg2OutputFieldMapTest {
    @Test public void timestampsIdentifyBothFieldsNotReleaseCallbackCount() {
        Mpeg2OutputFieldMap map=new Mpeg2OutputFieldMap();
        map.note(1_000_000);map.note(1_033_367);map.note(1_066_733);
        assertEquals(0,map.phase(1_000_000,30000.0/1001));
        assertEquals(1,map.phase(1_016_683,30000.0/1001));
        assertEquals(0,map.phase(1_033_367,30000.0/1001));
        // A dropped callback does not flip subsequent parity.
        assertEquals(0,map.phase(1_066_733,30000.0/1001));
        assertEquals(1,map.phase(1_083_417,30000.0/1001));
    }
    @Test public void reorderedPicturesMissingEvidenceAndSeekDoNotGuess() {
        Mpeg2OutputFieldMap map=new Mpeg2OutputFieldMap();
        map.note(1_000_000);map.note(1_080_000);map.note(1_040_000);
        assertEquals(1,map.phase(1_060_000,25));
        assertEquals(-1,map.phase(1_099_999,0));
        assertEquals(-1,map.phase(999_000,25));
        assertEquals(-1,map.phase(1_200_000,25));
        map.clear();assertEquals(-1,map.phase(1_060_000,25));
        for(int i=0;i<2049;i++)map.note(i*40_000L);
        assertEquals(-1,map.phase(0,25));
    }
}
