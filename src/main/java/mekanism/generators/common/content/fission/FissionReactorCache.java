package mekanism.generators.common.content.fission;

import mekanism.api.NBTConstants;
import mekanism.api.gas.GasStack;
import mekanism.api.heat.HeatAPI;
import mekanism.common.multiblock.MultiblockCache;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

public class FissionReactorCache extends MultiblockCache<SynchronizedFissionData> {

    public GasStack fuel;
    public GasStack waste;
    public GasStack gasCoolant;
    public GasStack heatedCoolant;

    public FluidStack coolant;
    public FluidStack steam;

    public double rateLimit = SynchronizedFissionData.getDefaultRateLimit();
    public boolean active;

    public double burnRemaining;
    public double partialWaste;
    public double storedHeat = -1;
    public double heatCapacity;
    public double reactorDamage;
    public boolean forceDisable;

    @Override
    public void apply(SynchronizedFissionData data) {
        data.updateCapacities();
        data.fuelTank.setGas(fuel == null ? null : fuel.copy());
        data.wasteTank.setGas(waste == null ? null : waste.copy());
        data.gasCoolantTank.setGas(gasCoolant == null ? null : gasCoolant.copy());
        data.heatedCoolantTank.setGas(heatedCoolant == null ? null : heatedCoolant.copy());
        data.coolantTank.setFluid(coolant == null ? null : coolant.copy());
        data.steamTank.setFluid(steam == null ? null : steam.copy());

        data.rateLimit = HeatAPI.isFinite(rateLimit) ? Math.max(0, rateLimit) : SynchronizedFissionData.getDefaultRateLimit();
        data.active = active;
        data.burnRemaining = HeatAPI.isFinite(burnRemaining) ? Math.max(0, Math.min(HeatAPI.MAX_HEAT, burnRemaining)) : 0;
        data.partialWaste = HeatAPI.isFinite(partialWaste) ? Math.max(0, Math.min(HeatAPI.MAX_HEAT, partialWaste)) : 0;
        if (storedHeat >= 0 && HeatAPI.isFinite(storedHeat) && heatCapacity >= 1 && HeatAPI.isFinite(heatCapacity)) {
            data.getHeatCapacitor().setHeatCapacity(heatCapacity, false);
            data.getHeatCapacitor().setHeat(storedHeat);
        }
        data.reactorDamage = SynchronizedFissionData.sanitizeDamage(reactorDamage);
        data.forceDisable = forceDisable;
        data.sanitizeRuntimeState();
        data.updateCapacities();
    }

    @Override
    public void sync(SynchronizedFissionData data) {
        data.sanitizeRuntimeState();
        fuel = syncGasStack(fuel, data.fuelTank.getGas());
        waste = syncGasStack(waste, data.wasteTank.getGas());
        gasCoolant = syncGasStack(gasCoolant, data.gasCoolantTank.getGas());
        heatedCoolant = syncGasStack(heatedCoolant, data.heatedCoolantTank.getGas());
        coolant = syncFluidStack(coolant, data.coolantTank.getFluid());
        steam = syncFluidStack(steam, data.steamTank.getFluid());

        rateLimit = HeatAPI.isFinite(data.rateLimit) ? Math.max(0, data.rateLimit) : SynchronizedFissionData.getDefaultRateLimit();
        active = data.active;
        burnRemaining = HeatAPI.isFinite(data.burnRemaining) ? Math.max(0, Math.min(HeatAPI.MAX_HEAT, data.burnRemaining)) : 0;
        partialWaste = HeatAPI.isFinite(data.partialWaste) ? Math.max(0, Math.min(HeatAPI.MAX_HEAT, data.partialWaste)) : 0;
        storedHeat = data.getHeatCapacitor().getHeat();
        heatCapacity = data.getHeatCapacitor().getHeatCapacity();
        reactorDamage = SynchronizedFissionData.sanitizeDamage(data.reactorDamage);
        forceDisable = data.forceDisable;
    }

    @Override
    public void load(NBTTagCompound nbtTags) {
        if (nbtTags.hasKey("cachedFuel")) {
            fuel = GasStack.readFromNBT(nbtTags.getCompoundTag("cachedFuel"));
        }
        if (nbtTags.hasKey("cachedWaste")) {
            waste = GasStack.readFromNBT(nbtTags.getCompoundTag("cachedWaste"));
        }
        if (nbtTags.hasKey("cachedGasCoolant")) {
            gasCoolant = GasStack.readFromNBT(nbtTags.getCompoundTag("cachedGasCoolant"));
        }
        if (nbtTags.hasKey("cachedHeatedCoolant")) {
            heatedCoolant = GasStack.readFromNBT(nbtTags.getCompoundTag("cachedHeatedCoolant"));
        }
        if (nbtTags.hasKey("cachedCoolant")) {
            coolant = FluidStack.loadFluidStackFromNBT(nbtTags.getCompoundTag("cachedCoolant"));
        }
        if (nbtTags.hasKey("cachedSteam")) {
            steam = FluidStack.loadFluidStackFromNBT(nbtTags.getCompoundTag("cachedSteam"));
        }

        rateLimit = nbtTags.hasKey("fissionRateLimit") ? nbtTags.getDouble("fissionRateLimit") : SynchronizedFissionData.getDefaultRateLimit();
        active = nbtTags.getBoolean("fissionActive");
        burnRemaining = nbtTags.getDouble("fissionBurnRemaining");
        partialWaste = nbtTags.getDouble("fissionPartialWaste");
        if (nbtTags.hasKey(NBTConstants.HEAT_STORED) && nbtTags.hasKey(NBTConstants.HEAT_CAPACITY)) {
            double loadedHeat = nbtTags.getDouble(NBTConstants.HEAT_STORED);
            double loadedCapacity = nbtTags.getDouble(NBTConstants.HEAT_CAPACITY);
            if (loadedHeat >= 0 && HeatAPI.isFinite(loadedHeat) && loadedCapacity >= 1 && HeatAPI.isFinite(loadedCapacity)) {
                storedHeat = HeatAPI.sanitizeHeat(loadedHeat, 0);
                heatCapacity = HeatAPI.sanitizeHeatCapacity(loadedCapacity);
            }
        }
        reactorDamage = nbtTags.getDouble("fissionReactorDamage");
        forceDisable = nbtTags.getBoolean("fissionForceDisable");
        if (!HeatAPI.isFinite(rateLimit) || rateLimit < 0) {
            rateLimit = SynchronizedFissionData.getDefaultRateLimit();
        }
        burnRemaining = HeatAPI.isFinite(burnRemaining) ? Math.max(0, Math.min(HeatAPI.MAX_HEAT, burnRemaining)) : 0;
        partialWaste = HeatAPI.isFinite(partialWaste) ? Math.max(0, Math.min(HeatAPI.MAX_HEAT, partialWaste)) : 0;
        reactorDamage = SynchronizedFissionData.sanitizeDamage(reactorDamage);
    }

    @Override
    public void save(NBTTagCompound nbtTags) {
        if (fuel != null) {
            nbtTags.setTag("cachedFuel", fuel.write(new NBTTagCompound()));
        }
        if (waste != null) {
            nbtTags.setTag("cachedWaste", waste.write(new NBTTagCompound()));
        }
        if (gasCoolant != null) {
            nbtTags.setTag("cachedGasCoolant", gasCoolant.write(new NBTTagCompound()));
        }
        if (heatedCoolant != null) {
            nbtTags.setTag("cachedHeatedCoolant", heatedCoolant.write(new NBTTagCompound()));
        }
        if (coolant != null) {
            nbtTags.setTag("cachedCoolant", coolant.writeToNBT(new NBTTagCompound()));
        }
        if (steam != null) {
            nbtTags.setTag("cachedSteam", steam.writeToNBT(new NBTTagCompound()));
        }

        nbtTags.setDouble("fissionRateLimit", HeatAPI.isFinite(rateLimit) ? Math.max(0, rateLimit) : SynchronizedFissionData.getDefaultRateLimit());
        nbtTags.setBoolean("fissionActive", active);
        nbtTags.setDouble("fissionBurnRemaining", HeatAPI.isFinite(burnRemaining) ? Math.max(0, Math.min(HeatAPI.MAX_HEAT, burnRemaining)) : 0);
        nbtTags.setDouble("fissionPartialWaste", HeatAPI.isFinite(partialWaste) ? Math.max(0, Math.min(HeatAPI.MAX_HEAT, partialWaste)) : 0);
        if (storedHeat >= 0 && HeatAPI.isFinite(storedHeat) && heatCapacity >= 1 && HeatAPI.isFinite(heatCapacity)) {
            nbtTags.setDouble(NBTConstants.HEAT_STORED, storedHeat);
            nbtTags.setDouble(NBTConstants.HEAT_CAPACITY, heatCapacity);
        }
        nbtTags.setDouble("fissionReactorDamage", SynchronizedFissionData.sanitizeDamage(reactorDamage));
        nbtTags.setBoolean("fissionForceDisable", forceDisable);
    }
}
