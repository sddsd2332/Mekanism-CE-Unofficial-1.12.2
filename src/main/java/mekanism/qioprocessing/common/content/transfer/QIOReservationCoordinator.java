package mekanism.qioprocessing.common.content.transfer;

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
import mekanism.qioprocessing.common.content.material.QIOMaterialClaimCoordinator;
import mekanism.qioprocessing.common.content.material.QIOMaterialCommitment;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Converts a complete logical claim into a durable job-owned resource buffer. */
public final class QIOReservationCoordinator {

    public enum Outcome {
        READY,
        RETRY_REQUIRED,
        WAITING_MATERIALS,
        WAITING_ACCESS
    }

    private QIOReservationCoordinator() {
    }

    @Nonnull
    public static Outcome reserve(@Nonnull QIOProcessingNetworkData network,
          @Nonnull UUID jobId, @Nonnull IQIOStorageView view,
          @Nonnull QIOMaterialClaimCoordinator.PersistenceBarrier persistenceBarrier)
          throws IOException {
        Objects.requireNonNull(network, "network");
        Objects.requireNonNull(jobId, "jobId");
        Objects.requireNonNull(view, "view");
        Objects.requireNonNull(persistenceBarrier, "persistenceBarrier");
        QIOCraftingJob job = requireJob(network, jobId);
        QIOMaterialCommitment commitment = requireCommitment(network, jobId);
        if (job.getState() == QIOCraftingJobState.READY &&
              commitment.getState() == QIOMaterialCommitment.State.CONSUMED) {
            return Outcome.READY;
        }
        if (job.getState() != QIOCraftingJobState.RESERVING ||
              job.getExecutionSlotToken() == null || !commitment.isFullyCommitted()) {
            throw new IllegalStateException("QIO job is not ready to convert its claim into a reservation");
        }

        QIODurableTransferRecord transfer = network.getReservationTransfer(jobId);
        if (commitment.getRequiredAmounts().isEmpty()) {
            if (transfer != null) {
                throw new IllegalStateException("Empty QIO reservation unexpectedly has a transfer log");
            }
            if (!view.isValid() || !network.getFrequencyUUID().equals(view.getFrequencyUUID())) {
                network.releaseExecutionSlot(jobId, QIOCraftingJobState.WAITING_ACCESS);
                persistenceBarrier.persist(network);
                return Outcome.WAITING_ACCESS;
            }
            network.completeEmptyReservation(jobId, view.getClaimRevision());
            persistenceBarrier.persist(network);
            return Outcome.READY;
        }

        if (transfer == null) {
            if (!view.isValid() || !network.getFrequencyUUID().equals(view.getFrequencyUUID())) {
                network.releaseExecutionSlot(jobId, QIOCraftingJobState.WAITING_ACCESS);
                persistenceBarrier.persist(network);
                return Outcome.WAITING_ACCESS;
            }
            QIOStorageSnapshot snapshot = view.getSnapshot();
            QIOResourceClaim claim = view.getClaim(commitment.getClaimId());
            Map<PortableResourceDescriptor, UUID> bindings = resolveBindings(view, snapshot,
                  claim, commitment.getCommittedAmounts());
            if (bindings == null || !claimMatches(claim, job,
                  commitment, bindings)) {
                network.releaseExecutionSlot(jobId, QIOCraftingJobState.WAITING_ACCESS);
                persistenceBarrier.persist(network);
                return Outcome.WAITING_ACCESS;
            }
            Map<PortableResourceDescriptor, Long> backed = readBackedAmounts(view, job,
                  commitment, bindings);
            if (backed == null) {
                network.releaseExecutionSlot(jobId, QIOCraftingJobState.WAITING_ACCESS);
                persistenceBarrier.persist(network);
                return Outcome.WAITING_ACCESS;
            }
            if (!backed.equals(commitment.getCommittedAmounts())) {
                network.applyActiveClaim(jobId, backed, view.getClaimRevision());
                network.releaseExecutionSlot(jobId, QIOCraftingJobState.WAITING_MATERIALS);
                persistenceBarrier.persist(network);
                return Outcome.WAITING_MATERIALS;
            }
            Map<PortableResourceDescriptor, BigInteger> baselines = resolveBaselines(view,
                  commitment.getCommittedAmounts(), bindings);
            if (baselines == null) {
                network.releaseExecutionSlot(jobId, QIOCraftingJobState.WAITING_ACCESS);
                persistenceBarrier.persist(network);
                return Outcome.WAITING_ACCESS;
            }
            transfer = QIODurableTransferRecord.qioToJob(UUID.randomUUID(), UUID.randomUUID(),
                  network.getFrequencyUUID(), jobId, job.getActivePlan().getRevision(),
                  commitment.getCommittedAmounts(), bindings, baselines,
                  snapshot.getContentsRevision(), snapshot.getClaimRevision());
            network.addDurableTransfer(transfer);
            persistenceBarrier.persist(network);
        }

        if (transfer.getPhase() == QIODurableTransferRecord.Phase.PREPARED) {
            if (!view.isValid() || !network.getFrequencyUUID().equals(view.getFrequencyUUID())) {
                network.abortPreparedReservation(transfer.getTransferId(),
                      QIOCraftingJobState.WAITING_ACCESS);
                persistenceBarrier.persist(network);
                return Outcome.WAITING_ACCESS;
            }
            Map<UUID, Long> resolved = resolveAmounts(transfer);
            Map<UUID, BigInteger> baselines = resolveBaselines(transfer);
            QIOClaimRequest consume = QIOClaimRequest.consumeReconciled(
                  transfer.getRequestId(), transfer.getTransferId(), commitment.getClaimId(),
                  MekanismQIOProcessing.MODID, job.getClaimOwnerId(),
                  transfer.getExpectedContentsRevision(), transfer.getExpectedClaimRevision(),
                  resolved, baselines);
            QIOClaimResult result = view.submitClaim(consume);
            if (result.isSuccess() && !result.getConsumedAmounts().equals(resolved)) {
                // A successful core consume is ownership-changing. Keep the PREPARED record and slot
                // for diagnostics rather than incorrectly treating it as a no-debit failure.
                throw new IllegalStateException("QIO claim consume receipt differs from its durable intent");
            }
            if (!result.isSuccess()) {
                boolean stillOwned = claimMatches(view.getClaim(commitment.getClaimId()), job,
                      commitment, transfer.getQIOResourceUUIDs());
                if (stillOwned) {
                    Map<PortableResourceDescriptor, Long> backed = readBackedAmounts(view, job,
                          commitment, transfer.getQIOResourceUUIDs());
                    if (backed == null) {
                        network.abortPreparedReservation(transfer.getTransferId(),
                              QIOCraftingJobState.WAITING_ACCESS);
                        persistenceBarrier.persist(network);
                        return Outcome.WAITING_ACCESS;
                    }
                    if (!backed.equals(commitment.getCommittedAmounts())) {
                        network.applyActiveClaim(jobId, backed, view.getClaimRevision());
                        network.abortPreparedReservation(transfer.getTransferId(),
                              QIOCraftingJobState.WAITING_MATERIALS);
                        persistenceBarrier.persist(network);
                        return Outcome.WAITING_MATERIALS;
                    }
                }
                network.abortPreparedReservation(transfer.getTransferId(), stillOwned ?
                      QIOCraftingJobState.WAITING_EXECUTION_SLOT : QIOCraftingJobState.WAITING_ACCESS);
                persistenceBarrier.persist(network);
                return stillOwned ? Outcome.RETRY_REQUIRED : Outcome.WAITING_ACCESS;
            }
            network.markReservationSourceDebited(transfer.getTransferId(),
                  result.getRequestId() + "/" + result.getStatus(), result.getClaimRevision());
            persistenceBarrier.persist(network);
        }

        if (transfer.getPhase() == QIODurableTransferRecord.Phase.SOURCE_DEBITED) {
            // Destination credit and reservation completion live in the same network record.
            network.creditReservationDestination(transfer.getTransferId());
        }
        if (transfer.getPhase() == QIODurableTransferRecord.Phase.DESTINATION_CREDITED) {
            network.completeReservation(transfer.getTransferId());
            persistenceBarrier.persist(network);
        }
        return job.getState() == QIOCraftingJobState.READY ? Outcome.READY : Outcome.WAITING_ACCESS;
    }

    @Nullable
    private static Map<PortableResourceDescriptor, UUID> resolveBindings(IQIOStorageView view,
          QIOStorageSnapshot snapshot, @Nullable QIOResourceClaim claim,
          Map<PortableResourceDescriptor, Long> resources) {
        Map<PortableResourceDescriptor, UUID> available = new LinkedHashMap<>();
        for (QIOStorageEntry entry : snapshot.getEntries()) {
            PortableResourceDescriptor descriptor = PortableResourceDescriptor.fromStorageEntry(entry);
            UUID previous = available.put(descriptor, entry.getResourceUUID());
            if (previous != null && !previous.equals(entry.getResourceUUID())) {
                return null;
            }
        }
        if (claim != null) {
            for (UUID resourceUUID : claim.getResourceAmounts().keySet()) {
                QIOStorageEntry entry = view.getResource(resourceUUID);
                if (entry == null || !resourceUUID.equals(entry.getResourceUUID())) {
                    return null;
                }
                PortableResourceDescriptor descriptor =
                      PortableResourceDescriptor.fromStorageEntry(entry);
                UUID previous = available.put(descriptor, resourceUUID);
                if (previous != null && !previous.equals(resourceUUID)) {
                    return null;
                }
            }
        }
        Map<PortableResourceDescriptor, UUID> bindings = new LinkedHashMap<>();
        for (PortableResourceDescriptor resource : resources.keySet()) {
            UUID uuid = available.get(resource);
            if (uuid == null) {
                return null;
            }
            bindings.put(resource, uuid);
        }
        return bindings;
    }

    @Nullable
    private static Map<PortableResourceDescriptor, Long> readBackedAmounts(IQIOStorageView view,
          QIOCraftingJob job, QIOMaterialCommitment commitment,
          Map<PortableResourceDescriptor, UUID> bindings) {
        QIOClaimBacking backing = view.getClaimBacking(commitment.getClaimId());
        if (backing == null || backing.getContentsRevision() != view.getContentsRevision() ||
              backing.getClaimRevision() != view.getClaimRevision() ||
              !claimMatches(backing.getClaim(), job, commitment, bindings)) {
            return null;
        }
        Map<PortableResourceDescriptor, Long> translated = new LinkedHashMap<>();
        for (Map.Entry<PortableResourceDescriptor, UUID> binding : bindings.entrySet()) {
            long amount = backing.getBackedAmounts().getOrDefault(binding.getValue(), 0L);
            if (amount > 0) {
                translated.put(binding.getKey(), amount);
            }
        }
        return translated;
    }

    private static boolean claimMatches(@Nullable QIOResourceClaim claim, QIOCraftingJob job,
          QIOMaterialCommitment commitment,
          Map<PortableResourceDescriptor, UUID> bindings) {
        return claim != null && MekanismQIOProcessing.MODID.equals(claim.getOwnerNamespace()) &&
              job.getClaimOwnerId().equals(claim.getOwnerId()) &&
              claim.getResourceAmounts().equals(resolveAmounts(commitment.getCommittedAmounts(), bindings));
    }

    @Nonnull
    private static Map<UUID, Long> resolveAmounts(QIODurableTransferRecord transfer) {
        return resolveAmounts(transfer.getResources(), transfer.getQIOResourceUUIDs());
    }

    @Nullable
    private static Map<PortableResourceDescriptor, BigInteger> resolveBaselines(
          IQIOStorageView view, Map<PortableResourceDescriptor, Long> resources,
          Map<PortableResourceDescriptor, UUID> bindings) {
        Map<PortableResourceDescriptor, BigInteger> baselines = new LinkedHashMap<>();
        for (Map.Entry<PortableResourceDescriptor, Long> resource : resources.entrySet()) {
            UUID uuid = bindings.get(resource.getKey());
            QIOStorageEntry entry = uuid == null ? null : view.getResource(uuid);
            if (entry == null || !resource.getKey().equals(
                  PortableResourceDescriptor.fromStorageEntry(entry)) ||
                  entry.getExactStoredAmount().compareTo(BigInteger.valueOf(resource.getValue())) < 0) {
                return null;
            }
            baselines.put(resource.getKey(), entry.getExactStoredAmount());
        }
        return baselines;
    }

    @Nonnull
    private static Map<UUID, BigInteger> resolveBaselines(QIODurableTransferRecord transfer) {
        Map<UUID, BigInteger> resolved = new LinkedHashMap<>();
        for (Map.Entry<PortableResourceDescriptor, BigInteger> entry :
              transfer.getQIOBaselines().entrySet()) {
            UUID uuid = transfer.getQIOResourceUUIDs().get(entry.getKey());
            if (uuid == null || resolved.put(uuid, entry.getValue()) != null) {
                return new LinkedHashMap<>();
            }
        }
        return resolved;
    }

    @Nonnull
    private static Map<UUID, Long> resolveAmounts(Map<PortableResourceDescriptor, Long> resources,
          Map<PortableResourceDescriptor, UUID> bindings) {
        Map<UUID, Long> resolved = new LinkedHashMap<>();
        for (Map.Entry<PortableResourceDescriptor, Long> entry : resources.entrySet()) {
            UUID uuid = bindings.get(entry.getKey());
            if (uuid == null || resolved.put(uuid, entry.getValue()) != null) {
                return new LinkedHashMap<>();
            }
        }
        return resolved;
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
            throw new IllegalArgumentException("Unknown QIO material commitment for job " + jobId);
        }
        return commitment;
    }
}
