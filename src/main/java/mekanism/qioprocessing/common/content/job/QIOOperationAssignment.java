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

    public QIOOperationAssignment(@Nonnull UUID operationId, @Nonnull ProviderKind providerKind,
          @Nonnull UUID deviceUUID, long laneId, long dispatchedAtTick) {
        this(operationId, providerKind, deviceUUID, laneId, 1, dispatchedAtTick);
    }

    public QIOOperationAssignment(@Nonnull UUID operationId, @Nonnull ProviderKind providerKind,
          @Nonnull UUID deviceUUID, long laneId, long operationCount, long dispatchedAtTick) {
        this(operationId, providerKind, deviceUUID, laneId, operationCount, dispatchedAtTick,
              "");
    }

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
    public UUID getOperationId() {
        return operationId;
    }

    @Nonnull
    public ProviderKind getProviderKind() {
        return providerKind;
    }

    @Nonnull
    public UUID getDeviceUUID() {
        return deviceUUID;
    }

    public long getLaneId() {
        return laneId;
    }

    public long getDispatchedAtTick() {
        return dispatchedAtTick;
    }

    public long getOperationCount() {
        return operationCount;
    }

    @Nonnull
    public String getExecutionRouteKey() {
        return executionRouteKey;
    }

    @Nonnull
    public State getState() {
        return state;
    }

    public long getCurrentTick() {
        return currentTick;
    }

    public long getTotalTicks() {
        return totalTicks;
    }

    @Nullable
    public String getDiagnostic() {
        return diagnostic;
    }

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
