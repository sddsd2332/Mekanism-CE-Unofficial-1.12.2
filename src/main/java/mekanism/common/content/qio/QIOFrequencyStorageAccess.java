package mekanism.common.content.qio;

import mekanism.api.qio.external.IQIOFrequencyStorageAccess;
import mekanism.api.qio.external.IQIOStorageView;
import mekanism.api.qio.external.QIOFrequencyReference;
import mekanism.common.frequency.FrequencyManager;
import mekanism.common.frequency.FrequencyType;
import mekanism.common.util.SecurityUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;
import java.util.UUID;

/** Server-side access point for dashboard-independent QIO automation. */
public final class QIOFrequencyStorageAccess implements IQIOFrequencyStorageAccess {

    public static final QIOFrequencyStorageAccess INSTANCE = new QIOFrequencyStorageAccess();

    private QIOFrequencyStorageAccess() {
    }

    @Nonnull
    public QIOFrequencyReference createReference(@Nonnull QIOFrequency frequency,
          @Nullable UUID bindingPlayerUUID) {
        Objects.requireNonNull(frequency, "frequency");
        return new QIOFrequencyReference(frequency.getFrequencyUUID(), frequency.getName(),
              frequency.getOwner(), frequency.getSecurity(), bindingPlayerUUID);
    }

    @Nullable
    @Override
    public IQIOStorageView open(@Nonnull QIOFrequencyReference reference,
          @Nullable UUID requester) {
        Objects.requireNonNull(reference, "reference");
        QIOFrequency frequency = resolve(reference);
        if (frequency == null || !isAccessible(reference, frequency, requester)) {
            return null;
        }
        return new QIOStorageViewImpl(frequency,
              () -> isAccessible(reference, frequency, requester), null, null);
    }

    /** Performs the same identity and security check as {@link #open} without allocating a view. */
    public boolean canAccess(@Nonnull QIOFrequencyReference reference, @Nullable UUID requester) {
        Objects.requireNonNull(reference, "reference");
        QIOFrequency frequency = resolve(reference);
        return frequency != null && isAccessible(reference, frequency, requester);
    }

    @Nullable
    private QIOFrequency resolve(QIOFrequencyReference reference) {
        FrequencyManager<QIOFrequency> manager = FrequencyType.QIO.getManager(
              reference.getOwnerUUID(), reference.getSecurityMode());
        if (manager == null) {
            return null;
        }
        QIOFrequency frequency = manager.getFrequency(reference.getFrequencyName());
        return matches(reference, frequency) ? frequency : null;
    }

    private boolean isAccessible(QIOFrequencyReference reference, QIOFrequency frequency,
          @Nullable UUID requester) {
        return QIOStorageManager.isLoaded() && matches(reference, frequency) &&
              resolve(reference) == frequency &&
              SecurityUtils.canAccess(frequency.getSecurity(),
                    reference.getBindingPlayerUUID(), frequency.getOwner()) &&
              SecurityUtils.canAccess(frequency.getSecurity(), requester, frequency.getOwner());
    }

    private static boolean matches(QIOFrequencyReference reference,
          @Nullable QIOFrequency frequency) {
        return frequency != null && frequency.isValid() && !frequency.isRemoved() &&
              reference.getFrequencyUUID().equals(frequency.getFrequencyUUID()) &&
              reference.getFrequencyName().equals(frequency.getName()) &&
              Objects.equals(reference.getOwnerUUID(), frequency.getOwner()) &&
              reference.getSecurityMode() == frequency.getSecurity();
    }
}
