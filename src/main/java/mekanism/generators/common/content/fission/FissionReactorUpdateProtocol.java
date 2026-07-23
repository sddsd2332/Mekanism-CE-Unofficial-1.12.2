package mekanism.generators.common.content.fission;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import mekanism.api.Coord4D;
import mekanism.api.heat.HeatAPI;
import mekanism.common.multiblock.MultiblockCache;
import mekanism.common.multiblock.MultiblockManager;
import mekanism.common.multiblock.UpdateProtocol;
import mekanism.generators.common.MekanismGenerators;
import mekanism.generators.common.block.states.BlockStateGenerator.GeneratorType;
import mekanism.generators.common.tile.fission.TileEntityControlRodAssembly;
import mekanism.generators.common.tile.fission.TileEntityFissionFuelAssembly;
import mekanism.generators.common.tile.fission.TileEntityFissionReactorCasing;
import mekanism.generators.common.tile.reactor.TileEntityReactorGlass;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nullable;
import java.util.*;

public class FissionReactorUpdateProtocol extends UpdateProtocol<SynchronizedFissionData> {

    public FissionReactorUpdateProtocol(TileEntityFissionReactorCasing tileEntity) {
        super(tileEntity);
    }

    @Override
    protected boolean isValidFrame(int x, int y, int z) {
        GeneratorType type = GeneratorType.get(pointer.getWorld().getBlockState(new BlockPos(x, y, z)));
        return type == GeneratorType.FISSION_REACTOR_CASING || type == GeneratorType.FISSION_REACTOR_PORT || type == GeneratorType.FISSION_REACTOR_LOGIC_ADAPTER;
    }

    @Override
    public boolean isViableNode(int x, int y, int z) {
        TileEntity tile = new Coord4D(x, y, z, pointer.getWorld().provider.getDimension()).getTileEntity(pointer.getWorld());
        // Fission shell should not accept generic Structural Glass; only reactor glass is valid for non-frame walls.
        return tile instanceof TileEntityReactorGlass || MultiblockManager.areEqual(tile, pointer);
    }

    @Override
    protected boolean isValidInnerNode(int x, int y, int z) {
        if (super.isValidInnerNode(x, y, z)) {
            return true;
        }
        TileEntity tile = pointer.getWorld().getTileEntity(new BlockPos(x, y, z));
        return tile instanceof TileEntityFissionFuelAssembly || tile instanceof TileEntityControlRodAssembly;
    }

    @Override
    protected boolean canForm(SynchronizedFissionData structure) {
        Map<AssemblyPos, FuelAssembly> assemblies = new Object2ObjectOpenHashMap<>();
        Set<Coord4D> fuelCoords = new ObjectOpenHashSet<>();

        int fuelAssemblyCount = 0;
        int surfaceArea = 0;

        for (Coord4D coord : innerNodes) {
            TileEntity tile = coord.getTileEntity(pointer.getWorld());
            AssemblyPos pos = new AssemblyPos(coord.x, coord.z);
            FuelAssembly assembly = assemblies.computeIfAbsent(pos, key -> new FuelAssembly());

            if (tile instanceof TileEntityFissionFuelAssembly) {
                assembly.fuelYLevels.add(coord.y);
                structure.internalLocations.add(coord);
                fuelAssemblyCount++;
                surfaceArea += 6;
                for (EnumFacing side : EnumFacing.VALUES) {
                    if (fuelCoords.contains(coord.offset(side))) {
                        surfaceArea -= 2;
                    }
                }
                fuelCoords.add(coord);
            } else if (tile instanceof TileEntityControlRodAssembly) {
                if (assembly.controlRodY != null) {
                    return false;
                }
                assembly.controlRodY = coord.y;
                structure.internalLocations.add(coord);
            }
        }

        if (assemblies.isEmpty()) {
            return false;
        }

        structure.assemblies.clear();
        int dimension = pointer.getWorld().provider.getDimension();
        for (Map.Entry<AssemblyPos, FuelAssembly> entry : assemblies.entrySet()) {
            AssemblyPos pos = entry.getKey();
            FuelAssembly assembly = entry.getValue();
            if (assembly.controlRodY == null || assembly.fuelYLevels.isEmpty()) {
                return false;
            }

            assembly.fuelYLevels.sort(Comparator.naturalOrder());
            int previousY = -1;
            for (int fuelY : assembly.fuelYLevels) {
                if (previousY != -1 && fuelY != previousY + 1) {
                    return false;
                }
                previousY = fuelY;
            }

            if (assembly.controlRodY != previousY + 1) {
                return false;
            }

            Coord4D start = new Coord4D(pos.x, assembly.fuelYLevels.get(0), pos.z, dimension);
            structure.assemblies.add(new SynchronizedFissionData.FormedAssembly(start, assembly.fuelYLevels.size()));
        }

        structure.fuelAssemblies = fuelAssemblyCount;
        structure.surfaceArea = surfaceArea;
        structure.updateCapacities();
        return true;
    }

    @Override
    protected MultiblockCache<SynchronizedFissionData> getNewCache() {
        return new FissionReactorCache();
    }

    @Override
    protected SynchronizedFissionData getNewStructure() {
        return new SynchronizedFissionData();
    }

    @Override
    protected MultiblockManager<SynchronizedFissionData> getManager() {
        return MekanismGenerators.fissionManager;
    }

    @Override
    protected void mergeCaches(List<ItemStack> rejectedItems, MultiblockCache<SynchronizedFissionData> cache, MultiblockCache<SynchronizedFissionData> merge) {
        FissionReactorCache fissionCache = (FissionReactorCache) cache;
        FissionReactorCache mergeCache = (FissionReactorCache) merge;

        double fuelOverflow = getFuelMergeOverflow(fissionCache.fuel, mergeCache.fuel);
        fissionCache.fuel = mergeGasStack(fissionCache.fuel, mergeCache.fuel);
        fissionCache.waste = mergeGasStack(fissionCache.waste, mergeCache.waste);
        fissionCache.gasCoolant = mergeGasStack(fissionCache.gasCoolant, mergeCache.gasCoolant);
        fissionCache.heatedCoolant = mergeGasStack(fissionCache.heatedCoolant, mergeCache.heatedCoolant);
        fissionCache.coolant = mergeFluidStack(fissionCache.coolant, mergeCache.coolant);
        fissionCache.steam = mergeFluidStack(fissionCache.steam, mergeCache.steam);

        double currentRate = HeatAPI.isFinite(fissionCache.rateLimit) ? Math.max(0, fissionCache.rateLimit) : 0;
        double incomingRate = HeatAPI.isFinite(mergeCache.rateLimit) ? Math.max(0, mergeCache.rateLimit) : 0;
        fissionCache.rateLimit = Math.min(HeatAPI.MAX_HEAT, Math.max(currentRate, incomingRate));
        fissionCache.active |= mergeCache.active;
        fissionCache.burnRemaining = HeatAPI.addHeatClamped(fissionCache.burnRemaining, Math.max(0, mergeCache.burnRemaining));
        fissionCache.burnRemaining = HeatAPI.addHeatClamped(fissionCache.burnRemaining, fuelOverflow);
        fissionCache.partialWaste = HeatAPI.addHeatClamped(fissionCache.partialWaste, Math.max(0, mergeCache.partialWaste));
        if (mergeCache.storedHeat >= 0 && HeatAPI.isFinite(mergeCache.storedHeat)) {
            if (fissionCache.storedHeat < 0 || !HeatAPI.isFinite(fissionCache.storedHeat)) {
                fissionCache.storedHeat = mergeCache.storedHeat;
            } else {
                fissionCache.storedHeat = HeatAPI.addHeatClamped(fissionCache.storedHeat, mergeCache.storedHeat);
            }
        }
        if (mergeCache.heatCapacity >= 1 && HeatAPI.isFinite(mergeCache.heatCapacity)) {
            if (fissionCache.heatCapacity < 1 || !HeatAPI.isFinite(fissionCache.heatCapacity)) {
                fissionCache.heatCapacity = mergeCache.heatCapacity;
            } else {
                double mergedCapacity = fissionCache.heatCapacity + mergeCache.heatCapacity;
                fissionCache.heatCapacity = HeatAPI.isFinite(mergedCapacity) ? HeatAPI.sanitizeHeatCapacity(mergedCapacity) : HeatAPI.MAX_HEAT;
            }
        }
        double currentDamage = HeatAPI.isFinite(fissionCache.reactorDamage) ? Math.max(0, fissionCache.reactorDamage) : 0;
        double incomingDamage = HeatAPI.isFinite(mergeCache.reactorDamage) ? Math.max(0, mergeCache.reactorDamage) : 0;
        fissionCache.reactorDamage = SynchronizedFissionData.sanitizeDamage(Math.max(currentDamage, incomingDamage));
        fissionCache.forceDisable |= mergeCache.forceDisable;
    }

    static double getFuelMergeOverflow(@Nullable mekanism.api.gas.GasStack current, @Nullable mekanism.api.gas.GasStack incoming) {
        if (current == null || incoming == null || !current.isGasEqual(incoming)) {
            return 0;
        }
        long total = (long) Math.max(0, current.amount) + Math.max(0, incoming.amount);
        return Math.max(0, total - (long) Integer.MAX_VALUE);
    }

    @Override
    protected void onFormed() {
        super.onFormed();
        structureFound.updateAmbientTemperature(pointer.getWorld());
        structureFound.updateHeatCapacity();
        structureFound.updateCapacities();
        structureFound.sanitizeStoredContents();
        structureFound.syncPrev();
    }

    private static class AssemblyPos {

        private final int x;
        private final int z;

        private AssemblyPos(int x, int z) {
            this.x = x;
            this.z = z;
        }

        @Override
        public int hashCode() {
            int result = x;
            result = 31 * result + z;
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof AssemblyPos other)) {
                return false;
            }
            return other.x == x && other.z == z;
        }
    }

    private static class FuelAssembly {

        private final List<Integer> fuelYLevels = new ArrayList<>();
        private Integer controlRodY;
    }
}
