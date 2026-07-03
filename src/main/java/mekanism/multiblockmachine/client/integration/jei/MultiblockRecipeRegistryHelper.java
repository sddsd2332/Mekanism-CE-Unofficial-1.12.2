package mekanism.multiblockmachine.client.integration.jei;

import mekanism.common.recipe.RecipeHandler;
import mekanism.common.recipe.RecipeHandler.Recipe;
import mekanism.multiblockmachine.client.gui.generator.GuiLargeGasGenerator;
import mekanism.multiblockmachine.client.gui.machine.GuiLargeChemicalInfuser;
import mekanism.multiblockmachine.client.gui.machine.GuiLargeChemicalWasher;
import mekanism.multiblockmachine.client.gui.machine.GuiLargeElectrolyticSeparator;
import mekanism.multiblockmachine.client.gui.machine.GuiLargeSolarNeutronActivator;
import mekanism.multiblockmachine.common.registries.MultiblockMachineBlocks;
import mezz.jei.api.IModRegistry;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;

public class MultiblockRecipeRegistryHelper {

    public static void registerLargeSeparator(IModRegistry registry) {
        registerRecipeItem(registry, MultiblockMachineBlocks.LargeElectrolyticSeparator, Recipe.ELECTROLYTIC_SEPARATOR.getJEICategory());
    }

    public static void registerLargeChemicalInfuser(IModRegistry registry) {
        registerRecipeItem(registry, MultiblockMachineBlocks.LargeChemicalInfuser, Recipe.CHEMICAL_INFUSER.getJEICategory());
    }

    public static void registerLargeChemicalWasher(IModRegistry registry) {
        registerRecipeItem(registry, MultiblockMachineBlocks.LargeChemicalWasher, Recipe.CHEMICAL_WASHER.getJEICategory());
    }

    public static void registerGasStackFlueToEnergyRecipe(IModRegistry registry) {
        registerRecipeItem(registry, MultiblockMachineBlocks.LargeGasGenerator, RecipeHandler.Recipe.GAS_FUEL_TO_ENERGY_RECIPE.getJEICategory());
    }

    public static void registerLargeSolarNeutronActivator(IModRegistry registry) {
        registerRecipeItem(registry, MultiblockMachineBlocks.LargeSolarNeutronActivator, RecipeHandler.Recipe.SOLAR_NEUTRON_ACTIVATOR.getJEICategory());
    }

    private static void registerRecipeItem(IModRegistry registry, Block block, String... recipe) {
        ItemStack add = new ItemStack(block);
        registry.addRecipeCatalyst(add, recipe);
    }
}
