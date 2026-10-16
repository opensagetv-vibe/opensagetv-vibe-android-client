package opensagetv.vibe.miniclient.android.diagnostics;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.Decoder;
import org.junit.Test;
import static org.junit.Assert.*;
public class DiagnosticUrlQrTest {
    @Test public void exactUrlRoundTripAndQuietZone() throws Exception {
        String url="http://192.168.10.25:8272/";BitMatrix qr=DiagnosticUrlQr.encode(url);
        int n=qr.getWidth()-8;boolean[][] modules=new boolean[n][n];
        for(int y=0;y<n;y++)for(int x=0;x<n;x++)modules[y][x]=qr.get(x+4,y+4);
        assertEquals(url,new Decoder().decode(modules).getText());
        for(int p=0;p<qr.getWidth();p++){assertFalse(qr.get(p,0));assertFalse(qr.get(0,p));}
    }
    @Test(expected=IllegalArgumentException.class) public void neverEncodeTokenUrl() throws Exception {
        DiagnosticUrlQr.encode("http://192.168.10.25:8272/?token=private");
    }
}
