package mekanism.common.content.boiler;

import mekanism.api.NBTConstants;
import mekanism.api.gas.GasStack;
import mekanism.api.heat.HeatAPI;
import mekanism.common.multiblock.MultiblockCache;
import mekanism.common.MekanismFluids;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidRegistry;

public class BoilerCache extends MultiblockCache<SynchronizedBoilerData> {

    @Override
    public void validateMerge(MultiblockCache<SynchronizedBoilerData> incoming) throws java.io.IOException {
        super.validateMerge(incoming);
        BoilerCache other = (BoilerCache) incoming;
        validateFluidMerge(water, other.water);
        validateFluidMerge(steam, other.steam);
        validateGasMerge(input, other.input);
        validateGasMerge(output, other.output);
        validateSum(Math.max(0, storedHeat), Math.max(0, other.storedHeat), HeatAPI.MAX_HEAT, "heat");
        validateSum(heatCapacity, other.heatCapacity, HeatAPI.MAX_HEAT, "heat capacity");
    }

    @Override
    public void validateCapacity(SynchronizedBoilerData target) throws java.io.IOException {
        super.validateCapacity(target);
        requireMerge(water == null || water.getFluid() == FluidRegistry.WATER, "Invalid boiler water; source inventory is retained");
        requireMerge(steam == null || steam.getFluid() == FluidRegistry.getFluid("steam"), "Invalid boiler steam; source inventory is retained");
        requireMerge(input == null || input.getGas() == MekanismFluids.SuperheatedSodium, "Invalid boiler input gas; source inventory is retained");
        requireMerge(output == null || output.getGas() == MekanismFluids.Sodium, "Invalid boiler output gas; source inventory is retained");
        if (water != null) validateAmount(water.amount, target.getWaterCapacity(), "water");
        if (steam != null) validateAmount(steam.amount, target.getSteamCapacity(), "steam");
        if (input != null) validateAmount(input.amount, target.getInputGasCapacity(), "heated coolant");
        if (output != null) validateAmount(output.amount, target.getOutputGasCapacity(), "cooled coolant");
    }

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
        water = syncFluidStack(water, data.waterStored);
        steam = syncFluidStack(steam, data.steamStored);
        input = syncGasStack(input, data.InputGas);
        output = syncGasStack(output, data.OutputGas);
        storedHeat = data.getHeatCapacitor().getHeat();
        heatCapacity = data.getHeatCapacitor().getHeatCapacity();
    }

    @Override
    public void load(NBTTagCompound nbtTags) {
        water = null;
        steam = null;
        input = null;
        output = null;
        storedHeat = -1;
        heatCapacity = 0;
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
