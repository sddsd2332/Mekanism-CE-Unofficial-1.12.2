package mekanism.qioprocessing.common.planning;

import mekanism.api.qio.external.IQIOStorageView;
import mekanism.api.qio.external.QIOFrequencyReference;
import mekanism.api.qio.external.QIOStorageSnapshot;
import mekanism.api.qio.resource.QIOResourceDescriptor;

import java.io.IOException;
import java.math.BigInteger;
import java.util.*;

/**
 * Server-thread Provider planning and Claim handoff; no machine mutation occurs here.
 */
public final class QIOProviderOrderPreparation {
    public enum Status {PLAN_FAILED, NO_RESERVATION_REQUIRED, CLAIM_RESERVED, CLAIM_REJECTED, NEEDS_RECONCILIATION}

    public static final class Result {
        private final Status status;
        private final QIOProviderRoutePlanner.Result plan;
        private final QIOClaimOrderSubmission.Outcome reservation;

        private Result(Status status, QIOProviderRoutePlanner.Result plan,
                       QIOClaimOrderSubmission.Outcome reservation) {
            this.status = status;
            this.plan = plan;
            this.reservation = reservation;
        }

        public Status status() {
            return status;
        }

        public QIOProviderRoutePlanner.Result plan() {
            return plan;
        }

        public QIOClaimOrderSubmission.Outcome reservation() {
            return reservation;
        }
    }

    private final Thread owner = Thread.currentThread();
    private final QIOClaimOrderSubmission submission;

    public QIOProviderOrderPreparation(QIOClaimOrderSubmission submission) {
        this.submission = Objects.requireNonNull(submission, "submission");
    }

    public Result prepareAndReserve(Map<QIOResourceDescriptor, Long> targets,
                                    QIOProviderRouteCatalog.Snapshot catalog, IQIOStorageView storage,
                                    QIOFrequencyReference reference, UUID orderId, UUID requestId, UUID claimId) throws IOException {
        requireOwner();
        if (storage == null || !storage.isValid() || reference == null
                || reference.getBindingPlayerUUID() == null
                || !storage.getFrequencyUUID().equals(reference.getFrequencyUUID())) {
            throw new IllegalArgumentException("Provider order requires an authorized bound frequency");
        }
        QIOStorageSnapshot snapshot = storage.getSnapshot();
        Map<QIOResourceDescriptor, Long> inventory = inventory(snapshot);
        QIOProviderRoutePlanner.Result plan = QIOProviderRoutePlanner.plan(
                new QIOProviderRoutePlanner.Request(targets, inventory),
                catalog.forFrequency(storage.getFrequencyUUID()));
        if (!plan.successful()) return new Result(Status.PLAN_FAILED, plan, null);
        Optional<QIOClaimOrderIntent> intent = QIOClaimOrderIntent.prepareProviderExecutable(
                plan, snapshot, orderId, requestId, claimId, reference);
        if (!intent.isPresent()) return new Result(Status.NO_RESERVATION_REQUIRED, plan, null);
        QIOClaimOrderSubmission.Outcome outcome = submission.reserveProvider(intent.get(), storage);
        Status status = outcome.reserved() ? Status.CLAIM_RESERVED
                : outcome.status() == QIOClaimOrderSubmission.Status.NEEDS_RECONCILIATION
                ? Status.NEEDS_RECONCILIATION : Status.CLAIM_REJECTED;
        return new Result(status, plan, outcome);
    }

    private static Map<QIOResourceDescriptor, Long> inventory(QIOStorageSnapshot snapshot) {
        if (snapshot == null) throw new IllegalArgumentException("Provider order requires a storage snapshot");
        Map<QIOResourceDescriptor, Long> inventory = new LinkedHashMap<>();
        snapshot.getEntries().forEach(entry -> {
            BigInteger available = entry.getExactAvailableAmount();
            if (available.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
                throw new IllegalArgumentException("Provider inventory exceeds long planning range");
            }
            if (available.signum() > 0)
                inventory.merge(entry.getDescriptor(), available.longValueExact(), Math::addExact);
        });
        return Collections.unmodifiableMap(inventory);
    }

    private void requireOwner() {
        if (Thread.currentThread() != owner)
            throw new IllegalStateException("Provider order preparation requires its owner thread");
    }
}
