package mekanism.common.recipe.machines;

import mekanism.api.inventory.IInventorySlot;
import mekanism.common.recipe.inputs.ItemStackInput;
import mekanism.common.recipe.outputs.ItemStackOutput;
import net.minecraft.item.ItemStack;

public abstract class BasicMachineRecipe<RECIPE extends BasicMachineRecipe<RECIPE>> extends MachineRecipe<ItemStackInput, ItemStackOutput, RECIPE> {

    public BasicMachineRecipe(ItemStackInput input, ItemStackOutput output) {
        super(input, output);
    }

    public BasicMachineRecipe(ItemStack input, ItemStack output) {
        this(new ItemStackInput(input), new ItemStackOutput(output));
    }

    public boolean inputMatches(IInventorySlot inputSlot) {
        return getInput().useItemStackFromSlot(inputSlot, false);
    }

    public boolean canOperate(IInventorySlot inputSlot, IInventorySlot outputSlot) {
        return inputMatches(inputSlot) && getOutput().applyOutputs(outputSlot, false);
    }

}
