package mekanism.common.recipe.outputs;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.inventory.IInventorySlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import mekanism.common.recipe.cache.RecipeExecutionPlanner;
import mekanism.common.recipe.cache.RecipeRandomContext;

import java.util.Random;

public class ChanceOutput extends MachineOutput<ChanceOutput> {

    private static Random rand = new Random();

    public ItemStack primaryOutput = ItemStack.EMPTY;

    public ItemStack secondaryOutput = ItemStack.EMPTY;

    public double secondaryChance;

    public ChanceOutput(ItemStack primary, ItemStack secondary, double chance) {
        primaryOutput = primary;
        secondaryOutput = secondary;
        secondaryChance = chance;
    }

    public ChanceOutput() {
    }

    public ChanceOutput(ItemStack primary) {
        primaryOutput = primary;
    }

    @Override
    public void load(NBTTagCompound nbtTags) {
        primaryOutput = new ItemStack(nbtTags.getCompoundTag("primaryOutput"));
        secondaryOutput = new ItemStack(nbtTags.getCompoundTag("secondaryOutput"));
        secondaryChance = nbtTags.getDouble("secondaryChance");
    }

    public boolean checkSecondary() {
        return RecipeRandomContext.nextDouble(rand) <= secondaryChance;
    }

    public boolean hasPrimary() {
        return !primaryOutput.isEmpty();
    }

    public boolean hasSecondary() {
        return !secondaryOutput.isEmpty();
    }

    public ItemStack getMainOutput() {
        return primaryOutput.copy();
    }

    public ItemStack getMaxSecondaryOutput() {
        return secondaryChance > 0 && hasSecondary() ? secondaryOutput.copy() : ItemStack.EMPTY;
    }

    public ItemStack getSecondaryOutput() {
        return secondaryChance > 0 && checkSecondary() ? secondaryOutput.copy() : ItemStack.EMPTY;
    }

    /** Returns the same probability result for a captured plan seed and operation index. */
    public ItemStack getSecondaryOutput(long randomSeed, long operationIndex) {
        return secondaryChance > 0 && RecipeExecutionPlanner.roll(randomSeed, operationIndex, secondaryChance) ?
              secondaryOutput.copy() : ItemStack.EMPTY;
    }

    public ItemStack nextSecondaryOutput() {
        return getSecondaryOutput();
    }

    public boolean applyOutputs(IInventorySlot primarySlot, IInventorySlot secondarySlot, boolean doEmit) {
        if (hasPrimary()) {
            if (applyOutputs(primarySlot, doEmit, primaryOutput)) {
                return false;
            }
        }
        ItemStack secondary = doEmit ? getSecondaryOutput() : getMaxSecondaryOutput();
        if (!secondary.isEmpty()) {
            return !applyOutputs(secondarySlot, doEmit, secondary);
        }
        return true;
    }

    private boolean applyOutputs(IInventorySlot slot, boolean doEmit, ItemStack output) {
        return !slot.insertItem(output, doEmit ? Action.EXECUTE : Action.SIMULATE, AutomationType.INTERNAL).isEmpty();
    }

    @Override
    public ChanceOutput copy() {
        return new ChanceOutput(primaryOutput.copy(), secondaryOutput.copy(), secondaryChance);
    }
}
