package mekanism.qioprocessing.common.planning;

import mekanism.api.qio.external.IQIOStorageView;
import mekanism.api.qio.external.QIOFrequencyReference;
import mekanism.api.qio.external.QIOStorageSnapshot;
import mekanism.qioprocessing.common.pattern.WorkbenchPatternIndex;
import mekanism.qioprocessing.common.recipe.RecipeCatalog;
import mekanism.qioprocessing.common.recipe.RecipeItemKey;
import mekanism.qioprocessing.common.workbench.WorkbenchSessions;
import mekanism.qioprocessing.common.workbench.WorkbenchSnapshot;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * Asynchronous pure-data planning followed by an owner-thread, authorized Claim handoff.
 */
public final class QIOWorkbenchOrderPreparation {
    public enum Status {
        RUNNING, PLANNING_FAILED, NO_CLAIM_REQUIRED, CLAIM_RESERVED, CLAIM_REJECTED,
        NEEDS_RECONCILIATION, COMPLETED, FAILED, CANCELLED
    }

    public static final class Prepared {
        private final Status status;
        private final QIOPatternPlanner.Result plan;
        private final QIOClaimOrderSubmission.Outcome reservation;

        private Prepared(Status status, QIOPatternPlanner.Result plan, QIOClaimOrderSubmission.Outcome reservation) {
            this.status = status;
            this.plan = plan;
            this.reservation = reservation;
        }

        public Status status() {
            return status;
        }

        public QIOPatternPlanner.Result plan() {
            return plan;
        }

        public QIOClaimOrderSubmission.Outcome reservation() {
            return reservation;
        }

        public static Prepared status(Status status) {
            return new Prepared(Objects.requireNonNull(status, "status"), null, null);
        }
    }

    public static final class Job {
        private final QIOWorkbenchOrderPreparation origin;
        private final UUID playerId, frequencyId, orderId, requestId, claimId;
        private final QIOFrequencyReference reference;
        private final Future<QIOPatternPlanner.Result> pending;
        private QIOClaimOrderIntent intent;

        private Job(QIOWorkbenchOrderPreparation origin, UUID playerId, UUID frequencyId, UUID orderId, UUID requestId,
                    UUID claimId, QIOFrequencyReference reference, Future<QIOPatternPlanner.Result> pending) {
            this.origin = origin;
            this.playerId = playerId;
            this.frequencyId = frequencyId;
            this.orderId = orderId;
            this.requestId = requestId;
            this.claimId = claimId;
            this.reference = reference;
            this.pending = pending;
        }

        public UUID orderId() {
            return orderId;
        }

        public QIOFrequencyReference frequencyReference() {
            return reference;
        }

        public boolean ready() {
            return pending.isDone();
        }

        public void cancel() {
            pending.cancel(true);
        }
    }

    private final Thread owner = Thread.currentThread();
    private final ExecutorService worker;
    private final QIOClaimOrderSubmission submission;

    public QIOWorkbenchOrderPreparation(ExecutorService worker, QIOClaimOrderSubmission submission) {
        this.worker = Objects.requireNonNull(worker);
        this.submission = Objects.requireNonNull(submission);
    }

    public Job start(WorkbenchSessions.Access access, IQIOStorageView authorizedView, WorkbenchSnapshot book,
                     RecipeCatalog catalog, Map<RecipeItemKey, Long> targets) {
        requireOwner();
        checkAccess(access, authorizedView, book.id());
        QIOFrequencyReference reference = access.frequencyReference();
        if (reference != null && (!book.id().equals(reference.getFrequencyUUID())
                || !access.player().equals(reference.getBindingPlayerUUID()))) {
            throw new IllegalStateException("Workbench frequency reference is not bound to the authorized player");
        }
        QIOStorageSnapshot storage = authorizedView.getSnapshot();
        if (!storage.getFrequencyUUID().equals(book.id()))
            throw new IllegalStateException("QIO storage frequency changed");
        QIOPlanningRequest request = new QIOPlanningRequest(targets, QIOPlanClaimIntent.inventory(storage));
        WorkbenchPatternIndex index = WorkbenchPatternIndex.build(book, catalog);
        Future<QIOPatternPlanner.Result> pending = worker.submit(() -> QIOPatternPlanner.plan(request, index));
        return new Job(this, access.player(), book.id(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), reference, pending);
    }

    public Prepared finish(Job job, WorkbenchSessions.Access access, IQIOStorageView authorizedView,
                           WorkbenchSnapshot currentBook, RecipeCatalog currentCatalog) throws IOException {
        requireOwner();
        Objects.requireNonNull(job, "job");
        if (job.origin != this) throw new IllegalArgumentException("Order job belongs to another preparation service");
        if (access == null || !job.playerId.equals(access.player()))
            throw new IllegalStateException("Order job belongs to another player");
        if (!Objects.equals(job.reference, access.frequencyReference()))
            throw new IllegalStateException("Order frequency reference changed");
        checkAccess(access, authorizedView, job.frequencyId);
        if (!job.pending.isDone()) return new Prepared(Status.RUNNING, null, null);
        QIOPatternPlanner.Result plan;
        try {
            plan = job.pending.get();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Planning interrupted", error);
        } catch (ExecutionException | java.util.concurrent.CancellationException error) {
            return new Prepared(Status.PLANNING_FAILED, null, null);
        }
        if (!plan.successful()) return new Prepared(Status.PLANNING_FAILED, plan, null);
        if (!job.frequencyId.equals(currentBook.id()) || plan.revision() != currentBook.revision()
                || !plan.generation().equals(currentCatalog.generation()))
            throw new IllegalStateException("Plan configuration or catalog changed");
        if (job.intent == null) {
            QIOStorageSnapshot fresh = authorizedView.getSnapshot();
            if (!job.frequencyId.equals(fresh.getFrequencyUUID()))
                throw new IllegalStateException("QIO storage frequency changed");
            java.util.Optional<QIOClaimOrderIntent> prepared = QIOClaimOrderIntent.prepareExecutable(plan, fresh, currentBook,
                    job.orderId, job.requestId, job.claimId, job.reference, WorkbenchPatternIndex.build(currentBook, currentCatalog),
                    access.dimension());
            if (!prepared.isPresent()) return new Prepared(Status.NO_CLAIM_REQUIRED, plan, null);
            job.intent = prepared.get();
        }
        checkAccess(access, authorizedView, job.frequencyId);
        QIOClaimOrderSubmission.Outcome outcome = submission.reserve(job.intent, currentBook,
                currentCatalog.generation(), authorizedView);
        Status status = outcome.reserved() ? Status.CLAIM_RESERVED
                : outcome.status() == QIOClaimOrderSubmission.Status.NEEDS_RECONCILIATION ? Status.NEEDS_RECONCILIATION
                : Status.CLAIM_REJECTED;
        return new Prepared(status, plan, outcome);
    }

    private static void checkAccess(WorkbenchSessions.Access access, IQIOStorageView view, UUID frequencyId) {
        if (access == null || !access.valid() || !frequencyId.equals(access.frequency())
                || view == null || !view.isValid() || !frequencyId.equals(view.getFrequencyUUID())) {
            throw new IllegalStateException("Workbench order requires an authorized QIO session");
        }
    }

    private void requireOwner() {
        if (Thread.currentThread() != owner)
            throw new IllegalStateException("Order preparation requires its owner thread");
    }
}
