package mekanism.common.block.states;

import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import mekanism.common.Mekanism;
import mekanism.common.MekanismBlocks;
import mekanism.common.base.IBlockType;
import mekanism.common.base.IFactory.RecipeType;
import mekanism.common.block.BlockMachine;
import mekanism.common.config.MekanismConfig;
import mekanism.common.tier.BaseTier;
import mekanism.common.tier.FactoryTier;
import mekanism.common.tile.*;
import mekanism.common.tile.factory.*;
import mekanism.common.tile.laser.TileEntityLaser;
import mekanism.common.tile.laser.TileEntityLaserAmplifier;
import mekanism.common.tile.laser.TileEntityLaserTractorBeam;
import mekanism.common.tile.machine.*;
import mekanism.common.util.LangUtils;
import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.block.statemap.StateMapperBase;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumFacing.Plane;
import net.minecraft.util.IStringSerializable;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.property.ExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class BlockStateMachine extends ExtendedBlockState {

    public static final PropertyBool activeProperty = PropertyBool.create("active");
    public static final PropertyEnum<BaseTier> tierProperty = PropertyEnum.create("tier", BaseTier.class);
    public static final PropertyEnum<RecipeType> recipeProperty = PropertyEnum.create("recipe", RecipeType.class);

    public BlockStateMachine(BlockMachine block, PropertyEnum<?> typeProperty) {
        super(block, new IProperty[]{BlockStateFacing.facingProperty, typeProperty, activeProperty, tierProperty, recipeProperty}, new IUnlistedProperty[]{});
    }

    public enum MachineBlock {
        MACHINE_BLOCK_1,
        MACHINE_BLOCK_2,
        MACHINE_BLOCK_3,
        MACHINE_BLOCK_4;

        PropertyEnum<MachineType> machineTypeProperty;

        public PropertyEnum<MachineType> getProperty() {
            if (machineTypeProperty == null) {
                machineTypeProperty = PropertyEnum.create("type", MachineType.class, input -> input != null && input.typeBlock == this && input.isValidMachine());
            }
            return machineTypeProperty;
        }

        public Block getBlock() {
            return switch (this) {
                case MACHINE_BLOCK_1 -> MekanismBlocks.MachineBlock;
                case MACHINE_BLOCK_2 -> MekanismBlocks.MachineBlock2;
                case MACHINE_BLOCK_3 -> MekanismBlocks.MachineBlock3;
                case MACHINE_BLOCK_4 -> MekanismBlocks.MachineBlock4;
            };
        }
    }

    public enum MachineType implements IStringSerializable, IBlockType {
        ENRICHMENT_CHAMBER(MachineBlock.MACHINE_BLOCK_1, 0, "EnrichmentChamber", 3, TileEntityEnrichmentChamber::new, true, false, true, Plane.HORIZONTAL, true, true, true),
        OSMIUM_COMPRESSOR(MachineBlock.MACHINE_BLOCK_1, 1, "OsmiumCompressor", 4, TileEntityOsmiumCompressor::new, true, false, true, Plane.HORIZONTAL, true, true, true),
        COMBINER(MachineBlock.MACHINE_BLOCK_1, 2, "Combiner", 5, TileEntityCombiner::new, true, false, true, Plane.HORIZONTAL, true, true, true),
        CRUSHER(MachineBlock.MACHINE_BLOCK_1, 3, "Crusher", 6, TileEntityCrusher::new, true, false, true, Plane.HORIZONTAL, true, true, true),
        DIGITAL_MINER(MachineBlock.MACHINE_BLOCK_1, 4, "DigitalMiner", 2, TileEntityDigitalMiner::new, true, true, true, Plane.HORIZONTAL, true, false, false),
        BASIC_FACTORY(MachineBlock.MACHINE_BLOCK_1, 5, "Factory", 11, TileEntityBasicFactory::new, true, false, true, Plane.HORIZONTAL, true, FactoryTier.BASIC, false, false),
        ADVANCED_FACTORY(MachineBlock.MACHINE_BLOCK_1, 6, "Factory", 11, TileEntityAdvancedFactory::new, true, false, true, Plane.HORIZONTAL, true, FactoryTier.ADVANCED, false, false),
        ELITE_FACTORY(MachineBlock.MACHINE_BLOCK_1, 7, "Factory", 11, TileEntityEliteFactory::new, true, false, true, Plane.HORIZONTAL, true, FactoryTier.ELITE, false, false),
        METALLURGIC_INFUSER(MachineBlock.MACHINE_BLOCK_1, 8, "MetallurgicInfuser", 12, TileEntityMetallurgicInfuser::new, true, true, true, Plane.HORIZONTAL, false, false, false),
        PURIFICATION_CHAMBER(MachineBlock.MACHINE_BLOCK_1, 9, "PurificationChamber", 15, TileEntityPurificationChamber::new, true, false, true, Plane.HORIZONTAL, true, true, true),
        ENERGIZED_SMELTER(MachineBlock.MACHINE_BLOCK_1, 10, "EnergizedSmelter", 16, TileEntityEnergizedSmelter::new, true, false, true, Plane.HORIZONTAL, true, true, true),
        TELEPORTER(MachineBlock.MACHINE_BLOCK_1, 11, "Teleporter", 13, TileEntityTeleporter::new, true, false, false, BlockStateUtils.NO_ROTATION, false, true, true),
        ELECTRIC_PUMP(MachineBlock.MACHINE_BLOCK_1, 12, "ElectricPump", 17, TileEntityElectricPump::new, true, true, false, Plane.HORIZONTAL, false, false, false),
        PERSONAL_CHEST(MachineBlock.MACHINE_BLOCK_1, 13, "PersonalChest", 19, TileEntityPersonalChest::new, true, true, false, Plane.HORIZONTAL, false, false, false),
        CHARGEPAD(MachineBlock.MACHINE_BLOCK_1, 14, "Chargepad", -1, TileEntityChargepad::new, true, true, false, Plane.HORIZONTAL, false, false, false),
        LOGISTICAL_SORTER(MachineBlock.MACHINE_BLOCK_1, 15, "LogisticalSorter", 59, TileEntityLogisticalSorter::new, false, true, false, BlockStateUtils.ALL_FACINGS, true, false, false),

        ROTARY_CONDENSENTRATOR(MachineBlock.MACHINE_BLOCK_2, 0, "RotaryCondensentrator", 7, TileEntityRotaryCondensentrator::new, true, true, false, Plane.HORIZONTAL, false, false, false),
        CHEMICAL_OXIDIZER(MachineBlock.MACHINE_BLOCK_2, 1, "ChemicalOxidizer", 29, TileEntityChemicalOxidizer::new, true, true, true, Plane.HORIZONTAL, true, false, false),
        CHEMICAL_INFUSER(MachineBlock.MACHINE_BLOCK_2, 2, "ChemicalInfuser", 30, TileEntityChemicalInfuser::new, true, true, false, Plane.HORIZONTAL, true, false, false),
        CHEMICAL_INJECTION_CHAMBER(MachineBlock.MACHINE_BLOCK_2, 3, "ChemicalInjectionChamber", 31, TileEntityChemicalInjectionChamber::new, true, false, true, Plane.HORIZONTAL, true, true, true),
        ELECTROLYTIC_SEPARATOR(MachineBlock.MACHINE_BLOCK_2, 4, "ElectrolyticSeparator", 32, TileEntityElectrolyticSeparator::new, true, true, false, Plane.HORIZONTAL, true, false, false),
        PRECISION_SAWMILL(MachineBlock.MACHINE_BLOCK_2, 5, "PrecisionSawmill", 34, TileEntityPrecisionSawmill::new, true, false, true, Plane.HORIZONTAL, true, true, true),
        CHEMICAL_DISSOLUTION_CHAMBER(MachineBlock.MACHINE_BLOCK_2, 6, "ChemicalDissolutionChamber", 35, TileEntityChemicalDissolutionChamber::new, true, true, true, Plane.HORIZONTAL, true, false, false),
        CHEMICAL_WASHER(MachineBlock.MACHINE_BLOCK_2, 7, "ChemicalWasher", 36, TileEntityChemicalWasher::new, true, true, false, Plane.HORIZONTAL, true, false, false),
        CHEMICAL_CRYSTALLIZER(MachineBlock.MACHINE_BLOCK_2, 8, "ChemicalCrystallizer", 37, TileEntityChemicalCrystallizer::new, true, true, true, Plane.HORIZONTAL, true, false, false),
        SEISMIC_VIBRATOR(MachineBlock.MACHINE_BLOCK_2, 9, "SeismicVibrator", 39, TileEntitySeismicVibrator::new, true, true, false, Plane.HORIZONTAL, true, false, false),
        PRESSURIZED_REACTION_CHAMBER(MachineBlock.MACHINE_BLOCK_2, 10, "PressurizedReactionChamber", 40, TileEntityPRC::new, true, true, false, Plane.HORIZONTAL, true, false, false),
        FLUID_TANK(MachineBlock.MACHINE_BLOCK_2, 11, "FluidTank", 41, TileEntityFluidTank::new, false, true, false, BlockStateUtils.NO_ROTATION, true, false, false),
        FLUIDIC_PLENISHER(MachineBlock.MACHINE_BLOCK_2, 12, "FluidicPlenisher", 42, TileEntityFluidicPlenisher::new, true, true, false, Plane.HORIZONTAL, true, false, false),
        LASER(MachineBlock.MACHINE_BLOCK_2, 13, "Laser", -1, TileEntityLaser::new, true, true, false, BlockStateUtils.ALL_FACINGS, false, false, false),
        LASER_AMPLIFIER(MachineBlock.MACHINE_BLOCK_2, 14, "LaserAmplifier", 44, TileEntityLaserAmplifier::new, false, true, false, BlockStateUtils.ALL_FACINGS, true, false, false),
        LASER_TRACTOR_BEAM(MachineBlock.MACHINE_BLOCK_2, 15, "LaserTractorBeam", 45, TileEntityLaserTractorBeam::new, false, true, false, BlockStateUtils.ALL_FACINGS, true, false, false),

        QUANTUM_ENTANGLOPORTER(MachineBlock.MACHINE_BLOCK_3, 0, "QuantumEntangloporter", 46, TileEntityQuantumEntangloporter::new, true, false, false, BlockStateUtils.ALL_FACINGS, false, false, false),
        SOLAR_NEUTRON_ACTIVATOR(MachineBlock.MACHINE_BLOCK_3, 1, "SolarNeutronActivator", 47, TileEntitySolarNeutronActivator::new, false, true, false, Plane.HORIZONTAL, true, false, false),
        AMBIENT_ACCUMULATOR(MachineBlock.MACHINE_BLOCK_3, 2, "AmbientAccumulator", 48, TileEntityAmbientAccumulator::new, false, true, false, Plane.HORIZONTAL, true, false, false),
        OREDICTIONIFICATOR(MachineBlock.MACHINE_BLOCK_3, 3, "Oredictionificator", 52, TileEntityOredictionificator::new, false, false, false, Plane.HORIZONTAL, true, true, true),
        RESISTIVE_HEATER(MachineBlock.MACHINE_BLOCK_3, 4, "ResistiveHeater", 53, TileEntityResistiveHeater::new, true, false, false, Plane.HORIZONTAL, true, false, false),
        FORMULAIC_ASSEMBLICATOR(MachineBlock.MACHINE_BLOCK_3, 5, "FormulaicAssemblicator", 56, TileEntityFormulaicAssemblicator::new, true, false, true, Plane.HORIZONTAL, true, true, true),
        FUELWOOD_HEATER(MachineBlock.MACHINE_BLOCK_3, 6, "FuelwoodHeater", 58, TileEntityFuelwoodHeater::new, false, false, false, Plane.HORIZONTAL, true, true, true),
        ULTIMATE_FACTORY(MachineBlock.MACHINE_BLOCK_3, 7, "Factory", 11, TileEntityUltimateFactory::new, true, false, true, Plane.HORIZONTAL, true, FactoryTier.ULTIMATE, false, false),
        ISOTOPIC_CENTRIFUGE(MachineBlock.MACHINE_BLOCK_3, 9, "IsotopicCentrifuge", 60, TileEntityIsotopicCentrifuge::new, true, true, false, Plane.HORIZONTAL, false, false, false),
        NUTRITIONAL_LIQUIFIER(MachineBlock.MACHINE_BLOCK_3, 10, "NutritionalLiquifier", 61, TileEntityNutritionalLiquifier::new, true, true, true, Plane.HORIZONTAL, false, false, false),
        SUPERCHARGED_COIL(MachineBlock.MACHINE_BLOCK_3, 11, "SuperchargedCoil", -1, TileEntitySuperchargedCoil::new, false, true, false, BlockStateUtils.ALL_FACINGS, false, false, false),
        ORGANIC_FARM(MachineBlock.MACHINE_BLOCK_3, 12, "OrganicFarm", 62, TileEntityOrganicFarm::new, true, false, true, Plane.HORIZONTAL, true, true, true),
        ANTIPROTONIC_NUCLEOSYNTHESIZER(MachineBlock.MACHINE_BLOCK_3, 13, "antiprotonicnucleosynthesizer", 63, TileEntityAntiprotonicNucleosynthesizer::new, true, true, true, Plane.HORIZONTAL, true, false, false),
        AMBIENT_ACCUMULATOR_ENERGY(MachineBlock.MACHINE_BLOCK_3, 14, "AmbientAccumulatorEnergy", 73, TileEntityAmbientAccumulatorEnergy::new, true, true, true, Plane.HORIZONTAL, true, false, false),

        STAMPING(MachineBlock.MACHINE_BLOCK_4, 0, "Stamping", 64, TileEntityStamping::new, true, false, true, Plane.HORIZONTAL, true, true, true),
        ROLLING(MachineBlock.MACHINE_BLOCK_4, 1, "Rolling", 65, TileEntityRolling::new, true, false, true, Plane.HORIZONTAL, true, true, true),
        BRUSHED(MachineBlock.MACHINE_BLOCK_4, 2, "Brushed", 66, TileEntityBrushed::new, true, false, true, Plane.HORIZONTAL, true, true, true),
        TURNING(MachineBlock.MACHINE_BLOCK_4, 3, "Turning", 67, TileEntityTurning::new, true, false, true, Plane.HORIZONTAL, true, true, true),
        ALLOY(MachineBlock.MACHINE_BLOCK_4, 4, "Alloy", 68, TileEntityAlloy::new, true, false, true, Plane.HORIZONTAL, true, true, true),
        //CELL_CULTIVATE(MachineBlock.MACHINE_BLOCK_4, 5, "CellCultivate", 69, TileEntityCellCultivate::new, true, false, true, Plane.HORIZONTAL, true),
        CELL_EXTRACTOR(MachineBlock.MACHINE_BLOCK_4, 6, "CellExtractor", 70, TileEntityCellExtractor::new, true, false, true, Plane.HORIZONTAL, true, true, true),
        CELL_SEPARATOR(MachineBlock.MACHINE_BLOCK_4, 7, "CellSeparator", 71, TileEntityCellSeparator::new, true, false, true, Plane.HORIZONTAL, true, true, true),
        RECYCLER(MachineBlock.MACHINE_BLOCK_4, 8, "Recycler", 72, TileEntityRecycler::new, true, false, true, Plane.HORIZONTAL, true, true, true),
        INDUSTRIAL_ALARM(MachineBlock.MACHINE_BLOCK_4, 9, "IndustrialAlarm", -1, TileEntityIndustrialAlarm::new, false, true, false, BlockStateUtils.ALL_FACINGS, false, false, false),
        HYBRID_STORAGE(MachineBlock.MACHINE_BLOCK_4, 10, "Hybrid_Storage", 74, TileEntityHybridStorage::new, true, false, false, BlockStateUtils.ALL_FACINGS, false, true, true),
        MODIFICATION_STATION(MachineBlock.MACHINE_BLOCK_4, 11, "Modification_Station", 75, TileEntityModificationStation::new, true, true, true, Plane.HORIZONTAL, false, false, false),
        RADIOACTIVE_WASTE_BARREL(MachineBlock.MACHINE_BLOCK_4, 12, "radioactive_waste_barrel", -1, TileEntityRadioactiveWasteBarrel::new, false, false, false, Plane.HORIZONTAL, false, false, false),
        SPS(MachineBlock.MACHINE_BLOCK_4, 13, "sps", 76, TileEntitySPS::new, true, false, false, Plane.HORIZONTAL, true, false, false),
        DIMENSIONAL_STABILIZER(MachineBlock.MACHINE_BLOCK_4, 14, "dimensional_stabilizer", 79, TileEntityDimensionalStabilizer::new, true, false, true, BlockStateUtils.NO_ROTATION, true, true, true);


        private static final Map<MachineBlock, Int2ReferenceMap<MachineType>> VALID_METAS = new EnumMap<>(MachineBlock.class);

        static {
            Arrays.stream(values()).forEach(MachineType::registerType);
        }

        public MachineBlock typeBlock;
        public int meta;
        public String blockName;
        public int guiId;
        public Supplier<TileEntity> tileEntitySupplier;
        public boolean isElectric;
        public boolean hasModel;
        public boolean supportsUpgrades;
        public Predicate<EnumFacing> facingPredicate;
        public boolean activable;
        public FactoryTier factoryTier;
        public boolean isFullBlock;
        public boolean isOpaqueCube;

        MachineType(MachineBlock block, int m, String name, int gui, Supplier<TileEntity> tileClass, boolean electric, boolean model, boolean upgrades,
                    Predicate<EnumFacing> predicate, boolean hasActiveTexture, boolean fullBlock, boolean opaque) {
            this(block, m, name, gui, tileClass, electric, model, upgrades, predicate, hasActiveTexture, null, fullBlock, opaque);
        }

        MachineType(MachineBlock block, int m, String name, int gui, Supplier<TileEntity> tileClass, boolean electric, boolean model, boolean upgrades,
                    Predicate<EnumFacing> predicate, boolean hasActiveTexture, FactoryTier factoryTier, boolean fullBlock, boolean opaque) {
            typeBlock = block;
            meta = m;
            blockName = name;
            guiId = gui;
            tileEntitySupplier = tileClass;
            isElectric = electric;
            hasModel = model;
            supportsUpgrades = upgrades;
            facingPredicate = predicate;
            activable = hasActiveTexture;
            this.factoryTier = factoryTier;
            isFullBlock = fullBlock;
            isOpaqueCube = opaque;
        }

        private void registerType() {
            VALID_METAS.computeIfAbsent(typeBlock, k -> new Int2ReferenceOpenHashMap<>()).put(meta, this);
        }

        private static final List<MachineType> VALID_MACHINES = new ArrayList<>();

        static {
            Arrays.stream(MachineType.values()).filter(MachineType::isValidMachine).forEach(VALID_MACHINES::add);
        }

        public static List<MachineType> getValidMachines() {
            return VALID_MACHINES;
        }

        public static MachineType get(IBlockState state) {
            if (state.getBlock() instanceof BlockMachine machine) {
                return state.getValue(machine.getTypeProperty());
            }
            return null;
        }

        public static MachineType get(Block block, int meta) {
            if (block instanceof BlockMachine machine) {
                return get(machine.getMachineBlock(), meta);
            }
            return null;
        }

        public static MachineType get(MachineBlock block, int meta) {
            Int2ReferenceMap<MachineType> meta2Type = VALID_METAS.get(block);
            if (meta2Type == null) {
                return null;
            }
            return meta2Type.get(meta);
        }

        public static MachineType get(ItemStack stack) {
            return get(Block.getBlockFromItem(stack.getItem()), stack.getItemDamage());
        }

        @Override
        public String getBlockName() {
            return blockName;
        }

        @Override
        public boolean isEnabled() {
            return MekanismConfig.current().general.machinesManager.isEnabled(this);
        }

        public boolean isValidMachine() {
            return true;
            //   return this != HYBRID_STORAGE;
        }

        public TileEntity create() {
            return this.tileEntitySupplier != null ? this.tileEntitySupplier.get() : null;
        }

        public double getUsage() {
            return switch (this) {
                case ENRICHMENT_CHAMBER -> MekanismConfig.current().usage.enrichmentChamber.val();
                case OSMIUM_COMPRESSOR -> MekanismConfig.current().usage.osmiumCompressor.val();
                case COMBINER -> MekanismConfig.current().usage.combiner.val();
                case CRUSHER -> MekanismConfig.current().usage.crusher.val();
                case DIGITAL_MINER -> MekanismConfig.current().usage.digitalMiner.val();
                case METALLURGIC_INFUSER -> MekanismConfig.current().usage.metallurgicInfuser.val();
                case PURIFICATION_CHAMBER -> MekanismConfig.current().usage.purificationChamber.val();
                case ENERGIZED_SMELTER -> MekanismConfig.current().usage.energizedSmelter.val();
                case TELEPORTER -> 12500;
                case ELECTRIC_PUMP -> MekanismConfig.current().usage.electricPump.val();
                case PERSONAL_CHEST -> 30;
                case CHARGEPAD -> 25;
                case ROTARY_CONDENSENTRATOR -> MekanismConfig.current().usage.rotaryCondensentrator.val();
                case CHEMICAL_OXIDIZER -> MekanismConfig.current().usage.oxidationChamber.val();
                case CHEMICAL_INFUSER -> MekanismConfig.current().usage.chemicalInfuser.val();
                case CHEMICAL_INJECTION_CHAMBER -> MekanismConfig.current().usage.chemicalInjectionChamber.val();
                case ELECTROLYTIC_SEPARATOR -> MekanismConfig.current().general.FROM_H2.val() * 2;
                case PRECISION_SAWMILL -> MekanismConfig.current().usage.precisionSawmill.val();
                case CHEMICAL_DISSOLUTION_CHAMBER -> MekanismConfig.current().usage.chemicalDissolutionChamber.val();
                case CHEMICAL_WASHER -> MekanismConfig.current().usage.chemicalWasher.val();
                case CHEMICAL_CRYSTALLIZER -> MekanismConfig.current().usage.chemicalCrystallizer.val();
                case SEISMIC_VIBRATOR -> MekanismConfig.current().usage.seismicVibrator.val();
                case PRESSURIZED_REACTION_CHAMBER -> MekanismConfig.current().usage.pressurizedReactionBase.val();
                case FLUIDIC_PLENISHER -> MekanismConfig.current().usage.fluidicPlenisher.val();
                case LASER -> MekanismConfig.current().usage.laser.val();
                case RESISTIVE_HEATER -> 100;
                case FORMULAIC_ASSEMBLICATOR -> MekanismConfig.current().usage.formulaicAssemblicator.val();
                /**
                 * Add Start
                 */
                case ISOTOPIC_CENTRIFUGE -> MekanismConfig.current().usage.isotopicCentrifuge.val();
                case NUTRITIONAL_LIQUIFIER -> MekanismConfig.current().usage.liquifierNutritional.val();
                case ORGANIC_FARM -> MekanismConfig.current().usage.organicfarm.val();
                case ANTIPROTONIC_NUCLEOSYNTHESIZER -> MekanismConfig.current().usage.nucleosynthesizer.val();
                case STAMPING -> MekanismConfig.current().usage.stamping.val();
                case ROLLING -> MekanismConfig.current().usage.rolling.val();
                case BRUSHED -> MekanismConfig.current().usage.brushed.val();
                case TURNING -> MekanismConfig.current().usage.turning.val();
                case ALLOY -> MekanismConfig.current().usage.alloy.val();
                case CELL_EXTRACTOR -> MekanismConfig.current().usage.cellExtractor.val();
                case CELL_SEPARATOR -> MekanismConfig.current().usage.cellSeparator.val();
                case RECYCLER -> MekanismConfig.current().usage.recycler.val();
                case AMBIENT_ACCUMULATOR_ENERGY -> MekanismConfig.current().usage.AmbientAccumulatorEnergy.val();
                case MODIFICATION_STATION -> MekanismConfig.current().usage.modificationStation.val();
                case DIMENSIONAL_STABILIZER -> 5000;
                default -> 0;
            };
        }

        private double getConfigStorage() {
            return switch (this) {
                case ENRICHMENT_CHAMBER -> MekanismConfig.current().storage.enrichmentChamber.val();
                case OSMIUM_COMPRESSOR -> MekanismConfig.current().storage.osmiumCompressor.val();
                case COMBINER -> MekanismConfig.current().storage.combiner.val();
                case CRUSHER -> MekanismConfig.current().storage.crusher.val();
                case DIGITAL_MINER -> MekanismConfig.current().storage.digitalMiner.val();
                case METALLURGIC_INFUSER -> MekanismConfig.current().storage.metallurgicInfuser.val();
                case PURIFICATION_CHAMBER -> MekanismConfig.current().storage.purificationChamber.val();
                case ENERGIZED_SMELTER -> MekanismConfig.current().storage.energizedSmelter.val();
                case TELEPORTER -> MekanismConfig.current().storage.teleporter.val();
                case ELECTRIC_PUMP -> MekanismConfig.current().storage.electricPump.val();
                case CHARGEPAD -> MekanismConfig.current().storage.chargePad.val();
                case ROTARY_CONDENSENTRATOR -> MekanismConfig.current().storage.rotaryCondensentrator.val();
                case CHEMICAL_OXIDIZER -> MekanismConfig.current().storage.oxidationChamber.val();
                case CHEMICAL_INFUSER -> MekanismConfig.current().storage.chemicalInfuser.val();
                case CHEMICAL_INJECTION_CHAMBER -> MekanismConfig.current().storage.chemicalInjectionChamber.val();
                case ELECTROLYTIC_SEPARATOR -> MekanismConfig.current().storage.electrolyticSeparator.val();
                case PRECISION_SAWMILL -> MekanismConfig.current().storage.precisionSawmill.val();
                case CHEMICAL_DISSOLUTION_CHAMBER -> MekanismConfig.current().storage.chemicalDissolutionChamber.val();
                case CHEMICAL_WASHER -> MekanismConfig.current().storage.chemicalWasher.val();
                case CHEMICAL_CRYSTALLIZER -> MekanismConfig.current().storage.chemicalCrystallizer.val();
                case SEISMIC_VIBRATOR -> MekanismConfig.current().storage.seismicVibrator.val();
                case PRESSURIZED_REACTION_CHAMBER -> MekanismConfig.current().storage.pressurizedReactionBase.val();
                case FLUIDIC_PLENISHER -> MekanismConfig.current().storage.fluidicPlenisher.val();
                case LASER -> MekanismConfig.current().storage.laser.val();
                case FORMULAIC_ASSEMBLICATOR -> MekanismConfig.current().storage.formulaicAssemblicator.val();
                /**
                 * Add Start
                 */
                case ISOTOPIC_CENTRIFUGE -> MekanismConfig.current().storage.isotopicCentrifuge.val();
                case NUTRITIONAL_LIQUIFIER -> MekanismConfig.current().storage.liquifierNutritional.val();
                case ORGANIC_FARM -> MekanismConfig.current().storage.organicfarm.val();
                case ANTIPROTONIC_NUCLEOSYNTHESIZER -> MekanismConfig.current().storage.nucleosynthesizer.val();
                case STAMPING -> MekanismConfig.current().storage.stamping.val();
                case ROLLING -> MekanismConfig.current().storage.rolling.val();
                case BRUSHED -> MekanismConfig.current().storage.brushed.val();
                case TURNING -> MekanismConfig.current().storage.turning.val();
                case ALLOY -> MekanismConfig.current().storage.alloy.val();
                case CELL_EXTRACTOR -> MekanismConfig.current().storage.cellExtractor.val();
                case CELL_SEPARATOR -> MekanismConfig.current().storage.cellSeparator.val();
                case RECYCLER -> MekanismConfig.current().storage.recycler.val();
                case INDUSTRIAL_ALARM -> 0;
                case AMBIENT_ACCUMULATOR_ENERGY -> MekanismConfig.current().storage.AmbientAccumulatorEnergy.val();
                case HYBRID_STORAGE -> MekanismConfig.current().storage.HybridStorageEnergy.val();
                case MODIFICATION_STATION -> MekanismConfig.current().storage.modificationStation.val();
                case SPS -> 1000000000; //todo
                case DIMENSIONAL_STABILIZER -> 40000;
                default -> 400 * getUsage();
            };
        }

        public double getStorage() {
            return Math.max(getConfigStorage(), getUsage());
        }

        public String getDescription() {
            return LangUtils.localize("tooltip." + blockName);
        }

        public ItemStack getStack() {
            return new ItemStack(typeBlock.getBlock(), 1, meta);
        }

        @Override
        public String getName() {
            return name().toLowerCase(Locale.ROOT);
        }

        @Override
        public String toString() {
            return getName();
        }

        public boolean canRotateTo(EnumFacing side) {
            return facingPredicate.test(side);
        }

        public boolean hasRotations() {
            return !facingPredicate.equals(BlockStateUtils.NO_ROTATION);
        }

        public boolean hasActiveTexture() {
            return activable;
        }

        public boolean isFactory() {
            return factoryTier != null;
        }
    }

    public static class MachineBlockStateMapper extends StateMapperBase {

        // use int-packed keys to avoid allocating strings during cache build and lookups
        private final Int2ReferenceMap<ModelResourceLocation> cache = new Int2ReferenceOpenHashMap<>();

        public MachineBlockStateMapper() {
            buildCache();
        }

        private void buildCache() {
            for (MachineType type : MachineType.values()) {
                if (!type.isValidMachine()) {
                    continue;
                }

                boolean hasActive = type.hasActiveTexture();
                boolean hasRotations = type.hasRotations();
                boolean isFactory = type.isFactory();

                List<Boolean> activeOptions = hasActive ? Arrays.asList(Boolean.FALSE, Boolean.TRUE) : Collections.singletonList(null);
                EnumFacing[] facings = hasRotations ? EnumFacing.values() : new EnumFacing[]{null};
                RecipeType[] recipes = isFactory ? RecipeType.values() : new RecipeType[]{null};

                for (Boolean activeVal : activeOptions) {
                    for (EnumFacing facingVal : facings) {
                        EnumFacing useFacing = facingVal;
                        if (useFacing != null && !type.canRotateTo(useFacing)) {
                            useFacing = EnumFacing.NORTH;
                        }
                        for (RecipeType recipeVal : recipes) {
                            StringBuilder builder = new StringBuilder();
                            String nameOverride = null;

                            if (hasActive) {
                                builder.append(activeProperty.getName()).append("=").append(activeVal);
                            }

                            if (hasRotations) {
                                if (builder.length() > 0) builder.append(",");
                                builder.append(BlockStateFacing.facingProperty.getName()).append("=").append((useFacing != null ? useFacing : EnumFacing.NORTH).getName());
                            }

                            if (isFactory && recipeVal != null) {
                                nameOverride = type.getName() + "_" + recipeVal.getName();
                            }

                            if (builder.length() == 0) {
                                builder.append("normal");
                            }

                            ResourceLocation baseLocation = new ResourceLocation(Mekanism.MODID, nameOverride != null ? nameOverride : type.getName());
                            int key = makeKey(type, useFacing, activeVal, recipeVal);
                            cache.put(key, new ModelResourceLocation(baseLocation, builder.toString()));
                        }
                    }
                }
            }
        }

        /**
         * Pack (typeOrdinal in bits 0-7) | (recipeOrdinal in bits 8-15) | (facingIndex in bits 16-18) | (activeBit in bit 19)
         * facingIndex: 0 = null/normal, 1..6 = EnumFacing.ordinal()+1
         */
        private int makeKey(MachineType type, EnumFacing facing, Boolean active, RecipeType recipe) {
            int typeOrd = type.ordinal() & 0xFF;
            int recipeOrd = (recipe != null ? recipe.ordinal() : 0) & 0xFF;
            int facingIndex = 0;
            if (facing != null) {
                facingIndex = (facing.ordinal() + 1) & 0x07;
            }
            int activeBit = (active != null && active) ? 1 : 0;
            return typeOrd | (recipeOrd << 8) | (facingIndex << 16) | (activeBit << 19);
        }

        @Nonnull
        @Override
        protected ModelResourceLocation getModelResourceLocation(@Nonnull IBlockState state) {
            BlockMachine block = (BlockMachine) state.getBlock();
            MachineType type = state.getValue(block.getTypeProperty());

            StringBuilder builder = new StringBuilder();
            String nameOverride = null;

            Boolean activeVal = null;
            EnumFacing facingVal = null;
            RecipeType recipeVal = null;

            if (type.hasActiveTexture()) {
                activeVal = state.getValue(activeProperty);
                builder.append(activeProperty.getName()).append("=").append(activeVal);
            }

            if (type.hasRotations()) {
                EnumFacing facing = state.getValue(BlockStateFacing.facingProperty);
                if (!type.canRotateTo(facing)) {
                    facing = EnumFacing.NORTH;
                }
                facingVal = facing;
                if (builder.length() > 0) builder.append(",");
                builder.append(BlockStateFacing.facingProperty.getName()).append("=").append(facing.getName());
            }

            if (type.isFactory()) {
                recipeVal = state.getValue(recipeProperty);
                nameOverride = type.getName() + "_" + recipeVal.getName();
            }

            if (builder.length() == 0) {
                builder.append("normal");
            }

            int key = makeKey(type, facingVal, activeVal, recipeVal);
            ModelResourceLocation cached = cache.get(key);
            if (cached != null) {
                return cached;
            }

            // fallback (rare) - construct on the fly
            ResourceLocation baseLocation = new ResourceLocation(Mekanism.MODID, nameOverride != null ? nameOverride : type.getName());
            return new ModelResourceLocation(baseLocation, builder.toString());
        }
    }
}
