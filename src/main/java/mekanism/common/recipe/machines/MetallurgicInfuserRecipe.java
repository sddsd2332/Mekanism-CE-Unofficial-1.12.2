package mekanism.common.recipe.machines;

import mekanism.api.inventory.IInventorySlot;
import mekanism.common.InfuseStorage;
import mekanism.common.recipe.inputs.InfusionInput;
import mekanism.common.recipe.outputs.ItemStackOutput;
import net.minecraft.item.ItemStack;

public class MetallurgicInfuserRecipe extends MachineRecipe<InfusionInput, ItemStackOutput, MetallurgicInfuserRecipe> {

    public MetallurgicInfuserRecipe(InfusionInput input, ItemStackOutput output) {
        super(input, output);
    }

    public MetallurgicInfuserRecipe(InfusionInput input, ItemStack output) {
        this(input, new ItemStackOutput(output));
    }

    public boolean inputMatches(IInventorySlot inputSlot, InfuseStorage infuse) {
        return getInput().use(inputSlot, infuse, false);
    }

    public boolean canOperate(IInventorySlot inputSlot, IInventorySlot outputSlot, InfuseStorage infuse) {
        return inputMatches(inputSlot, infuse) && getOutput().applyOutputs(outputSlot, false);
    }

    @Override
    public MetallurgicInfuserRecipe copy() {
        return new MetallurgicInfuserRecipe(getInput().copy(), getOutput().copy());
    }

}
