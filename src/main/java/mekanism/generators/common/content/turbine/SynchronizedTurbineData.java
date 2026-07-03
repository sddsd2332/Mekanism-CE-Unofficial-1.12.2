package mekanism.generators.common.content.turbine;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.Coord4D;
import mekanism.api.energy.IEnergyContainer;
import mekanism.common.config.MekanismConfig;
import mekanism.common.multiblock.SynchronizedData;
import mekanism.common.tile.TileEntityGasTank.GasMode;
import mekanism.common.util.FluidContainerUtils;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;
import java.util.Map;

public class SynchronizedTurbineData extends SynchronizedData<SynchronizedTurbineData> implements IEnergyContainer {

    public static final float ROTATION_THRESHOLD = 0.001F;
    public static Map<String, Float> clientRotationMap = new Object2ObjectOpenHashMap<>();

    @Nullable
    public FluidStack fluidStored;

    @Nullable
    public FluidStack prevFluid;

    public double electricityStored;

    public GasMode dumpMode = GasMode.IDLE;

    public int blades;
    public int vents;
    public int coils;
    public int condensers;

    public int lowerVolume;

    public Coord4D complex;

    public int lastSteamInput;
    public int newSteamInput;

    public int flowRemaining;

    public int clientDispersers;
    public int clientFlow;
    public float clientRotation;

    public SynchronizedTurbineData() {
        energyContainers.add(this);
    }

    public int getDispersers() {
        return (volLength - 2) * (volWidth - 2) - 1;
    }

    public int getFluidCapacity() {
        return lowerVolume * TurbineUpdateProtocol.FLUID_PER_TANK;
    }

    public int getVentWaterCapacity() {
        return condensers * MekanismConfig.current().generators.condenserRate.val();
    }

    public int getSteamAmount() {
        return fluidStored == null ? 0 : fluidStored.amount;
    }

    public int getVentWaterAmount() {
        return Math.max(flowRemaining, 0);
    }

    public int setSteamStackSize(int amount) {
        if (fluidStored == null || fluidStored.amount <= 0) {
            return 0;
        }
        int capacity = getFluidCapacity();
        if (amount > capacity) {
            amount = capacity;
        }
        if (amount <= 0) {
            fluidStored = null;
            return 0;
        }
        fluidStored = FluidContainerUtils.copyWithAmount(fluidStored, amount);
        return amount;
    }

    public int shrinkSteamStack(int amount) {
        int stored = getSteamAmount();
        if (stored <= 0 || amount <= 0) {
            return 0;
        }
        int newSize = setSteamStackSize(stored - amount);
        return stored - newSize;
    }

    public int setVentWaterStackSize(int amount) {
        int capacity = getVentWaterCapacity();
        if (amount > capacity) {
            amount = capacity;
        }
        if (amount <= 0) {
            flowRemaining = 0;
            return 0;
        }
        flowRemaining = amount;
        return amount;
    }

    public int shrinkVentWaterStack(int amount) {
        int stored = getVentWaterAmount();
        if (stored <= 0 || amount <= 0) {
            return 0;
        }
        int newSize = setVentWaterStackSize(stored - amount);
        return stored - newSize;
    }

    public void clampSteamToCapacity() {
        sanitizeStoredFluid();
        setSteamStackSize(getSteamAmount());
    }

    public double getEnergyCapacity() {
        return volume * MekanismConfig.current().generators.turbineGeneratorStorage.val(); //16 MJ energy capacity per volume
    }

    @Override
    public double getEnergy() {
        return electricityStored;
    }

    @Override
    public void setEnergy(double energy) {
        electricityStored = Math.max(0, Math.min(energy, getMaxEnergy()));
    }

    @Override
    public double getMaxEnergy() {
        return getEnergyCapacity();
    }

    @Override
    public double insert(double amount, Action action, AutomationType automationType) {
        return automationType == AutomationType.INTERNAL && isFormed() ? IEnergyContainer.super.insert(amount, action, automationType) : amount;
    }

    @Override
    public double extract(double amount, Action action, AutomationType automationType) {
        return automationType == AutomationType.EXTERNAL && isFormed() ? IEnergyContainer.super.extract(amount, action, automationType) : 0;
    }

    public boolean sanitizeStoredFluid() {
        if (fluidStored != null && (fluidStored.amount <= 0 || fluidStored.getFluid() != FluidRegistry.getFluid("steam"))) {
            setSteamStackSize(0);
            return true;
        }
        return false;
    }

    public boolean needsRenderUpdate() {
        if ((fluidStored == null && prevFluid != null) || (fluidStored != null && prevFluid == null)) {
            return true;
        }
        if (fluidStored != null) {
            return (fluidStored.getFluid() != prevFluid.getFluid()) || (fluidStored.amount != prevFluid.amount);
        }
        return false;
    }
}
