/*
 * Copyright 2015 The SageTV Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package opensagetv.vibe.miniclient;

/**
 * Owns the small amount of state that controls MiniClient GFX reconnects.
 *
 * <p>The wire loop remains in {@link MiniClientConnection}; this class only
 * makes the existing eligibility and event-suppression rules explicit and
 * safely published between the GFX reader and event producer threads.</p>
 */
final class ConnectionReconnectState {
    private volatile boolean allowed;
    private volatile boolean firstFrameStarted;
    private volatile boolean reconnecting;

    boolean isAllowed() {
        return allowed;
    }

    void setAllowed(boolean allowed) {
        this.allowed = allowed;
    }

    boolean markFirstFrameStarted() {
        if (firstFrameStarted)
            return false;
        firstFrameStarted = true;
        return true;
    }

    boolean hasFirstFrameStarted() {
        return firstFrameStarted;
    }

    boolean canReconnect(boolean alive, boolean encryptedEvents) {
        return allowed && alive && firstFrameStarted && !encryptedEvents;
    }

    void begin() {
        reconnecting = true;
    }

    void finish() {
        reconnecting = false;
    }

    boolean isReconnecting() {
        return reconnecting;
    }

    boolean shouldDispatchEvents() {
        return !reconnecting;
    }
}
