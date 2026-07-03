package mekanism.common.recipe.outputs;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.IExtendedGasTank;
import net.minecraft.nbt.NBTTagCompound;

import java.util.Random;

public class GasOutput extends MachineOutput<GasOutput> {

    private static Random rand = new Random();

    public GasStack output;

    public GasOutput(GasStack stack) {
        output = stack;
    }

    public GasOutput() {
    }

    @Override
    public void load(NBTTagCompound nbtTags) {
        output = GasStack.readFromNBT(nbtTags.getCompoundTag("output"));
    }

    @Override
    public GasOutput copy() {
        return new GasOutput(output.copy());
    }

    public boolean applyOutputs(IExtendedGasTank gasTank, boolean doEmit, int scale) {
        GasStack toOutput = output.copy().withAmount(output.amount * scale);
        GasStack remainder = gasTank.insert(toOutput, Action.get(doEmit), AutomationType.INTERNAL);
        return remainder == null || remainder.amount <= 0;
    }
}
