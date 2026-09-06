/*
 * Copyright 2026 The OpenSageTV Vibe Authors.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package opensagetv.vibe.miniclient;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

public class GfxImageAllocationRecoveryTest
{
    @Test
    public void successfulAllocationDoesNotEvict()
    {
        GfxImageAllocationRecovery recovery = new GfxImageAllocationRecovery();
        String value = recovery.allocate(() -> "ok", () -> {
            fail("eviction must not run");
            return false;
        });
        assertEquals("ok", value);
        assertEquals(0, recovery.getAttempts());
        assertEquals(0, recovery.getEvictions());
    }

    @Test
    public void evictsOneEntryAndRetriesExactlyOnce()
    {
        GfxImageAllocationRecovery recovery = new GfxImageAllocationRecovery();
        AtomicInteger allocations = new AtomicInteger();
        AtomicInteger evictions = new AtomicInteger();
        String value = recovery.allocate(() -> {
            if (allocations.getAndIncrement() == 0)
                throw new OutOfMemoryError("first");
            return "recovered";
        }, () -> {
            evictions.incrementAndGet();
            return true;
        });
        assertEquals("recovered", value);
        assertEquals(2, allocations.get());
        assertEquals(1, evictions.get());
        assertEquals(1, recovery.getAttempts());
        assertEquals(1, recovery.getEvictions());
        assertEquals(1, recovery.getSuccesses());
        assertEquals(0, recovery.getFailures());
    }

    @Test
    public void noDisposableEntryRethrowsOriginalOutOfMemory()
    {
        GfxImageAllocationRecovery recovery = new GfxImageAllocationRecovery();
        OutOfMemoryError expected = new OutOfMemoryError("original");
        try
        {
            recovery.allocate(() -> { throw expected; }, () -> false);
            fail("expected OutOfMemoryError");
        }
        catch (OutOfMemoryError actual)
        {
            assertSame(expected, actual);
        }
        assertEquals(1, recovery.getAttempts());
        assertEquals(0, recovery.getEvictions());
        assertEquals(0, recovery.getSuccesses());
        assertEquals(1, recovery.getFailures());
    }

    @Test
    public void secondOutOfMemoryIsNeverHiddenOrRetriedAgain()
    {
        GfxImageAllocationRecovery recovery = new GfxImageAllocationRecovery();
        AtomicInteger allocations = new AtomicInteger();
        OutOfMemoryError terminal = new OutOfMemoryError("terminal");
        try
        {
            recovery.allocate(() -> {
                if (allocations.getAndIncrement() == 0)
                    throw new OutOfMemoryError("first");
                throw terminal;
            }, () -> true);
            fail("expected OutOfMemoryError");
        }
        catch (OutOfMemoryError actual)
        {
            assertSame(terminal, actual);
            assertEquals(1, actual.getSuppressed().length);
        }
        assertEquals(2, allocations.get());
        assertEquals(1, recovery.getAttempts());
        assertEquals(1, recovery.getEvictions());
        assertEquals(0, recovery.getSuccesses());
        assertEquals(1, recovery.getFailures());
    }
}
