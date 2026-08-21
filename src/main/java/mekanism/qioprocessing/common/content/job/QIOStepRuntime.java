package mekanism.qioprocessing.common.content.job;

import mekanism.qioprocessing.common.content.QIOProcessingDataException;
import mekanism.qioprocessing.common.content.QIOProcessingNbt;
import mekanism.qioprocessing.common.content.plan.QIOPlanStep;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Durable progress for one immutable plan node. */
/**
 * QIO 处理模块中的 QIOStepRuntime 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIOStepRuntime {

    private final long nodeId;
    private final long requiredOperations;
    private long completedOperations;
    private long failedAttempts;
    private long runtimeRevision;
    private long activeOperationCount;
    private final Map<UUID, QIOOperationAssignment> activeOperations = new LinkedHashMap<>();

    /** 创建计划步骤的运行时计数器。 */
    public QIOStepRuntime(long nodeId, long requiredOperations) {
        this.nodeId = QIOProcessingNbt.requireNonNegative(nodeId, "nodeId");
        if (requiredOperations <= 0) {
            throw new IllegalArgumentException("QIO step operation count must be positive");
        }
        this.requiredOperations = requiredOperations;
    }

    @Nonnull
    /** 从计划步骤创建对应运行时。 */
    public static QIOStepRuntime create(@Nonnull QIOPlanStep step) {
        return new QIOStepRuntime(step.getNodeId(), step.getOperations());
    }

    /** 返回计划节点标识。 */
    public long getNodeId() {
        return nodeId;
    }

    /** 返回所需总批次数。 */
    public long getRequiredOperations() {
        return requiredOperations;
    }

    /** 返回已完成批次数。 */
    public long getCompletedOperations() {
        return completedOperations;
    }

    /** 返回失败尝试次数。 */
    public long getFailedAttempts() {
        return failedAttempts;
    }

    /** 返回运行时版本号。 */
    public long getRuntimeRevision() {
        return runtimeRevision;
    }

    /** 返回尚未完成批次数。 */
    public long getRemainingOperations() {
        return requiredOperations - completedOperations - activeOperationCount;
    }

    /** 判断步骤是否已完成且没有活动操作。 */
    public boolean isComplete() {
        return completedOperations == requiredOperations && activeOperations.isEmpty();
    }

    /** 判断是否存在活动操作分配。 */
    public boolean hasActiveAssignments() {
        return !activeOperations.isEmpty();
    }

    /** 返回活动分配数量。 */
    public int getActiveAssignmentCount() {
        return activeOperations.size();
    }

    /** Number of real recipe operations represented by all active lane assignments. */
    /** 返回活动分配承载的批次数。 */
    public long getActiveOperationCount() {
        return activeOperationCount;
    }

    @Nonnull
    /** 返回活动操作的只读视图。 */
    public Map<UUID, QIOOperationAssignment> getActiveOperations() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(activeOperations));
    }

    /** Returns a stable prefix without copying assignments that cannot be visited this tick. */
    @Nonnull
    /** 按派发顺序返回不超过上限的活动操作。 */
    public List<QIOOperationAssignment> getActiveOperations(int maximum) {
        if (maximum < 0) {
            throw new IllegalArgumentException("Maximum active operation snapshot is negative");
        }
        List<QIOOperationAssignment> operations = new ArrayList<>(
              Math.min(maximum, activeOperations.size()));
        for (QIOOperationAssignment assignment : activeOperations.values()) {
            if (operations.size() >= maximum) {
                break;
            }
            operations.add(assignment);
        }
        return Collections.unmodifiableList(operations);
    }

    @Nullable
    /** 按操作标识取得活动分配。 */
    public QIOOperationAssignment getOperation(UUID operationId) {
        return operationId == null ? null : activeOperations.get(operationId);
    }

    /** 注册一个新的活动操作分配。 */
    public void start(@Nonnull QIOOperationAssignment assignment) {
        Objects.requireNonNull(assignment, "assignment");
        long nextActive = Math.addExact(activeOperationCount, assignment.getOperationCount());
        if (nextActive > requiredOperations - completedOperations ||
              activeOperations.putIfAbsent(assignment.getOperationId(), assignment) != null) {
            throw new IllegalStateException("QIO step cannot accept another operation");
        }
        activeOperationCount = nextActive;
        incrementRevision();
    }

    /** 更新指定活动操作的状态和进度。 */
    public boolean update(@Nonnull UUID operationId, @Nonnull QIOOperationAssignment.State state,
          long currentTick, long totalTicks, @Nullable String diagnostic) {
        QIOOperationAssignment assignment = requireOperation(operationId);
        if (!assignment.update(state, currentTick, totalTicks, diagnostic)) {
            return false;
        }
        incrementRevision();
        return true;
    }

    /** 结算指定操作并累计完成批次。 */
    public void complete(@Nonnull UUID operationId) {
        QIOOperationAssignment assignment = requireOperation(operationId);
        long nextCompleted = Math.addExact(completedOperations, assignment.getOperationCount());
        if (nextCompleted > requiredOperations) {
            throw new IllegalStateException("QIO step completed more operations than planned");
        }
        activeOperations.remove(operationId);
        activeOperationCount = Math.subtractExact(activeOperationCount,
              assignment.getOperationCount());
        completedOperations = nextCompleted;
        incrementRevision();
    }

    /** 记录指定操作失败及诊断，同时增加失败次数。 */
    public void fail(@Nonnull UUID operationId, @Nonnull String diagnostic) {
        QIOOperationAssignment assignment = requireOperation(operationId);
        assignment.update(QIOOperationAssignment.State.FAILED, assignment.getCurrentTick(),
              assignment.getTotalTicks(), diagnostic);
        activeOperations.remove(operationId);
        activeOperationCount = Math.subtractExact(activeOperationCount,
              assignment.getOperationCount());
        failedAttempts = Math.addExact(failedAttempts, 1);
        incrementRevision();
    }

    @Nonnull
    /** 将步骤运行时和活动分配写入 NBT。 */
    public NBTTagCompound write() {
        NBTTagCompound data = new NBTTagCompound();
        data.setLong("nodeId", nodeId);
        data.setLong("requiredOperations", requiredOperations);
        data.setLong("completedOperations", completedOperations);
        data.setLong("failedAttempts", failedAttempts);
        data.setLong("runtimeRevision", runtimeRevision);
        NBTTagList active = new NBTTagList();
        List<QIOOperationAssignment> ordered = new ArrayList<>(activeOperations.values());
        ordered.sort((left, right) -> left.getOperationId().compareTo(right.getOperationId()));
        ordered.forEach(assignment -> active.appendTag(assignment.write()));
        data.setTag("activeOperations", active);
        return data;
    }

    @Nonnull
    /** 从 NBT 读取并校验步骤运行时。 */
    public static QIOStepRuntime read(@Nonnull NBTTagCompound data)
          throws QIOProcessingDataException {
        try {
            QIOStepRuntime runtime = new QIOStepRuntime(data.getLong("nodeId"),
                  data.getLong("requiredOperations"));
            runtime.completedOperations = QIOProcessingNbt.requireNonNegative(
                  data.getLong("completedOperations"), "completedOperations");
            runtime.failedAttempts = QIOProcessingNbt.requireNonNegative(
                  data.getLong("failedAttempts"), "failedAttempts");
            runtime.runtimeRevision = QIOProcessingNbt.requireNonNegative(
                  data.getLong("runtimeRevision"), "runtimeRevision");
            NBTTagList active = data.getTagList("activeOperations", NBT.TAG_COMPOUND);
            for (int index = 0; index < active.tagCount(); index++) {
                QIOOperationAssignment assignment = QIOOperationAssignment.read(
                      active.getCompoundTagAt(index));
                if (runtime.activeOperations.put(assignment.getOperationId(), assignment) != null) {
                    throw new QIOProcessingDataException("Duplicate QIO step operation assignment");
                }
            }
            long activeOperations = 0;
            for (QIOOperationAssignment assignment : runtime.activeOperations.values()) {
                activeOperations = Math.addExact(activeOperations,
                      assignment.getOperationCount());
            }
            if (runtime.completedOperations > runtime.requiredOperations ||
                  activeOperations > runtime.requiredOperations - runtime.completedOperations) {
                throw new QIOProcessingDataException("QIO step progress exceeds its plan");
            }
            runtime.activeOperationCount = activeOperations;
            return runtime;
        } catch (QIOProcessingDataException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new QIOProcessingDataException("Invalid QIO step runtime", e);
        }
    }

    private QIOOperationAssignment requireOperation(UUID operationId) {
        QIOOperationAssignment assignment = activeOperations.get(
              Objects.requireNonNull(operationId, "operationId"));
        if (assignment == null) {
            throw new IllegalArgumentException("Unknown QIO step operation " + operationId);
        }
        return assignment;
    }

    private void incrementRevision() {
        if (runtimeRevision == Long.MAX_VALUE) {
            throw new IllegalStateException("QIO step runtime revision exhausted");
        }
        runtimeRevision++;
    }
}
