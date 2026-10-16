package opensagetv.vibe.miniclient.android.diagnostics;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class DiagnosticHistoryTest {
    @Test public void opaqueDownloadsRedactAndNeverServeArbitraryFiles() throws Exception {
        Path root=Files.createTempDirectory("diagnostic-history-unit");
        try {
            Path logs=Files.createDirectory(root.resolve("logs")),tests=Files.createDirectory(root.resolve("current-video-tests"));
            Path stalls=Files.createDirectory(root.resolve("push-stall-diagnostics"));
            Files.write(stalls.resolve("push-stall-test.jsonl"),"{\"outcome\":\"recovered\",\"durationMs\":12000}\npassword=private\n192.168.10.175".getBytes(StandardCharsets.UTF_8));
            Files.write(root.resolve("Sage.properties"),"unique=secret".getBytes(StandardCharsets.UTF_8));
            DiagnosticHistory history=new DiagnosticHistory(root.toFile(),logs.toFile(),tests.toFile());
            List<Map<String,Object>> list=history.list();assertEquals(1,list.size());
            assertTrue(list.get(0).get("summary").toString().contains("recovered"));assertTrue(list.get(0).get("summary").toString().contains("12000"));
            String id=list.get(0).get("id").toString();assertTrue(id.matches("[0-9a-f]{16}"));
            Files.write(stalls.resolve("push-stall-test.jsonl"),"\nMore evidence".getBytes(StandardCharsets.UTF_8),StandardOpenOption.APPEND);
            assertEquals(id,history.list().get(0).get("id"));
            String text=new String(history.download(id),StandardCharsets.UTF_8);assertFalse(text.contains("private"));assertFalse(text.contains("192.168.10.175"));
            try{history.download("../../Sage.properties");fail();}catch(IllegalArgumentException expected){}
            try{history.download("1234567890abcdef");fail();}catch(FileNotFoundException expected){}
            Files.createSymbolicLink(stalls.resolve("push-stall-link.jsonl"),root.resolve("Sage.properties"));
            assertEquals(1,history.list().size());
        } finally {
            try(java.util.stream.Stream<Path> files=Files.walk(root)){
                for(Path file:(Iterable<Path>)files.sorted(Comparator.reverseOrder())::iterator)Files.delete(file);
            }
        }
    }
    @Test public void urlAndAuthorizationCredentialsNeverLeaveTheDevice() {
        String value=DiagnosticRedactor.redact("http://user:private@192.168.10.232/path\nAuthorization: Bearer sensitive-value\nAuthorization: Basic hidden-secret");
        assertFalse(value.contains("private"));assertFalse(value.contains("sensitive-value"));assertFalse(value.contains("hidden-secret"));
        assertFalse(value.contains("192.168.10.232"));
    }
}
