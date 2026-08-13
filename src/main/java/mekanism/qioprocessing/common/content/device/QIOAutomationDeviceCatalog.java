package mekanism.qioprocessing.common.content.device;

import mekanism.qioprocessing.api.machine.QIOAutomationDeviceLocation;
import mekanism.qioprocessing.common.content.QIOProcessingDataException;
import mekanism.qioprocessing.common.content.QIOProcessingNbt;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Persistent online/offline device directory owned by one QIO processing frequency. */
public final class QIOAutomationDeviceCatalog {

    private static final int MAX_PERSISTED_DEVICES = 1_000_000;

    private final Map<UUID, QIOAutomationDeviceSnapshot> devices = new LinkedHashMap<>();
    private final Map<ChunkKey, List<UUID>> devicesByChunk = new LinkedHashMap<>();
    private long revision;

    public long getRevision() {
        return revision;
    }

    public int size() {
        return devices.size();
    }

    @Nullable
    public QIOAutomationDeviceSnapshot get(@Nullable UUID deviceUUID) {
        return deviceUUID == null ? null : devices.get(deviceUUID);
    }

    @Nonnull
    public List<QIOAutomationDeviceSnapshot> getDevices() {
        List<QIOAutomationDeviceSnapshot> snapshots = new ArrayList<>(devices.values());
        snapshots.sort(Comparator.comparing(snapshot -> snapshot.getDeviceUUID().toString()));
        return Collections.unmodifiableList(snapshots);
    }

    /** Returns only records whose persisted coordinate belongs to one chunk. */
    @Nonnull
    public List<QIOAutomationDeviceSnapshot> getDevicesInChunk(int dimension, int chunkX,
          int chunkZ) {
        List<UUID> ids = devicesByChunk.get(new ChunkKey(dimension, chunkX, chunkZ));
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        List<QIOAutomationDeviceSnapshot> snapshots = new ArrayList<>(ids.size());
        for (UUID deviceUUID : ids) {
            QIOAutomationDeviceSnapshot snapshot = devices.get(deviceUUID);
            if (snapshot != null) {
                snapshots.add(snapshot);
            }
        }
        snapshots.sort(Comparator.comparing(snapshot -> snapshot.getDeviceUUID().toString()));
        return Collections.unmodifiableList(snapshots);
    }

    public boolean observe(@Nonnull QIOAutomationDeviceSnapshot snapshot, int maximumDevices) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (maximumDevices <= 0) {
            throw new IllegalArgumentException("maximumDevices must be positive");
        }
        QIOAutomationDeviceSnapshot previous = devices.get(snapshot.getDeviceUUID());
        if (previous == null && devices.size() >= maximumDevices) {
            throw new IllegalStateException("QIO automation device directory limit reached");
        }
        if (previous != null && previous.sameLiveObservation(snapshot)) {
            return false;
        }
        if (previous != null) {
            unindex(previous);
        }
        devices.put(snapshot.getDeviceUUID(), snapshot);
        index(snapshot);
        incrementRevision();
        return true;
    }

    public boolean markOffline(@Nonnull UUID deviceUUID,
          @Nonnull QIOAutomationDeviceLocation expectedLocation, long currentTick) {
        QIOAutomationDeviceSnapshot existing = devices.get(
              Objects.requireNonNull(deviceUUID, "deviceUUID"));
        if (existing == null || !existing.getLocation().equals(
              Objects.requireNonNull(expectedLocation, "expectedLocation")) ||
              !existing.isOnline()) {
            return false;
        }
        devices.put(deviceUUID, existing.offline(currentTick));
        incrementRevision();
        return true;
    }

    /** Loaded files cannot prove that any old TileEntity is still online. */
    public boolean markAllOfflineAfterLoad() {
        boolean changed = false;
        for (Map.Entry<UUID, QIOAutomationDeviceSnapshot> entry : devices.entrySet()) {
            if (entry.getValue().isOnline()) {
                entry.setValue(entry.getValue().offline(entry.getValue().getLastSeenTick()));
                changed = true;
            }
        }
        if (changed) {
            incrementRevision();
        }
        return changed;
    }

    public boolean remove(@Nonnull UUID deviceUUID, long expectedRevision) {
        if (revision != expectedRevision) {
            throw new IllegalStateException("QIO automation device directory revision changed");
        }
        QIOAutomationDeviceSnapshot existing = devices.get(
              Objects.requireNonNull(deviceUUID, "deviceUUID"));
        if (existing == null) {
            return false;
        }
        if (existing.isOnline()) {
            throw new IllegalStateException("An online QIO automation device cannot be forgotten");
        }
        devices.remove(deviceUUID);
        unindex(existing);
        incrementRevision();
        return true;
    }

    /**
     * Removes an authoritative device membership without relying on a client-observed revision.
     * The location guard prevents a delayed lifecycle callback from deleting a newer observation
     * of the same persistent UUID at another position.
     */
    public boolean removeAtLocation(@Nonnull UUID deviceUUID,
          @Nonnull QIOAutomationDeviceLocation expectedLocation) {
        QIOAutomationDeviceSnapshot existing = devices.get(
              Objects.requireNonNull(deviceUUID, "deviceUUID"));
        if (existing == null || !existing.getLocation().equals(
              Objects.requireNonNull(expectedLocation, "expectedLocation"))) {
            return false;
        }
        devices.remove(deviceUUID);
        unindex(existing);
        incrementRevision();
        return true;
    }

    @Nonnull
    public NBTTagCompound write() {
        NBTTagCompound data = new NBTTagCompound();
        data.setLong("revision", revision);
        NBTTagList storedDevices = new NBTTagList();
        getDevices().forEach(snapshot -> storedDevices.appendTag(snapshot.write()));
        data.setTag("devices", storedDevices);
        return data;
    }

    @Nonnull
    public static QIOAutomationDeviceCatalog read(@Nonnull NBTTagCompound data)
          throws QIOProcessingDataException {
        try {
            QIOAutomationDeviceCatalog catalog = new QIOAutomationDeviceCatalog();
            catalog.revision = QIOProcessingNbt.requireNonNegative(data.getLong("revision"),
                  "automationDeviceDirectoryRevision");
            NBTTagList devices = data.getTagList("devices", NBT.TAG_COMPOUND);
            if (devices.tagCount() > MAX_PERSISTED_DEVICES) {
                throw new QIOProcessingDataException("QIO automation device directory is too large");
            }
            for (int index = 0; index < devices.tagCount(); index++) {
                QIOAutomationDeviceSnapshot snapshot = QIOAutomationDeviceSnapshot.read(
                      devices.getCompoundTagAt(index));
                if (catalog.devices.put(snapshot.getDeviceUUID(), snapshot) != null) {
                    throw new QIOProcessingDataException("Duplicate QIO automation device snapshot " +
                          snapshot.getDeviceUUID());
                }
                catalog.index(snapshot);
            }
            return catalog;
        } catch (QIOProcessingDataException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new QIOProcessingDataException("Invalid QIO automation device directory", e);
        }
    }

    private void incrementRevision() {
        if (revision == Long.MAX_VALUE) {
            throw new IllegalStateException("QIO automation device directory revision exhausted");
        }
        revision++;
    }

    private void index(QIOAutomationDeviceSnapshot snapshot) {
        devicesByChunk.computeIfAbsent(ChunkKey.of(snapshot.getLocation()),
              ignored -> new ArrayList<>()).add(snapshot.getDeviceUUID());
    }

    private void unindex(QIOAutomationDeviceSnapshot snapshot) {
        ChunkKey key = ChunkKey.of(snapshot.getLocation());
        List<UUID> ids = devicesByChunk.get(key);
        if (ids != null) {
            ids.remove(snapshot.getDeviceUUID());
            if (ids.isEmpty()) {
                devicesByChunk.remove(key);
            }
        }
    }

    private static final class ChunkKey {

        private final int dimension;
        private final int chunkX;
        private final int chunkZ;

        private ChunkKey(int dimension, int chunkX, int chunkZ) {
            this.dimension = dimension;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
        }

        private static ChunkKey of(QIOAutomationDeviceLocation location) {
            return new ChunkKey(location.dimension(), location.position().getX() >> 4,
                  location.position().getZ() >> 4);
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj || obj instanceof ChunkKey other &&
                  dimension == other.dimension && chunkX == other.chunkX &&
                  chunkZ == other.chunkZ;
        }

        @Override
        public int hashCode() {
            int result = dimension;
            result = 31 * result + chunkX;
            return 31 * result + chunkZ;
        }
    }
}
