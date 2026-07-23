package mekanism.common.content.qio;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import mekanism.api.Action;
import mekanism.common.tier.QIODriveTier;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class QIODriveRecord {

    static final int DATA_VERSION = 1;

    private final UUID driveId;
    private ResourceLocation definitionName;
    private final QIODriveType driveType;
    private QIOAmount countCapacity;
    private QIOAmount storageCapacity;
    private int typeCapacity;
    private boolean countCapacityUnlimited;
    private boolean typeCapacityUnlimited;
    private BigInteger totalStorageUnits = BigInteger.ZERO;
    private final Object2LongMap<UUID> contents = new Object2LongOpenHashMap<>();
    private final Map<UUID, QIOResourceKind> resourceKinds = new HashMap<>();

    public QIODriveRecord(UUID driveId, QIODriveTier tier) {
        this(driveId, Objects.requireNonNull(tier, "tier").getDefinition(), QIODriveType.MIXED);
    }

    public QIODriveRecord(UUID driveId, QIODriveTier tier, QIODriveType driveType) {
        this(driveId, Objects.requireNonNull(tier, "tier").getDefinition(), driveType);
    }

    public QIODriveRecord(UUID driveId, QIODriveDefinition definition) {
        this(driveId, definition, QIODriveType.MIXED);
    }

    public QIODriveRecord(UUID driveId, QIODriveDefinition definition, QIODriveType driveType) {
        this(driveId, Objects.requireNonNull(definition, "definition").getRegistryName(), driveType,
              Objects.requireNonNull(driveType, "driveType").getExactCountCapacity(definition),
              driveType.getExactStorageCapacity(definition), definition.getMaxTypes(), definition.isCountUnlimited(),
              definition.isTypesUnlimited());
        if (!QIODriveDefinition.isRegistered(definition)) {
            throw new IllegalArgumentException("QIO drive definition is not registered: " + definition.getRegistryName());
        }
    }

    private QIODriveRecord(UUID driveId, ResourceLocation definitionName, QIODriveType driveType,
          QIOAmount countCapacity, QIOAmount storageCapacity, int typeCapacity, boolean countCapacityUnlimited,
          boolean typeCapacityUnlimited) {
        this.driveId = Objects.requireNonNull(driveId, "driveId");
        this.definitionName = Objects.requireNonNull(definitionName, "definitionName");
        this.driveType = Objects.requireNonNull(driveType, "driveType");
        validateCapacities(driveType, countCapacity, storageCapacity, typeCapacity, countCapacityUnlimited,
              typeCapacityUnlimited);
        this.countCapacity = Objects.requireNonNull(countCapacity, "countCapacity");
        this.storageCapacity = Objects.requireNonNull(storageCapacity, "storageCapacity");
        this.typeCapacity = typeCapacity;
        this.countCapacityUnlimited = countCapacityUnlimited;
        this.typeCapacityUnlimited = typeCapacityUnlimited;
    }

    static QIODriveRecord read(UUID fileUUID, NBTTagCompound data) {
        int version = data.getInteger("version");
        if (version != DATA_VERSION) {
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
        QIODriveType driveType = QIODriveType.byName(data.getString("driveType"));
        if (driveType == null) {
            throw new IllegalArgumentException("Unknown QIO drive type: " + data.getString("driveType"));
        }
        ResourceLocation definitionName;
        try {
            definitionName = new ResourceLocation(data.getString("definition"));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid QIO drive definition: " + data.getString("definition"), e);
        }
        long projectedCountCapacity = data.getLong("countCapacity");
        long projectedStorageCapacity = data.getLong("storageCapacity");
        int typeCapacity = data.getInteger("typeCapacity");
        boolean unlimitedCount = data.getBoolean("unlimitedCount");
        boolean unlimitedTypes = data.getBoolean("unlimitedTypes");
        QIOAmount exactCountCapacity = readExactCapacity(data, "countCapacityExact");
        QIOAmount exactStorageCapacity = readExactCapacity(data, "storageCapacityExact");
        if (projectedCountCapacity != exactCountCapacity.longValueClamped() ||
              projectedStorageCapacity != exactStorageCapacity.longValueClamped()) {
            throw new IllegalArgumentException("QIO drive capacity projection does not match its exact snapshot");
        }
        QIODriveRecord record = new QIODriveRecord(fileUUID, definitionName, driveType, exactCountCapacity,
              exactStorageCapacity, typeCapacity, unlimitedCount, unlimitedTypes);
        QIODriveDefinition resolvedDefinition = QIODriveDefinition.byName(definitionName);
        if (resolvedDefinition == null) {
            QIOLog.LOGGER.warn("QIO drive {} uses unavailable definition {}; preserving its contents and capacity",
                  fileUUID, definitionName);
        } else if (!record.matchesDefinition(resolvedDefinition)) {
            QIOLog.LOGGER.warn("QIO drive {} uses a capacity snapshot that differs from definition {}; " +
                  "preserving it until an owning physical drive reconciles the definition", fileUUID, definitionName);
        }
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
            QIOResourceKind storedKind = QIOResourceKind.byName(entry.getString("kind"));
            if (storedKind == null) {
                throw new IllegalArgumentException("Unknown resource kind in QIO drive " + fileUUID);
            }
            QIOResourceKind registeredKind = QIOResourceTypeRegistry.INSTANCE.getKindByUUID(resource);
            if (storedKind != null && registeredKind != null && storedKind != registeredKind) {
                throw new IllegalArgumentException("Resource kind mismatch in QIO drive " + fileUUID);
            }
            QIOResourceKind kind = registeredKind != null ? registeredKind : storedKind;
            long previous = record.contents.getOrDefault(resource, 0L);
            QIOResourceKind previousKind = record.resourceKinds.get(resource);
            if (previous > 0 && previousKind != kind) {
                throw new IllegalArgumentException("Duplicate resource kinds in QIO drive " + fileUUID);
            }
            long combined;
            try {
                combined = Math.addExact(previous, amount);
            } catch (ArithmeticException e) {
                throw new IllegalArgumentException("Amount overflow in QIO drive " + fileUUID, e);
            }
            record.totalStorageUnits = record.totalStorageUnits.add(BigInteger.valueOf(amount)
                  .multiply(BigInteger.valueOf(QIOStorageUnits.getUnitsPerResource(kind))));
            record.contents.put(resource, combined);
            record.resourceKinds.put(resource, kind);
        }
        if (record.totalStorageUnits.compareTo(record.getExactStorageCapacity().toBigInteger()) > 0 ||
              record.contents.size() > record.getTypeCapacity()) {
            throw new IllegalArgumentException("QIO drive contents exceed the capacity of definition " +
                  record.definitionName);
        }
        long cachedCount = data.getLong("count");
        int cachedTypes = data.getInteger("types");
        long expectedCachedCount = record.getTotalCount();
        boolean staleStorageUnits = data.getLong("storageUnits") != record.getTotalStorageUnits();
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
        data.setString("definition", definitionName.toString());
        data.setString("driveType", driveType.getSerializedName());
        data.setLong("countCapacity", getCountCapacity());
        data.setLong("storageCapacity", getStorageCapacity());
        data.setByteArray("countCapacityExact", countCapacity.toBigInteger().toByteArray());
        data.setByteArray("storageCapacityExact", storageCapacity.toBigInteger().toByteArray());
        data.setInteger("typeCapacity", typeCapacity);
        data.setBoolean("unlimitedCount", countCapacityUnlimited);
        data.setBoolean("unlimitedTypes", typeCapacityUnlimited);
        data.setLong("count", getTotalCount());
        data.setLong("storageUnits", getTotalStorageUnits());
        data.setByteArray("storageUnitsExact", totalStorageUnits.toByteArray());
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
        if (kind == null || amount <= 0 || (!typeCapacityUnlimited && newType && contents.size() >= getTypeCapacity())) {
            return 0;
        }
        BigInteger remaining = storageCapacity.toBigInteger().subtract(totalStorageUnits);
        if (remaining.signum() <= 0) {
            return 0;
        }
        return QIOStorageUnits.getInsertableAmount(kind, amount, remaining);
    }

    public synchronized long insert(UUID resource, long amount, Action action) {
        QIOResourceKind kind = getKindForInsert(resource);
        if (resource == null || action == null || kind == null || !driveType.accepts(kind)) {
            return 0;
        }
        long previous = contents.getOrDefault(resource, 0L);
        long inserted = Math.min(getInsertable(kind, amount, previous == 0), Long.MAX_VALUE - previous);
        if (inserted > 0 && action.execute()) {
            try {
                long next = Math.addExact(previous, inserted);
                BigInteger insertedStorageUnits = BigInteger.valueOf(inserted)
                      .multiply(BigInteger.valueOf(QIOStorageUnits.getUnitsPerResource(kind)));
                BigInteger nextTotal = totalStorageUnits.add(insertedStorageUnits);
                if (next < 0 || nextTotal.compareTo(storageCapacity.toBigInteger()) > 0) {
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
            BigInteger extractedUnits = BigInteger.valueOf(extracted)
                  .multiply(BigInteger.valueOf(QIOStorageUnits.getUnitsPerResource(kind)));
            totalStorageUnits = totalStorageUnits.subtract(extractedUnits).max(BigInteger.ZERO);
        }
        return extracted;
    }

    @Deprecated
    public boolean upgradeTier(QIODriveTier newTier) {
        return newTier != null && QIODriveStorage.INSTANCE.upgradeDefinition(this, newTier.getDefinition());
    }

    public UUID getDriveId() {
        return driveId;
    }

    @Nullable
    public synchronized QIODriveTier getTier() {
        return QIODriveTier.byDefinition(getDefinition());
    }

    @Nullable
    public synchronized QIODriveDefinition getDefinition() {
        QIODriveDefinition definition = QIODriveDefinition.byName(definitionName);
        return matchesDefinition(definition) ? definition : null;
    }

    public synchronized ResourceLocation getDefinitionName() {
        return definitionName;
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
        return countCapacity.longValueClamped();
    }

    public synchronized long getStorageCapacity() {
        return storageCapacity.longValueClamped();
    }

    public synchronized QIOAmount getExactCountCapacity() {
        return countCapacity;
    }

    public synchronized QIOAmount getExactStorageCapacity() {
        return storageCapacity;
    }

    public synchronized int getTypeCapacity() {
        return typeCapacity;
    }

    public synchronized boolean hasUnlimitedCountCapacity() {
        return countCapacityUnlimited;
    }

    public synchronized boolean hasUnlimitedTypeCapacity() {
        return typeCapacityUnlimited;
    }

    public synchronized boolean hasCreativeCapacity() {
        return countCapacityUnlimited && typeCapacityUnlimited;
    }

    public synchronized long getTotalCount() {
        return getExactTotalCount().longValueClamped();
    }

    public synchronized long getTotalStorageUnits() {
        return totalStorageUnits.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0 ? Long.MAX_VALUE : totalStorageUnits.longValue();
    }

    public synchronized QIOAmount getExactTotalCount() {
        return QIOAmount.of(totalStorageUnits).divideRoundUp(QIOStorageUnits.UNITS_PER_ITEM);
    }

    public synchronized QIOAmount getExactTotalStorageUnits() {
        return QIOAmount.of(totalStorageUnits);
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

    synchronized boolean matchesDefinition(@Nullable QIODriveDefinition definition) {
        return getDefinitionUpdate(definition) == DefinitionUpdate.UNCHANGED;
    }

    synchronized boolean canApplyDefinition(@Nullable QIODriveDefinition definition) {
        return getDefinitionUpdate(definition) != DefinitionUpdate.INCOMPATIBLE;
    }

    synchronized DefinitionUpdate getDefinitionUpdate(@Nullable QIODriveDefinition definition) {
        if (!QIODriveDefinition.isRegistered(definition)) {
            return DefinitionUpdate.INCOMPATIBLE;
        }
        QIOAmount candidateCount;
        QIOAmount candidateStorage;
        try {
            candidateCount = driveType.getExactCountCapacity(definition);
            candidateStorage = driveType.getExactStorageCapacity(definition);
        } catch (RuntimeException ignored) {
            return DefinitionUpdate.INCOMPATIBLE;
        }
        int candidateTypes = definition.getMaxTypes();
        boolean candidateUnlimitedCount = definition.isCountUnlimited();
        boolean candidateUnlimitedTypes = definition.isTypesUnlimited();
        boolean sameName = definitionName.equals(definition.getRegistryName());
        boolean sameCapacity = countCapacity.equals(candidateCount) && storageCapacity.equals(candidateStorage) &&
              typeCapacity == candidateTypes && countCapacityUnlimited == candidateUnlimitedCount &&
              typeCapacityUnlimited == candidateUnlimitedTypes;
        if (sameName && sameCapacity) {
            return DefinitionUpdate.UNCHANGED;
        }
        boolean countNonDecreasing = candidateUnlimitedCount || (!countCapacityUnlimited &&
              candidateCount.compareTo(countCapacity) >= 0 && candidateStorage.compareTo(storageCapacity) >= 0);
        boolean typesNonDecreasing = candidateUnlimitedTypes || (!typeCapacityUnlimited && candidateTypes >= typeCapacity);
        boolean nonDecreasing = countNonDecreasing && typesNonDecreasing;
        boolean increasesCapacity = (candidateUnlimitedCount && !countCapacityUnlimited) ||
              (!candidateUnlimitedCount && !countCapacityUnlimited &&
                    (candidateCount.compareTo(countCapacity) > 0 || candidateStorage.compareTo(storageCapacity) > 0)) ||
              (candidateUnlimitedTypes && !typeCapacityUnlimited) ||
              (!candidateUnlimitedTypes && !typeCapacityUnlimited && candidateTypes > typeCapacity);
        if (nonDecreasing && increasesCapacity) {
            return DefinitionUpdate.UPGRADED;
        }
        // Addon updates may reduce a definition while retaining its stable ID.
        // Accept that resize only when every stored resource still fits. A
        // differently named smaller item is never allowed to shrink a record.
        if (sameName && (candidateUnlimitedCount || totalStorageUnits.compareTo(candidateStorage.toBigInteger()) <= 0) &&
              (candidateUnlimitedTypes || contents.size() <= candidateTypes)) {
            return DefinitionUpdate.RESIZED;
        }
        return DefinitionUpdate.INCOMPATIBLE;
    }

    synchronized DefinitionUpdate applyDefinition(QIODriveDefinition definition) {
        DefinitionUpdate update = getDefinitionUpdate(definition);
        if (!update.changed()) {
            return update;
        }
        QIOAmount candidateCount = driveType.getExactCountCapacity(definition);
        QIOAmount candidateStorage = driveType.getExactStorageCapacity(definition);
        int candidateTypes = definition.getMaxTypes();
        definitionName = definition.getRegistryName();
        countCapacity = candidateCount;
        storageCapacity = candidateStorage;
        typeCapacity = candidateTypes;
        countCapacityUnlimited = definition.isCountUnlimited();
        typeCapacityUnlimited = definition.isTypesUnlimited();
        return update;
    }

    private static void validateCapacities(QIODriveType driveType, QIOAmount countCapacity,
          QIOAmount storageCapacity, int typeCapacity,
          boolean countCapacityUnlimited, boolean typeCapacityUnlimited) {
        Objects.requireNonNull(driveType, "driveType");
        Objects.requireNonNull(countCapacity, "countCapacity");
        Objects.requireNonNull(storageCapacity, "storageCapacity");
        if (countCapacity.isZero()) {
            throw new IllegalArgumentException("QIO drive count capacity must be positive");
        }
        if (countCapacityUnlimited) {
            QIOAmount expectedCount = driveType.getExactCountCapacity(Long.MAX_VALUE);
            if (!countCapacity.equals(expectedCount)) {
                throw new IllegalArgumentException("Invalid exact unlimited QIO count capacity: " + countCapacity +
                      " (expected " + expectedCount + ")");
            }
        }
        QIOAmount expectedStorage = countCapacity.multiply(QIOStorageUnits.UNITS_PER_ITEM);
        if (!storageCapacity.equals(expectedStorage)) {
            throw new IllegalArgumentException("Invalid QIO drive storage capacity: " + storageCapacity +
                  " (expected " + expectedStorage + ")");
        }
        if (typeCapacityUnlimited) {
            if (typeCapacity != Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Unlimited QIO type capacity must use Integer.MAX_VALUE sentinel");
            }
        } else if (typeCapacity <= 0) {
            throw new IllegalArgumentException("Invalid QIO drive type capacity: " + typeCapacity);
        }
    }

    private static QIOAmount readExactCapacity(NBTTagCompound data, String key) {
        if (!data.hasKey(key, NBT.TAG_BYTE_ARRAY)) {
            throw new IllegalArgumentException("Missing exact QIO capacity snapshot: " + key);
        }
        BigInteger value = new BigInteger(data.getByteArray(key));
        if (value.signum() <= 0) {
            throw new IllegalArgumentException("Invalid exact QIO capacity snapshot: " + key);
        }
        return QIOAmount.of(value);
    }

    enum DefinitionUpdate {
        UNCHANGED,
        UPGRADED,
        RESIZED,
        INCOMPATIBLE;

        boolean changed() {
            return this == UPGRADED || this == RESIZED;
        }
    }
}
