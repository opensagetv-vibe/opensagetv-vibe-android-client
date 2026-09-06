package opensagetv.vibe.miniclient;

import java.util.concurrent.CopyOnWriteArrayList;

/** Thread-safe registration-order listener dispatch without reflection. */
public final class OrderedListenerRegistry<T>
{
    public interface Dispatcher<T>
    {
        void dispatch(T listener);
    }

    private final CopyOnWriteArrayList<T> listeners = new CopyOnWriteArrayList<T>();

    public void register(T listener)
    {
        if (listener == null)
            throw new IllegalArgumentException("listener must not be null");
        listeners.addIfAbsent(listener);
    }

    public void unregister(T listener)
    {
        if (listener != null)
            listeners.remove(listener);
    }

    public void dispatch(Dispatcher<T> dispatcher)
    {
        if (dispatcher == null)
            throw new IllegalArgumentException("dispatcher must not be null");
        for (T listener : listeners)
            dispatcher.dispatch(listener);
    }

    public int size()
    {
        return listeners.size();
    }
}
