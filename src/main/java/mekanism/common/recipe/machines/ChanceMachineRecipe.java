package mekanism.common.recipe.machines;

import mekanism.api.inventory.IInventorySlot;
import mekanism.common.recipe.inputs.ItemStackInput;
import mekanism.common.recipe.outputs.ChanceOutput;

public abstract class ChanceMachineRecipe<RECIPE extends ChanceMachineRecipe<RECIPE>> extends MachineRecipe<ItemStackInput, ChanceOutput, RECIPE> {

    public ChanceMachineRecipe(ItemStackInput input, ChanceOutput output) {
        super(input, output);
    }

    public boolean inputMatches(IInventorySlot inputSlot) {
        return getInput().useItemStackFromSlot(inputSlot, false);
    }

    public boolean canOperate(IInventorySlot inputSlot, IInventorySlot primarySlot, IInventorySlot secondarySlot) {
        return inputMatches(inputSlot) && getOutput().applyOutputs(primarySlot, secondarySlot, false);
    }

}
