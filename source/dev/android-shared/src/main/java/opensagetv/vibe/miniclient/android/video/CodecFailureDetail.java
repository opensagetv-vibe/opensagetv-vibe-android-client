package opensagetv.vibe.miniclient.android.video;

/** Closed, bounded error attribution for an explicit diagnostic snapshot.
 * Never reads exception messages, URLs, media paths or nested arbitrary objects.
 */
public final class CodecFailureDetail {
    private CodecFailureDetail() {}
    public static String describe(Object error) {
        if (!(error instanceof Throwable)) return "";
        StringBuilder out=new StringBuilder();
        Throwable current=(Throwable)error;
        for(int depth=0;current!=null&&depth<6;depth++) {
            if(out.length()>0)out.append(" > ");
            out.append(atom(current.getClass().getSimpleName()));
            for(String field:new String[]{"mimeType","diagnosticInfo","decoderName"}) {
                String value=field(current,field);
                if(!value.isEmpty())out.append(" ").append(field).append("=").append(value);
            }
            try {
                Object codec=current.getClass().getField("codecInfo").get(current);
                String name=field(codec,"name");
                if(!name.isEmpty())out.append(" codec=").append(name);
            } catch(ReflectiveOperationException|RuntimeException ignored) {}
            try {
                Object format=current.getClass().getField("rendererFormat").get(current);
                String mime=field(format,"sampleMimeType");
                if(!mime.isEmpty())out.append(" inputMime=").append(mime);
                Object channels=format.getClass().getField("channelCount").get(format);
                if(channels instanceof Integer&&(Integer)channels>=-1&&(Integer)channels<=32)out.append(" channels=").append(channels);
                if("audio/mp4a-latm".equals(mime)){
                    Object data=format.getClass().getField("initializationData").get(format);
                    if(data instanceof java.util.List&&!((java.util.List<?>)data).isEmpty()){
                        Object first=((java.util.List<?>)data).get(0);
                        if(first instanceof byte[]&&((byte[])first).length>=2){
                            byte[] asc=(byte[])first;
                            out.append(" ascBytes=").append(asc.length).append(" ascChannels=").append((asc[1]>>3)&15);
                        }
                    }
                }
            } catch(ReflectiveOperationException|RuntimeException ignored) {}
            Throwable next=current.getCause();if(next==current)break;current=next;
        }
        return out.toString();
    }
    private static String field(Object owner,String name) {
        if(owner==null)return "";
        try {
            Object value=owner.getClass().getField(name).get(owner);
            if(!(value instanceof String)||((String)value).length()>120)return "";
            String text=(String)value;
            String pattern=("mimeType".equals(name)||"sampleMimeType".equals(name))?"(audio|video|application|text)/[A-Za-z0-9.+_-]+":
                "diagnosticInfo".equals(name)?"(android\\.media\\.MediaCodec\\.|com\\.google\\.android\\.exoplayer2\\.|androidx\\.media3\\.|neg_)[A-Za-z0-9_.-]+":
                "(OMX\\.|c2\\.|android\\.)[A-Za-z0-9_.-]+";
            return text.matches(pattern)?text:"";
        }
        catch(ReflectiveOperationException|RuntimeException ignored){return "";}
    }
    private static String atom(String value) {
        return value!=null&&value.length()<=120&&value.matches("[A-Za-z0-9_.$]+")?value:"";
    }
}
