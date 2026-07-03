package mekanism.common.recipe.machines;

import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.IExtendedGasTank;
import mekanism.common.recipe.inputs.GasAndFluidInput;
import mekanism.common.recipe.outputs.FluidOutput;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

public class ReplicatorFluidStackRecipe extends MachineRecipe<GasAndFluidInput, FluidOutput, ReplicatorFluidStackRecipe> {

    public double extraEnergy;

    public int ticks;

    public ReplicatorFluidStackRecipe(GasAndFluidInput input, FluidOutput output, double energy, int duration) {
        super(input, output);
        extraEnergy = energy;
        ticks = duration;
    }

    public ReplicatorFluidStackRecipe(FluidStack input, GasStack gas, FluidStack output, double energy, int duration) {
        this(new GasAndFluidInput(gas, input), new FluidOutput(output), energy, duration);
    }

    public ReplicatorFluidStackRecipe(GasAndFluidInput input, FluidOutput outputSolid, NBTTagCompound extraNBT) {
        super(input, outputSolid);
        extraEnergy = extraNBT.getDouble("extraEnergy");
        ticks = extraNBT.getInteger("duration");
    }

    public boolean canOperate(IExtendedGasTank inputTank, IExtendedFluidTank fluidTank, IExtendedFluidTank outputTank) {
        return getInput().useGas(inputTank, false, 1) && getInput().useFluid(fluidTank, false, 1)  && getOutput().applyOutputs(outputTank, false);
    }

    @Override
    public ReplicatorFluidStackRecipe copy() {
        return new ReplicatorFluidStackRecipe(getInput().copy(), getOutput().copy(), extraEnergy, ticks);
    }
}
