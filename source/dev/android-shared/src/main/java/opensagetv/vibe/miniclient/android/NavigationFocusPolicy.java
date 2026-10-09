package opensagetv.vibe.miniclient.android;

/** Sparse-icon focus: prefer the same axis, then the nearest icon ahead. */
final class NavigationFocusPolicy
{
    private NavigationFocusPolicy() { }

    // Bounds are left/top/right/bottom; callers omit hidden, disabled and
    // non-focusable views. This pure selector also covers nested grid layouts.
    static int verticalTarget(int[][] bounds, int current, boolean down)
    { return target(bounds, current, down, 1, 3, 0, 2); }

    static int horizontalTarget(int[][] bounds, int current, boolean right)
    { return target(bounds, current, right, 0, 2, 1, 3); }

    private static int target(int[][] bounds, int current, boolean forward,
                              int start, int end, int crossStart, int crossEnd)
    {
        int[] from = bounds[current];
        long fromAxis = (long) from[start] + from[end];
        long fromCross = (long) from[crossStart] + from[crossEnd];
        int best = current;
        long bestDistance = Long.MAX_VALUE, bestCross = Long.MAX_VALUE;
        for (int index = 0; index < bounds.length; index++)
        {
            if (index == current) continue;
            int[] to = bounds[index];
            long distance = ((long) to[start] + to[end] - fromAxis) * (forward ? 1 : -1);
            // A movement beam must overlap; adjacent-row/column edges touching
            // are not overlap. First preserve the requested row/column.
            if (distance <= 0 || to[crossEnd] <= from[crossStart]
                    || to[crossStart] >= from[crossEnd]) continue;
            long distanceCross = Math.abs((long) to[crossStart] + to[crossEnd] - fromCross);
            if (distance < bestDistance || (distance == bestDistance && distanceCross < bestCross))
            { best = index; bestDistance = distance; bestCross = distanceCross; }
        }
        if (best != current) return best;
        // Isolated columns/rows must still be reachable. Only if the beam is
        // empty, choose the geometrically closest icon in the requested half-
        // plane. Never wrap backwards or activate a button while moving focus.
        long bestSquared = Long.MAX_VALUE;
        for (int index = 0; index < bounds.length; index++)
        {
            if (index == current) continue;
            int[] to = bounds[index];
            long distance = ((long) to[start] + to[end] - fromAxis) * (forward ? 1 : -1);
            if (distance <= 0) continue;
            long cross = (long) to[crossStart] + to[crossEnd] - fromCross;
            long squared = distance * distance + cross * cross;
            if (squared < bestSquared)
            { best = index; bestSquared = squared; }
        }
        return best;
    }
}
