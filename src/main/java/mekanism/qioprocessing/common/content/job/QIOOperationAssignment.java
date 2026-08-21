package mekanism.qioprocessing.common.content.job;

import mekanism.qioprocessing.common.content.QIOProcessingDataException;
import mekanism.qioprocessing.common.content.QIOProcessingNbt;
import mekanism.qioprocessing.common.content.plan.QIOPlanStep.ProviderKind;
import net.minecraft.nbt.NBTTagCompound;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;
import java.util.UUID;

/** Persistent assignment of one real recipe operation to one physical provider lane. */
/**
 * QIO 处理模块中的 QIOOperationAssignment 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIOOperationAssignment {

    private static final int SCHEMA_VERSION = 2;

    public enum State {
        LOADING,
        PROCESSING,
        COLLECTING,
        OUTPUT_BLOCKED,
        FAILED
    }

    private final UUID operationId;
    private final ProviderKind providerKind;
    private final UUID deviceUUID;
    private final long laneId;
    private final long operationCount;
    private final long dispatchedAtTick;
    private final String executionRouteKey;
    private State state;
    private long currentTick;
    private long totalTicks;
    @Nullable
    private String diagnostic;

    /** 创建机器/处理器操作分配的初始状态。 */
    public QIOOperationAssignment(@Nonnull UUID operationId, @Nonnull ProviderKind providerKind,
          @Nonnull UUID deviceUUID, long laneId, long dispatchedAtTick) {
        this(operationId, providerKind, deviceUUID, laneId, 1, dispatchedAtTick);
    }

    /** 创建带路由键的操作分配。 */
    public QIOOperationAssignment(@Nonnull UUID operationId, @Nonnull ProviderKind providerKind,
          @Nonnull UUID deviceUUID, long laneId, long operationCount, long dispatchedAtTick) {
        this(operationId, providerKind, deviceUUID, laneId, operationCount, dispatchedAtTick,
              "");
    }

    /** 创建完整操作分配并恢复其运行计数。 */
    public QIOOperationAssignment(@Nonnull UUID operationId,
          @Nonnull ProviderKind providerKind, @Nonnull UUID deviceUUID, long laneId,
          long operationCount, long dispatchedAtTick,
          @Nonnull String executionRouteKey) {
        this.operationId = Objects.requireNonNull(operationId, "operationId");
        this.providerKind = Objects.requireNonNull(providerKind, "providerKind");
        this.deviceUUID = Objects.requireNonNull(deviceUUID, "deviceUUID");
        this.laneId = QIOProcessingNbt.requireNonNegative(laneId, "laneId");
        if (operationCount <= 0) {
            throw new IllegalArgumentException("operationCount must be positive");
        }
        this.operationCount = operationCount;
        this.dispatchedAtTick = QIOProcessingNbt.requireNonNegative(dispatchedAtTick,
              "dispatchedAtTick");
        String checkedRouteKey = Objects.requireNonNull(executionRouteKey,
              "executionRouteKey").trim();
        if (checkedRouteKey.length() > 1_536) {
            throw new IllegalArgumentException("QIO execution route key is too long");
        }
        this.executionRouteKey = checkedRouteKey;
        state = State.LOADING;
    }

    @Nonnull
    /** 返回操作标识。 */
    public UUID getOperationId() {
        return operationId;
    }

    @Nonnull
    /** 返回 Provider 类型。 */
    public ProviderKind getProviderKind() {
        return providerKind;
    }

    @Nonnull
    /** 返回执行设备标识。 */
    public UUID getDeviceUUID() {
        return deviceUUID;
    }

    /** 返回设备通道编号。 */
    public long getLaneId() {
        return laneId;
    }

    /** 返回派发时刻。 */
    public long getDispatchedAtTick() {
        return dispatchedAtTick;
    }

    /** 返回该分配承载的批次数。 */
    public long getOperationCount() {
        return operationCount;
    }

    @Nonnull
    /** 返回执行路由键。 */
    public String getExecutionRouteKey() {
        return executionRouteKey;
    }

    @Nonnull
    /** 返回操作状态。 */
    public State getState() {
        return state;
    }

    /** 返回当前运行 tick。 */
    public long getCurrentTick() {
        return currentTick;
    }

    /** 返回预计总 tick。 */
    public long getTotalTicks() {
        return totalTicks;
    }

    @Nullable
    /** 返回阻塞/失败诊断文本。 */
    public String getDiagnostic() {
        return diagnostic;
    }

    /** 更新运行状态和计数器；参数不一致时返回 false。 */
    public boolean update(@Nonnull State state, long currentTick, long totalTicks,
          @Nullable String diagnostic) {
        State checkedState = Objects.requireNonNull(state, "state");
        long checkedCurrentTick = QIOProcessingNbt.requireNonNegative(currentTick, "currentTick");
        long checkedTotalTicks = QIOProcessingNbt.requireNonNegative(totalTicks, "totalTicks");
        if (checkedCurrentTick > checkedTotalTicks && checkedTotalTicks > 0) {
            throw new IllegalArgumentException("QIO operation progress exceeds its total");
        }
        String normalizedDiagnostic = diagnostic == null ? null : checkedDiagnostic(diagnostic);
        if (checkedState == State.FAILED && normalizedDiagnostic == null) {
            throw new IllegalArgumentException("A failed QIO operation requires a diagnostic");
        }
        if (this.state == checkedState && this.currentTick == checkedCurrentTick &&
              this.totalTicks == checkedTotalTicks && Objects.equals(this.diagnostic,
              normalizedDiagnostic)) {
            return false;
        }
        this.state = checkedState;
        this.currentTick = checkedCurrentTick;
        this.totalTicks = checkedTotalTicks;
        this.diagnostic = normalizedDiagnostic;
        return true;
    }

    @Nonnull
    /** 将操作分配写入 NBT。 */
    public NBTTagCompound write() {
        NBTTagCompound data = new NBTTagCompound();
        data.setInteger("operationAssignmentSchemaVersion", SCHEMA_VERSION);
        QIOProcessingNbt.writeUUID(data, "operationId", operationId);
        data.setString("providerKind", providerKind.name());
        QIOProcessingNbt.writeUUID(data, "deviceUUID", deviceUUID);
        data.setLong("laneId", laneId);
        data.setLong("operationCount", operationCount);
        data.setLong("dispatchedAtTick", dispatchedAtTick);
        data.setString("executionRouteKey", executionRouteKey);
        data.setString("state", state.name());
        data.setLong("currentTick", currentTick);
        data.setLong("totalTicks", totalTicks);
        if (diagnostic != null) {
            data.setString("diagnostic", diagnostic);
        }
        return data;
    }

    @Nonnull
    /** 从 NBT 读取并校验操作分配。 */
    public static QIOOperationAssignment read(@Nonnull NBTTagCompound data)
          throws QIOProcessingDataException {
        try {
            if (data.getInteger("operationAssignmentSchemaVersion") != SCHEMA_VERSION ||
                !data.hasKey("executionRouteKey", 8)) {
                throw new QIOProcessingDataException(
                      "Unsupported or incomplete QIO operation assignment schema");
            }
            QIOOperationAssignment assignment = new QIOOperationAssignment(
                  QIOProcessingNbt.readUUID(data, "operationId"),
                  QIOProcessingNbt.readEnum(data, "providerKind", ProviderKind.class),
                  QIOProcessingNbt.readUUID(data, "deviceUUID"), data.getLong("laneId"),
                  data.getLong("operationCount"), data.getLong("dispatchedAtTick"),
                  data.getString("executionRouteKey"));
            assignment.update(QIOProcessingNbt.readEnum(data, "state", State.class),
                  data.getLong("currentTick"), data.getLong("totalTicks"),
                  data.hasKey("diagnostic", 8) ? data.getString("diagnostic") : null);
            return assignment;
        } catch (QIOProcessingDataException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new QIOProcessingDataException("Invalid QIO operation assignment", e);
        }
    }

    private static String checkedDiagnostic(String value) {
        String checked = value.trim();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException("QIO operation diagnostic cannot be empty");
        }
        return checked.substring(0, Math.min(512, checked.length()));
    }
}
