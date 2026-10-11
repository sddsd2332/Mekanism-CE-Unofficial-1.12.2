package mekanism.qioprocessing.common.planning;

import mekanism.api.qio.external.IQIOStorageView;
import mekanism.api.qio.external.QIOClaimBacking;
import mekanism.api.qio.external.QIOClaimResult;
import mekanism.api.qio.external.QIOResourceClaim;
import mekanism.qioprocessing.common.workbench.WorkbenchSnapshot;

import java.io.IOException;
import java.util.UUID;

/**
 * Server-thread reservation boundary; only a persisted request may reach QIO.
 */
public final class QIOClaimOrderSubmission {
    public enum Status {RESERVED, RELEASED_PARTIAL, CANCELLED, REJECTED, NEEDS_RECONCILIATION}

    public static final class Outcome {
        private final Status status;
        private final QIOClaimResult claimResult, releaseResult;

        private Outcome(Status status, QIOClaimResult claimResult, QIOClaimResult releaseResult) {
            this.status = status;
            this.claimResult = claimResult;
            this.releaseResult = releaseResult;
        }

        public Status status() {
            return status;
        }

        public QIOClaimResult claimResult() {
            return claimResult;
        }

        public QIOClaimResult releaseResult() {
            return releaseResult;
        }

        public boolean reserved() {
            return status == Status.RESERVED;
        }
    }

    private final Thread owner = Thread.currentThread();
    private final QIOClaimOrderStore store;

    public QIOClaimOrderSubmission(QIOClaimOrderStore store) {
        this.store = java.util.Objects.requireNonNull(store);
    }

    public Outcome reserve(QIOClaimOrderIntent intent, WorkbenchSnapshot book, String catalogGeneration,
                           IQIOStorageView authorizedView) throws IOException {
        requireOwner();
        check(intent, book, catalogGeneration, authorizedView);
        store.saveNew(intent);
        return resume(intent.orderId(), book, catalogGeneration, authorizedView);
    }

    /**
     * Provider-specific reservation boundary; Provider catalog identity is carried by its manifest.
     */
    public Outcome reserveProvider(QIOClaimOrderIntent intent, IQIOStorageView authorizedView) throws IOException {
        requireOwner();
        if (intent == null || intent.providerManifest() == null)
            throw new IllegalArgumentException("Provider manifest is required");
        checkView(intent, authorizedView);
        store.saveNew(intent);
        return resumeProvider(intent.orderId(), authorizedView);
    }

    public Outcome resumeProvider(UUID orderId, IQIOStorageView authorizedView) throws IOException {
        requireOwner();
        QIOClaimOrderIntent intent = store.load(orderId);
        if (intent.providerManifest() == null) throw new IllegalArgumentException("Order is not a Provider order");
        checkView(intent, authorizedView);
        if (store.cancellationRequested(orderId)) {
            QIOResourceClaim claim = authorizedView.getClaim(intent.claimId());
            if (claim == null && authorizedView.isValid()) return new Outcome(Status.CANCELLED, null, null);
            QIOClaimResult release = release(intent, authorizedView);
            return new Outcome(release != null ? Status.CANCELLED : Status.NEEDS_RECONCILIATION, null, release);
        }
        store.requireNoActiveScan();
        QIOClaimResult result = authorizedView.submitClaim(intent.request());
        if (!intent.requestId().equals(result.getRequestId()))
            return new Outcome(Status.NEEDS_RECONCILIATION, result, null);
        if (result.getStatus() == QIOClaimResult.Status.PARTIAL) {
            store.requestCancellation(orderId);
            QIOClaimResult release = release(intent, authorizedView);
            return new Outcome(release != null ? Status.RELEASED_PARTIAL : Status.NEEDS_RECONCILIATION, result, release);
        }
        if (result.getStatus() != QIOClaimResult.Status.APPLIED && result.getStatus() != QIOClaimResult.Status.NO_CHANGE) {
            return new Outcome(Status.REJECTED, result, null);
        }
        QIOResourceClaim claim = authorizedView.getClaim(intent.claimId());
        QIOClaimBacking backing = authorizedView.getClaimBacking(intent.claimId());
        if (!result.getUnavailableAmounts().isEmpty() || !intent.amounts().equals(result.getCommittedAmounts())
                || !matches(intent, result.getClaim()) || !matches(intent, claim)
                || backing == null || !matches(intent, backing.getClaim()) || !backing.isFullyBacked()
                || !intent.amounts().equals(backing.getBackedAmounts()) || !authorizedView.isValid()) {
            return new Outcome(Status.NEEDS_RECONCILIATION, result, null);
        }
        return new Outcome(Status.RESERVED, result, null);
    }

    /**
     * Replays exactly the previously journaled request; a failed CAS requires a new plan and a new order.
     */
    public Outcome resume(UUID orderId, WorkbenchSnapshot book, String catalogGeneration,
                          IQIOStorageView authorizedView) throws IOException {
        requireOwner();
        QIOClaimOrderIntent intent = store.load(orderId);
        checkView(intent, authorizedView);
        if (store.cancellationRequested(orderId)) {
            QIOResourceClaim claim = authorizedView.getClaim(intent.claimId());
            if (claim == null && authorizedView.isValid()) return new Outcome(Status.CANCELLED, null, null);
            QIOClaimResult release = release(intent, authorizedView);
            return new Outcome(release != null ? Status.CANCELLED : Status.NEEDS_RECONCILIATION, null, release);
        }
        store.requireNoActiveScan();
        check(intent, book, catalogGeneration, authorizedView);
        QIOClaimResult result = authorizedView.submitClaim(intent.request());
        if (!intent.requestId().equals(result.getRequestId()))
            return new Outcome(Status.NEEDS_RECONCILIATION, result, null);
        if (result.getStatus() == QIOClaimResult.Status.PARTIAL) {
            store.requestCancellation(orderId);
            if (!authorizedView.isValid()) return new Outcome(Status.NEEDS_RECONCILIATION, result, null);
            if (authorizedView.getClaim(intent.claimId()) == null)
                return new Outcome(Status.RELEASED_PARTIAL, result, null);
            QIOClaimResult release = release(intent, authorizedView);
            return new Outcome(release != null ? Status.RELEASED_PARTIAL : Status.NEEDS_RECONCILIATION, result, release);
        }
        if (result.getStatus() != QIOClaimResult.Status.APPLIED && result.getStatus() != QIOClaimResult.Status.NO_CHANGE) {
            return new Outcome(Status.REJECTED, result, null);
        }
        QIOResourceClaim claim = authorizedView.getClaim(intent.claimId());
        QIOClaimBacking backing = authorizedView.getClaimBacking(intent.claimId());
        if (!result.getUnavailableAmounts().isEmpty() || !intent.amounts().equals(result.getCommittedAmounts())
                || !matches(intent, result.getClaim()) || !matches(intent, claim)
                || backing == null || !matches(intent, backing.getClaim())
                || !backing.isFullyBacked() || !intent.amounts().equals(backing.getBackedAmounts())
                || !authorizedView.isValid()) {
            return new Outcome(Status.NEEDS_RECONCILIATION, result, null);
        }
        return new Outcome(Status.RESERVED, result, null);
    }

    /**
     * Explicit cleanup also works after the plan or catalog changed; never creates a new reservation.
     */
    public boolean release(UUID orderId, IQIOStorageView authorizedView) throws IOException {
        requireOwner();
        QIOClaimOrderIntent intent = store.load(orderId);
        checkView(intent, authorizedView);
        store.requestCancellation(orderId);
        if (!authorizedView.isValid()) return false;
        return authorizedView.getClaim(intent.claimId()) == null ? authorizedView.isValid() : release(intent, authorizedView) != null;
    }

    private static QIOClaimResult release(QIOClaimOrderIntent intent, IQIOStorageView view) {
        QIOResourceClaim claim = view.getClaim(intent.claimId());
        if (claim == null) return null;
        if (!intent.claimId().equals(claim.getClaimId()) || !QIOPlanClaimIntent.OWNER_NAMESPACE.equals(claim.getOwnerNamespace())
                || !intent.orderId().toString().equals(claim.getOwnerId())) return null;
        QIOClaimResult result = view.submitClaim(intent.releaseRequest());
        if (!intent.releaseRequest().getRequestId().equals(result.getRequestId())) return null;
        if (result.getStatus() != QIOClaimResult.Status.APPLIED && result.getStatus() != QIOClaimResult.Status.NO_CHANGE
                && result.getStatus() != QIOClaimResult.Status.NOT_FOUND) return null;
        return view.getClaim(intent.claimId()) == null ? result : null;
    }

    private static boolean matches(QIOClaimOrderIntent intent, QIOResourceClaim claim) {
        return claim != null && intent.claimId().equals(claim.getClaimId())
                && QIOPlanClaimIntent.OWNER_NAMESPACE.equals(claim.getOwnerNamespace())
                && intent.orderId().toString().equals(claim.getOwnerId())
                && claim.getState() == QIOResourceClaim.State.ACTIVE && intent.amounts().equals(claim.getResourceAmounts());
    }

    private static void check(QIOClaimOrderIntent intent, WorkbenchSnapshot book, String catalogGeneration,
                              IQIOStorageView view) {
        checkView(intent, view);
        if (book == null || !intent.frequencyId().equals(book.id()) || intent.bookRevision() != book.revision()
                || !intent.generation().equals(catalogGeneration))
            throw new IllegalStateException("Stale claim order configuration or catalog");
    }

    private static void checkView(QIOClaimOrderIntent intent, IQIOStorageView view) {
        if (view == null || !view.isValid() || !intent.frequencyId().equals(view.getFrequencyUUID())) {
            throw new IllegalStateException("Claim order requires the authorized original QIO frequency");
        }
    }

    private void requireOwner() {
        if (Thread.currentThread() != owner)
            throw new IllegalStateException("Claim submission requires its owner thread");
    }
}

