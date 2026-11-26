package opensagetv.vibe.miniclient.android.video;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Closed-field, credential-free formatting; requested settings are not output proof. */
public final class MimOutputStatus {
    private static String section(String json,String name){Matcher m=Pattern.compile("\""+name+"\"\\s*:\\s*(\\{[^{}]*\\})").matcher(json==null?"":json);return m.find()?m.group(1):"";}
    private static String field(String json,String name){Matcher m=Pattern.compile("\""+name+"\"\\s*:\\s*\"([A-Za-z0-9_. /-]{1,80})\"").matcher(json);return m.find()?m.group(1):"Unknown";}
    private static boolean flag(String json,String name){return Pattern.compile("\""+name+"\"\\s*:\\s*true\\s*[,}]").matcher(json).find();}
    public static String summary(String json){
        String execution=section(json,"execution"),path=field(execution,"path"),encoder=field(execution,"encoder");
        if(!encoder.matches("[A-Za-z0-9_.-]{1,64}"))encoder="Unknown";
        boolean known=("full_gpu".equals(path)||"mixed".equals(path)||"software".equals(path))&&!"Unknown".equals(encoder);
        String encode="copy".equals(path)?"None (Copy/Remux)":known?(flag(execution,"hardwareEncode")?"Hardware":"Software")+" ("+encoder+")":"Unknown";
        String decode=known?(flag(execution,"hardwareDecode")?"Hardware":"Software"):"Unknown";
        StringBuilder text=new StringBuilder("Server video encoding: ").append(encode).append("\nServer decoding: ").append(decode);
        String output=section(json,"effectiveOutput");
        if(flag(output,"observed")){
            text.append("\nEffective output: ").append(field(output,"videoCodec")).append(' ').append(field(output,"width")).append('x').append(field(output,"height"))
                .append(" / FPS ").append(field(output,"fps")).append(" / video ").append(field(output,"videoBitrate")).append(" bps")
                .append("\nServer audio output: ").append(field(output,"audioCodec")).append(" / ").append(field(output,"audioBitrate")).append(" bps / ").append(field(output,"audioChannels")).append(" channels")
                .append("\nOutput container: ").append(field(output,"container"));
        }else text.append("\nEffective output: Unknown (not observed)");
        String requested=section(json,"outputSettings");
        if(!requested.isEmpty())text.append("\nRequested video encoder: ").append(field(requested,"encoderPolicy"));
        String hardware=field(requested,"hardwareBackend");
        if(!"Unknown".equals(hardware))text.append("\nRequested hardware encoder: ").append(hardware);
        String fallback=field(json,"outputFallbackReason");if(!"Unknown".equals(fallback)&&!"none".equals(fallback))text.append("\nOutput fallback: ").append(fallback);
        return text.toString();
    }
    private MimOutputStatus(){}
}
