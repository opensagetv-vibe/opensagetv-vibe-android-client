package opensagetv.vibe.miniclient;

/**
 * Minimal application-event boundary shared by the platform front ends.
 * <p/>
 * Created by seans on 05/12/15.
 */
public interface IBus {
    void register(Object object);

    void unregister(Object object);

    void post(Object event);
}
