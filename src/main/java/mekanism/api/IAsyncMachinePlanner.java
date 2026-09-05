package mekanism.api;

/**
 * Explicit three phase contract for machine work which is safe to calculate off the
 * server thread.
 *
 * <p>{@link #captureSnapshot()} and {@link #commitPlan(Object, Object)} are called on
 * the server thread. {@link #calculatePlan(Object)} is called by a worker and must be
 * a pure function of the captured value. A planner must never retain or expose a
 * TileEntity, World, capability handler, container, registry map, or callback to live
 * machine state in its snapshot or plan.</p>
 */
public interface IAsyncMachinePlanner<SNAPSHOT, PLAN> {

    /** Captures a short lived, defensive snapshot on the server thread. */
    SNAPSHOT captureSnapshot();

    /** Calculates a plan from the snapshot on a worker thread. */
    PLAN calculatePlan(SNAPSHOT snapshot);

    /**
     * Returns the detached function that the scheduler may invoke on a worker.
     * Returning {@code null} keeps this planner entirely on the server thread.
     */
    default IAsyncPlanCalculator<SNAPSHOT, PLAN> getAsyncPlanCalculator() {
        return null;
    }

    /** Applies a previously calculated plan on the server thread. */
    void commitPlan(SNAPSHOT snapshot, PLAN plan);

    /**
     * Optional final validation hook. It is invoked on the server thread immediately
     * before commit, after the scheduler has checked the machine and recipe versions.
     */
    default boolean isPlanStillValid(SNAPSHOT snapshot, PLAN plan) {
        return true;
    }

    /** Called on the server thread when a plan is discarded or calculation fails. */
    default void onPlanDiscarded(SNAPSHOT snapshot, PLAN plan, Throwable cause) {
    }
}
