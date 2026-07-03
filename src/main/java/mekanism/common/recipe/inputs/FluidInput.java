package mekanism.common.recipe.inputs;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.fluid.ExtendedFluidHandlerUtils;
import mekanism.api.fluid.IExtendedFluidTank;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

public class FluidInput extends MachineInput<FluidInput> {

    public FluidStack ingredient;

    public FluidInput(FluidStack stack) {
        ingredient = stack;
    }

    public FluidInput() {
    }

    @Override
    public void load(NBTTagCompound nbtTags) {
        ingredient = FluidStack.loadFluidStackFromNBT(nbtTags.getCompoundTag("input"));
    }

    @Override
    public FluidInput copy() {
        return new FluidInput(ingredient.copy());
    }

    @Override
    public boolean isValid() {
        return ingredient != null;
    }

    public boolean useFluid(IExtendedFluidTank fluidTank, boolean deplete, int scale) {
        if (ingredient == null || scale <= 0) {
            return false;
        }
        int amount = ingredient.amount * scale;
        FluidStack stored = fluidTank.getFluid();
        if (stored != null && stored.containsFluid(new FluidStack(ingredient, amount))) {
            FluidStack extracted = fluidTank.extract(amount, Action.get(deplete), AutomationType.INTERNAL);
            return !ExtendedFluidHandlerUtils.isEmpty(extracted) && extracted.amount == amount;
        }
        return false;
    }

    @Override
    public int hashIngredients() {
        return ingredient.getFluid() != null ? ingredient.getFluid().hashCode() : 0;
    }

    @Override
    public boolean testEquality(FluidInput other) {
        if (!isValid()) {
            return !other.isValid();
        }
        return ingredient.equals(other.ingredient);
    }

    @Override
    public boolean isInstance(Object other) {
        return other instanceof FluidInput;
    }
}
