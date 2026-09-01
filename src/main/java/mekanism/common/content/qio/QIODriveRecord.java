package mekanism.common.content.qio;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import mekanism.api.Action;
import mekanism.api.qio.resource.QIOResourceDescriptor;
import mekanism.api.qio.resource.QIOResourceFamily;
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

    static final int DATA_VERSION = 3;
    private static final int LEGACY_DATA_VERSION = 1;
    private static final int EXTENSIBLE_RESOURCE_DATA_VERSION = 2;

    private final UUID driveId;
    private ResourceLocation definitionName;
    private final ResourceLocation specializationName;
    private QIOAmount countCapacity;
    private QIOAmount storageCapacity;
    private int typeCapacity;
    private boolean countCapacityUnlimited;
    private boolean typeCapacityUnlimited;
    private BigInteger totalStorageUnits = BigInteger.ZERO;
    private final Object2LongMap<UUID> contents = new Object2LongOpenHashMap<>();
    private final Map<UUID, StoredResourceMetadata> resourceMetadata = new HashMap<>();

    public QIODriveRecord(UUID driveId, QIODriveTier tier) {
        this(driveId, Objects.requireNonNull(tier, "tier").getDefinition(), QIODriveSpecializations.MIXED);
    }

    public QIODriveRecord(UUID driveId, QIODriveTier tier, QIODriveType driveType) {
        this(driveId, Objects.requireNonNull(tier, "tier").getDefinition(),
              Objects.requireNonNull(driveType, "driveType").getSpecialization());
    }

    public QIODriveRecord(UUID driveId, QIODriveTier tier, QIODriveSpecialization specialization) {
        this(driveId, Objects.requireNonNull(tier, "tier").getDefinition(), specialization);
    }

    public QIODriveRecord(UUID driveId, QIODriveDefinition definition) {
        this(driveId, definition, QIODriveSpecializations.MIXED);
    }

    public QIODriveRecord(UUID driveId, QIODriveDefinition definition, QIODriveType driveType) {
        this(driveId, definition, Objects.requireNonNull(driveType, "driveType").getSpecialization());
    }

    public QIODriveRecord(UUID driveId, QIODriveDefinition definition,
          QIODriveSpecialization specialization) {
        this(driveId, Objects.requireNonNull(definition, "definition").getRegistryName(),
              Objects.requireNonNull(specialization, "specialization").getRegistryName(),
              specialization.getExactCountCapacity(definition), specialization.getExactStorageCapacity(definition),
              definition.getMaxTypes(), definition.isCountUnlimited(),
              definition.isTypesUnlimited());
        if (!QIODriveDefinition.isRegistered(definition)) {
            throw new IllegalArgumentException("QIO drive definition is not registered: " + definition.getRegistryName());
        }
        if (!QIODriveSpecializationRegistry.INSTANCE.isRegistered(specialization)) {
            throw new IllegalArgumentException("QIO drive specialization is not registered: " +
                  specialization.getRegistryName());
        }
    }

    private QIODriveRecord(UUID driveId, ResourceLocation definitionName, ResourceLocation specializationName,
          QIOAmount countCapacity, QIOAmount storageCapacity, int typeCapacity, boolean countCapacityUnlimited,
          boolean typeCapacityUnlimited) {
        this.driveId = Objects.requireNonNull(driveId, "driveId");
        this.definitionName = Objects.requireNonNull(definitionName, "definitionName");
        this.specializationName = Objects.requireNonNull(specializationName, "specializationName");
        validateCapacities(countCapacity, storageCapacity, typeCapacity, countCapacityUnlimited,
              typeCapacityUnlimited);
        this.countCapacity = Objects.requireNonNull(countCapacity, "countCapacity");
        this.storageCapacity = Objects.requireNonNull(storageCapacity, "storageCapacity");
        this.typeCapacity = typeCapacity;
        this.countCapacityUnlimited = countCapacityUnlimited;
        this.typeCapacityUnlimited = typeCapacityUnlimited;
    }

    static QIODriveRecord read(UUID fileUUID, NBTTagCompound data) {
        int version = data.getInteger("version");
        if (version != LEGACY_DATA_VERSION && version != EXTENSIBLE_RESOURCE_DATA_VERSION &&
              version != DATA_VERSION) {
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
        ResourceLocation specializationName;
        if (version < DATA_VERSION) {
            QIODriveType driveType = QIODriveType.byName(data.getString("driveType"));
            if (driveType == null) {
                throw new IllegalArgumentException("Unknown QIO drive type: " + data.getString("driveType"));
            }
            specializationName = driveType.getSpecialization().getRegistryName();
        } else {
            try {
                specializationName = new ResourceLocation(data.getString("specialization"));
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("Invalid QIO drive specialization: " +
                      data.getString("specialization"), e);
            }
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
        QIODriveRecord record = new QIODriveRecord(fileUUID, definitionName, specializationName, exactCountCapacity,
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
            StoredResourceMetadata storedMetadata = version == LEGACY_DATA_VERSION ?
                  StoredResourceMetadata.fromLegacyKind(entry.getString("kind"), fileUUID) :
                  StoredResourceMetadata.read(entry, fileUUID);
            QIOResourceType registeredType = QIOResourceTypeRegistry.INSTANCE.getTypeByUUID(resource);
            if (registeredType != null && !storedMetadata.matches(registeredType)) {
                throw new IllegalArgumentException("Resource metadata mismatch in QIO drive " + fileUUID);
            }
            StoredResourceMetadata metadata = registeredType == null ? storedMetadata :
                  StoredResourceMetadata.from(registeredType);
            long previous = record.contents.getOrDefault(resource, 0L);
            StoredResourceMetadata previousMetadata = record.resourceMetadata.get(resource);
            if (previous > 0 && !metadata.equals(previousMetadata)) {
                throw new IllegalArgumentException("Duplicate resource metadata in QIO drive " + fileUUID);
            }
            long combined;
            try {
                combined = Math.addExact(previous, amount);
            } catch (ArithmeticException e) {
                throw new IllegalArgumentException("Amount overflow in QIO drive " + fileUUID, e);
            }
            record.totalStorageUnits = record.totalStorageUnits.add(BigInteger.valueOf(amount)
                  .multiply(BigInteger.valueOf(metadata.storageUnitsPerUnit)));
            record.contents.put(resource, combined);
            record.resourceMetadata.put(resource, metadata);
        }
        if (version < DATA_VERSION && (record.totalStorageUnits.compareTo(
              record.getExactStorageCapacity().toBigInteger()) > 0 ||
              record.contents.size() > record.getTypeCapacity())) {
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
        data.setString("specialization", specializationName.toString());
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
            getResourceMetadata(entry.getKey()).write(stored);
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
        if (kind == null || amount <= 0 || isOverCapacity() ||
              (!typeCapacityUnlimited && newType && contents.size() >= getTypeCapacity())) {
            return 0;
        }
        BigInteger remaining = storageCapacity.toBigInteger().subtract(totalStorageUnits);
        if (remaining.signum() <= 0) {
            return 0;
        }
        return QIOStorageUnits.getInsertableAmount(kind, amount, remaining);
    }

    public synchronized long getInsertable(QIOResourceDescriptor descriptor, long amount, boolean newType) {
        return descriptor == null ? 0 : getInsertable(descriptor.getStorageUnitsPerUnit(), amount, newType);
    }

    public synchronized long getInsertable(long storageUnitsPerUnit, long amount, boolean newType) {
        if (storageUnitsPerUnit <= 0 || amount <= 0 || isOverCapacity() ||
              (!typeCapacityUnlimited && newType && contents.size() >= getTypeCapacity())) {
            return 0;
        }
        BigInteger remaining = storageCapacity.toBigInteger().subtract(totalStorageUnits);
        return remaining.signum() <= 0 ? 0 :
              QIOStorageUnits.getInsertableAmount(storageUnitsPerUnit, amount, remaining);
    }

    public synchronized long insert(UUID resource, long amount, Action action) {
        StoredResourceMetadata metadata = getMetadataForInsert(resource);
        QIODriveSpecialization specialization = getSpecialization();
        if (resource == null || action == null || metadata == null || specialization == null ||
              !specialization.accepts(metadata.family, metadata.codecId)) {
            return 0;
        }
        long previous = contents.getOrDefault(resource, 0L);
        long inserted = Math.min(getInsertable(metadata.storageUnitsPerUnit, amount, previous == 0),
              Long.MAX_VALUE - previous);
        if (inserted > 0 && action.execute()) {
            try {
                long next = Math.addExact(previous, inserted);
                BigInteger insertedStorageUnits = BigInteger.valueOf(inserted)
                      .multiply(BigInteger.valueOf(metadata.storageUnitsPerUnit));
                BigInteger nextTotal = totalStorageUnits.add(insertedStorageUnits);
                if (next < 0 || nextTotal.compareTo(storageCapacity.toBigInteger()) > 0) {
                    return 0;
                }
                contents.put(resource, next);
                resourceMetadata.put(resource, metadata);
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
        QIOResourceType registeredType = QIOResourceTypeRegistry.INSTANCE.getTypeByUUID(resource);
        if (registeredType == null || !registeredType.isResolved()) {
            return 0;
        }
        long previous = contents.getOrDefault(resource, 0L);
        if (previous <= 0) {
            return 0;
        }
        StoredResourceMetadata metadata = getResourceMetadata(resource);
        long extracted = Math.min(amount, previous);
        if (extracted > 0 && action.execute()) {
            long remaining = previous - extracted;
            if (remaining == 0) {
                contents.removeLong(resource);
                resourceMetadata.remove(resource);
            } else {
                contents.put(resource, remaining);
            }
            BigInteger extractedUnits = BigInteger.valueOf(extracted)
                  .multiply(BigInteger.valueOf(metadata.storageUnitsPerUnit));
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

    @Nullable
    public QIODriveSpecialization getSpecialization() {
        return QIODriveSpecializationRegistry.INSTANCE.get(specializationName);
    }

    public ResourceLocation getSpecializationName() {
        return specializationName;
    }

    /** @deprecated Custom specializations cannot be represented by the legacy enum. */
    @Deprecated
    @Nullable
    public QIODriveType getDriveType() {
        return QIODriveType.fromSpecialization(getSpecialization());
    }

    /** Verifies the authoritative family before a specialized drive is mutated. */
    public synchronized boolean acceptsResource(@Nullable UUID resource) {
        if (resource == null) {
            return false;
        }
        QIODriveSpecialization specialization = getSpecialization();
        if (specialization == null) {
            return false;
        }
        if (specialization.isMixed()) {
            return true;
        }
        QIOResourceType type = QIOResourceTypeRegistry.INSTANCE.getTypeByUUID(resource);
        StoredResourceMetadata metadata = resourceMetadata.get(resource);
        if (type != null && metadata != null && !metadata.matches(type)) {
            return false;
        }
        String family = type == null ? metadata == null ? null : metadata.family : type.getFamily();
        ResourceLocation codecId = type == null ? metadata == null ? null : metadata.codecId : type.getCodecId();
        return specialization.accepts(family, codecId);
    }

    /**
     * Unknown resource types are retained so removing a content mod does not
     * destroy drive data. Any resource whose family is still known must match
     * the specialization before the drive may be mounted.
     */
    public synchronized boolean hasValidResourceKinds() {
        QIODriveSpecialization specialization = getSpecialization();
        if (specialization == null) {
            return false;
        }
        if (specialization.isMixed()) {
            return true;
        }
        for (UUID resource : contents.keySet()) {
            QIOResourceType type = QIOResourceTypeRegistry.INSTANCE.getTypeByUUID(resource);
            StoredResourceMetadata stored = resourceMetadata.get(resource);
            if (stored == null || type != null && !stored.matches(type)) {
                return false;
            }
            String family = type == null ? stored.family : type.getFamily();
            ResourceLocation codecId = type == null ? stored.codecId : type.getCodecId();
            if (!specialization.accepts(family, codecId)) {
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

    /** A resized drive remains readable but cannot accept anything until it is back within both limits. */
    public synchronized boolean isOverCapacity() {
        return !countCapacityUnlimited && totalStorageUnits.compareTo(storageCapacity.toBigInteger()) > 0 ||
              !typeCapacityUnlimited && contents.size() > typeCapacity;
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

    @Nullable
    public synchronized QIOResourceKind getResourceKind(UUID resource) {
        QIOResourceType type = QIOResourceTypeRegistry.INSTANCE.getTypeByUUID(resource);
        if (type != null) {
            return type.getKind();
        }
        StoredResourceMetadata metadata = resourceMetadata.get(resource);
        return metadata == null ? null : metadata.legacyKind();
    }

    @Nullable
    public synchronized String getResourceFamily(@Nullable UUID resource) {
        QIOResourceType type = QIOResourceTypeRegistry.INSTANCE.getTypeByUUID(resource);
        if (type != null) {
            return type.getFamily();
        }
        StoredResourceMetadata metadata = resourceMetadata.get(resource);
        return metadata == null ? null : metadata.family;
    }

    @Nullable
    public synchronized ResourceLocation getResourceCodecId(@Nullable UUID resource) {
        QIOResourceType type = QIOResourceTypeRegistry.INSTANCE.getTypeByUUID(resource);
        if (type != null) {
            return type.getCodecId();
        }
        StoredResourceMetadata metadata = resourceMetadata.get(resource);
        return metadata == null ? null : metadata.codecId;
    }

    public synchronized long getResourceStorageUnitsPerUnit(@Nullable UUID resource) {
        QIOResourceType type = QIOResourceTypeRegistry.INSTANCE.getTypeByUUID(resource);
        if (type != null) {
            return type.getStorageUnitsPerUnit();
        }
        StoredResourceMetadata metadata = resourceMetadata.get(resource);
        return metadata == null ? 0 : metadata.storageUnitsPerUnit;
    }

    private StoredResourceMetadata getResourceMetadata(UUID resource) {
        QIOResourceType type = QIOResourceTypeRegistry.INSTANCE.getTypeByUUID(resource);
        StoredResourceMetadata stored = resourceMetadata.get(resource);
        if (type != null) {
            StoredResourceMetadata registered = StoredResourceMetadata.from(type);
            if (stored != null && !stored.equals(registered)) {
                throw new IllegalStateException("QIO resource metadata changed for " + resource);
            }
            return registered;
        }
        if (stored == null) {
            throw new IllegalStateException("Missing QIO resource metadata for " + resource);
        }
        return stored;
    }

    @Nullable
    private StoredResourceMetadata getMetadataForInsert(@Nullable UUID resource) {
        if (resource == null) {
            return null;
        }
        QIOResourceType type = QIOResourceTypeRegistry.INSTANCE.getTypeByUUID(resource);
        if (type == null || !type.isResolved()) {
            return null;
        }
        StoredResourceMetadata registered = StoredResourceMetadata.from(type);
        StoredResourceMetadata stored = resourceMetadata.get(resource);
        if (stored != null && !stored.equals(registered)) {
            return null;
        }
        return registered;
    }

    synchronized boolean matchesDefinition(@Nullable QIODriveDefinition definition) {
        return getDefinitionUpdate(definition) == DefinitionUpdate.UNCHANGED;
    }

    synchronized boolean canApplyDefinition(@Nullable QIODriveDefinition definition) {
        return getDefinitionUpdate(definition) != DefinitionUpdate.INCOMPATIBLE;
    }

    synchronized DefinitionUpdate getDefinitionUpdate(@Nullable QIODriveDefinition definition) {
        return getDefinitionUpdate(definition, getSpecialization());
    }

    synchronized DefinitionUpdate getDefinitionUpdate(@Nullable QIODriveDefinition definition,
          @Nullable QIODriveSpecialization specialization) {
        if (!QIODriveDefinition.isRegistered(definition) ||
              !QIODriveSpecializationRegistry.INSTANCE.isRegistered(specialization) ||
              !specializationName.equals(specialization.getRegistryName())) {
            return DefinitionUpdate.INCOMPATIBLE;
        }
        QIOAmount candidateCount;
        QIOAmount candidateStorage;
        try {
            candidateCount = specialization.getExactCountCapacity(definition);
            candidateStorage = specialization.getExactStorageCapacity(definition);
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
        // Registry or configuration changes may shrink a stable definition. Keep the record
        // mounted in a recoverable over-capacity state instead of hiding all of its contents.
        if (sameName) {
            return DefinitionUpdate.RESIZED;
        }
        return DefinitionUpdate.INCOMPATIBLE;
    }

    synchronized DefinitionUpdate applyDefinition(QIODriveDefinition definition) {
        return applyDefinition(definition, getSpecialization());
    }

    synchronized DefinitionUpdate applyDefinition(QIODriveDefinition definition,
          @Nullable QIODriveSpecialization specialization) {
        DefinitionUpdate update = getDefinitionUpdate(definition, specialization);
        if (!update.changed()) {
            return update;
        }
        QIOAmount candidateCount = specialization.getExactCountCapacity(definition);
        QIOAmount candidateStorage = specialization.getExactStorageCapacity(definition);
        int candidateTypes = definition.getMaxTypes();
        definitionName = definition.getRegistryName();
        countCapacity = candidateCount;
        storageCapacity = candidateStorage;
        typeCapacity = candidateTypes;
        countCapacityUnlimited = definition.isCountUnlimited();
        typeCapacityUnlimited = definition.isTypesUnlimited();
        return update;
    }

    private static void validateCapacities(QIOAmount countCapacity,
          QIOAmount storageCapacity, int typeCapacity,
          boolean countCapacityUnlimited, boolean typeCapacityUnlimited) {
        Objects.requireNonNull(countCapacity, "countCapacity");
        Objects.requireNonNull(storageCapacity, "storageCapacity");
        if (countCapacity.isZero()) {
            throw new IllegalArgumentException("QIO drive count capacity must be positive");
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

    private static final class StoredResourceMetadata {

        private final String family;
        private final ResourceLocation codecId;
        private final long storageUnitsPerUnit;

        private StoredResourceMetadata(String family, ResourceLocation codecId, long storageUnitsPerUnit) {
            this.family = QIOResourceFamily.requireValid(family);
            this.codecId = Objects.requireNonNull(codecId, "codecId");
            if (storageUnitsPerUnit <= 0) {
                throw new IllegalArgumentException("QIO resource storage units must be positive");
            }
            this.storageUnitsPerUnit = storageUnitsPerUnit;
        }

        private static StoredResourceMetadata from(QIOResourceType type) {
            return new StoredResourceMetadata(type.getFamily(), type.getCodecId(),
                  type.getStorageUnitsPerUnit());
        }

        private static StoredResourceMetadata fromLegacyKind(String name, UUID driveId) {
            QIOResourceKind kind = QIOResourceKind.byName(name);
            if (kind == null || !kind.isBuiltin()) {
                throw new IllegalArgumentException("Unknown resource kind in QIO drive " + driveId);
            }
            return new StoredResourceMetadata(kind.getFamily(),
                  Objects.requireNonNull(kind.getCodecId()), QIOStorageUnits.getUnitsPerResource(kind));
        }

        private static StoredResourceMetadata read(NBTTagCompound data, UUID driveId) {
            if (!data.hasKey("family", NBT.TAG_STRING) || !data.hasKey("codec", NBT.TAG_STRING) ||
                  !data.hasKey("storageUnitsPerUnit", NBT.TAG_LONG)) {
                throw new IllegalArgumentException("Incomplete resource metadata in QIO drive " + driveId);
            }
            try {
                return new StoredResourceMetadata(data.getString("family"),
                      new ResourceLocation(data.getString("codec")), data.getLong("storageUnitsPerUnit"));
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("Invalid resource metadata in QIO drive " + driveId, e);
            }
        }

        private boolean matches(QIOResourceType type) {
            return family.equals(type.getFamily()) && codecId.equals(type.getCodecId()) &&
                  storageUnitsPerUnit == type.getStorageUnitsPerUnit();
        }

        @Nullable
        private QIOResourceKind legacyKind() {
            for (QIOResourceKind kind : QIOResourceKind.values()) {
                if (kind.getCodecId() != null && kind.getCodecId().equals(codecId)) {
                    return kind;
                }
            }
            return null;
        }

        private void write(NBTTagCompound data) {
            data.setString("family", family);
            data.setString("codec", codecId.toString());
            data.setLong("storageUnitsPerUnit", storageUnitsPerUnit);
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj || obj instanceof StoredResourceMetadata other &&
                  storageUnitsPerUnit == other.storageUnitsPerUnit && family.equals(other.family) &&
                  codecId.equals(other.codecId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(family, codecId, storageUnitsPerUnit);
        }
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
