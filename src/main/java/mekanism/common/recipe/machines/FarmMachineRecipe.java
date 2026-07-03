package mekanism.common.recipe.machines;

import mekanism.api.gas.GasStack;
import mekanism.api.gas.IExtendedGasTank;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.recipe.cache.IConstantGasRecipe;
import mekanism.common.recipe.inputs.AdvancedMachineInput;
import mekanism.common.recipe.inputs.MachineInput;
import mekanism.common.recipe.outputs.ChanceOutput;
import net.minecraft.item.ItemStack;

public abstract class FarmMachineRecipe<RECIPE extends FarmMachineRecipe<RECIPE>> extends MachineRecipe<AdvancedMachineInput, ChanceOutput, RECIPE> implements IConstantGasRecipe<ChanceOutput> {

    public FarmMachineRecipe(AdvancedMachineInput input, ChanceOutput output) {
        super(input, output);
    }

    @Override
    public ItemStack getItemInput() {
        return getInput().itemStack;
    }

    @Override
    public GasStack getGasInput() {
        return new GasStack(getInput().gasType, 1);
    }

    @Override
    public boolean test(ItemStack itemInput, GasStack gasInput) {
        return MachineInput.inputContains(itemInput, getInput().itemStack) && gasInput != null && gasInput.getGas() == getInput().gasType;
    }

    @Override
    public ChanceOutput getOutput(ItemStack itemInput, GasStack gasInput) {
        return getOutput().copy();
    }

    @Override
    public boolean isOutputEmpty(ChanceOutput output) {
        return false;
    }

    public boolean inputMatches(IInventorySlot inputSlot, IExtendedGasTank gasTank, int amount) {
        return getInput().useItem(inputSlot, false) && getInput().useSecondary(gasTank, amount, false);
    }

    public boolean canOperate(IInventorySlot inputSlot, IExtendedGasTank gasTank, int amount, IInventorySlot primarySlot, IInventorySlot secondarySlot) {
        return inputMatches(inputSlot, gasTank, amount) && getOutput().applyOutputs(primarySlot, secondarySlot, false);
    }

}
