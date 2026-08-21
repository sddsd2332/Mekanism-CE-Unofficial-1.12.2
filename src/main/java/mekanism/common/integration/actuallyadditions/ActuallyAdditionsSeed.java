package mekanism.common.integration.actuallyadditions;

import mekanism.common.MekanismFluids;
import mekanism.common.config.MekanismConfig;
import mekanism.common.recipe.RecipeHandler;
import mekanism.common.recipe.inputs.FarmInput;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

/**
 * Actually Additions organic farm compat.
 */
public class ActuallyAdditionsSeed {

    public static void seed() {
        register("actuallyadditions:item_rice_seed", new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("actuallyadditions:item_food")), 3, 17), new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("actuallyadditions:item_food")), 24, 17), 4);
        register("actuallyadditions:item_canola_seed", new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("actuallyadditions:item_misc")), 3, 13), new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("actuallyadditions:item_misc")), 24, 13), 4);
        register("actuallyadditions:item_flax_seed", new ItemStack(Items.STRING, 3), new ItemStack(Items.STRING, 24), 4);
        register("actuallyadditions:item_coffee_seed", new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("actuallyadditions:item_coffee_beans")), 3), new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("actuallyadditions:item_coffee_beans")), 24), 4);
    }

    private static void register(String seedId, ItemStack waterOutput, ItemStack nutrientOutput, int seedReturnAmount) {
        ItemStack seedStack = getItemStack(seedId, 1, 0);
        if (seedStack.isEmpty()) {
            return;
        }

        if (RecipeHandler.Recipe.ORGANIC_FARM.containsRecipe(seedStack)) {
            RecipeHandler.Recipe.ORGANIC_FARM.remove(RecipeHandler.Recipe.ORGANIC_FARM.get().get(new FarmInput(seedStack, MekanismFluids.NutrientSolution)));
            RecipeHandler.Recipe.ORGANIC_FARM.remove(RecipeHandler.Recipe.ORGANIC_FARM.get().get(new FarmInput(seedStack, new FluidStack(FluidRegistry.WATER, 1))));
        }

        RecipeHandler.addOrganicFarmRecipe(seedStack, MekanismFluids.NutrientSolution, nutrientOutput, new ItemStack(seedStack.getItem(), seedReturnAmount), MekanismConfig.current().mekce.seed.val());
        RecipeHandler.addOrganicFarmRecipe(seedStack, new FluidStack(FluidRegistry.WATER, 1), waterOutput, new ItemStack(seedStack.getItem(), 1), MekanismConfig.current().mekce.seed.val());
    }

    private static ItemStack getItemStack(String id, int amount, int meta) {
        net.minecraft.item.Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
        return item == null ? ItemStack.EMPTY : new ItemStack(item, amount, meta);
    }
}
