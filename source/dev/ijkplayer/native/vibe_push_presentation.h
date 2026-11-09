/* LGPL-2.1-or-later. Actual SDL output is authoritative while paused; an
 * audio-master fallback seek_pos=0 is not a displayed-frame timestamp.
 * No synthetic timer, source seek, new Java ABI, or hardware STC register. */
static void vibe_arm_output_epoch(FFPlayer *ffp, int epoch)
{
    VideoState *is = ffp->is;
    if (!is) return;
    __atomic_store_n(&is->vibe_output_min_serial, is->videoq.serial + 1, __ATOMIC_RELEASE);
    __atomic_store_n(&is->vibe_output_reported_epoch, 0, __ATOMIC_RELEASE);
    __atomic_store_n(&is->vibe_output_serial, 0, __ATOMIC_RELEASE);
    __atomic_store_n(&ffp->vibe_output_epoch, epoch, __ATOMIC_RELEASE);
}

static void vibe_reset_output(FFPlayer *ffp)
{
    if (!ffp->is) return;
    __atomic_store_n(&ffp->is->vibe_output_serial, 0, __ATOMIC_RELEASE);
    __atomic_store_n(&ffp->is->vibe_output_reported_epoch, 0, __ATOMIC_RELEASE);
}

static void vibe_note_output(FFPlayer *ffp, Frame *frame)
{
    VideoState *is = ffp->is;
    int epoch = __atomic_load_n(&ffp->vibe_output_epoch, __ATOMIC_ACQUIRE);
    if (!is || (!ffp->vibe_push_input && !ffp->vibe_hardware_step) || !frame || isnan(frame->pts) ||
            frame->pts < 0 || frame->pts * 1000 >= INT_MAX ||
            frame->serial != is->videoq.serial || frame->serial <
                    __atomic_load_n(&is->vibe_output_min_serial, __ATOMIC_ACQUIRE)) return;
    __atomic_store_n(&is->vibe_output_pts_ms, (int)(frame->pts * 1000), __ATOMIC_RELEASE);
    __atomic_store_n(&is->vibe_output_serial, frame->serial, __ATOMIC_RELEASE);
    if (epoch > 0 && epoch != __atomic_load_n(&is->vibe_output_reported_epoch, __ATOMIC_ACQUIRE)) {
        __atomic_store_n(&is->vibe_output_reported_epoch, epoch, __ATOMIC_RELEASE);
        ffp_notify_msg3(ffp, 12066, epoch, frame->serial);
    }
}

static double vibe_paused_presentation_clock(FFPlayer *ffp, double fallback)
{
    VideoState *is = ffp->is;
    if (!is || (!ffp->vibe_push_input && !ffp->vibe_hardware_step) || !is->pause_req ||
            __atomic_load_n(&is->vibe_output_serial, __ATOMIC_ACQUIRE) != is->videoq.serial)
        return fallback;
    return (double)__atomic_load_n(&is->vibe_output_pts_ms, __ATOMIC_ACQUIRE) / 1000.0;
}
