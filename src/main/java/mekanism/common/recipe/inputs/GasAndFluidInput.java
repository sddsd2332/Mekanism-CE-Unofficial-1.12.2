package mekanism.common.recipe.inputs;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.fluid.ExtendedFluidHandlerUtils;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.IExtendedGasTank;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

public class GasAndFluidInput extends MachineInput<GasAndFluidInput> {

    public GasStack ingredientGas;
    public FluidStack ingredientFluid;

    public GasAndFluidInput(GasStack gas, FluidStack fluid) {
        ingredientGas = gas;
        ingredientFluid = fluid;
    }

    public GasAndFluidInput() {
    }

    @Override
    public void load(NBTTagCompound nbtTags) {
        ingredientGas = GasStack.readFromNBT(nbtTags.getCompoundTag("ingredientGas"));
        ingredientFluid = FluidStack.loadFluidStackFromNBT(nbtTags.getCompoundTag("ingredientFluid"));
    }

    @Override
    public GasAndFluidInput copy() {
        return new GasAndFluidInput(ingredientGas.copy(), ingredientFluid.copy());
    }

    @Override
    public boolean isValid() {
        return ingredientGas != null && ingredientFluid != null;
    }

    public boolean useGas(IExtendedGasTank gasTank, boolean deplete, int scale) {
        int amount = ingredientGas.amount * scale;
        GasStack gas = gasTank.getGas();
        if (gas == null || !gas.isGasEqual(ingredientGas) || gas.amount < amount) {
            return false;
        }
        GasStack extracted = gasTank.extract(amount, Action.get(deplete), AutomationType.INTERNAL);
        return extracted != null && extracted.amount == amount;
    }

    public boolean useFluid(IExtendedFluidTank fluidTank, boolean deplete, int scale) {
        if (ingredientFluid == null || scale <= 0) {
            return false;
        }
        int amount = ingredientFluid.amount * scale;
        FluidStack stored = fluidTank.getFluid();
        if (stored != null && stored.containsFluid(new FluidStack(ingredientFluid, amount))) {
            FluidStack extracted = fluidTank.extract(amount, Action.get(deplete), AutomationType.INTERNAL);
            return !ExtendedFluidHandlerUtils.isEmpty(extracted) && extracted.amount == amount;
        }
        return false;
    }

    @Override
    public int hashIngredients() {
        return (ingredientFluid.getFluid() != null ? ingredientFluid.getFluid().hashCode() : 0) << 8 | ingredientGas.hashCode();
    }

    @Override
    public boolean testEquality(GasAndFluidInput other) {
       return other.containsType(ingredientFluid) && other.containsType(ingredientGas);
    }

    public boolean containsType(FluidStack stack) {
        if (stack == null || stack.amount == 0) {
            return false;
        }
        return stack.isFluidEqual(ingredientFluid);
    }

    public boolean containsType(GasStack stack) {
        if (stack == null || stack.amount == 0) {
            return false;
        }
        return stack.isGasEqual(ingredientGas);
    }

    @Override
    public boolean isInstance(Object other) {
        return other instanceof GasAndFluidInput;
    }
}
