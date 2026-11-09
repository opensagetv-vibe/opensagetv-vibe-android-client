package opensagetv.vibe.miniclient.video;

import java.util.WeakHashMap;

/** Window-scoped ownership of an optional preferred display mode, including 0=Auto. */
public final class DisplayModeRestorePolicy
{
    private static final class Lease
    {
        final int previous;
        final int applied;
        Lease(int previous, int applied) { this.previous = previous; this.applied = applied; }
    }

    // Never retain an Activity/Window after Android destroys it.
    private final WeakHashMap<Object, Lease> leases = new WeakHashMap<Object, Lease>();

    public synchronized void applying(Object window, int previousPreference, int target)
    {
        if (window == null) throw new IllegalArgumentException("window required");
        Lease previous = leases.get(window);
        int original = previous != null && previous.applied == previousPreference
                ? previous.previous : previousPreference;
        leases.put(window, new Lease(original, target));
    }

    /** Null means no owned value may be overwritten; a boxed zero restores Auto. */
    public synchronized Integer release(Object window, int currentPreference)
    {
        Lease lease = leases.remove(window);
        return lease != null && lease.applied == currentPreference ? lease.previous : null;
    }
}
