package opensagetv.vibe.miniclient.prefs;

import java.util.Set;

/**
 * Simple Abstract way of handling Preferences
 */
public interface PrefStore
{
    String getString(String key);

    String getString(String key, String defValue);

    void setString(String key, String value);

    long getLong(String key);

    long getLong(String key, long defValue);

    void setLong(String key, long value);

    int getInt(String key);

    int getInt(String key, int defValue);

    void setInt(String key, int value);

    double getDouble(String key);

    double getDouble(String key, double defValue);

    void setDouble(String key, double value);

    boolean getBoolean(String key);

    boolean getBoolean(String key, boolean defValue);

    void setBoolean(String key, boolean value);

    Set<Object> keys();

    void remove(String key);

    boolean contains(String key);

    boolean canSet(String key);

    /**
     * Gets the streaming mode that SageTV uses to send content to the client
     *
     * @return fixed, dynamic, pull
     */
    String getStreamingMode();

    /**
     * Preference on when to transcode content.
     *
     * Always - Will tell SageTV that there are no supported pull formats
     * When Needed - Will give SageTV a list of supported formats
     *
     * @return Returns the preference for transcoding
     */
    String getFixedEncodingPreference();

    /**
     * Get the container format to be used for Fixed Encoding
     *
     * @return Container format
     */
    String getFixedEncodingContainerFormat();

    String getFixedEncodingAudioCodec();

    String getFixedEncodingAudioChannels();

    int getFixedEncodingVideoBitrateKBPS();

    int getFixedEncodingAudioBitrateKBPS();

    String getFixedEncodingFPS();

    int getFixedEncodingKeyFrameInterval();

    boolean getFixedEncodingUseBFrames();

    String getFixedEncodingVideoResolution();

    String getFixedRemuxingPreference();

    String getFixedRemuxingFormat();

    interface Keys
    {
    
        String image_cache_size_mb = "image_cache_size_mb";
        String disk_image_cache_size_mb = "disk_image_cache_size_mb";

        String cache_images_on_disk = "cache_images_on_disk";
        String use_bitmap_images = "use_bitmap_images";

        /**
         * values: high, med, low
         */
        String local_fs_security = "local_fs_security";
        String mplayer_extra_video_codecs = "mplayer/extra_video_codecs";
        String mplayer_extra_audio_codecs = "mplayer/extra_audio_codecs";

        /**
         * values: dynamic, fixed, pull
         */
        //String streaming_mode = "streaming_mode";
    
        /**
         * Preference on when to transcode.
         * Always - Will tell SageTV that there are no supported pull formats
         * When Needed - Will give SageTV a list of supported formats
         */
        //String fixed_encoding_preference = "fixed_encoding/preference";
        
        /**
         * The container format that will be used for fixed transcoding
         */
        //String fixed_encoding_format = "fixed_encoding/format";
    
        /**
         * The audio codec to be use for fixed transcoding
         */
        //String fixed_encoding_audio_code = "fixed_encoding/audio_codec";
    
        /**
         * The number of audio channels for fixed transcoding
         */
        //String fixed_encoding_audio_channels = "fixed_encoding/audio_channels";
        
        /**
         * 000 will be added to this value, so we only set, 64 to mean 64,000
         */
        //String fixed_encoding_video_bitrate_kbps = "fixed_encoding/video_bitrate_kbps";
        /**
         * 000 will be added to this value, so we only set, 64 to mean 64,000
         */
        //String fixed_encoding_audio_bitrate_kbps = "fixed_encoding/audio_bitrate_kbps";

        //String fixed_encoding_fps = "fixed_encoding/fps";
        //String fixed_encoding_key_frame_interval = "fixed_encoding/key_frame_interval";
        //String fixed_encoding_use_b_frames = "fixed_encoding/use_b_frames";
        //String fixed_encoding_video_resolution = "fixed_encoding/video_resolution";

        String video_buffer_size = "video_buffer_size";
        String audio_buffer_size = "audio_buffer_size";

        // auto connect settings
        String auto_connect_to_last_server = "auto_connect_to_last_server";
        String auto_connect_delay = "auto_connect_delay";
        String last_connected_server = "last_connected_server";

        /**
         * Log to file
         */
        String use_log_to_sdcard = "use_log_to_sdcard";

        /**
         * Use remote buttons change depending on the state of the player
         */
        //String use_stateful_remote = "use_stateful_remote";

        /**
         * values: debug, info, warn, error
         */
        String log_level = "log_level";

        /**
         * if true, then aspect ratio debugging is enabled.
         */
        String debug_ar = "debug_ar";


        /**
         * if enabled the long press select will bring up OSD
         */
        //String long_press_select_for_osd = "long_press_select_for_osd";

        /**
         * Debug Settings
         */
        String debug_log_unmapped_keypresses = "debug_log_unmapped_keypresses";

        /**
         * If set to true, then when the app pauses, it will tear down
         */
        String app_destroy_on_pause = "app_destroy_on_pause";

        /** New positive policy key; migrated once from the inverse legacy key. */
        String keep_session_in_background = "keep_session_in_background";

        /** Resume only playback that the background policy itself paused. */
        String resume_background_playback = "resume_background_playback";

        /** Seconds before an enabled preserved background session is disconnected; zero disables the timeout. */
        String background_session_timeout_seconds = "background_session_timeout_seconds";

        /** Disc/DVD playback policy: auto, native, hybrid, or mim_main_feature. */
        String disc_playback_policy = "disc_playback_policy";

        /** Ask a capable SageTV server to start the DVD main feature instead of its menu. */
        String disc_skip_menus = "disc_skip_menus";

        /** Ask a capable SageTV server to bypass preview titles while retaining the main menu. */
        String disc_skip_previews = "disc_skip_previews";

        /** Fall back to the stock/native DVD path when an optional Vibe path is unavailable. */
        String disc_compatibility_fallback = "disc_compatibility_fallback";

        /** Native DVD MPEG-2 missing-PTS repair: auto, on, or off. */
        String disc_mpeg2_timestamp_repair = "disc_mpeg2_timestamp_repair";

        /**
         * If set to true, then system sleep is disabled
         */
        String disable_sleep = "disable_sleep";

        /**
         * String: exoplayer, media3, and ijkplayer are the current possible values
         */
        String default_player = "default_player";

        /**
         * String: shared video decoding policy used by all player backends.
         * Values: hardware, software, hardware_preferred. Default: hardware.
         */
        String decoding_method = "decoding_method";

        /** Preferred BCP-47 audio language; empty means automatic/server order. */
        String preferred_audio_language = "preferred_audio_language";

        /** Preferred BCP-47 subtitle language; does not enable captions. */
        String preferred_subtitle_language = "preferred_subtitle_language";

        /** auto, cea608, or cea708; SageTV remains the caption on/off authority. */
        String preferred_caption_standard = "preferred_caption_standard";

        /** CEA-608 channel 1-4 or CEA-708 service 1-63. */
        String preferred_caption_service = "preferred_caption_service";

        /**
         * Caption fallback for an unmodified SageTV server that cannot send
         * VIDEO_CC_STATE: stv, off, cc1, or cc2. A Vibe server's STV state
         * always overrides this compatibility setting.
         */
        String legacy_server_caption_mode = "legacy_server_caption_mode";

        /** Media3 MediaCodec queueing mode: sync, auto, or async. Default: sync. */
        String media3_codec_mode = "media3_codec_mode";

        /** Legacy ExoPlayer MediaCodec queueing mode: sync, auto, or async. Default: sync. */
        String exo2_codec_mode = "exo2_codec_mode";

        /** Bounded Pull/SMB buffering preset: low_latency, balanced, or resilient. */
        String playback_buffer_preset = "playback_buffer_preset";

        /** Bounded player-presentation subtitle offset; never changes SageTV time. */
        String playback_subtitle_offset_ms = "playback_subtitle_offset_ms";

        /** Locally rendered text-caption bottom safe area, 5 through 35 percent. */
        String playback_subtitle_safe_area_percent = "playback_subtitle_safe_area_percent";

        /** Locally rendered text-caption size, 75 through 150 percent. */
        String playback_subtitle_text_scale_percent = "playback_subtitle_text_scale_percent";

        /** Locally rendered text-caption style: system, outline, or black_box. */
        String playback_subtitle_text_style = "playback_subtitle_text_style";

        /** Bounded player-presentation audio offset; unsupported outputs ignore it. */
        String playback_audio_offset_ms = "playback_audio_offset_ms";

        /** Active display matching: off, seamless, or always. */
        String playback_refresh_rate_matching = "playback_refresh_rate_matching";

        /** Delay before a server-owned DVD decoder reload after a display-mode change. */
        String playback_refresh_settle_ms = "playback_refresh_settle_ms";

        /**
         * Boolean: Announce when Software decoder is being used
         */
        String announce_software_decoder = "announce_software_decoder";

        /**
         * Boolean: use native software decoders over ffmpeg software decoders
         */
        String prefer_android_software_decoders = "prefer_android_software_decoders";

        /**
         * Boolean: IJK only. Enable MediaCodec for MPEG-2 video. Default false because the
         * IJK 0.8.8 MPEG-2 MediaCodec path can produce black video on Fire TV while the
         * bundled FFmpeg MPEG-2 software decoder remains available as a fallback.
         */
        String ijk_mediacodec_mpeg2 = "ijk_mediacodec_mpeg2";

        /** GSYVideoPlayer only. Selects the independent GSY underlying engine. */
        String gsy_player_engine = "gsy_player_engine";

        /**
         * Debug/commissioning gate for the experimental Android System MediaPlayer
         * datasource bridge. Normal System selection remains fail-safe on Media3.
         */
        String gsy_system_probe_enabled = "gsy_system_probe_enabled";

        /**
         * Integer: Exoplayer ffmpeg extenstion setting.  (Off = 0, On = 1, Prefer = 2)
         */
        String exoplayer_ffmpeg_extension_setting = "exoplayer_ffmpeg_extension";

        /**
         * Boolean: if true, then only software decoders are used
         */
        String disable_hardware_decoders = "disable_hardware_decoders";

        /**
         * Boolean: if true, then only software decoders are used
         */
        String disable_audio_passthrough = "disable_audio_passthrough";

        /**
         * Boolean: default is true.  When enabled uses full screen resolution, when
         * disabled, it will report its screen size to be half native.
         */
        String use_native_resolution = "use_native_resolution";

        /**
         * Boolean: default is true.  When true, then when the SageTV exits, you go back to the
         * Android Launcher
         */
        String exit_to_home_screen = "exit_to_home_screen";

        /**
         * Boolean: default is false.  When true, then the Leanback Launcher will be used on a
         * Phone/Tablet
         */
        String use_tv_ui_on_tablet = "use_tv_ui_on_tablet";

        /**
         * How mas in MS the repeated keys will repeat during a key hold
         */
        String repeat_key_ms = "repeat_key_ms";

        /**
         * How long a key is held before repeats will happen
         */
        String repeat_key_delay_ms = "repeat_key_delay_ms";

        /**
         * Client ID
         */
        String client_id = "clientid";
        String use_opengl_ui = "use_opengl_ui";

        String exit_on_standby = "exit_on_standby";

        /**
         * used for testing only.  Do not enable this.
         */
        String use_httpls = "use_httpls";

        /**
         * Send DebugSageCommandEvent before sending SageCommand to SageTV
         */
        String debug_sage_commands = "debug_sage_commands";
    }
}
