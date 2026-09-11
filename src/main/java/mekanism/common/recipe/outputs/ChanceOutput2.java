package mekanism.common.recipe.outputs;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.inventory.IInventorySlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.Random;

public class ChanceOutput2 extends MachineOutput<ChanceOutput2> {

    /** Legacy shared source, only used by the no-argument overloads. See {@link #checkSecondary(Random)}. */
    private static final Random LEGACY_RANDOM = new Random();

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
        return checkSecondary(LEGACY_RANDOM);
    }

    /**
     * Rolls the primary output chance with an explicit random source.
     *
     * @param random the source to draw from; must not be null
     * @return true when the primary output should be produced
     */
    public boolean checkSecondary(@Nonnull Random random) {
        Objects.requireNonNull(random, "Random source cannot be null");
        return random.nextDouble() <= primaryChance;
    }

    public boolean hasPrimary() {
        return !primaryOutput.isEmpty();
    }

    public ItemStack getMaxPrimaryOutput() {
        return primaryChance > 0 && hasPrimary() ? primaryOutput.copy() : ItemStack.EMPTY;
    }

    public ItemStack getPrimaryOutput() {
        return getPrimaryOutput(LEGACY_RANDOM);
    }

    /**
     * Rolls the primary output with an explicit random source.
     *
     * @param random the source to draw from; must not be null
     * @return a copy of the primary output when the roll succeeds, otherwise empty
     */
    public ItemStack getPrimaryOutput(@Nonnull Random random) {
        return primaryChance > 0 && checkSecondary(random) ? primaryOutput.copy() : ItemStack.EMPTY;
    }

    public boolean applyOutputs(IInventorySlot primarySlot, boolean doEmit) {
        return applyOutputs(primarySlot, doEmit, LEGACY_RANDOM);
    }

    /**
     * Applies this output with an explicit random source.
     *
     * @param random the source used when {@code doEmit} is true; must not be null
     */
    public boolean applyOutputs(IInventorySlot primarySlot, boolean doEmit, @Nonnull Random random) {
        Objects.requireNonNull(random, "Random source cannot be null");
        ItemStack output = doEmit ? getPrimaryOutput(random) : getMaxPrimaryOutput();
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
