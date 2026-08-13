package mekanism.api.qio.external;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Immutable result of an idempotent QIO claim request. */
public final class QIOClaimResult {

    public enum Status {
        APPLIED,
        PARTIAL,
        NO_CHANGE,
        NOT_FOUND,
        OWNER_MISMATCH,
        STALE_CONTENTS,
        STALE_CLAIMS,
        INSUFFICIENT_RESOURCES,
        RECONCILIATION_CONFLICT,
        REQUEST_ID_CONFLICT,
        INVALID_REQUEST,
        FAILED
    }

    private final UUID requestId;
    private final Status status;
    private final long claimRevision;
    private final QIOResourceClaim claim;
    private final Map<UUID, Long> committedAmounts;
    private final Map<UUID, Long> unavailableAmounts;
    private final Map<UUID, Long> consumedAmounts;

    public QIOClaimResult(@Nonnull UUID requestId, @Nonnull Status status, long claimRevision,
          @Nullable QIOResourceClaim claim, @Nonnull Map<UUID, Long> committedAmounts,
          @Nonnull Map<UUID, Long> unavailableAmounts, @Nonnull Map<UUID, Long> consumedAmounts) {
        this.requestId = Objects.requireNonNull(requestId, "requestId");
        this.status = Objects.requireNonNull(status, "status");
        if (claimRevision < 0) {
            throw new IllegalArgumentException("claimRevision cannot be negative");
        }
        this.claimRevision = claimRevision;
        this.claim = claim;
        this.committedAmounts = copyAmounts(committedAmounts);
        this.unavailableAmounts = copyAmounts(unavailableAmounts);
        this.consumedAmounts = copyAmounts(consumedAmounts);
    }

    @Nonnull
    public UUID getRequestId() {
        return requestId;
    }

    @Nonnull
    public Status getStatus() {
        return status;
    }

    public long getClaimRevision() {
        return claimRevision;
    }

    @Nullable
    public QIOResourceClaim getClaim() {
        return claim;
    }

    @Nonnull
    public Map<UUID, Long> getCommittedAmounts() {
        return committedAmounts;
    }

    @Nonnull
    public Map<UUID, Long> getUnavailableAmounts() {
        return unavailableAmounts;
    }

    @Nonnull
    public Map<UUID, Long> getConsumedAmounts() {
        return consumedAmounts;
    }

    public boolean isSuccess() {
        return status == Status.APPLIED || status == Status.PARTIAL || status == Status.NO_CHANGE;
    }

    private static Map<UUID, Long> copyAmounts(Map<UUID, Long> amounts) {
        Objects.requireNonNull(amounts, "amounts");
        Map<UUID, Long> copy = new LinkedHashMap<>();
        amounts.entrySet().stream()
              .sorted(Map.Entry.comparingByKey())
              .forEach(entry -> {
                  UUID resource = Objects.requireNonNull(entry.getKey(), "resourceUUID");
                  Long amount = Objects.requireNonNull(entry.getValue(), "resourceAmount");
                  if (amount < 0) {
                      throw new IllegalArgumentException("QIO claim result amounts cannot be negative");
                  }
                  if (amount > 0) {
                      copy.put(resource, amount);
                  }
              });
        return Collections.unmodifiableMap(copy);
    }
}
