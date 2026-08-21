package mekanism.common.integration.crafttweaker.handlers;

import crafttweaker.CraftTweakerAPI;
import crafttweaker.annotations.ZenRegister;
import crafttweaker.api.item.IIngredient;
import crafttweaker.api.item.IItemStack;
import crafttweaker.api.liquid.ILiquidStack;
import crafttweaker.api.minecraft.CraftTweakerMC;
import mekanism.api.gas.GasStack;
import mekanism.api.recipes.FarmChanceOutput;
import mekanism.common.Mekanism;
import mekanism.common.integration.crafttweaker.CrafttweakerIntegration;
import mekanism.common.integration.crafttweaker.gas.IGasStack;
import mekanism.common.integration.crafttweaker.helpers.GasHelper;
import mekanism.common.integration.crafttweaker.helpers.IngredientHelper;
import mekanism.common.integration.crafttweaker.util.AddMekanismRecipe;
import mekanism.common.integration.crafttweaker.util.IngredientWrapper;
import mekanism.common.integration.crafttweaker.util.RemoveAllMekanismRecipe;
import mekanism.common.integration.crafttweaker.util.RemoveMekanismRecipe;
import mekanism.common.recipe.RecipeHandler.Recipe;
import mekanism.common.recipe.inputs.FarmInput;
import mekanism.common.recipe.machines.FarmRecipe;
import mekanism.common.recipe.outputs.FarmOutput;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import stanhebben.zenscript.annotations.Optional;
import stanhebben.zenscript.annotations.ZenClass;
import stanhebben.zenscript.annotations.ZenMethod;

import java.util.ArrayList;
import java.util.List;

@ZenClass("mods.mekanism.organicfarm")
@ZenRegister
public class OrganicFarm {
    public static final String NAME = Mekanism.MOD_NAME + " Organic Farm";

    @ZenMethod
    public static void addRecipe(IIngredient ingredientInput, IGasStack gasInput, IItemStack itemOutput, @Optional IItemStack optionalItemOutput, @Optional double optionalChance) {
        if (IngredientHelper.checkNotNull(NAME, ingredientInput, gasInput, itemOutput)) {
            FarmOutput output = optionalItemOutput == null ? new FarmOutput(CraftTweakerMC.getItemStack(itemOutput)) : new FarmOutput(CraftTweakerMC.getItemStack(itemOutput),
                    CraftTweakerMC.getItemStack(optionalItemOutput), optionalChance);
            addGasRecipes(ingredientInput, GasHelper.toGas(gasInput), output);
        }
    }

    @ZenMethod
    public static void addRecipe(IIngredient ingredientInput, ILiquidStack fluidInput, IItemStack itemOutput, @Optional IItemStack optionalItemOutput,
          @Optional double optionalChance) {
        if (IngredientHelper.checkNotNull(NAME, ingredientInput, fluidInput, itemOutput)) {
            FarmOutput output = optionalItemOutput == null ? new FarmOutput(CraftTweakerMC.getItemStack(itemOutput)) : new FarmOutput(CraftTweakerMC.getItemStack(itemOutput),
                  CraftTweakerMC.getItemStack(optionalItemOutput), optionalChance);
            addFluidRecipes(ingredientInput, IngredientHelper.toFluid(fluidInput), output);
        }
    }

    @ZenMethod
    public static void addRecipe(IIngredient ingredientInput, IGasStack gasInput, IItemStack itemOutput, IItemStack[] chanceItemOutputs, double[] chances) {
        if (IngredientHelper.checkNotNull(NAME, ingredientInput, gasInput, itemOutput)) {
            FarmOutput output = createOutput(itemOutput, chanceItemOutputs, chances);
            if (output != null) {
                addGasRecipes(ingredientInput, GasHelper.toGas(gasInput), output);
            }
        }
    }

    @ZenMethod
    public static void addRecipe(IIngredient ingredientInput, ILiquidStack fluidInput, IItemStack itemOutput, IItemStack[] chanceItemOutputs, double[] chances) {
        if (IngredientHelper.checkNotNull(NAME, ingredientInput, fluidInput, itemOutput)) {
            FarmOutput output = createOutput(itemOutput, chanceItemOutputs, chances);
            if (output != null) {
                addFluidRecipes(ingredientInput, IngredientHelper.toFluid(fluidInput), output);
            }
        }
    }

    private static FarmOutput createOutput(IItemStack itemOutput, IItemStack[] chanceItemOutputs, double[] chances) {
        if (chanceItemOutputs == null || chances == null || chanceItemOutputs.length != chances.length) {
            CraftTweakerAPI.logError(NAME + " chance output and chance arrays must be non-null and have the same length");
            return null;
        }
        if (chanceItemOutputs.length > FarmOutput.MAX_CHANCE_OUTPUTS) {
            CraftTweakerAPI.logError(NAME + " supports at most " + FarmOutput.MAX_CHANCE_OUTPUTS + " chance outputs");
            return null;
        }
        List<FarmChanceOutput> outputs = new ArrayList<>(chanceItemOutputs.length);
        for (int i = 0; i < chanceItemOutputs.length; i++) {
            if (chanceItemOutputs[i] == null) {
                CraftTweakerAPI.logError(NAME + " chance output at index " + i + " must not be null");
                return null;
            }
            if (Double.isNaN(chances[i]) || Double.isInfinite(chances[i]) || chances[i] < 0 || chances[i] > 1) {
                CraftTweakerAPI.logError(NAME + " chance at index " + i + " must be between 0 and 1");
                return null;
            }
            outputs.add(new FarmChanceOutput(CraftTweakerMC.getItemStack(chanceItemOutputs[i]), chances[i]));
        }
        return new FarmOutput(CraftTweakerMC.getItemStack(itemOutput), outputs);
    }

    private static void addGasRecipes(IIngredient ingredientInput, GasStack gasInput, FarmOutput output) {
        List<FarmRecipe> recipes = new ArrayList<>();
        for (ItemStack stack : CraftTweakerMC.getIngredient(ingredientInput).getMatchingStacks()) {
            recipes.add(new FarmRecipe(new FarmInput(stack, gasInput), output));
        }
        CrafttweakerIntegration.LATE_ADDITIONS.add(new AddMekanismRecipe<>(NAME, Recipe.ORGANIC_FARM, recipes));
    }

    private static void addFluidRecipes(IIngredient ingredientInput, FluidStack fluidInput, FarmOutput output) {
        List<FarmRecipe> recipes = new ArrayList<>();
        for (ItemStack stack : CraftTweakerMC.getIngredient(ingredientInput).getMatchingStacks()) {
            recipes.add(new FarmRecipe(new FarmInput(stack, fluidInput), output));
        }
        CrafttweakerIntegration.LATE_ADDITIONS.add(new AddMekanismRecipe<>(NAME, Recipe.ORGANIC_FARM, recipes));
    }

    @ZenMethod
    public static void removeRecipe(IIngredient itemInput, @Optional IIngredient gasInput, @Optional IIngredient itemOutput, @Optional IIngredient optionalItemOutput) {
        if (IngredientHelper.checkNotNull(NAME, itemInput)) {
            CrafttweakerIntegration.LATE_REMOVALS.add(new RemoveMekanismRecipe<>(NAME, Recipe.ORGANIC_FARM, new IngredientWrapper(itemOutput, optionalItemOutput),
                    new IngredientWrapper(itemInput, gasInput)));
        }
    }

    @ZenMethod
    public static void removeAllRecipes() {
        CrafttweakerIntegration.LATE_REMOVALS.add(new RemoveAllMekanismRecipe<>(NAME, Recipe.ORGANIC_FARM));
    }


}
