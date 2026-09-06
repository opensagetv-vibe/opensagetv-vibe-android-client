/*
 * Copyright 2026 The OpenSageTV Vibe Authors.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package opensagetv.vibe.miniclient;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Performs one bounded UI-image allocation retry after evicting one disposable
 * LRU cache entry. A terminal {@link OutOfMemoryError} is always rethrown.
 */
final class GfxImageAllocationRecovery
{
    interface Allocation<T>
    {
        T allocate();
    }

    interface Evictor
    {
        boolean evictOldestDisposable();
    }

    private final AtomicLong attempts = new AtomicLong();
    private final AtomicLong evictions = new AtomicLong();
    private final AtomicLong successes = new AtomicLong();
    private final AtomicLong failures = new AtomicLong();

    <T> T allocate(Allocation<T> allocation, Evictor evictor)
    {
        try
        {
            return allocation.allocate();
        }
        catch (OutOfMemoryError firstFailure)
        {
            attempts.incrementAndGet();
            if (!evictor.evictOldestDisposable())
            {
                failures.incrementAndGet();
                throw firstFailure;
            }
            evictions.incrementAndGet();
            try
            {
                T recovered = allocation.allocate();
                successes.incrementAndGet();
                return recovered;
            }
            catch (OutOfMemoryError terminalFailure)
            {
                failures.incrementAndGet();
                terminalFailure.addSuppressed(firstFailure);
                throw terminalFailure;
            }
        }
    }

    long getAttempts() { return attempts.get(); }
    long getEvictions() { return evictions.get(); }
    long getSuccesses() { return successes.get(); }
    long getFailures() { return failures.get(); }
}
