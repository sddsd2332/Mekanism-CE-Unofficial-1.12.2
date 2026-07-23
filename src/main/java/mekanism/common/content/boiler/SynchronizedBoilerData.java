package mekanism.common.content.boiler;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import mekanism.api.Coord4D;
import mekanism.api.heat.HeatAPI;
import mekanism.api.heat.IHeatCapacitor;
import mekanism.api.gas.GasStack;
import mekanism.common.MekanismFluids;
import mekanism.common.config.MekanismConfig;
import mekanism.common.content.tank.SynchronizedTankData.ValveData;
import mekanism.common.multiblock.SynchronizedData;
import mekanism.common.capabilities.heat.VariableHeatCapacitor;
import mekanism.common.util.FluidContainerUtils;
import mekanism.common.util.UnitDisplayUtils.TemperatureUnit;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class SynchronizedBoilerData extends SynchronizedData<SynchronizedBoilerData> {

    /** Server-side hot-state lookup used by inner blocks. */
    public static final Map<String, Boolean> hotMap = new ConcurrentHashMap<>();

    /** Client-only compatibility lookup used by the 1.12 blockstate and lighting hooks. */
    @Deprecated
    public static final Map<String, Boolean> clientHotMap = new ConcurrentHashMap<>();

    public static final double CASING_HEAT_CAPACITY = 50;
    public static final double CASING_INSULATION_COEFFICIENT = 100_000;
    public static final double CASING_INVERSE_CONDUCTION_COEFFICIENT = 1;
    public static final double BASE_BOIL_TEMP = TemperatureUnit.CELSIUS.zeroOffset + 100;
    public static final double COOLANT_COOLING_EFFICIENCY = 0.4;
    public static final double HEATED_COOLANT_TEMP = 100_000;
    public static final double SODIUM_THERMAL_ENTHALPY = 5;
    private static final double DEFAULT_WATER_CONDUCTIVITY = 0.7;
    private static final double DEFAULT_SUPERHEATING_TRANSFER = 16_000_000;
    private static final double DEFAULT_STEAM_ENTHALPY = 10;
    private static final int DEFAULT_HEATED_COOLANT_PER_TANK = 256_000;
    private static final int DEFAULT_COOLED_COOLANT_PER_TANK = 256_000;

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

    public double biomeAmbientTemp = HeatAPI.AMBIENT_TEMP;
    private final VariableHeatCapacitor heatCapacitor = VariableHeatCapacitor.create(
          CASING_HEAT_CAPACITY,
          () -> CASING_INVERSE_CONDUCTION_COEFFICIENT,
          () -> CASING_INSULATION_COEFFICIENT,
          () -> biomeAmbientTemp,
          this
    );

    public int superheatingElements;

    public int waterVolume;

    public int steamVolume;

    public Coord4D upperRenderLocation;

    public Set<ValveData> valves = new ObjectOpenHashSet<>();

    public SynchronizedBoilerData() {
    }

    /**
     * @return how much heat energy is needed to convert one unit of water into steam
     */
    public static double getHeatEnthalpy() {
        double enthalpy = MekanismConfig.current().general == null ? DEFAULT_STEAM_ENTHALPY : MekanismConfig.current().general.maxEnergyPerSteam.val();
        return HeatAPI.isFinite(enthalpy) && enthalpy > HeatAPI.EPSILON && enthalpy <= HeatAPI.MAX_HEAT ? enthalpy : DEFAULT_STEAM_ENTHALPY;
    }

    public static double getSteamEnergyEfficiency() {
        return 0.2;
    }

    public double getHeatAvailable() {
        double temperature = HeatAPI.sanitizeTemperature(getTemperature());
        double conductivity = getWaterConductivity();
        if (conductivity <= 0) {
            return 0;
        }
        double heatAvailable = HeatAPI.multiplyHeatSigned(temperature - BASE_BOIL_TEMP, heatCapacitor.getHeatCapacity());
        if (!HeatAPI.isFinite(heatAvailable) || heatAvailable <= 0) {
            return 0;
        }
        heatAvailable = HeatAPI.multiplyHeat(heatAvailable, conductivity);
        double elementHeat = HeatAPI.multiplyHeat(Math.max(0, superheatingElements), getSuperheatingTransfer());
        return Math.max(0, Math.min(heatAvailable, elementHeat));
    }

    private static double getWaterConductivity() {
        double conductivity = MekanismConfig.current().general == null ? DEFAULT_WATER_CONDUCTIVITY : MekanismConfig.current().general.boilerWaterConductivity.val();
        return HeatAPI.isFinite(conductivity) && conductivity >= 0 && conductivity <= 1 ? conductivity : DEFAULT_WATER_CONDUCTIVITY;
    }

    public static double getSuperheatingTransfer() {
        double transfer = MekanismConfig.current().general == null ? DEFAULT_SUPERHEATING_TRANSFER : MekanismConfig.current().general.superheatingHeatTransfer.val();
        return HeatAPI.isFinite(transfer) && transfer >= 0 && transfer <= 1_024_000_000 ? transfer : DEFAULT_SUPERHEATING_TRANSFER;
    }

    public int getBoilCapacity() {
        double transfer = HeatAPI.multiplyHeat(Math.max(0, superheatingElements), getSuperheatingTransfer());
        double enthalpy = getHeatEnthalpy();
        double efficiency = getSteamEnergyEfficiency();
        if (transfer <= 0 || enthalpy <= 0 || efficiency <= 0) {
            return 0;
        }
        double capacity = transfer / enthalpy * efficiency;
        return !HeatAPI.isFinite(capacity) || capacity >= Integer.MAX_VALUE ? Integer.MAX_VALUE : Math.max(0, (int) Math.floor(capacity));
    }

    public VariableHeatCapacitor getHeatCapacitor() {
        return heatCapacitor;
    }

    public double getTemperature() {
        return heatCapacitor.getTemperature();
    }

    public void updateHeatCapacity() {
        double capacity = HeatAPI.multiplyHeat(CASING_HEAT_CAPACITY, Math.max(0, locations.size()));
        heatCapacitor.updateHeatAndCapacity(Math.max(CASING_HEAT_CAPACITY, HeatAPI.sanitizeHeatCapacity(capacity)));
    }

    public void updateAmbientTemperature(World world) {
        if (world == null || minLocation == null || maxLocation == null) {
            biomeAmbientTemp = HeatAPI.AMBIENT_TEMP;
            return;
        }
        BlockPos min = minLocation.getPos();
        BlockPos max = maxLocation.getPos();
        BlockPos[] corners = {
              min,
              new BlockPos(max.getX(), min.getY(), min.getZ()),
              new BlockPos(min.getX(), min.getY(), max.getZ()),
              new BlockPos(max.getX(), min.getY(), max.getZ()),
              new BlockPos(min.getX(), max.getY(), min.getZ()),
              new BlockPos(max.getX(), max.getY(), min.getZ()),
              new BlockPos(min.getX(), max.getY(), max.getZ()),
              max
        };
        double biomeTemperature = 0;
        for (BlockPos corner : corners) {
            double temperature = world.getBiomeForCoordsBody(corner).getTemperature(corner);
            biomeTemperature += HeatAPI.isFinite(temperature) ? temperature : 0.8D;
        }
        biomeAmbientTemp = HeatAPI.getAmbientTemp(biomeTemperature / corners.length);
        if (!HeatAPI.isFinite(biomeAmbientTemp)) {
            biomeAmbientTemp = HeatAPI.AMBIENT_TEMP;
        }
    }

    public double simulateEnvironment() {
        double inverseConduction = HeatAPI.AIR_INVERSE_COEFFICIENT + CASING_INSULATION_COEFFICIENT + CASING_INVERSE_CONDUCTION_COEFFICIENT;
        if (!HeatAPI.isFinite(inverseConduction) || inverseConduction <= 0) {
            inverseConduction = HeatAPI.MAX_HEAT;
        }
        double temperatureToTransfer = (HeatAPI.sanitizeTemperature(getTemperature()) - HeatAPI.sanitizeTemperature(biomeAmbientTemp)) / inverseConduction;
        double heatToTransfer = HeatAPI.multiplyHeatSigned(temperatureToTransfer, heatCapacitor.getHeatCapacity());
        if (HeatAPI.isFinite(heatToTransfer) && Math.abs(heatToTransfer) > HeatAPI.EPSILON) {
            double before = heatCapacitor.getHeat();
            heatCapacitor.handleHeat(-heatToTransfer);
            double actual = before - heatCapacitor.getHeat();
            if (HeatAPI.isFinite(actual) && heatCapacitor.getHeatCapacity() > 0) {
                temperatureToTransfer = actual / heatCapacitor.getHeatCapacity();
            }
        }
        return HeatAPI.isFinite(temperatureToTransfer) ? Math.max(temperatureToTransfer, 0) : 0;
    }

    public int getWaterCapacity() {
        return scaledCapacity(waterVolume, getConfiguredCapacity(
              MekanismConfig.current().general == null ? BoilerUpdateProtocol.WATER_PER_TANK :
                    MekanismConfig.current().general.boilerWaterPerTank.val(), BoilerUpdateProtocol.WATER_PER_TANK));
    }

    public int getSteamCapacity() {
        return scaledCapacity(steamVolume, getConfiguredCapacity(
              MekanismConfig.current().general == null ? BoilerUpdateProtocol.STEAM_PER_TANK :
                    MekanismConfig.current().general.boilerSteamPerTank.val(), BoilerUpdateProtocol.STEAM_PER_TANK));
    }

    public int getInputGasCapacity() {
        int configured = MekanismConfig.current().general == null ? DEFAULT_HEATED_COOLANT_PER_TANK :
              MekanismConfig.current().general.boilerHeatedCoolantPerTank.val();
        return scaledCapacity(waterVolume, getConfiguredCapacity(configured, DEFAULT_HEATED_COOLANT_PER_TANK));
    }

    public int getOutputGasCapacity() {
        int configured = MekanismConfig.current().general == null ? DEFAULT_COOLED_COOLANT_PER_TANK :
              MekanismConfig.current().general.boilerCooledCoolantPerTank.val();
        return scaledCapacity(steamVolume, getConfiguredCapacity(configured, DEFAULT_COOLED_COOLANT_PER_TANK));
    }

    private static int getConfiguredCapacity(int configured, int fallback) {
        return configured > 0 ? configured : fallback;
    }

    private static int scaledCapacity(int volume, int perVolume) {
        long capacity = (long) Math.max(0, volume) * Math.max(0, perVolume);
        return (int) Math.min(Integer.MAX_VALUE, capacity);
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
        if (waterStored != null && ((waterStored.getFluid() != prevWater.getFluid()) || (waterStored.amount != prevWater.amount))) {
            return true;
        }
        if ((steamStored == null && prevSteam != null) || (steamStored != null && prevSteam == null)) {
            return true;
        }
        if (steamStored != null && ((steamStored.getFluid() != prevSteam.getFluid()) || (steamStored.amount != prevSteam.amount))) {
            return true;
        }

        if ((InputGas == null && prevInputGas != null) || (InputGas != null && prevInputGas == null)) {
            return true;
        }
        if (InputGas != null && ((InputGas.getGas() != prevInputGas.getGas()) || (InputGas.amount != prevInputGas.amount))) {
            return true;
        }

        if ((OutputGas == null && prevOutputGas != null) || (OutputGas != null && prevOutputGas == null)) {
            return true;
        }
        return OutputGas != null && ((OutputGas.getGas() != prevOutputGas.getGas()) || (OutputGas.amount != prevOutputGas.amount));
    }

}
