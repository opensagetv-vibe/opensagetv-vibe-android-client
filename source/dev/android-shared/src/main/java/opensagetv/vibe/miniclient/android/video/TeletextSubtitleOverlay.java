package opensagetv.vibe.miniclient.android.video;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.TextView;

import opensagetv.vibe.miniclient.android.ui.AndroidUIController;
import opensagetv.vibe.miniclient.prefs.PrefStore;

/** Focus-free local renderer used for explicitly selected Teletext services. */
final class TeletextSubtitleOverlay extends TextView
{
    TeletextSubtitleOverlay(android.content.Context context)
    {
        super(context);
        setClickable(false);
        setFocusable(false);
        setGravity(Gravity.CENTER);
        setTextColor(Color.WHITE);
        setTypeface(Typeface.MONOSPACE);
        setElevation(115.0f);
        setPadding(dp(12), dp(4), dp(12), dp(4));
    }

    void applyPresentation(PrefStore prefs)
    {
        int scale = TextSubtitlePresentation.textScalePercent(prefs);
        setTextSize(22.0f * scale / 100.0f);
        String style = TextSubtitlePresentation.style(prefs);
        if (TextSubtitlePresentation.STYLE_BLACK_BOX.equals(style))
        {
            setBackgroundColor(0xcc000000);
            setShadowLayer(0, 0, 0, Color.TRANSPARENT);
        }
        else
        {
            setBackgroundColor(Color.TRANSPARENT);
            setShadowLayer(TextSubtitlePresentation.STYLE_OUTLINE.equals(style) ? 4f : 2f,
                    0f, 0f, Color.BLACK);
        }
    }

    static void attach(final AndroidUIController controller,
            final TeletextSubtitleOverlay overlay)
    {
        controller.runOnUiThread(new Runnable()
        {
            @Override public void run()
            {
                if (overlay.getParent() != null) return;
                ViewGroup root = (ViewGroup) ((Activity) controller.getContext())
                        .findViewById(android.R.id.content);
                int safe = TextSubtitlePresentation.safeAreaPercent(
                        controller.getClient().properties());
                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
                params.leftMargin = dp(controller, 24);
                params.rightMargin = dp(controller, 24);
                params.bottomMargin = Math.max(dp(controller, 36),
                        root.getHeight() * safe / 100);
                overlay.applyPresentation(controller.getClient().properties());
                root.addView(overlay, params);
                overlay.bringToFront();
            }
        });
    }

    static void detach(final AndroidUIController controller,
            final TeletextSubtitleOverlay overlay)
    {
        if (overlay == null) return;
        controller.runOnUiThread(new Runnable()
        {
            @Override public void run()
            {
                ViewParent parent = overlay.getParent();
                if (parent instanceof ViewGroup) ((ViewGroup) parent).removeView(overlay);
            }
        });
    }

    private int dp(int value) { return dpValue(getResources().getDisplayMetrics().density, value); }

    private static int dp(AndroidUIController controller, int value)
    {
        return dpValue(controller.getContext().getResources().getDisplayMetrics().density, value);
    }

    private static int dpValue(float density, int value)
    {
        return Math.max(1, Math.round(value * density));
    }
}
