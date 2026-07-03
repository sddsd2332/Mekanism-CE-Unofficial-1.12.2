package mekanism.generators.common.content.fission;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import mekanism.api.*;
import mekanism.api.gas.GasStack;
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
    public static final double HEAT_PER_BURN = 50;
    public static final double BASE_TEMPERATURE = 300;
    public static final double BOIL_TEMPERATURE = 373;
    public static final double BOIL_EFFICIENCY_TARGET = 4;

    public static final double MIN_DAMAGE_TEMPERATURE = 1_200;
    public static final double MAX_DAMAGE_TEMPERATURE = 1_800;
    public static final double MAX_DAMAGE = 100;
    public static final double MELTDOWN_CHANCE = 0.001;
    public static final double MELTDOWN_RADIATION_MULTIPLIER = 50;
    public static final double POST_MELTDOWN_DAMAGE = 0.75 * MAX_DAMAGE;
    public static final double MELTDOWN_EXPLOSION_CHANCE = 1D / 512_000D;
    private static final double INVERSE_INSULATION_COEFFICIENT = 10_000;
    private static final double INVERSE_CONDUCTION_COEFFICIENT = 10;
    private static final double DEFAULT_WATER_COOLING_CONDUCTIVITY = 0.5;
    private static final double DEFAULT_SODIUM_COOLING_CONDUCTIVITY = 1;
    private static final double DEFAULT_STEAM_ENERGY_EFFICIENCY = 0.2;
    private static final double DEFAULT_SODIUM_THERMAL_ENTHALPY = 0.5;
    // Our 1.12 fission model stores thermal state directly as "temperature", so we apply scale factors to map consumed heat to temp delta.
    private static final double WATER_HEAT_TO_TEMP_SCALE = 0.4;
    private static final double SODIUM_HEAT_TO_TEMP_SCALE = 0.7;

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
    public double casingHeatCapacity = getDefaultCasingHeatCapacity();

    public double rateLimit = getDefaultRateLimit();
    public boolean active;

    public double burnRemaining;
    public double partialWaste;
    public double temperature = BASE_TEMPERATURE;
    public double reactorDamage;
    public boolean forceDisable;

    public long lastBoilRate;
    public double lastBurnRate;
    public double lastEnvironmentLoss;

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
        return MekanismConfig.current().generators != null ? MekanismConfig.current().generators.fissionDefaultBurnRate.val() : DEFAULT_RATE_LIMIT;
    }

    private int getCoolantCapacity() {
        return Math.max(1, volume * COOLANT_PER_VOLUME);
    }

    private int getSteamCapacity() {
        return Math.max(1, volume * STEAM_PER_VOLUME);
    }

    private boolean isValidFluidCoolant(FluidStack stack) {
        return stack.getFluid() == FluidRegistry.WATER && gasCoolantTank.getStored() == 0;
    }

    private boolean isValidGasCoolant(mekanism.api.gas.Gas gas) {
        return gas == MekanismFluids.Sodium && coolantTank.getFluidAmount() == 0;
    }

    private static double getBurnPerAssembly() {
        return MekanismConfig.current().generators != null ? MekanismConfig.current().generators.fissionBurnPerAssembly.val() : BURN_PER_ASSEMBLY;
    }

    private static double getHeatPerBurn() {
        return MekanismConfig.current().generators != null ? MekanismConfig.current().generators.fissionHeatPerBurn.val() : HEAT_PER_BURN;
    }

    private static double getDefaultCasingHeatCapacity() {
        return MekanismConfig.current().generators != null ? MekanismConfig.current().generators.fissionCasingHeatCapacity.val() : 1000D;
    }

    private static double getBoilEfficiencyTarget() {
        return MekanismConfig.current().generators != null ? MekanismConfig.current().generators.fissionSurfaceAreaTarget.val() : BOIL_EFFICIENCY_TARGET;
    }

    private static boolean areMeltdownsEnabled() {
        return MekanismConfig.current().generators == null || MekanismConfig.current().generators.fissionMeltdownsEnabled.val();
    }

    private static double getMeltdownChance() {
        return MekanismConfig.current().generators != null ? MekanismConfig.current().generators.fissionMeltdownChance.val() : MELTDOWN_CHANCE;
    }

    private static double getMeltdownRadiationMultiplier() {
        return MekanismConfig.current().generators != null ? MekanismConfig.current().generators.fissionMeltdownRadiationMultiplier.val() : MELTDOWN_RADIATION_MULTIPLIER;
    }

    private static double getPostMeltdownDamage() {
        return MekanismConfig.current().generators != null ? MekanismConfig.current().generators.fissionPostMeltdownDamage.val() : POST_MELTDOWN_DAMAGE;
    }

    private static double getWaterThermalEnthalpy() {
        if (MekanismConfig.current().general == null) {
            return 0.1;
        }
        double energyPerSteam = MekanismConfig.current().general.maxEnergyPerSteam.val();
        double energyPerHeat = MekanismConfig.current().general.energyPerHeat.val();
        if (energyPerHeat <= 0) {
            return 0.1;
        }
        return energyPerSteam / energyPerHeat;
    }

    private static double getSteamEnergyEfficiency() {
        if (MekanismConfig.current().generators == null) {
            return DEFAULT_STEAM_ENERGY_EFFICIENCY;
        }
        return Math.max(0.000_001D, MekanismConfig.current().generators.fissionSteamEfficiency.val());
    }

    private static double getWaterCoolingConductivity() {
        if (MekanismConfig.current().generators == null) {
            return DEFAULT_WATER_COOLING_CONDUCTIVITY;
        }
        return Math.max(0, MekanismConfig.current().generators.fissionWaterConductivity.val());
    }

    private static double getSodiumCoolingConductivity() {
        if (MekanismConfig.current().generators == null) {
            return DEFAULT_SODIUM_COOLING_CONDUCTIVITY;
        }
        return Math.max(0, MekanismConfig.current().generators.fissionSodiumConductivity.val());
    }

    private static double getSodiumThermalEnthalpy() {
        if (MekanismConfig.current().generators == null) {
            return DEFAULT_SODIUM_THERMAL_ENTHALPY;
        }
        return Math.max(0.000_001D, MekanismConfig.current().generators.fissionSodiumThermalEnthalpy.val());
    }

    public void tick(World world) {
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
        if (!locations.isEmpty()) {
            casingHeatCapacity = getDefaultCasingHeatCapacity() * locations.size();
        }
        int fuelCapacity = Math.max(1, fuelAssemblies * FUEL_PER_ASSEMBLY);
        int heatedCoolantCapacity = Math.max(1, volume * HEATED_COOLANT_PER_VOLUME);
        fuelTank.setMaxGas(fuelCapacity);
        wasteTank.setMaxGas(fuelCapacity);
        gasCoolantTank.setMaxGas(getCoolantCapacity());
        heatedCoolantTank.setMaxGas(heatedCoolantCapacity);
        clampTank(fuelTank);
        clampTank(wasteTank);
        clampTank(heatedCoolantTank);
        clampTank(steamTank);
        clampCoolantTanks();

        rateLimit = Math.max(0, Math.min(getMaxBurnRate(), rateLimit));
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

    private void burnFuel(World world) {
        if (fuelTank.getStored() <= 0) {
            lastBurnRate = 0;
            return;
        }

        double availableFuel = fuelTank.getStored() + burnRemaining;
        double toBurn = Math.min(Math.min(rateLimit, getMaxBurnRate()), availableFuel);
        if (toBurn <= 0) {
            lastBurnRate = 0;
            return;
        }

        availableFuel -= toBurn;
        int remainingFuel = Math.max(0, (int) Math.floor(availableFuel));
        burnRemaining = Math.max(0, availableFuel - remainingFuel);
        fuelTank.setStackSize(remainingFuel, Action.EXECUTE);

        temperature += toBurn * getHeatPerBurn();

        partialWaste += toBurn;
        int wasteToAdd = (int) Math.floor(partialWaste);
        if (wasteToAdd > 0) {
            partialWaste -= wasteToAdd;
            GasStack waste = new GasStack(MekanismFluids.NuclearWaste, wasteToAdd);
            GasStack remainder = wasteTank.insert(waste, Action.EXECUTE, AutomationType.INTERNAL);
            int accepted = waste.amount - (remainder == null ? 0 : remainder.amount);
            int leftoverWaste = Math.max(0, wasteToAdd - accepted);
            if (leftoverWaste > 0 && waste.getGas() != null && waste.getGas().isRadiation()) {
                radiateFromCore(world, leftoverWaste * waste.getGas().getRadioactivity());
            }
        }
        lastBurnRate = toBurn;
    }

    private void handleCoolant() {
        if (temperature <= BOIL_TEMPERATURE) {
            lastBoilRate = 0;
            return;
        }

        double boilEfficiency = getBoilEfficiency();
        double availableHeat = (temperature - BOIL_TEMPERATURE) * boilEfficiency;
        if (availableHeat <= 0) {
            lastBoilRate = 0;
            return;
        }

        if (coolantTank.getFluidAmount() > 0 && gasCoolantTank.getStored() == 0) {
            double caseCoolantHeat = availableHeat * getWaterCoolingConductivity();
            double waterThermalEnthalpy = getWaterThermalEnthalpy();
            double steamEnergyEfficiency = getSteamEnergyEfficiency();
            if (waterThermalEnthalpy <= 0 || steamEnergyEfficiency <= 0) {
                lastBoilRate = 0;
                return;
            }
            int toBoil = (int) Math.floor(steamEnergyEfficiency * caseCoolantHeat / waterThermalEnthalpy);
            toBoil = Math.min(toBoil, coolantTank.getFluidAmount());
            if (toBoil <= 0) {
                lastBoilRate = 0;
                return;
            }

            coolantTank.extract(toBoil, Action.EXECUTE, AutomationType.INTERNAL);
            if (FluidRegistry.getFluid("steam") != null) {
                // Align 1.16 behavior: excess output is treated as loss and does not block cooling.
                steamTank.insert(new FluidStack(FluidRegistry.getFluid("steam"), toBoil), Action.EXECUTE, AutomationType.INTERNAL);
            }
            double consumedHeat = toBoil * waterThermalEnthalpy / steamEnergyEfficiency;
            temperature = Math.max(BASE_TEMPERATURE, temperature - consumedHeat * WATER_HEAT_TO_TEMP_SCALE);
            lastBoilRate = toBoil;
            return;
        }

        if (gasCoolantTank.getStored() > 0 && coolantTank.getFluidAmount() == 0) {
            double caseCoolantHeat = availableHeat * getSodiumCoolingConductivity();
            double sodiumThermalEnthalpy = getSodiumThermalEnthalpy();
            if (sodiumThermalEnthalpy <= 0) {
                lastBoilRate = 0;
                return;
            }
            int toHeat = (int) Math.floor(caseCoolantHeat / sodiumThermalEnthalpy);
            toHeat = Math.min(toHeat, gasCoolantTank.getStored());
            if (toHeat <= 0) {
                lastBoilRate = 0;
                return;
            }

            gasCoolantTank.extract(toHeat, Action.EXECUTE, AutomationType.INTERNAL);
            // Align 1.16 behavior: excess heated coolant is treated as loss and does not block cooling.
            heatedCoolantTank.insert(new GasStack(MekanismFluids.SuperheatedSodium, toHeat), Action.EXECUTE, AutomationType.INTERNAL);
            double consumedHeat = toHeat * sodiumThermalEnthalpy;
            temperature = Math.max(BASE_TEMPERATURE, temperature - consumedHeat * SODIUM_HEAT_TO_TEMP_SCALE);
            lastBoilRate = toHeat;
            return;
        }

        lastBoilRate = 0;
    }

    private void dissipateHeat() {
        double invConduction = IHeatTransfer.AIR_INVERSE_COEFFICIENT + INVERSE_INSULATION_COEFFICIENT + INVERSE_CONDUCTION_COEFFICIENT;
        double tempToTransfer = (temperature - BASE_TEMPERATURE) / invConduction;
        temperature -= tempToTransfer;
        if (temperature < 0) {
            temperature = 0;
        }
        lastEnvironmentLoss = Math.max(tempToTransfer, 0);
    }

    private void handleDamage() {
        if (temperature > MIN_DAMAGE_TEMPERATURE) {
            double damageRate = Math.min(temperature, MAX_DAMAGE_TEMPERATURE) / (MIN_DAMAGE_TEMPERATURE * 10);
            reactorDamage = Math.min(MAX_DAMAGE, reactorDamage + damageRate);
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
        AxisAlignedBB hotZone = new AxisAlignedBB(minLocation.x + 1, minLocation.y + 1, minLocation.z + 1, maxLocation.x, maxLocation.y, maxLocation.z);
        Iterable<EntityLivingBase> entities = world.getEntitiesWithinAABB(EntityLivingBase.class, hotZone);
        double wasteRadiation = getWasteTankRadioactivity(false) / 3_600D;
        double magnitude = lastBurnRate + wasteRadiation;
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
        if (temperature < MIN_DAMAGE_TEMPERATURE || reactorDamage < MAX_DAMAGE) {
            return false;
        }
        if (!areMeltdownsEnabled()) {
            setForceDisable(true);
            return false;
        }
        if (isForceDisabled()) {
            // If meltdowns were disabled before and now re-enabled, trigger immediately while still critical.
            setForceDisable(false);
            return true;
        }
        return random.nextDouble() < (reactorDamage / MAX_DAMAGE) * getMeltdownChance();
    }

    public double collectRadiationForMeltdown() {
        double radiation = getTankRadioactivityAndDump(fuelTank) + getWasteTankRadioactivity(true);
        radiation += getTankRadioactivityAndDump(gasCoolantTank) + getTankRadioactivityAndDump(heatedCoolantTank);
        return radiation * getMeltdownRadiationMultiplier();
    }

    public void onMeltdown() {
        active = false;
        forceDisable = false;
        reactorDamage = getPostMeltdownDamage();
        burnRemaining = 0;
        partialWaste = 0;
        temperature = BASE_TEMPERATURE;
        lastBurnRate = 0;
        lastBoilRate = 0;
        lastEnvironmentLoss = 0;
        clearStoredTanks();
    }

    public void clearStoredTanks() {
        fuelTank.setEmpty();
        wasteTank.setEmpty();
        gasCoolantTank.setEmpty();
        heatedCoolantTank.setEmpty();
        coolantTank.setEmpty();
        steamTank.setEmpty();
    }

    private double getTankRadioactivityAndDump(ValidatingGasTank tank) {
        GasStack gas = tank.getGas();
        if (gas != null && gas.getGas() != null && gas.getGas().isRadiation()) {
            double radiation = gas.amount * gas.getGas().getRadioactivity();
            tank.setEmpty();
            return radiation;
        }
        return 0;
    }

    private double getWasteTankRadioactivity(boolean dump) {
        double radiation = 0;
        GasStack waste = wasteTank.getGas();
        if (waste != null && waste.getGas() != null && waste.getGas().isRadiation()) {
            radiation += waste.amount * waste.getGas().getRadioactivity();
            if (dump) {
                wasteTank.setEmpty();
            }
        }
        if (partialWaste > 0 && MekanismFluids.NuclearWaste != null && MekanismFluids.NuclearWaste.isRadiation()) {
            radiation += partialWaste * MekanismFluids.NuclearWaste.getRadioactivity();
        }
        return radiation;
    }

    private void radiateFromCore(World world, double magnitude) {
        if (world == null || magnitude <= 0) {
            return;
        }
        Coord4D center = getReactorCenter();
        if (center != null) {
            MekanismAPI.getRadiationManager().radiate(center, magnitude);
        }
    }

    private Coord4D getReactorCenter() {
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

    public boolean shouldPlaySoundAt(BlockPos pos) {
        if (pos == null || minLocation == null || maxLocation == null) {
            return true;
        }
        boolean cornerX = pos.getX() == minLocation.x || pos.getX() == maxLocation.x;
        boolean cornerY = pos.getY() == minLocation.y || pos.getY() == maxLocation.y;
        boolean cornerZ = pos.getZ() == minLocation.z || pos.getZ() == maxLocation.z;
        return cornerX && cornerY && cornerZ;
    }

    public double getEstimatedMeltdownMagnitude() {
        int shellSize = Math.max(1, locations.size());
        double deltaTemp = Math.max(0, temperature - BASE_TEMPERATURE);
        return Math.max(1, deltaTemp * shellSize);
    }

    public double getMaxBurnRate() {
        return fuelAssemblies * getBurnPerAssembly();
    }

    public double getBoilEfficiency() {
        if (fuelAssemblies <= 0) {
            return 0;
        }
        double averageSurfaceArea = (double) surfaceArea / fuelAssemblies;
        return Math.min(1, averageSurfaceArea / getBoilEfficiencyTarget());
    }

    public void setRateLimit(double rate) {
        rateLimit = Math.max(0, Math.min(getMaxBurnRate(), rate));
    }

    private void setForceDisable(boolean forceDisable) {
        this.forceDisable = forceDisable;
        if (forceDisable) {
            active = false;
        }
    }

    public void syncPrev() {
        prevFuel = fuelTank.getGas() == null ? null : fuelTank.getGas().copy();
        prevWaste = wasteTank.getGas() == null ? null : wasteTank.getGas().copy();
        prevGasCoolant = gasCoolantTank.getGas() == null ? null : gasCoolantTank.getGas().copy();
        prevHeatedCoolant = heatedCoolantTank.getGas() == null ? null : heatedCoolantTank.getGas().copy();
        prevCoolant = coolantTank.getFluid() == null ? null : coolantTank.getFluid().copy();
        prevSteam = steamTank.getFluid() == null ? null : steamTank.getFluid().copy();
        prevTemperature = temperature;
        prevDamage = reactorDamage;
        prevActive = active;
    }

    public boolean needsRenderUpdate() {
        if (prevActive != active || prevTemperature != temperature || prevDamage != reactorDamage) {
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
