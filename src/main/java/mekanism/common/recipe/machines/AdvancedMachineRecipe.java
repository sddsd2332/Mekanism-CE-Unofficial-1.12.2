package mekanism.common.recipe.machines;

import mekanism.api.gas.Gas;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.IExtendedGasTank;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.recipe.cache.IConstantGasRecipe;
import mekanism.common.recipe.inputs.AdvancedMachineInput;
import mekanism.common.recipe.inputs.MachineInput;
import mekanism.common.recipe.outputs.ItemStackOutput;
import net.minecraft.item.ItemStack;

public abstract class AdvancedMachineRecipe<RECIPE extends AdvancedMachineRecipe<RECIPE>> extends MachineRecipe<AdvancedMachineInput, ItemStackOutput, RECIPE> implements IConstantGasRecipe<ItemStack> {

    public AdvancedMachineRecipe(AdvancedMachineInput input, ItemStackOutput output) {
        super(input, output);
    }

    public AdvancedMachineRecipe(ItemStack input, Gas gas, ItemStack output) {
        this(new AdvancedMachineInput(input, gas), new ItemStackOutput(output));
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
    public ItemStack getOutput(ItemStack itemInput, GasStack gasInput) {
        return getOutput().output.copy();
    }

    @Override
    public boolean isOutputEmpty(ItemStack output) {
        return output.isEmpty();
    }

    public boolean inputMatches(IInventorySlot inputSlot, IExtendedGasTank gasTank, int amount) {
        return getInput().useItem(inputSlot, false) && getInput().useSecondary(gasTank, amount, false);
    }

    public boolean canOperate(IInventorySlot inputSlot, IInventorySlot outputSlot, IExtendedGasTank gasTank, int amount) {
        return inputMatches(inputSlot, gasTank, amount) && getOutput().applyOutputs(outputSlot, false);
    }

}
