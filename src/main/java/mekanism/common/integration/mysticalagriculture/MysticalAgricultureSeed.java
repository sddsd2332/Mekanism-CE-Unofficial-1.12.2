package mekanism.common.integration.mysticalagriculture;

import com.blakebr0.mysticalagriculture.config.ModConfig;
import com.blakebr0.mysticalagriculture.lib.CropType;
import mekanism.api.recipes.FarmChanceOutput;
import mekanism.common.MekanismFluids;
import mekanism.common.config.MekanismConfig;
import mekanism.common.recipe.RecipeHandler;
import mekanism.common.recipe.inputs.FarmInput;
import mekanism.common.recipe.machines.FarmRecipe;
import mekanism.common.util.StackUtils;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

import static mekanism.common.integration.MekanismHooks.MYSTICALAGRICULTURE_MOD_ID;


/**
 * This code is obtained through CofhCore.
 */
public class MysticalAgricultureSeed {

    public static void seed() {

        ItemStack fertilizedEssence = getItemStack("fertilized_essence", 1, 0);
        double seedChance = MekanismConfig.current().mekce.seed.val();
        if (ModConfig.confFertilizedEssence && !fertilizedEssence.isEmpty()) {

            for (CropType.Type type : CropType.Type.values()) {
                if (!type.isEnabled() || type.getSeed() == null || type.getCrop() == null) {
                    continue;
                }
                ItemStack seeds = new ItemStack(type.getSeed());
                FarmInput nutrientInput = new FarmInput(seeds, MekanismFluids.NutrientSolution);
                FarmInput waterInput = new FarmInput(seeds, new FluidStack(FluidRegistry.WATER, 1));
                FarmRecipe nutrientRecipe = RecipeHandler.Recipe.ORGANIC_FARM.get().get(nutrientInput);
                FarmRecipe waterRecipe = RecipeHandler.Recipe.ORGANIC_FARM.get().get(waterInput);
                if (nutrientRecipe != null) {
                    RecipeHandler.Recipe.ORGANIC_FARM.remove(nutrientRecipe);
                }
                if (waterRecipe != null) {
                    RecipeHandler.Recipe.ORGANIC_FARM.remove(waterRecipe);
                }
                List<FarmChanceOutput> nutrientOutputs = new ArrayList<>();
                nutrientOutputs.add(new FarmChanceOutput(new ItemStack(type.getSeed(), 4), seedChance));
                nutrientOutputs.add(new FarmChanceOutput(StackUtils.size(fertilizedEssence,4), 0.05D));
                List<FarmChanceOutput> waterOutputs = new ArrayList<>();
                waterOutputs.add(new FarmChanceOutput(new ItemStack(type.getSeed()), seedChance));
                waterOutputs.add(new FarmChanceOutput(fertilizedEssence, 0.05D));
                RecipeHandler.addOrganicFarmRecipe(seeds, MekanismFluids.NutrientSolution, new ItemStack(type.getCrop(), 24), nutrientOutputs);
                RecipeHandler.addOrganicFarmRecipe(seeds, new FluidStack(FluidRegistry.WATER, 1), new ItemStack(type.getCrop(), 3), waterOutputs);
            }
        }

        for (int i = 1; i <= 5; i++) {
            ItemStack seeds = getSeeds("tier" + i + "_inferium");
            List<FarmChanceOutput> nutrientOutputs = new ArrayList<>();
            nutrientOutputs.add(new FarmChanceOutput(new ItemStack(seeds.getItem(), 4), seedChance));
            List<FarmChanceOutput> waterOutputs = new ArrayList<>();
            waterOutputs.add(new FarmChanceOutput(new ItemStack(seeds.getItem()), seedChance));
            if (ModConfig.confFertilizedEssence && !fertilizedEssence.isEmpty()) {
                ItemStack nutrientFertilizer = fertilizedEssence.copy();
                nutrientFertilizer.setCount(4);
                nutrientOutputs.add(new FarmChanceOutput(nutrientFertilizer, 0.05D));
                waterOutputs.add(new FarmChanceOutput(fertilizedEssence, 0.05D));
            }
            if (seeds != ItemStack.EMPTY) {
                if (RecipeHandler.Recipe.ORGANIC_FARM.containsRecipe(seeds)) {
                    RecipeHandler.Recipe.ORGANIC_FARM.remove(RecipeHandler.Recipe.ORGANIC_FARM.get().get(new FarmInput(seeds, MekanismFluids.NutrientSolution)));
                    RecipeHandler.Recipe.ORGANIC_FARM.remove(RecipeHandler.Recipe.ORGANIC_FARM.get().get(new FarmInput(seeds, new FluidStack(FluidRegistry.WATER, 1))));
                    RecipeHandler.addOrganicFarmRecipe(seeds, MekanismFluids.NutrientSolution, getItemStack("crafting", i * 10, 0), nutrientOutputs);
                    RecipeHandler.addOrganicFarmRecipe(seeds, new FluidStack(FluidRegistry.WATER, 1), getItemStack("crafting", i * 5, 0), waterOutputs);
                } else if (!RecipeHandler.Recipe.ORGANIC_FARM.containsRecipe(seeds)) {
                    RecipeHandler.addOrganicFarmRecipe(seeds, MekanismFluids.NutrientSolution, getItemStack("crafting", i * 10, 0), nutrientOutputs);
                    RecipeHandler.addOrganicFarmRecipe(seeds, new FluidStack(FluidRegistry.WATER, 1), getItemStack("crafting", i * 5, 0), waterOutputs);
                }
            }
        }


        if (Loader.isModLoaded("mysticalagradditions")) {
            ItemStack tier6seeds = getSeeds2("tier6_inferium");
            List<FarmChanceOutput> nutrientOutputs = new ArrayList<>();
            nutrientOutputs.add(new FarmChanceOutput(new ItemStack(tier6seeds.getItem(), 4), seedChance));
            List<FarmChanceOutput> waterOutputs = new ArrayList<>();
            waterOutputs.add(new FarmChanceOutput(new ItemStack(tier6seeds.getItem()), seedChance));
            if (ModConfig.confFertilizedEssence && !fertilizedEssence.isEmpty()) {
                nutrientOutputs.add(new FarmChanceOutput(StackUtils.size(fertilizedEssence,4), 0.05D));
                waterOutputs.add(new FarmChanceOutput(fertilizedEssence, 0.05D));
            }
            if (tier6seeds != ItemStack.EMPTY) {
                if (RecipeHandler.Recipe.ORGANIC_FARM.containsRecipe(tier6seeds)) {
                    RecipeHandler.Recipe.ORGANIC_FARM.remove(RecipeHandler.Recipe.ORGANIC_FARM.get().get(new FarmInput(tier6seeds, MekanismFluids.NutrientSolution)));
                    RecipeHandler.Recipe.ORGANIC_FARM.remove(RecipeHandler.Recipe.ORGANIC_FARM.get().get(new FarmInput(tier6seeds, new FluidStack(FluidRegistry.WATER, 1))));
                    RecipeHandler.addOrganicFarmRecipe(tier6seeds, MekanismFluids.NutrientSolution, getItemStack("crafting", 60, 0), nutrientOutputs);
                    RecipeHandler.addOrganicFarmRecipe(tier6seeds, new FluidStack(FluidRegistry.WATER, 1), getItemStack("crafting", 30, 0), waterOutputs);
                } else if (!RecipeHandler.Recipe.ORGANIC_FARM.containsRecipe(tier6seeds)) {
                    RecipeHandler.addOrganicFarmRecipe(tier6seeds, MekanismFluids.NutrientSolution, getItemStack("crafting", 60, 0), nutrientOutputs);
                    RecipeHandler.addOrganicFarmRecipe(tier6seeds, new FluidStack(FluidRegistry.WATER, 1), getItemStack("crafting", 30, 0), waterOutputs);
                }
            }
        }
    }


    protected static ItemStack getItemStack(String id, String name, int amount, int meta) {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id + ":" + name));
        return item != null ? new ItemStack(item, amount, meta) : ItemStack.EMPTY;
    }

    protected static ItemStack getSeeds(String name) {
        return getItemStack(MYSTICALAGRICULTURE_MOD_ID, name + "_seeds", 1, 0);
    }

    protected static ItemStack getSeeds2(String name) {
        return getItemStack("mysticalagradditions", name + "_seeds", 1, 0);
    }

    protected static ItemStack getItemStack(String name, int amount, int meta) {
        return getItemStack(MYSTICALAGRICULTURE_MOD_ID, name, amount, meta);
    }
}
