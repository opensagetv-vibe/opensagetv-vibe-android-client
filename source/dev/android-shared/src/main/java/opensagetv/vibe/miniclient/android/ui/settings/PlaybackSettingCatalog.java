package opensagetv.vibe.miniclient.android.ui.settings;

import android.content.Context;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import opensagetv.vibe.miniclient.android.R;
import opensagetv.vibe.miniclient.android.prefs.AndroidPrefStore;
import opensagetv.vibe.miniclient.prefs.PrefStore;

/** Shared saved-key/choice/default/timing contract for startup and playback UI.
 * Does not open a decoder, change a transport, or persist anything on inspection.
 */
public final class PlaybackSettingCatalog {
    public enum Timing {
        IMMEDIATE("Immediate"), RESTART("Restarts playback when supported"),
        NEXT("Next playback"), RECONNECT("Reconnect required"), DVD_DISPLAY("Next DVD display change");
        public final String label;
        Timing(String label){this.label=label;}
    }
    public enum Kind { STRING, INTEGER, BOOLEAN, ADJUSTMENT }
    public enum Capability { ANY, EXO, IJK_FILTER, GPU_FILTER, TEXT, AUDIO, FIXED }
    public static final class Definition {
        public final String key,title,defaultValue,group;
        public final int labels,values,min,max;
        public final Kind kind;
        public final Timing timing;
        public final Capability capability;
        public final boolean session;
        private Definition(String key,String title,String fallback,String group,int labels,int values,
                Kind kind,int min,int max,Timing timing,Capability capability,boolean session){
            this.key=key;this.title=title;this.defaultValue=fallback;this.group=group;
            this.labels=labels;this.values=values;this.kind=kind;this.min=min;this.max=max;
            this.timing=timing;this.capability=capability;this.session=session;
        }
        public String[] labels(Context context){return context.getResources().getStringArray(labels);}
        public String[] values(Context context){return context.getResources().getStringArray(values);}
        public String display(Context context,String value){
            if(values!=0){String[] choices=values(context),names=labels(context);
                for(int i=0;i<choices.length;i++)if(choices[i].equals(value))return names[i];}
            return value+(key.endsWith("_offset_ms")?" ms":"");
        }
        public boolean validNumber(String value){try{int n=Integer.parseInt(value);return n>=min&&n<=max;}catch(RuntimeException e){return false;}}
    }
    private static Definition choice(String key,String title,String fallback,String group,int labels,int values,
            Timing timing,Capability capability,boolean session){
        return new Definition(key,title,fallback,group,labels,values,Kind.STRING,0,0,timing,capability,session);
    }
    private static Definition number(String key,String title,int fallback,String group,int min,int max,
            Timing timing,Capability capability,boolean session){
        return new Definition(key,title,""+fallback,group,0,0,Kind.INTEGER,min,max,timing,capability,session);
    }
    private static Definition bool(String key,String title,boolean fallback,String group,Timing timing,Capability capability){
        return new Definition(key,title,""+fallback,group,R.array.entries_playback_boolean,R.array.values_playback_boolean,
                Kind.BOOLEAN,0,0,timing,capability,true);
    }
    public static final Definition PLAYER=choice(PrefStore.Keys.default_player,"Player","exoplayer","video",R.array.entries_list_default_player,R.array.entryvalues_list_default_player,Timing.RESTART,Capability.ANY,true);
    public static final Definition GSY_ENGINE=choice(PrefStore.Keys.gsy_player_engine,"GSY Engine","auto","engine",R.array.entries_gsy_player_engine,R.array.entryvalues_gsy_player_engine,Timing.RESTART,Capability.ANY,true);
    public static final Definition DECODING=choice(PrefStore.Keys.decoding_method,"Decoding Method","hardware","video",R.array.entries_list_decoding_method,R.array.entryvalues_list_decoding_method,Timing.RESTART,Capability.ANY,true);
    public static final Definition DEINTERLACE=choice(PrefStore.Keys.video_deinterlace_mode,"Player Deinterlace","auto","video",R.array.entries_video_deinterlace_mode,R.array.entryvalues_video_deinterlace_mode,Timing.RESTART,Capability.ANY,true);
    public static final Definition BUFFER=choice(PrefStore.Keys.playback_buffer_preset,"Source Buffering","balanced","transport",R.array.entries_playback_buffer_preset,R.array.values_playback_buffer_preset,Timing.RESTART,Capability.EXO,true);
    public static final Definition MEDIA3_QUEUE=choice(PrefStore.Keys.media3_codec_mode,"Decoder Queueing Mode","sync","advanced",R.array.entries_codec_queueing_mode,R.array.entryvalues_codec_queueing_mode,Timing.RESTART,Capability.EXO,true);
    public static final Definition EXO_QUEUE=choice(PrefStore.Keys.exo2_codec_mode,"Decoder Queueing Mode","sync","advanced",R.array.entries_codec_queueing_mode,R.array.entryvalues_codec_queueing_mode,Timing.RESTART,Capability.EXO,true);
    public static final Definition IJK_FILTER=choice(PrefStore.Keys.ijk_deinterlace_filter,"Software Deinterlace Filter","auto","filters",R.array.entries_playback_ijk_filter,R.array.values_playback_ijk_filter,Timing.RESTART,Capability.IJK_FILTER,true);
    public static final Definition GPU_FILTER=choice(PrefStore.Keys.video_gpu_filter,"GPU Filter","fast","filters",R.array.entries_playback_gpu_filter,R.array.values_playback_gpu_filter,Timing.RESTART,Capability.GPU_FILTER,true);
    public static final Definition GPU_ORDER=choice(PrefStore.Keys.video_gpu_field_order,"Field Order","auto","filters",R.array.entries_playback_gpu_order,R.array.values_playback_gpu_order,Timing.RESTART,Capability.GPU_FILTER,true);
    public static final Definition GPU_RATE=choice(PrefStore.Keys.video_gpu_output_rate,"Output Rate","field","filters",R.array.entries_playback_gpu_rate,R.array.values_playback_gpu_rate,Timing.RESTART,Capability.GPU_FILTER,true);
    public static final Definition REFRESH=choice(PrefStore.Keys.playback_refresh_rate_matching,"Refresh-rate Matching","off","display",R.array.entries_playback_refresh,R.array.values_playback_refresh,Timing.IMMEDIATE,Capability.ANY,true);
    public static final Definition SETTLE=new Definition(PrefStore.Keys.playback_refresh_settle_ms,"HDMI Settle","0","display",R.array.entries_playback_settle,R.array.values_playback_settle,Kind.INTEGER,0,1500,Timing.DVD_DISPLAY,Capability.ANY,true);
    public static final Definition DVD_REPAIR=choice(PrefStore.Keys.disc_mpeg2_timestamp_repair,"Timestamp Repair","auto","dvd",R.array.entries_disc_mpeg2_timestamp_repair,R.array.entryvalues_disc_mpeg2_timestamp_repair,Timing.RESTART,Capability.ANY,true);
    // Existing negative boolean is deliberately retained; do not create a second output key.
    public static final Definition AUDIO_OUTPUT=new Definition(PrefStore.Keys.disable_audio_passthrough,"Audio Output","true","audio",R.array.entries_playback_audio_output,R.array.values_playback_audio_output,Kind.BOOLEAN,0,0,Timing.IMMEDIATE,Capability.AUDIO,true);
    public static final Definition PASSTHROUGH_OFFSET=bool(PrefStore.Keys.playback_passthrough_audio_offset_enabled,"Passthrough Offset",false,"audio",Timing.IMMEDIATE,Capability.AUDIO);
    public static final Definition AUDIO_OFFSET=number(PrefStore.Keys.playback_audio_offset_ms,"Audio Offset",0,"audio",-4000,4000,Timing.IMMEDIATE,Capability.AUDIO,true);
    public static final Definition SUBTITLE_OFFSET=number(PrefStore.Keys.playback_subtitle_offset_ms,"Subtitle Offset",0,"captions",-2000,2000,Timing.IMMEDIATE,Capability.TEXT,true);
    public static final Definition SUBTITLE_SAFE=number(PrefStore.Keys.playback_subtitle_safe_area_percent,"Bottom Safe Area (%)",18,"captions",5,35,Timing.IMMEDIATE,Capability.TEXT,true);
    public static final Definition SUBTITLE_SCALE=number(PrefStore.Keys.playback_subtitle_text_scale_percent,"Text Size (%)",100,"captions",75,150,Timing.IMMEDIATE,Capability.TEXT,true);
    public static final Definition SUBTITLE_STYLE=choice(PrefStore.Keys.playback_subtitle_text_style,"Subtitle Style","system","captions",R.array.entries_playback_subtitle_style,R.array.values_playback_subtitle_style,Timing.IMMEDIATE,Capability.TEXT,true);
    public static final Definition PROCESSING=choice(PrefStore.Keys.mim_direct_mode,"Processing Method","off","fixed",R.array.entries_mim_direct_mode,R.array.entryvalues_mim_direct_mode,Timing.RECONNECT,Capability.FIXED,false);
    public static final Definition ADJUSTMENT=new Definition("mim_direct/adjustment_method","Automatic Bitrate Adjustment","off","fixed",R.array.entries_fixed_adjustment_method,R.array.entryvalues_fixed_adjustment_method,Kind.ADJUSTMENT,0,0,Timing.NEXT,Capability.FIXED,false);
    public static final Definition MINIMUM=new Definition(PrefStore.Keys.mim_direct_minimum_kbps,"Minimum Video Bitrate (kbps)","250","fixed",0,0,Kind.STRING,250,50000,Timing.NEXT,Capability.FIXED,false);
    public static final Definition MAXIMUM=choice(AndroidPrefStore.FIXED_ENCODING_VIDEO_BITRATE_KBPS,"Maximum Video Bitrate (kbps)","4000","fixed",R.array.entries_list_video_bitrate_preference,R.array.entryvalues_list_video_bitrate_preference,Timing.NEXT,Capability.FIXED,false);
    public static final Definition ENCODER=choice("mim_direct/encoder_policy","Video Encoder","server","fixed",R.array.entries_transcode_encoder,R.array.values_transcode_encoder,Timing.NEXT,Capability.FIXED,false);
    public static final Definition HARDWARE_ENCODER=choice("mim_direct/hardware_backend","Hardware Encoder","auto","fixed",R.array.entries_hardware_encoder,R.array.values_hardware_encoder,Timing.NEXT,Capability.FIXED,false);
    public static final Definition VIDEO_CODEC=choice("fixed_encoding/video_codec","Video Codec","auto","fixed",R.array.entries_transcode_video_codec,R.array.values_transcode_video_codec,Timing.NEXT,Capability.FIXED,false);
    public static final Definition RESOLUTION=choice(AndroidPrefStore.FIXED_ENCODING_VIDEO_RESOLUTION,"Resolution","SOURCE","fixed",R.array.entries_list_video_resolution_preference,R.array.entryvalues_list_video_resolution_preference,Timing.NEXT,Capability.FIXED,false);
    public static final Definition FPS=choice(AndroidPrefStore.FIXED_ENCODING_FPS,"Frame Rate","SOURCE","fixed",R.array.entries_list_video_fps_preference,R.array.entryvalues_list_video_fps_preference,Timing.NEXT,Capability.FIXED,false);
    public static final Definition AUDIO_CODEC=choice(AndroidPrefStore.FIXED_ENCODING_AUDIO_CODEC,"Audio Codec","aac","fixed",R.array.entries_transcode_audio_codec,R.array.values_transcode_audio_codec,Timing.NEXT,Capability.FIXED,false);
    public static final Definition AUDIO_BITRATE=choice(AndroidPrefStore.FIXED_ENCODING_AUDIO_BITRATE_KBPS,"Audio Bitrate","128","fixed",R.array.entries_transcode_audio_bitrate,R.array.values_transcode_audio_bitrate,Timing.NEXT,Capability.FIXED,false);
    public static final Definition CHANNELS=choice(AndroidPrefStore.FIXED_ENCODING_AUDIO_CHANNELS,"Audio Channels","","fixed",R.array.entries_list_audio_channels_preference,R.array.entryvalues_list_audio_channels_preference,Timing.NEXT,Capability.FIXED,false);
    public static final Definition CONTAINER=choice(AndroidPrefStore.FIXED_ENCODING_FORMAT,"Transcoding Container Format","mpegts","fixed",R.array.entries_list_transcoding_formats_preference,R.array.entryvalues_list_transcoding_formats_preference,Timing.NEXT,Capability.FIXED,false);
    public static final Definition REMUX=choice(AndroidPrefStore.FIXED_REMUXING_PREFERENCE,"Remuxing Method","needed","fixed",R.array.entries_list_remuxing_setting_preference,R.array.entryvalues_list_remuxing_setting_preference,Timing.NEXT,Capability.FIXED,false);
    public static final Definition REMUX_CONTAINER=choice(AndroidPrefStore.FIXED_REMUXING_FORMAT,"Remux Container Format","matroska","fixed",R.array.entries_list_remuxing_formats_preference,R.array.entryvalues_list_remuxing_formats_preference,Timing.NEXT,Capability.FIXED,false);
    public static final List<Definition> FIXED_OUTPUT=Collections.unmodifiableList(Arrays.asList(ENCODER,HARDWARE_ENCODER,VIDEO_CODEC,CONTAINER,REMUX,REMUX_CONTAINER,RESOLUTION,FPS,AUDIO_CODEC,AUDIO_BITRATE,CHANNELS));
    public static final List<Definition> ALL;
    static {java.util.ArrayList<Definition> definitions=new java.util.ArrayList<>(Arrays.asList(PLAYER,GSY_ENGINE,DECODING,DEINTERLACE,BUFFER,MEDIA3_QUEUE,EXO_QUEUE,IJK_FILTER,GPU_FILTER,GPU_ORDER,GPU_RATE,REFRESH,SETTLE,DVD_REPAIR,AUDIO_OUTPUT,PASSTHROUGH_OFFSET,AUDIO_OFFSET,SUBTITLE_OFFSET,SUBTITLE_SAFE,SUBTITLE_SCALE,SUBTITLE_STYLE,PROCESSING,ADJUSTMENT,MINIMUM,MAXIMUM));definitions.addAll(FIXED_OUTPUT);ALL=Collections.unmodifiableList(definitions);}
    public static String fixedUnavailable(Definition d,String mode,boolean adjusted,String remux){
        return fixedUnavailable(d,mode,adjusted,remux,"server");
    }
    public static String fixedUnavailable(Definition d,String mode,boolean adjusted,String remux,String encoderPolicy){
        boolean copy="copy".equals(mode),stock="off".equals(mode),hls="hls".equals(mode);
        if(d==HARDWARE_ENCODER&&"software".equals(encoderPolicy))return "Software Only does not use a hardware encoder; saved choice retained";
        if(d==ENCODER||d==HARDWARE_ENCODER||d==VIDEO_CODEC)return stock||copy?"Video encoding selection applies to MIM Transcode / HLS only":null;
        if(d==ADJUSTMENT||d==MINIMUM)return !hls?"Automatic adjustment and Minimum apply to HLS only":d==MINIMUM&&!adjusted?"Enable Automatic Bitrate Adjustment first":null;
        if(d==MAXIMUM||d==RESOLUTION||d==FPS||d==AUDIO_CODEC||d==AUDIO_BITRATE||d==CHANNELS)return copy?"Direct Copy never re-encodes audio or video":null;
        if(d==CONTAINER&&copy)return "Copy uses Remux Container Format, not a transcoding container";
        if(d==REMUX_CONTAINER&&"off".equals(remux))return "Remuxing is Off; saved container retained";
        return null;
    }
    public static Definition find(String key){for(Definition d:ALL)if(d.key.equals(key))return d;return null;}
    public static final class RuntimeSupport {
        public final boolean active,exo,ijkFilter,gpuFilter,text,audio,fixed;
        public RuntimeSupport(boolean active,boolean exo,boolean ijkFilter,boolean gpuFilter,boolean text,boolean audio,boolean fixed){
            this.active=active;this.exo=exo;this.ijkFilter=ijkFilter;this.gpuFilter=gpuFilter;this.text=text;this.audio=audio;this.fixed=fixed;
        }
    }
    public static String runtimeUnavailable(Definition d,RuntimeSupport support){
        if(d==PLAYER)return null;
        if(d.session&&!support.active)return "No active player; saved choice retained";
        if(d.capability==Capability.EXO&&!support.exo)return "Media3 / legacy Exo delegates only; saved choice retained";
        if(d.capability==Capability.IJK_FILTER&&!support.ijkFilter)return "IJK software interlaced frames only";
        if(d.capability==Capability.GPU_FILTER&&!support.gpuFilter)return "Supported Player GPU path required; server filtering is separate";
        if(d.capability==Capability.TEXT&&!support.text)return "Unsupported subtitle presentation on this backend";
        if(d.capability==Capability.AUDIO&&!support.audio)return "Unsupported active audio output";
        if(d.capability==Capability.FIXED&&!support.fixed)return "Select Fixed in main settings first";
        return null;
    }
    /** Honest startup applicability: hardware/provider availability is not guessed. */
    public static String defaultUnavailable(Definition d,String backend){
        if(d.capability==Capability.IJK_FILTER&&!"ijkplayer".equals(backend))return "IJK software frames only; saved value retained";
        if((d.capability==Capability.EXO||d.capability==Capability.GPU_FILTER)&&"ijkplayer".equals(backend))return "Not used by IJK; saved value retained";
        return null;
    }
    private PlaybackSettingCatalog(){}
}
