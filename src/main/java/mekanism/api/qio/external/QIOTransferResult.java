package mekanism.api.qio.external;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.UUID;

/** Persistent idempotent QIO resource transfer receipt. */
public final class QIOTransferResult {

    public enum Status {
        APPLIED,
        REPLAYED,
        NO_CAPACITY,
        RECONCILIATION_CONFLICT,
        TRANSFER_ID_CONFLICT,
        INVALID_REQUEST,
        FAILED
    }

    private final UUID transferId;
    private final Status status;
    private final long requestedAmount;
    private final long transferredAmount;

    public QIOTransferResult(@Nonnull UUID transferId, @Nonnull Status status, long requestedAmount,
          long transferredAmount) {
        this.transferId = Objects.requireNonNull(transferId, "Transfer id cannot be null");
        this.status = Objects.requireNonNull(status, "Transfer status cannot be null");
        if (requestedAmount <= 0 || transferredAmount < 0 || transferredAmount > requestedAmount) {
            throw new IllegalArgumentException("Invalid QIO transfer amounts");
        }
        this.requestedAmount = requestedAmount;
        this.transferredAmount = transferredAmount;
    }

    @Nonnull
    public UUID getTransferId() {
        return transferId;
    }

    @Nonnull
    public Status getStatus() {
        return status;
    }

    public long getRequestedAmount() {
        return requestedAmount;
    }

    public long getTransferredAmount() {
        return transferredAmount;
    }

    public boolean isSuccess() {
        return status == Status.APPLIED || status == Status.REPLAYED;
    }
}
