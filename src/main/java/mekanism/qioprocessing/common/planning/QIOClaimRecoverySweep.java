package mekanism.qioprocessing.common.planning;

import mekanism.api.qio.external.IQIOFrequencyStorageAccess;
import mekanism.api.qio.external.QIOFrequencyReference;

import java.io.IOException;
import java.util.Objects;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/**
 * One owner-thread filesystem entry and at most one authorized release per step.
 */
public final class QIOClaimRecoverySweep implements AutoCloseable {
    public static final int MAX_AUTOMATIC_ATTEMPTS = 3;
    public static final long RETRY_DELAY_MILLIS = 5 * 60 * 1000L;
    private final Thread owner = Thread.currentThread();
    private final QIOClaimOrderStore store;
    private final QIOClaimOrderRecovery recovery;
    private final IQIOFrequencyStorageAccess access;
    private final LongSupplier clock;
    private final Predicate<QIOClaimOrderIntent> eligible;
    private QIOClaimOrderStore.Scan scan;
    private boolean complete;
    private int released, awaiting, unresolved, retryable, exhausted, operatorRequired;
    private UUID lastOrderId;
    private QIOClaimOrderRecovery.Status lastStatus;

    public QIOClaimRecoverySweep(QIOClaimOrderStore store, QIOClaimOrderRecovery recovery,
                                 IQIOFrequencyStorageAccess access) {
        this(store, recovery, access, System::currentTimeMillis);
    }

    public QIOClaimRecoverySweep(QIOClaimOrderStore store, QIOClaimOrderRecovery recovery,
                                 IQIOFrequencyStorageAccess access, LongSupplier clock) {
        this(store, recovery, access, clock, ignored -> true);
    }

    public QIOClaimRecoverySweep(QIOClaimOrderStore store, QIOClaimOrderRecovery recovery,
                                 IQIOFrequencyStorageAccess access, LongSupplier clock,
                                 Predicate<QIOClaimOrderIntent> eligible) {
        this.store = Objects.requireNonNull(store);
        this.recovery = Objects.requireNonNull(recovery);
        this.access = Objects.requireNonNull(access);
        this.clock = Objects.requireNonNull(clock);
        this.eligible = Objects.requireNonNull(eligible);
    }

    public boolean step() throws IOException {
        requireOwner();
        if (complete) return true;
        lastOrderId = null;
        lastStatus = null;
        if (scan == null) scan = store.scan();
        QIOClaimOrderStore.ScanEntry entry = scan.next();
        if (entry == null) {
            close();
            return true;
        }
        if (entry.orderId() != null) {
            QIOClaimOrderIntent intent = store.load(entry.orderId());
            if (intent.isExecutable() && !store.cancellationRequested(entry.orderId())) return false;
            if (!eligible.test(intent)) return false;
            QIOClaimOrderStore.RecoveryRecord previous = store.readRecovery(entry.orderId());
            if (previous != null) {
                if (!store.cancellationRequested(entry.orderId())) {
                    throw new IOException("Claim recovery diagnostic without cancellation: " + entry.orderId());
                }
                if (previous.status() == QIOClaimOrderRecovery.Status.RELEASED) return false;
                if (!hasBoundReference(entry.orderId())) {
                    operatorRequired++;
                    return false;
                }
                if (previous.attempts() >= MAX_AUTOMATIC_ATTEMPTS) {
                    exhausted++;
                    return false;
                }
                long nowMillis = clock.getAsLong();
                if (nowMillis < previous.lastAttemptMillis()
                        || nowMillis - previous.lastAttemptMillis() < RETRY_DELAY_MILLIS) {
                    retryable++;
                    return false;
                }
            }
            QIOClaimOrderRecovery.Status status = recovery.abandonOne(entry.orderId(), access);
            lastOrderId = entry.orderId();
            lastStatus = status;
            switch (status) {
                case RELEASED:
                    released++;
                    break;
                case AWAITING_AUTHORIZATION:
                    awaiting++;
                    break;
                case NEEDS_RECONCILIATION:
                    unresolved++;
                    break;
                default:
                    throw new IllegalStateException("Unexpected claim recovery result: " + status);
            }
            if (status != QIOClaimOrderRecovery.Status.RELEASED) {
                if (!hasBoundReference(entry.orderId())) operatorRequired++;
                else if (store.readRecovery(entry.orderId()).attempts() < MAX_AUTOMATIC_ATTEMPTS) retryable++;
                else exhausted++;
            }
        }
        return false;
    }

    public int released() {
        return released;
    }

    public int awaiting() {
        return awaiting;
    }

    public int unresolved() {
        return unresolved;
    }

    public int retryable() {
        return retryable;
    }

    public int exhausted() {
        return exhausted;
    }

    public int operatorRequired() {
        return operatorRequired;
    }

    public boolean complete() {
        return complete;
    }

    public UUID lastOrderId() {
        return lastOrderId;
    }

    public QIOClaimOrderRecovery.Status lastStatus() {
        return lastStatus;
    }

    @Override
    public void close() throws IOException {
        requireOwner();
        complete = true;
        if (scan != null) {
            scan.close();
            scan = null;
        }
    }

    private void requireOwner() {
        if (Thread.currentThread() != owner)
            throw new IllegalStateException("Claim recovery sweep requires its owner thread");
    }

    private boolean hasBoundReference(UUID orderId) throws IOException {
        QIOFrequencyReference reference = store.load(orderId).frequencyReference();
        return reference != null && reference.getBindingPlayerUUID() != null;
    }
}

