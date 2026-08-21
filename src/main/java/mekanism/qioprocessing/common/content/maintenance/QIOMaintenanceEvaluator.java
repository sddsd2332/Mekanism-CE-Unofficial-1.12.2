package mekanism.qioprocessing.common.content.maintenance;

import mekanism.api.qio.external.QIOStorageEntry;
import mekanism.api.qio.external.QIOStorageSnapshot;
import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkData;
import mekanism.qioprocessing.common.content.buffer.QIOJobBuffer;
import mekanism.qioprocessing.common.content.job.QIOCraftingJob;
import mekanism.qioprocessing.common.content.job.QIOCraftingJobSource;
import mekanism.qioprocessing.common.content.transfer.QIODurableTransferRecord;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Pure main-thread decision logic for one normalized maintenance resource group. */
/**
 * QIO 处理模块中的 QIOMaintenanceEvaluator 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIOMaintenanceEvaluator {

    public enum Status {
        NOT_TRIGGERED,
        RETRY_WAIT,
        ACTIVE_ORDER,
        REQUEST
    }

    public static final class EffectiveStock {

        private final BigInteger qioSpendable;
        private final BigInteger settledInboundAwaitingQIO;
        private final BigInteger committedGuaranteedInbound;

        private EffectiveStock(BigInteger qioSpendable, BigInteger settledInboundAwaitingQIO,
              BigInteger committedGuaranteedInbound) {
            this.qioSpendable = requireNonNegative(qioSpendable, "qioSpendable");
            this.settledInboundAwaitingQIO = requireNonNegative(settledInboundAwaitingQIO,
                  "settledInboundAwaitingQIO");
            this.committedGuaranteedInbound = requireNonNegative(committedGuaranteedInbound,
                  "committedGuaranteedInbound");
        }

        @Nonnull
        public BigInteger getQioSpendable() {
            return qioSpendable;
        }

        @Nonnull
        public BigInteger getSettledInboundAwaitingQIO() {
            return settledInboundAwaitingQIO;
        }

        @Nonnull
        public BigInteger getCommittedGuaranteedInbound() {
            return committedGuaranteedInbound;
        }

        @Nonnull
        public BigInteger getTotal() {
            return qioSpendable.add(settledInboundAwaitingQIO)
                  .add(committedGuaranteedInbound);
        }
    }

    /** One immutable-by-convention stock projection shared by an entire evaluation slice. */
    public static final class EvaluationContext {

        private final UUID frequencyUUID;
        private final Map<PortableResourceDescriptor, BigInteger> spendable;
        private final Map<PortableResourceDescriptor, BigInteger> settled;
        private final Map<PortableResourceDescriptor, BigInteger> guaranteed;
        private final Map<PortableResourceDescriptor, UUID> activeMaintenanceJobs;

        private EvaluationContext(UUID frequencyUUID,
              Map<PortableResourceDescriptor, BigInteger> spendable,
              Map<PortableResourceDescriptor, BigInteger> settled,
              Map<PortableResourceDescriptor, BigInteger> guaranteed,
              Map<PortableResourceDescriptor, UUID> activeMaintenanceJobs) {
            this.frequencyUUID = frequencyUUID;
            this.spendable = spendable;
            this.settled = settled;
            this.guaranteed = guaranteed;
            this.activeMaintenanceJobs = activeMaintenanceJobs;
        }

        @Nonnull
        public static EvaluationContext create(@Nonnull QIOProcessingNetworkData network,
              @Nonnull QIOStorageSnapshot storage) {
            Objects.requireNonNull(network, "network");
            Objects.requireNonNull(storage, "storage");
            if (!network.getFrequencyUUID().equals(storage.getFrequencyUUID())) {
                throw new IllegalArgumentException("Maintenance storage targets another frequency");
            }
            Map<PortableResourceDescriptor, BigInteger> spendable = new LinkedHashMap<>();
            for (QIOStorageEntry entry : storage.getEntries()) {
                if (entry == null) continue;
                PortableResourceDescriptor resource = PortableResourceDescriptor.fromStorageEntry(entry);
                spendable.merge(resource, entry.getExactAvailableAmount(), BigInteger::add);
            }

            return create(network, spendable, null);
        }

        /** Builds a projection for only the resources selected by one bounded evaluation slice. */
        @Nonnull
        public static EvaluationContext createProjected(@Nonnull QIOProcessingNetworkData network,
              @Nonnull Map<PortableResourceDescriptor, BigInteger> projectedSpendable) {
            Objects.requireNonNull(network, "network");
            Objects.requireNonNull(projectedSpendable, "projectedSpendable");
            Map<PortableResourceDescriptor, BigInteger> spendable = new LinkedHashMap<>();
            for (Map.Entry<PortableResourceDescriptor, BigInteger> entry :
                  projectedSpendable.entrySet()) {
                PortableResourceDescriptor resource = Objects.requireNonNull(entry.getKey(),
                      "projected resource");
                spendable.put(resource, requireNonNegative(entry.getValue(),
                      "projected spendable"));
            }
            return create(network, spendable, spendable.keySet());
        }

        private static EvaluationContext create(QIOProcessingNetworkData network,
              Map<PortableResourceDescriptor, BigInteger> spendable,
              @Nullable java.util.Set<PortableResourceDescriptor> projectedResources) {

            Map<UUID, Map<PortableResourceDescriptor, BigInteger>> creditedByJob =
                  new LinkedHashMap<>();
            for (QIODurableTransferRecord transfer : network.getDurableTransfers()) {
                UUID jobId = transfer.getOwnerJobId();
                if (jobId == null || transfer.getType() != QIODurableTransferRecord.Type.JOB_TO_QIO ||
                      transfer.getResolution() != QIODurableTransferRecord.Resolution.NONE ||
                      transfer.getPhase() != QIODurableTransferRecord.Phase.DESTINATION_CREDITED) {
                    continue;
                }
                Map<PortableResourceDescriptor, BigInteger> credited = creditedByJob
                      .computeIfAbsent(jobId, ignored -> new LinkedHashMap<>());
                transfer.getResources().forEach((resource, amount) -> {
                    if (projectedResources == null || projectedResources.contains(resource)) {
                        credited.merge(resource, BigInteger.valueOf(amount), BigInteger::add);
                    }
                });
            }

            Map<PortableResourceDescriptor, BigInteger> settled = new LinkedHashMap<>();
            Map<PortableResourceDescriptor, BigInteger> guaranteed = new LinkedHashMap<>();
            Map<PortableResourceDescriptor, UUID> activeMaintenanceJobs = new LinkedHashMap<>();
            for (QIOCraftingJob job : network.getJobs()) {
                if (!contributesGuaranteedRoot(job)) continue;
                PortableResourceDescriptor resource = job.getActivePlan().getRootResource();
                if (projectedResources != null && !projectedResources.contains(resource)) {
                    continue;
                }
                if (job.getSource() == QIOCraftingJobSource.MAINTENANCE) {
                    activeMaintenanceJobs.putIfAbsent(resource, job.getJobId());
                }
                BigInteger remaining = BigInteger.valueOf(job.getRemainingGuaranteedRootAmount());
                BigInteger alreadyCredited = creditedByJob
                      .getOrDefault(job.getJobId(), Collections.emptyMap())
                      .getOrDefault(resource, BigInteger.ZERO).min(remaining);
                remaining = remaining.subtract(alreadyCredited);
                QIOJobBuffer buffer = network.getJobBuffer(job.getJobId());
                BigInteger settledForJob = buffer == null ? BigInteger.ZERO :
                      settledRootInBuffer(job, buffer, resource).min(remaining);
                settled.merge(resource, settledForJob, BigInteger::add);
                guaranteed.merge(resource, remaining.subtract(settledForJob), BigInteger::add);
            }
            return new EvaluationContext(network.getFrequencyUUID(), spendable, settled,
                  guaranteed, activeMaintenanceJobs);
        }

        private StockAndOrder stock(PortableResourceDescriptor resource) {
            return new StockAndOrder(new EffectiveStock(
                  spendable.getOrDefault(resource, BigInteger.ZERO),
                  settled.getOrDefault(resource, BigInteger.ZERO),
                  guaranteed.getOrDefault(resource, BigInteger.ZERO)),
                  activeMaintenanceJobs.get(resource));
        }
    }

    public static final class Decision {

        private final Status status;
        private final EffectiveStock effectiveStock;
        private final List<QIOMaintenanceRule> triggeredRules;
        private final long requestAmount;
        private final long jobPriority;
        private final long retryAtTick;
        @Nullable
        private final UUID activeJobId;

        private Decision(Status status, EffectiveStock effectiveStock,
              List<QIOMaintenanceRule> triggeredRules, long requestAmount, long jobPriority,
              long retryAtTick, @Nullable UUID activeJobId) {
            this.status = Objects.requireNonNull(status, "status");
            this.effectiveStock = Objects.requireNonNull(effectiveStock, "effectiveStock");
            this.triggeredRules = Collections.unmodifiableList(new ArrayList<>(triggeredRules));
            this.requestAmount = requestAmount;
            this.jobPriority = jobPriority;
            this.retryAtTick = retryAtTick;
            this.activeJobId = activeJobId;
        }

        @Nonnull
        public Status getStatus() {
            return status;
        }

        @Nonnull
        public EffectiveStock getEffectiveStock() {
            return effectiveStock;
        }

        @Nonnull
        public List<QIOMaintenanceRule> getTriggeredRules() {
            return triggeredRules;
        }

        public long getRequestAmount() {
            return requestAmount;
        }

        public long getJobPriority() {
            return jobPriority;
        }

        public long getRetryAtTick() {
            return retryAtTick;
        }

        @Nullable
        public UUID getActiveJobId() {
            return activeJobId;
        }
    }

    private QIOMaintenanceEvaluator() {
    }

    @Nonnull
    public static Decision evaluate(@Nonnull QIOProcessingNetworkData network,
          @Nonnull QIOStorageSnapshot storage, @Nonnull PortableResourceDescriptor resource,
          @Nonnull List<QIOMaintenanceRule> group, long serverBatchCap, long currentTick) {
        return evaluate(network, EvaluationContext.create(network, storage), resource, group,
              serverBatchCap, currentTick);
    }

    @Nonnull
    public static Decision evaluate(@Nonnull QIOProcessingNetworkData network,
          @Nonnull EvaluationContext context, @Nonnull PortableResourceDescriptor resource,
          @Nonnull List<QIOMaintenanceRule> group, long serverBatchCap, long currentTick) {
        Objects.requireNonNull(network, "network");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(resource, "resource");
        Objects.requireNonNull(group, "group");
        if (!network.getFrequencyUUID().equals(context.frequencyUUID) ||
              serverBatchCap <= 0 || currentTick < 0) {
            throw new IllegalArgumentException("Invalid maintenance evaluation input");
        }
        List<QIOMaintenanceRule> ordered = new ArrayList<>(group);
        ordered.sort(Comparator.comparing(rule -> rule.getRuleId().toString()));
        for (QIOMaintenanceRule rule : ordered) {
            if (!resource.equals(rule.getResource())) {
                throw new IllegalArgumentException("Maintenance rule group contains another resource");
            }
        }

        StockAndOrder stock = context.stock(resource);
        List<QIOMaintenanceRule> triggered = new ArrayList<>();
        for (QIOMaintenanceRule rule : ordered) {
            if (rule.isEnabled() && stock.effectiveStock.getTotal().compareTo(
                  BigInteger.valueOf(rule.getTriggerAmount())) < 0) {
                triggered.add(rule);
            }
        }
        if (stock.activeMaintenanceJobId != null) {
            return new Decision(Status.ACTIVE_ORDER, stock.effectiveStock, triggered, 0, 0,
                  0, stock.activeMaintenanceJobId);
        }
        if (triggered.isEmpty()) {
            return new Decision(Status.NOT_TRIGGERED, stock.effectiveStock, triggered, 0, 0,
                  0, null);
        }

        long retryAtTick = 0;
        for (QIOMaintenanceRule rule : triggered) {
            retryAtTick = Math.max(retryAtTick, rule.getNextRetryTick());
        }
        if (currentTick < retryAtTick) {
            return new Decision(Status.RETRY_WAIT, stock.effectiveStock, triggered, 0, 0,
                  retryAtTick, null);
        }

        long effectivePriority = Long.MIN_VALUE;
        long effectiveBatchCap = serverBatchCap;
        for (QIOMaintenanceRule rule : triggered) {
            effectivePriority = Math.max(effectivePriority, rule.getJobPriority());
            effectiveBatchCap = Math.min(effectiveBatchCap, rule.getMaximumSingleRequest());
        }
        // The configured amount is a real batch size. Counting guaranteed inbound above keeps
        // the batch from being submitted twice while still allowing deliberate target overshoot.
        long requestAmount = effectiveBatchCap;
        if (requestAmount <= 0) {
            return new Decision(Status.NOT_TRIGGERED, stock.effectiveStock, triggered, 0, 0,
                  0, null);
        }
        return new Decision(Status.REQUEST, stock.effectiveStock, triggered, requestAmount,
              effectivePriority, 0, null);
    }

    private static StockAndOrder calculateEffectiveStock(QIOProcessingNetworkData network,
          QIOStorageSnapshot storage, PortableResourceDescriptor resource) {
        BigInteger spendable = BigInteger.ZERO;
        for (QIOStorageEntry entry : storage.getEntries()) {
            if (entry != null && resource.equals(PortableResourceDescriptor.fromStorageEntry(entry))) {
                spendable = spendable.add(entry.getExactAvailableAmount());
            }
        }

        BigInteger settled = BigInteger.ZERO;
        BigInteger guaranteed = BigInteger.ZERO;
        UUID activeMaintenanceJobId = null;
        for (QIOCraftingJob job : network.getJobs()) {
            if (!contributesGuaranteedRoot(job, resource)) {
                continue;
            }
            if (job.getSource() == QIOCraftingJobSource.MAINTENANCE &&
                  activeMaintenanceJobId == null) {
                activeMaintenanceJobId = job.getJobId();
            }
            BigInteger remaining = BigInteger.valueOf(job.getRemainingGuaranteedRootAmount());
            BigInteger alreadyCredited = creditedToQIO(network, job.getJobId(), resource)
                  .min(remaining);
            remaining = remaining.subtract(alreadyCredited);
            QIOJobBuffer buffer = network.getJobBuffer(job.getJobId());
            BigInteger settledForJob = buffer == null ? BigInteger.ZERO :
                  settledRootInBuffer(job, buffer, resource).min(remaining);
            settled = settled.add(settledForJob);
            guaranteed = guaranteed.add(remaining.subtract(settledForJob));
        }
        return new StockAndOrder(new EffectiveStock(spendable, settled, guaranteed),
              activeMaintenanceJobId);
    }

    private static boolean contributesGuaranteedRoot(QIOCraftingJob job,
          PortableResourceDescriptor resource) {
        return contributesGuaranteedRoot(job) &&
              resource.equals(job.getActivePlan().getRootResource());
    }

    private static boolean contributesGuaranteedRoot(QIOCraftingJob job) {
        return !job.getState().isTerminal() && !job.isCancellationRequested() &&
              job.getState() != mekanism.qioprocessing.common.content.job.QIOCraftingJobState.CANCEL_REQUESTED &&
              job.getState() != mekanism.qioprocessing.common.content.job.QIOCraftingJobState.RETURNING &&
              job.getState() != mekanism.qioprocessing.common.content.job.QIOCraftingJobState.OPERATION_CONTAMINATED &&
              job.getState() != mekanism.qioprocessing.common.content.job.QIOCraftingJobState.ORPHANED_FREQUENCY &&
              job.getRemainingGuaranteedRootAmount() > 0;
    }

    private static BigInteger creditedToQIO(QIOProcessingNetworkData network, UUID jobId,
          PortableResourceDescriptor resource) {
        BigInteger credited = BigInteger.ZERO;
        for (QIODurableTransferRecord transfer : network.getDurableTransfers()) {
            if (jobId.equals(transfer.getOwnerJobId()) &&
                  transfer.getType() == QIODurableTransferRecord.Type.JOB_TO_QIO &&
                  transfer.getResolution() == QIODurableTransferRecord.Resolution.NONE &&
                  transfer.getPhase() == QIODurableTransferRecord.Phase.DESTINATION_CREDITED) {
                credited = credited.add(BigInteger.valueOf(
                      transfer.getResources().getOrDefault(resource, 0L)));
            }
        }
        return credited;
    }

    private static BigInteger settledRootInBuffer(QIOCraftingJob job, QIOJobBuffer buffer,
          PortableResourceDescriptor resource) {
        BigInteger settled = BigInteger.valueOf(buffer.get(QIOJobBuffer.Compartment.SETTLED_OUTPUT,
              resource)).add(BigInteger.valueOf(buffer.get(QIOJobBuffer.Compartment.RETURNING,
              resource)));
        if (job.areAllStepsComplete()) {
            settled = settled.add(BigInteger.valueOf(buffer.get(QIOJobBuffer.Compartment.PRODUCED,
                  resource)));
        }
        return settled;
    }

    private static BigInteger requireNonNegative(BigInteger value, String name) {
        Objects.requireNonNull(value, name);
        if (value.signum() < 0) {
            throw new IllegalArgumentException(name + " cannot be negative");
        }
        return value;
    }

    private static final class StockAndOrder {

        private final EffectiveStock effectiveStock;
        @Nullable
        private final UUID activeMaintenanceJobId;

        private StockAndOrder(EffectiveStock effectiveStock,
              @Nullable UUID activeMaintenanceJobId) {
            this.effectiveStock = effectiveStock;
            this.activeMaintenanceJobId = activeMaintenanceJobId;
        }
    }
}
