package mekanism.common.content.qio;

import mekanism.api.qio.resource.QIOResourceDescriptor;

import javax.annotation.Nonnull;
import java.math.BigInteger;

/** Fixed-point conversion used by every QIO count-capacity calculation. */
public final class QIOStorageUnits {

    public static final long UNITS_PER_ITEM = 1_000L;

    private QIOStorageUnits() {
    }

    public static long getUnitsPerResource(@Nonnull QIOResourceKind kind) {
        return kind == QIOResourceKind.ITEM ? UNITS_PER_ITEM : 1L;
    }

    public static long getUnitsPerResource(@Nonnull QIOResourceDescriptor descriptor) {
        return descriptor.getStorageUnitsPerUnit();
    }

    public static long toStorageUnits(@Nonnull QIOResourceKind kind, long amount) {
        if (amount <= 0) {
            return 0;
        }
        long unitsPerResource = getUnitsPerResource(kind);
        return amount > Long.MAX_VALUE / unitsPerResource ? Long.MAX_VALUE : amount * unitsPerResource;
    }

    public static long toStorageUnits(@Nonnull QIOResourceDescriptor descriptor, long amount) {
        return toStorageUnits(descriptor.getStorageUnitsPerUnit(), amount);
    }

    public static long toStorageUnits(long unitsPerResource, long amount) {
        if (amount <= 0 || unitsPerResource <= 0) {
            return 0;
        }
        return amount > Long.MAX_VALUE / unitsPerResource ? Long.MAX_VALUE : amount * unitsPerResource;
    }

    public static long toStorageCapacity(long countCapacity) {
        return toStorageUnits(QIOResourceKind.ITEM, countCapacity);
    }

    /** Converts exact storage units to the integer count shown by mixed QIO views. */
    public static long toItemEquivalent(long storageUnits) {
        if (storageUnits <= 0) {
            return 0;
        }
        return storageUnits / UNITS_PER_ITEM + (storageUnits % UNITS_PER_ITEM == 0 ? 0 : 1);
    }

    public static long getInsertableAmount(@Nonnull QIOResourceKind kind, long requested, long availableStorageUnits) {
        return getInsertableAmount(kind, requested, BigInteger.valueOf(Math.max(0, availableStorageUnits)));
    }

    public static long getInsertableAmount(@Nonnull QIOResourceKind kind, long requested,
          @Nonnull BigInteger availableStorageUnits) {
        return getInsertableAmount(getUnitsPerResource(kind), requested, availableStorageUnits);
    }

    public static long getInsertableAmount(@Nonnull QIOResourceDescriptor descriptor, long requested,
          @Nonnull BigInteger availableStorageUnits) {
        return getInsertableAmount(descriptor.getStorageUnitsPerUnit(), requested, availableStorageUnits);
    }

    public static long getInsertableAmount(long unitsPerResource, long requested,
          @Nonnull BigInteger availableStorageUnits) {
        if (requested <= 0 || availableStorageUnits.signum() <= 0) {
            return 0;
        }
        if (unitsPerResource <= 0) {
            return 0;
        }
        BigInteger units = BigInteger.valueOf(unitsPerResource);
        BigInteger requestedAmount = BigInteger.valueOf(requested);
        BigInteger availableAmount = availableStorageUnits.divide(units);
        return availableAmount.compareTo(requestedAmount) >= 0 ? requested : availableAmount.longValue();
    }

    public static long safeAdd(long first, long second) {
        if (first < 0 || second < 0 || second > Long.MAX_VALUE - first) {
            return Long.MAX_VALUE;
        }
        return first + second;
    }
}
