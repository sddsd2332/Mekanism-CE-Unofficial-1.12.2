package mekanism.common.content.boiler;

import mekanism.api.gas.GasStack;
import mekanism.common.multiblock.MultiblockCache;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

public class BoilerCache extends MultiblockCache<SynchronizedBoilerData> {

    public FluidStack water;
    public FluidStack steam;
    public GasStack input;
    public GasStack output;
    public double temperature;

    @Override
    public void apply(SynchronizedBoilerData data) {
        data.waterStored = water == null ? null : water.copy();
        data.steamStored = steam == null ? null : steam.copy();
        data.InputGas = input == null ? null : input.copy();
        data.OutputGas = output == null ? null : output.copy();
        data.temperature = temperature;
    }

    @Override
    public void sync(SynchronizedBoilerData data) {
        water = data.waterStored == null ? null : data.waterStored.copy();
        steam = data.steamStored == null ? null : data.steamStored.copy();
        input = data.InputGas == null ? null : data.InputGas.copy();
        output = data.OutputGas == null ? null : data.OutputGas.copy();
        temperature = data.temperature;
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
        temperature = nbtTags.getDouble("temperature");
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
        nbtTags.setDouble("temperature", temperature);
    }
}
