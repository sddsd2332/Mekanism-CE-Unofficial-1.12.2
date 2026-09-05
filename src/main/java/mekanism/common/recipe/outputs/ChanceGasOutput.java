package mekanism.common.recipe.outputs;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.IExtendedGasTank;
import net.minecraft.nbt.NBTTagCompound;
import mekanism.common.recipe.cache.RecipeExecutionPlanner;
import mekanism.common.recipe.cache.RecipeRandomContext;

import java.util.Random;

public class ChanceGasOutput extends MachineOutput<ChanceGasOutput> {

    private static Random rand = new Random();

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
        return RecipeRandomContext.nextDouble(rand) <= primaryChance;
    }

    public GasStack getMaxOutput() {
        return primaryChance > 0 && output != null && output.amount > 0 ? output.copy() : null;
    }

    public GasStack getOutput() {
        return primaryChance > 0 && checkSecondary() && output != null && output.amount > 0 ? output.copy() : null;
    }

    public GasStack getOutput(long randomSeed, long operationIndex) {
        return primaryChance > 0 && RecipeExecutionPlanner.roll(randomSeed, operationIndex, primaryChance) &&
              output != null && output.amount > 0 ? output.copy() : null;
    }

    @Override
    public ChanceGasOutput copy() {
        return new ChanceGasOutput(output == null ? null : output.copy(), primaryChance);
    }

    public boolean applyOutputs(IExtendedGasTank gasTank, boolean doEmit, int scale) {
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
                GasStack toOutput = getOutput();
                if (toOutput != null) {
                    gasTank.insert(toOutput, Action.EXECUTE, AutomationType.INTERNAL);
                }
            }
        }
        return true;
    }
}
