package mekanism.common.recipe.machines;

import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.IExtendedGasTank;
import mekanism.common.recipe.inputs.RotaryInput;
import mekanism.common.recipe.outputs.RotaryOutput;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;

public class RotaryRecipe extends MachineRecipe<RotaryInput, RotaryOutput, RotaryRecipe> {

    public RotaryRecipe(RotaryInput input, RotaryOutput output) {
        super(input, output);
    }

    public RotaryRecipe(RotaryInput input, RotaryOutput output, NBTTagCompound extraNBT) {
        this(input, output);
    }

    public RotaryRecipe(FluidStack fluidInput, GasStack gasInput, GasStack gasOutput, FluidStack fluidOutput) {
        this(new RotaryInput(fluidInput, gasInput), new RotaryOutput(gasOutput, fluidOutput));
    }

    @Override
    public RotaryRecipe copy() {
        return new RotaryRecipe(getInput().copy(), getOutput().copy());
    }

    public boolean hasGasToFluid() {
        return getInput().gasInput != null && getOutput().fluidOutput != null;
    }

    public boolean hasFluidToGas() {
        return getInput().fluidInput != null && getOutput().gasOutput != null;
    }

    public boolean test(@Nullable GasStack input) {
        return hasGasToFluid() && getInput().containsType(input);
    }

    public boolean test(@Nullable FluidStack input) {
        return hasFluidToGas() && getInput().containsType(input);
    }

    @Nullable
    public GasStack getGasInput() {
        return hasGasToFluid() ? getInput().gasInput : null;
    }

    @Nullable
    public FluidStack getFluidInput() {
        return hasFluidToGas() ? getInput().fluidInput : null;
    }

    @Nullable
    public GasStack getGasOutput(@Nullable FluidStack input) {
        return hasFluidToGas() && getOutput().gasOutput != null ? getOutput().gasOutput.copy() : null;
    }

    @Nullable
    public FluidStack getFluidOutput(@Nullable GasStack input) {
        return hasGasToFluid() && getOutput().fluidOutput != null ? getOutput().fluidOutput.copy() : null;
    }

    public boolean canOperateGasToFluid(IExtendedGasTank gasTank, IExtendedFluidTank fluidTank) {
        return hasGasToFluid() && getInput().useGas(gasTank, false, 1) && getOutput().applyFluidOutput(fluidTank, false, 1);
    }

    public boolean canOperateFluidToGas(IExtendedFluidTank fluidTank, IExtendedGasTank gasTank) {
        return hasFluidToGas() && getInput().useFluid(fluidTank, false, 1) && getOutput().applyGasOutput(gasTank, false, 1);
    }

}
