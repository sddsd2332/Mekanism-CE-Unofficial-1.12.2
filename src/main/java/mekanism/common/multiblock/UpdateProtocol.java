package mekanism.common.multiblock;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import mekanism.api.Coord4D;
import mekanism.api.gas.GasStack;
import mekanism.common.tile.multiblock.TileEntityMultiblock;
import mekanism.common.util.FluidContainerUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;
import java.util.*;
import java.io.IOException;
import net.minecraft.nbt.NBTTagCompound;
import mekanism.common.multiblock.persistence.ManagedCacheStore;
import mekanism.common.multiblock.persistence.MultiblockPersistenceSession;

public abstract class UpdateProtocol<T extends SynchronizedData<T>> {

    /**
     * The multiblock nodes that have already been iterated over.
     */
    public Set<Coord4D> iteratedNodes = new ObjectOpenHashSet<>();

    public Set<Coord4D> innerNodes = new ObjectOpenHashSet<>();

    /**
     * The structures found, all connected by some nodes to the pointer.
     */
    public T structureFound = null;

    /**
     * The original block the calculation is getting run from.
     */
    public TileEntityMultiblock<T> pointer;

    public UpdateProtocol(TileEntityMultiblock<T> tileEntity) {
        pointer = tileEntity;
    }

    /**
     * Recursively loops through each node connected to the given TileEntity.
     *
     * @param coord - coord to start with
     * @param queue - the queue to add next nodes to to avoid recursion
     */
    public void loopThrough(Coord4D coord, Deque<Coord4D> queue) {
        int origX = coord.x, origY = coord.y, origZ = coord.z;
        if (isCorner(origX, origY, origZ)) {
            int xmin = 0, xmax = 0, ymin = 0, ymax = 0, zmin = 0, zmax = 0;
            if (isViableNode(origX + 1, origY, origZ)) {
                xmax = findViableNode(coord, 1, 0, 0);
            } else {
                xmin = findViableNode(coord, -1, 0, 0);
            }
            if (isViableNode(origX, origY + 1, origZ)) {
                ymax = findViableNode(coord, 0, 1, 0);
            } else {
                ymin = findViableNode(coord, 0, -1, 0);
            }
            if (isViableNode(origX, origY, origZ + 1)) {
                zmax = findViableNode(coord, 0, 0, 1);
            } else {
                zmin = findViableNode(coord, 0, 0, -1);
            }

            Set<Coord4D> locations = new ObjectOpenHashSet<>();
            boolean isValid = true;

            int minX = origX + xmin;
            int maxX = origX + xmax;
            int minY = origY + ymin;
            int maxY = origY + ymax;
            int minZ = origZ + zmin;
            int maxZ = origZ + zmax;
            int length = xmax - xmin + 1;
            int height = ymax - ymin + 1;
            int width = zmax - zmin + 1;
            isValid = length <= 18 && height <= 18 && width <= 18 &&
                  isAreaLoaded(new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ));
            for (int x = xmin; isValid && x <= xmax; x++) {
                int xPos = origX + x;
                for (int y = ymin; y <= ymax; y++) {
                    int yPos = origY + y;
                    for (int z = zmin; z <= zmax; z++) {
                        int zPos = origZ + z;
                        if (x == xmin || x == xmax || y == ymin || y == ymax || z == zmin || z == zmax) {
                            if (!isViableNode(xPos, yPos, zPos) || isFrame(coord.translate(x, y, z), minX, maxX, minY, maxY, minZ, maxZ) && !isValidFrame(xPos, yPos, zPos)) {
                                //If it is not a valid node or if it is supposed to be a frame but is invalid
                                // then we are not valid over all
                                isValid = false;
                                break;
                            } else {
                                locations.add(coord.translate(x, y, z));
                            }
                        } else if (!isValidInnerNode(xPos, yPos, zPos)) {
                            isValid = false;
                            break;
                        } else if (!isAir(xPos, yPos, zPos)) {
                            innerNodes.add(new Coord4D(xPos, yPos, zPos, pointer.getWorld().provider.getDimension()));
                        }
                    }
                    if (!isValid) {
                        break;
                    }
                }
                if (!isValid) {
                    break;
                }
            }

            if (isValid) {
                //Check the boolean values before performing other calculations
                if (length <= 18 && height <= 18 && width <= 18) {
                    T structure = getNewStructure();
                    structure.locations = locations;
                    structure.volLength = length;
                    structure.volHeight = height;
                    structure.volWidth = width;
                    structure.volume = structure.volLength * structure.volHeight * structure.volWidth;
                    structure.renderLocation = coord.translate(0, 1, 0);
                    structure.minLocation = coord.translate(xmin, ymin, zmin);
                    structure.maxLocation = coord.translate(xmax, ymax, zmax);

                    if (structure.volLength >= 3 && structure.volHeight >= 3 && structure.volWidth >= 3) {
                        onStructureCreated(structure, origX, origY, origZ, xmin, xmax, ymin, ymax, zmin, zmax);
                        if (structure.locations.contains(Coord4D.get(pointer)) && isCorrectCorner(coord, minX, minY, minZ)) {
                            if (canForm(structure)) {
                                structureFound = structure;
                                return;
                            }
                        }
                    }
                }
            }
        }

        innerNodes.clear();
        iteratedNodes.add(coord);

        if (iteratedNodes.size() > 2048) {
            return;
        }

        for (EnumFacing side : EnumFacing.VALUES) {
            Coord4D sideCoord = coord.offset(side);
            if (isViableNode(sideCoord.getPos())) {
                if (!iteratedNodes.contains(sideCoord)) {
                    queue.addLast(sideCoord);
                }
            }
        }
    }

    protected boolean canForm(T structure) {
        return true;
    }

    public EnumFacing getSide(Coord4D obj, int xmin, int xmax, int ymin, int ymax, int zmin, int zmax) {
        if (obj.x == xmin) {
            return EnumFacing.WEST;
        } else if (obj.x == xmax) {
            return EnumFacing.EAST;
        } else if (obj.y == ymin) {
            return EnumFacing.DOWN;
        } else if (obj.y == ymax) {
            return EnumFacing.UP;
        } else if (obj.z == zmin) {
            return EnumFacing.NORTH;
        } else if (obj.z == zmax) {
            return EnumFacing.SOUTH;
        }
        return null;
    }

    /**
     * @param x - x coordinate
     * @param y - y coordinate
     * @param z - z coordinate
     * @return Whether or not the block at the specified location is an air block.
     */
    protected boolean isAir(int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        return isLoaded(pos) && pointer.getWorld().isAirBlock(pos);
    }

    protected boolean isLoaded(BlockPos pos) {
        if (pointer.getWorld().isBlockLoaded(pos, false)) return true;
        pointer.requestCacheRetry();
        return false;
    }

    protected boolean isAreaLoaded(BlockPos min, BlockPos max) {
        if (pointer.getWorld().isAreaLoaded(min, max, false)) return true;
        pointer.requestCacheRetry();
        return false;
    }

    protected boolean isValidInnerNode(int x, int y, int z) {
        return isAir(x, y, z);
    }

    /**
     * Helper method for reducing duplicate code in loopThrough.
     *
     * @param orig   Starting position
     * @param xShift Direction x is being changed, 1 is increasing, 0 means not changing, -1 means decreasing. Only one of xShift, yShift, and zShift should not be 0
     *               during any call. A value of 1 also implies that it is a viable node so we start checking at 1 instead of 0.
     * @param yShift Direction y is being changed, 1 is increasing, 0 means not changing, -1 means decreasing. Only one of xShift, yShift, and zShift should not be 0
     *               during any call. A value of 1 also implies that it is a viable node so we start checking at 1 instead of 0.
     * @param zShift Direction z is being changed, 1 is increasing, 0 means not changing, -1 means decreasing. Only one of xShift, yShift, and zShift should not be 0
     *               during any call. A value of 1 also implies that it is a viable node so we start checking at 1 instead of 0.
     * @return x, y, or z depending on which one is not zero.
     */
    private int findViableNode(Coord4D orig, int xShift, int yShift, int zShift) {
        int x = xShift == 1 ? 1 : 0;
        int y = yShift == 1 ? 1 : 0;
        int z = zShift == 1 ? 1 : 0;
        while (Math.abs(x) + Math.abs(y) + Math.abs(z) < 18 &&
              isViableNode(orig.x + x + xShift, orig.y + y + yShift, orig.z + z + zShift)) {
            x += xShift;
            y += yShift;
            z += zShift;
        }
        return x != 0 ? x : y != 0 ? y : z;
    }

    private boolean isCorner(int x, int y, int z) {
        return (!isViableNode(x + 1, y, z) || !isViableNode(x - 1, y, z)) &&
                (!isViableNode(x, y + 1, z) || !isViableNode(x, y - 1, z)) &&
                (!isViableNode(x, y, z + 1) || !isViableNode(x, y, z - 1));
    }

    /**
     * @param x - x coordinate
     * @param y - y coordinate
     * @param z - z coordinate
     * @return Whether or not the block at the specified location is a viable node for a multiblock structure.
     */
    public boolean isViableNode(int x, int y, int z) {
        if (!isLoaded(new BlockPos(x, y, z))) return false;
        TileEntity tile = new Coord4D(x, y, z, pointer.getWorld().provider.getDimension()).getTileEntity(pointer.getWorld());
        if (tile != null && tile.isInvalid()) {
            pointer.requestCacheRetry();
            return false;
        }
        if (tile instanceof IStructuralMultiblock block && block.canInterface(pointer)) {
            return true;
        }
        return MultiblockManager.areEqual(tile, pointer);

    }

    /**
     * @param pos - coordinates
     * @return Whether or not the block at the specified location is a viable node for a multiblock structure.
     */
    public boolean isViableNode(BlockPos pos) {
        return isViableNode(pos.getX(), pos.getY(), pos.getZ());
    }

    /**
     * @param obj  - location to check
     * @param xmin - minimum x value
     * @param ymin - minimum y value
     * @param zmin - minimum z value
     * @return If the block at the specified location is on the minimum of all angles of this multiblock structure, and the one to use for the actual calculation.
     */
    private boolean isCorrectCorner(Coord4D obj, int xmin, int ymin, int zmin) {
        return obj.x == xmin && obj.y == ymin && obj.z == zmin;
    }

    /**
     * @param obj  - location to check
     * @param xmin - minimum x value
     * @param xmax - maximum x value
     * @param ymin - minimum y value
     * @param ymax - maximum y value
     * @param zmin - minimum z value
     * @param zmax - maximum z value
     * @return Whether or not the block at the specified location is considered a frame on the multiblock structure.
     */
    private boolean isFrame(Coord4D obj, int xmin, int xmax, int ymin, int ymax, int zmin, int zmax) {
        boolean xMatches = obj.x == xmin || obj.x == xmax;
        boolean yMatches = obj.y == ymin || obj.y == ymax;
        boolean zMatches = obj.z == zmin || obj.z == zmax;
        return xMatches && yMatches || xMatches && zMatches || yMatches && zMatches;
    }

    /**
     * @param x - x coordinate
     * @param y - y coordinate
     * @param z - z coordinate
     * @return Whether or not the block at the specified location serves as a frame for a multiblock structure.
     */
    protected abstract boolean isValidFrame(int x, int y, int z);

    protected abstract MultiblockCache<T> getNewCache();

    protected abstract T getNewStructure();

    protected abstract MultiblockManager<T> getManager();

    protected abstract void mergeCaches(List<ItemStack> rejectedItems, MultiblockCache<T> cache, MultiblockCache<T> merge);

    @Nullable
    protected GasStack mergeGasStack(@Nullable GasStack current, @Nullable GasStack incoming) {
        if (current == null) {
            return incoming == null ? null : incoming.copy();
        } else if (incoming != null && current.isGasEqual(incoming)) {
            long amount = (long) Math.max(0, current.amount) + Math.max(0, incoming.amount);
            return current.copy().withAmount((int) Math.min(Integer.MAX_VALUE, amount));
        }
        return current;
    }

    @Nullable
    protected FluidStack mergeFluidStack(@Nullable FluidStack current, @Nullable FluidStack incoming) {
        if (current == null) {
            return incoming == null ? null : incoming.copy();
        } else if (incoming != null && current.isFluidEqual(incoming)) {
            long amount = (long) Math.max(0, current.amount) + Math.max(0, incoming.amount);
            return FluidContainerUtils.copyWithAmount(current, (int) Math.min(Integer.MAX_VALUE, amount));
        }
        return current;
    }

    protected void onFormed() {
        structureFound.internalLocations.forEach(coord -> {
            TileEntity tile = coord.getTileEntity(pointer.getWorld());
            if (tile instanceof TileEntityInternalMultiblock block) {
                block.setMultiblock(structureFound.inventoryID);
            }
        });
    }

    protected void onStructureCreated(T structure, int origX, int origY, int origZ, int xmin, int xmax, int ymin, int ymax, int zmin, int zmax) {
    }

    protected void onStructureDestroyed(T structure) {
        getManager().detached(pointer.getWorld(), structure);
        structure.internalLocations.forEach(this::killInnerNode);
    }

    protected void onCacheIdsInvalidated(Set<String> staleIds) {
    }

    private void killInnerNode(Coord4D coord) {
        TileEntity tile = coord.getTileEntity(pointer.getWorld());
        if (tile instanceof TileEntityInternalMultiblock block) {
            block.setMultiblock(null);
        }
    }

    /**
     * Runs the protocol and updates all nodes that make a part of the multiblock.
     */
    public void doUpdate() {
        //A neighbor update may destroy and immediately rebuild a structure after it already ran this
        //tick. Capture the live shared state before any cache is pulled or any tile loses its pointer.
        T previousStructure = pointer.structure;
        String previousInventoryId = previousStructure == null ? null : previousStructure.inventoryID;
        Set<Coord4D> previousLocations = previousStructure == null ? Collections.emptySet() :
              new ObjectOpenHashSet<>(previousStructure.locations);
        pointer.syncCachedDataFromStructure();
        Deque<Coord4D> pathingQueue = new LinkedList<>();
        pathingQueue.add(Coord4D.get(pointer));
        while (pathingQueue.peek() != null) {
            Coord4D next = pathingQueue.removeFirst();
            if (!iteratedNodes.contains(next)) {
                loopThrough(next, pathingQueue);
            }
        }

        if (structureFound != null) {
            for (Coord4D coord : iteratedNodes) {
                if (!structureFound.locations.contains(coord)) {
                    iteratedNodes.forEach(newCoord -> {
                        TileEntity tile = newCoord.getTileEntity(pointer.getWorld());
                        if (tile instanceof TileEntityMultiblock<?> multiblock) {
                            if (multiblock.structure != null) {
                                T source = (T) multiblock.structure;
                                if (!source.destroyed) {
                                    onStructureDestroyed(source);
                                    source.destroyed = true;
                                }
                                multiblock.syncCachedDataFromStructure();
                                multiblock.structure.setFormed(false);
                            }
                            multiblock.structure = null;
                        } else if (tile instanceof IStructuralMultiblock block) {
                            block.setController(null);
                        }
                    });
                    innerNodes.forEach(this::killInnerNode);
                    return;
                }
            }

            formFoundStructure(previousStructure, previousInventoryId, previousLocations);
        } else {
            iteratedNodes.forEach(coord -> {
                TileEntity tile = coord.getTileEntity(pointer.getWorld());
                if (tile instanceof TileEntityMultiblock) {
                    TileEntityMultiblock<T> tileEntity = (TileEntityMultiblock<T>) tile;
                    if (tileEntity.structure != null && !tileEntity.structure.destroyed) {
                        onStructureDestroyed(tileEntity.structure);
                        tileEntity.syncCachedDataFromStructure();
                        tileEntity.structure.destroyed = true;
                        tileEntity.structure.setFormed(false);
                    }
                    tileEntity.structure = null;
                } else if (tile instanceof IStructuralMultiblock block) {
                    block.setController(null);
                }
            });
            innerNodes.forEach(this::killInnerNode);
        }
    }

    /** Shared cache claim/publication path for rectangular structures and the SPS shape scanner. */
    protected void formFoundStructure(T previousStructure, String previousInventoryId, Set<Coord4D> previousLocations) {
        Set<Coord4D> multiblockLocations = new ObjectOpenHashSet<>();
        List<TileEntityMultiblock<T>> casings = new ArrayList<>();
        for (Coord4D obj : structureFound.locations) {
            TileEntity tileEntity = obj.getTileEntity(pointer.getWorld());
            if (tileEntity instanceof TileEntityMultiblock<?> block && block.getManager() == getManager()) {
                TileEntityMultiblock<T> multiblock = (TileEntityMultiblock<T>) block;
                multiblockLocations.add(obj);
                casings.add(multiblock);
            }
        }
        long[] positions = multiblockLocations.stream().mapToLong(coord -> coord.getPos().toLong()).sorted().toArray();
        try {
            MultiblockPersistenceSession session = getManager().persistence(pointer.getWorld());
            if (session == null) { waitForCache(previousStructure); return; }
            Map<ManagedCacheStore.SourceKey, List<TileEntityMultiblock<T>>> references = new LinkedHashMap<>();
            for (TileEntityMultiblock<T> casing : casings) {
                if (casing.cacheFormat < 0) throw new IOException("Unknown or corrupt managed cache reference");
                if (casing.cachedID == null) continue;
                UUID id = UUID.fromString(casing.cachedID);
                ManagedCacheStore.SourceKey source;
                if (casing.cacheFormat == 1) {
                    source = ManagedCacheStore.SourceKey.managed(id);
                    if (session.reserved(source)) { waitForCache(previousStructure); return; }
                    if (session.entry(id) == null) {
                        ManagedCacheStore.Entry recovered = session.resolve(source, positions);
                        if (recovered == null) throw new IOException("Consumed managed reference does not belong to this structure");
                        source = ManagedCacheStore.SourceKey.managed(recovered.id);
                    }
                } else {
                    MultiblockPersistenceSession.LegacyStatus status = session.legacyStatus(id);
                    if (status == MultiblockPersistenceSession.LegacyStatus.LOADING || status == MultiblockPersistenceSession.LegacyStatus.RESERVED) {
                        waitForCache(previousStructure); return;
                    }
                    if (status == MultiblockPersistenceSession.LegacyStatus.FAILED) throw new IOException("Legacy history could not be loaded", session.legacyFailure());
                    if (status == MultiblockPersistenceSession.LegacyStatus.CONFLICT) throw new IOException("Legacy UUID collides with managed authority");
                    if (status == MultiblockPersistenceSession.LegacyStatus.INVALIDATED) {
                        casing.cachedID = null;
                        casing.cachedData = casing.getNewCache();
                        casing.cachedDataTimestamp = Long.MIN_VALUE;
                        casing.markDirty();
                        continue;
                    }
                    if (status == MultiblockPersistenceSession.LegacyStatus.CONSUMED) {
                        ManagedCacheStore.Entry recovered = session.resolve(ManagedCacheStore.SourceKey.legacy(id), positions);
                        if (recovered == null) throw new IOException("Consumed legacy reference does not belong to this structure");
                        source = ManagedCacheStore.SourceKey.managed(recovered.id);
                    } else {
                        getManager().updateCache(casing);
                        source = ManagedCacheStore.SourceKey.legacy(id);
                    }
                }
                references.computeIfAbsent(source, ignored -> new ArrayList<>()).add(casing);
            }
            if (references.isEmpty()) {
                ManagedCacheStore.Entry retained = session.findByBinding(positions);
                if (retained != null) {
                    ManagedCacheStore.SourceKey source = ManagedCacheStore.SourceKey.managed(retained.id);
                    if (session.reserved(source)) { waitForCache(previousStructure); return; }
                    references.put(source, casings);
                }
            }
            boolean legacyReuse = previousStructure != null && !previousStructure.destroyed && previousInventoryId != null &&
                  previousLocations.equals(multiblockLocations) && references.size() == 1 &&
                  references.keySet().iterator().next().equals(ManagedCacheStore.SourceKey.legacy(UUID.fromString(previousInventoryId)));
            if (legacyReuse) {
                MultiblockCache<T> cache = getManager().selectFormationCache(pointer.getWorld(), previousInventoryId, casings, structureFound);
                cache.validateCapacity(structureFound);
                publishFormation(cache, previousInventoryId, false);
                return;
            }

            // Freeze every live source before deriving the submitted snapshot. This also flushes
            // machine-specific destruction state (notably the induction-cell energy queue).
            pauseSourceStructures(previousStructure);
            List<MultiblockCache<T>> sources = new ArrayList<>();
            for (Map.Entry<ManagedCacheStore.SourceKey, List<TileEntityMultiblock<T>>> reference : references.entrySet()) {
                ManagedCacheStore.SourceKey key = reference.getKey();
                T owner = key.legacy ? null : getManager().boundStructure(pointer.getWorld(), key.id.toString());
                if (owner != null) throw new IOException("Managed cache is already bound to another live structure");
                sources.add(key.legacy ? getManager().selectFormationCache(pointer.getWorld(), key.id.toString(), reference.getValue(), structureFound) :
                      getManager().managedCache(pointer.getWorld(), key.id.toString(), pointer));
            }
            MultiblockCache<T> cache = previewMergedCache(sources, structureFound);
            if (references.size() == 1) {
                ManagedCacheStore.SourceKey only = references.keySet().iterator().next();
                if (!only.legacy && session.entry(only.id).matchesBinding(positions)) {
                    if (!getManager().bindManaged(pointer.getWorld(), only.id.toString(), positions, structureFound, cache)) {
                        throw new IOException("Managed cache binding is not available");
                    }
                    publishFormation(cache, only.id.toString(), true);
                    return;
                }
            }
            getManager().beginTransfer(pointer.getWorld(), references.keySet(), positions, cache, casings);
            pointer.requestCacheRetry();
            pointer.reportCacheFormationFailure(null);
        } catch (IOException | IllegalArgumentException error) {
            pointer.reportCacheFormationFailure(error.getMessage());
            pauseSourceStructures(previousStructure);
        }
    }

    /** Detach while the unloading casing and any induction cells are still accessible. */
    public void detachCurrentStructure() {
        if (pointer.structure != null) {
            structureFound = pointer.structure;
            pauseSourceStructures(pointer.structure);
        }
    }

    private void waitForCache(T previousStructure) {
        pauseSourceStructures(previousStructure);
        pointer.reportCacheFormationFailure(null);
        pointer.requestCacheRetry();
    }

    private void publishFormation(MultiblockCache<T> cache, String idToUse, boolean managed) {
        pointer.reportCacheFormationFailure(null);
        cache.apply(structureFound);
        structureFound.inventoryID = idToUse;
        structureFound.setFormed(true);

        onFormed();

        List<IStructuralMultiblock> structures = new ArrayList<>();
        List<TileEntityMultiblock<T>> formedTiles = new ArrayList<>();
        Coord4D toUse = null;

        for (Coord4D obj : structureFound.locations) {
            TileEntity tileEntity = obj.getTileEntity(pointer.getWorld());
            if (tileEntity instanceof TileEntityMultiblock) {
                TileEntityMultiblock<T> multiblock = (TileEntityMultiblock<T>) tileEntity;
                multiblock.structure = structureFound;
                multiblock.cacheFormat = managed ? 1 : 0;
                multiblock.cachedID = idToUse;
                formedTiles.add(multiblock);
                if (toUse == null) {
                    toUse = obj;
                }
            } else if (tileEntity instanceof IStructuralMultiblock block) {
                structures.add(block);
            }
        }

        //Remove all structural multiblocks from locations, set controllers
        for (IStructuralMultiblock node : structures) {
            if (node instanceof mekanism.common.tile.TileEntityStructuralGlass glass) structureFound.bindStructuralGlass(glass, toUse);
            else node.setController(toUse);
            structureFound.locations.remove(Coord4D.get((TileEntity) node));
        }
        //Make the new shared state and ID visible immediately on every loaded replica. This
        //also establishes the canonical manager cache before another neighbor callback can run.
        for (TileEntityMultiblock<T> formedTile : formedTiles) {
            formedTile.syncCachedDataFromStructure();
            formedTile.markDirty();
        }
        TileEntityMultiblock<T> renderer = null;
        for (TileEntityMultiblock<T> formedTile : formedTiles) {
            if (formedTile.isRendering && renderer == null) {
                renderer = formedTile;
            } else {
                formedTile.isRendering = false;
            }
        }
        if (renderer == null && !formedTiles.isEmpty()) {
            renderer = formedTiles.get(0);
            renderer.isRendering = true;
        }
        structureFound.hasRenderer = renderer != null;
        if (renderer != null) {
            renderer.sendStructure = true;
            //prevStructure remains true during an in-place rebuild, so the base tile tick
            //will not automatically resend the new UUID and dimensions.
            pointer.sendPacketToRenderer();
        }
    }

    /** Pure preview used by both the legacy transition and the durable migration adapter. */
    protected MultiblockCache<T> previewMergedCache(Collection<MultiblockCache<T>> sources, T target) throws IOException {
        MultiblockCache<T> result = null;
        List<ItemStack> rejected = new ArrayList<>();
        for (MultiblockCache<T> source : sources) {
            source.validateCapacity(target);
            NBTTagCompound snapshot = new NBTTagCompound();
            source.save(snapshot);
            MultiblockCache<T> detached = getNewCache();
            detached.load(snapshot);
            NBTTagCompound roundTrip = new NBTTagCompound();
            detached.save(roundTrip);
            if (!snapshot.equals(roundTrip)) throw new IOException("Cache snapshot cannot round-trip without changing stored state");
            if (result == null) result = detached;
            else {
                result.validateMerge(detached);
                mergeCaches(rejected, result, detached);
                if (!rejected.isEmpty()) throw new IOException("Merged items were rejected; source inventories are retained");
            }
        }
        if (result == null) result = getNewCache();
        result.validateCapacity(target);
        return result;
    }

    /** Freeze a rejected/reconfiguring source without consuming its inventory or invalidating its ID. */
    protected void pauseSourceStructures(@Nullable T previous) {
        Set<T> sources = Collections.newSetFromMap(new IdentityHashMap<>());
        if (previous != null) sources.add(previous);
        for (Coord4D coord : structureFound.locations) {
            TileEntity tile = coord.getTileEntity(pointer.getWorld());
            if (tile instanceof TileEntityMultiblock<?> multiblock && multiblock.getManager() == getManager() && multiblock.structure != null) {
                sources.add((T) multiblock.structure);
            }
        }
        for (T source : sources) {
            if (!source.destroyed) {
                onStructureDestroyed(source);
                source.destroyed = true;
            }
            source.setFormed(false);
            for (Coord4D coord : source.locations) {
                TileEntity tile = coord.getTileEntity(pointer.getWorld());
                if (tile instanceof TileEntityMultiblock<?> block && block.getManager() == getManager() && block.structure == source) {
                    TileEntityMultiblock<T> casing = (TileEntityMultiblock<T>) block;
                    casing.syncCachedDataFromStructure();
                    getManager().updateCache(casing, true);
                    casing.structure = null;
                    casing.markDirty();
                    casing.requestCacheRetry();
                }
            }
        }
    }

    public abstract static class NodeChecker {

        public abstract boolean isValid(final Coord4D coord);

        public boolean shouldContinue(int iterated) {
            return true;
        }
    }

    public static class NodeCounter {

        public Set<Coord4D> iterated = new ObjectOpenHashSet<>();

        public NodeChecker checker;

        public NodeCounter(NodeChecker c) {
            checker = c;
        }

        public void loop(Coord4D pos) {
            iterated.add(pos);

            if (!checker.shouldContinue(iterated.size())) {
                return;
            }

            for (EnumFacing side : EnumFacing.VALUES) {
                Coord4D coord = pos.offset(side);

                if (!iterated.contains(coord) && checker.isValid(coord)) {
                    loop(coord);
                }
            }
        }

        public int calculate(Coord4D coord) {
            if (!checker.isValid(coord)) {
                return 0;
            }
            loop(coord);
            return iterated.size();
        }
    }
}
