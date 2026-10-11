package mekanism.qioprocessing.common.planning;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * A pure calculation yields a bounded main-thread operation instead of blocking its worker.
 * submit runs on the executor's owner thread; false means backpressure and is retried later.
 * The task keeps its admission slot, cancellation identity and original timeout while suspended.
 */
public abstract class QIOPlanningContinuation extends RuntimeException {
    protected QIOPlanningContinuation(String diagnostic) {
        super(diagnostic, null, false, false);
    }

    public abstract boolean submit(UUID taskId, Runnable resume, Consumer<Throwable> failure);

    /**
     * Must be nonblocking and thread-safe, including watchdog cancellation.
     */
    public abstract void cancel(UUID taskId);
}
