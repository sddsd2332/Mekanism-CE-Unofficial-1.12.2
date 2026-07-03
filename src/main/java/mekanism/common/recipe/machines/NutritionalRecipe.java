package mekanism.common.recipe.machines;

import mekanism.api.gas.GasStack;
import mekanism.api.gas.IExtendedGasTank;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.recipe.inputs.ItemStackInput;
import mekanism.common.recipe.outputs.GasOutput;
import net.minecraft.item.ItemStack;

public class NutritionalRecipe extends MachineRecipe<ItemStackInput, GasOutput, NutritionalRecipe> {

    public NutritionalRecipe(ItemStackInput input, GasOutput output) {
        super(input, output);
    }

    public NutritionalRecipe(ItemStack input, GasStack output) {
        this(new ItemStackInput(input), new GasOutput(output));
    }

    @Override
    public NutritionalRecipe copy() {
        return new NutritionalRecipe(getInput().copy(), getOutput().copy());
    }

    public boolean canOperate(IInventorySlot inputSlot, IExtendedGasTank outputTank) {
        return getInput().useItemStackFromSlot(inputSlot, false) && getOutput().applyOutputs(outputTank, false, 1);
    }

}
