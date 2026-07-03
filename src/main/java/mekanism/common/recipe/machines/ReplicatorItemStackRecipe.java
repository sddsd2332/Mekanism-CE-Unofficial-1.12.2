package mekanism.common.recipe.machines;

import mekanism.api.gas.GasStack;
import mekanism.api.gas.IExtendedGasTank;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.recipe.inputs.NucleosynthesizerInput;
import mekanism.common.recipe.outputs.ItemStackOutput;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

public class ReplicatorItemStackRecipe extends MachineRecipe<NucleosynthesizerInput, ItemStackOutput, ReplicatorItemStackRecipe> {

    public double extraEnergy;

    public int ticks;

    public ReplicatorItemStackRecipe(NucleosynthesizerInput input, ItemStackOutput output, double energy, int duration) {
        super(input, output);
        extraEnergy = energy;
        ticks = duration;
    }

    public ReplicatorItemStackRecipe(ItemStack input, GasStack gas, ItemStack output, double energy, int duration) {
        this(new NucleosynthesizerInput(input, gas), new ItemStackOutput(output), energy, duration);
    }

    public ReplicatorItemStackRecipe(NucleosynthesizerInput input, ItemStackOutput outputSolid, NBTTagCompound extraNBT) {
        super(input, outputSolid);
        extraEnergy = extraNBT.getDouble("extraEnergy");
        ticks = extraNBT.getInteger("duration");
    }

    public boolean canOperate(IInventorySlot inputSlot, IInventorySlot outputSlot, IExtendedGasTank gasTank) {
        return getInput().use(inputSlot, gasTank, false) && getOutput().applyOutputs(outputSlot, false);
    }

    @Override
    public ReplicatorItemStackRecipe copy() {
        return new ReplicatorItemStackRecipe(getInput().copy(), getOutput().copy(), extraEnergy, ticks);
    }
}
