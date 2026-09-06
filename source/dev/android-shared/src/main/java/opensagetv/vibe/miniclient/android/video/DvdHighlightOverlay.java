package opensagetv.vibe.miniclient.android.video;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;

import opensagetv.vibe.miniclient.android.ui.AndroidUIController;

/**
 * Bounded Android rendering for the selected button rectangle supplied by
 * SageTV's server-side DVD VM. The authored menu remains in the MPEG stream;
 * this view only makes the current navigation target visible.
 */
final class DvdHighlightOverlay extends View
{
    private volatile Bitmap bitmap;
    private volatile boolean active;
    private volatile View videoView;

    DvdHighlightOverlay(android.content.Context context)
    {
        super(context);
        setClickable(false);
        setFocusable(false);
        setElevation(110.0f);
    }

    void applySubpicture(Bitmap bitmap, boolean visible)
    {
        this.bitmap = bitmap;
        active = visible && bitmap != null;
        postInvalidate();
    }

    void clear()
    {
        bitmap = null;
        active = false;
        postInvalidate();
    }

    /** Keep authored 720x480/576 SPU coordinates aligned with the video surface. */
    void matchVideoView(View videoView)
    {
        this.videoView = videoView;
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas)
    {
        super.onDraw(canvas);
        Bitmap current = bitmap;
        if (!active || current == null || getWidth() <= 0 || getHeight() <= 0)
            return;
        Rect destination = videoDestinationRect();
        if (destination.width() <= 0 || destination.height() <= 0)
            return;
        canvas.drawBitmap(current, null, destination, null);
    }

    private Rect videoDestinationRect()
    {
        View target = videoView;
        if (target == null || target.getWidth() <= 0 || target.getHeight() <= 0)
            return new Rect(0, 0, getWidth(), getHeight());
        int[] targetLocation = new int[2];
        int[] overlayLocation = new int[2];
        target.getLocationInWindow(targetLocation);
        getLocationInWindow(overlayLocation);
        int left = targetLocation[0] - overlayLocation[0];
        int top = targetLocation[1] - overlayLocation[1];
        return new Rect(left, top, left + target.getWidth(), top + target.getHeight());
    }

    static void attach(final AndroidUIController controller, final DvdHighlightOverlay overlay)
    {
        controller.runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                if (overlay.getParent() != null)
                    return;
                ViewGroup root = (ViewGroup) ((Activity) controller.getContext())
                        .findViewById(android.R.id.content);
                overlay.matchVideoView(controller.getVideoView());
                root.addView(overlay, new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT));
                overlay.bringToFront();
            }
        });
    }

    static void detach(final AndroidUIController controller, final DvdHighlightOverlay overlay)
    {
        if (overlay == null)
            return;
        controller.runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                ViewParent parent = overlay.getParent();
                if (parent instanceof ViewGroup)
                    ((ViewGroup) parent).removeView(overlay);
            }
        });
    }
}
