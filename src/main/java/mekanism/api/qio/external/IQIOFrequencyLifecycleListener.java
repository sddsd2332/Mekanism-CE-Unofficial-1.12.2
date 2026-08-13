package mekanism.api.qio.external;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/** Coordinates external persistent state with normal QIO frequency deletion. */
public interface IQIOFrequencyLifecycleListener {

    @Nonnull
    QIOFrequencyDeleteCheck beforeDelete(@Nonnull QIOFrequencyReference reference,
          @Nullable UUID requester);

    default void afterDelete(@Nonnull QIOFrequencyReference reference,
          @Nullable UUID requester) {
    }
}
