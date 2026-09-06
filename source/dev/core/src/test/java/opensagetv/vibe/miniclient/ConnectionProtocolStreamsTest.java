/*
 * Copyright 2015 The SageTV Authors. All Rights Reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package opensagetv.vibe.miniclient;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ConnectionProtocolStreamsTest {
    @Test
    public void openRequiresBothStreamDirections() throws Exception {
        ConnectionProtocolStreams owner = new ConnectionProtocolStreams();
        try {
            owner.open(null, new ByteArrayOutputStream());
            fail("missing input must fail");
        } catch (IOException expected) {
        }
        try {
            owner.open(new ByteArrayInputStream(new byte[0]), null);
            fail("missing output must fail");
        } catch (IOException expected) {
        }
        assertFalse(owner.isOpen());
    }

    @Test
    public void replacementClosesPreviousPairAndCloseIsIdempotent() throws Exception {
        ConnectionProtocolStreams owner = new ConnectionProtocolStreams();
        CloseTrackingInput firstInput = new CloseTrackingInput();
        CloseTrackingOutput firstOutput = new CloseTrackingOutput();
        ConnectionProtocolStreams.OpenStreams first = owner.open(firstInput, firstOutput);
        assertNotNull(first.gfxInput);
        assertNotNull(first.eventOutput);
        assertTrue(owner.isOpen());

        CloseTrackingInput secondInput = new CloseTrackingInput();
        CloseTrackingOutput secondOutput = new CloseTrackingOutput();
        owner.open(secondInput, secondOutput);
        assertTrue(firstInput.closed);
        assertTrue(firstOutput.closed);
        assertTrue(owner.isOpen());

        owner.close();
        owner.close();
        assertTrue(secondInput.closed);
        assertTrue(secondOutput.closed);
        assertFalse(owner.isOpen());
    }

    private static final class CloseTrackingInput extends ByteArrayInputStream {
        boolean closed;

        CloseTrackingInput() {
            super(new byte[0]);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }

    private static final class CloseTrackingOutput extends ByteArrayOutputStream {
        boolean closed;

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }
}
