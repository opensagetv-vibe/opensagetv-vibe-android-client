package opensagetv.vibe.miniclient.android.video.ijkplayer;

import android.annotation.TargetApi;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.Locale;

import opensagetv.vibe.miniclient.android.MiniclientApplication;
import opensagetv.vibe.miniclient.android.video.AndroidCodecPolicy;
import opensagetv.vibe.miniclient.android.video.DecodingMethod;
import opensagetv.vibe.miniclient.prefs.PrefStore;
import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.IjkMediaCodecInfo;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;

/**
 * Created by seans on 03/12/16.
 */

public class CodecSelector implements IjkMediaPlayer.OnMediaCodecSelectListener {
    private final static String TAG = "IJKCodecSelector";
    private static final String MIME_MPEG2 = "video/mpeg2";

    public static final CodecSelector sInstance = new CodecSelector();

    @SuppressWarnings("deprecation")
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public String onMediaCodecSelect(IMediaPlayer mp, String mimeType, int profile, int level) {
        Log.d(TAG, String.format(Locale.US, "onSelectCodec: mime=%s, profile=%d, level=%d", mimeType, profile, level));

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN)
            return null;

        if (TextUtils.isEmpty(mimeType))
            return null;

        DecodingMethod decodingMethod = DecodingMethod.fromPreference(
                MiniclientApplication.get().getClient().properties().getString(
                        PrefStore.Keys.decoding_method, DecodingMethod.DEFAULT_PREFERENCE));
        if (!decodingMethod.allowHardware()) {
            Log.d(TAG, "Decoding Method is Software. IJK MediaCodec selection disabled.");
            return null;
        }

        // The Amazon AFTMM / mantis MTK MPEG-2 decoder is a confirmed bad path for
        // SageTV's IJK custom-data-source playback. The original SageTV IJK 0.8.8 runtime can get stuck repeatedly failing
        // dequeueInputBuffer() on this device/codec pair.
        // Returning null here is a second safety net in case the native runtime asks
        // for a codec even though IjkDecoderOptions disabled mediacodec-mpeg2.
        if (MIME_MPEG2.equalsIgnoreCase(mimeType) && isKnownBrokenMpeg2HardwareDevice()) {
            Log.w(TAG, "Rejecting broken Amazon AFTMM/mantis MPEG-2 MediaCodec; using IJK/FFmpeg software decoder");
            return null;
        }

        // Log.i(TAG, String.format(Locale.US, "onSelectCodec: mime=%s, profile=%d, level=%d", mimeType, profile, level));
        ArrayList<IjkMediaCodecInfo> candidateCodecList = new ArrayList<IjkMediaCodecInfo>();
        int numCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
            Log.d(TAG, String.format(Locale.US, "  found codec: %s", codecInfo.getName()));
            if (codecInfo.isEncoder())
                continue;

            if (AndroidCodecPolicy.isCodecDisabled(
                    MiniclientApplication.get().getClient().properties(), codecInfo.getName())) {
                Log.d(TAG, "Skipping BLOCKED Codec " + codecInfo.getName());
                continue;
            }

            if (!isCodecUsableDecoder(codecInfo, codecInfo.getName(), false)) {
                Log.d(TAG, "Skipping BROKEN/UNUSABLE Codec " + codecInfo.getName());
                continue;
            }

            // Decoding Method=Hardware/Hardware Preferred uses vendor/platform hardware
            // MediaCodec only. Decoding Method=Software disables MediaCodec entirely and
            // lets IJK/FFmpeg decode, so Android software MediaCodec candidates are never
            // mixed into the hardware candidate list.
            if (AndroidCodecPolicy.isSoftwareCodecName(codecInfo.getName())) {
                Log.d(TAG, "Skipping SOFTWARE MediaCodec candidate " + codecInfo.getName());
                continue;
            }

            String[] types = codecInfo.getSupportedTypes();
            if (types == null)
                continue;

            for(String type: types) {
                if (TextUtils.isEmpty(type))
                    continue;

                Log.d(TAG, String.format(Locale.US, "    mime: %s", type));
                if (!type.equalsIgnoreCase(mimeType))
                    continue;

                IjkMediaCodecInfo candidate = IjkMediaCodecInfo.setupCandidate(codecInfo, mimeType);
                if (candidate == null)
                    continue;

                candidateCodecList.add(candidate);
                Log.i(TAG, String.format(Locale.US, "candidate codec: %s rank=%d", codecInfo.getName(), candidate.mRank));
                candidate.dumpProfileLevels(mimeType);
            }
        }

        if (candidateCodecList.isEmpty()) {
            return null;
        }

        IjkMediaCodecInfo bestCodec = candidateCodecList.get(0);

        for (IjkMediaCodecInfo codec : candidateCodecList) {
            if (codec.mRank > bestCodec.mRank) {
                bestCodec = codec;
            }
        }

        // In Hardware mode the user explicitly asked us to test hardware, so a usable
        // hardware codec is selected even when old IJK gives it a low rank. In Hardware
        // Preferred mode a low-ranked codec is rejected so legacy IJK falls back to its bundled FFmpeg software decoder.
        if (bestCodec.mRank < IjkMediaCodecInfo.RANK_LAST_CHANCE) {
            if (decodingMethod == DecodingMethod.HARDWARE) {
                Log.w(TAG, String.format(Locale.US, "Hardware mode forcing low-ranked hardware codec: %s; Rank: %d", bestCodec.mCodecInfo.getName(), bestCodec.mRank));
            } else {
                Log.w(TAG, String.format(Locale.US, "Hardware Preferred rejecting low-ranked codec for software fallback: %s; Rank: %d", bestCodec.mCodecInfo.getName(), bestCodec.mRank));
                return null;
            }
        }

        Log.i(TAG, String.format(Locale.US, "selected codec: %s rank=%d", bestCodec.mCodecInfo.getName(), bestCodec.mRank));
        return bestCodec.mCodecInfo.getName();
    }

    static boolean isKnownBrokenMpeg2HardwareDevice() {
        return "Amazon".equalsIgnoreCase(Build.MANUFACTURER)
                && ("mantis".equalsIgnoreCase(Build.DEVICE)
                || "AFTMM".equalsIgnoreCase(Build.MODEL));
    }



    /**
     * Copied from ExoPlayer, since, it does a good job of blacklisting some decoders
     * https://github.com/google/ExoPlayer/blob/7d991cef305e95cae5cd2a9feadf4af8858b284b/library/src/main/java/com/google/android/exoplayer2/mediacodec/MediaCodecUtil.java
     * @param info codec info
     * @param name codec name
     * @param secureDecodersExplicit Whether the decoder is required to support secure decryption. Always pass false
     *     unless secure decryption really is required.
     * @return
     */
    private static boolean isCodecUsableDecoder(android.media.MediaCodecInfo info, String name,
                                                boolean secureDecodersExplicit) {
        if (info.isEncoder() || (!secureDecodersExplicit && name.endsWith(".secure"))) {
            return false;
        }

        // Work around broken audio decoders.
        if (Build.VERSION.SDK_INT < 21
                && ("CIPAACDecoder".equals(name)
                || "CIPMP3Decoder".equals(name)
                || "CIPVorbisDecoder".equals(name)
                || "AACDecoder".equals(name)
                || "MP3Decoder".equals(name))) {
            return false;
        }
        // Work around https://github.com/google/ExoPlayer/issues/398
        if (Build.VERSION.SDK_INT < 18 && "OMX.SEC.MP3.Decoder".equals(name)) {
            return false;
        }
        // Work around https://github.com/google/ExoPlayer/issues/1528
        if (Build.VERSION.SDK_INT < 18 && "OMX.MTK.AUDIO.DECODER.AAC".equals(name)
                && "a70".equals(Build.DEVICE)) {
            return false;
        }

        // Work around an issue where querying/creating a particular MP3 decoder on some devices on
        // platform API version 16 fails.
        if (Build.VERSION.SDK_INT == 16
                && "OMX.qcom.audio.decoder.mp3".equals(name)
                && ("dlxu".equals(Build.DEVICE) // HTC Butterfly
                || "protou".equals(Build.DEVICE) // HTC Desire X
                || "ville".equals(Build.DEVICE) // HTC One S
                || "villeplus".equals(Build.DEVICE)
                || "villec2".equals(Build.DEVICE)
                || Build.DEVICE.startsWith("gee") // LGE Optimus G
                || "C6602".equals(Build.DEVICE) // Sony Xperia Z
                || "C6603".equals(Build.DEVICE)
                || "C6606".equals(Build.DEVICE)
                || "C6616".equals(Build.DEVICE)
                || "L36h".equals(Build.DEVICE)
                || "SO-02E".equals(Build.DEVICE))) {
            return false;
        }

        // Work around an issue where large timestamps are not propagated correctly.
        if (Build.VERSION.SDK_INT == 16
                && "OMX.qcom.audio.decoder.aac".equals(name)
                && ("C1504".equals(Build.DEVICE) // Sony Xperia E
                || "C1505".equals(Build.DEVICE)
                || "C1604".equals(Build.DEVICE) // Sony Xperia E dual
                || "C1605".equals(Build.DEVICE))) {
            return false;
        }

        // Work around https://github.com/google/ExoPlayer/issues/548
        // VP8 decoder on Samsung Galaxy S3/S4/S4 Mini/Tab 3 does not render video.
        if (Build.VERSION.SDK_INT <= 19
                && (Build.DEVICE.startsWith("d2") || Build.DEVICE.startsWith("serrano")
                || Build.DEVICE.startsWith("jflte") || Build.DEVICE.startsWith("santos"))
                && "samsung".equals(Build.MANUFACTURER) && "OMX.SEC.vp8.dec".equals(name)) {
            return false;
        }
        // VP8 decoder on Samsung Galaxy S4 cannot be queried.
        if (Build.VERSION.SDK_INT <= 19 && Build.DEVICE.startsWith("jflte")
                && "OMX.qcom.video.decoder.vp8".equals(name)) {
            return false;
        }

        return true;
    }
}
