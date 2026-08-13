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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Serial main-thread coordinator for durable, partial material claims. */
public final class QIOMaterialClaimCoordinator {

    public enum Outcome {
        FULLY_COMMITTED,
        PARTIALLY_COMMITTED,
        RETRY_REQUIRED,
        WAITING_ACCESS
    }

    public enum CancellationOutcome {
        CANCELLED,
        RETRY_REQUIRED,
        WAITING_ACCESS
    }

    @FunctionalInterface
    public interface PersistenceBarrier {

        void persist(QIOProcessingNetworkData network) throws IOException;
    }

    public static final class RefreshResult {

        private final Outcome outcome;
        @Nullable
        private final QIOClaimResult claimResult;

        private RefreshResult(Outcome outcome, @Nullable QIOClaimResult claimResult) {
            this.outcome = outcome;
            this.claimResult = claimResult;
        }

        @Nonnull
        public Outcome getOutcome() {
            return outcome;
        }

        @Nullable
        public QIOClaimResult getClaimResult() {
            return claimResult;
        }
    }

    private QIOMaterialClaimCoordinator() {
    }

    @Nonnull
    public static RefreshResult refresh(@Nonnull QIOProcessingNetworkData network,
          @Nonnull UUID jobId, @Nonnull IQIOStorageView view,
          @Nonnull PersistenceBarrier persistenceBarrier) throws IOException {
        Objects.requireNonNull(network, "network");
        Objects.requireNonNull(jobId, "jobId");
        Objects.requireNonNull(view, "view");
        Objects.requireNonNull(persistenceBarrier, "persistenceBarrier");
        if (!view.isValid() || !network.getFrequencyUUID().equals(view.getFrequencyUUID())) {
            network.markClaimNeedsReconciliation(jobId);
            persistenceBarrier.persist(network);
            return new RefreshResult(Outcome.WAITING_ACCESS, null);
        }
        QIOCraftingJob job = requireJob(network, jobId);
        QIOMaterialCommitment commitment = requireCommitment(network, jobId);
        if (commitment.getState() != QIOMaterialCommitment.State.ACTIVE &&
              commitment.getState() != QIOMaterialCommitment.State.NEEDS_RECONCILIATION) {
            throw new IllegalStateException("Cannot refresh material commitment in state " +
                  commitment.getState());
        }

        QIOStorageSnapshot snapshot = view.getSnapshot();
        SnapshotIndex index = SnapshotIndex.create(snapshot);
        ClaimObservation observation = readOwnedClaim(view, commitment, job, index);
        if (observation == null) {
            network.markClaimNeedsReconciliation(jobId);
            persistenceBarrier.persist(network);
            return new RefreshResult(Outcome.WAITING_ACCESS, null);
        }

        QIOPendingClaimMutation pending = commitment.getPendingMutation();
        if (pending == null) {
            Map<PortableResourceDescriptor, Long> desiredClaim = calculateTarget(commitment,
                  observation.claimed, observation.backed, index);
            if (desiredClaim.equals(observation.claimed) &&
                observation.priority == commitment.getPriority()) {
                if (commitment.getState() == QIOMaterialCommitment.State.ACTIVE &&
                      commitment.getCommittedAmounts().equals(observation.backed)) {
                    return resultFor(commitment, null);
                }
                network.applyActiveClaim(jobId, observation.backed, snapshot.getClaimRevision());
                persistenceBarrier.persist(network);
                return resultFor(network.getCommitment(jobId), null);
            }
            pending = new QIOPendingClaimMutation(UUID.randomUUID(),
                  QIOPendingClaimMutation.Mode.CREATE_OR_ADJUST, snapshot.getContentsRevision(),
                  snapshot.getClaimRevision(), desiredClaim);
            network.prepareClaimMutation(jobId, pending);
            // This save is the durable intent boundary. The core mutation must never precede it.
            persistenceBarrier.persist(network);
        }

        Map<UUID, Long> resourceAmounts = index.resolve(pending.getAmounts());
        if (resourceAmounts == null || pending.getMode() == QIOPendingClaimMutation.Mode.CREATE_OR_ADJUST &&
              resourceAmounts.isEmpty()) {
            network.markClaimNeedsReconciliation(jobId);
            persistenceBarrier.persist(network);
            return new RefreshResult(Outcome.WAITING_ACCESS, null);
        }
        QIOClaimRequest request = switch (pending.getMode()) {
            case CREATE_OR_ADJUST -> QIOClaimRequest.createOrAdjust(pending.getRequestId(),
                  commitment.getClaimId(), MekanismQIOProcessing.MODID, job.getClaimOwnerId(),
                  commitment.getPriority(), commitment.getEnqueueSequence(),
                  pending.getExpectedContentsRevision(), pending.getExpectedClaimRevision(), resourceAmounts);
            case RELEASE -> QIOClaimRequest.release(pending.getRequestId(), commitment.getClaimId(),
                  MekanismQIOProcessing.MODID, job.getClaimOwnerId(),
                  pending.getExpectedClaimRevision(), resourceAmounts);
        };
        QIOClaimResult claimResult;
        try {
            claimResult = view.submitClaim(request);
        } catch (RuntimeException e) {
            // Keep the durable intent intact so the same request ID can be replayed after access returns.
            throw e;
        }

        if (claimResult.isSuccess()) {
            QIOStorageSnapshot currentSnapshot = view.getSnapshot();
            SnapshotIndex currentIndex = SnapshotIndex.create(currentSnapshot);
            ClaimObservation current = readOwnedClaim(view, commitment, job, currentIndex);
            if (current == null) {
                network.markClaimNeedsReconciliation(jobId);
                persistenceBarrier.persist(network);
                return new RefreshResult(Outcome.WAITING_ACCESS, claimResult);
            }
            network.applyActiveClaim(jobId, current.backed, currentSnapshot.getClaimRevision());
            persistenceBarrier.persist(network);
            return resultFor(network.getCommitment(jobId), claimResult);
        }

        if (claimResult.getStatus() == QIOClaimResult.Status.STALE_CONTENTS ||
              claimResult.getStatus() == QIOClaimResult.Status.STALE_CLAIMS) {
            QIOStorageSnapshot currentSnapshot = view.getSnapshot();
            SnapshotIndex currentIndex = SnapshotIndex.create(currentSnapshot);
            ClaimObservation current = readOwnedClaim(view, commitment,
                  job, currentIndex);
            if (current == null) {
                network.markClaimNeedsReconciliation(jobId);
                persistenceBarrier.persist(network);
                return new RefreshResult(Outcome.WAITING_ACCESS, claimResult);
            }
            network.applyActiveClaim(jobId, current.backed, currentSnapshot.getClaimRevision());
            persistenceBarrier.persist(network);
            return new RefreshResult(Outcome.RETRY_REQUIRED, claimResult);
        }

        network.markClaimNeedsReconciliation(jobId);
        persistenceBarrier.persist(network);
        return new RefreshResult(Outcome.WAITING_ACCESS, claimResult);
    }

    @Nonnull
    public static CancellationOutcome cancelUnreserved(@Nonnull QIOProcessingNetworkData network,
          @Nonnull UUID jobId, @Nonnull IQIOStorageView view,
          @Nonnull PersistenceBarrier persistenceBarrier) throws IOException {
        Objects.requireNonNull(network, "network");
        Objects.requireNonNull(jobId, "jobId");
        Objects.requireNonNull(view, "view");
        Objects.requireNonNull(persistenceBarrier, "persistenceBarrier");
        QIOCraftingJob job = requireJob(network, jobId);
        QIOMaterialCommitment commitment = requireCommitment(network, jobId);
        if (job.getExecutionSlotToken() != null || network.getJobBuffer(jobId) == null ||
              !network.getJobBuffer(jobId).isEmpty()) {
            throw new IllegalStateException("cancelUnreserved only accepts jobs without physical assets");
        }
        if (!view.isValid() || !network.getFrequencyUUID().equals(view.getFrequencyUUID())) {
            network.markClaimNeedsReconciliation(jobId);
            persistenceBarrier.persist(network);
            return CancellationOutcome.WAITING_ACCESS;
        }
        QIOResourceClaim existing = view.getClaim(commitment.getClaimId());
        if (existing == null) {
            network.completeClaimRelease(jobId, view.getClaimRevision());
            persistenceBarrier.persist(network);
            return CancellationOutcome.CANCELLED;
        }
        if (!MekanismQIOProcessing.MODID.equals(existing.getOwnerNamespace()) ||
              !job.getClaimOwnerId().equals(existing.getOwnerId())) {
            network.markClaimNeedsReconciliation(jobId);
            persistenceBarrier.persist(network);
            return CancellationOutcome.WAITING_ACCESS;
        }

        QIOPendingClaimMutation pending = commitment.getPendingMutation();
        if (pending == null) {
            QIOStorageSnapshot snapshot = view.getSnapshot();
            pending = new QIOPendingClaimMutation(UUID.randomUUID(),
                  QIOPendingClaimMutation.Mode.RELEASE, snapshot.getContentsRevision(),
                  snapshot.getClaimRevision(), Collections.emptyMap());
            network.prepareClaimRelease(jobId, pending);
            persistenceBarrier.persist(network);
        } else if (pending.getMode() != QIOPendingClaimMutation.Mode.RELEASE) {
            throw new IllegalStateException("A material claim adjustment must settle before cancellation");
        }

        QIOClaimResult result = view.submitClaim(QIOClaimRequest.release(pending.getRequestId(),
              commitment.getClaimId(), MekanismQIOProcessing.MODID, job.getClaimOwnerId(),
              pending.getExpectedClaimRevision(), Collections.emptyMap()));
        if (result.isSuccess() || result.getStatus() == QIOClaimResult.Status.NOT_FOUND &&
              view.getClaim(commitment.getClaimId()) == null) {
            network.completeClaimRelease(jobId, result.getClaimRevision());
            persistenceBarrier.persist(network);
            return CancellationOutcome.CANCELLED;
        }
        if (result.getStatus() == QIOClaimResult.Status.STALE_CLAIMS ||
              result.getStatus() == QIOClaimResult.Status.STALE_CONTENTS) {
            QIOStorageSnapshot currentSnapshot = view.getSnapshot();
            SnapshotIndex currentIndex = SnapshotIndex.create(currentSnapshot);
            ClaimObservation current = readOwnedClaim(view, commitment,
                  job, currentIndex);
            if (current != null) {
                network.applyActiveClaim(jobId, current.backed, currentSnapshot.getClaimRevision());
                persistenceBarrier.persist(network);
                return CancellationOutcome.RETRY_REQUIRED;
            }
        }
        network.markClaimNeedsReconciliation(jobId);
        persistenceBarrier.persist(network);
        return CancellationOutcome.WAITING_ACCESS;
    }

    @Nonnull
    private static RefreshResult resultFor(@Nullable QIOMaterialCommitment commitment,
          @Nullable QIOClaimResult result) {
        if (commitment == null) {
            throw new IllegalStateException("Material commitment disappeared during claim refresh");
        }
        return new RefreshResult(commitment.isFullyCommitted() ? Outcome.FULLY_COMMITTED :
              Outcome.PARTIALLY_COMMITTED, result);
    }

    @Nullable
    private static ClaimObservation readOwnedClaim(IQIOStorageView view,
          QIOMaterialCommitment commitment, QIOCraftingJob job, SnapshotIndex index) {
        QIOResourceClaim claim = view.getClaim(commitment.getClaimId());
        if (claim == null) {
            // A missing claim has no meaningful priority. Treat it as matching this job so an
            // empty desired claim remains a valid no-op instead of creating an invalid mutation.
            return ClaimObservation.empty(commitment.getPriority());
        }
        if (!MekanismQIOProcessing.MODID.equals(claim.getOwnerNamespace()) ||
              !job.getClaimOwnerId().equals(claim.getOwnerId())) {
            return null;
        }
        QIOClaimBacking backing = view.getClaimBacking(commitment.getClaimId());
        if (backing == null || backing.getContentsRevision() != index.contentsRevision ||
              backing.getClaimRevision() != index.claimRevision ||
              !sameClaim(claim, backing.getClaim())) {
            return null;
        }
        Map<PortableResourceDescriptor, Long> claimed = index.translate(claim.getResourceAmounts(),
              view);
        Map<PortableResourceDescriptor, Long> backed = index.translate(backing.getBackedAmounts(),
              view);
        if (claimed == null || backed == null) {
            return null;
        }
        if (!commitment.acceptsCommittedAmounts(claimed) ||
            !commitment.acceptsCommittedAmounts(backed)) {
            return null;
        }
        return new ClaimObservation(claimed, backed, claim.getPriority());
    }

    private static boolean sameClaim(QIOResourceClaim left, QIOResourceClaim right) {
        return left.getClaimId().equals(right.getClaimId()) &&
              left.getOwnerNamespace().equals(right.getOwnerNamespace()) &&
              left.getOwnerId().equals(right.getOwnerId()) &&
              left.getPriority() == right.getPriority() &&
              left.getEnqueueSequence() == right.getEnqueueSequence() &&
              left.getRevision() == right.getRevision() &&
              left.getState() == right.getState() &&
              left.getResourceAmounts().equals(right.getResourceAmounts());
    }

    @Nonnull
    private static Map<PortableResourceDescriptor, Long> calculateTarget(
          QIOMaterialCommitment commitment, Map<PortableResourceDescriptor, Long> claimed,
          Map<PortableResourceDescriptor, Long> backed, SnapshotIndex index) {
        return QIOCandidateClaimResolver.resolve(commitment.getMaterialRequirements(), claimed,
              backed, index.availableAmounts());
    }

    private static QIOCraftingJob requireJob(QIOProcessingNetworkData network, UUID jobId) {
        QIOCraftingJob job = network.getJob(jobId);
        if (job == null) {
            throw new IllegalArgumentException("Unknown QIO job " + jobId);
        }
        return job;
    }

    private static QIOMaterialCommitment requireCommitment(QIOProcessingNetworkData network,
          UUID jobId) {
        QIOMaterialCommitment commitment = network.getCommitment(jobId);
        if (commitment == null) {
            throw new IllegalArgumentException("Unknown material commitment for QIO job " + jobId);
        }
        return commitment;
    }

    private static final class SnapshotIndex {

        private final Map<PortableResourceDescriptor, QIOStorageEntry> byDescriptor = new LinkedHashMap<>();
        private final Map<UUID, PortableResourceDescriptor> byUUID = new LinkedHashMap<>();
        private final long contentsRevision;
        private final long claimRevision;

        private SnapshotIndex(long contentsRevision, long claimRevision) {
            this.contentsRevision = contentsRevision;
            this.claimRevision = claimRevision;
        }

        private static SnapshotIndex create(QIOStorageSnapshot snapshot) {
            SnapshotIndex index = new SnapshotIndex(snapshot.getContentsRevision(),
                  snapshot.getClaimRevision());
            for (QIOStorageEntry entry : snapshot.getEntries()) {
                PortableResourceDescriptor descriptor = PortableResourceDescriptor.fromStorageEntry(entry);
                QIOStorageEntry previous = index.byDescriptor.put(descriptor, entry);
                if (previous != null && !previous.getResourceUUID().equals(entry.getResourceUUID())) {
                    throw new IllegalStateException("QIO snapshot maps one portable resource to multiple UUIDs");
                }
                PortableResourceDescriptor old = index.byUUID.put(entry.getResourceUUID(), descriptor);
                if (old != null && !old.equals(descriptor)) {
                    throw new IllegalStateException("QIO snapshot UUID maps to multiple portable resources");
                }
            }
            return index;
        }

        private long getAvailable(PortableResourceDescriptor descriptor) {
            QIOStorageEntry entry = byDescriptor.get(descriptor);
            if (entry == null) {
                return 0;
            }
            BigInteger available = entry.getExactAvailableAmount();
            return available.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) >= 0 ?
                  Long.MAX_VALUE : available.longValue();
        }

        private Map<PortableResourceDescriptor, Long> availableAmounts() {
            Map<PortableResourceDescriptor, Long> amounts = new LinkedHashMap<>();
            for (Map.Entry<PortableResourceDescriptor, QIOStorageEntry> entry :
                  byDescriptor.entrySet()) {
                long available = getAvailable(entry.getKey());
                if (available > 0) amounts.put(entry.getKey(), available);
            }
            return amounts;
        }

        @Nullable
        private Map<UUID, Long> resolve(Map<PortableResourceDescriptor, Long> amounts) {
            Map<UUID, Long> resolved = new LinkedHashMap<>();
            for (Map.Entry<PortableResourceDescriptor, Long> entry : amounts.entrySet()) {
                QIOStorageEntry storageEntry = byDescriptor.get(entry.getKey());
                if (storageEntry == null) {
                    return null;
                }
                resolved.put(storageEntry.getResourceUUID(), entry.getValue());
            }
            return resolved;
        }

        @Nullable
        private Map<PortableResourceDescriptor, Long> translate(Map<UUID, Long> amounts,
              IQIOStorageView view) {
            Map<PortableResourceDescriptor, Long> translated = new LinkedHashMap<>();
            for (Map.Entry<UUID, Long> entry : amounts.entrySet()) {
                PortableResourceDescriptor descriptor = byUUID.get(entry.getKey());
                if (descriptor == null) {
                    QIOStorageEntry storageEntry = view.getResource(entry.getKey());
                    if (storageEntry == null) {
                        return null;
                    }
                    descriptor = PortableResourceDescriptor.fromStorageEntry(storageEntry);
                    PortableResourceDescriptor previous = byUUID.put(entry.getKey(), descriptor);
                    QIOStorageEntry old = byDescriptor.put(descriptor, storageEntry);
                    if (previous != null && !previous.equals(descriptor) || old != null &&
                          !old.getResourceUUID().equals(entry.getKey())) {
                        return null;
                    }
                }
                if (descriptor == null || translated.put(descriptor, entry.getValue()) != null) {
                    return null;
                }
            }
            return translated;
        }
    }

    private static final class ClaimObservation {

        private final Map<PortableResourceDescriptor, Long> claimed;
        private final Map<PortableResourceDescriptor, Long> backed;
        private final long priority;

        private ClaimObservation(Map<PortableResourceDescriptor, Long> claimed,
              Map<PortableResourceDescriptor, Long> backed, long priority) {
            this.claimed = claimed;
            this.backed = backed;
            this.priority = priority;
        }

        private static ClaimObservation empty(long priority) {
            return new ClaimObservation(Collections.emptyMap(), Collections.emptyMap(), priority);
        }
    }
}
