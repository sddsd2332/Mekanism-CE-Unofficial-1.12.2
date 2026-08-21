package mekanism.qioprocessing.common.content.maintenance;

import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.QIOProcessingDataException;
import mekanism.qioprocessing.common.content.QIOProcessingNbt;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;
import java.util.UUID;

/** One persistent frequency-owned requester rule and its idempotent runtime linkage. */
/**
 * QIO 处理模块中的 QIOMaintenanceRule 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIOMaintenanceRule {

    public enum EvaluationStatus {
        NEVER,
        NOT_TRIGGERED,
        PLANNING,
        ACTIVE_ORDER,
        NO_ROUTE,
        ACCESS_DENIED,
        ERROR
    }

    private final UUID ruleId;
    private final PortableResourceDescriptor resource;
    private final UUID creator;
    private UUID lastModifiedBy;
    private boolean enabled;
    private long triggerAmount;
    private long targetAmount;
    private long maximumSingleRequest;
    private long jobPriority;
    private int retryIntervalTicks;
    private long ruleRevision;
    private long createdAtTick;
    private long modifiedAtTick;
    @Nullable
    private UUID lastEvaluationId;
    @Nullable
    private UUID outstandingJobId;
    private long nextRetryTick;
    private EvaluationStatus evaluationStatus = EvaluationStatus.NEVER;
    private String diagnostic = "";

    public QIOMaintenanceRule(@Nonnull UUID ruleId,
          @Nonnull PortableResourceDescriptor resource, @Nonnull UUID creator, boolean enabled,
          long triggerAmount, long targetAmount, long maximumSingleRequest, long jobPriority,
          int retryIntervalTicks, long createdAtTick) {
        this.ruleId = Objects.requireNonNull(ruleId, "ruleId");
        this.resource = Objects.requireNonNull(resource, "resource");
        this.creator = Objects.requireNonNull(creator, "creator");
        lastModifiedBy = creator;
        this.createdAtTick = requireTick(createdAtTick, "createdAtTick");
        modifiedAtTick = this.createdAtTick;
        apply(enabled, triggerAmount, targetAmount, maximumSingleRequest, jobPriority,
              retryIntervalTicks);
    }

    @Nonnull
    public UUID getRuleId() {
        return ruleId;
    }

    @Nonnull
    public PortableResourceDescriptor getResource() {
        return resource;
    }

    @Nonnull
    public UUID getCreator() {
        return creator;
    }

    @Nonnull
    public UUID getLastModifiedBy() {
        return lastModifiedBy;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public long getTriggerAmount() {
        return triggerAmount;
    }

    public long getTargetAmount() {
        return targetAmount;
    }

    public long getMaximumSingleRequest() {
        return maximumSingleRequest;
    }

    public long getJobPriority() {
        return jobPriority;
    }

    public int getRetryIntervalTicks() {
        return retryIntervalTicks;
    }

    public long getRuleRevision() {
        return ruleRevision;
    }

    public long getCreatedAtTick() {
        return createdAtTick;
    }

    public long getModifiedAtTick() {
        return modifiedAtTick;
    }

    @Nullable
    public UUID getLastEvaluationId() {
        return lastEvaluationId;
    }

    @Nullable
    public UUID getOutstandingJobId() {
        return outstandingJobId;
    }

    public long getNextRetryTick() {
        return nextRetryTick;
    }

    @Nonnull
    public EvaluationStatus getEvaluationStatus() {
        return evaluationStatus;
    }

    @Nonnull
    public String getDiagnostic() {
        return diagnostic;
    }

    void update(@Nonnull UUID editor, long expectedRevision, boolean enabled,
          long triggerAmount, long targetAmount, long maximumSingleRequest, long jobPriority,
          int retryIntervalTicks, long modifiedAtTick) {
        if (ruleRevision != expectedRevision) {
            throw new IllegalStateException("QIO maintenance rule revision changed");
        }
        apply(enabled, triggerAmount, targetAmount, maximumSingleRequest, jobPriority,
              retryIntervalTicks);
        lastModifiedBy = Objects.requireNonNull(editor, "editor");
        this.modifiedAtTick = requireTick(modifiedAtTick, "modifiedAtTick");
        if (ruleRevision == Long.MAX_VALUE) {
            throw new IllegalStateException("QIO maintenance rule revision exhausted");
        }
        ruleRevision++;
    }

    void recordEvaluation(@Nonnull UUID evaluationId, @Nonnull EvaluationStatus status,
          long currentTick, @Nullable String diagnostic) {
        lastEvaluationId = Objects.requireNonNull(evaluationId, "evaluationId");
        evaluationStatus = Objects.requireNonNull(status, "status");
        this.diagnostic = boundedDiagnostic(diagnostic);
        nextRetryTick = status == EvaluationStatus.NO_ROUTE ||
              status == EvaluationStatus.ACCESS_DENIED || status == EvaluationStatus.ERROR ?
              saturatedAdd(currentTick, retryIntervalTicks) : 0;
    }

    void bindOutstanding(@Nonnull UUID evaluationId, @Nonnull UUID jobId) {
        UUID checkedJobId = Objects.requireNonNull(jobId, "jobId");
        if (outstandingJobId != null && !outstandingJobId.equals(checkedJobId)) {
            throw new IllegalStateException("QIO maintenance rule already owns another order");
        }
        lastEvaluationId = Objects.requireNonNull(evaluationId, "evaluationId");
        outstandingJobId = checkedJobId;
        evaluationStatus = EvaluationStatus.ACTIVE_ORDER;
        diagnostic = "";
        nextRetryTick = 0;
    }

    void clearOutstanding() {
        outstandingJobId = null;
        if (evaluationStatus == EvaluationStatus.ACTIVE_ORDER ||
              evaluationStatus == EvaluationStatus.PLANNING) {
            evaluationStatus = EvaluationStatus.NEVER;
        }
    }

    @Nonnull
    public NBTTagCompound write() {
        NBTTagCompound data = new NBTTagCompound();
        QIOProcessingNbt.writeUUID(data, "ruleId", ruleId);
        data.setTag("resource", resource.write());
        QIOProcessingNbt.writeUUID(data, "creator", creator);
        QIOProcessingNbt.writeUUID(data, "lastModifiedBy", lastModifiedBy);
        data.setBoolean("enabled", enabled);
        data.setLong("triggerAmount", triggerAmount);
        data.setLong("targetAmount", targetAmount);
        data.setLong("maximumSingleRequest", maximumSingleRequest);
        data.setLong("jobPriority", jobPriority);
        data.setInteger("retryIntervalTicks", retryIntervalTicks);
        data.setLong("ruleRevision", ruleRevision);
        data.setLong("createdAtTick", createdAtTick);
        data.setLong("modifiedAtTick", modifiedAtTick);
        if (lastEvaluationId != null) {
            QIOProcessingNbt.writeUUID(data, "lastEvaluationId", lastEvaluationId);
        }
        if (outstandingJobId != null) {
            QIOProcessingNbt.writeUUID(data, "outstandingJobId", outstandingJobId);
        }
        data.setLong("nextRetryTick", nextRetryTick);
        data.setString("evaluationStatus", evaluationStatus.name());
        if (!diagnostic.isEmpty()) {
            data.setString("diagnostic", diagnostic);
        }
        return data;
    }

    @Nonnull
    public static QIOMaintenanceRule read(@Nonnull NBTTagCompound data)
          throws QIOProcessingDataException {
        try {
            QIOMaintenanceRule rule = new QIOMaintenanceRule(
                  QIOProcessingNbt.readUUID(data, "ruleId"),
                  PortableResourceDescriptor.read(data.getCompoundTag("resource")),
                  QIOProcessingNbt.readUUID(data, "creator"), data.getBoolean("enabled"),
                  data.getLong("triggerAmount"), data.getLong("targetAmount"),
                  data.getLong("maximumSingleRequest"), data.getLong("jobPriority"),
                  data.getInteger("retryIntervalTicks"), data.getLong("createdAtTick"));
            rule.lastModifiedBy = QIOProcessingNbt.readUUID(data, "lastModifiedBy");
            rule.ruleRevision = QIOProcessingNbt.requireNonNegative(
                  data.getLong("ruleRevision"), "ruleRevision");
            rule.modifiedAtTick = requireTick(data.getLong("modifiedAtTick"),
                  "modifiedAtTick");
            rule.lastEvaluationId = data.hasKey("lastEvaluationId", NBT.TAG_STRING) ?
                  QIOProcessingNbt.readUUID(data, "lastEvaluationId") : null;
            rule.outstandingJobId = data.hasKey("outstandingJobId", NBT.TAG_STRING) ?
                  QIOProcessingNbt.readUUID(data, "outstandingJobId") : null;
            rule.nextRetryTick = requireTick(data.getLong("nextRetryTick"), "nextRetryTick");
            rule.evaluationStatus = QIOProcessingNbt.readEnum(data, "evaluationStatus",
                  EvaluationStatus.class);
            rule.diagnostic = boundedDiagnostic(data.getString("diagnostic"));
            return rule;
        } catch (QIOProcessingDataException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new QIOProcessingDataException("Invalid QIO maintenance rule", e);
        }
    }

    private void apply(boolean enabled, long triggerAmount, long targetAmount,
          long maximumSingleRequest, long jobPriority, int retryIntervalTicks) {
        if (triggerAmount < 0 || targetAmount < triggerAmount || maximumSingleRequest <= 0 ||
              retryIntervalTicks < 20) {
            throw new IllegalArgumentException("Invalid QIO maintenance rule limits");
        }
        this.enabled = enabled;
        this.triggerAmount = triggerAmount;
        this.targetAmount = targetAmount;
        this.maximumSingleRequest = maximumSingleRequest;
        this.jobPriority = jobPriority;
        this.retryIntervalTicks = retryIntervalTicks;
    }

    private static long requireTick(long value, String name) {
        return QIOProcessingNbt.requireNonNegative(value, name);
    }

    private static long saturatedAdd(long left, long right) {
        return right > Long.MAX_VALUE - left ? Long.MAX_VALUE : left + right;
    }

    private static String boundedDiagnostic(@Nullable String value) {
        String checked = value == null ? "" : value.trim();
        return checked.substring(0, Math.min(512, checked.length()));
    }
}
