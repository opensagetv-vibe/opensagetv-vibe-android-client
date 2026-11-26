package opensagetv.vibe.miniclient.android.tv.debug;

import java.io.IOException;
import java.lang.reflect.Field;

/** One explicit fault on the player's playback thread, absent from release builds. */
final class PushParserRecoveryFault
{
    private PushParserRecoveryFault() {}

    static void inject(Object backend)
    {
        try
        {
            for (int i = 0; i < 4; i++)
            {
                Object delegate = read(backend, "delegate");
                if (delegate == null) break;
                backend = delegate;
            }
            if (!Boolean.TRUE.equals(read(backend, "pushMode"))
                    || Boolean.TRUE.equals(read(backend, "dvdPushMode")))
                throw new IllegalStateException("fault requires ordinary Push, not DVD/Pull");
            Object player = read(backend, "player");
            if (player instanceof com.google.android.exoplayer2.ExoPlayer)
            {
                ((com.google.android.exoplayer2.ExoPlayer) player).createMessage((type, payload) -> {
                    throw com.google.android.exoplayer2.ExoPlaybackException.createForSource(
                            new IOException("ISSUE-004 explicit test fault"),
                            com.google.android.exoplayer2.PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED);
                }).send();
            }
            else if (player instanceof androidx.media3.exoplayer.ExoPlayer)
            {
                ((androidx.media3.exoplayer.ExoPlayer) player).createMessage((type, payload) -> {
                    throw androidx.media3.exoplayer.ExoPlaybackException.createForSource(
                            new IOException("ISSUE-004 explicit test fault"),
                            androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED);
                }).send();
            }
            else throw new IllegalStateException("fault requires an active Exo-based player");
        }
        catch (ReflectiveOperationException error)
        {
            throw new IllegalStateException("active player cannot be inspected", error);
        }
    }

    private static Object read(Object target, String name) throws IllegalAccessException
    {
        if (target == null) return null;
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass())
        {
            try
            {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            }
            catch (NoSuchFieldException ignored) {}
        }
        return null;
    }
}
