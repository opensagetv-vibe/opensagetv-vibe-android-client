"""Shared GPU-stage source boundary tests; actual pixels run on the Android GPU."""
from pathlib import Path
import ast
import xml.etree.ElementTree as ET
import unittest

ROOT=Path(__file__).resolve().parents[1]
JAVA=ROOT/'source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video'

class GpuDeinterlaceTests(unittest.TestCase):
    def test_egl_fence_bridge_is_standard_bounded_and_preserves_gles2_input(self):
        native=(ROOT/'source/dev/android-shared/src/main/cpp/vibe_gpu_fence.c').read_text()
        for name in ('EGL_KHR_fence_sync','EGL_KHR_wait_sync','GL_OES_EGL_sync',
                     'eglGetCurrentDisplay()==display','pthread_once(&resolved,resolve)',
                     'eglCreateSyncKHR','eglWaitSyncKHR','eglDestroySyncKHR','uintptr_t'):
            self.assertIn(name,native)
        for forbidden in ('eglClientWaitSync','glFinish','glReadPixels','MediaCodec','AudioTrack','usleep'):
            self.assertNotIn(forbidden,native)
        bridge=(JAVA/'GpuEglFence.java').read_text()
        self.assertIn('catch(UnsatisfiedLinkError missing)',bridge)
        self.assertIn('display.getNativeHandle()',bridge)
        makefile=(ROOT/'source/dev/android-shared/src/main/cpp/Android.mk').read_text()
        self.assertIn('max-page-size=16384',makefile)
        build=(ROOT/'source/dev/android-shared/build.gradle').read_text()
        self.assertIn("ndkBuild { path file('src/main/cpp/Android.mk') }",build)
        self.assertIn("abiFilters 'armeabi-v7a', 'arm64-v8a'",build)
        owner=(JAVA/'GpuDeinterlaceOutput.java').read_text()
        self.assertIn('EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE',owner)
        worker=(JAVA/'GpuPreparedFieldPresenter.java').read_text()
        self.assertIn('EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE',worker)
        self.assertIn('require(GpuEglFence.available(display))',worker)
    def test_failed_surfaceless_trial_is_withdrawn_not_silently_published(self):
        source=(JAVA/'GpuDeinterlaceOutput.java').read_text()
        self.assertNotIn('surfacelessInput',source)
        self.assertIn('EGL14.eglMakeCurrent(display,surface,surface,eglContext)',source)
        self.assertIn('inputDrawable:',source)
    def test_shared_presenter_owns_one_display_tick_and_never_acquires_decoder_images(self):
        worker=(JAVA/'GpuPreparedFieldPresenter.java').read_text()
        tick=worker[worker.index('    private void present('):worker.index('    private void discard(')]
        self.assertEqual(1,tick.count('eglSwapBuffers('))
        self.assertLess(tick.index('schedule();'),tick.index('eglSwapBuffers('))
        for required in ('new HandlerThread("VibeGpuPresent")','GpuEglFence.waitGpu(display,field.readyFence)',
                         'GpuEglFence.create(display)','GLES20.glFlush()','fields.size()>2',
                         'first.epoch!=epoch.get()','gpu_original_field_queue_late',
                         '2_000_000_000L','listener.returned(field,readFence)'):
            self.assertIn(required,worker)
        for forbidden in ('SurfaceTexture','updateTexImage(','glReadPixels(','glFinish(',
                          'glClientWaitSync(','Thread.sleep(','seekTo('):
            self.assertNotIn(forbidden,worker)
        self.assertNotIn('eglTerminate(',worker)
        self.assertIn('Process.setThreadPriority(Process.THREAD_PRIORITY_DISPLAY)',worker)
    def test_shared_display_slots_wait_for_read_fence_and_old_epochs_cannot_release_new_slots(self):
        source=(JAVA/'GpuDeinterlaceOutput.java').read_text()
        callback=source[source.index('@Override public void returned(GpuPreparedFieldPresenter.Field'):
                        source.index('@Override public void startupDiscarded()')]
        self.assertLess(callback.index('GpuEglFence.waitGpu(display,readFence)'),callback.index('preparedBusy[field.slot]=false'))
        self.assertIn('if(field.epoch==splitEpoch)',callback)
        self.assertIn('presenter.clear(++splitEpoch)',source)
        self.assertIn('splitFieldContext=QUALIFIED_SPLIT_FIELD_PRESENTER && fieldRate && !inspect && frameProbe==null',source)
        self.assertIn('android.os.Build.VERSION.SDK_INT==25 && "mantis".equals(android.os.Build.DEVICE)',source)
        self.assertIn('if(presenter!=null)return false;',source)
        self.assertIn('if(splitFieldContext && QUALIFIED_SPLIT_FIELD_PRESENTER && provenWovenPair)',source)
        self.assertIn('private static final boolean QUALIFIED_SPLIT_FIELD_PRESENTER=true',source)
        self.assertNotIn('QUALIFIED_SPLIT_FIELD_PRESENTER && debug',source)
        self.assertIn('presenterAwaitingFreshInput=true',source)
        self.assertIn('boolean freshDecoderInputPending=pendingDecoderInput',source)
        self.assertIn('pendingDecoderInput=true',source)
        self.assertIn('choreographer.postFrameCallback(fieldTick)',source)
        self.assertIn('if(!wasPlaying && splitFieldContext)',source)
        self.assertIn('"Pending".equals(state) && "reanchoring".equals(reason)',source)
        self.assertIn('gl.postDelayed(() -> {',source)
        caption=(ROOT/'scripts/mcp_caption_test.py').read_text(encoding='utf-8')
        self.assertIn('"--require-client-gpu"',caption)
        self.assertIn('"video_deinterlace_mode": args.video_deinterlace_mode',caption)
        self.assertIn('str(state.get("videoDeinterlaceState", "")) == "Client GPU"',caption)
        self.assertIn('presenter!=null && availablePreparedSlots()<2',source)
        self.assertIn('reason="awaiting_prepared_field_slots"',source)
        worker=(JAVA/'GpuPreparedFieldPresenter.java').read_text()
        clear=worker[worker.index('    long clear('):worker.index('    private void initialize()')]
        self.assertLess(clear.index('epoch.set(nextEpoch)'),clear.index('handler.post'))
        self.assertIn('await(done,500',clear)
        self.assertIn('boolean needsBarrier=readCommandsSinceClear;',clear)
        self.assertIn('if(needsBarrier)',clear)
        self.assertLess(worker.index('readCommandsSinceClear=true;'),worker.index('GLES20.glDrawArrays'))
        self.assertIn('EGL14.eglDestroySurface(display,window)',worker)
        self.assertIn('thread.quitSafely()',worker)
        self.assertIn('error.addSuppressed(closeError)',worker)
    def test_inspection_never_switches_to_pair_preflight_or_duplicate_return_after_probe(self):
        source=(JAVA/'GpuDeinterlaceOutput.java').read_text()
        for beginning,ending in (('public boolean prepareWovenFormat','public void setSourcePictureLookup'),
                                 ('public boolean isDuplicateWovenOutput','public long decoderTextureReleaseNs'),
                                 ('public long decoderTextureReleaseNs','/** Main-thread')):
            method=source[source.index(beginning):source.index(ending,source.index(beginning)+1)]
            self.assertIn('if(inspect || !fieldRate',method)
        self.assertIn('codecSubmittedPictureUs=!inspect && frame.interlaced',source)
    def test_retained_field_has_priority_over_next_decoder_fence(self):
        source=(JAVA/'GpuDeinterlaceOutput.java').read_text()
        tick=source[source.index('    private void consumeAndPresent'):source.index('    private void presentQueuedField')]
        self.assertLess(tick.index('presentQueuedField(frameTimeNs)'),tick.index('if(inputDue &&'))
        self.assertIn('long beforeOutput=outputFrames;',tick)
        self.assertIn('boolean inputDue=pendingDecoderInput && frameTimeNs>=decoderInputNotBeforeNs;',tick)
        self.assertIn('if(presenter==null && !pendingFields.isEmpty())presentQueuedField(frameTimeNs)',tick)
        self.assertIn('if(presenter==null && outputFrames==beforeOutput)presentQueuedField(frameTimeNs)',tick)
        self.assertEqual(1,tick.count('postFrameCallback(fieldTick)'))
        self.assertIn('deferredWovenInput=!inspect && fieldRate',source)
        listener=source[source.index('decoderTexture.setOnFrameAvailableListener'):source.index('input = new Surface(decoderTexture)')]
        self.assertNotIn('!splitFieldContext && playing',listener)
    def test_input_return_occurs_only_after_both_original_fields_are_gpu_retained(self):
        source=(JAVA/'GpuDeinterlaceOutput.java').read_text()
        queued=source[source.index('queueOriginalFields(frame,firstDue,half)'):source.index('// Probe/paused/trick/frame-mode')]
        self.assertIn('GLES20.glFlush();releaseHeldTexture()',queued)
        self.assertLess(queued.index('queueOriginalFields'),queued.index('releaseHeldTexture'))
    def test_woven_input_notifications_defer_once_without_reanchoring_pts(self):
        source=(JAVA/'GpuDeinterlaceOutput.java').read_text()
        self.assertIn('if(!pendingDecoderInput)',source)
        self.assertIn('frameTimeNs>=decoderInputNotBeforeNs',source)
        callback=source[source.index('    private void consumeAndPresent'):source.index('    private void presentQueuedField')]
        self.assertEqual(1,callback.count('postFrameCallback(fieldTick)'))
        self.assertIn('decoderTransitions.get()==0',callback)
        self.assertNotIn('Thread.sleep',callback)
    def test_preflight_is_bounded_offscreen_before_codec_release_not_a_clock_reset(self):
        source=(JAVA/'GpuDeinterlaceOutput.java').read_text()
        preflight=source[source.index('    public boolean prepareWovenFormat'):source.index('    public void setSourcePictureLookup')]
        for expected in ('!fieldRate','frameProbe!=null','evidence==null || !evidence.confirmed()',
                         'gl.post','2_000_000_000L','formatWarm=true','gpu_format_preflight_deadline'):
            self.assertIn(expected,preflight)
        for absent in ('eglSwapBuffers','outputFrames++','Thread.sleep','seekTo','setPlaybackParameters'):
            self.assertNotIn(absent,preflight)
    def test_texture_preparation_keeps_original_display_deadline_separate(self):
        source=(JAVA/'GpuDeinterlaceOutput.java').read_text()
        method=source[source.index('    public long decoderTextureReleaseNs'):source.index('    /** Main-thread')]
        for bound in ('!fieldRate','!playing','frameProbe!=null','frame.interlaced && frame.duplicatedWoven',
                      'frame.sourceFieldPhase>=0','Math.abs(frame.speed-1f)<.01f'):
            self.assertIn(bound,method)
        self.assertIn('frame.codecReleaseNs=Math.min(deadlineNs,System.nanoTime())',method)
        self.assertNotRegex(method,r'frame\.releaseNs\s*=(?!=)')
        self.assertIn('Math.abs(item.codecReleaseNs-stampNs)',source)
        for renderer in ('media3/SurfaceVideoRenderer.java','exoplayer2/GpuSurfaceVideoRenderer.java'):
            text=(JAVA/renderer).read_text()
            self.assertIn('super.renderOutputBufferV21(codec,index,presentationUs,textureTime)',text)
            self.assertNotIn('codec.releaseOutputBuffer(index,true)',text)
    def test_only_source_mapped_duplicate_woven_codec_output_can_be_skipped(self):
        source=(JAVA/'GpuDeinterlaceOutput.java').read_text()
        guard=source[source.index('    public boolean isDuplicateWovenOutput'):source.index('    /** Main-thread')]
        for bound in ('!fieldRate','!playing','frameProbe!=null','closed.get()','failed.get()',
                      'decoderTransitions.get()>0','lookup==null','codecSubmittedPictureUs==Long.MIN_VALUE'):
            self.assertIn(bound,guard)
        self.assertIn('lookup.pictureTimeUs(presentationUs)==codecSubmittedPictureUs',guard)
        self.assertIn('frame.interlaced && duplicatedWoven && frame.nativeBypassReason.isEmpty()',source)
        for renderer in ('media3/SurfaceVideoRenderer.java','exoplayer2/GpuSurfaceVideoRenderer.java'):
            text=(JAVA/renderer).read_text()
            self.assertIn('!decodeOnly && !last',text)
            self.assertIn('Math.abs(getPlaybackSpeed()-1f)<.01f',text)
            self.assertIn('skipOutputBuffer(codec,index,presentationUs-getOutputStreamOffsetUs())',text)
    def test_original_fields_are_gpu_retained_and_submitted_one_per_display_tick(self):
        source=(JAVA/'GpuDeinterlaceOutput.java').read_text()
        self.assertIn('preparedTextures=new int[4]',source)
        self.assertIn('new PreparedField(first,frame.firstField,firstDue',source)
        self.assertIn('new PreparedField(second,1-frame.firstField,firstDue+half',source)
        self.assertIn('choreographer.postFrameCallback(fieldTick)',source)
        tick=source[source.index('    private void presentQueuedField('):source.index('    private static final class PreparedField')]
        self.assertEqual(1,tick.count('eglSwapBuffers('))
        self.assertIn('gpu_original_field_queue_late',tick)
        self.assertIn('decoderTransitions.get()>0',tick)
        self.assertIn('removeFrameCallback(fieldTick)',source)
        self.assertIn('gpu_original_field_queue_backpressure',source)
        self.assertIn('queuedEpochOutputs==0',tick)
        self.assertIn('2_000_000_000L',tick)
        self.assertIn('startupFieldsDiscarded',source)
        self.assertNotIn('glReadPixels',source)
        self.assertIn('queuedFields:',source)
        policy=(ROOT/'source/dev/core/src/main/java/opensagetv/vibe/miniclient/video/GpuDeinterlacePolicy.java').read_text()
        self.assertIn('if(fieldOutput && provenWovenPair)return minimum==0 && maximum>=0 ? 0 : -1;',policy)

    def test_fast_queue_retains_full_horizontal_same_parity_rows_not_full_bob_copies(self):
        source=(JAVA/'GpuDeinterlaceOutput.java').read_text()
        self.assertIn('int storedHeight="motion".equals(algorithm) ? height : height/2;',source)
        self.assertIn('prepareOriginalField(first,frame.firstField)',source)
        self.assertIn('prepareOriginalField(second,1-frame.firstField)',source)
        self.assertIn('GLES20.glViewport(0,0,width,height/2)',source)
        self.assertIn('program==presentProgram',source)
        allocate=source[source.index('    private void allocatePreparedFields'):source.index('    private void queueOriginalFields')]
        self.assertIn('GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR',allocate)
        self.assertIn('GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR',allocate)

    def test_hidden_overlay_uses_off_mode_not_overridden_visible_flag(self):
        driver=(ROOT/'scripts/mcp_gpu_deinterlace_test.py').read_text()
        self.assertIn("overlay_mode='off' if args.hide_overlay else 'detailed'",driver)
        self.assertIn("{'mode':overlay_mode}",driver)
        self.assertNotIn("{'visible':not args.hide_overlay,'mode':'detailed'}",driver)

    def test_source_sized_gpu_buffers_are_owned_and_native_layout_is_restored(self):
        view=(JAVA/'PlayerSurfaceView.java').read_text(encoding='utf-8')
        self.assertIn('getHolder().setFixedSize(width,height)',view)
        self.assertIn('if(gpuBufferOwner!=owner)return',view)
        self.assertIn('getHolder().setSizeFromLayout()',view)
        output=(JAVA/'GpuDeinterlaceOutput.java').read_text(encoding='utf-8')
        self.assertIn('!inspect && frame.interlaced && frame.duplicatedWoven',output)
        self.assertIn('main.post(() -> {',output)
        self.assertIn('useGpuSourceBuffers(this,bufferWidth,bufferHeight)',output)
        self.assertIn('targetWidth!=frame.width || targetHeight!=frame.height',output)
        self.assertIn('awaiting_source_sized_display',output)
        self.assertIn('restoreAfterGpuBuffers(this)',output)
        self.assertNotIn('setFixedSize', (JAVA/'media3/Media3MediaPlayerImpl.java').read_text())

    def test_phase_deadline_observation_is_finite_debug_probe_only(self):
        text=(JAVA/'GpuDeinterlaceOutput.java').read_text(encoding='utf-8')
        self.assertIn('if(profilePhaseRemaining>0)',text)
        self.assertIn('profilePhaseRemaining--',text)
        arm=text[text.index('    public boolean profileNextFrames('):text.index('    public boolean probeNextFrames(')]
        self.assertIn('!debug',arm)
        self.assertIn('count>240',arm)
        self.assertIn('profilePhaseRemaining=count',arm)
        self.assertIn('frame.releaseNs-System.nanoTime()',text)
        self.assertNotIn('glReadPixels',text)
        self.assertIn('nextDebugProfile.getAndSet(0)',text)
        self.assertIn('profileRemaining=profilePhaseRemaining=',text)

    def test_unknown_input_does_not_claim_display_before_fallback_classification(self):
        text=(JAVA/'GpuDeinterlaceOutput.java').read_text(encoding='utf-8')
        attach=text[text.index('    private void attach('):text.index('    private boolean ensureWindow(')]
        self.assertNotIn('eglCreateWindowSurface(',attach)
        self.assertIn('displaySurface = surface;',attach)
        render=text[text.index('    private void render()'):text.index('    private void draw(')]
        self.assertLess(render.index('fail(frame.nativeBypassReason,null)'),render.index('ensureWindow('))
        refresh=text[text.index('    public void refreshNativeDisplayLayer()'):text.index('    public void rendererAttached()')]
        self.assertIn('if (!displayLayerChanged) return;',refresh)
        self.assertIn('GpuDeinterlacePolicy.unsupportedInputReason(mime)',text)

    def test_explicit_gpu_choice_exists_in_startup_and_live_settings(self):
        resources=ROOT/'source/dev/android-shared/src/main/res'
        arrays=ET.parse(resources/'values/arrays.xml').getroot()
        values=[node.text for node in arrays.find("string-array[@name='entryvalues_video_deinterlace_mode']")]
        labels=[node.text for node in arrays.find("string-array[@name='entries_video_deinterlace_mode']")]
        self.assertEqual(['auto','off','client','client_gpu'],values)
        self.assertEqual('Client GPU',labels[values.index('client_gpu')])
        prefs=ET.parse(resources/'xml/prefs.xml').getroot()
        key='{http://schemas.android.com/apk/res/android}key'
        self.assertTrue(any(node.get(key)=='video_deinterlace_mode' for node in prefs.iter('ListPreference')))
        live=(JAVA.parent/'ActivePlayerAdjustmentsDialog.java').read_text(encoding='utf-8')
        self.assertIn('"Client", "Client GPU", "MIM"',live)
        settings=(JAVA.parent/'ui/settings/SettingsFragment.java').read_text(encoding='utf-8')
        self.assertIn('configureDeinterlacePreference(String.valueOf(newValue))',settings)
        self.assertIn('final boolean fixed="fixed".equals(streaming)',settings)
        self.assertIn('"Auto (MIM requires Fixed)"',settings)
    def test_software_decode_and_cpu_video_readback_are_not_a_hidden_fallback(self):
        text=(JAVA/'GpuDeinterlaceOutput.java').read_text(encoding='utf-8')
        self.assertIn('new Surface(decoderTexture)',text)
        self.assertIn('GL_TEXTURE_EXTERNAL_OES',text)
        self.assertNotIn('glReadPixels',text)
        self.assertNotIn('getOutputImage',text)
        self.assertNotIn('AudioTrack',text)
        self.assertNotIn('setPlaybackParameters',text)
        self.assertIn('hardware && GpuDeinterlacePolicy.wovenMpeg2',text)

    def test_history_and_surface_lifetime_are_session_owned(self):
        text=(JAVA/'GpuDeinterlaceOutput.java').read_text(encoding='utf-8')
        for required in ('frames.size() == 32','frames.clear()','decoderTexture.release()',
                         'input.release()','eglDestroySurface','eglDestroyContext',
                         'thread.quitSafely()','closed.compareAndSet(false,true)'):
            self.assertIn(required,text)
        self.assertIn('failed.compareAndSet(false,true)',text)
        self.assertIn('decoded frame metadata unmatched',text)

    def test_backends_keep_their_own_decoder_and_existing_clock(self):
        for backend,name in [('media3','Media3MediaPlayerImpl'),('exoplayer2','Exo2MediaPlayerImpl')]:
            text=(JAVA/backend/(name+'.java')).read_text(encoding='utf-8')
            for required in ('GpuVideoOutputOwner','GpuDeinterlacePolicy.requested(mode,',
                             'bindVideoOutput(player)','gpuVideoOutput.flush()',
                             'gpuVideoOutput.setPlaying(isPlaying)','gpuToRelease.close()',
                             'getSelectedVideoDecoderForDebug()','playbackRateController.getRate()'):
                self.assertIn(required,text)
            self.assertIn('expected.setVideoSurface(gpuVideoOutput.inputSurface())',text)
            self.assertIn('PrefStore.Keys.video_gpu_output_rate,"field"',text)
        exo=(JAVA/'exoplayer2/Exo2MediaPlayerImpl.java').read_text(encoding='utf-8')
        self.assertNotIn('new Media3MediaPlayerImpl',exo)

    def test_shader_field_order_and_boundary_pixels_are_explicit(self):
        text=(JAVA/'GpuDeinterlaceShaders.java').read_text(encoding='utf-8')
        for required in ('uHeight-1.-row','uParity','below=above','above=below',
                         'uHasPrevious','uEnabled<.5','uMotion'):
            self.assertIn(required,text)
        debug=(ROOT/'source/dev/android-tv/src/debug/java/opensagetv/vibe/miniclient/android/video/GpuDeinterlaceSelfTest.java').read_text(encoding='utf-8')
        self.assertIn('glReadPixels',debug)
        self.assertIn('(7-y)%2!=field',debug)
        self.assertIn('playbackReadback=false',debug)
        self.assertIn('EXTRACT_FIELD_EXTERNAL',debug)
        self.assertIn('PRESENT_FIELD',debug)

    def test_gpu_controls_are_compact_child_and_do_not_replace_ijk_choices(self):
        text=(JAVA.parent/'ActivePlayerAdjustmentsDialog.java').read_text(encoding='utf-8')
        for required in ('"GPU filter"','"Field order"','"Output rate"','showVideoFilters()',
                         '"fast","motion"','"auto","top","bottom"','"frame","field"',
                         'setIjkDeinterlaceFilter(value)','setGpuFieldOrder(value)'):
            self.assertIn(required,text)

    def test_control_context_is_proven_before_watch(self):
        text=(ROOT/'scripts/mcp_gpu_deinterlace_test.py').read_text(encoding='utf-8')
        self.assertLess(text.index("'dev_server_control_identity'"),text.index("'dev_play_server_path'"))
        for required in ('dev_checkpoint_settings','dev_restore_settings','ccRestore','scopeVerified'):
            self.assertIn(required,text)

    def test_surface_delivery_uses_existing_clock_and_keeps_vendor_workarounds(self):
        for path in ('media3/SurfaceVideoRenderer.java','exoplayer2/GpuSurfaceVideoRenderer.java'):
            text=(JAVA/path).read_text(encoding='utf-8')
            for required in ('processOutputBuffer','getPlaybackSpeed()',
                             'elapsedRealtimeUs','GpuDeinterlacePolicy.releaseLeadUs(output.codedFps())','return false',
                             'output.isPlaying()'):
                self.assertIn(required,text)
            self.assertNotIn('Thread.sleep',text)
            self.assertNotIn('seekTo',text)
        legacy=(JAVA/'exoplayer2/GpuSurfaceVideoRenderer.java').read_text(encoding='utf-8')
        self.assertIn('extends Exo2SurfaceVideoRenderer',legacy)
        factory=(JAVA/'exoplayer2/Exo2AudioExtensionRenderersFactory.java').read_text(encoding='utf-8')
        self.assertEqual(factory.count('protected void buildVideoRenderers('),1)
        self.assertIn('gpuOutputProvider == null',factory)

    def test_field_rate_uses_decoder_pts_not_collapsed_surface_callbacks(self):
        text=(JAVA/'GpuDeinterlaceOutput.java').read_text(encoding='utf-8')
        self.assertIn('secondFieldDelayNs(frame.fps,frame.metadataStepUs*1000L',text)
        self.assertNotIn('secondFieldDelayNs(frame.fps,delta',text)
        self.assertIn('releaseOwnedHandles();',text)
        self.assertIn('close();\n                throw new IllegalStateException("GPU initialization deadline")',text)
        self.assertIn('count>240',text)
        self.assertIn('if(!debug',text)
        self.assertIn('profileRemaining--',text)
        self.assertIn('if(currentSurface==surface)return;',text)
        self.assertIn('makeCurrent(window==EGL14.EGL_NO_SURFACE ? pbuffer : window)',text)
        self.assertIn('EGL_MIN_SWAP_INTERVAL',text)
        self.assertIn('GpuDeinterlacePolicy.swapInterval(fieldRate,minimumSwapInterval,maximumSwapInterval',text)
        self.assertIn('EGL14.EGL_MAX_SWAP_INTERVAL',text)
        self.assertIn('provenWovenPair',text)
        self.assertIn('android.os.Build.VERSION.SDK_INT,android.os.Build.DEVICE,renderer',text)

    def test_ijk_algorithm_selection_requires_actual_software_output(self):
        text=(JAVA/'ClientDeinterlaceStatus.java').read_text(encoding='utf-8')
        selection=text[text.index('public static boolean supportsSoftwareSelection'):text.index('public static String filter')]
        self.assertIn('mediaInfo((IJKMediaPlayerImpl) player)',selection)
        self.assertIn('"avcodec".equalsIgnoreCase(info.mVideoDecoder)',selection)
        self.assertNotIn('return player instanceof IJKMediaPlayerImpl;',selection)

    def test_real_time_budget_is_not_just_an_advancing_clock(self):
        text=(ROOT/'scripts/mcp_gpu_deinterlace_test.py').read_text(encoding='utf-8')
        for required in ('health_capturedMonotonicMs','presentedFps','args.require_field_cadence',
                         'coded*(2 if args.require_field_cadence else 1)*.9',
                         'GPU video presentation below real-time','expiresAtMs',
                         'dev_gpu_output_profile'):
            self.assertIn(required,text)
        self.assertIn('processing changed during observation:',text)
        self.assertLess(text.index("if final.get('videoDeinterlaceState')!=desired:"),text.index("native_bypass=str(final.get"))

    def test_preferences_restore_is_independent_of_caption_restore(self):
        text=(ROOT/'scripts/mcp_gpu_deinterlace_test.py').read_text(encoding='utf-8')
        tail=text[text.index('    finally:'):]
        self.assertIn("except Exception as exc: restore_errors.append('CC: '+str(exc))",tail)
        self.assertIn('        if checkpoint:\n            try:',tail)
        self.assertIn("'dev_restore_settings',timeout=100",tail)
        self.assertIn('--restore-only',text)
        self.assertIn("'graceful_timeout_s':2},timeout=120",text)

    def test_latched_texture_is_returned_before_codec_lifecycle_work(self):
        output=(JAVA/'GpuDeinterlaceOutput.java').read_text(encoding='utf-8')
        for required in ('decoderTexture.releaseTexImage()','decoderTransitions.incrementAndGet()',
                         'returned.await(500,TimeUnit.MILLISECONDS)',
                         'if(decoderTransitions.get()>0) {releaseHeldTexture();return;}',
                         'compareAndSet(previous,Math.max(0,previous-1))'):
            self.assertIn(required,output)
        for path in ('media3/SurfaceVideoRenderer.java','exoplayer2/GpuSurfaceVideoRenderer.java'):
            text=(JAVA/path).read_text(encoding='utf-8')
            for hook in ('releaseCodec','onDisabled','onPositionReset'):
                start=text.index('protected void '+hook+'(')
                body=text[start:text.index('\n    }',start)]
                self.assertLess(body.index('beginDecoderTransition()'),body.index('super.'+hook+'('))
                self.assertIn('finally',body)
                self.assertIn('endDecoderTransition()',body)
        for backend,name in (('media3','Media3MediaPlayerImpl'),('exoplayer2','Exo2MediaPlayerImpl')):
            text=(JAVA/backend/(name+'.java')).read_text(encoding='utf-8')
            self.assertLess(text.index('gpuToRelease.beginDecoderTransition()'),text.index('playerToRelease.release()'))
            self.assertIn('setGpuOutputProvider(outputSlot::get)',text)
            self.assertIn('if (expected != player) return;',text)
            self.assertIn('slotToRelease.set(null)',text)
            self.assertLess(text.index('playerToRelease.release()'),text.index('slotToRelease.set(null)'))

    def test_sustained_output_deficit_uses_bounded_native_fallback(self):
        text=(JAVA/'GpuDeinterlaceOutput.java').read_text(encoding='utf-8')
        body=text[text.index('    private void render()'):text.index('    private void draw(')]
        self.assertIn('budget.belowBudget(System.nanoTime(),outputFrames,required)',body)
        self.assertIn('gpu_presentation_budget',body)
        self.assertIn('fieldRate ? 2f : 1f',body)
        self.assertIn('budget.reset();',text)
        self.assertNotIn('glFinish(',text)

    def test_gpu_close_waits_for_real_renderer_detach(self):
        output=(JAVA/'GpuDeinterlaceOutput.java').read_text(encoding='utf-8')
        self.assertIn('attachedRenderers.get()>0',output)
        self.assertIn('closeRequested.get())closeAfterDetach()',output)
        self.assertIn('else guarded(() -> {pendingDecoderInput=false;render();})',output)
        failure=output[output.index('private void fail(String failureReason'):]
        self.assertLess(failure.index('beginDecoderTransition()'),failure.index('main.post'))
        for path in ('media3/SurfaceVideoRenderer.java','exoplayer2/GpuSurfaceVideoRenderer.java'):
            text=(JAVA/path).read_text(encoding='utf-8')
            start=text.index('@Override public void handleMessage')
            body=text[start:text.index('@Override protected void onCodecInitialized',start)]
            self.assertLess(body.rindex('super.handleMessage(type,message)'),body.index('attachGpu(next)'))
            self.assertIn('next.rendererAttached()',text)
            self.assertIn('previous.rendererDetached()',text)
            self.assertIn('attachGpu(null)',text[text.index('protected void onDisabled'):])
            self.assertIn('attachedGpu!=null && attachedGpu!=next && getCodecInfo()!=null',body)
            self.assertIn('GpuDeinterlacePolicy.releaseBeforeTransition(',body)
            self.assertLess(body.index('releaseCodec();'),body.rindex('super.handleMessage(type,message)'))
            # NVIDIA retarget is narrower than MTK's seek/disable workaround.
            reset=text[text.index('protected void onPositionReset'):text.index('protected boolean processOutputBuffer')]
            self.assertNotIn('refreshOnGpuReplacement',reset)

    def test_native_fallback_detaches_display_producer_before_callback(self):
        output=(JAVA/'GpuDeinterlaceOutput.java').read_text(encoding='utf-8')
        failure=output[output.index('private void fail(String failureReason'):output.index('@Override public void close()')]
        self.assertLess(failure.index('beginDecoderTransition()'),failure.index('destroyWindow()'))
        self.assertLess(failure.index('destroyWindow()'),failure.index('main.post'))
        self.assertNotIn('decoderTexture.release()',failure)
        transition=output[output.index('public void beginDecoderTransition()'):output.index('public void endDecoderTransition()')]
        self.assertNotIn('destroyWindow()',transition)
        self.assertIn('require(EGL14.eglDestroySurface(display,window))',output)

    def test_scoped_native_handoff_refreshes_layer_without_seek(self):
        output=(JAVA/'GpuDeinterlaceOutput.java').read_text(encoding='utf-8')
        self.assertIn('holder.setFormat(android.graphics.PixelFormat.RGBX_8888)',output)
        self.assertIn('((PlayerSurfaceView)videoView).refreshAfterGpuOutput()',output)
        view=(JAVA/'PlayerSurfaceView.java').read_text(encoding='utf-8')
        self.assertIn('? android.graphics.PixelFormat.RGB_565 : android.graphics.PixelFormat.RGBX_8888',view)
        self.assertIn('getHolder().setFormat(gpuNativeRefreshFormat)',view)
        for backend,name in (('media3','Media3MediaPlayerImpl'),('exoplayer2','Exo2MediaPlayerImpl')):
            text=(JAVA/backend/(name+'.java')).read_text(encoding='utf-8')
            callback=text[text.index('reason -> {'):text.index('PlaybackDebugTrap.recordDetailed("gpu_filter_unavailable"')]
            self.assertIn('GpuDeinterlacePolicy.refreshOnGpuReplacement(',callback)
            self.assertLess(callback.index('failed.refreshNativeDisplayLayer()'),callback.index('expected.setVideoSurfaceView'))
            self.assertNotIn('seekTo',callback)
            self.assertNotIn('setMediaSource',callback)

    def test_confirmed_progressive_bypass_returns_to_native_not_gpu_copy(self):
        output=(JAVA/'GpuDeinterlaceOutput.java').read_text(encoding='utf-8')
        render=output[output.index('    private void render()'):output.index('    private void draw(')]
        self.assertLess(render.index('!inspect && !frame.nativeBypassReason.isEmpty()'),render.index('boolean motion='))
        self.assertIn('fail(frame.nativeBypassReason,null);return;',render)
        status=(JAVA/'ClientDeinterlaceStatus.java').read_text(encoding='utf-8')
        self.assertIn('startsWith("gpu_native_bypass_") ? "Bypass" : "Unavailable"',status)
        driver=(ROOT/'scripts/mcp_gpu_deinterlace_test.py').read_text(encoding='utf-8')
        self.assertIn("or native_bypass else 'videoGpuOutputFrames'",driver)

    def test_fresh_gate_waits_for_stv_not_only_socket_connection(self):
        text=(ROOT/'scripts/mcp_gpu_deinterlace_test.py').read_text(encoding='utf-8')
        self.assertIn('readiness.update(menu_present=True,automation_ready=True,stable_ms=500)',text)
        self.assertIn("result['uiReadiness']=ready",text)
        readiness=text[text.index("readiness={'connected'"):text.index("result['uiReadiness']=ready")]
        self.assertIn('if not args.same_activity:',readiness)
        self.assertLess(text.index("if not original_cc: original_cc="),text.index("print('WATCH_SELECTED_DEVICE_ONLY'"))

    def test_pause_observation_waits_without_replaying_control(self):
        text=(ROOT/'scripts/mcp_gpu_deinterlace_test.py').read_text(encoding='utf-8')
        self.assertIn('if paused_clock_stable(previous_pause,a):',text)
        self.assertIn('time.monotonic()-pause_quiet_since>=.5',text)
        self.assertIn('pause_deadline=time.monotonic()+5',text)
        self.assertEqual(1,text.count("'dev_server_media_control',{'operation':'pause'}"))
        self.assertLess(text.index("result['pauseSettled']="),text.index('time.sleep(1.2);b='))

    def test_native_bypass_keeps_commissioned_source_rate_and_unknown_is_not_qualified(self):
        import math
        tree=ast.parse((ROOT/'scripts/mcp_gpu_deinterlace_test.py').read_text(encoding='utf-8'))
        function=next(node for node in tree.body if isinstance(node,ast.FunctionDef)
                      and node.name=='cadence_source_fps')
        namespace={'math':math}
        exec(compile(ast.Module(body=[function],type_ignores=[]),'cadence-oracle','exec'),namespace)
        source=namespace['cadence_source_fps']
        self.assertAlmostEqual(source({'videoGpuCodedFps':0},60000/1001),60000/1001)
        self.assertEqual(25,source({'videoGpuCodedFps':25},60))
        for invalid in (None,'bad',float('nan'),float('inf'),-1,241):
            self.assertEqual(0,source({'videoGpuCodedFps':invalid},invalid))
        text=(ROOT/'scripts/mcp_gpu_deinterlace_test.py').read_text(encoding='utf-8')
        self.assertIn("case.get('path')==args.server_path",text)
        self.assertIn("'sourceRateKnown':coded>0",text)
        self.assertIn('else (False if args.require_field_cadence else None)',text)

    def test_paused_clock_rejects_inflight_drain_and_missing_flags(self):
        # Exercise the pure oracle without loading the host-unavailable SDK.
        tree=ast.parse((ROOT/'scripts/mcp_gpu_deinterlace_test.py').read_text(encoding='utf-8'))
        function=next(node for node in tree.body if isinstance(node,ast.FunctionDef)
                      and node.name=='paused_clock_stable')
        namespace={}
        exec(compile(ast.Module(body=[function],type_ignores=[]),'pause-oracle','exec'),namespace)
        stable=namespace['paused_clock_stable']
        paused={'health_playWhenReady':False,'health_isPlaying':False,'mediaTimeMs':25000}
        self.assertFalse(stable(None,paused))
        self.assertFalse(stable(paused,dict(paused,mediaTimeMs=25244)))
        self.assertTrue(stable(paused,dict(paused,mediaTimeMs=25010)))
        self.assertFalse(stable(paused,dict(paused,mediaTimeMs=25030)))
        self.assertFalse(stable(paused,dict(paused,health_isPlaying=True)))
        self.assertFalse(stable({},{}))
        no_clock={'health_playWhenReady':False,'health_isPlaying':False}
        self.assertFalse(stable(no_clock,no_clock))

    def test_repeat_gate_proves_same_process_activity_not_just_no_reboot(self):
        text=(ROOT/'scripts/mcp_gpu_deinterlace_test.py').read_text(encoding='utf-8')
        self.assertIn('--same-activity',text)
        self.assertIn("first.get('pids')==after_activity.get('pids')",text)
        self.assertIn("first.get('resumedActivity')==after_activity.get('resumedActivity')",text)
        self.assertIn("if not args.same_activity:\n            call_dict(client,'dev_connect_server'",text)
        self.assertIn('same-Activity gate requires the current matching backend/server/Pull connection',text)

    def test_gsy_qualification_requires_actual_delegate_not_only_preference(self):
        text=(ROOT/'scripts/mcp_gpu_deinterlace_test.py').read_text(encoding='utf-8')
        self.assertIn("args.gsy_engine in ('media3','legacy_exo')",text)
        self.assertIn("current.get('health_backendClass','')",text)
        self.assertIn('requested GSY delegate was not proven by the active backend',text)

    def test_physical_driver_can_require_explicit_push_payload(self):
        text=(ROOT/'scripts/mcp_gpu_deinterlace_test.py').read_text(encoding='utf-8')
        self.assertIn("choices=('pull','push','dynamic')",text)
        self.assertIn("args.streaming in ('push','dynamic')",text)
        self.assertIn("int(state.get('health_dataSourcePushedBytes',0))>0",text)

    def test_disc_gate_can_require_actual_client_gpu_output(self):
        text=(ROOT/'scripts/mcp_disc_test.py').read_text(encoding='utf-8')
        self.assertIn('--require-client-gpu',text)
        self.assertIn('"video_deinterlace_mode": args.video_deinterlace_mode',text)
        self.assertIn('Native DVD did not activate Client GPU output',text)
        self.assertIn('"dev_checkpoint_settings"',text)
        self.assertIn('"dev_restore_settings"',text)
        self.assertIn('if settings_checkpoint_active:',text)

if __name__=='__main__': unittest.main()
