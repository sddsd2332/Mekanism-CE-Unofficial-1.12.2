package mekanism.common.recipe.outputs;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.inventory.IInventorySlot;
import mekanism.api.recipes.FarmChanceOutput;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Organic Farm output: one guaranteed item and up to 63 independently rolled items.
 */
public class FarmOutput extends MachineOutput<FarmOutput> {

    public static final int MAX_CHANCE_OUTPUTS = 63;

    /** Legacy shared source, only used by the no-argument overloads. See {@link #getOutputs(Random)}. */
    private static final Random LEGACY_RANDOM = new Random();

    public ItemStack primaryOutput = ItemStack.EMPTY;
    private List<FarmChanceOutput> chanceOutputs = Collections.emptyList();

    public FarmOutput(ItemStack guaranteedOutput, List<FarmChanceOutput> chanceOutputs) {
        setOutputs(guaranteedOutput, chanceOutputs);
    }

    public FarmOutput(ItemStack guaranteedOutput, FarmChanceOutput... chanceOutputs) {
        this(guaranteedOutput, Arrays.asList(chanceOutputs));
    }

    public FarmOutput(ItemStack guaranteedOutput, ItemStack chanceOutput, double chance) {
        this(guaranteedOutput, new FarmChanceOutput(chanceOutput, chance));
    }

    public FarmOutput(ItemStack guaranteedOutput) {
        this(guaranteedOutput, Collections.emptyList());
    }

    public FarmOutput() {
    }

    private void setOutputs(ItemStack guaranteedOutput, List<FarmChanceOutput> chanceOutputs) {
        if (guaranteedOutput == null || guaranteedOutput.isEmpty()) {
            throw new IllegalArgumentException("Farm guaranteed output cannot be empty");
        }
        if (chanceOutputs == null) {
            throw new IllegalArgumentException("Farm chance output list cannot be null");
        }
        if (chanceOutputs.size() > MAX_CHANCE_OUTPUTS) {
            throw new IllegalArgumentException("Organic Farm recipes support at most " + MAX_CHANCE_OUTPUTS + " chance outputs");
        }
        List<FarmChanceOutput> outputs = new ArrayList<>(chanceOutputs.size());
        for (FarmChanceOutput chanceOutput : chanceOutputs) {
            if (chanceOutput == null) {
                throw new IllegalArgumentException("Farm chance output cannot be null");
            }
            if (chanceOutput.getChance() > 0) {
                outputs.add(chanceOutput.copy());
            }
        }
        primaryOutput = guaranteedOutput.copy();
        this.chanceOutputs = Collections.unmodifiableList(outputs);
    }

    @Override
    public void load(NBTTagCompound nbtTags) {
        ItemStack guaranteedOutput = new ItemStack(nbtTags.getCompoundTag("primaryOutput"));
        List<FarmChanceOutput> outputs = new ArrayList<>();
        NBTTagList chanceOutputTags = nbtTags.getTagList("chanceOutputs", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < chanceOutputTags.tagCount(); i++) {
            NBTTagCompound chanceOutputTag = chanceOutputTags.getCompoundTagAt(i);
            ItemStack output = new ItemStack(chanceOutputTag.getCompoundTag("output"));
            outputs.add(new FarmChanceOutput(output, chanceOutputTag.getDouble("chance")));
        }
        if (outputs.isEmpty() && nbtTags.hasKey("secondaryOutput")) {
            ItemStack legacyOutput = new ItemStack(nbtTags.getCompoundTag("secondaryOutput"));
            if (!legacyOutput.isEmpty()) {
                outputs.add(new FarmChanceOutput(legacyOutput, nbtTags.getDouble("secondaryChance")));
            }
        }
        if (guaranteedOutput.isEmpty() || outputs.size() > MAX_CHANCE_OUTPUTS) {
            primaryOutput = ItemStack.EMPTY;
            chanceOutputs = Collections.emptyList();
            return;
        }
        setOutputs(guaranteedOutput, outputs);
    }

    public boolean isValid() {
        return !primaryOutput.isEmpty() && chanceOutputs.size() <= MAX_CHANCE_OUTPUTS;
    }

    public ItemStack getGuaranteedOutput() {
        return primaryOutput.copy();
    }

    public ItemStack getMainOutput() {
        return getGuaranteedOutput();
    }

    public List<FarmChanceOutput> getChanceOutputs() {
        List<FarmChanceOutput> outputs = new ArrayList<>(chanceOutputs.size());
        for (FarmChanceOutput chanceOutput : chanceOutputs) {
            outputs.add(chanceOutput.copy());
        }
        return Collections.unmodifiableList(outputs);
    }

    public List<ItemStack> getMaxOutputs() {
        List<ItemStack> outputs = new ArrayList<>(chanceOutputs.size() + 1);
        outputs.add(getGuaranteedOutput());
        for (FarmChanceOutput chanceOutput : chanceOutputs) {
            if (chanceOutput.getChance() > 0) {
                outputs.add(chanceOutput.getOutput());
            }
        }
        return outputs;
    }

    /**
     * Returns the worst-case output list with equal item stacks merged. This is
     * useful for capacity checks and integrations which cannot represent
     * independent chance entries.
     */
    public List<ItemStack> getMergedMaxOutputs() {
        List<ItemStack> merged = new ArrayList<>();
        for (ItemStack output : getMaxOutputs()) {
            if (output.isEmpty()) {
                continue;
            }
            FarmOutput.mergeInto(merged, output);
        }
        return merged;
    }

    private static void mergeInto(List<ItemStack> outputs, ItemStack output) {
        for (ItemStack existing : outputs) {
            if (net.minecraftforge.items.ItemHandlerHelper.canItemStacksStack(existing, output)) {
                existing.grow(output.getCount());
                return;
            }
        }
        outputs.add(output.copy());
    }

    public List<ItemStack> getOutputs() {
        return getOutputs(LEGACY_RANDOM);
    }

    /**
     * Rolls all chance outputs with an explicit random source.
     *
     * @param random the source to draw from; must not be null
     */
    public List<ItemStack> getOutputs(@Nonnull Random random) {
        Objects.requireNonNull(random, "Random source cannot be null");
        List<ItemStack> outputs = new ArrayList<>(chanceOutputs.size() + 1);
        outputs.add(getGuaranteedOutput());
        for (FarmChanceOutput chanceOutput : chanceOutputs) {
            if (random.nextDouble() < chanceOutput.getChance()) {
                outputs.add(chanceOutput.getOutput());
            }
        }
        return outputs;
    }

    // Temporary compatibility helpers for callers that still render or process the old two-output shape.
    public ItemStack getMaxSecondaryOutput() {
        if (chanceOutputs.isEmpty() || chanceOutputs.get(0).getChance() <= 0) {
            return ItemStack.EMPTY;
        }
        return chanceOutputs.get(0).getOutput();
    }

    public ItemStack getSecondaryOutput() {
        return getSecondaryOutput(LEGACY_RANDOM);
    }

    /**
     * Rolls the first chance output with an explicit random source.
     *
     * @param random the source to draw from; must not be null
     */
    public ItemStack getSecondaryOutput(@Nonnull Random random) {
        Objects.requireNonNull(random, "Random source cannot be null");
        if (chanceOutputs.isEmpty()) {
            return ItemStack.EMPTY;
        }
        FarmChanceOutput output = chanceOutputs.get(0);
        return random.nextDouble() < output.getChance() ? output.getOutput() : ItemStack.EMPTY;
    }

    public ItemStack nextSecondaryOutput() {
        return getSecondaryOutput();
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
        if (!insert(primarySlot, primaryOutput, doEmit)) {
            return false;
        }
        ItemStack secondary = doEmit ? getSecondaryOutput(random) : getMaxSecondaryOutput();
        return secondary.isEmpty() || insert(secondarySlot, secondary, doEmit);
    }

    private boolean insert(IInventorySlot slot, ItemStack output, boolean doEmit) {
        return slot.insertItem(output.copy(), doEmit ? Action.EXECUTE : Action.SIMULATE, AutomationType.INTERNAL).isEmpty();
    }

    @Override
    public FarmOutput copy() {
        return new FarmOutput(primaryOutput, chanceOutputs);
    }
}
