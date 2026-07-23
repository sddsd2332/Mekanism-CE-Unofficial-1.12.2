package mekanism.common.content.boiler;

import mekanism.api.NBTConstants;
import mekanism.api.gas.GasStack;
import mekanism.api.heat.HeatAPI;
import mekanism.common.multiblock.MultiblockCache;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

public class BoilerCache extends MultiblockCache<SynchronizedBoilerData> {

    public FluidStack water;
    public FluidStack steam;
    public GasStack input;
    public GasStack output;
    public double storedHeat = -1;
    public double heatCapacity;

    @Override
    public void apply(SynchronizedBoilerData data) {
        data.waterStored = water == null ? null : water.copy();
        data.steamStored = steam == null ? null : steam.copy();
        data.InputGas = input == null ? null : input.copy();
        data.OutputGas = output == null ? null : output.copy();
        if (storedHeat >= 0 && HeatAPI.isFinite(storedHeat) && heatCapacity >= 1 && HeatAPI.isFinite(heatCapacity)) {
            data.getHeatCapacitor().setHeatCapacity(heatCapacity, false);
            data.getHeatCapacitor().setHeat(storedHeat);
        }
    }

    @Override
    public void sync(SynchronizedBoilerData data) {
        water = data.waterStored == null ? null : data.waterStored.copy();
        steam = data.steamStored == null ? null : data.steamStored.copy();
        input = data.InputGas == null ? null : data.InputGas.copy();
        output = data.OutputGas == null ? null : data.OutputGas.copy();
        storedHeat = data.getHeatCapacitor().getHeat();
        heatCapacity = data.getHeatCapacitor().getHeatCapacity();
    }

    @Override
    public void load(NBTTagCompound nbtTags) {
        if (nbtTags.hasKey("cachedWater")) {
            water = FluidStack.loadFluidStackFromNBT(nbtTags.getCompoundTag("cachedWater"));
        }
        if (nbtTags.hasKey("cachedSteam")) {
            steam = FluidStack.loadFluidStackFromNBT(nbtTags.getCompoundTag("cachedSteam"));
        }
        if (nbtTags.hasKey("cachedInputGas")){
            input = GasStack.readFromNBT(nbtTags.getCompoundTag("cachedInputGas"));
        }
        if (nbtTags.hasKey("cachedOutputGas")){
            output = GasStack.readFromNBT(nbtTags.getCompoundTag("cachedOutputGas"));
        }
        if (nbtTags.hasKey(NBTConstants.HEAT_STORED) && nbtTags.hasKey(NBTConstants.HEAT_CAPACITY)) {
            double loadedHeat = nbtTags.getDouble(NBTConstants.HEAT_STORED);
            double loadedCapacity = nbtTags.getDouble(NBTConstants.HEAT_CAPACITY);
            if (loadedHeat >= 0 && HeatAPI.isFinite(loadedHeat) && loadedCapacity >= 1 && HeatAPI.isFinite(loadedCapacity)) {
                storedHeat = HeatAPI.sanitizeHeat(loadedHeat, 0);
                heatCapacity = HeatAPI.sanitizeHeatCapacity(loadedCapacity);
            }
        }
    }

    @Override
    public void save(NBTTagCompound nbtTags) {
        if (water != null) {
            nbtTags.setTag("cachedWater", water.writeToNBT(new NBTTagCompound()));
        }
        if (steam != null) {
            nbtTags.setTag("cachedSteam", steam.writeToNBT(new NBTTagCompound()));
        }
        if (input != null){
            nbtTags.setTag("cachedInputGas",input.write(new NBTTagCompound()));
        }
        if (output != null){
            nbtTags.setTag("cachedOutputGas",output.write(new NBTTagCompound()));
        }
        if (storedHeat >= 0 && HeatAPI.isFinite(storedHeat) && heatCapacity >= 1 && HeatAPI.isFinite(heatCapacity)) {
            nbtTags.setDouble(NBTConstants.HEAT_STORED, storedHeat);
            nbtTags.setDouble(NBTConstants.HEAT_CAPACITY, heatCapacity);
        }
    }
}
