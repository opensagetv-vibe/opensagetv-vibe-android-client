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

import opensagetv.vibe.miniclient.uibridge.Dimension;
import opensagetv.vibe.miniclient.uibridge.HandlesNativeGFXCommand;
import opensagetv.vibe.miniclient.uibridge.Rectangle;
import opensagetv.vibe.miniclient.uibridge.UIRenderer;
import opensagetv.vibe.miniclient.util.VerboseLogging;

public class GFXCMD2 {
    public static final boolean ENABLE_MOUSE_MOTION_EVENTS = true;
    public static final String[] CMD_NAMES = {"", "INIT", "DEINIT", "", "", "", "", "", "", "", "", "", "", "", "", "", "DRAWRECT",
            "FILLRECT", "CLEARRECT", "DRAWOVAL", "FILLOVAL", "DRAWROUNDRECT", "FILLROUNDRECT", "DRAWTEXT", "DRAWTEXTURED",
            "DRAWLINE", "LOADIMAGE", "UNLOADIMAGE", "LOADFONT", "UNLOADFONT", "FLIPBUFFER", "STARTFRAME", "LOADIMAGELINE",
            "PREPIMAGE", "LOADIMAGECOMPRESSED", "XFMIMAGE", "LOADFONTSTREAM", "CREATESURFACE", "SETTARGETSURFACE", "",
            "DRAWTEXTUREDDIFFUSED", "PUSHTRANSFORM", "POPTRANSFORM", "TEXTUREBATCH", "LOADCACHEDIMAGE", "LOADIMAGETARGETED",
            "PREPIMAGETARGETED"};
    public static final int GFXCMD_INIT = 1;
    public static final int GFXCMD_DEINIT = 2;
    public static final int GFXCMD_DRAWRECT = 16;
    public static final int GFXCMD_FILLRECT = 17;
    // x, y, width, height, thickness, argbTL, argbTR, argbBR, argbBL
    public static final int GFXCMD_CLEARRECT = 18;
    // x, y, width, height, argbTL, argbTR, argbBR, argbBL
    public static final int GFXCMD_DRAWOVAL = 19;
    // x, y, width, height, argbTL, argbTR, argbBR, argbBL
    public static final int GFXCMD_FILLOVAL = 20;
    // x, y, width, height, thickness, argbTL, argbTR, argbBR, argbBL,
    // clipX, clipY, clipW, clipH
    public static final int GFXCMD_DRAWROUNDRECT = 21;
    // x, y, width, height, argbTL, argbTR, argbBR, argbBL,
    // clipX, clipY, clipW, clipH
    public static final int GFXCMD_FILLROUNDRECT = 22;
    // x, y, width, height, thickness, arcRadius, argbTL, argbTR, argbBR,
    // argbBL,
    // clipX, clipY, clipW, clipH
    public static final int GFXCMD_DRAWTEXT = 23;
    // x, y, width, height, arcRadius, argbTL, argbTR, argbBR, argbBL,
    // clipX, clipY, clipW, clipH
    public static final int GFXCMD_DRAWTEXTURED = 24;
    // x, y, len, text, handle, argb, clipX, clipY, clipW, clipH
    public static final int GFXCMD_DRAWLINE = 25;
    // x, y, width, height, handle, srcx, srcy, srcwidth, srcheight, blend
    public static final int GFXCMD_LOADIMAGE = 26;
    // x1, y1, x2, y2, argb1, argb2
    public static final int GFXCMD_UNLOADIMAGE = 27;
    // width, height
    public static final int GFXCMD_LOADFONT = 28;
    // handle
    public static final int GFXCMD_UNLOADFONT = 29;
    // namelen, name, style, size
    public static final int GFXCMD_FLIPBUFFER = 30;
    // handle
    public static final int GFXCMD_STARTFRAME = 31;
    public static final int GFXCMD_LOADIMAGELINE = 32;
    public static final int GFXCMD_PREPIMAGE = 33;
    // handle, line, len, data
    public static final int GFXCMD_LOADIMAGECOMPRESSED = 34;
    // width, height
    public static final int GFXCMD_XFMIMAGE = 35;
    // handle, len, data
    public static final int GFXCMD_LOADFONTSTREAM = 36;
    // srcHandle, destHandle, destWidth, destHeight, maskCornerArc
    public static final int GFXCMD_CREATESURFACE = 37;
    // namelen, name, len, data
    public static final int GFXCMD_SETTARGETSURFACE = 38;
    // width, height
    public static final int GFXCMD_DRAWTEXTUREDDIFFUSE = 40;
    // handle
    public static final int GFXCMD_PUSHTRANSFORM = 41;
    // x, y, width, height, handle, srcx, srcy, srcwidth, srcheight, blend,
    // diffhandle, diffsrcx, diffsrcy, diffsrcwidth, diffsrcheight
    public static final int GFXCMD_POPTRANSFORM = 42;
    // v'= matrix * v
    // sent by row, then col, 12 values (skip the 4th column since its fixed)
    public static final int GFXCMD_TEXTUREBATCH = 43;
    public static final int GFXCMD_LOADCACHEDIMAGE = 44;
    // count, size
    public static final int GFXCMD_LOADIMAGETARGETED = 45;
    // handle, width, height, cacheResourceID
    public static final int GFXCMD_PREPIMAGETARGETED = 46;
    // handle, width, height, [format]
    public static final int GFXCMD_SETVIDEOPROP = 130;
    // handle, width, height, [cache resource id] (but this will never actually
    // load from the offline cache, this is only for knowing where to cache it)
    private static final Logger log = LoggerFactory.getLogger(GFXCMD2.class);
    private final MiniClient client;
    // mode, sx, sy, swidth, sheight, ox, oy, owidth, oheight, alpha, activewin
    private UIRenderer<?> windowManager;
    private MiniClientConnection myConn;
    private final GfxHandleAllocator handles = new GfxHandleAllocator();
    private boolean cursorHidden;
    private boolean deleageGFXCommands = false;
    private final GfxLifecycleFrameCommands lifecycleFrameCommands;
    private final GfxDrawingCommands drawingCommands;
    private final GfxSurfaceVideoCommands surfaceVideoCommands;
    private final GfxFontCommands fontCommands;
    private final GfxTransformBatchCommands transformBatchCommands;
    private final GfxImageCacheCommands imageCacheCommands;
    public GFXCMD2(MiniClient client) {
        this.client = client;
        this.windowManager = client.getUIRenderer();
        this.myConn = client.getCurrentConnection();
        this.lifecycleFrameCommands = new GfxLifecycleFrameCommands(windowManager);
        this.drawingCommands = new GfxDrawingCommands(client, windowManager);
        this.surfaceVideoCommands = new GfxSurfaceVideoCommands(client, windowManager, handles);
        this.fontCommands = new GfxFontCommands(client, myConn);
        this.transformBatchCommands = new GfxTransformBatchCommands();
        this.imageCacheCommands = new GfxImageCacheCommands(client, myConn, windowManager, handles);
        deleageGFXCommands=this.windowManager instanceof HandlesNativeGFXCommand;
    }

    public static int readInt(int pos, byte[] cmddata) {
        pos += 4; // for the 4 bytes for the header
        return ((cmddata[pos + 0] & 0xFF) << 24) | ((cmddata[pos + 1] & 0xFF) << 16) | ((cmddata[pos + 2] & 0xFF) << 8)
                | (cmddata[pos + 3] & 0xFF);
    }

    public static float readFloat(int pos, byte[] cmddata) {
        pos += 4; // for the 4 bytes for the header
        return Float.intBitsToFloat(((cmddata[pos + 0] & 0xFF) << 24) | ((cmddata[pos + 1] & 0xFF) << 16)
                | ((cmddata[pos + 2] & 0xFF) << 8) | (cmddata[pos + 3] & 0xFF));
    }

    public static int readIntSwapped(int pos, byte[] cmddata) {
        pos += 4; // for the 4 bytes for the header
        return ((cmddata[pos + 3] & 0xFF) << 24) | ((cmddata[pos + 2] & 0xFF) << 16) | ((cmddata[pos + 1] & 0xFF) << 8)
                | (cmddata[pos + 0] & 0xFF);
    }

    public static short readShort(int pos, byte[] cmddata) {
        pos += 4; // for the 4 bytes for the header
        return (short) (((cmddata[pos + 0] & 0xFF) << 8) | (cmddata[pos + 1] & 0xFF));
    }

    public static short readShortSwapped(int pos, byte[] cmddata) {
        pos += 4; // for the 4 bytes for the header
        return (short) (((cmddata[pos + 1] & 0xFF) << 8) | (cmddata[pos + 0] & 0xFF));
    }

    public void close() {
        windowManager.close();
    }

    public void refresh() {
        windowManager.refresh();
    }

    public int ExecuteGFXCommand(int cmd, int len, byte[] cmddata, int[] hasret) {
        len -= 4; // for the 4 byte header
        hasret[0] = 0; // Nothing to return by default

        if (deleageGFXCommands) {
            return ((HandlesNativeGFXCommand)windowManager).ExecuteGFXCommand(cmd, len, cmddata, hasret);
        }

        if (VerboseLogging.DETAILED_GFX) {
            if (cmd == GFXCMD_SETVIDEOPROP) {
                log.debug("GFXCMD=GFXCMD_SETVIDEOPROP");
            } else {
                if (cmd == GFXCMD_CREATESURFACE
                        || cmd == GFXCMD_SETTARGETSURFACE
                        || cmd == GFXCMD_PREPIMAGE) {
                    // we will log these later
                } else {
                    if (VerboseLogging.DETAILED_GFX_TEXTURES || cmd != GFXCMD_DRAWTEXTURED) {
                        log.debug("GFXCMD={}", ((cmd >= 0 && cmd < CMD_NAMES.length) ? CMD_NAMES[cmd] : ("UnknownCmd " + cmd)));
                    }
                }
            }
        }

        if (!windowManager.hasGraphicsCanvas()) {
            switch (cmd) {
                case GFXCMD_INIT:
                case GFXCMD_DEINIT:
                case GFXCMD_STARTFRAME:
                case GFXCMD_FLIPBUFFER:
                    windowManager.hideCursor();
                    break;
                case GFXCMD_DRAWRECT:
                case GFXCMD_FILLRECT:
                case GFXCMD_CLEARRECT:
                case GFXCMD_DRAWOVAL:
                case GFXCMD_FILLOVAL:
                case GFXCMD_DRAWROUNDRECT:
                case GFXCMD_FILLROUNDRECT:
                case GFXCMD_DRAWTEXT:
                case GFXCMD_DRAWTEXTURED:
                case GFXCMD_DRAWLINE:
                case GFXCMD_LOADIMAGE:
                case GFXCMD_LOADIMAGETARGETED:
                case GFXCMD_UNLOADIMAGE:
                case GFXCMD_LOADFONT:
                case GFXCMD_UNLOADFONT:
                case GFXCMD_SETTARGETSURFACE:
                case GFXCMD_CREATESURFACE:
                    break;
                case GFXCMD_PREPIMAGE:
                case GFXCMD_LOADIMAGELINE:
                case GFXCMD_LOADIMAGECOMPRESSED:
                case GFXCMD_XFMIMAGE:
                case GFXCMD_LOADCACHEDIMAGE:
                case GFXCMD_PREPIMAGETARGETED:
                    if (!cursorHidden)
                        windowManager.showBusyCursor();
                    break;
            }
        }

        if (GfxCommandFamily.of(cmd) == GfxCommandFamily.Family.LIFECYCLE_FRAME)
            return lifecycleFrameCommands.execute(cmd, hasret);
        if (GfxCommandFamily.of(cmd) == GfxCommandFamily.Family.SURFACE_VIDEO)
            return surfaceVideoCommands.execute(cmd, len, cmddata, hasret);
        if (GfxCommandFamily.of(cmd) == GfxCommandFamily.Family.FONT)
            return fontCommands.execute(cmd, len, cmddata);
        if (GfxCommandFamily.of(cmd) == GfxCommandFamily.Family.TRANSFORM_BATCH)
            return transformBatchCommands.execute(cmd, len, cmddata);
        if (GfxCommandFamily.of(cmd) == GfxCommandFamily.Family.IMAGE_CACHE)
            return imageCacheCommands.execute(cmd, len, cmddata, hasret);

        switch (cmd) {
            case GFXCMD_DRAWRECT:
            case GFXCMD_FILLRECT:
            case GFXCMD_CLEARRECT:
            case GFXCMD_DRAWOVAL:
            case GFXCMD_FILLOVAL:
            case GFXCMD_DRAWROUNDRECT:
            case GFXCMD_FILLROUNDRECT:
            case GFXCMD_DRAWTEXT:
            case GFXCMD_DRAWTEXTURED:
            case GFXCMD_DRAWLINE:
            case GFXCMD_DRAWTEXTUREDDIFFUSE:
                return drawingCommands.execute(cmd, len, cmddata);
            default:
                log.error("GFXCMD Unhandled Command: {}", cmd);
                return -1;
        }
    }

    public boolean createVideo(int width, int height, int format) {
        return windowManager.createVideo(width, height, format);
    }

    public boolean updateVideo(int frametype, java.nio.ByteBuffer buf) {
        return windowManager.updateVideo(frametype, buf);
    }

    public String getVideoOutParams() {
        return null;
    }

    public Dimension getScreenSize() {
        return this.windowManager.getScreenSize();
    }

    public UIRenderer<?> getWindow() {
        return windowManager;
    }

    public void setVideoBounds(Rectangle o, Rectangle o1) {
        windowManager.setVideoBounds(o, o1);
    }

    public long getImageAllocationRecoveryAttempts() {
        return imageCacheCommands.getAllocationRecoveryAttempts();
    }

    public long getImageAllocationRecoveryEvictions() {
        return imageCacheCommands.getAllocationRecoveryEvictions();
    }

    public long getImageAllocationRecoverySuccesses() {
        return imageCacheCommands.getAllocationRecoverySuccesses();
    }

    public long getImageAllocationRecoveryFailures() {
        return imageCacheCommands.getAllocationRecoveryFailures();
    }
}
