package mekanism.common.content.qio;

import mekanism.api.gas.GasStack;
import mekanism.api.qio.resource.QIOResourceCodec;
import mekanism.api.qio.resource.QIOResourceDescriptor;
import mekanism.common.lib.inventory.HashedItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants.NBT;
import net.minecraftforge.fluids.FluidStack;

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
import java.util.Objects;

/**
 * World-scoped UUID registry for item, fluid, and gas templates.
 *
 * <p>The individual resource files are authoritative. The index is only a
 * discovery/diagnostic cache and is rebuilt whenever it is missing or invalid.</p>
 */
public final class QIOResourceTypeRegistry {

    public static final QIOResourceTypeRegistry INSTANCE = new QIOResourceTypeRegistry();
    private static final int INDEX_VERSION = 2;

    private final Map<UUID, QIOResourceType> byUUID = new HashMap<>();
    private final Map<QIOResourceType, UUID> byType = new HashMap<>();
    private final Set<UUID> dirtyTypes = new HashSet<>();
    private final Set<UUID> damagedTypes = new HashSet<>();
    private File requestedWorldDirectory;
    private File worldDirectory;
    private File resourceDirectory;
    private boolean indexDirty;
    private boolean loaded;

    private QIOResourceTypeRegistry() {
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
        File requested = worldDirectory.getAbsoluteFile();
        if (loaded && requested.equals(requestedWorldDirectory)) {
            return;
        }
        try {
            File canonical = worldDirectory.getCanonicalFile();
            if (loaded && canonical.equals(this.worldDirectory)) {
                requestedWorldDirectory = requested;
                return;
            }
            clearRuntimeState();
            requestedWorldDirectory = requested;
            this.worldDirectory = canonical;
            File qioDirectory = new File(canonical, "mekanism/qio");
            resourceDirectory = new File(qioDirectory, "resource_types");
            QIOFileIO.ensureDirectory(resourceDirectory);
            loadFiles();
            loaded = true;
        } catch (IOException e) {
            QIOLog.LOGGER.error("Unable to initialize QIO resource type storage", e);
            clearRuntimeState();
            loaded = false;
        }
    }

    public synchronized boolean isLoaded() {
        return loaded;
    }

    /** Tracks any resolved codec-backed resource in the world registry. */
    @Nonnull
    public synchronized UUID getOrTrack(@Nonnull QIOResourceDescriptor descriptor) {
        requireLoaded();
        Objects.requireNonNull(descriptor, "descriptor");
        if (!descriptor.isResolved()) {
            throw new IllegalArgumentException("Cannot track an unresolved QIO resource codec: " +
                  descriptor.getCodecId());
        }
        QIOResourceType key = QIOResourceType.descriptorLookup(descriptor);
        UUID uuid = byType.get(key);
        if (uuid == null) {
            uuid = addType(key, QIOResourceType.forDescriptor(UUID.randomUUID(), descriptor));
        }
        return uuid;
    }

    @Nonnull
    public synchronized <T> UUID getOrTrack(@Nonnull QIOResourceCodec<T> codec, @Nonnull T value) {
        return getOrTrack(QIOResourceDescriptor.of(codec, value));
    }

    @Nonnull
    public synchronized UUID getOrTrackItem(HashedItem item) {
        Objects.requireNonNull(item, "item");
        return getOrTrack(QIOResourceType.itemLookup(item).getDescriptor());
    }

    @Nonnull
    public synchronized UUID getOrTrackFluid(FluidStack fluid) {
        return getOrTrack(QIOResourceType.fluidLookup(fluid).getDescriptor());
    }

    @Nonnull
    public synchronized UUID getOrTrackGas(GasStack gas) {
        return getOrTrack(QIOResourceType.gasLookup(gas).getDescriptor());
    }

    @Nullable
    public synchronized UUID getUUIDFor(@Nullable QIOResourceDescriptor descriptor) {
        return loaded && descriptor != null ? byType.get(QIOResourceType.descriptorLookup(descriptor)) : null;
    }

    @Nullable
    public synchronized UUID getUUIDForItem(HashedItem item) {
        return loaded && item != null ? byType.get(QIOResourceType.itemLookup(item)) : null;
    }

    @Nullable
    public synchronized UUID getUUIDForFluid(FluidStack fluid) {
        return loaded && fluid != null ? byType.get(QIOResourceType.fluidLookup(fluid)) : null;
    }

    @Nullable
    public synchronized UUID getUUIDForGas(GasStack gas) {
        return loaded && gas != null ? byType.get(QIOResourceType.gasLookup(gas)) : null;
    }

    @Nullable
    public synchronized QIOResourceType getTypeByUUID(@Nullable UUID uuid) {
        return uuid == null ? null : byUUID.get(uuid);
    }

    @Nullable
    public synchronized QIOResourceDescriptor getDescriptorByUUID(@Nullable UUID uuid) {
        QIOResourceType type = uuid == null ? null : byUUID.get(uuid);
        return type == null ? null : type.getDescriptor();
    }

    @Nullable
    public synchronized String getFamilyByUUID(@Nullable UUID uuid) {
        QIOResourceType type = uuid == null ? null : byUUID.get(uuid);
        return type == null ? null : type.getFamily();
    }

    public synchronized long getStorageUnitsPerUnit(@Nullable UUID uuid) {
        QIOResourceType type = uuid == null ? null : byUUID.get(uuid);
        return type == null ? 0 : type.getStorageUnitsPerUnit();
    }

    public synchronized boolean isResolved(@Nullable UUID uuid) {
        QIOResourceType type = uuid == null ? null : byUUID.get(uuid);
        return type != null && type.isResolved();
    }

    public synchronized boolean isDamaged(@Nullable UUID uuid) {
        return uuid != null && damagedTypes.contains(uuid);
    }

    @Nonnull
    public synchronized Set<UUID> getDamagedTypes() {
        return Collections.unmodifiableSet(new HashSet<>(damagedTypes));
    }

    @Nullable
    public synchronized QIOResourceKind getKindByUUID(@Nullable UUID uuid) {
        QIOResourceType type = uuid == null ? null : byUUID.get(uuid);
        return type == null ? null : type.getKind();
    }

    @Nullable
    public synchronized FluidStack getFluidType(@Nullable UUID uuid) {
        QIOResourceType type = uuid == null ? null : byUUID.get(uuid);
        return type == null ? null : type.getFluidType();
    }

    @Nullable
    public synchronized GasStack getGasType(@Nullable UUID uuid) {
        QIOResourceType type = uuid == null ? null : byUUID.get(uuid);
        return type == null ? null : type.createGasStack(1);
    }

    @Nullable
    public synchronized HashedItem getItemType(@Nullable UUID uuid) {
        QIOResourceType type = uuid == null ? null : byUUID.get(uuid);
        return type == null ? null : type.getItemType();
    }

    @Nonnull
    public synchronized ItemStack createItemStack(@Nullable UUID uuid, int size) {
        QIOResourceType type = uuid == null ? null : byUUID.get(uuid);
        return type == null ? ItemStack.EMPTY : type.createItemStack(size);
    }

    @Nullable
    public synchronized FluidStack createFluidStack(@Nullable UUID uuid, int amount) {
        QIOResourceType type = uuid == null ? null : byUUID.get(uuid);
        return type == null ? null : type.createFluidStack(amount);
    }

    @Nullable
    public synchronized GasStack createGasStack(@Nullable UUID uuid, int amount) {
        QIOResourceType type = uuid == null ? null : byUUID.get(uuid);
        return type == null ? null : type.createGasStack(amount);
    }

    @Nonnull
    public synchronized List<QIOResourceType> getTypes() {
        List<QIOResourceType> types = new ArrayList<>(byUUID.values());
        types.sort((left, right) -> left.getUUID().toString().compareTo(right.getUUID().toString()));
        return Collections.unmodifiableList(types);
    }

    public synchronized void markTypeDirty(UUID uuid) {
        if (uuid != null && byUUID.containsKey(uuid)) {
            dirtyTypes.add(uuid);
            indexDirty = true;
        }
    }

    public boolean flush() {
        Map<UUID, QIOResourceType> pending;
        synchronized (this) {
            if (!loaded) {
                return true;
            }
            pending = new HashMap<>();
            for (UUID uuid : dirtyTypes) {
                QIOResourceType type = byUUID.get(uuid);
                if (type != null) {
                    pending.put(uuid, type);
                }
            }
        }
        Set<UUID> saved = new HashSet<>();
        for (Map.Entry<UUID, QIOResourceType> entry : pending.entrySet()) {
            try {
                QIOFileIO.writeAtomic(QIOFileIO.dataFile(resourceDirectory, entry.getKey()), entry.getValue().write());
                saved.add(entry.getKey());
            } catch (IOException e) {
                QIOLog.LOGGER.error("Unable to save QIO resource type {}", entry.getKey(), e);
            }
        }
        synchronized (this) {
            dirtyTypes.removeAll(saved);
            if (indexDirty && dirtyTypes.isEmpty()) {
                if (writeIndex()) {
                    indexDirty = false;
                }
            }
            return dirtyTypes.isEmpty() && !indexDirty;
        }
    }

    public synchronized void reset() {
        clearRuntimeState();
    }

    private UUID addType(QIOResourceType lookupKey, QIOResourceType type) {
        UUID uuid = type.getUUID();
        byUUID.put(uuid, type);
        byType.put(lookupKey, uuid);
        dirtyTypes.add(uuid);
        indexDirty = true;
        return uuid;
    }

    private void loadFiles() throws IOException {
        Map<UUID, QIOResourceType> loadedTypes = new HashMap<>();
        File[] allFiles = resourceDirectory.listFiles(File::isFile);
        if (allFiles != null) {
            for (File file : allFiles) {
                UUID quarantined = QIOFileIO.parseQuarantinedDataFileUUID(file);
                if (quarantined != null) {
                    damagedTypes.add(quarantined);
                }
            }
        }
        for (File file : QIOFileIO.listDataFiles(resourceDirectory)) {
            UUID uuid = QIOFileIO.parseDataFileUUID(file);
            if (uuid == null) {
                QIOLog.LOGGER.warn("Ignoring QIO resource file with an invalid UUID name: {}", file.getName());
                continue;
            }
            try {
                NBTTagCompound data = QIOFileIO.read(file);
                if (data == null) {
                    throw new IOException("File is empty");
                }
                QIOResourceType type = QIOResourceType.read(uuid, data);
                loadedTypes.put(uuid, type);
                if (data.getInteger("version") != QIOResourceType.DATA_VERSION) {
                    dirtyTypes.add(uuid);
                }
                damagedTypes.remove(uuid);
            } catch (Exception e) {
                damagedTypes.add(uuid);
                QIOLog.LOGGER.warn("Skipping damaged QIO resource type file {}", file.getName(), e);
                QIOFileIO.quarantine(file);
            }
        }
        byUUID.putAll(loadedTypes);
        for (Map.Entry<UUID, QIOResourceType> entry : loadedTypes.entrySet()) {
            byType.putIfAbsent(entry.getValue(), entry.getKey());
        }
        indexDirty = !isIndexValid(loadedTypes.keySet());
    }

    private boolean isIndexValid(Set<UUID> actualUUIDs) {
        File index = new File(resourceDirectory.getParentFile(), "resource_type_index.dat");
        try {
            NBTTagCompound data = QIOFileIO.read(index);
            if (data == null || data.getInteger("version") != INDEX_VERSION) {
                return false;
            }
            Set<UUID> indexed = new HashSet<>();
            NBTTagList resources = data.getTagList("resources", NBT.TAG_COMPOUND);
            for (int i = 0; i < resources.tagCount(); i++) {
                String value = resources.getCompoundTagAt(i).getString("uuid");
                indexed.add(UUID.fromString(value));
            }
            return indexed.equals(actualUUIDs);
        } catch (Exception e) {
            QIOLog.LOGGER.warn("QIO resource type index is damaged; rebuilding it", e);
            return false;
        }
    }

    private boolean writeIndex() {
        NBTTagCompound data = new NBTTagCompound();
        data.setInteger("version", INDEX_VERSION);
        NBTTagList resources = new NBTTagList();
        List<UUID> uuids = new ArrayList<>(byUUID.keySet());
        uuids.sort((left, right) -> left.toString().compareTo(right.toString()));
        for (UUID uuid : uuids) {
            QIOResourceType type = byUUID.get(uuid);
            NBTTagCompound entry = new NBTTagCompound();
            entry.setString("uuid", uuid.toString());
            entry.setString("family", type.getFamily());
            entry.setString("codec", type.getCodecId().toString());
            entry.setBoolean("resolved", type.isResolved());
            entry.setString("lastKnownFile", uuid.toString() + ".dat");
            resources.appendTag(entry);
        }
        data.setTag("resources", resources);
        try {
            QIOFileIO.writeAtomic(new File(resourceDirectory.getParentFile(), "resource_type_index.dat"), data);
            return true;
        } catch (IOException e) {
            QIOLog.LOGGER.error("Unable to save QIO resource type index", e);
            return false;
        }
    }

    private void requireLoaded() {
        if (!loaded) {
            throw new IllegalStateException("QIO resource types are not loaded for a world");
        }
    }

    private void clearRuntimeState() {
        byUUID.clear();
        byType.clear();
        dirtyTypes.clear();
        damagedTypes.clear();
        requestedWorldDirectory = null;
        worldDirectory = null;
        resourceDirectory = null;
        indexDirty = false;
        loaded = false;
    }
}
