package mekanism.api.qio.external;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Immutable view of the part of a logical claim currently backed by physical QIO storage. */
public final class QIOClaimBacking {

    private final QIOResourceClaim claim;
    private final long contentsRevision;
    private final long claimRevision;
    private final Map<UUID, Long> backedAmounts;
    private final Map<UUID, Long> unsupportedAmounts;

    public QIOClaimBacking(@Nonnull QIOResourceClaim claim, long contentsRevision,
          long claimRevision, @Nonnull Map<UUID, Long> backedAmounts) {
        this.claim = Objects.requireNonNull(claim, "claim");
        if (contentsRevision < 0 || claimRevision < 0) {
            throw new IllegalArgumentException("QIO claim backing revisions cannot be negative");
        }
        this.contentsRevision = contentsRevision;
        this.claimRevision = claimRevision;
        this.backedAmounts = copyBackedAmounts(claim, backedAmounts);
        Map<UUID, Long> unsupported = new LinkedHashMap<>();
        for (Map.Entry<UUID, Long> entry : claim.getResourceAmounts().entrySet()) {
            long backed = this.backedAmounts.getOrDefault(entry.getKey(), 0L);
            if (backed < entry.getValue()) {
                unsupported.put(entry.getKey(), entry.getValue() - backed);
            }
        }
        unsupportedAmounts = Collections.unmodifiableMap(unsupported);
    }

    @Nonnull
    public QIOResourceClaim getClaim() {
        return claim;
    }

    public long getContentsRevision() {
        return contentsRevision;
    }

    public long getClaimRevision() {
        return claimRevision;
    }

    @Nonnull
    public Map<UUID, Long> getBackedAmounts() {
        return backedAmounts;
    }

    @Nonnull
    public Map<UUID, Long> getUnsupportedAmounts() {
        return unsupportedAmounts;
    }

    public boolean isFullyBacked() {
        return unsupportedAmounts.isEmpty();
    }

    private static Map<UUID, Long> copyBackedAmounts(QIOResourceClaim claim,
          Map<UUID, Long> amounts) {
        Objects.requireNonNull(amounts, "backedAmounts");
        Map<UUID, Long> copy = new LinkedHashMap<>();
        amounts.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            UUID resource = Objects.requireNonNull(entry.getKey(), "resourceUUID");
            Long amount = Objects.requireNonNull(entry.getValue(), "backedAmount");
            long claimed = claim.getAmount(resource);
            if (amount <= 0 || amount > claimed) {
                throw new IllegalArgumentException("Backed amount must be within its logical claim");
            }
            copy.put(resource, amount);
        });
        return Collections.unmodifiableMap(copy);
    }
}
