package opensagetv.vibe.miniclient.android.video;

import opensagetv.vibe.miniclient.prefs.PrefStore;

/** Saved output intent, captured once before negotiation; no player/INI writes. */
public final class MimOutputSettings {
    public final String encoder,hardwareBackend,videoCodec,resolution,fps,audioCodec,audioKbps,channels,container,remux;
    public MimOutputSettings(PrefStore p,String mode){
        boolean copy="copy".equals(mode);
        encoder=copy?"server":p.getString("mim_direct/encoder_policy","server");
        hardwareBackend=copy||"software".equals(encoder)?"auto":p.getString("mim_direct/hardware_backend","auto");
        videoCodec=copy?"auto":p.getString("fixed_encoding/video_codec","auto");
        resolution=copy?"SOURCE":p.getFixedEncodingVideoResolution();fps=copy?"SOURCE":p.getFixedEncodingFPS();audioCodec=copy?"aac":p.getFixedEncodingAudioCodec();
        audioKbps=copy?"128":p.getString("fixed_encoding/audio_bitrate_kbps","128");channels=copy?"":p.getFixedEncodingAudioChannels();
        remux=p.getFixedRemuxingPreference();
        container="copy".equals(mode)||"always".equals(remux)?p.getFixedRemuxingFormat():p.getFixedEncodingContainerFormat();
    }
    public boolean strict(){return !"auto".equals(hardwareBackend)||!"server".equals(encoder)||!"auto".equals(videoCodec)||!"SOURCE".equals(resolution)||!"SOURCE".equals(fps)||!"aac".equals(audioCodec)||!"128".equals(audioKbps)||!channels.isEmpty()||!"mpegts".equals(container);}
    public String validationError(boolean copy){
        if(!"mpegts".equals(container)&&!(copy&&"off".equals(remux)))return "This MIM transport supports MPEG-TS only. Select MPEG-TS or Stock SageTV Pipeline for MKV/DVD output.";
        if(!copy&&"mp2".equals(audioCodec)&&"6".equals(channels))return "MP2 does not support 5.1 output. Select AAC/AC3 or Stereo.";
        return "";
    }
    public String query(boolean copy){
        StringBuilder q=new StringBuilder("&outputRevision=1");
        String[][] values=copy?new String[][]{{"encoderPolicy","server"},{"container",container},{"remux",remux}}:
            new String[][]{{"encoderPolicy",encoder},{"videoCodec",videoCodec},{"resolution",resolution},{"frameRate",fps},{"audioCodec",audioCodec},{"audioKbps",audioKbps},{"audioChannels",channels},{"container",container},{"remux",remux}};
        for(String[] pair:values)try{q.append('&').append(pair[0]).append('=').append(java.net.URLEncoder.encode(pair[1],"UTF-8"));}catch(java.io.UnsupportedEncodingException impossible){throw new AssertionError(impossible);}
        if(!copy&&!"auto".equals(hardwareBackend))try{q.append("&hardwareBackend=").append(java.net.URLEncoder.encode(hardwareBackend,"UTF-8"));}catch(java.io.UnsupportedEncodingException impossible){throw new AssertionError(impossible);}
        return q.toString();
    }
}
