package mekanism.common.content.boiler;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import mekanism.api.Coord4D;
import mekanism.api.heat.HeatAPI;
import mekanism.common.Mekanism;
import mekanism.common.block.states.BlockStateBasic.BasicBlockType;
import mekanism.common.content.tank.SynchronizedTankData.ValveData;
import mekanism.common.multiblock.MultiblockCache;
import mekanism.common.multiblock.MultiblockManager;
import mekanism.common.multiblock.UpdateProtocol;
import mekanism.common.tile.TileEntityPressureDisperser;
import mekanism.common.tile.TileEntitySuperheatingElement;
import mekanism.common.tile.multiblock.TileEntityBoilerCasing;
import mekanism.common.tile.multiblock.TileEntityBoilerValve;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Set;

public class BoilerUpdateProtocol extends UpdateProtocol<SynchronizedBoilerData> {

    public static final int WATER_PER_TANK = 16000;
    public static final int STEAM_PER_TANK = 160000;

    public BoilerUpdateProtocol(TileEntityBoilerCasing tileEntity) {
        super(tileEntity);
    }

    @Override
    protected boolean isValidFrame(int x, int y, int z) {
        return BasicBlockType.get(pointer.getWorld().getBlockState(new BlockPos(x, y, z))) == BasicBlockType.BOILER_CASING;
    }

    @Override
    protected boolean isValidInnerNode(int x, int y, int z) {
        if (super.isValidInnerNode(x, y, z)) {
            return true;
        }
        TileEntity tile = new Coord4D(x, y, z, pointer.getWorld().provider.getDimension()).getTileEntity(pointer.getWorld());
        return tile instanceof TileEntityPressureDisperser || tile instanceof TileEntitySuperheatingElement;
    }

    @Override
    protected boolean canForm(SynchronizedBoilerData structure) {
        if (structure.volHeight < 3) {
            return false;
        }
        Set<Coord4D> dispersers = new ObjectOpenHashSet<>();
        Set<Coord4D> elements = new ObjectOpenHashSet<>();
        for (Coord4D coord : innerNodes) {
            TileEntity tile = coord.getTileEntity(pointer.getWorld());
            if (tile instanceof TileEntityPressureDisperser) {
                dispersers.add(coord);
            } else if (tile instanceof TileEntitySuperheatingElement) {
                structure.internalLocations.add(coord);
                elements.add(coord);
            }
        }
        //Ensure at least one disperser exists
        if (dispersers.isEmpty()) {
            return false;
        }

        //Find a single disperser contained within this multiblock
        final Coord4D initDisperser = dispersers.iterator().next();

        //Ensure that a full horizontal plane of dispersers exist, surrounding the found disperser
        Coord4D pos = new Coord4D(structure.renderLocation.x, initDisperser.y, structure.renderLocation.z, pointer.getWorld().provider.getDimension());
        for (int x = 1; x < structure.volLength - 1; x++) {
            for (int z = 1; z < structure.volWidth - 1; z++) {
                Coord4D coord4D = pos.translate(x, 0, z);
                TileEntity tile = coord4D.getTileEntity(pointer.getWorld());
                if (!(tile instanceof TileEntityPressureDisperser)) {
                    return false;
                }
                dispersers.remove(coord4D);
            }
        }

        //If there are more dispersers than those on the plane found, the structure is invalid
        if (!dispersers.isEmpty()) {
            return false;
        }

        if (elements.size() > 0) {
            structure.superheatingElements = new NodeCounter(new NodeChecker() {
                @Override
                public boolean isValid(Coord4D coord) {
                    return coord.getTileEntity(pointer.getWorld()) instanceof TileEntitySuperheatingElement;
                }
            }).calculate(elements.iterator().next());
        }

        if (elements.size() > structure.superheatingElements) {
            return false;
        }

        Coord4D initAir = null;
        int totalAir = 0;

        //Find the first available block in the structure for water storage (including casings)
        for (int x = structure.renderLocation.x; x < structure.renderLocation.x + structure.volLength; x++) {
            for (int y = structure.renderLocation.y; y < initDisperser.y; y++) {
                for (int z = structure.renderLocation.z; z < structure.renderLocation.z + structure.volWidth; z++) {
                    if (pointer.getWorld().isAirBlock(new BlockPos(x, y, z)) || isViableNode(x, y, z)) {
                        initAir = new Coord4D(x, y, z, pointer.getWorld().provider.getDimension());
                        totalAir++;
                    }
                }
            }
        }

        //Some air must exist for the structure to be valid
        if (initAir == null) {
            return false;
        }

        //Gradle build requires these fields to be final
        final Coord4D renderLocation = structure.renderLocation;
        final int volLength = structure.volLength;
        final int volWidth = structure.volWidth;
        structure.waterVolume = new NodeCounter(new NodeChecker() {
            @Override
            public final boolean isValid(Coord4D coord) {
                return coord.y >= renderLocation.y - 1 && coord.y < initDisperser.y &&
                        coord.x >= renderLocation.x && coord.x < renderLocation.x + volLength &&
                        coord.z >= renderLocation.z && coord.z < renderLocation.z + volWidth &&
                        (coord.isAirBlock(pointer.getWorld()) || isViableNode(coord.getPos()));
            }
        }).calculate(initAir);

        //Make sure all air blocks are connected
        if (totalAir > structure.waterVolume) {
            return false;
        }

        int steamHeight = (structure.renderLocation.y + structure.volHeight - 2) - initDisperser.y;
        structure.steamVolume = structure.volWidth * structure.volLength * steamHeight;
        structure.upperRenderLocation = new Coord4D(structure.renderLocation.x, initDisperser.y + 1, structure.renderLocation.z, pointer.getWorld().provider.getDimension());
        return true;
    }

    @Override
    protected BoilerCache getNewCache() {
        return new BoilerCache();
    }

    @Override
    protected SynchronizedBoilerData getNewStructure() {
        return new SynchronizedBoilerData();
    }

    @Override
    protected MultiblockManager<SynchronizedBoilerData> getManager() {
        return Mekanism.boilerManager;
    }

    @Override
    protected void mergeCaches(List<ItemStack> rejectedItems, MultiblockCache<SynchronizedBoilerData> cache, MultiblockCache<SynchronizedBoilerData> merge) {
        BoilerCache boilerCache = (BoilerCache) cache;
        BoilerCache mergeCache = (BoilerCache) merge;
        boilerCache.water = mergeFluidStack(boilerCache.water, mergeCache.water);
        boilerCache.steam = mergeFluidStack(boilerCache.steam, mergeCache.steam);
        boilerCache.input = mergeGasStack(boilerCache.input, mergeCache.input);
        boilerCache.output = mergeGasStack(boilerCache.output, mergeCache.output);

        if (mergeCache.storedHeat >= 0 && HeatAPI.isFinite(mergeCache.storedHeat)) {
            if (boilerCache.storedHeat < 0 || !HeatAPI.isFinite(boilerCache.storedHeat)) {
                boilerCache.storedHeat = mergeCache.storedHeat;
            } else {
                boilerCache.storedHeat = HeatAPI.addHeatClamped(boilerCache.storedHeat, mergeCache.storedHeat);
            }
        }
        if (mergeCache.heatCapacity >= 1 && HeatAPI.isFinite(mergeCache.heatCapacity)) {
            if (boilerCache.heatCapacity < 1 || !HeatAPI.isFinite(boilerCache.heatCapacity)) {
                boilerCache.heatCapacity = mergeCache.heatCapacity;
            } else {
                double mergedCapacity = boilerCache.heatCapacity + mergeCache.heatCapacity;
                boilerCache.heatCapacity = HeatAPI.isFinite(mergedCapacity) ? HeatAPI.sanitizeHeatCapacity(mergedCapacity) : HeatAPI.MAX_HEAT;
            }
        }
    }

    @Override
    protected void onFormed() {
        structureFound.updateAmbientTemperature(pointer.getWorld());
        structureFound.updateHeatCapacity();
        structureFound.clampStoredSubstancesToCapacity();
        boolean hot = HeatAPI.isFinite(structureFound.getTemperature()) &&
              structureFound.getTemperature() >= SynchronizedBoilerData.BASE_BOIL_TEMP - 0.01F;
        structureFound.clientHot = hot;
        if (structureFound.inventoryID != null) {
            SynchronizedBoilerData.hotMap.put(structureFound.inventoryID, hot);
        }
        //Publish the hot state before assigning the UUID to internal elements so their blockstate
        //is correct immediately after a hot boiler reforms.
        super.onFormed();
    }

    @Override
    protected void onStructureDestroyed(SynchronizedBoilerData structure) {
        if (structure.inventoryID != null) {
            SynchronizedBoilerData.hotMap.remove(structure.inventoryID);
        }
        super.onStructureDestroyed(structure);
    }

    @Override
    protected void onCacheIdsInvalidated(Set<String> staleIds) {
        staleIds.forEach(SynchronizedBoilerData.hotMap::remove);
    }

    @Override
    protected void onStructureCreated(SynchronizedBoilerData structure, int origX, int origY, int origZ, int xmin, int xmax, int ymin, int ymax, int zmin, int zmax) {
        for (Coord4D obj : structure.locations) {
            if (obj.getTileEntity(pointer.getWorld()) instanceof TileEntityBoilerValve) {
                ValveData data = new ValveData();
                data.location = obj;
                data.side = getSide(obj, origX + xmin, origX + xmax, origY + ymin, origY + ymax, origZ + zmin, origZ + zmax);
                structure.valves.add(data);
            }
        }
    }
}
