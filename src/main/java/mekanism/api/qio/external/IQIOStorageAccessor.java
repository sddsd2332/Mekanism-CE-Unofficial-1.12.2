package mekanism.api.qio.external;

import javax.annotation.Nullable;
import java.util.UUID;

/** Opens a QIO storage view after applying Mekanism security rules. */
@FunctionalInterface
public interface IQIOStorageAccessor {

    @Nullable
    IQIOStorageView open(@Nullable UUID requester);
}
