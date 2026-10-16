package opensagetv.vibe.miniclient.android.diagnostics;

import java.net.URI;

/** Pure chooser policy; no preference writes, DNS lookup or service enabling. */
public final class DiagnosticExportPolicy {
    private DiagnosticExportPolicy() { }
    public static String firstLanUrl(String status) {
        if (status == null) return "";
        for (String word : status.trim().split("\\s+")) {
            try { if (validWebUrl(word)) return word; } catch (RuntimeException ignored) { }
        }
        return "";
    }
    public static boolean validWebUrl(String value) {
        try {
            URI u = new URI(value);
            if (!"http".equals(u.getScheme()) || u.getRawUserInfo() != null
                    || u.getRawQuery() != null || u.getRawFragment() != null
                    || !"/".equals(u.getRawPath()) || u.getPort() < 1024 || u.getPort() > 65535) return false;
            String host=u.getHost();
            if (host == null || !host.matches("[0-9]{1,3}(?:\\.[0-9]{1,3}){3}")) return false;
            String[] parts=host.split("\\.");int[] octets=new int[4];
            for(int i=0;i<4;i++){octets[i]=Integer.parseInt(parts[i]);if(octets[i]>255)return false;}
            return octets[0]==10 || (octets[0]==172 && octets[1]>=16 && octets[1]<=31)
                    || (octets[0]==192 && octets[1]==168);
        } catch (Exception invalid) { return false; }
    }
    public static boolean webAvailable(boolean enabled, String liveUrl) {
        return enabled && validWebUrl(liveUrl);
    }
    public static boolean serverAvailable(boolean connected, boolean capabilityConfirmed) {
        return connected && capabilityConfirmed;
    }
}
