package mekanism.api.qio.external;

import javax.annotation.Nonnull;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** One internally consistent, immutable QIO storage snapshot. */
public final class QIOStorageSnapshot {

    private final UUID frequencyUUID;
    private final String frequencyName;
    private final long contentsRevision;
    private final long capacityRevision;
    private final long accessRevision;
    private final List<QIOStorageEntry> entries;
    private final BigInteger finiteCountCapacity;
    private final BigInteger finiteTypeCapacity;
    private final int unlimitedCountDrives;
    private final int unlimitedTypeDrives;

    public QIOStorageSnapshot(UUID frequencyUUID, String frequencyName, long contentsRevision,
          long capacityRevision, long accessRevision, List<QIOStorageEntry> entries,
          BigInteger finiteCountCapacity, BigInteger finiteTypeCapacity, int unlimitedCountDrives,
          int unlimitedTypeDrives) {
        this.frequencyUUID = Objects.requireNonNull(frequencyUUID, "frequencyUUID");
        this.frequencyName = frequencyName == null ? "" : frequencyName;
        this.contentsRevision = contentsRevision;
        this.capacityRevision = capacityRevision;
        this.accessRevision = accessRevision;
        this.entries = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(entries, "entries")));
        this.finiteCountCapacity = requireNonNegative(finiteCountCapacity, "finiteCountCapacity");
        this.finiteTypeCapacity = requireNonNegative(finiteTypeCapacity, "finiteTypeCapacity");
        this.unlimitedCountDrives = Math.max(0, unlimitedCountDrives);
        this.unlimitedTypeDrives = Math.max(0, unlimitedTypeDrives);
    }

    @Nonnull
    public UUID getFrequencyUUID() {
        return frequencyUUID;
    }

    @Nonnull
    public String getFrequencyName() {
        return frequencyName;
    }

    public long getContentsRevision() {
        return contentsRevision;
    }

    public long getCapacityRevision() {
        return capacityRevision;
    }

    public long getAccessRevision() {
        return accessRevision;
    }

    @Nonnull
    public List<QIOStorageEntry> getEntries() {
        return entries;
    }

    @Nonnull
    public BigInteger getFiniteCountCapacity() {
        return finiteCountCapacity;
    }

    @Nonnull
    public BigInteger getFiniteTypeCapacity() {
        return finiteTypeCapacity;
    }

    public int getUnlimitedCountDrives() {
        return unlimitedCountDrives;
    }

    public int getUnlimitedTypeDrives() {
        return unlimitedTypeDrives;
    }

    public boolean hasUnlimitedCountCapacity() {
        return unlimitedCountDrives > 0;
    }

    public boolean hasUnlimitedTypeCapacity() {
        return unlimitedTypeDrives > 0;
    }

    private static BigInteger requireNonNegative(BigInteger value, String name) {
        Objects.requireNonNull(value, name);
        if (value.signum() < 0) {
            throw new IllegalArgumentException(name + " cannot be negative");
        }
        return value;
    }
}
