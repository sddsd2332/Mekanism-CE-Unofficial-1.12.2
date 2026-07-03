package mekanism.common.content.boiler;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import mekanism.api.Coord4D;
import mekanism.api.IHeatTransfer;
import mekanism.api.gas.GasStack;
import mekanism.common.MekanismFluids;
import mekanism.common.config.MekanismConfig;
import mekanism.common.content.tank.SynchronizedTankData.ValveData;
import mekanism.common.multiblock.SynchronizedData;
import mekanism.common.util.FluidContainerUtils;
import mekanism.common.util.UnitDisplayUtils.TemperatureUnit;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import java.util.Map;
import java.util.Set;

public class SynchronizedBoilerData extends SynchronizedData<SynchronizedBoilerData> implements IHeatTransfer {

    public static Map<String, Boolean> clientHotMap = new Object2ObjectOpenHashMap<>();

    public static double CASING_INSULATION_COEFFICIENT = 1;
    public static double CASING_INVERSE_CONDUCTION_COEFFICIENT = 1;
    public static double BASE_BOIL_TEMP = 100 - (TemperatureUnit.AMBIENT.zeroOffset - TemperatureUnit.CELSIUS.zeroOffset);
    public static double HEATED_COOLANT_TEMP = 100_000D;
    public static double COOLANT_COOLING_EFFICIENCY = 0.4;

    public FluidStack waterStored;
    public FluidStack prevWater;
    public FluidStack steamStored;
    public FluidStack prevSteam;
    public GasStack InputGas;
    public GasStack prevInputGas;
    public GasStack OutputGas;
    public GasStack prevOutputGas;

    public double lastEnvironmentLoss;
    public int lastBoilRate;
    public int lastMaxBoil;

    public boolean clientHot;

    public double temperature;

    public double heatToAbsorb;

    public double heatCapacity = 1000;

    public int superheatingElements;

    public int waterVolume;

    public int steamVolume;

    public Coord4D upperRenderLocation;

    public Set<ValveData> valves = new ObjectOpenHashSet<>();

    public SynchronizedBoilerData() {
        heatTransfers.add(this);
    }

    /**
     * @return how much heat energy is needed to convert one unit of water into steam
     */
    public static double getHeatEnthalpy() {
        return MekanismConfig.current().general.maxEnergyPerSteam.val() / MekanismConfig.current().general.energyPerHeat.val();
    }

    public double getHeatAvailable() {
        double heatAvailable = (temperature - BASE_BOIL_TEMP) * locations.size();
        return Math.min(heatAvailable, superheatingElements * MekanismConfig.current().general.superheatingHeatTransfer.val());
    }

    public int getWaterCapacity() {
        return waterVolume * BoilerUpdateProtocol.WATER_PER_TANK;
    }

    public int getSteamCapacity() {
        return steamVolume * BoilerUpdateProtocol.STEAM_PER_TANK;
    }

    public int getInputGasCapacity() {
        return waterVolume * BoilerUpdateProtocol.WATER_PER_TANK;
    }

    public int getOutputGasCapacity() {
        return steamVolume * BoilerUpdateProtocol.STEAM_PER_TANK;
    }

    public int getWaterAmount() {
        return waterStored == null ? 0 : waterStored.amount;
    }

    public int getSteamAmount() {
        return steamStored == null ? 0 : steamStored.amount;
    }

    public int getInputGasAmount() {
        return InputGas == null ? 0 : InputGas.amount;
    }

    public int getOutputGasAmount() {
        return OutputGas == null ? 0 : OutputGas.amount;
    }

    public int setWaterStackSize(int amount) {
        if (waterStored == null) {
            return 0;
        }
        int capacity = getWaterCapacity();
        if (amount > capacity) {
            amount = capacity;
        }
        if (amount <= 0) {
            waterStored = null;
            return 0;
        }
        waterStored = FluidContainerUtils.copyWithAmount(waterStored, amount);
        return amount;
    }

    public int setSteamStackSize(int amount) {
        if (steamStored == null) {
            return 0;
        }
        int capacity = getSteamCapacity();
        if (amount > capacity) {
            amount = capacity;
        }
        if (amount <= 0) {
            steamStored = null;
            return 0;
        }
        steamStored = FluidContainerUtils.copyWithAmount(steamStored, amount);
        return amount;
    }

    public int setInputGasStackSize(int amount) {
        if (InputGas == null) {
            return 0;
        }
        int capacity = getInputGasCapacity();
        if (amount > capacity) {
            amount = capacity;
        }
        if (amount <= 0) {
            InputGas = null;
            return 0;
        }
        InputGas = InputGas.copy().withAmount(amount);
        return amount;
    }

    public int setOutputGasStackSize(int amount) {
        if (OutputGas == null) {
            return 0;
        }
        int capacity = getOutputGasCapacity();
        if (amount > capacity) {
            amount = capacity;
        }
        if (amount <= 0) {
            OutputGas = null;
            return 0;
        }
        OutputGas = OutputGas.copy().withAmount(amount);
        return amount;
    }

    public void clampStoredSubstancesToCapacity() {
        sanitizeStoredSubstances();
        setWaterStackSize(getWaterAmount());
        setSteamStackSize(getSteamAmount());
        setInputGasStackSize(getInputGasAmount());
        setOutputGasStackSize(getOutputGasAmount());
    }

    public boolean sanitizeStoredSubstances() {
        boolean changed = false;
        if (waterStored != null && (waterStored.amount <= 0 || waterStored.getFluid() != FluidRegistry.WATER)) {
            setWaterStackSize(0);
            changed = true;
        }
        if (steamStored != null && (steamStored.amount <= 0 || steamStored.getFluid() != FluidRegistry.getFluid("steam"))) {
            setSteamStackSize(0);
            changed = true;
        }
        if (InputGas != null && (InputGas.amount <= 0 || InputGas.getGas() != MekanismFluids.SuperheatedSodium)) {
            setInputGasStackSize(0);
            changed = true;
        }
        if (OutputGas != null && (OutputGas.amount <= 0 || OutputGas.getGas() != MekanismFluids.Sodium)) {
            setOutputGasStackSize(0);
            changed = true;
        }
        return changed;
    }

    public boolean needsRenderUpdate() {
        if ((waterStored == null && prevWater != null) || (waterStored != null && prevWater == null)) {
            return true;
        }
        if (waterStored != null) {
            return ((waterStored.getFluid() != prevWater.getFluid()) || (waterStored.amount != prevWater.amount));
        }
        if ((steamStored == null && prevSteam != null) || (steamStored != null && prevSteam == null)) {
            return true;
        }
        if (steamStored != null) {
            return (steamStored.getFluid() != prevSteam.getFluid()) || (steamStored.amount != prevSteam.amount);
        }

        if ((InputGas == null && prevInputGas != null) || (InputGas != null && prevInputGas == null)) {
            return true;
        }
        if (InputGas != null) {
            return ((InputGas.getGas() != prevInputGas.getGas()) || (InputGas.amount != prevInputGas.amount));
        }

        if ((OutputGas== null && prevOutputGas != null) || (OutputGas != null && prevOutputGas == null)) {
            return true;
        }
        if (OutputGas != null) {
            return ((OutputGas.getGas() != prevOutputGas.getGas()) || (OutputGas.amount != prevOutputGas.amount));
        }
        return false;
    }

    @Override
    public double getTemp() {
        return temperature;
    }



    @Override
    public double getInverseConductionCoefficient() {
        return CASING_INVERSE_CONDUCTION_COEFFICIENT * locations.size();
    }

    @Override
    public double getInsulationCoefficient(EnumFacing side) {
        return CASING_INSULATION_COEFFICIENT * locations.size();
    }

    @Override
    public void transferHeatTo(double heat) {
        heatToAbsorb += heat;
    }

    @Override
    public double[] simulateHeat() {
        double invConduction = IHeatTransfer.AIR_INVERSE_COEFFICIENT + (CASING_INSULATION_COEFFICIENT + CASING_INVERSE_CONDUCTION_COEFFICIENT) * locations.size();
        double heatToTransfer = temperature / invConduction;
        transferHeatTo(-heatToTransfer);
        return new double[]{0, heatToTransfer};
    }

    @Override
    public double applyTemperatureChange() {
        temperature += heatToAbsorb / locations.size();
        heatToAbsorb = 0;
        return temperature;
    }

    @Override
    public boolean canConnectHeat(EnumFacing side) {
        return false;
    }

    @Override
    public IHeatTransfer getAdjacent(EnumFacing side) {
        return null;
    }
}
