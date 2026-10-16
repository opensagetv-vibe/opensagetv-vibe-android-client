package opensagetv.vibe.miniclient.android.diagnostics;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Opaque IDs for existing, bounded support files. A request never supplies a
 * path and downloads are redacted text, not the original private file.
 */
public final class DiagnosticHistory {
    private final File privateRoot, logRoot, testRoot;
    public DiagnosticHistory(File privateRoot, File logRoot, File testRoot) {
        this.privateRoot = privateRoot; this.logRoot = logRoot; this.testRoot = testRoot;
    }
    private static final class Report {
        final File file; final String kind, id;
        Report(File file, String kind) throws Exception {
            this.file = file; this.kind = kind;
            // Keep the ID stable while an active log grows. Rotated/expired
            // reports disappear from the bounded catalog; filenames stay private.
            byte[] hash = MessageDigest.getInstance("SHA-256").digest((kind + ":" + file.getName()).getBytes(StandardCharsets.UTF_8));
            StringBuilder value = new StringBuilder(); for (int i=0;i<8;i++) value.append(String.format(Locale.ROOT,"%02x",hash[i]&255)); id=value.toString();
        }
    }
    private List<Report> reports() throws Exception {
        List<Report> found = new ArrayList<>();
        add(found,new File(privateRoot,"push-stall-diagnostics"),"push-stall", "push-stall-.*\\.jsonl",4);
        add(found,privateRoot,"playback-trace","playback-trace.*\\.jsonl",4);
        add(found,testRoot,"video-test",".*\\.txt",4);
        add(found,logRoot,"app-log",".*\\.txt",3);
        found.sort((a,b)->Long.compare(b.file.lastModified(),a.file.lastModified())); return found;
    }
    private static void add(List<Report> found, File root, String kind, String pattern, int keep) throws Exception {
        if (!root.isDirectory()) return;
        File[] files = root.listFiles((dir,name)->name.matches(pattern)); if (files == null) return;
        Arrays.sort(files,(a,b)->Long.compare(b.lastModified(),a.lastModified()));
        int accepted=0;
        for (File file: files) {
            if (accepted>=keep) break;
            if (!file.isFile() || !file.getCanonicalFile().getParentFile().equals(root.getCanonicalFile())) continue;
            // Symlinks are never report aliases, even when they point inside.
            if (!new File(root.getCanonicalFile(),file.getName()).equals(file.getCanonicalFile())) continue;
            found.add(new Report(file,kind)); accepted++;
        }
    }
    public List<Map<String,Object>> list() throws Exception {
        List<Map<String,Object>> out = new ArrayList<>();
        for (Report report : reports()) {
            boolean header = report.kind.equals("push-stall") || report.kind.equals("video-test");
            String content = new String(header ? readHead(report.file,4096) : readTail(report.file,4096),StandardCharsets.UTF_8);
            Map<String,Object> row = new LinkedHashMap<>(); row.put("id",report.id); row.put("kind",report.kind);
            row.put("timeMs",report.file.lastModified()); row.put("bytes",report.file.length());
            // Reading a tail is bounded; lack of a header never invents an
            // outcome/crash. The full selected redacted download supplies detail.
            String line = content.split("\\r?\\n",2)[0];
            if(report.kind.equals("push-stall")) {
                java.util.regex.Matcher outcome=java.util.regex.Pattern.compile("\\\"outcome\\\"\\s*:\\s*\\\"([A-Za-z0-9_-]{1,64})\\\"").matcher(content);
                java.util.regex.Matcher duration=java.util.regex.Pattern.compile("\\\"durationMs\\\"\\s*:\\s*([0-9]{1,12})").matcher(content);
                line=(outcome.find()?"Outcome: "+outcome.group(1):"Outcome unavailable")
                        +(duration.find()?"; captured duration "+duration.group(1)+" ms":"");
            }
            row.put("summary",DiagnosticRedactor.redact(line.length()>400?line.substring(0,400):line));
            row.put("note",report.kind.equals("app-log") ? "Collected log: crash/error evidence only if actually recorded" : "Existing diagnostic report; summary is a bounded excerpt"); out.add(row);
        }
        return out;
    }
    public byte[] download(String id) throws Exception {
        if (id == null || !id.matches("[0-9a-f]{16}")) throw new IllegalArgumentException("Invalid report ID");
        for (Report report:reports()) if (id.equals(report.id)) return DiagnosticRedactor.redact(
                "# " + report.kind + "; bounded last256KiB\n" + new String(readTail(report.file,256*1024),StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8);
        throw new FileNotFoundException("Report expired or unavailable");
    }
    public static byte[] readTail(File file,int maximum) throws IOException {
        try (RandomAccessFile input=new RandomAccessFile(file,"r")) {
            long size=input.length(); int length=(int)Math.min(size,maximum); byte[] text=new byte[length];
            input.seek(Math.max(0,size-length)); input.readFully(text); return text;
        }
    }
    private static byte[] readHead(File file,int maximum) throws IOException {
        try(InputStream input=new FileInputStream(file)) {
            ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] block=new byte[1024];int read;
            while(out.size()<maximum && (read=input.read(block,0,Math.min(block.length,maximum-out.size())))>=0)out.write(block,0,read);
            return out.toByteArray();
        }
    }
}
