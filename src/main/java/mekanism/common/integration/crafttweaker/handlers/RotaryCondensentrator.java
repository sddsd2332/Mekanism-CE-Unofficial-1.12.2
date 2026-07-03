package mekanism.common.integration.crafttweaker.handlers;

import crafttweaker.annotations.ZenRegister;
import crafttweaker.api.item.IIngredient;
import crafttweaker.api.liquid.ILiquidStack;
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
import mekanism.common.recipe.machines.RotaryRecipe;
import net.minecraftforge.fluids.FluidStack;
import stanhebben.zenscript.annotations.Optional;
import stanhebben.zenscript.annotations.ZenClass;
import stanhebben.zenscript.annotations.ZenMethod;

@ZenClass("mods.mekanism.rotarycondensentrator")
@ZenRegister
public class RotaryCondensentrator {

    public static final String NAME = Mekanism.MOD_NAME + " Rotary Condensentrator";

    @ZenMethod
    public static void addRecipe(ILiquidStack fluidInput, IGasStack gasInput, IGasStack gasOutput, ILiquidStack fluidOutput) {
        if (IngredientHelper.checkNotNull(NAME, fluidInput, gasInput, gasOutput, fluidOutput)) {
            addRecipe(IngredientHelper.toFluid(fluidInput), GasHelper.toGas(gasInput), GasHelper.toGas(gasOutput), IngredientHelper.toFluid(fluidOutput));
        }
    }

    @ZenMethod
    public static void addFluidToGasRecipe(ILiquidStack fluidInput, IGasStack gasOutput) {
        if (IngredientHelper.checkNotNull(NAME, fluidInput, gasOutput)) {
            addRecipe(IngredientHelper.toFluid(fluidInput), null, GasHelper.toGas(gasOutput), null);
        }
    }

    @ZenMethod
    public static void addGasToFluidRecipe(IGasStack gasInput, ILiquidStack fluidOutput) {
        if (IngredientHelper.checkNotNull(NAME, gasInput, fluidOutput)) {
            addRecipe(null, GasHelper.toGas(gasInput), null, IngredientHelper.toFluid(fluidOutput));
        }
    }

    private static void addRecipe(FluidStack fluidInput, mekanism.api.gas.GasStack gasInput, mekanism.api.gas.GasStack gasOutput, FluidStack fluidOutput) {
        CrafttweakerIntegration.LATE_ADDITIONS.add(new AddMekanismRecipe<>(NAME, Recipe.ROTARY_CONDENSENTRATOR,
                new RotaryRecipe(fluidInput, gasInput, gasOutput, fluidOutput)));
    }

    @ZenMethod
    public static void removeRecipe(@Optional IIngredient fluidInput, @Optional IIngredient gasInput, @Optional IIngredient gasOutput,
          @Optional IIngredient fluidOutput) {
        CrafttweakerIntegration.LATE_REMOVALS.add(new RemoveMekanismRecipe<>(NAME, Recipe.ROTARY_CONDENSENTRATOR,
                new IngredientWrapper(gasOutput, fluidOutput), new IngredientWrapper(fluidInput, gasInput)));
    }

    @ZenMethod
    public static void removeFluidToGasRecipe(IIngredient fluidInput, @Optional IIngredient gasOutput) {
        if (IngredientHelper.checkNotNull(NAME, fluidInput)) {
            CrafttweakerIntegration.LATE_REMOVALS.add(new RemoveMekanismRecipe<>(NAME, Recipe.ROTARY_CONDENSENTRATOR,
                    new IngredientWrapper(gasOutput, (IIngredient) null), new IngredientWrapper(fluidInput, (IIngredient) null)));
        }
    }

    @ZenMethod
    public static void removeGasToFluidRecipe(IIngredient gasInput, @Optional IIngredient fluidOutput) {
        if (IngredientHelper.checkNotNull(NAME, gasInput)) {
            CrafttweakerIntegration.LATE_REMOVALS.add(new RemoveMekanismRecipe<>(NAME, Recipe.ROTARY_CONDENSENTRATOR,
                    new IngredientWrapper(null, fluidOutput), new IngredientWrapper(null, gasInput)));
        }
    }

    @ZenMethod
    public static void removeAllRecipes() {
        CrafttweakerIntegration.LATE_REMOVALS.add(new RemoveAllMekanismRecipe<>(NAME, Recipe.ROTARY_CONDENSENTRATOR));
    }
}
