package mekanism.common.content.sps;

import mekanism.api.Coord4D;
import mekanism.common.Mekanism;
import mekanism.common.block.states.BlockStateBasic.BasicBlockType;
import mekanism.common.multiblock.IStructuralMultiblock;
import mekanism.common.multiblock.MultiblockCache;
import mekanism.common.multiblock.MultiblockManager;
import mekanism.common.multiblock.UpdateProtocol;
import mekanism.common.tile.TileEntitySuperchargedCoil;
import mekanism.common.tile.multiblock.TileEntityMultiblock;
import mekanism.common.tile.multiblock.TileEntitySPSCasing;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import java.util.*;

public class SPSUpdateProtocol extends UpdateProtocol<SynchronizedSPSData> {

    private static final int STRUCTURE_SIZE = 7;
    private static final byte[][] ALLOWED_GRID = new byte[][]{
          {0, 0, 1, 1, 1, 0, 0},
          {0, 1, 2, 2, 2, 1, 0},
          {1, 2, 2, 2, 2, 2, 1},
          {1, 2, 2, 2, 2, 2, 1},
          {1, 2, 2, 2, 2, 2, 1},
          {0, 1, 2, 2, 2, 1, 0},
          {0, 0, 1, 1, 1, 0, 0}
    };

    public SPSUpdateProtocol(TileEntitySPSCasing tileEntity) {
        super(tileEntity);
    }

    @Override
    protected boolean isValidFrame(int x, int y, int z) {
        BasicBlockType type = BasicBlockType.get(pointer.getWorld().getBlockState(new net.minecraft.util.math.BlockPos(x, y, z)));
        return type == BasicBlockType.SPS_CASING || type == BasicBlockType.SPS_PORT || type == BasicBlockType.STRUCTURAL_GLASS;
    }

    @Override
    protected boolean isValidInnerNode(int x, int y, int z) {
        if (super.isValidInnerNode(x, y, z)) {
            return true;
        }
        TileEntity tile = new Coord4D(x, y, z, pointer.getWorld().provider.getDimension()).getTileEntity(pointer.getWorld());
        return tile instanceof TileEntitySuperchargedCoil;
    }

    @Override
    public void doUpdate() {
        SynchronizedSPSData found = findStructure();
        if (found != null) {
            this.structureFound = found;
            form(found);
        } else {
            destroyCurrentStructure();
        }
    }

    private void form(SynchronizedSPSData found) {
        //Clear old tiles that are no longer part of the newly formed structure
        if (pointer.structure != null) {
            for (Coord4D oldCoord : pointer.structure.locations) {
                if (!found.locations.contains(oldCoord)) {
                    TileEntity oldTile = oldCoord.getTileEntity(pointer.getWorld());
                    if (oldTile instanceof TileEntityMultiblock<?> multiblock && multiblock.getManager() == getManager()) {
                        if (multiblock.structure != null) {
                            multiblock.structure.setFormed(false);
                        }
                        multiblock.structure = null;
                    } else if (oldTile instanceof IStructuralMultiblock structural) {
                        structural.setController(null);
                    }
                }
            }
        }

        List<String> idsFound = new ArrayList<>();
        found.locations.forEach(obj -> {
            TileEntity tileEntity = obj.getTileEntity(pointer.getWorld());
            if (tileEntity instanceof TileEntityMultiblock<?> block && block.cachedID != null) {
                idsFound.add(block.cachedID);
            }
        });
        MultiblockCache<SynchronizedSPSData> cache = null;
        String idToUse = null;
        if (idsFound.isEmpty()) {
            cache = getNewCache();
            idToUse = MultiblockManager.getUniqueInventoryID();
        } else {
            List<ItemStack> rejectedItems = new ArrayList<>();
            Set<String> checkedIds = new HashSet<>();
            for (String id : idsFound) {
                if (!checkedIds.add(id)) {
                    continue;
                }
                if (getManager().inventories.get(id) != null) {
                    if (cache == null) {
                        cache = getManager().pullInventory(pointer.getWorld(), id);
                    } else {
                        mergeCaches(rejectedItems, cache, getManager().pullInventory(pointer.getWorld(), id));
                    }
                    idToUse = id;
                }
            }
        }
        if (cache == null) {
            if (!idsFound.isEmpty()) {
                String fallbackId = idsFound.get(0);
                for (Coord4D obj : found.locations) {
                    TileEntity tileEntity = obj.getTileEntity(pointer.getWorld());
                    if (tileEntity instanceof TileEntityMultiblock<?> block && Objects.equals(block.cachedID, fallbackId)) {
                        cache = (MultiblockCache<SynchronizedSPSData>) block.cachedData;
                        break;
                    }
                }
                idToUse = fallbackId;
            }
            if (cache == null) {
                cache = getNewCache();
                if (idToUse == null) {
                    idToUse = MultiblockManager.getUniqueInventoryID();
                }
            }
        }
        cache.apply(found);
        found.inventoryID = idToUse;
        found.setFormed(true);
        onFormed();

        List<IStructuralMultiblock> structures = new ArrayList<>();
        Coord4D toUse = null;
        for (Coord4D obj : found.locations) {
            TileEntity tileEntity = obj.getTileEntity(pointer.getWorld());
            if (tileEntity instanceof TileEntityMultiblock) {
                ((TileEntityMultiblock<SynchronizedSPSData>) tileEntity).structure = found;
                if (toUse == null) {
                    toUse = obj;
                }
            } else if (tileEntity instanceof IStructuralMultiblock structural) {
                structures.add(structural);
            }
        }
        for (IStructuralMultiblock structural : structures) {
            structural.setController(toUse);
        }
    }

    private void destroyCurrentStructure() {
        SynchronizedSPSData current = pointer.structure;
        if (current == null) {
            return;
        }
        if (!current.destroyed) {
            onStructureDestroyed(current);
            current.destroyed = true;
            current.setFormed(false);
        }
        for (Coord4D coord : current.locations) {
            TileEntity tile = coord.getTileEntity(pointer.getWorld());
            if (tile instanceof TileEntityMultiblock<?> multiblock && multiblock.getManager() == getManager()) {
                if (multiblock.structure != null) {
                    multiblock.structure.setFormed(false);
                }
                multiblock.structure = null;
            } else if (tile instanceof IStructuralMultiblock structural) {
                structural.setController(null);
            }
        }
    }

    private SynchronizedSPSData findStructure() {
        Coord4D pointerPos = Coord4D.get(pointer);
        int minSearchX = pointerPos.x - (STRUCTURE_SIZE - 1);
        int minSearchY = pointerPos.y - (STRUCTURE_SIZE - 1);
        int minSearchZ = pointerPos.z - (STRUCTURE_SIZE - 1);
        for (int minX = minSearchX; minX <= pointerPos.x; minX++) {
            int maxX = minX + STRUCTURE_SIZE - 1;
            for (int minY = minSearchY; minY <= pointerPos.y; minY++) {
                int maxY = minY + STRUCTURE_SIZE - 1;
                for (int minZ = minSearchZ; minZ <= pointerPos.z; minZ++) {
                    int maxZ = minZ + STRUCTURE_SIZE - 1;
                    if (!isBoundary(pointerPos.x, pointerPos.y, pointerPos.z, minX, maxX, minY, maxY, minZ, maxZ)) {
                        continue;
                    }
                    SynchronizedSPSData candidate = validateCandidate(minX, maxX, minY, maxY, minZ, maxZ);
                    if (candidate != null && candidate.locations.contains(pointerPos)) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    private SynchronizedSPSData validateCandidate(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        int dim = pointer.getWorld().provider.getDimension();
        SynchronizedSPSData structure = getNewStructure();
        structure.volLength = STRUCTURE_SIZE;
        structure.volWidth = STRUCTURE_SIZE;
        structure.volHeight = STRUCTURE_SIZE;
        structure.volume = STRUCTURE_SIZE * STRUCTURE_SIZE * STRUCTURE_SIZE;
        structure.minLocation = new Coord4D(minX, minY, minZ, dim);
        structure.maxLocation = new Coord4D(maxX, maxY, maxZ, dim);
        structure.renderLocation = new Coord4D(minX, minY + 1, minZ, dim);

        Set<Coord4D> innerCoils = new HashSet<>();
        Map<Coord4D, EnumFacing> ports = new HashMap<>();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Coord4D pos = new Coord4D(x, y, z, dim);
                    if (isBoundary(x, y, z, minX, maxX, minY, maxY, minZ, maxZ)) {
                        byte requirement = getRequirement(x, y, z, minX, maxX, minY, maxY, minZ, maxZ);
                        BasicBlockType type = BasicBlockType.get(pointer.getWorld().getBlockState(new BlockPos(x, y, z)));
                        boolean isCasing = type == BasicBlockType.SPS_CASING;
                        boolean isPort = type == BasicBlockType.SPS_PORT;
                        boolean isStructuralGlass = type == BasicBlockType.STRUCTURAL_GLASS;
                        if (requirement == 1) {
                            if (!isCasing) {
                                return null;
                            }
                            structure.locations.add(pos);
                        } else if (requirement == 2) {
                            if (!isCasing && !isPort && !isStructuralGlass) {
                                return null;
                            }
                            structure.locations.add(pos);
                        } else if (isCasing || isPort) {
                            //Ignored position: allow blocks but include them if they are SPS structure blocks.
                            structure.locations.add(pos);
                        }
                        if (isPort) {
                            ports.put(pos, getBoundarySide(x, y, z, minX, maxX, minY, maxY, minZ, maxZ));
                        }
                    } else {
                        TileEntity tile = pos.getTileEntity(pointer.getWorld());
                        if (tile instanceof TileEntitySuperchargedCoil) {
                            innerCoils.add(pos);
                            structure.internalLocations.add(pos);
                        } else if (!pointer.getWorld().isAirBlock(pos.getPos())) {
                            return null;
                        }
                    }
                }
            }
        }

        structure.portToCoilMap.clear();
        Set<Coord4D> validCoils = new HashSet<>();
        for (Map.Entry<Coord4D, EnumFacing> entry : ports.entrySet()) {
            Coord4D inner = entry.getKey().offset(entry.getValue().getOpposite());
            if (innerCoils.contains(inner)) {
                structure.portToCoilMap.put(entry.getKey(), inner);
                validCoils.add(inner);
            }
        }
        for (Coord4D coilPos : innerCoils) {
            if (!validCoils.contains(coilPos)) {
                return null;
            }
        }
        return structure;
    }

    private static boolean isBoundary(int x, int y, int z, int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        return x == minX || x == maxX || y == minY || y == maxY || z == minZ || z == maxZ;
    }

    private static EnumFacing getBoundarySide(int x, int y, int z, int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        if (x == minX) {
            return EnumFacing.WEST;
        } else if (x == maxX) {
            return EnumFacing.EAST;
        } else if (y == minY) {
            return EnumFacing.DOWN;
        } else if (y == maxY) {
            return EnumFacing.UP;
        } else if (z == minZ) {
            return EnumFacing.NORTH;
        }
        return EnumFacing.SOUTH;
    }

    private static byte getRequirement(int x, int y, int z, int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        if (x == minX || x == maxX) {
            return ALLOWED_GRID[y - minY][z - minZ];
        } else if (y == minY || y == maxY) {
            return ALLOWED_GRID[x - minX][z - minZ];
        }
        return ALLOWED_GRID[x - minX][y - minY];
    }

    @Override
    protected MultiblockManager<SynchronizedSPSData> getManager() {
        return Mekanism.spsManager;
    }

    @Override
    protected MultiblockCache<SynchronizedSPSData> getNewCache() {
        return new SPSCache();
    }

    @Override
    protected SynchronizedSPSData getNewStructure() {
        return new SynchronizedSPSData();
    }

    @Override
    protected void mergeCaches(List<ItemStack> rejectedItems, MultiblockCache<SynchronizedSPSData> cache, MultiblockCache<SynchronizedSPSData> merge) {
        SPSCache spsCache = (SPSCache) cache;
        SPSCache mergeCache = (SPSCache) merge;
        spsCache.inputGas = mergeGasStack(spsCache.inputGas, mergeCache.inputGas);
        spsCache.outputGas = mergeGasStack(spsCache.outputGas, mergeCache.outputGas);
        spsCache.progress += mergeCache.progress;
        spsCache.inputProcessed += mergeCache.inputProcessed;
        spsCache.receivedEnergy += mergeCache.receivedEnergy;
        spsCache.lastReceivedEnergy = Math.max(spsCache.lastReceivedEnergy, mergeCache.lastReceivedEnergy);
        spsCache.lastProcessed = Math.max(spsCache.lastProcessed, mergeCache.lastProcessed);
        spsCache.couldOperate |= mergeCache.couldOperate;
    }

    @Override
    protected void onFormed() {
        super.onFormed();
        structureFound.clampTanksToCapacity();
        structureFound.sanitizeStoredGases();
    }
}
