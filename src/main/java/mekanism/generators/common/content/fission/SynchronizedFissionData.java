package mekanism.generators.common.content.fission;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import mekanism.api.*;
import mekanism.api.gas.GasStack;
import mekanism.api.heat.HeatAPI;
import mekanism.common.capabilities.heat.VariableHeatCapacitor;
import mekanism.common.MekanismFluids;
import mekanism.common.capabilities.fluid.VariableCapacityFluidTank;
import mekanism.common.capabilities.tank.ValidatingGasTank;
import mekanism.common.config.MekanismConfig;
import mekanism.common.multiblock.SynchronizedData;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import java.util.Random;
import java.util.Set;

public class SynchronizedFissionData extends SynchronizedData<SynchronizedFissionData> {

    public static final int COOLANT_PER_VOLUME = 100_000;
    public static final int STEAM_PER_VOLUME = 1_000_000;
    public static final int HEATED_COOLANT_PER_VOLUME = 1_000_000;
    public static final int FUEL_PER_ASSEMBLY = 8_000;

    public static final double DEFAULT_RATE_LIMIT = 0.1;
    public static final double BURN_PER_ASSEMBLY = 1;
    public static final double ENERGY_PER_FISSION_FUEL = 1_000_000;
    public static final double BASE_TEMPERATURE = 300;
    public static final double BOIL_TEMPERATURE = 373.15;
    public static final double BOIL_EFFICIENCY_TARGET = 4;

    public static final double MIN_DAMAGE_TEMPERATURE = 1_200;
    public static final double MAX_DAMAGE_TEMPERATURE = 1_800;
    public static final double MAX_DAMAGE = 100;
    public static final double MELTDOWN_CHANCE = 0.001;
    public static final double MELTDOWN_RADIATION_MULTIPLIER = 50;
    public static final double POST_MELTDOWN_DAMAGE = 0.75 * MAX_DAMAGE;
    public static final double MELTDOWN_EXPLOSION_CHANCE = 1D / 512_000D;
    private static final double DEFAULT_CASING_HEAT_CAPACITY = 1_000;
    private static final double INVERSE_INSULATION_COEFFICIENT = 10_000;
    private static final double INVERSE_CONDUCTION_COEFFICIENT = 10;
    private static final double ENVIRONMENT_INVERSE_CONDUCTION = HeatAPI.AIR_INVERSE_COEFFICIENT + INVERSE_INSULATION_COEFFICIENT +
          INVERSE_CONDUCTION_COEFFICIENT;
    private static final double DEFAULT_WATER_COOLING_CONDUCTIVITY = 0.5;
    private static final double DEFAULT_SODIUM_COOLING_CONDUCTIVITY = 1;
    private static final double DEFAULT_STEAM_ENERGY_EFFICIENCY = 0.2;
    private static final double DEFAULT_SODIUM_THERMAL_ENTHALPY = 5;

    public final Set<FormedAssembly> assemblies = new ObjectOpenHashSet<>();

    public final VariableCapacityFluidTank coolantTank = VariableCapacityFluidTank.input(this, this::getCoolantCapacity,
          this::isValidFluidCoolant, this);
    public final VariableCapacityFluidTank steamTank = VariableCapacityFluidTank.output(this, this::getSteamCapacity,
          stack -> stack.getFluid() == FluidRegistry.getFluid("steam"), this);
    public final ValidatingGasTank fuelTank = new ValidatingGasTank(1, gas -> gas == MekanismFluids.FissileFuel);
    public final ValidatingGasTank wasteTank = new ValidatingGasTank(1, gas -> gas == MekanismFluids.NuclearWaste);
    public final ValidatingGasTank gasCoolantTank = new ValidatingGasTank(1, this::isValidGasCoolant);
    public final ValidatingGasTank heatedCoolantTank = new ValidatingGasTank(1, gas -> gas == MekanismFluids.SuperheatedSodium);

    public int fuelAssemblies;
    public int surfaceArea;
    public double biomeAmbientTemp = HeatAPI.AMBIENT_TEMP;
    private final VariableHeatCapacitor heatCapacitor = VariableHeatCapacitor.create(
          getDefaultCasingHeatCapacity(),
          () -> INVERSE_CONDUCTION_COEFFICIENT,
          () -> INVERSE_INSULATION_COEFFICIENT,
          () -> biomeAmbientTemp,
          this
    );

    public double rateLimit = getDefaultRateLimit();
    public boolean active;

    public double burnRemaining;
    public double partialWaste;
    public double reactorDamage;
    public boolean forceDisable;

    public long lastBoilRate;
    public double lastBurnRate;
    public double lastEnvironmentLoss;

    private Coord4D reactorCenter;
    private AxisAlignedBB radiationBounds;

    private GasStack prevFuel;
    private GasStack prevWaste;
    private GasStack prevGasCoolant;
    private GasStack prevHeatedCoolant;
    private FluidStack prevCoolant;
    private FluidStack prevSteam;
    private double prevTemperature = BASE_TEMPERATURE;
    private double prevDamage;
    private boolean prevActive;

    public SynchronizedFissionData() {
        fluidTanks.add(coolantTank);
        fluidTanks.add(steamTank);
        gasTanks.add(fuelTank);
        gasTanks.add(heatedCoolantTank);
        gasTanks.add(wasteTank);
        gasTanks.add(gasCoolantTank);
    }

    public static double getDefaultRateLimit() {
        double rate = MekanismConfig.current().generators != null ? MekanismConfig.current().generators.fissionDefaultBurnRate.val() : DEFAULT_RATE_LIMIT;
        return sanitizeConfig(rate, DEFAULT_RATE_LIMIT, 0, HeatAPI.MAX_HEAT);
    }

    private int getCoolantCapacity() {
        return scaledCapacity(volume, getCooledCoolantPerVolume());
    }

    private int getSteamCapacity() {
        return scaledCapacity(volume, getHeatedCoolantPerVolume());
    }

    private static int scaledCapacity(int volume, int perVolume) {
        long capacity = (long) Math.max(0, volume) * perVolume;
        return capacity >= Integer.MAX_VALUE ? Integer.MAX_VALUE : Math.max(1, (int) capacity);
    }

    private boolean isValidFluidCoolant(FluidStack stack) {
        return stack.getFluid() == FluidRegistry.WATER && gasCoolantTank.getStored() == 0;
    }

    private boolean isValidGasCoolant(mekanism.api.gas.Gas gas) {
        return gas == MekanismFluids.Sodium && coolantTank.getFluidAmount() == 0;
    }

    private static double getBurnPerAssembly() {
        double burn = MekanismConfig.current().generators != null ? MekanismConfig.current().generators.fissionBurnPerAssembly.val() : BURN_PER_ASSEMBLY;
        return sanitizeConfig(burn, BURN_PER_ASSEMBLY, 0, HeatAPI.MAX_HEAT);
    }

    private static int getFuelPerAssembly() {
        int capacity = MekanismConfig.current().generators != null ? MekanismConfig.current().generators.fissionFuelPerAssembly.val() : FUEL_PER_ASSEMBLY;
        return Math.max(1, capacity);
    }

    private static int getCooledCoolantPerVolume() {
        int capacity = MekanismConfig.current().generators != null ? MekanismConfig.current().generators.fissionCooledCoolantPerTank.val() : COOLANT_PER_VOLUME;
        return Math.max(1, capacity);
    }

    private static int getHeatedCoolantPerVolume() {
        int capacity = MekanismConfig.current().generators != null ? MekanismConfig.current().generators.fissionHeatedCoolantPerTank.val() : HEATED_COOLANT_PER_VOLUME;
        return Math.max(1, capacity);
    }

    private static double getEnergyPerFissionFuel() {
        double energy = MekanismConfig.current().generators != null ? MekanismConfig.current().generators.energyPerFissionFuel.val() : ENERGY_PER_FISSION_FUEL;
        return sanitizeConfig(energy, ENERGY_PER_FISSION_FUEL, 0, Integer.MAX_VALUE);
    }

    private static double getDefaultCasingHeatCapacity() {
        double capacity = MekanismConfig.current().generators != null ? MekanismConfig.current().generators.fissionCasingHeatCapacity.val() : DEFAULT_CASING_HEAT_CAPACITY;
        return sanitizeConfig(capacity, DEFAULT_CASING_HEAT_CAPACITY, 1, 1_000_000);
    }

    private static double getBoilEfficiencyTarget() {
        double target = MekanismConfig.current().generators != null ? MekanismConfig.current().generators.fissionSurfaceAreaTarget.val() : BOIL_EFFICIENCY_TARGET;
        return sanitizeConfig(target, BOIL_EFFICIENCY_TARGET, 1, HeatAPI.MAX_HEAT);
    }

    private static boolean areMeltdownsEnabled() {
        return MekanismConfig.current().generators == null || MekanismConfig.current().generators.fissionMeltdownsEnabled.val();
    }

    private static double getMeltdownChance() {
        double chance = MekanismConfig.current().generators != null ? MekanismConfig.current().generators.fissionMeltdownChance.val() : MELTDOWN_CHANCE;
        return sanitizeConfig(chance, MELTDOWN_CHANCE, 0, 1);
    }

    private static double getMeltdownRadiationMultiplier() {
        double multiplier = MekanismConfig.current().generators != null ? MekanismConfig.current().generators.fissionMeltdownRadiationMultiplier.val() : MELTDOWN_RADIATION_MULTIPLIER;
        return sanitizeConfig(multiplier, MELTDOWN_RADIATION_MULTIPLIER, 0, HeatAPI.MAX_HEAT);
    }

    private static double getPostMeltdownDamage() {
        double damage = MekanismConfig.current().generators != null ? MekanismConfig.current().generators.fissionPostMeltdownDamage.val() : POST_MELTDOWN_DAMAGE;
        return sanitizeConfig(damage, POST_MELTDOWN_DAMAGE, 0, MAX_DAMAGE);
    }

    private static double getWaterThermalEnthalpy() {
        double enthalpy = MekanismConfig.current().general == null ? 10 : MekanismConfig.current().general.maxEnergyPerSteam.val();
        return sanitizeConfig(enthalpy, 10, HeatAPI.EPSILON, HeatAPI.MAX_HEAT);
    }

    private static double getSteamEnergyEfficiency() {
        if (MekanismConfig.current().generators == null) {
            return DEFAULT_STEAM_ENERGY_EFFICIENCY;
        }
        double efficiency = MekanismConfig.current().generators.fissionSteamEfficiency.val();
        return sanitizeConfig(efficiency, DEFAULT_STEAM_ENERGY_EFFICIENCY, 0.000_001D, 1);
    }

    private static double getWaterCoolingConductivity() {
        if (MekanismConfig.current().generators == null) {
            return DEFAULT_WATER_COOLING_CONDUCTIVITY;
        }
        double conductivity = MekanismConfig.current().generators.fissionWaterConductivity.val();
        return sanitizeConfig(conductivity, DEFAULT_WATER_COOLING_CONDUCTIVITY, 0, 1);
    }

    private static double getSodiumCoolingConductivity() {
        if (MekanismConfig.current().generators == null) {
            return DEFAULT_SODIUM_COOLING_CONDUCTIVITY;
        }
        double conductivity = MekanismConfig.current().generators.fissionSodiumConductivity.val();
        return sanitizeConfig(conductivity, DEFAULT_SODIUM_COOLING_CONDUCTIVITY, 0, 1);
    }

    private static double getSodiumThermalEnthalpy() {
        if (MekanismConfig.current().generators == null) {
            return DEFAULT_SODIUM_THERMAL_ENTHALPY;
        }
        double enthalpy = MekanismConfig.current().generators.fissionSodiumThermalEnthalpy.val();
        return sanitizeConfig(enthalpy, DEFAULT_SODIUM_THERMAL_ENTHALPY, 0.000_001D, HeatAPI.MAX_HEAT);
    }

    private static double sanitizeConfig(double value, double fallback, double min, double max) {
        return HeatAPI.isFinite(value) && value >= min && value <= max ? value : fallback;
    }

    public void tick(World world) {
        sanitizeRuntimeState();
        updateCapacities();
        if (active && !isForceDisabled()) {
            burnFuel(world);
        } else {
            lastBurnRate = 0;
        }
        handleCoolant();
        dissipateHeat();
        handleDamage();
        radiateEntities(world);
    }

    public void updateCapacities() {
        long rawFuelCapacity = (long) Math.max(0, fuelAssemblies) * getFuelPerAssembly();
        int fuelCapacity = (int) Math.max(1, Math.min(Integer.MAX_VALUE, rawFuelCapacity));
        int heatedCoolantCapacity = scaledCapacity(volume, getHeatedCoolantPerVolume());
        int storedFuel = Math.max(0, fuelTank.getStored());
        if (storedFuel > fuelCapacity) {
            // Cache merges and structure shrinkage may temporarily put more whole fuel in the
            // tank than the rebuilt reactor can hold. Keep the excess in the burn accumulator
            // instead of silently deleting it when setMaxGas clamps the tank.
            burnRemaining = HeatAPI.addHeatClamped(burnRemaining, storedFuel - (double) fuelCapacity);
        }
        fuelTank.setMaxGas(fuelCapacity);
        wasteTank.setMaxGas(fuelCapacity);
        gasCoolantTank.setMaxGas(getCoolantCapacity());
        heatedCoolantTank.setMaxGas(heatedCoolantCapacity);
        clampTank(fuelTank);
        clampTank(wasteTank);
        clampTank(heatedCoolantTank);
        clampTank(steamTank);
        clampCoolantTanks();

        rateLimit = HeatAPI.isFinite(rateLimit) ? Math.max(0, Math.min(getMaxBurnRate(), rateLimit)) : Math.min(getDefaultRateLimit(), getMaxBurnRate());
    }

    public VariableHeatCapacitor getHeatCapacitor() {
        return heatCapacitor;
    }

    public double getTemperature() {
        return heatCapacitor.getTemperature();
    }

    public void updateHeatCapacity() {
        double capacity = HeatAPI.multiplyHeat(getDefaultCasingHeatCapacity(), Math.max(1, locations.size()));
        heatCapacitor.updateHeatAndCapacity(HeatAPI.sanitizeHeatCapacity(capacity));
    }

    public void sanitizeRuntimeState() {
        biomeAmbientTemp = HeatAPI.sanitizeTemperature(biomeAmbientTemp);
        rateLimit = HeatAPI.isFinite(rateLimit) ? Math.max(0, Math.min(HeatAPI.MAX_HEAT, rateLimit)) : getDefaultRateLimit();
        burnRemaining = HeatAPI.isFinite(burnRemaining) ? Math.max(0, Math.min(HeatAPI.MAX_HEAT, burnRemaining)) : 0;
        partialWaste = HeatAPI.isFinite(partialWaste) ? Math.max(0, Math.min(HeatAPI.MAX_HEAT, partialWaste)) : 0;
        reactorDamage = sanitizeDamage(reactorDamage);
        lastBurnRate = HeatAPI.isFinite(lastBurnRate) ? Math.max(0, Math.min(HeatAPI.MAX_HEAT, lastBurnRate)) : 0;
        lastEnvironmentLoss = HeatAPI.isFinite(lastEnvironmentLoss) ? Math.max(0, Math.min(HeatAPI.MAX_HEAT, lastEnvironmentLoss)) : 0;
        lastBoilRate = Math.max(0, lastBoilRate);
        if (forceDisable) {
            active = false;
        }
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
            biomeTemperature += world.getBiomeForCoordsBody(corner).getTemperature(corner);
        }
        biomeAmbientTemp = HeatAPI.getAmbientTemp(biomeTemperature / corners.length);
    }

    public boolean sanitizeStoredContents() {
        boolean changed = false;
        changed |= sanitizeTank(fuelTank);
        changed |= sanitizeTank(wasteTank);
        changed |= sanitizeTank(heatedCoolantTank);
        changed |= sanitizeTank(steamTank);
        changed |= sanitizeCoolantTanks();
        return changed;
    }

    private static void clampTank(ValidatingGasTank tank) {
        GasStack gas = tank.getGas();
        if (gas != null) {
            tank.setStackSize(gas.amount, Action.EXECUTE);
        }
        sanitizeTank(tank);
    }

    private static void clampTank(VariableCapacityFluidTank tank) {
        FluidStack fluid = tank.getFluid();
        if (fluid != null) {
            tank.setStackSize(fluid.amount, Action.EXECUTE);
        }
        sanitizeTank(tank);
    }

    private static boolean sanitizeTank(ValidatingGasTank tank) {
        GasStack gas = tank.getGas();
        if (gas != null && (gas.amount <= 0 || !tank.isValid(gas))) {
            tank.setEmpty();
            return true;
        }
        return false;
    }

    private static boolean sanitizeTank(VariableCapacityFluidTank tank) {
        FluidStack fluid = tank.getFluid();
        if (fluid != null && (fluid.amount <= 0 || !tank.isFluidValid(fluid))) {
            tank.setEmpty();
            return true;
        }
        return false;
    }

    private void clampCoolantTanks() {
        FluidStack fluid = coolantTank.getFluid();
        GasStack gas = gasCoolantTank.getGas();
        if (fluid != null) {
            coolantTank.setStackSize(fluid.amount, Action.EXECUTE);
        }
        if (gas != null) {
            gasCoolantTank.setStackSize(gas.amount, Action.EXECUTE);
        }
        sanitizeCoolantTanks();
    }

    private boolean sanitizeCoolantTanks() {
        FluidStack fluid = coolantTank.getFluid();
        GasStack gas = gasCoolantTank.getGas();
        boolean fluidInvalidType = fluid != null && (fluid.amount <= 0 || fluid.getFluid() != FluidRegistry.WATER);
        boolean gasInvalidType = gas != null && (gas.amount <= 0 || gas.getGas() != MekanismFluids.Sodium);
        boolean changed = false;
        if (fluidInvalidType) {
            coolantTank.setEmpty();
            changed = true;
        }
        if (gasInvalidType) {
            gasCoolantTank.setEmpty();
            changed = true;
        }
        if (!fluidInvalidType && !gasInvalidType && fluid != null && gas != null) {
            gasCoolantTank.setEmpty();
            changed = true;
        }
        return changed;
    }

    void burnFuel(World world) {
        double storedFuelAmount = Math.max(0, fuelTank.getStored());
        double residualFuel = HeatAPI.isFinite(burnRemaining) ? Math.max(0, burnRemaining) : 0;
        if (storedFuelAmount <= 0 && residualFuel <= 0) {
            lastBurnRate = 0;
            return;
        }

        // burnRemaining may include overflow preserved while caches are merged or a structure
        // shrinks, so the represented total is allowed to exceed the physical tank capacity.
        double availableFuel = HeatAPI.addHeatClamped(storedFuelAmount, residualFuel);
        double maxBurnRate = getMaxBurnRate();
        double configuredRate = HeatAPI.isFinite(rateLimit) ? Math.max(0, rateLimit) : 0;
        double toBurn = Math.min(Math.min(configuredRate, maxBurnRate), availableFuel);
        if (!HeatAPI.isFinite(toBurn) || toBurn <= 0) {
            lastBurnRate = 0;
            return;
        }

        availableFuel -= toBurn;
        int remainingFuel = (int) Math.min(fuelTank.getCapacity(), Math.floor(availableFuel));
        burnRemaining = Math.max(0, availableFuel - remainingFuel);
        if (remainingFuel > 0 && fuelTank.isEmpty() && MekanismFluids.FissileFuel != null) {
            fuelTank.setGas(new GasStack(MekanismFluids.FissileFuel, remainingFuel));
        } else {
            fuelTank.setStackSize(remainingFuel, Action.EXECUTE);
        }

        heatCapacitor.handleHeat(HeatAPI.multiplyHeat(toBurn, getEnergyPerFissionFuel()));

        partialWaste = HeatAPI.addHeatClamped(partialWaste, toBurn);
        double wholeWaste = Math.floor(partialWaste);
        int wasteToAdd = (int) Math.min(Integer.MAX_VALUE, wholeWaste);
        double overflowWaste = Math.max(0, wholeWaste - wasteToAdd);
        partialWaste -= wholeWaste;
        if (overflowWaste > 0 && MekanismFluids.NuclearWaste != null && MekanismFluids.NuclearWaste.isRadiation()) {
            radiateFromCore(world, HeatAPI.multiplyHeat(overflowWaste, MekanismFluids.NuclearWaste.getRadioactivity()));
        }
        if (wasteToAdd > 0) {
            if (MekanismFluids.NuclearWaste != null) {
                int accepted;
                if (wasteTank.isEmpty()) {
                    GasStack waste = new GasStack(MekanismFluids.NuclearWaste, wasteToAdd);
                    GasStack remainder = wasteTank.insert(waste, Action.EXECUTE, AutomationType.INTERNAL);
                    accepted = waste.amount - (remainder == null ? 0 : remainder.amount);
                } else if (wasteTank.isTypeEqual(MekanismFluids.NuclearWaste)) {
                    accepted = wasteTank.growStack(wasteToAdd, Action.EXECUTE);
                } else {
                    accepted = 0;
                }
                int leftoverWaste = Math.max(0, wasteToAdd - accepted);
                if (leftoverWaste > 0 && MekanismFluids.NuclearWaste.isRadiation()) {
                    radiateFromCore(world, HeatAPI.multiplyHeat(leftoverWaste, MekanismFluids.NuclearWaste.getRadioactivity()));
                }
            }
        }
        lastBurnRate = toBurn;
    }

    private void handleCoolant() {
        if (getTemperature() <= BOIL_TEMPERATURE) {
            lastBoilRate = 0;
            return;
        }

        double boilEfficiency = getBoilEfficiency();
        double availableHeat = (heatCapacitor.getHeat() - HeatAPI.multiplyHeat(BOIL_TEMPERATURE, heatCapacitor.getHeatCapacity())) * boilEfficiency;
        if (!HeatAPI.isFinite(availableHeat) || availableHeat <= 0) {
            lastBoilRate = 0;
            return;
        }

        if (coolantTank.getFluidAmount() > 0 && gasCoolantTank.getStored() == 0) {
            double caseCoolantHeat = availableHeat * getWaterCoolingConductivity();
            double waterThermalEnthalpy = getWaterThermalEnthalpy();
            double steamEnergyEfficiency = getSteamEnergyEfficiency();
            if (!HeatAPI.isFinite(caseCoolantHeat) || caseCoolantHeat <= 0 || waterThermalEnthalpy <= 0 || steamEnergyEfficiency <= 0 ||
                FluidRegistry.getFluid("steam") == null) {
                lastBoilRate = 0;
                return;
            }
            double boilAmount = steamEnergyEfficiency * caseCoolantHeat / waterThermalEnthalpy;
            int toBoil = HeatAPI.isFinite(boilAmount) ? (int) Math.min(Integer.MAX_VALUE, Math.max(0, Math.floor(boilAmount))) : Integer.MAX_VALUE;
            toBoil = Math.min(toBoil, coolantTank.getFluidAmount());
            if (toBoil <= 0) {
                lastBoilRate = 0;
                return;
            }

            FluidStack extracted = coolantTank.extract(toBoil, Action.EXECUTE, AutomationType.INTERNAL);
            int extractedAmount = extracted == null ? 0 : extracted.amount;
            if (extractedAmount <= 0) {
                lastBoilRate = 0;
                return;
            }
            // Align 26.2 behavior: excess output is vented and does not block cooling.
            steamTank.insert(new FluidStack(FluidRegistry.getFluid("steam"), extractedAmount), Action.EXECUTE, AutomationType.INTERNAL);
            double consumedHeat = HeatAPI.multiplyHeat(extractedAmount, waterThermalEnthalpy / steamEnergyEfficiency);
            heatCapacitor.handleHeat(-consumedHeat);
            lastBoilRate = extractedAmount;
            return;
        }

        if (gasCoolantTank.getStored() > 0 && coolantTank.getFluidAmount() == 0) {
            if (MekanismFluids.SuperheatedSodium == null) {
                lastBoilRate = 0;
                return;
            }
            double caseCoolantHeat = availableHeat * getSodiumCoolingConductivity();
            double sodiumThermalEnthalpy = getSodiumThermalEnthalpy();
            if (!HeatAPI.isFinite(caseCoolantHeat) || caseCoolantHeat <= 0 || sodiumThermalEnthalpy <= 0) {
                lastBoilRate = 0;
                return;
            }
            double heatAmount = caseCoolantHeat / sodiumThermalEnthalpy;
            int toHeat = HeatAPI.isFinite(heatAmount) ? (int) Math.min(Integer.MAX_VALUE, Math.max(0, Math.floor(heatAmount))) : Integer.MAX_VALUE;
            toHeat = Math.min(toHeat, gasCoolantTank.getStored());
            if (toHeat <= 0) {
                lastBoilRate = 0;
                return;
            }

            GasStack extracted = gasCoolantTank.extract(toHeat, Action.EXECUTE, AutomationType.INTERNAL);
            int extractedAmount = extracted == null ? 0 : extracted.amount;
            if (extractedAmount <= 0) {
                lastBoilRate = 0;
                return;
            }
            // Align 26.2 behavior: excess heated coolant is vented and does not block cooling.
            heatedCoolantTank.insert(new GasStack(MekanismFluids.SuperheatedSodium, extractedAmount), Action.EXECUTE, AutomationType.INTERNAL);
            double consumedHeat = HeatAPI.multiplyHeat(extractedAmount, sodiumThermalEnthalpy);
            heatCapacitor.handleHeat(-consumedHeat);
            lastBoilRate = extractedAmount;
            return;
        }

        lastBoilRate = 0;
    }

    private void dissipateHeat() {
        double tempToTransfer = (HeatAPI.sanitizeTemperature(getTemperature()) - HeatAPI.sanitizeTemperature(biomeAmbientTemp)) /
              ENVIRONMENT_INVERSE_CONDUCTION;
        double heatToTransfer = HeatAPI.multiplyHeatSigned(tempToTransfer, heatCapacitor.getHeatCapacity());
        double heatBefore = heatCapacitor.getHeat();
        if (HeatAPI.isFinite(heatToTransfer)) {
            heatCapacitor.handleHeat(-heatToTransfer);
        }
        double heatAfter = heatCapacitor.getHeat();
        double actualLoss = (heatBefore - heatAfter) / heatCapacitor.getHeatCapacity();
        lastEnvironmentLoss = HeatAPI.isFinite(actualLoss) ? Math.max(0, actualLoss) : 0;
    }

    private void handleDamage() {
        double temperature = HeatAPI.sanitizeTemperature(getTemperature());
        if (!HeatAPI.isFinite(reactorDamage)) {
            reactorDamage = 0;
        }
        if (temperature > MIN_DAMAGE_TEMPERATURE) {
            double damageRate = Math.min(temperature, MAX_DAMAGE_TEMPERATURE) / (MIN_DAMAGE_TEMPERATURE * 10);
            reactorDamage = HeatAPI.addHeatClamped(reactorDamage, damageRate);
        } else {
            double repairRate = (MIN_DAMAGE_TEMPERATURE - temperature) / (MIN_DAMAGE_TEMPERATURE * 100);
            reactorDamage = Math.max(0, reactorDamage - repairRate);
        }

        if (temperature < MIN_DAMAGE_TEMPERATURE && reactorDamage < MAX_DAMAGE) {
            setForceDisable(false);
        }
    }

    private void radiateEntities(World world) {
        if (world == null || minLocation == null || maxLocation == null || lastBurnRate <= 0 || !MekanismAPI.getRadiationManager().isRadiationEnabled()) {
            return;
        }
        if (world.rand.nextInt(20) != 0) {
            return;
        }
        AxisAlignedBB hotZone = getRadiationBounds();
        if (hotZone == null) {
            return;
        }
        Iterable<EntityLivingBase> entities = world.getEntitiesWithinAABB(EntityLivingBase.class, hotZone);
        double wasteRadiation = getWasteTankRadioactivity(false) / 3_600D;
        double magnitude = HeatAPI.addHeatClamped(lastBurnRate, wasteRadiation);
        if (magnitude <= 0) {
            return;
        }
        for (EntityLivingBase entity : entities) {
            MekanismAPI.getRadiationManager().radiate(entity, magnitude);
        }
    }

    public boolean isForceDisabled() {
        return forceDisable;
    }

    public boolean shouldMeltdown(Random random) {
        double temperature = HeatAPI.sanitizeTemperature(getTemperature());
        if (temperature < MIN_DAMAGE_TEMPERATURE || reactorDamage < MAX_DAMAGE) {
            return false;
        }
        boolean meltdownsEnabled = areMeltdownsEnabled();
        if (isForceDisabled() && meltdownsEnabled) {
            // If meltdowns were disabled before and now re-enabled, trigger immediately while still critical.
            setForceDisable(false);
            return true;
        }
        if (random != null && random.nextDouble() < Math.min(1, (reactorDamage / MAX_DAMAGE) * getMeltdownChance())) {
            if (meltdownsEnabled) {
                return true;
            }
            setForceDisable(true);
        }
        return false;
    }

    public double collectRadiationForMeltdown() {
        double radiation = getTankRadioactivityAndDump(fuelTank) + getWasteTankRadioactivity(true);
        radiation += getTankRadioactivityAndDump(gasCoolantTank) + getTankRadioactivityAndDump(heatedCoolantTank);
        return HeatAPI.multiplyHeat(radiation, getMeltdownRadiationMultiplier());
    }

    public void onMeltdown() {
        active = false;
        forceDisable = false;
        reactorDamage = getPostMeltdownDamage();
        burnRemaining = 0;
        partialWaste = 0;
        heatCapacitor.setHeat(HeatAPI.multiplyHeat(HeatAPI.sanitizeTemperature(biomeAmbientTemp), heatCapacitor.getHeatCapacity()));
        lastBurnRate = 0;
        lastBoilRate = 0;
        lastEnvironmentLoss = 0;
        //Match modern meltdown behavior: radioactive contents are dumped by
        //collectRadiationForMeltdown when radiation is enabled. Water, steam, cooled
        //coolant, and otherwise retained fuel survive; heated coolant is always lost.
        heatedCoolantTank.setEmpty();
    }

    private double getTankRadioactivityAndDump(ValidatingGasTank tank) {
        GasStack gas = tank.getGas();
        if (gas != null && gas.getGas() != null && gas.getGas().isRadiation()) {
            double radiation = HeatAPI.multiplyHeat(gas.amount, gas.getGas().getRadioactivity());
            tank.setEmpty();
            return radiation;
        }
        return 0;
    }

    private double getWasteTankRadioactivity(boolean dump) {
        double radiation = 0;
        GasStack waste = wasteTank.getGas();
        if (waste != null && waste.getGas() != null && waste.getGas().isRadiation()) {
            radiation = HeatAPI.multiplyHeat(waste.amount, waste.getGas().getRadioactivity());
            if (dump) {
                wasteTank.setEmpty();
            }
        }
        if (partialWaste > 0 && MekanismFluids.NuclearWaste != null && MekanismFluids.NuclearWaste.isRadiation()) {
            radiation = HeatAPI.addHeatClamped(radiation, HeatAPI.multiplyHeat(partialWaste, MekanismFluids.NuclearWaste.getRadioactivity()));
        }
        return radiation;
    }

    private void radiateFromCore(World world, double magnitude) {
        if (world == null || !HeatAPI.isFinite(magnitude) || magnitude <= 0) {
            return;
        }
        Coord4D center = getReactorCenter();
        if (center != null) {
            MekanismAPI.getRadiationManager().radiate(center, magnitude);
        }
    }

    public void updateDerivedGeometry() {
        reactorCenter = calculateReactorCenter();
        radiationBounds = minLocation == null || maxLocation == null ? null :
              new AxisAlignedBB(minLocation.x + 1, minLocation.y + 1, minLocation.z + 1, maxLocation.x, maxLocation.y, maxLocation.z);
    }

    public Coord4D getReactorCenter() {
        if (reactorCenter == null) {
            reactorCenter = calculateReactorCenter();
        }
        return reactorCenter;
    }

    private Coord4D calculateReactorCenter() {
        if (minLocation != null && maxLocation != null) {
            return new Coord4D((minLocation.x + maxLocation.x) / 2D, (minLocation.y + maxLocation.y) / 2D, (minLocation.z + maxLocation.z) / 2D,
                    minLocation.dimensionId);
        }
        if (renderLocation != null && volLength > 0 && volWidth > 0 && volHeight > 0) {
            int minX = renderLocation.x;
            int minY = renderLocation.y - 1;
            int minZ = renderLocation.z;
            return new Coord4D(minX + (volLength - 1) / 2D, minY + (volHeight - 1) / 2D, minZ + (volWidth - 1) / 2D, renderLocation.dimensionId);
        }
        if (minLocation != null) {
            return minLocation;
        }
        if (renderLocation != null) {
            return renderLocation;
        }
        return locations.isEmpty() ? null : locations.iterator().next();
    }

    AxisAlignedBB getRadiationBounds() {
        if (radiationBounds == null && minLocation != null && maxLocation != null) {
            radiationBounds = new AxisAlignedBB(minLocation.x + 1, minLocation.y + 1, minLocation.z + 1, maxLocation.x, maxLocation.y, maxLocation.z);
        }
        return radiationBounds;
    }

    public boolean shouldPlaySoundAt(BlockPos pos) {
        if (pos == null || minLocation == null || maxLocation == null) {
            return false;
        }
        boolean cornerX = pos.getX() == minLocation.x || pos.getX() == maxLocation.x;
        boolean cornerY = pos.getY() == minLocation.y || pos.getY() == maxLocation.y;
        boolean cornerZ = pos.getZ() == minLocation.z || pos.getZ() == maxLocation.z;
        return cornerX && cornerY && cornerZ;
    }

    public double getEstimatedMeltdownMagnitude() {
        return Math.max(1, HeatAPI.sanitizeHeat(heatCapacitor.getHeat(), 1));
    }

    static double sanitizeDamage(double damage) {
        return HeatAPI.isFinite(damage) ? Math.max(0, Math.min(HeatAPI.MAX_HEAT, damage)) : 0;
    }

    public double getMaxBurnRate() {
        double max = (double) Math.max(0, fuelAssemblies) * getBurnPerAssembly();
        return HeatAPI.isFinite(max) ? Math.min(HeatAPI.MAX_HEAT, max) : HeatAPI.MAX_HEAT;
    }

    public double getBoilEfficiency() {
        if (fuelAssemblies <= 0) {
            return 0;
        }
        double averageSurfaceArea = (double) Math.max(0, surfaceArea) / fuelAssemblies;
        double efficiency = averageSurfaceArea / getBoilEfficiencyTarget();
        return HeatAPI.isFinite(efficiency) ? Math.min(1, Math.max(0, efficiency)) : 1;
    }

    public void setRateLimit(double rate) {
        rateLimit = HeatAPI.isFinite(rate) ? Math.max(0, Math.min(getMaxBurnRate(), rate)) : 0;
    }

    private void setForceDisable(boolean forceDisable) {
        this.forceDisable = forceDisable;
        if (forceDisable) {
            active = false;
        }
    }

    public void syncPrev() {
        prevFuel = syncGasSnapshot(prevFuel, fuelTank.getGas());
        prevWaste = syncGasSnapshot(prevWaste, wasteTank.getGas());
        prevGasCoolant = syncGasSnapshot(prevGasCoolant, gasCoolantTank.getGas());
        prevHeatedCoolant = syncGasSnapshot(prevHeatedCoolant, heatedCoolantTank.getGas());
        prevCoolant = syncFluidSnapshot(prevCoolant, coolantTank.getFluid());
        prevSteam = syncFluidSnapshot(prevSteam, steamTank.getFluid());
        prevTemperature = getTemperature();
        prevDamage = reactorDamage;
        prevActive = active;
    }

    private static GasStack syncGasSnapshot(GasStack cached, GasStack source) {
        if (source == null) {
            return null;
        }
        if (cached != null && cached.isGasEqual(source)) {
            cached.amount = source.amount;
            return cached;
        }
        return source.copy();
    }

    private static FluidStack syncFluidSnapshot(FluidStack cached, FluidStack source) {
        if (source == null) {
            return null;
        }
        if (cached != null && cached.isFluidEqual(source)) {
            cached.amount = source.amount;
            return cached;
        }
        return source.copy();
    }

    public boolean needsRenderUpdate() {
        if (prevActive != active || prevTemperature != getTemperature() || prevDamage != reactorDamage) {
            return true;
        }
        if ((fuelTank.getGas() == null) != (prevFuel == null)) {
            return true;
        }
        if (fuelTank.getGas() != null && (prevFuel == null || !fuelTank.getGas().isGasEqual(prevFuel) || fuelTank.getGas().amount != prevFuel.amount)) {
            return true;
        }
        if ((wasteTank.getGas() == null) != (prevWaste == null)) {
            return true;
        }
        if (wasteTank.getGas() != null && (prevWaste == null || !wasteTank.getGas().isGasEqual(prevWaste) || wasteTank.getGas().amount != prevWaste.amount)) {
            return true;
        }
        if ((gasCoolantTank.getGas() == null) != (prevGasCoolant == null)) {
            return true;
        }
        if (gasCoolantTank.getGas() != null && (prevGasCoolant == null || !gasCoolantTank.getGas().isGasEqual(prevGasCoolant)
                || gasCoolantTank.getGas().amount != prevGasCoolant.amount)) {
            return true;
        }
        if ((heatedCoolantTank.getGas() == null) != (prevHeatedCoolant == null)) {
            return true;
        }
        if (heatedCoolantTank.getGas() != null && (prevHeatedCoolant == null || !heatedCoolantTank.getGas().isGasEqual(prevHeatedCoolant)
                || heatedCoolantTank.getGas().amount != prevHeatedCoolant.amount)) {
            return true;
        }
        if ((coolantTank.getFluid() == null) != (prevCoolant == null)) {
            return true;
        }
        if (coolantTank.getFluid() != null && (prevCoolant == null || coolantTank.getFluid().getFluid() != prevCoolant.getFluid() || coolantTank.getFluid().amount != prevCoolant.amount)) {
            return true;
        }
        if ((steamTank.getFluid() == null) != (prevSteam == null)) {
            return true;
        }
        return steamTank.getFluid() != null && (prevSteam == null || steamTank.getFluid().getFluid() != prevSteam.getFluid() || steamTank.getFluid().amount != prevSteam.amount);
    }

    public static class FormedAssembly {

        public final Coord4D start;
        public final int height;

        public FormedAssembly(Coord4D start, int height) {
            this.start = start;
            this.height = height;
        }

        @Override
        public int hashCode() {
            int result = start.hashCode();
            result = 31 * result + height;
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof FormedAssembly other)) {
                return false;
            }
            return height == other.height && start.equals(other.start);
        }
    }
}
