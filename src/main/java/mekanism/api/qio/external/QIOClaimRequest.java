package mekanism.api.qio.external;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Immutable, idempotent mutation request for one QIO logical resource claim. */
public final class QIOClaimRequest {

    public static final long ANY_REVISION = -1;

    public enum Mode {
        CREATE_OR_ADJUST,
        REASSIGN,
        RELEASE,
        CONSUME
    }

    private final UUID requestId;
    private final Mode mode;
    private final UUID claimId;
    private final UUID sourceClaimId;
    private final UUID transferId;
    private final String ownerNamespace;
    private final String ownerId;
    private final long priority;
    private final long enqueueSequence;
    private final long expectedContentsRevision;
    private final long expectedClaimRevision;
    private final Map<UUID, Long> resourceAmounts;
    private final Map<UUID, BigInteger> expectedStoredAmounts;

    private QIOClaimRequest(UUID requestId, Mode mode, UUID claimId, @Nullable UUID sourceClaimId,
          @Nullable UUID transferId, String ownerNamespace, String ownerId, long priority,
          long enqueueSequence, long expectedContentsRevision, long expectedClaimRevision,
          Map<UUID, Long> resourceAmounts, Map<UUID, BigInteger> expectedStoredAmounts) {
        this.requestId = Objects.requireNonNull(requestId, "requestId");
        this.mode = Objects.requireNonNull(mode, "mode");
        this.claimId = Objects.requireNonNull(claimId, "claimId");
        this.sourceClaimId = sourceClaimId;
        this.transferId = transferId;
        this.ownerNamespace = requireText(ownerNamespace, "ownerNamespace");
        this.ownerId = requireText(ownerId, "ownerId");
        this.priority = priority;
        if (enqueueSequence < 0) {
            throw new IllegalArgumentException("enqueueSequence cannot be negative");
        }
        this.enqueueSequence = enqueueSequence;
        this.expectedContentsRevision = requireRevision(expectedContentsRevision, "expectedContentsRevision");
        this.expectedClaimRevision = requireRevision(expectedClaimRevision, "expectedClaimRevision");
        this.resourceAmounts = copyAmounts(resourceAmounts, mode == Mode.RELEASE || mode == Mode.CONSUME);
        this.expectedStoredAmounts = copyStoredAmounts(expectedStoredAmounts);
        if (mode == Mode.REASSIGN && sourceClaimId == null) {
            throw new IllegalArgumentException("REASSIGN requires a sourceClaimId");
        }
        if (mode == Mode.CONSUME && transferId == null) {
            throw new IllegalArgumentException("CONSUME requires a transferId");
        }
        if (mode == Mode.CONSUME && this.expectedStoredAmounts.isEmpty()) {
            throw new IllegalArgumentException("CONSUME requires exact physical storage baselines");
        }
        if (!this.expectedStoredAmounts.isEmpty() && (mode != Mode.CONSUME ||
              !this.expectedStoredAmounts.keySet().equals(this.resourceAmounts.keySet()))) {
            throw new IllegalArgumentException(
                  "Physical storage baselines must exactly match a CONSUME request");
        }
        for (Map.Entry<UUID, BigInteger> baseline : this.expectedStoredAmounts.entrySet()) {
            if (baseline.getValue().compareTo(BigInteger.valueOf(
                  this.resourceAmounts.get(baseline.getKey()))) < 0) {
                throw new IllegalArgumentException(
                      "A physical storage baseline cannot be smaller than its consumed amount");
            }
        }
    }

    @Nonnull
    public static QIOClaimRequest createOrAdjust(UUID requestId, UUID claimId, String ownerNamespace,
          String ownerId, long priority, long enqueueSequence, long expectedContentsRevision,
          long expectedClaimRevision, Map<UUID, Long> targetAmounts) {
        return new QIOClaimRequest(requestId, Mode.CREATE_OR_ADJUST, claimId, null, null,
              ownerNamespace, ownerId, priority, enqueueSequence, expectedContentsRevision,
              expectedClaimRevision, targetAmounts, Collections.emptyMap());
    }

    @Nonnull
    public static QIOClaimRequest reassign(UUID requestId, UUID sourceClaimId, UUID targetClaimId,
          String ownerNamespace, String ownerId, long priority, long enqueueSequence,
          long expectedContentsRevision, long expectedClaimRevision, Map<UUID, Long> targetAmounts) {
        return new QIOClaimRequest(requestId, Mode.REASSIGN, targetClaimId, sourceClaimId, null,
              ownerNamespace, ownerId, priority, enqueueSequence, expectedContentsRevision,
              expectedClaimRevision, targetAmounts, Collections.emptyMap());
    }

    @Nonnull
    public static QIOClaimRequest release(UUID requestId, UUID claimId, String ownerNamespace,
          String ownerId, long expectedClaimRevision, Map<UUID, Long> releaseAmounts) {
        return new QIOClaimRequest(requestId, Mode.RELEASE, claimId, null, null, ownerNamespace,
              ownerId, 0, 0, ANY_REVISION, expectedClaimRevision, releaseAmounts,
              Collections.emptyMap());
    }

    /**
     * Creates a consume request that can reconcile an independently persisted drive mutation.
     * Each baseline is the exact stored amount observed before the durable consume intent was saved.
     */
    @Nonnull
    public static QIOClaimRequest consumeReconciled(UUID requestId, UUID transferId, UUID claimId,
          String ownerNamespace, String ownerId, long expectedContentsRevision,
          long expectedClaimRevision, Map<UUID, Long> consumeAmounts,
          Map<UUID, BigInteger> expectedStoredAmounts) {
        return new QIOClaimRequest(requestId, Mode.CONSUME, claimId, null, transferId,
              ownerNamespace, ownerId, 0, 0, expectedContentsRevision, expectedClaimRevision,
              consumeAmounts, expectedStoredAmounts);
    }

    @Nonnull
    public UUID getRequestId() {
        return requestId;
    }

    @Nonnull
    public Mode getMode() {
        return mode;
    }

    @Nonnull
    public UUID getClaimId() {
        return claimId;
    }

    @Nullable
    public UUID getSourceClaimId() {
        return sourceClaimId;
    }

    @Nullable
    public UUID getTransferId() {
        return transferId;
    }

    @Nonnull
    public String getOwnerNamespace() {
        return ownerNamespace;
    }

    @Nonnull
    public String getOwnerId() {
        return ownerId;
    }

    public long getPriority() {
        return priority;
    }

    public long getEnqueueSequence() {
        return enqueueSequence;
    }

    public long getExpectedContentsRevision() {
        return expectedContentsRevision;
    }

    public long getExpectedClaimRevision() {
        return expectedClaimRevision;
    }

    @Nonnull
    public Map<UUID, Long> getResourceAmounts() {
        return resourceAmounts;
    }

    /** Exact pre-mutation drive amounts used by crash reconciliation, or empty for legacy requests. */
    @Nonnull
    public Map<UUID, BigInteger> getExpectedStoredAmounts() {
        return expectedStoredAmounts;
    }

    private static Map<UUID, Long> copyAmounts(Map<UUID, Long> amounts, boolean allowEmpty) {
        Objects.requireNonNull(amounts, "resourceAmounts");
        Map<UUID, Long> copy = new LinkedHashMap<>();
        amounts.entrySet().stream()
              .sorted(Map.Entry.comparingByKey())
              .forEach(entry -> {
                  UUID resource = Objects.requireNonNull(entry.getKey(), "resourceUUID");
                  Long amount = Objects.requireNonNull(entry.getValue(), "resourceAmount");
                  if (amount <= 0) {
                      throw new IllegalArgumentException("QIO claim request amounts must be positive");
                  }
                  copy.put(resource, amount);
              });
        if (!allowEmpty && copy.isEmpty()) {
            throw new IllegalArgumentException("QIO claim request cannot be empty");
        }
        return Collections.unmodifiableMap(copy);
    }

    private static Map<UUID, BigInteger> copyStoredAmounts(Map<UUID, BigInteger> amounts) {
        Objects.requireNonNull(amounts, "expectedStoredAmounts");
        Map<UUID, BigInteger> copy = new LinkedHashMap<>();
        amounts.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            UUID resource = Objects.requireNonNull(entry.getKey(), "storedResourceUUID");
            BigInteger amount = Objects.requireNonNull(entry.getValue(), "storedAmount");
            if (amount.signum() < 0) {
                throw new IllegalArgumentException("QIO physical storage baselines cannot be negative");
            }
            copy.put(resource, amount);
        });
        return Collections.unmodifiableMap(copy);
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        String trimmed = value.trim();
        if (trimmed.isEmpty() || trimmed.length() > 256) {
            throw new IllegalArgumentException(name + " must contain 1..256 characters");
        }
        return trimmed;
    }

    private static long requireRevision(long value, String name) {
        if (value < ANY_REVISION) {
            throw new IllegalArgumentException(name + " must be -1 or non-negative");
        }
        return value;
    }
}
