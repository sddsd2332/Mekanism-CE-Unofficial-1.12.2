package mekanism.api;

/** Exposes the optimistic-concurrency version used by machine processing plans. */
public interface IProcessingStateVersion {

    /** Returns the current monotonically increasing machine state version. */
    long getProcessingStateVersion();

    /** Alias used by integrations which call the value simply the state version. */
    default long getStateVersion() {
        return getProcessingStateVersion();
    }

    /** Returns whether a snapshot captured at {@code version} is still current. */
    default boolean isProcessingStateCurrent(long version) {
        return getProcessingStateVersion() == version;
    }
}
