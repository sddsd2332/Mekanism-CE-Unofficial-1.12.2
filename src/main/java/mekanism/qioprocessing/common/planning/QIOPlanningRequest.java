package mekanism.qioprocessing.common.planning;

import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.QIOProcessingNbt;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.UUID;

/** Immutable request passed to a QIO planning worker. */
/**
 * QIO 处理模块中的 QIOPlanningRequest 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIOPlanningRequest {

    private final UUID planId;
    private final int planRevision;
    private final QIOPlanningSnapshot snapshot;
    private final PortableResourceDescriptor rootResource;
    private final long rootAmount;

    /** 创建一次带快照和根产物需求的规划请求。 */
    public QIOPlanningRequest(@Nonnull UUID planId, int planRevision,
          @Nonnull QIOPlanningSnapshot snapshot,
          @Nonnull PortableResourceDescriptor rootResource, long rootAmount) {
        this.planId = Objects.requireNonNull(planId, "planId");
        this.planRevision = QIOProcessingNbt.requirePositive(planRevision, "planRevision");
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
        this.rootResource = Objects.requireNonNull(rootResource, "rootResource");
        if (rootAmount <= 0) {
            throw new IllegalArgumentException("rootAmount must be positive");
        }
        this.rootAmount = rootAmount;
    }

    @Nonnull
    /** 返回规划请求标识。 */
    public UUID getPlanId() {
        return planId;
    }

    /** 返回计划版本。 */
    public int getPlanRevision() {
        return planRevision;
    }

    @Nonnull
    /** 返回规划使用的只读网络快照。 */
    public QIOPlanningSnapshot getSnapshot() {
        return snapshot;
    }

    @Nonnull
    /** 返回根产物资源身份。 */
    public PortableResourceDescriptor getRootResource() {
        return rootResource;
    }

    /** 返回根产物需求数量。 */
    public long getRootAmount() {
        return rootAmount;
    }
}
