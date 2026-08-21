package mekanism.common.recipe.machines;

import mekanism.api.gas.GasStack;
import mekanism.api.gas.IExtendedGasTank;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.recipe.cache.IConstantGasRecipe;
import mekanism.common.recipe.inputs.FarmInput;
import mekanism.common.recipe.outputs.FarmOutput;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;

public abstract class FarmMachineRecipe<RECIPE extends FarmMachineRecipe<RECIPE>> extends MachineRecipe<FarmInput, FarmOutput, RECIPE> implements IConstantGasRecipe<FarmOutput> {

    public FarmMachineRecipe(FarmInput input, FarmOutput output) {
        super(input, output);
    }

    @Override
    public ItemStack getItemInput() {
        return getInput().itemStack.copy();
    }

    @Nullable
    @Override
    public GasStack getGasInput() {
        return getInput().gasInput == null ? null : getInput().gasInput.copy();
    }

    @Nullable
    public FluidStack getFluidInput() {
        return getInput().fluidInput == null ? null : getInput().fluidInput.copy();
    }

    @Override
    public boolean test(ItemStack itemInput, GasStack gasInput) {
        return getInput().matches(itemInput, gasInput);
    }

    public boolean test(ItemStack itemInput, FluidStack fluidInput) {
        return getInput().matches(itemInput, fluidInput);
    }

    @Override
    public FarmOutput getOutput(ItemStack itemInput, GasStack gasInput) {
        return getOutput().copy();
    }

    public FarmOutput getOutput(ItemStack itemInput, FluidStack fluidInput) {
        return getOutput().copy();
    }

    @Override
    public boolean isOutputEmpty(FarmOutput output) {
        return output == null || !output.isValid();
    }

    public boolean inputMatches(IInventorySlot inputSlot, IExtendedGasTank gasTank, int amount) {
        return getInput().useItem(inputSlot, false) && getInput().useGas(gasTank, amount, false);
    }

    public boolean inputMatches(IInventorySlot inputSlot, IExtendedFluidTank fluidTank, int amount) {
        return getInput().useItem(inputSlot, false) && getInput().useFluid(fluidTank, amount, false);
    }

    public boolean canOperate(IInventorySlot inputSlot, IExtendedGasTank gasTank, int amount, IInventorySlot primarySlot, IInventorySlot secondarySlot) {
        return inputMatches(inputSlot, gasTank, amount) && getOutput().applyOutputs(primarySlot, secondarySlot, false);
    }

}
