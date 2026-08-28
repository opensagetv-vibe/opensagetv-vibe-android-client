package sagex.miniclient.android.video.media3;

import androidx.media3.exoplayer.mediacodec.MediaCodecInfo;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import sagex.miniclient.android.video.AndroidCodecPolicy;
import sagex.miniclient.android.video.DecodingMethod;

/** Applies the shared video-decoding policy to Media3 MediaCodec selection. */
public class Media3CodecSelector implements MediaCodecSelector
{
    protected final Logger log = LoggerFactory.getLogger(this.getClass());
    private final DecodingMethod decodingMethod;

    public Media3CodecSelector()
    {
        this(DecodingMethod.HARDWARE);
    }

    public Media3CodecSelector(DecodingMethod decodingMethod)
    {
        this.decodingMethod = decodingMethod == null ? DecodingMethod.HARDWARE : decodingMethod;
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

        for (MediaCodecInfo codec : codecs)
        {
            log.debug("Media3 decoder candidate {} for {}", codec.name, mimeType);
            if (AndroidCodecPolicy.isSoftwareCodecName(codec.name))
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
        return selected;
    }
}
