package mekanism.common.recipe.outputs;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.IExtendedGasTank;
import net.minecraft.nbt.NBTTagCompound;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.Random;

public class ChanceGasOutput extends MachineOutput<ChanceGasOutput> {

    /** Legacy shared source, only used by the no-argument overloads. See {@link #checkSecondary(Random)}. */
    private static final Random LEGACY_RANDOM = new Random();

    public GasStack output;
    public double primaryChance;
    public ChanceGasOutput(GasStack stack, double chance) {
        output = stack;
        primaryChance = chance;
    }

    public ChanceGasOutput() {
    }

    @Override
    public void load(NBTTagCompound nbtTags) {
        output = GasStack.readFromNBT(nbtTags.getCompoundTag("output"));
        primaryChance = nbtTags.getDouble("primaryChance");
    }

    public boolean checkSecondary() {
        return checkSecondary(LEGACY_RANDOM);
    }

    /**
     * Rolls the output chance with an explicit random source.
     *
     * @param random the source to draw from; must not be null
     * @return true when the output should be produced
     */
    public boolean checkSecondary(@Nonnull Random random) {
        Objects.requireNonNull(random, "Random source cannot be null");
        return random.nextDouble() <= primaryChance;
    }

    public GasStack getMaxOutput() {
        return primaryChance > 0 && output != null && output.amount > 0 ? output.copy() : null;
    }

    public GasStack getOutput() {
        return getOutput(LEGACY_RANDOM);
    }

    /**
     * Rolls the output with an explicit random source.
     *
     * @param random the source to draw from; must not be null
     * @return a copy of the output when the roll succeeds, otherwise null
     */
    public GasStack getOutput(@Nonnull Random random) {
        Objects.requireNonNull(random, "Random source cannot be null");
        return primaryChance > 0 && checkSecondary(random) && output != null && output.amount > 0 ? output.copy() : null;
    }

    @Override
    public ChanceGasOutput copy() {
        return new ChanceGasOutput(output == null ? null : output.copy(), primaryChance);
    }

    public boolean applyOutputs(IExtendedGasTank gasTank, boolean doEmit, int scale) {
        return applyOutputs(gasTank, doEmit, scale, LEGACY_RANDOM);
    }

    /**
     * Applies this output with an explicit random source.
     *
     * @param random the source used when {@code doEmit} is true; must not be null
     */
    public boolean applyOutputs(IExtendedGasTank gasTank, boolean doEmit, int scale, @Nonnull Random random) {
        Objects.requireNonNull(random, "Random source cannot be null");
        GasStack maxOutput = getMaxOutput();
        if (maxOutput == null || scale <= 0) {
            return true;
        }
        maxOutput = maxOutput.withAmount(maxOutput.amount * scale);
        GasStack remainder = gasTank.insert(maxOutput, Action.SIMULATE, AutomationType.INTERNAL);
        if (remainder != null && remainder.amount > 0) {
            return false;
        }
        if (doEmit) {
            for (int i = 0; i < scale; i++) {
                GasStack toOutput = getOutput(random);
                if (toOutput != null) {
                    gasTank.insert(toOutput, Action.EXECUTE, AutomationType.INTERNAL);
                }
            }
        }
        return true;
    }
}
