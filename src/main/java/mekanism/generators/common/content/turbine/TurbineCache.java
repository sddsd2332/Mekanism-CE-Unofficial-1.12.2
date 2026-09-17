package mekanism.generators.common.content.turbine;

import mekanism.common.multiblock.MultiblockCache;
import mekanism.common.tile.TileEntityGasTank.GasMode;
import mekanism.common.util.MekanismUtils;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidRegistry;

public class TurbineCache extends MultiblockCache<SynchronizedTurbineData> {

    @Override
    public void validateMerge(MultiblockCache<SynchronizedTurbineData> incoming) throws java.io.IOException {
        super.validateMerge(incoming);
        TurbineCache other = (TurbineCache) incoming;
        validateFluidMerge(fluid, other.fluid);
        validateSum(electricity, other.electricity, Double.MAX_VALUE, "turbine energy");
        validateSum(ventWater, other.ventWater, Integer.MAX_VALUE, "turbine vent water");
        validateSum(lastSteamInput, other.lastSteamInput, Integer.MAX_VALUE, "turbine previous steam input");
        validateSum(newSteamInput, other.newSteamInput, Integer.MAX_VALUE, "turbine current steam input");
    }

    @Override
    public void validateCapacity(SynchronizedTurbineData target) throws java.io.IOException {
        super.validateCapacity(target);
        requireMerge(fluid == null || fluid.getFluid() == FluidRegistry.getFluid("steam"), "Invalid turbine steam; source inventory is retained");
        if (fluid != null) validateAmount(fluid.amount, target.getFluidCapacity(), "steam");
        requireMerge(Double.isFinite(electricity) && electricity >= 0 && electricity <= target.getEnergyCapacity(), "Stored turbine energy exceeds target capacity");
        validateAmount(ventWater, target.getVentWaterCapacity(), "turbine vent water");
        validateAmount(lastSteamInput, Integer.MAX_VALUE, "turbine previous steam input");
        validateAmount(newSteamInput, Integer.MAX_VALUE, "turbine current steam input");
    }

    public FluidStack fluid;
    public double electricity;
    public int ventWater;
    public int lastSteamInput;
    public int newSteamInput;
    public GasMode dumpMode = GasMode.IDLE;

    @Override
    public void apply(SynchronizedTurbineData data) {
        data.fluidStored = fluid == null ? null : fluid.copy();
        data.electricityStored = electricity;
        data.flowRemaining = ventWater;
        data.lastSteamInput = lastSteamInput;
        data.newSteamInput = newSteamInput;
        data.dumpMode = dumpMode;
    }

    @Override
    public void sync(SynchronizedTurbineData data) {
        fluid = syncFluidStack(fluid, data.fluidStored);
        electricity = data.electricityStored;
        ventWater = data.flowRemaining;
        lastSteamInput = data.lastSteamInput;
        newSteamInput = data.newSteamInput;
        dumpMode = data.dumpMode;
    }

    @Override
    public void load(NBTTagCompound nbtTags) {
        fluid = nbtTags.hasKey("cachedFluid") ? FluidStack.loadFluidStackFromNBT(nbtTags.getCompoundTag("cachedFluid")) : null;
        electricity = nbtTags.getDouble("electricity");
        // Old casing snapshots have no such fields. Their absent values naturally read as zero.
        ventWater = nbtTags.getInteger("ventWater");
        lastSteamInput = nbtTags.getInteger("lastSteamInput");
        newSteamInput = nbtTags.getInteger("newSteamInput");
        dumpMode = MekanismUtils.getByIndex(GasMode.values(), nbtTags.getInteger("dumpMode"), GasMode.IDLE);
    }

    @Override
    public void save(NBTTagCompound nbtTags) {
        if (fluid != null) {
            nbtTags.setTag("cachedFluid", fluid.writeToNBT(new NBTTagCompound()));
        }
        nbtTags.setDouble("electricity", electricity);
        nbtTags.setInteger("ventWater", ventWater);
        nbtTags.setInteger("lastSteamInput", lastSteamInput);
        nbtTags.setInteger("newSteamInput", newSteamInput);
        nbtTags.setInteger("dumpMode", dumpMode.ordinal());
    }
}
