package mekanism.common.integration.mysticalagriculture;

import mekanism.common.MekanismFluids;
import mekanism.common.config.MekanismConfig;
import mekanism.common.recipe.RecipeHandler;
import mekanism.common.recipe.inputs.FarmInput;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Mystical Creations organic farm compat.
 */
public class MysticalCreationsSeed {

    private static final String CUSTOM_SEEDS_CLASS = "com.focamacho.mysticalcreations.seeds.CustomSeeds";

    public static void seed() {
        List<?> seeds = getCustomSeeds();
        if (seeds == null || seeds.isEmpty()) {
            return;
        }

        for (Object customSeed : seeds) {
            ItemStack seedStack = getSeedStack(customSeed);
            ItemStack essenceStack = getEssenceStack(customSeed);
            int tier = getTier(customSeed);

            if (seedStack.isEmpty() || essenceStack.isEmpty()) {
                continue;
            }

            if (RecipeHandler.Recipe.ORGANIC_FARM.containsRecipe(seedStack)) {
                RecipeHandler.Recipe.ORGANIC_FARM.remove(RecipeHandler.Recipe.ORGANIC_FARM.get().get(new FarmInput(seedStack, MekanismFluids.NutrientSolution)));
                RecipeHandler.Recipe.ORGANIC_FARM.remove(RecipeHandler.Recipe.ORGANIC_FARM.get().get(new FarmInput(seedStack, new FluidStack(FluidRegistry.WATER, 1))));
            }

            RecipeHandler.addOrganicFarmRecipe(seedStack, MekanismFluids.NutrientSolution, new ItemStack(essenceStack.getItem(), clampAmount(tier * 10), essenceStack.getMetadata()), new ItemStack(seedStack.getItem(), 4, seedStack.getMetadata()), MekanismConfig.current().mekce.seed.val());
            RecipeHandler.addOrganicFarmRecipe(seedStack, new FluidStack(FluidRegistry.WATER, 1), new ItemStack(essenceStack.getItem(), clampAmount(tier * 5), essenceStack.getMetadata()), new ItemStack(seedStack.getItem(), 1, seedStack.getMetadata()), MekanismConfig.current().mekce.seed.val());
        }
    }

    private static List<?> getCustomSeeds() {
        try {
            Class<?> customSeedsClass = Class.forName(CUSTOM_SEEDS_CLASS);
            Field allSeedsField = customSeedsClass.getField("allSeeds");
            Object value = allSeedsField.get(null);
            if (value instanceof List<?>) {
                return (List<?>) value;
            }
            return null;
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }

    private static ItemStack getSeedStack(Object customSeed) {
        return getItemStack(customSeed, "getSeed");
    }

    private static ItemStack getEssenceStack(Object customSeed) {
        return getItemStack(customSeed, "getEssence");
    }

    private static int getTier(Object customSeed) {
        try {
            Method method = customSeed.getClass().getMethod("getTier");
            Object value = method.invoke(customSeed);
            if (value instanceof Integer) {
                return (Integer) value;
            }
            return 1;
        } catch (ReflectiveOperationException | LinkageError e) {
            return 1;
        }
    }

    private static ItemStack getItemStack(Object customSeed, String methodName) {
        try {
            Method method = customSeed.getClass().getMethod(methodName);
            Object value = method.invoke(customSeed);
            if (value instanceof Item) {
                return new ItemStack((Item) value);
            }
        } catch (ReflectiveOperationException | LinkageError e) {
            return ItemStack.EMPTY;
        }
        return ItemStack.EMPTY;
    }

    private static int clampAmount(int amount) {
        return Math.min(Math.max(1, amount), 64);
    }
}
