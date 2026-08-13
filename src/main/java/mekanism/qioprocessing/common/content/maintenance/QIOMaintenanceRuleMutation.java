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

/** One bounded create, update, or delete command for a frequency-owned rule. */
public final class QIOMaintenanceRuleMutation {

    public enum Kind {
        CREATE,
        UPDATE,
        DELETE
    }

    private final Kind kind;
    @Nullable
    private final UUID ruleId;
    private final long expectedRuleRevision;
    @Nullable
    private final PortableResourceDescriptor resource;
    private final boolean enabled;
    private final long triggerAmount;
    private final long targetAmount;
    private final long maximumSingleRequest;
    private final long jobPriority;
    private final int retryIntervalTicks;

    private QIOMaintenanceRuleMutation(Kind kind, @Nullable UUID ruleId,
          long expectedRuleRevision, @Nullable PortableResourceDescriptor resource,
          boolean enabled, long triggerAmount, long targetAmount,
          long maximumSingleRequest, long jobPriority, int retryIntervalTicks) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.ruleId = ruleId;
        this.expectedRuleRevision = expectedRuleRevision;
        this.resource = resource;
        this.enabled = enabled;
        this.triggerAmount = triggerAmount;
        this.targetAmount = targetAmount;
        this.maximumSingleRequest = maximumSingleRequest;
        this.jobPriority = jobPriority;
        this.retryIntervalTicks = retryIntervalTicks;
        validate();
    }

    @Nonnull
    public static QIOMaintenanceRuleMutation create(
          @Nonnull PortableResourceDescriptor resource, boolean enabled,
          long triggerAmount, long targetAmount, long maximumSingleRequest,
          long jobPriority, int retryIntervalTicks) {
        return new QIOMaintenanceRuleMutation(Kind.CREATE, null, -1, resource,
              enabled, triggerAmount, targetAmount, maximumSingleRequest,
              jobPriority, retryIntervalTicks);
    }

    @Nonnull
    public static QIOMaintenanceRuleMutation update(@Nonnull UUID ruleId,
          long expectedRuleRevision, boolean enabled, long triggerAmount,
          long targetAmount, long maximumSingleRequest, long jobPriority,
          int retryIntervalTicks) {
        return new QIOMaintenanceRuleMutation(Kind.UPDATE, ruleId,
              expectedRuleRevision, null, enabled, triggerAmount, targetAmount,
              maximumSingleRequest, jobPriority, retryIntervalTicks);
    }

    @Nonnull
    public static QIOMaintenanceRuleMutation delete(@Nonnull UUID ruleId,
          long expectedRuleRevision) {
        return new QIOMaintenanceRuleMutation(Kind.DELETE, ruleId,
              expectedRuleRevision, null, false, 0, 1, 1, 0, 20);
    }

    @Nonnull
    public Kind getKind() {
        return kind;
    }

    @Nullable
    public UUID getRuleId() {
        return ruleId;
    }

    public long getExpectedRuleRevision() {
        return expectedRuleRevision;
    }

    @Nullable
    public PortableResourceDescriptor getResource() {
        return resource;
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

    @Nonnull
    public NBTTagCompound write() {
        NBTTagCompound data = new NBTTagCompound();
        data.setString("kind", kind.name());
        if (ruleId != null) {
            QIOProcessingNbt.writeUUID(data, "ruleId", ruleId);
            data.setLong("expectedRuleRevision", expectedRuleRevision);
        }
        if (resource != null) {
            data.setTag("resource", resource.write());
        }
        if (kind != Kind.DELETE) {
            data.setBoolean("enabled", enabled);
            data.setLong("triggerAmount", triggerAmount);
            data.setLong("targetAmount", targetAmount);
            data.setLong("maximumSingleRequest", maximumSingleRequest);
            data.setLong("jobPriority", jobPriority);
            data.setInteger("retryIntervalTicks", retryIntervalTicks);
        }
        return data;
    }

    @Nonnull
    public static QIOMaintenanceRuleMutation read(@Nonnull NBTTagCompound data)
          throws QIOProcessingDataException {
        try {
            Kind kind = QIOProcessingNbt.readEnum(data, "kind", Kind.class);
            UUID ruleId = data.hasKey("ruleId", NBT.TAG_STRING) ?
                  QIOProcessingNbt.readUUID(data, "ruleId") : null;
            PortableResourceDescriptor resource = data.hasKey("resource", NBT.TAG_COMPOUND) ?
                  PortableResourceDescriptor.read(data.getCompoundTag("resource")) : null;
            return new QIOMaintenanceRuleMutation(kind, ruleId,
                  ruleId == null ? -1 : data.getLong("expectedRuleRevision"), resource,
                  data.getBoolean("enabled"), data.getLong("triggerAmount"),
                  data.getLong("targetAmount"), data.getLong("maximumSingleRequest"),
                  data.getLong("jobPriority"), data.getInteger("retryIntervalTicks"));
        } catch (QIOProcessingDataException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new QIOProcessingDataException("Invalid maintenance rule mutation", e);
        }
    }

    private void validate() {
        if ((kind == Kind.CREATE) != (resource != null) ||
            (kind == Kind.CREATE) == (ruleId != null) ||
            (ruleId == null ? expectedRuleRevision != -1 : expectedRuleRevision < 0)) {
            throw new IllegalArgumentException("Maintenance rule mutation identity mismatch");
        }
        if (kind != Kind.DELETE && (triggerAmount < 0 || targetAmount < triggerAmount ||
            maximumSingleRequest <= 0 || retryIntervalTicks < 20)) {
            throw new IllegalArgumentException("Invalid maintenance rule limits");
        }
    }
}
