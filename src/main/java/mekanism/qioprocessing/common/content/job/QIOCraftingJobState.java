package mekanism.qioprocessing.common.content.job;

public enum QIOCraftingJobState {
    QUEUED,
    PLANNING,
    REPLAN_REQUIRED,
    WAITING_MATERIALS,
    WAITING_PROVIDER,
    WAITING_ACCESS,
    WAITING_EXECUTION_SLOT,
    READY,
    RESERVING,
    REPLAN_DRAINING,
    DISPATCHING,
    PROCESSING,
    COLLECTING,
    DELIVERING,
    COMPLETED,
    CANCEL_REQUESTED,
    RETURNING,
    RETURN_BLOCKED,
    PROVIDER_LOST,
    OPERATION_CONTAMINATED,
    ORPHANED_FREQUENCY,
    FAILED,
    CANCELLED;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }
}
