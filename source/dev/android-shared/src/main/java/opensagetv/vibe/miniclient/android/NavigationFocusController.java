package opensagetv.vibe.miniclient.android;

import android.graphics.Rect;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import java.util.ArrayList;

/** Event-only navigation for the local icon dialog, not playback/STV input. */
final class NavigationFocusController
{
    private NavigationFocusController() { }

    static boolean handle(ViewGroup root, View focused, KeyEvent event)
    {
        int key = event.getKeyCode();
        boolean vertical = key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN;
        boolean horizontal = key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT;
        if (!vertical && !horizontal) return false;
        ArrayList<View> icons = new ArrayList<>();
        collect(root, icons);
        int current = icons.indexOf(focused);
        if (current < 0) return false;
        if (event.getAction() == KeyEvent.ACTION_DOWN)
        {
            int[][] bounds = new int[icons.size()][4];
            for (int index = 0; index < icons.size(); index++)
            {
                View icon = icons.get(index);
                Rect rect = new Rect();
                icon.getDrawingRect(rect);
                root.offsetDescendantRectToMyCoords(icon, rect);
                bounds[index] = new int[]{rect.left, rect.top, rect.right, rect.bottom};
            }
            int next = vertical
                    ? NavigationFocusPolicy.verticalTarget(bounds, current, key == KeyEvent.KEYCODE_DPAD_DOWN)
                    : NavigationFocusPolicy.horizontalTarget(bounds, current, key == KeyEvent.KEYCODE_DPAD_RIGHT);
            if (next != current) icons.get(next).requestFocus();
        }
        // Consume UP as well: Android must not perform a second default move.
        return true;
    }

    private static void collect(ViewGroup root, ArrayList<View> icons)
    {
        for (int index = 0; index < root.getChildCount(); index++)
        {
            View child = root.getChildAt(index);
            if (!child.isShown() || !child.isEnabled()) continue;
            if (child instanceof ViewGroup) collect((ViewGroup) child, icons);
            else if (child.isFocusable()) icons.add(child);
        }
    }
}
