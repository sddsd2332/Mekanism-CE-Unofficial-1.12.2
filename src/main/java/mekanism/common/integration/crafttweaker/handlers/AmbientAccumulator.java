package mekanism.common.integration.crafttweaker.handlers;

import crafttweaker.annotations.ZenRegister;
import crafttweaker.api.item.IIngredient;
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
import mekanism.common.recipe.machines.AmbientGasRecipe;
import stanhebben.zenscript.annotations.Optional;
import stanhebben.zenscript.annotations.ZenClass;
import stanhebben.zenscript.annotations.ZenMethod;

@ZenClass("mods.mekanism.ambientaccumulator")
@ZenRegister
public class AmbientAccumulator {

    public static final String NAME = Mekanism.MOD_NAME + " Ambient Accumulator";

    @ZenMethod
    public static void addRecipe(int Input, IGasStack gasOutput, double chance) {
        if (IngredientHelper.checkNotNull(NAME, gasOutput)) {
            CrafttweakerIntegration.LATE_ADDITIONS.add(new AddMekanismRecipe<>(NAME, Recipe.AMBIENT_ACCUMULATOR, new AmbientGasRecipe(Input, GasHelper.toGas(gasOutput), chance)));
        }
    }

    @ZenMethod
    public static void removeRecipe(IIngredient Input, @Optional IIngredient gasOutput) {
        if (IngredientHelper.checkNotNull(NAME, Input)) {
            CrafttweakerIntegration.LATE_REMOVALS.add(new RemoveMekanismRecipe<>(NAME, Recipe.AMBIENT_ACCUMULATOR, new IngredientWrapper(gasOutput),
                    new IngredientWrapper(Input)));
        }
    }

    @ZenMethod
    public static void removeAllRecipes() {
        CrafttweakerIntegration.LATE_REMOVALS.add(new RemoveAllMekanismRecipe<>(NAME, Recipe.AMBIENT_ACCUMULATOR));
    }
}