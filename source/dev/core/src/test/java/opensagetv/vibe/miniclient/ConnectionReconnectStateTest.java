/*
 * Copyright 2015 The SageTV Authors. All Rights Reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package opensagetv.vibe.miniclient;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ConnectionReconnectStateTest {
    @Test
    public void reconnectRequiresNegotiationLiveConnectionFirstFrameAndPlainEvents() {
        ConnectionReconnectState state = new ConnectionReconnectState();

        assertFalse(state.canReconnect(true, false));
        state.setAllowed(true);
        assertFalse(state.canReconnect(true, false));
        assertTrue(state.markFirstFrameStarted());
        assertFalse(state.markFirstFrameStarted());
        assertTrue(state.hasFirstFrameStarted());
        assertTrue(state.canReconnect(true, false));
        assertFalse(state.canReconnect(false, false));
        assertFalse(state.canReconnect(true, true));
    }

    @Test
    public void reconnectSuppressesEventsOnlyBetweenBeginAndFinish() {
        ConnectionReconnectState state = new ConnectionReconnectState();

        assertTrue(state.shouldDispatchEvents());
        state.begin();
        assertTrue(state.isReconnecting());
        assertFalse(state.shouldDispatchEvents());
        state.finish();
        assertFalse(state.isReconnecting());
        assertTrue(state.shouldDispatchEvents());
    }

    @Test
    public void negotiatedSupportCanBeRevoked() {
        ConnectionReconnectState state = new ConnectionReconnectState();
        state.setAllowed(true);
        state.markFirstFrameStarted();
        assertTrue(state.isAllowed());
        assertTrue(state.canReconnect(true, false));

        state.setAllowed(false);
        assertFalse(state.isAllowed());
        assertFalse(state.canReconnect(true, false));
    }
}
