package mekanism.qioprocessing.common.content.job;

/**
 * QIO 处理模块中的 QIOCraftingJobState 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
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
