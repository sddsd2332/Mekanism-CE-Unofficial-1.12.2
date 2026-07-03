package mekanism.common.recipe.machines;

import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.IExtendedGasTank;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.recipe.inputs.PressurizedInput;
import mekanism.common.recipe.outputs.PressurizedOutput;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

public class PressurizedRecipe extends MachineRecipe<PressurizedInput, PressurizedOutput, PressurizedRecipe> {

    public double extraEnergy;

    public int ticks;

    public PressurizedRecipe(ItemStack inputSolid, FluidStack inputFluid, GasStack inputGas, ItemStack outputSolid, GasStack outputGas, double energy, int duration) {
        this(new PressurizedInput(inputSolid, inputFluid, inputGas), new PressurizedOutput(outputSolid, outputGas), energy, duration);
    }

    public PressurizedRecipe(PressurizedInput pressurizedInput, PressurizedOutput pressurizedProducts, double energy, int duration) {
        super(pressurizedInput, pressurizedProducts);
        extraEnergy = energy;
        ticks = duration;
    }

    public PressurizedRecipe(PressurizedInput pressurizedInput, PressurizedOutput pressurizedProducts, NBTTagCompound extraNBT) {
        super(pressurizedInput, pressurizedProducts);
        extraEnergy = extraNBT.getDouble("extraEnergy");
        ticks = extraNBT.getInteger("duration");
    }

    public boolean test(ItemStack itemInput, FluidStack fluidInput, GasStack gasInput) {
        return getInput().meets(new PressurizedInput(itemInput, fluidInput, gasInput));
    }

    public PressurizedOutput getOutput(ItemStack itemInput, FluidStack fluidInput, GasStack gasInput) {
        return getOutput().copy();
    }

    @Override
    public PressurizedRecipe copy() {
        return new PressurizedRecipe(getInput().copy(), getOutput().copy(), extraEnergy, ticks);
    }

    public boolean canOperate(IInventorySlot inputSlot, IExtendedFluidTank inputFluidTank, IExtendedGasTank inputGasTank,
          IExtendedGasTank outputGasTank, IInventorySlot outputSlot) {
        return getInput().use(inputSlot, inputFluidTank, inputGasTank, false) && getOutput().applyOutputs(outputSlot, outputGasTank, false);
    }

}
