package opensagetv.vibe.miniclient.android.video;

import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.os.Build;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Read-only platform MediaCodec inventory used for diagnostics and selection evidence. */
public final class DeviceCodecCapabilityProfile
{
    private static volatile DeviceCodecCapabilityProfile cached;

    private final List<Entry> videoDecoders;

    private DeviceCodecCapabilityProfile(List<Entry> videoDecoders)
    {
        this.videoDecoders = Collections.unmodifiableList(videoDecoders);
    }

    public static DeviceCodecCapabilityProfile current()
    {
        DeviceCodecCapabilityProfile value = cached;
        if (value != null) return value;
        synchronized (DeviceCodecCapabilityProfile.class)
        {
            value = cached;
            if (value == null)
            {
                value = collect();
                cached = value;
            }
            return value;
        }
    }

    public List<Entry> getVideoDecoders()
    {
        return videoDecoders;
    }

    public Entry find(String codecName, String mimeType)
    {
        for (Entry entry : videoDecoders)
        {
            if (entry.codecName.equals(codecName) && entry.mimeType.equalsIgnoreCase(mimeType))
                return entry;
        }
        return null;
    }

    /** Prefer platform classification where available and retain the legacy name fallback. */
    public boolean isSoftwareDecoder(String codecName, String mimeType)
    {
        Entry entry = find(codecName, mimeType);
        return entry == null
                ? AndroidCodecPolicy.isSoftwareCodecName(codecName)
                : entry.softwareOnly;
    }

    /** Kodi preserves DTS for intermittent missing PTS on MediaTek and NVIDIA decoders. */
    public boolean hasMpeg2HardwareDecoderNeedingMissingPtsRepair()
    {
        for (Entry entry : videoDecoders)
        {
            String name = entry.codecName.toLowerCase(Locale.US);
            if ("video/mpeg2".equalsIgnoreCase(entry.mimeType)
                    && entry.hardwareAccelerated
                    && (name.startsWith("omx.mtk") || name.startsWith("c2.mtk")
                    || name.startsWith("omx.nvidia")))
                return true;
        }
        return false;
    }

    public String compactWire()
    {
        StringBuilder entries = new StringBuilder();
        for (Entry entry : videoDecoders)
        {
            if (entries.length() > 0) entries.append('|');
            entries.append(entry.compact());
        }
        return "codecDevice=" + clean(Build.MANUFACTURER) + '_' + clean(Build.MODEL)
                + ";codecApi=" + Build.VERSION.SDK_INT
                + ";codecProfileCount=" + videoDecoders.size()
                + ";codecInterlaceCapability=not_reported_by_android"
                + ";codecProfiles=" + entries;
    }

    private static DeviceCodecCapabilityProfile collect()
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
                if (!AndroidCodecPolicy.isVideoMimeType(type)) continue;
                try
                {
                    result.add(Entry.from(info, type));
                }
                catch (Throwable ignored)
                {
                    result.add(Entry.minimal(info.getName(), type));
                }
            }
        }
        Collections.sort(result, new Comparator<Entry>()
        {
            @Override
            public int compare(Entry left, Entry right)
            {
                int byMime = left.mimeType.compareTo(right.mimeType);
                return byMime != 0 ? byMime : left.codecName.compareTo(right.codecName);
            }
        });
        return new DeviceCodecCapabilityProfile(result);
    }

    public static final class Entry
    {
        public final String codecName;
        public final String canonicalName;
        public final String mimeType;
        public final boolean hardwareAccelerated;
        public final boolean softwareOnly;
        public final boolean vendor;
        public final boolean alias;
        public final boolean adaptive;
        public final boolean secure;
        public final boolean tunneled;
        public final int maximumWidth;
        public final int maximumHeight;
        public final String profileLevels;

        private Entry(String codecName, String canonicalName, String mimeType,
                      boolean hardwareAccelerated, boolean softwareOnly,
                      boolean vendor, boolean alias, boolean adaptive,
                      boolean secure, boolean tunneled, int maximumWidth,
                      int maximumHeight, String profileLevels)
        {
            this.codecName = codecName;
            this.canonicalName = canonicalName;
            this.mimeType = mimeType;
            this.hardwareAccelerated = hardwareAccelerated;
            this.softwareOnly = softwareOnly;
            this.vendor = vendor;
            this.alias = alias;
            this.adaptive = adaptive;
            this.secure = secure;
            this.tunneled = tunneled;
            this.maximumWidth = maximumWidth;
            this.maximumHeight = maximumHeight;
            this.profileLevels = profileLevels;
        }

        static Entry from(MediaCodecInfo info, String mimeType)
        {
            MediaCodecInfo.CodecCapabilities caps = info.getCapabilitiesForType(mimeType);
            boolean software = AndroidCodecPolicy.isSoftwareCodecName(info.getName());
            boolean hardware = !software;
            boolean vendor = hardware;
            boolean alias = false;
            String canonical = info.getName();
            if (Build.VERSION.SDK_INT >= 29)
            {
                software = info.isSoftwareOnly();
                hardware = info.isHardwareAccelerated();
                vendor = info.isVendor();
                alias = info.isAlias();
                canonical = info.getCanonicalName();
            }
            int width = 0;
            int height = 0;
            try
            {
                MediaCodecInfo.VideoCapabilities video = caps.getVideoCapabilities();
                width = video.getSupportedWidths().getUpper();
                height = video.getSupportedHeights().getUpper();
            }
            catch (Throwable ignored)
            {
            }
            StringBuilder profiles = new StringBuilder();
            if (caps.profileLevels != null)
            {
                for (MediaCodecInfo.CodecProfileLevel item : caps.profileLevels)
                {
                    if (profiles.length() > 0) profiles.append('.');
                    profiles.append(item.profile).append('_').append(item.level);
                }
            }
            return new Entry(info.getName(), canonical, mimeType.toLowerCase(Locale.US),
                    hardware, software, vendor, alias,
                    caps.isFeatureSupported(MediaCodecInfo.CodecCapabilities.FEATURE_AdaptivePlayback),
                    caps.isFeatureSupported(MediaCodecInfo.CodecCapabilities.FEATURE_SecurePlayback),
                    caps.isFeatureSupported(MediaCodecInfo.CodecCapabilities.FEATURE_TunneledPlayback),
                    width, height, profiles.length() == 0 ? "none" : profiles.toString());
        }

        static Entry minimal(String codecName, String mimeType)
        {
            boolean software = AndroidCodecPolicy.isSoftwareCodecName(codecName);
            return new Entry(codecName, codecName, mimeType.toLowerCase(Locale.US),
                    !software, software, !software, false, false, false, false,
                    0, 0, "unavailable");
        }

        String compact()
        {
            return clean(codecName) + ',' + clean(canonicalName) + ',' + clean(mimeType)
                    + ',' + (hardwareAccelerated ? "hw" : softwareOnly ? "sw" : "unknown")
                    + ",vendor=" + vendor + ",alias=" + alias
                    + ",adaptive=" + adaptive + ",secure=" + secure
                    + ",tunneled=" + tunneled + ",max=" + maximumWidth + 'x' + maximumHeight
                    + ",profiles=" + clean(profileLevels);
        }
    }

    private static String clean(String value)
    {
        if (value == null || value.trim().isEmpty()) return "unknown";
        return value.trim().replace(';', '_').replace('|', '_').replace(',', '_').replace(' ', '_');
    }
}
