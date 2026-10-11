package mekanism.common.config;

import mekanism.common.config.options.BooleanOption;
import mekanism.common.config.options.DoubleOption;
import mekanism.common.config.options.IntOption;

/**
 * Created by Thiakil on 15/03/2019.
 */
public class UsageConfig extends BaseConfig {

    public final BooleanOption randomizedConsumption = new BooleanOption(this, "RandomizedConsumption", true,
            "Allow statistical secondary gas/fluid usage. QIO processing and AE upgrades always force exact usage on the machine.");

    public final DoubleOption enrichmentChamber = new DoubleOption(this, "EnrichmentChamberUsage", 50D,
            "Energy per operation tick (Joules).");

    public final DoubleOption osmiumCompressor = new DoubleOption(this, "OsmiumCompressorUsage", 100D,
            "Energy per operation tick (Joules).");

    public final DoubleOption combiner = new DoubleOption(this, "CombinerUsage", 50D,
            "Energy per operation tick (Joules).");

    public final DoubleOption crusher = new DoubleOption(this, "CrusherUsage", 50D,
            "Energy per operation tick (Joules).");

    public final DoubleOption metallurgicInfuser = new DoubleOption(this, "MetallurgicInfuserUsage", 50D,
            "Energy per operation tick (Joules).");

    public final DoubleOption purificationChamber = new DoubleOption(this, "PurificationChamberUsage", 200D,
            "Energy per operation tick (Joules).");

    public final DoubleOption energizedSmelter = new DoubleOption(this, "EnergizedSmelterUsage", 50D,
            "Energy per operation tick (Joules).");

    public final DoubleOption digitalMiner = new DoubleOption(this, "DigitalMinerUsage", 100D,
            "Energy per operation tick (Joules).");

    public final DoubleOption electricPump = new DoubleOption(this, "ElectricPumpUsage", 100D,
            "Energy per operation tick (Joules).");

    public final DoubleOption rotaryCondensentrator = new DoubleOption(this, "RotaryCondensentratorUsage", 50D,
            "Energy per operation tick (Joules).");

    public final DoubleOption oxidationChamber = new DoubleOption(this, "OxidationChamberUsage", 200D,
            "Energy per operation tick (Joules).");

    public final DoubleOption chemicalInfuser = new DoubleOption(this, "ChemicalInfuserUsage", 200D,
            "Energy per operation tick (Joules).");

    public final DoubleOption chemicalInjectionChamber = new DoubleOption(this, "ChemicalInjectionChamberUsage", 400D,
            "Energy per operation tick (Joules).");

    public final DoubleOption precisionSawmill = new DoubleOption(this, "PrecisionSawmillUsage", 50D,
            "Energy per operation tick (Joules).");

    public final DoubleOption chemicalDissolutionChamber = new DoubleOption(this, "ChemicalDissolutionChamberUsage", 400D,
            "Energy per operation tick (Joules).");

    public final DoubleOption chemicalWasher = new DoubleOption(this, "ChemicalWasherUsage", 200D,
            "Energy per operation tick (Joules).");

    public final DoubleOption chemicalCrystallizer = new DoubleOption(this, "ChemicalCrystallizerUsage", 400D,
            "Energy per operation tick (Joules).");

    public final DoubleOption seismicVibrator = new DoubleOption(this, "SeismicVibratorUsage", 50D,
            "Energy per operation tick (Joules).");

    public final DoubleOption pressurizedReactionBase = new DoubleOption(this, "PressurizedReactionBaseUsage", 5D,
            "Energy per operation tick (Joules).");

    public final DoubleOption fluidicPlenisher = new DoubleOption(this, "FluidicPlenisherUsage", 100D,
            "Energy per operation tick (Joules).");

    public final DoubleOption laser = new DoubleOption(this, "LaserUsage", 5000D,
            "Energy per operation tick (Joules).");

    public final DoubleOption heavyWaterElectrolysis = new DoubleOption(this, "HeavyWaterElectrolysisUsage", 800D,
            "Energy needed for one [recipe unit] of heavy water production (Joules).");

    public final DoubleOption formulaicAssemblicator = new DoubleOption(this, "FormulaicAssemblicatorUsage", 100D,
            "Energy per operation tick (Joules).");

    public final IntOption teleporterBase = new IntOption(this, "TeleporterBaseUsage", 1000,
            "Base Joules cost for a teleportation.");

    public final IntOption teleporterDistance = new IntOption(this, "TeleporterDistanceUsage", 10,
            "Joules per unit of distance travelled during teleportation - sqrt(xDiff^2 + yDiff^2 + zDiff^2).");

    public final IntOption teleporterDimensionPenalty = new IntOption(this, "TeleporterDimensionPenalty", 10000,
            "Flat additional cost for interdimensional teleportation.");

    /**
     * Add Start
     */
    public final DoubleOption isotopicCentrifuge = new DoubleOption(this, "isotopicCentrifuge", 200D,
            "Energy per operation tick (Joules).");

    public final DoubleOption liquifierNutritional = new DoubleOption(this, "OxidationChamberUsage", 200D,
            "Energy per operation tick (Joules).");

    public final DoubleOption organicfarm = new DoubleOption(this, "OrganicFarmUsage", 50D,
            "Energy per operation tick (Joules).");

    public final DoubleOption nucleosynthesizer = new DoubleOption(this, "nucleosynthesizerUsage", 200D,
            "Energy per operation tick (Joules).");

    public final DoubleOption stamping = new DoubleOption(this, "StampingUsage", 50D,
            "Energy per operation tick (Joules).");

    public final DoubleOption rolling = new DoubleOption(this, "RollingUsage", 50D,
            "Energy per operation tick (Joules).");

    public final DoubleOption brushed = new DoubleOption(this, "BrushedUsage", 50D,
            "Energy per operation tick (Joules).");

    public final DoubleOption turning = new DoubleOption(this, "TurningUsage", 50D,
            "Energy per operation tick (Joules).");

    public final DoubleOption alloy = new DoubleOption(this, "AlloyUsage", 50D,
            "Energy per operation tick (Joules).");

    public final DoubleOption cellExtractor = new DoubleOption(this, "CellExtractorUsage", 50D,
            "Energy per operation tick (Joules).");

    public final DoubleOption cellSeparator = new DoubleOption(this, "CellSeparatorUsage", 50D,
            "Energy per operation tick (Joules).");

    public final DoubleOption recycler = new DoubleOption(this, "RecyclerUsage", 50D,
            "Energy per operation tick (Joules).");

    public final DoubleOption AmbientAccumulatorEnergy = new DoubleOption(this, "AmbientAccumulatorEnergyUsage", 200D,
            "Energy per operation tick (Joules).");

    public final DoubleOption modificationStation = new DoubleOption(this, "modificationStationUsage", 100D,
            "Energy per operation tick (Joules).");

    @Override
    public String getCategory() {
        return "usage";
    }
}
