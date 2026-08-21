package mekanism.client.jei.machine;

import mekanism.api.gas.Gas;
import mekanism.api.math.MathUtils;
import mekanism.client.jei.MekanismJEI;
import mekanism.common.recipe.GasConversionHandler;
import mekanism.common.recipe.inputs.FarmInput;
import mekanism.common.recipe.machines.FarmMachineRecipe;
import mekanism.common.recipe.outputs.FarmOutput;
import mekanism.common.tile.prefab.TileEntityFarmMachine;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.ingredients.VanillaTypes;
import net.minecraft.item.ItemStack;

import java.util.List;

public class FarmMachineRecipeWrapper<RECIPE extends FarmMachineRecipe<RECIPE>> extends MekanismRecipeWrapper<RECIPE> {

    public FarmMachineRecipeWrapper(RECIPE recipe) {
        super(recipe);
    }

    @Override
    public void getIngredients(IIngredients ingredients) {
        FarmInput input = recipe.getInput();
        FarmOutput output = recipe.getOutput();
        ingredients.setInput(VanillaTypes.ITEM, input.itemStack);
        int amount = getSecondaryInputAmount();
        if (input.isGasInput()) {
            ingredients.setInput(MekanismJEI.TYPE_GAS, input.gasInput.copy().withAmount(amount));
        } else if (input.isFluidInput()) {
            ingredients.setInput(VanillaTypes.FLUID, new net.minecraftforge.fluids.FluidStack(input.fluidInput, amount));
        }
        ingredients.setOutputs(VanillaTypes.ITEM, output.getMaxOutputs());
    }

    public int getSecondaryInputAmount() {
        FarmInput input = recipe.getInput();
        int recipeAmount = input.isGasInput() ? input.gasInput.amount : input.fluidInput.amount;
        return Math.max(1, MathUtils.clampToInt((long) recipeAmount * TileEntityFarmMachine.BASE_TICKS_REQUIRED *
              TileEntityFarmMachine.BASE_GAS_PER_TICK));
    }

    public List<ItemStack> getFuelStacks(Gas gasType) {
        return GasConversionHandler.getStacksForGas(gasType);
    }

}
