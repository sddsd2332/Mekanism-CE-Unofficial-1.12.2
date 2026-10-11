package mekanism.qioprocessing.common.planning;

import mekanism.api.qio.external.IQIOFrequencyStorageAccess;
import mekanism.api.qio.external.IQIOStorageView;
import mekanism.api.qio.external.QIOFrequencyReference;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;

/**
 * Explicit fail-closed recovery for orders whose executable steps were never persisted.
 */
public final class QIOClaimOrderRecovery {
    public enum Status {ATTEMPTING, RELEASED, AWAITING_AUTHORIZATION, NEEDS_RECONCILIATION}

    private final Thread owner = Thread.currentThread();
    private final QIOClaimOrderStore store;
    private final QIOClaimOrderSubmission submission;

    public QIOClaimOrderRecovery(QIOClaimOrderStore store, QIOClaimOrderSubmission submission) {
        this.store = Objects.requireNonNull(store);
        this.submission = Objects.requireNonNull(submission);
    }

    /**
     * Reopens only a persisted exact reference under its original bound player identity.
     */
    public Map<UUID, Status> abandonAndRelease(IQIOFrequencyStorageAccess access) throws IOException {
        Objects.requireNonNull(access, "access");
        return abandonAndRelease(opener(access));
    }

    public Status abandonOne(UUID orderId, IQIOFrequencyStorageAccess access) throws IOException {
        return abandonOne(orderId, opener(Objects.requireNonNull(access, "access")));
    }

    private static Function<QIOClaimOrderIntent, IQIOStorageView> opener(IQIOFrequencyStorageAccess access) {
        return intent -> {
            QIOFrequencyReference reference = intent.frequencyReference();
            return reference == null || !intent.frequencyId().equals(reference.getFrequencyUUID())
                    || reference.getBindingPlayerUUID() == null ? null
                    : access.open(reference, reference.getBindingPlayerUUID());
        };
    }

    /**
     * The opener must reauthorize each exact frequency and return a lifecycle-bound view.
     */
    public Map<UUID, Status> abandonAndRelease(Function<QIOClaimOrderIntent, IQIOStorageView> authorizedOpener) throws IOException {
        if (Thread.currentThread() != owner)
            throw new IllegalStateException("Claim recovery requires its owner thread");
        Objects.requireNonNull(authorizedOpener, "authorizedOpener");
        Map<UUID, Status> results = new LinkedHashMap<>();
        for (UUID orderId : store.orderIds()) results.put(orderId, abandonOne(orderId, authorizedOpener));
        return Collections.unmodifiableMap(results);
    }

    public Status abandonOne(UUID orderId, Function<QIOClaimOrderIntent, IQIOStorageView> authorizedOpener) throws IOException {
        if (Thread.currentThread() != owner)
            throw new IllegalStateException("Claim recovery requires its owner thread");
        Objects.requireNonNull(authorizedOpener, "authorizedOpener");
        QIOClaimOrderIntent intent = store.load(orderId);
        store.requestCancellation(orderId);
        store.beginRecovery(orderId, System.currentTimeMillis());
        IQIOStorageView view = authorizedOpener.apply(intent);
        Status result = Status.AWAITING_AUTHORIZATION;
        if (view != null) {
            try {
                if (view.isValid() && intent.frequencyId().equals(view.getFrequencyUUID())) {
                    result = submission.release(orderId, view) ? Status.RELEASED : Status.NEEDS_RECONCILIATION;
                }
            } catch (IllegalStateException error) {
                result = Status.AWAITING_AUTHORIZATION;
            } finally {
                view.close();
            }
        }
        store.finishRecovery(orderId, result, System.currentTimeMillis());
        return result;
    }
}

