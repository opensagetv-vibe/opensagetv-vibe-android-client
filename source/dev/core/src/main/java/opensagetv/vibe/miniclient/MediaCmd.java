/*
 * Copyright 2015 The SageTV Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package opensagetv.vibe.miniclient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import opensagetv.vibe.miniclient.media.MediaUrlContext;
import opensagetv.vibe.miniclient.prefs.PrefStore;
import opensagetv.vibe.miniclient.uibridge.Dimension;
import opensagetv.vibe.miniclient.uibridge.Rectangle;
import opensagetv.vibe.miniclient.util.Utils;
import opensagetv.vibe.miniclient.util.VerboseLogging;

/**
 * @author Narflex
 */
public class MediaCmd
{
    public static final int MEDIACMD_INIT = 0;
    public static final int MEDIACMD_DEINIT = 1;
    public static final int MEDIACMD_OPENURL = 16;
    public static final int MEDIACMD_GETMEDIATIME = 17;
    // length, url
    public static final int MEDIACMD_SETMUTE = 18;
    public static final int MEDIACMD_STOP = 19;
    // mute
    public static final int MEDIACMD_PAUSE = 20;
    public static final int MEDIACMD_PLAY = 21;
    public static final int MEDIACMD_FLUSH = 22;
    public static final int MEDIACMD_PUSHBUFFER = 23;
    public static final int MEDIACMD_GETVIDEORECT = 24;
    // size, flags, data
    public static final int MEDIACMD_SETVIDEORECT = 25;
    // returns 16bit width, 16bit height
    public static final int MEDIACMD_GETVOLUME = 26;
    // x, y, width, height, x, y, width, height
    public static final int MEDIACMD_SETVOLUME = 27;
    // volume
    public static final int MEDIACMD_FRAMESTEP = 28;
    public static final int MEDIACMD_SEEK = 29;
    public static final int MEDIACMD_SETRATE = 30;
    public static final int MEDIACMD_DVD_NEWCELL = 32;
    public static final int MEDIACMD_DVD_CLUT = 33;
    public static final int MEDIACMD_DVD_SPUCTRL = 34;
    public static final int MEDIACMD_DVD_STC = 35;
    public static final int MEDIACMD_DVD_STREAMS = 36;
    public static final int MEDIACMD_DVD_FORMAT = 37;


    public static final int STREAM_TYPE_AUDIO = 0;
    public static final int STREAM_TYPE_SUBTITLE = 1;

    public static final Map<Integer, String> CMDMAP = new HashMap<Integer, String>();
    private static final Logger log = LoggerFactory.getLogger(MediaCmd.class);

    private final MiniClient client;
    private MiniPlayerPlugin playa;
    private volatile int sageTvClosedCaptionState;
    private volatile boolean sageTvClosedCaptionStateReceived;
    private boolean pushMode;
    private boolean dvdSessionPending;
    private long dvdPushedBytes;
    private long dvdEpochPushedBytes;
    private volatile long dvdLastReadBytes = -1;
    private volatile long dvdDrainPollCount;
    private volatile long dvdDrainReadyCount;
    private volatile long dvdDecoderBufferedAheadMs = -1;
    private volatile long dvdInitCount;
    private volatile long dvdPushCommandCount;
    private volatile long dvdPushMediaCount;
    private volatile long dvdNewCellCount;
    private volatile long dvdClutCount;
    private volatile long dvdSpuControlCount;
    private volatile long dvdStcCount;
    private volatile long dvdStreamCount;
    private volatile int dvdLastStreamType = -1;
    private volatile int dvdLastStreamPosition = -1;
    private volatile int dvdLastAudioStreamPosition = -1;
    private volatile int dvdLastSubtitleStreamPosition = -1;
    private volatile long dvdFormatCount;
    private volatile long dvdTransientEosCount;
    private volatile boolean dvdMimRuntimeFallback;
    private long dvdDrainSignaledEpochBytes = Long.MIN_VALUE;
    private volatile int lastPushReply;
    private int DESIRED_VIDEO_PREBUFFER_SIZE = 16 * 1024 * 1024;
    private int DESIRED_AUDIO_PREBUFFER_SIZE = 2 * 1024 * 1024;
    private int maxPrebufferSize;
    private MiniClientConnection myConn;
    private long lastServerStartTime = -1;
    private long lastServerRequestedSeekMs = -1;
    private long lastServerSeekSequence = 0;
    private long lastServerSeekMonotonicMs = -1;
    private long lastServerSeekWallMs = -1;
    private long lastServerFlushSequence = 0;
    private long lastServerFlushMonotonicMs = -1;
    private long lastServerAnchorSequence = 0;
    private long lastServerAnchorMonotonicMs = -1;
    private volatile String lastOpenUrl = "";
    private volatile String lastOpenChannel = "";
    private volatile int serverChannelBandwidthKbps = -1;
    private volatile int serverStreamBandwidthKbps = -1;
    private volatile int serverTargetBandwidthKbps = -1;
    private volatile long serverMuxTimeMs = -1;
    private volatile long clientBufferTimeMs = -1;
    private volatile int clientBufferAvailableBytes = -1;
    private volatile int lastPushPayloadBytes;
    private volatile int lastPushFlags;
    private volatile long detailedPushSampleSequence;
    private volatile long detailedPushSampleMonotonicMs = -1;
    private volatile long detailedPushSampleWallMs = -1;
    private volatile boolean restartPlayerOnNextFlush;
    private volatile long controlledReloadTargetMs = -1;
    private volatile long controlledReloadRequestedMs = -1;
    private volatile int controlledReloadCorrectionCount;
    private volatile boolean controlledReloadAwaitingReplacementStc;
    // Bounded debug evidence for the brief OPENURL interval where stock STVs
    // can paint an incorrect end-of-file timeline. The reflective bridge is a
    // no-op in release/desktop builds, and the counter prevents continuous
    // GETMEDIATIME instrumentation.
    private long startupMediaTimeTraceDeadlineMs = -1;
    private int startupMediaTimeTraceRemaining;

    static
    {
        CMDMAP.put(MEDIACMD_INIT, "MEDIACMD_INIT");
        CMDMAP.put(MEDIACMD_DEINIT, "MEDIACMD_DEINIT");
        CMDMAP.put(MEDIACMD_OPENURL, "MEDIACMD_OPENURL");
        CMDMAP.put(MEDIACMD_GETMEDIATIME, "MEDIACMD_GETMEDIATIME");

        CMDMAP.put(MEDIACMD_SETMUTE, "MEDIACMD_SETMUTE");
        CMDMAP.put(MEDIACMD_STOP, "MEDIACMD_STOP");

        CMDMAP.put(MEDIACMD_PAUSE, "MEDIACMD_PAUSE");
        CMDMAP.put(MEDIACMD_PLAY, "MEDIACMD_PLAY");
        CMDMAP.put(MEDIACMD_FLUSH, "MEDIACMD_FLUSH");
        CMDMAP.put(MEDIACMD_PUSHBUFFER, "MEDIACMD_PUSHBUFFER");
        CMDMAP.put(MEDIACMD_GETVIDEORECT, "MEDIACMD_GETVIDEORECT");

        CMDMAP.put(MEDIACMD_SETVIDEORECT, "MEDIACMD_SETVIDEORECT");

        CMDMAP.put(MEDIACMD_GETVOLUME, "MEDIACMD_GETVOLUME");

        CMDMAP.put(MEDIACMD_SETVOLUME, "MEDIACMD_SETVOLUME");

        CMDMAP.put(MEDIACMD_FRAMESTEP, "MEDIACMD_FRAMESTEP");
        CMDMAP.put(MEDIACMD_SEEK, "MEDIACMD_SEEK");
        CMDMAP.put(MEDIACMD_SETRATE, "MEDIACMD_SETRATE");
        CMDMAP.put(MEDIACMD_DVD_NEWCELL, "MEDIACMD_DVD_NEWCELL");
        CMDMAP.put(MEDIACMD_DVD_CLUT, "MEDIACMD_DVD_CLUT");
        CMDMAP.put(MEDIACMD_DVD_SPUCTRL, "MEDIACMD_DVD_SPUCTRL");
        CMDMAP.put(MEDIACMD_DVD_STC, "MEDIACMD_DVD_STC");
        CMDMAP.put(MEDIACMD_DVD_STREAMS, "MEDIACMD_DVD_STREAMS");
        CMDMAP.put(MEDIACMD_DVD_FORMAT, "MEDIACMD_DVD_FORMAT");
    }

    /**
     * Creates a new instance of MediaCmd
     */
    public MediaCmd(MiniClient client)
    {
        this.client = client;
        this.myConn = client.getCurrentConnection();
    }

    public static void writeInt(int value, byte[] data, int offset)
    {
        data[offset] = (byte) ((value >> 24) & 0xFF);
        data[offset + 1] = (byte) ((value >> 16) & 0xFF);
        data[offset + 2] = (byte) ((value >> 8) & 0xFF);
        data[offset + 3] = (byte) (value & 0xFF);
    }

    public static void writeShort(short value, byte[] data, int offset)
    {
        data[offset] = (byte) ((value >> 8) & 0xFF);
        data[offset + 1] = (byte) (value & 0xFF);
    }

    public static int readInt(int pos, byte[] cmddata)
    {
        return ((cmddata[pos + 0] & 0xFF) << 24) | ((cmddata[pos + 1] & 0xFF) << 16) | ((cmddata[pos + 2] & 0xFF) << 8) | (cmddata[pos + 3] & 0xFF);
    }

    public static short readShort(int pos, byte[] cmddata)
    {
        return (short) (((cmddata[pos + 0] & 0xFF) << 8) | (cmddata[pos + 1] & 0xFF));
    }

    public MiniPlayerPlugin getPlaya()
    {
        return playa;
    }

    /**
     * Applies the server/STV closed-caption state to the Android decoder.
     * The state is retained when no player exists so a subsequent OPENURL
     * receives the same authoritative SageTV selection.
     */
    public void setSageTvClosedCaptionState(int ccState)
    {
        sageTvClosedCaptionState = Math.max(0, ccState);
        sageTvClosedCaptionStateReceived = true;
        applySageTvClosedCaptionState();
    }

    public boolean hasSageTvClosedCaptionState()
    {
        return sageTvClosedCaptionStateReceived;
    }

    public int getSageTvClosedCaptionState()
    {
        return sageTvClosedCaptionState;
    }

    public boolean isLegacyServerCaptionFallbackActive()
    {
        if (sageTvClosedCaptionStateReceived)
            return false;
        PrefStore properties = client.properties();
        if (properties == null)
            return false;
        String mode = properties.getString(
                PrefStore.Keys.legacy_server_caption_mode, "stv");
        return "cc1".equals(mode) || "cc2".equals(mode);
    }

    /**
     * Changes the compatibility caption mode used when an older SageTV server
     * cannot publish the STV's VIDEO_CC_STATE. The choice is persisted and is
     * applied to the active player immediately. If the server has published a
     * state, that authoritative STV state continues to win.
     */
    public void setLegacyServerCaptionMode(String requestedMode)
    {
        String mode = requestedMode == null ? "stv" : requestedMode.trim().toLowerCase();
        if (!"off".equals(mode) && !"cc1".equals(mode) && !"cc2".equals(mode))
            mode = "stv";
        client.properties().setString(PrefStore.Keys.legacy_server_caption_mode, mode);
        applySageTvClosedCaptionState();
    }

    public String getLegacyServerCaptionMode()
    {
        String mode = client.properties().getString(
                PrefStore.Keys.legacy_server_caption_mode, "stv");
        if ("off".equals(mode) || "cc1".equals(mode) || "cc2".equals(mode))
            return mode;
        return "stv";
    }

    private void applySageTvClosedCaptionState()
    {
        MiniPlayerPlugin currentPlayer = playa;
        if (currentPlayer == null)
            return;

        boolean captionsEnabled = sageTvClosedCaptionStateReceived
                ? sageTvClosedCaptionState != 0
                : isLegacyServerCaptionFallbackActive();
        if (!captionsEnabled)
            currentPlayer.setSubtitleTrack(MiniPlayerPlugin.DISABLE_TRACK);
        else
        {
            if (!sageTvClosedCaptionStateReceived)
            {
                String mode = client.properties().getString(
                        PrefStore.Keys.legacy_server_caption_mode, "stv");
                client.properties().setString(PrefStore.Keys.preferred_caption_standard, "cea608");
                client.properties().setString(PrefStore.Keys.preferred_caption_service,
                        "cc2".equals(mode) ? "2" : "1");
            }
            currentPlayer.setPreferredSubtitleTrack();
        }
    }

    public String getLastOpenUrlForDebug()
    {
        return lastOpenUrl;
    }

    public String getLastOpenChannelForDebug()
    {
        return lastOpenChannel;
    }

    /**
     * Rebuild the Android decoder at the same absolute DVD position.
     *
     * <p>The actual release is performed by the media-command thread when the
     * compatible SageTV server sends FLUSH for the seek. This avoids racing an
     * in-flight PUSHBUFFER on the UI thread. Ordinary Pull/Push sessions are
     * intentionally rejected until they have an equivalent server-owned
     * reload handshake.</p>
     */
    public boolean requestControlledPlayerReload()
    {
        if (!dvdSessionPending || playa == null || myConn == null)
            return false;
        long targetMs = Math.max(0L, getMediaTimeMillis());
        controlledReloadTargetMs = targetMs;
        controlledReloadRequestedMs = targetMs;
        controlledReloadCorrectionCount = 0;
        controlledReloadAwaitingReplacementStc = false;
        restartPlayerOnNextFlush = true;
        if (!myConn.postVibeSeekEvent(targetMs))
        {
            restartPlayerOnNextFlush = false;
            controlledReloadTargetMs = -1;
            controlledReloadRequestedMs = -1;
            controlledReloadAwaitingReplacementStc = false;
            return false;
        }
        return true;
    }

    public void close()
    {
        if (myConn.getGfxCmd() != null)
            myConn.getGfxCmd().setVideoBounds(null, null);
        if (playa != null)
            playa.free();
        playa = null;
    }

    public int ExecuteMediaCommand(int cmd, int len, byte[] cmddata, byte[] retbuf)
    {
        if (VerboseLogging.DETAILED_MEDIA_COMMAND)
        {
            if (VerboseLogging.DETAILED_MEDIA_COMMAND_PUSHBUFFER || cmd != MEDIACMD_PUSHBUFFER)
            {
                log.debug("MEDIACMD='{}[{}]'", cmd, CMDMAP.get(cmd));
            }
        }
        switch (cmd)
        {
            case MEDIACMD_INIT:
                resetDvdProtocolStats();
                dvdInitCount++;
                try
                {
                    DESIRED_VIDEO_PREBUFFER_SIZE = client.properties().getInt(PrefStore.Keys.video_buffer_size, (4 * 1024 * 1024));
                    DESIRED_AUDIO_PREBUFFER_SIZE = client.properties().getInt(PrefStore.Keys.audio_buffer_size, (2 * 1024 * 1024));
                }
                catch (Exception e)
                {
                    log.error("MEDIACMD_INIT: ERROR", e);
                }

                readInt(0, cmddata); // video format code
                // MiniDVDPlayer starts with INIT and then pushes MPEG data; it
                // does not send OPENURL. Defer construction until the first
                // DVD metadata or PUSHBUFFER command so ordinary sessions keep
                // their existing INIT -> OPENURL ownership.
                dvdSessionPending = true;
                // MiniDVDPlayer does not send OPENURL, so it cannot rely on
                // OPENURL to publish the available Push-buffer capacity. A
                // zero reply leaves the server VM polling empty PUSHBUFFER
                // commands forever and it never sends DVD metadata or MPEG.
                maxPrebufferSize = DESIRED_VIDEO_PREBUFFER_SIZE;
                writeInt(1, retbuf, 0);
                return 4;

            case MEDIACMD_DEINIT:

                writeInt(1, retbuf, 0);
                PlaybackDebugEventBridge.recordAsync("server_deinit_command", playa);
                close();
                return 4;

            case MEDIACMD_OPENURL:
                this.setLastServerStartPosition(-1);
                resetDetailedPushStats();
                startupMediaTimeTraceDeadlineMs = monotonicMs() + 4_000L;
                startupMediaTimeTraceRemaining = 12;

                int strLen = readInt(0, cmddata);
                String urlString = "";
                maxPrebufferSize = DESIRED_VIDEO_PREBUFFER_SIZE;
                
                if (strLen > 1)
                {
                    urlString = new String(cmddata, 4, strLen - 1);
                    log.debug("JVL - MEDIACMD_OPENURL {}", urlString);
                }
                PlaybackDebugEventBridge.recordAsyncDetailed(
                        "server_openurl_command", playa, "source=" + urlString);

                String lowerUrl = urlString.toLowerCase();
                // An explicit Vibe transport URL changes only the pushed media
                // representation. It remains the same DVD wire session.
                dvdSessionPending = lowerUrl.startsWith("push:dvd");
                dvdMimRuntimeFallback = dvdSessionPending
                        && lowerUrl.contains("fallback=mim_failure");
                boolean legacyActive = lowerUrl.endsWith(".mpg")
                        || lowerUrl.endsWith(".ts") || lowerUrl.endsWith(".flv");
                MediaUrlContext mediaContext = MediaUrlContext.parse(urlString, legacyActive);
                urlString = mediaContext.getUrl();
                lastOpenUrl = urlString;
                lastOpenChannel = mediaContext.getChannelHint();
                if (mediaContext.isExplicit())
                {
                    log.debug("Explicit media URL state: active={}, buffer={}, major={}, minor={}, channel={}",
                            mediaContext.isActive(), mediaContext.getBufferSize(),
                            mediaContext.getMajorTypeHint(), mediaContext.getMinorTypeHint(),
                            mediaContext.getChannelHint());
                }
                
                if (!urlString.startsWith("push:"))
                {
                    if (urlString.startsWith("dvd:"))
                    {
                        log.error("DVD PlayBack not supported");
                    }
                    else if (urlString.startsWith("file://"))
                    {
                        playa = myConn.newPlayerPlugin( urlString);//new MiniMPlayerPlugin(myConn.getGfxCmd(), myConn);
                        playa.setPushMode(false);
                        playa.setServerMediaMetadataExplicit(mediaContext.isExplicit());
                        playa.load(mediaContext.getMajorTypeHint(), mediaContext.getMinorTypeHint(),
                                mediaContext.getEncodingHint(), urlString, null,
                                mediaContext.isActive(), mediaContext.getBufferSize());
                        notifyPlaybackLoadStarted();
                        applySageTvClosedCaptionState();
                        pushMode = false;
                    }
                    else
                    {
                    
                        playa = myConn.newPlayerPlugin( urlString);//new MiniMPlayerPlugin(myConn.getGfxCmd(), myConn);
                        // We always set it to be an active file because it'll get turned off by the streaming code if it is not.
                        // It's safe to say it's active when it's not (as long as it's a streamable file format), but the opposite is not true.
                        // So we always say it's active to avoid any problems loading the file if it's a streamable file format.
                        boolean isActive = mediaContext.isActive();
                        playa.setPushMode(false);
                        playa.setServerMediaMetadataExplicit(mediaContext.isExplicit());
                        playa.load(mediaContext.getMajorTypeHint(), mediaContext.getMinorTypeHint(),
                                mediaContext.getEncodingHint(), urlString, myConn.getServerName(),
                                isActive, mediaContext.getBufferSize());
                        notifyPlaybackLoadStarted();
                        applySageTvClosedCaptionState();
                        pushMode = false;
                    }
                }
                else
                {
                    pushMode = true;
                    {
                        if (playa != null)
                            playa.free();
                        if (urlString.indexOf("audio") != -1 && urlString.indexOf("bf=vid") == -1) {
                            maxPrebufferSize = DESIRED_AUDIO_PREBUFFER_SIZE;
                        } else {
                            maxPrebufferSize = DESIRED_VIDEO_PREBUFFER_SIZE;
                        }
                        playa = myConn.newPlayerPlugin( urlString);//new MiniMPlayerPlugin(myConn.getGfxCmd(), myConn);
                        playa.setPushMode(true);
                        playa.setServerMediaMetadataExplicit(mediaContext.isExplicit());
                        playa.load((byte) 0, (byte) 0, "", urlString, null, true, 0);
                        notifyPlaybackLoadStarted();
                        applySageTvClosedCaptionState();
                    }
                }
                writeInt(1, retbuf, 0);

                return 4;
            case MEDIACMD_GETMEDIATIME:
                // MiniDVDPlayer queries media time after INIT but before its
                // first metadata/PUSHBUFFER command.  The server always waits
                // for an integer reply; returning zero bytes here deadlocks the
                // media socket until SageTV's 30-second timeout closes the UI.
                long theTime = playa == null ? 0 : getMediaTimeMillis();
                writeInt((int) theTime, retbuf, 0);
                if (startupMediaTimeTraceRemaining > 0
                        && monotonicMs() <= startupMediaTimeTraceDeadlineMs)
                {
                    startupMediaTimeTraceRemaining--;
                    PlaybackDebugEventBridge.recordAsyncDetailed(
                            "server_media_time_startup_reply", playa,
                            "replyMs=" + theTime
                                    + ";wireInt=" + (int) theTime
                                    + ";playerPresent=" + (playa != null)
                                    + ";playerState=" + (playa == null ? -1 : playa.getState())
                                    + ";serverAnchorMs=" + getLastServerStartPosition());
                }
                // MiniDVDPlayer's legacy DVD wire protocol always consumes a
                // four-byte GETMEDIATIME reply.  The optional fifth playback-
                // state byte used by ordinary MiniPlayer sessions would remain
                // queued and shift every subsequent DVD command reply by one
                // byte, preventing the VM drain handshake from completing.
                if (MiniClientConnection.detailedBufferStats && !dvdSessionPending) {
                    if (playa != null) {
                        retbuf[4] = (byte) (playa.getState() & 0xFF);
                    } else {
                        retbuf[4] = 0;
                    }
                    return 5;
                } else
                    return 4;
            case MEDIACMD_SETMUTE:
                writeInt(1, retbuf, 0);
                if (playa == null)
                    return 4;
                playa.setMute(readInt(0, cmddata) != 0);
                return 4;
            case MEDIACMD_STOP:
                writeInt(1, retbuf, 0);
                if (playa == null)
                    return 4;
                PlaybackDebugEventBridge.recordAsync("server_stop_command", playa);
                playa.stop();
                return 4;
            case MEDIACMD_PAUSE:
                writeInt(1, retbuf, 0);
                if (playa == null)
                    return 4;
                log.debug("Pause was called");
                PlaybackDebugEventBridge.recordAsync("server_pause_command", playa);
                playa.pause();
                return 4;
            case MEDIACMD_PLAY:
                writeInt(1, retbuf, 0);
                if (playa == null)
                    return 4;
                log.debug("Play was called");
                PlaybackDebugEventBridge.recordAsync("server_play_command", playa);
                playa.play();
                return 4;
            case MEDIACMD_FLUSH:
                writeInt(1, retbuf, 0);

                if (playa != null && pushMode)
                {
                    lastServerFlushSequence++;
                    lastServerFlushMonotonicMs = monotonicMs();
                    PlaybackDebugEventBridge.recordAsync("server_flush_command", playa);
                    if (restartPlayerOnNextFlush)
                    {
                        restartPlayerOnNextFlush = false;
                        MiniPlayerPlugin replacedPlayer = playa;
                        playa = null;
                        replacedPlayer.free();
                        controlledReloadAwaitingReplacementStc = true;
                        controlledReloadCorrectionCount++;
                        // The seek that caused this FLUSH may have delivered
                        // its STC to the old decoder before the release. Queue
                        // the same server-owned seek again only after playa is
                        // null so the resulting metadata/data belong to the
                        // replacement decoder.
                        if (!myConn.postVibeSeekEvent(controlledReloadTargetMs))
                        {
                            log.warn("Unable to queue post-release DVD position restore target={}",
                                    controlledReloadTargetMs);
                            controlledReloadTargetMs = -1;
                            controlledReloadRequestedMs = -1;
                            controlledReloadAwaitingReplacementStc = false;
                        }
                        else
                        {
                            log.info("Queued post-release DVD position restore target={}",
                                    controlledReloadTargetMs);
                        }
                        dvdEpochPushedBytes = 0;
                        dvdDrainSignaledEpochBytes = Long.MIN_VALUE;
                        this.setLastServerStartPosition(-1);
                        log.info("Released active DVD player on server FLUSH for controlled same-position reload");
                    }
                    else if (dvdSessionPending && dvdPushedBytes == 0)
                    {
                        // MiniDVDPlayer flushes the decoder after its initial
                        // empty/drain handshake, before it sends any MPEG data.
                        // Re-preparing Media3 here creates competing extractor
                        // readers on the same still-empty push datasource.
                        log.debug("Ignoring pre-data DVD initialization flush");
                    }
                    else
                    {
                        log.debug("Flush - Flush called on pushMode.  Setting last server time to -1");
                        playa.flush();
                        // PushBufferDataSource starts a new byte-position epoch
                        // after FLUSH. Keep the lifetime counter for diagnostics,
                        // but drain decisions must compare the player's rebased
                        // read position with bytes pushed in this epoch only.
                        dvdEpochPushedBytes = 0;
                        // The authored root/submenu cells can have identical
                        // byte lengths. The transient-EOS one-shot is scoped to
                        // one byte epoch, not to a byte-count value; retaining
                        // the old count suppresses EOF for the next equal-sized
                        // menu cell and deadlocks Media3 against the DVD VM's
                        // drain poll.
                        dvdDrainSignaledEpochBytes = Long.MIN_VALUE;
                    }
                    this.setLastServerStartPosition(-1);
                }

                return 4;

            case MEDIACMD_PUSHBUFFER:
                dvdPushCommandCount++;
                int buffSize = readInt(0, cmddata);
                int flags = readInt(4, cmddata);
                lastPushPayloadBytes = buffSize;
                lastPushFlags = flags;
                int bufDataOffset = 8;
                boolean hasDetailedStats = false;
                int statsChannelBWKbps = -1;
                int statsStreamBWKbps = -1;
                int statsTargetBWKbps = -1;
                int statsServerMuxTimeMs = -1;
                long statsClientMediaTimeMs = -1;

                if (MiniClientConnection.detailedBufferStats && buffSize > 0 && len > buffSize + 13)
                {
                    bufDataOffset += 10;
                    hasDetailedStats = true;
                    statsChannelBWKbps = Math.max(0, (int) readShort(8, cmddata));
                    statsStreamBWKbps = Math.max(0, (int) readShort(10, cmddata));
                    statsTargetBWKbps = Math.max(0, (int) readShort(12, cmddata));
                    statsServerMuxTimeMs = readInt(14, cmddata);
                    statsClientMediaTimeMs = playa == null ? -1 : getMediaTimeMillis();

                    if (playa != null)
                    {
                        /*
                         * During push playback, after the server calls for a flush (Most Likely a seek event)
                         * sagetv sends the playback position telling us about the buffer.
                         * We will store this position so that it can be used in players that do not read
                         * the position from the container on push playback
                        */
                        if (lastServerStartTime < 0 && statsServerMuxTimeMs > 0)
                        {
                            log.debug("Flush - Last server time is -1, and serverMuxtime > 0.  ServerMuxTime: {}", Utils.toHHMMSS(statsServerMuxTimeMs, true));
                            this.setLastServerStartPosition(statsServerMuxTimeMs);
                            PlaybackDebugEventBridge.recordAsync("server_anchor_set", playa);
                        }
                        if (VerboseLogging.DETAILED_PUSHBUFFER_LOGGING)
                        {
                            log.debug("PushBuffer: chanBW={} streamBW={} targetBW={} serverMUXTime={} lastServerStartTime={}",
                                    statsChannelBWKbps, statsStreamBWKbps, statsTargetBWKbps,
                                    Utils.toHHMMSS(statsServerMuxTimeMs, true),
                                    Utils.toHHMMSS(this.getLastServerStartPosition(), true));
                        }

                    }
                }

                // SageTV performs its bandwidth estimate after INIT by sending
                // four synthetic 16 KiB PUSHBUFFER packets before OPENURL. Do
                // not confuse those packets with MiniDVDPlayer media. A real
                // DVD payload is MPEG-PS and contains a pack start code; the
                // synthetic probe is the repeating 00,01,02,... byte pattern.
                if (dvdSessionPending && playa == null &&
                        (((flags & 0x100) != 0) ||
                                containsMpegPsPackHeader(cmddata, bufDataOffset, buffSize)))
                    ensureDvdPushPlayer();

                //sometimes pushbuffer is called to just get bandwidth so don't pass that along to the player
                //boolean noMoreData = flags == 0x80 && playa != null;
                if (playa != null)
                {
                    if (buffSize > 0)
                    {
                        try
                        {
                            playa.pushData(cmddata, bufDataOffset, buffSize);
                            if (dvdSessionPending)
                            {
                                dvdPushedBytes += buffSize;
                                dvdEpochPushedBytes += buffSize;
                                dvdPushMediaCount++;
                            }
                        }
                        catch (IOException e)
                        {
                            log.error("Pushbuffer Error", e);
                            client.closeConnection();
                        }
                    }

                    if (flags == 0x80 && dvdSessionPending)
                    {
                        // MiniDVDPlayer uses EOS at boundaries between DVD VM
                        // cells/titles. End the current decoder generation so
                        // Media3 can drain its final audio/video samples, while
                        // leaving the reusable PUSH session open for the next
                        // generation after FLUSH. Older players which do not
                        // advertise this capability retain the safe historical
                        // behavior of keeping the datasource open.
                        dvdTransientEosCount++;
                        if (playa instanceof TransientPushSegmentPlayer)
                        {
                            ((TransientPushSegmentPlayer) playa).signalPushSegmentEnd();
                            dvdDrainSignaledEpochBytes = dvdEpochPushedBytes;
                            log.debug("DVD segment boundary signaled to current PUSH reader generation");
                        }
                        else
                        {
                            log.debug("DVD segment boundary received; player has no transient-EOS capability");
                        }
                    }
                    else if (flags == 0x80)
                    {
                        log.debug("------------------------- setServerEOS Called --------------------------------");
                        playa.setServerEOS();
                    }
                }

                int rv;

                /*
                 * Always indicate we have at least 512K of buffer...there's NO reason to stop buffering additional
                 * data since as playback goes on we keep writing to the filesystem anyways. Yeah, we could recover some bandwidth
                 * but that's not how any online video players work and we shouldn't be any different than that.
                */
                if (playa == null)
                {
                    rv = maxPrebufferSize;
                }
                else
                {
                    //rv = (int)(PushBufferDataSource.PIPE_SIZE - (bufferFilePushedBytes - playa.getLastFileReadPos()));
                    rv = playa.getBufferLeft();
                    // The DVD VM uses flag 0x100 as a decoder-drained poll.
                    // Native MiniClients return -2 once less than 64 KiB is
                    // queued, allowing VM EMPTY/PAUSE states to advance.  A
                    // normal positive free-space reply leaves the server VM
                    // waiting forever before it sends the first menu/title
                    // MPEG bytes.
                    long dvdReadBytes = playa.getLastFileReadPos();
                    dvdLastReadBytes = dvdReadBytes;
                    dvdDecoderBufferedAheadMs = playa.getBufferedPlaybackAheadMillis();
                    long dvdUnreadBytes = Math.max(0,
                            dvdEpochPushedBytes - Math.max(0, dvdReadBytes));
                    if (dvdSessionPending && (flags & 0x100) != 0)
                    {
                        dvdDrainPollCount++;
                        // A post-data MEDIACMD_FLUSH is an explicit decoder
                        // discontinuity, not a request to render the previous
                        // cell to completion. flush() has already discarded
                        // the old PUSH byte epoch and asked the backend to
                        // rebuild its pipeline when replacement bytes arrive.
                        // Media3 can continue reporting the retained still
                        // menu frame as decoded-ahead while that rebuild is
                        // pending. Waiting for that retained frame to age out
                        // deadlocks the DVD VM: it will not send the next cell
                        // until this poll returns -2. The pre-data initialization
                        // flush is ignored above, so an empty epoch after any
                        // pushed DVD bytes unambiguously means the requested
                        // discontinuity is ready.
                        boolean flushedEpochReady = dvdEpochPushedBytes == 0
                                && dvdPushedBytes > 0;
                        // Cumulative byte counters can rebase when the player
                        // flushes between DVD cells.  Near-full free space is
                        // the authoritative indication that the 4 MiB Android
                        // push buffer has drained across that boundary.
                        boolean pushBufferDrained = rv >= ((4 * 1024 * 1024) - (64 * 1024));
                        // Media3 can enter BUFFERING with a final authored
                        // DVD tail of roughly one NTSC GOP that cannot advance
                        // without more bytes. Waiting for the historical
                        // 250 ms cutoff deadlocks both sides (the DVD VM is
                        // waiting for -2 while Media3 is waiting for input).
                        // A bounded 500 ms tail still preserves visible cell
                        // content and lets the VM deliver the replacement cell.
                        boolean decoderDrained = flushedEpochReady
                                || dvdDecoderBufferedAheadMs < 0
                                || dvdDecoderBufferedAheadMs <= 500;
                        boolean inputDrained = flushedEpochReady
                                || dvdUnreadBytes <= (64 * 1024)
                                || pushBufferDrained;
                        // Stock MiniDVDPlayer normally enters EMPTY/PAUSE by
                        // sending 0x100 drain polls; it does not first send the
                        // nominal 0x80 EOS flag. When all pushed bytes have
                        // reached the extractor but Media3 still has a larger
                        // decoded tail, end only this reader generation. That
                        // lets audio/video render to empty so the later poll can
                        // return -2. The epoch byte guard makes this one-shot.
                        if (inputDrained && !decoderDrained
                                && dvdEpochPushedBytes > 0
                                && dvdDrainSignaledEpochBytes != dvdEpochPushedBytes
                                && playa instanceof TransientPushSegmentPlayer)
                        {
                            ((TransientPushSegmentPlayer) playa).signalPushSegmentEnd();
                            dvdDrainSignaledEpochBytes = dvdEpochPushedBytes;
                            dvdTransientEosCount++;
                            log.debug("DVD drain poll ended current PUSH reader generation at {} bytes",
                                    dvdEpochPushedBytes);
                        }
                        if (inputDrained && decoderDrained)
                        {
                            rv = -2;
                            dvdDrainReadyCount++;
                        }
                    }
                    // log.debug("PUSHBUFFER: bufSize: " + buffSize + " availSize=" + rv);
                }
                
                if (VerboseLogging.DETAILED_PUSHBUFFER_LOGGING)
                {
                    if (rv < 0)
                    {
                        log.debug("PUSHBUFFER: We Letting Server know we are done:  rv: {}", rv);
                    }
                }

                writeInt(rv, retbuf, 0);
                lastPushReply = rv;

                if (hasDetailedStats)
                {
                    recordDetailedPushStats(statsChannelBWKbps, statsStreamBWKbps,
                            statsTargetBWKbps, statsServerMuxTimeMs,
                            statsClientMediaTimeMs, rv, buffSize, flags);
                }

                // MiniDVDPlayer only consumes the four-byte buffer-space
                // response.  Appending the ordinary Push-session telemetry
                // shifts the DVD socket stream and corrupts all later replies.
                if (MiniClientConnection.detailedBufferStats &&
                        !(dvdSessionPending && (playa != null || buffSize == 0 || (flags & 0x100) != 0)))
                {
                    if (playa != null)
                    {
                        writeInt((int) getMediaTimeMillis(), retbuf, 4);
                        retbuf[8] = (byte) (playa.getState() & 0xFF);
                    }
                    else
                    {
                        writeInt(0, retbuf, 4);
                        retbuf[8] = 0;
                    }

                    if (playa != null)
                    {
                        retbuf[8] = (byte) (playa.getState() & 0xFF);
                    }

                    return 9;
                }
                else
                {
                    return 4;
                }

            case MEDIACMD_GETVOLUME:
                if (playa == null)
                    writeInt(65535, retbuf, 0);
                else
                    writeInt(Math.round(playa.getVolume() * 65535), retbuf, 0);
                return 4;
            case MEDIACMD_SETVOLUME:
                if (playa == null)
                    writeInt(65535, retbuf, 0);
                else
                    writeInt(Math.round(playa.setVolume(readInt(0, cmddata) / 65535.0f) * 65535), retbuf, 0);
                return 4;
            case MEDIACMD_FRAMESTEP:
                int frameAmount = len >= 4 ? readInt(0, cmddata) : 0;
                boolean frameStepAccepted = playa != null && playa.frameStep(frameAmount);
                PlaybackDebugEventBridge.recordAsync(frameStepAccepted
                        ? "server_frame_step_command" : "server_frame_step_unsupported", playa);
                writeInt(frameStepAccepted ? 1 : 0, retbuf, 0);
                return 4;
            case MEDIACMD_SETVIDEORECT:
                Rectangle srcRect = new Rectangle(readInt(0, cmddata), readInt(4, cmddata),
                        readInt(8, cmddata), readInt(12, cmddata));
                Rectangle destRect = new Rectangle(readInt(16, cmddata), readInt(20, cmddata),
                        readInt(24, cmddata), readInt(28, cmddata));
                if (playa != null)
                    playa.setVideoRectangles(srcRect, destRect, false);
                myConn.getGfxCmd().setVideoBounds(srcRect, destRect);
                writeInt(0, retbuf, 0);
                return 4;
            case MEDIACMD_GETVIDEORECT:
                Dimension vidRect = null;
                if (playa != null) {
                    vidRect = playa.getVideoDimensions();
                    writeShort((short) vidRect.width, retbuf, 0);
                    writeShort((short) vidRect.height, retbuf, 2);
                } else {
                    writeInt(0, retbuf, 0);
                }
                return 4;
            case MEDIACMD_SEEK:

                long seekTime = ((long) readInt(0, cmddata) << 32) | (readInt(4, cmddata) & 0xffffffffL);
                lastServerRequestedSeekMs = seekTime;
                lastServerSeekSequence++;
                lastServerSeekMonotonicMs = monotonicMs();
                lastServerSeekWallMs = System.currentTimeMillis();
                PlaybackDebugEventBridge.recordAsyncDetailed(
                        "server_seek_command", playa, "requestedMs=" + seekTime);

                if (playa != null)
                {
                    log.debug("MEDIACMD_SEEK called: {}", seekTime);
                    playa.seek(seekTime);
                }

                return 0;

            case MEDIACMD_SETRATE:
                float requestedRate = len >= 4
                        ? Float.intBitsToFloat(readInt(0, cmddata)) : 1.0f;
                float acceptedRate = playa == null
                        ? 1.0f : playa.setPlaybackRate(requestedRate);
                PlaybackDebugEventBridge.recordAsync(
                        acceptedRate == requestedRate
                                ? "server_playback_rate_command"
                                : "server_playback_rate_unsupported", playa);
                writeInt(Float.floatToIntBits(acceptedRate), retbuf, 0);
                return 4;

            case MEDIACMD_DVD_NEWCELL:
                dvdNewCellCount++;
                return executeDvdPayload(cmddata, len, retbuf, MEDIACMD_DVD_NEWCELL);
            case MEDIACMD_DVD_CLUT:
                dvdClutCount++;
                return executeDvdPayload(cmddata, len, retbuf, MEDIACMD_DVD_CLUT);
            case MEDIACMD_DVD_SPUCTRL:
                dvdSpuControlCount++;
                return executeDvdPayload(cmddata, len, retbuf, MEDIACMD_DVD_SPUCTRL);
            case MEDIACMD_DVD_STC:
                dvdStcCount++;
                ensureDvdPushPlayer();
                if (playa != null && len >= 4)
                {
                    int stc = readInt(0, cmddata);
                    playa.dvdSetStc(stc);
                    correctControlledReloadLanding(stc);
                }
                writeInt(playa == null ? -1 : 0, retbuf, 0);
                return 4;
            case MEDIACMD_DVD_STREAMS:
                dvdStreamCount++;
                ensureDvdPushPlayer();
                int streamResult = 0;
                if (playa != null)
                {
                    try
                    {
                        int streamType = readInt(0, cmddata);
                        int streamPos = readInt(4, cmddata);
                        dvdLastStreamType = streamType;
                        dvdLastStreamPosition = streamPos;

                        log.debug("JVL - Stream Type: {}  Stream Pos: {}", streamType, streamPos);
                        if (streamType == STREAM_TYPE_AUDIO)
                        {
                            dvdLastAudioStreamPosition = streamPos;
                            log.debug("JVL - Changing Audio Track");
                            playa.setAudioTrack(streamPos);
                        }
                        else if (streamType == STREAM_TYPE_SUBTITLE)
                        {
                            dvdLastSubtitleStreamPosition = streamPos;
                            log.debug("JVL - Changing TEXT Track");
                            // DVD subpictures are private-stream bitmap data,
                            // not Media3/Exo text tracks.  Let the DVD-aware
                            // backend select the authored SPU substream.
                            playa.dvdSetStream(streamType, streamPos);
                        }
                        else
                        {
                            log.error("JVL - UNKNOWN Stream Type");
                        }
                    }
                    catch (Throwable t)
                    {
                        log.error("Failed to set Stream Type", t);
                        streamResult = -1;
                    }
                }
                else
                {
                    streamResult = -1;
                }
                // MiniPlayer.DVDStream always waits for this four-byte reply.
                // Returning no bytes on an unavailable player or track-selection
                // exception deadlocks the server until its 30-second socket timeout.
                writeInt(streamResult, retbuf, 0);
                return 4;
            case MEDIACMD_DVD_FORMAT:
                dvdFormatCount++;
                ensureDvdPushPlayer();
                if (playa != null && len >= 4)
                    playa.dvdSetFormat(readInt(0, cmddata));
                writeInt(playa == null ? -1 : 0, retbuf, 0);
                return 4;

            default:
                log.error("MEDIACMD Unhandled Media Command: {}", cmd);
                return -1;
        }
    }

    private void ensureDvdPushPlayer()
    {
        if (!dvdSessionPending || playa != null)
            return;

        try
        {
            playa = myConn.newPlayerPlugin("push:dvd");
            playa.setPushMode(true);
            playa.load((byte) 0, (byte) 0, "MPEG2-PS", "push:dvd", null, false, 0);
            pushMode = true;
            maxPrebufferSize = DESIRED_VIDEO_PREBUFFER_SIZE;
            applySageTvClosedCaptionState();
            log.info("Created lazy DVD push player for server-side MiniDVDPlayer session");
        }
        catch (Throwable t)
        {
            log.error("Unable to create DVD push player", t);
            if (playa != null)
                playa.free();
            playa = null;
        }
    }

    /**
     * A replacement decoder can receive the DVD VM's zero bootstrap STC even
     * though the seek that caused the replacement targeted the middle of a
     * title. Reissue the target once after the replacement exists, then use
     * the same bounded VOBU-anchor correction as the MCP seek gate. This keeps
     * an on-the-fly decoder change at the user's visible position without
     * pretending a client-local seek can reposition the server DVD reader.
     */
    private void correctControlledReloadLanding(int stc)
    {
        long targetMs = controlledReloadTargetMs;
        if (targetMs < 0 || myConn == null || !controlledReloadAwaitingReplacementStc)
            return;

        long anchorMs = ((long) stc & 0xFFFFFFFFL) * 1_000L / 45_000L;
        long errorMs = anchorMs - targetMs;
        if (Math.abs(errorMs) <= 2_500L)
        {
            log.info("Controlled DVD reload retained position target={} anchor={} corrections={}",
                    targetMs, anchorMs, controlledReloadCorrectionCount);
            controlledReloadTargetMs = -1;
            controlledReloadRequestedMs = -1;
            controlledReloadAwaitingReplacementStc = false;
            return;
        }
        if (controlledReloadCorrectionCount >= 2)
        {
            log.warn("Controlled DVD reload position correction exhausted target={} anchor={}",
                    targetMs, anchorMs);
            controlledReloadTargetMs = -1;
            controlledReloadRequestedMs = -1;
            controlledReloadAwaitingReplacementStc = false;
            return;
        }

        long requestedMs;
        if (targetMs > 30_000L && anchorMs < targetMs / 2L)
        {
            // The first STC after constructing a fresh decoder is commonly
            // the DVD bootstrap value zero. Reissue the original target now
            // that the replacement player is present.
            requestedMs = targetMs;
        }
        else
        {
            requestedMs = Math.max(0L, controlledReloadRequestedMs - errorMs);
        }
        controlledReloadRequestedMs = requestedMs;
        controlledReloadCorrectionCount++;
        if (!myConn.postVibeSeekEvent(requestedMs))
        {
            log.warn("Unable to queue controlled DVD reload correction target={}", requestedMs);
            controlledReloadTargetMs = -1;
            controlledReloadRequestedMs = -1;
            controlledReloadAwaitingReplacementStc = false;
        }
        else
        {
            log.info("Queued controlled DVD reload correction target={} anchor={} request={} attempt={}",
                    targetMs, anchorMs, requestedMs, controlledReloadCorrectionCount);
        }
    }

    static boolean containsMpegPsPackHeader(byte[] data, int offset, int length)
    {
        if (data == null || offset < 0 || length < 4 || offset > data.length)
            return false;
        int end = Math.min(data.length, offset + length) - 3;
        for (int i = offset; i < end; i++)
        {
            if (data[i] == 0 && data[i + 1] == 0 &&
                    data[i + 2] == 1 && (data[i + 3] & 0xFF) == 0xBA)
                return true;
        }
        return false;
    }

    private int executeDvdPayload(byte[] cmddata, int len, byte[] retbuf, int command)
    {
        ensureDvdPushPlayer();
        int result = -1;
        if (playa != null && len >= 4)
        {
            int payloadSize = readInt(0, cmddata);
            if (payloadSize >= 0 && payloadSize <= len - 4)
            {
                byte[] payload = new byte[payloadSize];
                System.arraycopy(cmddata, 4, payload, 0, payloadSize);
                if (command == MEDIACMD_DVD_NEWCELL)
                    playa.dvdNewCell(payloadSize, payload);
                else if (command == MEDIACMD_DVD_CLUT)
                    playa.dvdSetClut(payloadSize, payload);
                else
                    playa.dvdSetSpuControl(payloadSize, payload);
                result = 0;
            }
        }
        writeInt(result, retbuf, 0);
        return 4;
    }

    private long getMediaTimeMillis()
    {
        if (playa != null)
        {
            return playa.getMediaTimeMillis(this.getLastServerStartPosition());
        }

        return lastServerStartTime;
    }

    private void setLastServerStartPosition(long position)
    {
        this.lastServerStartTime = position;
        if (position >= 0)
        {
            lastServerAnchorSequence++;
            lastServerAnchorMonotonicMs = monotonicMs();
        }
        else
        {
            lastServerAnchorMonotonicMs = -1;
        }
    }

    private static long monotonicMs()
    {
        return System.nanoTime() / 1000000L;
    }

    private void notifyPlaybackLoadStarted()
    {
        if (myConn != null && myConn.getUiRenderer() != null)
            myConn.getUiRenderer().onPlaybackLoadStarted();
    }

    public long getLastServerStartPosition()
    {
        if(this.lastServerStartTime < 0)
        {
            return 0;
        }

        return lastServerStartTime;
    }

    public long getLastServerRequestedSeekMs()
    {
        return lastServerRequestedSeekMs;
    }

    public long getLastServerSeekSequence()
    {
        return lastServerSeekSequence;
    }

    public long getLastServerSeekMonotonicMs()
    {
        return lastServerSeekMonotonicMs;
    }

    public long getLastServerSeekWallMs()
    {
        return lastServerSeekWallMs;
    }

    public long getLastServerFlushSequence()
    {
        return lastServerFlushSequence;
    }

    public long getLastServerFlushMonotonicMs()
    {
        return lastServerFlushMonotonicMs;
    }

    public long getLastServerAnchorSequence()
    {
        return lastServerAnchorSequence;
    }

    public long getLastServerAnchorMonotonicMs()
    {
        return lastServerAnchorMonotonicMs;
    }

    private void resetDetailedPushStats()
    {
        serverChannelBandwidthKbps = -1;
        serverStreamBandwidthKbps = -1;
        serverTargetBandwidthKbps = -1;
        serverMuxTimeMs = -1;
        clientBufferTimeMs = -1;
        clientBufferAvailableBytes = -1;
        lastPushPayloadBytes = 0;
        lastPushFlags = 0;
        detailedPushSampleSequence = 0;
        detailedPushSampleMonotonicMs = -1;
        detailedPushSampleWallMs = -1;
    }

    private void resetDvdProtocolStats()
    {
        dvdSessionPending = false;
        dvdPushedBytes = 0;
        dvdEpochPushedBytes = 0;
        dvdLastReadBytes = -1;
        dvdDrainPollCount = 0;
        dvdDrainReadyCount = 0;
        dvdDecoderBufferedAheadMs = -1;
        dvdInitCount = 0;
        dvdPushCommandCount = 0;
        dvdPushMediaCount = 0;
        dvdNewCellCount = 0;
        dvdClutCount = 0;
        dvdSpuControlCount = 0;
        dvdStcCount = 0;
        dvdStreamCount = 0;
        dvdLastStreamType = -1;
        dvdLastStreamPosition = -1;
        dvdLastAudioStreamPosition = -1;
        dvdLastSubtitleStreamPosition = -1;
        dvdFormatCount = 0;
        dvdTransientEosCount = 0;
        dvdMimRuntimeFallback = false;
        dvdDrainSignaledEpochBytes = Long.MIN_VALUE;
    }

    private void recordDetailedPushStats(int channelBandwidthKbps, int streamBandwidthKbps,
            int targetBandwidthKbps, long muxTimeMs, long clientMediaTimeMs,
            int bufferAvailableBytes, int payloadBytes, int flags)
    {
        serverChannelBandwidthKbps = channelBandwidthKbps;
        serverStreamBandwidthKbps = streamBandwidthKbps;
        serverTargetBandwidthKbps = targetBandwidthKbps;
        serverMuxTimeMs = muxTimeMs;
        clientBufferTimeMs = clientMediaTimeMs < 0 ? -1 : muxTimeMs - clientMediaTimeMs;
        clientBufferAvailableBytes = bufferAvailableBytes;
        lastPushPayloadBytes = payloadBytes;
        lastPushFlags = flags;
        detailedPushSampleMonotonicMs = monotonicMs();
        detailedPushSampleWallMs = System.currentTimeMillis();
        detailedPushSampleSequence++;
    }

    public int getServerChannelBandwidthKbps() { return serverChannelBandwidthKbps; }
    public int getServerStreamBandwidthKbps() { return serverStreamBandwidthKbps; }
    public int getServerTargetBandwidthKbps() { return serverTargetBandwidthKbps; }
    public long getServerMuxTimeMs() { return serverMuxTimeMs; }
    public long getClientBufferTimeMs() { return clientBufferTimeMs; }
    public int getClientBufferAvailableBytes() { return clientBufferAvailableBytes; }
    public int getLastPushPayloadBytes() { return lastPushPayloadBytes; }
    public int getLastPushFlags() { return lastPushFlags; }
    public boolean isDvdSessionPending() { return dvdSessionPending; }
    public long getDvdPushedBytes() { return dvdPushedBytes; }
    public long getDvdEpochPushedBytes() { return dvdEpochPushedBytes; }
    public long getDvdLastReadBytes() { return dvdLastReadBytes; }
    public long getDvdDrainPollCount() { return dvdDrainPollCount; }
    public long getDvdDrainReadyCount() { return dvdDrainReadyCount; }
    public long getDvdDecoderBufferedAheadMs() { return dvdDecoderBufferedAheadMs; }
    public long getDvdInitCount() { return dvdInitCount; }
    public long getDvdPushCommandCount() { return dvdPushCommandCount; }
    public long getDvdPushMediaCount() { return dvdPushMediaCount; }
    public long getDvdNewCellCount() { return dvdNewCellCount; }
    public long getDvdClutCount() { return dvdClutCount; }
    public long getDvdSpuControlCount() { return dvdSpuControlCount; }
    public long getDvdStcCount() { return dvdStcCount; }
    public long getDvdStreamCount() { return dvdStreamCount; }
    public int getDvdLastStreamType() { return dvdLastStreamType; }
    public int getDvdLastStreamPosition() { return dvdLastStreamPosition; }
    public int getDvdLastAudioStreamPosition() { return dvdLastAudioStreamPosition; }
    public int getDvdLastSubtitleStreamPosition() { return dvdLastSubtitleStreamPosition; }
    public long getDvdFormatCount() { return dvdFormatCount; }
    public long getDvdTransientEosCount() { return dvdTransientEosCount; }
    public boolean isDvdMimRuntimeFallback() { return dvdMimRuntimeFallback; }
    public int getLastPushReply() { return lastPushReply; }
    public long getDetailedPushSampleSequence() { return detailedPushSampleSequence; }
    public long getDetailedPushSampleMonotonicMs() { return detailedPushSampleMonotonicMs; }
    public long getDetailedPushSampleWallMs() { return detailedPushSampleWallMs; }
}
