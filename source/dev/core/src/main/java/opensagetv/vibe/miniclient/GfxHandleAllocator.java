/*
 * Copyright 2015 The SageTV Authors. All Rights Reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package opensagetv.vibe.miniclient;

/** Preserves the single monotonically increasing handle namespace used by GFX commands. */
final class GfxHandleAllocator {
    private int nextHandle = 2;

    synchronized int next() {
        return nextHandle++;
    }
}
