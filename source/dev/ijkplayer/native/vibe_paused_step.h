/* LGPL-2.1-or-later. Client-owned forward Push step; no producer seek.
 * Included after FFplay's clock/pause helpers. All cross-thread scalar controls
 * are atomic ints; no new mutex lifetime, native exports or JNI Java ABI. */
static void vibe_step_reset(FFPlayer *ffp)
{
    VideoState *is = ffp->is;
    if (!is) return;
    vibe_reset_output(ffp);
    __atomic_store_n(&is->vibe_step_request, 0, __ATOMIC_RELEASE);
    __atomic_store_n(&is->vibe_step_presented_ms, 0, __ATOMIC_RELEASE);
    __atomic_store_n(&is->vibe_step_audio_floor_ms, 0, __ATOMIC_RELEASE);
    __atomic_store_n(&is->vibe_step_discard_pcm, 0, __ATOMIC_RELEASE);
}

static void vibe_step_request(FFPlayer *ffp, int request)
{
    VideoState *is = ffp->is;
    if (!is) return;
    int software = ffp->vibe_push_input && ffp->vibe_raw_cea &&
            !ffp->mediacodec_all_videos && !ffp->mediacodec_mpeg2 &&
            !ffp->mediacodec_avc && !ffp->mediacodec_hevc;
    /* A setting is not decoder proof: a hardware trial must actually have
     * opened the MediaCodec pipeline, never its silent software fallback. */
    int hardware = ffp->vibe_hardware_step &&
            ffp->stat.vdec_type == FFP_PROPV_DECODER_MEDIACODEC;
    if (!software && !hardware) return;
    SDL_LockMutex(is->play_mutex);
    if (request == 0) {
        __atomic_store_n(&is->vibe_step_request, 0, __ATOMIC_RELEASE);
        is->step = 0;
        stream_update_pause_l(ffp);
        SDL_UnlockMutex(is->play_mutex);
        return;
    }
    Frame *shown = frame_queue_peek_last(&is->pictq);
    if (is->pause_req && is->paused && is->video_st && !is->seek_req &&
            !__atomic_load_n(&is->vibe_step_request, __ATOMIC_ACQUIRE) &&
            is->pictq.rindex_shown && shown && !isnan(shown->pts) &&
            shown->pts > 0 && shown->pts * 1000 < INT_MAX &&
            shown->serial == is->videoq.serial) {
        is->vibe_step_serial = shown->serial;
        is->vibe_step_start_ms = (int)(shown->pts * 1000);
        __atomic_store_n(&is->vibe_step_request, request, __ATOMIC_RELEASE);
        step_to_next_frame_l(ffp); /* Audio remains paused during FFplay step. */
    }
    SDL_UnlockMutex(is->play_mutex);
}

static void vibe_step_presented(FFPlayer *ffp, Frame *frame)
{
    vibe_note_output(ffp, frame);
    VideoState *is = ffp->is;
    if (!is || !frame || isnan(frame->pts) || frame->pts * 1000 >= INT_MAX)
        return;
    int request = __atomic_load_n(&is->vibe_step_request, __ATOMIC_ACQUIRE);
    int ms = (int)(frame->pts * 1000);
    if (!request || !is->pause_req || frame->serial != is->videoq.serial ||
            frame->serial != is->vibe_step_serial || ms <= is->vibe_step_start_ms)
        return; /* Redrawing the old paused frame is not successful stepping. */
    if (!__atomic_compare_exchange_n(&is->vibe_step_request, &request, 0, 0,
            __ATOMIC_ACQ_REL, __ATOMIC_ACQUIRE)) return;
    __atomic_store_n(&is->vibe_step_presented_ms, ms, __ATOMIC_RELEASE);
    __atomic_store_n(&is->vibe_step_audio_floor_ms, ms, __ATOMIC_RELEASE);
    __atomic_store_n(&is->vibe_step_discard_pcm, 1, __ATOMIC_RELEASE);
    SDL_AoutFlushAudio(ffp->aout); /* Never replay already-submitted old PCM. */
    ffp_notify_msg3(ffp, 12065, request, frame->serial);
}
