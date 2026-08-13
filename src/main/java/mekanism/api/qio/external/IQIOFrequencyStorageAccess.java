package mekanism.api.qio.external;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/** Opens a lifecycle-bound QIO view for an exact persisted frequency reference. */
@FunctionalInterface
public interface IQIOFrequencyStorageAccess {

    @Nullable
    IQIOStorageView open(@Nonnull QIOFrequencyReference reference, @Nullable UUID requester);
}
