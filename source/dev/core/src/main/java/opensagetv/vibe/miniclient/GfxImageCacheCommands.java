/*
 * Copyright 2015 The SageTV Authors. All Rights Reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package opensagetv.vibe.miniclient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import opensagetv.vibe.miniclient.uibridge.UIRenderer;
import opensagetv.vibe.miniclient.util.VerboseLogging;

/** Owns image/cache protocol state and executes the image command family. */
final class GfxImageCacheCommands {
    private static final Logger log = LoggerFactory.getLogger(GfxImageCacheCommands.class);

    private final MiniClient client;
    private final MiniClientConnection connection;
    private final UIRenderer<?> renderer;
    private final GfxHandleAllocator handles;
    private final GfxImageAllocationRecovery allocationRecovery =
            new GfxImageAllocationRecovery();
    private String lastImageResourceID;
    private int lastImageResourceIDHandle;

    GfxImageCacheCommands(MiniClient client, MiniClientConnection connection,
                          UIRenderer<?> renderer, GfxHandleAllocator handles) {
        this.client = client;
        this.connection = connection;
        this.renderer = renderer;
        this.handles = handles;
    }

    int execute(int cmd, int len, byte[] cmddata, int[] hasret) {
        switch (cmd) {
            case GFXCMD2.GFXCMD_LOADIMAGE:
                // width, height
                if (len >= 8) {
                    int width, height;
                    int imghandle = handles.next();
                    width = GFXCMD2.readInt(0, cmddata);
                    height = GFXCMD2.readInt(4, cmddata);
                    if (!client.getImageCache().canCache(width, height)) {
                        imghandle = 0;
                    } else {
                        opensagetv.vibe.miniclient.uibridge.ImageHolder<?> img = loadImageWithRecovery(width, height);
                        client.getImageCache().put(imghandle, img, width, height);
                    }
                    hasret[0] = 1;
                    return imghandle;
                } else {
                    log.warn("Invalid len for GFXCMD_LOADIMAGE: {}", len);
                }
                break;
            case GFXCMD2.GFXCMD_LOADIMAGETARGETED:
                // handle, width, height // Not used unless we do uncompressed
                // images
                if (len >= 12) {
                    int width, height;
                    int imghandle = GFXCMD2.readInt(0, cmddata);
                    width = GFXCMD2.readInt(4, cmddata);
                    height = GFXCMD2.readInt(8, cmddata);
                    if (!client.getImageCache().makeRoom(width, height))
                    {
                        log.error("Unable to make room for targeted image {} ({}x{})",
                                imghandle, width, height);
                        hasret[0] = 0;
                        break;
                    }
                    opensagetv.vibe.miniclient.uibridge.ImageHolder<?> img = loadImageWithRecovery(width, height);
                    client.getImageCache().put(imghandle, img, width, height);
                    client.getImageCache().registerImageAccess(imghandle);
                    hasret[0] = 0;
                } else {
                    log.warn("Invalid len for GFXCMD_LOADIMAGETARGETED: {}", len);
                }
                break;
            case GFXCMD2.GFXCMD_PREPIMAGE:
                // width, height
                if (len >= 8) {
                    int width, height;
                    //int imghandle = handleCount++;;
                    width = GFXCMD2.readInt(0, cmddata);
                    height = GFXCMD2.readInt(4, cmddata);
                    // java.awt.Image img = new java.awt.image.BufferedImage(width,
                    // height, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                    // imageMap.put(new Integer(imghandle), img);
                    // We don't actually use this, it's just for being sure we have
                    // enough room for allocation
                    int imghandle = 1;
                    if (!client.getImageCache().canCache(width, height))
                        imghandle = 0;
                    else if (len >= 12) {
                        // We've got enough room for it and there's a cache ID,
                        // check if we've got it cached locally
                        int strlen = GFXCMD2.readInt(8, cmddata);
                        if (strlen > 1) {
                            try {
                                String rezName = new String(cmddata, 16, strlen - 1);
                                lastImageResourceID = rezName;
                                // We use this hashcode to match it up on the
                                // loadCompressedImage call so we know we're caching
                                // the right thing
                                lastImageResourceIDHandle = imghandle = Math.abs(lastImageResourceID.hashCode());
                                java.io.File cachedFile = client.getImageCache().getCachedImageFile(rezName);
                                if (cachedFile != null) {
                                    // We've got it locally in our cache! Read it
                                    // from there.
                                    opensagetv.vibe.miniclient.uibridge.ImageHolder bi = renderer.readImage(cachedFile);
                                    if (bi == null || bi.getWidth() != width || bi.getHeight() != height) {
                                        if (bi != null) {
                                            // It doesn't match the cache
                                            log.debug("PREPIMAGE: CACHE ID verification failed for rezName={} cacheSize={}x{} reqSize={}x{}",
                                                    rezName, bi.getWidth(), bi.getHeight(), width, height);
                                            bi.dispose();
                                            cachedFile.delete();
                                        }
                                        // else we failed loading it from the cache
                                        // so we want it for sure!
                                    } else {
                                        imghandle = handles.next();
                                        if (VerboseLogging.DETAILED_GFX)
                                            log.debug("PREPIMAGE[{}]: Loading Loading From Cache: {}", imghandle, cachedFile);
                                        bi.setHandle(imghandle);
                                        client.getImageCache().put(imghandle, bi, width, height);
                                        renderer.registerTexture(bi);
                                        hasret[0] = 1;
                                        return -1 * imghandle;
                                    }
                                }
                            } catch (Exception e) {
                                log.error("ERROR loading compressed image", e);
                                e.printStackTrace();
                            }
                        }
                    }
                    // imghandle=STBGFX.GFX_loadImage(width, height);
                    hasret[0] = 1;
                    return imghandle;
                } else {
                    log.error("Invalid len for GFXCMD_PREPIMAGE: {}", len);
                }
                break;
            case GFXCMD2.GFXCMD_PREPIMAGETARGETED:
                // handle, width, height, [rezID]
                if (len >= 12) {
                    int imghandle, width, height;
                    imghandle = GFXCMD2.readInt(0, cmddata);
                    width = GFXCMD2.readInt(4, cmddata);
                    height = GFXCMD2.readInt(8, cmddata);
                    int strlen = GFXCMD2.readInt(12, cmddata);
                    client.getImageCache().makeRoom(width, height);
                    if (len >= 16) {
                        // We will not have this cached locally...but setup our vars
                        // to track it
                        String rezName = new String(cmddata, 20, strlen - 1);
                        lastImageResourceID = rezName;
                        lastImageResourceIDHandle = imghandle;
                        log.debug("Prepped targeted image with handle " + imghandle + " resource=" + rezName);
                    }
                    client.getImageCache().registerImageAccess(imghandle);
                    hasret[0] = 0;
                } else {
                    log.warn("Invalid len for GFXCMD_PREPIMAGE: {}", len);
                }
                break;
            case GFXCMD2.GFXCMD_LOADCACHEDIMAGE:
                // width, height
                if (len >= 18) {
                    int width, height, imghandle;
                    imghandle = GFXCMD2.readInt(0, cmddata);
                    width = GFXCMD2.readInt(4, cmddata);
                    height = GFXCMD2.readInt(8, cmddata);
                    int strlen = GFXCMD2.readInt(12, cmddata);
                    String rezName = new String(cmddata, 20, strlen - 1);
                    log.debug("imghandle=" + imghandle + " width=" + width + " height=" + height + " strlen=" + strlen
                            + " rezName=" + rezName);
                    client.getImageCache().makeRoom(width, height);
                    client.getImageCache().registerImageAccess(imghandle);
                    try {
                        log.debug("Loading resource from cache: {}", rezName);
                        java.io.File cachedFile = client.getImageCache().getCachedImageFile(rezName);
                        if (cachedFile != null) {
                            // We've got it locally in our cache! Read it from
                            // there.
                            log.debug("Image found in cache!");

                            // We've got it locally in our cache! Read it from
                            // there.
                            opensagetv.vibe.miniclient.uibridge.ImageHolder bi = renderer.readImage(cachedFile);
                            if (bi == null || bi.getWidth() != width || bi.getHeight() != height) {
                                if (bi != null) {
                                    // It doesn't match the cache
                                    log.debug("LOADIMAGECACHE: CACHE ID verification failed for rezName={} cacheSize={}x{} reqSize={}x{}",
                                            rezName, bi.getWidth(), bi.getHeight(), width, height);
                                    bi.dispose();
                                    cachedFile.delete();
                                }
                                // else we failed loading it from the cache so we
                                // want it for sure!
                                // This load failed but the server thought it would
                                // succeed, so we need to inform it that the image
                                // is no longer loaded.
                                client.getImageCache().postImageUnload(imghandle);
                                connection.postOfflineCacheChange(false, rezName);
                            } else {
                                bi.setHandle(imghandle);
                                client.getImageCache().put(imghandle, bi, width, height);
                                renderer.registerTexture(bi);
                                hasret[0] = 0;
                            }
                        } else {
                            log.error("ERROR Image not found in cache that should be there! rezName={}", rezName);
                            // This load failed but the server thought it would
                            // succeed, so we need to inform it that the image is no
                            // longer loaded.
                            client.getImageCache().postImageUnload(imghandle);
                            connection.postOfflineCacheChange(false, rezName);
                        }
                    } catch (Exception e) {
                        log.error("ERROR loading compressed image", e);
                    }
                    hasret[0] = 0;
                } else {
                    log.error("Invalid len for GFXCMD_PREPIMAGE: {}", len);
                }
                break;
            case GFXCMD2.GFXCMD_UNLOADIMAGE:
                // handle
                if (len == 4) {
                    int handle;
                    handle = GFXCMD2.readInt(0, cmddata);
                    client.getImageCache().unloadImage(handle);
                    client.getImageCache().clearImageAccess(handle);
                } else {
                    log.error("Invalid len for GFXCMD_UNLOADIMAGE: {}", len);
                }
                break;
            case GFXCMD2.GFXCMD_LOADIMAGELINE:
                // handle, line, len, data
                if (len >= 12 && len >= (12 + GFXCMD2.readInt(8, cmddata))) {
                    int handle, line, len2;
                    // unsigned char *data=&cmddata[12];
                    handle = GFXCMD2.readInt(0, cmddata);
                    line = GFXCMD2.readInt(4, cmddata);
                    len2 = GFXCMD2.readInt(8, cmddata);
                    renderer.loadImageLine(handle, client.getImageCache().get(handle), line, len2, cmddata);
                    client.getImageCache().registerImageAccess(handle);
                } else {
                    log.error("Invalid len for GFXCMD_LOADIMAGELINE: {}", len);
                }
                break;
            case GFXCMD2.GFXCMD_LOADIMAGECOMPRESSED:
                // handle, line, len, data
                if (len >= 8 && len >= (8 + GFXCMD2.readInt(4, cmddata))) {
                    int handle, len2;
                    handle = GFXCMD2.readInt(0, cmddata);
                    len2 = GFXCMD2.readInt(4, cmddata);
                    java.io.File cacheFile = null;
                    // Save this image to our disk cache
                    java.io.FileOutputStream fos = null;
                    boolean deleteCacheFile = false;
                    String resID = null;
                    try {
                        boolean cacheAdd = false;
                        //log.debug("LoadImageCompressed: {}", handle);
                        if (lastImageResourceID != null && lastImageResourceIDHandle == handle) {
                            //log.debug("LoadImageCompressed: {}, {}", handle, lastImageResourceID);
                            cacheFile = client.getImageCache().getCachedImageFile(lastImageResourceID, false);
                            resID = lastImageResourceID;
                            if (cacheFile != null && (!cacheFile.isFile() || cacheFile.length() == 0))
                                cacheAdd = true;
                        }
                        if (cacheFile == null) {
                            cacheFile = java.io.File.createTempFile("stv", "img");
                            deleteCacheFile = true;
                            log.debug("LoadImageCompressed: {}, CACHED: {}", handle, cacheFile);
                        }
                        fos = new java.io.FileOutputStream(cacheFile);
                        fos.write(cmddata, 12, len2); // an extra 4 for the header
                        if (cacheAdd) {
                            connection.postOfflineCacheChange(true, lastImageResourceID);
                        }
                    } catch (java.io.IOException e) {
                        log.error("ERROR writing to cache file {}", cacheFile, e);
                        cacheFile = null;
                    } finally {
                        try {
                            if (fos != null)
                                fos.close();
                        } catch (Exception e) {
                        }
                        fos = null;
                    }
                    client.getImageCache().registerImageAccess(handle);
                    if (cacheFile != null) {
                        if (!connection.doesUseAdvancedImageCaching()) {
                            handle = handles.next();
                            hasret[0] = 1;
                        } else
                            hasret[0] = 0;
                        //log.debug("LOADIMAGE: AdvancedImageCaching: {}, Handle: {}, Return: {}", connection.doesUseAdvancedImageCaching(), handle, hasret[0]);
                        try {
                            //log.debug("LoadImageCompressed: {}, reading Cached File: {}", handle, cacheFile);
                            opensagetv.vibe.miniclient.uibridge.ImageHolder img = null;
                            img = renderer.readImage(cacheFile);
                            img.setHandle(handle);
                            client.getImageCache().put(handle, img, img.getWidth(), img.getHeight());
                            renderer.registerTexture(img);
                            if (deleteCacheFile)
                                cacheFile.delete();
                            return handle;
                        } catch (Exception e) {
                            log.error("ERROR loading compressed image", e);
                        }
                    }
                    if (deleteCacheFile && cacheFile != null)
                        cacheFile.delete();
                } else {
                    log.error("Invalid len for GFXCMD_LOADIMAGECOMPRESSED: {}", len);
                }
                break;
            case GFXCMD2.GFXCMD_XFMIMAGE:
                // srcHandle, destHandle, destWidth, destHeight, maskCornerArc
                if (len >= 20) {
                    int srcHandle, destHandle, destWidth, destHeight, maskCornerArc;
                    srcHandle = GFXCMD2.readInt(0, cmddata);
                    destHandle = GFXCMD2.readInt(4, cmddata);
                    destWidth = GFXCMD2.readInt(8, cmddata);
                    destHeight = GFXCMD2.readInt(12, cmddata);
                    maskCornerArc = GFXCMD2.readInt(16, cmddata);
                    int rvHandle = destHandle;
                    if (!connection.doesUseAdvancedImageCaching()) {
                        rvHandle = handles.next();
                        hasret[0] = 1;
                    } else
                        hasret[0] = 0;
                    opensagetv.vibe.miniclient.uibridge.ImageHolder srcImg = client.getImageCache().get(srcHandle);
                    if ((hasret[0] == 1 && !client.getImageCache().canCache(destWidth, destHeight)) || srcImg == null)
                        rvHandle = 0;
                    else {
                        opensagetv.vibe.miniclient.uibridge.ImageHolder destImg = newImageWithRecovery(destWidth, destHeight);
                        client.getImageCache().put(rvHandle, destImg, destWidth, destHeight);
                        renderer.xfmImage(srcHandle, srcImg, destHandle, destImg, destWidth, destHeight, maskCornerArc);
                    }
                    return rvHandle;
                } else {
                    log.error("Invalid len for GFXCMD_XFMIMAGE: {}", len);
                }
                break;
            default:
                throw new IllegalArgumentException("Not an image/cache command: " + cmd);
        }
        return 0;
    }

    private opensagetv.vibe.miniclient.uibridge.ImageHolder<?> loadImageWithRecovery(
            final int width, final int height)
    {
        return allocationRecovery.allocate(
                new GfxImageAllocationRecovery.Allocation<opensagetv.vibe.miniclient.uibridge.ImageHolder<?>>()
                {
                    @Override
                    public opensagetv.vibe.miniclient.uibridge.ImageHolder<?> allocate()
                    {
                        return renderer.loadImage(width, height);
                    }
                },
                new GfxImageAllocationRecovery.Evictor()
                {
                    @Override
                    public boolean evictOldestDisposable()
                    {
                        return client.getImageCache().evictOldestDisposableImage();
                    }
                });
    }

    private opensagetv.vibe.miniclient.uibridge.ImageHolder<?> newImageWithRecovery(
            final int width, final int height)
    {
        return allocationRecovery.allocate(
                new GfxImageAllocationRecovery.Allocation<opensagetv.vibe.miniclient.uibridge.ImageHolder<?>>()
                {
                    @Override
                    public opensagetv.vibe.miniclient.uibridge.ImageHolder<?> allocate()
                    {
                        return renderer.newImage(width, height);
                    }
                },
                new GfxImageAllocationRecovery.Evictor()
                {
                    @Override
                    public boolean evictOldestDisposable()
                    {
                        return client.getImageCache().evictOldestDisposableImage();
                    }
                });
    }

    long getAllocationRecoveryAttempts() { return allocationRecovery.getAttempts(); }
    long getAllocationRecoveryEvictions() { return allocationRecovery.getEvictions(); }
    long getAllocationRecoverySuccesses() { return allocationRecovery.getSuccesses(); }
    long getAllocationRecoveryFailures() { return allocationRecovery.getFailures(); }
}
