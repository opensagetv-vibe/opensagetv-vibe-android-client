package opensagetv.vibe.miniclient.android.diagnostics;
import org.junit.Test;
import static org.junit.Assert.*;
public class DiagnosticExportPolicyTest {
    @Test public void offOrUnavailableWebNeverEnabled() {
        assertFalse(DiagnosticExportPolicy.webAvailable(false,"http://192.168.10.25:8272/"));
        assertFalse(DiagnosticExportPolicy.webAvailable(true,"Off"));
        assertTrue(DiagnosticExportPolicy.webAvailable(true,"http://192.168.10.25:8272/"));
    }
    @Test public void serverNeedsConnectionAndReceiver() {
        assertFalse(DiagnosticExportPolicy.serverAvailable(false,true));
        assertFalse(DiagnosticExportPolicy.serverAvailable(true,false));
        assertTrue(DiagnosticExportPolicy.serverAvailable(true,true));
    }
    @Test public void chooseOnlyCredentialFreeNumericLanUrl() {
        assertEquals("http://192.168.10.25:8272/",DiagnosticExportPolicy.firstLanUrl("http://127.0.0.1:8272/ http://192.168.10.25:8272/ http://10.2.3.4:8272/"));
        for(String value:new String[]{"http://8.8.8.8:8272/","http://example.com:8272/","http://user:pass@192.168.1.1:8272/","http://192.168.1.1:8272/?token=secret","http://192.168.1.1:8272/#secret","http://192.168.999.1:8272/","http://172.32.0.1:8272/","http://192.168.1.1:80/","http://192.168.1.1:8272/bundle.zip"})assertFalse(value,DiagnosticExportPolicy.validWebUrl(value));
    }
}
