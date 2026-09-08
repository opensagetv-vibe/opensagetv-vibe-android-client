package opensagetv.vibe.miniclient.android.tv.debug;

import static opensagetv.vibe.miniclient.android.tv.debug.DebugValueParser.clean;
import static opensagetv.vibe.miniclient.android.tv.debug.DebugValueParser.parseBoolean;
import static opensagetv.vibe.miniclient.android.tv.debug.DebugValueParser.parseBoundedInt;
import static opensagetv.vibe.miniclient.android.tv.debug.DebugValueParser.text;

import android.content.Context;
import android.content.Intent;

import opensagetv.vibe.miniclient.MiniClient;
import opensagetv.vibe.miniclient.android.MiniclientApplication;
import opensagetv.vibe.miniclient.android.prefs.AndroidPrefStore;
import opensagetv.vibe.miniclient.android.video.DecodingMethod;
import opensagetv.vibe.miniclient.android.video.PlayerBackend;
import opensagetv.vibe.miniclient.android.video.gsy.GSYPlayerEngine;
import opensagetv.vibe.miniclient.prefs.PrefStore;
import opensagetv.vibe.miniclient.net.SmbPathMapper;
import opensagetv.vibe.miniclient.media.TrackPreferencePolicy;

/** Validates and applies debug-only player configuration for the next playback. */
final class DebugPlayerConfigCommands
{
    private DebugPlayerConfigCommands()
    {
    }

    static String configure(Context context, Intent intent)
    {
        MiniClient client = requireClient(context);
        PrefStore prefs = client.properties();

        String player = clean(intent.getStringExtra("player"));
        String streaming = clean(intent.getStringExtra("streaming"));
        String decoding = clean(intent.getStringExtra("decoding"));
        String gsyEngine = clean(intent.getStringExtra("gsy_engine"));
        String gsySystemProbe = clean(intent.getStringExtra("gsy_system_probe"));
        String preferredAudioLanguage = clean(intent.getStringExtra("preferred_audio_language"));
        String preferredSubtitleLanguage = clean(intent.getStringExtra("preferred_subtitle_language"));
        String preferredCaptionStandard = clean(intent.getStringExtra("preferred_caption_standard"));
        String preferredCaptionService = clean(intent.getStringExtra("preferred_caption_service"));
        String fixedEncodingPreference = clean(intent.getStringExtra("fixed_encoding_preference"));
        String fixedEncodingFormat = clean(intent.getStringExtra("fixed_encoding_format"));
        String fixedVideoBitrateKbps = clean(intent.getStringExtra("fixed_video_bitrate_kbps"));
        String fixedVideoFps = clean(intent.getStringExtra("fixed_video_fps"));
        String fixedKeyFrameInterval = clean(intent.getStringExtra("fixed_key_frame_interval"));
        String fixedUseBFrames = clean(intent.getStringExtra("fixed_use_b_frames"));
        String fixedVideoResolution = clean(intent.getStringExtra("fixed_video_resolution"));
        String fixedAudioCodec = clean(intent.getStringExtra("fixed_audio_codec"));
        String fixedAudioBitrateKbps = clean(intent.getStringExtra("fixed_audio_bitrate_kbps"));
        String fixedAudioChannels = clean(intent.getStringExtra("fixed_audio_channels"));
        String fixedRemuxingPreference = clean(intent.getStringExtra("fixed_remuxing_preference"));
        String fixedRemuxingFormat = clean(intent.getStringExtra("fixed_remuxing_format"));
        // SMB mappings and profile URLs can contain case-sensitive Linux/share
        // path components. Only command/enumeration tokens use clean().
        String smbMappings = text(intent.getStringExtra("smb_mappings"));
        String smbUsername = clean(intent.getStringExtra("smb_username"));
        String smbPassword = intent.getStringExtra("smb_password");
        String smbDomain = clean(intent.getStringExtra("smb_domain"));
        boolean clearSmbAuth = parseBoolean(clean(intent.getStringExtra("smb_clear_auth")), false);
        String smbProfileDirectory = text(intent.getStringExtra("smb_profile_directory"));
        String smbProfileUsername = clean(intent.getStringExtra("smb_profile_username"));
        String smbProfilePassword = intent.getStringExtra("smb_profile_password");
        String smbProfileDomain = clean(intent.getStringExtra("smb_profile_domain"));
        String keepSessionInBackground = clean(intent.getStringExtra("keep_session_in_background"));
        String resumeBackgroundPlayback = clean(intent.getStringExtra("resume_background_playback"));
        String backgroundSessionTimeoutSeconds = clean(
                intent.getStringExtra("background_session_timeout_seconds"));
        String discPlaybackPolicy = clean(intent.getStringExtra("disc_playback_policy"));
        String discSkipMenus = clean(intent.getStringExtra("disc_skip_menus"));
        String discSkipPreviews = clean(intent.getStringExtra("disc_skip_previews"));
        String discCompatibilityFallback = clean(
                intent.getStringExtra("disc_compatibility_fallback"));
        String discMpeg2TimestampRepair = clean(
                intent.getStringExtra("disc_mpeg2_timestamp_repair"));
        String waitForPlaybackBeforeFirstOsd = clean(
                intent.getStringExtra("wait_for_playback_before_first_osd"));
        boolean clearSmbProfileAuth = parseBoolean(
                clean(intent.getStringExtra("smb_profile_clear_auth")), false);

        if (!keepSessionInBackground.isEmpty())
            prefs.setBoolean(PrefStore.Keys.keep_session_in_background,
                    parseBoolean(keepSessionInBackground, false));
        if (!resumeBackgroundPlayback.isEmpty())
            prefs.setBoolean(PrefStore.Keys.resume_background_playback,
                    parseBoolean(resumeBackgroundPlayback, true));
        if (!backgroundSessionTimeoutSeconds.isEmpty())
            prefs.setString(PrefStore.Keys.background_session_timeout_seconds,
                    Integer.toString(parseBoundedInt(backgroundSessionTimeoutSeconds, 300, 0, 86400)));
        if (!discPlaybackPolicy.isEmpty())
        {
            String value = discPlaybackPolicy.toLowerCase();
            if (!("auto".equals(value) || "native".equals(value)
                    || "hybrid".equals(value) || "mim_main_feature".equals(value)))
                throw new IllegalArgumentException("invalid disc playback policy: "
                        + discPlaybackPolicy);
            prefs.setString(PrefStore.Keys.disc_playback_policy, value);
        }
        if (!discSkipMenus.isEmpty())
            prefs.setBoolean(PrefStore.Keys.disc_skip_menus,
                    parseBoolean(discSkipMenus, false));
        if (!discSkipPreviews.isEmpty())
            prefs.setBoolean(PrefStore.Keys.disc_skip_previews,
                    parseBoolean(discSkipPreviews, false));
        if (!discCompatibilityFallback.isEmpty())
            prefs.setBoolean(PrefStore.Keys.disc_compatibility_fallback,
                    parseBoolean(discCompatibilityFallback, true));
        if (!discMpeg2TimestampRepair.isEmpty())
        {
            String value = discMpeg2TimestampRepair.toLowerCase();
            if (!("auto".equals(value) || "on".equals(value) || "off".equals(value)))
                throw new IllegalArgumentException("invalid DVD MPEG-2 timestamp repair mode: "
                        + discMpeg2TimestampRepair);
            prefs.setString(PrefStore.Keys.disc_mpeg2_timestamp_repair, value);
        }
        if (!waitForPlaybackBeforeFirstOsd.isEmpty())
            prefs.setBoolean(PrefStore.Keys.wait_for_playback_before_first_osd,
                    parseBoolean(waitForPlaybackBeforeFirstOsd, false));

        if (!player.isEmpty())
        {
            PlayerBackend parsed = PlayerBackend.fromPreference(player);
            if (!parsed.preferenceValue().equalsIgnoreCase(player))
                throw new IllegalArgumentException("invalid player: " + player);
            prefs.setString(PrefStore.Keys.default_player, parsed.preferenceValue());
        }

        if (!streaming.isEmpty())
        {
            String streamingPreference = "push".equals(streaming) || "push/dynamic".equals(streaming)
                    ? "dynamic" : streaming;
            if (!("dynamic".equals(streamingPreference) || "pull".equals(streamingPreference)
                    || "fixed".equals(streamingPreference)
                    || AndroidPrefStore.STREAMING_MODE_SMB_DIRECT.equals(streamingPreference)
                    || AndroidPrefStore.STREAMING_MODE_SMB_AUTO.equals(streamingPreference)))
                throw new IllegalArgumentException("invalid streaming mode: " + streaming);
            prefs.setString(AndroidPrefStore.STREAMING_MODE, streamingPreference);
        }

        if (!decoding.isEmpty())
        {
            String decodingPreference = "fallback".equals(decoding) ? "hardware_preferred" : decoding;
            DecodingMethod parsed = DecodingMethod.fromPreference(decodingPreference);
            if (!parsed.preferenceValue().equalsIgnoreCase(decodingPreference))
                throw new IllegalArgumentException("invalid decoding method: " + decoding);
            prefs.setString(PrefStore.Keys.decoding_method, parsed.preferenceValue());
        }

        if (!gsyEngine.isEmpty())
        {
            GSYPlayerEngine parsed = GSYPlayerEngine.fromPreference(gsyEngine);
            if (!parsed.preferenceValue().equalsIgnoreCase(gsyEngine))
                throw new IllegalArgumentException("invalid GSY engine: " + gsyEngine);
            prefs.setString(PrefStore.Keys.gsy_player_engine, parsed.preferenceValue());
        }
        if (!gsySystemProbe.isEmpty())
            prefs.setBoolean(PrefStore.Keys.gsy_system_probe_enabled,
                    parseBoolean(gsySystemProbe, false));

        if (!preferredAudioLanguage.isEmpty())
        {
            if ("auto".equalsIgnoreCase(preferredAudioLanguage))
                prefs.setString(PrefStore.Keys.preferred_audio_language, "");
            else if (!TrackPreferencePolicy.isValidLanguage(preferredAudioLanguage))
                throw new IllegalArgumentException("invalid preferred audio language: " + preferredAudioLanguage);
            else
                prefs.setString(PrefStore.Keys.preferred_audio_language,
                        TrackPreferencePolicy.normalizeLanguage(preferredAudioLanguage));
        }
        if (!preferredSubtitleLanguage.isEmpty())
        {
            if ("auto".equalsIgnoreCase(preferredSubtitleLanguage))
                prefs.setString(PrefStore.Keys.preferred_subtitle_language, "");
            else if (!TrackPreferencePolicy.isValidLanguage(preferredSubtitleLanguage))
                throw new IllegalArgumentException("invalid preferred subtitle language: " + preferredSubtitleLanguage);
            else
                prefs.setString(PrefStore.Keys.preferred_subtitle_language,
                        TrackPreferencePolicy.normalizeLanguage(preferredSubtitleLanguage));
        }
        if (!preferredCaptionStandard.isEmpty())
        {
            if (!TrackPreferencePolicy.isValidCaptionStandard(preferredCaptionStandard))
                throw new IllegalArgumentException("invalid preferred caption standard: " + preferredCaptionStandard);
            prefs.setString(PrefStore.Keys.preferred_caption_standard,
                    TrackPreferencePolicy.normalizeCaptionStandard(preferredCaptionStandard));
        }
        if (!preferredCaptionService.isEmpty())
        {
            String standard = preferredCaptionStandard.isEmpty()
                    ? prefs.getString(PrefStore.Keys.preferred_caption_standard, "auto")
                    : preferredCaptionStandard;
            int service;
            try
            {
                service = Integer.parseInt(preferredCaptionService);
            }
            catch (NumberFormatException ex)
            {
                throw new IllegalArgumentException("invalid preferred caption service: " + preferredCaptionService);
            }
            int maximum = TrackPreferencePolicy.CAPTION_STANDARD_CEA608.equals(
                    TrackPreferencePolicy.normalizeCaptionStandard(standard)) ? 4 : 63;
            if (service < 1 || service > maximum)
                throw new IllegalArgumentException("preferred caption service must be 1-" + maximum);
            prefs.setString(PrefStore.Keys.preferred_caption_service, String.valueOf(service));
        }

        if (!fixedEncodingPreference.isEmpty())
        {
            String value = fixedEncodingPreference.toLowerCase();
            if (!("needed".equals(value) || "always".equals(value)))
                throw new IllegalArgumentException("invalid fixed encoding preference: " + fixedEncodingPreference);
            prefs.setString(AndroidPrefStore.FIXED_ENCODING_PREFERENCE, value);
        }
        if (!fixedEncodingFormat.isEmpty())
        {
            String value = fixedEncodingFormat.toLowerCase();
            if (!("matroska".equals(value) || "dvd".equals(value) || "mpegts".equals(value)))
                throw new IllegalArgumentException("invalid fixed encoding format: " + fixedEncodingFormat);
            prefs.setString(AndroidPrefStore.FIXED_ENCODING_FORMAT, value);
        }
        if (!fixedVideoBitrateKbps.isEmpty())
            prefs.setInt(AndroidPrefStore.FIXED_ENCODING_VIDEO_BITRATE_KBPS, parseBoundedInt(fixedVideoBitrateKbps, 4000, 1, 100000));
        if (!fixedVideoFps.isEmpty())
        {
            String value = fixedVideoFps.toUpperCase();
            if (!("SOURCE".equals(value) || "24".equals(value) || "29.97".equals(value) || "59.94".equals(value)))
                throw new IllegalArgumentException("invalid fixed video fps: " + fixedVideoFps);
            prefs.setString(AndroidPrefStore.FIXED_ENCODING_FPS, value);
        }
        if (!fixedKeyFrameInterval.isEmpty())
            prefs.setInt(AndroidPrefStore.FIXED_ENCODING_KEY_FRAME_INTERVAL, parseBoundedInt(fixedKeyFrameInterval, 10, 1, 600));
        if (!fixedUseBFrames.isEmpty())
            prefs.setBoolean(AndroidPrefStore.FIXED_ENCODING_USE_B_FRAMES, parseBoolean(fixedUseBFrames, true));
        if (!fixedVideoResolution.isEmpty())
        {
            String value = fixedVideoResolution.toUpperCase();
            if (!("SOURCE".equals(value) || "CIF".equals(value) || "D1".equals(value) || "720".equals(value) || "1080".equals(value)))
                throw new IllegalArgumentException("invalid fixed video resolution: " + fixedVideoResolution);
            prefs.setString(AndroidPrefStore.FIXED_ENCODING_VIDEO_RESOLUTION, value);
        }
        if (!fixedAudioCodec.isEmpty())
        {
            String value = fixedAudioCodec.toLowerCase();
            if (!("aac".equals(value) || "ac3".equals(value) || "mp2".equals(value)))
                throw new IllegalArgumentException("invalid fixed audio codec: " + fixedAudioCodec);
            prefs.setString(AndroidPrefStore.FIXED_ENCODING_AUDIO_CODEC, value);
        }
        if (!fixedAudioBitrateKbps.isEmpty())
            prefs.setInt(AndroidPrefStore.FIXED_ENCODING_AUDIO_BITRATE_KBPS, parseBoundedInt(fixedAudioBitrateKbps, 128, 1, 10000));
        if (!fixedAudioChannels.isEmpty())
        {
            String value = fixedAudioChannels.toLowerCase();
            if ("source".equals(value)) value = "";
            if (!("".equals(value) || "1".equals(value) || "2".equals(value) || "6".equals(value)))
                throw new IllegalArgumentException("invalid fixed audio channels: " + fixedAudioChannels);
            prefs.setString(AndroidPrefStore.FIXED_ENCODING_AUDIO_CHANNELS, value);
        }
        if (!fixedRemuxingPreference.isEmpty())
        {
            String value = fixedRemuxingPreference.toLowerCase();
            if (!("needed".equals(value) || "always".equals(value) || "off".equals(value)))
                throw new IllegalArgumentException("invalid fixed remuxing preference: " + fixedRemuxingPreference);
            prefs.setString(AndroidPrefStore.FIXED_REMUXING_PREFERENCE, value);
        }
        if (!fixedRemuxingFormat.isEmpty())
        {
            String value = fixedRemuxingFormat.toLowerCase();
            if (!("matroska".equals(value) || "dvd".equals(value) || "mpegts".equals(value)))
                throw new IllegalArgumentException("invalid fixed remuxing format: " + fixedRemuxingFormat);
            prefs.setString(AndroidPrefStore.FIXED_REMUXING_FORMAT, value);
        }

        if (!smbMappings.isEmpty())
        {
            SmbPathMapper.parse(smbMappings);
            prefs.setString(AndroidPrefStore.SMB_MAPPINGS, smbMappings);
        }
        if (clearSmbAuth)
        {
            prefs.setString(AndroidPrefStore.SMB_USERNAME, "");
            prefs.setString(AndroidPrefStore.SMB_PASSWORD, "");
            prefs.setString(AndroidPrefStore.SMB_DOMAIN, "");
        }
        else
        {
            if (!smbUsername.isEmpty()) prefs.setString(AndroidPrefStore.SMB_USERNAME, smbUsername);
            if (smbPassword != null && !smbPassword.isEmpty())
                prefs.setString(AndroidPrefStore.SMB_PASSWORD, smbPassword);
            if (!smbDomain.isEmpty()) prefs.setString(AndroidPrefStore.SMB_DOMAIN, smbDomain);
        }

        if (!smbProfileDirectory.isEmpty())
        {
            SmbPathMapper.parse("/ => " + smbProfileDirectory).map("/");
            prefs.setString(AndroidPrefStore.SMB_PROFILE_DIRECTORY, smbProfileDirectory);
        }
        if (clearSmbProfileAuth)
        {
            prefs.setString(AndroidPrefStore.SMB_PROFILE_USERNAME, "");
            prefs.setString(AndroidPrefStore.SMB_PROFILE_PASSWORD, "");
            prefs.setString(AndroidPrefStore.SMB_PROFILE_DOMAIN, "");
        }
        else
        {
            if (!smbProfileUsername.isEmpty())
                prefs.setString(AndroidPrefStore.SMB_PROFILE_USERNAME, smbProfileUsername);
            if (smbProfilePassword != null && !smbProfilePassword.isEmpty())
                prefs.setString(AndroidPrefStore.SMB_PROFILE_PASSWORD, smbProfilePassword);
            if (!smbProfileDomain.isEmpty())
                prefs.setString(AndroidPrefStore.SMB_PROFILE_DOMAIN, smbProfileDomain);
        }

        return "op=config;" + DebugStateProvider.configuredValues(prefs)
                + ";smbAuthConfigured="
                + !prefs.getString(AndroidPrefStore.SMB_USERNAME, "").isEmpty()
                + ";smbProfileDirectoryConfigured="
                + !prefs.getString(AndroidPrefStore.SMB_PROFILE_DIRECTORY, "").isEmpty()
                + ";smbProfileAuthConfigured="
                + !prefs.getString(AndroidPrefStore.SMB_PROFILE_USERNAME, "").isEmpty()
                + ";appliesNextPlayback=true";
    }

    private static MiniClient requireClient(Context context)
    {
        MiniclientApplication app = MiniclientApplication.get(context);
        if (app == null || app.getClient() == null)
            throw new IllegalStateException("MiniClient application is not initialized");
        return app.getClient();
    }
}
