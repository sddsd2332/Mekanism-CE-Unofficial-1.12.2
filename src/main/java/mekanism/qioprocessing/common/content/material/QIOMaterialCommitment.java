package mekanism.qioprocessing.common.content.material;

import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.QIOProcessingDataException;
import mekanism.qioprocessing.common.content.QIOProcessingNbt;
import mekanism.qioprocessing.common.content.plan.QIOCandidateOption;
import mekanism.qioprocessing.common.content.plan.QIOCandidateRequirement;
import mekanism.qioprocessing.common.content.plan.QIOPlanMaterialRequirements;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Module-side meaning of the core claim owned by one immutable plan revision. */
/**
 * QIO 处理模块中的 QIOMaterialCommitment 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIOMaterialCommitment {

    private static final int SCHEMA_VERSION = 2;

    public enum State {
        ACTIVE,
        CONSUMED,
        RELEASING,
        RELEASED,
        NEEDS_RECONCILIATION
    }

    private static final int MAX_RESOURCE_ENTRIES = 65_536;

    private final UUID jobId;
    private final int planRevision;
    private final UUID claimId;
    private long priority;
    private final long enqueueSequence;
    private final QIOPlanMaterialRequirements materialRequirements;
    private final Map<PortableResourceDescriptor, Long> requiredAmounts;
    private Map<PortableResourceDescriptor, Long> committedAmounts;
    private Map<PortableResourceDescriptor, Long> missingAmounts;
    private long observedClaimRevision;
    private State state;
    @Nullable
    private QIOPendingClaimMutation pendingMutation;

    public QIOMaterialCommitment(@Nonnull UUID jobId, int planRevision, @Nonnull UUID claimId,
          long priority, long enqueueSequence,
          @Nonnull Map<PortableResourceDescriptor, Long> requiredAmounts) {
        this(jobId, planRevision, claimId, priority, enqueueSequence,
              new QIOPlanMaterialRequirements(requiredAmounts, Collections.emptyList()));
    }

    public QIOMaterialCommitment(@Nonnull UUID jobId, int planRevision, @Nonnull UUID claimId,
          long priority, long enqueueSequence,
          @Nonnull QIOPlanMaterialRequirements materialRequirements) {
        this(jobId, planRevision, claimId, priority, enqueueSequence, materialRequirements,
              Collections.emptyMap(), materialRequirements.preferredProjection(), 0,
              State.ACTIVE, null);
    }

    private QIOMaterialCommitment(UUID jobId, int planRevision, UUID claimId, long priority,
          long enqueueSequence, QIOPlanMaterialRequirements materialRequirements,
          Map<PortableResourceDescriptor, Long> committedAmounts,
          Map<PortableResourceDescriptor, Long> missingAmounts, long observedClaimRevision,
          State state, @Nullable QIOPendingClaimMutation pendingMutation) {
        this.jobId = Objects.requireNonNull(jobId, "jobId");
        this.planRevision = QIOProcessingNbt.requirePositive(planRevision, "planRevision");
        this.claimId = Objects.requireNonNull(claimId, "claimId");
        this.priority = priority;
        this.enqueueSequence = QIOProcessingNbt.requireNonNegative(enqueueSequence, "enqueueSequence");
        this.materialRequirements = Objects.requireNonNull(materialRequirements,
              "materialRequirements");
        this.requiredAmounts = materialRequirements.preferredProjection();
        this.committedAmounts = QIOProcessingNbt.copyAmounts(committedAmounts, true, "committedAmounts");
        this.missingAmounts = QIOProcessingNbt.copyAmounts(missingAmounts, true, "missingAmounts");
        this.observedClaimRevision = QIOProcessingNbt.requireNonNegative(observedClaimRevision,
              "observedClaimRevision");
        this.state = Objects.requireNonNull(state, "state");
        this.pendingMutation = pendingMutation;
        validateAmounts();
    }

    @Nonnull
    public UUID getJobId() {
        return jobId;
    }

    public int getPlanRevision() {
        return planRevision;
    }

    @Nonnull
    public UUID getClaimId() {
        return claimId;
    }

    public long getPriority() {
        return priority;
    }

    public void updatePriority(long priority) {
        if (state != State.ACTIVE && state != State.NEEDS_RECONCILIATION &&
            state != State.CONSUMED) {
            throw new IllegalStateException("Cannot reprioritize a claim in state " + state);
        }
        this.priority = priority;
    }

    public long getEnqueueSequence() {
        return enqueueSequence;
    }

    @Nonnull
    public Map<PortableResourceDescriptor, Long> getRequiredAmounts() {
        return requiredAmounts;
    }

    @Nonnull
    public QIOPlanMaterialRequirements getMaterialRequirements() {
        return materialRequirements;
    }

    @Nonnull
    public Map<PortableResourceDescriptor, Long> getCommittedAmounts() {
        return committedAmounts;
    }

    @Nonnull
    public Map<PortableResourceDescriptor, Long> getMissingAmounts() {
        return missingAmounts;
    }

    @Nonnull
    public Set<PortableResourceDescriptor> getMissingResourceKeys() {
        return evaluate(committedAmounts).missingResources;
    }

    public boolean acceptsCommittedAmounts(
          @Nonnull Map<PortableResourceDescriptor, Long> committed) {
        return evaluate(QIOProcessingNbt.copyAmounts(committed, true,
              "candidate committed amounts")).valid;
    }

    public long getObservedClaimRevision() {
        return observedClaimRevision;
    }

    @Nonnull
    public State getState() {
        return state;
    }

    @Nullable
    public QIOPendingClaimMutation getPendingMutation() {
        return pendingMutation;
    }

    public boolean isFullyCommitted() {
        return state == State.ACTIVE && missingAmounts.isEmpty() && pendingMutation == null;
    }

    public void prepare(@Nonnull QIOPendingClaimMutation mutation) {
        if (pendingMutation != null) {
            throw new IllegalStateException("A claim mutation is already pending for job " + jobId);
        }
        if (state != State.ACTIVE && state != State.RELEASING &&
              state != State.NEEDS_RECONCILIATION) {
            throw new IllegalStateException("Cannot mutate a claim in state " + state);
        }
        pendingMutation = Objects.requireNonNull(mutation, "mutation");
    }

    public void applyActiveClaim(@Nonnull Map<PortableResourceDescriptor, Long> committed,
          long claimRevision) {
        if (state == State.CONSUMED || state == State.RELEASED) {
            throw new IllegalStateException("Cannot restore a claim after resources changed ownership");
        }
        Map<PortableResourceDescriptor, Long> checked = QIOProcessingNbt.copyAmounts(committed,
              true, "committedAmounts");
        Evaluation evaluation = evaluate(checked);
        if (!evaluation.valid) {
            throw new IllegalArgumentException(
                  "Committed QIO resources do not satisfy this material requirement shape");
        }
        committedAmounts = checked;
        missingAmounts = evaluation.missingAmounts;
        observedClaimRevision = QIOProcessingNbt.requireNonNegative(claimRevision, "claimRevision");
        state = State.ACTIVE;
        pendingMutation = null;
        validateAmounts();
    }

    public void markNeedsReconciliation() {
        state = State.NEEDS_RECONCILIATION;
        pendingMutation = null;
    }

    public void markConsumed(long claimRevision) {
        committedAmounts = Collections.emptyMap();
        missingAmounts = Collections.emptyMap();
        observedClaimRevision = QIOProcessingNbt.requireNonNegative(claimRevision, "claimRevision");
        pendingMutation = null;
        state = State.CONSUMED;
    }

    public void beginRelease(@Nonnull QIOPendingClaimMutation mutation) {
        if (pendingMutation != null) {
            throw new IllegalStateException("A claim mutation is already pending for job " + jobId);
        }
        state = State.RELEASING;
        prepare(mutation);
    }

    public void markReleased(long claimRevision) {
        committedAmounts = Collections.emptyMap();
        missingAmounts = Collections.emptyMap();
        observedClaimRevision = QIOProcessingNbt.requireNonNegative(claimRevision, "claimRevision");
        pendingMutation = null;
        state = State.RELEASED;
    }

    @Nonnull
    public NBTTagCompound write() {
        NBTTagCompound data = new NBTTagCompound();
        data.setInteger("materialCommitmentSchemaVersion", SCHEMA_VERSION);
        QIOProcessingNbt.writeUUID(data, "jobId", jobId);
        data.setInteger("planRevision", planRevision);
        QIOProcessingNbt.writeUUID(data, "claimId", claimId);
        data.setLong("priority", priority);
        data.setLong("enqueueSequence", enqueueSequence);
        data.setString("state", state.name());
        data.setLong("observedClaimRevision", observedClaimRevision);
        data.setTag("requiredAmounts", QIOProcessingNbt.writeAmounts(requiredAmounts));
        data.setTag("materialRequirements", materialRequirements.write());
        data.setTag("committedAmounts", QIOProcessingNbt.writeAmounts(committedAmounts));
        data.setTag("missingAmounts", QIOProcessingNbt.writeAmounts(missingAmounts));
        if (pendingMutation != null) {
            data.setTag("pendingMutation", pendingMutation.write());
        }
        return data;
    }

    @Nonnull
    public static QIOMaterialCommitment read(@Nonnull NBTTagCompound data)
          throws QIOProcessingDataException {
        try {
            if (data.getInteger("materialCommitmentSchemaVersion") != SCHEMA_VERSION ||
                !data.hasKey("materialRequirements", NBT.TAG_COMPOUND) ||
                !data.hasKey("requiredAmounts", NBT.TAG_LIST)) {
                throw new QIOProcessingDataException(
                      "Unsupported or incomplete QIO material commitment schema");
            }
            QIOPlanMaterialRequirements requirements = QIOPlanMaterialRequirements.read(
                  data.getCompoundTag("materialRequirements"));
            if (!requirements.preferredProjection().equals(QIOProcessingNbt.readAmounts(data,
                  "requiredAmounts", MAX_RESOURCE_ENTRIES))) {
                throw new QIOProcessingDataException(
                      "QIO material commitment display requirements disagree with its groups");
            }
            return new QIOMaterialCommitment(QIOProcessingNbt.readUUID(data, "jobId"),
                  data.getInteger("planRevision"), QIOProcessingNbt.readUUID(data, "claimId"),
                  data.getLong("priority"), data.getLong("enqueueSequence"),
                  requirements,
                  QIOProcessingNbt.readAmounts(data, "committedAmounts", MAX_RESOURCE_ENTRIES),
                  QIOProcessingNbt.readAmounts(data, "missingAmounts", MAX_RESOURCE_ENTRIES),
                  data.getLong("observedClaimRevision"),
                  QIOProcessingNbt.readEnum(data, "state", State.class),
                  data.hasKey("pendingMutation", NBT.TAG_COMPOUND) ?
                        QIOPendingClaimMutation.read(data.getCompoundTag("pendingMutation")) : null);
        } catch (QIOProcessingDataException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new QIOProcessingDataException("Invalid QIO material commitment", e);
        }
    }

    @Nonnull
    private Evaluation evaluate(Map<PortableResourceDescriptor, Long> committed) {
        Map<PortableResourceDescriptor, Long> remaining = new LinkedHashMap<>(committed);
        Map<PortableResourceDescriptor, Long> missing = new LinkedHashMap<>();
        Set<PortableResourceDescriptor> missingResources = new LinkedHashSet<>();
        for (Map.Entry<PortableResourceDescriptor, Long> requirement :
              materialRequirements.getExactAmounts().entrySet()) {
            long held = Math.min(requirement.getValue(), remaining.getOrDefault(
                  requirement.getKey(), 0L));
            consume(remaining, requirement.getKey(), held);
            if (held < requirement.getValue()) {
                missing.put(requirement.getKey(), requirement.getValue() - held);
                missingResources.add(requirement.getKey());
            }
        }
        for (QIOCandidateRequirement requirement :
              materialRequirements.getCandidateRequirements()) {
            long missingUnits = requirement.getRequiredUnits();
            for (QIOCandidateOption option : requirement.getOptions()) {
                long available = remaining.getOrDefault(option.getResource(), 0L);
                long units = Math.min(missingUnits, available / option.getAmountPerUnit());
                if (units <= 0) continue;
                consume(remaining, option.getResource(), Math.multiplyExact(units,
                      option.getAmountPerUnit()));
                missingUnits -= units;
                if (missingUnits == 0) break;
            }
            if (missingUnits > 0) {
                QIOCandidateOption preferred = requirement.getOptions().get(0);
                missing.merge(preferred.getResource(), Math.multiplyExact(missingUnits,
                      preferred.getAmountPerUnit()), Math::addExact);
                requirement.getOptions().forEach(option ->
                      missingResources.add(option.getResource()));
            }
        }
        boolean valid = remaining.values().stream().allMatch(amount -> amount == 0);
        return new Evaluation(valid, Collections.unmodifiableMap(missing),
              Collections.unmodifiableSet(missingResources));
    }

    private static void consume(Map<PortableResourceDescriptor, Long> amounts,
          PortableResourceDescriptor resource, long amount) {
        if (amount <= 0) return;
        long stored = amounts.getOrDefault(resource, 0L);
        if (stored == amount) amounts.remove(resource);
        else amounts.put(resource, stored - amount);
    }

    private void validateAmounts() {
        if (state == State.ACTIVE || state == State.RELEASING || state == State.NEEDS_RECONCILIATION) {
            Evaluation evaluation = evaluate(committedAmounts);
            if (!evaluation.valid || !evaluation.missingAmounts.equals(missingAmounts)) {
                throw new IllegalArgumentException("Material missing amounts do not match required minus committed");
            }
        } else if (!committedAmounts.isEmpty() || !missingAmounts.isEmpty()) {
            throw new IllegalArgumentException("Settled material commitment still contains active amounts");
        }
    }

    private static final class Evaluation {

        private final boolean valid;
        private final Map<PortableResourceDescriptor, Long> missingAmounts;
        private final Set<PortableResourceDescriptor> missingResources;

        private Evaluation(boolean valid,
              Map<PortableResourceDescriptor, Long> missingAmounts,
              Set<PortableResourceDescriptor> missingResources) {
            this.valid = valid;
            this.missingAmounts = missingAmounts;
            this.missingResources = missingResources;
        }
    }
}
