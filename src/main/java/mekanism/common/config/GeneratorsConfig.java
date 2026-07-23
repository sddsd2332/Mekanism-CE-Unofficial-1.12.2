package mekanism.common.config;

import io.netty.buffer.ByteBuf;
import mekanism.common.config.options.BooleanOption;
import mekanism.common.config.options.DoubleOption;
import mekanism.common.config.options.IntOption;
import mekanism.common.config.options.IntSetOption;
import mekanism.generators.common.block.states.BlockStateGenerator.GeneratorType;
import net.minecraftforge.common.config.Configuration;

/**
 * Created by Thiakil on 15/03/2019.
 */
public class GeneratorsConfig extends BaseConfig {

    public final DoubleOption advancedSolarGeneration = new DoubleOption(this,  "AdvancedSolarGeneration", 300D,
            "Peak output for the Advanced Solar Generator. Note: It can go higher than this value in some extreme environments.");

    public final DoubleOption bioGeneration = new DoubleOption(this,  "BioGeneration", 350D,
            "Amount of energy in Joules the Bio Generator produces per tick.");

    public final DoubleOption heatGeneration = new DoubleOption(this,  "HeatGeneration", 100D,
            "Amount of heat the Heat Generator produces per tick while burning lava.", 0D, Integer.MAX_VALUE);

    public final DoubleOption heatGenerationLava = new DoubleOption(this,  "HeatGenerationLava", 7D,
            "Heat produced per adjacent lava side by the Heat Generator.", 0D, Integer.MAX_VALUE / 7D);

    public final DoubleOption heatGenerationNether = new DoubleOption(this,  "HeatGenerationNether", 10D,
            "Additional heat produced by a Heat Generator in the Nether.", 0D, Integer.MAX_VALUE);

    public final IntOption heatTankCapacity = new IntOption(this, "HeatGeneratorTankCapacity", 1_000,
            "Lava tank capacity of the Heat Generator.", 1, Integer.MAX_VALUE);

    public final IntOption heatGenerationFluidRate = new IntOption(this, "HeatGenerationFluidRate", 100,
            "Lava consumed per tick to produce the active Heat Generator heat.", 1, Integer.MAX_VALUE);

    public final DoubleOption solarGeneration = new DoubleOption(this,  "SolarGeneration", 50D,
            "Peak output for the Solar Generator. Note: It can go higher than this value in some extreme environments.");

    public final IntOption turbineBladesPerCoil = new IntOption(this,  "TurbineBladesPerCoil", 4,
            "The number of blades on each turbine coil per blade applied.");

    public final DoubleOption turbineVentGasFlow = new DoubleOption(this,  "TurbineVentGasFlow", 16000D,
            "The rate at which steam is vented into the turbine.");

    public final DoubleOption turbineDisperserGasFlow = new DoubleOption(this,  "TurbineDisperserGasFlow", 640D,
            "The rate at which steam is dispersed into the turbine.");

    public final IntOption condenserRate = new IntOption(this,  "TurbineCondenserFlowRate", 32000,
            "The rate at which steam is condensed in the turbine.");

    public final DoubleOption energyPerFusionFuel = new DoubleOption(this,  "EnergyPerFusionFuel", 10_000_000D,
            "Affects the Injection Rate, Max Temp, and Ignition Temp.", 0D, Integer.MAX_VALUE);

    public final DoubleOption fusionThermocoupleEfficiency = new DoubleOption(this, "FusionThermocoupleEfficiency", 0.05D,
            "Fraction of fusion casing heat dissipated to air that is converted to Joules.", 0D, 1D);

    public final DoubleOption fusionCasingThermalConductivity = new DoubleOption(this, "FusionCasingThermalConductivity", 0.1D,
            "Fraction of fusion casing heat transferred to non-water heat sinks.", 0.001D, 1D);

    public final DoubleOption fusionWaterHeatingRatio = new DoubleOption(this, "FusionWaterHeatingRatio", 0.3D,
            "Fraction of fusion casing heat transferred to water while actively cooled.", 0D, 1D);

    public final DoubleOption windGenerationMin = new DoubleOption(this,  "WindGenerationMin", 60D,
            "Minimum base generation value of the Wind Generator.");

    public final DoubleOption windGenerationMax = new DoubleOption(this,  "WindGenerationMax", 480D,
            "Maximum base generation value of the Wind Generator.");

    public final IntOption windGenerationMinY = new IntOption(this,  "WindGenerationMinY", 24,
            "The minimum Y value that affects the Wind Generators Power generation.");

    public final IntOption windGenerationMaxY = new IntOption(this,  "WindGenerationMaxY", 255,
            "The maximum Y value that affects the Wind Generators Power generation.");

    public final IntSetOption windGenerationDimBlacklist = new IntSetOption(this,  "WindGenerationDimBlacklist", new int[0],
            "The list of dimension ids that the Wind Generator will not generate power in.").setRequiresWorldRestart();

    public final DoubleOption advancedSolarGeneratorStorage = new DoubleOption(this,
            "AdvancedSolarGeneratorStorage", 200000D, "Energy capable of being stored");
    public final DoubleOption bioGeneratorStorage = new DoubleOption(this,
            "BioGeneratorStorage", 160000D, "Energy capable of being stored");
    public final DoubleOption heatGeneratorStorage = new DoubleOption(this,
            "HeatGeneratorStorage", 240D, "Energy capable of being stored");
    public final DoubleOption solarGeneratorStorage = new DoubleOption(this,
            "SolarGeneratorStorage", 96000D, "Energy capable of being stored");
    public final DoubleOption windGeneratorStorage = new DoubleOption(this,
            "WindGeneratorStorage", 96000D, "Energy capable of being stored");
    public final DoubleOption turbineGeneratorStorage = new DoubleOption(this,
            "TurbineGeneratorStorage", 16000000D, "How much energy per volume, default 16MJ");
    public final DoubleOption reactorGeneratorStorage = new DoubleOption(this,
            "ReactorGeneratorStorage", 1000000000D, "Energy capable of being stored");

    public final IntOption reactorGeneratorInjectionRate = new IntOption(this,
            "reactorGeneratorInjectionRate", 98, "The maximum injection rate of the fusion reactor needs to be set to a multiple of 2",2,Integer.MAX_VALUE);


    public final IntOption ItemHohlraumMaxGas = new IntOption(this,  "ItemHohlraumMaxGas", 10, "How many gases can be added to Hohlraum",1,Integer.MAX_VALUE);

    public final IntOption FusionReactorsDeuteriumTank = new IntOption(this, "FusionReactorsDeuteriumTank",1000);
    public final IntOption FusionReactorsTritiumTank = new IntOption(this, "FusionReactorsTritiumTank",1000);
    public final IntOption FusionReactorsFuelTank = new IntOption(this, "FusionReactorsFuelTank",1000);
    public final IntOption FusionReactorsWaterTank  = new IntOption(this, "FusionReactorsWaterTank",1_000_000);
    public final IntOption FusionReactorsSteamTank  = new IntOption(this, "FusionReactorsSteamTank",100_000_000);

    public final DoubleOption energyPerFissionFuel = new DoubleOption(this, "EnergyPerFissionFuel", 1_000_000D,
            "Heat energy produced by each mB of fissile fuel burned.", 0D, Integer.MAX_VALUE);
    public final DoubleOption fissionCasingHeatCapacity = new DoubleOption(this, "FissionCasingHeatCapacity", 1000D,
            "Heat capacity contribution of each formed fission reactor casing block.", 1D, 1000000D);
    public final DoubleOption fissionSurfaceAreaTarget = new DoubleOption(this, "FissionSurfaceAreaTarget", 4D,
            "Average fuel assembly surface area required for 100% boiling efficiency.", 1D, Double.MAX_VALUE);
    public final BooleanOption fissionMeltdownsEnabled = new BooleanOption(this, "FissionMeltdownsEnabled", true,
            "If disabled, reactor will force shutdown at critical damage instead of melting down.");
    public final DoubleOption fissionMeltdownChance = new DoubleOption(this, "FissionMeltdownChance", 0.001D,
            "Base per-tick meltdown chance once critical damage is reached.", 0D, 1D);
    public final DoubleOption fissionMeltdownRadiationMultiplier = new DoubleOption(this, "FissionMeltdownRadiationMultiplier", 50D,
            "Multiplier applied to dumped radioactive contents when meltdown happens.", 0D, Double.MAX_VALUE);
    public final DoubleOption fissionPostMeltdownDamage = new DoubleOption(this, "FissionPostMeltdownDamage", 75D,
            "Reactor damage level after a meltdown.", 0D, 100D);
    public final DoubleOption fissionDefaultBurnRate = new DoubleOption(this, "FissionDefaultBurnRate", 0.1D,
            "Default burn rate used when a fission multiblock is formed.", 0.001D, 1D);
    public final DoubleOption fissionBurnPerAssembly = new DoubleOption(this, "FissionBurnPerAssembly", 1D,
            "Max burn rate contribution provided by each fuel assembly.", 1D, 1_000_000D);
    public final IntOption fissionFuelPerAssembly = new IntOption(this, "FissionFuelPerAssembly", 8_000,
            "Fuel and waste capacity provided by each fission fuel assembly.", 1, Integer.MAX_VALUE);
    public final IntOption fissionCooledCoolantPerTank = new IntOption(this, "FissionCooledCoolantPerTank", 100_000,
            "Water or cooled sodium capacity provided by each fission reactor volume block.", 1, Integer.MAX_VALUE);
    public final IntOption fissionHeatedCoolantPerTank = new IntOption(this, "FissionHeatedCoolantPerTank", 1_000_000,
            "Steam or heated sodium capacity provided by each fission reactor volume block.", 1, Integer.MAX_VALUE);
    public final DoubleOption fissionWaterConductivity = new DoubleOption(this, "FissionWaterConductivity", 0.5D,
            "Water coolant conductivity multiplier used by fission reactor cooling.", 0D, 1D);
    public final DoubleOption fissionSodiumConductivity = new DoubleOption(this, "FissionSodiumConductivity", 1D,
            "Sodium coolant conductivity multiplier used by fission reactor cooling.", 0D, 1D);
    public final DoubleOption fissionSteamEfficiency = new DoubleOption(this, "FissionSteamEfficiency", 0.2D,
            "Steam conversion efficiency used by fission water cooling.", 0.000_001D, 1D);
    public final DoubleOption fissionSodiumThermalEnthalpy = new DoubleOption(this, "FissionSodiumThermalEnthalpy", 5D,
            "Thermal enthalpy used for sodium heating conversion in fission reactors.", 0.000_001D, Double.MAX_VALUE);

    public TypeConfigManager<GeneratorType> generatorsManager = new TypeConfigManager<>(this, "generators", GeneratorType.class,
            GeneratorType::getGeneratorsForConfig, GeneratorType::getBlockName);

    @Override
    public void load(Configuration config) {
        super.load(config);
        validate();
    }

    @Override
    public void read(ByteBuf config) {
        super.read(config);
        validate();
    }

    private void validate() {
        //ensure windGenerationMaxY is > windGenerationMinY
        windGenerationMaxY.set(Math.max(windGenerationMinY.val() + 1, windGenerationMaxY.val()));
        int toUse = reactorGeneratorInjectionRate.val();
        toUse -= toUse % 2;
        reactorGeneratorInjectionRate.set(toUse);
        heatTankCapacity.set(Math.max(1, heatTankCapacity.val()));
        heatGenerationFluidRate.set(Math.max(1, Math.min(heatTankCapacity.val(), heatGenerationFluidRate.val())));
    }

    @Override
    public String getCategory() {
        return "generation";
    }
}
