package mekanism.api.qio.external;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** A server-tick-batched QIO storage change notification. */
public final class QIOStorageChangeBatch {

    private final long oldContentsRevision;
    private final long newContentsRevision;
    private final long oldCapacityRevision;
    private final long newCapacityRevision;
    private final long oldClaimRevision;
    private final long newClaimRevision;
    private final long accessRevision;
    private final List<QIOStorageChange> changes;
    private final boolean fullRescanRequired;
    private final boolean invalidated;

    public QIOStorageChangeBatch(long oldContentsRevision, long newContentsRevision,
          long oldCapacityRevision, long newCapacityRevision, long accessRevision,
          List<QIOStorageChange> changes, boolean fullRescanRequired, boolean invalidated) {
        this(oldContentsRevision, newContentsRevision, oldCapacityRevision, newCapacityRevision,
              0, 0, accessRevision, changes, fullRescanRequired, invalidated);
    }

    public QIOStorageChangeBatch(long oldContentsRevision, long newContentsRevision,
          long oldCapacityRevision, long newCapacityRevision, long oldClaimRevision,
          long newClaimRevision, long accessRevision, List<QIOStorageChange> changes,
          boolean fullRescanRequired, boolean invalidated) {
        this.oldContentsRevision = oldContentsRevision;
        this.newContentsRevision = newContentsRevision;
        this.oldCapacityRevision = oldCapacityRevision;
        this.newCapacityRevision = newCapacityRevision;
        this.oldClaimRevision = oldClaimRevision;
        this.newClaimRevision = newClaimRevision;
        this.accessRevision = accessRevision;
        this.changes = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(changes, "changes")));
        this.fullRescanRequired = fullRescanRequired;
        this.invalidated = invalidated;
    }

    @Nonnull
    public static QIOStorageChangeBatch invalidated(long contentsRevision, long capacityRevision,
          long accessRevision) {
        return invalidated(contentsRevision, capacityRevision, 0, accessRevision);
    }

    @Nonnull
    public static QIOStorageChangeBatch invalidated(long contentsRevision, long capacityRevision,
          long claimRevision, long accessRevision) {
        return new QIOStorageChangeBatch(contentsRevision, contentsRevision, capacityRevision,
              capacityRevision, claimRevision, claimRevision, accessRevision, Collections.emptyList(), true, true);
    }

    public long getOldContentsRevision() {
        return oldContentsRevision;
    }

    public long getNewContentsRevision() {
        return newContentsRevision;
    }

    public long getOldCapacityRevision() {
        return oldCapacityRevision;
    }

    public long getNewCapacityRevision() {
        return newCapacityRevision;
    }

    public long getOldClaimRevision() {
        return oldClaimRevision;
    }

    public long getNewClaimRevision() {
        return newClaimRevision;
    }

    public long getAccessRevision() {
        return accessRevision;
    }

    @Nonnull
    public List<QIOStorageChange> getChanges() {
        return changes;
    }

    public boolean isCapacityChanged() {
        return oldCapacityRevision != newCapacityRevision;
    }

    public boolean isClaimChanged() {
        return oldClaimRevision != newClaimRevision;
    }

    public boolean isFullRescanRequired() {
        return fullRescanRequired;
    }

    public boolean isInvalidated() {
        return invalidated;
    }

    @Nonnull
    public QIOStorageChangeBatch requiringFullRescan() {
        if (fullRescanRequired) {
            return this;
        }
        return new QIOStorageChangeBatch(oldContentsRevision, newContentsRevision,
              oldCapacityRevision, newCapacityRevision, oldClaimRevision, newClaimRevision,
              accessRevision, changes, true, invalidated);
    }
}
