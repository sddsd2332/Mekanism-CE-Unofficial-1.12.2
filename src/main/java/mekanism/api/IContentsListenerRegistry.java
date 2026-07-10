package mekanism.api;

/**
 * Exposes dynamic registration of additional content change listeners.
 * Registering a listener does not immediately report the current contents.
 * Callbacks may originate from a machine processing thread, so listeners must not directly perform work that is restricted to the server thread.
 */
public interface IContentsListenerRegistry {

    /**
     * Adds an additional listener.
     *
     * @return {@code true} if the listener was added
     */
    boolean addContentsListener(IContentsListener listener);

    /**
     * Removes an additional listener. The same listener instance used during registration must be supplied.
     *
     * @return {@code true} if the listener was removed
     */
    boolean removeContentsListener(IContentsListener listener);
}
