package mekanism.common.recipe.machines;

import mekanism.api.gas.GasStack;
import mekanism.api.gas.IExtendedGasTank;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.recipe.inputs.NucleosynthesizerInput;
import mekanism.common.recipe.outputs.ItemStackOutput;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

public class NucleosynthesizerRecipe extends MachineRecipe<NucleosynthesizerInput, ItemStackOutput, NucleosynthesizerRecipe> {

    public double extraEnergy;

    public int ticks;

    public NucleosynthesizerRecipe(ItemStack inputSolid, GasStack inputGas, ItemStack outputSolid, double energy, int duration) {
        this(new NucleosynthesizerInput(inputSolid, inputGas), new ItemStackOutput(outputSolid), energy, duration);
    }

    public NucleosynthesizerRecipe(NucleosynthesizerInput input, ItemStackOutput outputSolid, double energy, int duration) {
        super(input, outputSolid);
        extraEnergy = energy;
        ticks = duration;
    }

    public NucleosynthesizerRecipe(NucleosynthesizerInput input, ItemStackOutput outputSolid, NBTTagCompound extraNBT) {
        super(input, outputSolid);
        extraEnergy = extraNBT.getDouble("extraEnergy");
        ticks = extraNBT.getInteger("duration");
    }

    @Override
    public NucleosynthesizerRecipe copy() {
        return new NucleosynthesizerRecipe(getInput().copy(), getOutput().copy(), extraEnergy, ticks);
    }

    public boolean canOperate(IInventorySlot inputSlot, IExtendedGasTank inputGasTank, IInventorySlot outputSlot) {
        return getInput().use(inputSlot, inputGasTank, false) && getOutput().applyOutputs(outputSlot, false);
    }

}
