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

import java.io.IOException;

import opensagetv.vibe.miniclient.media.SubtitleTrack;
import opensagetv.vibe.miniclient.uibridge.Dimension;
import opensagetv.vibe.miniclient.uibridge.Rectangle;

public interface MiniPlayerPlugin extends Runnable
{
     int DISABLE_TRACK = 8192;
     int PREFERRED_TRACK = -2;


    /**
     * Indicates the MediaPlayer is in an uninitialized state
     */
    int NO_STATE = 0;
    /**
     * Indicates the MediaPlayer has loaded a file and is ready for playback
     */
    int LOADED_STATE = 1;
    /**
     * The MediaPlayer is playing
     */
    int PLAY_STATE = 2;
    /**
     * The MediaPlayer is paused
     */
    int PAUSE_STATE = 3;
    /**
     * The MediaPlayer is stopped
     */
    int STOPPED_STATE = 4;
    /**
     * The MediaPlayer has encountered an end of stream
     */
    int EOS_STATE = 5;

    void free();

    /**
     * Sets the push to true to use PUSH or false to use PULL
     *
     * @param b
     */
    void setPushMode(boolean b);

    /**
     * Identifies whether the active/growing-file hints came from the Vibe URL
     * metadata extension. Stock SageTV servers do not provide those hints, so
     * clients must verify the historical extension-based active-file guess.
     */
    default void setServerMediaMetadataExplicit(boolean explicit) { }

    /**
     * Should check pushMode to determine if PUSH or PULL is being used
     */
    void load(byte majorTypeHint, byte minorTypeHint, String encodingHint, String urlString, String hostname, boolean timeshifted, long bufferSize);

    /**
     * Return the current play time on the MediaPlayer.  lastServerTime will be passed when PUSH is used to indicate the
     * last media time after a PUSH happened.  In the case where the player's time reset's to 0, then this can be used
     * by the player to append the lastServerTime+playerTime to get the "real" playback time.
     *
     * @param lastServerTime
     * @return
     */
    long getMediaTimeMillis(long lastServerTime);

    /**
     * Appears to be used only during detailed buffered stats
     * @return
     */
    int getState();

    void setMute(boolean b);

    void stop();

    void pause();

    void play();

    /**
     * Requests a signed playback rate. Implementations return the rate they
     * accepted; the compatibility default remains normal 1x playback.
     */
    default float setPlaybackRate(float rate)
    {
        return 1.0f;
    }

    /** Current accepted playback rate, including while paused. */
    default float getPlaybackRate()
    {
        return 1.0f;
    }

    /** True after this playback generation has presented its first video frame. */
    default boolean hasRenderedFirstVideoFrame() { return false; }

    /** Whether this active backend can apply a presentation-only subtitle offset. */
    default boolean supportsSubtitleOffset() { return false; }

    /**
     * Apply a bounded subtitle/caption presentation offset without changing
     * SageTV's reported media clock. Positive values present later.
     */
    default boolean setSubtitleOffsetMillis(int offsetMs) { return false; }

    default int getSubtitleOffsetMillis() { return 0; }

    /** True only for locally rendered text captions/subtitles. */
    default boolean supportsTextSubtitlePresentation() { return false; }

    /**
     * Apply bounded text presentation without changing DVD bitmap subpictures.
     * The safe-area value is percent of the video height above the bottom edge;
     * text scale is a percentage of the backend's normal caption size.
     */
    default boolean setTextSubtitlePresentation(int safeAreaPercent,
            int textScalePercent, String style) { return false; }

    default int getTextSubtitleSafeAreaPercent() { return 18; }

    default int getTextSubtitleScalePercent() { return 100; }

    default String getTextSubtitleStyle() { return "system"; }

    /** Whether this active output path can apply an audio offset reliably. */
    default boolean supportsAudioOffset() { return false; }

    /** Positive values present audio later; unsupported outputs return false. */
    default boolean setAudioOffsetMillis(int offsetMs) { return false; }

    default int getAudioOffsetMillis() { return 0; }

    /** Resolved active content frame rate, or a non-positive value if unknown. */
    default float getContentFrameRateHz() { return -1f; }

    void seek(long timeMS);

    /**
     * Requests a signed number of video frames while paused. Implementations
     * return false when the current backend/transport cannot step safely.
     */
    default boolean frameStep(int amount)
    {
        return false;
    }

    /**
     * Servers is telling us that there is no more data.  We can still play any buffered data
     * but no more data is coming.  This is only used during PUSH mode.
     */
    void setServerEOS();

    long getLastFileReadPos();

    int getVolume();

    int setVolume(float v);

    /**
     * Set the audio track to be played back.
     * @param streamPos The audio track position (zero based) in the file
     */
    void setAudioTrack(int streamPos);

    /** Stable identifiers accepted by {@link #setAudioTrack(int)} for the active media. */
    default int[] getAudioTrackIds() { return new int[0]; }

    /** Human-readable labels corresponding one-for-one with {@link #getAudioTrackIds()}. */
    default String[] getAudioTrackLabels() { return new String[0]; }

    /** Active identifier from {@link #getAudioTrackIds()}, or -1 when unknown. */
    default int getSelectedAudioTrack() { return -1; }

    /** Read-only description of the resolved audio output path. */
    default String getAudioOutputSummary() { return "unknown"; }

    /** True only when this backend can safely switch passthrough without changing transport. */
    default boolean supportsAudioPassthroughControl() { return false; }

    /** Apply a session-only passthrough policy. Unsupported outputs return false. */
    default boolean setAudioPassthroughEnabled(boolean enabled) { return false; }

    /**
     * Set the subtitle track to be played back.
     * @param streamPos The audio track position (zero based) in the file
     */
    void setSubtitleTrack(int streamPos);

    /**
     * Selects the configured preferred subtitle/caption service. SageTV still
     * owns whether captions are enabled; the default preserves legacy track 0.
     */
    default void setPreferredSubtitleTrack()
    {
        setSubtitleTrack(0);
    }

    /**
     * The subtitle track that is currently selected
     * @return the select track index or DISABLE_TRACK if none are selected
     * or if subtitles are not supported
     */
    int getSelectedSubtitleTrack();

    /**
     * Gets the count of Subtitle/ClosedCaption tracks the player identified
     */
    int getSubtitleTrackCount();

    /**
     * Get the list of tracks the player has identified.  Returns an empty array if no tracks were found, or if
     * the player does not support rendering subtitles.
     *
     * @return Array of support subtitle tracks
     */
    SubtitleTrack[] getSubtitleTracks();

    void setVideoRectangles(Rectangle srcRect, Rectangle destRect, boolean hideCursor);

    Dimension getVideoDimensions();

    void pushData(byte[] cmddata, int bufDataOffset, int buffSize) throws IOException;

    void flush();

    /**
     * Return the # of bytes left in the media buffer
     *
     * @return
     */
    int getBufferLeft();

    /** Queued decoder/render duration beyond the current playback position, or -1 if unknown. */
    default long getBufferedPlaybackAheadMillis() { return -1L; }

    /**
     * Set the Advanced Aspect Ratio Mode for the player
     *
     * @param aspectMode
     */
    void setVideoAdvancedAspect(String aspectMode);

    /**
     * DVD navigation metadata sent by SageTV's server-side MiniDVDPlayer.
     * Backends that only need the pushed MPEG program stream may leave these
     * as no-ops; menu-capable renderers can use them for SPU/highlight state.
     */
    default void dvdNewCell(int payloadSize, byte[] payload) { }

    default void dvdSetClut(int payloadSize, byte[] payload) { }

    default void dvdSetSpuControl(int payloadSize, byte[] payload) { }

    /**
     * Returns true only while the server has supplied an active authored DVD
     * button highlight.  Android uses this to route the directional pad to
     * DVD navigation without changing the user's normal playback key map for
     * DVD titles, previews, or other video.
     */
    default boolean isDvdMenuNavigationActive() { return false; }

    default void dvdSetStc(int stc) { }

    default void dvdSetFormat(int format) { }

    default void dvdSetStream(int streamType, int streamPosition) { }
}
