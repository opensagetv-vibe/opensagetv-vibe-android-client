package opensagetv.vibe.miniclient.android.video;

/** Immutable backend-neutral player/datasource state captured on the UI thread. */
public final class PlaybackHealthSnapshot
{
    private final Object backendPlayer;
    private final Object dataSource;
    private final boolean pushMode;
    private final boolean playerReady;
    private final boolean seekPending;
    private final boolean flushed;
    private final boolean errorState;
    private final int retryCount;

    public PlaybackHealthSnapshot(Object backendPlayer, Object dataSource,
                                  boolean pushMode, boolean playerReady,
                                  boolean seekPending, boolean flushed,
                                  boolean errorState, int retryCount)
    {
        this.backendPlayer = backendPlayer;
        this.dataSource = dataSource;
        this.pushMode = pushMode;
        this.playerReady = playerReady;
        this.seekPending = seekPending;
        this.flushed = flushed;
        this.errorState = errorState;
        this.retryCount = retryCount;
    }

    public Object getBackendPlayer() { return backendPlayer; }
    public Object getDataSource() { return dataSource; }
    public boolean isPushMode() { return pushMode; }
    public boolean isPlayerReady() { return playerReady; }
    public boolean isSeekPending() { return seekPending; }
    public boolean isFlushed() { return flushed; }
    public boolean isErrorState() { return errorState; }
    public int getRetryCount() { return retryCount; }
}
