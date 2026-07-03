package mekanism.generators.client.jei;

import mekanism.common.recipe.RecipeHandler;
import mekanism.common.recipe.inputs.MachineInput;
import mekanism.common.recipe.machines.MachineRecipe;
import mekanism.common.recipe.outputs.MachineOutput;
import mekanism.generators.client.gui.GuiFissionReactor;
import mekanism.generators.client.gui.GuiFusionReactorHeat;
import mekanism.generators.client.gui.GuiGasGenerator;
import mekanism.generators.client.jei.machine.other.FissionReactorRecipeCategory;
import mekanism.generators.client.jei.machine.other.FissionReactorRecipeWrapper;
import mekanism.generators.client.jei.machine.other.FusionCoolingRecipeWrapper;
import mekanism.generators.client.jei.machine.other.GasStackFlueToEnergyRecipeWrapper;
import mekanism.generators.common.block.states.BlockStateGenerator;
import mekanism.generators.common.block.states.BlockStateReactor;
import mezz.jei.api.IModRegistry;
import mezz.jei.api.recipe.IRecipeWrapperFactory;

import java.util.Arrays;
import java.util.stream.Collectors;


public class GeneratorRecipeRegistryHelper {

    public static void registerFissionReactor(IModRegistry registry) {
        registry.addRecipes(Arrays.asList(
                FissionReactorRecipeWrapper.waterCooled(),
                FissionReactorRecipeWrapper.sodiumCooled()
        ), FissionReactorRecipeCategory.UID);
        registry.addRecipeCatalyst(BlockStateGenerator.GeneratorType.FISSION_REACTOR_CASING.getStack(), FissionReactorRecipeCategory.UID);
        registry.addRecipeCatalyst(BlockStateGenerator.GeneratorType.FISSION_REACTOR_PORT.getStack(), FissionReactorRecipeCategory.UID);
        registry.addRecipeCatalyst(BlockStateGenerator.GeneratorType.FISSION_REACTOR_LOGIC_ADAPTER.getStack(), FissionReactorRecipeCategory.UID);
        registry.addRecipeCatalyst(BlockStateGenerator.GeneratorType.FISSION_FUEL_ASSEMBLY.getStack(), FissionReactorRecipeCategory.UID);
        registry.addRecipeCatalyst(BlockStateGenerator.GeneratorType.CONTROL_ROD_ASSEMBLY.getStack(), FissionReactorRecipeCategory.UID);
    }


    public static void registerFusionCooling(IModRegistry registry) {
        addRecipes(registry, RecipeHandler.Recipe.FUSION_COOLING, FusionCoolingRecipeWrapper::new);
        registry.addRecipeCatalyst(BlockStateReactor.ReactorBlockType.REACTOR_CONTROLLER.getStack(1), RecipeHandler.Recipe.FUSION_COOLING.getJEICategory());
        registry.addRecipeCatalyst(BlockStateReactor.ReactorBlockType.REACTOR_PORT.getStack(1), RecipeHandler.Recipe.FUSION_COOLING.getJEICategory());
    }

    public static void registerGasStackFlueToEnergyRecipe(IModRegistry registry) {
        addRecipes(registry, RecipeHandler.Recipe.GAS_FUEL_TO_ENERGY_RECIPE, GasStackFlueToEnergyRecipeWrapper::new);
        registry.addRecipeCatalyst(BlockStateGenerator.GeneratorType.GAS_GENERATOR.getStack(), RecipeHandler.Recipe.GAS_FUEL_TO_ENERGY_RECIPE.getJEICategory());
    }

    private static <INPUT extends MachineInput<INPUT>, OUTPUT extends MachineOutput<OUTPUT>, RECIPE extends MachineRecipe<INPUT, OUTPUT, RECIPE>>
    void addRecipes(IModRegistry registry, RecipeHandler.Recipe<INPUT, OUTPUT, RECIPE> type, IRecipeWrapperFactory<RECIPE> factory) {
        String recipeCategoryUid = type.getJEICategory();
        registry.handleRecipes(type.getRecipeClass(), factory, recipeCategoryUid);
        registry.addRecipes(type.get().values().stream().map(factory::getRecipeWrapper).collect(Collectors.toList()), recipeCategoryUid);
    }
}
