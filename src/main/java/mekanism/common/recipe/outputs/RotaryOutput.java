package mekanism.common.recipe.outputs;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.fluid.ExtendedFluidHandlerUtils;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.IExtendedGasTank;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;

public class RotaryOutput extends MachineOutput<RotaryOutput> {

    @Nullable
    public GasStack gasOutput;
    @Nullable
    public FluidStack fluidOutput;

    public RotaryOutput(@Nullable GasStack gasOutput, @Nullable FluidStack fluidOutput) {
        this.gasOutput = gasOutput;
        this.fluidOutput = fluidOutput;
    }

    public RotaryOutput() {
    }

    @Override
    public void load(NBTTagCompound nbtTags) {
        gasOutput = GasStack.readFromNBT(nbtTags.getCompoundTag("gasOutput"));
        fluidOutput = FluidStack.loadFluidStackFromNBT(nbtTags.getCompoundTag("fluidOutput"));
    }

    @Override
    public RotaryOutput copy() {
        return new RotaryOutput(gasOutput == null ? null : gasOutput.copy(), fluidOutput == null ? null : fluidOutput.copy());
    }

    public boolean isValid() {
        return gasOutput != null || fluidOutput != null;
    }

    public boolean applyGasOutput(IExtendedGasTank gasTank, boolean doEmit, int scale) {
        if (gasOutput == null || gasOutput.amount <= 0 || scale <= 0) {
            return false;
        }
        GasStack toOutput = gasOutput.copy().withAmount(gasOutput.amount * scale);
        GasStack remainder = gasTank.insert(toOutput, Action.get(doEmit), AutomationType.INTERNAL);
        return remainder == null || remainder.amount <= 0;
    }

    public boolean applyFluidOutput(IExtendedFluidTank fluidTank, boolean doEmit, int scale) {
        if (fluidOutput == null || fluidOutput.amount <= 0 || scale <= 0) {
            return false;
        }
        FluidStack toOutput = new FluidStack(fluidOutput, fluidOutput.amount * scale);
        FluidStack remainder = fluidTank.insert(toOutput, Action.get(doEmit), AutomationType.INTERNAL);
        return ExtendedFluidHandlerUtils.isEmpty(remainder);
    }
}
