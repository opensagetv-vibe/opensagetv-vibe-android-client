/*
 * Copyright 2015 The SageTV Authors. All Rights Reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package opensagetv.vibe.miniclient;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class GfxHandleAllocatorTest {
    @Test
    public void preservesHistoricalFirstHandleAndMonotonicNamespace() {
        GfxHandleAllocator handles = new GfxHandleAllocator();
        assertEquals(2, handles.next());
        assertEquals(3, handles.next());
        assertEquals(4, handles.next());
    }
}
