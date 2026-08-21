package mekanism.qioprocessing.common.content.material;

import mekanism.api.qio.external.IQIOStorageView;
import mekanism.api.qio.external.QIOClaimBacking;
import mekanism.api.qio.external.QIOClaimRequest;
import mekanism.api.qio.external.QIOClaimResult;
import mekanism.api.qio.external.QIOResourceClaim;
import mekanism.api.qio.external.QIOStorageEntry;
import mekanism.api.qio.external.QIOStorageSnapshot;
import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.MekanismQIOProcessing;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkData;
import mekanism.qioprocessing.common.content.job.QIOCraftingJob;
import mekanism.qioprocessing.common.content.job.QIOCraftingJobState;
import mekanism.qioprocessing.common.content.job.QIOPlanRevisionTransition;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Replays the durable atomic claim handoff before a pending plan becomes active. */
/**
 * QIO 处理模块中的 QIOPlanReassignmentCoordinator 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIOPlanReassignmentCoordinator {
    public enum Outcome { ACTIVATED, RETRY_REQUIRED, WAITING_ACCESS }
    private QIOPlanReassignmentCoordinator() {}

    @Nonnull
    public static Outcome advance(@Nonnull QIOProcessingNetworkData network,
          @Nonnull UUID jobId, @Nonnull IQIOStorageView view,
          @Nonnull QIOMaterialClaimCoordinator.PersistenceBarrier barrier) throws IOException {
        Objects.requireNonNull(network, "network"); Objects.requireNonNull(jobId, "jobId");
        Objects.requireNonNull(view, "view"); Objects.requireNonNull(barrier, "barrier");
        QIOCraftingJob job = network.getJob(jobId);
        if (job == null || job.getState() != QIOCraftingJobState.REPLAN_REQUIRED ||
              job.getRevisionTransition() == null || network.hasPhysicalJobAssets(jobId)) {
            throw new IllegalStateException("QIO job is not ready for claim reassignment");
        }
        if (!view.isValid() || !network.getFrequencyUUID().equals(view.getFrequencyUUID())) {
            return Outcome.WAITING_ACCESS;
        }
        QIOPlanRevisionTransition transition = job.getRevisionTransition();
        if (!transition.hasClaimIntent()) {
            QIOStorageSnapshot snapshot = view.getSnapshot();
            SnapshotIndex index = SnapshotIndex.create(snapshot);
            QIOMaterialCommitment old = network.getCommitment(jobId);
            if (old == null) throw new IllegalStateException("QIO replan lost its material commitment");
            QIOResourceClaim source = view.getClaim(old.getClaimId());
            if (source != null && (!MekanismQIOProcessing.MODID.equals(source.getOwnerNamespace()) ||
                  !job.getClaimOwnerId().equals(source.getOwnerId()))) {
                return Outcome.WAITING_ACCESS;
            }
            Map<PortableResourceDescriptor, Long> sourceAmounts = source == null ?
                  Collections.emptyMap() : index.translate(source.getResourceAmounts());
            if (sourceAmounts == null) return Outcome.WAITING_ACCESS;
            QIOClaimBacking sourceBacking = source == null ? null :
                  view.getClaimBacking(source.getClaimId());
            Map<PortableResourceDescriptor, Long> sourceBacked = source == null ?
                  Collections.emptyMap() : sourceBacking == null ? null :
                  index.translate(sourceBacking.getBackedAmounts());
            if (sourceBacked == null) return Outcome.WAITING_ACCESS;
            Map<PortableResourceDescriptor, Long> desired = QIOCandidateClaimResolver.resolve(
                  transition.getPendingPlan().getMaterialRequirements(), sourceAmounts,
                  sourceBacked,
                  index.availableAmounts());
            if (desired.isEmpty() && source == null) {
                network.completeReplan(jobId, Collections.emptyMap(), snapshot.getClaimRevision());
                barrier.persist(network);
                return Outcome.ACTIVATED;
            }
            QIOPlanRevisionTransition.ClaimMode mode = desired.isEmpty() ?
                  QIOPlanRevisionTransition.ClaimMode.RELEASE : source == null ?
                        QIOPlanRevisionTransition.ClaimMode.CREATE :
                        QIOPlanRevisionTransition.ClaimMode.REASSIGN;
            network.prepareReplanClaim(jobId, mode, UUID.randomUUID(),
                  mode == QIOPlanRevisionTransition.ClaimMode.REASSIGN ? source.getClaimId() : null,
                  snapshot.getContentsRevision(), snapshot.getClaimRevision(), desired);
            barrier.persist(network);
            transition = job.getRevisionTransition();
        }

        QIOStorageSnapshot current = view.getSnapshot();
        SnapshotIndex currentIndex = SnapshotIndex.create(current);
        Map<UUID, Long> target = currentIndex.resolve(transition.getTargetClaimAmounts());
        if (target == null && transition.getClaimMode() != QIOPlanRevisionTransition.ClaimMode.RELEASE) {
            network.clearReplanClaimIntent(jobId);
            barrier.persist(network);
            return Outcome.RETRY_REQUIRED;
        }
        QIOClaimRequest request = switch (transition.getClaimMode()) {
            case CREATE -> QIOClaimRequest.createOrAdjust(transition.getRequestId(),
                  transition.getTargetClaimId(), MekanismQIOProcessing.MODID,
                  job.getPendingClaimOwnerId(), job.getBasePriority(), job.getEnqueueSequence(),
                  transition.getExpectedContentsRevision(), transition.getExpectedClaimRevision(), target);
            case REASSIGN -> QIOClaimRequest.reassign(transition.getRequestId(),
                  transition.getSourceClaimId(), transition.getTargetClaimId(),
                  MekanismQIOProcessing.MODID, job.getPendingClaimOwnerId(),
                  job.getBasePriority(), job.getEnqueueSequence(),
                  transition.getExpectedContentsRevision(), transition.getExpectedClaimRevision(), target);
            case RELEASE -> QIOClaimRequest.release(transition.getRequestId(),
                  network.getCommitment(jobId).getClaimId(), MekanismQIOProcessing.MODID,
                  job.getClaimOwnerId(), transition.getExpectedClaimRevision(), Collections.emptyMap());
        };
        QIOClaimResult result = view.submitClaim(request);
        if (result.getStatus() == QIOClaimResult.Status.STALE_CONTENTS ||
              result.getStatus() == QIOClaimResult.Status.STALE_CLAIMS) {
            network.clearReplanClaimIntent(jobId);
            barrier.persist(network);
            return Outcome.RETRY_REQUIRED;
        }
        if (!result.isSuccess() && !(transition.getClaimMode() ==
              QIOPlanRevisionTransition.ClaimMode.RELEASE &&
              result.getStatus() == QIOClaimResult.Status.NOT_FOUND)) {
            return Outcome.WAITING_ACCESS;
        }
        Map<PortableResourceDescriptor, Long> committed = Collections.emptyMap();
        if (transition.getClaimMode() != QIOPlanRevisionTransition.ClaimMode.RELEASE) {
            QIOClaimBacking backing = view.getClaimBacking(transition.getTargetClaimId());
            QIOStorageSnapshot after = view.getSnapshot();
            SnapshotIndex afterIndex = SnapshotIndex.create(after);
            if (backing == null || backing.getClaimRevision() != after.getClaimRevision()) {
                return Outcome.WAITING_ACCESS;
            }
            committed = afterIndex.translate(backing.getBackedAmounts());
            if (committed == null) return Outcome.WAITING_ACCESS;
            current = after;
        } else {
            current = view.getSnapshot();
        }
        network.completeReplan(jobId, committed, current.getClaimRevision());
        barrier.persist(network);
        return Outcome.ACTIVATED;
    }

    private static final class SnapshotIndex {
        private final Map<PortableResourceDescriptor, QIOStorageEntry> byDescriptor = new LinkedHashMap<>();
        private final Map<UUID, PortableResourceDescriptor> byUUID = new LinkedHashMap<>();
        private static SnapshotIndex create(QIOStorageSnapshot snapshot) {
            SnapshotIndex index = new SnapshotIndex();
            for (QIOStorageEntry entry : snapshot.getEntries()) {
                PortableResourceDescriptor descriptor = PortableResourceDescriptor.fromStorageEntry(entry);
                index.byDescriptor.put(descriptor, entry); index.byUUID.put(entry.getResourceUUID(), descriptor);
            }
            return index;
        }
        @Nullable private Map<UUID, Long> resolve(Map<PortableResourceDescriptor, Long> amounts) {
            Map<UUID, Long> resolved = new LinkedHashMap<>();
            for (Map.Entry<PortableResourceDescriptor, Long> amount : amounts.entrySet()) {
                QIOStorageEntry entry = byDescriptor.get(amount.getKey()); if (entry == null) return null;
                resolved.put(entry.getResourceUUID(), amount.getValue());
            }
            return resolved;
        }
        @Nullable private Map<PortableResourceDescriptor, Long> translate(Map<UUID, Long> amounts) {
            Map<PortableResourceDescriptor, Long> translated = new LinkedHashMap<>();
            for (Map.Entry<UUID, Long> amount : amounts.entrySet()) {
                PortableResourceDescriptor descriptor = byUUID.get(amount.getKey()); if (descriptor == null) return null;
                translated.put(descriptor, amount.getValue());
            }
            return translated;
        }
        private Map<PortableResourceDescriptor, Long> availableAmounts() {
            Map<PortableResourceDescriptor, Long> amounts = new LinkedHashMap<>();
            for (Map.Entry<PortableResourceDescriptor, QIOStorageEntry> entry :
                  byDescriptor.entrySet()) {
                BigInteger available = entry.getValue().getExactAvailableAmount();
                long amount = available.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) >= 0 ?
                      Long.MAX_VALUE : available.longValue();
                if (amount > 0) amounts.put(entry.getKey(), amount);
            }
            return amounts;
        }
    }
}
