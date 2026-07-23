package mekanism.common.multiblock;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import mekanism.api.Coord4D;
import mekanism.common.tile.multiblock.TileEntityMultiblock;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nonnull;
import java.util.*;

public class MultiblockManager<T extends SynchronizedData<T>> {

    private static final Set<MultiblockManager<?>> MANAGERS = new ObjectOpenHashSet<>();

    public String name;

    /**
     * A map containing references to all multiblock inventory caches.
     */
    public Map<String, MultiblockCache<T>> inventories = new Object2ObjectOpenHashMap<>();

    /** Latest world time represented by each canonical cache. */
    private final Map<String, Long> inventoryTimestamps = new Object2ObjectOpenHashMap<>();

    /**
     * Structure processing claims live outside transient SynchronizedData instances so that an
     * immediate unform/reform cannot process the same inventory twice in one world tick.
     */
    private final Map<Integer, Map<String, Long>> serverTickClaims = new HashMap<>();

    /** Per-dimension persistent tombstones for caches already consumed by a newer structure. */
    private final Map<World, InvalidatedCacheData> invalidatedCacheData = new WeakHashMap<>();

    public MultiblockManager(String s) {
        name = s;
        MANAGERS.add(this);
    }

    public static void tick(World world) {
        MANAGERS.forEach(manager -> manager.tickSelf(world));
    }

    public static String getStructureId(TileEntityMultiblock<?> tile) {
        return tile.structure != null ? tile.getSynchronizedData().inventoryID : null;
    }

    public static boolean areEqual(TileEntity tile1, TileEntity tile2) {
        if (tile1 instanceof TileEntityMultiblock<?> multiblock1 && tile2 instanceof TileEntityMultiblock<?> multiblock2) {
            return multiblock1.getManager() == multiblock2.getManager();
        }
        return false;
    }

    public static void reset() {
        MANAGERS.forEach(manager -> {
            manager.inventories.clear();
            manager.inventoryTimestamps.clear();
            manager.serverTickClaims.clear();
            manager.invalidatedCacheData.clear();
        });
    }

    /** Claims structure-wide processing for a persistent multiblock inventory in a dimension. */
    public boolean tryClaimServerTick(int dimensionId, String inventoryID, long gameTime) {
        Map<String, Long> dimensionClaims = serverTickClaims.computeIfAbsent(dimensionId, ignored -> new HashMap<>());
        Long previousTick = dimensionClaims.put(inventoryID, gameTime);
        return previousTick == null || previousTick != gameTime;
    }

    /** Carries a processing claim across the cache ID replacement performed while reforming. */
    public void inheritServerTickClaim(int dimensionId, Collection<String> staleIds, String replacementId, long gameTime) {
        Map<String, Long> dimensionClaims = serverTickClaims.get(dimensionId);
        if (dimensionClaims == null || replacementId == null) {
            return;
        }
        for (String staleId : staleIds) {
            if (Objects.equals(dimensionClaims.get(staleId), gameTime)) {
                dimensionClaims.put(replacementId, gameTime);
                return;
            }
        }
    }

    /**
     * Grabs an inventory from the world's caches, and removes all the world's references to it.
     *
     * @param world - world the cache is stored in
     * @param id    - inventory ID to pull
     * @return correct multiblock inventory cache
     */
    public MultiblockCache<T> pullInventory(World world, String id) {
        MultiblockCache<T> toReturn = inventories.get(id);
        if (toReturn == null) {
            return null;
        }
        toReturn.locations.forEach(obj -> {
            TileEntity tile = obj.getTileEntity(world);
            if (tile instanceof TileEntityMultiblock<?> multiblock && multiblock.getManager() == this) {
                TileEntityMultiblock<T> tileEntity = (TileEntityMultiblock<T>) multiblock;
                if (Objects.equals(tileEntity.cachedID, id)) {
                    tileEntity.cachedData = tileEntity.getNewCache();
                    tileEntity.cachedID = null;
                    tileEntity.cachedDataTimestamp = Long.MIN_VALUE;
                    tileEntity.markDirty();
                }
            }
        });
        invalidateInventory(world, id);
        return toReturn;
    }

    /** Permanently invalidates an ID so an unloaded stale casing cannot recreate its consumed cache later. */
    public void invalidateInventory(World world, String id) {
        if (id == null) {
            return;
        }
        inventories.remove(id);
        inventoryTimestamps.remove(id);
        if (world != null && !world.isRemote) {
            getInvalidatedCacheData(world).invalidate(id);
        }
    }

    public boolean isInventoryInvalidated(World world, String id) {
        return world != null && !world.isRemote && id != null && getInvalidatedCacheData(world).contains(id);
    }

    private InvalidatedCacheData getInvalidatedCacheData(World world) {
        InvalidatedCacheData data = invalidatedCacheData.get(world);
        if (data == null) {
            String dataName = "mekanism_" + name + "_invalidated_multiblocks";
            data = (InvalidatedCacheData) world.getPerWorldStorage().getOrLoadData(InvalidatedCacheData.class, dataName);
            if (data == null) {
                data = new InvalidatedCacheData(dataName);
                world.getPerWorldStorage().setData(dataName, data);
            }
            invalidatedCacheData.put(world, data);
        }
        return data;
    }

    /**
     * Grabs a unique inventory ID for a multiblock.
     *
     * @return unique inventory ID
     */
    public static String getUniqueInventoryID() {
        return UUID.randomUUID().toString();
    }

    public void tickSelf(World world) {
        ArrayList<String> idsToKill = new ArrayList<>();
        for (Map.Entry<String, MultiblockCache<T>> entry : inventories.entrySet()) {
            String inventoryID = entry.getKey();
            Set<Coord4D> tilesToKill = new ObjectOpenHashSet<>();
            for (Coord4D obj : entry.getValue().locations) {
                if (obj.dimensionId != world.provider.getDimension() || !obj.exists(world)) {
                    continue;
                }
                TileEntity tileEntity = obj.getTileEntity(world);
                if (!(tileEntity instanceof TileEntityMultiblock<?> multiblock) || multiblock.getManager() != this) {
                    tilesToKill.add(obj);
                } else {
                    String structureId = getStructureId(multiblock);
                    String referencedId = structureId == null ? multiblock.cachedID : structureId;
                    if (!Objects.equals(referencedId, inventoryID)) {
                        tilesToKill.add(obj);
                    }
                }
            }
            if (!tilesToKill.isEmpty()) {
                entry.getValue().locations.removeAll(tilesToKill);
            }
            if (entry.getValue().locations.isEmpty()) {
                idsToKill.add(inventoryID);
            }
        }
        idsToKill.forEach(id -> {
            inventories.remove(id);
            inventoryTimestamps.remove(id);
        });

        Map<String, Long> dimensionClaims = serverTickClaims.get(world.provider.getDimension());
        if (dimensionClaims != null) {
            long gameTime = world.getTotalWorldTime();
            //Keep this tick's claims until the following tick so late same-tick callbacks remain guarded.
            dimensionClaims.entrySet().removeIf(entry -> entry.getValue() != gameTime);
            if (dimensionClaims.isEmpty()) {
                serverTickClaims.remove(world.provider.getDimension());
            }
        }

    }

    public void updateCache(TileEntityMultiblock<T> tile) {
        if (tile.cachedID == null) {
            return;
        }
        if (isInventoryInvalidated(tile.getWorld(), tile.cachedID)) {
            tile.cachedData = tile.getNewCache();
            tile.cachedID = null;
            tile.cachedDataTimestamp = Long.MIN_VALUE;
            tile.markDirty();
            return;
        }
        tile.cachedData.locations.add(Coord4D.get(tile));
        MultiblockCache<T> current = inventories.get(tile.cachedID);
        if (current == null) {
            inventories.put(tile.cachedID, tile.cachedData);
            inventoryTimestamps.put(tile.cachedID, tile.cachedDataTimestamp);
        } else {
            long currentTimestamp = inventoryTimestamps.getOrDefault(tile.cachedID, Long.MIN_VALUE);
            if (tile.structure == null && tile.cachedDataTimestamp > currentTimestamp) {
                //During chunk loading prefer the newest persisted replica, independent of load order.
                tile.cachedData.locations.addAll(current.locations);
                inventories.put(tile.cachedID, tile.cachedData);
                inventoryTimestamps.put(tile.cachedID, tile.cachedDataTimestamp);
            } else {
                current.locations.add(Coord4D.get(tile));
                if (tile.structure != null && Objects.equals(tile.cachedID, tile.structure.inventoryID)) {
                    //Keep the canonical runtime cache at the latest immediately visible shared state.
                    current.sync(tile.structure);
                    inventoryTimestamps.put(tile.cachedID, Math.max(currentTimestamp, tile.cachedDataTimestamp));
                }
            }
        }
    }

    public static class InvalidatedCacheData extends WorldSavedData {

        private static final String INVALIDATED_IDS = "invalidatedIDs";

        private final Set<String> invalidatedIds = new ObjectOpenHashSet<>();

        public InvalidatedCacheData(String name) {
            super(name);
        }

        public boolean contains(String id) {
            return invalidatedIds.contains(id);
        }

        public void invalidate(String id) {
            if (id != null && invalidatedIds.add(id)) {
                markDirty();
            }
        }

        @Override
        public void readFromNBT(@Nonnull NBTTagCompound nbt) {
            invalidatedIds.clear();
            NBTTagList ids = nbt.getTagList(INVALIDATED_IDS, NBT.TAG_STRING);
            for (int i = 0; i < ids.tagCount(); i++) {
                String id = ids.getStringTagAt(i);
                if (!id.isEmpty()) {
                    invalidatedIds.add(id);
                }
            }
        }

        @Nonnull
        @Override
        public NBTTagCompound writeToNBT(@Nonnull NBTTagCompound nbt) {
            NBTTagList ids = new NBTTagList();
            invalidatedIds.forEach(id -> ids.appendTag(new NBTTagString(id)));
            nbt.setTag(INVALIDATED_IDS, ids);
            return nbt;
        }
    }
}
