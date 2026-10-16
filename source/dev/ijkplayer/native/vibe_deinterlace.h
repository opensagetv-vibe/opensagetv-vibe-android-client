/* OpenSageTV Vibe: LGPL-2.1-or-later native IJK adaptation.
 * Software video only. Keep the legacy audio/filter plumbing and MediaCodec
 * path unchanged; existing LGPL YADIF and first-field interpolation process
 * only marked software fields. Choice0 preserves the qualified adaptive mode.
 */
#include "libavfilter/avfilter.h"
#include "libavfilter/buffersrc.h"
#include "libavfilter/buffersink.h"

typedef struct VibeDeinterlace {
    AVFilterGraph *graph;
    AVFilterContext *source, *sink;
    int width, height, format, serial, draining, fast_active;
} VibeDeinterlace;

static void vibe_deinterlace_close(VibeDeinterlace *filter)
{
    avfilter_graph_free(&filter->graph);
    memset(filter, 0, sizeof(*filter));
}

/* HD software on older TV CPUs must not spend a frame budget in a temporal
 * filter and accumulate seconds of video lag behind audio. For ordinary
 * 8-bit planar 4:2:0 HD, interpolate missing lines of the FIRST field only.
 * This bounded spatial path sacrifices vertical detail, not timestamps or
 * playback speed; no pixel is taken from a field captured at another instant.
 * SD retains YADIF's temporal quality. Hardware/progressive never enter here.
 */
static int vibe_deinterlace_fast_hd(AVFrame *frame)
{
    int ret = av_frame_make_writable(frame);
    if (ret < 0) return ret;
    const int parity = frame->top_field_first ? 0 : 1;
    for (int plane = 0; plane < 3; ++plane) {
        const int width = plane ? (frame->width + 1) / 2 : frame->width;
        const int height = plane ? (frame->height + 1) / 2 : frame->height;
        const int stride = frame->linesize[plane];
        for (int row = 1 - parity; row < height; row += 2) {
            int previous = row - 1, next = row + 1;
            if (previous < 0) previous = next;
            if (next >= height) next = previous;
            uint8_t *dst = frame->data[plane] + row * stride;
            const uint8_t *a = frame->data[plane] + previous * stride;
            const uint8_t *b = frame->data[plane] + next * stride;
            for (int col = 0; col < width; ++col)
                dst[col] = ((unsigned)a[col] + b[col] + 1) >> 1;
        }
    }
    frame->interlaced_frame = 0;
    return 1;
}

/* Return one output frame, zero while awaiting the next temporal neighbour,
 * or a real error. Progressive frames bypass creation completely; after a
 * mixed stream activates the graph, yadif's deint=interlaced preserves them.
 * The frame's original timestamp coordinate is preserved, never replaced by
 * wall time. A new seek serial destroys all old neighbours before accepting
 * new input, preventing pre-seek pictures from leaking into resumed playback.
 */
static int vibe_deinterlace_frame(VibeDeinterlace *filter, AVFrame *frame,
                                 AVRational time_base, AVRational rate,
                                 int serial, int drain, int choice)
{
    int ret;
    filter->fast_active = 0;
    if (!drain && frame->interlaced_frame &&
        (choice == 1 || (choice != 2 && (frame->width > 1280 || frame->height > 720))) &&
        frame->height >= 4 &&
        (frame->format == AV_PIX_FMT_YUV420P || frame->format == AV_PIX_FMT_YUVJ420P) &&
        frame->linesize[0] >= frame->width &&
        frame->linesize[1] >= (frame->width + 1) / 2 &&
        frame->linesize[2] >= (frame->width + 1) / 2 &&
        frame->data[0] && frame->data[1] && frame->data[2]) {
        if (filter->graph) vibe_deinterlace_close(filter);
        ret = vibe_deinterlace_fast_hd(frame);
        filter->fast_active = ret > 0;
        return ret;
    }
    if (filter->graph && (filter->serial != serial || (!drain &&
        (filter->draining ||
        (filter->width != frame->width || filter->height != frame->height ||
         filter->format != frame->format)))))
        vibe_deinterlace_close(filter);
    if (!filter->graph) {
        if (drain) return 0;
        if (!frame->interlaced_frame) return 1;
        // Legacy IJK disables its generic CONFIG_AVFILTER initialization.
        // Register the already linked LGPL filters before looking them up.
        // FFmpeg internally makes this a one-time registration; no decoder or
        // audio pipeline is switched, and progressive/hardware never enter.
        avfilter_register_all();
        const AVFilter *buffer = avfilter_get_by_name("buffer");
        const AVFilter *deinterlacer = avfilter_get_by_name("yadif");
        const AVFilter *sink = avfilter_get_by_name("buffersink");
        if (!buffer || !deinterlacer || !sink) return AVERROR_FILTER_NOT_FOUND;
        char args[256];
        AVRational sar = frame->sample_aspect_ratio;
        if (!sar.num || !sar.den) sar = (AVRational){1, 1};
        snprintf(args, sizeof(args),
                 "video_size=%dx%d:pix_fmt=%d:time_base=%d/%d:pixel_aspect=%d/%d",
                 frame->width, frame->height, frame->format,
                 time_base.num, time_base.den, sar.num, sar.den);
        filter->graph = avfilter_graph_alloc();
        if (!filter->graph) return AVERROR(ENOMEM);
        filter->graph->nb_threads = 2;
        AVFilterContext *yadif = NULL;
        ret = avfilter_graph_create_filter(&filter->source, buffer,
                                          "vibe_input", args, NULL, filter->graph);
        if (ret >= 0) ret = avfilter_graph_create_filter(&yadif, deinterlacer,
                                          "vibe_fields", "mode=send_frame:parity=auto:deint=interlaced",
                                          NULL, filter->graph);
        if (ret >= 0) ret = avfilter_graph_create_filter(&filter->sink, sink,
                                          "vibe_output", NULL, NULL, filter->graph);
        if (ret >= 0) ret = avfilter_link(filter->source, 0, yadif, 0);
        if (ret >= 0) ret = avfilter_link(yadif, 0, filter->sink, 0);
        if (ret >= 0) ret = avfilter_graph_config(filter->graph, NULL);
        if (ret < 0) { vibe_deinterlace_close(filter); return ret; }
        filter->width = frame->width; filter->height = frame->height;
        filter->format = frame->format; filter->serial = serial;
        av_log(NULL, AV_LOG_WARNING, "VIBE IJK software deinterlace active: yadif interlaced-only %dx%d serial=%d\n",
               frame->width, frame->height, serial);
    }
    if (!drain || !filter->draining) {
        ret = av_buffersrc_add_frame_flags(filter->source, drain ? NULL : frame,
                                          AV_BUFFERSRC_FLAG_KEEP_REF);
        if (ret < 0) return ret;
        if (drain) filter->draining = 1;
    }
    av_frame_unref(frame);
    ret = av_buffersink_get_frame(filter->sink, frame);
    if (ret == AVERROR(EAGAIN) || ret == AVERROR_EOF) return 0;
    if (ret < 0) return ret;
    if (frame->pts != AV_NOPTS_VALUE)
        frame->pts = av_rescale_q(frame->pts, av_buffersink_get_time_base(filter->sink), time_base);
    return 1;
}
