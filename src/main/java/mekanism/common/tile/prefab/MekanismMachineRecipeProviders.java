package mekanism.common.tile.prefab;

import mekanism.api.processing.MachinePort;
import mekanism.api.processing.MachineRecipeProvider;
import mekanism.api.processing.MachineRecipeProviderRegistry;
import mekanism.api.processing.MachineRecipeRoute;
import mekanism.api.AutomationType;
import mekanism.common.Mekanism;
import mekanism.common.MekanismFluids;
import mekanism.common.base.IFactory.RecipeType;
import mekanism.common.recipe.RecipeHandler;
import mekanism.common.recipe.processing.MachineRecipeRouteCollectors;
import mekanism.common.tile.factory.TileEntityFactory;
import mekanism.common.tile.machine.TileEntityAmbientAccumulator;
import mekanism.common.tile.machine.TileEntityAmbientAccumulatorEnergy;
import mekanism.common.tile.machine.TileEntityAntiprotonicNucleosynthesizer;
import mekanism.common.tile.machine.TileEntityChemicalCrystallizer;
import mekanism.common.tile.machine.TileEntityChemicalDissolutionChamber;
import mekanism.common.tile.machine.TileEntityChemicalInfuser;
import mekanism.common.tile.machine.TileEntityChemicalOxidizer;
import mekanism.common.tile.machine.TileEntityChemicalWasher;
import mekanism.common.tile.machine.TileEntityDigitalMiner;
import mekanism.common.tile.machine.TileEntityElectricPump;
import mekanism.common.tile.machine.TileEntityElectrolyticSeparator;
import mekanism.common.tile.machine.TileEntityIsotopicCentrifuge;
import mekanism.common.tile.machine.TileEntityMetallurgicInfuser;
import mekanism.common.tile.machine.TileEntityNutritionalLiquifier;
import mekanism.common.tile.machine.TileEntityPRC;
import mekanism.common.tile.machine.TileEntityRotaryCondensentrator;
import mekanism.common.tile.machine.TileEntitySolarNeutronActivator;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/** Registers Mekanism machines with the network-independent processing bridge. */
@SuppressWarnings({"rawtypes", "unchecked"})
public final class MekanismMachineRecipeProviders {

    private static boolean registered;

    private MekanismMachineRecipeProviders() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registerPrefabMachines();
        registerChemicalMachines();
        registerFactory();
        registerOutputMachines();
        registered = true;
    }

    private static void registerPrefabMachines() {
        register("electric_machine", TileEntityElectricMachine.class, tile -> tile.getRecipes(),
              tile -> MachineRecipeRouteCollectors.collectBasicItem(tile.getRecipes()),
              tile -> ports(
                    MachinePort.item("item_input", MachinePort.Role.INPUT, tile.inputSlot),
                    MachinePort.item("item_output", MachinePort.Role.OUTPUT, tile.outputSlot)));

        register("chance_machine", TileEntityChanceMachine.class, tile -> tile.getRecipes(),
              tile -> MachineRecipeRouteCollectors.collectChanceItem(tile.getRecipes()),
              tile -> ports(
                    MachinePort.item("item_input", MachinePort.Role.INPUT, tile.inputSlot),
                    MachinePort.item("item_output", MachinePort.Role.OUTPUT, tile.outputSlot),
                    MachinePort.item("secondary_item_output", MachinePort.Role.OUTPUT, tile.secondaryOutputSlot)));

        register("guaranteed_chance_machine", TileEntityChanceMachine2.class, tile -> tile.getRecipes(),
              tile -> MachineRecipeRouteCollectors.collectGuaranteedChanceItem(tile.getRecipes()),
              tile -> ports(
                    MachinePort.item("item_input", MachinePort.Role.INPUT, tile.inputSlot),
                    MachinePort.item("item_output", MachinePort.Role.OUTPUT, tile.outputSlot)));

        register("double_item_machine", TileEntityDoubleElectricMachine.class, tile -> tile.getRecipes(),
              tile -> MachineRecipeRouteCollectors.collectDoubleItem(tile.getRecipes()),
              tile -> ports(
                    MachinePort.item("item_input", MachinePort.Role.INPUT, tile.inputSlot),
                    MachinePort.item("extra_item_input", MachinePort.Role.INPUT, tile.extraSlot),
                    MachinePort.item("item_output", MachinePort.Role.OUTPUT, tile.outputSlot)));

        register("advanced_gas_machine", TileEntityAdvancedElectricMachine.class, tile -> tile.getRecipes(),
              tile -> MachineRecipeRouteCollectors.collectAdvancedGasToItem(tile.getRecipes(), tile.getRecipeGasUsagePerOperation()),
              tile -> ports(
                    MachinePort.item("item_input", MachinePort.Role.INPUT, tile.inputSlot),
                    MachinePort.gas("gas_input", MachinePort.Role.INPUT, tile.gasTank),
                    MachinePort.item("item_output", MachinePort.Role.OUTPUT, tile.outputSlot)),
              TileEntityAdvancedElectricMachine::getRecipeGasUsagePerOperation);

        register("farm_machine", TileEntityFarmMachine.class, tile -> tile.getRecipes(),
              tile -> MachineRecipeRouteCollectors.collectFarmGasToItem(tile.getRecipes(), tile.getRecipeGasUsagePerOperation()),
              tile -> ports(
                    MachinePort.item("item_input", MachinePort.Role.INPUT, tile.inputSlot),
                    MachinePort.gas("gas_input", MachinePort.Role.INPUT, tile.gasTank),
                    MachinePort.item("item_output", MachinePort.Role.OUTPUT, tile.outputSlot),
                    MachinePort.item("secondary_item_output", MachinePort.Role.OUTPUT, tile.secondaryOutputSlot)),
              TileEntityFarmMachine::getRecipeGasUsagePerOperation);
    }

    private static void registerChemicalMachines() {
        register("metallurgic_infuser", TileEntityMetallurgicInfuser.class, TileEntityMetallurgicInfuser::getRecipes,
              tile -> MachineRecipeRouteCollectors.collectInfusionItem(tile.getRecipes()),
              tile -> ports(
                    MachinePort.item("item_input", MachinePort.Role.INPUT, tile.getRecipeInputSlot()),
                    MachinePort.item("extra_item_input", MachinePort.Role.INPUT, tile.getRecipeExtraInputSlot()),
                    MachinePort.item("item_output", MachinePort.Role.OUTPUT, tile.getRecipeOutputSlot())));

        register("chemical_dissolution_chamber", TileEntityChemicalDissolutionChamber.class,
              TileEntityChemicalDissolutionChamber::getRecipes,
              tile -> MachineRecipeRouteCollectors.collectItemGasToGas(tile.getRecipes(), MekanismFluids.SulfuricAcid,
                    tile.getRecipeGasUsagePerOperation()),
              tile -> ports(
                    MachinePort.item("item_input", MachinePort.Role.INPUT, tile.getRecipeInputSlot()),
                    MachinePort.gas("gas_input", MachinePort.Role.INPUT, tile.injectTank),
                    MachinePort.gas("gas_output", MachinePort.Role.OUTPUT, tile.outputTank)),
              TileEntityChemicalDissolutionChamber::getRecipeGasUsagePerOperation);

        register("chemical_oxidizer", TileEntityChemicalOxidizer.class, TileEntityChemicalOxidizer::getRecipes,
              tile -> MachineRecipeRouteCollectors.collectItemToGas(tile.getRecipes()),
              tile -> ports(
                    MachinePort.item("item_input", MachinePort.Role.INPUT, tile.getRecipeInputSlot()),
                    MachinePort.gas("gas_output", MachinePort.Role.OUTPUT, tile.gasTank)));

        register("nutritional_liquifier", TileEntityNutritionalLiquifier.class, TileEntityNutritionalLiquifier::getRecipes,
              tile -> MachineRecipeRouteCollectors.collectItemToGas(tile.getRecipes()),
              tile -> ports(
                    MachinePort.item("item_input", MachinePort.Role.INPUT, tile.getRecipeInputSlot()),
                    MachinePort.gas("gas_output", MachinePort.Role.OUTPUT, tile.gasTank)));

        register("chemical_crystallizer", TileEntityChemicalCrystallizer.class, TileEntityChemicalCrystallizer::getRecipes,
              tile -> MachineRecipeRouteCollectors.collectGasToItem(tile.getRecipes()),
              tile -> ports(
                    MachinePort.gas("gas_input", MachinePort.Role.INPUT, tile.inputTank),
                    MachinePort.item("item_output", MachinePort.Role.OUTPUT, tile.getRecipeOutputSlot())));

        register("chemical_infuser", TileEntityChemicalInfuser.class, TileEntityChemicalInfuser::getRecipes,
              tile -> MachineRecipeRouteCollectors.collectChemicalPairToGas(tile.getRecipes()),
              tile -> ports(
                    MachinePort.gas("left_gas", MachinePort.Role.INPUT, tile.leftTank),
                    MachinePort.gas("right_gas", MachinePort.Role.INPUT, tile.rightTank),
                    MachinePort.gas("gas_output", MachinePort.Role.OUTPUT, tile.centerTank)));

        register("chemical_washer", TileEntityChemicalWasher.class, TileEntityChemicalWasher::getRecipes,
              tile -> MachineRecipeRouteCollectors.collectGasFluidToGas(tile.getRecipes()),
              tile -> ports(
                    MachinePort.gas("gas_input", MachinePort.Role.INPUT, tile.inputTank),
                    MachinePort.fluid("fluid_input", MachinePort.Role.INPUT, tile.fluidTank),
                    MachinePort.gas("gas_output", MachinePort.Role.OUTPUT, tile.outputTank)));

        register("electrolytic_separator", TileEntityElectrolyticSeparator.class, TileEntityElectrolyticSeparator::getRecipes,
              tile -> MachineRecipeRouteCollectors.collectFluidToGasPair(tile.getRecipes()),
              tile -> ports(
                    MachinePort.fluid("fluid_input", MachinePort.Role.INPUT, tile.fluidTank),
                    MachinePort.gas("left_gas_output", MachinePort.Role.OUTPUT, tile.leftTank),
                    MachinePort.gas("right_gas_output", MachinePort.Role.OUTPUT, tile.rightTank)));

        register("isotopic_centrifuge", TileEntityIsotopicCentrifuge.class, TileEntityIsotopicCentrifuge::getRecipes,
              tile -> MachineRecipeRouteCollectors.collectGasToGas(tile.getRecipes()),
              tile -> ports(
                    MachinePort.gas("gas_input", MachinePort.Role.INPUT, tile.inputTank),
                    MachinePort.gas("gas_output", MachinePort.Role.OUTPUT, tile.outputTank)));

        register("pressurized_reaction_chamber", TileEntityPRC.class, TileEntityPRC::getRecipes,
              tile -> MachineRecipeRouteCollectors.collectPressurized(tile.getRecipes()),
              tile -> ports(
                    MachinePort.item("item_input", MachinePort.Role.INPUT, tile.getRecipeInputSlot()),
                    MachinePort.fluid("fluid_input", MachinePort.Role.INPUT, tile.inputFluidTank),
                    MachinePort.gas("gas_input", MachinePort.Role.INPUT, tile.inputGasTank),
                    MachinePort.item("item_output", MachinePort.Role.OUTPUT, tile.getRecipeOutputSlot()),
                    MachinePort.gas("gas_output", MachinePort.Role.OUTPUT, tile.outputGasTank)));

        register("antiprotonic_nucleosynthesizer", TileEntityAntiprotonicNucleosynthesizer.class,
              TileEntityAntiprotonicNucleosynthesizer::getRecipes,
              tile -> MachineRecipeRouteCollectors.collectNucleosynthesizerGasToItem(tile.getRecipes()),
              tile -> ports(
                    MachinePort.item("item_input", MachinePort.Role.INPUT, tile.getRecipeInputSlot()),
                    MachinePort.gas("gas_input", MachinePort.Role.INPUT, tile.inputGasTank),
                    MachinePort.item("item_output", MachinePort.Role.OUTPUT, tile.getRecipeOutputSlot())));

        register("rotary_condensentrator", TileEntityRotaryCondensentrator.class,
              ignored -> RecipeHandler.Recipe.ROTARY_CONDENSENTRATOR.get(),
              tile -> tile.mode == 0 ?
                    MachineRecipeRouteCollectors.collectRotaryGasToFluid(RecipeHandler.Recipe.ROTARY_CONDENSENTRATOR.get()) :
                    MachineRecipeRouteCollectors.collectRotaryFluidToGas(RecipeHandler.Recipe.ROTARY_CONDENSENTRATOR.get()),
              tile -> tile.mode == 0 ? ports(
                    MachinePort.gas("gas_input", MachinePort.Role.INPUT, tile.gasTank),
                    MachinePort.fluid("fluid_output", MachinePort.Role.OUTPUT, tile.fluidTank)) : ports(
                    MachinePort.fluid("fluid_input", MachinePort.Role.INPUT, tile.fluidTank),
                    MachinePort.gas("gas_output", MachinePort.Role.OUTPUT, tile.gasTank)),
              tile -> tile.mode);

        register("solar_neutron_activator", TileEntitySolarNeutronActivator.class,
              ignored -> RecipeHandler.Recipe.SOLAR_NEUTRON_ACTIVATOR.get(),
              ignored -> MachineRecipeRouteCollectors.collectGasToGas(RecipeHandler.Recipe.SOLAR_NEUTRON_ACTIVATOR.get()),
              tile -> ports(
                    MachinePort.gas("gas_input", MachinePort.Role.INPUT, tile.inputTank),
                    MachinePort.gas("gas_output", MachinePort.Role.OUTPUT, tile.outputTank)));
    }

    private static void registerFactory() {
        register("factory", TileEntityFactory.class, MekanismMachineRecipeProviders::getFactoryRecipeSource,
              MekanismMachineRecipeProviders::getFactoryRoutes, MekanismMachineRecipeProviders::getFactoryPorts,
              tile -> {
                  int revision = tile.getRecipeType().ordinal();
                  revision = 31 * revision + tile.getProcessCount();
                  return 31 * revision + tile.getRecipeGasUsagePerOperation();
              });
    }

    private static Object getFactoryRecipeSource(TileEntityFactory tile) {
        return switch (tile.getRecipeType()) {
            case SMELTING -> RecipeHandler.Recipe.ENERGIZED_SMELTER.get();
            case ENRICHING -> RecipeHandler.Recipe.ENRICHMENT_CHAMBER.get();
            case CRUSHING -> RecipeHandler.Recipe.CRUSHER.get();
            case COMPRESSING -> RecipeHandler.Recipe.OSMIUM_COMPRESSOR.get();
            case COMBINING -> RecipeHandler.Recipe.COMBINER.get();
            case PURIFYING -> RecipeHandler.Recipe.PURIFICATION_CHAMBER.get();
            case INJECTING -> RecipeHandler.Recipe.CHEMICAL_INJECTION_CHAMBER.get();
            case INFUSING -> RecipeHandler.Recipe.METALLURGIC_INFUSER.get();
            case SAWING -> RecipeHandler.Recipe.PRECISION_SAWMILL.get();
            case STAMPING -> RecipeHandler.Recipe.STAMPING.get();
            case ROLLING -> RecipeHandler.Recipe.ROLLING.get();
            case BRUSHED -> RecipeHandler.Recipe.BRUSHED.get();
            case TURNING -> RecipeHandler.Recipe.TURNING.get();
            case AllOY -> RecipeHandler.Recipe.ALLOY.get();
            case EXTRACTOR -> RecipeHandler.Recipe.CELL_EXTRACTOR.get();
            case SEPARATOR -> RecipeHandler.Recipe.CELL_SEPARATOR.get();
            case FARM -> RecipeHandler.Recipe.ORGANIC_FARM.get();
            case RECYCLER -> RecipeHandler.Recipe.RECYCLER.get();
            case PRC -> RecipeHandler.Recipe.PRESSURIZED_REACTION_CHAMBER.get();
            case NUCLEOSYNTHESIZER -> RecipeHandler.Recipe.ANTIPROTONIC_NUCLEOSYNTHESIZER.get();
        };
    }

    private static List<MachineRecipeRoute> getFactoryRoutes(TileEntityFactory tile) {
        List<MachineRecipeRoute> routes = switch (tile.getRecipeType()) {
            case SMELTING -> MachineRecipeRouteCollectors.collectBasicItem(RecipeHandler.Recipe.ENERGIZED_SMELTER.get());
            case ENRICHING -> MachineRecipeRouteCollectors.collectBasicItem(RecipeHandler.Recipe.ENRICHMENT_CHAMBER.get());
            case CRUSHING -> MachineRecipeRouteCollectors.collectBasicItem(RecipeHandler.Recipe.CRUSHER.get());
            case COMPRESSING -> MachineRecipeRouteCollectors.collectAdvancedGasToItem(
                  RecipeHandler.Recipe.OSMIUM_COMPRESSOR.get(), tile.getRecipeGasUsagePerOperation());
            case COMBINING -> MachineRecipeRouteCollectors.collectDoubleItem(RecipeHandler.Recipe.COMBINER.get());
            case PURIFYING -> MachineRecipeRouteCollectors.collectAdvancedGasToItem(
                  RecipeHandler.Recipe.PURIFICATION_CHAMBER.get(), tile.getRecipeGasUsagePerOperation());
            case INJECTING -> MachineRecipeRouteCollectors.collectAdvancedGasToItem(
                  RecipeHandler.Recipe.CHEMICAL_INJECTION_CHAMBER.get(), tile.getRecipeGasUsagePerOperation());
            case INFUSING -> MachineRecipeRouteCollectors.collectInfusionItem(RecipeHandler.Recipe.METALLURGIC_INFUSER.get());
            case SAWING -> MachineRecipeRouteCollectors.collectChanceItem(RecipeHandler.Recipe.PRECISION_SAWMILL.get());
            case STAMPING -> MachineRecipeRouteCollectors.collectBasicItem(RecipeHandler.Recipe.STAMPING.get());
            case ROLLING -> MachineRecipeRouteCollectors.collectBasicItem(RecipeHandler.Recipe.ROLLING.get());
            case BRUSHED -> MachineRecipeRouteCollectors.collectBasicItem(RecipeHandler.Recipe.BRUSHED.get());
            case TURNING -> MachineRecipeRouteCollectors.collectBasicItem(RecipeHandler.Recipe.TURNING.get());
            case AllOY -> MachineRecipeRouteCollectors.collectDoubleItem(RecipeHandler.Recipe.ALLOY.get());
            case EXTRACTOR -> MachineRecipeRouteCollectors.collectChanceItem(RecipeHandler.Recipe.CELL_EXTRACTOR.get());
            case SEPARATOR -> MachineRecipeRouteCollectors.collectChanceItem(RecipeHandler.Recipe.CELL_SEPARATOR.get());
            case FARM -> MachineRecipeRouteCollectors.collectFarmGasToItem(
                  RecipeHandler.Recipe.ORGANIC_FARM.get(), tile.getRecipeGasUsagePerOperation());
            case RECYCLER -> MachineRecipeRouteCollectors.collectGuaranteedChanceItem(RecipeHandler.Recipe.RECYCLER.get());
            case PRC -> MachineRecipeRouteCollectors.collectPressurized(RecipeHandler.Recipe.PRESSURIZED_REACTION_CHAMBER.get());
            case NUCLEOSYNTHESIZER -> MachineRecipeRouteCollectors.collectNucleosynthesizerGasToItem(
                  RecipeHandler.Recipe.ANTIPROTONIC_NUCLEOSYNTHESIZER.get());
        };
        return MachineRecipeRouteCollectors.expandLanes(routes, tile.getProcessCount(), getFactorySharedPorts(tile.getRecipeType()));
    }

    private static String[] getFactorySharedPorts(RecipeType type) {
        return switch (type) {
            case COMPRESSING, PURIFYING, INJECTING, FARM, NUCLEOSYNTHESIZER -> new String[]{"gas_input"};
            case COMBINING, INFUSING, AllOY -> new String[]{"extra_item_input"};
            case PRC -> new String[]{"fluid_input", "gas_input", "gas_output"};
            default -> new String[0];
        };
    }

    private static List<MachinePort> getFactoryPorts(TileEntityFactory tile) {
        List<MachinePort> ports = new ArrayList<>();
        RecipeType type = tile.getRecipeType();
        boolean hasSecondaryOutput = type == RecipeType.SAWING || type == RecipeType.EXTRACTOR ||
              type == RecipeType.SEPARATOR || type == RecipeType.FARM;
        for (int process = 0; process < tile.getProcessCount(); process++) {
            add(ports, MachinePort.item("item_input_" + process, MachinePort.Role.INPUT, tile.getRecipeInputSlot(process)));
            add(ports, MachinePort.item("item_output_" + process, MachinePort.Role.OUTPUT, tile.getRecipeOutputSlot(process)));
            if (hasSecondaryOutput) {
                add(ports, MachinePort.item("secondary_item_output_" + process, MachinePort.Role.OUTPUT,
                      tile.getRecipeSecondaryOutputSlot(process)));
            }
        }
        if (type == RecipeType.COMBINING || type == RecipeType.INFUSING || type == RecipeType.AllOY) {
            add(ports, MachinePort.item("extra_item_input", MachinePort.Role.INPUT, tile.getRecipeExtraInputSlot()));
        }
        if (type == RecipeType.COMPRESSING || type == RecipeType.PURIFYING || type == RecipeType.INJECTING ||
            type == RecipeType.FARM || type == RecipeType.NUCLEOSYNTHESIZER || type == RecipeType.PRC) {
            add(ports, MachinePort.gas("gas_input", MachinePort.Role.INPUT, tile.getInputGasTank()));
        }
        if (type == RecipeType.PRC) {
            add(ports, MachinePort.fluid("fluid_input", MachinePort.Role.INPUT, tile.getInputFluidTank()));
            add(ports, MachinePort.gas("gas_output", MachinePort.Role.OUTPUT, tile.getOutputGasTank()));
        }
        return ports;
    }

    private static void registerOutputMachines() {
        register("electric_pump", TileEntityElectricPump.class, ignored -> null, ignored -> Collections.emptyList(),
              tile -> ports(MachinePort.fluid("fluid_output", MachinePort.Role.OUTPUT, tile.fluidTank)));
        register("ambient_accumulator", TileEntityAmbientAccumulator.class, ignored -> null, ignored -> Collections.emptyList(),
              tile -> ports(MachinePort.gas("gas_output", MachinePort.Role.OUTPUT, tile.collectedGas)));
        register("ambient_accumulator_energy", TileEntityAmbientAccumulatorEnergy.class, ignored -> null,
              ignored -> Collections.emptyList(),
              tile -> ports(MachinePort.gas("gas_output", MachinePort.Role.OUTPUT, tile.outputTank)));
        register("digital_miner", TileEntityDigitalMiner.class, ignored -> null, ignored -> Collections.emptyList(),
              MekanismMachineRecipeProviders::getDigitalMinerPorts);
    }

    private static List<MachinePort> getDigitalMinerPorts(TileEntityDigitalMiner tile) {
        List<MachinePort> ports = new ArrayList<>();
        List<mekanism.api.inventory.IInventorySlot> slots = tile.getMiningOutputSlots();
        for (int index = 0; index < slots.size(); index++) {
            add(ports, MachinePort.item("item_output_" + index, MachinePort.Role.OUTPUT, slots.get(index),
                  AutomationType.EXTERNAL));
        }
        return ports;
    }

    private static List<MachinePort> ports(MachinePort... values) {
        List<MachinePort> ports = new ArrayList<>(values.length);
        for (MachinePort value : values) {
            add(ports, value);
        }
        return ports;
    }

    private static void add(List<MachinePort> ports, MachinePort port) {
        if (port != null) {
            ports.add(port);
        }
    }

    private static <TILE extends TileEntity> void register(String path, Class<TILE> tileClass,
          Function<TILE, Object> recipeSource, Function<TILE, List<MachineRecipeRoute>> routes,
          Function<TILE, List<MachinePort>> ports) {
        register(path, tileClass, recipeSource, routes, ports, ignored -> 0);
    }

    private static <TILE extends TileEntity> void register(String path, Class<TILE> tileClass,
          Function<TILE, Object> recipeSource, Function<TILE, List<MachineRecipeRoute>> routes,
          Function<TILE, List<MachinePort>> ports, ToIntFunction<TILE> configurationRevision) {
        MachineRecipeProviderRegistry.register(new ResourceLocation(Mekanism.MODID, path), tileClass,
              new MachineRecipeProvider<TILE>() {
                  @Override
                  public Object getRecipeSourceKey(TILE tile) {
                      return recipeSource.apply(tile);
                  }

                  @Override
                  public int getConfigurationRevision(TILE tile) {
                      return 31 * RecipeHandler.getGlobalRecipeVersion() + configurationRevision.applyAsInt(tile);
                  }

                  @Override
                  public List<MachineRecipeRoute> getRecipeRoutes(TILE tile) {
                      return routes.apply(tile);
                  }

                  @Override
                  public List<MachinePort> getPorts(TILE tile) {
                      return ports.apply(tile);
                  }
              });
    }
}
