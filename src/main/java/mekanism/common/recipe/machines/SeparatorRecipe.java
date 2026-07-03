package mekanism.common.recipe.machines;

import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.IExtendedGasTank;
import mekanism.common.recipe.inputs.FluidInput;
import mekanism.common.recipe.outputs.ChemicalPairOutput;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

public class SeparatorRecipe extends MachineRecipe<FluidInput, ChemicalPairOutput, SeparatorRecipe> {

    public double energyUsage;

    public SeparatorRecipe(FluidInput input, double energy, ChemicalPairOutput output) {
        super(input, output);
        energyUsage = energy;
    }

    public SeparatorRecipe(FluidInput input, ChemicalPairOutput output, NBTTagCompound extraNBT) {
        super(input, output);
        energyUsage = extraNBT.getDouble("energyUsage");
    }

    public SeparatorRecipe(FluidStack input, double energy, GasStack left, GasStack right) {
        this(new FluidInput(input), energy, new ChemicalPairOutput(left, right));
    }

    @Override
    public SeparatorRecipe copy() {
        return new SeparatorRecipe(getInput().copy(), energyUsage, getOutput().copy());
    }

    public boolean canOperate(IExtendedFluidTank fluidTank, IExtendedGasTank leftTank, IExtendedGasTank rightTank) {
        return getInput().useFluid(fluidTank, false, 1) && getOutput().applyOutputs(leftTank, rightTank, false, 1);
    }

}
