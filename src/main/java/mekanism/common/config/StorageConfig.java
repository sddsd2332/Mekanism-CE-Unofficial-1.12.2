package mekanism.common.config;

import mekanism.common.config.options.DoubleOption;

public class StorageConfig extends BaseConfig {

    public final DoubleOption enrichmentChamber = new DoubleOption(this, "EnrichmentChamberStorage", 20000D,
            "Base energy storage (Joules).");

    public final DoubleOption osmiumCompressor = new DoubleOption(this, "OsmiumCompressorStorage", 80000D,
            "Base energy storage (Joules).");

    public final DoubleOption combiner = new DoubleOption(this, "CombinerStorage", 40000D,
            "Base energy storage (Joules).");

    public final DoubleOption crusher = new DoubleOption(this, "CrusherStorage", 20000D,
            "Base energy storage (Joules).");

    public final DoubleOption metallurgicInfuser = new DoubleOption(this, "MetallurgicInfuserStorage", 20000D,
            "Base energy storage (Joules).");

    public final DoubleOption purificationChamber = new DoubleOption(this, "PurificationChamberStorage", 80000D,
            "Base energy storage (Joules).");

    public final DoubleOption energizedSmelter = new DoubleOption(this, "EnergizedSmelterStorage", 20000D,
            "Base energy storage (Joules).");

    public final DoubleOption digitalMiner = new DoubleOption(this, "DigitalMinerStorage", 40000D,
            "Base energy storage (Joules).");

    public final DoubleOption electricPump = new DoubleOption(this, "ElectricPumpStorage", 40000D,
            "Base energy storage (Joules).");

    public final DoubleOption chargePad = new DoubleOption(this, "ChargePadStorage", 40000D,
            "Base energy storage (Joules).");

    public final DoubleOption rotaryCondensentrator = new DoubleOption(this, "RotaryCondensentratorStorage", 20000D,
            "Base energy storage (Joules).");

    public final DoubleOption oxidationChamber = new DoubleOption(this, "OxidationChamberStorage", 80000D,
            "Base energy storage (Joules).");

    public final DoubleOption chemicalInfuser = new DoubleOption(this, "ChemicalInfuserStorage", 80000D,
            "Base energy storage (Joules).");

    public final DoubleOption chemicalInjectionChamber = new DoubleOption(this, "ChemicalInjectionChamberStorage", 160000D,
            "Base energy storage (Joules).");

    public final DoubleOption electrolyticSeparator = new DoubleOption(this, "ElectrolyticSeparatorStorage", 160000D,
            "Base energy storage (Joules).");

    public final DoubleOption precisionSawmill = new DoubleOption(this, "PrecisionSawmillStorage", 20000D,
            "Base energy storage (Joules).");

    public final DoubleOption chemicalDissolutionChamber = new DoubleOption(this, "ChemicalDissolutionChamberStorage", 160000D,
            "Base energy storage (Joules).");

    public final DoubleOption chemicalWasher = new DoubleOption(this, "ChemicalWasherStorage", 80000D,
            "Base energy storage (Joules).");

    public final DoubleOption chemicalCrystallizer = new DoubleOption(this, "ChemicalCrystallizerStorage", 160000D,
            "Base energy storage (Joules).");

    public final DoubleOption seismicVibrator = new DoubleOption(this, "SeismicVibratorStorage", 20000D,
            "Base energy storage (Joules).");

    public final DoubleOption pressurizedReactionBase = new DoubleOption(this, "PressurizedReactionBaseStorage", 2000D,
            "Base energy storage (Joules).");

    public final DoubleOption fluidicPlenisher = new DoubleOption(this, "FluidicPlenisherStorage", 40000D,
            "Base energy storage (Joules).");

    public final DoubleOption laser = new DoubleOption(this, "LaserStorage", 2000000D,
            "Base energy storage (Joules).");

    public final DoubleOption formulaicAssemblicator = new DoubleOption(this, "FormulaicAssemblicatorStorage", 40000D,
            "Base energy storage (Joules).");

    public final DoubleOption teleporter = new DoubleOption(this, "TeleporterStorage", 5000000D,
            "Base energy storage (Joules).");

    /**
     * Add Start
     */
    public final DoubleOption isotopicCentrifuge = new DoubleOption(this, "isotopicCentrifuge", 80000D,
            "Base energy storage (Joules).");

    public final DoubleOption liquifierNutritional = new DoubleOption(this, "liquifierNutritionalStorage", 80000D,
            "Base energy storage (Joules).");

    public final DoubleOption organicfarm = new DoubleOption(this, "OrganicFarmStorage", 20000D,
            "Base energy storage (Joules).");

    public final DoubleOption nucleosynthesizer = new DoubleOption(this, "nucleosynthesizerStorage", 80000D,
            "Base energy storage (Joules).");

    public final DoubleOption stamping = new DoubleOption(this, "StampingStorage", 20000D,
            "Base energy storage (Joules).");

    public final DoubleOption rolling = new DoubleOption(this, "RollingStorage", 20000D,
            "Base energy storage (Joules).");

    public final DoubleOption brushed = new DoubleOption(this, "BrushedStorage", 20000D,
            "Base energy storage (Joules).");

    public final DoubleOption turning = new DoubleOption(this, "TurningStorage", 20000D,
            "Base energy storage (Joules).");

    public final DoubleOption alloy = new DoubleOption(this, "AlloyStorage", 40000D,
            "Base energy storage (Joules).");

    public final DoubleOption cellExtractor = new DoubleOption(this, "CellExtractorStorage", 20000D,
            "Base energy storage (Joules).");

    public final DoubleOption cellSeparator = new DoubleOption(this, "CellSeparatorStorage", 20000D,
            "Base energy storage (Joules).");

    public final DoubleOption recycler = new DoubleOption(this, "RecyclerStorage", 20000D,
            "Base energy storage (Joules).");

    public final DoubleOption AmbientAccumulatorEnergy = new DoubleOption(this, "AmbientAccumulatorEnergyStorage", 80000D,
            "Base energy storage (Joules).");

    public final DoubleOption HybridStorageEnergy = new DoubleOption(this, "HybridStorageEnergy", 128000000D,
            "Base energy storage (Joules).");

    public final DoubleOption modificationStation = new DoubleOption(this, "modificationStationEnergy", 40000D,
            "Base energy storage (Joules).");


    @Override
    public String getCategory() {
        return "storage";
    }
}
