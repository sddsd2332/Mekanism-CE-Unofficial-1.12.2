package mekanism.common.recipe.machines;

import mekanism.api.inventory.IInventorySlot;
import mekanism.common.recipe.inputs.ItemStackInput;
import mekanism.common.recipe.outputs.ChanceOutput2;

public abstract class Chance2MachineRecipe<RECIPE extends Chance2MachineRecipe<RECIPE>> extends MachineRecipe<ItemStackInput, ChanceOutput2, RECIPE> {

    public Chance2MachineRecipe(ItemStackInput input, ChanceOutput2 output) {
        super(input, output);
    }

    public boolean inputMatches(IInventorySlot inputSlot) {
        return getInput().useItemStackFromSlot(inputSlot, false);
    }

    public boolean canOperate(IInventorySlot inputSlot, IInventorySlot primarySlot) {
        return inputMatches(inputSlot) && getOutput().applyOutputs(primarySlot, false);
    }

}
