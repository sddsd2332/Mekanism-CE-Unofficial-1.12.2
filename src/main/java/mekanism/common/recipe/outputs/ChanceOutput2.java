package mekanism.common.recipe.outputs;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.inventory.IInventorySlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import java.util.Random;

public class ChanceOutput2 extends MachineOutput<ChanceOutput2> {

    private static Random rand = new Random();

    public ItemStack primaryOutput = ItemStack.EMPTY;

    public double primaryChance;

    public ChanceOutput2(ItemStack primary, double chance) {
        primaryOutput = primary;
        primaryChance = chance;
    }

    public ChanceOutput2() {
    }


    @Override
    public void load(NBTTagCompound nbtTags) {
        primaryOutput = new ItemStack(nbtTags.getCompoundTag("primaryOutput"));
        primaryChance = nbtTags.getDouble("primaryChance");
    }

    public boolean checkSecondary() {
        return rand.nextDouble() <= primaryChance;
    }

    public boolean hasPrimary() {
        return !primaryOutput.isEmpty();
    }

    public ItemStack getMaxPrimaryOutput() {
        return primaryChance > 0 && hasPrimary() ? primaryOutput.copy() : ItemStack.EMPTY;
    }

    public ItemStack getPrimaryOutput() {
        return primaryChance > 0 && checkSecondary() ? primaryOutput.copy() : ItemStack.EMPTY;
    }

    public boolean applyOutputs(IInventorySlot primarySlot, boolean doEmit) {
        ItemStack output = doEmit ? getPrimaryOutput() : getMaxPrimaryOutput();
        if (!output.isEmpty()) {
            return !applyOutputs(primarySlot, doEmit, output);
        }
        return true;
    }

    private boolean applyOutputs(IInventorySlot slot, boolean doEmit, ItemStack output) {
        return !slot.insertItem(output, doEmit ? Action.EXECUTE : Action.SIMULATE, AutomationType.INTERNAL).isEmpty();
    }

    @Override
    public ChanceOutput2 copy() {
        return new ChanceOutput2(primaryOutput.copy(), primaryChance);
    }
}
