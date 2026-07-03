package mekanism.common.recipe.outputs;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.inventory.IInventorySlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

public class ItemStackOutput extends MachineOutput<ItemStackOutput> {

    public ItemStack output = ItemStack.EMPTY;

    public ItemStackOutput(ItemStack stack) {
        output = stack;
    }

    public ItemStackOutput() {
    }

    @Override
    public void load(NBTTagCompound nbtTags) {
        output = new ItemStack(nbtTags.getCompoundTag("output"));
    }

    public boolean applyOutputs(IInventorySlot slot, boolean doEmit) {
        return slot.insertItem(output, doEmit ? Action.EXECUTE : Action.SIMULATE, AutomationType.INTERNAL).isEmpty();
    }

    @Override
    public ItemStackOutput copy() {
        return new ItemStackOutput(output.copy());
    }
}
