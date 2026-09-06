from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
CONNECTION = ROOT / "source/dev/core/src/main/java/opensagetv/vibe/miniclient/MiniClientConnection.java"
WORKER_OWNER = ROOT / "source/dev/core/src/main/java/opensagetv/vibe/miniclient/ConnectionWorkerOwner.java"
EVENT_ROUTER = ROOT / "source/dev/core/src/main/java/opensagetv/vibe/miniclient/ConnectionEventRouter.java"
GFX_EXCHANGE = ROOT / "source/dev/core/src/main/java/opensagetv/vibe/miniclient/GfxFrameExchange.java"
FILE_TRANSFERS = ROOT / "source/dev/core/src/main/java/opensagetv/vibe/miniclient/ConnectionFileTransferOwner.java"
RECONNECT_STATE = ROOT / "source/dev/core/src/main/java/opensagetv/vibe/miniclient/ConnectionReconnectState.java"
PROTOCOL_STREAMS = ROOT / "source/dev/core/src/main/java/opensagetv/vibe/miniclient/ConnectionProtocolStreams.java"
GFX = ROOT / "source/dev/core/src/main/java/opensagetv/vibe/miniclient/GFXCMD2.java"
GFX_LIFECYCLE = ROOT / "source/dev/core/src/main/java/opensagetv/vibe/miniclient/GfxLifecycleFrameCommands.java"
GFX_DRAWING = ROOT / "source/dev/core/src/main/java/opensagetv/vibe/miniclient/GfxDrawingCommands.java"
GFX_SURFACE_VIDEO = ROOT / "source/dev/core/src/main/java/opensagetv/vibe/miniclient/GfxSurfaceVideoCommands.java"
GFX_FONT = ROOT / "source/dev/core/src/main/java/opensagetv/vibe/miniclient/GfxFontCommands.java"
GFX_TRANSFORM = ROOT / "source/dev/core/src/main/java/opensagetv/vibe/miniclient/GfxTransformBatchCommands.java"
GFX_IMAGE = ROOT / "source/dev/core/src/main/java/opensagetv/vibe/miniclient/GfxImageCacheCommands.java"
GFX_IMAGE_RECOVERY = ROOT / "source/dev/core/src/main/java/opensagetv/vibe/miniclient/GfxImageAllocationRecovery.java"
IMAGE_CACHE = ROOT / "source/dev/core/src/main/java/opensagetv/vibe/miniclient/ImageCache.java"
MEDIA = ROOT / "source/dev/core/src/main/java/opensagetv/vibe/miniclient/MediaCmd.java"
MEDIA3 = ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/media3/Media3MediaPlayerImpl.java"
EXO2 = ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/exoplayer2/Exo2MediaPlayerImpl.java"
LIFECYCLE = ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/UIActivityLifeCycleHandler.java"
DOC = ROOT / "docs/CONNECTION_PROTOCOL_LIFECYCLE.md"


def assert_in_order(testcase, text, *needles):
    cursor = -1
    for needle in needles:
        location = text.find(needle, cursor + 1)
        testcase.assertNotEqual(location, -1, f"missing ordering marker: {needle}")
        testcase.assertGreater(location, cursor, f"out-of-order marker: {needle}")
        cursor = location


class ConnectionProtocolCharacterizationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.connection = CONNECTION.read_text(encoding="utf-8")
        cls.worker_owner = WORKER_OWNER.read_text(encoding="utf-8")
        cls.event_router = EVENT_ROUTER.read_text(encoding="utf-8")
        cls.gfx_exchange = GFX_EXCHANGE.read_text(encoding="utf-8")
        cls.file_transfers = FILE_TRANSFERS.read_text(encoding="utf-8")
        cls.reconnect_state = RECONNECT_STATE.read_text(encoding="utf-8")
        cls.protocol_streams = PROTOCOL_STREAMS.read_text(encoding="utf-8")
        cls.gfx = GFX.read_text(encoding="utf-8")
        cls.gfx_lifecycle = GFX_LIFECYCLE.read_text(encoding="utf-8")
        cls.gfx_drawing = GFX_DRAWING.read_text(encoding="utf-8")
        cls.gfx_surface_video = GFX_SURFACE_VIDEO.read_text(encoding="utf-8")
        cls.gfx_font = GFX_FONT.read_text(encoding="utf-8")
        cls.gfx_transform = GFX_TRANSFORM.read_text(encoding="utf-8")
        cls.gfx_image = GFX_IMAGE.read_text(encoding="utf-8")
        cls.gfx_image_recovery = GFX_IMAGE_RECOVERY.read_text(encoding="utf-8")
        cls.image_cache = IMAGE_CACHE.read_text(encoding="utf-8")
        cls.media = MEDIA.read_text(encoding="utf-8")
        cls.media3 = MEDIA3.read_text(encoding="utf-8")
        cls.exo2 = EXO2.read_text(encoding="utf-8")
        cls.lifecycle = LIFECYCLE.read_text(encoding="utf-8")
        cls.doc = DOC.read_text(encoding="utf-8")

    def test_connection_starts_media_before_gfx(self):
        connect = self.connection[
            self.connection.index("public void connect()") :
            self.connection.index("private void discoverCodecSupport()")
        ]
        assert_in_order(
            self,
            connect,
            "connectionWorkers.setMediaSocket(EstablishServerConnection(1))",
            "connectionWorkers.setGfxSocket(EstablishServerConnection(0))",
            "alive = true",
            "client.setCurrentConnection(this)",
            'new Thread("Media-"',
            "connectionWorkers.awaitMediaWorkerStarted(2000L)",
            'connectionDiagnostics.lifecycle("media_before_gfx_ready")',
            'new Thread("GFX-"',
        )
        self.assertNotIn("Thread.sleep(100)", connect)
        self.assertIn("mediaWorkerStarted.countDown()", self.worker_owner)

    def test_physical_ordering_gate_forces_home_close_policy(self):
        script = (ROOT / "scripts/mcp_connection_order_test.py").read_text(encoding="utf-8")
        self.assertIn('"keep_session_in_background": False', script)

    def test_gfx_framing_precedes_serial_dispatch(self):
        assert_in_order(
            self,
            self.connection,
            'new ConnectionEventRouter("EVTRouter"',
            "eventRouterThread.start()",
            "protocolStreams.open(connectionWorkers.gfxSocket())",
            'new Thread("GFXRead")',
            "myStream.readFully(gfxCmds)",
            "myStream.readFully(cmdbuffer, 0, len)",
            "gfxFrames.publish(gfxCmds, cmdbuffer",
            "GfxFrameExchange.Frame frame = gfxFrames.awaitFrame()",
            "cmd = frame.header",
            "myGfx.ExecuteGFXCommand",
            "gfxFrames.complete(frame)",
        )
        self.assertIn("while (pending != null)", self.gfx_exchange)
        self.assertIn("pending != frame", self.gfx_exchange)

    def test_event_writer_and_reconnect_contract_is_recorded(self):
        self.assertIn("new ArrayBlockingQueue<Runnable>(100)", self.event_router)
        self.assertIn("synchronized (eventChannel)", self.connection)
        self.assertIn("if (dispatchState.shouldDispatch())", self.event_router)
        self.assertIn("queue.take()", self.event_router)
        self.assertIn("command == GFXCMD_MEDIA_RECONNECT", self.connection)
        self.assertIn("installGfxReconnectSocket(reconnectSocket)", self.connection)
        self.assertIn("reconnectState.canReconnect(alive, encryptEvents)", self.connection)
        self.assertIn("return allowed && alive && firstFrameStarted && !encryptedEvents", self.reconnect_state)
        self.assertIn("return !reconnecting", self.reconnect_state)
        self.assertIn("protocolStreams.close()", self.connection)

    def test_image_allocation_recovery_is_single_attempt_and_fail_open_is_forbidden(self):
        self.assertIn("catch (OutOfMemoryError firstFailure)", self.gfx_image_recovery)
        self.assertEqual(2, self.gfx_image_recovery.count("allocation.allocate()"))
        self.assertIn("throw firstFailure", self.gfx_image_recovery)
        self.assertIn("throw terminalFailure", self.gfx_image_recovery)
        self.assertIn("evictOldestDisposableImage()", self.gfx_image)
        self.assertIn("activeConnection != null && activeConnection.isConnected()", self.image_cache)
        self.assertIn("long requestedBytes = imageBytes(width, height)", self.image_cache)
        self.assertIn("closeQuietly(oldInput)", self.protocol_streams)
        self.assertIn("closeQuietly(oldOutput)", self.protocol_streams)

    def test_resume_repaint_uses_ordered_event_router(self):
        repaint = self.connection[
            self.connection.index("public void postRepaintEvent") :
            self.connection.index("public void postImageUnload")
        ]
        assert_in_order(
            self,
            repaint,
            "eventRouterThread == null || eventRouterThread.queue == null",
            "eventRouterThread.enqueue(new Runnable()",
            "synchronized (eventChannel)",
            "eventChannel.write(UI_REPAINT_EVENT_REPLY_TYPE)",
            "eventChannel.flush()",
        )
        self.assertIn("Activity resume can request a repaint from Android's main thread", repaint)

    def test_vibe_exact_path_event_is_bounded_and_verifiable(self):
        self.assertIn("VIBE_WATCH_FILE_EVENT_REPLY_TYPE = 230", self.connection)
        self.assertIn("VIBE_WATCH_FILE_FROM_BEGINNING_EVENT_REPLY_TYPE = 232", self.connection)
        self.assertIn("VIBE_SEEK_EVENT_REPLY_TYPE = 233", self.connection)
        self.assertIn("postVibeWatchFileEvent(String serverPath)", self.connection)
        self.assertIn("postVibeWatchFileEvent(String serverPath, boolean fromBeginning)", self.connection)
        self.assertIn("postVibeSeekEvent(final long targetMs)", self.connection)
        self.assertIn("StandardCharsets.UTF_8", self.connection)
        self.assertIn("pathData.length > 8192", self.connection)
        self.assertIn("evtEncryptCipher.doFinal(pathData)", self.connection)

    def test_vibe_exact_channel_event_preserves_dotted_channel(self):
        self.assertIn("VIBE_CHANNEL_SET_EVENT_REPLY_TYPE = 231", self.connection)
        self.assertIn("postVibeChannelSetEvent(String channel)", self.connection)
        self.assertIn('channel.matches("[0-9]+(?:\\\\.[0-9]+)?")', self.connection)
        self.assertIn("channelData.length > 32", self.connection)
        self.assertIn("evtEncryptCipher.doFinal(channelData)", self.connection)
        self.assertIn("lastOpenChannel", self.media)
        self.assertIn("mediaContext.getChannelHint()", self.media)

    def test_close_unblocks_workers_without_clearing_a_newer_connection(self):
        close = self.connection[
            self.connection.index("public synchronized void close()") :
            self.connection.index("private void GFXThread()")
        ]
        assert_in_order(
            self,
            close,
            "alive = false",
            "client.clearCurrentConnection(this)",
            "uiTimer.cancel()",
            "connectionWorkers.closeGfxSocket()",
            "oldGfx.close()",
            "connectionWorkers.closeMediaSocket()",
            "connectionWorkers.interruptEventRouter()",
            "connectionWorkers.awaitStopped",
        )
        self.assertIn("if (!connectionWorkers.beginClose())", close)
        self.assertNotIn("private volatile java.net.Socket gfxSocket", self.connection)
        self.assertNotIn("private volatile java.net.Socket mediaSocket", self.connection)
        self.assertIn("private volatile Socket gfxSocket", self.worker_owner)
        self.assertIn("private volatile Socket mediaSocket", self.worker_owner)
        self.assertIn("worker == current", self.worker_owner)
        self.assertIn("deadline - System.nanoTime()", self.worker_owner)

    def test_reconnect_sockets_cannot_be_installed_after_shutdown(self):
        self.assertIn("private volatile boolean alive", self.connection)
        self.assertIn("private synchronized boolean installGfxReconnectSocket", self.connection)
        self.assertIn("private synchronized boolean installMediaReconnectSocket", self.connection)
        self.assertIn("if (!alive)", self.connection)
        self.assertIn("closeQuietly(socket)", self.connection)

    def test_remote_file_transfers_are_connection_owned(self):
        self.assertIn("fileTransfers.start", self.connection)
        self.assertIn("fileTransfers.close()", self.connection)
        self.assertIn('"SageTV-FS-Xfer-"', self.file_transfers)
        self.assertIn("active.remove(this)", self.file_transfers)
        self.assertIn("transfer.closeResources()", self.file_transfers)
        self.assertNotIn("private void asyncFSXfer", self.connection)

    def test_dvd_stream_selection_always_replies_and_runs_on_player_thread(self):
        dvd_streams = self.media[
            self.media.index("case MEDIACMD_DVD_STREAMS:") :
            self.media.index("default:", self.media.index("case MEDIACMD_DVD_STREAMS:"))
        ]
        self.assertIn("writeInt(streamResult, retbuf, 0)", dvd_streams)
        self.assertIn("return 4", dvd_streams)
        self.assertNotIn("return 0", dvd_streams)
        for player in (self.media3, self.exo2):
            audio = player[player.index("public void setAudioTrack") : player.index("public synchronized void flush")]
            self.assertIn("context.runOnUiThread", audio)
            subtitle = player[player.index("public void setSubtitleTrack") : player.index("public int getSelectedSubtitleTrack")]
            self.assertIn("context.runOnUiThread", subtitle)

    def test_media_state_url_capability_and_legacy_fallback_are_explicit(self):
        self.assertIn('"MEDIA_STATE_URL".equals(propName)', self.connection)
        self.assertIn('propVal = "TRUE";', self.connection)
        self.assertIn("MediaUrlContext.parse(urlString, legacyActive)", self.media)
        self.assertIn("mediaContext.getMajorTypeHint()", self.media)
        self.assertIn("mediaContext.getBufferSize()", self.media)
        self.assertIn("boolean isActive = mediaContext.isActive();", self.media)

    def test_lifecycle_connect_and_pause_sequence_is_preserved(self):
        assert_in_order(
            self,
            self.lifecycle,
            "mgr = activityCallback.createUIRenderer(this)",
            "miniClientView = activityCallback.createAndConfigureUIView(this)",
            "startMiniClient(si)",
            "connectionExecutor.submit",
            "client.connectConnection(si, UIActivityLifeCycleHandler.this)",
            "isConnectionTaskActive(generation)",
            "client.publishConnectedIfCurrent(connection)",
        )
        self.assertIn("Executors.newSingleThreadExecutor", self.lifecycle)
        self.assertIn('new Thread(runnable, "ANDROID-MINICLIENT")', self.lifecycle)
        self.assertIn("connectionFuture.cancel(true)", self.lifecycle)
        pause = self.lifecycle[
            self.lifecycle.index("public void onPause(Activity activity)") :
            self.lifecycle.index("public void onCreate(Activity activity)")
        ]
        assert_in_order(
            self,
            pause,
            "client.eventbus().unregister(this)",
            ".getPlaya().pause()",
            "EventRouter.postCommand(client, SageCommand.STOP)",
            "cancelConnectionTask(false)",
            "client.closeConnection()",
            "finish()",
        )

        destroy = self.lifecycle[
            self.lifecycle.index("public void onDestroy()") :
            self.lifecycle.index("public void setConnectingIsVisible")
        ]
        assert_in_order(
            self,
            destroy,
            "cancelConnectionTask(true)",
            "client.closeConnection()",
        )

    def test_gfx_dispatch_remains_serial_and_renderer_owned(self):
        self.assertIn("public int ExecuteGFXCommand", self.gfx)
        self.assertIn("lifecycleFrameCommands.execute(cmd, hasret)", self.gfx)
        self.assertIn("renderer.startFrame()", self.gfx_lifecycle)
        self.assertIn("renderer.flipBuffer()", self.gfx_lifecycle)
        self.assertIn("drawingCommands.execute(cmd, len, cmddata)", self.gfx)
        self.assertIn("renderer.drawTexture", self.gfx_drawing)
        self.assertIn("registerImageAccess(handle)", self.gfx_drawing)
        self.assertIn("surfaceVideoCommands.execute(cmd, len, cmddata, hasret)", self.gfx)
        self.assertIn("renderer.setVideoBounds", self.gfx_surface_video)
        self.assertIn("fontCommands.execute(cmd, len, cmddata)", self.gfx)
        self.assertIn("saveCacheData", self.gfx_font)
        self.assertIn("transformBatchCommands.execute(cmd, len, cmddata)", self.gfx)
        self.assertIn("GFXCMD_PUSHTRANSFORM", self.gfx_transform)
        self.assertIn("imageCacheCommands.execute(cmd, len, cmddata, hasret)", self.gfx)
        self.assertIn("lastImageResourceID", self.gfx_image)
        self.assertIn("postOfflineCacheChange", self.gfx_image)
        self.assertNotIn("new Thread", self.gfx)

    def test_document_records_migration_boundaries(self):
        for term in (
            "Startup sequence",
            "Command and reply ordering",
            "Reconnect behavior",
            "Teardown behavior and known gaps",
            "Required extraction order",
            "host-only characterization",
        ):
            self.assertIn(term, self.doc)


if __name__ == "__main__":
    unittest.main()
