package opensagetv.vibe.miniclient.android.video;

import android.content.Context;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.os.Build;

import com.google.android.exoplayer2.audio.AudioCapabilities;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import opensagetv.vibe.miniclient.media.AudioCodec;

/**
 * Device-local audio evidence. Decoder availability and encoded-sink support
 * are deliberately reported separately: neither one proves that the active
 * playback session is using passthrough.
 */
public final class DeviceAudioCapabilityProfile
{
    private static volatile DeviceAudioCapabilityProfile cached;

    private final List<Entry> audioDecoders;
    private final AudioCapabilities sinkCapabilities;

    private DeviceAudioCapabilityProfile(List<Entry> audioDecoders,
                                         AudioCapabilities sinkCapabilities)
    {
        this.audioDecoders = Collections.unmodifiableList(audioDecoders);
        this.sinkCapabilities = sinkCapabilities;
    }

    public static DeviceAudioCapabilityProfile current(Context context)
    {
        DeviceAudioCapabilityProfile value = cached;
        if (value != null) return value;
        synchronized (DeviceAudioCapabilityProfile.class)
        {
            value = cached;
            if (value == null)
            {
                value = collect(context.getApplicationContext());
                cached = value;
            }
            return value;
        }
    }

    public boolean hasDecoder(AudioCodec codec)
    {
        for (Entry entry : audioDecoders)
        {
            if (codec.hasAndroidMimeType(entry.mimeType)) return true;
        }
        return false;
    }

    public boolean sinkSupportsEncoded(AudioCodec codec)
    {
        if (sinkCapabilities == null) return false;
        for (int encoding : codec.getAndroidAudioEncodings())
        {
            if (sinkCapabilities.supportsEncoding(encoding)) return true;
        }
        return false;
    }

    /** Playable means decodable to PCM OR accepted by the currently connected encoded sink. */
    public boolean canPlay(AudioCodec codec)
    {
        return hasDecoder(codec) || sinkSupportsEncoded(codec);
    }

    public String compactWire()
    {
        StringBuilder decoders = new StringBuilder();
        for (Entry entry : audioDecoders)
        {
            if (decoders.length() > 0) decoders.append('|');
            decoders.append(entry.compact());
        }
        StringBuilder evidence = new StringBuilder();
        for (AudioCodec codec : AudioCodec.values())
        {
            if (evidence.length() > 0) evidence.append('|');
            evidence.append(clean(codec.getName()))
                    .append(",decoder=").append(hasDecoder(codec))
                    .append(",sink=").append(sinkSupportsEncoded(codec))
                    .append(",playable=").append(canPlay(codec));
        }
        return "audioCodecProfileCount=" + audioDecoders.size()
                + ";audioCodecProfiles=" + decoders
                + ";audioCodecEvidence=" + evidence
                + ";audioPassthroughAdvertised=false"
                + ";audioPassthroughState=not_inferred_from_mime";
    }

    private static DeviceAudioCapabilityProfile collect(Context context)
    {
        ArrayList<Entry> result = new ArrayList<Entry>();
        MediaCodecInfo[] infos;
        if (Build.VERSION.SDK_INT >= 21)
            infos = new MediaCodecList(MediaCodecList.ALL_CODECS).getCodecInfos();
        else
        {
            infos = new MediaCodecInfo[MediaCodecList.getCodecCount()];
            for (int i = 0; i < infos.length; i++) infos[i] = MediaCodecList.getCodecInfoAt(i);
        }
        for (MediaCodecInfo info : infos)
        {
            if (info == null || info.isEncoder()) continue;
            for (String type : info.getSupportedTypes())
            {
                if (type == null || !type.toLowerCase(Locale.US).startsWith("audio/")) continue;
                result.add(Entry.from(info, type));
            }
        }
        Collections.sort(result, new Comparator<Entry>()
        {
            @Override public int compare(Entry left, Entry right)
            {
                int mime = left.mimeType.compareTo(right.mimeType);
                return mime != 0 ? mime : left.codecName.compareTo(right.codecName);
            }
        });
        return new DeviceAudioCapabilityProfile(result,
                AudioCapabilities.getCapabilities(context));
    }

    private static final class Entry
    {
        final String codecName;
        final String mimeType;
        final String kind;

        private Entry(String codecName, String mimeType, String kind)
        {
            this.codecName = codecName;
            this.mimeType = mimeType;
            this.kind = kind;
        }

        static Entry from(MediaCodecInfo info, String mimeType)
        {
            boolean software = AndroidCodecPolicy.isSoftwareCodecName(info.getName());
            String kind = software ? "sw" : "hw";
            if (Build.VERSION.SDK_INT >= 29)
                kind = info.isSoftwareOnly() ? "sw" : info.isHardwareAccelerated() ? "hw" : "unknown";
            return new Entry(info.getName(), mimeType.toLowerCase(Locale.US), kind);
        }

        String compact()
        {
            return clean(codecName) + ',' + clean(mimeType) + ',' + kind;
        }
    }

    private static String clean(String value)
    {
        if (value == null || value.trim().isEmpty()) return "unknown";
        return value.trim().replace(';', '_').replace('|', '_').replace(',', '_').replace(' ', '_');
    }
}
