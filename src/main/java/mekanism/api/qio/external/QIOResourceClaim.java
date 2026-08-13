package mekanism.api.qio.external;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Immutable logical ownership of resources that are still physically stored in QIO. */
public final class QIOResourceClaim {

    public enum State {
        ACTIVE,
        CONSUMING,
        RELEASED
    }

    private final UUID claimId;
    private final String ownerNamespace;
    private final String ownerId;
    private final long priority;
    private final long enqueueSequence;
    private final long revision;
    private final State state;
    private final Map<UUID, Long> resourceAmounts;

    public QIOResourceClaim(@Nonnull UUID claimId, @Nonnull String ownerNamespace, @Nonnull String ownerId,
          long priority, long enqueueSequence, long revision, @Nonnull State state,
          @Nonnull Map<UUID, Long> resourceAmounts) {
        this.claimId = Objects.requireNonNull(claimId, "claimId");
        this.ownerNamespace = requireText(ownerNamespace, "ownerNamespace");
        this.ownerId = requireText(ownerId, "ownerId");
        this.priority = priority;
        this.enqueueSequence = requireNonNegative(enqueueSequence, "enqueueSequence");
        this.revision = requireNonNegative(revision, "revision");
        this.state = Objects.requireNonNull(state, "state");
        this.resourceAmounts = copyAmounts(resourceAmounts);
    }

    @Nonnull
    public UUID getClaimId() {
        return claimId;
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

    public long getRevision() {
        return revision;
    }

    @Nonnull
    public State getState() {
        return state;
    }

    @Nonnull
    public Map<UUID, Long> getResourceAmounts() {
        return resourceAmounts;
    }

    public long getAmount(@Nonnull UUID resourceUUID) {
        return resourceAmounts.getOrDefault(Objects.requireNonNull(resourceUUID, "resourceUUID"), 0L);
    }

    private static Map<UUID, Long> copyAmounts(Map<UUID, Long> amounts) {
        Objects.requireNonNull(amounts, "resourceAmounts");
        Map<UUID, Long> copy = new LinkedHashMap<>();
        amounts.entrySet().stream()
              .sorted(Map.Entry.comparingByKey())
              .forEach(entry -> {
                  UUID resource = Objects.requireNonNull(entry.getKey(), "resourceUUID");
                  Long amount = Objects.requireNonNull(entry.getValue(), "resourceAmount");
                  if (amount <= 0) {
                      throw new IllegalArgumentException("QIO claim amounts must be positive");
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

    private static long requireNonNegative(long value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " cannot be negative");
        }
        return value;
    }
}
