package mekanism.common.content.qio;

import mekanism.common.tier.QIODriveTier;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * World-level owner of QIO drive records and the duplicate-UUID mount lock.
 */
public final class QIODriveStorage {

    public static final QIODriveStorage INSTANCE = new QIODriveStorage();
    private static final int INDEX_VERSION = 2;

    private final Map<UUID, QIODriveRecord> records = new HashMap<>();
    private final Set<UUID> dirtyDrives = new HashSet<>();
    private final Set<UUID> damagedDrives = new HashSet<>();
    private final Map<UUID, QIODriveMount> activeMounts = new HashMap<>();
    private File worldDirectory;
    private File driveDirectory;
    private boolean indexDirty;
    private boolean loaded;
    private long mountRevision;

    private QIODriveStorage() {
    }

    public synchronized void createOrLoad(World world) {
        if (world != null && !world.isRemote) {
            createOrLoad(world.getSaveHandler().getWorldDirectory());
        }
    }

    /** Exposed for deterministic storage tests and tooling. */
    public synchronized void createOrLoad(File worldDirectory) {
        if (worldDirectory == null) {
            return;
        }
        try {
            File canonical = worldDirectory.getCanonicalFile();
            if (loaded && canonical.equals(this.worldDirectory)) {
                return;
            }
            clearRuntimeState();
            this.worldDirectory = canonical;
            File qioDirectory = new File(canonical, "mekanism/qio");
            driveDirectory = new File(qioDirectory, "drives");
            QIOFileIO.ensureDirectory(driveDirectory);
            loadFiles();
            loaded = true;
        } catch (IOException e) {
            QIOLog.LOGGER.error("Unable to initialize QIO drive storage", e);
            clearRuntimeState();
            loaded = false;
        }
    }

    public synchronized boolean isLoaded() {
        return loaded;
    }

    @Nullable
    public synchronized QIODriveRecord getOrCreate(UUID driveId, QIODriveTier tier) {
        return getOrCreate(driveId, tier, QIODriveType.MIXED);
    }

    @Nullable
    public synchronized QIODriveRecord getOrCreate(UUID driveId, QIODriveTier tier, QIODriveType driveType) {
        requireLoaded();
        if (driveId == null || tier == null || driveType == null || damagedDrives.contains(driveId)) {
            return null;
        }
        QIODriveRecord record = records.get(driveId);
        if (record == null) {
            record = new QIODriveRecord(driveId, tier, driveType);
            records.put(driveId, record);
            dirtyDrives.add(driveId);
            indexDirty = true;
        } else if (record.getDriveType() != driveType) {
            // A UUID identifies one physical drive record. Never reinterpret
            // an existing record through a differently specialized item.
            return null;
        } else if (record.upgradeTier(tier)) {
            // A higher-tier physical drive may be encountered before it is
            // mounted (for example while restoring an inventory). Persist the
            // expanded capacity immediately; never downgrade an existing
            // record when a lower-tier copy is seen.
            dirtyDrives.add(driveId);
            indexDirty = true;
        }
        return record;
    }

    @Nullable
    public synchronized QIODriveRecord get(@Nullable UUID driveId) {
        return driveId == null ? null : records.get(driveId);
    }

    public synchronized boolean hasDrive(@Nullable UUID driveId) {
        return driveId != null && records.containsKey(driveId);
    }

    public synchronized boolean isDamaged(@Nullable UUID driveId) {
        return driveId != null && damagedDrives.contains(driveId);
    }

    public synchronized void markDriveDirty(@Nullable UUID driveId) {
        if (loaded && driveId != null && records.containsKey(driveId)) {
            dirtyDrives.add(driveId);
            indexDirty = true;
        }
    }

    public synchronized boolean upgradeTier(@Nullable UUID driveId, QIODriveTier tier) {
        if (driveId == null || tier == null) {
            return false;
        }
        QIODriveRecord record = records.get(driveId);
        if (record == null) {
            return false;
        }
        boolean changed = record.upgradeTier(tier);
        if (changed) {
            dirtyDrives.add(driveId);
            indexDirty = true;
        }
        return changed;
    }

    @Nonnull
    public synchronized MountResult tryMount(@Nullable UUID driveId, @Nullable QIODriveMount mount) {
        if (driveId == null || mount == null || !records.containsKey(driveId)) {
            return MountResult.MISSING_STORAGE;
        }
        QIODriveMount active = activeMounts.get(driveId);
        if (active == null) {
            activeMounts.put(driveId, mount);
            mountRevision++;
            return MountResult.MOUNTED;
        }
        if (active.equals(mount)) {
            return active.getHolder() == mount.getHolder() ? MountResult.ALREADY_MOUNTED : MountResult.DUPLICATE_UUID;
        }
        // The stable world position ordering wins even if frequencies tick in
        // a different order after a restart or chunk load.
        if (mount.compareTo(active) < 0) {
            activeMounts.put(driveId, mount);
            mountRevision++;
            return MountResult.MOUNTED;
        }
        return MountResult.DUPLICATE_UUID;
    }

    public synchronized void unmount(@Nullable QIODriveMount mount) {
        if (mount == null) {
            return;
        }
        List<UUID> toRemove = new ArrayList<>();
        for (Map.Entry<UUID, QIODriveMount> entry : activeMounts.entrySet()) {
            if (entry.getValue().equals(mount) && entry.getValue().getHolder() == mount.getHolder()) {
                toRemove.add(entry.getKey());
            }
        }
        for (UUID uuid : toRemove) {
            activeMounts.remove(uuid);
            mountRevision++;
        }
    }

    public synchronized void unmountAllForHolder(@Nullable IQIODriveHolder holder) {
        if (holder == null) {
            return;
        }
        List<UUID> toRemove = new ArrayList<>();
        for (Map.Entry<UUID, QIODriveMount> entry : activeMounts.entrySet()) {
            if (entry.getValue().getHolder() == holder) {
                toRemove.add(entry.getKey());
            }
        }
        for (UUID uuid : toRemove) {
            activeMounts.remove(uuid);
            mountRevision++;
        }
    }

    public synchronized long getMountRevision() {
        return mountRevision;
    }

    @Nullable
    public synchronized QIODriveMount getActiveMount(@Nullable UUID driveId) {
        return driveId == null ? null : activeMounts.get(driveId);
    }

    @Nonnull
    public synchronized Set<UUID> getDamagedDrives() {
        return Collections.unmodifiableSet(new HashSet<>(damagedDrives));
    }

    public void flush() {
        Map<UUID, QIODriveRecord> pending;
        synchronized (this) {
            if (!loaded) {
                return;
            }
            pending = new HashMap<>();
            for (UUID uuid : dirtyDrives) {
                QIODriveRecord record = records.get(uuid);
                if (record != null && !damagedDrives.contains(uuid)) {
                    pending.put(uuid, record);
                }
            }
        }
        Set<UUID> saved = new HashSet<>();
        for (Map.Entry<UUID, QIODriveRecord> entry : pending.entrySet()) {
            try {
                QIOFileIO.writeAtomic(QIOFileIO.dataFile(driveDirectory, entry.getKey()), entry.getValue().write());
                saved.add(entry.getKey());
            } catch (IOException e) {
                QIOLog.LOGGER.error("Unable to save QIO drive {}", entry.getKey(), e);
            }
        }
        synchronized (this) {
            dirtyDrives.removeAll(saved);
            if (indexDirty && dirtyDrives.isEmpty() && writeIndex()) {
                indexDirty = false;
            }
        }
    }

    public synchronized void reset() {
        clearRuntimeState();
    }

    private void loadFiles() throws IOException {
        Map<UUID, QIODriveRecord> loadedRecords = new HashMap<>();
        boolean migratedRecord = false;
        File[] allFiles = driveDirectory.listFiles(File::isFile);
        if (allFiles != null) {
            for (File file : allFiles) {
                UUID quarantined = QIOFileIO.parseQuarantinedDataFileUUID(file);
                if (quarantined != null) {
                    damagedDrives.add(quarantined);
                }
            }
        }
        for (File file : QIOFileIO.listDataFiles(driveDirectory)) {
            UUID uuid = QIOFileIO.parseDataFileUUID(file);
            if (uuid == null) {
                QIOLog.LOGGER.warn("Ignoring QIO drive file with an invalid UUID name: {}", file.getName());
                continue;
            }
            try {
                NBTTagCompound data = QIOFileIO.read(file);
                if (data == null) {
                    throw new IOException("File is empty");
                }
                loadedRecords.put(uuid, QIODriveRecord.read(uuid, data));
                if (data.getInteger("version") < QIODriveRecord.DATA_VERSION) {
                    dirtyDrives.add(uuid);
                    migratedRecord = true;
                }
                damagedDrives.remove(uuid);
            } catch (Exception e) {
                damagedDrives.add(uuid);
                QIOLog.LOGGER.error("QIO drive {} is damaged; it will remain unavailable until restored", uuid, e);
                QIOFileIO.quarantine(file);
            }
        }
        records.putAll(loadedRecords);
        indexDirty = migratedRecord || !isIndexValid(loadedRecords.keySet());
    }

    private boolean isIndexValid(Set<UUID> actualUUIDs) {
        File index = new File(driveDirectory.getParentFile(), "drive_index.dat");
        try {
            NBTTagCompound data = QIOFileIO.read(index);
            if (data == null || data.getInteger("version") != INDEX_VERSION) {
                return false;
            }
            Set<UUID> indexed = new HashSet<>();
            NBTTagList drives = data.getTagList("drives", NBT.TAG_COMPOUND);
            for (int i = 0; i < drives.tagCount(); i++) {
                indexed.add(UUID.fromString(drives.getCompoundTagAt(i).getString("uuid")));
            }
            return indexed.equals(actualUUIDs);
        } catch (Exception e) {
            QIOLog.LOGGER.warn("QIO drive index is damaged; rebuilding it", e);
            return false;
        }
    }

    private boolean writeIndex() {
        NBTTagCompound data = new NBTTagCompound();
        data.setInteger("version", INDEX_VERSION);
        NBTTagList drives = new NBTTagList();
        List<UUID> uuids = new ArrayList<>(records.keySet());
        uuids.sort((left, right) -> left.toString().compareTo(right.toString()));
        for (UUID uuid : uuids) {
            QIODriveRecord record = records.get(uuid);
            NBTTagCompound entry = new NBTTagCompound();
            entry.setString("uuid", uuid.toString());
            entry.setString("tier", record.getTier().getSerializedName());
            entry.setString("driveType", record.getDriveType().getSerializedName());
            entry.setLong("count", record.getTotalCount());
            entry.setInteger("types", record.getTotalTypes());
            entry.setString("lastKnownFile", uuid.toString() + ".dat");
            drives.appendTag(entry);
        }
        data.setTag("drives", drives);
        try {
            QIOFileIO.writeAtomic(new File(driveDirectory.getParentFile(), "drive_index.dat"), data);
            return true;
        } catch (IOException e) {
            QIOLog.LOGGER.error("Unable to save QIO drive index", e);
            return false;
        }
    }

    private void requireLoaded() {
        if (!loaded) {
            throw new IllegalStateException("QIO drives are not loaded for a world");
        }
    }

    private void clearRuntimeState() {
        records.clear();
        dirtyDrives.clear();
        damagedDrives.clear();
        activeMounts.clear();
        worldDirectory = null;
        driveDirectory = null;
        indexDirty = false;
        loaded = false;
        mountRevision = 0;
    }

    public enum MountResult {
        MOUNTED,
        ALREADY_MOUNTED,
        DUPLICATE_UUID,
        INVALID_DRIVE,
        MISSING_STORAGE
    }
}
