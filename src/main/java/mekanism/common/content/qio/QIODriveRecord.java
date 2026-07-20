package mekanism.common.content.qio;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import mekanism.api.Action;
import mekanism.common.tier.QIODriveTier;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class QIODriveRecord {

    static final int DATA_VERSION = 3;

    private final UUID driveId;
    private QIODriveTier tier;
    private final QIODriveType driveType;
    private long totalStorageUnits;
    private final Object2LongMap<UUID> contents = new Object2LongOpenHashMap<>();
    private final Map<UUID, QIOResourceKind> resourceKinds = new HashMap<>();

    public QIODriveRecord(UUID driveId, QIODriveTier tier) {
        this(driveId, tier, QIODriveType.MIXED);
    }

    public QIODriveRecord(UUID driveId, QIODriveTier tier, QIODriveType driveType) {
        this.driveId = Objects.requireNonNull(driveId, "driveId");
        this.tier = Objects.requireNonNull(tier, "tier");
        this.driveType = Objects.requireNonNull(driveType, "driveType");
    }

    static QIODriveRecord read(UUID fileUUID, NBTTagCompound data) {
        int version = data.getInteger("version");
        if (version < 1 || version > DATA_VERSION) {
            throw new IllegalArgumentException("Unsupported QIO drive version: " + version);
        }
        UUID storedUUID;
        try {
            storedUUID = UUID.fromString(data.getString("uuid"));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid QIO drive UUID", e);
        }
        if (!fileUUID.equals(storedUUID)) {
            throw new IllegalArgumentException("QIO drive UUID does not match its file name");
        }
        QIODriveTier tier = QIODriveTier.byName(data.getString("tier"));
        if (tier == null) {
            throw new IllegalArgumentException("Unknown QIO drive tier: " + data.getString("tier"));
        }
        QIODriveType driveType = version == 1 ? QIODriveType.MIXED : QIODriveType.byName(data.getString("driveType"));
        if (driveType == null) {
            throw new IllegalArgumentException("Unknown QIO drive type: " + data.getString("driveType"));
        }
        QIODriveRecord record = new QIODriveRecord(fileUUID, tier, driveType);
        long legacyRawCount = 0;
        NBTTagList storedContents = data.getTagList("contents", NBT.TAG_COMPOUND);
        for (int i = 0; i < storedContents.tagCount(); i++) {
            NBTTagCompound entry = storedContents.getCompoundTagAt(i);
            UUID resource;
            try {
                resource = UUID.fromString(entry.getString("resource"));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid resource UUID in QIO drive " + fileUUID, e);
            }
            long amount = entry.getLong("amount");
            if (amount <= 0) {
                throw new IllegalArgumentException("Non-positive amount in QIO drive " + fileUUID);
            }
            QIOResourceKind storedKind = version >= 3 ? QIOResourceKind.byName(entry.getString("kind")) : null;
            QIOResourceKind registeredKind = QIOResourceTypeRegistry.INSTANCE.getKindByUUID(resource);
            if (storedKind != null && registeredKind != null && storedKind != registeredKind) {
                throw new IllegalArgumentException("Resource kind mismatch in QIO drive " + fileUUID);
            }
            QIOResourceKind kind = registeredKind != null ? registeredKind : storedKind;
            if (kind == null) {
                kind = driveType.getResourceKind() == null ? QIOResourceKind.ITEM : driveType.getResourceKind();
            }
            long previous = record.contents.getOrDefault(resource, 0L);
            QIOResourceKind previousKind = record.resourceKinds.get(resource);
            if (previous > 0 && previousKind != kind) {
                throw new IllegalArgumentException("Duplicate resource kinds in QIO drive " + fileUUID);
            }
            long combined;
            try {
                combined = Math.addExact(previous, amount);
                legacyRawCount = Math.addExact(legacyRawCount, amount);
                record.totalStorageUnits = Math.addExact(record.totalStorageUnits,
                      QIOStorageUnits.toStorageUnits(kind, amount));
            } catch (ArithmeticException e) {
                throw new IllegalArgumentException("Amount overflow in QIO drive " + fileUUID, e);
            }
            record.contents.put(resource, combined);
            record.resourceKinds.put(resource, kind);
        }
        if (record.totalStorageUnits > record.getStorageCapacity() || record.contents.size() > record.getTypeCapacity()) {
            throw new IllegalArgumentException("QIO drive contents exceed the capacity of tier " + tier);
        }
        long cachedCount = data.getLong("count");
        int cachedTypes = data.getInteger("types");
        long expectedCachedCount = version >= 3 ? record.getTotalCount() : legacyRawCount;
        boolean staleStorageUnits = version >= 3 && data.getLong("storageUnits") != record.totalStorageUnits;
        if (cachedCount != expectedCachedCount || cachedTypes != record.contents.size() || staleStorageUnits) {
            QIOLog.LOGGER.warn("Rebuilt stale QIO drive metadata for {} (cached {}/{}, actual {}/{})",
                  fileUUID, cachedCount, cachedTypes, record.getTotalCount(), record.contents.size());
        }
        return record;
    }

    synchronized NBTTagCompound write() {
        NBTTagCompound data = new NBTTagCompound();
        data.setInteger("version", DATA_VERSION);
        data.setString("uuid", driveId.toString());
        data.setString("tier", tier.getSerializedName());
        data.setString("driveType", driveType.getSerializedName());
        data.setLong("count", getTotalCount());
        data.setLong("storageUnits", totalStorageUnits);
        data.setInteger("types", contents.size());
        NBTTagList storedContents = new NBTTagList();
        for (Object2LongMap.Entry<UUID> entry : contents.object2LongEntrySet()) {
            if (entry.getLongValue() <= 0) {
                continue;
            }
            NBTTagCompound stored = new NBTTagCompound();
            stored.setString("resource", entry.getKey().toString());
            stored.setString("kind", getResourceKind(entry.getKey()).getSerializedName());
            stored.setLong("amount", entry.getLongValue());
            storedContents.appendTag(stored);
        }
        data.setTag("contents", storedContents);
        return data;
    }

    public synchronized long getInsertable(long amount, boolean newType) {
        return getInsertable(QIOResourceKind.ITEM, amount, newType);
    }

    public synchronized long getInsertable(QIOResourceKind kind, long amount, boolean newType) {
        if (kind == null || amount <= 0 || totalStorageUnits < 0 || totalStorageUnits >= getStorageCapacity() ||
              newType && contents.size() >= getTypeCapacity()) {
            return 0;
        }
        return QIOStorageUnits.getInsertableAmount(kind, amount, getStorageCapacity() - totalStorageUnits);
    }

    public synchronized long insert(UUID resource, long amount, Action action) {
        QIOResourceKind kind = getKindForInsert(resource);
        if (resource == null || action == null || kind == null || !driveType.accepts(kind)) {
            return 0;
        }
        long previous = contents.getOrDefault(resource, 0L);
        long inserted = getInsertable(kind, amount, previous == 0);
        if (inserted > 0 && action.execute()) {
            try {
                long next = Math.addExact(previous, inserted);
                long insertedStorageUnits = QIOStorageUnits.toStorageUnits(kind, inserted);
                long nextTotal = Math.addExact(totalStorageUnits, insertedStorageUnits);
                if (next < 0 || nextTotal < 0 || nextTotal > getStorageCapacity()) {
                    return 0;
                }
                contents.put(resource, next);
                resourceKinds.put(resource, kind);
                totalStorageUnits = nextTotal;
            } catch (ArithmeticException ignored) {
                return 0;
            }
        }
        return inserted;
    }

    public synchronized long extract(UUID resource, long amount, Action action) {
        if (resource == null || action == null || amount <= 0) {
            return 0;
        }
        long previous = contents.getOrDefault(resource, 0L);
        if (previous <= 0) {
            return 0;
        }
        QIOResourceKind kind = getResourceKind(resource);
        long extracted = Math.min(amount, previous);
        if (extracted > 0 && action.execute()) {
            long remaining = previous - extracted;
            if (remaining == 0) {
                contents.removeLong(resource);
                resourceKinds.remove(resource);
            } else {
                contents.put(resource, remaining);
            }
            totalStorageUnits = Math.max(0, totalStorageUnits - QIOStorageUnits.toStorageUnits(kind, extracted));
        }
        return extracted;
    }

    public synchronized boolean upgradeTier(QIODriveTier newTier) {
        if (newTier.ordinal() <= tier.ordinal()) {
            return false;
        }
        tier = newTier;
        return true;
    }

    public UUID getDriveId() {
        return driveId;
    }

    public synchronized QIODriveTier getTier() {
        return tier;
    }

    public QIODriveType getDriveType() {
        return driveType;
    }

    /**
     * Verifies the authoritative resource kind before a specialized drive is
     * mutated. Mixed drives deliberately remain compatible with legacy and
     * unresolved resource UUIDs.
     */
    public synchronized boolean acceptsResource(@Nullable UUID resource) {
        if (resource == null) {
            return false;
        }
        if (driveType.isMixed()) {
            return true;
        }
        QIOResourceKind kind = QIOResourceTypeRegistry.INSTANCE.getKindByUUID(resource);
        if (kind == null) {
            kind = resourceKinds.get(resource);
        }
        return driveType.accepts(kind);
    }

    /**
     * Unknown resource types are retained so removing a content mod does not
     * destroy drive data. Any resource whose kind is still known must match
     * the specialization before the drive may be mounted.
     */
    public synchronized boolean hasValidResourceKinds() {
        if (driveType.isMixed()) {
            return true;
        }
        for (UUID resource : contents.keySet()) {
            QIOResourceKind kind = QIOResourceTypeRegistry.INSTANCE.getKindByUUID(resource);
            QIOResourceKind storedKind = resourceKinds.get(resource);
            if (kind != null && storedKind != null && kind != storedKind) {
                return false;
            }
            if (kind == null) {
                kind = storedKind;
            }
            if (kind != null && !driveType.accepts(kind)) {
                return false;
            }
        }
        return true;
    }

    public synchronized long getCountCapacity() {
        return driveType.getCountCapacity(tier);
    }

    public synchronized long getStorageCapacity() {
        return driveType.getStorageCapacity(tier);
    }

    public synchronized int getTypeCapacity() {
        return tier.getMaxTypes();
    }

    public synchronized long getTotalCount() {
        return QIOStorageUnits.toItemEquivalent(totalStorageUnits);
    }

    public synchronized long getTotalStorageUnits() {
        return totalStorageUnits;
    }

    public synchronized int getTotalTypes() {
        return contents.size();
    }

    public synchronized long getStored(UUID resource) {
        return contents.getOrDefault(resource, 0L);
    }

    public synchronized Object2LongMap<UUID> getContents() {
        return new Object2LongOpenHashMap<>(contents);
    }

    public synchronized QIOResourceKind getResourceKind(UUID resource) {
        QIOResourceKind kind = QIOResourceTypeRegistry.INSTANCE.getKindByUUID(resource);
        if (kind != null) {
            return kind;
        }
        kind = resourceKinds.get(resource);
        if (kind != null) {
            return kind;
        }
        QIOResourceKind specializedKind = driveType.getResourceKind();
        return specializedKind == null ? QIOResourceKind.ITEM : specializedKind;
    }

    @Nullable
    private QIOResourceKind getKindForInsert(@Nullable UUID resource) {
        if (resource == null) {
            return null;
        }
        QIOResourceKind registeredKind = QIOResourceTypeRegistry.INSTANCE.getKindByUUID(resource);
        QIOResourceKind storedKind = resourceKinds.get(resource);
        if (registeredKind != null && storedKind != null && registeredKind != storedKind) {
            return null;
        }
        if (registeredKind != null) {
            return registeredKind;
        }
        if (storedKind != null) {
            return storedKind;
        }
        return driveType.isMixed() ? QIOResourceKind.ITEM : null;
    }
}
