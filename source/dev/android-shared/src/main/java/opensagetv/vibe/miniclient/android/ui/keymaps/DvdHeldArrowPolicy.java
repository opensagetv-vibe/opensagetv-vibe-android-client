package opensagetv.vibe.miniclient.android.ui.keymaps;

/** Title-only hold timing; authored menus and ordinary video never use it. */
final class DvdHeldArrowPolicy
{
    static final long CHAPTER_HOLD_MS = 1_000L;
    static final long CHAPTER_REPEAT_MS = 1_000L;
    // Give nearby scenes a full second at each speed before coarse searching.
    // 256x fits stock MiniDVD's signed 10.5 rate field; do not invent a seek
    // protocol or report this requested rate as measured source throughput.
    static final long RATE_STEP_MS = 1_000L;
    static final int MAX_RATE = 256;

    static int scanMagnitude(long heldMs)
    {
        int steps = (int) Math.min(7L, Math.max(0L, heldMs) / RATE_STEP_MS);
        return Math.min(MAX_RATE, 2 << steps);
    }

    static boolean chapterDue(long heldMs, long sinceLastChapterMs)
    {
        return heldMs >= CHAPTER_HOLD_MS && sinceLastChapterMs >= CHAPTER_REPEAT_MS;
    }
}
