package mekanism.common.content.tank;

import mekanism.api.Coord4D;
import mekanism.common.Mekanism;
import mekanism.common.MekanismBlocks;
import mekanism.common.block.BlockBasic;
import mekanism.common.block.states.BlockStateBasic.BasicBlockType;
import mekanism.common.config.MekanismConfig;
import mekanism.common.content.tank.SynchronizedTankData.ValveData;
import mekanism.common.multiblock.MultiblockCache;
import mekanism.common.multiblock.MultiblockManager;
import mekanism.common.multiblock.UpdateProtocol;
import mekanism.common.tile.multiblock.TileEntityDynamicTank;
import mekanism.common.tile.multiblock.TileEntityDynamicValve;
import mekanism.common.util.StackUtils;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

import java.util.List;

public class TankUpdateProtocol extends UpdateProtocol<SynchronizedTankData> {

    public static final int FLUID_PER_TANK = MekanismConfig.current().general.dynamicTankFluidPerTank.val();


    public TankUpdateProtocol(TileEntityDynamicTank tileEntity) {
        super(tileEntity);
    }

    @Override
    protected boolean isValidFrame(int x, int y, int z) {
        IBlockState state = pointer.getWorld().getBlockState(new BlockPos(x, y, z));
        return state.getBlock() == MekanismBlocks.BasicBlock && state.getValue(((BlockBasic) state.getBlock()).getTypeProperty()) == BasicBlockType.DYNAMIC_TANK;
    }

    @Override
    protected TankCache getNewCache() {
        return new TankCache();
    }

    @Override
    protected SynchronizedTankData getNewStructure() {
        return new SynchronizedTankData((TileEntityDynamicTank) pointer);
    }

    @Override
    protected MultiblockManager<SynchronizedTankData> getManager() {
        return Mekanism.tankManager;
    }

    @Override
    protected void mergeCaches(List<ItemStack> rejectedItems, MultiblockCache<SynchronizedTankData> cache, MultiblockCache<SynchronizedTankData> merge) {
        TankCache tankCache = (TankCache) cache;
        TankCache mergeCache = (TankCache) merge;
        // Dynamic tank is single-medium: keep one type and only merge matching stacks.
        if (tankCache.fluid != null && tankCache.gas != null) {
            if (tankCache.fluid.amount >= tankCache.gas.amount) {
                tankCache.gas = null;
            } else {
                tankCache.fluid = null;
            }
        }
        if (tankCache.fluid != null) {
            tankCache.fluid = mergeFluidStack(tankCache.fluid, mergeCache.fluid);
        } else if (tankCache.gas != null) {
            tankCache.gas = mergeGasStack(tankCache.gas, mergeCache.gas);
        } else if (mergeCache.fluid != null && mergeCache.gas != null) {
            if (mergeCache.fluid.amount >= mergeCache.gas.amount) {
                tankCache.fluid = mergeCache.fluid.copy();
            } else {
                tankCache.gas = mergeCache.gas.copy();
            }
        } else if (mergeCache.fluid != null) {
            tankCache.fluid = mergeCache.fluid.copy();
        } else if (mergeCache.gas != null) {
            tankCache.gas = mergeCache.gas.copy();
        }
        tankCache.editMode = mergeCache.editMode;
        List<ItemStack> rejects = StackUtils.getMergeRejects(tankCache.getInventorySlots(null), mergeCache.getInventorySlots(null));
        if (!rejects.isEmpty()) {
            rejectedItems.addAll(rejects);
        }
        StackUtils.merge(tankCache.getInventorySlots(null), mergeCache.getInventorySlots(null));
    }

    @Override
    protected void onFormed() {
        super.onFormed();
        structureFound.clampStoredSubstancesToCapacity();
    }

    @Override
    protected void onStructureCreated(SynchronizedTankData structure, int origX, int origY, int origZ, int xmin, int xmax, int ymin, int ymax, int zmin, int zmax) {
        for (Coord4D obj : structure.locations) {
            if (obj.getTileEntity(pointer.getWorld()) instanceof TileEntityDynamicValve) {
                ValveData data = new ValveData();
                data.location = obj;
                data.side = getSide(obj, origX + xmin, origX + xmax, origY + ymin, origY + ymax, origZ + zmin, origZ + zmax);
                structure.valves.add(data);
            }
        }
    }
}
