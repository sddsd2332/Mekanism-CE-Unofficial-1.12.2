package mekanism.multiblockmachine.common.processing;

import mekanism.api.processing.MachinePort;
import mekanism.api.processing.MachineRecipeProvider;
import mekanism.api.processing.MachineRecipeProviderRegistry;
import mekanism.api.processing.MachineRecipeRoute;
import mekanism.api.processing.ProviderConformanceDescriptor;
import mekanism.api.processing.QIOAutomationMode;
import mekanism.common.recipe.RecipeHandler;
import mekanism.common.recipe.processing.MachineRecipeRouteCollectors;
import mekanism.multiblockmachine.common.MekanismMultiblockMachine;
import mekanism.multiblockmachine.common.tile.machine.TileEntityLargeChemicalInfuser;
import mekanism.multiblockmachine.common.tile.machine.TileEntityLargeChemicalWasher;
import mekanism.multiblockmachine.common.tile.machine.TileEntityLargeElectrolyticSeparator;
import mekanism.multiblockmachine.common.tile.machine.TileEntityLargeSolarNeutronActivator;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Registers multiblock module machines with Mekanism's shared processing bridge. */
public final class MultiblockMachineRecipeProviders {

    private static boolean registered;
    private static final ProviderConformanceDescriptor QIO_CONFORMANCE =
          ProviderConformanceDescriptor.builder(MekanismMultiblockMachine.MODID, "main")
                .supports(QIOAutomationMode.SCHEDULED, QIOAutomationMode.PASSIVE, QIOAutomationMode.OUTPUT_ONLY)
                .build();

    private MultiblockMachineRecipeProviders() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        register("large_chemical_infuser", TileEntityLargeChemicalInfuser.class, TileEntityLargeChemicalInfuser::getRecipes,
              tile -> MachineRecipeRouteCollectors.collectChemicalPairToGas(tile.getRecipes()),
              tile -> ports(
                    MachinePort.gas("left_gas", MachinePort.Role.INPUT, tile.leftTank),
                    MachinePort.gas("right_gas", MachinePort.Role.INPUT, tile.rightTank),
                    MachinePort.gas("gas_output", MachinePort.Role.OUTPUT, tile.centerTank)));

        register("large_chemical_washer", TileEntityLargeChemicalWasher.class, TileEntityLargeChemicalWasher::getRecipes,
              tile -> MachineRecipeRouteCollectors.collectGasFluidToGas(tile.getRecipes()),
              tile -> ports(
                    MachinePort.gas("gas_input", MachinePort.Role.INPUT, tile.inputTank),
                    MachinePort.fluid("fluid_input", MachinePort.Role.INPUT, tile.fluidTank),
                    MachinePort.gas("gas_output", MachinePort.Role.OUTPUT, tile.outputTank)));

        register("large_electrolytic_separator", TileEntityLargeElectrolyticSeparator.class,
              TileEntityLargeElectrolyticSeparator::getRecipes,
              tile -> MachineRecipeRouteCollectors.collectFluidToGasPair(tile.getRecipes()),
              tile -> ports(
                    MachinePort.fluid("fluid_input", MachinePort.Role.INPUT, tile.fluidTank),
                    MachinePort.gas("left_gas_output", MachinePort.Role.OUTPUT, tile.leftTank),
                    MachinePort.gas("right_gas_output", MachinePort.Role.OUTPUT, tile.rightTank)));

        register("large_solar_neutron_activator", TileEntityLargeSolarNeutronActivator.class,
              ignored -> RecipeHandler.Recipe.SOLAR_NEUTRON_ACTIVATOR.get(),
              ignored -> MachineRecipeRouteCollectors.collectGasToGas(RecipeHandler.Recipe.SOLAR_NEUTRON_ACTIVATOR.get()),
              tile -> ports(
                    MachinePort.gas("gas_input", MachinePort.Role.INPUT, tile.inputTank),
                    MachinePort.gas("gas_output", MachinePort.Role.OUTPUT, tile.outputTank)));
        registered = true;
    }

    private static List<MachinePort> ports(MachinePort... values) {
        List<MachinePort> ports = new ArrayList<>(values.length);
        for (MachinePort value : values) {
            if (value != null) {
                ports.add(value);
            }
        }
        return ports;
    }

    private static <TILE extends TileEntity> void register(String path, Class<TILE> tileClass,
          Function<TILE, Object> recipeSource, Function<TILE, List<MachineRecipeRoute>> routes,
          Function<TILE, List<MachinePort>> ports) {
        MachineRecipeProviderRegistry.register(new ResourceLocation(MekanismMultiblockMachine.MODID, path), tileClass,
              new MachineRecipeProvider<TILE>() {
                  @Override
                  public Object getRecipeSourceKey(TILE tile) {
                      return recipeSource.apply(tile);
                  }

                  @Override
                  public int getConfigurationRevision(TILE tile) {
                       return RecipeHandler.foldRecipeGeneration(RecipeHandler.getGlobalRecipeGeneration(), 0);
                  }

                  @Override
                  public List<MachineRecipeRoute> getRecipeRoutes(TILE tile) {
                      return routes.apply(tile);
                  }

                  @Override
                  public List<MachinePort> getPorts(TILE tile) {
                      return ports.apply(tile);
                  }

                  @Override
                  public ProviderConformanceDescriptor getQIOConformance(TILE tile) {
                      return QIO_CONFORMANCE;
                  }
              });
    }
}
