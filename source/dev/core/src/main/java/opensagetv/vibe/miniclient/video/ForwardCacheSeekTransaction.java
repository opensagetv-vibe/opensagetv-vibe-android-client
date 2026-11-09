package opensagetv.vibe.miniclient.video;

/** Selected decoder queues change together, or restore their exact read indices. */
public final class ForwardCacheSeekTransaction
{
    private ForwardCacheSeekTransaction() { }

    public interface Queue
    {
        boolean selected();
        boolean required();
        int readIndex();
        boolean seek(long positionUs);
        boolean restore(int readIndex);
    }

    public static boolean seek(Queue[] queues, long positionUs)
    {
        int[] previous = new int[queues.length];
        boolean hasRequired = false;
        for (int i = 0; i < queues.length; i++) previous[i] = queues[i].readIndex();
        for (Queue queue : queues) hasRequired |= queue.selected() && queue.required();
        if (!hasRequired) return false;
        for (int i = 0; i < queues.length; i++)
        {
            Queue queue = queues[i];
            if (!queue.selected()) continue;
            if (!queue.seek(positionUs))
            {
                if (!queue.required())
                {
                    // Sparse subtitles need not cover every A/V timestamp.
                    // Failed SampleQueue.seekTo can rewind even on false;
                    // retain the old cursor rather than replay stale cues.
                    if (queue.readIndex() != previous[i] && !queue.restore(previous[i]))
                        throw new IllegalStateException("Decoder cache lost a sparse retained read index");
                    continue;
                }
                for (int j = 0; j <= i; j++)
                    if (queues[j].selected() && queues[j].readIndex() != previous[j]
                            && !queues[j].restore(previous[j]))
                        throw new IllegalStateException("Decoder cache lost a retained read index");
                return false;
            }
        }
        return true;
    }

    public static boolean containsForwardTarget(long currentUs, long targetUs, long bufferedUs)
    {
        return currentUs >= 0 && targetUs >= currentUs && bufferedUs >= targetUs;
    }
}
