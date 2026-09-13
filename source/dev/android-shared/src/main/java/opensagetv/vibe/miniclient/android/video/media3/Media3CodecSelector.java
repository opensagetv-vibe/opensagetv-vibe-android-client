package opensagetv.vibe.miniclient.android.video.media3;

import androidx.media3.exoplayer.mediacodec.MediaCodecInfo;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import opensagetv.vibe.miniclient.android.video.AndroidCodecPolicy;
import opensagetv.vibe.miniclient.android.video.DecodingMethod;
import opensagetv.vibe.miniclient.android.video.DeviceCodecCapabilityProfile;
import opensagetv.vibe.miniclient.android.MiniclientApplication;
import opensagetv.vibe.miniclient.prefs.PrefStore;
import opensagetv.vibe.miniclient.video.DecoderAttemptTelemetry;

/** Applies the shared video-decoding policy to Media3 MediaCodec selection. */
public class Media3CodecSelector implements MediaCodecSelector
{
    protected final Logger log = LoggerFactory.getLogger(this.getClass());
    private final DecodingMethod decodingMethod;
    private final DecoderAttemptTelemetry telemetry;
    private final Set<String> sessionExclusions;

    public Media3CodecSelector()
    {
        this(DecodingMethod.HARDWARE);
    }

    public Media3CodecSelector(DecodingMethod decodingMethod)
    {
        this(decodingMethod, null, Collections.<String>emptySet());
    }

    public Media3CodecSelector(DecodingMethod decodingMethod,
            DecoderAttemptTelemetry telemetry, Set<String> sessionExclusions)
    {
        this.decodingMethod = decodingMethod == null ? DecodingMethod.HARDWARE : decodingMethod;
        this.telemetry = telemetry;
        this.sessionExclusions = sessionExclusions == null
                ? Collections.<String>emptySet() : new HashSet<>(sessionExclusions);
    }

    @Override
    public List<MediaCodecInfo> getDecoderInfos(String mimeType, boolean requiresSecureDecoder, boolean requiresTunnelingDecoder) throws MediaCodecUtil.DecoderQueryException
    {
        List<MediaCodecInfo> codecs = MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder);

        if (!AndroidCodecPolicy.isVideoMimeType(mimeType))
        {
            return codecs;
        }

        ArrayList<MediaCodecInfo> hardware = new ArrayList<MediaCodecInfo>();
        ArrayList<MediaCodecInfo> software = new ArrayList<MediaCodecInfo>();
        PrefStore prefs = MiniclientApplication.get().getClient().properties();

        for (MediaCodecInfo codec : codecs)
        {
            log.debug("Media3 decoder candidate {} for {}", codec.name, mimeType);
            if (AndroidCodecPolicy.isCodecDisabled(prefs, codec.name))
            {
                log.info("Media3 decoder disabled by user rule: {} for {}", codec.name, mimeType);
                continue;
            }
            if (sessionExclusions.contains(codec.name))
            {
                log.info("Media3 decoder excluded for current session: {} for {}", codec.name, mimeType);
                continue;
            }
            if (DeviceCodecCapabilityProfile.current().isSoftwareDecoder(codec.name, mimeType))
            {
                software.add(codec);
            }
            else
            {
                hardware.add(codec);
            }
        }

        ArrayList<MediaCodecInfo> selected = new ArrayList<MediaCodecInfo>();
        switch (decodingMethod)
        {
            case SOFTWARE:
                selected.addAll(software);
                break;
            case HARDWARE_PREFERRED:
                selected.addAll(hardware);
                selected.addAll(software);
                break;
            case HARDWARE:
            default:
                selected.addAll(hardware);
                break;
        }

        log.info("Media3 Decoding Method={} mime={} hardwareCandidates={} softwareCandidates={} selected={}",
                decodingMethod.displayName(), mimeType, hardware.size(), software.size(), selected.size());
        if (telemetry != null)
        {
            ArrayList<String> names = new ArrayList<>();
            for (MediaCodecInfo codec : selected) names.add(codec.name);
            telemetry.recordCandidates(mimeType, names);
        }
        return selected;
    }
}
