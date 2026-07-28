package mekanism.api.qio.external;

import javax.annotation.Nonnull;
import java.math.BigInteger;
import java.util.Objects;

/** Exact old and new amount for one resource in a QIO change batch. */
public final class QIOStorageChange {

    private final QIOStorageEntry resource;
    private final BigInteger oldAmount;
    private final BigInteger newAmount;

    public QIOStorageChange(QIOStorageEntry resource, BigInteger oldAmount, BigInteger newAmount) {
        this.resource = Objects.requireNonNull(resource, "resource").withAmount(BigInteger.ZERO);
        this.oldAmount = requireAmount(oldAmount, "oldAmount");
        this.newAmount = requireAmount(newAmount, "newAmount");
    }

    @Nonnull
    public QIOStorageEntry getResource() {
        return resource;
    }

    @Nonnull
    public BigInteger getOldAmount() {
        return oldAmount;
    }

    @Nonnull
    public BigInteger getNewAmount() {
        return newAmount;
    }

    public long getOldAmountClamped() {
        return clamp(oldAmount);
    }

    public long getNewAmountClamped() {
        return clamp(newAmount);
    }

    private static BigInteger requireAmount(BigInteger amount, String name) {
        Objects.requireNonNull(amount, name);
        if (amount.signum() < 0) {
            throw new IllegalArgumentException(name + " cannot be negative");
        }
        return amount;
    }

    private static long clamp(BigInteger amount) {
        return amount.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) >= 0 ? Long.MAX_VALUE : amount.longValue();
    }
}
