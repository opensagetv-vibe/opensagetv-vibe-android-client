package opensagetv.vibe.miniclient.android;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import java.lang.ref.WeakReference;

import opensagetv.vibe.miniclient.BackgroundSessionState;

/** Application-wide visibility grace period and bounded background session state. */
public final class BackgroundSessionOwner implements Application.ActivityLifecycleCallbacks
{
    public interface Listener
    {
        void onApplicationBackgroundCandidate();
        void onApplicationBackgroundCandidateCancelled();
        void onApplicationBackgrounded();
        void onApplicationForegrounded();
        void onBackgroundSessionTimedOut();
    }

    private static final long TRANSITION_GRACE_MS = 350L;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final BackgroundSessionState sessionState = new BackgroundSessionState();
    private WeakReference<Listener> listener = new WeakReference<Listener>(null);
    private int startedActivities;
    private long visibilityGeneration;
    private Runnable pendingBackground;
    private Runnable pendingSessionTimeout;
    private boolean background;
    private long sessionTimeoutMs = 300000L;
    private long sessionTimeoutDeadlineMonotonicMs = -1L;
    private long sessionTimeoutCount;

    public synchronized void setSessionTimeoutMs(long timeoutMs)
    {
        sessionTimeoutMs = Math.max(0L, Math.min(timeoutMs, 86400000L));
    }

    public void setListener(Listener next)
    {
        listener = new WeakReference<Listener>(next);
    }

    public void clearListener(Listener expected)
    {
        if (listener.get() == expected)
            listener = new WeakReference<Listener>(null);
    }

    public BackgroundSessionState state()
    {
        return sessionState;
    }

    public boolean hasPendingPreservedSession()
    {
        BackgroundSessionState.State current = sessionState.getState();
        return current == BackgroundSessionState.State.BACKGROUND_APP_PAUSED
                || current == BackgroundSessionState.State.BACKGROUND_USER_PAUSED;
    }

    public synchronized String compactWire()
    {
        return sessionState.compactWire()
                + ";appStartedActivityCount=" + startedActivities
                + ";appBackground=" + background
                + ";backgroundSessionTimeoutMs=" + sessionTimeoutMs
                + ";backgroundSessionTimeoutDeadlineMonotonicMs=" + sessionTimeoutDeadlineMonotonicMs
                + ";backgroundSessionTimeoutCount=" + sessionTimeoutCount;
    }

    @Override public void onActivityCreated(Activity activity, Bundle state) { }

    @Override
    public synchronized void onActivityStarted(Activity activity)
    {
        startedActivities++;
        visibilityGeneration++;
        cancelPendingLocked();
        final Listener transitionListener = listener.get();
        if (transitionListener != null)
            transitionListener.onApplicationBackgroundCandidateCancelled();
        if (background)
        {
            background = false;
            final Listener current = listener.get();
            if (current != null)
                mainHandler.post(new Runnable() { @Override public void run() { current.onApplicationForegrounded(); } });
        }
    }

    @Override public void onActivityResumed(Activity activity) { }
    @Override
    public void onActivityPaused(Activity activity)
    {
        Listener current = listener.get();
        if (current != null)
            current.onApplicationBackgroundCandidate();
    }

    @Override
    public synchronized void onActivityStopped(Activity activity)
    {
        startedActivities = Math.max(0, startedActivities - 1);
        if (startedActivities != 0 || activity.isChangingConfigurations())
            return;
        final long generation = ++visibilityGeneration;
        pendingBackground = new Runnable()
        {
            @Override public void run()
            {
                Listener current;
                synchronized (BackgroundSessionOwner.this)
                {
                    if (generation != visibilityGeneration || startedActivities != 0)
                        return;
                    pendingBackground = null;
                    background = true;
                    current = listener.get();
                }
                if (current != null)
                    current.onApplicationBackgrounded();
                synchronized (BackgroundSessionOwner.this)
                {
                    if (generation == visibilityGeneration && background && sessionTimeoutMs > 0L)
                        scheduleSessionTimeoutLocked(generation);
                }
            }
        };
        mainHandler.postDelayed(pendingBackground, TRANSITION_GRACE_MS);
    }

    @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) { }
    @Override public void onActivityDestroyed(Activity activity) { }

    private void cancelPendingLocked()
    {
        if (pendingBackground != null)
        {
            mainHandler.removeCallbacks(pendingBackground);
            pendingBackground = null;
        }
        if (pendingSessionTimeout != null)
        {
            mainHandler.removeCallbacks(pendingSessionTimeout);
            pendingSessionTimeout = null;
        }
        sessionTimeoutDeadlineMonotonicMs = -1L;
    }

    private void scheduleSessionTimeoutLocked(final long generation)
    {
        if (pendingSessionTimeout != null)
            mainHandler.removeCallbacks(pendingSessionTimeout);
        sessionTimeoutDeadlineMonotonicMs = System.nanoTime() / 1000000L + sessionTimeoutMs;
        pendingSessionTimeout = new Runnable()
        {
            @Override public void run()
            {
                Listener current;
                synchronized (BackgroundSessionOwner.this)
                {
                    if (generation != visibilityGeneration || !background || startedActivities != 0)
                        return;
                    pendingSessionTimeout = null;
                    sessionTimeoutDeadlineMonotonicMs = -1L;
                    sessionTimeoutCount++;
                    current = listener.get();
                }
                if (current != null)
                    current.onBackgroundSessionTimedOut();
            }
        };
        mainHandler.postDelayed(pendingSessionTimeout, sessionTimeoutMs);
    }
}
