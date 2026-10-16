package opensagetv.vibe.miniclient.android;

import android.app.Activity;
import java.util.*;
import opensagetv.vibe.miniclient.*;
import opensagetv.vibe.miniclient.media.*;
import opensagetv.vibe.miniclient.android.diagnostics.DiagnosticRedactor;
import opensagetv.vibe.miniclient.android.diagnostics.DiagnosticCaptionMetadata;

/** Main-thread, read-only facade over the existing statistics overlay. Never
 * call this from an HTTP worker: Exo/Media3 getters have thread affinity.
 */
public final class ClientDiagnosticSnapshot {
    private ClientDiagnosticSnapshot() { }
    private static Object number(long value) { return value < 0 ? null : value; }
    private static Object text(String value) { return value == null || value.isEmpty() ? null : DiagnosticRedactor.redact(value); }
    public static Map<String,Object> capture(Activity activity, MiniClient client, MiniClientConnection connection) {
        Map<String,Object> result = new LinkedHashMap<>();
        MediaCmd media = connection == null ? null : connection.getMediaCmd();
        ActivePlayerStatsSnapshot stats = ActivePlayerStatsSnapshot.capture(activity, client, media);
        Map<String,Object> p = new LinkedHashMap<>();
        p.put("active",stats.active); p.put("backend",stats.backend); p.put("transport",stats.transport); p.put("state",stats.state);
        p.put("positionMs",number(stats.positionMs)); p.put("durationMs",number(stats.durationMs));
        p.put("bufferedAheadMs",number(stats.bufferedAheadMs)); p.put("bufferBytes",number(stats.bufferBytes));
        p.put("decoder",text(stats.videoDecoder)); p.put("decoderKind",text(stats.videoDecoderKind));
        p.put("deinterlace",text(stats.deinterlace));
        p.put("deinterlaceImplementation",text(stats.deinterlaceImplementation));
        p.put("deinterlaceFilter",text(stats.deinterlaceFilter));
        p.put("gpuReason",text(stats.gpuReason)); p.put("gpuInputFrames",stats.gpuInputFrames);
        p.put("gpuOutputFrames",stats.gpuOutputFrames); p.put("gpuSecondFields",stats.gpuSecondFields);
        p.put("videoDecodeFps",stats.videoDecodeFps >= 0 ? stats.videoDecodeFps : null);
        p.put("videoOutputFps",stats.videoOutputFps >= 0 ? stats.videoOutputFps : null);
        p.put("videoMime",text(stats.videoMime)); p.put("width",number(stats.videoWidth)); p.put("height",number(stats.videoHeight));
        p.put("fps",stats.contentFps > 0 ? stats.contentFps : null); p.put("displayHz",stats.displayHz > 0 ? stats.displayHz : null);
        p.put("renderedFrames",number(stats.videoRendered)); p.put("droppedFrames",number(stats.videoDropped)); p.put("skippedFrames",number(stats.videoSkipped));
        p.put("audioMime",text(stats.audioMime)); p.put("audioDecoder",text(stats.audioDecoder)); p.put("audioChannels",number(stats.audioChannels));
        p.put("audioSampleRate",number(stats.audioSampleRate)); p.put("audioOutput",text(stats.audioOutput)); p.put("configuredAudioOffsetMs",stats.audioOffsetMs);
        p.put("audioOffsetPath",text(stats.audioOffsetPath)); p.put("measuredLipSyncMs",null);
        // Background prefetch (isLoading) can occur during healthy playback.
        // Only the Exo-family STATE_BUFFERING value means playback buffering;
        // native backends without this getter are unknown, not inferred stalled.
        p.put("seekPending",stats.seekPending); p.put("loading",stats.loading);
        p.put("buffering",stats.backendPlaybackState < 0 ? null : stats.backendPlaybackState == 2);
        p.put("retryCount",stats.retryCount); p.put("playerError",text(stats.playerError)); p.put("decoderEvents",text(stats.decoderEvents));
        p.put("readWaitMs",number(stats.readWaitMs)); p.put("readErrors",number(stats.readErrors));
        p.put("details",DiagnosticRedactor.redact(stats.exportText(stats.reportedActivityKbps)));
        result.put("playback",p);
        Map<String,Object> captions = new LinkedHashMap<>();
        MiniPlayerPlugin player = media == null ? null : media.getPlaya();
        // Internal identity only: the HTTP owner removes this before JSON.
        // A connection's MediaCmd survives source/player replacement, so it
        // cannot itself identify a new playback context for pending seeks.
        result.put("_timelineOwner",player);
        List<Map<String,Object>> tracks = new ArrayList<>();
        if (player != null) {
            int selected = player.getSelectedSubtitleTrack();
            captions.put("selectedTrack",selected == MiniPlayerPlugin.DISABLE_TRACK ? "Off" : selected);
            captions.put("cc1Track",player.getMappedSubtitleTrackForClosedCaptionChannel(1));
            captions.put("cc2Track",player.getMappedSubtitleTrackForClosedCaptionChannel(2));
            boolean observedCea = player.hasObservedCeaCaptionData(); captions.put("observedCea",observedCea);
            SubtitleTrack[] inventory = player.getSubtitleTracks();
            if (inventory != null) for (SubtitleTrack track : inventory) {
                if (track == null || tracks.size() >= 64) continue;
                SubtitleCodec codec = track.getSubtitleCodec();
                boolean cea = codec == SubtitleCodec.CEA608 || codec == SubtitleCodec.CEA708;
                boolean broadcast = cea || codec == SubtitleCodec.TELETEXT || codec == SubtitleCodec.DVB;
                Map<String,Object> row = new LinkedHashMap<>(); row.put("id",track.getIndex());
                row.put("type",codec == null ? "Unknown" : codec.getName()); row.put("language",text(track.getLanguage()));
                row.put("label",text(track.getLabel())); row.put("supported",track.isSupported());
                row.put("selected",track.getIndex() == selected); row.put("category",broadcast ? "Broadcast CC" : "File/DVD subtitle");
                row.put("evidence",cea ? (observedCea ? "CEA packets observed; per-service payload not verified"
                        : "Extractor-declared; no CEA packets observed") : "Discovered service");
                row.put("service",track.getAccessibilityChannel() > 0 ? track.getAccessibilityChannel() : null);
                row.put("pid",DiagnosticCaptionMetadata.transportPid(codec == SubtitleCodec.DVB
                        || codec == SubtitleCodec.TELETEXT, track.getSourceStreamId())); tracks.add(row);
            }
        }
        captions.put("services",tracks);
        captions.put("callbackNegotiated",connection != null && connection.isSubtitleCallbackEnabled());
        captions.put("callbackEvents",connection == null ? null : connection.getSubtitleCallbackEventCount());
        captions.put("callbackBytes",connection == null ? null : connection.getSubtitleCallbackByteCount());
        if(media!=null){captions.put("stvStateKnown",media.hasSageTvClosedCaptionState());captions.put("stvState",media.getSageTvClosedCaptionState());}
        result.put("captions",captions);
        result.put("seekSequence",media == null ? 0 : media.getLastServerSeekSequence());
        result.put("seekTargetMs",media == null ? -1 : media.getLastServerRequestedSeekMs());
        result.put("seekWallMs",media == null ? 0 : media.getLastServerSeekWallMs());
        return result;
    }
}
