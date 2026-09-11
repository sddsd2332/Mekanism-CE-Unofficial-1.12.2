package mekanism.common.recipe.outputs;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.inventory.IInventorySlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.Random;

public class ChanceOutput extends MachineOutput<ChanceOutput> {

    /**
     * Legacy shared source, only used by the no-argument overloads. New call sites should pass an explicit source so that results are
     * reproducible for a given seed and independent between lanes. See {@link #checkSecondary(Random)}.
     */
    private static final Random LEGACY_RANDOM = new Random();

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
        return checkSecondary(LEGACY_RANDOM);
    }

    /**
     * Rolls the secondary output chance with an explicit random source.
     *
     * @param random the source to draw from; must not be null
     * @return true when the secondary output should be produced
     */
    public boolean checkSecondary(@Nonnull Random random) {
        Objects.requireNonNull(random, "Random source cannot be null");
        return random.nextDouble() <= secondaryChance;
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
        return getSecondaryOutput(LEGACY_RANDOM);
    }

    /**
     * Rolls the secondary output with an explicit random source.
     *
     * @param random the source to draw from; must not be null
     * @return a copy of the secondary output when the roll succeeds, otherwise empty
     */
    public ItemStack getSecondaryOutput(@Nonnull Random random) {
        return secondaryChance > 0 && checkSecondary(random) ? secondaryOutput.copy() : ItemStack.EMPTY;
    }

    public ItemStack nextSecondaryOutput() {
        return getSecondaryOutput();
    }

    /**
     * Rolls the secondary output for the next operation with an explicit random source.
     *
     * @param random the source to draw from; must not be null
     * @return a copy of the secondary output when the roll succeeds, otherwise empty
     */
    public ItemStack nextSecondaryOutput(@Nonnull Random random) {
        return getSecondaryOutput(random);
    }

    public boolean applyOutputs(IInventorySlot primarySlot, IInventorySlot secondarySlot, boolean doEmit) {
        return applyOutputs(primarySlot, secondarySlot, doEmit, LEGACY_RANDOM);
    }

    /**
     * Applies this output with an explicit random source.
     *
     * @param random the source used when {@code doEmit} is true; must not be null
     */
    public boolean applyOutputs(IInventorySlot primarySlot, IInventorySlot secondarySlot, boolean doEmit, @Nonnull Random random) {
        Objects.requireNonNull(random, "Random source cannot be null");
        if (hasPrimary()) {
            if (applyOutputs(primarySlot, doEmit, primaryOutput)) {
                return false;
            }
        }
        ItemStack secondary = doEmit ? getSecondaryOutput(random) : getMaxSecondaryOutput();
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
