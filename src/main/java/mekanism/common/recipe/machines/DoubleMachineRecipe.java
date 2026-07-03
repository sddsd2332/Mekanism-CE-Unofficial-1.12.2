package mekanism.common.recipe.machines;

import mekanism.api.inventory.IInventorySlot;
import mekanism.common.recipe.inputs.DoubleMachineInput;
import mekanism.common.recipe.outputs.ItemStackOutput;
import net.minecraft.item.ItemStack;

public abstract class DoubleMachineRecipe<RECIPE extends DoubleMachineRecipe<RECIPE>> extends MachineRecipe<DoubleMachineInput, ItemStackOutput, RECIPE> {

    public DoubleMachineRecipe(DoubleMachineInput input, ItemStackOutput output) {
        super(input, output);
    }

    public DoubleMachineRecipe(ItemStack input, ItemStack extra, ItemStack output) {
        this(new DoubleMachineInput(input, extra), new ItemStackOutput(output));
    }

    public boolean inputMatches(IInventorySlot inputSlot, IInventorySlot extraSlot) {
        return getInput().useItem(inputSlot, false) && getInput().useExtra(extraSlot, false);
    }

    public boolean canOperate(IInventorySlot inputSlot, IInventorySlot extraSlot, IInventorySlot outputSlot) {
        return inputMatches(inputSlot, extraSlot) && getOutput().applyOutputs(outputSlot, false);
    }

}
