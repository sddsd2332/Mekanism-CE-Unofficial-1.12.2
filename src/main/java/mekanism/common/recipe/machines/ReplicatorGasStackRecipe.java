package mekanism.common.recipe.machines;

import mekanism.api.gas.GasStack;
import mekanism.api.gas.IExtendedGasTank;
import mekanism.common.recipe.inputs.ChemicalGasInput;
import mekanism.common.recipe.outputs.GasOutput;
import net.minecraft.nbt.NBTTagCompound;

public class ReplicatorGasStackRecipe extends MachineRecipe<ChemicalGasInput, GasOutput, ReplicatorGasStackRecipe> {

    public double extraEnergy;

    public int ticks;

    public ReplicatorGasStackRecipe(ChemicalGasInput input, GasOutput output, double energy, int duration) {
        super(input, output);
        extraEnergy = energy;
        ticks = duration;
    }

    public ReplicatorGasStackRecipe(GasStack input, GasStack gas, GasStack output, double energy, int duration) {
        this(new ChemicalGasInput(input, gas), new GasOutput(output), energy, duration);
    }

    public ReplicatorGasStackRecipe(ChemicalGasInput input, GasOutput outputSolid, NBTTagCompound extraNBT) {
        super(input, outputSolid);
        extraEnergy = extraNBT.getDouble("extraEnergy");
        ticks = extraNBT.getInteger("duration");
    }

    public boolean canOperate(IExtendedGasTank leftTank, IExtendedGasTank rightTank, IExtendedGasTank outputTank) {
        return getInput().useGas(leftTank, rightTank, false, 1) && getOutput().applyOutputs(outputTank, false, 1);
    }

    @Override
    public ReplicatorGasStackRecipe copy() {
        return new ReplicatorGasStackRecipe(getInput().copy(), getOutput().copy(), extraEnergy, ticks);
    }
}
